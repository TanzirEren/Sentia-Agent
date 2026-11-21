package com.sentia.ai.app.data.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface MessageDao {
    @Insert
    long insert(MessageEntity m);

    @Update
    void update(MessageEntity m);

    @Query("SELECT * FROM messages WHERE sessionId = :sid ORDER BY id ASC")
    List<MessageEntity> list(long sid);

    @Query("DELETE FROM messages WHERE sessionId = :sid AND id >= :fromId")
    void deleteFrom(long sid, long fromId);

    @Query("DELETE FROM messages WHERE id = :id")
    void delete(long id);

    @Query("SELECT COUNT(*) FROM messages")
    int count();
}
