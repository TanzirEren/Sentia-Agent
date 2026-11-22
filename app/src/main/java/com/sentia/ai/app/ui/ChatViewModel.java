package com.sentia.ai.app.ui;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.MediatorLiveData;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sentia.ai.app.data.KeyRotator;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.data.ProfileStore;
import com.sentia.ai.app.data.ProviderStore;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.data.db.AppDatabase;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.data.db.ProjectFileEntity;
import com.sentia.ai.app.data.db.SessionEntity;
import com.sentia.ai.app.model.Attachment;
import com.sentia.ai.app.model.ModelEntry;
import com.sentia.ai.app.model.Provider;
import com.sentia.ai.app.net.Cancelable;
import com.sentia.ai.app.net.ChatMsg;
import com.sentia.ai.app.net.ChatRequest;
import com.sentia.ai.app.net.ClientFactory;
import com.sentia.ai.app.net.LlmClient;
import com.sentia.ai.app.net.StreamListener;
import com.sentia.ai.app.util.AttachmentUtil;
import com.sentia.ai.app.util.ImageUtil;
import com.sentia.ai.app.util.MemoryTags;
import com.sentia.ai.app.util.ProjectContext;
import com.sentia.ai.app.util.TokenEstimator;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns the current chat: loads history, sends messages, streams replies,
 * and saves everything to Room. All state changes happen on the main thread;
 * database work runs on a single background thread.
 */
public class ChatViewModel extends AndroidViewModel {
    private final AppDatabase db;
    private final SettingsStore settings;
    private final ProviderStore providers;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private final MutableLiveData<List<MessageEntity>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> streaming = new MutableLiveData<>(false);
    private final MutableLiveData<String> toast = new MutableLiveData<>();
    private final MutableLiveData<String> title = new MutableLiveData<>("");
    private final MediatorLiveData<List<SessionEntity>> sessionsMediator = new MediatorLiveData<>();
    private LiveData<List<SessionEntity>> sessionsSource;
    private final MutableLiveData<Long> currentSessionLive = new MutableLiveData<>(0L);
    private final MutableLiveData<MessageEntity> editing = new MutableLiveData<>(null);

    private long sessionId = 0;
    private int streamToken = 0;
    private Cancelable call;
    private long streamMsgId;
    private final StringBuilder sb = new StringBuilder();
    private boolean gotUsage;
    private int usageIn, usageOut;
    private boolean flushPending;
    private volatile int lastRequestChars;
    private final Set<Integer> triedKeys = new HashSet<>();
    private long pendingFolderId = 0;
    private long streamFolderId = 0;
    private final MutableLiveData<Long> currentFolderLive = new MutableLiveData<>(0L);
    private final ProjectRepo repo;
    private final LiveData<List<FolderEntity>> foldersLive;

    public ChatViewModel(@NonNull Application app) {
        super(app);
        db = AppDatabase.get(app);
        settings = SettingsStore.get(app);
        providers = ProviderStore.get(app);
        repo = ProjectRepo.get(app);
        refreshSessions("");
        foldersLive = db.folderDao().allRecent();
    }

    private void refreshSessions(String q) {
        String t = q == null ? "" : q.trim();
        if (sessionsSource != null) sessionsMediator.removeSource(sessionsSource);
        sessionsSource = t.isEmpty() ? db.sessionDao().all() : db.sessionDao().search("%" + t + "%");
        sessionsMediator.addSource(sessionsSource, sessionsMediator::setValue);
    }

    // ------------------------------------------------------------ live data

    public LiveData<List<MessageEntity>> messages() { return messages; }
    public LiveData<Boolean> streaming() { return streaming; }
    public LiveData<String> toast() { return toast; }
    public LiveData<String> title() { return title; }
    public LiveData<Long> currentSession() { return currentSessionLive; }
    public LiveData<MessageEntity> editing() { return editing; }
    public LiveData<List<SessionEntity>> sessions() { return sessionsMediator; }
    public LiveData<List<FolderEntity>> folders() { return foldersLive; }
    /** Id of the project the open chat belongs to, 0 when it is in no project. */
    public LiveData<Long> currentFolder() { return currentFolderLive; }
    public long currentFolderId() { Long v = currentFolderLive.getValue(); return v == null ? 0 : v; }

