/**
 * Copyright 2016 West Coast Informatics, LLC
 */
package com.wci.umls.server.jpa.services;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import javax.persistence.NoResultException;

import org.apache.log4j.Logger;

import com.wci.umls.server.Project;
import com.wci.umls.server.User;
import com.wci.umls.server.UserRole;
import com.wci.umls.server.ValidationResult;
import com.wci.umls.server.helpers.ConfigUtility;
import com.wci.umls.server.helpers.KeyValuePair;
import com.wci.umls.server.helpers.KeyValuePairList;
import com.wci.umls.server.helpers.PfsParameter;
import com.wci.umls.server.helpers.ProjectList;
import com.wci.umls.server.helpers.content.ConceptList;
import com.wci.umls.server.jpa.ProjectJpa;
import com.wci.umls.server.jpa.ValidationResultJpa;
import com.wci.umls.server.jpa.helpers.ProjectListJpa;
import com.wci.umls.server.model.content.Atom;
import com.wci.umls.server.model.content.Code;
import com.wci.umls.server.model.content.Concept;
import com.wci.umls.server.model.content.Descriptor;
import com.wci.umls.server.services.ProjectService;
import com.wci.umls.server.services.handlers.ValidationCheck;

/**
 * JPA and JAXB enabled implementation of {@link ProjectService}.
 */
public class ProjectServiceJpa extends RootServiceJpa implements ProjectService {

  /** The config properties. */
  protected static Properties config = null;

  /** The validation handlers. */
  protected static Map<String, ValidationCheck> validationHandlersMap = null;

  static {
    validationHandlersMap = new HashMap<>();
    try {
      if (config == null)
        config = ConfigUtility.getConfigProperties();
      final String key = "validation.service.handler";
      for (final String handlerName : config.getProperty(key).split(",")) {
        if (handlerName.isEmpty())
          continue;
        // Add handlers to map
        final ValidationCheck handlerService =
            ConfigUtility.newStandardHandlerInstanceWithConfiguration(key,
                handlerName, ValidationCheck.class);
        validationHandlersMap.put(handlerName, handlerService);
      }
    } catch (Exception e) {
      e.printStackTrace();
      validationHandlersMap = null;
    }
  }

  /**
   * Instantiates an empty {@link ProjectServiceJpa}.
   *
   * @throws Exception the exception
   */
  public ProjectServiceJpa() throws Exception {
    super();

    if (validationHandlersMap == null) {
      throw new Exception(
          "Validation handlers did not properly initialize, serious error.");
    }
  }

  /* see superclass */
  @Override
  public ConceptList findConceptsInScope(Project project, PfsParameter pfs)
    throws Exception {
    Logger.getLogger(getClass()).info(
        "Project Service - get project scope - " + project);

    return null;
  }

  /* see superclass */
  @Override
  public Project getProject(Long id) {
    Logger.getLogger(getClass()).debug("Project Service - get project " + id);
    final Project project = manager.find(ProjectJpa.class, id);
    handleLazyInit(project);
    return project;
  }

  /* see superclass */
  @Override
  @SuppressWarnings("unchecked")
  public ProjectList getProjects() {
    Logger.getLogger(getClass()).debug("Project Service - get projects");
    javax.persistence.Query query =
        manager.createQuery("select a from ProjectJpa a");
    try {
      final List<Project> projects = query.getResultList();
      final ProjectList projectList = new ProjectListJpa();
      projectList.setObjects(projects);
      for (final Project project : projectList.getObjects()) {
        handleLazyInit(project);
      }
      return projectList;
    } catch (NoResultException e) {
      return null;
    }
  }

