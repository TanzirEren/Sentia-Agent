package com.sentia.ai.app.workspace;

import java.util.Locale;

/** Decides how a file should be opened, from its name. */
public final class FileKinds {
    public static final int TEXT = 0;
    public static final int IMAGE = 1;
    public static final int BINARY = 2;

    private FileKinds() {}

    private static final String[] IMAGE_EXT = {"png", "jpg", "jpeg", "gif", "webp", "bmp"};
    private static final String[] BINARY_EXT = {"apk", "aab", "zip", "jar", "aar", "so", "dex", "class", "gz", "tar",
            "7z", "rar", "pdf", "mp3", "mp4", "mkv", "avi", "wav", "ogg", "ttf", "otf", "woff", "woff2", "keystore",
            "jks", "db", "sqlite", "bin", "exe", "dll", "o", "a", "ico", "heic", "svgz"};

    public static String ext(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        if (i <= 0 || i == name.length() - 1) return "";
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public static int kind(String name) {
        String e = ext(name);
        for (String s : IMAGE_EXT) if (s.equals(e)) return IMAGE;
        for (String s : BINARY_EXT) if (s.equals(e)) return BINARY;
        return TEXT;
    }

    public static String mime(String name) {
        String e = ext(name);
        switch (e) {
            case "png": return "image/png";
            case "jpg":
            case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            case "html":
            case "htm": return "text/html";
            case "css": return "text/css";
            case "js": return "text/javascript";
            case "json": return "application/json";
            case "xml": return "text/xml";
            case "md": return "text/markdown";
            case "zip": return "application/zip";
            case "apk": return "application/vnd.android.package-archive";
            case "pdf": return "application/pdf";
            default: return kind(name) == TEXT ? "text/plain" : "application/octet-stream";
        }
    }
}
