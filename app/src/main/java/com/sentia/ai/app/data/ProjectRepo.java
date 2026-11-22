package com.sentia.ai.app.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import androidx.lifecycle.LiveData;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.db.AppDatabase;
import com.sentia.ai.app.data.db.FolderCount;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.data.db.ProjectFileEntity;
import com.sentia.ai.app.data.db.SessionEntity;
import com.sentia.ai.app.model.Provider;
import com.sentia.ai.app.model.ProjectCommand;
import com.sentia.ai.app.net.ChatMsg;
import com.sentia.ai.app.net.ChatRequest;
import com.sentia.ai.app.net.ClientFactory;
import com.sentia.ai.app.net.StreamListener;
import com.sentia.ai.app.util.AttachmentUtil;
import com.sentia.ai.app.util.Callback;
import com.sentia.ai.app.util.MemoryTags;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All project work in one place: create, edit, pin, memory, commands, files and the AI brief.
 * Database and file work runs on one background thread; callbacks come back on the main thread.
 */
public final class ProjectRepo {
    public static final int MAX_FILES = 25;
    public static final long MAX_FILE_BYTES = 1024L * 1024L;
    public static final int MAX_MEMORY_CHARS = 4000;

    /** Result of an AI brief request: text, or an error message. */
    public static final class BriefResult {
        public String text;
        public String error;
    }

    /** Result of adding files. */
    public static final class AddResult {
        public int added;
        public int rejected;
        public boolean limitReached;
    }

    private static ProjectRepo instance;

