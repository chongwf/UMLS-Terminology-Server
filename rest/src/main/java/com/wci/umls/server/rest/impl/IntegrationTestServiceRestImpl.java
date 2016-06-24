/*
 *    Copyright 2015 West Coast Informatics, LLC
 */
package com.wci.umls.server.rest.impl;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;

import com.wci.umls.server.UserRole;
import com.wci.umls.server.jpa.content.ConceptJpa;
import com.wci.umls.server.jpa.services.ContentServiceJpa;
import com.wci.umls.server.jpa.services.SecurityServiceJpa;
import com.wci.umls.server.jpa.services.WorkflowServiceJpa;
import com.wci.umls.server.jpa.services.rest.IntegrationTestServiceRest;
import com.wci.umls.server.jpa.worfklow.WorklistJpa;
import com.wci.umls.server.model.content.Concept;
import com.wci.umls.server.model.workflow.Worklist;
import com.wci.umls.server.services.ContentService;
import com.wci.umls.server.services.SecurityService;
import com.wci.umls.server.services.WorkflowService;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

/**
 * REST implementation for {@link IntegrationTestServiceRest}..
 */
@Path("/test")
@Consumes({
    MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML
})
@Produces({
    MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML
})
@Api(value = "/test", description = "Support Integration Tests")
public class IntegrationTestServiceRestImpl extends RootServiceRestImpl
    implements IntegrationTestServiceRest {

  /** The security service. */
  private SecurityService securityService;

  /**
   * Instantiates an empty {@link IntegrationTestServiceRestImpl}.
   *
   * @throws Exception the exception
   */
  public IntegrationTestServiceRestImpl() throws Exception {
    securityService = new SecurityServiceJpa();
  }

  /* see superclass */
  @Override
  @PUT
  @Path("/concept/add")
  @ApiOperation(value = "Add new concept", notes = "Creates a new concept", response = ConceptJpa.class)
  public Concept addConcept(
    @ApiParam(value = "Concept, e.g. newConcept", required = true) ConceptJpa concept,
    @ApiParam(value = "Authorization token, e.g. 'guest'", required = true) @HeaderParam("Authorization") String authToken)
    throws Exception {
    Logger.getLogger(getClass())
        .info("RESTful call PUT (Concept): /add " + concept);

    ContentService contentService = new ContentServiceJpa();
    try {
      final String authUser = authorizeApp(securityService, authToken,
          "add concept", UserRole.ADMINISTRATOR);
      contentService.setLastModifiedBy(authUser);
      contentService.setMolecularActionFlag(false);

      // Add concept
      Concept newConcept = contentService.addConcept(concept);

      return newConcept;
    } catch (Exception e) {
      handleException(e, "trying to add a concept");
      return null;
    } finally {
      contentService.close();
      securityService.close();
    }

  }

  /* see superclass */
  @Override
  @DELETE
  @Path("/concept/remove/{id}")
  @ApiOperation(value = "Remove concept", notes = "Removes the concept with the specified id")
  public void removeConcept(
    @ApiParam(value = "Concept id, e.g. 3", required = true) @PathParam("id") Long id,
    @ApiParam(value = "Authorization token, e.g. 'guest'", required = true) @HeaderParam("Authorization") String authToken)
    throws Exception {
    Logger.getLogger(getClass())
        .info("RESTful call DELETE (Concept): /remove/" + id);

    ContentService contentService = new ContentServiceJpa();
    try {
      String authUser = authorizeApp(securityService, authToken,
          "remove concept", UserRole.ADMINISTRATOR);
      contentService.setLastModifiedBy(authUser);
      contentService.setMolecularActionFlag(false);

      // Create service and configure transaction scope
      contentService.removeConcept(id);

      contentService.addLogEntry(authUser, id, id, "REMOVE concept " + id);

    } catch (Exception e) {
      handleException(e, "trying to remove a concept");
    } finally {
      contentService.close();
      securityService.close();
    }

  }


    @Override
    @POST
    @Path("/worklist/add")
    @ApiOperation(value = "Add a worklist", notes = "Add a worklist", response = WorklistJpa.class)
    public Worklist addWorklist(
      @ApiParam(value = "Worklist to add", required = true) WorklistJpa worklist,
      @ApiParam(value = "Authorization token, e.g. 'guest'", required = true) @HeaderParam("Authorization") String authToken)
      throws Exception {

      Logger.getLogger(getClass())
          .info("RESTful POST call (Integration Test): /config/add/"
              + worklist.toString() + " " + authToken);

      String action = "trying to add worklist";

      WorkflowService workflowService = new WorkflowServiceJpa();

      try {

        final String authUser = authorizeProject(workflowService, worklist.getProjectId(),
            securityService, authToken, action, UserRole.AUTHOR);

        workflowService.setLastModifiedBy(authUser);
        return workflowService.addWorklist(worklist);
        
      } catch (Exception e) {
        handleException(e, "trying to add worklist");
        return null;
      } finally {
        workflowService.close();
        securityService.close();
      }

    }
    

    @Override
    @DELETE
    @Path("/worklist/{id}/remove")
    @ApiOperation(value = "Remove a worklist", notes = "Remove a worklist")
    public void removeWorklist(
      @ApiParam(value = "Worklist id, e.g. 1", required = true) @PathParam("id") Long worklistId,
      @ApiParam(value = "Authorization token, e.g. 'guest'", required = true) @HeaderParam("Authorization") String authToken)
        throws Exception {
      Logger.getLogger(getClass())
          .info("RESTful call (Integration Test): /worklist/" + worklistId + "/remove");

      WorkflowService workflowService = new WorkflowServiceJpa();
      try {

        final String authUser = authorizeApp(securityService, authToken, "remove worklist",
            UserRole.USER);

        workflowService.setLastModifiedBy(authUser);
        workflowService.removeWorklist(worklistId);
      } catch (Exception e) {

        handleException(e, "trying to remove a worklist");
      } finally {
        workflowService.close();
        securityService.close();
      }

    }

    @Override
    @GET
    @Path("/worklist/{id}")
    @ApiOperation(value = "Get a worklist", notes = "Get a worklist", response = WorklistJpa.class)
    public Worklist getWorklist(
      @ApiParam(value = "Worklist id, e.g. 1", required = true) @PathParam("id") Long worklistId,
      @ApiParam(value = "Authorization token, e.g. 'guest'", required = true) @HeaderParam("Authorization") String authToken)
        throws Exception {
      Logger.getLogger(getClass())
          .info("RESTful call (Integration Test): /worklist/" + worklistId);

      WorkflowService workflowService = new WorkflowServiceJpa();
      try {

        final String authUser = authorizeApp(securityService, authToken, "get worklist",
            UserRole.USER);

        Worklist worklist = workflowService.getWorklist(worklistId);
        workflowService.setLastModifiedBy(authUser);
        return workflowService.getWorklist(worklistId);
      } catch (Exception e) {

        handleException(e, "trying to remove a worklist");
      } finally {
        workflowService.close();
        securityService.close();
      }
      return null;
    }

 
}
