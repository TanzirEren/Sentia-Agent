package com.sentia.ai.app.util;

import android.content.Context;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sentia.ai.app.data.db.AppDatabase;
import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.data.db.SessionEntity;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Writes every chat to a JSON or Markdown file. Call from a background thread. */
public final class ChatExporter {
    private ChatExporter() {}

    public static boolean export(Context ctx, Uri uri, boolean markdown) {
        try {
            AppDatabase db = AppDatabase.get(ctx);
            List<SessionEntity> sessions = db.sessionDao().allSync();
            String out;
            if (markdown) {
                StringBuilder sb = new StringBuilder("# Sentia chats\n\n");
                SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                for (SessionEntity s : sessions) {
                    sb.append("## ").append(s.title == null ? "Chat" : s.title).append("\n");
                    sb.append("_").append(f.format(new Date(s.createdAt))).append("_\n\n");
                    for (MessageEntity m : db.messageDao().list(s.id)) {
                        if ("error".equals(m.role)) continue;
                        sb.append("**").append("user".equals(m.role) ? "You" : "Sentia").append("**\n\n");
                        sb.append(m.content == null ? "" : m.content).append("\n\n");
                    }
                    sb.append("---\n\n");
                }
                out = sb.toString();
            } else {
                JsonArray arr = new JsonArray();
                for (SessionEntity s : sessions) {
                    JsonObject so = new JsonObject();
                    so.addProperty("title", s.title);
                    so.addProperty("createdAt", s.createdAt);
                    so.addProperty("updatedAt", s.updatedAt);
                    JsonArray ma = new JsonArray();
                    for (MessageEntity m : db.messageDao().list(s.id)) {
                        JsonObject mo = new JsonObject();
                        mo.addProperty("role", m.role);
                        mo.addProperty("content", m.content);
                        mo.addProperty("model", m.modelLabel);
                        mo.addProperty("createdAt", m.createdAt);
                        ma.add(mo);
                    }
                    so.add("messages", ma);
                    arr.add(so);
                }
                Gson g = new GsonBuilder().setPrettyPrinting().create();
                out = g.toJson(arr);
            }
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                if (os == null) return false;
                os.write(out.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
