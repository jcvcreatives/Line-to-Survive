package com.example.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists best level progress and audio settings locally on the device.
 */
class ProgressRepository(context: Context) {
  private val prefs: SharedPreferences =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getBestLevel(): Int {
    return prefs.getInt(KEY_BEST_LEVEL, 1).coerceIn(1, MAX_LEVELS)
  }

  fun recordLevelReached(level: Int) {
    val clamped = level.coerceIn(1, MAX_LEVELS)
    val currentBest = getBestLevel()
    if (clamped > currentBest) {
      prefs.edit().putInt(KEY_BEST_LEVEL, clamped).apply()
    }
  }

  fun getLevelStars(level: Int): Int {
    return prefs.getInt("$KEY_LEVEL_STARS_PREFIX$level", 0).coerceIn(0, 3)
  }

  fun recordLevelStars(level: Int, stars: Int) {
    val clampedLevel = level.coerceIn(1, MAX_LEVELS)
    val clampedStars = stars.coerceIn(1, 3)
    val existing = getLevelStars(clampedLevel)
    if (clampedStars > existing) {
      prefs.edit().putInt("$KEY_LEVEL_STARS_PREFIX$clampedLevel", clampedStars).apply()
    }
  }

  fun getAllLevelStars(): Map<Int, Int> {
    val map = mutableMapOf<Int, Int>()
    for (lvl in 1..MAX_LEVELS) {
      val s = getLevelStars(lvl)
      if (s > 0) {
        map[lvl] = s
      }
    }
    return map
  }

  fun isSoundEnabled(): Boolean {
    return prefs.getBoolean(KEY_SOUND_ENABLED, true)
  }

  fun setSoundEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
  }

  companion object {
    const val MAX_LEVELS = 50
    private const val PREFS_NAME = "draw_to_survive_prefs"
    private const val KEY_BEST_LEVEL = "best_level"
    private const val KEY_LEVEL_STARS_PREFIX = "level_stars_"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
  }
}
