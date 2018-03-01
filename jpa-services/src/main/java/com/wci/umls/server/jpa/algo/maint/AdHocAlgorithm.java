/*
 *    Copyright 2015 West Coast Informatics, LLC
 */
package com.wci.umls.server.jpa.algo.maint;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import javax.persistence.Query;

import com.wci.umls.server.AlgorithmParameter;
import com.wci.umls.server.ValidationResult;
import com.wci.umls.server.helpers.PfsParameter;
import com.wci.umls.server.jpa.AlgorithmParameterJpa;
import com.wci.umls.server.jpa.ValidationResultJpa;
import com.wci.umls.server.jpa.algo.AbstractInsertMaintReleaseAlgorithm;
import com.wci.umls.server.jpa.algo.action.UndoMolecularAction;
import com.wci.umls.server.jpa.content.ConceptRelationshipJpa;
import com.wci.umls.server.jpa.helpers.PfsParameterJpa;
import com.wci.umls.server.model.actions.MolecularAction;
import com.wci.umls.server.model.actions.MolecularActionList;
import com.wci.umls.server.model.content.Atom;
import com.wci.umls.server.model.content.ConceptRelationship;
import com.wci.umls.server.model.content.Definition;
import com.wci.umls.server.services.RootService;

/**
 * Implementation of an algorithm to execute an action based on a user-defined
 * query.
 */
public class AdHocAlgorithm extends AbstractInsertMaintReleaseAlgorithm {

  /** The actionName. */
  private String actionName;

  /**
   * Instantiates an empty {@link AdHocAlgorithm}.
   * @throws Exception if anything goes wrong
   */
  public AdHocAlgorithm() throws Exception {
    super();
    setActivityId(UUID.randomUUID().toString());
    setWorkId("QUERYACTION");
    setLastModifiedBy("admin");
  }

  /**
   * Check preconditions.
   *
   * @return the validation result
   * @throws Exception the exception
   */
  /* see superclass */
  @Override
  public ValidationResult checkPreconditions() throws Exception {

    ValidationResult validationResult = new ValidationResultJpa();

    if (getProject() == null) {
      throw new Exception("Ad Hoc algorithms requires a project to be set");
    }

    return validationResult;
  }

  /**
   * Compute.
   *
   * @throws Exception the exception
   */
  /* see superclass */
  @Override
  public void compute() throws Exception {
    logInfo("Starting " + getName());
    logInfo("  actionName = " + actionName);

    // No Molecular actions will be generated by this algorithm
    setMolecularActionFlag(false);

    commitClearBegin();

    // Add all ad hoc actions to if-statement chain.
    if (actionName.equals("Fix Orphan Definitions")) {
      fixOrphanDefinitions();
    } else if (actionName.equals("Undo Stampings")) {
      undoStampings();
    } else if (actionName.equals("Remove Bad Relationships")) {
      removeBadRelationships();
    } else {
      throw new Exception("Valid Action Name not specified.");
    }

    commitClearBegin();

    logInfo("  project = " + getProject().getId());
    logInfo("  workId = " + getWorkId());
    logInfo("  activityId = " + getActivityId());
    logInfo("  user  = " + getLastModifiedBy());
    logInfo("Finished " + getName());

  }

