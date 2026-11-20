package com.sentia.ai.app.model;

import java.util.ArrayList;
import java.util.List;
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

    public String firstKey() {
        if (apiKeys == null || apiKeys.isEmpty()) return "";
        String k = apiKeys.get(0);
        return k == null ? "" : k.trim();
    }
}
