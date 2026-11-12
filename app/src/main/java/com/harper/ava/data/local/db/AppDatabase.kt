package com.harper.ava.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 1 -> 2 (stage 5): the scanner now stores the MediaStore
 * RELATIVE_PATH and DISPLAY_NAME of each song, so the Library Folders
 * tab can group by folder and the lyrics/details screens can find the
 * file name. Both columns are nullable, so existing rows stay valid.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN folderPath TEXT")
        db.execSQL("ALTER TABLE songs ADD COLUMN fileName TEXT")
    }
}

/**
 * Migration 2 -> 3 (0.7.0 Insights): adds the `listening_history` table —
 * one row per real play, with a snapshot of the song's metadata. No
 * existing table changes, so no user data is touched.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `listening_history` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`songId` INTEGER NOT NULL, " +
                "`playedAtMs` INTEGER NOT NULL, " +
                "`listenedMs` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, " +
                "`album` TEXT NOT NULL, " +
                "`genre` TEXT, " +
                "`durationMs` INTEGER NOT NULL, " +
                "`albumArtUri` TEXT)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_playedAtMs` ON `listening_history` (`playedAtMs`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_history_songId` ON `listening_history` (`songId`)")
    }
}

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class, FavoriteEntity::class, ListeningHistoryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ava.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
