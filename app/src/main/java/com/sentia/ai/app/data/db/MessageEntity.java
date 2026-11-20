package com.sentia.ai.app.data.db;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "messages",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "id",
                childColumns = "sessionId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("sessionId")})
public class MessageEntity {
    public static final int DONE = 0;
    public static final int STREAMING = 1;
    public static final int STOPPED = 2;

    @PrimaryKey(autoGenerate = true)
    public long id;
    public long sessionId;
    /** "user", "assistant" or "error" */
    public String role;
    public String content;
    public String attachmentsJson;
    public String modelLabel;
    public int tokensIn;
    public int tokensOut;
    public long createdAt;
    public int status;

    public MessageEntity copy() {
        MessageEntity m = new MessageEntity();
        m.id = id;
        m.sessionId = sessionId;
        m.role = role;
        m.content = content;
        m.attachmentsJson = attachmentsJson;
        m.modelLabel = modelLabel;
        m.tokensIn = tokensIn;
        m.tokensOut = tokensOut;
        m.createdAt = createdAt;
        m.status = status;
        return m;
    }
}
