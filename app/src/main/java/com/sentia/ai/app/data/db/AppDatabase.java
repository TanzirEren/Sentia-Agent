package com.sentia.ai.app.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {SessionEntity.class, MessageEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    public abstract SessionDao sessionDao();

    public abstract MessageDao messageDao();

    public static synchronized AppDatabase get(Context c) {
        if (instance == null) {
            instance = Room.databaseBuilder(c.getApplicationContext(), AppDatabase.class, "sentia.db")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
