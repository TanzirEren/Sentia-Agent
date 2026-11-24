package com.sentia.ai.app.tools;

/** What a tool returns: plain text for the model, and whether it failed. */
public final class ToolResult {
    public final boolean error;
    public final String text;
    public final boolean truncated;

    private ToolResult(boolean error, String text, boolean truncated) {
        this.error = error;
        this.text = text == null ? "" : text;
        this.truncated = truncated;
    }

    public static ToolResult ok(String text) {
        return new ToolResult(false, text, false);
    }

    public static ToolResult ok(String text, boolean truncated) {
        return new ToolResult(false, text, truncated);
    }

    public static ToolResult error(String text) {
        return new ToolResult(true, text, false);
    }

    /** Cuts long text and adds a marker the model can understand. */
    public static String cut(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "\n[truncated: " + (text.length() - max) + " more characters]";
    }
}
