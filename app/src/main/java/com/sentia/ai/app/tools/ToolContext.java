package com.sentia.ai.app.tools;

import android.content.Context;

import com.sentia.ai.app.workspace.WorkspaceFs;

/** What a tool may use while it runs: the app context and the project's workspace. */
public final class ToolContext {
    public static final int MAX_OUTPUT_CHARS = 30000;

    private final Context app;
    private final long projectId;
    private final WorkspaceFs fs;

    public ToolContext(Context app, long projectId, WorkspaceFs fs) {
        this.app = app.getApplicationContext();
        this.projectId = projectId;
        this.fs = fs;
    }

    public Context app() {
        return app;
    }

    public long projectId() {
        return projectId;
    }

    public WorkspaceFs fs() {
        return fs;
    }
}
