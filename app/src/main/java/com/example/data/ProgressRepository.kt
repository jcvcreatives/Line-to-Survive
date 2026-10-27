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
    return prefs.getInt(KEY_BEST_LEVEL, 1).coerceAtLeast(1)
  }

  fun recordLevelReached(level: Int) {
    val currentBest = getBestLevel()
    if (level > currentBest) {
      prefs.edit().putInt(KEY_BEST_LEVEL, level).apply()
    }
  }

  fun isSoundEnabled(): Boolean {
    return prefs.getBoolean(KEY_SOUND_ENABLED, true)
  }

  fun setSoundEnabled(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply()
  }

  companion object {
    private const val PREFS_NAME = "draw_to_survive_prefs"
    private const val KEY_BEST_LEVEL = "best_level"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
  }
}
