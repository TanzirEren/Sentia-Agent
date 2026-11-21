package com.sentia.ai.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.sentia.ai.app.R;
import com.sentia.ai.app.data.KeyRotator;
import com.sentia.ai.app.data.ProviderStore;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.data.db.AppDatabase;
import com.sentia.ai.app.data.db.ModelUsage;
import com.sentia.ai.app.data.db.TokenPoint;
import com.sentia.ai.app.model.Provider;
import com.sentia.ai.app.net.BalanceFetcher;
import com.sentia.ai.app.util.Fmt;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Token usage per period and model, plus the provider balance when it can be read. */
public class TokenActivity extends AppCompatActivity {
    private static final long DAY = 24L * 60 * 60 * 1000;

    private AppDatabase db;
    private SettingsStore settings;
    private ProviderStore providers;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int range = 0;
    private long sessionId = 0;

    private TextView tvProvider, tvModel, tvBalance, tvBalanceDetail, tvTotal, tvIn, tvOut, tvReplies, tvAvg, tvChat;
    private TextView[] rangeChips;
    private View ivInfinity;
    private ProgressBar pbBalance;
    private LinearLayout llModels;
    private BarChartView chart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tokens);
        Bars.apply(this);
        db = AppDatabase.get(this);
        settings = SettingsStore.get(this);
        providers = ProviderStore.get(this);
        sessionId = getIntent().getLongExtra("sessionId", 0);

        tvProvider = findViewById(R.id.tvTokProvider);
        tvModel = findViewById(R.id.tvTokModel);
        tvBalance = findViewById(R.id.tvBalance);
        tvBalanceDetail = findViewById(R.id.tvBalanceDetail);
        ivInfinity = findViewById(R.id.ivInfinity);
        pbBalance = findViewById(R.id.pbBalance);
        tvTotal = findViewById(R.id.tvTotalTokens);
        tvIn = findViewById(R.id.tvStatIn);
        tvOut = findViewById(R.id.tvStatOut);
        tvReplies = findViewById(R.id.tvStatReplies);
        tvAvg = findViewById(R.id.tvStatAvg);
        tvChat = findViewById(R.id.tvChatTokens);
        llModels = findViewById(R.id.llModels);
        chart = findViewById(R.id.chart);
        rangeChips = new TextView[]{
                findViewById(R.id.tvRangeToday), findViewById(R.id.tvRange7),
                findViewById(R.id.tvRange30), findViewById(R.id.tvRangeAll)};

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnTokRefresh).setOnClickListener(v -> loadAll());
        for (int i = 0; i < rangeChips.length; i++) {
            final int idx = i;
            rangeChips[i].setOnClickListener(v -> {
                range = idx;
                styleChips();
                loadStats();
            });
        }
        styleChips();
        loadAll();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private void loadAll() {
        loadModelCard();
        loadBalance();
        loadStats();
        loadChat();
    }

    private void styleChips() {
        for (int i = 0; i < rangeChips.length; i++) {
            boolean sel = i == range;
            rangeChips[i].setBackgroundResource(sel ? R.drawable.bg_pill_ink : R.drawable.bg_pill_nude);
            rangeChips[i].setTextColor(ContextCompat.getColor(this, sel ? R.color.on_ink : R.color.ink));
        }
    }

    // ------------------------------------------------------------------ model and balance

    private Provider provider() {
        return providers.get(settings.selectedProviderId());
    }

    private void loadModelCard() {
        Provider p = provider();
        String m = settings.selectedModelId();
        if (p == null || m == null || m.isEmpty()) {
            tvProvider.setText("");
            tvModel.setText(R.string.tok_no_provider);
        } else {
            tvProvider.setText(p.name);
            tvModel.setText(m);
        }
    }

    private void loadBalance() {
        final Provider p = provider();
        ivInfinity.setVisibility(View.GONE);
        tvBalance.setVisibility(View.VISIBLE);
        if (p == null) {
            tvBalance.setText("\u2014");
            tvBalanceDetail.setText(R.string.tok_no_provider);
            return;
        }
        int ki = KeyRotator.pick(p);
        final String key = ki >= 0 ? KeyRotator.keyAt(p, ki) : "";
        pbBalance.setVisibility(View.VISIBLE);
        tvBalance.setText("");
        tvBalanceDetail.setText(R.string.tok_balance_loading);
        io.execute(() -> {
            final BalanceFetcher.Balance b = BalanceFetcher.fetch(p, key);
            main.post(() -> {
                pbBalance.setVisibility(View.GONE);
                if (isFinishing()) return;
                if (!b.supported) {
                    ivInfinity.setVisibility(View.VISIBLE);
                    tvBalance.setVisibility(View.GONE);
                    tvBalanceDetail.setText(R.string.tok_not_reported);
                } else if (b.error != null) {
                    tvBalance.setText("\u2014");
                    tvBalanceDetail.setText(getString(R.string.tok_balance_failed) + ": " + b.error);
                } else if (b.unlimited) {
                    ivInfinity.setVisibility(View.VISIBLE);
                    tvBalance.setText(R.string.tok_unlimited);
                    tvBalanceDetail.setText(b.detail);
                } else {
                    tvBalance.setText(b.text);
                    tvBalanceDetail.setText(b.detail);
                }
            });
        });
    }

    // ------------------------------------------------------------------ usage

    private long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void loadStats() {
        final long today = startOfToday();
        final long since;
        switch (range) {
            case 0: since = today; break;
            case 1: since = System.currentTimeMillis() - 7 * DAY; break;
            case 2: since = System.currentTimeMillis() - 30 * DAY; break;
            default: since = 0;
        }
        io.execute(() -> {
            final long in = db.messageDao().sumIn(since);
            final long out = db.messageDao().sumOut(since);
            final int replies = db.messageDao().replies(since);
            final List<ModelUsage> models = db.messageDao().byModel(since);

            long chartStart = today - 6 * DAY;
            List<TokenPoint> pts = db.messageDao().points(chartStart);
            final float[] daily = new float[7];
            for (TokenPoint tp : pts) {
                int idx = (int) ((tp.createdAt - chartStart) / DAY);
                if (idx >= 0 && idx < 7) daily[idx] += tp.total;
            }
            final String[] labels = new String[7];
            SimpleDateFormat f = new SimpleDateFormat("EEE", Locale.ENGLISH);
            for (int i = 0; i < 7; i++) labels[i] = f.format(new Date(chartStart + i * DAY + DAY / 2));

            main.post(() -> {
                if (isFinishing()) return;
                long total = in + out;
                tvTotal.setText(Fmt.grouped(total));
                tvIn.setText(Fmt.compact(in));
                tvOut.setText(Fmt.compact(out));
                tvReplies.setText(String.valueOf(replies));
                tvAvg.setText(replies == 0 ? "0" : Fmt.compact(total / replies));
                chart.setData(daily, labels);
                renderModels(models);
            });
        });
    }

    private void renderModels(List<ModelUsage> models) {
        llModels.removeAllViews();
        if (models == null || models.isEmpty()) {
            TextView t = new TextView(this);
            t.setText(R.string.tok_no_data);
            t.setTextColor(ContextCompat.getColor(this, R.color.ink_muted));
            t.setTextSize(13f);
            t.setPadding(0, 8, 0, 8);
            llModels.addView(t);
            return;
        }
        long max = 1;
        for (ModelUsage m : models) max = Math.max(max, m.tin + m.tout);
        LayoutInflater inf = LayoutInflater.from(this);
        for (ModelUsage m : models) {
            View row = inf.inflate(R.layout.item_usage_row, llModels, false);
            long total = m.tin + m.tout;
            ((TextView) row.findViewById(R.id.tvUsageLabel)).setText(m.label);
            ((TextView) row.findViewById(R.id.tvUsageTotal)).setText(Fmt.compact(total));
            ((ProgressBar) row.findViewById(R.id.pbUsage)).setProgress((int) (total * 100 / max));
            ((TextView) row.findViewById(R.id.tvUsageSub)).setText(
                    getString(R.string.tok_input) + " " + Fmt.compact(m.tin) + "  \u00b7  "
                            + getString(R.string.tok_output) + " " + Fmt.compact(m.tout) + "  \u00b7  "
                            + m.n + " " + getString(R.string.tok_replies).toLowerCase(Locale.ROOT));
            llModels.addView(row);
        }
    }

    private void loadChat() {
        if (sessionId == 0) {
            tvChat.setText(R.string.tok_chat_none);
            return;
        }
        io.execute(() -> {
            final long t = db.messageDao().sessionTokens(sessionId);
            main.post(() -> {
                if (!isFinishing()) tvChat.setText(getString(R.string.tok_chat_value, Fmt.grouped(t)));
            });
        });
    }
}
