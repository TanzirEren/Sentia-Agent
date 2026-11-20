package com.sentia.ai.app.net;

import com.sentia.ai.app.model.Provider;

import java.util.ArrayList;
import java.util.List;

public class ChatRequest {
    public Provider provider;
    public String modelId;
    public String systemPrompt;
    public String apiKey = "";
    public List<ChatMsg> messages = new ArrayList<>();
}