    public static synchronized ProjectRepo get(Context c) {
        if (instance == null) instance = new ProjectRepo(c.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final AppDatabase db;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private ProjectRepo(Context app) {
        this.app = app;
        this.db = AppDatabase.get(app);
    }

    // ------------------------------------------------------------ live data

    public LiveData<List<FolderEntity>> folders() {
        return db.folderDao().allRecent();
    }

    public LiveData<FolderEntity> folder(long id) {
        return db.folderDao().live(id);
    }

    public LiveData<List<SessionEntity>> chatsIn(long folderId) {
        return db.sessionDao().inFolderLive(folderId);
    }

    public LiveData<List<ProjectFileEntity>> files(long folderId) {
        return db.projectFileDao().live(folderId);
    }

    public LiveData<List<FolderCount>> chatCounts() {
        return db.sessionDao().folderCounts();
    }

    public LiveData<List<FolderCount>> fileCounts() {
        return db.projectFileDao().counts();
    }

    // ------------------------------------------------------------ projects

    public void create(final String name, final String desc, final int icon, final Callback<FolderEntity> cb) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            final FolderEntity f = new FolderEntity();
            f.name = name;
            f.description = desc;
            f.icon = icon;
            f.createdAt = now;
            f.updatedAt = now;
            f.id = db.folderDao().insert(f);
            if (cb != null) main.post(() -> cb.onResult(f));
        });
    }

    public void update(final long id, final String name, final String desc, final int icon) {
        io.execute(() -> db.folderDao().updateInfo(id, name, desc, icon, System.currentTimeMillis()));
    }

    public void setPinned(final long id, final boolean pinned) {
        io.execute(() -> db.folderDao().setPinned(id, pinned));
    }

    public void setSessionPinned(final long sessionId, final boolean pinned) {
        io.execute(() -> db.sessionDao().setPinned(sessionId, pinned));
    }

    public void setInstructions(final long id, final String text) {
        io.execute(() -> db.folderDao().setInstructions(id, text, System.currentTimeMillis()));
    }

    public void setMemory(final long id, final String text) {
        io.execute(() -> db.folderDao().setMemory(id, limitMemory(text), System.currentTimeMillis()));
    }

    /** Adds notes (from the AI) to the project memory, skipping duplicates. */
    public void appendMemory(final long id, final List<String> notes) {
        if (notes == null || notes.isEmpty()) return;
        io.execute(() -> {
            FolderEntity f = db.folderDao().get(id);
            if (f == null) return;
            String cur = f.memory == null ? "" : f.memory.trim();
            StringBuilder sb = new StringBuilder(cur);
            for (String n : notes) {
                String line = n.startsWith("- ") ? n : "- " + n;
                if (cur.toLowerCase(Locale.ROOT).contains(line.toLowerCase(Locale.ROOT))) continue;
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
            db.folderDao().setMemory(id, limitMemory(sb.toString()), System.currentTimeMillis());
        });
    }

    /** Keeps the newest lines when the memory gets too long. */
    private static String limitMemory(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() <= MAX_MEMORY_CHARS) return t;
        String[] lines = t.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = lines.length - 1; i >= 0; i--) {
            if (sb.length() + lines[i].length() + 1 > MAX_MEMORY_CHARS) break;
            sb.insert(0, lines[i] + "\n");
        }
        return sb.toString().trim();
    }

    public void setCommands(final long id, final List<ProjectCommand> list) {
        final String json = gson.toJson(list == null ? new ArrayList<ProjectCommand>() : list);
        io.execute(() -> db.folderDao().setCommands(id, json, System.currentTimeMillis()));
    }

    public static List<ProjectCommand> parseCommands(String json) {
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<ProjectCommand>>() {
            }.getType();
            List<ProjectCommand> l = new Gson().fromJson(json, t);
            return l == null ? new ArrayList<ProjectCommand>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void setPlanFirst(final long id, final boolean on) {
        io.execute(() -> db.folderDao().setPlanFirst(id, on));
    }

    public void delete(final long id, final boolean deleteChats, final Runnable done) {
        io.execute(() -> {
            if (deleteChats) db.sessionDao().deleteInFolder(id);
            else db.sessionDao().clearFolder(id);
            db.folderDao().delete(id);
            deleteDir(new File(new File(app.getFilesDir(), "projects"), String.valueOf(id)));
            if (done != null) main.post(done);
        });
    }

    public void renameSession(final long sessionId, final String title) {
        io.execute(() -> db.sessionDao().rename(sessionId, title));
    }

    public void deleteSession(final long sessionId) {
        io.execute(() -> db.sessionDao().delete(sessionId));
    }

    public void moveSession(final long sessionId, final long folderId) {
        io.execute(() -> {
            db.sessionDao().moveToFolder(sessionId, folderId);
            if (folderId != 0) db.folderDao().touch(folderId, System.currentTimeMillis());
        });
    }

    private static void deleteDir(File d) {
        File[] kids = d.listFiles();
        if (kids != null) for (File k : kids) k.delete();
        d.delete();
    }

    // ------------------------------------------------------------ files

    public void addFiles(final long folderId, final List<Uri> uris, final Callback<AddResult> cb) {
        io.execute(() -> {
            AddResult r = new AddResult();
            int have = db.projectFileDao().list(folderId).size();
            File dir = new File(new File(app.getFilesDir(), "projects"), String.valueOf(folderId));
            if (!dir.exists()) dir.mkdirs();
            for (Uri u : uris) {
                if (have + r.added >= MAX_FILES) {
                    r.limitReached = true;
                    r.rejected++;
                    continue;
                }
                ProjectFileEntity pf = copyIn(u, dir, folderId);
                if (pf == null) {
                    r.rejected++;
                    continue;
                }
                db.projectFileDao().insert(pf);
                r.added++;
            }
            if (r.added > 0) db.folderDao().touch(folderId, System.currentTimeMillis());
            if (cb != null) main.post(() -> cb.onResult(r));
        });
    }

    private ProjectFileEntity copyIn(Uri uri, File dir, long folderId) {
        File out = null;
        try {
            ContentResolver cr = app.getContentResolver();
            String name = "file.txt";
            Cursor c = cr.query(uri, null, null, null, null);
            if (c != null) {
                try {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                } finally {
                    c.close();
                }
            }
            name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
            String mime = cr.getType(uri);
            out = new File(dir, System.currentTimeMillis() + "_" + name);
            long total = 0;
            try (InputStream in = cr.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                if (in == null) return null;
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_FILE_BYTES) {
                        os.close();
                        out.delete();
                        return null;
                    }
                    os.write(buf, 0, n);
                }
            }
            if (total == 0 || AttachmentUtil.readText(out.getAbsolutePath()) == null) {
                out.delete();
                return null; // empty or binary
            }
            ProjectFileEntity pf = new ProjectFileEntity();
            pf.folderId = folderId;
            pf.name = name;
            pf.mime = mime == null ? "text/plain" : mime;
            pf.path = out.getAbsolutePath();
            pf.size = total;
            pf.addedAt = System.currentTimeMillis();
            pf.enabled = true;
            return pf;
        } catch (Exception e) {
            if (out != null) out.delete();
            return null;
        }
    }

    public void setFileEnabled(final long fileId, final boolean on) {
        io.execute(() -> db.projectFileDao().setEnabled(fileId, on));
    }

    public void deleteFile(final long fileId) {
        io.execute(() -> {
            ProjectFileEntity f = db.projectFileDao().get(fileId);
            if (f != null && f.path != null) new File(f.path).delete();
            db.projectFileDao().delete(fileId);
        });
    }

    // ------------------------------------------------------------ AI project brief

    /** Asks the selected model for a short brief of the project. Nothing is saved until the user agrees. */
    public void generateBrief(final long folderId, final Callback<BriefResult> cb) {
        io.execute(() -> {
            final BriefResult res = new BriefResult();
            try {
                FolderEntity f = db.folderDao().get(folderId);
                SettingsStore st = SettingsStore.get(app);
                ProviderStore ps = ProviderStore.get(app);
                Provider p = ps.get(st.selectedProviderId());
                String model = st.selectedModelId();
                if (f == null) {
                    res.error = app.getString(R.string.brief_failed);
                } else if (p == null || !p.enabled || model == null || model.isEmpty()) {
                    res.error = app.getString(R.string.need_provider);
                } else {
                    String source = collectSource(f);
                    if (source.isEmpty()) {
                        res.error = app.getString(R.string.brief_nothing);
                    } else {
                        runBrief(f, p, model, source, cb);
                        return;
                    }
                }
            } catch (Exception e) {
                res.error = e.getMessage() == null ? app.getString(R.string.brief_failed) : e.getMessage();
            }
            main.post(() -> cb.onResult(res));
        });
    }

    private String collectSource(FolderEntity f) {
        StringBuilder sb = new StringBuilder();
        if (f.description != null && !f.description.trim().isEmpty()) sb.append("Description: ").append(f.description.trim()).append("\n");
        if (f.instructions != null && !f.instructions.trim().isEmpty()) sb.append("Instructions: ").append(f.instructions.trim()).append("\n");
        if (f.memory != null && !f.memory.trim().isEmpty()) sb.append("Memory notes:\n").append(f.memory.trim()).append("\n");
        List<SessionEntity> sessions = db.sessionDao().inFolder(f.id);
        int taken = 0;
        for (SessionEntity s : sessions) {
            if (taken >= 8 || sb.length() > 9000) break;
            List<MessageEntity> msgs = db.messageDao().list(s.id);
            if (msgs.isEmpty()) continue;
            taken++;
            sb.append("\n### Chat: ").append(s.title == null ? "" : s.title).append("\n");
            int from = Math.max(0, msgs.size() - 6);
            for (int i = from; i < msgs.size(); i++) {
                MessageEntity m = msgs.get(i);
                if (!"user".equals(m.role) && !"assistant".equals(m.role)) continue;
                String c = m.content == null ? "" : MemoryTags.strip(m.content);
                if (c.length() > 500) c = c.substring(0, 500) + "...";
                sb.append("user".equals(m.role) ? "User: " : "AI: ").append(c.replace('\n', ' ')).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private void runBrief(FolderEntity f, Provider p, String model, String source, final Callback<BriefResult> cb) {
        ChatRequest req = new ChatRequest();
        req.provider = p;
        req.modelId = model;
        req.systemPrompt = app.getString(R.string.brief_system);
        int ki = KeyRotator.pick(p);
        req.keyIndex = Math.max(ki, 0);
        req.apiKey = ki >= 0 ? KeyRotator.keyAt(p, ki) : "";
        req.maxTokens = 0;
        ChatMsg m = new ChatMsg();
        m.role = "user";
        m.text = app.getString(R.string.brief_prompt) + "\n\nProject: " + (f.name == null ? "" : f.name) + "\n\n" + source;
        req.messages.add(m);

        final StringBuilder out = new StringBuilder();
        ClientFactory.forProvider(p).stream(req, new StreamListener() {
            @Override
            public void onDelta(String text) {
                synchronized (out) {
                    out.append(text);
                }
            }

            @Override
            public void onUsage(int promptTokens, int completionTokens) {
            }

            @Override
            public void onDone() {
                final BriefResult r = new BriefResult();
                synchronized (out) {
                    r.text = MemoryTags.strip(out.toString()).trim();
                }
                if (r.text.isEmpty()) r.error = app.getString(R.string.brief_failed);
                main.post(() -> cb.onResult(r));
            }

            @Override
            public void onError(String message) {
                final BriefResult r = new BriefResult();
                r.error = message == null || message.isEmpty() ? app.getString(R.string.brief_failed) : message;
                main.post(() -> cb.onResult(r));
            }
        });
    }
}
