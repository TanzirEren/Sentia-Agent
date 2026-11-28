package com.sentia.ai.app.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SessionDao {
    @Insert
    long insert(SessionEntity s);

    @Update
    void update(SessionEntity s);

    @Query("DELETE FROM sessions WHERE id = :id")
    void delete(long id);

    @Query("SELECT * FROM sessions WHERE id = :id")
    SessionEntity get(long id);

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    LiveData<List<SessionEntity>> all();

    @Query("SELECT * FROM sessions WHERE title LIKE :q OR id IN "
            + "(SELECT sessionId FROM messages WHERE role != 'tool' AND content LIKE :q) ORDER BY updatedAt DESC")
    LiveData<List<SessionEntity>> search(String q);

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    void rename(long id, String title);

    @Query("UPDATE sessions SET updatedAt = :t WHERE id = :id")
    void touch(long id, long t);

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    List<SessionEntity> allSync();

    @Query("SELECT COUNT(*) FROM sessions")
    int count();

    @Query("DELETE FROM sessions")
    void deleteAll();

    @Query("UPDATE sessions SET folderId = :folderId WHERE id = :id")
    void moveToFolder(long id, long folderId);

    @Query("UPDATE sessions SET folderId = 0 WHERE folderId = :folderId")
    void clearFolder(long folderId);

    @Query("DELETE FROM sessions WHERE folderId = :folderId")
    void deleteInFolder(long folderId);

    @Query("SELECT * FROM sessions WHERE folderId = :folderId ORDER BY updatedAt DESC")
    List<SessionEntity> inFolder(long folderId);

    @Query("SELECT * FROM sessions WHERE folderId = :folderId ORDER BY pinned DESC, updatedAt DESC")
    LiveData<List<SessionEntity>> inFolderLive(long folderId);

    @Query("UPDATE sessions SET pinned = :pinned WHERE id = :id")
    void setPinned(long id, boolean pinned);

    @Query("SELECT folderId AS folderId, COUNT(*) AS n FROM sessions WHERE folderId != 0 GROUP BY folderId")
    LiveData<List<FolderCount>> folderCounts();
}
