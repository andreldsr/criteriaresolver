package com.github.andreldsr.criteriaresolver.repository;

import com.github.andreldsr.criteriaresolver.annotation.CriteriaField;
import com.github.andreldsr.criteriaresolver.annotation.ProjectionField;
import com.github.andreldsr.criteriaresolver.searchobject.SearchObject;
import org.hibernate.query.criteria.internal.path.PluralAttributePath;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public abstract class CriteriaResolverBaseRepository<T> {
    EntityManager em;

    private Class<T> c;
    private CriteriaBuilder criteriaBuilder;
    private Root<T> root;
    private Map<String, Join> joinMap;

    @SuppressWarnings("unchecked")
    public CriteriaResolverBaseRepository(EntityManager entityManager){
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

    public <D> TypedQuery<D> getGenericQuery(SearchObject searchObject, Class<D> clazz){
        return buildQuery(searchObject, clazz);
    }

    public TypedQuery<T> getQuery(SearchObject searchObject){
        return buildQuery(searchObject, c);
    }

    private <D> TypedQuery<D> buildQuery(SearchObject searchObject, Class<D> clazz){
        CriteriaQuery<D> criteriaQuery = createQuery(searchObject, clazz);
        return em.createQuery(criteriaQuery);
    }

    private <D> CriteriaQuery<D> createQuery(SearchObject searchObject, Class<D> clazz){
        return createQuery(searchObject, clazz, true);
    }

    private <D> CriteriaQuery<D> createQuery(SearchObject searchObject, Class<D> clazz, boolean createProjections) {
        CriteriaQuery<D> criteriaQuery = initializeQuery(clazz);
        createJoins(searchObject);
        if(createProjections) setProjections(criteriaQuery, clazz);
        List<Predicate> predicates = getPredicates(root, searchObject);
        criteriaQuery.where(predicates.toArray(new Predicate[0]));
        return criteriaQuery;
    }

    private <D> CriteriaQuery<D> initializeQuery(Class<D> clazz) {
        CriteriaQuery<D> criteriaQuery;
        criteriaBuilder = em.getCriteriaBuilder();
        criteriaQuery = criteriaBuilder.createQuery(clazz);
        root = criteriaQuery.from(c);
        return criteriaQuery;
    }

    public Long getCount(SearchObject searchObject){
        CriteriaQuery<Long> query = createQuery(searchObject, Long.class, false);
        query.select(criteriaBuilder.count(root));
        return em.createQuery(query).getSingleResult();
    }

    private void setProjections(CriteriaQuery genericCriteria, Class clazz) {
        List<String> projections = new ArrayList();
        if(c == clazz) return;
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
        genericCriteria.multiselect(selectionList.toArray(selectionArray));
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
            joinMap.get(key).alias(key);
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
            Path newPath = path.get(part);
            path = checkPathCollection(newPath, part);
        }
        return path;
    }

    private Path checkPathCollection(Path newPath, String part) {
        if(newPath instanceof PluralAttributePath){
            newPath = handleJoinPath(part);
        }
        return newPath;
    }

    private Path handleJoinPath(String part) {
        if(!joinMap.containsKey(part)){
            joinMap.put(part, root.join(part));
        }
        return joinMap.get(part);
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
                predicate = criteriaBuilder.in(getPath(fieldName)).value(value).not();
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

