package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClipProjectEntity::class, VideoClipEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ClipForgeDatabase : RoomDatabase() {
    abstract fun clipForgeDao(): ClipForgeDao

    companion object {
        @Volatile
        private var instance: ClipForgeDatabase? = null

        fun getInstance(context: Context): ClipForgeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClipForgeDatabase::class.java,
                    "clipforge_database"
                ).build().also { instance = it }
            }
        }
    }
}
