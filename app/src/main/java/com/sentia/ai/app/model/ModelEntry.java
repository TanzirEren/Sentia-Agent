package com.sentia.ai.app.model;

public class ModelEntry {
    public String id;
    public boolean enabled = true;
    public boolean free = false;

    public ModelEntry() {}

    public ModelEntry(String id) {
        this.id = id;
    }
}
