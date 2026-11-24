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

    /** Pinned projects first, then the most recently used. */
    @Query("SELECT * FROM folders ORDER BY pinned DESC, updatedAt DESC")
    LiveData<List<FolderEntity>> allRecent();

    @Query("SELECT * FROM folders WHERE id = :id")
    FolderEntity get(long id);

    @Query("SELECT * FROM folders WHERE id = :id")
    LiveData<FolderEntity> live(long id);

    @Query("UPDATE folders SET name = :name, updatedAt = :t WHERE id = :id")
    void rename(long id, String name, long t);

    @Query("UPDATE folders SET name = :name, description = :desc, icon = :icon, updatedAt = :t WHERE id = :id")
    void updateInfo(long id, String name, String desc, int icon, long t);

    @Query("UPDATE folders SET instructions = :text, updatedAt = :t WHERE id = :id")
    void setInstructions(long id, String text, long t);

    @Query("UPDATE folders SET memory = :text, updatedAt = :t WHERE id = :id")
    void setMemory(long id, String text, long t);

    @Query("UPDATE folders SET commands = :json, updatedAt = :t WHERE id = :id")
    void setCommands(long id, String json, long t);

    @Query("UPDATE folders SET planFirst = :on WHERE id = :id")
    void setPlanFirst(long id, boolean on);

    @Query("UPDATE folders SET workspaceLinked = :linked, workspaceSource = :source, workspaceUpdatedAt = :t WHERE id = :id")
    void setWorkspace(long id, boolean linked, String source, long t);

    @Query("UPDATE folders SET pinned = :pinned WHERE id = :id")
    void setPinned(long id, boolean pinned);

    @Query("UPDATE folders SET updatedAt = :t WHERE id = :id")
    void touch(long id, long t);

    @Query("DELETE FROM folders WHERE id = :id")
    void delete(long id);
}
