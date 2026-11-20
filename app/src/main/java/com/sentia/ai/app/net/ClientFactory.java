package com.sentia.ai.app.net;

import com.sentia.ai.app.model.Provider;

public final class ClientFactory {
    private ClientFactory() {}

    public static LlmClient forProvider(Provider p) {
        if (Provider.TYPE_ANTHROPIC.equals(p.type)) return new AnthropicClient();
        return new OpenAiClient();
    }
}
