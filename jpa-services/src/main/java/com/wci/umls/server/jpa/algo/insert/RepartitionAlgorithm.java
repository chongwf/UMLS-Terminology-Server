/*
 *    Copyright 2015 West Coast Informatics, LLC
 */
package com.wci.umls.server.jpa.algo.insert;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import com.wci.umls.server.AlgorithmParameter;
import com.wci.umls.server.Project;
import com.wci.umls.server.ValidationResult;
import com.wci.umls.server.helpers.LocalException;
import com.wci.umls.server.jpa.AlgorithmParameterJpa;
import com.wci.umls.server.jpa.ValidationResultJpa;
import com.wci.umls.server.jpa.algo.AbstractSourceInsertionAlgorithm;
import com.wci.umls.server.jpa.services.WorkflowServiceJpa;
import com.wci.umls.server.model.workflow.WorkflowBin;
import com.wci.umls.server.model.workflow.WorkflowBinDefinition;
import com.wci.umls.server.model.workflow.WorkflowConfig;
import com.wci.umls.server.services.WorkflowService;

/**
 * Implementation of an algorithm to repartition bins.
 */
public class RepartitionAlgorithm extends AbstractSourceInsertionAlgorithm {

  /** The type. */
  private String type;

  /**
   * Instantiates an empty {@link RepartitionAlgorithm}.
   * @throws Exception if anything goes wrong
   */
  public RepartitionAlgorithm() throws Exception {
    super();
    setActivityId(UUID.randomUUID().toString());
    setWorkId("REPARTITION");
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
      throw new Exception("Repartition requires a project to be set");
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
    logInfo("Starting REPARTITION");

    // No molecular actions will be generated by this algorithm
    setMolecularActionFlag(false);

    // This algorithm has two steps: clearing the bins, and regenerating the bins
    setSteps(2);
    
    final WorkflowServiceJpa workflowService = new WorkflowServiceJpa();
    try {
      workflowService.setLastModifiedBy(getLastModifiedBy());
      
      // Set transaction mode
      workflowService.setTransactionPerOperation(false);
      workflowService.beginTransaction();

      // Load the project and workflow config
      Project project = getProject();
      // verifyProject -> n/a because we're getting bins for a project
      if (!project.isEditingEnabled()) {
        throw new LocalException(
            "Editing is disabled on project: " + getProject().getName());
      }

      // Start by clearing the bins
      // remove bins and all of the tracking records in the bins
      logInfo("[Repartition] Clearing Current " + type + " Bins");
      
      final List<WorkflowBin> results =
          workflowService.getWorkflowBins(project, type);
      for (final WorkflowBin workflowBin : results) {
        workflowService.removeWorkflowBin(workflowBin.getId(), true);
      }

      workflowService.commit();
      workflowService.beginTransaction();
      
      // Update the progress
      updateProgress();
      
      logInfo("[Repartition] Clearing Current " + type + " Bins Completed");
      logInfo("[Repartition] Regenerating " + type + " Bins");
      commitClearBegin();
      
      // reread after the commit
      project = workflowService.getProject(project.getId());

      final WorkflowConfig workflowConfig =
          workflowService.getWorkflowConfig(project, type);

      // concepts seen set
      final Set<Long> conceptsSeen = new HashSet<>();
      final Map<Long, String> conceptIdWorklistNameMap =
          workflowService.getConceptIdWorklistNameMap(getProject());

      // Look up the bin definitions
      int rank = 0;
      for (final WorkflowBinDefinition definition : workflowConfig
          .getWorkflowBinDefinitions()) {

        workflowService.regenerateBinHelper(project, definition, ++rank,
            conceptsSeen, conceptIdWorklistNameMap);
      }
      workflowService.commit();

      // Update the progress
      updateProgress();

      logInfo("[Repartition] Regenerating " + type + " Bins Completed");

      logInfo("  project = " + project.getId());
      logInfo("  workId = " + getWorkId());
      logInfo("  activityId = " + getActivityId());
      logInfo("  user  = " + getLastModifiedBy());
      logInfo("Finished REPARTITION");

    } catch (

    Exception e) {
      logError("Unexpected problem - " + e.getMessage());
      throw e;
    } finally {
      workflowService.close();
    }

  }

  /**
   * Reset.
   *
   * @throws Exception the exception
   */
  /* see superclass */
  @Override
  public void reset() throws Exception {
    // n/a - No reset
  }

  /* see superclass */
  @Override
  public void checkProperties(Properties p) throws Exception {
    // n/a
  }

  /* see superclass */
  @Override
  public void setProperties(Properties p) throws Exception {
    if (p.getProperty("type") != null) {
      type = String.valueOf(p.getProperty("type"));
    }
  }

  /**
   * Returns the parameters.
   *
   * @return the parameters
   */
  /* see superclass */
  @Override
  public List<AlgorithmParameter> getParameters() {
    final List<AlgorithmParameter> params = super.getParameters();

    // Load all workflow configs, get all types, populate pick-list for workflow
    // bin type parameter
    // Set default value to first mutually exclusive config found.
    List<String> possibleValues = new ArrayList<>();
    String defaultValue = "";
    try {
      final WorkflowService workflowService = new WorkflowServiceJpa();
      final List<WorkflowConfig> configs =
          workflowService.getWorkflowConfigs(getProject());
      for (WorkflowConfig config : configs) {
        possibleValues.add(config.getType());
        if (defaultValue.equals("")) {
          if (config.isMutuallyExclusive()) {
            defaultValue = config.getType();
          }
        }
      }
    } catch (Exception e) {
      // n/a
    }

    AlgorithmParameter param = new AlgorithmParameterJpa("Workflow Bin Type",
        "type", "The type of workflow bin to repartition",
        "e.g. MUTUALLY_EXCLUSIVE", 40, AlgorithmParameter.Type.ENUM,
        defaultValue);
    param.setPossibleValues(possibleValues);
    params.add(param);

    return params;
  }

  @Override
  public String getDescription() {
    return "Regenerates workflow bins.";
  }
}