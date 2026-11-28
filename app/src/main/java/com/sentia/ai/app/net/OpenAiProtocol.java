package com.sentia.ai.app.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The OpenAI chat completions format: how a request body is built (with tools) and how a stream is read.
 * No network code here, so it can be tested without a device.
 */
public final class OpenAiProtocol {
    private OpenAiProtocol() {}

    public static JsonObject body(ChatRequest r) {
        JsonObject body = new JsonObject();
        body.addProperty("model", r.modelId);
        body.addProperty("stream", true);
        JsonObject so = new JsonObject();
        so.addProperty("include_usage", true);
        body.add("stream_options", so);
        if (r.temperature != null) body.addProperty("temperature", r.temperature);
        if (r.maxTokens > 0) body.addProperty("max_tokens", r.maxTokens);

        JsonArray msgs = new JsonArray();
        if (r.systemPrompt != null && !r.systemPrompt.trim().isEmpty()) {
            JsonObject s = new JsonObject();
            s.addProperty("role", "system");
            s.addProperty("content", r.systemPrompt);
            msgs.add(s);
        }
        for (ChatMsg m : r.messages) msgs.add(message(m));
        body.add("messages", msgs);

        if (r.tools != null && !r.tools.isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ToolSpec t : r.tools) {
                JsonObject fn = new JsonObject();
                fn.addProperty("name", t.name);
                fn.addProperty("description", t.description);
                fn.add("parameters", schema(t.schemaJson));
                JsonObject o = new JsonObject();
                o.addProperty("type", "function");
                o.add("function", fn);
                tools.add(o);
            }
            body.add("tools", tools);
        }
        return body;
    }

    static JsonElement schema(String json) {
        try {
            JsonElement e = JsonParser.parseString(json);
            if (e.isJsonObject()) return e;
        } catch (RuntimeException ignored) {
            // fall through to an empty schema
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "object");
        o.add("properties", new JsonObject());
        return o;
    }

    private static JsonObject message(ChatMsg m) {
        JsonObject o = new JsonObject();
        if ("tool".equals(m.role)) {
            o.addProperty("role", "tool");
            o.addProperty("tool_call_id", m.toolCallId);
            o.addProperty("content", m.text == null ? "" : m.text);
            return o;
        }
        o.addProperty("role", m.role);
        if ("assistant".equals(m.role) && m.toolCalls != null && !m.toolCalls.isEmpty()) {
            if (m.text == null || m.text.isEmpty()) o.add("content", JsonNull.INSTANCE);
            else o.addProperty("content", m.text);
            JsonArray calls = new JsonArray();
            for (ToolCall c : m.toolCalls) {
                JsonObject fn = new JsonObject();
                fn.addProperty("name", c.name);
                fn.addProperty("arguments", c.argsJson == null || c.argsJson.isEmpty() ? "{}" : c.argsJson);
                JsonObject co = new JsonObject();
                co.addProperty("id", c.id);
                co.addProperty("type", "function");
                co.add("function", fn);
                calls.add(co);
            }
            o.add("tool_calls", calls);
            return o;
        }
        if (m.images.isEmpty()) {
            o.addProperty("content", m.text);
        } else {
            JsonArray parts = new JsonArray();
            JsonObject t = new JsonObject();
            t.addProperty("type", "text");
            t.addProperty("text", m.text);
            parts.add(t);
            for (ChatMsg.ImagePart ip : m.images) {
                JsonObject im = new JsonObject();
                im.addProperty("type", "image_url");
                JsonObject u = new JsonObject();
                u.addProperty("url", "data:" + ip.mime + ";base64," + ip.b64);
                im.add("image_url", u);
                parts.add(im);
            }
            o.add("content", parts);
        }
        return o;
    }

    /** Reads the JSON of each stream event. Text goes out as it comes, tool calls when the stream ends. */
    public static final class Parser {
        private static final class Acc {
            String id = "";
            String name = "";
            final StringBuilder args = new StringBuilder();
        }

        private final StreamListener l;
        private final List<Acc> accs = new ArrayList<>();
        private final Map<Integer, Acc> byIndex = new HashMap<>();
        private boolean flushed;
        /** Set when an event carried an error. */
        public String error;

        public Parser(StreamListener l) {
            this.l = l;
        }

        /** Returns false when the event is an error (see error). */
        public boolean feed(String data) {
            JsonObject j;
            try {
                JsonElement e = JsonParser.parseString(data);
                if (!e.isJsonObject()) return true;
                j = e.getAsJsonObject();
            } catch (RuntimeException ex) {
                return true;
            }
            try {
                if (j.has("error") && !j.get("error").isJsonNull()) {
                    String m = ApiErrors.fromObject(j);
                    error = m == null ? "Provider returned an error" : m;
                    return false;
                }
                if (j.has("usage") && j.get("usage").isJsonObject()) {
                    JsonObject u = j.getAsJsonObject("usage");
                    int pi = intOf(u, "prompt_tokens");
                    int co = intOf(u, "completion_tokens");
                    if (pi > 0 || co > 0) l.onUsage(pi, co);
                }
                if (j.has("choices") && j.get("choices").isJsonArray()) {
                    JsonArray ch = j.getAsJsonArray("choices");
                    if (ch.size() > 0 && ch.get(0).isJsonObject()) {
                        JsonObject c0 = ch.get(0).getAsJsonObject();
                        if (c0.has("delta") && c0.get("delta").isJsonObject()) {
                            JsonObject delta = c0.getAsJsonObject("delta");
                            if (delta.has("content") && !delta.get("content").isJsonNull()
                                    && delta.get("content").isJsonPrimitive()) {
                                String t = delta.get("content").getAsString();
                                if (!t.isEmpty()) l.onDelta(t);
                            }
                            if (delta.has("tool_calls") && delta.get("tool_calls").isJsonArray()) {
                                for (JsonElement te : delta.getAsJsonArray("tool_calls")) {
                                    if (te.isJsonObject()) toolPiece(te.getAsJsonObject());
                                }
                            }
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // a strange event is skipped, the stream goes on
            }
            return true;
        }

        private static int intOf(JsonObject o, String k) {
            return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : 0;
        }

        private static String strOf(JsonObject o, String k) {
            return o.has(k) && !o.get(k).isJsonNull() && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
        }

        private void toolPiece(JsonObject e) {
            int idx = e.has("index") && !e.get("index").isJsonNull() ? e.get("index").getAsInt() : -1;
            String id = strOf(e, "id");
            Acc acc = null;
            if (!id.isEmpty()) {
                for (Acc a : accs) if (id.equals(a.id)) acc = a;
                if (acc == null) {
                    // a new call. Some providers send index 0 for every call, so the id decides.
                    acc = new Acc();
                    acc.id = id;
                    accs.add(acc);
                }
                if (idx >= 0) byIndex.put(idx, acc);
            } else if (idx >= 0 && byIndex.containsKey(idx)) {
                acc = byIndex.get(idx);
            } else if (idx >= 0) {
                acc = new Acc();
                accs.add(acc);
                byIndex.put(idx, acc);
            } else if (!accs.isEmpty()) {
                acc = accs.get(accs.size() - 1);
            } else {
                acc = new Acc();
                accs.add(acc);
            }
            if (e.has("function") && e.get("function").isJsonObject()) {
                JsonObject fn = e.getAsJsonObject("function");
                String name = strOf(fn, "name");
                if (!name.isEmpty() && acc.name.isEmpty()) acc.name = name;
                String args = strOf(fn, "arguments");
                if (!args.isEmpty()) acc.args.append(args);
            }
        }

        /** Sends the finished tool calls. Safe to call more than once. */
        public void flush() {
            if (flushed) return;
            flushed = true;
            Set<String> used = new HashSet<>();
            int n = 0;
            for (Acc a : accs) {
                if (a.name.isEmpty()) continue;
                n++;
                String id = a.id;
                if (id.isEmpty() || used.contains(id)) id = "call_" + n + "_" + Integer.toHexString(a.args.toString().hashCode());
                used.add(id);
                String args = a.args.toString().trim();
                l.onToolCall(new ToolCall(id, a.name, args.isEmpty() ? "{}" : args));
            }
        }
    }
}
