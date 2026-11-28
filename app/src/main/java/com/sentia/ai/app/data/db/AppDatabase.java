package com.sentia.ai.app.data.db;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {SessionEntity.class, MessageEntity.class, FolderEntity.class, ProjectFileEntity.class}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase instance;

    public abstract SessionDao sessionDao();

    public abstract MessageDao messageDao();

    public abstract FolderDao folderDao();

    public abstract ProjectFileDao projectFileDao();

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
            // Safety: make sure the project columns exist on the chats table.
            addColumnIfMissing(db, "sessions", "folderId", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "sessions", "pinned", "INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** 2 to 3: project icon, description, pin, memory, commands, plan-first, and the project files table. */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            addColumnIfMissing(db, "folders", "description", "TEXT");
            addColumnIfMissing(db, "folders", "icon", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "folders", "pinned", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "folders", "memory", "TEXT");
            addColumnIfMissing(db, "folders", "commands", "TEXT");
            addColumnIfMissing(db, "folders", "planFirst", "INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS `project_files` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`folderId` INTEGER NOT NULL, "
                    + "`name` TEXT, "
                    + "`mime` TEXT, "
                    + "`path` TEXT, "
                    + "`size` INTEGER NOT NULL, "
                    + "`addedAt` INTEGER NOT NULL, "
                    + "`enabled` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_files_folderId` ON `project_files` (`folderId`)");
        }
    };

    /** 3 to 4: link a project to its workspace folder. Only new columns, nothing is removed. */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            addColumnIfMissing(db, "folders", "workspaceLinked", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "folders", "workspaceSource", "TEXT");
            addColumnIfMissing(db, "folders", "workspaceUpdatedAt", "INTEGER NOT NULL DEFAULT 0");
        }
    };

    /** 4 to 5: tool calls and tool results are stored with the messages. Only new columns. */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            addColumnIfMissing(db, "messages", "toolCallsJson", "TEXT");
            addColumnIfMissing(db, "messages", "toolCallId", "TEXT");
            addColumnIfMissing(db, "messages", "toolName", "TEXT");
            addColumnIfMissing(db, "messages", "toolError", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(db, "messages", "toolMs", "INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static void addColumnIfMissing(SupportSQLiteDatabase db, String table, String column, String ddl) {
        boolean found = false;
        Cursor c = db.query("PRAGMA table_info(`" + table + "`)");
        try {
            int nameIdx = c.getColumnIndex("name");
            while (c.moveToNext()) {
                if (column.equals(c.getString(nameIdx))) {
                    found = true;
                    break;
                }
            }
        } finally {
            c.close();
        }
        if (!found) db.execSQL("ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + ddl);
    }

    public static synchronized AppDatabase get(Context c) {
        if (instance == null) {
            instance = Room.databaseBuilder(c.getApplicationContext(), AppDatabase.class, "sentia.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build();
        }
        return instance;
    }
}
