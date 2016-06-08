/*
 * Copyright 2016 West Coast Informatics, LLC
 */
package com.wci.umls.server.jpa.helpers.content;

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.wci.umls.server.helpers.AbstractResultList;
import com.wci.umls.server.helpers.content.MappingList;
import com.wci.umls.server.jpa.content.MappingJpa;
import com.wci.umls.server.model.content.WorkflowEpoch;

/**
 * JAXB enabled implementation of {@link MappingList}.
 */
@XmlRootElement(name = "mappingList")
public class MappingListJpa extends AbstractResultList<WorkflowEpoch> implements
    MappingList {

  /* see superclass */
  @Override
  @XmlElement(type = MappingJpa.class, name = "mapping")
  public List<WorkflowEpoch> getObjects() {
    return super.getObjectsTransient();
  }

}
