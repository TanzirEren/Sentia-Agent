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

    @Query("SELECT COALESCE(SUM(tokensIn), 0) FROM messages WHERE role = 'assistant' AND createdAt >= :since")
    long sumIn(long since);

    @Query("SELECT COALESCE(SUM(tokensOut), 0) FROM messages WHERE role = 'assistant' AND createdAt >= :since")
    long sumOut(long since);

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'assistant' AND createdAt >= :since")
    int replies(long since);

    @Query("SELECT modelLabel AS label, SUM(tokensIn) AS tin, SUM(tokensOut) AS tout, COUNT(*) AS n "
            + "FROM messages WHERE role = 'assistant' AND modelLabel IS NOT NULL AND createdAt >= :since "
            + "GROUP BY modelLabel ORDER BY (SUM(tokensIn) + SUM(tokensOut)) DESC")
    List<ModelUsage> byModel(long since);

    @Query("SELECT createdAt AS createdAt, (tokensIn + tokensOut) AS total FROM messages "
            + "WHERE role = 'assistant' AND createdAt >= :since")
    List<TokenPoint> points(long since);

    @Query("SELECT COALESCE(SUM(tokensIn + tokensOut), 0) FROM messages WHERE sessionId = :sid AND role = 'assistant'")
    long sessionTokens(long sid);
}
