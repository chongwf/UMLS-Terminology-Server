/**
 * Copyright 2016 West Coast Informatics, LLC
 */
package com.wci.umls.server.rest.impl;

import java.util.Date;

import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;

import com.wci.umls.server.Project;
import com.wci.umls.server.UserRole;
import com.wci.umls.server.ValidationResult;
import com.wci.umls.server.jpa.ValidationResultJpa;
import com.wci.umls.server.jpa.actions.MolecularActionJpa;
import com.wci.umls.server.jpa.content.SemanticTypeComponentJpa;
import com.wci.umls.server.jpa.services.ContentServiceJpa;
import com.wci.umls.server.jpa.services.SecurityServiceJpa;
import com.wci.umls.server.jpa.services.rest.ContentServiceRest;
import com.wci.umls.server.jpa.services.rest.MetaEditingServiceRest;
import com.wci.umls.server.model.actions.MolecularAction;
import com.wci.umls.server.model.content.Concept;
import com.wci.umls.server.model.content.SemanticTypeComponent;
import com.wci.umls.server.model.workflow.WorkflowStatus;
import com.wci.umls.server.services.ContentService;
import com.wci.umls.server.services.SecurityService;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

/**
 * REST implementation for {@link ContentServiceRest}..
 */
@Path("/meta")
@Consumes({
    MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML
})
@Produces({
    MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML
})
@Api(value = "/meta", description = "Operations for metathesaurus editing")
public class MetaEditingServiceRestImpl extends RootServiceRestImpl
    implements MetaEditingServiceRest {

  /** The security service. */
  private SecurityService securityService;

  /**
   * Instantiates an empty {@link MetaEditingServiceRestImpl}.
   *
   * @throws Exception the exception
   */
  public MetaEditingServiceRestImpl() throws Exception {
    securityService = new SecurityServiceJpa();
  }

  /* see superclass */
  @Override
  @POST
  @Path("/sty/add")
  @ApiOperation(value = "Add semantic type to concept", notes = "Add semantic type to concept on a project branch", response = ValidationResultJpa.class)
  public ValidationResult addSemanticType(
    @ApiParam(value = "Project id, e.g. 1", required = true) @QueryParam("projectId") Long projectId,
    @ApiParam(value = "Concept id, e.g. 2", required = true) @QueryParam("conceptId") Long conceptId,
    @ApiParam(value = "Concept timestamp, as date", required = true) @QueryParam("timestamp") Long timestamp,
    @ApiParam(value = "Semantic type to add", required = true) SemanticTypeComponentJpa semanticTypeComponent,
    @ApiParam(value = "Override warnings", required = false) @QueryParam("overrideWarnings") boolean overrideWarnings,
    @ApiParam(value = "Authorization token, e.g. 'author'", required = true) @HeaderParam("Authorization") String authToken)
      throws Exception {
    {

      Logger.getLogger(getClass())
          .info("RESTful POST call (MetaEditing): /sty/" + projectId + "/"
              + conceptId + "/add for user " + authToken + " with sty value "
              + semanticTypeComponent.getSemanticType());

      final String action = "add semantic type to concept";

      final ValidationResult validationResult = new ValidationResultJpa();

      // instantiate services
      final ContentService contentService = new ContentServiceJpa();

      try {

        // authorize and get user name from the token
        final String userName = authorizeProject(contentService, projectId,
            securityService, authToken, action, UserRole.AUTHOR);

        // Initialization: retrieve and lock the concept, create molecular
        // action and prep services
        final Concept concept = prepareConceptAndServiceHelper(contentService,
            conceptId, userName, "ADD_SEMANTIC_TYPE");

        // retrieve the project
        final Project project = contentService.getProject(projectId);

        //
        // Check prerequisites
        //

        // perform action-specific validation
        // NOTE: No action-specific validation required for addSemanticType

        // check project and concept compatibility
        checkPrerequisitesForProjectAndConcept(project, concept,
            validationResult);

        // check for stale-state
        if (concept.getTimestamp().getTime() != timestamp) {
          validationResult.getErrors().add(
              "Stale state detected: stored timestamp does not match passed timestamp");
        }

        // check that semantic type is valid
        if (contentService.getSemanticType(
            semanticTypeComponent.getSemanticType(), concept.getTerminology(),
            concept.getVersion()) == null) {
          validationResult.getErrors()
              .add("Cannot add semantic type: Invalid semantic type");
        }

        // check if semantic type already exists on this concept
        for (SemanticTypeComponent s : concept.getSemanticTypes()) {
          if (s.getSemanticType()
              .equals(semanticTypeComponent.getSemanticType())) {
            validationResult.getErrors().add(
                "Cannot add semantic type: Concept already contains semantic type");
          }
        }

        // if prerequisites fail, return validation result
        if (!validationResult.getErrors().isEmpty()
            || (!validationResult.getWarnings().isEmpty()
                && !overrideWarnings)) {
          // rollback -- unlocks the concept and closes transaction
          contentService.rollback();
          return validationResult;
        }

        //
        // Perform the action
        //

        // add the semantic type component itself and set the last modified
        semanticTypeComponent.setLastModifiedBy(userName);
        semanticTypeComponent.setWorkflowStatus(WorkflowStatus.NEEDS_REVIEW);
        semanticTypeComponent = (SemanticTypeComponentJpa) contentService
            .addSemanticTypeComponent(semanticTypeComponent, concept);

        // add the semantic type and set the last modified by
        concept.getSemanticTypes().add(semanticTypeComponent);
        concept.setWorkflowStatus(WorkflowStatus.NEEDS_REVIEW);

        // update the concept
        contentService.updateConcept(concept);

        // log the REST call
        contentService.addLogEntry(userName, projectId, conceptId,
            "Add semantic type " + semanticTypeComponent.getSemanticType()
                + " to concept " + concept.getTerminologyId());

        // commit (also removes the lock)
        contentService.commit();

        return validationResult;

      } catch (Exception e) {

        // rollback any changes (releases the lock)
        if (contentService != null) {
          try {
            contentService.rollback();
          } catch (IllegalStateException ise) {
            // do nothing -- if no transaction, no lock exists
          }
        }
        handleException(e, action);
        return null;
      } finally {
        contentService.close();
        securityService.close();
      }
    }

  }

  @Override
  @POST
  @Path("/sty/remove/{id}")
  @ApiOperation(value = "Remove semantic type from concept", notes = "Remove semantic type from concept on a project branch", response = ValidationResultJpa.class)
  public ValidationResult removeSemanticType(
    @ApiParam(value = "Project id, e.g. 1", required = true) @QueryParam("projectId") Long projectId,
    @ApiParam(value = "Concept id, e.g. 2", required = true) @QueryParam("conceptId") Long conceptId,
    @ApiParam(value = "Concept timestamp, in ms ", required = true) @QueryParam("timestamp") Long timestamp,
    @ApiParam(value = "Semantic type id, e.g. 3", required = true) @PathParam("id") Long semanticTypeComponentId,
    @ApiParam(value = "Override warnings", required = false) @QueryParam("overrideWarnings") boolean overrideWarnings,
    @ApiParam(value = "Authorization token, e.g. 'author'", required = true) @HeaderParam("Authorization") String authToken)
      throws Exception {

    Logger.getLogger(getClass())
        .info("RESTful POST call (MetaEditing): /sty/" + projectId + "/"
            + conceptId + "/remove for user " + authToken + " with id "
            + semanticTypeComponentId);

    final String action = "add semantic type to concept";

    final ValidationResult validationResult = new ValidationResultJpa();

    final ContentService contentService = new ContentServiceJpa();

    try {

      // authorize and get user name from the token
      final String userName = authorizeProject(contentService, projectId,
          securityService, authToken, action, UserRole.AUTHOR);

      // get the concept and prepare the service
      final Concept concept = prepareConceptAndServiceHelper(contentService,
          conceptId, userName, "REMOVE_SEMANTIC_TYPE");

      // retrieve the project
      final Project project = contentService.getProject(projectId);

      //
      // Check prerequisites
      //

      // perform action-specific validation
      // NOTE: No validation required for removeSemanticType

      // check project and concept compatibility
      checkPrerequisitesForProjectAndConcept(project, concept,
          validationResult);

      // check for stale-state
      if (concept.getTimestamp().getTime() < timestamp) {
        validationResult.getErrors()
            .add("Stale state detected: concept modified after retrieval");
      }

      // check that semantic type component exists on concept
      SemanticTypeComponent semanticTypeComponent = null;
      for (final SemanticTypeComponent sty : concept.getSemanticTypes()) {
        if (sty.getId().equals(semanticTypeComponentId)) {
          semanticTypeComponent = sty;
        }
      }
      if (semanticTypeComponent == null) {
        validationResult.getErrors().add(
            "Semantic type could not be removed from concept, not present");
      }
      // if prerequisites fail, return validation result
      if (!validationResult.getErrors().isEmpty()) {
        contentService.rollback();
        return validationResult;
      }

      //
      // Perform the action
      //

      // remove the semantic type component from the concept and update
      concept.getSemanticTypes().remove(semanticTypeComponent);
      concept.setWorkflowStatus(WorkflowStatus.NEEDS_REVIEW);
      contentService.updateConcept(concept);

      // remove the semantic type component
      contentService.removeSemanticTypeComponent(semanticTypeComponent.getId());

      // log the REST call
      contentService.addLogEntry(userName, projectId, conceptId,
          "Remove semantic type " + semanticTypeComponent.getSemanticType()
              + " from concept " + concept.getTerminologyId());

      // commit (also adds the molecular action and removes the lock)
      contentService.commit();

      return validationResult;

    } catch (Exception e) {

      // rollback any changes (releases the lock)
      if (contentService != null) {
        try {
          contentService.rollback();
        } catch (IllegalStateException ise) {
          // do nothing -- if no transaction, no lock exists
        }
      }
      handleException(e, action);
      return null;
    } finally {
      contentService.close();
      securityService.close();
    }
  }

  /**
   * Validate project and concept.
   *
   * @param project the project
   * @param concept the concept
   * @throws Exception the exception
   */
  private void checkPrerequisitesForProjectAndConcept(Project project,
    Concept concept, ValidationResult validationResult) throws Exception {

    // throw exception on terminology mismatch
    if (!concept.getTerminology().equals(project.getTerminology())) {
      validationResult.getErrors()
          .add("Project and concept terminologies do not match");
    }

    // throw exception on branch mismatch
    if (!concept.getBranch().equals(project.getBranch())) {
      validationResult.getErrors()
          .add("Project and concept branches do not match");
    }
  }

  /**
   * Helper function to (1) retrieve and lock concept, (2) prepare molecular
   * action, and (3) set required service flags
   *
   * @param contentService the content service
   * @param conceptId the concept id
   * @param userName the user name
   * @param actionType the action type
   * @return the concept
   * @throws Exception the exception
   */
  // TODO Feel free to rename this to something less clumsy :)
  private Concept prepareConceptAndServiceHelper(ContentService contentService,
    Long conceptId, String userName, String actionType) throws Exception {
    
 // prepare the transaction
  
    contentService.setTransactionPerOperation(false);
    contentService.beginTransaction();


    Concept concept;
    synchronized (conceptId.toString().intern()) {

      // retrieve the concept
      concept = contentService.getConcept(conceptId);

      // lock the concept via Hibernate, secondary protection
      if (contentService.isObjectLocked(concept)) {
        throw new Exception("Fatal error: concept is locked");
      }
      contentService.lockObject(concept);

    }

    // construct the molecular action
    final MolecularAction molecularAction = new MolecularActionJpa();
    molecularAction.setTerminology(concept.getTerminology());
    molecularAction.setTerminologyId(concept.getTerminologyId());
    molecularAction.setVersion(concept.getVersion());
    molecularAction.setType(actionType);
    molecularAction.setTimestamp(new Date());

    // set the service flags and variables
    contentService.setMolecularActionFlag(true);
    contentService.setMolecularAction(molecularAction);
    contentService.setLastModifiedFlag(true);
    contentService.setLastModifiedBy(userName);

    // persist the molecular action
    contentService.addMolecularAction(molecularAction);

    return concept;
  }
}
