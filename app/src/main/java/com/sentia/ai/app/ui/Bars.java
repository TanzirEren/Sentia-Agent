package com.sentia.ai.app.ui;

import android.app.Activity;
import android.content.res.Configuration;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

final class Bars {
    private Bars() {}

    /** Dark icons on a light background, light icons on a dark one. */
    static void apply(Activity a) {
        boolean night = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(a.getWindow(), a.getWindow().getDecorView());
        c.setAppearanceLightStatusBars(!night);
        c.setAppearanceLightNavigationBars(!night);
    }
}
