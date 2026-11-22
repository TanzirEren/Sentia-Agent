package com.sentia.ai.app.util;

import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.data.db.ProjectFileEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the project part of the system prompt: instructions, memory, plan-first rule,
 * memory protocol and the project files. Small file sets are sent in full; large sets are
 * indexed and only the excerpts that match the user's question (BM25 ranking) are added.
 */
public final class ProjectContext {
    /** If all enabled files together are at most this many characters, send them in full. */
    public static final int FULL_BUDGET = 24000;
    /** Otherwise send at most this many characters of the best matching excerpts. */
    public static final int EXCERPT_BUDGET = 14000;
    private static final int CHUNK_TARGET = 1200;
    private static final int CHUNK_MIN = 500;

    private ProjectContext() {}

    private static final class Chunk {
        String file;
        int index;
        int total;
        String text;
        Map<String, Integer> tf = new HashMap<>();
        int len;
        double score;
        int fileOrder;
    }

    public static String build(FolderEntity f, List<ProjectFileEntity> files, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are working inside the project \"").append(f.name == null ? "" : f.name).append("\".");
        if (f.description != null && !f.description.trim().isEmpty()) {
            sb.append(" Project description: ").append(f.description.trim());
        }
        if (f.instructions != null && !f.instructions.trim().isEmpty()) {
            sb.append("\n\nProject instructions (follow them in every reply of this chat):\n")
                    .append(f.instructions.trim());
        }
        if (f.memory != null && !f.memory.trim().isEmpty()) {
            sb.append("\n\nProject memory (facts and decisions saved earlier; treat them as true unless the user says otherwise):\n")
                    .append(f.memory.trim());
        }
        if (f.planFirst) {
            sb.append("\n\nPlan first: before a big task (many steps, several files or larger code changes) "
                    + "reply with a short numbered plan and ask the user to confirm. Answer small questions directly.");
        }
        sb.append("\n\nProject memory protocol: if the user states a lasting fact, preference or decision about "
                + "this project, end your reply with one line <remember>short note</remember>. "
                + "Use it rarely, never for temporary things, and never mention the tag.");
        appendFiles(sb, files, query);
        return sb.toString();
    }

    private static void appendFiles(StringBuilder sb, List<ProjectFileEntity> files, String query) {
        if (files == null || files.isEmpty()) return;
        List<String> names = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        long total = 0;
        for (ProjectFileEntity pf : files) {
            if (!pf.enabled) continue;
            String t = AttachmentUtil.readText(pf.path);
            if (t == null || t.trim().isEmpty()) continue;
            names.add(pf.name == null ? "file" : pf.name);
            texts.add(t);
            total += t.length();
        }
        if (names.isEmpty()) return;

        if (total <= FULL_BUDGET) {
            sb.append("\n\nProject files (use them when they are relevant to the question):");
            for (int i = 0; i < names.size(); i++) {
                sb.append("\n\n[Project file: ").append(names.get(i)).append("]\n```\n")
                        .append(texts.get(i)).append("\n```");
            }
            return;
        }

        sb.append("\n\nThis project has ").append(names.size()).append(" files. Only the excerpts that best match "
                + "the latest question are shown below. If you need another part, say which file and ask the user.");
        sb.append("\nFile index:");
        for (int i = 0; i < names.size(); i++) {
            sb.append("\n- ").append(names.get(i)).append(" (").append(texts.get(i).length()).append(" chars)");
        }
        List<Chunk> all = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) all.addAll(chunk(names.get(i), texts.get(i), i));
        List<Chunk> picked = rank(all, query);
        for (Chunk c : picked) {
            sb.append("\n\n[Excerpt from ").append(c.file).append(", part ").append(c.index + 1)
                    .append(" of ").append(c.total).append("]\n```\n").append(c.text).append("\n```");
        }
    }

    // ------------------------------------------------------------ chunking and ranking

    private static List<Chunk> chunk(String file, String text, int order) {
        List<Chunk> out = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        StringBuilder cur = new StringBuilder();
        for (String line : lines) {
            cur.append(line).append('\n');
            boolean blank = line.trim().isEmpty();
            if (cur.length() >= CHUNK_TARGET || (blank && cur.length() >= CHUNK_MIN)) {
                out.add(makeChunk(file, cur.toString(), order));
                cur.setLength(0);
            }
        }
        if (cur.toString().trim().length() > 0) out.add(makeChunk(file, cur.toString(), order));
        for (int i = 0; i < out.size(); i++) {
            out.get(i).index = i;
            out.get(i).total = out.size();
        }
        return out;
    }

    private static Chunk makeChunk(String file, String text, int order) {
        Chunk c = new Chunk();
        c.file = file;
        c.text = text.trim();
        c.fileOrder = order;
        List<String> toks = tokens(c.text + " " + file);
        c.len = toks.size();
        for (String t : toks) {
            Integer n = c.tf.get(t);
            c.tf.put(t, n == null ? 1 : n + 1);
        }
        return c;
    }

    static List<String> tokens(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        StringBuilder w = new StringBuilder();
        String low = s.toLowerCase(Locale.ROOT);
        for (int i = 0; i < low.length(); i++) {
            char ch = low.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                w.append(ch);
            } else {
                if (w.length() >= 2) out.add(w.toString());
                w.setLength(0);
            }
        }
        if (w.length() >= 2) out.add(w.toString());
        return out;
    }

    private static List<Chunk> rank(List<Chunk> all, String query) {
        Set<String> q = new HashSet<>(tokens(query));
        int n = all.size();
        double avg = 0;
        for (Chunk c : all) avg += c.len;
        avg = n == 0 ? 1 : Math.max(1, avg / n);
        Map<String, Integer> df = new HashMap<>();
        for (Chunk c : all) {
            for (String t : c.tf.keySet()) {
                if (q.contains(t)) {
                    Integer d = df.get(t);
                    df.put(t, d == null ? 1 : d + 1);
                }
            }
        }
        double k1 = 1.2, b = 0.75;
        for (Chunk c : all) {
            double s = 0;
            for (String t : q) {
                Integer f = c.tf.get(t);
                if (f == null) continue;
                int d = df.containsKey(t) ? df.get(t) : 0;
                double idf = Math.log(1 + (n - d + 0.5) / (d + 0.5));
                s += idf * (f * (k1 + 1)) / (f + k1 * (1 - b + b * c.len / avg));
            }
            c.score = s;
        }
        List<Chunk> sorted = new ArrayList<>(all);
        Collections.sort(sorted, new Comparator<Chunk>() {
            @Override
            public int compare(Chunk a, Chunk b2) {
                int r = Double.compare(b2.score, a.score);
                if (r != 0) return r;
                if (a.fileOrder != b2.fileOrder) return a.fileOrder - b2.fileOrder;
                return a.index - b2.index;
            }
        });
        List<Chunk> picked = new ArrayList<>();
        int used = 0;
        boolean anyMatch = !sorted.isEmpty() && sorted.get(0).score > 0;
        for (Chunk c : sorted) {
            if (anyMatch && c.score <= 0) break;
            if (!anyMatch && c.index != 0) continue; // no keyword match: show the start of each file
            if (used + c.text.length() > EXCERPT_BUDGET && !picked.isEmpty()) {
                if (anyMatch) continue;
                break;
            }
            picked.add(c);
            used += c.text.length();
        }
        Collections.sort(picked, new Comparator<Chunk>() {
            @Override
            public int compare(Chunk a, Chunk b2) {
                if (a.fileOrder != b2.fileOrder) return a.fileOrder - b2.fileOrder;
                return a.index - b2.index;
            }
        });
        return picked;
    }

    /** Rough token cost of the project block, for the project page. */
    public static int estimateTokens(FolderEntity f, List<ProjectFileEntity> files) {
        long chars = 400;
        if (f.instructions != null) chars += f.instructions.length();
        if (f.memory != null) chars += f.memory.length();
        if (f.description != null) chars += f.description.length();
        long fileChars = 0;
        if (files != null) for (ProjectFileEntity pf : files) if (pf.enabled) fileChars += pf.size;
        chars += fileChars <= FULL_BUDGET ? fileChars : EXCERPT_BUDGET + 300;
        return (int) (chars / 4);
    }
}
