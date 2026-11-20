package com.sentia.ai.app.ui;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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
import com.sentia.ai.app.data.db.MessageEntity;
import com.sentia.ai.app.data.db.SessionEntity;
import com.sentia.ai.app.model.Attachment;
import com.sentia.ai.app.model.Provider;
import com.sentia.ai.app.model.SavedPrompt;
import com.sentia.ai.app.util.AttachmentUtil;
import com.sentia.ai.app.util.TokenEstimator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final int MAX_ATTACHMENTS = 8;

    private ChatViewModel vm;
    private SettingsStore settings;
    private ProviderStore providers;

    private DrawerLayout drawer;
    private RecyclerView rvMessages, rvSessions, rvAtt;
    private EditText etInput, etSearch;
    private TextView tvTitle, tvModel, tvHint;
    private View emptyState, editBanner, btnScrollDown;
    private ImageButton btnSend;

    private ChatAdapter chatAdapter;
    private SessionAdapter sessionAdapter;
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
        setupMessages();
        setupDrawer();
        setupInput();
        observe();
        setupBack();

        etInput.setText(settings.draft());
        etInput.setSelection(etInput.getText().length());
        updateModelPill();
        updateSendState();
        handleShareIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        vm.ensureSelection();
        updateModelPill();
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
        findViewById(R.id.rowNewChat).setOnClickListener(v -> {
            startNewChat();
            drawer.closeDrawer(GravityCompat.START);
        });

        sessionAdapter = new SessionAdapter(this, new SessionAdapter.Listener() {
            @Override
            public void onOpen(SessionEntity s) {
                vm.openSession(s.id);
                drawer.closeDrawer(GravityCompat.START);
            }

            @Override
            public void onMenu(View anchor, SessionEntity s) {
                showSessionMenu(anchor, s);
            }
        });
        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        rvSessions.setAdapter(sessionAdapter);

        etSearch.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                vm.setSearch(s.toString());
            }
        });

        View.OnClickListener soon = v -> Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
        findViewById(R.id.btnSettings).setOnClickListener(soon);
        findViewById(R.id.btnProfile).setOnClickListener(soon);
        findViewById(R.id.btnTokens).setOnClickListener(soon);
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

        etInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateSendState();
                updateHint();
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
                if (autoScroll) scrollToEnd();
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
        vm.sessions().observe(this, l -> sessionAdapter.submit(l));
        vm.currentSession().observe(this, id -> sessionAdapter.setCurrent(id == null ? 0 : id));
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
                if (drawer.isDrawerOpen(GravityCompat.START)) {
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
                QuickProviderDialog.show(this, null, this::updateModelPill);
            } else {
                showModelPicker();
            }
            return;
        }
        btnSend.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        vm.send(text, new ArrayList<>(pending));
        etInput.setText("");
        settings.setDraft("");
        pending.clear();
        refreshAttachments();
        autoScroll = true;
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
                QuickProviderDialog.show(MainActivity.this, null, MainActivity.this::updateModelPill);
            }

            @Override
            public void onEditProvider(Provider p) {
                QuickProviderDialog.show(MainActivity.this, p, MainActivity.this::updateModelPill);
            }
        });
    }

    private void showSessionMenu(View anchor, SessionEntity s) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, R.string.rename);
        pm.getMenu().add(0, 2, 1, R.string.delete);
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) showRename(s);
            else if (item.getItemId() == 2) confirmDelete(s);
            return true;
        });
        pm.show();
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
