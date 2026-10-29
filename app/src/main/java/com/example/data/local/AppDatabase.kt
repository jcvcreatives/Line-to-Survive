package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database for Ink Bridge Runner: Ignis Titan Edition.
 */
@Database(
  entities = [PlayerEntity::class, DailyChallengeEntity::class],
  version = 1,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun gameDao(): GameDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "ignis_titan_game_database"
        )
          .fallbackToDestructiveMigration(dropAllTables = true)
          .allowMainThreadQueries()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
