package com.github.andreldsr.criteriaresolver.searchobject;

import io.swagger.annotations.ApiModelProperty;

import javax.persistence.criteria.JoinType;
import java.util.HashMap;
import java.util.Map;

public abstract class SearchObject {
    @ApiModelProperty(hidden = true)
    private Map<String, JoinType> joins = new HashMap<>();
    public Map<String, JoinType> getJoins() {
        return joins;
    }

    public void createJoins() {

    }

    public SearchObject(){
        this.createJoins();
    }
}