    public void clearToast() { toast.setValue(null); }

    public void setSearch(String q) { refreshSessions(q); }

    public long currentSessionId() { return sessionId; }

    private List<MessageEntity> cur() {
        List<MessageEntity> l = messages.getValue();
        return l == null ? new ArrayList<>() : l;
    }

    private void setList(List<MessageEntity> l) { messages.setValue(l); }

    // ------------------------------------------------------------ model selection

    public Provider selectedProvider() {
        Provider p = providers.get(settings.selectedProviderId());
        return (p != null && p.enabled) ? p : null;
    }

    public boolean hasModel() {
        ensureSelection();
        Provider p = selectedProvider();
        String m = settings.selectedModelId();
        if (p == null || m == null || m.isEmpty()) return false;
        for (ModelEntry e : p.models) if (m.equals(e.id) && e.enabled) return true;
        return false;
    }

    /** Picks the first available model if nothing valid is selected. */
    public void ensureSelection() {
        Provider p = selectedProvider();
        String m = settings.selectedModelId();
        boolean valid = false;
        if (p != null && m != null) {
            for (ModelEntry e : p.models) if (m.equals(e.id) && e.enabled) valid = true;
        }
        if (!valid) {
            String[] first = providers.firstAvailable();
            if (first != null) settings.setSelected(first[0], first[1]);
        }
    }

    // ------------------------------------------------------------ session management

    public void newChat() {
        stop();
        pendingFolderId = 0;
        sessionId = 0;
        currentFolderLive.setValue(0L);
        currentSessionLive.setValue(0L);
        title.setValue("");
        editing.setValue(null);
        setList(new ArrayList<>());
    }

    /** Starts an empty chat that will be saved inside the given project. */
    public void newChatInFolder(FolderEntity f) {
        newChat();
        pendingFolderId = f.id;
        currentFolderLive.setValue(f.id);
        title.setValue(f.name == null ? "" : f.name);
    }

    /** Starts an empty chat inside a project, looked up by id (used from the project page). */
    public void newChatInFolderId(final long folderId) {
        io.execute(() -> {
            final FolderEntity f = db.folderDao().get(folderId);
            if (f != null) main.post(() -> newChatInFolder(f));
        });
    }

    public void openSession(final long id) {
        stop();
        pendingFolderId = 0;
        io.execute(() -> {
            final SessionEntity s = db.sessionDao().get(id);
            final List<MessageEntity> l = db.messageDao().list(id);
            main.post(() -> {
                if (s == null) return;
                sessionId = id;
                currentFolderLive.setValue(s.folderId);
                currentSessionLive.setValue(id);
                title.setValue(s.title == null ? "" : s.title);
                editing.setValue(null);
                setList(l);
            });
        });
    }

    public void renameSession(final long id, final String newTitle) {
        io.execute(() -> {
            db.sessionDao().rename(id, newTitle);
            main.post(() -> {
                if (id == sessionId) title.setValue(newTitle);
            });
        });
    }

    public void deleteSession(final long id) {
        io.execute(() -> {
            db.sessionDao().delete(id);
            main.post(() -> {
                if (id == sessionId) newChat();
            });
        });
    }


    // ------------------------------------------------------------ projects (folders)

    public void createFolder(String name, String desc, int icon, final boolean openChat) {
        repo.create(name, desc, icon, f -> {
            if (openChat) newChatInFolder(f);
        });
    }

    public void createFolderAndMove(String name, String desc, int icon, final long sid) {
        repo.create(name, desc, icon, f -> moveSession(sid, f.id));
    }

