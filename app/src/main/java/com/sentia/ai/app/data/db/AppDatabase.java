package com.sentia.ai.app.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {SessionEntity.class, MessageEntity.class, FolderEntity.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    public abstract SessionDao sessionDao();

    public abstract MessageDao messageDao();

    public abstract FolderDao folderDao();

    /** 1 to 2: adds project folders. Existing chats are kept. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `folders` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`updatedAt` INTEGER NOT NULL, "
                    + "`instructions` TEXT, "
                    + "`color` INTEGER NOT NULL)");
        }
    };

    public static synchronized AppDatabase get(Context c) {
        if (instance == null) {
            instance = Room.databaseBuilder(c.getApplicationContext(), AppDatabase.class, "sentia.db")
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
        }
        return instance;
    }
}
