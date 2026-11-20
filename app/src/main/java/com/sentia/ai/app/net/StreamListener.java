package com.sentia.ai.app.net;

/** Callbacks can arrive on any thread. */
public interface StreamListener {
    void onDelta(String text);

    void onUsage(int promptTokens, int completionTokens);

    void onDone();

    void onError(String message);
}
