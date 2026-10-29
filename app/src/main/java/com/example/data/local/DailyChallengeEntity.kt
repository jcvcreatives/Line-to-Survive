package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity persisting the Daily Challenge seeded by UTC date (`yyyy-MM-dd`).
 */
@Entity(tableName = "daily_challenges")
data class DailyChallengeEntity(
  @PrimaryKey val dateKey: String,
  val seed: Long,
  val modifierName: String,
  val completed: Boolean = false
)
