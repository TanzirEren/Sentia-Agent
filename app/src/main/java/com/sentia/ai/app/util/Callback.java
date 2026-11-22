package com.sentia.ai.app.util;

/** Small result callback, always delivered on the main thread. */
public interface Callback<T> {
    void onResult(T value);
}
