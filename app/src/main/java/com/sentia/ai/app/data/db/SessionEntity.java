package com.sentia.ai.app.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sessions")
public class SessionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title;
    public long createdAt;
    public long updatedAt;
    public String providerId;
    public String modelId;
    /** 0 = not in a project folder (used by the project folders feature) */
    public long folderId;
    public boolean pinned;
}
