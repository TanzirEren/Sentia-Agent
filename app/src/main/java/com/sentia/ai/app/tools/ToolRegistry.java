package com.sentia.ai.app.tools;

import com.sentia.ai.app.tools.file.ListDirTool;
import com.sentia.ai.app.tools.file.ReadTool;
import com.sentia.ai.app.workspace.WsException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All tools by name. Phase 2B reads this to tell the model what it can call. */
public final class ToolRegistry {
    private static ToolRegistry instance;

    public static synchronized ToolRegistry get() {
        if (instance == null) {
            instance = new ToolRegistry();
            instance.register(new ReadTool());
            instance.register(new ListDirTool());
        }
        return instance;
    }

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    private ToolRegistry() {}

    public synchronized void register(Tool t) {
        tools.put(t.name(), t);
    }

    public synchronized Tool find(String name) {
        return tools.get(name);
    }

    public synchronized List<Tool> all() {
        return new ArrayList<>(tools.values());
    }

    /** Tool list in one neutral format: [{name, description, parameters}]. Provider adapters convert it. */
    public synchronized JSONArray describeAll() {
        JSONArray arr = new JSONArray();
        for (Tool t : tools.values()) {
            try {
                JSONObject o = new JSONObject();
                o.put("name", t.name());
                o.put("description", t.description());
                o.put("parameters", new JSONObject(t.jsonSchema()));
                arr.put(o);
            } catch (JSONException e) {
                // a broken schema must not break the others
            }
        }
        return arr;
    }

    /** Runs a tool. Any failure comes back as an error result, so the model can retry. */
    public ToolResult execute(String name, JSONObject input, ToolContext ctx) {
        Tool t = find(name);
        if (t == null) return ToolResult.error("Unknown tool: " + name);
        try {
            ToolResult r = t.execute(input == null ? new JSONObject() : input, ctx);
            return r == null ? ToolResult.error("Tool returned nothing") : r;
        } catch (WsException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            String m = e.getMessage();
            return ToolResult.error("Tool failed: " + (m == null ? e.getClass().getSimpleName() : m));
        }
    }
}
