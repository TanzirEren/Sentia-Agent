package com.sentia.ai.app.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.util.Fmt;
import com.sentia.ai.app.workspace.DraftStore;
import com.sentia.ai.app.workspace.FileKinds;
import com.sentia.ai.app.workspace.RecentFiles;
import com.sentia.ai.app.workspace.WorkspaceFs;
import com.sentia.ai.app.workspace.WorkspaceManager;
import com.sentia.ai.app.workspace.WsEntry;
import com.sentia.ai.app.workspace.WsErrors;
import com.sentia.ai.app.workspace.WsException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Opens one workspace file: code editor (Sora), image viewer, or a short "cannot open" message. */
public class EditorActivity extends AppCompatActivity {
    public static final String EXTRA_PROJECT = "project_id";
    public static final String EXTRA_PATH = "path";

    private static final String[] SYMBOLS = {"{", "}", "(", ")", "[", "]", "<", ">", ";", ":", "\"", "'", "=", "/",
            "\\", "#", "!", "&", "|", "*", "+", "-", "_", ",", ".", "$", "@", "?", "%", "~", "`", "^"};

    private long projectId;
    private String path;
    private String fileName;
    private SettingsStore settings;
    private DraftStore drafts;
    private RecentFiles recent;
    private final Handler main = new Handler(Looper.getMainLooper());

    private CodeEditorView editor;
    private ZoomImageView viewer;
    private View dotDirty, findBar, replaceRow, stateBox, symbolScroll, statusBar, pbLoading;
    private ImageButton btnUndo, btnRedo, btnSave;
    private TextView tvCursor, tvInfo, tvFindCount, chipCase, chipRegex;
    private EditText etFind, etReplace;

    private boolean loaded;
    private boolean textMode;
    private boolean dirty;
    private boolean readOnly;
    private boolean saving;
    private boolean crlf, bom;
    private long baseModified, baseSize;
    private boolean caseOn, regexOn;
    private boolean askingExternal;

    private final Runnable draftRunner = new Runnable() {
        @Override
        public void run() {
            writeDraft();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId = getIntent().getLongExtra(EXTRA_PROJECT, 0);
        path = getIntent().getStringExtra(EXTRA_PATH);
        if (projectId == 0 || path == null || path.isEmpty()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_editor);
        Bars.apply(this);
        settings = SettingsStore.get(this);
        drafts = new DraftStore(this);
        recent = new RecentFiles(this);
        fileName = WorkspaceFs.lastSegment(path);

        bindViews();
        ((TextView) findViewById(R.id.tvFileName)).setText(fileName);
        ((TextView) findViewById(R.id.tvFilePath)).setText(path);
        setupEditor();
        setupSymbols();
        setupFind();
        setupButtons();
        setupBack();
        load();
    }

    private void bindViews() {
        editor = findViewById(R.id.codeEditor);
        viewer = findViewById(R.id.ivViewer);
        dotDirty = findViewById(R.id.dotDirty);
        findBar = findViewById(R.id.findBar);
        replaceRow = findViewById(R.id.replaceRow);
        stateBox = findViewById(R.id.stateBox);
        symbolScroll = findViewById(R.id.symbolScroll);
        statusBar = findViewById(R.id.statusBar);
        pbLoading = findViewById(R.id.pbLoading);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);
        btnSave = findViewById(R.id.btnSave);
        tvCursor = findViewById(R.id.tvCursor);
        tvInfo = findViewById(R.id.tvInfo);
        tvFindCount = findViewById(R.id.tvFindCount);
        chipCase = findViewById(R.id.chipCase);
        chipRegex = findViewById(R.id.chipRegex);
        etFind = findViewById(R.id.etFind);
        etReplace = findViewById(R.id.etReplace);
        etFind.setSaveEnabled(false);
        etReplace.setSaveEnabled(false);
    }

    private void setupEditor() {
        editor.setListener(new CodeEditorView.Listener() {
            @Override
            public void onContentChanged() {
                if (!loaded) return;
                setDirty(true);
                updateUndoRedo();
                main.removeCallbacks(draftRunner);
                main.postDelayed(draftRunner, 1500);
            }

            @Override
            public void onCursor(int line, int column) {
                tvCursor.setText(getString(R.string.ed_cursor, line, column));
            }
        });
        editor.setFontSizeSp(settings.editorTextSizeSp());
        editor.setWordWrap(settings.editorWordWrap());
        editor.setLineNumbers(settings.editorLineNumbers());
        editor.setTabSize(settings.editorTabSize());
        Typeface tf = null;
        if (settings.editorFont() == 1) tf = ResourcesCompat.getFont(this, R.font.jetbrains_mono);
        editor.setMonoTypeface(tf == null ? Typeface.MONOSPACE : tf);
    }

