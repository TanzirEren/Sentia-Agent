package com.sentia.ai.app.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.workspace.FileKinds;
import com.sentia.ai.app.workspace.RecentFiles;
import com.sentia.ai.app.workspace.WorkspaceFs;
import com.sentia.ai.app.workspace.WorkspaceManager;
import com.sentia.ai.app.workspace.WsEntry;
import com.sentia.ai.app.workspace.WsErrors;
import com.sentia.ai.app.workspace.WsException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The file explorer. One class drives both the full screen (FilesActivity) and the right drawer
 * in the chat screen, so they always behave the same.
 */
public final class ExplorerController {
    public interface Host {
        void onOpenFile(String rel);
    }

    private interface NameCb {
        void onName(String name);
    }

    private interface PathCb {
        void onPath(String path);
    }

    private final Activity act;
    private final Host host;
    private final WorkspaceActions actions;
    private final SettingsStore settings;
    private final RecentFiles recent;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final View root;
    private final TextView tvTitle, tvEmpty, tvSelCount;
    private final EditText etFilter;
    private final LinearLayout crumbs;
    private final View scrollCrumbs, selectBar;
    private final RecyclerView rv;
    private final ExplorerAdapter adapter;

    private long projectId;
    private String projectName = "";
    private String focus = "";
    private String filter = "";
    private final Set<String> expanded = new HashSet<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private boolean selecting;
    private boolean workspaceMissing;
    private int loadToken;
    private final Runnable filterRunner = new Runnable() {
        @Override
        public void run() {
            filter = etFilter.getText().toString().trim();
            refresh();
        }
    };

