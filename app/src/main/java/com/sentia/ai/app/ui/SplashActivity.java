package com.sentia.ai.app.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sentia.ai.app.R;

/**
 * Animated launch screen. The logo is drawn in three layers (shell, chevron, dash) so the
 * shell pops in, the prompt slides in and the dash blinks like a terminal cursor.
 * The logo colors follow the theme: ivory in dark mode, black in light mode.
 */
public class SplashActivity extends AppCompatActivity {
    private static final long TOTAL_MS = 2000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean launched = false;
    private AnimatorSet set;

    private final Runnable go = new Runnable() {
        @Override
        public void run() {
            launch();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        Bars.apply(this);

        final View shell = findViewById(R.id.ivShell);
        final View chevron = findViewById(R.id.ivChevron);
        final View dash = findViewById(R.id.ivDash);
        final TextView name = findViewById(R.id.tvSplashName);
        final View tag = findViewById(R.id.tvSplashTag);
        float d = getResources().getDisplayMetrics().density;

        shell.setAlpha(0f);
        shell.setScaleX(0.84f);
        shell.setScaleY(0.84f);
        chevron.setAlpha(0f);
        chevron.setTranslationX(-36f * d);
        dash.setAlpha(0f);
        name.setAlpha(0f);
        name.setTranslationY(14f * d);
        name.setLetterSpacing(0.6f);
        tag.setAlpha(0f);
        tag.setTranslationY(8f * d);

        ObjectAnimator shellA = ObjectAnimator.ofFloat(shell, View.ALPHA, 0f, 1f);
        shellA.setDuration(420);
        ObjectAnimator sx = ObjectAnimator.ofFloat(shell, View.SCALE_X, 0.84f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(shell, View.SCALE_Y, 0.84f, 1f);
        sx.setDuration(650);
        sy.setDuration(650);
        sx.setInterpolator(new OvershootInterpolator(1.1f));
        sy.setInterpolator(new OvershootInterpolator(1.1f));

        ObjectAnimator chA = ObjectAnimator.ofFloat(chevron, View.ALPHA, 0f, 1f);
        chA.setDuration(300);
        chA.setStartDelay(380);
        ObjectAnimator chX = ObjectAnimator.ofFloat(chevron, View.TRANSLATION_X, -36f * d, 0f);
        chX.setDuration(450);
        chX.setStartDelay(380);
        chX.setInterpolator(new DecelerateInterpolator(1.6f));

        ObjectAnimator dashIn = ObjectAnimator.ofFloat(dash, View.ALPHA, 0f, 1f);
        dashIn.setDuration(180);
        dashIn.setStartDelay(700);
        ObjectAnimator blink = ObjectAnimator.ofFloat(dash, View.ALPHA, 1f, 0.1f, 1f, 0.1f, 1f);
        blink.setDuration(700);
        blink.setStartDelay(900);

        ObjectAnimator nA = ObjectAnimator.ofFloat(name, View.ALPHA, 0f, 1f);
        nA.setDuration(520);
        nA.setStartDelay(550);
        ObjectAnimator nY = ObjectAnimator.ofFloat(name, View.TRANSLATION_Y, 14f * d, 0f);
        nY.setDuration(520);
        nY.setStartDelay(550);
        nY.setInterpolator(new DecelerateInterpolator());
        ValueAnimator ls = ValueAnimator.ofFloat(0.6f, 0.32f);
        ls.setDuration(700);
        ls.setStartDelay(550);
        ls.setInterpolator(new DecelerateInterpolator());
        ls.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                name.setLetterSpacing((Float) a.getAnimatedValue());
            }
        });

        ObjectAnimator tA = ObjectAnimator.ofFloat(tag, View.ALPHA, 0f, 1f);
        tA.setDuration(450);
        tA.setStartDelay(900);
        ObjectAnimator tY = ObjectAnimator.ofFloat(tag, View.TRANSLATION_Y, 8f * d, 0f);
        tY.setDuration(450);
        tY.setStartDelay(900);

        set = new AnimatorSet();
        set.playTogether(shellA, sx, sy, chA, chX, dashIn, blink, nA, nY, ls, tA, tY);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                handler.postDelayed(go, TOTAL_MS - 1600);
            }
        });
        set.start();
        // Safety net in case animations are disabled in developer options.
        handler.postDelayed(go, TOTAL_MS + 600);
    }

    private void launch() {
        if (launched || isFinishing()) return;
        launched = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(go);
        if (set != null) set.cancel();
        super.onDestroy();
    }
}
