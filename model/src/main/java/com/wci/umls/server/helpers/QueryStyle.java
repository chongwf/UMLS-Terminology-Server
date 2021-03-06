/*
 *    Copyright 2017 West Coast Informatics, LLC
 */
package com.wci.umls.server.helpers;

/**
 * Enumeration used to drive style of execution.
 */
public enum QueryStyle {

  /** The one column. */
  ID,
  /** The two column. */
  ID_PAIR,
  /** The cluster. */
  CLUSTER,
  /** The report. */
  REPORT,
  /** The other. */
  OTHER;
}