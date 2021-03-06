/*
 *    Copyright 2016 West Coast Informatics, LLC
 */
package com.wci.umls.server.jpa.services.validation;

import java.util.List;
import java.util.Properties;

import com.wci.umls.server.ValidationResult;
import com.wci.umls.server.jpa.ValidationResultJpa;
import com.wci.umls.server.model.content.Atom;
import com.wci.umls.server.model.content.Concept;

/**
 * Validates those {@link Concept}s that contain at least one publishable
 * {@link Atom} merged by the merge engine, indicated by being last modified by
 * ENG-.
 */
public class DT_I2 extends AbstractValidationCheck {

  /* see superclass */
  @Override
  public void setProperties(Properties p) {
    // n/a
  }

  /* see superclass */
  @Override
  public ValidationResult validate(Concept source) {
    ValidationResult result = new ValidationResultJpa();

    if (source==null){
      return result;
    }
    
    //
    // Get all atoms
    //
    boolean violation = false;
    List<Atom> atoms = source.getAtoms();

    //
    // Find one last modified by "ENG-"
    //
    for (Atom atom : atoms) {
      if (atom.getLastModifiedBy().startsWith("ENG-") && atom.isPublishable()) {
        violation = true;
        break;
      }
    }

    if (violation) {
      result.getErrors().add(getName()
          + ": Concept contains at least one atom merged by the merge engine.");
      return result;
    }

    return result;
  }

  /* see superclass */
  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

}
