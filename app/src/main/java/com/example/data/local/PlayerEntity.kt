package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity persisting the player's Alchemist's Bazaar currency, equipped cosmetic runner skin,
 * equipped ink trail, equipped utility perk, and comma-separated unlocked items.
 */
@Entity(tableName = "player_profile")
data class PlayerEntity(
  @PrimaryKey val id: Int = 0,
  val currency: Int = 500,
  val equippedSkin: String = "DEFAULT",
  val equippedTrail: String = "STANDARD_INK",
  val equippedPerk: String = "",
  val unlockedSkinsCsv: String = "DEFAULT",
  val unlockedTrailsCsv: String = "STANDARD_INK",
  val unlockedPerksCsv: String = ""
) {
  val unlockedSkins: Set<String>
    get() = unlockedSkinsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

  val unlockedTrails: Set<String>
    get() = unlockedTrailsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

  val unlockedPerks: Set<String>
    get() = unlockedPerksCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
