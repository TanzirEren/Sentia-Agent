package com.sentia.ai.app;

import android.app.Application;

import com.sentia.ai.app.data.SettingsStore;

public class SentiaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SettingsStore.get(this).applyTheme();
    }
}
