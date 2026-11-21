package com.sentia.ai.app.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProviderPresets;
import com.sentia.ai.app.data.ProviderStore;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.model.ModelEntry;
import com.sentia.ai.app.model.Provider;
import com.sentia.ai.app.net.ModelFetcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-height dialog to add or edit a provider: keys, models, fetch, free tags, select all/free. */
public final class ProviderSheet {
    private ProviderSheet() {}

    public static void show(Context ctx, Provider existing, Runnable onChanged) {
        new Editor(ctx, existing, onChanged).open();
    }

    private static final class ModelVH extends RecyclerView.ViewHolder {
        final CheckBox cb;
        final TextView id;
        final TextView free;

        ModelVH(View v) {
            super(v);
            cb = v.findViewById(R.id.cbModel);
            id = v.findViewById(R.id.tvEditModelId);
            free = v.findViewById(R.id.tvEditFree);
        }
    }

    private static final class Editor {
        private final Context ctx;
        private final Provider work;
        private final boolean isNew;
        private final Runnable onChanged;
        private final ProviderStore store;
        private final SettingsStore settings;
        private final Handler main = new Handler(Looper.getMainLooper());
        private final ExecutorService io = Executors.newSingleThreadExecutor();
        private final List<ModelEntry> models;
        private final List<ModelEntry> visible = new ArrayList<>();

        private BottomSheetDialog dialog;
        private View root;
        private TextView tvPreset, chipOpenAi, chipAnthropic, tvModelCount, tvFetchStatus, tvNoModels, tvFetch;
        private EditText etName, etUrl, etManual, etFilter;
        private LinearLayout llKeys;
        private ProgressBar pb;
        private RecyclerView rv;
        private RecyclerView.Adapter<ModelVH> adapter;
        private String type;
        private boolean fetching;

        Editor(Context ctx, Provider existing, Runnable onChanged) {
            this.ctx = ctx;
            this.isNew = existing == null;
            this.work = existing != null ? existing.copy() : new Provider();
            if (work.models == null) work.models = new ArrayList<>();
            if (work.apiKeys == null) work.apiKeys = new ArrayList<>();
            this.models = work.models;
            this.onChanged = onChanged;
            this.store = ProviderStore.get(ctx);
            this.settings = SettingsStore.get(ctx);
            this.type = work.type == null ? Provider.TYPE_OPENAI : work.type;
        }