  /* see superclass */
  @Override
  public UserRole getUserRoleForProject(String username, Long projectId)
    throws Exception {
    Logger.getLogger(getClass()).debug(
        "Project Service - get user role for project - " + username + ", "
            + projectId);
    final Project project = getProject(projectId);
    if (project == null) {
      throw new Exception("No project found for " + projectId);
    }

    // check admin
    for (final Map.Entry<User, UserRole> entry : project.getUserRoleMap()
        .entrySet()) {
      if (username.equals(entry.getKey().getName())
          && entry.getValue().equals(UserRole.ADMINISTRATOR)) {
        return UserRole.ADMINISTRATOR;
      }
    }

    // check viewer
    for (final Map.Entry<User, UserRole> entry : project.getUserRoleMap()
        .entrySet()) {
      if (username.equals(entry.getKey().getName())
          && entry.getValue().equals(UserRole.VIEWER)) {
        return UserRole.VIEWER;
      }
    }

    // check reviewer
    for (final Map.Entry<User, UserRole> entry : project.getUserRoleMap()
        .entrySet()) {
      if (username.equals(entry.getKey().getName())
          && entry.getValue().equals(UserRole.REVIEWER)) {
        return UserRole.REVIEWER;
      }
    }

    // check user
    for (final Map.Entry<User, UserRole> entry : project.getUserRoleMap()
        .entrySet()) {
      if (username.equals(entry.getKey().getName())
          && entry.getValue().equals(UserRole.USER)) {
        return UserRole.USER;
      }
    }

    // check author
    for (final Map.Entry<User, UserRole> entry : project.getUserRoleMap()
        .entrySet()) {
      if (username.equals(entry.getKey().getName())
          && entry.getValue().equals(UserRole.AUTHOR)) {
        return UserRole.AUTHOR;
      }
    }

    return null;
  }

  /* see superclass */
  @Override
  public Project addProject(Project project) {
    Logger.getLogger(getClass()).debug(
        "Project Service - add project - " + project);
    try {
      // Set last modified date
      project.setLastModified(new Date());

      // add the project
      if (getTransactionPerOperation()) {
        tx = manager.getTransaction();
        tx.begin();
        manager.persist(project);
        tx.commit();
      } else {
        manager.persist(project);
      }
      return project;
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      throw e;
    }
  }

  /* see superclass */
  @Override
  public void updateProject(Project project) {
    Logger.getLogger(getClass()).debug(
        "Project Service - update project - " + project);

    try {
      // Set modification date
      project.setLastModified(new Date());

      // update
      if (getTransactionPerOperation()) {
        tx = manager.getTransaction();
        tx.begin();
        manager.merge(project);
        tx.commit();
      } else {
        manager.merge(project);
      }
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      throw e;
    }
  }

  /* see superclass */
  @Override
  public void removeProject(Long id) {
    Logger.getLogger(getClass())
        .debug("Project Service - remove project " + id);
    try {
      // Get transaction and object
      tx = manager.getTransaction();
      final Project project = manager.find(ProjectJpa.class, id);

      // if project doesn't exist, return
      if (project == null)
        return;

      // Set modification date
      project.setLastModified(new Date());

      // Remove
      if (getTransactionPerOperation()) {
        tx.begin();
        if (manager.contains(project)) {
          manager.remove(project);
        } else {
          manager.remove(manager.merge(project));
        }
        tx.commit();
      } else {
        if (manager.contains(project)) {
          manager.remove(project);
        } else {
          manager.remove(manager.merge(project));
        }
      }
    } catch (Exception e) {
      if (tx.isActive()) {
        tx.rollback();
      }
      throw e;
    }
  }

  /**
   * Handle lazy initialization.
   *
   * @param project the project
   */
  @SuppressWarnings("static-method")
  private void handleLazyInit(Project project) {
    if (project == null) {
      return;
    }
    project.getUserRoleMap().size();
    project.getValidationChecks().size();
    project.getSemanticTypeCategoryMap().size();
    if (project.getPrecedenceList() != null) {
      project.getPrecedenceList().getName();
    }
    project.getValidCategories().size();
  }

