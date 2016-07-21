/*
 *    Copyright 2015 West Coast Informatics, LLC
 */
package com.wci.umls.server.jpa.algo.action;

import java.util.ArrayList;
import java.util.List;

import com.wci.umls.server.ValidationResult;
import com.wci.umls.server.helpers.ComponentInfo;
import com.wci.umls.server.helpers.LocalException;
import com.wci.umls.server.jpa.ValidationResultJpa;
import com.wci.umls.server.jpa.content.ConceptJpa;
import com.wci.umls.server.jpa.content.ConceptRelationshipJpa;
import com.wci.umls.server.jpa.content.SemanticTypeComponentJpa;
import com.wci.umls.server.model.content.Atom;
import com.wci.umls.server.model.content.Concept;
import com.wci.umls.server.model.content.ConceptRelationship;
import com.wci.umls.server.model.content.Relationship;
import com.wci.umls.server.model.content.SemanticTypeComponent;
import com.wci.umls.server.model.workflow.WorkflowStatus;
import com.wci.umls.server.services.handlers.GraphResolutionHandler;

/**
 * A molecular action for merging two concepts.
 */
public class MergeMolecularAction extends AbstractMolecularAction {

  /** The to concept pre updates. */
  private Concept toConceptPreUpdates;

  /** The to concept post updates. */
  private Concept toConceptPostUpdates;

  /** The from concept pre updates. */
  private Concept fromConceptPreUpdates;

  /**
   * Instantiates an empty {@link MergeMolecularAction}.
   *
   * @throws Exception the exception
   */
  public MergeMolecularAction() throws Exception {
    super();
    // n/a
  }

  /**
   * Returns the from concept.
   *
   * @return the from concept
   */
  public Concept getFromConcept() {
    return getConcept2();
  }

  /**
   * Returns the to concept.
   *
   * @return the to concept
   */
  public Concept getToConcept() {
    return getConcept();
  }

  /**
   * Returns the to concept pre updates.
   *
   * @return the to concept pre updates
   */
  public Concept getToConceptPreUpdates() {
    return toConceptPreUpdates;
  }

  /**
   * Returns the to concept post updates.
   *
   * @return the to concept post updates
   */
  public Concept getToConceptPostUpdates() {
    return toConceptPostUpdates;
  }

  /**
   * Returns the from concept pre updates.
   *
   * @return the from concept pre updates
   */
  public Concept getFromConceptPreUpdates() {
    return fromConceptPreUpdates;
  }

  /* see superclass */
  @Override
  public ValidationResult checkPreconditions() throws Exception {
    final ValidationResult validationResult = new ValidationResultJpa();
    // Perform action specific validation - n/a

    // Metadata referential integrity checking

    // Check to make sure concepts are different
    if (getFromConcept() == getToConcept()) {
      throw new LocalException(
          "Cannot merge concept " + getFromConcept().getId() + " with concept "
              + getToConcept().getId() + " - identical concept.");
    }
    
    // Merging concepts must be from the same terminology
    if (!(getFromConcept().getTerminology().toString().equals(getToConcept().getTerminology().toString()))){
      throw new LocalException(
          "Two concepts must be from the same terminology to be merged, but concept " + getFromConcept().getId() + " has terminology " + getFromConcept().getTerminology() +", and Concept " + getToConcept().getId() + " has terminology " + getToConcept().getTerminology());
    }
    
    validateMerge(getProject(), getToConcept(), getFromConcept());

    return validationResult;
  }

