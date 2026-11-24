package com.sentia.ai.app.workspace;

/** One file or folder inside a workspace. path is relative to the workspace root and uses "/". */
public final class WsEntry {
    public final String name;
    public final String path;
    public final boolean dir;
    public final long size;
    public final long modified;

    public WsEntry(String name, String path, boolean dir, long size, long modified) {
        this.name = name;
        this.path = path;
        this.dir = dir;
        this.size = size;
        this.modified = modified;
    }

    /** The folder that contains this entry, or an empty string for the root. */
    public String parent() {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }
}