  private void undoStampings() throws Exception {
    // 11/29/2017 - A Stamping action was run on a checklist of 14,000 concepts,
    // and it was decided they didn't want them to be stamped after all. Based
    // on the activityId: chk_sct_new_approve, identify all molecular actions
    // and undo them

    int successful = 0;
    int unsuccessful = 0;
    final String activityId = "chk_sct_new_approve";

    // Find all molecular actions associated with the activityId
    final PfsParameter pfs = new PfsParameterJpa();
    pfs.setAscending(false);
    pfs.setSortField("timestamp");
    final MolecularActionList molecularActions =
        findMolecularActions(null, getProject().getTerminology(),
            getProject().getVersion(), "activityId:" + activityId, pfs);

    for (final MolecularAction molecularAction : molecularActions
        .getObjects()) {

      if (molecularAction.isUndoneFlag()) {
        logInfo("Already undone: molecularAction=" + molecularAction.getId()
            + ", for concept=" + molecularAction.getComponentId());
        successful++;
      }

      else {
        // Create and set up an undo action
        final UndoMolecularAction undoAction = new UndoMolecularAction();

        try {
          // Configure and run the undo action
          undoAction.setProject(getProject());
          undoAction.setActivityId(molecularAction.getActivityId());
          undoAction.setConceptId(molecularAction.getComponentId());
          undoAction.setConceptId2(molecularAction.getComponentId2());
          undoAction.setLastModifiedBy(molecularAction.getLastModifiedBy());
          undoAction.setTransactionPerOperation(false);
          undoAction.setMolecularActionFlag(false);
          undoAction.setChangeStatusFlag(true);
          undoAction.setMolecularActionId(molecularAction.getId());
          undoAction.setForce(true);
          undoAction.performMolecularAction(undoAction, getLastModifiedBy(),
              false, false);

          logInfo(
              "Successful undo for molecularAction=" + molecularAction.getId()
                  + ", for concept=" + molecularAction.getComponentId());
          successful++;
        } catch (Exception e) {
          logInfo("Could not undo molecularAction=" + molecularAction.getId()
              + ", for concept=" + molecularAction.getComponentId());
          unsuccessful++;
        } finally {
          undoAction.close();
        }
      }

      logAndCommit(unsuccessful + successful, 100, 100);

    }

    logInfo(
        "[UndoStampings] " + successful + " stampings successfully undone.");
    logInfo("[UndoStampings] " + unsuccessful + " stampings unable to undo.");

  }

  private void fixOrphanDefinitions() throws Exception {
    // 11/14/2017 - Bug in SplitMolecularAction found where definitions weren't
    // being copied over with atoms when split out, resulting in orphaned
    // definitions.
    // Load these definitions and re-add them to the appropriate atom.

    int successful = 0;

    final Map<Long, Long> definitionIdAtomIdMap = new HashMap<>();
    definitionIdAtomIdMap.put(37014L, 338961L);
    definitionIdAtomIdMap.put(275324L, 6783080L);
    definitionIdAtomIdMap.put(275326L, 6783082L);
    definitionIdAtomIdMap.put(275327L, 6783083L);
    definitionIdAtomIdMap.put(275328L, 6783084L);
    definitionIdAtomIdMap.put(275329L, 6783085L);
    definitionIdAtomIdMap.put(275330L, 6783086L);
    definitionIdAtomIdMap.put(275333L, 6783089L);
    definitionIdAtomIdMap.put(275562L, 6783318L);
    definitionIdAtomIdMap.put(275825L, 6783578L);
    definitionIdAtomIdMap.put(327827L, 6815915L);
    definitionIdAtomIdMap.put(327871L, 6815960L);
    definitionIdAtomIdMap.put(327938L, 6816027L);
    definitionIdAtomIdMap.put(327972L, 6816061L);
    definitionIdAtomIdMap.put(327975L, 6816064L);
    definitionIdAtomIdMap.put(328045L, 6816138L);
    definitionIdAtomIdMap.put(328111L, 6816205L);
    definitionIdAtomIdMap.put(362093L, 6854283L);
    definitionIdAtomIdMap.put(362094L, 6851063L);
    definitionIdAtomIdMap.put(362125L, 6854335L);
    definitionIdAtomIdMap.put(362126L, 6851079L);
    definitionIdAtomIdMap.put(362199L, 6854454L);
    definitionIdAtomIdMap.put(362200L, 6851116L);
    definitionIdAtomIdMap.put(5322L, 49047L);
    definitionIdAtomIdMap.put(40739L, 470079L);
    definitionIdAtomIdMap.put(75071L, 1446086L);
    definitionIdAtomIdMap.put(50708L, 685841L);

    for (Map.Entry<Long, Long> entry : definitionIdAtomIdMap.entrySet()) {
      final Long definitionId = entry.getKey();
      final Long atomId = entry.getValue();

      final Atom atom = getAtom(atomId);
      if (atom == null) {
        logWarn("Could not find atom with id=" + atomId);
        continue;
      }

      final Definition definition = getDefinition(definitionId);
      if (definition == null) {
        logWarn("Could not find definition with id=" + definitionId);
        continue;
      }

      if (atom.getDefinitions().contains(definition)) {
        logWarn(
            "atom=" + atomId + " already contains definition=" + definitionId);
        continue;
      }

      atom.getDefinitions().add(definition);
      updateAtom(atom);
      successful++;
    }

    logInfo("[FixOrphanDefinitions] " + successful
        + " orphan definitions successfully reattached.");

  }

