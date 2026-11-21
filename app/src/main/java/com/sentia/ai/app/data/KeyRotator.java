package com.sentia.ai.app.data;

import com.sentia.ai.app.model.Provider;

import java.util.HashMap;
import java.util.Set;

/** Chooses which API key to use and skips keys that recently ran out. */
public final class KeyRotator {
    private KeyRotator() {}

    private static boolean blocked(Provider p, int idx) {
        if (p.keyBlockedUntil == null) return false;
        Long until = p.keyBlockedUntil.get(String.valueOf(idx));
        return until != null && until > System.currentTimeMillis();
    }

    /** Index of the key to start with, or -1 when the provider has no keys. */
    public static int pick(Provider p) {
        int n = p.apiKeys == null ? 0 : p.apiKeys.size();
        if (n == 0) return -1;
        int start = Math.floorMod(p.activeKey, n);
        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            if (!blocked(p, idx)) return idx;
        }
        return start;
    }

    public static String keyAt(Provider p, int idx) {
        if (idx < 0 || p.apiKeys == null || idx >= p.apiKeys.size()) return "";
        String k = p.apiKeys.get(idx);
        return k == null ? "" : k.trim();
    }

    /** Next usable key that has not been tried yet, or -1. */
    public static int next(Provider p, Set<Integer> tried) {
        int n = p.apiKeys == null ? 0 : p.apiKeys.size();
        for (int idx = 0; idx < n; idx++) {
            if (!tried.contains(idx) && !blocked(p, idx)) return idx;
        }
        return -1;
    }

    public static void block(ProviderStore store, Provider p, int idx, long millis) {
        if (p.keyBlockedUntil == null) p.keyBlockedUntil = new HashMap<>();
        p.keyBlockedUntil.put(String.valueOf(idx), System.currentTimeMillis() + millis);
        store.save(p);
    }

    public static void setActive(ProviderStore store, Provider p, int idx) {
        p.activeKey = idx;
        store.save(p);
    }
}
