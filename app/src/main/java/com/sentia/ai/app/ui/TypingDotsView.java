package com.sentia.ai.app.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import com.sentia.ai.app.R;

/** Three softly pulsing dots shown while waiting for the first token. */
public class TypingDotsView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;

    public TypingDotsView(Context c) { this(c, null); }

    public TypingDotsView(Context c, AttributeSet a) {
        super(c, a);
        paint.setColor(ContextCompat.getColor(c, R.color.ink_muted));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = getHeight() * 0.18f;
        float cy = getHeight() / 2f;
        float gap = r * 3.2f;
        float startX = r + 2f;
        for (int i = 0; i < 3; i++) {
            float t = (phase + (2 - i) * 0.18f) % 1f;
            float wave = (float) (0.5 + 0.5 * Math.sin(t * 2 * Math.PI));
            paint.setAlpha((int) (90 + 165 * wave));
            canvas.drawCircle(startX + i * gap, cy - wave * r * 0.6f, r * (0.8f + 0.25f * wave), paint);
        }
    }
}
