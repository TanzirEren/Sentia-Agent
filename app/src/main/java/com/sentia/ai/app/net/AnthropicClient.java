package com.sentia.ai.app.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

/** Streams from the Anthropic Messages API. */
public class AnthropicClient implements LlmClient {

    @Override
    public Cancelable stream(ChatRequest r, StreamListener listener) {
        String base = r.provider.baseUrl == null ? "" : r.provider.baseUrl.trim();
        if (base.isEmpty()) base = "https://api.anthropic.com/v1";
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        JsonObject body = new JsonObject();
        body.addProperty("model", r.modelId);
        body.addProperty("max_tokens", 8192);
        body.addProperty("stream", true);
        if (r.systemPrompt != null && !r.systemPrompt.trim().isEmpty()) {
            body.addProperty("system", r.systemPrompt);
        }
        JsonArray msgs = new JsonArray();
        for (ChatMsg m : r.messages) {
            if (!"user".equals(m.role) && !"assistant".equals(m.role)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
            JsonArray blocks = new JsonArray();
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
            o.add("content", blocks);
            msgs.add(o);
        }
        body.add("messages", msgs);

        Request req = new Request.Builder()
                .url(base + "/messages")
                .header("x-api-key", r.apiKey == null ? "" : r.apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                .build();

        final StreamListener l = listener;
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicInteger inTokens = new AtomicInteger(0);
        final AtomicInteger outTokens = new AtomicInteger(0);

        EventSource es;
        try {
            es = EventSources.createFactory(Http.CLIENT).newEventSource(req, new EventSourceListener() {
                private void finish() {
                    if (done.compareAndSet(false, true)) {
                        if (inTokens.get() > 0 || outTokens.get() > 0) l.onUsage(inTokens.get(), outTokens.get());
                        l.onDone();
                    }
                }

                private void fail(String m) {
                    if (done.compareAndSet(false, true)) l.onError(m);
                }

                @Override
                public void onEvent(EventSource source, String id, String type, String data) {
                    if (data == null) return;
                    try {
                        JsonObject j = JsonParser.parseString(data.trim()).getAsJsonObject();
                        String t = j.has("type") ? j.get("type").getAsString() : (type == null ? "" : type);
                        switch (t) {
                            case "message_start":
                                if (j.has("message") && j.getAsJsonObject("message").has("usage")) {
                                    JsonObject u = j.getAsJsonObject("message").getAsJsonObject("usage");
                                    if (u.has("input_tokens")) inTokens.set(u.get("input_tokens").getAsInt());
                                }
                                break;
                            case "content_block_delta":
                                if (j.has("delta")) {
                                    JsonObject d = j.getAsJsonObject("delta");
                                    if (d.has("text") && !d.get("text").isJsonNull()) {
                                        String s = d.get("text").getAsString();
                                        if (!s.isEmpty()) l.onDelta(s);
                                    }
                                }
                                break;
                            case "message_delta":
                                if (j.has("usage") && j.getAsJsonObject("usage").has("output_tokens")) {
                                    outTokens.set(j.getAsJsonObject("usage").get("output_tokens").getAsInt());
                                }
                                break;
                            case "message_stop":
                                finish();
                                break;
                            case "error": {
                                String m = ApiErrors.fromObject(j);
                                fail(m == null ? "Provider returned an error" : m);
                                break;
                            }
                            default:
                                break;
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onClosed(EventSource source) {
                    finish();
                }

                @Override
                public void onFailure(EventSource source, Throwable t, Response resp) {
                    String msg;
                    if (resp != null) {
                        String b = "";
                        try {
                            if (resp.body() != null) b = resp.body().string();
                        } catch (Exception ignored) {
                        }
                        String detail = ApiErrors.extract(b);
                        msg = resp.code() == 200
                                ? (detail.isEmpty() ? "The server did not return a stream" : detail)
                                : "HTTP " + resp.code() + (detail.isEmpty() ? "" : ": " + detail);
                    } else if (t != null) {
                        msg = "Network error: " + (t.getMessage() == null ? t.toString() : t.getMessage());
                    } else {
                        msg = "Connection closed";
                    }
                    fail(msg);
                }
            });
        } catch (Exception e) {
            l.onError("Invalid request: " + e.getMessage());
            return () -> { };
        }
        final EventSource fes = es;
        return fes::cancel;
    }
}
