package com.sentia.ai.app.data.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** A text file the user attached to a project. The AI can use it when it is relevant. */
@Entity(
        tableName = "project_files",
        foreignKeys = @ForeignKey(
                entity = FolderEntity.class,
                parentColumns = "id",
                childColumns = "folderId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("folderId")})
public class ProjectFileEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long folderId;
    public String name;
    public String mime;
    /** Absolute path inside the app's private storage. */
    public String path;
    public long size;
    public long addedAt;
    /** Off = the file stays in the project but is not sent to the AI. */
    public boolean enabled;
}
