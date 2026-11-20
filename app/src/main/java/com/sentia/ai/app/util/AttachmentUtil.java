package com.sentia.ai.app.util;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.sentia.ai.app.model.Attachment;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class AttachmentUtil {
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private static final int MAX_TEXT_BYTES = 200 * 1024;

    private AttachmentUtil() {}

    /** Copies a picked Uri into app storage. Call from a background thread. */
    public static Attachment save(Context ctx, Uri uri) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            String name = "file";
            Cursor c = cr.query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                } finally {
                    c.close();
                }
            }
            String mime = cr.getType(uri);
            if (mime == null) mime = "application/octet-stream";
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");

            File dir = new File(ctx.getFilesDir(), "attachments");
            if (!dir.exists()) dir.mkdirs();
            File out = new File(dir, System.currentTimeMillis() + "_" + name);
            long total = 0;
            try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                if (in == null) return null;
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) {
                        out.delete();
                        return null;
                    }
                    os.write(buf, 0, n);
                }
            }
            String kind = mime.startsWith("image/") ? Attachment.IMAGE : Attachment.FILE;
            return new Attachment(kind, name, mime, out.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    /** Reads a text file (up to 200 KB). Returns null for binary files. */
    public static String readText(String path) {
        try (InputStream in = new FileInputStream(path)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            boolean truncated = false;
            while ((n = in.read(buf)) > 0) {
                if (bos.size() + n > MAX_TEXT_BYTES) {
                    bos.write(buf, 0, MAX_TEXT_BYTES - bos.size());
                    truncated = true;
                    break;
                }
                bos.write(buf, 0, n);
            }
            byte[] bytes = bos.toByteArray();
            int check = Math.min(bytes.length, 4096);
            for (int i = 0; i < check; i++) if (bytes[i] == 0) return null;
            String s = new String(bytes, StandardCharsets.UTF_8);
            return truncated ? s + "\n[... file truncated ...]" : s;
        } catch (Exception e) {
            return null;
        }
    }

    public static String extension(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
