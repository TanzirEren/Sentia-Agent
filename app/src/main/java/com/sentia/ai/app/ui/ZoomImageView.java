package com.sentia.ai.app.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/** Image that fits the screen at first, then supports pinch zoom and drag. */
public class ZoomImageView extends AppCompatImageView {
    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final ScaleGestureDetector scaler;
    private float baseScale = 1f;
    private float lastX, lastY;
    private boolean dragging;
    private int lastW, lastH;

    public ZoomImageView(Context c) {
        this(c, null);
    }

    public ZoomImageView(Context c, AttributeSet a) {
        super(c, a);
        setScaleType(ScaleType.MATRIX);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float cur = currentScale();
                float factor = d.getScaleFactor();
                float target = Math.max(baseScale, Math.min(baseScale * 6f, cur * factor));
                factor = target / cur;
                matrix.postScale(factor, factor, d.getFocusX(), d.getFocusY());
                clamp();
                setImageMatrix(matrix);
                return true;
            }
        });
    }

    @Override
    public void setImageDrawable(Drawable d) {
        super.setImageDrawable(d);
        lastW = 0;
        fit();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fit();
    }

    private void fit() {
        Drawable d = getDrawable();
        int vw = getWidth(), vh = getHeight();
        if (d == null || vw == 0 || vh == 0) return;
        int dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        if (lastW == vw && lastH == vh) return;
        lastW = vw;
        lastH = vh;
        baseScale = Math.min((float) vw / dw, (float) vh / dh);
        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate((vw - dw * baseScale) / 2f, (vh - dh * baseScale) / 2f);
        setImageMatrix(matrix);
    }

    private float currentScale() {
        matrix.getValues(values);
        return values[Matrix.MSCALE_X];
    }

    private void clamp() {
        Drawable d = getDrawable();
        if (d == null) return;
        RectF r = new RectF(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.mapRect(r);
        float dx = 0, dy = 0;
        int vw = getWidth(), vh = getHeight();
        if (r.width() <= vw) dx = (vw - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < vw) dx = vw - r.right;
        if (r.height() <= vh) dy = (vh - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < vh) dy = vh - r.bottom;
        matrix.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && !scaler.isInProgress() && e.getPointerCount() == 1) {
                    matrix.postTranslate(e.getX() - lastX, e.getY() - lastY);
                    clamp();
                    setImageMatrix(matrix);
                }
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = e.getActionMasked() == MotionEvent.ACTION_POINTER_UP;
                if (dragging && e.getPointerCount() > 1) {
                    int idx = e.getActionIndex() == 0 ? 1 : 0;
                    lastX = e.getX(idx);
                    lastY = e.getY(idx);
                }
                break;
            default:
                break;
        }
        return true;
    }
}
