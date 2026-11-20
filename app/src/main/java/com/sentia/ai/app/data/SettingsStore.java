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

    public float textSizeSp() { return p.getFloat("text_size", 14f); }
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
}
