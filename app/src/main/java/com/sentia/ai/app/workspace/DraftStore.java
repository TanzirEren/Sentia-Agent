package com.sentia.ai.app.workspace;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Keeps unsaved editor text in the cache, so a crash or a killed app does not lose it. */
public final class DraftStore {
    /** A saved draft. base is the file's lastModified when the draft was written. */
    public static final class Draft {
        public long base;
        public boolean crlf;
        public String text;
        public long savedAt;
    }

    private final File dir;

    public DraftStore(Context c) {
        dir = new File(c.getApplicationContext().getCacheDir(), "drafts");
    }

    private File fileFor(long projectId, String rel) {
        String key;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(rel.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            key = sb.toString();
        } catch (NoSuchAlgorithmException e) {
            key = String.valueOf(rel.hashCode());
        }
        return new File(dir, "p" + projectId + "_" + key + ".draft");
    }

    public void save(long projectId, String rel, long base, boolean crlf, String text) {
        try {
            dir.mkdirs();
            File f = fileFor(projectId, rel);
            try (FileOutputStream os = new FileOutputStream(f)) {
                os.write((base + " " + (crlf ? 1 : 0) + "\n").getBytes(StandardCharsets.UTF_8));
                os.write(text.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // a missing draft is not a problem
        }
    }

    public Draft load(long projectId, String rel) {
        File f = fileFor(projectId, rel);
        if (!f.isFile()) return null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String head = r.readLine();
            if (head == null) return null;
            String[] parts = head.trim().split(" ");
            if (parts.length < 2) return null;
            Draft d = new Draft();
            d.base = Long.parseLong(parts[0]);
            d.crlf = "1".equals(parts[1]);
            d.savedAt = f.lastModified();
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            d.text = sb.toString();
            return d;
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    public void delete(long projectId, String rel) {
        fileFor(projectId, rel).delete();
    }
}
