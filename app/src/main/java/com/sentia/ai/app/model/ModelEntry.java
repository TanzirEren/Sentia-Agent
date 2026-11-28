package com.sentia.ai.app.model;

public class ModelEntry {
    public String id;
    public boolean enabled = true;
    public boolean free = false;
    /** False when this model should not be sent tools (it does not support them). Old saved data keeps true. */
    public boolean tools = true;

    public ModelEntry() {}

    public ModelEntry(String id) {
        this.id = id;
    }
}
