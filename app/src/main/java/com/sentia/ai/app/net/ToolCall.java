package com.sentia.ai.app.net;

/** One tool call the model asked for. argsJson is the arguments exactly as the model wrote them. */
public final class ToolCall {
    public String id;
    public String name;
    public String argsJson;

    public ToolCall() {}

    public ToolCall(String id, String name, String argsJson) {
        this.id = id;
        this.name = name;
        this.argsJson = argsJson;
    }
}
