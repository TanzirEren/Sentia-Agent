package com.sentia.ai.app.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.sentia.ai.app.R;
import com.sentia.ai.app.data.ProjectRepo;
import com.sentia.ai.app.data.db.FolderEntity;
import com.sentia.ai.app.tools.Tool;
import com.sentia.ai.app.tools.ToolContext;
import com.sentia.ai.app.tools.ToolRegistry;
import com.sentia.ai.app.tools.ToolResult;
import com.sentia.ai.app.util.ProjectIcons;
import com.sentia.ai.app.workspace.WorkspaceManager;
import com.sentia.ai.app.workspace.WsErrors;
import com.sentia.ai.app.workspace.WsException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Developer screen: run one tool by hand on a project's workspace, before the agent uses it. */
public class ToolTesterActivity extends AppCompatActivity {
    private TextView tvProject, tvInfo, tvOutput;
    private EditText etArgs;
    private LinearLayout chips;
    private List<FolderEntity> folders = new ArrayList<>();
    private FolderEntity project;
    private Tool tool;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tool_tester);
        Bars.apply(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvProject = findViewById(R.id.tvTesterProject);
        tvInfo = findViewById(R.id.tvToolInfo);
        tvOutput = findViewById(R.id.tvOutput);
        etArgs = findViewById(R.id.etArgs);
        chips = findViewById(R.id.toolChips);
        etArgs.setSaveEnabled(false);
        tvOutput.setSaveEnabled(false);
        chips.setSaveFromParentEnabled(false);

        for (final Tool t : ToolRegistry.get().all()) {
            TextView c = new TextView(this);
            c.setText(t.name());
            c.setTextSize(13);
            c.setGravity(android.view.Gravity.CENTER);
            c.setPadding(dp(16), 0, dp(16), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
            lp.setMarginEnd(dp(8));
            c.setLayoutParams(lp);
            c.setTag(t);
            c.setOnClickListener(v -> selectTool(t));
            chips.addView(c);
        }

        tvProject.setOnClickListener(v -> chooseProject());
        findViewById(R.id.btnRun).setOnClickListener(v -> run());

        ProjectRepo.get(this).folders().observe(this, l -> {
            folders = l == null ? new ArrayList<FolderEntity>() : l;
            if (project == null) {
                for (FolderEntity f : folders) {
                    if (f.workspaceLinked) {
                        project = f;
                        break;
                    }
                }
            }
            updateProject();
        });
        List<Tool> all = ToolRegistry.get().all();
        if (!all.isEmpty()) selectTool(all.get(0));
        updateProject();
    }

    private void updateProject() {
        tvProject.setText(project == null ? getString(R.string.tester_pick_project) : project.name);
    }

    private void chooseProject() {
        List<ActionSheet.Row> rows = new ArrayList<>();
        for (final FolderEntity f : folders) {
            String label = (f.name == null ? "" : f.name) + (f.workspaceLinked ? "" : "  (" + getString(R.string.tester_no_ws) + ")");
            rows.add(new ActionSheet.Row(ProjectIcons.res(f.icon), label, () -> {
                project = f;
                updateProject();
            }));
        }
        if (rows.isEmpty()) {
            tvOutput.setText(R.string.tester_no_projects);
            return;
        }
        ActionSheet.show(this, getString(R.string.tester_pick_project), rows);
    }

    private void selectTool(Tool t) {
        tool = t;
        for (int i = 0; i < chips.getChildCount(); i++) {
            View c = chips.getChildAt(i);
            boolean on = c.getTag() == t;
            c.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip_outline);
            ((TextView) c).setTextColor(getColor(on ? R.color.on_ink : R.color.ink));
        }
        tvInfo.setText(t.description() + (t.needsPermission(new JSONObject()) ? "\n" + getString(R.string.tester_needs_perm) : ""));
        etArgs.setText(template(t));
    }

    /** A starting JSON for the tool, built from its schema. */
    private static String template(Tool t) {
        try {
            JSONObject schema = new JSONObject(t.jsonSchema());
            JSONObject props = schema.optJSONObject("properties");
            JSONObject out = new JSONObject();
            if (props != null) {
                Iterator<String> it = props.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject p = props.optJSONObject(k);
                    String type = p == null ? "string" : p.optString("type", "string");
                    if ("integer".equals(type) || "number".equals(type)) out.put(k, 1);
                    else if ("boolean".equals(type)) out.put(k, false);
                    else out.put(k, "");
                }
            }
            return out.toString(2);
        } catch (JSONException e) {
            return "{}";
        }
    }

    private void run() {
        if (tool == null) return;
        if (project == null) {
            tvOutput.setText(R.string.tester_pick_project);
            return;
        }
        final JSONObject input;
        try {
            String raw = etArgs.getText().toString().trim();
            input = raw.isEmpty() ? new JSONObject() : new JSONObject(raw);
        } catch (JSONException e) {
            tvOutput.setText(getString(R.string.tester_bad_json, e.getMessage()));
            return;
        }
        final long id = project.id;
        final String name = tool.name();
        tvOutput.setText(R.string.tester_running);
        WorkspaceManager.run(() -> {
            String text;
            try {
                long t0 = SystemClock.elapsedRealtime();
                ToolContext ctx = new ToolContext(this, id, WorkspaceManager.fs(this, id, false));
                ToolResult r = ToolRegistry.get().execute(name, input, ctx);
                long ms = SystemClock.elapsedRealtime() - t0;
                text = (r.error ? "ERROR" : "OK") + " - " + ms + " ms" + (r.truncated ? " - truncated" : "")
                        + "\n\n" + r.text;
            } catch (WsException e) {
                text = "ERROR\n\n" + WsErrors.text(this, e);
            }
            final String shown = text;
            WorkspaceManager.post(() -> tvOutput.setText(shown));
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