  private void removeBadRelationships() throws Exception {
    // 3/1/2018 Bug in RelationshipLoader during a UMLS insertion
    // created multiple relationships between concepts.
    // It also created a number of self-referential relationships.
    // Identify and remove them.

    int removals = 0;

    Set<Long> relIds = new HashSet<>();
    Set<String> seenRelIdPairs = new HashSet<>();
    
    // Get alternate terminology Ids for AtomRelationships, CodeRelationships,
    // ConceptRelationships, etc.
      Query query = getEntityManager().createQuery("select a.id from "
          + "ConceptRelationshipJpa a "
          + "where a.terminology = :terminology and a.version = :version and a.publishable=true");
      query.setParameter("terminology", "MTH");
      query.setParameter("version", "2017AB");

      logInfo("[SourceLoader] Loading " 
          + "ConceptRelationship ids for relationships created by the MTH 2017AB insertion");

      List<Object> list = query.getResultList();
      for (final Object entry : list) {
        final Long id = Long.valueOf(entry.toString());
        relIds.add(id);
      }
    
    for (Long id : relIds) {
      final ConceptRelationship rel = (ConceptRelationshipJpa) getRelationship(id, ConceptRelationshipJpa.class);

      if (rel == null){
        logWarn("Could not find concept relationship with id=" + id);
        continue;        
      }
      
      //If this is a self-referential relationship, remove it and its inverse
      if(rel.getFrom().getId().equals(rel.getTo().getId())){
        ConceptRelationship inverseRel = (ConceptRelationshipJpa) getInverseRelationship(rel.getTerminology(), rel.getVersion(), rel);
        removeRelationship(id, ConceptRelationshipJpa.class);
        removeRelationship(inverseRel.getId(), ConceptRelationshipJpa.class);
        removals++;
      }

      //If this the concept-pair has been seen, remove this relationship and its inverse
      else if(seenRelIdPairs.contains(rel.getFrom().getId() + "|" + rel.getTo().getId())){
        ConceptRelationship inverseRel = (ConceptRelationshipJpa) getInverseRelationship(rel.getTerminology(), rel.getVersion(), rel);
        removeRelationship(id, ConceptRelationshipJpa.class);
        removeRelationship(inverseRel.getId(), ConceptRelationshipJpa.class);
        removals++;
      }

      //Otherwise, log this concept-pair as seen.
      else{
        seenRelIdPairs.add(rel.getFrom().getId() + "|" + rel.getTo().getId());
      }

      //Occasionally commit
      if(removals % RootService.commitCt == 0){
        commitClearBegin();
      }
      
    }

    logInfo("[RemoveBadRelationships] " + removals
        + " bad relationships successfully removed.");
    
  }  
  
  /* see superclass */
  @Override
  public void reset() throws Exception {
    logInfo("Starting RESET " + getName());
    // n/a - No reset
    logInfo("Finished RESET " + getName());
  }

  /* see superclass */
  @Override
  public void checkProperties(Properties p) throws Exception {
    checkRequiredProperties(new String[] {
        "actionName"
    }, p);
  }

  /* see superclass */
  @Override
  public void setProperties(Properties p) throws Exception {

    if (p.getProperty("actionName") != null) {
      actionName = String.valueOf(p.getProperty("actionName"));
    }

  }

  /**
   * Returns the parameters.
   *
   * @return the parameters
   * @throws Exception the exception
   */
  /* see superclass */
  @Override
  public List<AlgorithmParameter> getParameters() throws Exception {
    final List<AlgorithmParameter> params = super.getParameters();
    AlgorithmParameter param = new AlgorithmParameterJpa("Action Name",
        "actionName", "Name of Ad Hoc Action to be performed",
        "e.g. Fix Orphan Definitions", 200, AlgorithmParameter.Type.ENUM, "");
    param.setPossibleValues(
        Arrays.asList("Fix Orphan Definitions", "Undo Stampings", "Remove Bad Relationships"));
    params.add(param);

    return params;
  }

  /* see superclass */
  @Override
  public String getDescription() {
    return "Perform Ad Hoc Action, normally for data fixes.";
  }

}