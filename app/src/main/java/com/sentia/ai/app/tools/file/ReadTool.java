package com.sentia.ai.app.tools.file;

import com.sentia.ai.app.tools.Tool;
import com.sentia.ai.app.tools.ToolContext;
import com.sentia.ai.app.tools.ToolResult;
import com.sentia.ai.app.workspace.WorkspaceFs;
import com.sentia.ai.app.workspace.WsEntry;
import com.sentia.ai.app.workspace.WsException;

import org.json.JSONObject;

import java.util.Locale;

/** Reads a text file with line numbers, so the model can point to exact lines when it edits. */
public class ReadTool implements Tool {
    public static final int DEFAULT_LIMIT = 2000;
    private static final int MAX_LINE_CHARS = 2000;

    @Override
    public String name() {
        return "Read";
    }

    @Override
    public String description() {
        return "Read a text file from the project workspace. Output has line numbers. "
                + "Use offset and limit to read a part of a big file.";
    }

    @Override
    public String jsonSchema() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"path\":{\"type\":\"string\",\"description\":\"File path relative to the workspace root\"},"
                + "\"offset\":{\"type\":\"integer\",\"description\":\"First line to read, starting at 1\"},"
                + "\"limit\":{\"type\":\"integer\",\"description\":\"Maximum number of lines, default 2000\"}},"
                + "\"required\":[\"path\"]}";
    }

    @Override
    public boolean needsPermission(JSONObject input) {
        return false;
    }

    @Override
    public ToolResult execute(JSONObject input, ToolContext ctx) throws Exception {
        String path = input.optString("path", "").trim();
        if (path.isEmpty()) return ToolResult.error("Missing parameter: path");
        int offset = Math.max(1, input.optInt("offset", 1));
        int limit = input.optInt("limit", DEFAULT_LIMIT);
        if (limit <= 0) limit = DEFAULT_LIMIT;
        limit = Math.min(limit, 5000);

        WorkspaceFs fs = ctx.fs();
        WsEntry st = fs.stat(path);
        if (st == null) return ToolResult.error("File not found: " + path);
        if (st.dir) return ToolResult.error("This is a folder. Use ListDir for folders: " + path);

        WorkspaceFs.Slice s;
        try {
            s = fs.readLines(path, offset, limit, MAX_LINE_CHARS);
        } catch (WsException e) {
            if (e.code == WsException.BINARY || e.code == WsException.NOT_UTF8) {
                return ToolResult.error("This is a binary file and cannot be read as text: " + path);
            }
            if (e.code == WsException.TOO_LARGE) {
                return ToolResult.error("This file is too large to read: " + path);
            }
            throw e;
        }
        if (s.total == 0) return ToolResult.ok("(empty file)");
        if (offset > s.total) {
            return ToolResult.error("offset " + offset + " is past the end. The file has " + s.total + " lines.");
        }
        StringBuilder sb = new StringBuilder();
        int no = s.from;
        int shown = 0;
        boolean truncated = false;
        for (String line : s.lines) {
            String row = String.format(Locale.US, "%6d\t", no) + line + "\n";
            if (sb.length() + row.length() > ToolContext.MAX_OUTPUT_CHARS && shown > 0) {
                truncated = true;
                break;
            }
            sb.append(row);
            no++;
            shown++;
        }
        int last = s.from + shown - 1;
        if (last < s.total) {
            sb.append("[showing lines ").append(s.from).append("-").append(last).append(" of ").append(s.total)
                    .append(". Use offset=").append(last + 1).append(" to continue]");
            truncated = true;
        }
        if (s.cutLines) sb.append("\n[some long lines were cut at ").append(MAX_LINE_CHARS).append(" characters]");
        String out = sb.toString();
        return ToolResult.ok(out, truncated);
    }
}
