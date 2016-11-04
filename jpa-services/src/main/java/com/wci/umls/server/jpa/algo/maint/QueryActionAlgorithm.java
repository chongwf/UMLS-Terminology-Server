/*
 *    Copyright 2015 West Coast Informatics, LLC
 */
package com.wci.umls.server.jpa.algo.maint;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import com.wci.umls.server.AlgorithmParameter;
import com.wci.umls.server.ValidationResult;
import com.wci.umls.server.helpers.QueryType;
import com.wci.umls.server.jpa.AlgorithmParameterJpa;
import com.wci.umls.server.jpa.ValidationResultJpa;
import com.wci.umls.server.jpa.algo.AbstractAlgorithm;
import com.wci.umls.server.model.meta.IdType;
import com.wci.umls.server.services.handlers.IdentifierAssignmentHandler;

/**
 * Implementation of an algorithm to execute an action based on a user-defined
 * query.
 */
public class QueryActionAlgorithm extends AbstractAlgorithm {

  /** The previous progress. */
  private int previousProgress;

  /** The steps. */
  private int steps;

  /** The steps completed. */
  private int stepsCompleted;

  /** The object type. */
  private String objectType;

  /** The query type. */
  private String queryType;

  /** The query. */
  private String query;

  /** The action. */
  private String action;

  /**
   * Instantiates an empty {@link QueryActionAlgorithm}.
   * @throws Exception if anything goes wrong
   */
  public QueryActionAlgorithm() throws Exception {
    super();
    setActivityId(UUID.randomUUID().toString());
    setWorkId("QUERYACTION");
    setLastModifiedBy("admin");
  }

  /**
   * Returns the object type.
   *
   * @return the object type
   */
  public String getObjectType() {
    return objectType;
  }

  /**
   * Returns the query type.
   *
   * @return the query type
   */
  public String getQueryType() {
    return queryType;
  }

  /**
   * Returns the query.
   *
   * @return the query
   */
  public String getQuery() {
    return query;
  }

  /**
   * Returns the action.
   *
   * @return the action
   */
  public String getAction() {
    return action;
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
      throw new Exception(
          "Query Action algorithms requires a project to be set");
    }
    if (getQuery() == null) {
      throw new Exception("Query Action algorithms requires a query to be set");
    }
    if (getObjectType() == null) {
      throw new Exception(
          "Query Action algorithms requires an object type to be set");
    }
    if (getAction() == null) {
      throw new Exception(
          "Query Action algorithms requires an action to be set");
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
    logInfo("Starting QUERYACTION: " + objectType + " " + action);

    // No molecular actions will be generated by this algorithm
    setMolecularActionFlag(false);

    // Set up the handler for identifier assignment
    final IdentifierAssignmentHandler handler =
        newIdentifierAssignmentHandler(getProject().getTerminology());
    handler.setTransactionPerOperation(false);
    handler.beginTransaction();

    try {

      previousProgress = 0;
      stepsCompleted = 0;

      logInfo("[QueryAction]: " + objectType + " " + action);

      // Update the progress
      updateProgress();

      logInfo("  project = " + getProject().getId());
      logInfo("  workId = " + getWorkId());
      logInfo("  activityId = " + getActivityId());
      logInfo("  user  = " + getLastModifiedBy());
      logInfo("Finished QUERYACTION: " + objectType + " " + action);

    } catch (

    Exception e) {
      logError("Unexpected problem - " + e.getMessage());
      throw e;
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

  /**
   * Update progress.
   *
   * @throws Exception the exception
   */
  public void updateProgress() throws Exception {
    stepsCompleted++;
    int currentProgress = (int) ((100 * stepsCompleted / steps));
    if (currentProgress > previousProgress) {
      fireProgressEvent(currentProgress,
          "QUERYACTION progress: " + currentProgress + "%");
      previousProgress = currentProgress;
    }
  }

  /**
   * Sets the properties.
   *
   * @param p the properties
   * @throws Exception the exception
   */
  /* see superclass */
  @Override
  public void setProperties(Properties p) throws Exception {
    checkRequiredProperties(new String[] {
        // TODO - handle problem with config.properties needing properties
    }, p);

    objectType = String.valueOf(p.getProperty("objectType"));
    action = String.valueOf(p.getProperty("action"));
    queryType = String.valueOf(p.getProperty("queryType"));
    query = String.valueOf(p.getProperty("query"));

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
    AlgorithmParameter param = new AlgorithmParameterJpa("ObjectType",
        "objectType", "Type of object an action will be performed on",
        "e.g. Concept", 200, AlgorithmParameter.Type.ENUM);
    param.setPossibleValues(Arrays.asList(IdType.CONCEPT.toString(),
        IdType.RELATIONSHIP.toString(), IdType.SEMANTIC_TYPE.toString(),
        IdType.ATTRIBUTE.toString(), IdType.ATOM.toString()));
    params.add(param);

    param = new AlgorithmParameterJpa("Action", "action",
        "An action to perform on the specified object type", "e.g. Approve",
        200, AlgorithmParameter.Type.ENUM);
    param.setPossibleValues(
        Arrays.asList("Remove", "Make unpublishable", "Approve"));
    params.add(param);

    param = new AlgorithmParameterJpa("QueryType", "queryType",
        "The language the query is written in", "e.g. JQL", 200,
        AlgorithmParameter.Type.ENUM);
    param.setPossibleValues(EnumSet.allOf(QueryType.class).stream()
        .map(e -> e.toString()).collect(Collectors.toList()));
    params.add(param);

    param = new AlgorithmParameterJpa("Query", "query",
        "A query to perform action only on objects that meet the criteria",
        "e.g. query in format of the query type", 4000,
        AlgorithmParameter.Type.TEXT);
    params.add(param);

    return params;
  }

}