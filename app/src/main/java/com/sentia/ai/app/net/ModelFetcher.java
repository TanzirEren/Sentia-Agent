package com.sentia.ai.app.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sentia.ai.app.model.ModelEntry;
import com.sentia.ai.app.model.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Request;
import okhttp3.Response;

/** Asks a provider for its model list (GET /models). Call from a background thread. */
public final class ModelFetcher {
    public static final class Result {
        public final List<ModelEntry> models;
        public final String error;

        Result(List<ModelEntry> models, String error) {
            this.models = models;
            this.error = error;
        }
    }

    private ModelFetcher() {}

    public static Result fetch(String baseUrl, String type, String apiKey) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (base.isEmpty()) return new Result(null, "Enter the base URL first");

        boolean anthropic = Provider.TYPE_ANTHROPIC.equals(type);
        Request.Builder rb = new Request.Builder().url(base + "/models" + (anthropic ? "?limit=1000" : "")).get();
        if (anthropic) {
            rb.header("x-api-key", apiKey == null ? "" : apiKey);
            rb.header("anthropic-version", "2023-06-01");
        } else if (apiKey != null && !apiKey.isEmpty()) {
            rb.header("Authorization", "Bearer " + apiKey);
        }
        try (Response resp = Http.CLIENT.newCall(rb.build()).execute()) {
            String body = resp.body() == null ? "" : resp.body().string();
            if (!resp.isSuccessful()) {
                String d = ApiErrors.extract(body);
                if (resp.code() == 404) {
                    return new Result(null, "This provider does not list models. Add model IDs by hand.");
                }
                return new Result(null, "HTTP " + resp.code() + (d.isEmpty() ? "" : ": " + d));
            }
            return new Result(parse(body, base), null);
        } catch (Exception e) {
            return new Result(null, "Network error: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    private static List<ModelEntry> parse(String body, String base) {
        List<ModelEntry> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(body);
            JsonArray arr = null;
            if (root.isJsonObject()) {
                JsonObject o = root.getAsJsonObject();
                if (o.has("data") && o.get("data").isJsonArray()) arr = o.getAsJsonArray("data");
                else if (o.has("models") && o.get("models").isJsonArray()) arr = o.getAsJsonArray("models");
            } else if (root.isJsonArray()) {
                arr = root.getAsJsonArray();
            }
            if (arr == null) return out;
            boolean gemini = base.contains("generativelanguage.googleapis.com");
            for (JsonElement el : arr) {
                String id = null;
                boolean free = false;
                if (el.isJsonPrimitive()) {
                    id = el.getAsString();
                } else if (el.isJsonObject()) {
                    JsonObject m = el.getAsJsonObject();
                    if (m.has("id") && !m.get("id").isJsonNull()) id = m.get("id").getAsString();
                    else if (m.has("name") && !m.get("name").isJsonNull()) id = m.get("name").getAsString();
                    free = isFree(m, id);
                }
                if (id == null || id.isEmpty()) continue;
                if (gemini && id.startsWith("models/")) id = id.substring("models/".length());
                ModelEntry me = new ModelEntry(id);
                me.free = free;
                me.enabled = false;
                out.add(me);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, (a, b) -> {
            if (a.free != b.free) return a.free ? -1 : 1;
            return a.id.compareToIgnoreCase(b.id);
        });
        return out;
    }

    private static boolean isFree(JsonObject m, String id) {
        if (id != null && id.endsWith(":free")) return true;
        if (m.has("pricing") && m.get("pricing").isJsonObject()) {
            JsonObject pr = m.getAsJsonObject("pricing");
            if (pr.has("prompt") && pr.has("completion")) {
                return zero(pr.get("prompt")) && zero(pr.get("completion"));
            }
        }
        return false;
    }

    private static boolean zero(JsonElement e) {
        try {
            if (e.isJsonNull()) return false;
            return Double.parseDouble(e.getAsString()) == 0.0;
        } catch (Exception ex) {
            return false;
        }
    }
}
