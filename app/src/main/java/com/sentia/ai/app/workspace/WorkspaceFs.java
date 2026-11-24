package com.sentia.ai.app.workspace;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Every file operation of a workspace goes through this class (UI and, later, the agent tools).
 * Paths are relative to the workspace root and use "/". Nothing can leave the root:
 * ".." is refused, and a path that goes through a symbolic link is refused.
 */
public final class WorkspaceFs {
    public static final long SOFT_TEXT_BYTES = 1024L * 1024L;
    public static final long MAX_TEXT_BYTES = 8L * 1024L * 1024L;
    public static final long MAX_READ_BYTES = 20L * 1024L * 1024L;
    public static final int MAX_DEPTH = 40;
    public static final String TMP_SUFFIX = ".sentia-tmp";

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String[] IGNORED = {".git", "node_modules", "build", ".gradle", ".idea", ".cxx",
            "__pycache__", ".DS_Store", ".dart_tool", ".next", ".venv"};

    /** Text of a file plus what is needed to save it back the same way. */
    public static final class TextDoc {
        public String text;
        public boolean crlf;
        public boolean bom;
        public long modified;
        public long size;
    }

    /** A slice of lines, used by the Read tool. */
    public static final class Slice {
        public final List<String> lines = new ArrayList<>();
        public int total;
        public int from;
        public boolean cutLines;
    }

    public interface Visitor {
        /** Return false to stop the walk. */
        boolean visit(WsEntry e, int depth);
    }

    private static final Comparator<WsEntry> ORDER = (a, b) -> {
        if (a.dir != b.dir) return a.dir ? -1 : 1;
        return a.name.compareToIgnoreCase(b.name);
    };

    private final File root;
    private final boolean showHidden;

    public WorkspaceFs(File rootDir, boolean showHidden) throws WsException {
        try {
            if (!rootDir.exists()) rootDir.mkdirs();
            this.root = rootDir.getCanonicalFile();
        } catch (IOException e) {
            throw new WsException(WsException.IO, "Cannot open the workspace folder");
        }
        this.showHidden = showHidden;
    }

    public File root() {
        return root;
    }

    public boolean showsHidden() {
        return showHidden;
    }

    // ------------------------------------------------------------ paths

