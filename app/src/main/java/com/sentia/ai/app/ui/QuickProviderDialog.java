package com.sentia.ai.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProviderStore;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.model.ModelEntry;
import com.sentia.ai.app.model.Provider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Simple add/edit provider dialog. The full provider manager comes with Settings (1B). */
public final class QuickProviderDialog {
    private static final class Preset {
        final String label, name, type, url, models;

        Preset(String label, String name, String type, String url, String models) {
            this.label = label;
            this.name = name;
            this.type = type;
            this.url = url;
            this.models = models;
        }
    }

    private static final Preset[] PRESETS = new Preset[]{
            new Preset("Custom (OpenAI-compatible)", "", Provider.TYPE_OPENAI, "", ""),
            new Preset("OpenRouter", "OpenRouter", Provider.TYPE_OPENAI, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
            new Preset("OpenAI", "OpenAI", Provider.TYPE_OPENAI, "https://api.openai.com/v1", "gpt-4o-mini"),
            new Preset("Anthropic (Claude)", "Anthropic", Provider.TYPE_ANTHROPIC, "https://api.anthropic.com/v1", "claude-sonnet-5-5"),
            new Preset("Google Gemini", "Gemini", Provider.TYPE_OPENAI, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
            new Preset("Groq", "Groq", Provider.TYPE_OPENAI, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
            new Preset("DeepSeek", "DeepSeek", Provider.TYPE_OPENAI, "https://api.deepseek.com/v1", "deepseek-chat"),
            new Preset("Ollama (your network)", "Ollama", Provider.TYPE_OPENAI, "http://192.168.0.10:11434/v1", "llama3.2"),
    };

    private QuickProviderDialog() {}

    /** @param existing null to add a new provider */
    public static void show(Context ctx, Provider existing, Runnable onSaved) {
        final ProviderStore store = ProviderStore.get(ctx);
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_provider, null);
        final Spinner sp = v.findViewById(R.id.spPreset);
        final EditText etName = v.findViewById(R.id.etName);
        final EditText etUrl = v.findViewById(R.id.etUrl);
        final EditText etKey = v.findViewById(R.id.etKey);
        final EditText etModels = v.findViewById(R.id.etModels);

        List<String> labels = new ArrayList<>();
        for (Preset p : PRESETS) labels.add(p.label);
        ArrayAdapter<String> ad = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_dropdown_item, labels);
        sp.setAdapter(ad);

        final String[] typeHolder = {Provider.TYPE_OPENAI};

        if (existing != null) {
            etName.setText(existing.name);
            etUrl.setText(existing.baseUrl);
            etKey.setText(existing.firstKey());
            StringBuilder sb = new StringBuilder();
            for (ModelEntry m : existing.models) sb.append(m.id).append('\n');
            etModels.setText(sb.toString().trim());
            typeHolder[0] = existing.type;
            sp.setEnabled(false);
        } else {
            sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Preset p = PRESETS[position];
                    etName.setText(p.name);
                    etUrl.setText(p.url);
                    etModels.setText(p.models);
                    typeHolder[0] = p.type;
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(ctx)
                .setTitle(existing == null ? R.string.add_provider : R.string.edit_provider)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null);
        if (existing != null) {
            b.setNeutralButton(R.string.delete, (dialog, which) -> {
                store.delete(existing.id);
                SettingsStore s = SettingsStore.get(ctx);
                if (existing.id.equals(s.selectedProviderId())) s.setSelected("", "");
                if (onSaved != null) onSaved.run();
            });
        }
        final AlertDialog dialog = b.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            String key = etKey.getText().toString().trim();
            String[] lines = etModels.getText().toString().split("\n");
            if (name.isEmpty()) {
                Toast.makeText(ctx, R.string.provider_need_name, Toast.LENGTH_SHORT).show();
                return;
            }
            if (url.isEmpty()) {
                Toast.makeText(ctx, R.string.provider_need_url, Toast.LENGTH_SHORT).show();
                return;
            }
            Set<String> ids = new LinkedHashSet<>();
            for (String l : lines) {
                String t = l.trim();
                if (!t.isEmpty()) ids.add(t);
            }
            if (ids.isEmpty()) {
                Toast.makeText(ctx, R.string.provider_need_model, Toast.LENGTH_SHORT).show();
                return;
            }
            Provider p = existing != null ? existing : new Provider();
            p.name = name;
            p.baseUrl = url;
            p.type = typeHolder[0];
            p.apiKeys = new ArrayList<>();
            if (!key.isEmpty()) p.apiKeys.add(key);
            List<ModelEntry> models = new ArrayList<>();
            for (String id : ids) {
                ModelEntry old = null;
                if (existing != null) {
                    for (ModelEntry m : existing.models) if (id.equals(m.id)) old = m;
                }
                ModelEntry me = old != null ? old : new ModelEntry(id);
                models.add(me);
            }
            p.models = models;
            store.save(p);
            SettingsStore s = SettingsStore.get(ctx);
            if (s.selectedProviderId().isEmpty() || store.get(s.selectedProviderId()) == null) {
                s.setSelected(p.id, models.get(0).id);
            }
            dialog.dismiss();
            if (onSaved != null) onSaved.run();
        });
    }
}
