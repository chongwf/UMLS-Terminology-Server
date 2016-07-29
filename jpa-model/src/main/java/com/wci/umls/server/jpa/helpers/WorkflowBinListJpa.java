/**
 * Copyright 2015 West Coast Informatics, LLC
 */
package com.wci.umls.server.jpa.helpers;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wci.umls.server.helpers.AbstractResultList;
import com.wci.umls.server.helpers.WorkflowBinList;
import com.wci.umls.server.jpa.worfklow.WorkflowBinJpa;
import com.wci.umls.server.model.workflow.WorkflowBin;

/**
 * JAXB enabled implementation of {@link WorkflowBinList}.
 */
@XmlRootElement(name = "worklistList")
public class WorkflowBinListJpa extends AbstractResultList<WorkflowBin>
    implements WorkflowBinList {

  /* see superclass */
  @Override
  @XmlElement(type = WorkflowBinJpa.class, name = "worklists")
  public List<WorkflowBin> getObjects() {
    return super.getObjectsTransient();
  }

}