    public void deleteFolder(final long id, final boolean deleteChats) {
        repo.delete(id, deleteChats, this::validateCurrent);
    }

    public void moveSession(final long sid, final long folderId) {
        repo.moveSession(sid, folderId);
        if (sid == sessionId) currentFolderLive.setValue(folderId);
    }

    public void setSessionPinned(long sid, boolean pinned) {
        repo.setSessionPinned(sid, pinned);
    }

    // ------------------------------------------------------------ editing

    public void startEdit(MessageEntity m) { editing.setValue(m); }

    public void cancelEdit() { editing.setValue(null); }

    public List<Attachment> parseAttachments(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<Attachment>>() {}.getType();
            List<Attachment> l = gson.fromJson(json, t);
            return l == null ? new ArrayList<>() : l;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean isLastMessage(MessageEntity m) {
        List<MessageEntity> l = cur();
        return !l.isEmpty() && l.get(l.size() - 1).id == m.id;
    }

    // ------------------------------------------------------------ sending

    public void send(String rawText, List<Attachment> atts) {
        final String text = rawText == null ? "" : rawText.trim();
        final List<Attachment> attachments = atts == null ? new ArrayList<>() : new ArrayList<>(atts);
        if (text.isEmpty() && attachments.isEmpty()) return;
        if (Boolean.TRUE.equals(streaming.getValue())) return;
        if (!hasModel()) {
            toast.setValue(getApplication().getString(com.sentia.ai.app.R.string.need_provider));
            return;
        }
        final MessageEntity ed = editing.getValue();
        editing.setValue(null);
        final long editFrom = ed == null ? 0 : ed.id;
        final long sidIn = sessionId;
        final long folderIn = pendingFolderId;
        pendingFolderId = 0;
        final Provider p = selectedProvider();
        final String modelId = settings.selectedModelId();
        streaming.setValue(true);

        io.execute(() -> {
            try {
                long sid = sidIn;
                long now = System.currentTimeMillis();
                if (sid == 0) {
                    SessionEntity s = new SessionEntity();
                    s.title = makeTitle(text, attachments);
                    s.createdAt = now;
                    s.updatedAt = now;
                    s.providerId = p.id;
                    s.modelId = modelId;
                    s.folderId = folderIn;
                    sid = db.sessionDao().insert(s);
                }
                if (editFrom != 0) db.messageDao().deleteFrom(sid, editFrom);
                MessageEntity u = new MessageEntity();
                u.sessionId = sid;
                u.role = "user";
                u.content = text;
                u.attachmentsJson = attachments.isEmpty() ? null : gson.toJson(attachments);
                u.createdAt = now;
                u.status = MessageEntity.DONE;
                u.id = db.messageDao().insert(u);
                db.sessionDao().touch(sid, now);
                beginAssistant(sid, p, modelId);
            } catch (Exception e) {
                postFail(e);
            }
        });
    }

    public void regenerate(MessageEntity m) {
        if (Boolean.TRUE.equals(streaming.getValue())) return;
        if (!hasModel()) {
            toast.setValue(getApplication().getString(com.sentia.ai.app.R.string.need_provider));
            return;
        }
        final long sid = sessionId;
        if (sid == 0) return;
        final long cutFrom = "user".equals(m.role) ? m.id + 1 : m.id;
        List<MessageEntity> trimmed = new ArrayList<>();
        for (MessageEntity x : cur()) if (x.id < cutFrom) trimmed.add(x);
        setList(trimmed);
        streaming.setValue(true);
        final Provider p = selectedProvider();
        final String modelId = settings.selectedModelId();
        io.execute(() -> {
            try {
                db.messageDao().deleteFrom(sid, cutFrom);
                beginAssistant(sid, p, modelId);
            } catch (Exception e) {
                postFail(e);
            }
        });
    }

    private void postFail(Exception e) {
        main.post(() -> {
            streaming.setValue(false);
            toast.setValue("Error: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
        });
    }

    /** Runs on the io thread. Builds the request, inserts the empty reply, then starts streaming on main. */
    private void beginAssistant(final long sid, final Provider p, final String modelId) {
        List<MessageEntity> history = db.messageDao().list(sid);
        String extra = "";
        long folderOfChat = 0;
        SessionEntity sess = db.sessionDao().get(sid);
        if (sess != null && sess.folderId != 0) {
            FolderEntity folder = db.folderDao().get(sess.folderId);
            if (folder != null) {
                folderOfChat = folder.id;
                List<ProjectFileEntity> files = db.projectFileDao().list(folder.id);
                extra = ProjectContext.build(folder, files, lastUserText(history));
            }
        }
        final long folderForStream = folderOfChat;
        final ChatRequest req = buildRequest(history, p, modelId, extra);

        MessageEntity a = new MessageEntity();
        a.sessionId = sid;
        a.role = "assistant";
        a.content = "";
        a.createdAt = System.currentTimeMillis();
        a.status = MessageEntity.STREAMING;
        a.modelLabel = p.name + " \u00b7 " + modelId;
        a.id = db.messageDao().insert(a);

        final List<MessageEntity> all = new ArrayList<>(history);
        all.add(a);
        final SessionEntity s = db.sessionDao().get(sid);
        final long msgId = a.id;
        main.post(() -> {
            sessionId = sid;
            streamFolderId = folderForStream;
            currentFolderLive.setValue(s == null ? 0L : s.folderId);
            currentSessionLive.setValue(sid);
            if (s != null) title.setValue(s.title == null ? "" : s.title);
            setList(all);
            triedKeys.clear();
            triedKeys.add(req.keyIndex);
            startStream(req, msgId);
        });
    }

    /** The last two user messages joined, used to pick the matching project file excerpts. */
    private static String lastUserText(List<MessageEntity> history) {
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (int i = history.size() - 1; i >= 0 && found < 2; i--) {
            MessageEntity m = history.get(i);
            if ("user".equals(m.role) && m.content != null) {
                sb.append(m.content).append('\n');
                found++;
            }
        }
        return sb.toString();
    }

    private ChatRequest buildRequest(List<MessageEntity> history, Provider p, String modelId, String extra) {
        ChatRequest r = new ChatRequest();
        r.provider = p;
        r.modelId = modelId;
        r.systemPrompt = composeSystemPrompt(extra);
        int ki = KeyRotator.pick(p);
        r.keyIndex = Math.max(ki, 0);
        r.apiKey = ki >= 0 ? KeyRotator.keyAt(p, ki) : "";
        r.temperature = settings.temperatureEnabled() ? Float.valueOf(settings.temperature()) : null;
        r.maxTokens = settings.maxTokens();

        List<ChatMsg> out = new ArrayList<>();
        int budget = settings.contextChars();
        int chars = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            MessageEntity m = history.get(i);
            if (!"user".equals(m.role) && !"assistant".equals(m.role)) continue;
            boolean noText = m.content == null || m.content.isEmpty();
            if (noText && m.attachmentsJson == null) continue;
            ChatMsg c = toChatMsg(m);
            chars += c.text.length();
            out.add(0, c);
            if (chars > budget && out.size() > 1) break;
        }
        while (!out.isEmpty() && !"user".equals(out.get(0).role)) out.remove(0);
        r.messages = out;
        lastRequestChars = chars + (r.systemPrompt == null ? 0 : r.systemPrompt.length());
        return r;
    }

    private String composeSystemPrompt(String extra) {
        StringBuilder sb = new StringBuilder(settings.systemPrompt());
        String lang = settings.replyLanguage();
        if ("en".equals(lang)) sb.append("\n\nAlways reply in English.");
        else if ("bn".equals(lang)) sb.append("\n\nAlways reply in Bangla (Bengali).");
        String profile = ProfileStore.get(getApplication()).promptBlock();
        if (!profile.isEmpty()) sb.append("\n\n").append(profile);
        if (extra != null && !extra.isEmpty()) sb.append("\n\n").append(extra);
        if (settings.includeDate()) {
            sb.append("\n\nCurrent date and time: ")
                    .append(new SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm z", Locale.ENGLISH).format(new Date()));
        }
        return sb.toString();
    }

    private ChatMsg toChatMsg(MessageEntity m) {
        ChatMsg c = new ChatMsg();
        c.role = m.role;
        StringBuilder text = new StringBuilder(m.content == null ? "" : m.content);
        if ("user".equals(m.role)) {
            for (Attachment a : parseAttachments(m.attachmentsJson)) {
                if (Attachment.IMAGE.equals(a.kind)) {
                    String b64 = ImageUtil.toBase64Jpeg(a.path, 1280);
                    if (b64 != null) {
                        ChatMsg.ImagePart ip = new ChatMsg.ImagePart();
                        ip.mime = "image/jpeg";
                        ip.b64 = b64;
                        c.images.add(ip);
                    }
                } else {
                    String t = AttachmentUtil.readText(a.path);
                    text.append("\n\n[Attached file: ").append(a.name).append("]\n");
                    if (t == null) text.append("(binary file, content not shown)");
                    else text.append("```\n").append(t).append("\n```");
                }
            }
            if (text.toString().trim().isEmpty() && !c.images.isEmpty()) text.append("(see attached image)");
        }
        c.text = text.toString();
        return c;
    }

    private String makeTitle(String text, List<Attachment> atts) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty() && atts != null && !atts.isEmpty()) t = atts.get(0).name;
        if (t.isEmpty()) return "New chat";
        t = t.split("\n")[0].trim();
        return t.length() > 40 ? t.substring(0, 40).trim() + "..." : t;
    }

