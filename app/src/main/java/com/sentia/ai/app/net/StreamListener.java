package com.sentia.ai.app.net;

/** Callbacks can arrive on any thread. */
public interface StreamListener {
    void onDelta(String text);

    void onUsage(int promptTokens, int completionTokens);

    void onDone();

    void onError(String message);

    /** Called instead of onError when the server answered with an HTTP error code. */
    default void onHttpError(int code, String message) {
        onError(message);
    }
}
