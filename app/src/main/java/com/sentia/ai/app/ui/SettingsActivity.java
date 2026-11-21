package com.sentia.ai.app.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.PromptStore;
import com.sentia.ai.app.data.ProviderStore;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.data.db.AppDatabase;
import com.sentia.ai.app.model.SavedPrompt;
import com.sentia.ai.app.util.ChatExporter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {
    private static final String URL_TELEGRAM = "https://t.me/agentsentia";
    private static final String URL_WEBSITE = "https://sentia-agent.vercel.app";
    private static final String URL_GITHUB = "https://github.com/developer-tanzir/Sentia-Agent";
    private static final String URL_DEVELOPER = "https://t.me/DeveloperTanzir";
    private static final String URL_PRIVACY = "https://sentia-agent.vercel.app/privacy";

    private interface IntCb {
        void run(int index);
    }

    private SettingsStore settings;
    private ProviderStore providers;
    private LayoutInflater inf;
    private android.widget.LinearLayout list;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private TextView valProviders, valPrompts;
    private ActivityResultLauncher<String> exportJson, exportMd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Bars.apply(this);
        settings = SettingsStore.get(this);
        providers = ProviderStore.get(this);
        inf = LayoutInflater.from(this);
        list = findViewById(R.id.settingsList);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        exportJson = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) runExport(uri, false);
        });
        exportMd = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/markdown"), uri -> {
            if (uri != null) runExport(uri, true);
        });
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshValues();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ screen

    private void build() {
        // ---- AI
        header(R.string.sec_ai);
        View prov = row(getString(R.string.set_providers), null, "", true, v -> startActivity(new Intent(this, ProvidersActivity.class)));
        valProviders = prov.findViewById(R.id.tvSetValue);
        row(getString(R.string.set_system_prompt), getString(R.string.set_system_prompt_sub), null, true, v -> editSystemPrompt());

        // ---- Appearance
        header(R.string.sec_appearance);
        final View theme = row(getString(R.string.set_theme), null, themeLabel(), true, null);
        theme.setOnClickListener(v -> choose(R.string.set_theme,
                new String[]{getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)},
                settings.themeMode(), i -> {
                    settings.setThemeMode(i);
                    setValue(theme, themeLabel());
                }));
        slider(getString(R.string.set_text_size), 11f, 20f, 1f, settings.textSizeSp(), true,
                v -> String.format(java.util.Locale.US, "%.0f sp", v), (v, fromUser) -> settings.setTextSizeSp(v));
        final View weight = row(getString(R.string.set_font_weight), null, weightLabel(), true, null);
        weight.setOnClickListener(v -> choose(R.string.set_font_weight,
                new String[]{getString(R.string.font_light), getString(R.string.font_regular)},
                settings.fontWeight(), i -> {
                    settings.setFontWeight(i);
                    setValue(weight, weightLabel());
                }));

        // ---- Chat
        header(R.string.sec_chat);
        toggle(R.string.set_markdown, R.string.set_markdown_sub, settings.markdown(), (b, c) -> settings.setMarkdown(c));
        toggle(R.string.set_syntax, 0, settings.syntaxHighlight(), (b, c) -> settings.setSyntaxHighlight(c));
        toggle(R.string.set_show_model, 0, settings.showModelName(), (b, c) -> settings.setShowModelName(c));
        toggle(R.string.set_show_tokens, 0, settings.showTokens(), (b, c) -> settings.setShowTokens(c));
        toggle(R.string.set_haptics, 0, settings.haptics(), (b, c) -> settings.setHaptics(c));
        toggle(R.string.set_autoscroll, 0, settings.autoScrollStream(), (b, c) -> settings.setAutoScrollStream(c));
        toggle(R.string.set_enter_send, R.string.set_enter_send_sub, settings.enterToSend(), (b, c) -> settings.setEnterToSend(c));

        // ---- Model behaviour
        header(R.string.sec_model);
        final View lang = row(getString(R.string.set_reply_lang), null, replyLangLabel(), true, null);
        lang.setOnClickListener(v -> choose(R.string.set_reply_lang,
                new String[]{getString(R.string.lang_auto), getString(R.string.lang_en), getString(R.string.lang_bn)},
                replyLangIndex(), i -> {
                    settings.setReplyLanguage(i == 1 ? "en" : i == 2 ? "bn" : "auto");
                    setValue(lang, replyLangLabel());
                }));
        toggle(R.string.set_include_date, R.string.set_include_date_sub, settings.includeDate(), (b, c) -> settings.setIncludeDate(c));
        final View tempSlider = slider(getString(R.string.temperature), 0f, 2f, 0.1f, settings.temperature(), false,
                v -> String.format(java.util.Locale.US, "%.1f", v), (v, fromUser) -> settings.setTemperature(v));
        tempSlider.setVisibility(settings.temperatureEnabled() ? View.VISIBLE : View.GONE);
        // insert the temperature switch above its slider
        MaterialSwitch tempSwitch = toggleAt(list.indexOfChild(tempSlider), R.string.set_temperature, R.string.set_temperature_sub,
                settings.temperatureEnabled(), (b, c) -> {
                    settings.setTemperatureEnabled(c);
                    tempSlider.setVisibility(c ? View.VISIBLE : View.GONE);
                });
        tempSwitch.setChecked(settings.temperatureEnabled());
        final int[] tokenValues = {0, 1024, 2048, 4096, 8192, 16384};
        final String[] tokenLabels = {getString(R.string.max_tokens_auto), "1,024", "2,048", "4,096", "8,192", "16,384"};
        final View maxTok = row(getString(R.string.set_max_tokens), null, tokenLabels[nearest(tokenValues, settings.maxTokens())], true, null);
        maxTok.setOnClickListener(v -> choose(R.string.set_max_tokens, tokenLabels, nearest(tokenValues, settings.maxTokens()), i -> {
            settings.setMaxTokens(tokenValues[i]);
            setValue(maxTok, tokenLabels[i]);
        }));
        final int[] ctxValues = {20000, 60000, 120000, 400000};
        final String[] ctxLabels = {getString(R.string.ctx_small), getString(R.string.ctx_medium), getString(R.string.ctx_large), getString(R.string.ctx_max)};
        final View ctxRow = row(getString(R.string.set_context), null, ctxLabels[nearest(ctxValues, settings.contextChars())], true, null);
        ctxRow.setOnClickListener(v -> choose(R.string.set_context, ctxLabels, nearest(ctxValues, settings.contextChars()), i -> {
            settings.setContextChars(ctxValues[i]);
            setValue(ctxRow, ctxLabels[i]);
        }));

        // ---- Voice
        header(R.string.sec_voice);
        final View voice = row(getString(R.string.set_voice_lang), null, voiceLabel(), true, null);
        voice.setOnClickListener(v -> choose(R.string.set_voice_lang,
                new String[]{getString(R.string.voice_device), getString(R.string.voice_bn), getString(R.string.voice_en)},
                voiceIndex(), i -> {
                    settings.setVoiceLang(i == 1 ? "bn-BD" : i == 2 ? "en-US" : "");
                    setValue(voice, voiceLabel());
                }));

        // ---- Data
        header(R.string.sec_data);
        row(getString(R.string.set_export), null, null, true, v -> chooseExport());
        View pr = row(getString(R.string.set_saved_prompts), null, "", true, v -> managePrompts());
        valPrompts = pr.findViewById(R.id.tvSetValue);
        row(getString(R.string.set_clear_cache), getString(R.string.set_clear_cache_sub), null, false, v -> confirm(
                R.string.set_clear_cache, R.string.set_clear_cache_sub, this::clearAttachments));
        row(getString(R.string.set_delete_all), null, null, false, v -> confirm(
                R.string.delete_all_title, R.string.delete_all_msg, this::deleteAllChats));

        // ---- About
        header(R.string.sec_about);
        row(getString(R.string.version_label), null, versionName(), false, null);
        row(getString(R.string.developer_label), null, getString(R.string.developer_name), false, null);
        row(getString(R.string.set_privacy), null, null, true, v -> open(URL_PRIVACY));
        row(getString(R.string.set_licenses), null, null, true, v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.set_licenses).setMessage(R.string.licenses_body)
                .setPositiveButton(android.R.string.ok, null).show());
        row(getString(R.string.set_share), null, null, true, v -> share());
        row(getString(R.string.set_reset), null, null, false, v -> confirm(R.string.reset_title, R.string.reset_msg, () -> {
            settings.resetAll();
            Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show();
            recreate();
        }));

        View bar = inf.inflate(R.layout.item_social_bar, list, false);
        bar.findViewById(R.id.btnSocTelegram).setOnClickListener(v -> open(URL_TELEGRAM));
        bar.findViewById(R.id.btnSocWeb).setOnClickListener(v -> open(URL_WEBSITE));
        bar.findViewById(R.id.btnSocGithub).setOnClickListener(v -> open(URL_GITHUB));
        bar.findViewById(R.id.btnSocDev).setOnClickListener(v -> open(URL_DEVELOPER));
        ((TextView) bar.findViewById(R.id.tvDevLine)).setText(
                getString(R.string.app_name) + " " + versionName() + "  \u00b7  " + getString(R.string.developer_name));
        list.addView(bar);

        refreshValues();
    }

    private void refreshValues() {
        if (valProviders != null) {
            int n = providers.all().size();
            valProviders.setText(n == 0 ? getString(R.string.no_provider_yet) : getString(R.string.providers_count, n));
            valProviders.setVisibility(View.VISIBLE);
        }
        if (valPrompts != null) {
            valPrompts.setText(String.valueOf(PromptStore.get(this).all().size()));
            valPrompts.setVisibility(View.VISIBLE);
        }
    }

    // ------------------------------------------------------------------ row builders

    private void header(int res) {
        TextView t = (TextView) inf.inflate(R.layout.item_setting_header, list, false);
        t.setText(res);
        list.addView(t);
    }

    private View row(String title, String sub, String value, boolean chevron, View.OnClickListener click) {
        View v = inf.inflate(R.layout.item_setting_row, list, false);
        ((TextView) v.findViewById(R.id.tvSetTitle)).setText(title);
        TextView s = v.findViewById(R.id.tvSetSub);
        if (sub != null) {
            s.setText(sub);
            s.setVisibility(View.VISIBLE);
        }
        TextView val = v.findViewById(R.id.tvSetValue);
        if (value != null) {
            val.setText(value);
            val.setVisibility(View.VISIBLE);
        }
        v.findViewById(R.id.ivSetChevron).setVisibility(chevron ? View.VISIBLE : View.GONE);
        if (click != null) v.setOnClickListener(click);
        else v.setBackground(null);
        list.addView(v);
        return v;
    }

    private void setValue(View row, String value) {
        TextView t = row.findViewById(R.id.tvSetValue);
        t.setText(value);
        t.setVisibility(View.VISIBLE);
    }

    private MaterialSwitch toggle(int title, int sub, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        return toggleAt(list.getChildCount(), title, sub, checked, l);
    }

    private MaterialSwitch toggleAt(int index, int title, int sub, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        final View v = inf.inflate(R.layout.item_setting_row, list, false);
        ((TextView) v.findViewById(R.id.tvSetTitle)).setText(title);
        if (sub != 0) {
            TextView s = v.findViewById(R.id.tvSetSub);
            s.setText(sub);
            s.setVisibility(View.VISIBLE);
        }
        final MaterialSwitch sw = v.findViewById(R.id.swSet);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        v.setOnClickListener(x -> sw.toggle());
        list.addView(v, Math.max(0, Math.min(index, list.getChildCount())));
        return sw;
    }

    private interface Formatter {
        String f(float v);
    }

    private interface SliderCb {
        void changed(float v, boolean fromUser);
    }

    private View slider(String title, float min, float max, float step, float value, boolean preview, Formatter fmt, SliderCb cb) {
        final View v = inf.inflate(R.layout.item_setting_slider, list, false);
        ((TextView) v.findViewById(R.id.tvSliderTitle)).setText(title);
        final TextView val = v.findViewById(R.id.tvSliderValue);
        final TextView prev = v.findViewById(R.id.tvSliderPreview);
        Slider s = v.findViewById(R.id.sliderSet);
        s.setValueFrom(min);
        s.setValueTo(max);
        s.setStepSize(step);
        float clamped = Math.max(min, Math.min(max, value));
        s.setValue(Math.round(clamped / step) * step);
        val.setText(fmt.f(s.getValue()));
        if (preview) {
            prev.setVisibility(View.VISIBLE);
            prev.setTextSize(s.getValue());
        }
        s.addOnChangeListener((sl, nv, fromUser) -> {
            val.setText(fmt.f(nv));
            if (preview) prev.setTextSize(nv);
            cb.changed(nv, fromUser);
        });
        list.addView(v);
        return v;
    }

    // ------------------------------------------------------------------ dialogs and actions

    private void choose(int titleRes, String[] labels, int checked, IntCb cb) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    cb.run(which);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirm(int title, int msg, Runnable onYes) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> onYes.run())
                .show();
    }

    private void editSystemPrompt() {
        View v = inf.inflate(R.layout.dialog_text_edit, null);
        final TextInputEditText et = v.findViewById(R.id.etDialogText);
        et.setText(settings.systemPrompt());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.system_prompt_hint_title)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.reset_default, (d, w) -> settings.setSystemPrompt(""))
                .setPositiveButton(R.string.save, (d, w) -> {
                    String t = et.getText() == null ? "" : et.getText().toString().trim();
                    settings.setSystemPrompt(t);
                })
                .show();
    }

    private void chooseExport() {
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row(R.drawable.ic_file, getString(R.string.export_json), () -> exportJson.launch("sentia-chats.json")));
        rows.add(new ActionSheet.Row(R.drawable.ic_file, getString(R.string.export_md), () -> exportMd.launch("sentia-chats.md")));
        ActionSheet.show(this, getString(R.string.set_export), rows);
    }

    private void runExport(final Uri uri, final boolean md) {
        io.execute(() -> {
            final boolean ok = ChatExporter.export(getApplicationContext(), uri, md);
            runOnUiThread(() -> Toast.makeText(this, ok ? R.string.export_done : R.string.export_failed, Toast.LENGTH_SHORT).show());
        });
    }

    private void managePrompts() {
        final PromptStore store = PromptStore.get(this);
        List<SavedPrompt> all = store.all();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.prompts_empty, Toast.LENGTH_LONG).show();
            return;
        }
        List<ActionSheet.Row> rows = new ArrayList<>();
        for (SavedPrompt p : all) {
            final SavedPrompt sp = p;
            rows.add(new ActionSheet.Row(R.drawable.ic_trash, sp.title, () -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete)
                    .setMessage(sp.title)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        store.delete(sp.id);
                        refreshValues();
                    })
                    .show()));
        }
        ActionSheet.show(this, getString(R.string.set_saved_prompts), rows);
    }

    private void clearAttachments() {
        io.execute(() -> {
            File dir = new File(getFilesDir(), "attachments");
            File[] files = dir.listFiles();
            if (files != null) for (File f : files) f.delete();
            runOnUiThread(() -> Toast.makeText(this, R.string.cache_cleared, Toast.LENGTH_SHORT).show());
        });
    }

    private void deleteAllChats() {
        io.execute(() -> {
            AppDatabase.get(getApplicationContext()).sessionDao().deleteAll();
            runOnUiThread(() -> Toast.makeText(this, R.string.chats_deleted, Toast.LENGTH_SHORT).show());
        });
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void share() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, "Sentia, an AI agent app: " + URL_WEBSITE);
        startActivity(Intent.createChooser(i, null));
    }

    // ------------------------------------------------------------------ labels

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName == null ? "" : pi.versionName;
        } catch (Exception e) {
            return "";
        }
    }

    private String themeLabel() {
        switch (settings.themeMode()) {
            case 1: return getString(R.string.theme_light);
            case 2: return getString(R.string.theme_dark);
            default: return getString(R.string.theme_system);
        }
    }

    private String weightLabel() {
        return getString(settings.fontWeight() == 1 ? R.string.font_regular : R.string.font_light);
    }

    private int replyLangIndex() {
        String l = settings.replyLanguage();
        return "en".equals(l) ? 1 : "bn".equals(l) ? 2 : 0;
    }

    private String replyLangLabel() {
        int i = replyLangIndex();
        return getString(i == 1 ? R.string.lang_en : i == 2 ? R.string.lang_bn : R.string.lang_auto);
    }

    private int voiceIndex() {
        String l = settings.voiceLang();
        return "bn-BD".equals(l) ? 1 : "en-US".equals(l) ? 2 : 0;
    }

    private String voiceLabel() {
        int i = voiceIndex();
        return getString(i == 1 ? R.string.voice_bn : i == 2 ? R.string.voice_en : R.string.voice_device);
    }

    private static int nearest(int[] values, int target) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (Math.abs(values[i] - target) < Math.abs(values[best] - target)) best = i;
        }
        return best;
    }
}
