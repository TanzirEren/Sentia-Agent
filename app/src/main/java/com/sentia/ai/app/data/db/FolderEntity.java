package com.sentia.ai.app.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** A project folder that groups chats and can carry its own instructions. */
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
}
