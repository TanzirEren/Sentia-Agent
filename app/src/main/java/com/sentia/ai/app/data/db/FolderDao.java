package com.sentia.ai.app.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FolderDao {
    @Insert
    long insert(FolderEntity f);

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    LiveData<List<FolderEntity>> all();

    @Query("SELECT * FROM folders WHERE id = :id")
    FolderEntity get(long id);

    @Query("UPDATE folders SET name = :name, updatedAt = :t WHERE id = :id")
    void rename(long id, String name, long t);

    @Query("UPDATE folders SET instructions = :text, updatedAt = :t WHERE id = :id")
    void setInstructions(long id, String text, long t);

    @Query("DELETE FROM folders WHERE id = :id")
    void delete(long id);
}
