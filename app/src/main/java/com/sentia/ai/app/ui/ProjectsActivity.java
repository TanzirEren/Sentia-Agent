package com.sentia.ai.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.data.db.FolderCount;
import com.sentia.ai.app.data.db.FolderEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All projects with search and a + button to create one. */
public class ProjectsActivity extends AppCompatActivity {
    private ProjectRepo repo;
    private ProjectsAdapter adapter;
    private View searchRow, emptyView, btnCreateFirst;
    private EditText etSearch;
    private TextView tvEmptyTitle, tvEmptyMsg;
    private List<FolderEntity> all = new ArrayList<>();
    private Map<Long, Integer> chatN = new HashMap<>();
    private Map<Long, Integer> fileN = new HashMap<>();
    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_projects);
        Bars.apply(this);
        repo = ProjectRepo.get(this);

        searchRow = findViewById(R.id.searchRow);
        emptyView = findViewById(R.id.emptyView);
        btnCreateFirst = findViewById(R.id.btnCreateFirst);
        etSearch = findViewById(R.id.etSearch);
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle);
        tvEmptyMsg = findViewById(R.id.tvEmptyMsg);

        RecyclerView rv = findViewById(R.id.rvProjects);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectsAdapter(new ProjectsAdapter.Listener() {
            @Override
            public void onOpen(FolderEntity f) {
                Intent i = new Intent(ProjectsActivity.this, ProjectActivity.class);
                i.putExtra(ProjectActivity.EXTRA_ID, f.id);
                startActivity(i);
            }

            @Override
            public void onMenu(View anchor, FolderEntity f) {
                showMenu(anchor, f);
            }
        });
        rv.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAdd).setOnClickListener(v -> showCreate());
        btnCreateFirst.setOnClickListener(v -> showCreate());
        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            searchRow.setVisibility(View.VISIBLE);
            etSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        });
        findViewById(R.id.btnSearchClose).setOnClickListener(v -> {
            etSearch.setText("");
            searchRow.setVisibility(View.GONE);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        });
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                refresh();
            }
        });

        repo.folders().observe(this, l -> {
            all = l == null ? new ArrayList<FolderEntity>() : l;
            refresh();
        });
        repo.chatCounts().observe(this, l -> {
            chatN = toMap(l);
            refresh();
        });
        repo.fileCounts().observe(this, l -> {
            fileN = toMap(l);
            refresh();
        });
    }

    private static Map<Long, Integer> toMap(List<FolderCount> l) {
        Map<Long, Integer> m = new HashMap<>();
        if (l != null) for (FolderCount c : l) m.put(c.folderId, c.n);
        return m;
    }

    private void refresh() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<FolderEntity> shown = new ArrayList<>();
        for (FolderEntity f : all) {
            if (q.isEmpty()
                    || (f.name != null && f.name.toLowerCase(Locale.ROOT).contains(q))
                    || (f.description != null && f.description.toLowerCase(Locale.ROOT).contains(q))) {
                shown.add(f);
            }
        }
        adapter.submit(shown, chatN, fileN);
        boolean none = shown.isEmpty();
        emptyView.setVisibility(none ? View.VISIBLE : View.GONE);
        if (none) {
            boolean searching = !q.isEmpty();
            tvEmptyTitle.setText(searching ? R.string.projects_no_match : R.string.projects_empty_title);
            tvEmptyMsg.setVisibility(searching ? View.GONE : View.VISIBLE);
            btnCreateFirst.setVisibility(searching ? View.GONE : View.VISIBLE);
        }
    }

    private void showCreate() {
        ProjectDialog.show(this, null, (name, desc, icon) -> repo.create(name, desc, icon, f ->
                Toast.makeText(this, R.string.project_created, Toast.LENGTH_SHORT).show()));
    }

    private void showMenu(View anchor, final FolderEntity f) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, f.pinned ? R.string.act_unpin_project : R.string.act_pin_project);
        pm.getMenu().add(0, 2, 1, R.string.act_edit_project);
        pm.getMenu().add(0, 3, 2, R.string.delete);
        pm.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 1) {
                repo.setPinned(f.id, !f.pinned);
                Toast.makeText(this, f.pinned ? R.string.project_unpinned_toast : R.string.project_pinned_toast,
                        Toast.LENGTH_SHORT).show();
            } else if (id == 2) {
                ProjectDialog.show(this, f, (name, desc, icon) -> repo.update(f.id, name, desc, icon));
            } else if (id == 3) {
                confirmDelete(f);
            }
            return true;
        });
        pm.show();
    }

    private void confirmDelete(final FolderEntity f) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_project_title)
                .setMessage(R.string.delete_project_msg)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.keep_chats, (d, w) -> repo.delete(f.id, false, null))
                .setPositiveButton(R.string.delete_with_chats, (d, w) -> repo.delete(f.id, true, null))
                .show();
    }
}
