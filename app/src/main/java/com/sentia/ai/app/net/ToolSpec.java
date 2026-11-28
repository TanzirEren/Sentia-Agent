package com.sentia.ai.app.net;

/** A tool as the model sees it: name, description and a JSON Schema (as a JSON object string). */
public final class ToolSpec {
    public final String name;
    public final String description;
    public final String schemaJson;

    public ToolSpec(String name, String description, String schemaJson) {
        this.name = name;
        this.description = description;
        this.schemaJson = schemaJson;
    }
}