    // ------------------------------------------------------------ streaming (main thread)

    private void startStream(ChatRequest req, long msgId) {
        final int token = ++streamToken;
        streamMsgId = msgId;
        sb.setLength(0);
        gotUsage = false;
        usageIn = 0;
        usageOut = 0;
        streaming.setValue(true);

        LlmClient client = ClientFactory.forProvider(req.provider);
        call = client.stream(req, new StreamListener() {
            @Override
            public void onDelta(final String t) {
                main.post(() -> {
                    if (token != streamToken) return;
                    sb.append(t);
                    scheduleFlush();
                });
            }

            @Override
            public void onUsage(final int in, final int out) {
                main.post(() -> {
                    if (token != streamToken) return;
                    gotUsage = true;
                    usageIn = in;
                    usageOut = out;
                });
            }

            @Override
            public void onDone() {
                main.post(() -> {
                    if (token != streamToken) return;
                    finishStream(MessageEntity.DONE, null);
                });
            }

            @Override
            public void onHttpError(final int code, final String message) {
                main.post(() -> {
                    if (token != streamToken) return;
                    boolean keyProblem = code == 401 || code == 402 || code == 403 || code == 429;
                    if (sb.length() == 0 && keyProblem && rotateKey(req, msgId, code)) return;
                    finishStream(MessageEntity.STOPPED, message == null ? "Unknown error" : message);
                });
            }

            @Override
            public void onError(final String message) {
                main.post(() -> {
                    if (token != streamToken) return;
                    finishStream(MessageEntity.STOPPED, message == null ? "Unknown error" : message);
                });
            }
        });
    }

