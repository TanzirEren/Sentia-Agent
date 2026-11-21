package com.sentia.ai.app.model;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Provider {
    public static final String TYPE_OPENAI = "openai";
    public static final String TYPE_ANTHROPIC = "anthropic";

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String type = TYPE_OPENAI;
    public String baseUrl = "";
    public List<String> apiKeys = new ArrayList<>();
    public List<ModelEntry> models = new ArrayList<>();
    public boolean enabled = true;
    /** Index of the key used first. Moves forward when a key runs out. */
    public int activeKey = 0;
    /** Key index (as text) to the time in millis until which that key is skipped. */
    public Map<String, Long> keyBlockedUntil = new HashMap<>();

    public String firstKey() {
        if (apiKeys == null || apiKeys.isEmpty()) return "";
        String k = apiKeys.get(0);
        return k == null ? "" : k.trim();
    }

    public int enabledModelCount() {
        int n = 0;
        if (models != null) for (ModelEntry m : models) if (m.enabled) n++;
        return n;
    }

    public Provider copy() {
        Gson g = new Gson();
        return g.fromJson(g.toJson(this), Provider.class);
    }
}
