package com.sentia.ai.app.ui;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.workspace.WorkspaceFs;
import com.sentia.ai.app.workspace.WorkspaceManager;
import com.sentia.ai.app.workspace.WsTransfer;
import com.sentia.ai.app.workspace.WsErrors;
import com.sentia.ai.app.workspace.WsException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Workspace jobs of one project that need a picker or a progress dialog:
 * create, import a folder or ZIP, export a ZIP, delete. Create it inside onCreate of the activity.
 */
public final class WorkspaceActions {
    public interface Listener {
        void onWorkspaceChanged();
    }

    private final AppCompatActivity act;
    private final Listener listener;
    private final ProjectRepo repo;
    private final SettingsStore settings;
    private final ActivityResultLauncher<Uri> pickTree;
    private final ActivityResultLauncher<String[]> pickZip;
    private final ActivityResultLauncher<String> saveZip;

    private long projectId;
    private String projectName = "";

    private AlertDialog progressDialog;
    private TextView tvMsg, tvCur;
    private volatile boolean cancelled;
    private long lastUi;

    public WorkspaceActions(AppCompatActivity activity, long projectId, String projectName, Listener l) {
        this.act = activity;
        this.listener = l;
        this.projectId = projectId;
        this.projectName = projectName == null ? "" : projectName;
        this.repo = ProjectRepo.get(activity);
        this.settings = SettingsStore.get(activity);
        pickTree = activity.registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
            if (uri != null) runImportFolder(uri);
        });
        pickZip = activity.registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) runImportZip(uri);
        });
        saveZip = activity.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
            if (uri != null) runSaveZip(uri);
        });
    }

    public void setProject(long id, String name) {
        this.projectId = id;
        this.projectName = name == null ? "" : name;
    }

    public long projectId() {
        return projectId;
    }

    // ------------------------------------------------------------ entry points

    /** Sheet for a project that has no workspace yet: blank, folder or ZIP. */
    public void showAddSheet() {
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row(R.drawable.ic_new_folder, act.getString(R.string.ws_create_blank), this::createBlank));
        rows.add(new ActionSheet.Row(R.drawable.ic_folder, act.getString(R.string.ws_import_folder), this::importFolder));
        rows.add(new ActionSheet.Row(R.drawable.ic_zip, act.getString(R.string.ws_import_zip), this::importZip));
        ActionSheet.show(act, act.getString(R.string.ws_add_title), rows);
    }

    public void createBlank() {
        final long id = projectId;
        WorkspaceManager.run(() -> {
            try {
                WorkspaceManager.createBlank(act, id);
                repo.setWorkspace(id, true, "blank");
                WorkspaceManager.post(() -> {
                    Toast.makeText(act, R.string.ws_created, Toast.LENGTH_SHORT).show();
                    listener.onWorkspaceChanged();
                });
            } catch (WsException e) {
                final String m = WsErrors.text(act, e);
                WorkspaceManager.post(() -> Toast.makeText(act, m, Toast.LENGTH_LONG).show());
            }
        });
    }

    public void importFolder() {
        pickTree.launch(null);
    }

    public void importZip() {
        pickZip.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
    }

    public void exportZipShare() {
        runExport(null);
    }

    public void exportZipSave() {
        saveZip.launch(safeName() + ".zip");
    }

    /** Zips only the given paths and shares the file. */
    public void exportSelection(List<String> rels) {
        runExport(rels);
    }

    public void confirmDeleteWorkspace() {
        new MaterialAlertDialogBuilder(act)
                .setTitle(R.string.ws_delete_title)
                .setMessage(R.string.ws_delete_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    final long id = projectId;
                    WorkspaceManager.run(() -> {
                        WorkspaceManager.delete(act, id);
                        repo.setWorkspace(id, false, null);
                        WorkspaceManager.post(() -> {
                            Toast.makeText(act, R.string.ws_deleted, Toast.LENGTH_SHORT).show();
                            listener.onWorkspaceChanged();
                        });
                    });
                })
                .show();
    }

    // ------------------------------------------------------------ jobs

    private void runImportFolder(final Uri uri) {
        final long id = projectId;
        showProgress(R.string.ws_importing);
        WorkspaceManager.run(() -> {
            final WsTransfer.Result r = WorkspaceManager.importFolder(act, id, uri, progress());
            finishImport(id, r, "folder");
        });
    }

    private void runImportZip(final Uri uri) {
        final long id = projectId;
        showProgress(R.string.ws_importing);
        WorkspaceManager.run(() -> {
            final WsTransfer.Result r = WorkspaceManager.importZip(act, id, uri, progress());
            finishImport(id, r, "zip");
        });
    }

    private void finishImport(final long id, final WsTransfer.Result r, final String source) {
        if (r.errorCode == 0 && (r.files > 0 || !r.cancelled)) repo.setWorkspace(id, true, source);
        WorkspaceManager.post(() -> {
            hideProgress();
            if (r.errorCode != 0) {
                Toast.makeText(act, WsErrors.text(act, r.errorCode), Toast.LENGTH_LONG).show();
            } else {
                StringBuilder sb = new StringBuilder(act.getString(R.string.ws_import_done, r.files));
                if (r.skipped > 0) sb.append("  ").append(act.getString(R.string.ws_import_skipped, r.skipped));
                if (r.limit) sb.append("  ").append(act.getString(R.string.ws_import_limit));
                if (r.topFolderRemoved) sb.append("  ").append(act.getString(R.string.ws_import_top));
                if (r.cancelled) sb.append("  ").append(act.getString(R.string.ws_import_cancelled));
                Toast.makeText(act, sb.toString(), Toast.LENGTH_LONG).show();
            }
            listener.onWorkspaceChanged();
        });
    }

    private void runExport(final List<String> rels) {
        final long id = projectId;
        final boolean hidden = settings.showHiddenFiles();
        final String name = rels != null && rels.size() == 1
                ? clean(WorkspaceFs.lastSegment(rels.get(0))) : safeName();
        showProgress(R.string.ws_exporting);
        WorkspaceManager.run(() -> {
            try {
                WorkspaceFs fs = WorkspaceManager.fs(act, id, hidden);
                File dir = WorkspaceManager.exportCacheDir(act);
                File out = new File(dir, name + ".zip");
                WorkspaceManager.exportZip(fs, rels, out, progress());
                WorkspaceManager.post(() -> {
                    hideProgress();
                    if (cancelled) {
                        out.delete();
                        return;
                    }
                    try {
                        WorkspaceManager.share(act, out, "application/zip", act.getString(R.string.ws_share_zip));
                    } catch (RuntimeException e) {
                        Toast.makeText(act, R.string.ws_err_io, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (IOException e) {
                final String m = WsErrors.text(act, e);
                WorkspaceManager.post(() -> {
                    hideProgress();
                    Toast.makeText(act, m, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void runSaveZip(final Uri target) {
        final long id = projectId;
        final boolean hidden = settings.showHiddenFiles();
        showProgress(R.string.ws_exporting);
        WorkspaceManager.run(() -> {
            boolean ok = false;
            try {
                WorkspaceFs fs = WorkspaceManager.fs(act, id, hidden);
                File dir = WorkspaceManager.exportCacheDir(act);
                File tmp = new File(dir, safeName() + ".zip");
                WorkspaceManager.exportZip(fs, null, tmp, progress());
                try (InputStream in = new FileInputStream(tmp);
                     OutputStream os = act.getContentResolver().openOutputStream(target)) {
                    if (os != null) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                        ok = true;
                    }
                }
                tmp.delete();
            } catch (IOException e) {
                ok = false;
            }
            final boolean done = ok;
            WorkspaceManager.post(() -> {
                hideProgress();
                Toast.makeText(act, done ? R.string.ws_zip_saved : R.string.ws_err_io, Toast.LENGTH_LONG).show();
            });
        });
    }

    // ------------------------------------------------------------ progress dialog

    private WsTransfer.Progress progress() {
        return new WsTransfer.Progress() {
            @Override
            public void onProgress(final int files, final String current) {
                long now = System.currentTimeMillis();
                if (now - lastUi < 120) return;
                lastUi = now;
                WorkspaceManager.post(() -> {
                    if (tvMsg != null) tvMsg.setText(act.getString(R.string.ws_progress_files, files));
                    if (tvCur != null) tvCur.setText(current);
                });
            }

            @Override
            public boolean cancelled() {
                return cancelled;
            }
        };
    }

    private void showProgress(int titleRes) {
        cancelled = false;
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_progress, null);
        tvMsg = v.findViewById(R.id.tvProgressMsg);
        tvCur = v.findViewById(R.id.tvProgressCur);
        tvMsg.setText(titleRes);
        progressDialog = new MaterialAlertDialogBuilder(act)
                .setTitle(titleRes)
                .setView(v)
                .setCancelable(false)
                .setNegativeButton(R.string.cancel, (d, w) -> cancelled = true)
                .show();
    }

    private void hideProgress() {
        if (progressDialog != null) {
            try {
                progressDialog.dismiss();
            } catch (RuntimeException e) {
                // activity already closed
            }
            progressDialog = null;
        }
        tvMsg = null;
        tvCur = null;
    }

    private String safeName() {
        return clean(projectName.isEmpty() ? "workspace" : projectName);
    }

    private static String clean(String raw) {
        String n = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        if (n.length() > 40) n = n.substring(0, 40);
        return n.isEmpty() ? "workspace" : n;
    }
}