        void open() {
            dialog = new BottomSheetDialog(ctx);
            root = LayoutInflater.from(ctx).inflate(R.layout.sheet_provider, null);
            int h = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.93f);
            root.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));

            tvPreset = root.findViewById(R.id.tvPreset);
            chipOpenAi = root.findViewById(R.id.chipOpenAi);
            chipAnthropic = root.findViewById(R.id.chipAnthropic);
            tvModelCount = root.findViewById(R.id.tvModelCount);
            tvFetchStatus = root.findViewById(R.id.tvFetchStatus);
            tvNoModels = root.findViewById(R.id.tvNoModels);
            tvFetch = root.findViewById(R.id.tvFetch);
            etName = root.findViewById(R.id.etProvName);
            etUrl = root.findViewById(R.id.etProvUrl);
            etManual = root.findViewById(R.id.etManualModels);
            etFilter = root.findViewById(R.id.etModelFilter);
            llKeys = root.findViewById(R.id.llKeys);
            pb = root.findViewById(R.id.pbFetch);
            rv = root.findViewById(R.id.rvEditModels);

            ((TextView) root.findViewById(R.id.tvProvTitle)).setText(isNew ? R.string.add_provider : R.string.edit_provider);
            etName.setText(work.name);
            etUrl.setText(work.baseUrl);

            if (work.apiKeys.isEmpty()) addKeyRow("");
            else for (String k : work.apiKeys) addKeyRow(k);

            setupModelList();
            updateTypeChips();

            if (!isNew) root.findViewById(R.id.presetRow).setVisibility(View.GONE);
            root.findViewById(R.id.presetRow).setOnClickListener(v -> showPresets());
            chipOpenAi.setOnClickListener(v -> {
                type = Provider.TYPE_OPENAI;
                updateTypeChips();
            });
            chipAnthropic.setOnClickListener(v -> {
                type = Provider.TYPE_ANTHROPIC;
                updateTypeChips();
            });
            root.findViewById(R.id.tvAddKey).setOnClickListener(v -> addKeyRow(""));
            root.findViewById(R.id.tvAddModels).setOnClickListener(v -> addManualModels());
            tvFetch.setOnClickListener(v -> fetch());
            root.findViewById(R.id.tvSelAll).setOnClickListener(v -> bulk(0));
            root.findViewById(R.id.tvSelFree).setOnClickListener(v -> bulk(1));
            root.findViewById(R.id.tvSelNone).setOnClickListener(v -> bulk(2));
            etFilter.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    refreshVisible();
                }
            });
            root.findViewById(R.id.btnProvClose).setOnClickListener(v -> dialog.dismiss());
            root.findViewById(R.id.btnProvSave).setOnClickListener(v -> save());
            View del = root.findViewById(R.id.btnProvDelete);
            if (!isNew) {
                del.setVisibility(View.VISIBLE);
                del.setOnClickListener(v -> confirmDelete());
            }

            dialog.setContentView(root);
            dialog.setOnDismissListener(d -> io.shutdown());
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            BottomSheetBehavior<FrameLayout> b = dialog.getBehavior();
            b.setState(BottomSheetBehavior.STATE_EXPANDED);
            b.setSkipCollapsed(true);
            b.setDraggable(false);
            dialog.show();
        }

        // ------------------------------------------------------------ presets / type

        private void showPresets() {
            List<ActionSheet.Row> rows = new ArrayList<>();
            for (ProviderPresets.Preset p : ProviderPresets.all()) {
                final ProviderPresets.Preset pr = p;
                String label = p.note.isEmpty() ? p.name : p.name + "  \u00b7  " + p.note;
                rows.add(new ActionSheet.Row(R.drawable.ic_chat, label, () -> applyPreset(pr)));
            }
            ActionSheet.show(ctx, ctx.getString(R.string.choose_preset), rows);
        }

        private void applyPreset(ProviderPresets.Preset p) {
            tvPreset.setText(p.name);
            if (!p.name.startsWith("Custom")) etName.setText(p.name);
            etUrl.setText(p.baseUrl);
            type = p.type;
            updateTypeChips();
            if (!p.note.isEmpty()) {
                tvFetchStatus.setText(p.note);
                tvFetchStatus.setVisibility(View.VISIBLE);
            }
        }

        private void updateTypeChips() {
            style(chipOpenAi, Provider.TYPE_OPENAI.equals(type));
            style(chipAnthropic, Provider.TYPE_ANTHROPIC.equals(type));
        }

        private void style(TextView t, boolean selected) {
            t.setBackgroundResource(selected ? R.drawable.bg_pill_ink : R.drawable.bg_pill_nude);
            t.setTextColor(ContextCompat.getColor(ctx, selected ? R.color.on_ink : R.color.ink));
        }

        // ------------------------------------------------------------ keys

        private void addKeyRow(String value) {
            final View row = LayoutInflater.from(ctx).inflate(R.layout.item_provider_key, llKeys, false);
            TextInputEditText et = row.findViewById(R.id.etKeyItem);
            et.setText(value);
            row.findViewById(R.id.btnKeyDelete).setOnClickListener(v -> {
                llKeys.removeView(row);
                if (llKeys.getChildCount() == 0) addKeyRow("");
            });
            llKeys.addView(row);
        }

        private List<String> collectKeys() {
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < llKeys.getChildCount(); i++) {
                TextInputEditText et = llKeys.getChildAt(i).findViewById(R.id.etKeyItem);
                String k = et.getText() == null ? "" : et.getText().toString().trim();
                if (!k.isEmpty() && !keys.contains(k)) keys.add(k);
            }
            return keys;
        }

        // ------------------------------------------------------------ models

        private void setupModelList() {
            rv.setLayoutManager(new LinearLayoutManager(ctx));
            adapter = new RecyclerView.Adapter<ModelVH>() {
                @NonNull
                @Override
                public ModelVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    return new ModelVH(LayoutInflater.from(ctx).inflate(R.layout.item_model_edit, parent, false));
                }

                @Override
                public void onBindViewHolder(@NonNull ModelVH h, int position) {
                    final ModelEntry m = visible.get(position);
                    h.id.setText(m.id);
                    h.cb.setChecked(m.enabled);
                    h.free.setVisibility(m.free ? View.VISIBLE : View.GONE);
                    h.itemView.setOnClickListener(v -> {
                        m.enabled = !m.enabled;
                        h.cb.setChecked(m.enabled);
                        updateCount();
                    });
                }

                @Override
                public int getItemCount() {
                    return visible.size();
                }
            };
            rv.setAdapter(adapter);
            refreshVisible();
        }

        private void refreshVisible() {
            String q = etFilter.getText().toString().trim().toLowerCase(Locale.ROOT);
            visible.clear();
            for (ModelEntry m : models) {
                if (q.isEmpty() || m.id.toLowerCase(Locale.ROOT).contains(q)) visible.add(m);
            }
            adapter.notifyDataSetChanged();
            tvNoModels.setVisibility(models.isEmpty() ? View.VISIBLE : View.GONE);
            updateCount();
        }

        private void updateCount() {
            int on = 0;
            for (ModelEntry m : models) if (m.enabled) on++;
            tvModelCount.setText(ctx.getString(R.string.models_selected, on, models.size()));
        }

        private void addManualModels() {
            String[] lines = etManual.getText().toString().split("\n");
            Set<String> have = new HashSet<>();
            for (ModelEntry m : models) have.add(m.id);
            List<ModelEntry> added = new ArrayList<>();
            for (String l : lines) {
                String t = l.trim();
                if (t.isEmpty() || have.contains(t)) continue;
                have.add(t);
                added.add(new ModelEntry(t));
            }
            models.addAll(0, added);
            etManual.setText("");
            refreshVisible();
        }

        /** 0 = select all shown, 1 = only free, 2 = clear */
        private void bulk(int mode) {
            for (ModelEntry m : visible) {
                if (mode == 0) m.enabled = true;
                else if (mode == 1) m.enabled = m.free;
                else m.enabled = false;
            }
            adapter.notifyDataSetChanged();
            updateCount();
        }

        private void fetch() {
            if (fetching) return;
            final String url = etUrl.getText().toString().trim();
            if (url.isEmpty()) {
                Toast.makeText(ctx, R.string.fetch_need_url, Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> keys = collectKeys();
            final String key = keys.isEmpty() ? "" : keys.get(0);
            final String t = type;
            fetching = true;
            pb.setVisibility(View.VISIBLE);
            tvFetchStatus.setVisibility(View.VISIBLE);
            tvFetchStatus.setText(R.string.fetching);
            io.execute(() -> {
                final ModelFetcher.Result r = ModelFetcher.fetch(url, t, key);
                main.post(() -> {
                    fetching = false;
                    pb.setVisibility(View.GONE);
                    if (!dialog.isShowing()) return;
                    if (r.error != null) {
                        tvFetchStatus.setText(r.error);
                        return;
                    }
                    Map<String, ModelEntry> byId = new HashMap<>();
                    for (ModelEntry m : models) byId.put(m.id, m);
                    int total = 0, free = 0;
                    for (ModelEntry f : r.models) {
                        total++;
                        if (f.free) free++;
                        ModelEntry ex = byId.get(f.id);
                        if (ex != null) {
                            ex.free = f.free;
                        } else {
                            models.add(f);
                            byId.put(f.id, f);
                        }
                    }
                    tvFetchStatus.setText(ctx.getString(R.string.fetch_ok, total, free));
                    refreshVisible();
                });
            });
        }

        // ------------------------------------------------------------ save / delete

        private void save() {
            String name = etName.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(ctx, R.string.provider_need_name, Toast.LENGTH_SHORT).show();
                return;
            }
            if (url.isEmpty()) {
                Toast.makeText(ctx, R.string.provider_need_url, Toast.LENGTH_SHORT).show();
                return;
            }
            addManualModels();
            int enabled = 0;
            String firstEnabled = null;
            for (ModelEntry m : models) {
                if (m.enabled) {
                    enabled++;
                    if (firstEnabled == null) firstEnabled = m.id;
                }
            }
            if (enabled == 0) {
                Toast.makeText(ctx, R.string.provider_need_enabled, Toast.LENGTH_SHORT).show();
                return;
            }
            List<String> keys = collectKeys();
            work.name = name;
            work.baseUrl = url;
            work.type = type;
            work.apiKeys = keys;
            work.models = models;
            if (work.activeKey >= keys.size()) work.activeKey = 0;
            if (work.keyBlockedUntil != null) work.keyBlockedUntil.clear();
            if (isNew) work.enabled = true;
            store.save(work);

            Provider sel = store.get(settings.selectedProviderId());
            String selModel = settings.selectedModelId();
            boolean selOk = false;
            if (sel != null && sel.enabled) {
                for (ModelEntry m : sel.models) if (m.enabled && m.id.equals(selModel)) selOk = true;
            }
            if (!selOk) settings.setSelected(work.id, firstEnabled);

            Toast.makeText(ctx, R.string.provider_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            if (onChanged != null) onChanged.run();
        }

        private void confirmDelete() {
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.delete)
                    .setMessage(work.name)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (d, w) -> {
                        store.delete(work.id);
                        if (work.id.equals(settings.selectedProviderId())) settings.setSelected("", "");
                        Toast.makeText(ctx, R.string.provider_deleted, Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        if (onChanged != null) onChanged.run();
                    })
                    .show();
        }
    }
}
