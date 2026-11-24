package com.sentia.ai.app.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A project: groups chats and carries its own instructions, files, memory and commands. */
@Entity(tableName = "folders")
public class FolderEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;
    public long createdAt;
    public long updatedAt;
    /** Extra instructions sent with every chat inside this project. */
    public String instructions;
    public int color;
    /** Short description shown on the project card. */
    public String description;
    /** Index into ProjectIcons (0 to 29). */
    public int icon;
    public boolean pinned;
    /** Project memory: one note per line, filled by the user and by the AI. */
    public String memory;
    /** Custom slash commands as a JSON array of {name, prompt}. */
    public String commands;
    /** When true the AI proposes a short plan before big tasks. */
    public boolean planFirst;
    /** True when this project has a workspace folder (files the agent can work on). */
    public boolean workspaceLinked;
    /** Where the workspace came from: blank, folder or zip. */
    public String workspaceSource;
    public long workspaceUpdatedAt;
}