    /** Cleans a relative path. Throws when it tries to leave the workspace. */
    public static String normalize(String rel) throws WsException {
        if (rel == null) return "";
        String s = rel.replace('\\', '/');
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (String part : s.split("/")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) throw new WsException(WsException.ESCAPE, "Path escapes the workspace: " + rel);
            if (part.indexOf('\0') >= 0) throw new WsException(WsException.BAD_NAME, "Invalid character in path");
            if (++depth > MAX_DEPTH) throw new WsException(WsException.LIMIT, "Path is too deep");
            if (out.length() > 0) out.append('/');
            out.append(part);
        }
        return out.toString();
    }

    /** Checks one file or folder name. */
    public static void validateName(String name) throws WsException {
        if (name == null) throw new WsException(WsException.BAD_NAME, "Empty name");
        String n = name;
        if (n.isEmpty() || !n.equals(n.trim()) || n.equals(".") || n.equals("..") || n.length() > 120) {
            throw new WsException(WsException.BAD_NAME, "Invalid name: " + name);
        }
        if (n.endsWith(".")) throw new WsException(WsException.BAD_NAME, "A name cannot end with a dot");
        if (n.endsWith(TMP_SUFFIX)) throw new WsException(WsException.BAD_NAME, "Reserved name");
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c == '/' || c == '\\' || c < 32 || c == 127) {
                throw new WsException(WsException.BAD_NAME, "Invalid character in name: " + name);
            }
        }
    }

    /** Safe path to a real file. Throws for "..", for symbolic links and for anything outside the root. */
    public File resolve(String rel) throws WsException {
        String n = normalize(rel);
        File lexical = n.isEmpty() ? root : new File(root, n);
        try {
            String canon = lexical.getCanonicalPath();
            String lex = lexical.getAbsolutePath();
            if (!canon.equals(lex)) {
                throw new WsException(WsException.SYMLINK, "Symbolic links are not allowed: " + rel);
            }
            String r = root.getPath();
            if (!(canon.equals(r) || canon.startsWith(r + File.separator))) {
                throw new WsException(WsException.ESCAPE, "Path escapes the workspace: " + rel);
            }
            return lexical;
        } catch (WsException e) {
            throw e; // WsException is an IOException too: keep its own code
        } catch (IOException e) {
            throw new WsException(WsException.IO, "Cannot read path: " + rel);
        }
    }

    private static boolean isSymlink(File f) {
        try {
            File parent = f.getParentFile();
            File canonSelf = parent == null ? f : new File(parent.getCanonicalFile(), f.getName());
            return !canonSelf.getCanonicalFile().equals(canonSelf.getAbsoluteFile());
        } catch (IOException e) {
            return true;
        }
    }

    public boolean isHidden(String name) {
        if (name.endsWith(TMP_SUFFIX)) return true;
        if (showHidden) return false;
        for (String s : IGNORED) if (s.equals(name)) return true;
        return false;
    }

    private WsEntry entry(String rel, File f) {
        return new WsEntry(f.getName(), rel, f.isDirectory(), f.isFile() ? f.length() : 0, f.lastModified());
    }

    public boolean exists(String rel) throws WsException {
        return resolve(rel).exists();
    }

    /** Info about a path, or null when it does not exist. */
    public WsEntry stat(String rel) throws WsException {
        File f = resolve(rel);
        if (!f.exists()) return null;
        return entry(normalize(rel), f);
    }

    // ------------------------------------------------------------ listing

    public List<WsEntry> list(String rel) throws WsException {
        File dir = resolve(rel);
        if (!dir.exists()) throw new WsException(WsException.NOT_FOUND, "Folder not found: " + rel);
        if (!dir.isDirectory()) throw new WsException(WsException.NOT_DIR, "Not a folder: " + rel);
        String base = normalize(rel);
        File[] kids = dir.listFiles();
        List<WsEntry> out = new ArrayList<>();
        if (kids != null) {
            for (File k : kids) {
                String name = k.getName();
                if (isHidden(name)) continue;
                if (isSymlink(k)) continue;
                out.add(entry(base.isEmpty() ? name : base + "/" + name, k));
            }
        }
        Collections.sort(out, ORDER);
        return out;
    }

    /** Walks a folder (depth first, folders before files). The visitor can stop the walk. */
    public void walk(String rel, int maxDepth, Visitor v) throws WsException {
        walkInner(normalize(rel), 0, maxDepth, v);
    }

    private boolean walkInner(String rel, int depth, int maxDepth, Visitor v) throws WsException {
        for (WsEntry e : list(rel)) {
            if (!v.visit(e, depth)) return false;
            if (e.dir && depth + 1 < maxDepth) {
                if (!walkInner(e.path, depth + 1, maxDepth, v)) return false;
            }
        }
        return true;
    }

    public WsStats stats() throws WsException {
        final WsStats s = new WsStats();
        walk("", MAX_DEPTH, (e, d) -> {
            if (e.dir) s.dirs++;
            else {
                s.files++;
                s.bytes += e.size;
            }
            return true;
        });
        return s;
    }

    /** Names that contain the query, anywhere in the tree. */
    public List<WsEntry> search(String query, final int limit) throws WsException {
        final String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        final List<WsEntry> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        final int[] visited = {0};
        walk("", MAX_DEPTH, (e, d) -> {
            if (++visited[0] > 20000) return false;
            if (e.name.toLowerCase(Locale.ROOT).contains(q)) out.add(e);
            return out.size() < limit;
        });
        return out;
    }

    /** Every folder path (including the root as an empty string), used by the Move picker. */
    public List<String> allDirs(final int limit) throws WsException {
        final List<String> out = new ArrayList<>();
        out.add("");
        walk("", MAX_DEPTH, (e, d) -> {
            if (e.dir) out.add(e.path);
            return out.size() < limit;
        });
        return out;
    }

    // ------------------------------------------------------------ reading

    private static byte[] readAll(File f, int expected) throws WsException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(expected, 32));
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new WsException(WsException.IO, "Cannot read file");
        }
    }

    private static boolean hasNul(byte[] data, int limit) {
        int n = Math.min(data.length, limit);
        for (int i = 0; i < n; i++) if (data[i] == 0) return true;
        return false;
    }

    /** True when the beginning of the file looks like binary data. */
    public boolean looksBinary(String rel) throws WsException {
        File f = resolve(rel);
        if (!f.isFile()) return false;
        byte[] head = new byte[8000];
        try (InputStream in = new FileInputStream(f)) {
            int n = in.read(head);
            if (n <= 0) return false;
            for (int i = 0; i < n; i++) if (head[i] == 0) return true;
            return false;
        } catch (IOException e) {
            throw new WsException(WsException.IO, "Cannot read file");
        }
    }

    /** Reads a whole UTF-8 text file. Line endings are normalised to "\n" in the result. */
    public TextDoc readText(String rel) throws WsException {
        File f = resolve(rel);
        if (!f.exists()) throw new WsException(WsException.NOT_FOUND, "File not found: " + rel);
        if (!f.isFile()) throw new WsException(WsException.NOT_FILE, "Not a file: " + rel);
        long len = f.length();
        if (len > MAX_TEXT_BYTES) throw new WsException(WsException.TOO_LARGE, "File is too large to open");
        long modified = f.lastModified();
        byte[] data = readAll(f, (int) len);
        TextDoc d = new TextDoc();
        int off = 0;
        if (data.length >= 3 && data[0] == BOM[0] && data[1] == BOM[1] && data[2] == BOM[2]) {
            d.bom = true;
            off = 3;
        }
        if (hasNul(data, 8000)) throw new WsException(WsException.BINARY, "Binary file");
        CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String s;
        try {
            s = dec.decode(ByteBuffer.wrap(data, off, data.length - off)).toString();
        } catch (CharacterCodingException e) {
            throw new WsException(WsException.NOT_UTF8, "File is not UTF-8 text");
        }
        if (s.indexOf("\r\n") >= 0) {
            d.crlf = true;
            s = s.replace("\r\n", "\n");
        }
        d.text = s;
        d.modified = modified;
        d.size = len;
        return d;
    }

    /** Reads some lines of a text file, streaming, so big files are fine. start is 1-based. */
    public Slice readLines(String rel, int start, int max, int maxLineChars) throws WsException {
        File f = resolve(rel);
        if (!f.exists()) throw new WsException(WsException.NOT_FOUND, "File not found: " + rel);
        if (!f.isFile()) throw new WsException(WsException.NOT_FILE, "Not a file: " + rel);
        if (f.length() > MAX_READ_BYTES) throw new WsException(WsException.TOO_LARGE, "File is too large to read");
        if (looksBinary(rel)) throw new WsException(WsException.BINARY, "Binary file");
        int from = Math.max(1, start);
        int to = from + Math.max(1, max) - 1;
        Slice s = new Slice();
        s.from = from;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            int no = 0;
            while ((line = r.readLine()) != null) {
                no++;
                if (no == 1 && !line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
                if (no >= from && no <= to) {
                    if (line.length() > maxLineChars) {
                        line = line.substring(0, maxLineChars);
                        s.cutLines = true;
                    }
                    s.lines.add(line);
                }
            }
            s.total = no;
        } catch (IOException e) {
            throw new WsException(WsException.IO, "Cannot read file");
        }
        return s;
    }

    // ------------------------------------------------------------ writing

    /** Writes text safely: first to a temp file, then renamed over the target. */
    public void writeText(String rel, String text, boolean crlf, boolean bom) throws WsException {
        String n = normalize(rel);
        if (n.isEmpty()) throw new WsException(WsException.ROOT, "Cannot write the workspace root");
        validateName(lastSegment(n));
        File f = resolve(n);
        if (f.isDirectory()) throw new WsException(WsException.NOT_FILE, "Not a file: " + rel);
        String out = crlf ? text.replace("\r\n", "\n").replace("\n", "\r\n") : text;
        byte[] body = out.getBytes(StandardCharsets.UTF_8);
        if (body.length + (bom ? 3L : 0L) > MAX_TEXT_BYTES) {
            throw new WsException(WsException.TOO_LARGE, "Text is too large to save");
        }
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new WsException(WsException.IO, "Cannot create the folder");
        }
        File tmp = new File(parent, f.getName() + TMP_SUFFIX);
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            if (bom) os.write(BOM);
            os.write(body);
            os.flush();
            os.getFD().sync();
        } catch (IOException e) {
            tmp.delete();
            throw new WsException(WsException.IO, "Cannot save the file");
        }
        if (!tmp.renameTo(f)) {
            if (f.exists() && !f.delete()) {
                tmp.delete();
                throw new WsException(WsException.IO, "Cannot replace the file");
            }
            if (!tmp.renameTo(f)) {
                tmp.delete();
                throw new WsException(WsException.IO, "Cannot replace the file");
            }
        }
    }

    /** Copies a stream into a workspace file (used by import). Returns the bytes written. */
    public long writeStream(String rel, InputStream in, long maxBytes) throws WsException {
        String n = normalize(rel);
        if (n.isEmpty()) throw new WsException(WsException.ROOT, "Cannot write the workspace root");
        validateName(lastSegment(n));
        File f = resolve(n);
        if (f.isDirectory()) throw new WsException(WsException.NOT_FILE, "Not a file: " + rel);
        File parent = f.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new WsException(WsException.IO, "Cannot create the folder");
        }
        File tmp = new File(parent, f.getName() + TMP_SUFFIX);
        long total = 0;
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > maxBytes) {
                    throw new WsException(WsException.TOO_LARGE, "File is too large");
                }
                os.write(buf, 0, r);
            }
        } catch (WsException e) {
            tmp.delete();
            throw e;
        } catch (IOException e) {
            tmp.delete();
            throw new WsException(WsException.IO, "Cannot copy the file");
        }
        if (f.exists()) f.delete();
        if (!tmp.renameTo(f)) {
            tmp.delete();
            throw new WsException(WsException.IO, "Cannot copy the file");
        }
        return total;
    }

    public void createFile(String rel) throws WsException {
        String n = normalize(rel);
        if (n.isEmpty()) throw new WsException(WsException.ROOT, "Cannot create the workspace root");
        validateName(lastSegment(n));
        File f = resolve(n);
        if (f.exists()) throw new WsException(WsException.EXISTS, "Already exists: " + rel);
        writeText(n, "", false, false);
    }

    public void mkdir(String rel) throws WsException {
        String n = normalize(rel);
        if (n.isEmpty()) throw new WsException(WsException.ROOT, "Cannot create the workspace root");
        validateName(lastSegment(n));
        File f = resolve(n);
        if (f.exists()) throw new WsException(WsException.EXISTS, "Already exists: " + rel);
        if (!f.mkdirs()) throw new WsException(WsException.IO, "Cannot create the folder");
    }

    public void delete(String rel) throws WsException {
        String n = normalize(rel);
        if (n.isEmpty()) throw new WsException(WsException.ROOT, "Cannot delete the workspace root");
        File f = resolve(n);
        if (!f.exists()) throw new WsException(WsException.NOT_FOUND, "Not found: " + rel);
        deleteRec(f);
        if (f.exists()) throw new WsException(WsException.IO, "Could not delete everything");
    }

    private void deleteRec(File f) {
        if (f.isDirectory() && !isSymlink(f)) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }

    /** Renames inside the same folder. Returns the new relative path. */
    public String rename(String rel, String newName) throws WsException {
        String n = normalize(rel);
        if (n.isEmpty()) throw new WsException(WsException.ROOT, "Cannot rename the workspace root");
        validateName(newName);
        File src = resolve(n);
        if (!src.exists()) throw new WsException(WsException.NOT_FOUND, "Not found: " + rel);
        String parent = parentOf(n);
        String target = parent.isEmpty() ? newName : parent + "/" + newName;
        File dst = resolve(target);
        if (dst.exists()) throw new WsException(WsException.EXISTS, "Already exists: " + newName);
        if (!src.renameTo(dst)) throw new WsException(WsException.IO, "Rename failed");
        return target;
    }

    /** Moves into another folder. Returns the new relative path. */
    public String move(String rel, String destDir) throws WsException {
        String n = normalize(rel);
        String d = normalize(destDir);
        if (n.isEmpty()) throw new WsException(WsException.ROOT, "Cannot move the workspace root");
        File src = resolve(n);
        File dir = resolve(d);
        if (!src.exists()) throw new WsException(WsException.NOT_FOUND, "Not found: " + rel);
        if (!dir.isDirectory()) throw new WsException(WsException.NOT_DIR, "Not a folder: " + destDir);
        if (src.isDirectory() && (d.equals(n) || d.startsWith(n + "/"))) {
            throw new WsException(WsException.INSIDE_SELF, "Cannot move a folder into itself");
        }
        String name = lastSegment(n);
        String target = d.isEmpty() ? name : d + "/" + name;
        if (target.equals(n)) return n;
        File dst = resolve(target);
        if (dst.exists()) throw new WsException(WsException.EXISTS, "Already exists: " + name);
        if (!src.renameTo(dst)) {
            copyRec(src, dst, new int[]{0});
            deleteRec(src);
        }
        return target;
    }

    /** Copies into a folder under a new name (or a free name when newName is null). Returns the new path. */
    public String copy(String rel, String destDir, String newName) throws WsException {
        String n = normalize(rel);
        String d = normalize(destDir);
        if (n.isEmpty()) throw new WsException(WsException.ROOT, "Cannot copy the workspace root");
        File src = resolve(n);
        File dir = resolve(d);
        if (!src.exists()) throw new WsException(WsException.NOT_FOUND, "Not found: " + rel);
        if (!dir.isDirectory()) throw new WsException(WsException.NOT_DIR, "Not a folder: " + destDir);
        if (src.isDirectory() && (d.equals(n) || d.startsWith(n + "/"))) {
            throw new WsException(WsException.INSIDE_SELF, "Cannot copy a folder into itself");
        }
        String name;
        if (newName != null) {
            name = newName;
        } else {
            String base = lastSegment(n);
            name = new File(dir, base).exists() ? uniqueName(d, base) : base;
        }
        validateName(name);
        String target = d.isEmpty() ? name : d + "/" + name;
        File dst = resolve(target);
        if (dst.exists()) throw new WsException(WsException.EXISTS, "Already exists: " + name);
        copyRec(src, dst, new int[]{0});
        return target;
    }

    public String duplicate(String rel) throws WsException {
        String n = normalize(rel);
        return copy(n, parentOf(n), null);
    }

    private void copyRec(File src, File dst, int[] count) throws WsException {
        if (++count[0] > 20000) throw new WsException(WsException.LIMIT, "Too many files");
        if (isSymlink(src)) return;
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) throw new WsException(WsException.IO, "Cannot create the folder");
            File[] kids = src.listFiles();
            if (kids != null) for (File k : kids) copyRec(k, new File(dst, k.getName()), count);
        } else {
            try (InputStream in = new FileInputStream(src); FileOutputStream os = new FileOutputStream(dst)) {
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) > 0) os.write(buf, 0, r);
            } catch (IOException e) {
                throw new WsException(WsException.IO, "Copy failed");
            }
        }
    }

    /** "name copy.ext", "name copy 2.ext" ... the first one that is free in the folder. */
    public String uniqueName(String dirRel, String name) throws WsException {
        File dir = resolve(dirRel);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String cand = base + " copy" + ext;
        int i = 2;
        while (new File(dir, cand).exists()) {
            cand = base + " copy " + i + ext;
            i++;
            if (i > 999) throw new WsException(WsException.LIMIT, "No free name");
        }
        return cand;
    }

    /** Same name but free: used by import so nothing is overwritten. */
    public String freeFolderName(String dirRel, String name) throws WsException {
        File dir = resolve(dirRel);
        String cand = name;
        int i = 2;
        while (new File(dir, cand).exists()) {
            cand = name + "-" + i;
            i++;
            if (i > 999) throw new WsException(WsException.LIMIT, "No free name");
        }
        return cand;
    }

    // ------------------------------------------------------------ small helpers

    public static String lastSegment(String rel) {
        int i = rel.lastIndexOf('/');
        return i < 0 ? rel : rel.substring(i + 1);
    }

    public static String parentOf(String rel) {
        int i = rel.lastIndexOf('/');
        return i < 0 ? "" : rel.substring(0, i);
    }

    public static String join(String dir, String name) {
        return dir == null || dir.isEmpty() ? name : dir + "/" + name;
    }
}
