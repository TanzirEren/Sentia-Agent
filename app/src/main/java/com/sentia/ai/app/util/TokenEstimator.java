package com.sentia.ai.app.util;

public final class TokenEstimator {
    private TokenEstimator() {}

    /** Rough estimate: about 4 characters per token. */
    public static int estimate(String s) {
        if (s == null || s.isEmpty()) return 0;
        return Math.max(1, s.length() / 4);
    }
}
