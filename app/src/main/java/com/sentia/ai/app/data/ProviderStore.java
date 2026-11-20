package com.sentia.ai.app.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sentia.ai.app.model.ModelEntry;
import com.sentia.ai.app.model.Provider;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Providers (with API keys) are stored as one encrypted JSON blob. */
public class ProviderStore {
    private static ProviderStore instance;

    public static synchronized ProviderStore get(Context c) {
        if (instance == null) instance = new ProviderStore(c.getApplicationContext());
        return instance;
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private List<Provider> cache;

    private ProviderStore(Context c) {
        prefs = c.getSharedPreferences("sentia_providers", Context.MODE_PRIVATE);
    }

    private void load() {
        if (cache != null) return;
        String json = SecureStore.decrypt(prefs.getString("providers_enc", null));
        List<Provider> list = null;
        if (json != null) {
            try {
                Type t = new TypeToken<List<Provider>>() {}.getType();
                list = gson.fromJson(json, t);
            } catch (Exception ignored) {
            }
        }
        cache = list == null ? new ArrayList<>() : list;
    }

    private void persist() {
        prefs.edit().putString("providers_enc", SecureStore.encrypt(gson.toJson(cache))).apply();
    }

    public synchronized List<Provider> all() {
        load();
        return new ArrayList<>(cache);
    }

    public synchronized Provider get(String id) {
        load();
        if (id == null) return null;
        for (Provider p : cache) if (id.equals(p.id)) return p;
        return null;
    }

    public synchronized void save(Provider p) {
        load();
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).id.equals(p.id)) {
                cache.set(i, p);
                persist();
                return;
            }
        }
        cache.add(p);
        persist();
    }

    public synchronized void delete(String id) {
        load();
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).id.equals(id)) {
                cache.remove(i);
                break;
            }
        }
        persist();
    }

    /** First enabled provider that has an enabled model, or null. */
    public synchronized String[] firstAvailable() {
        load();
        for (Provider p : cache) {
            if (!p.enabled) continue;
            for (ModelEntry m : p.models) {
                if (m.enabled && m.id != null && !m.id.isEmpty()) return new String[]{p.id, m.id};
            }
        }
        return null;
    }
}
