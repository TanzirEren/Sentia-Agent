package com.sentia.ai.app.tools.file;

import com.sentia.ai.app.tools.Tool;
import com.sentia.ai.app.tools.ToolContext;
import com.sentia.ai.app.tools.ToolResult;
import com.sentia.ai.app.workspace.WorkspaceFs;
import com.sentia.ai.app.workspace.WsEntry;

import org.json.JSONObject;

import java.util.Locale;

/** Lists a folder as a small tree. Folders such as .git and node_modules are left out. */
public class ListDirTool implements Tool {
    private static final int MAX_ENTRIES = 500;

    @Override
    public String name() {
        return "ListDir";
    }

    @Override
    public String description() {
        return "List files and folders in the project workspace. Use depth to see inside sub folders.";
    }

    @Override
    public String jsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"path\":{\"type\":\"string\",\"description\":\"Folder path relative to the workspace root, default is the root\"},"
                + "\"depth\":{\"type\":\"integer\",\"description\":\"How many levels to show, 1 to 5, default 1\"}},"
                + "\"required\":[]}";
    }

    @Override
    public String summary(JSONObject input) {
        String p = input.optString("path", "").trim();
        return p.isEmpty() ? "./" : p;
    }

    @Override
    public boolean needsPermission(JSONObject input) {
        return false;
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext ctx) throws Exception {
        String path = input.optString("path", "").trim();
        int depth = Math.max(1, Math.min(5, input.optInt("depth", 1)));
        final WorkspaceFs fs = ctx.fs();
        String base = WorkspaceFs.normalize(path);
        WsEntry st = base.isEmpty() ? null : fs.stat(base);
        if (!base.isEmpty()) {
            if (st == null) return ToolResult.error("Folder not found: " + path);
            if (!st.dir) return ToolResult.error("This is a file. Use Read for files: " + path);
        }
        final StringBuilder sb = new StringBuilder();
        final int[] count = {0};
        final boolean[] cut = {false};
        sb.append(base.isEmpty() ? "./" : base + "/").append('\n');
        fs.walk(base, depth, (e, d) -> {
            if (count[0] >= MAX_ENTRIES) {
                cut[0] = true;
                return false;
            }
            count[0]++;
            for (int i = 0; i <= d; i++) sb.append("  ");
            sb.append(e.name);
            if (e.dir) sb.append('/');
            else sb.append("  (").append(size(e.size)).append(')');
            sb.append('\n');
            return true;
        });
        if (count[0] == 0) sb.append("  (empty)\n");
        if (cut[0]) sb.append("[truncated: more than ").append(MAX_ENTRIES).append(" entries]");
        return ToolResult.ok(sb.toString().trim(), cut[0]);
    }

    private static String size(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format(Locale.US, "%.1f KB", b / 1024.0);
        return String.format(Locale.US, "%.1f MB", b / 1048576.0);
    }
}