    public ExplorerController(Activity activity, ViewGroup parent, long projectId, String projectName,
                              WorkspaceActions actions, Host host) {
        this.act = activity;
        this.host = host;
        this.actions = actions;
        this.settings = SettingsStore.get(activity);
        this.recent = new RecentFiles(activity);
        this.projectId = projectId;
        this.projectName = projectName == null ? "" : projectName;
        actions.setProject(projectId, this.projectName);

        root = LayoutInflater.from(activity).inflate(R.layout.view_explorer, parent, false);
        // Rows are built in code; saved view state must never be copied between screens.
        root.setSaveFromParentEnabled(false);
        tvTitle = root.findViewById(R.id.tvExplorerTitle);
        tvEmpty = root.findViewById(R.id.tvExpEmpty);
        tvSelCount = root.findViewById(R.id.tvSelCount);
        etFilter = root.findViewById(R.id.etExpFilter);
        crumbs = root.findViewById(R.id.crumbs);
        scrollCrumbs = root.findViewById(R.id.scrollCrumbs);
        selectBar = root.findViewById(R.id.selectBar);
        rv = root.findViewById(R.id.rvNodes);
        etFilter.setSaveEnabled(false);
        rv.setSaveEnabled(false);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        adapter = new ExplorerAdapter(new ExplorerAdapter.Listener() {
            @Override
            public void onClick(ExplorerAdapter.Node n) {
                handleClick(n);
            }

            @Override
            public void onLongClick(ExplorerAdapter.Node n) {
                handleLong(n);
            }
        });
        rv.setAdapter(adapter);
        parent.addView(root);

        root.findViewById(R.id.btnExpSearch).setOnClickListener(v -> toggleSearch());
        root.findViewById(R.id.btnExpNew).setOnClickListener(v -> showNewSheet());
        root.findViewById(R.id.btnExpMore).setOnClickListener(v -> showWorkspaceMenu());
        root.findViewById(R.id.btnSelClose).setOnClickListener(v -> clearSelection());
        root.findViewById(R.id.btnSelDelete).setOnClickListener(v -> confirmDelete(new ArrayList<>(selected)));
        root.findViewById(R.id.btnSelZip).setOnClickListener(v -> {
            if (!selected.isEmpty()) actions.exportSelection(new ArrayList<>(selected));
        });
        etFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                main.removeCallbacks(filterRunner);
                main.postDelayed(filterRunner, 250);
            }
        });
        etFilter.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard();
                return true;
            }
            return false;
        });
        refresh();
    }

    public View view() {
        return root;
    }

    /** Full screen hides the small title, the drawer shows "Files". */
    public void setTitleVisible(boolean visible) {
        tvTitle.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public void setProject(long id, String name) {
        if (id != projectId) {
            focus = "";
            filter = "";
            etFilter.setText("");
            etFilter.setVisibility(View.GONE);
            expanded.clear();
            selected.clear();
            selecting = false;
        }
        projectId = id;
        projectName = name == null ? "" : name;
        actions.setProject(id, projectName);
        refresh();
    }

    // ------------------------------------------------------------ loading

    public void refresh() {
        final int token = ++loadToken;
        final long id = projectId;
        final String f = focus;
        final String q = filter;
        final Set<String> exp = new HashSet<>(expanded);
        final boolean hidden = settings.showHiddenFiles();
        WorkspaceManager.run(() -> {
            final List<ExplorerAdapter.Node> nodes = new ArrayList<>();
            boolean missing = false;
            boolean focusLost = false;
            String err = null;
            if (!WorkspaceManager.exists(act, id)) {
                missing = true;
            } else {
                try {
                    WorkspaceFs fs = WorkspaceManager.fs(act, id, hidden);
                    if (!f.isEmpty()) {
                        WsEntry st = fs.stat(f);
                        if (st == null || !st.dir) focusLost = true;
                    }
                    if (!focusLost) {
                        if (q.isEmpty()) {
                            build(fs, f, 0, exp, nodes, new int[]{0});
                        } else {
                            for (WsEntry e : fs.search(q, 300)) {
                                if (!f.isEmpty() && !e.path.startsWith(f + "/")) continue;
                                ExplorerAdapter.Node n = new ExplorerAdapter.Node(e, 0);
                                n.showParent = true;
                                nodes.add(n);
                            }
                        }
                    }
                } catch (WsException e) {
                    err = WsErrors.text(act, e);
                }
            }
            final boolean fMissing = missing;
            final boolean fLost = focusLost;
            final String fErr = err;
            WorkspaceManager.post(() -> {
                if (token != loadToken) return;
                if (fLost) {
                    focus = "";
                    refresh();
                    return;
                }
                workspaceMissing = fMissing;
                adapter.submit(nodes, selecting, selected);
                buildCrumbs();
                updateSelectBar();
                if (fMissing) {
                    tvEmpty.setText(R.string.ws_missing);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else if (fErr != null) {
                    tvEmpty.setText(fErr);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else if (nodes.isEmpty()) {
                    tvEmpty.setText(q.isEmpty() ? R.string.ws_empty : R.string.ws_no_match);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
            });
        });
    }

    private static void build(WorkspaceFs fs, String dir, int depth, Set<String> exp,
                              List<ExplorerAdapter.Node> out, int[] budget) throws WsException {
        for (WsEntry e : fs.list(dir)) {
            if (++budget[0] > 3000) return;
            ExplorerAdapter.Node n = new ExplorerAdapter.Node(e, depth);
            n.expanded = e.dir && exp.contains(e.path);
            out.add(n);
            if (n.expanded) {
                try {
                    build(fs, e.path, depth + 1, exp, out, budget);
                } catch (WsException gone) {
                    // a folder that vanished while open is simply shown as empty
                }
            }
        }
    }

    private void buildCrumbs() {
        crumbs.removeAllViews();
        addCrumb(projectName.isEmpty() ? act.getString(R.string.ws_root) : projectName, "", focus.isEmpty());
        if (!focus.isEmpty()) {
            String[] parts = focus.split("/");
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) path.append('/');
                path.append(parts[i]);
                addCrumb(parts[i], path.toString(), i == parts.length - 1);
            }
        }
        scrollCrumbs.post(() -> ((android.widget.HorizontalScrollView) scrollCrumbs).fullScroll(View.FOCUS_RIGHT));
    }

    private void addCrumb(String label, final String path, boolean last) {
        if (crumbs.getChildCount() > 0) {
            TextView sep = new TextView(act);
            sep.setText("\u203A");
            sep.setTextColor(act.getColor(R.color.ink_faint));
            sep.setTextSize(14);
            sep.setPadding(dp(4), 0, dp(4), 0);
            crumbs.addView(sep);
        }
        TextView t = new TextView(act);
        t.setText(label);
        t.setTextSize(13);
        t.setMaxLines(1);
        t.setTextColor(act.getColor(last ? R.color.ink : R.color.ink_muted));
        t.setPadding(dp(6), dp(6), dp(6), dp(6));
        if (!last) {
            t.setOnClickListener(v -> {
                focus = path;
                clearSelectionQuiet();
                refresh();
            });
        }
        crumbs.addView(t);
    }

    // ------------------------------------------------------------ clicks

    private void handleClick(ExplorerAdapter.Node n) {
        if (selecting) {
            toggleSelect(n.e.path);
            return;
        }
        if (n.e.dir) {
            if (n.showParent) {
                // from a search result: jump into the folder
                focus = n.e.path;
                closeSearch();
                refresh();
                return;
            }
            if (expanded.contains(n.e.path)) expanded.remove(n.e.path);
            else expanded.add(n.e.path);
            refresh();
        } else {
            recent.add(projectId, n.e.path);
            hideKeyboard();
            host.onOpenFile(n.e.path);
        }
    }

    private void handleLong(ExplorerAdapter.Node n) {
        if (selecting) {
            toggleSelect(n.e.path);
            return;
        }
        showItemMenu(n.e);
    }

    // ------------------------------------------------------------ selection

    private void toggleSelect(String path) {
        if (!selected.remove(path)) selected.add(path);
        if (selected.isEmpty()) selecting = false;
        adapter.setSelection(selecting, selected);
        updateSelectBar();
    }

    private void startSelect(String path) {
        selecting = true;
        selected.clear();
        selected.add(path);
        adapter.setSelection(true, selected);
        updateSelectBar();
    }

    private void clearSelectionQuiet() {
        selecting = false;
        selected.clear();
    }

    public void clearSelection() {
        clearSelectionQuiet();
        adapter.setSelection(false, selected);
        updateSelectBar();
    }

    private void updateSelectBar() {
        selectBar.setVisibility(selecting ? View.VISIBLE : View.GONE);
        tvSelCount.setText(act.getString(R.string.ws_selected, selected.size()));
    }

    // ------------------------------------------------------------ search

    private void toggleSearch() {
        if (etFilter.getVisibility() == View.VISIBLE) {
            closeSearch();
            refresh();
        } else {
            etFilter.setVisibility(View.VISIBLE);
            etFilter.requestFocus();
            InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etFilter, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void closeSearch() {
        main.removeCallbacks(filterRunner);
        etFilter.setText("");
        etFilter.setVisibility(View.GONE);
        filter = "";
        hideKeyboard();
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) act.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etFilter.getWindowToken(), 0);
    }

    // ------------------------------------------------------------ back and lifecycle

    /** Returns true when it used the back press (closed selection, search, or went up a folder). */
    public boolean handleBack() {
        if (selecting) {
            clearSelection();
            return true;
        }
        if (etFilter.getVisibility() == View.VISIBLE) {
            closeSearch();
            refresh();
            return true;
        }
        if (!focus.isEmpty()) {
            focus = WorkspaceFs.parentOf(focus);
            refresh();
            return true;
        }
        return false;
    }

    public void onResume() {
        refresh();
    }

    public void release() {
        main.removeCallbacks(filterRunner);
        loadToken++;
    }

    // ------------------------------------------------------------ menus

    private void showNewSheet() {
        if (workspaceMissing) {
            actions.showAddSheet();
            return;
        }
        final String dir = focus;
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row(R.drawable.ic_new_file, act.getString(R.string.ws_new_file), () -> newFile(dir)));
        rows.add(new ActionSheet.Row(R.drawable.ic_new_folder, act.getString(R.string.ws_new_folder), () -> newFolder(dir)));
        ActionSheet.show(act, null, rows);
    }

    private void showWorkspaceMenu() {
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row(R.drawable.ic_refresh, act.getString(R.string.ws_refresh), this::refresh));
        if (!workspaceMissing) {
            rows.add(new ActionSheet.Row(R.drawable.ic_clock, act.getString(R.string.ws_recent), this::showRecent));
            rows.add(new ActionSheet.Row(R.drawable.ic_eye, act.getString(settings.showHiddenFiles()
                    ? R.string.ws_hide_hidden : R.string.ws_show_hidden), () -> {
                settings.setShowHiddenFiles(!settings.showHiddenFiles());
                refresh();
            }));
            rows.add(new ActionSheet.Row(R.drawable.ic_zip, act.getString(R.string.ws_export_share), actions::exportZipShare));
            rows.add(new ActionSheet.Row(R.drawable.ic_download, act.getString(R.string.ws_export_save), actions::exportZipSave));
        }
        rows.add(new ActionSheet.Row(R.drawable.ic_folder, act.getString(R.string.ws_import_folder), actions::importFolder));
        rows.add(new ActionSheet.Row(R.drawable.ic_file, act.getString(R.string.ws_import_zip), actions::importZip));
        if (workspaceMissing) {
            rows.add(new ActionSheet.Row(R.drawable.ic_new_folder, act.getString(R.string.ws_create_blank), actions::createBlank));
        } else {
            rows.add(new ActionSheet.Row(R.drawable.ic_trash, act.getString(R.string.ws_delete_title), actions::confirmDeleteWorkspace));
        }
        ActionSheet.show(act, null, rows);
    }

    private void showRecent() {
        final long id = projectId;
        final boolean hidden = settings.showHiddenFiles();
        WorkspaceManager.run(() -> {
            final List<String> alive = new ArrayList<>();
            try {
                WorkspaceFs fs = WorkspaceManager.fs(act, id, hidden);
                for (String r : recent.list(id)) {
                    WsEntry e = fs.stat(r);
                    if (e != null && !e.dir) alive.add(r);
                    else recent.remove(id, r);
                }
            } catch (WsException e) {
                // show what we have
            }
            WorkspaceManager.post(() -> {
                if (alive.isEmpty()) {
                    Toast.makeText(act, R.string.ws_recent_none, Toast.LENGTH_SHORT).show();
                    return;
                }
                List<ActionSheet.Row> rows = new ArrayList<>();
                for (final String r : alive) {
                    rows.add(new ActionSheet.Row(ExplorerAdapter.iconFor(WorkspaceFs.lastSegment(r)), r, () -> {
                        recent.add(id, r);
                        host.onOpenFile(r);
                    }));
                }
                ActionSheet.show(act, act.getString(R.string.ws_recent), rows);
            });
        });
    }

    private void showItemMenu(final WsEntry e) {
        List<ActionSheet.Row> rows = new ArrayList<>();
        if (e.dir) {
            rows.add(new ActionSheet.Row(R.drawable.ic_new_file, act.getString(R.string.ws_new_file), () -> newFile(e.path)));
            rows.add(new ActionSheet.Row(R.drawable.ic_new_folder, act.getString(R.string.ws_new_folder), () -> newFolder(e.path)));
            rows.add(new ActionSheet.Row(R.drawable.ic_workspace, act.getString(R.string.ws_open_as_root), () -> {
                focus = e.path;
                clearSelectionQuiet();
                refresh();
            }));
        } else {
            rows.add(new ActionSheet.Row(R.drawable.ic_edit, act.getString(R.string.ws_open), () -> {
                recent.add(projectId, e.path);
                host.onOpenFile(e.path);
            }));
        }
        rows.add(new ActionSheet.Row(R.drawable.ic_rename, act.getString(R.string.rename), () -> rename(e)));
        rows.add(new ActionSheet.Row(R.drawable.ic_duplicate, act.getString(R.string.ws_duplicate), () -> duplicate(e)));
        rows.add(new ActionSheet.Row(R.drawable.ic_move, act.getString(R.string.ws_move), () -> move(e)));
        rows.add(new ActionSheet.Row(R.drawable.ic_copy, act.getString(R.string.ws_copy_path), () -> copyPath(e.path)));
        if (!e.dir) {
            rows.add(new ActionSheet.Row(R.drawable.ic_share, act.getString(R.string.ws_share_file), () -> shareFile(e)));
        }
        rows.add(new ActionSheet.Row(R.drawable.ic_select, act.getString(R.string.ws_select), () -> startSelect(e.path)));
        rows.add(new ActionSheet.Row(R.drawable.ic_trash, act.getString(R.string.delete), () -> {
            List<String> one = new ArrayList<>();
            one.add(e.path);
            confirmDelete(one);
        }));
        ActionSheet.show(act, e.path, rows);
    }

    // ------------------------------------------------------------ operations

    private interface FsJob {
        String run(WorkspaceFs fs) throws WsException;
    }

    /** Runs a file job on the background thread, then refreshes. The result path (if any) goes to onDone. */
    private void job(final FsJob j, final PathCb onDone) {
        final long id = projectId;
        final boolean hidden = settings.showHiddenFiles();
        WorkspaceManager.run(() -> {
            String res = null;
            String err = null;
            try {
                res = j.run(WorkspaceManager.fs(act, id, hidden));
            } catch (WsException e) {
                err = WsErrors.text(act, e);
            }
            final String fRes = res;
            final String fErr = err;
            WorkspaceManager.post(() -> {
                if (fErr != null) Toast.makeText(act, fErr, Toast.LENGTH_LONG).show();
                else if (onDone != null) onDone.onPath(fRes);
                refresh();
            });
        });
    }

    private void promptName(int titleRes, String initial, final NameCb cb) {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_name, null);
        final EditText et = v.findViewById(R.id.etName);
        et.setSaveEnabled(false);
        et.setText(initial == null ? "" : initial);
        et.setSelection(et.getText().length());
        new MaterialAlertDialogBuilder(act)
                .setTitle(titleRes)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String t = et.getText().toString().trim();
                    if (!t.isEmpty()) cb.onName(t);
                })
                .show();
    }

    private void newFile(final String dir) {
        promptName(R.string.ws_new_file, "", name -> {
            final String path = WorkspaceFs.join(dir, name);
            job(fs -> {
                fs.createFile(path);
                return path;
            }, p -> {
                if (!dir.isEmpty()) expanded.add(dir);
                recent.add(projectId, path);
                host.onOpenFile(path);
            });
        });
    }

    private void newFolder(final String dir) {
        promptName(R.string.ws_new_folder, "", name -> {
            final String path = WorkspaceFs.join(dir, name);
            job(fs -> {
                fs.mkdir(path);
                return path;
            }, p -> {
                if (!dir.isEmpty()) expanded.add(dir);
                expanded.add(path);
            });
        });
    }

    private void rename(final WsEntry e) {
        promptName(R.string.rename, e.name, name -> {
            if (name.equals(e.name)) return;
            job(fs -> fs.rename(e.path, name), p -> {
                moved(e.path, p);
            });
        });
    }

    private void duplicate(final WsEntry e) {
        job(fs -> fs.duplicate(e.path), null);
    }

    private void move(final WsEntry e) {
        final long id = projectId;
        final boolean hidden = settings.showHiddenFiles();
        WorkspaceManager.run(() -> {
            final List<String> dirs = new ArrayList<>();
            try {
                for (String d : WorkspaceManager.fs(act, id, hidden).allDirs(300)) {
                    if (d.equals(WorkspaceFs.parentOf(e.path))) continue;
                    if (e.dir && (d.equals(e.path) || d.startsWith(e.path + "/"))) continue;
                    dirs.add(d);
                }
            } catch (WsException ex) {
                // empty list below
            }
            WorkspaceManager.post(() -> {
                if (dirs.isEmpty()) {
                    Toast.makeText(act, R.string.ws_no_folders, Toast.LENGTH_SHORT).show();
                    return;
                }
                List<ActionSheet.Row> rows = new ArrayList<>();
                for (final String d : dirs) {
                    String label = d.isEmpty() ? act.getString(R.string.ws_root) : d;
                    rows.add(new ActionSheet.Row(R.drawable.ic_folder, label, () ->
                            job(fs -> fs.move(e.path, d), p -> {
                                moved(e.path, p);
                                if (!d.isEmpty()) expanded.add(d);
                            })));
                }
                ActionSheet.show(act, act.getString(R.string.ws_move_to), rows);
            });
        });
    }

    /** Keeps expanded folders and recent files pointing at the new path after a rename or move. */
    private void moved(String from, String to) {
        List<String> exp = new ArrayList<>(expanded);
        for (String p : exp) {
            if (p.equals(from) || p.startsWith(from + "/")) {
                expanded.remove(p);
                if (to != null) expanded.add(to + p.substring(from.length()));
            }
        }
        recent.remove(projectId, from);
        clearSelectionQuiet();
    }

    private void confirmDelete(final List<String> paths) {
        if (paths.isEmpty()) return;
        String msg = paths.size() == 1
                ? act.getString(R.string.ws_delete_one, WorkspaceFs.lastSegment(paths.get(0)))
                : act.getString(R.string.ws_delete_many, paths.size());
        new MaterialAlertDialogBuilder(act)
                .setTitle(R.string.ws_delete_item_title)
                .setMessage(msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> job(fs -> {
                    for (String p : paths) fs.delete(p);
                    return null;
                }, x -> {
                    for (String p : paths) {
                        recent.remove(projectId, p);
                        expanded.remove(p);
                    }
                    clearSelectionQuiet();
                }))
                .show();
    }

    private void copyPath(String rel) {
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("path", rel));
            Toast.makeText(act, R.string.ws_path_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(final WsEntry e) {
        if (e.size > WorkspaceManager.MAX_IMPORT_FILE) {
            Toast.makeText(act, R.string.ws_err_too_large, Toast.LENGTH_LONG).show();
            return;
        }
        final long id = projectId;
        final boolean hidden = settings.showHiddenFiles();
        WorkspaceManager.run(() -> {
            File out = null;
            String err = null;
            try {
                WorkspaceFs fs = WorkspaceManager.fs(act, id, hidden);
                File src = fs.resolve(e.path);
                File dir = WorkspaceManager.exportCacheDir(act);
                out = new File(dir, e.name);
                java.io.FileInputStream in = new java.io.FileInputStream(src);
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                } finally {
                    in.close();
                }
            } catch (IOException ex) {
                err = WsErrors.text(act, ex);
            }
            final File fOut = out;
            final String fErr = err;
            WorkspaceManager.post(() -> {
                if (fErr != null || fOut == null) {
                    Toast.makeText(act, fErr == null ? act.getString(R.string.ws_err_io) : fErr, Toast.LENGTH_LONG).show();
                    return;
                }
                try {
                    WorkspaceManager.share(act, fOut, FileKinds.mime(e.name), act.getString(R.string.ws_share_file));
                } catch (RuntimeException ex) {
                    Toast.makeText(act, R.string.ws_err_io, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private int dp(int v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }
}
