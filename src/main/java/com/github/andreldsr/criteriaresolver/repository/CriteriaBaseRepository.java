package com.github.andreldsr.criteriaresolver.repository;
import com.github.andreldsr.criteriaresolver.annotation.CriteriaField;
import com.github.andreldsr.criteriaresolver.annotation.ProjectionField;
import com.github.andreldsr.criteriaresolver.searchobject.SearchObject;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;

public abstract class CriteriaBaseRepository<T> {
    EntityManager em;

    private Class<T> c;
    private CriteriaBuilder criteriaBuilder;
    private CriteriaQuery<?> genericCriteria;
    private CriteriaQuery<T> criteria;
    private Root<T> root;
    private Map<String, Join> joinMap;

    @SuppressWarnings("unchecked")
    public CriteriaBaseRepository(EntityManager entityManager){
        ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();
        this.c = (Class<T>) genericSuperclass.getActualTypeArguments()[0];
        this.em = entityManager;
        joinMap = new HashMap<>();
    }

    public List<T> getResultList(SearchObject searchObject){
        return this.getQuery(searchObject).getResultList();
    }

    public T getSingleResult(SearchObject searchObject) {
        return this.getQuery(searchObject).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    public Query getGenericQuery(SearchObject searchObject, Class clazz){
        criteriaBuilder = em.getCriteriaBuilder();
        genericCriteria = criteriaBuilder.createQuery(clazz);
        root = genericCriteria.from(c);
        createJoins(searchObject);
        setProjections(clazz);
        List<Predicate> predicates = getPredicates(root, searchObject);
        genericCriteria.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(genericCriteria);
    }

    public TypedQuery<T> getQuery(SearchObject searchObject){
        criteriaBuilder = em.getCriteriaBuilder();
        criteria = criteriaBuilder.createQuery(c);
        root = criteria.from(c);
        createJoins(searchObject);
        List<Predicate> predicates = getPredicates(root, searchObject);
        criteria.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(criteria);
    }

    private void setProjections(Class clazz) {
        List<String> projections = new ArrayList();
        for(Field field: clazz.getDeclaredFields()) {
            projections.add(getProjection(field));
        }
        List<Selection> selectionList = new ArrayList<>();
        Selection[] selectionArray;
        if(projections.size() == 0)
            return;
        for (String projection : projections) {
            selectionList.add(getPath(projection));
        }
        selectionArray = new Selection[selectionList.size()];
        genericCriteria.multiselect((Selection<?>[]) selectionList.toArray(selectionArray));
    }

    private String getProjection(Field field) {
        field.setAccessible(true);
        String projectionPath = field.getName();
        ProjectionField[] annotationsByType = field.getAnnotationsByType(ProjectionField.class);
        if(annotationsByType != null && annotationsByType.length == 1) {
            projectionPath = annotationsByType[0].projectionPath();
        }
        return projectionPath;
    }

    private void createJoins(SearchObject searchObject) {
        Map<String, JoinType> joins = searchObject.getJoins();
        Set<String> keySet = joins.keySet();
        for (String key : keySet) {
            joinMap.put(key, root.join(key, joins.get(key)));
        }
    }

    private List<Predicate> getPredicates(Root<T> root, SearchObject searchObject) {
        List<Predicate> predicates = new ArrayList<>();
        for(Field field: searchObject.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName;
            CriteriaField.ComparationType comparationType;
            CriteriaField[] annotationsByType = field.getAnnotationsByType(CriteriaField.class);
            if(annotationsByType.length == 1) {
                try {
                    Object value = field.get(searchObject);
                    if(value == null)
                        continue;
                    CriteriaField criteriaField = annotationsByType[0];
                    if(criteriaField.fieldName().equals("")) {
                        fieldName = field.getName();
                    }else {
                        fieldName = criteriaField.fieldName();
                    }
                    comparationType = criteriaField.comparationType();
                    predicates.add(getPredicate(fieldName, comparationType, value));
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return predicates;
    }

    private Path getPath(String attributeName) {
        Path path = root;
        for (String part : attributeName.split("\\.")) {
            path = path.get(part);
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private Predicate getPredicate(String fieldName, CriteriaField.ComparationType comparationType, Object value) {
        Predicate predicate = null;
        switch(comparationType) {
            case LIKE:
                predicate = criteriaBuilder.like(getPath(fieldName), "%" +value + "%");
                break;
            case STARTS_WITH:
                predicate = criteriaBuilder.like(getPath(fieldName), value + "%");
                break;
            case ENDS_WITH:
                predicate = criteriaBuilder.like(getPath(fieldName), "%" +value);
                break;
            case GREATER_THAN:
                predicate = criteriaBuilder.greaterThan(getPath(fieldName), (Comparable) value);
                break;
            case GREATER_EQUALS:
                predicate = criteriaBuilder.greaterThanOrEqualTo(getPath(fieldName), (Comparable) value);
                break;
            case LESS_THAN:
                predicate = criteriaBuilder.lessThan(getPath(fieldName), (Comparable) value);
                break;
            case LESS_EQUALS:
                predicate = criteriaBuilder.lessThanOrEqualTo(getPath(fieldName), (Comparable) value);
                break;
            case IN:
                predicate = criteriaBuilder.in(getPath(fieldName)).value(value);
                break;
            case NOT_IN:
                predicate = criteriaBuilder.not(getPath(fieldName)).in(value);
                break;
            case DIFFERENT:
                predicate = criteriaBuilder.notEqual(getPath(fieldName), value);
                break;
            default:
                predicate = criteriaBuilder.equal(getPath(fieldName), value);
                break;
        }
        return predicate;
    }
}

