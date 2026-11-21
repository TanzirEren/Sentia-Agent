package com.sentia.ai.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProfileStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Lets the user describe themselves so Sentia can personalise its answers. Everything is saved on the phone. */
public class ProfileActivity extends AppCompatActivity {
    private static final String[] TONE_KEYS = {"default", "friendly", "professional", "casual", "direct", "encouraging"};
    private static final String[] STYLE_KEYS = {"default", "concise", "detailed", "steps"};

    private interface TextCb {
        void set(String value);
    }

    private interface IntCb {
        void run(int index);
    }

    private ProfileStore store;
    private ProfileStore.Data d;
    private LayoutInflater inf;
    private LinearLayout list;
    private LinearLayout projectsBox;
    private ImageView ivAvatar;
    private TextView tvInitial, tvName, tvSub;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private ActivityResultLauncher<String> pickAvatar;
    private ActivityResultLauncher<String> exportProfile;
    private ActivityResultLauncher<String[]> importProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        Bars.apply(this);
        store = ProfileStore.get(this);
        d = store.data();
        inf = LayoutInflater.from(this);
        list = findViewById(R.id.profileList);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        pickAvatar = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri == null) return;
            io.execute(() -> {
                final boolean ok = store.setAvatar(uri);
                runOnUiThread(() -> {
                    if (!ok) Toast.makeText(this, R.string.prof_avatar_failed, Toast.LENGTH_SHORT).show();
                    refreshHeader();
                });
            });
        });
        exportProfile = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) writeProfile(uri);
        });
        importProfile = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) readProfile(uri);
        });
        build();
    }

    @Override
    protected void onPause() {
        store.save();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ screen

    private void build() {
        // avatar and name
        View head = inf.inflate(R.layout.item_profile_header, list, false);
        ivAvatar = head.findViewById(R.id.ivAvatar);
        tvInitial = head.findViewById(R.id.tvAvatarInitial);
        tvName = head.findViewById(R.id.tvProfileName);
        tvSub = head.findViewById(R.id.tvProfileSub);
        ivAvatar.setBackgroundResource(R.drawable.bg_circle_nude);
        ivAvatar.setClipToOutline(true);
        View.OnClickListener pick = v -> pickAvatar.launch("image/*");
        head.findViewById(R.id.btnAvatarEdit).setOnClickListener(pick);
        ivAvatar.setOnClickListener(pick);
        tvInitial.setOnClickListener(pick);
        list.addView(head);
        refreshHeader();

        header(R.string.sec_about_you);
        field(R.string.prof_name, d.name, false, s -> {
            d.name = s;
            refreshHeader();
        });
        field(R.string.prof_role, d.role, false, s -> {
            d.role = s;
            refreshHeader();
        });
        field(R.string.prof_about, d.about, true, s -> d.about = s);
        field(R.string.prof_languages, d.languages, false, s -> d.languages = s);

        header(R.string.sec_skills);
        chips(R.string.prof_skills, R.string.prof_skills_hint, R.string.prof_add_skill_hint, d.skills);
        chips(R.string.prof_interests, R.string.prof_interests_hint, R.string.prof_add_interest_hint, d.interests);

        header(R.string.prof_projects);
        projectsBox = new LinearLayout(this);
        projectsBox.setOrientation(LinearLayout.VERTICAL);
        list.addView(projectsBox);
        renderProjects();
        row(getString(R.string.prof_add_project), null, null, true, v -> addProject());

        header(R.string.sec_ai_prefs);
        final View tone = row(getString(R.string.prof_tone), null, toneLabel(), true, null);
        tone.setOnClickListener(v -> choose(R.string.prof_tone, toneLabels(), indexOf(TONE_KEYS, d.tone), i -> {
            d.tone = TONE_KEYS[i];
            store.save();
            setValue(tone, toneLabel());
        }));
        field(R.string.prof_instructions, d.instructions, true, s -> d.instructions = s);
        final View style = row(getString(R.string.prof_style), null, styleLabel(), true, null);
        style.setOnClickListener(v -> choose(R.string.prof_style, styleLabels(), indexOf(STYLE_KEYS, d.style), i -> {
            d.style = STYLE_KEYS[i];
            store.save();
            setValue(style, styleLabel());
        }));
        toggle(R.string.prof_use, R.string.prof_use_sub, d.useProfile, (b, c) -> {
            d.useProfile = c;
            store.save();
        });

        header(R.string.sec_profile_tools);
        row(getString(R.string.prof_preview), null, null, true, v -> preview());
        row(getString(R.string.prof_export), null, null, true, v -> exportProfile.launch("sentia-profile.json"));
        row(getString(R.string.prof_import), null, null, true,
                v -> importProfile.launch(new String[]{"application/json", "text/plain", "*/*"}));
        row(getString(R.string.prof_clear), null, null, false, v -> new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.prof_clear_title)
                .setMessage(R.string.prof_clear_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dlg, w) -> {
                    store.clear();
                    recreate();
                })
                .show());

        TextView note = new TextView(this);
        note.setText(R.string.prof_private);
        note.setTextColor(ContextCompat.getColor(this, R.color.ink_faint));
        note.setTextSize(12f);
        note.setPadding(dp(20), dp(20), dp(20), 0);
        list.addView(note);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void refreshHeader() {
        String name = d.name == null ? "" : d.name.trim();
        tvName.setText(name.isEmpty() ? getString(R.string.profile_title) : name);
        tvSub.setText(d.role == null ? "" : d.role.trim());
        tvSub.setVisibility(tvSub.getText().length() == 0 ? View.GONE : View.VISIBLE);
        Bitmap bmp = store.hasAvatar() ? BitmapFactory.decodeFile(store.avatarFile().getAbsolutePath()) : null;
        if (bmp != null) {
            ivAvatar.setImageBitmap(bmp);
            tvInitial.setVisibility(View.GONE);
        } else {
            ivAvatar.setImageDrawable(null);
            tvInitial.setVisibility(View.VISIBLE);
            tvInitial.setText(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase());
        }
    }

    // ------------------------------------------------------------------ builders

    private void header(int res) {
        TextView t = (TextView) inf.inflate(R.layout.item_setting_header, list, false);
        t.setText(res);
        list.addView(t);
    }

    private void field(int hint, String value, boolean multiline, final TextCb cb) {
        View v = inf.inflate(R.layout.item_profile_field, list, false);
        TextInputLayout til = v.findViewById(R.id.tilField);
        TextInputEditText et = v.findViewById(R.id.etField);
        til.setHint(getString(hint));
        if (multiline) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            et.setMinLines(3);
            et.setGravity(Gravity.TOP | Gravity.START);
        } else {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            et.setSingleLine(true);
        }
        et.setText(value == null ? "" : value);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                cb.set(s.toString());
                store.save();
            }
        });
        list.addView(v);
    }

    private void chips(int title, int hint, int inputHint, final List<String> items) {
        View v = inf.inflate(R.layout.item_profile_chips, list, false);
        ((TextView) v.findViewById(R.id.tvChipsTitle)).setText(title);
        ((TextView) v.findViewById(R.id.tvChipsHint)).setText(hint);
        final ChipGroup cg = v.findViewById(R.id.cgChips);
        final EditText et = v.findViewById(R.id.etChipInput);
        et.setHint(inputHint);
        for (String s : items) addChip(cg, items, s);
        final Runnable add = () -> {
            String t = et.getText().toString().trim();
            if (t.isEmpty()) return;
            if (!items.contains(t)) {
                items.add(t);
                addChip(cg, items, t);
                store.save();
            }
            et.setText("");
        };
        v.findViewById(R.id.btnChipAdd).setOnClickListener(x -> add.run());
        et.setOnEditorActionListener((tv, id, ev) -> {
            add.run();
            return true;
        });
        list.addView(v);
    }

    private void addChip(final ChipGroup cg, final List<String> items, final String text) {
        final Chip c = new Chip(this);
        c.setText(text);
        c.setCheckable(false);
        c.setCloseIconVisible(true);
        c.setChipBackgroundColorResource(R.color.nude);
        c.setChipStrokeWidth(0f);
        c.setTextColor(ContextCompat.getColor(this, R.color.ink));
        c.setCloseIconTintResource(R.color.ink_muted);
        c.setOnCloseIconClickListener(x -> {
            cg.removeView(c);
            items.remove(text);
            store.save();
        });
        cg.addView(c);
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

    private void toggle(int title, int sub, boolean checked, CompoundButton.OnCheckedChangeListener l) {
        View v = inf.inflate(R.layout.item_setting_row, list, false);
        ((TextView) v.findViewById(R.id.tvSetTitle)).setText(title);
        TextView s = v.findViewById(R.id.tvSetSub);
        s.setText(sub);
        s.setVisibility(View.VISIBLE);
        final MaterialSwitch sw = v.findViewById(R.id.swSet);
        sw.setVisibility(View.VISIBLE);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        v.setOnClickListener(x -> sw.toggle());
        list.addView(v);
    }

    private void choose(int titleRes, String[] labels, int checked, final IntCb cb) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, checked, (dlg, which) -> {
                    dlg.dismiss();
                    cb.run(which);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ projects

    private void renderProjects() {
        projectsBox.removeAllViews();
        for (final ProfileStore.Project p : d.projects) {
            View v = inf.inflate(R.layout.item_profile_project, projectsBox, false);
            ((TextView) v.findViewById(R.id.tvProjName)).setText(p.name);
            TextView desc = v.findViewById(R.id.tvProjDesc);
            if (p.desc == null || p.desc.trim().isEmpty()) desc.setVisibility(View.GONE);
            else desc.setText(p.desc);
            v.findViewById(R.id.btnProjDelete).setOnClickListener(x -> {
                d.projects.remove(p);
                store.save();
                renderProjects();
            });
            projectsBox.addView(v);
        }
    }

    private void addProject() {
        View v = inf.inflate(R.layout.dialog_project, null);
        final TextInputEditText name = v.findViewById(R.id.etProjName);
        final TextInputEditText desc = v.findViewById(R.id.etProjDesc);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.prof_add_project)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    String n = name.getText() == null ? "" : name.getText().toString().trim();
                    if (n.isEmpty()) return;
                    ProfileStore.Project p = new ProfileStore.Project();
                    p.name = n;
                    p.desc = desc.getText() == null ? "" : desc.getText().toString().trim();
                    d.projects.add(p);
                    store.save();
                    renderProjects();
                })
                .show();
    }

    // ------------------------------------------------------------------ preview / import / export

    private void preview() {
        String block = store.promptBlock();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.prof_preview)
                .setMessage(block.isEmpty() ? getString(R.string.prof_preview_empty) : block)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void writeProfile(final Uri uri) {
        io.execute(() -> {
            boolean ok;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new Exception("no stream");
                os.write(store.toJson().getBytes(StandardCharsets.UTF_8));
                ok = true;
            } catch (Exception e) {
                ok = false;
            }
            final boolean res = ok;
            runOnUiThread(() -> Toast.makeText(this, res ? R.string.prof_exported : R.string.export_failed, Toast.LENGTH_SHORT).show());
        });
    }

    private void readProfile(final Uri uri) {
        io.execute(() -> {
            boolean ok = false;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in != null) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    int total = 0;
                    while ((n = in.read(buf)) > 0 && total < 1024 * 1024) {
                        bos.write(buf, 0, n);
                        total += n;
                    }
                    ok = store.importJson(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
            }
            final boolean res = ok;
            runOnUiThread(() -> {
                Toast.makeText(this, res ? R.string.prof_imported : R.string.prof_import_failed, Toast.LENGTH_SHORT).show();
                if (res) recreate();
            });
        });
    }

    // ------------------------------------------------------------------ labels

    private String[] toneLabels() {
        return new String[]{getString(R.string.tone_default), getString(R.string.tone_friendly),
                getString(R.string.tone_professional), getString(R.string.tone_casual),
                getString(R.string.tone_direct), getString(R.string.tone_encouraging)};
    }

    private String[] styleLabels() {
        return new String[]{getString(R.string.style_default), getString(R.string.style_concise),
                getString(R.string.style_detailed), getString(R.string.style_steps)};
    }

    private String toneLabel() {
        return toneLabels()[indexOf(TONE_KEYS, d.tone)];
    }

    private String styleLabel() {
        return styleLabels()[indexOf(STYLE_KEYS, d.style)];
    }

    private static int indexOf(String[] keys, String key) {
        for (int i = 0; i < keys.length; i++) if (keys[i].equals(key)) return i;
        return 0;
    }
}
