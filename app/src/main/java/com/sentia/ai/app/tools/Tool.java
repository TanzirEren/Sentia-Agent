package com.sentia.ai.app.tools;

import org.json.JSONObject;

/** One thing the agent can do. Every tool is a small class registered in ToolRegistry. */
public interface Tool {
    /** Name the model uses, for example Read. */
    String name();

    /** Short text that tells the model what the tool is for. */
    String description();

    /** JSON Schema of the parameters, as a JSON object string. */
    String jsonSchema();

    /** True when the user must approve this call first. */
    boolean needsPermission(JSONObject input);

    ToolResult execute(JSONObject input, ToolContext ctx) throws Exception;

    /** A short line for the tool card in the chat, for example the file path. */
    default String summary(JSONObject input) {
        return "";
    }
}
