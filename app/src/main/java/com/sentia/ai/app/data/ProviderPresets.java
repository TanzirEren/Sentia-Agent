package com.sentia.ai.app.data;

import com.sentia.ai.app.model.Provider;

import java.util.ArrayList;
import java.util.List;

public final class ProviderPresets {
    public static final class Preset {
        public final String name;
        public final String type;
        public final String baseUrl;
        public final String note;

        Preset(String name, String type, String baseUrl, String note) {
            this.name = name;
            this.type = type;
            this.baseUrl = baseUrl;
            this.note = note;
        }
    }

    private ProviderPresets() {}

    public static List<Preset> all() {
        List<Preset> l = new ArrayList<>();
        String o = Provider.TYPE_OPENAI;
        l.add(new Preset("OpenRouter", o, "https://openrouter.ai/api/v1", "Hundreds of models, many free"));
        l.add(new Preset("OmniRoute", o, "http://localhost:20128/v1", "Self-hosted gateway, edit the address"));
        l.add(new Preset("OpenAI (GPT)", o, "https://api.openai.com/v1", ""));
        l.add(new Preset("Anthropic (Claude)", Provider.TYPE_ANTHROPIC, "https://api.anthropic.com/v1", ""));
        l.add(new Preset("Google Gemini", o, "https://generativelanguage.googleapis.com/v1beta/openai", ""));
        l.add(new Preset("Groq", o, "https://api.groq.com/openai/v1", ""));
        l.add(new Preset("DeepSeek", o, "https://api.deepseek.com/v1", ""));
        l.add(new Preset("Mistral", o, "https://api.mistral.ai/v1", ""));
        l.add(new Preset("xAI (Grok)", o, "https://api.x.ai/v1", ""));
        l.add(new Preset("Together AI", o, "https://api.together.xyz/v1", ""));
        l.add(new Preset("Fireworks AI", o, "https://api.fireworks.ai/inference/v1", ""));
        l.add(new Preset("Perplexity", o, "https://api.perplexity.ai", "No model list, add IDs by hand"));
        l.add(new Preset("Cerebras", o, "https://api.cerebras.ai/v1", ""));
        l.add(new Preset("SambaNova", o, "https://api.sambanova.ai/v1", ""));
        l.add(new Preset("NVIDIA NIM", o, "https://integrate.api.nvidia.com/v1", ""));
        l.add(new Preset("Hugging Face", o, "https://router.huggingface.co/v1", ""));
        l.add(new Preset("GitHub Models", o, "https://models.github.ai/inference", "Use a GitHub token as the key"));
        l.add(new Preset("Cohere", o, "https://api.cohere.ai/compatibility/v1", ""));
        l.add(new Preset("Moonshot (Kimi)", o, "https://api.moonshot.ai/v1", ""));
        l.add(new Preset("Zhipu (GLM)", o, "https://open.bigmodel.cn/api/paas/v4", ""));
        l.add(new Preset("Alibaba Qwen", o, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1", ""));
        l.add(new Preset("SiliconFlow", o, "https://api.siliconflow.cn/v1", ""));
        l.add(new Preset("Hyperbolic", o, "https://api.hyperbolic.xyz/v1", ""));
        l.add(new Preset("DeepInfra", o, "https://api.deepinfra.com/v1/openai", ""));
        l.add(new Preset("Novita AI", o, "https://api.novita.ai/v3/openai", ""));
        l.add(new Preset("Chutes", o, "https://llm.chutes.ai/v1", ""));
        l.add(new Preset("Ollama (your network)", o, "http://192.168.0.10:11434/v1", "Replace with your computer's address"));
        l.add(new Preset("LM Studio (your network)", o, "http://192.168.0.10:1234/v1", "Replace with your computer's address"));
        l.add(new Preset("Custom (OpenAI-compatible)", o, "", "Any server with /chat/completions"));
        l.add(new Preset("Custom (Anthropic-compatible)", Provider.TYPE_ANTHROPIC, "", "Any server with /messages"));
        return l;
    }
}
