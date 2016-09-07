/*
 *    Copyright 2016 West Coast Informatics, LLC
 */
package com.wci.umls.server.test.jpa.integrity;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.wci.umls.server.Project;
import com.wci.umls.server.ValidationResult;
import com.wci.umls.server.helpers.Branch;
import com.wci.umls.server.helpers.ProjectList;
import com.wci.umls.server.helpers.TypeKeyValue;
import com.wci.umls.server.jpa.ProjectJpa;
import com.wci.umls.server.jpa.algo.action.MergeMolecularAction;
import com.wci.umls.server.jpa.content.ConceptJpa;
import com.wci.umls.server.jpa.helpers.TypeKeyValueJpa;
import com.wci.umls.server.jpa.services.ContentServiceJpa;
import com.wci.umls.server.jpa.services.validation.MGV_I;
import com.wci.umls.server.model.content.Atom;
import com.wci.umls.server.model.content.Concept;
import com.wci.umls.server.test.helpers.IntegrationUnitSupport;

/**
 * Integration testing for {@link MGV_I}.
 */
public class MGV_ITest extends IntegrationUnitSupport {

  /** The project. */
  private Project project;

  /** The service. */
  protected ContentServiceJpa contentService;

  /**  The concept MSH 1. */
  private Concept conceptMSH1 = null;

  /**  The concept MSH 2. */
  private Concept conceptMSH2 = null;

  /**  The new concept. */
  private Concept newConcept = null;

  /** The moved atom. */
  private Atom movedAtom;
  
  /**
   * Setup class.
   *
   * @throws Exception the exception
   */
  @BeforeClass
  public static void setupClass() throws Exception {
    // do nothing
  }

  /**
   * Setup.
   *
   * @throws Exception the exception
   */
  @Before
  public void setup() throws Exception {
    project = null;
    conceptMSH1 = null;
    conceptMSH2 = null;
    newConcept = null;
    movedAtom = null;

    // instantiate service
    contentService = new ContentServiceJpa();
    contentService.setLastModifiedBy("admin");
    contentService.setMolecularActionFlag(false);

    // make a copy of the validationTest project
    ProjectList projects = contentService.getProjects();
    assertTrue(projects.size() > 0);
    project = new ProjectJpa(projects.getObjects().get(0));

    // Reset the project's validation check list, so only this integrity check
    // will run.
    project.setValidationChecks(new ArrayList<>(Arrays.asList("MGV_I")));

    // Setup validationData germane to this test, and add to project
    final List<TypeKeyValue> validationData =
        new ArrayList<TypeKeyValue>(project.getValidationData());
    validationData.add(new TypeKeyValueJpa("MGV_I", "MSH", ""));
    project.setValidationData(validationData);    
    
    // Get two concepts that both contain publishable "MSH" atoms, and 
    // create new concept     
    conceptMSH1 =
        contentService.getConcept("C0003123", "UMLS", "latest", Branch.ROOT);
    conceptMSH2 =
        contentService.getConcept("C0004611", "UMLS", "latest", Branch.ROOT);

    ConceptJpa c = new ConceptJpa(
        contentService.getConcept("C0000734", "UMLS", "latest", Branch.ROOT),
        false);
    c.setId(null);
    c = (ConceptJpa) contentService.addConcept(c);
    contentService = new ContentServiceJpa();
    contentService.setLastModifiedBy("admin");
    contentService.setMolecularActionFlag(false);

    newConcept = contentService.getConcept(c.getId());

    // Move one of concept1's "MSH" atoms into the new concept
    for(Atom atom : conceptMSH1.getAtoms()){
      if(atom.getTerminology().equals("MSH")){
        movedAtom = atom;
        contentService.moveAtoms(conceptMSH1, newConcept,
            new ArrayList<>(Arrays.asList(movedAtom)));
        break;
      }
    }
    
  }    

  /**
   * Test merge normal use.
   *
   * @throws Exception the exception
   */
  @Test
  public void testNormalUse() throws Exception {
    Logger.getLogger(getClass()).info("TEST " + name.getMethodName());

    //
    // Test violation of MGV_I
    // Both concepts contain "MSH" atoms with different codeId
    //

    // Create and configure the action
    final MergeMolecularAction action = new MergeMolecularAction();

    action.setProject(project);
    action.setConceptId(conceptMSH2.getId());
    action.setConceptId2(conceptMSH1.getId());
    action.setLastModifiedBy("admin");
    action.setLastModified(conceptMSH2.getLastModified().getTime());
    action.setOverrideWarnings(false);
    action.setTransactionPerOperation(false);
    action.setMolecularActionFlag(true);
    action.setChangeStatusFlag(true);

    // Check whether the action violates the validation check
    final ValidationResult validationResult = checkActionPreconditions(action);

    // Verify that it returned a validation error
    assertFalse(validationResult.isValid());

    //
    // Test non-violation of MGV_I
    // Both concepts contain "MSH" atoms only with the same codeId
    //

    // Create and configure the action
    final MergeMolecularAction action2 = new MergeMolecularAction();

    action2.setProject(project);
    action2.setConceptId(conceptMSH2.getId());
    action2.setConceptId2(newConcept.getId());
    action2.setLastModifiedBy("admin");
    action2.setLastModified(conceptMSH2.getLastModified().getTime());
    action2.setOverrideWarnings(false);
    action2.setTransactionPerOperation(false);
    action2.setMolecularActionFlag(true);
    action2.setChangeStatusFlag(true);

    // Check whether the action violates the validation check
    final ValidationResult validationResult2 =
        checkActionPreconditions(action2);

    // Verify that returned no validation errors
    assertTrue(validationResult2.isValid());
 
  }

  /**
   * Teardown.
   *
   * @throws Exception the exception
   */
  @After
  public void teardown() throws Exception {
    // Undo the actions done in setup
    
    if(movedAtom!=null){
      contentService = new ContentServiceJpa();
      contentService.setLastModifiedBy("admin");
      contentService.setMolecularActionFlag(false);
      
      contentService.moveAtoms(newConcept, conceptMSH1, new ArrayList<>(Arrays.asList(movedAtom)));
    }
    if(newConcept!=null){
      contentService = new ContentServiceJpa();
      contentService.setLastModifiedBy("admin");
      contentService.setMolecularActionFlag(false);
      
      contentService.removeConcept(newConcept.getId());
    }
  }

  /**
   * Teardown class.
   */
  @AfterClass
  public static void teardownClass() {
    // do nothing
  }

}
