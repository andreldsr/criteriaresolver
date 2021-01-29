package com.github.andreldsr.criteriaresolver.annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface CriteriaField {
    String fieldName() default "";
    ComparationType comparationType() default ComparationType.EQUALS;

    enum ComparationType {
        EQUALS, LIKE, GREATER_THAN, LESS_THAN, GREATER_EQUALS, LESS_EQUALS, IN, NOT_IN, DIFFERENT, STARTS_WITH, ENDS_WITH
    }
}
