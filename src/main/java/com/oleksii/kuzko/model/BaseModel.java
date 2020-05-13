package com.oleksii.kuzko.model;

public class BaseModel {

    protected final String id;


    BaseModel(String id) {
        this.id = id;
    }


    public String getId() {
        return id;
    }
}
