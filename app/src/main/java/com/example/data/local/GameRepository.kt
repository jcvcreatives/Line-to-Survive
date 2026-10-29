package com.example.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

/**
 * Repository coordinating Room persistence for PlayerEntity (currency, skins, trails, perks)
 * and DailyChallengeEntity (UTC date-seeded challenge modifiers).
 */
class GameRepository(
  private val gameDao: GameDao? = null
) {
  private val fallbackPlayerFlow = MutableStateFlow(PlayerEntity())
  private val fallbackChallengeFlow = MutableStateFlow(createDefaultDailyChallenge(currentUtcDateKey()))

  fun currentUtcDateKey(): String {
    return try {
      LocalDate.now(ZoneOffset.UTC).toString()
    } catch (_: Throwable) {
      "2026-10-29"
    }
  }

  fun createDefaultDailyChallenge(dateKey: String): DailyChallengeEntity {
    val seed = abs(dateKey.hashCode().toLong()) * 1103515245L + 12345L
    val modifiers = listOf("Glass Cannon", "Zero Gravity Ink", "Turbo Ignis", "Thick Fog")
    val modifierName = modifiers[(abs(dateKey.hashCode()) % modifiers.size)]
    return DailyChallengeEntity(
      dateKey = dateKey,
      seed = seed,
      modifierName = modifierName,
      completed = false
    )
  }

  fun observePlayerProfile(): Flow<PlayerEntity?> {
    return gameDao?.observePlayerProfile() ?: fallbackPlayerFlow.asStateFlow()
  }

  fun observeTodayChallenge(): Flow<DailyChallengeEntity?> {
    val today = currentUtcDateKey()
    return gameDao?.observeDailyChallenge(today) ?: fallbackChallengeFlow.asStateFlow()
  }

  suspend fun ensureInitialized(): Pair<PlayerEntity, DailyChallengeEntity> {
    val today = currentUtcDateKey()
    val dao = gameDao
    if (dao == null) {
      return fallbackPlayerFlow.value to fallbackChallengeFlow.value
    }
    var player = dao.getPlayerProfile()
    if (player == null) {
      player = PlayerEntity()
      dao.upsertPlayerProfile(player)
    }
    var challenge = dao.getDailyChallenge(today)
    if (challenge == null) {
      challenge = createDefaultDailyChallenge(today)
      dao.upsertDailyChallenge(challenge)
    }
    fallbackPlayerFlow.value = player
    fallbackChallengeFlow.value = challenge
    return player to challenge
  }

  suspend fun addCurrency(delta: Int): PlayerEntity {
    val current = gameDao?.getPlayerProfile() ?: fallbackPlayerFlow.value
    val updated = current.copy(currency = (current.currency + delta).coerceAtLeast(0))
    gameDao?.upsertPlayerProfile(updated)
    fallbackPlayerFlow.value = updated
    return updated
  }

  suspend fun markDailyChallengeCompleted(): Pair<PlayerEntity, DailyChallengeEntity> {
    val today = currentUtcDateKey()
    val currentChallenge = gameDao?.getDailyChallenge(today) ?: fallbackChallengeFlow.value
    val updatedChallenge = currentChallenge.copy(completed = true)
    gameDao?.upsertDailyChallenge(updatedChallenge)
    fallbackChallengeFlow.value = updatedChallenge
    val updatedPlayer = if (!currentChallenge.completed) {
      addCurrency(1000)
    } else {
      gameDao?.getPlayerProfile() ?: fallbackPlayerFlow.value
    }
    return updatedPlayer to updatedChallenge
  }

  suspend fun purchaseOrEquipSkin(skinId: String, cost: Int): PlayerEntity {
    val current = gameDao?.getPlayerProfile() ?: fallbackPlayerFlow.value
    val unlocked = current.unlockedSkins.toMutableSet()
    val updated = if (skinId in unlocked) {
      current.copy(equippedSkin = skinId)
    } else if (current.currency >= cost) {
      unlocked.add(skinId)
      current.copy(
        currency = current.currency - cost,
        equippedSkin = skinId,
        unlockedSkinsCsv = unlocked.joinToString(",")
      )
    } else {
      current
    }
    gameDao?.upsertPlayerProfile(updated)
    fallbackPlayerFlow.value = updated
    return updated
  }

  suspend fun purchaseOrEquipTrail(trailId: String, cost: Int): PlayerEntity {
    val current = gameDao?.getPlayerProfile() ?: fallbackPlayerFlow.value
    val unlocked = current.unlockedTrails.toMutableSet()
    val updated = if (trailId in unlocked) {
      current.copy(equippedTrail = trailId)
    } else if (current.currency >= cost) {
      unlocked.add(trailId)
      current.copy(
        currency = current.currency - cost,
        equippedTrail = trailId,
        unlockedTrailsCsv = unlocked.joinToString(",")
      )
    } else {
      current
    }
    gameDao?.upsertPlayerProfile(updated)
    fallbackPlayerFlow.value = updated
    return updated
  }

  suspend fun purchaseOrEquipPerk(perkId: String, cost: Int): PlayerEntity {
    val current = gameDao?.getPlayerProfile() ?: fallbackPlayerFlow.value
    val unlocked = current.unlockedPerks.toMutableSet()
    val updated = if (perkId in unlocked) {
      current.copy(equippedPerk = if (current.equippedPerk == perkId) "" else perkId)
    } else if (current.currency >= cost) {
      unlocked.add(perkId)
      current.copy(
        currency = current.currency - cost,
        equippedPerk = perkId,
        unlockedPerksCsv = unlocked.joinToString(",")
      )
    } else {
      current
    }
    gameDao?.upsertPlayerProfile(updated)
    fallbackPlayerFlow.value = updated
    return updated
  }
}