    private void setupButtons() {
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackRequested());
        findViewById(R.id.btnMore).setOnClickListener(v -> showMenu());
        findViewById(R.id.btnStateClose).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save(null));
        btnUndo.setOnClickListener(v -> {
            editor.undo();
            updateUndoRedo();
        });
        btnRedo.setOnClickListener(v -> {
            editor.redo();
            updateUndoRedo();
        });
    }

    private void setupBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackRequested();
            }
        });
    }

    private void onBackRequested() {
        if (findBar.getVisibility() == View.VISIBLE) {
            closeFind();
            return;
        }
        if (dirty && textMode) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.ed_unsaved_title)
                    .setMessage(getString(R.string.ed_unsaved_msg, fileName))
                    .setNegativeButton(R.string.cancel, null)
                    .setNeutralButton(R.string.ed_discard, (d, w) -> {
                        drafts.delete(projectId, path);
                        dirty = false;
                        finish();
                    })
                    .setPositiveButton(R.string.save, (d, w) -> save(this::finish))
                    .show();
            return;
        }
        finish();
    }

    // ------------------------------------------------------------ loading

    private void load() {
        pbLoading.setVisibility(View.VISIBLE);
        final long id = projectId;
        final String p = path;
        WorkspaceManager.run(() -> {
            try {
                WorkspaceFs fs = WorkspaceManager.fs(this, id, true);
                WsEntry st = fs.stat(p);
                if (st == null) {
                    showStateLater(R.string.ed_missing_title, R.string.ed_missing_sub);
                    return;
                }
                if (st.dir) {
                    showStateLater(R.string.ed_folder_title, R.string.ed_folder_sub);
                    return;
                }
                int kind = FileKinds.kind(fileName);
                if (kind == FileKinds.IMAGE) {
                    loadImage(fs, st);
                    return;
                }
                if (kind == FileKinds.BINARY || fs.looksBinary(p)) {
                    showStateLater(R.string.ed_binary_title, R.string.ed_binary_sub);
                    return;
                }
                if (st.size > WorkspaceFs.MAX_TEXT_BYTES) {
                    showStateLater(R.string.ed_toolarge_title, R.string.ed_toolarge_sub);
                    return;
                }
                final WorkspaceFs.TextDoc doc = fs.readText(p);
                EditorEngine.ensure(getApplicationContext());
                final boolean large = doc.size > WorkspaceFs.SOFT_TEXT_BYTES;
                WorkspaceManager.post(() -> onTextLoaded(doc, large));
            } catch (WsException e) {
                if (e.code == WsException.BINARY || e.code == WsException.NOT_UTF8) {
                    showStateLater(R.string.ed_binary_title, R.string.ed_binary_sub);
                } else {
                    final String m = WsErrors.text(this, e);
                    WorkspaceManager.post(() -> showState(getString(R.string.ed_error_title), m));
                }
            }
        });
    }

    private void showStateLater(final int title, final int sub) {
        WorkspaceManager.post(() -> showState(getString(title), getString(sub)));
    }

    private void showState(String title, String sub) {
        if (isFinishing() || isDestroyed()) return;
        pbLoading.setVisibility(View.GONE);
        editor.setVisibility(View.GONE);
        viewer.setVisibility(View.GONE);
        symbolScroll.setVisibility(View.GONE);
        stateBox.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvStateTitle)).setText(title);
        ((TextView) findViewById(R.id.tvStateSub)).setText(sub);
    }

    private void loadImage(WorkspaceFs fs, WsEntry st) throws WsException {
        File f = fs.resolve(st.path);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        final int w = o.outWidth, h = o.outHeight;
        int sample = 1;
        while (w / sample > 2048 || h / sample > 2048) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        final Bitmap bmp = BitmapFactory.decodeFile(f.getPath(), o);
        final long size = st.size;
        WorkspaceManager.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (bmp == null) {
                showState(getString(R.string.ed_image_fail_title), getString(R.string.ed_image_fail_sub));
                return;
            }
            pbLoading.setVisibility(View.GONE);
            editor.setVisibility(View.GONE);
            symbolScroll.setVisibility(View.GONE);
            findViewById(R.id.btnMore).setVisibility(View.GONE);
            viewer.setImageBitmap(bmp);
            viewer.setVisibility(View.VISIBLE);
            statusBar.setVisibility(View.VISIBLE);
            tvCursor.setText(w + " x " + h);
            tvInfo.setText(Fmt.bytes(size));
            loaded = true;
        });
    }

    private void onTextLoaded(final WorkspaceFs.TextDoc doc, boolean large) {
        if (isFinishing() || isDestroyed()) return;
        if (large) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.ed_large_title)
                    .setMessage(getString(R.string.ed_large_msg, Fmt.bytes(doc.size)))
                    .setCancelable(false)
                    .setNegativeButton(R.string.cancel, (d, w) -> finish())
                    .setNeutralButton(R.string.ed_edit_anyway, (d, w) -> showText(doc, false))
                    .setPositiveButton(R.string.ed_read_only, (d, w) -> showText(doc, true))
                    .show();
        } else {
            showText(doc, false);
        }
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private void showText(final WorkspaceFs.TextDoc doc, boolean ro) {
        crlf = doc.crlf;
        bom = doc.bom;
        baseModified = doc.modified;
        baseSize = doc.size;
        readOnly = ro;
        textMode = true;
        editor.applyLook(fileName, isNight(), getColor(R.color.surface), getColor(R.color.ink),
                getColor(R.color.ink_faint), getColor(R.color.line));
        editor.setContent(doc.text);
        editor.setReadOnly(ro);
        pbLoading.setVisibility(View.GONE);
        editor.setVisibility(View.VISIBLE);
        statusBar.setVisibility(View.VISIBLE);
        loaded = true;
        applyEditableUi();
        recent.add(projectId, path);

        final DraftStore.Draft draft = drafts.load(projectId, path);
        if (draft != null) {
            if (draft.text.equals(doc.text.replace("\r\n", "\n")) || ro) {
                drafts.delete(projectId, path);
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.ed_draft_title)
                        .setMessage(R.string.ed_draft_msg)
                        .setCancelable(false)
                        .setNegativeButton(R.string.ed_discard, (d, w) -> drafts.delete(projectId, path))
                        .setPositiveButton(R.string.ed_restore, (d, w) -> {
                            editor.setContent(draft.text);
                            setDirty(true);
                            updateUndoRedo();
                        })
                        .show();
            }
        }
    }

    /** Shows or hides the buttons that only make sense when the file can be edited. */
    private void applyEditableUi() {
        int vis = readOnly ? View.GONE : View.VISIBLE;
        btnUndo.setVisibility(vis);
        btnRedo.setVisibility(vis);
        btnSave.setVisibility(vis);
        symbolScroll.setVisibility(vis);
        replaceRow.setVisibility(vis);
        updateUndoRedo();
        setDirty(dirty);
        tvInfo.setText(infoLine());
    }

    private String infoLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(bom ? "UTF-8 BOM" : "UTF-8").append("  \u00b7  ").append(crlf ? "CRLF" : "LF")
                .append("  \u00b7  ").append(EditorEngine.languageLabel(fileName));
        if (readOnly) sb.append("  \u00b7  ").append(getString(R.string.ed_read_only));
        return sb.toString();
    }

    // ------------------------------------------------------------ dirty, undo, draft

    private void setDirty(boolean d) {
        dirty = d;
        dotDirty.setVisibility(d ? View.VISIBLE : View.GONE);
        btnSave.setAlpha(d ? 1f : 0.35f);
    }

    private void updateUndoRedo() {
        btnUndo.setAlpha(editor.canUndo() ? 1f : 0.35f);
        btnRedo.setAlpha(editor.canRedo() ? 1f : 0.35f);
    }

    private void writeDraft() {
        if (!loaded || !dirty || !textMode || readOnly) return;
        final String text = editor.getContent();
        final long base = baseModified;
        final boolean c = crlf;
        final long id = projectId;
        final String p = path;
        WorkspaceManager.run(() -> drafts.save(id, p, base, c, text));
    }

    // ------------------------------------------------------------ save

    private void save(final Runnable after) {
        if (!loaded || !textMode || readOnly || saving) return;
        saving = true;
        final String text = editor.getContent();
        final long id = projectId;
        final String p = path;
        final boolean c = crlf, b = bom;
        WorkspaceManager.run(() -> {
            String err = null;
            long mod = 0, size = 0;
            try {
                WorkspaceFs fs = WorkspaceManager.fs(this, id, true);
                fs.writeText(p, text, c, b);
                WsEntry st = fs.stat(p);
                if (st != null) {
                    mod = st.modified;
                    size = st.size;
                }
            } catch (WsException e) {
                err = WsErrors.text(this, e);
            }
            final String fErr = err;
            final long fMod = mod, fSize = size;
            WorkspaceManager.post(() -> {
                saving = false;
                if (fErr != null) {
                    Toast.makeText(this, fErr, Toast.LENGTH_LONG).show();
                    return;
                }
                baseModified = fMod;
                baseSize = fSize;
                setDirty(false);
                main.removeCallbacks(draftRunner);
                drafts.delete(id, p);
                Toast.makeText(this, R.string.ed_saved, Toast.LENGTH_SHORT).show();
                if (after != null) after.run();
            });
        });
    }

    // ------------------------------------------------------------ external changes

    @Override
    protected void onResume() {
        super.onResume();
        if (!loaded || !textMode || saving || askingExternal) return;
        final long id = projectId;
        final String p = path;
        WorkspaceManager.run(() -> {
            try {
                final WsEntry st = WorkspaceManager.fs(this, id, true).stat(p);
                WorkspaceManager.post(() -> {
                    if (st == null) {
                        Toast.makeText(this, R.string.ed_deleted_outside, Toast.LENGTH_LONG).show();
                    } else if (st.modified != baseModified || st.size != baseSize) {
                        onExternalChange(st);
                    }
                });
            } catch (WsException e) {
                // nothing to compare
            }
        });
    }

    private void onExternalChange(final WsEntry st) {
        if (isFinishing() || isDestroyed() || askingExternal) return;
        if (!dirty) {
            reload();
            Toast.makeText(this, R.string.ed_reloaded, Toast.LENGTH_SHORT).show();
            return;
        }
        askingExternal = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ed_changed_title)
                .setMessage(R.string.ed_changed_msg)
                .setCancelable(false)
                .setNegativeButton(R.string.ed_keep_mine, (d, w) -> {
                    baseModified = st.modified;
                    baseSize = st.size;
                    askingExternal = false;
                })
                .setPositiveButton(R.string.ed_reload, (d, w) -> {
                    askingExternal = false;
                    reload();
                })
                .show();
    }

    private void reload() {
        final long id = projectId;
        final String p = path;
        WorkspaceManager.run(() -> {
            try {
                final WorkspaceFs.TextDoc doc = WorkspaceManager.fs(this, id, true).readText(p);
                WorkspaceManager.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    crlf = doc.crlf;
                    bom = doc.bom;
                    baseModified = doc.modified;
                    baseSize = doc.size;
                    editor.setContent(doc.text);
                    setDirty(false);
                    drafts.delete(id, p);
                    updateUndoRedo();
                    tvInfo.setText(infoLine());
                });
            } catch (WsException e) {
                final String m = WsErrors.text(this, e);
                WorkspaceManager.post(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
            }
        });
    }

    // ------------------------------------------------------------ menu

    private void showMenu() {
        if (!loaded || !textMode) return;
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row(R.drawable.ic_search, getString(R.string.ed_find_replace), this::openFind));
        rows.add(new ActionSheet.Row(R.drawable.ic_arrow_down, getString(R.string.ed_goto), this::askGoto));
        rows.add(new ActionSheet.Row(R.drawable.ic_wrap, getString(editor.isWordWrap() ? R.string.ed_wrap_off : R.string.ed_wrap_on), () -> {
            boolean on = !editor.isWordWrap();
            editor.setWordWrap(on);
            settings.setEditorWordWrap(on);
        }));
        rows.add(new ActionSheet.Row(R.drawable.ic_edit, getString(readOnly ? R.string.ed_make_editable : R.string.ed_make_readonly), () -> {
            readOnly = !readOnly;
            editor.setReadOnly(readOnly);
            applyEditableUi();
        }));
        rows.add(new ActionSheet.Row(R.drawable.ic_refresh, getString(R.string.ed_reload_disk), () -> {
            if (dirty) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.ed_reload_disk)
                        .setMessage(R.string.ed_reload_lose)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.ed_reload, (d, w) -> reload())
                        .show();
            } else {
                reload();
            }
        }));
        rows.add(new ActionSheet.Row(R.drawable.ic_copy, getString(R.string.ws_copy_path), () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("path", path));
                Toast.makeText(this, R.string.ws_path_copied, Toast.LENGTH_SHORT).show();
            }
        }));
        ActionSheet.show(this, fileName, rows);
    }

    private void askGoto() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_name, null);
        final EditText et = v.findViewById(R.id.etName);
        et.setSaveEnabled(false);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.ed_goto_title, editor.lineCount()))
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ed_go, (d, w) -> {
                    try {
                        editor.goToLine(Integer.parseInt(et.getText().toString().trim()));
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, R.string.ed_goto_bad, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // ------------------------------------------------------------ symbols

    private void setupSymbols() {
        LinearLayout row = findViewById(R.id.symbolRow);
        row.setSaveFromParentEnabled(false);
        row.addView(symbolButton(getString(R.string.ed_tab), true, null));
        for (String s : SYMBOLS) row.addView(symbolButton(s, false, s));
    }

    private View symbolButton(String label, boolean tab, final String insert) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(getColor(R.color.ink));
        t.setTextSize(tab ? 13 : 17);
        t.setTypeface(Typeface.MONOSPACE);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(tab ? 52 : 38));
        t.setPadding(dp(6), 0, dp(6), 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        t.setBackgroundResource(android.R.color.transparent);
        t.setOnClickListener(v -> {
            if (!loaded || readOnly) return;
            if (tab) {
                int n = settings.editorTabSize();
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) sb.append(' ');
                editor.insert(sb.toString(), n);
            } else {
                editor.insert(insert, insert.length());
            }
        });
        return t;
    }

    // ------------------------------------------------------------ find and replace

    private void setupFind() {
        etFind.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                runFind();
            }
        });
        etFind.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                editor.findNext();
                updateFindCount();
                return true;
            }
            return false;
        });
        findViewById(R.id.btnFindNext).setOnClickListener(v -> {
            editor.findNext();
            updateFindCount();
        });
        findViewById(R.id.btnFindPrev).setOnClickListener(v -> {
            editor.findPrevious();
            updateFindCount();
        });
        findViewById(R.id.btnFindClose).setOnClickListener(v -> closeFind());
        findViewById(R.id.btnReplace).setOnClickListener(v -> {
            editor.replaceCurrent(etReplace.getText().toString());
            updateFindCount();
        });
        findViewById(R.id.btnReplaceAll).setOnClickListener(v -> {
            editor.replaceAll(etReplace.getText().toString());
            main.postDelayed(this::updateFindCount, 200);
        });
        chipCase.setOnClickListener(v -> {
            caseOn = !caseOn;
            styleChips();
            runFind();
        });
        chipRegex.setOnClickListener(v -> {
            regexOn = !regexOn;
            styleChips();
            runFind();
        });
        styleChips();
    }

    private void styleChips() {
        chipCase.setBackgroundResource(caseOn ? R.drawable.bg_chip_on : android.R.color.transparent);
        chipCase.setTextColor(getColor(caseOn ? R.color.on_ink : R.color.ink));
        chipRegex.setBackgroundResource(regexOn ? R.drawable.bg_chip_on : android.R.color.transparent);
        chipRegex.setTextColor(getColor(regexOn ? R.color.on_ink : R.color.ink));
    }

    private void openFind() {
        findBar.setVisibility(View.VISIBLE);
        etFind.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etFind, InputMethodManager.SHOW_IMPLICIT);
        runFind();
    }

    private void closeFind() {
        editor.stopFind();
        findBar.setVisibility(View.GONE);
        tvFindCount.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etFind.getWindowToken(), 0);
    }

    private void runFind() {
        editor.find(etFind.getText().toString(), !caseOn, regexOn);
        main.removeCallbacks(countRunner);
        main.postDelayed(countRunner, 250);
    }

    private final Runnable countRunner = new Runnable() {
        @Override
        public void run() {
            updateFindCount();
        }
    };

    private void updateFindCount() {
        int total = editor.matchCount();
        if (etFind.getText().length() == 0) {
            tvFindCount.setText("");
        } else if (total == 0) {
            tvFindCount.setText(R.string.ed_no_match);
        } else {
            int idx = editor.matchIndex();
            tvFindCount.setText(idx > 0 ? idx + "/" + total : String.valueOf(total));
        }
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onPause() {
        if (loaded && textMode) {
            float sp = editor.getFontSizeSp();
            if (sp >= SettingsStore.EDITOR_SIZE_MIN && sp <= SettingsStore.EDITOR_SIZE_MAX) {
                settings.setEditorTextSizeSp(sp);
            }
            if (dirty) {
                main.removeCallbacks(draftRunner);
                writeDraft();
            }
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (editor != null) editor.release();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
