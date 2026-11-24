package com.sentia.ai.app.util;

import java.util.Locale;

public final class Fmt {
    private Fmt() {}

    /** 950 stays 950, 1500 becomes 1.5K, 2500000 becomes 2.5M. */
    public static String compact(long n) {
        if (n < 1000) return String.valueOf(n);
        if (n < 1000000) return trim(n / 1000.0) + "K";
        if (n < 1000000000L) return trim(n / 1000000.0) + "M";
        return trim(n / 1000000000.0) + "B";
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** 1234567 becomes 1,234,567. */
    public static String grouped(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    /** 900 B, 1.5 KB, 2.5 MB. */
    public static String bytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024L) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        if (b < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.1f MB", b / 1048576.0);
        return String.format(Locale.US, "%.1f GB", b / 1073741824.0);
    }
}
