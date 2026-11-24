package com.sentia.ai.app.workspace;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Where workspaces live, and the big jobs: import a folder or a ZIP, export a ZIP, share, delete.
 * A project's workspace is files/workspaces/id. Everything here is blocking: call it from run().
 */
public final class WorkspaceManager {
    public static final int MAX_IMPORT_FILES = WsTransfer.MAX_IMPORT_FILES;
    public static final long MAX_IMPORT_BYTES = WsTransfer.MAX_IMPORT_BYTES;
    public static final long MAX_IMPORT_FILE = WsTransfer.MAX_IMPORT_FILE;

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private WorkspaceManager() {}

    public static void run(Runnable r) {
        POOL.execute(r);
    }

    public static void post(Runnable r) {
        MAIN.post(r);
    }

    // ------------------------------------------------------------ places

    public static File rootDir(Context c, long projectId) {
        return new File(new File(c.getApplicationContext().getFilesDir(), "workspaces"), String.valueOf(projectId));
    }

    public static boolean exists(Context c, long projectId) {
        return rootDir(c, projectId).isDirectory();
    }

    public static WorkspaceFs fs(Context c, long projectId, boolean showHidden) throws WsException {
        return new WorkspaceFs(rootDir(c, projectId), showHidden);
    }

    public static void createBlank(Context c, long projectId) throws WsException {
        File d = rootDir(c, projectId);
        if (!d.isDirectory() && !d.mkdirs()) throw new WsException(WsException.IO, "Cannot create the workspace");
    }

    public static void delete(Context c, long projectId) {
        deleteTree(rootDir(c, projectId));
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.isDirectory() ? f.listFiles() : null;
        if (kids != null) {
            for (File k : kids) {
                // never follow a symbolic link out of the workspace
                try {
                    if (!k.getCanonicalFile().equals(new File(f.getCanonicalFile(), k.getName()).getCanonicalFile())) {
                        k.delete();
                        continue;
                    }
                } catch (IOException e) {
                    k.delete();
                    continue;
                }
                deleteTree(k);
            }
        }
        f.delete();
    }

    public static WsStats stats(Context c, long projectId) {
        try {
            return fs(c, projectId, false).stats();
        } catch (WsException e) {
            return new WsStats();
        }
    }

    // ------------------------------------------------------------ import folder (SAF)

    public static WsTransfer.Result importFolder(Context c, long projectId, Uri tree, WsTransfer.Progress p) {
        WsTransfer.Result r = new WsTransfer.Result();
        try {
            DocumentFile src = DocumentFile.fromTreeUri(c, tree);
            if (src == null || !src.isDirectory()) {
                r.errorCode = WsException.NOT_DIR;
                return r;
            }
            createBlank(c, projectId);
            WorkspaceFs all = fs(c, projectId, true);
            WorkspaceFs vis = fs(c, projectId, false);
            String target = "";
            if (!all.list("").isEmpty()) {
                String nm = src.getName() == null ? "import" : src.getName();
                try {
                    WorkspaceFs.validateName(nm);
                } catch (WsException e) {
                    nm = "import";
                }
                target = all.freeFolderName("", nm);
                all.mkdir(target);
            }
            r.targetDir = target;
            copyTree(c, src, target, all, vis, r, p, 0);
        } catch (WsException e) {
            r.errorCode = e.code;
        } catch (RuntimeException e) {
            r.errorCode = WsException.IO;
        }
        return r;
    }

    private static void copyTree(Context c, DocumentFile dir, String rel, WorkspaceFs all, WorkspaceFs vis,
                                 WsTransfer.Result r, WsTransfer.Progress p, int depth) throws WsException {
        if (depth > WorkspaceFs.MAX_DEPTH - 2) {
            r.skipped++;
            return;
        }
        DocumentFile[] kids = dir.listFiles();
        for (DocumentFile k : kids) {
            if (p != null && p.cancelled()) {
                r.cancelled = true;
                return;
            }
            if (r.files >= MAX_IMPORT_FILES || r.bytes >= MAX_IMPORT_BYTES) {
                r.limit = true;
                return;
            }
            String name = k.getName();
            if (name == null || vis.isHidden(name)) {
                r.skipped++;
                continue;
            }
            try {
                WorkspaceFs.validateName(name);
            } catch (WsException e) {
                r.skipped++;
                continue;
            }
            String child = WorkspaceFs.join(rel, name);
            if (k.isDirectory()) {
                all.resolve(child).mkdirs();
                copyTree(c, k, child, all, vis, r, p, depth + 1);
                if (r.cancelled || r.limit) return;
            } else if (k.isFile()) {
                long len = k.length();
                if (len > MAX_IMPORT_FILE) {
                    r.skipped++;
                    continue;
                }
                try (InputStream in = c.getContentResolver().openInputStream(k.getUri())) {
                    if (in == null) {
                        r.skipped++;
                        continue;
                    }
                    r.bytes += all.writeStream(child, in, MAX_IMPORT_FILE);
                    r.files++;
                    if (p != null) p.onProgress(r.files, name);
                } catch (IOException e) {
                    r.skipped++;
                }
            }
        }
    }

    // ------------------------------------------------------------ import ZIP and export ZIP

    public static WsTransfer.Result importZip(final Context c, long projectId, final Uri zip, WsTransfer.Progress p) {
        WsTransfer.Result r = new WsTransfer.Result();
        try {
            createBlank(c, projectId);
            WorkspaceFs all = fs(c, projectId, true);
            WorkspaceFs vis = fs(c, projectId, false);
            String target = "";
            if (!all.list("").isEmpty()) {
                target = all.freeFolderName("", "imported-zip");
                all.mkdir(target);
            }
            r.targetDir = target;
            WsTransfer.importZip(new WsTransfer.ZipSource() {
                @Override
                public InputStream open() throws IOException {
                    InputStream in = c.getContentResolver().openInputStream(zip);
                    if (in == null) throw new IOException("Cannot open the ZIP file");
                    return in;
                }
            }, all, vis, target, r, p);
        } catch (WsException e) {
            r.errorCode = e.code;
        } catch (IOException | RuntimeException e) {
            r.errorCode = WsException.IO;
        }
        return r;
    }

    /** Writes selected paths (or everything when rels is null) into a ZIP. Returns the file count. */
    public static int exportZip(WorkspaceFs fs, List<String> rels, File out, WsTransfer.Progress p) throws IOException {
        return WsTransfer.exportZip(fs, rels, out, p);
    }

    // ------------------------------------------------------------ share

    /** A fresh folder in the cache for files that are about to be shared. Old files are removed. */
    public static File exportCacheDir(Context c) {
        File d = new File(c.getCacheDir(), "exports");
        File[] old = d.listFiles();
        if (old != null) for (File f : old) f.delete();
        d.mkdirs();
        return d;
    }

    public static void share(Context c, File file, String mime, String chooserTitle) {
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".files", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, chooserTitle);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(chooser);
    }
}
