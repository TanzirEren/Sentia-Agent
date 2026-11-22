package com.sentia.ai.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class SettingsStore {
    public static final String DEFAULT_SYSTEM_PROMPT =
            "You are Sentia, a helpful, accurate AI assistant. "
                    + "Reply in the same language the user writes in. "
                    + "Format answers with Markdown and put code in fenced code blocks with a language tag.";

    private static SettingsStore instance;

    public static synchronized SettingsStore get(Context c) {
        if (instance == null) instance = new SettingsStore(c.getApplicationContext());
        return instance;
    }

    private final SharedPreferences p;

    private SettingsStore(Context c) {
        p = c.getSharedPreferences("sentia_settings", Context.MODE_PRIVATE);
    }

    public static final float TEXT_MIN = 11f, TEXT_MAX = 20f;

    /** Always a readable size. A bad stored value (for example from an old state restore bug) is repaired. */
    public float textSizeSp() {
        float v;
        try {
            v = p.getFloat("text_size", 14f);
        } catch (ClassCastException e) {
            v = 14f;
        }
        if (Float.isNaN(v) || v < TEXT_MIN || v > TEXT_MAX) {
            v = 14f;
            p.edit().putFloat("text_size", v).apply();
        }
        return v;
    }
    public void setTextSizeSp(float v) { p.edit().putFloat("text_size", v).apply(); }

    /** 0 = system, 1 = light, 2 = dark */
    public int themeMode() { return p.getInt("theme_mode", 0); }
    public void setThemeMode(int m) { p.edit().putInt("theme_mode", m).apply(); applyTheme(); }

    public void applyTheme() {
        int mode;
        switch (themeMode()) {
            case 1: mode = AppCompatDelegate.MODE_NIGHT_NO; break;
            case 2: mode = AppCompatDelegate.MODE_NIGHT_YES; break;
            default: mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public String systemPrompt() {
        String s = p.getString("system_prompt", null);
        return (s == null || s.trim().isEmpty()) ? DEFAULT_SYSTEM_PROMPT : s;
    }
    public void setSystemPrompt(String s) { p.edit().putString("system_prompt", s).apply(); }

    public String selectedProviderId() { return p.getString("sel_provider", ""); }
    public String selectedModelId() { return p.getString("sel_model", ""); }
    public void setSelected(String providerId, String modelId) {
        p.edit().putString("sel_provider", providerId).putString("sel_model", modelId).apply();
    }

    public boolean enterToSend() { return p.getBoolean("enter_to_send", false); }
    public void setEnterToSend(boolean v) { p.edit().putBoolean("enter_to_send", v).apply(); }

    public String draft() { return p.getString("draft", ""); }
    public void setDraft(String s) { p.edit().putString("draft", s == null ? "" : s).apply(); }

    /** "" = device default, otherwise a BCP-47 tag such as bn-BD or en-US */
    public String voiceLang() { return p.getString("voice_lang", ""); }
    public void setVoiceLang(String s) { p.edit().putString("voice_lang", s).apply(); }

    // ---- appearance / chat toggles
    public int fontWeight() { return p.getInt("font_weight", 0); }
    public void setFontWeight(int v) { p.edit().putInt("font_weight", v).apply(); }

    public boolean markdown() { return p.getBoolean("markdown", true); }
    public void setMarkdown(boolean v) { p.edit().putBoolean("markdown", v).apply(); }

    public boolean syntaxHighlight() { return p.getBoolean("syntax", true); }
    public void setSyntaxHighlight(boolean v) { p.edit().putBoolean("syntax", v).apply(); }

    public boolean showTokens() { return p.getBoolean("show_tokens", true); }
    public void setShowTokens(boolean v) { p.edit().putBoolean("show_tokens", v).apply(); }

    public boolean showModelName() { return p.getBoolean("show_model", true); }
    public void setShowModelName(boolean v) { p.edit().putBoolean("show_model", v).apply(); }

    public boolean haptics() { return p.getBoolean("haptics", true); }
    public void setHaptics(boolean v) { p.edit().putBoolean("haptics", v).apply(); }

    public boolean autoScrollStream() { return p.getBoolean("autoscroll", true); }
    public void setAutoScrollStream(boolean v) { p.edit().putBoolean("autoscroll", v).apply(); }

    // ---- model behaviour
    /** "auto", "en" or "bn" */
    public String replyLanguage() { return p.getString("reply_lang", "auto"); }
    public void setReplyLanguage(String v) { p.edit().putString("reply_lang", v).apply(); }

    public boolean includeDate() { return p.getBoolean("include_date", false); }
    public void setIncludeDate(boolean v) { p.edit().putBoolean("include_date", v).apply(); }

    public boolean temperatureEnabled() { return p.getBoolean("temp_enabled", false); }
    public void setTemperatureEnabled(boolean v) { p.edit().putBoolean("temp_enabled", v).apply(); }

    public float temperature() {
        float v = p.getFloat("temperature", 0.7f);
        return (Float.isNaN(v) || v < 0f || v > 2f) ? 0.7f : v;
    }
    public void setTemperature(float v) { p.edit().putFloat("temperature", v).apply(); }

    /** 0 means "use the model default" */
    public int maxTokens() {
        int v = p.getInt("max_tokens", 0);
        return (v > 0 && v < 64) ? 0 : v; // a tiny limit would cut every reply
    }
    public void setMaxTokens(int v) { p.edit().putInt("max_tokens", v).apply(); }

    public int contextChars() {
        int v = p.getInt("context_chars", 120000);
        return v < 4000 ? 120000 : v;
    }
    public void setContextChars(int v) { p.edit().putInt("context_chars", v).apply(); }

    public void resetAll() {
        p.edit().clear().apply();
        applyTheme();
    }
}
