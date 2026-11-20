package com.sentia.ai.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sentia.ai.app.model.SavedPrompt;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PromptStore {
    private static PromptStore instance;

    public static synchronized PromptStore get(Context c) {
        if (instance == null) instance = new PromptStore(c.getApplicationContext());
        return instance;
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private PromptStore(Context c) {
        prefs = c.getSharedPreferences("sentia_prompts", Context.MODE_PRIVATE);
    }

    public synchronized List<SavedPrompt> all() {
        String json = prefs.getString("list", null);
        if (json == null) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<SavedPrompt>>() {}.getType();
            List<SavedPrompt> l = gson.fromJson(json, t);
            return l == null ? new ArrayList<>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public synchronized void add(String text) {
        if (text == null || text.trim().isEmpty()) return;
        List<SavedPrompt> l = all();
        SavedPrompt p = new SavedPrompt();
        p.id = UUID.randomUUID().toString();
        p.text = text.trim();
        String first = p.text.split("\n")[0].trim();
        p.title = first.length() > 48 ? first.substring(0, 48) + "..." : first;
        p.createdAt = System.currentTimeMillis();
        l.add(0, p);
        prefs.edit().putString("list", gson.toJson(l)).apply();
    }

    public synchronized void delete(String id) {
        List<SavedPrompt> l = all();
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).id.equals(id)) {
                l.remove(i);
                break;
            }
        }
        prefs.edit().putString("list", gson.toJson(l)).apply();
    }
}
