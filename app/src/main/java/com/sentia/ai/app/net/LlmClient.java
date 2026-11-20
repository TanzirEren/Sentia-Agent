package com.sentia.ai.app.net;

public interface LlmClient {
    Cancelable stream(ChatRequest request, StreamListener listener);
}
