package com.sentia.ai.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.workspace.WorkspaceManager;

/** Full screen file explorer of one project's workspace. */
public class FilesActivity extends AppCompatActivity {
    public static final String EXTRA_PROJECT = "project_id";

    private long projectId;
    private ExplorerController explorer;
    private WorkspaceActions actions;
    private boolean askedToCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectId = getIntent().getLongExtra(EXTRA_PROJECT, 0);
        if (projectId == 0) {
            finish();
            return;
        }
        setContentView(R.layout.activity_files);
        Bars.apply(this);
        final TextView tvTop = findViewById(R.id.tvTopTitle);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        actions = new WorkspaceActions(this, projectId, "", new WorkspaceActions.Listener() {
            @Override
            public void onWorkspaceChanged() {
                if (explorer != null) explorer.refresh();
            }
        });
        explorer = new ExplorerController(this, (ViewGroup) findViewById(R.id.explorerHost), projectId, "", actions,
                new ExplorerController.Host() {
                    @Override
                    public void onOpenFile(String rel) {
                        openFile(rel);
                    }
                });
        explorer.setTitleVisible(false);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!explorer.handleBack()) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        ProjectRepo.get(this).folder(projectId).observe(this, f -> {
            if (f == null) {
                finish();
                return;
            }
            tvTop.setText(f.name);
            explorer.setProject(projectId, f.name);
            if (!askedToCreate && !WorkspaceManager.exists(this, projectId)) {
                askedToCreate = true;
                actions.showAddSheet();
            }
        });
    }

    private void openFile(String rel) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_PROJECT, projectId);
        i.putExtra(EditorActivity.EXTRA_PATH, rel);
        startActivity(i);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (explorer != null) explorer.onResume();
    }

    @Override
    protected void onDestroy() {
        if (explorer != null) explorer.release();
        super.onDestroy();
    }
}