  /* see superclass */
  @Override
  public void compute() throws Exception {
    //
    // Perform the action (contentService will create atomic actions for CRUD
    // operations)
    //

    // Make copy of toConcept and fromConcept before changes, to pass into
    // change event
    toConceptPreUpdates = new ConceptJpa(getToConcept(), false);
    fromConceptPreUpdates = new ConceptJpa(getFromConcept(), false);

    // Add each atom from fromConcept to toConcept, delete from
    // fromConcept, and set to NEEDS_REVIEW
    final List<Atom> fromAtoms = new ArrayList<>(getFromConcept().getAtoms());
    moveAtoms(getToConcept(), getFromConcept(), fromAtoms);

    if (getChangeStatusFlag()) {
      for (Atom atm : fromAtoms) {
        atm.setWorkflowStatus(WorkflowStatus.NEEDS_REVIEW);
      }
    }

    // Add each semanticType from fromConcept to toConcept, and delete from
    // fromConcept
    // NOTE: Only add semantic type if it doesn't already exist in toConcept
    final List<SemanticTypeComponent> fromStys =
        new ArrayList<>(getFromConcept().getSemanticTypes());
    final List<SemanticTypeComponent> toStys =
        getToConcept().getSemanticTypes();

    for (SemanticTypeComponent sty : fromStys) {
      // remove the semantic type from the fromConcept
      getFromConcept().getSemanticTypes().remove(sty);

      // remove the semantic type component
      removeSemanticTypeComponent(sty.getId());

      if (!toStys.contains(sty)) {
        sty.setId(null);
        SemanticTypeComponentJpa newSemanticType =
            (SemanticTypeComponentJpa) addSemanticTypeComponent(sty,
                getToConcept());
        if (getChangeStatusFlag()) {
          newSemanticType.setWorkflowStatus(WorkflowStatus.NEEDS_REVIEW);
        }

        // add the semantic type and set the last modified by
        getToConcept().getSemanticTypes().add(newSemanticType);
      }

    }

    // Add each relationship from/to fromConcept to be attached to toConcept,
    // and delete from fromConcept

    List<ConceptRelationship> fromRelationships =
        new ArrayList<>(getFromConcept().getRelationships());

    // Go through all relationships in the fromConcept
    for (final ConceptRelationship rel : fromRelationships) {
      // Any relationship between from and toConcept is deleted
      if (getToConcept().getId() == rel.getTo().getId()) {

        // remove the relationship type component from the concept and update
        getFromConcept().getRelationships().remove(rel);
        // remove the relationship component
        removeRelationship(rel.getId(), rel.getClass());

        // If inverse relationship exists, remove it as well
        List<ConceptRelationship> toRelationships =
            new ArrayList<>(getToConcept().getRelationships());

        ConceptRelationship inverseRel = null;
        for (final Relationship<? extends ComponentInfo, ? extends ComponentInfo> innerInverseRel : toRelationships) {
          if (innerInverseRel.getTo().getId() == rel.getFrom().getId()
              && innerInverseRel.getFrom().getId() == rel.getTo().getId()) {
            if (inverseRel != null) {
              throw new Exception(
                  "Unexepected more than a single inverse relationship for relationship - "
                      + rel);
            }

            inverseRel = (ConceptRelationship) innerInverseRel;
          }
        }

        if (inverseRel != null) {
          // remove the inverse relationship type component from the concept
          // and update
          getToConcept().getRelationships().remove(inverseRel);

          // remove the inverse relationship component
          removeRelationship(inverseRel.getId(), inverseRel.getClass());
        }

      }
      // If relationship is not between two merging concepts, add relationship
      // and inverse toConcept and remove from fromConcept
      else {
        //
        // Remove relationship and inverseRelationship
        //
        // remove the relationship type component from the concept and update
        getFromConcept().getRelationships().remove(rel);

        // remove the relationship component
        removeRelationship(rel.getId(), rel.getClass());

        // If inverse relationship exists, remove it as well
        Concept thirdConcept = rel.getTo();
        List<ConceptRelationship> thirdRelationships =
            new ArrayList<>(thirdConcept.getRelationships());

        ConceptRelationship inverseRel2 = null;
        for (final Relationship<? extends ComponentInfo, ? extends ComponentInfo> innerInverseRel : thirdRelationships) {
          if (innerInverseRel.getTo().getId() == rel.getFrom().getId()
              && innerInverseRel.getFrom().getId() == rel.getTo().getId()) {
            if (inverseRel2 != null) {
              throw new Exception(
                  "Unexepected more than a single inverse relationship for relationship - "
                      + rel);
            }

            inverseRel2 = (ConceptRelationship) innerInverseRel;
          }
        }

        if (inverseRel2 != null) {
          // remove the inverse relationship type component from the concept
          // and update
          thirdConcept.getRelationships().remove(inverseRel2);

          // remove the inverse relationship component
          removeRelationship(inverseRel2.getId(), inverseRel2.getClass());
        }

        //
        // Create and add relationship and inverseRelationship
        //

        // set the relationship component last modified
        rel.setId(null);
        ConceptRelationshipJpa newRel =
            (ConceptRelationshipJpa) addRelationship(rel);
        newRel.setFrom(getToConcept());
        if (getChangeStatusFlag()) {
          newRel.setWorkflowStatus(WorkflowStatus.NEEDS_REVIEW);
        }

        // construct inverse relationship
        ConceptRelationshipJpa inverseRel =
            (ConceptRelationshipJpa) createInverseConceptRelationship(newRel);

        // set the inverse relationship component last modified
        ConceptRelationshipJpa newInverseRel =
            (ConceptRelationshipJpa) addRelationship(inverseRel);
        if (getChangeStatusFlag()) {
          newInverseRel.setWorkflowStatus(WorkflowStatus.NEEDS_REVIEW);
        }

        // add relationship and inverse to respective concepts and set last
        // modified by
        getToConcept().getRelationships().add(newRel);
        thirdConcept.getRelationships().add(newInverseRel);

      }
    }

    if (getChangeStatusFlag()) {
      getToConcept().setWorkflowStatus(WorkflowStatus.NEEDS_REVIEW);
    }

    // update the to concept, and delete the from concept
    updateConcept(getToConcept());
    removeConcept(getFromConcept().getId());

    // log the REST calls
    addLogEntry(getUserName(), getProject().getId(), getToConcept().getId(),
        getName() + " " + getFromConcept() + " into concept "
            + getToConcept().getId());

    // Make copy of toConcept to pass into change event
    toConceptPostUpdates = new ConceptJpa(getToConcept(), false);

    // Resolve all three concepts with graphresolutionhandler.resolve(concept)
    // so they can be appropriately read by ChangeEvent
    GraphResolutionHandler graphHandler =
        getGraphResolutionHandler(getToConcept().getTerminology());
    graphHandler.resolve(fromConceptPreUpdates);
    graphHandler.resolve(toConceptPreUpdates);
    graphHandler.resolve(toConceptPostUpdates);

  }

}