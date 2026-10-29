package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for reactive observation and persistence of player profile and daily challenges.
 */
@Dao
interface GameDao {
  @Query("SELECT * FROM player_profile WHERE id = 0 LIMIT 1")
  fun observePlayerProfile(): Flow<PlayerEntity?>

  @Query("SELECT * FROM player_profile WHERE id = 0 LIMIT 1")
  suspend fun getPlayerProfile(): PlayerEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertPlayerProfile(player: PlayerEntity)

  @Query("SELECT * FROM daily_challenges WHERE dateKey = :dateKey LIMIT 1")
  fun observeDailyChallenge(dateKey: String): Flow<DailyChallengeEntity?>

  @Query("SELECT * FROM daily_challenges WHERE dateKey = :dateKey LIMIT 1")
  suspend fun getDailyChallenge(dateKey: String): DailyChallengeEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDailyChallenge(challenge: DailyChallengeEntity)
}
