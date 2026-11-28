package com.sentia.ai.app.net;

import com.google.gson.JsonObject;

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

        JsonObject body = AnthropicProtocol.body(r);

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
        final AnthropicProtocol.Parser parser = new AnthropicProtocol.Parser(listener);

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

                private void failHttp(int code, String m) {
                    if (done.compareAndSet(false, true)) l.onHttpError(code, m);
                }

                @Override
                public void onEvent(EventSource source, String id, String type, String data) {
                    if (data == null) return;
                    int res = parser.feed(type, data);
                    inTokens.set(parser.inTokens);
                    outTokens.set(parser.outTokens);
                    if (res == AnthropicProtocol.Parser.STOP) finish();
                    else if (res == AnthropicProtocol.Parser.ERROR) fail(parser.error);
                }

                @Override
                public void onClosed(EventSource source) {
                    finish();
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
                        msg = resp.code() == 200
                                ? (detail.isEmpty() ? "The server did not return a stream" : detail)
                                : "HTTP " + resp.code() + (detail.isEmpty() ? "" : ": " + detail);
                    } else if (t != null) {
                        msg = "Network error: " + (t.getMessage() == null ? t.toString() : t.getMessage());
                    } else {
                        msg = "Connection closed";
                    }
                    if (httpCode != 0 && httpCode != 200) failHttp(httpCode, msg);
                    else fail(msg);
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
