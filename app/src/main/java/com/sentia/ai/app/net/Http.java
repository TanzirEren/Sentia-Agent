package com.sentia.ai.app.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

public final class Http {
    private Http() {}

    public static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
}
