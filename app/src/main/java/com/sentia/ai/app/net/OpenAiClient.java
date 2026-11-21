package com.sentia.ai.app.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

/** Streams from any OpenAI-compatible /chat/completions endpoint. */
public class OpenAiClient implements LlmClient {

    private static final class Guard {
        final StreamListener l;
        final AtomicBoolean done = new AtomicBoolean(false);

        Guard(StreamListener l) { this.l = l; }

        void finish() { if (done.compareAndSet(false, true)) l.onDone(); }

        void fail(String m) { if (done.compareAndSet(false, true)) l.onError(m); }

        void failHttp(int code, String m) { if (done.compareAndSet(false, true)) l.onHttpError(code, m); }
    }

    @Override
    public Cancelable stream(ChatRequest r, StreamListener listener) {
        String base = r.provider.baseUrl == null ? "" : r.provider.baseUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

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
        for (ChatMsg m : r.messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role);
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
            msgs.add(o);
        }
        body.add("messages", msgs);

        Request.Builder rb = new Request.Builder()
                .url(base + "/chat/completions")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(body.toString(), MediaType.parse("application/json")));
        if (r.apiKey != null && !r.apiKey.isEmpty()) {
            rb.header("Authorization", "Bearer " + r.apiKey);
        }
        if (base.contains("openrouter.ai")) {
            rb.header("HTTP-Referer", "https://sentia-agent.vercel.app");
            rb.header("X-Title", "Sentia");
        }

        final Guard g = new Guard(listener);
        EventSource es;
        try {
            es = EventSources.createFactory(Http.CLIENT).newEventSource(rb.build(), new EventSourceListener() {
                @Override
                public void onEvent(EventSource source, String id, String type, String data) {
                    if (data == null) return;
                    String d = data.trim();
                    if (d.equals("[DONE]")) {
                        g.finish();
                        return;
                    }
                    try {
                        JsonObject j = JsonParser.parseString(d).getAsJsonObject();
                        if (j.has("error") && !j.get("error").isJsonNull()) {
                            String m = ApiErrors.fromObject(j);
                            g.fail(m == null ? "Provider returned an error" : m);
                            return;
                        }
                        if (j.has("usage") && j.get("usage").isJsonObject()) {
                            JsonObject u = j.getAsJsonObject("usage");
                            int pi = u.has("prompt_tokens") && !u.get("prompt_tokens").isJsonNull() ? u.get("prompt_tokens").getAsInt() : 0;
                            int co = u.has("completion_tokens") && !u.get("completion_tokens").isJsonNull() ? u.get("completion_tokens").getAsInt() : 0;
                            if (pi > 0 || co > 0) g.l.onUsage(pi, co);
                        }
                        if (j.has("choices") && j.get("choices").isJsonArray()) {
                            JsonArray ch = j.getAsJsonArray("choices");
                            if (ch.size() > 0 && ch.get(0).isJsonObject()) {
                                JsonObject c0 = ch.get(0).getAsJsonObject();
                                if (c0.has("delta") && c0.get("delta").isJsonObject()) {
                                    JsonObject delta = c0.getAsJsonObject("delta");
                                    if (delta.has("content") && !delta.get("content").isJsonNull()) {
                                        String t = delta.get("content").getAsString();
                                        if (!t.isEmpty()) g.l.onDelta(t);
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onClosed(EventSource source) {
                    g.finish();
                }

                @Override
                public void onFailure(EventSource source, Throwable t, Response resp) {
                    String msg;
                    int httpCode = 0;
                    if (resp != null) {
                        httpCode = resp.code();
                        String b = "";
                        try {
                            if (resp.body() != null) b = resp.body().string();
                        } catch (Exception ignored) {
                        }
                        String detail = ApiErrors.extract(b);
                        if (resp.code() == 200) {
                            msg = detail.isEmpty() ? "The server did not return a stream" : detail;
                        } else {
                            msg = "HTTP " + resp.code() + (detail.isEmpty() ? "" : ": " + detail);
                        }
                    } else if (t != null) {
                        msg = "Network error: " + (t.getMessage() == null ? t.toString() : t.getMessage());
                    } else {
                        msg = "Connection closed";
                    }
                    if (httpCode != 0 && httpCode != 200) g.failHttp(httpCode, msg);
                    else g.fail(msg);
                }
            });
        } catch (Exception e) {
            g.fail("Invalid request: " + e.getMessage());
            return () -> { };
        }
        final EventSource fes = es;
        return fes::cancel;
    }
}
