package com.sentia.ai.app.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProviderStore;
import com.sentia.ai.app.data.PromptStore;
import com.sentia.ai.app.data.SettingsStore;
import com.sentia.ai.app.data.ProfileStore;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.data.db.SessionEntity;
import com.sentia.ai.app.model.Attachment;
import com.sentia.ai.app.model.Provider;
import com.sentia.ai.app.model.SavedPrompt;
import com.sentia.ai.app.model.ProjectCommand;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.util.ProjectIcons;
import com.sentia.ai.app.util.Slash;
import com.sentia.ai.app.util.AttachmentUtil;
import com.sentia.ai.app.util.ChatExporter;
import com.sentia.ai.app.util.TokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int MAX_ATTACHMENTS = 8;
    public static final String EXTRA_OPEN_SESSION = "open_session";
    public static final String EXTRA_NEW_CHAT_IN = "new_chat_in";

    private ChatViewModel vm;
    private SettingsStore settings;
    private ProviderStore providers;

    private DrawerLayout drawer;
    private ExplorerController explorer;
    private WorkspaceActions wsActions;
    private android.widget.FrameLayout explorerDrawer;
    private android.view.View btnFiles;
    private long explorerProject;
    private RecyclerView rvMessages, rvSessions, rvAtt;
    private EditText etInput, etSearch;
    private TextView tvTitle, tvModel, tvHint;
    private View emptyState, editBanner, btnScrollDown;
    private ImageButton btnSend;

    private ChatAdapter chatAdapter;
    private DrawerAdapter drawerAdapter;
    private List<FolderEntity> lastFolders = new ArrayList<>();
    private List<SessionEntity> lastSessions = new ArrayList<>();
    private String searchText = "";
    private long exportSessionId;
    private TextView tvGreeting;
    private RecyclerView rvSlash;
    private SlashAdapter slashAdapter;
    private ActivityResultLauncher<String> exportSessionMd, exportSessionJson;
    private AttachmentAdapter attAdapter;
    private LinearLayoutManager lm;

    private final List<Attachment> pending = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private boolean autoScroll = true;
    private boolean isStreaming = false;

    private ActivityResultLauncher<String> pickImages;
    private ActivityResultLauncher<String[]> pickFiles;
    private ActivityResultLauncher<Intent> speech;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = SettingsStore.get(this);
        providers = ProviderStore.get(this);
        vm = new ViewModelProvider(this).get(ChatViewModel.class);

        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat wc = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        wc.setAppearanceLightStatusBars(!night);
        wc.setAppearanceLightNavigationBars(!night);

        bindViews();
        registerLaunchers();
        registerExportLaunchers();
        setupExplorer();
        setupMessages();
        setupDrawer();
        setupInput();
        observe();
        setupBack();

        etInput.setText(settings.draft());
        etInput.setSelection(etInput.getText().length());
        updateModelPill();
        updateGreeting();
        updateSendState();
        handleShareIntent(getIntent());
        handleProjectIntent(getIntent());
    }

    private void handleProjectIntent(Intent intent) {
        if (intent == null) return;
        long openId = intent.getLongExtra(EXTRA_OPEN_SESSION, 0);
        long newIn = intent.getLongExtra(EXTRA_NEW_CHAT_IN, 0);
        if (openId != 0) {
            vm.openSession(openId);
        } else if (newIn != 0) {
            pending.clear();
            refreshAttachments();
            vm.newChatInFolderId(newIn);
        } else {
            return;
        }
        intent.removeExtra(EXTRA_OPEN_SESSION);
        intent.removeExtra(EXTRA_NEW_CHAT_IN);
        drawer.closeDrawer(GravityCompat.START);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
        handleProjectIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        vm.ensureSelection();
        vm.validateCurrent();
        updateModelPill();
        updateGreeting();
        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
        if (explorer != null && drawer.isDrawerOpen(GravityCompat.END)) explorer.onResume();
    }

    @Override
    protected void onDestroy() {
        if (explorer != null) explorer.release();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        settings.setDraft(etInput.getText().toString());
        super.onPause();
    }

    // ------------------------------------------------------------------ setup

    private void bindViews() {
        drawer = findViewById(R.id.drawerLayout);
        rvMessages = findViewById(R.id.rvMessages);
        rvSessions = findViewById(R.id.rvSessions);
        rvAtt = findViewById(R.id.rvAttachments);
        etInput = findViewById(R.id.etInput);
        etSearch = findViewById(R.id.etSearch);
        tvTitle = findViewById(R.id.tvTitle);
        tvModel = findViewById(R.id.tvModel);
        tvHint = findViewById(R.id.tvHint);
        emptyState = findViewById(R.id.emptyState);
        editBanner = findViewById(R.id.editBanner);
        btnScrollDown = findViewById(R.id.btnScrollDown);
        btnSend = findViewById(R.id.btnSend);
        tvGreeting = findViewById(R.id.tvGreeting);
        rvSlash = findViewById(R.id.rvSlash);
    }

    private void registerLaunchers() {
        pickImages = registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
            if (uris != null) addUris(uris);
        });
        pickFiles = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris != null) addUris(new ArrayList<>(uris));
        });
        speech = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (data == null) return;
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r == null || r.isEmpty()) return;
            String cur = etInput.getText().toString();
            String add = r.get(0);
            etInput.setText(cur.isEmpty() ? add : cur + (cur.endsWith(" ") ? "" : " ") + add);
            etInput.setSelection(etInput.getText().length());
        });
    }

    private void registerExportLaunchers() {
        exportSessionMd = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/markdown"), uri -> {
            if (uri != null) runSessionExport(uri, true);
        });
        exportSessionJson = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) runSessionExport(uri, false);
        });
    }

    private void setupMessages() {
        lm = new LinearLayoutManager(this);
        rvMessages.setLayoutManager(lm);
        rvMessages.setItemAnimator(null);
        chatAdapter = new ChatAdapter(this, new ChatAdapter.Listener() {
            @Override
            public void onActions(MessageEntity m) {
                showActions(m);
            }

            @Override
            public void onCopy(MessageEntity m) {
                copyText(m.content);
            }

            @Override
            public void onRegenerate(MessageEntity m) {
                vm.regenerate(m);
            }

            @Override
            public void onRetry(MessageEntity m) {
                vm.regenerate(m);
            }
        });
        rvMessages.setAdapter(chatAdapter);
        rvMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                boolean atBottom = !rv.canScrollVertically(1);
                if (dy < 0 && !atBottom) autoScroll = false;
                if (atBottom) autoScroll = true;
                btnScrollDown.setVisibility(atBottom ? View.GONE : View.VISIBLE);
            }
        });
        rvMessages.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b < ob && autoScroll) scrollToEnd();
        });
        btnScrollDown.setOnClickListener(v -> {
            autoScroll = true;
            scrollToEnd();
        });
    }

    private void setupDrawer() {
        findViewById(R.id.btnMenu).setOnClickListener(v -> {
            hideKeyboard();
            drawer.openDrawer(GravityCompat.START);
        });
        findViewById(R.id.btnNewChat).setOnClickListener(v -> startNewChat());
        findViewById(R.id.rowProjects).setOnClickListener(v -> {
            startActivity(new Intent(this, ProjectsActivity.class));
            drawer.closeDrawer(GravityCompat.START);
        });
        findViewById(R.id.rowNewChat).setOnClickListener(v -> {
            startNewChat();
            drawer.closeDrawer(GravityCompat.START);
        });

        drawerAdapter = new DrawerAdapter(this, new DrawerAdapter.Listener() {
            @Override
            public void onOpenSession(SessionEntity se) {
                vm.openSession(se.id);
                drawer.closeDrawer(GravityCompat.START);
            }

            @Override
            public void onSessionMenu(View anchor, SessionEntity se) {
                showSessionMenu(anchor, se);
            }

            @Override
            public void onOpenProject(FolderEntity f) {
                Intent i = new Intent(MainActivity.this, ProjectActivity.class);
                i.putExtra(ProjectActivity.EXTRA_ID, f.id);
                startActivity(i);
                drawer.closeDrawer(GravityCompat.START);
            }

            @Override
            public void onProjectMenu(View anchor, FolderEntity f) {
                showProjectMenu(anchor, f);
            }
        });
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(drawerAdapter);

        etSearch.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                searchText = s.toString();
                vm.setSearch(searchText);
                rebuildDrawer();
            }
        });

        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.btnTokens).setOnClickListener(v -> {
            Intent i = new Intent(this, TokenActivity.class);
            i.putExtra("sessionId", vm.currentSessionId());
            startActivity(i);
        });
    }

    private void setupInput() {
        attAdapter = new AttachmentAdapter(pending, index -> {
            if (index >= 0 && index < pending.size()) {
                pending.remove(index);
                refreshAttachments();
            }
        });
        rvAtt.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvAtt.setAdapter(attAdapter);

        slashAdapter = new SlashAdapter(item -> {
            etInput.setText("/" + item.name + " ");
            etInput.setSelection(etInput.getText().length());
        });
        rvSlash.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvSlash.setAdapter(slashAdapter);

        etInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateSendState();
                updateHint();
                updateSlash();
            }
        });
        etInput.setOnKeyListener((v, keyCode, event) -> {
            if (settings.enterToSend() && keyCode == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN && !event.isShiftPressed()) {
                btnSend.performClick();
                return true;
            }
            return false;
        });

        btnSend.setOnClickListener(v -> onSendClicked());
        findViewById(R.id.btnAttach).setOnClickListener(v -> showAttachMenu());
        findViewById(R.id.tvModel).setOnClickListener(v -> showModelPicker());
        findViewById(R.id.btnCancelEdit).setOnClickListener(v -> {
            vm.cancelEdit();
            etInput.setText("");
            pending.clear();
            refreshAttachments();
        });

        ImageButton mic = findViewById(R.id.btnMic);
        mic.setOnClickListener(v -> startVoice());
        mic.setOnLongClickListener(v -> {
            boolean bn = "bn-BD".equals(settings.voiceLang());
            settings.setVoiceLang(bn ? "en-US" : "bn-BD");
            Toast.makeText(this, bn ? R.string.voice_lang_en : R.string.voice_lang_bn, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void observe() {
        vm.messages().observe(this, list -> {
            chatAdapter.submit(list, () -> {
                if (autoScroll && (settings.autoScrollStream() || !isStreaming)) scrollToEnd();
            });
            boolean empty = list.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            rvMessages.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        });
        vm.streaming().observe(this, s -> {
            isStreaming = Boolean.TRUE.equals(s);
            updateSendState();
        });
        vm.title().observe(this, t -> tvTitle.setText(t == null || t.isEmpty() ? getString(R.string.app_name) : t));
        vm.sessions().observe(this, l -> {
            lastSessions = l == null ? new ArrayList<>() : l;
            rebuildDrawer();
        });
        vm.folders().observe(this, l -> {
            lastFolders = l == null ? new ArrayList<>() : l;
            rebuildDrawer();
            updateProjectTag();
            updateSlash();
            updateFilesButton();
        });
        vm.currentSession().observe(this, id -> drawerAdapter.setCurrent(id == null ? 0 : id));
        vm.currentFolder().observe(this, id -> {
            updateProjectTag();
            updateSlash();
            updateFilesButton();
        });
        vm.editing().observe(this, m -> editBanner.setVisibility(m == null ? View.GONE : View.VISIBLE));
        vm.toast().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                vm.clearToast();
            }
        });
    }

    private void setupBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.END)) {
                    if (explorer == null || !explorer.handleBack()) drawer.closeDrawer(GravityCompat.END);
                } else if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else if (vm.editing().getValue() != null) {
                    vm.cancelEdit();
                    etInput.setText("");
                    pending.clear();
                    refreshAttachments();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    // ------------------------------------------------------------------ actions

    private void onSendClicked() {
        if (isStreaming) {
            vm.stop();
            return;
        }
        String text = etInput.getText().toString();
        if (text.trim().isEmpty() && pending.isEmpty()) return;
        if (!vm.hasModel()) {
            Toast.makeText(this, R.string.need_provider, Toast.LENGTH_SHORT).show();
            if (providers.all().isEmpty()) {
                ProviderSheet.show(this, null, this::updateModelPill);
            } else {
                showModelPicker();
            }
            return;
        }
        if (settings.haptics()) btnSend.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        FolderEntity cf = currentFolderEntity();
        if (cf != null) {
            text = Slash.expand(text, Slash.available(this, ProjectRepo.parseCommands(cf.commands)));
        }
        vm.send(text, new ArrayList<>(pending));
        etInput.setText("");
        settings.setDraft("");
        pending.clear();
        refreshAttachments();
        autoScroll = true;
        rvMessages.postDelayed(this::scrollToEnd, 200);
    }

    private void startNewChat() {
        vm.newChat();
        etInput.setText("");
        pending.clear();
        refreshAttachments();
        autoScroll = true;
    }

    private void showActions(MessageEntity m) {
        final boolean user = "user".equals(m.role);
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row(R.drawable.ic_copy, getString(R.string.act_copy), () -> copyText(m.content)));
        boolean canRegen = !isStreaming && (user || vm.isLastMessage(m));
        if (canRegen) {
            rows.add(new ActionSheet.Row(R.drawable.ic_refresh, getString(R.string.act_regenerate), () -> vm.regenerate(m)));
        }
        if (user && !isStreaming) {
            rows.add(new ActionSheet.Row(R.drawable.ic_edit, getString(R.string.act_edit), () -> startEdit(m)));
        }
        rows.add(new ActionSheet.Row(R.drawable.ic_share, getString(R.string.act_share), () -> shareText(m.content)));
        rows.add(new ActionSheet.Row(R.drawable.ic_text_select, getString(R.string.act_select), () -> {
            Intent i = new Intent(this, TextSelectActivity.class);
            i.putExtra("text", m.content);
            startActivity(i);
        }));
        if (user) {
            rows.add(new ActionSheet.Row(R.drawable.ic_bookmark, getString(R.string.act_save_prompt), () -> {
                PromptStore.get(this).add(m.content);
                Toast.makeText(this, R.string.saved_prompt, Toast.LENGTH_SHORT).show();
            }));
        }
        ActionSheet.show(this, null, rows);
    }

    private void startEdit(MessageEntity m) {
        vm.startEdit(m);
        etInput.setText(m.content);
        etInput.setSelection(etInput.getText().length());
        pending.clear();
        pending.addAll(vm.parseAttachments(m.attachmentsJson));
        refreshAttachments();
        etInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void showAttachMenu() {
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row(R.drawable.ic_image, getString(R.string.act_photos), () -> {
            if (pending.size() >= MAX_ATTACHMENTS) {
                Toast.makeText(this, R.string.attach_limit, Toast.LENGTH_SHORT).show();
                return;
            }
            pickImages.launch("image/*");
        }));
        rows.add(new ActionSheet.Row(R.drawable.ic_file, getString(R.string.act_files), () -> {
            if (pending.size() >= MAX_ATTACHMENTS) {
                Toast.makeText(this, R.string.attach_limit, Toast.LENGTH_SHORT).show();
                return;
            }
            pickFiles.launch(new String[]{"*/*"});
        }));
        rows.add(new ActionSheet.Row(R.drawable.ic_bookmark, getString(R.string.act_prompts), this::showPrompts));
        ActionSheet.show(this, getString(R.string.attach_title), rows);
    }

    private void showPrompts() {
        final PromptStore store = PromptStore.get(this);
        List<SavedPrompt> all = store.all();
        if (all.isEmpty()) {
            Toast.makeText(this, R.string.prompts_empty, Toast.LENGTH_LONG).show();
            return;
        }
        List<ActionSheet.Row> rows = new ArrayList<>();
        for (SavedPrompt p : all) {
            final SavedPrompt sp = p;
            rows.add(new ActionSheet.Row(R.drawable.ic_bookmark, p.title, () -> {
                String cur = etInput.getText().toString();
                etInput.setText(cur.isEmpty() ? sp.text : cur + "\n" + sp.text);
                etInput.setSelection(etInput.getText().length());
                etInput.requestFocus();
            }, () -> new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.delete)
                    .setMessage(sp.title)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete, (d, w) -> store.delete(sp.id))
                    .show()));
        }
        ActionSheet.show(this, getString(R.string.prompts_title), rows);
    }

    private void showModelPicker() {
        hideKeyboard();
        ModelPickerSheet.show(this, new ModelPickerSheet.Callbacks() {
            @Override
            public void onSelected() {
                updateModelPill();
            }

            @Override
            public void onAddProvider() {
                ProviderSheet.show(MainActivity.this, null, MainActivity.this::updateModelPill);
            }

            @Override
            public void onEditProvider(Provider p) {
                ProviderSheet.show(MainActivity.this, p, MainActivity.this::updateModelPill);
            }
        });
    }

    private void rebuildDrawer() {
        if (drawerAdapter != null) drawerAdapter.submit(lastFolders, lastSessions, searchText);
    }

    private void updateGreeting() {
        String name = ProfileStore.get(this).displayName();
        tvGreeting.setText(name.isEmpty() ? getString(R.string.greeting) : getString(R.string.greeting_named, name));
    }

    private static String safeName(String raw, String fallback) {
        String n = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        if (n.length() > 40) n = n.substring(0, 40);
        return n.isEmpty() ? fallback : n;
    }

    private void showSessionMenu(View anchor, final SessionEntity s) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 5, 0, s.pinned ? R.string.act_unpin : R.string.act_pin);
        pm.getMenu().add(0, 1, 1, R.string.rename);
        pm.getMenu().add(0, 2, 2, R.string.act_move_project);
        pm.getMenu().add(0, 3, 3, R.string.act_export);
        pm.getMenu().add(0, 4, 4, R.string.delete);
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 5) vm.setSessionPinned(s.id, !s.pinned);
            else if (id == 1) showRename(s);
            else if (id == 2) chooseFolderFor(s);
            else if (id == 3) exportChatSheet(s);
            else if (id == 4) confirmDelete(s);
            return true;
        });
        pm.show();
    }

    private void chooseFolderFor(final SessionEntity s) {
        List<ActionSheet.Row> rows = new ArrayList<>();
        for (final FolderEntity f : lastFolders) {
            if (f.id == s.folderId) continue;
            rows.add(new ActionSheet.Row(ProjectIcons.res(f.icon), f.name == null ? "" : f.name,
                    () -> vm.moveSession(s.id, f.id)));
        }
        if (s.folderId != 0) {
            rows.add(new ActionSheet.Row(R.drawable.ic_close, getString(R.string.no_project), () -> vm.moveSession(s.id, 0)));
        }
        rows.add(new ActionSheet.Row(R.drawable.ic_plus, getString(R.string.new_project), () -> promptNewProjectAndMove(s)));
        ActionSheet.show(this, getString(R.string.move_title), rows);
    }

    private void exportChatSheet(final SessionEntity s) {
        List<ActionSheet.Row> rows = new ArrayList<>();
        rows.add(new ActionSheet.Row(R.drawable.ic_share, getString(R.string.share_text), () -> io.execute(() -> {
            final String md = ChatExporter.sessionMarkdown(getApplicationContext(), s.id);
            runOnUiThread(() -> shareText(md));
        })));
        rows.add(new ActionSheet.Row(R.drawable.ic_file, getString(R.string.save_md), () -> {
            exportSessionId = s.id;
            exportSessionMd.launch(safeName(s.title, "sentia-chat") + ".md");
        }));
        rows.add(new ActionSheet.Row(R.drawable.ic_file, getString(R.string.save_json), () -> {
            exportSessionId = s.id;
            exportSessionJson.launch(safeName(s.title, "sentia-chat") + ".json");
        }));
        ActionSheet.show(this, getString(R.string.export_chat_title), rows);
    }

    private void runSessionExport(final android.net.Uri uri, final boolean md) {
        final long sid = exportSessionId;
        io.execute(() -> {
            final boolean ok = ChatExporter.exportSession(getApplicationContext(), uri, sid, md);
            runOnUiThread(() -> Toast.makeText(this, ok ? R.string.export_done : R.string.export_failed, Toast.LENGTH_SHORT).show());
        });
    }

    private void showProjectMenu(View anchor, final FolderEntity f) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, R.string.act_new_chat_here);
        pm.getMenu().add(0, 2, 1, R.string.act_unpin_project);
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                pending.clear();
                refreshAttachments();
                vm.newChatInFolder(f);
                drawer.closeDrawer(GravityCompat.START);
            } else if (id == 2) {
                ProjectRepo.get(this).setPinned(f.id, false);
            }
            return true;
        });
        pm.show();
    }

    private void promptNewProjectAndMove(final SessionEntity s) {
        ProjectDialog.show(this, null, (name, desc, icon) -> vm.createFolderAndMove(name, desc, icon, s.id));
    }

    private FolderEntity currentFolderEntity() {
        long id = vm.currentFolderId();
        if (id == 0) return null;
        for (FolderEntity f : lastFolders) if (f.id == id) return f;
        return null;
    }

    /** Right drawer with the files of the current project (Phase 2A). */
    private void setupExplorer() {
        explorerDrawer = findViewById(R.id.explorerDrawer);
        btnFiles = findViewById(R.id.btnFiles);
        wsActions = new WorkspaceActions(this, 0, "", new WorkspaceActions.Listener() {
            @Override
            public void onWorkspaceChanged() {
                if (explorer != null) explorer.refresh();
            }
        });
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                if (drawerView == explorerDrawer && explorer != null) explorer.onResume();
            }
        });
        btnFiles.setOnClickListener(v -> {
            FolderEntity f = currentFolderEntity();
            if (f == null) return;
            ensureExplorer(f);
            drawer.openDrawer(GravityCompat.END);
        });
    }

    private void ensureExplorer(FolderEntity f) {
        if (explorer == null) {
            explorer = new ExplorerController(this, explorerDrawer, f.id, f.name, wsActions,
                    new ExplorerController.Host() {
                        @Override
                        public void onOpenFile(String rel) {
                            Intent i = new Intent(MainActivity.this, EditorActivity.class);
                            i.putExtra(EditorActivity.EXTRA_PROJECT, explorerProject);
                            i.putExtra(EditorActivity.EXTRA_PATH, rel);
                            startActivity(i);
                        }
                    });
            explorerProject = f.id;
        } else if (explorerProject != f.id) {
            explorer.setProject(f.id, f.name);
            explorerProject = f.id;
        }
    }

    /** The Files button and right drawer exist only while a project chat with a workspace is open. */
    private void updateFilesButton() {
        if (btnFiles == null) return;
        FolderEntity f = currentFolderEntity();
        boolean show = f != null && f.workspaceLinked;
        btnFiles.setVisibility(show ? View.VISIBLE : View.GONE);
        drawer.setDrawerLockMode(show ? DrawerLayout.LOCK_MODE_UNLOCKED : DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                GravityCompat.END);
        int margin = Math.round((show ? 100 : 64) * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) tvTitle.getLayoutParams();
        if (lp.getMarginStart() != margin || lp.getMarginEnd() != margin) {
            lp.setMarginStart(margin);
            lp.setMarginEnd(margin);
            tvTitle.setLayoutParams(lp);
        }
        if (!show) {
            if (drawer.isDrawerOpen(GravityCompat.END)) drawer.closeDrawer(GravityCompat.END);
        } else if (explorer != null && explorerProject != f.id) {
            ensureExplorer(f);
        } else if (explorer != null) {
            // the project name may have been renamed
            explorerProject = f.id;
        }
    }

    /** Shows the project icon in front of the chat title while a project chat is open. */
    private void updateProjectTag() {
        FolderEntity f = currentFolderEntity();
        if (f == null) {
            tvTitle.setCompoundDrawablesRelative(null, null, null, null);
            return;
        }
        Drawable d = ContextCompat.getDrawable(this, ProjectIcons.res(f.icon));
        if (d == null) return;
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, ContextCompat.getColor(this, R.color.ink_muted));
        int px = Math.round(16 * getResources().getDisplayMetrics().density);
        d.setBounds(0, 0, px, px);
        tvTitle.setCompoundDrawablesRelative(d, null, null, null);
        tvTitle.setCompoundDrawablePadding(Math.round(6 * getResources().getDisplayMetrics().density));
    }

    /** Suggestion chips while the user types a slash command inside a project chat. */
    private void updateSlash() {
        if (rvSlash == null || slashAdapter == null) return;
        FolderEntity f = currentFolderEntity();
        String t = etInput.getText().toString();
        if (f == null || !t.startsWith("/") || t.contains(" ") || t.contains("\n")) {
            rvSlash.setVisibility(View.GONE);
            return;
        }
        String q = t.substring(1).toLowerCase(java.util.Locale.ROOT);
        List<Slash.Item> match = new ArrayList<>();
        for (Slash.Item it : Slash.available(this, ProjectRepo.parseCommands(f.commands))) {
            if (it.name.startsWith(q)) match.add(it);
        }
        slashAdapter.submit(match);
        rvSlash.setVisibility(match.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showRename(SessionEntity s) {
        View v = getLayoutInflater().inflate(R.layout.dialog_rename, null);
        final EditText et = v.findViewById(R.id.etRename);
        et.setText(s.title);
        et.setSelection(et.getText().length());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rename_chat)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String t = et.getText().toString().trim();
                    if (!t.isEmpty()) vm.renameSession(s.id, t);
                })
                .show();
    }

    private void confirmDelete(SessionEntity s) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_chat)
                .setMessage(R.string.delete_chat_msg)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> vm.deleteSession(s.id))
                .show();
    }

    private void startVoice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        String lang = settings.voiceLang();
        if (!lang.isEmpty()) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        try {
            speech.launch(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.voice_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ attachments

    private void addUris(List<android.net.Uri> uris) {
        if (uris.isEmpty()) return;
        io.execute(() -> {
            final List<Attachment> added = new ArrayList<>();
            for (android.net.Uri u : uris) {
                if (pending.size() + added.size() >= MAX_ATTACHMENTS) break;
                Attachment a = AttachmentUtil.save(getApplicationContext(), u);
                if (a != null) added.add(a);
            }
            runOnUiThread(() -> {
                pending.addAll(added);
                refreshAttachments();
            });
        });
    }

    private void refreshAttachments() {
        attAdapter.notifyDataSetChanged();
        rvAtt.setVisibility(pending.isEmpty() ? View.GONE : View.VISIBLE);
        updateSendState();
    }

    // ------------------------------------------------------------------ helpers

    private void handleShareIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String t = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (t != null && !t.isEmpty()) {
            etInput.setText(t);
            etInput.setSelection(etInput.getText().length());
        }
    }

    private void updateSendState() {
        boolean can = !etInput.getText().toString().trim().isEmpty() || !pending.isEmpty();
        boolean enabled = isStreaming || can;
        btnSend.setEnabled(enabled);
        btnSend.setAlpha(enabled ? 1f : 0.35f);
        btnSend.setImageResource(isStreaming ? R.drawable.ic_stop : R.drawable.ic_arrow_up);
    }

    private void updateHint() {
        String t = etInput.getText().toString();
        if (t.length() > 300) {
            tvHint.setText("~" + TokenEstimator.estimate(t) + " tokens");
            tvHint.setVisibility(View.VISIBLE);
        } else {
            tvHint.setVisibility(View.GONE);
        }
    }

    private void updateModelPill() {
        Provider p = providers.get(settings.selectedProviderId());
        String m = settings.selectedModelId();
        if (p != null && m != null && !m.isEmpty()) tvModel.setText(m);
        else tvModel.setText(R.string.select_model);
    }

    private void scrollToEnd() {
        int n = chatAdapter.getItemCount();
        if (n == 0) return;
        rvMessages.scrollToPosition(n - 1);
        rvMessages.post(() -> rvMessages.scrollBy(0, 1000000));
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View v = getCurrentFocus();
        if (imm != null && v != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void copyText(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Sentia", text == null ? "" : text));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    private void shareText(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
        startActivity(Intent.createChooser(i, null));
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
