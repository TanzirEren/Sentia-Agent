package com.sentia.ai.app.net;

import com.sentia.ai.app.model.Provider;

import java.util.ArrayList;
import java.util.List;

public class ChatRequest {
    public Provider provider;
    public String modelId;
    public String systemPrompt;
    public String apiKey = "";
    /** Which of the provider's keys is in use (for automatic key switching). */
    public int keyIndex = 0;
    /** null = let the model decide */
    public Float temperature;
    /** 0 = let the model decide */
    public int maxTokens = 0;
    public List<ChatMsg> messages = new ArrayList<>();
    /** Tools the model may call. Empty = plain chat. */
    public List<ToolSpec> tools = new ArrayList<>();
}
