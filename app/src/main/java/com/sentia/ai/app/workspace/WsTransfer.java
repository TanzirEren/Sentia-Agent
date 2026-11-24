package com.sentia.ai.app.workspace;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** ZIP import and export, with no Android classes, so it can be tested on a plain JVM. */
public final class WsTransfer {
    public static final int MAX_IMPORT_FILES = 5000;
    public static final long MAX_IMPORT_BYTES = 100L * 1024L * 1024L;
    public static final long MAX_IMPORT_FILE = 20L * 1024L * 1024L;

    public interface Progress {
        void onProgress(int files, String current);

        boolean cancelled();
    }

    /** Where the ZIP bytes come from. It is opened twice: once to look, once to extract. */
    public interface ZipSource {
        InputStream open() throws IOException;
    }

    /** Result of an import. */
    public static final class Result {
        public int files;
        public long bytes;
        public int skipped;
        public boolean limit;
        public boolean cancelled;
        public boolean topFolderRemoved;
        public int errorCode;
        public String targetDir = "";
    }

    private WsTransfer() {}

    /**
     * Extracts a ZIP below the folder target. Names with "..", hidden folders (such as node_modules),
     * bad names and oversized entries are skipped and counted. A single top folder is removed.
     */
    public static void importZip(ZipSource src, WorkspaceFs all, WorkspaceFs vis, String target, Result r, Progress p)
            throws IOException {
        // pass 1: is everything inside one top folder?
        String top = null;
        boolean single = true;
        try (InputStream raw = src.open()) {
            ZipInputStream z = new ZipInputStream(new BufferedInputStream(raw));
            ZipEntry e;
            int seen = 0;
            while ((e = z.getNextEntry()) != null && seen < 100000) {
                seen++;
                String n = cleanZipName(e.getName());
                if (n.isEmpty()) continue;
                int i = n.indexOf('/');
                if (i < 0) {
                    single = false;
                    break;
                }
                String t = n.substring(0, i);
                if (top == null) top = t;
                else if (!top.equals(t)) {
                    single = false;
                    break;
                }
            }
        }
        String strip = single && top != null ? top + "/" : "";
        r.topFolderRemoved = !strip.isEmpty();

        // pass 2: write files
        try (InputStream raw = src.open()) {
            ZipInputStream z = new ZipInputStream(new BufferedInputStream(raw));
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (p != null && p.cancelled()) {
                    r.cancelled = true;
                    break;
                }
                if (r.files >= MAX_IMPORT_FILES || r.bytes >= MAX_IMPORT_BYTES) {
                    r.limit = true;
                    break;
                }
                String n = cleanZipName(e.getName());
                if (!strip.isEmpty() && n.startsWith(strip)) n = n.substring(strip.length());
                if (n.isEmpty()) continue;
                String clean;
                try {
                    clean = WorkspaceFs.normalize(n); // refuses ".."  (zip-slip)
                    for (String seg : clean.split("/")) {
                        WorkspaceFs.validateName(seg);
                        if (vis.isHidden(seg)) throw new WsException(WsException.BAD_NAME, "hidden");
                    }
                } catch (WsException bad) {
                    r.skipped++;
                    continue;
                }
                if (clean.isEmpty()) continue;
                String dest = WorkspaceFs.join(target, clean);
                try {
                    if (e.isDirectory()) {
                        all.resolve(dest).mkdirs();
                    } else {
                        long room = Math.min(MAX_IMPORT_FILE, MAX_IMPORT_BYTES - r.bytes);
                        r.bytes += all.writeStream(dest, z, room);
                        r.files++;
                        if (p != null) p.onProgress(r.files, WorkspaceFs.lastSegment(clean));
                    }
                } catch (WsException bad) {
                    r.skipped++;
                }
            }
        }
    }

    static String cleanZipName(String raw) {
        String n = raw == null ? "" : raw.replace('\\', '/');
        while (n.startsWith("/")) n = n.substring(1);
        return n;
    }

    // ------------------------------------------------------------ export

    /** Writes selected paths (or everything when rels is null) into a ZIP. Returns the file count. */
    public static int exportZip(WorkspaceFs fs, List<String> rels, File out, Progress p) throws IOException {
        File parent = out.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        int[] count = {0};
        try (ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            if (rels == null) {
                addDir(fs, "", z, count, p);
            } else {
                for (String rel : rels) {
                    WsEntry e = fs.stat(rel);
                    if (e == null) continue;
                    if (e.dir) addDir(fs, e.path, z, count, p);
                    else addFile(fs, e, z, count, p);
                }
            }
        }
        return count[0];
    }

    private static void addDir(WorkspaceFs fs, String rel, ZipOutputStream z, int[] count, Progress p) throws IOException {
        List<WsEntry> kids = fs.list(rel);
        if (kids.isEmpty() && !rel.isEmpty()) {
            z.putNextEntry(new ZipEntry(rel + "/"));
            z.closeEntry();
            return;
        }
        for (WsEntry k : kids) {
            if (p != null && p.cancelled()) return;
            if (k.dir) addDir(fs, k.path, z, count, p);
            else addFile(fs, k, z, count, p);
        }
    }

    private static void addFile(WorkspaceFs fs, WsEntry e, ZipOutputStream z, int[] count, Progress p) throws IOException {
        z.putNextEntry(new ZipEntry(e.path));
        try (InputStream in = new FileInputStream(fs.resolve(e.path))) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
        }
        z.closeEntry();
        count[0]++;
        if (p != null) p.onProgress(count[0], e.name);
    }
}
