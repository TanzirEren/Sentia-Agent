package com.sentia.ai.app.net;

import com.google.gson.JsonObject;

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

        JsonObject body = OpenAiProtocol.body(r);

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
        final OpenAiProtocol.Parser parser = new OpenAiProtocol.Parser(listener);
        EventSource es;
        try {
            es = EventSources.createFactory(Http.CLIENT).newEventSource(rb.build(), new EventSourceListener() {
                @Override
                public void onEvent(EventSource source, String id, String type, String data) {
                    if (data == null) return;
                    String d = data.trim();
                    if (d.equals("[DONE]")) {
                        parser.flush();
                        g.finish();
                        return;
                    }
                    if (!parser.feed(d)) g.fail(parser.error);
                }

                @Override
                public void onClosed(EventSource source) {
                    parser.flush();
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
