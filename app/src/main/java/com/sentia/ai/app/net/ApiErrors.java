package com.sentia.ai.app.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class ApiErrors {
    private ApiErrors() {}

    /** Pulls a readable message out of a provider error body. */
    public static String extract(String body) {
        if (body == null) return "";
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return "";
        try {
            JsonElement root = JsonParser.parseString(trimmed);
            if (root.isJsonObject()) {
                JsonObject o = root.getAsJsonObject();
                String m = fromObject(o);
                if (m != null) return m;
            } else if (root.isJsonArray() && root.getAsJsonArray().size() > 0
                    && root.getAsJsonArray().get(0).isJsonObject()) {
                String m = fromObject(root.getAsJsonArray().get(0).getAsJsonObject());
                if (m != null) return m;
            }
        } catch (Exception ignored) {
        }
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
    }

    public static String fromObject(JsonObject o) {
        if (o.has("error")) {
            JsonElement e = o.get("error");
            if (e.isJsonObject()) {
                JsonObject eo = e.getAsJsonObject();
                if (eo.has("message") && !eo.get("message").isJsonNull()) return eo.get("message").getAsString();
            } else if (e.isJsonPrimitive()) {
                return e.getAsString();
            }
        }
        if (o.has("message") && o.get("message").isJsonPrimitive()) return o.get("message").getAsString();
        if (o.has("detail") && o.get("detail").isJsonPrimitive()) return o.get("detail").getAsString();
        return null;
    }
}
