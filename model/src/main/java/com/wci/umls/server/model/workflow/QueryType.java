/**
 * Copyright 2016 West Coast Informatics, LLC
 */
package com.wci.umls.server.model.workflow;

/**
 * Enumeration of query type values.
 */
public enum QueryType {

  /** The jql type. */
  JQL,

  /** The sql type. */
  SQL,

  /** The lucene type. */
  LUCENE,

  /**
   * TODO: allow programmatic queries, they return List<Object[]> from some
   * interface (need a way to inject parameters)
   */
  PROGRAM;

}