    /** Tries the provider's next API key. Returns true if a new attempt was started. */
    private boolean rotateKey(ChatRequest req, long msgId, int code) {
        Provider p = req.provider;
        if (p.apiKeys == null || p.apiKeys.size() < 2) return false;
        KeyRotator.block(providers, p, req.keyIndex, code == 429 ? 60000L : 6L * 3600000L);
        int next = KeyRotator.next(p, triedKeys);
        if (next < 0) return false;
        triedKeys.add(next);
        KeyRotator.setActive(providers, p, next);
        req.keyIndex = next;
        req.apiKey = KeyRotator.keyAt(p, next);
        toast.setValue(getApplication().getString(com.sentia.ai.app.R.string.key_switched, next + 1));
        if (call != null) {
            try {
                call.cancel();
            } catch (Exception ignored) {
            }
        }
        streamToken++;
        startStream(req, msgId);
        return true;
    }

    /** If the open chat was deleted elsewhere (for example in Settings), start a fresh chat. */
    public void validateCurrent() {
        final long sid = sessionId;
        if (sid == 0) return;
        io.execute(() -> {
            final SessionEntity cur = db.sessionDao().get(sid);
            if (cur == null) {
                main.post(() -> {
                    if (sessionId == sid) newChat();
                });
            } else {
                final long fid = cur.folderId;
                main.post(() -> {
                    if (sessionId == sid) currentFolderLive.setValue(fid);
                });
            }
        });
    }

