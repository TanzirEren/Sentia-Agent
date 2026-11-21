package com.sentia.ai.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The user's personal profile. It is stored on the phone and added to the system prompt. */
public class ProfileStore {
    public static class Project {
        public String id = UUID.randomUUID().toString();
        public String name = "";
        public String desc = "";
    }

    public static class Data {
        public String name = "";
        public String role = "";
        public String about = "";
        public String languages = "";
        public String instructions = "";
        /** default, friendly, professional, casual, direct, encouraging */
        public String tone = "default";
        /** default, concise, detailed, steps */
        public String style = "default";
        public List<String> skills = new ArrayList<>();
        public List<String> interests = new ArrayList<>();
        public List<Project> projects = new ArrayList<>();
        public boolean useProfile = true;
    }

    private static ProfileStore instance;

    public static synchronized ProfileStore get(Context c) {
        if (instance == null) instance = new ProfileStore(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();
    private Data data;

    private ProfileStore(Context c) {
        ctx = c;
        prefs = c.getSharedPreferences("sentia_profile", Context.MODE_PRIVATE);
    }

    public synchronized Data data() {
        if (data == null) {
            Data d = null;
            try {
                d = gson.fromJson(prefs.getString("data", null), Data.class);
            } catch (Exception ignored) {
            }
            data = d == null ? new Data() : d;
            if (data.skills == null) data.skills = new ArrayList<>();
            if (data.interests == null) data.interests = new ArrayList<>();
            if (data.projects == null) data.projects = new ArrayList<>();
            if (data.tone == null) data.tone = "default";
            if (data.style == null) data.style = "default";
        }
        return data;
    }

    public synchronized void save() {
        prefs.edit().putString("data", gson.toJson(data())).apply();
    }

    public synchronized void clear() {
        data = new Data();
        save();
        avatarFile().delete();
    }

    public String toJson() {
        return gson.toJson(data());
    }

    /** Returns false when the text is not a valid profile. */
    public synchronized boolean importJson(String json) {
        try {
            Data d = gson.fromJson(json, Data.class);
            if (d == null) return false;
            data = d;
            data();
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- avatar

    public File avatarFile() {
        return new File(ctx.getFilesDir(), "profile_avatar.jpg");
    }

    public boolean hasAvatar() {
        return avatarFile().exists();
    }

    /** Crops the picked image to a square, shrinks it, and stores it. Call from a background thread. */
    public boolean setAvatar(Uri uri) {
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            Bitmap src = BitmapFactory.decodeStream(in);
            if (src == null) return false;
            int side = Math.min(src.getWidth(), src.getHeight());
            Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
            Bitmap out = Bitmap.createScaledBitmap(sq, 512, 512, true);
            try (FileOutputStream os = new FileOutputStream(avatarFile())) {
                out.compress(Bitmap.CompressFormat.JPEG, 90, os);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------- prompt

    public String displayName() {
        return data().name == null ? "" : data().name.trim();
    }

    /** Text added to the system prompt so the AI knows the user. Empty when off or nothing is set. */
    public String promptBlock() {
        Data d = data();
        if (!d.useProfile) return "";
        StringBuilder sb = new StringBuilder();
        line(sb, "Name", d.name, " (use it naturally when addressing the user)");
        line(sb, "Role", d.role, "");
        line(sb, "About", d.about, "");
        if (!d.skills.isEmpty()) line(sb, "Skills", join(d.skills), "");
        if (!d.interests.isEmpty()) line(sb, "Interests", join(d.interests), "");
        line(sb, "Languages", d.languages, "");
        if (!d.projects.isEmpty()) {
            StringBuilder p = new StringBuilder();
            for (Project pr : d.projects) {
                if (pr.name == null || pr.name.trim().isEmpty()) continue;
                if (p.length() > 0) p.append("; ");
                p.append(pr.name.trim());
                if (pr.desc != null && !pr.desc.trim().isEmpty()) p.append(": ").append(pr.desc.trim());
            }
            line(sb, "Current projects", p.toString(), "");
        }
        String tone = toneText(d.tone);
        if (!tone.isEmpty()) line(sb, "Preferred tone", tone, "");
        String style = styleText(d.style);
        if (!style.isEmpty()) line(sb, "Preferred answer style", style, "");
        boolean hasInstr = d.instructions != null && !d.instructions.trim().isEmpty();
        if (sb.length() == 0 && !hasInstr) return "";
        StringBuilder out = new StringBuilder();
        if (sb.length() > 0) {
            out.append("About the user (use this to personalise answers, and do not repeat it back unless asked):\n").append(sb);
        }
        if (hasInstr) {
            if (out.length() > 0) out.append('\n');
            out.append("Custom instructions from the user:\n").append(d.instructions.trim());
        }
        return out.toString().trim();
    }

    private static void line(StringBuilder sb, String label, String value, String suffix) {
        if (value == null || value.trim().isEmpty()) return;
        sb.append("- ").append(label).append(": ").append(value.trim()).append(suffix).append('\n');
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (s == null || s.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(s.trim());
        }
        return sb.toString();
    }

    public static String toneText(String key) {
        if (key == null) return "";
        switch (key) {
            case "friendly": return "friendly and warm";
            case "professional": return "professional and polished";
            case "casual": return "casual and relaxed";
            case "direct": return "direct and to the point";
            case "encouraging": return "encouraging and supportive";
            default: return "";
        }
    }

    public static String styleText(String key) {
        if (key == null) return "";
        switch (key) {
            case "concise": return "short and concise";
            case "detailed": return "detailed and thorough";
            case "steps": return "step by step";
            default: return "";
        }
    }
}
