package com.sentia.ai.app.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/** The Anthropic Messages format: request body (with tools) and stream reading. No network code. */
public final class AnthropicProtocol {
    private AnthropicProtocol() {}

    public static JsonObject body(ChatRequest r) {
        JsonObject body = new JsonObject();
        body.addProperty("model", r.modelId);
        body.addProperty("max_tokens", r.maxTokens > 0 ? r.maxTokens : 8192);
        if (r.temperature != null) body.addProperty("temperature", Math.min(1f, r.temperature));
        body.addProperty("stream", true);
        if (r.systemPrompt != null && !r.systemPrompt.trim().isEmpty()) {
            body.addProperty("system", r.systemPrompt);
        }

        JsonArray msgs = new JsonArray();
        JsonObject cur = null;
        String curRole = null;
        for (ChatMsg m : r.messages) {
            String role;
            JsonArray blocks = new JsonArray();
            if ("tool".equals(m.role)) {
                role = "user";
                JsonObject b = new JsonObject();
                b.addProperty("type", "tool_result");
                b.addProperty("tool_use_id", m.toolCallId);
                b.addProperty("content", m.text == null || m.text.isEmpty() ? "(no output)" : m.text);
                if (m.toolError) b.addProperty("is_error", true);
                blocks.add(b);
            } else if ("assistant".equals(m.role)) {
                role = "assistant";
                boolean hasCalls = m.toolCalls != null && !m.toolCalls.isEmpty();
                String text = m.text == null ? "" : m.text;
                if (!text.trim().isEmpty() || !hasCalls) {
                    JsonObject t = new JsonObject();
                    t.addProperty("type", "text");
                    t.addProperty("text", text.trim().isEmpty() ? " " : text);
                    blocks.add(t);
                }
                if (hasCalls) {
                    for (ToolCall c : m.toolCalls) {
                        JsonObject b = new JsonObject();
                        b.addProperty("type", "tool_use");
                        b.addProperty("id", c.id);
                        b.addProperty("name", c.name);
                        b.add("input", inputObject(c.argsJson));
                        blocks.add(b);
                    }
                }
            } else if ("user".equals(m.role)) {
                role = "user";
                for (ChatMsg.ImagePart ip : m.images) {
                    JsonObject b = new JsonObject();
                    b.addProperty("type", "image");
                    JsonObject src = new JsonObject();
                    src.addProperty("type", "base64");
                    src.addProperty("media_type", ip.mime);
                    src.addProperty("data", ip.b64);
                    b.add("source", src);
                    blocks.add(b);
                }
                JsonObject t = new JsonObject();
                t.addProperty("type", "text");
                t.addProperty("text", m.text == null || m.text.isEmpty() ? " " : m.text);
                blocks.add(t);
            } else {
                continue;
            }
            if (cur != null && role.equals(curRole)) {
                // two turns of the same role in a row are sent as one turn
                JsonArray existing = cur.getAsJsonArray("content");
                for (JsonElement e : blocks) existing.add(e);
            } else {
                cur = new JsonObject();
                cur.addProperty("role", role);
                cur.add("content", blocks);
                curRole = role;
                msgs.add(cur);
            }
        }
        body.add("messages", msgs);

        if (r.tools != null && !r.tools.isEmpty()) {
            JsonArray tools = new JsonArray();
            for (ToolSpec t : r.tools) {
                JsonObject o = new JsonObject();
                o.addProperty("name", t.name);
                o.addProperty("description", t.description);
                o.add("input_schema", OpenAiProtocol.schema(t.schemaJson));
                tools.add(o);
            }
            body.add("tools", tools);
        }
        return body;
    }

    /** The arguments must be a JSON object for Anthropic. Bad text becomes an empty object. */
    static JsonObject inputObject(String argsJson) {
        try {
            JsonElement e = JsonParser.parseString(argsJson == null || argsJson.trim().isEmpty() ? "{}" : argsJson);
            if (e.isJsonObject()) return e.getAsJsonObject();
        } catch (RuntimeException ignored) {
            // use the empty object below
        }
        return new JsonObject();
    }

    /** Reads stream events. Tool calls are sent as soon as their block is complete. */
    public static final class Parser {
        public static final int CONTINUE = 0;
        public static final int STOP = 1;
        public static final int ERROR = 2;

        private static final class Acc {
            String id = "";
            String name = "";
            final StringBuilder json = new StringBuilder();
        }

        private final StreamListener l;
        private final Map<Integer, Acc> blocks = new HashMap<>();
        public int inTokens;
        public int outTokens;
        public String error;

        public Parser(StreamListener l) {
            this.l = l;
        }

        public int feed(String eventType, String data) {
            try {
                JsonObject j = JsonParser.parseString(data.trim()).getAsJsonObject();
                String t = j.has("type") ? j.get("type").getAsString() : (eventType == null ? "" : eventType);
                switch (t) {
                    case "message_start":
                        if (j.has("message") && j.getAsJsonObject("message").has("usage")) {
                            JsonObject u = j.getAsJsonObject("message").getAsJsonObject("usage");
                            if (u.has("input_tokens") && !u.get("input_tokens").isJsonNull()) {
                                inTokens = u.get("input_tokens").getAsInt();
                            }
                        }
                        break;
                    case "content_block_start": {
                        int idx = j.has("index") ? j.get("index").getAsInt() : 0;
                        if (j.has("content_block") && j.get("content_block").isJsonObject()) {
                            JsonObject cb = j.getAsJsonObject("content_block");
                            if (cb.has("type") && "tool_use".equals(cb.get("type").getAsString())) {
                                Acc a = new Acc();
                                a.id = cb.has("id") ? cb.get("id").getAsString() : "";
                                a.name = cb.has("name") ? cb.get("name").getAsString() : "";
                                blocks.put(idx, a);
                            }
                        }
                        break;
                    }
                    case "content_block_delta":
                        if (j.has("delta")) {
                            JsonObject d = j.getAsJsonObject("delta");
                            int idx = j.has("index") ? j.get("index").getAsInt() : 0;
                            if (d.has("text") && !d.get("text").isJsonNull()) {
                                String s = d.get("text").getAsString();
                                if (!s.isEmpty()) l.onDelta(s);
                            }
                            if (d.has("partial_json") && !d.get("partial_json").isJsonNull()) {
                                Acc a = blocks.get(idx);
                                if (a != null) a.json.append(d.get("partial_json").getAsString());
                            }
                        }
                        break;
                    case "content_block_stop": {
                        int idx = j.has("index") ? j.get("index").getAsInt() : 0;
                        Acc a = blocks.remove(idx);
                        if (a != null && !a.name.isEmpty()) {
                            String args = a.json.toString().trim();
                            l.onToolCall(new ToolCall(a.id, a.name, args.isEmpty() ? "{}" : args));
                        }
                        break;
                    }
                    case "message_delta":
                        if (j.has("usage") && j.getAsJsonObject("usage").has("output_tokens")) {
                            outTokens = j.getAsJsonObject("usage").get("output_tokens").getAsInt();
                        }
                        break;
                    case "message_stop":
                        return STOP;
                    case "error": {
                        String m = ApiErrors.fromObject(j);
                        error = m == null ? "Provider returned an error" : m;
                        return ERROR;
                    }
                    default:
                        break;
                }
            } catch (RuntimeException ignored) {
                // a strange event is skipped, the stream goes on
            }
            return CONTINUE;
        }
    }
}