    private void scheduleFlush() {
        if (flushPending) return;
        flushPending = true;
        main.postDelayed(() -> {
            flushPending = false;
            pushStreamingContent();
        }, 50);
    }

    private void pushStreamingContent() {
        if (!Boolean.TRUE.equals(streaming.getValue())) return;
        List<MessageEntity> l = new ArrayList<>(cur());
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).id == streamMsgId) {
                MessageEntity c = l.get(i).copy();
                c.content = streamFolderId != 0 ? MemoryTags.strip(sb.toString()) : sb.toString();
                l.set(i, c);
                setList(l);
                return;
            }
        }
    }

    public void stop() {
        if (!Boolean.TRUE.equals(streaming.getValue())) return;
        if (call != null) {
            try {
                call.cancel();
            } catch (Exception ignored) {
            }
        }
        finishStream(MessageEntity.STOPPED, null);
    }

    private void finishStream(int status, String error) {
        call = null;
        streamToken++;
        final String raw = sb.toString();
        final String content = streamFolderId != 0 ? MemoryTags.strip(raw) : raw;
        if (streamFolderId != 0 && error == null) {
            List<String> notes = MemoryTags.extract(raw);
            if (!notes.isEmpty()) {
                repo.appendMemory(streamFolderId, notes);
                toast.setValue(getApplication().getString(com.sentia.ai.app.R.string.memory_saved));
            }
        }
        final boolean isError = error != null;
        List<MessageEntity> l = new ArrayList<>(cur());
        int idx = -1;
        for (int i = l.size() - 1; i >= 0; i--) {
            if (l.get(i).id == streamMsgId) {
                idx = i;
                break;
            }
        }
        streaming.setValue(false);
        if (idx < 0) return;

        final MessageEntity c = l.get(idx).copy();
        if (content.isEmpty() && !isError && status == MessageEntity.STOPPED) {
            // Stopped before any text arrived: drop the empty reply.
            l.remove(idx);
            setList(l);
            final long delId = c.id;
            io.execute(() -> db.messageDao().delete(delId));
            return;
        }
        if (isError && content.isEmpty()) {
            c.role = "error";
            c.content = error;
            c.status = MessageEntity.DONE;
        } else {
            c.content = content;
            c.status = isError ? MessageEntity.STOPPED : status;
            if (isError) toast.setValue(error);
            if (gotUsage && usageOut > 0) {
                c.tokensIn = usageIn;
                c.tokensOut = usageOut;
            } else {
                c.tokensIn = lastRequestChars / 4;
                c.tokensOut = TokenEstimator.estimate(content);
            }
        }
        l.set(idx, c);
        setList(l);
        final long now = System.currentTimeMillis();
        io.execute(() -> {
            db.messageDao().update(c);
            db.sessionDao().touch(c.sessionId, now);
        });
    }

    @Override
    protected void onCleared() {
        if (call != null) {
            try {
                call.cancel();
            } catch (Exception ignored) {
            }
        }
        io.shutdown();
        super.onCleared();
    }
}
