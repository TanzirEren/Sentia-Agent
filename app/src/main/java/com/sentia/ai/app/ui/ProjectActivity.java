package com.sentia.ai.app.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.data.db.ProjectFileEntity;
import com.sentia.ai.app.data.db.SessionEntity;
import com.sentia.ai.app.model.ProjectCommand;
import com.sentia.ai.app.util.ChatExporter;
import com.sentia.ai.app.util.Slash;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One project: files box, instructions box, smart tools, its chats and a New chat button. */
public class ProjectActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "project_id";

    private long folderId;
    private ProjectRepo repo;
    private ProjectPageAdapter adapter;
    private FolderEntity folder;
    private List<SessionEntity> chats = new ArrayList<>();
    private List<ProjectFileEntity> files = new ArrayList<>();
    private List<FolderEntity> allFolders = new ArrayList<>();
    private TextView tvTop;
    private ImageButton btnPin;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<String[]> pickFiles;
    private ActivityResultLauncher<String> exportMd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        folderId = getIntent().getLongExtra(EXTRA_ID, 0);
        if (folderId == 0) {
            finish();
            return;
        }
        setContentView(R.layout.activity_project);
        Bars.apply(this);
        repo = ProjectRepo.get(this);
        tvTop = findViewById(R.id.tvTopTitle);
        btnPin = findViewById(R.id.btnPin);

        pickFiles = registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
            if (uris == null || uris.isEmpty()) return;
            repo.addFiles(folderId, new ArrayList<Uri>(uris), r -> {
                String msg = "";
                if (r.added > 0) msg = getString(R.string.files_added, r.added);
                if (r.rejected > 0) msg += (msg.isEmpty() ? "" : "  ") + getString(R.string.files_rejected, r.rejected);
                if (r.limitReached) msg += "  " + getString(R.string.files_limit, ProjectRepo.MAX_FILES);
                if (!msg.isEmpty()) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            });
        });
        exportMd = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/markdown"), uri -> {
            if (uri == null || folder == null) return;
            final String name = folder.name;
            io.execute(() -> {
                final boolean ok = ChatExporter.exportFolder(getApplicationContext(), uri, folderId, name, true);
                runOnUiThread(() -> Toast.makeText(this, ok ? R.string.export_done : R.string.export_failed,
                        Toast.LENGTH_SHORT).show());
            });
        });

        RecyclerView rv = findViewById(R.id.rvPage);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectPageAdapter(new ProjectPageAdapter.Listener() {
            @Override
            public void onFiles() {
                ProjectFilesSheet.show(ProjectActivity.this, repo, folderId,
                        () -> pickFiles.launch(new String[]{"*/*"}));
            }

            @Override
            public void onInstructions() {
                editText(R.string.project_instructions, R.string.project_instructions_hint,
                        folder == null ? "" : folder.instructions, t -> repo.setInstructions(folderId, t));
            }

            @Override
            public void onMemory() {
                editText(R.string.memory_title, R.string.memory_hint,
                        folder == null ? "" : folder.memory, t -> repo.setMemory(folderId, t));
            }

            @Override
            public void onCommands() {
                showCommands();
            }

            @Override
            public void onTogglePlan() {
                if (folder == null) return;
                boolean on = !folder.planFirst;
                repo.setPlanFirst(folderId, on);
                Toast.makeText(ProjectActivity.this, on ? R.string.plan_on : R.string.plan_off, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onBrief() {
                runBrief();
            }

            @Override
            public void onOpenChat(SessionEntity s) {
                Intent i = new Intent(ProjectActivity.this, MainActivity.class);
                i.putExtra(MainActivity.EXTRA_OPEN_SESSION, s.id);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
            }

            @Override
            public void onChatMenu(View anchor, SessionEntity s) {
                showChatMenu(anchor, s);
            }
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.fabNewChat).setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra(MainActivity.EXTRA_NEW_CHAT_IN, folderId);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        });
        btnPin.setOnClickListener(v -> {
            if (folder == null) return;
            repo.setPinned(folderId, !folder.pinned);
            Toast.makeText(this, folder.pinned ? R.string.project_unpinned_toast : R.string.project_pinned_toast,
                    Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnMore).setOnClickListener(this::showMenu);

        repo.folder(folderId).observe(this, f -> {
            if (f == null) {
                if (folder != null) finish();
                return;
            }
            folder = f;
            tvTop.setText(f.name);
            btnPin.setImageResource(f.pinned ? R.drawable.ic_pin_filled : R.drawable.ic_pin);
            btnPin.setContentDescription(getString(f.pinned ? R.string.act_unpin_project : R.string.act_pin_project));
            push();
        });
        repo.chatsIn(folderId).observe(this, l -> {
            chats = l == null ? new ArrayList<SessionEntity>() : l;
            push();
        });
        repo.files(folderId).observe(this, l -> {
            files = l == null ? new ArrayList<ProjectFileEntity>() : l;
            push();
        });
        repo.folders().observe(this, l -> allFolders = l == null ? new ArrayList<FolderEntity>() : l);
    }

    private void push() {
        adapter.submit(folder, chats, files);
    }

    // ------------------------------------------------------------ menus

    private void showMenu(View anchor) {
        if (folder == null) return;
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, R.string.act_edit_project);
        pm.getMenu().add(0, 2, 1, R.string.act_export);
        pm.getMenu().add(0, 3, 2, R.string.delete);
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                ProjectDialog.show(this, folder, (name, desc, icon) -> repo.update(folderId, name, desc, icon));
            } else if (id == 2) {
                exportMd.launch(safe(folder.name) + ".md");
            } else if (id == 3) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete_project_title)
                        .setMessage(R.string.delete_project_msg)
                        .setNegativeButton(R.string.cancel, null)
                        .setNeutralButton(R.string.keep_chats, (d, w) -> repo.delete(folderId, false, null))
                        .setPositiveButton(R.string.delete_with_chats, (d, w) -> repo.delete(folderId, true, null))
                        .show();
            }
            return true;
        });
        pm.show();
    }

    private static String safe(String raw) {
        String n = raw == null ? "" : raw.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
        if (n.length() > 40) n = n.substring(0, 40);
        return n.isEmpty() ? "sentia-project" : n;
    }

    private void showChatMenu(View anchor, final SessionEntity s) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, s.pinned ? R.string.act_unpin : R.string.act_pin);
        pm.getMenu().add(0, 2, 1, R.string.rename);
        pm.getMenu().add(0, 3, 2, R.string.act_move_project);
        pm.getMenu().add(0, 4, 3, R.string.delete);
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) repo.setSessionPinned(s.id, !s.pinned);
            else if (id == 2) renameChat(s);
            else if (id == 3) moveChat(s);
            else if (id == 4) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.delete_chat)
                        .setMessage(R.string.delete_chat_msg)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.delete, (d, w) -> repo.deleteSession(s.id))
                        .show();
            }
            return true;
        });
        pm.show();
    }

    private void renameChat(final SessionEntity s) {
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
                    if (!t.isEmpty()) repo.renameSession(s.id, t);
                })
                .show();
    }

    private void moveChat(final SessionEntity s) {
        List<ActionSheet.Row> rows = new ArrayList<>();
        for (final FolderEntity f : allFolders) {
            if (f.id == s.folderId) continue;
            rows.add(new ActionSheet.Row(com.sentia.ai.app.util.ProjectIcons.res(f.icon),
                    f.name == null ? "" : f.name, () -> repo.moveSession(s.id, f.id)));
        }
        rows.add(new ActionSheet.Row(R.drawable.ic_close, getString(R.string.no_project), () -> repo.moveSession(s.id, 0)));
        ActionSheet.show(this, getString(R.string.move_title), rows);
    }

    // ------------------------------------------------------------ smart tools

    private interface TextSaved {
        void onSaved(String text);
    }

    private void editText(int title, int hint, String current, final TextSaved cb) {
        View v = getLayoutInflater().inflate(R.layout.dialog_text_edit, null);
        final TextInputEditText et = v.findViewById(R.id.etDialogText);
        et.setText(current == null ? "" : current);
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(hint)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (d, w) ->
                        cb.onSaved(et.getText() == null ? "" : et.getText().toString().trim()))
                .show();
    }

    private void showCommands() {
        final List<ProjectCommand> cmds = ProjectRepo.parseCommands(folder == null ? null : folder.commands);
        List<ActionSheet.Row> rows = new ArrayList<>();
        for (final ProjectCommand pc : cmds) {
            rows.add(new ActionSheet.Row(R.drawable.ic_slash, "/" + pc.name, () -> editCommand(cmds, pc),
                    () -> new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.command_delete_title)
                            .setMessage("/" + pc.name)
                            .setNegativeButton(R.string.cancel, null)
                            .setPositiveButton(R.string.delete, (d, w) -> {
                                cmds.remove(pc);
                                repo.setCommands(folderId, cmds);
                            })
                            .show()));
        }
        rows.add(new ActionSheet.Row(R.drawable.ic_plus, getString(R.string.command_add), () -> editCommand(cmds, null)));
        ActionSheet.show(this, getString(R.string.commands_title) + "\n" + getString(R.string.commands_hint), rows);
    }

    private void editCommand(final List<ProjectCommand> cmds, final ProjectCommand existing) {
        View v = getLayoutInflater().inflate(R.layout.dialog_command, null);
        final TextInputEditText etName = v.findViewById(R.id.etCmdName);
        final TextInputEditText etPrompt = v.findViewById(R.id.etCmdPrompt);
        if (existing != null) {
            etName.setText(existing.name);
            etPrompt.setText(existing.prompt);
        }
        final AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.command_add)
                .setView(v)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = Slash.clean(etName.getText() == null ? "" : etName.getText().toString());
            String prompt = etPrompt.getText() == null ? "" : etPrompt.getText().toString().trim();
            if (name.isEmpty() || prompt.isEmpty()) {
                Toast.makeText(this, R.string.command_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (existing != null) cmds.remove(existing);
            for (int i = cmds.size() - 1; i >= 0; i--) if (name.equals(cmds.get(i).name)) cmds.remove(i);
            cmds.add(new ProjectCommand(name, prompt));
            repo.setCommands(folderId, cmds);
            d.dismiss();
        });
    }

    private void runBrief() {
        final AlertDialog wait = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.brief_title)
                .setMessage(R.string.brief_working)
                .setCancelable(false)
                .show();
        repo.generateBrief(folderId, r -> {
            if (isFinishing() || isDestroyed()) return;
            wait.dismiss();
            if (r.error != null) {
                Toast.makeText(this, r.error, Toast.LENGTH_LONG).show();
                return;
            }
            final String text = r.text;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.brief_title)
                    .setMessage(text)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.brief_save, (d, w) -> {
                        String cur = folder == null || folder.memory == null ? "" : folder.memory.trim();
                        repo.setMemory(folderId, cur.isEmpty() ? text : cur + "\n" + text);
                        Toast.makeText(this, R.string.brief_saved, Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }
}
