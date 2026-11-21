package com.sentia.ai.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.sentia.ai.app.R;
import com.sentia.ai.app.util.Fmt;

/** A small bar chart for the last seven days of token use. */
public class BarChartView extends View {
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float[] values = new float[0];
    private String[] labels = new String[0];
    private final float density;

    public BarChartView(Context c) {
        this(c, null);
    }

    public BarChartView(Context c, AttributeSet a) {
        super(c, a);
        density = c.getResources().getDisplayMetrics().density;
        bar.setColor(ContextCompat.getColor(c, R.color.ink));
        track.setColor(ContextCompat.getColor(c, R.color.nude));
        label.setColor(ContextCompat.getColor(c, R.color.ink_muted));
        label.setTextSize(11 * density);
        label.setTextAlign(Paint.Align.CENTER);
        value.setColor(ContextCompat.getColor(c, R.color.ink));
        value.setTextSize(10 * density);
        value.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(float[] v, String[] l) {
        values = v == null ? new float[0] : v;
        labels = l == null ? new String[0] : l;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int n = values.length;
        if (n == 0) return;
        float w = getWidth();
        float h = getHeight();
        float labelH = 20 * density;
        float valueH = 16 * density;
        float chartTop = valueH;
        float chartBottom = h - labelH;
        float slot = w / n;
        float barW = Math.min(slot * 0.55f, 34 * density);
        float max = 0;
        for (float f : values) max = Math.max(max, f);
        float radius = 6 * density;
        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2f;
            rect.set(cx - barW / 2f, chartTop, cx + barW / 2f, chartBottom);
            canvas.drawRoundRect(rect, radius, radius, track);
            if (values[i] > 0 && max > 0) {
                float bh = Math.max(4 * density, (chartBottom - chartTop) * (values[i] / max));
                rect.set(cx - barW / 2f, chartBottom - bh, cx + barW / 2f, chartBottom);
                canvas.drawRoundRect(rect, radius, radius, bar);
                canvas.drawText(Fmt.compact((long) values[i]), cx, chartBottom - bh - 4 * density, value);
            }
            if (i < labels.length) canvas.drawText(labels[i], cx, h - 5 * density, label);
        }
    }
}
