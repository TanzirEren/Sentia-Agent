package com.sentia.ai.app.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ProjectFileDao {
    @Insert
    long insert(ProjectFileEntity f);

    @Query("SELECT * FROM project_files WHERE folderId = :fid ORDER BY addedAt DESC")
    LiveData<List<ProjectFileEntity>> live(long fid);

    @Query("SELECT * FROM project_files WHERE folderId = :fid ORDER BY addedAt ASC")
    List<ProjectFileEntity> list(long fid);

    @Query("SELECT * FROM project_files WHERE id = :id")
    ProjectFileEntity get(long id);

    @Query("UPDATE project_files SET enabled = :on WHERE id = :id")
    void setEnabled(long id, boolean on);

    @Query("DELETE FROM project_files WHERE id = :id")
    void delete(long id);

    @Query("SELECT folderId AS folderId, COUNT(*) AS n FROM project_files GROUP BY folderId")
    LiveData<List<FolderCount>> counts();
}
