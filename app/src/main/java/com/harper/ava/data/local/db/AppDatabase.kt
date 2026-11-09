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

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongCrossRef::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ava.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
