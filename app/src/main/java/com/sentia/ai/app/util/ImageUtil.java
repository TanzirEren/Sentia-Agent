package com.sentia.ai.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public final class ImageUtil {
    private ImageUtil() {}

    /** Downscales an image file and returns it as a base64 JPEG (no wrap). */
    public static String toBase64Jpeg(String path, int maxDim) {
        try {
            Bitmap bmp = decode(path, maxDim);
            if (bmp == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out);
            bmp.recycle();
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Bitmap thumb(String path, int maxDim) {
        try {
            return decode(path, maxDim);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap decode(String path, int maxDim) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while ((o.outWidth / sample) > maxDim * 2 || (o.outHeight / sample) > maxDim * 2) sample *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sample;
        Bitmap bmp = BitmapFactory.decodeFile(path, o2);
        if (bmp == null) return null;
        int w = bmp.getWidth(), h = bmp.getHeight();
        float scale = Math.min(1f, (float) maxDim / Math.max(w, h));
        if (scale < 1f) {
            Bitmap s = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
            if (s != bmp) bmp.recycle();
            bmp = s;
        }
        return bmp;
    }
}