  /**
   * Returns the pfs comparator.
   *
   * @param <T> the
   * @param clazz the clazz
   * @param pfs the pfs
   * @return the pfs comparator
   * @throws Exception the exception
   */
  @Override
  protected <T> Comparator<T> getPfsComparator(Class<T> clazz, PfsParameter pfs)
    throws Exception {
    if (pfs != null
        && (pfs.getSortField() != null && !pfs.getSortField().isEmpty())) {
      // check that specified sort field exists on Concept and is
      // a string
      final Field sortField = clazz.getField(pfs.getSortField());

      // allow the field to access the Concept values
      sortField.setAccessible(true);

      if (pfs.isAscending()) {
        // make comparator
        return new Comparator<T>() {
          @Override
          public int compare(T o1, T o2) {
            try {
              // handle dates explicitly
              if (o2 instanceof Date) {
                return ((Date) sortField.get(o1)).compareTo((Date) sortField
                    .get(o2));
              } else {
                // otherwise, sort based on conversion to string
                return (sortField.get(o1).toString()).compareTo(sortField.get(
                    o2).toString());
              }
            } catch (IllegalAccessException e) {
              // on exception, return equality
              return 0;
            }
          }
        };
      } else {
        // make comparator
        return new Comparator<T>() {
          @Override
          public int compare(T o2, T o1) {
            try {
              // handle dates explicitly
              if (o2 instanceof Date) {
                return ((Date) sortField.get(o1)).compareTo((Date) sortField
                    .get(o2));
              } else {
                // otherwise, sort based on conversion to string
                return (sortField.get(o1).toString()).compareTo(sortField.get(
                    o2).toString());
              }
            } catch (IllegalAccessException e) {
              // on exception, return equality
              return 0;
            }
          }
        };
      }

    } else {
      return null;
    }
  }

  /* see superclass */
  @SuppressWarnings("unchecked")
  @Override
  public ProjectList findProjectsForQuery(String query, PfsParameter pfs)
    throws Exception {
    Logger.getLogger(getClass()).info(
        "Project Service - find projects " + "/" + query);

    int[] totalCt = new int[1];
    List<Project> list =
        (List<Project>) getQueryResults(query == null || query.isEmpty()
            ? "id:[* TO *]" : query, ProjectJpa.class, ProjectJpa.class, pfs,
            totalCt);
    final ProjectList result = new ProjectListJpa();
    result.setTotalCount(totalCt[0]);
    result.setObjects(list);
    for (final Project project : result.getObjects()) {
      handleLazyInit(project);
    }
    return result;
  }

  /* see superclass */
  @Override
  public void refreshCaches() throws Exception {
    // n/a
  }

  /* see superclass */
  @Override
  public ValidationResult validateConcept(Project project, Concept concept) {
    final ValidationResult result = new ValidationResultJpa();
    for (final String key : validationHandlersMap.keySet()) {
      if (project.getValidationChecks().contains(key)) {
        result.merge(validationHandlersMap.get(key).validate(concept));
      }
    }
    return result;
  }

  /* see superclass */
  @Override
  public ValidationResult validateAtom(Project project, Atom atom) {
    final ValidationResult result = new ValidationResultJpa();
    for (final String key : validationHandlersMap.keySet()) {
      if (project.getValidationChecks().contains(key)) {
        result.merge(validationHandlersMap.get(key).validate(atom));
      }
    }
    return result;
  }

  /* see superclass */
  @Override
  public ValidationResult validateDescriptor(Project project,
    Descriptor descriptor) {
    final ValidationResult result = new ValidationResultJpa();
    for (final String key : validationHandlersMap.keySet()) {
      if (project.getValidationChecks().contains(key)) {
        result.merge(validationHandlersMap.get(key).validate(descriptor));
      }
    }
    return result;
  }

  /* see superclass */
  @Override
  public ValidationResult validateCode(Project project, Code code) {
    final ValidationResult result = new ValidationResultJpa();
    for (final String key : validationHandlersMap.keySet()) {
      if (project.getValidationChecks().contains(key)) {
        result.merge(validationHandlersMap.get(key).validate(code));
      }
    }
    return result;
  }

  /* see superclass */
  @Override
  public ValidationResult validateMerge(Project project, Concept concept1,
    Concept concept2) {
    final ValidationResult result = new ValidationResultJpa();
    for (final String key : validationHandlersMap.keySet()) {
      if (project.getValidationChecks().contains(key)) {
        result.merge(validationHandlersMap.get(key).validateMerge(concept1,
            concept2));
      }
    }
    return result;
  }

  /* see superclass */
  @Override
  public KeyValuePairList getValidationCheckNames(Project project) {
    final KeyValuePairList keyValueList = new KeyValuePairList();
    for (final Entry<String, ValidationCheck> entry : validationHandlersMap
        .entrySet()) {
      if (project == null
          || project.getValidationChecks().contains(entry.getKey())) {
        final KeyValuePair pair =
            new KeyValuePair(entry.getKey(), entry.getValue().getName());
        keyValueList.addKeyValuePair(pair);
      }
    }
    return keyValueList;
  }

}
