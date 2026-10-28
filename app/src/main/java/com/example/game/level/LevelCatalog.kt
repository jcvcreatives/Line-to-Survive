package com.example.game.level

import com.example.game.model.CalloutMoveType
import com.example.game.model.ChalkBit
import com.example.game.model.GuideStroke
import com.example.game.model.LevelConfig
import com.example.game.model.LevelDefinition
import com.example.game.model.Platform
import com.example.game.model.ScribblerEnemy
import com.example.game.model.ScriptedCallout
import com.example.game.model.ThumbtackCluster
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Complete 50-Level Progression & Game Mechanics Catalog for Draw to Survive.
 *
 * Implements `getLevelConfig(level)` for Levels 1..50 with the core scaling formula:
 * - Level Length (Distance to Finish Line): Starts at `800px` in Level 1, `+150px` per level (`8150px` at Level 50).
 * - Character Run Speed (`baseSpeed`): Starts at `3.0` in Level 1, scaling linearly up to `8.5` at Level 50.
 * - Gap Width Range (`minGap`, `maxGap`): Starts at `80px–140px` (Level 1) and scales up to `180px–380px` (Level 50).
 * - Platform Elevation Variance (`deltaY`): `0px` height difference in Levels 1–3, scaling up to `±160px` by Level 50.
 * - Max Ink Capacity & Regen Rate: Tightens relative to gap size from `1000` (`120/s`) in Level 1 down to `680` (`68/s`) in Level 50.
 * - Line Lifespan (Decay Timer): Starts at `5.0s` in Level 1, decreasing linearly down to `1.8s` by Level 50.
 */
object LevelCatalog {

  const val TOTAL_LEVELS = 50

  private val WORLD_THEMES = listOf(
    "World 1: Chalkboard Meadows",
    "World 2: Ember Sketch Caverns",
    "World 3: Neon Blueprint Peaks",
    "World 4: Molten Magma Foundry",
    "World 5: Inferno Citadel"
  )

  private val LEVEL_NAMES = listOf(
    // World 1 (1..10)
    "Homeroom Sketch", "Flatline Sprint", "Chalk Dust Dash", "Stepping Stones", "First Elevation",
    "Scribbler Ambush", "Bounce Pad Basics", "Thumbtack Alley", "Turbo Chalk Trial", "Meadow Boss Run",
    // World 2 (11..20)
    "Ember Cavern Entry", "Falling Stalactites", "Crumbling Bridges", "Double Gap Leap", "Low Gravity Vault",
    "Crimson Scribblers", "Razor Tack Ridge", "High Ledge Climb", "Gold Ink Express", "Cavern Guardian",
    // World 3 (21..30)
    "Blueprint Ascent", "Drafting Table Drop", "Steep Vector Ramp", "Architect's Gap", "Skyline Hurdles",
    "Shockwave Snip", "Pendulum Cliffs", "Wind Tunnel Sketch", "Vertigo Spire", "Blueprint Overlord",
    // World 4 (31..40)
    "Magma Foundry Gate", "Smelting Crucible", "Slag Pit Crossing", "Piston Ledges", "Superheated Ink",
    "Blazing Gauntlet", "Anvil Drop Canyon", "Molten Cascade", "Forge Master's Path", "Foundry Core",
    // World 5 (41..50)
    "Inferno Ramparts", "Obsidian Chasm", "Supersonic Sketch", " vanishing Chalk", "volcanic Updraft",
    "Demon Scribbler Pack", "Hellfire Spire", "Abyssal Leap", "Final Crucible", "Grandmaster of Survival"
  )

  /**
   * Dynamically calculates all scaling parameters for [level] (`1` to `50`).
   */
  fun getLevelConfig(level: Int): LevelConfig {
    val clampedLevel = level.coerceIn(1, TOTAL_LEVELS)
    val t = (clampedLevel - 1f) / (TOTAL_LEVELS - 1f) // 0.0 at L1 .. 1.0 at L50

    val worldIdx = (clampedLevel - 1) / 10
    val worldTitle = WORLD_THEMES[worldIdx.coerceIn(0, WORLD_THEMES.lastIndex)]
    val levelTitle = LEVEL_NAMES[(clampedLevel - 1).coerceIn(0, LEVEL_NAMES.lastIndex)]

    // 1. Level Length: 800px in Level 1, +150px per level
    val levelLength = 800f + (clampedLevel - 1) * 150f

    // 2. Character Run Speed (baseSpeed): 3.0 in Level 1 -> 8.5 in Level 50
    val baseSpeed = 3.0f + t * (8.5f - 3.0f)
    val runSpeed = 175f + t * (340f - 175f)

    // 3. Gap Width Range (minGap, maxGap): 80..140 in Level 1 -> 180..380 in Level 50
    val minGap = 80f + t * (180f - 80f)
    val maxGap = 140f + t * (380f - 140f)

    // 4. Platform Elevation Variance (deltaY): 0px in Levels 1..3 -> ±160px by Level 50
    val maxDeltaY = if (clampedLevel <= 3) {
      0f
    } else {
      ((clampedLevel - 3f) / 47f) * 160f
    }

    // 5. Max Ink Capacity & Regen Rate: tighter relative to gap size at higher levels
    val maxInkPerLevel = 1000f - t * 320f // 1000f at L1 -> 680f at L50
    val inkRechargeRate = 120f - t * 52f // 120f/s at L1 -> 68f/s at L50
    val parInkUsage = maxInkPerLevel * 0.92f
    val maxStrokeLength = 760f - t * 240f // Shorter, smarter ramps required

    // 6. Line Lifespan (Decay Timer): 5.0s in Level 1 -> 1.8s by Level 50
    val lineLifespan = 5.0f - t * (5.0f - 1.8f)

    val subtitle = when {
      clampedLevel <= 3 -> "Flat platforms (0px elevation). Draw sturdy bridges before your chalk fades (${String.format("%.1f", lineLifespan)}s)!"
      clampedLevel <= 10 -> "Platforms shift up to ±${maxDeltaY.toInt()}px! Use angled ramps and Bounce chalk."
      clampedLevel <= 25 -> "Speed ${String.format("%.1f", baseSpeed)}x • Lines decay in ${String.format("%.1f", lineLifespan)}s. Sketch short launch ramps!"
      clampedLevel <= 40 -> "Tight ink budget & wide ${minGap.toInt()}–${maxGap.toInt()}px gaps. Chain Chalk Bits & Turbo!"
      else -> "Master tier! ±${maxDeltaY.toInt()}px cliffs, ${String.format("%.1f", baseSpeed)}x speed, and ${String.format("%.1f", lineLifespan)}s line decay!"
    }

    return LevelConfig(
      level = clampedLevel,
      worldIndex = worldIdx + 1,
      worldTitle = worldTitle,
      levelTitle = levelTitle,
      levelSubtitle = subtitle,
      levelLength = levelLength,
      baseSpeed = baseSpeed,
      runSpeed = runSpeed,
      minGap = minGap,
      maxGap = maxGap,
      maxDeltaY = maxDeltaY,
      maxInkPerLevel = maxInkPerLevel,
      inkRechargeRate = inkRechargeRate,
      parInkUsage = parInkUsage,
      maxStrokeLength = maxStrokeLength,
      lineLifespan = lineLifespan
    )
  }

  fun getAllLevelConfigs(): List<LevelConfig> {
    return (1..TOTAL_LEVELS).map { getLevelConfig(it) }
  }

  fun getLevel(levelNumber: Int): LevelDefinition {
    val clamped = levelNumber.coerceIn(1, TOTAL_LEVELS)
    return if (clamped == 1) {
      createLevel1()
    } else {
      createScaledLevel(clamped)
    }
  }

  /**
   * Level 1 (Homeroom Sketch) built with `getLevelConfig(1)` parameters:
   * - Flat platforms (`deltaY = 0px`, `topY = 420f`)
   * - Gap widths within `80px–140px` (`140px` each)
   * - `baseSpeed = 3.0f` (`175f` world speed), `lineLifespan = 5.0s`
   */
  private fun createLevel1(): LevelDefinition {
    val cfg = getLevelConfig(1)
    val groundY = WorldConstants.DEFAULT_GROUND_Y
    return LevelDefinition(
      levelNumber = 1,
      title = cfg.levelTitle,
      subtitle = cfg.levelSubtitle,
      runSpeed = cfg.runSpeed,
      spawnPosition = Vec2(x = 110f, y = groundY),
      finishLineX = 1560f,
      levelEndX = 1880f,
      maxInkPerLevel = cfg.maxInkPerLevel,
      inkRechargeRate = cfg.inkRechargeRate,
      parInkUsage = cfg.parInkUsage,
      maxStrokeLength = cfg.maxStrokeLength,
      lineLifespan = cfg.lineLifespan,
      baseSpeed = cfg.baseSpeed,
      levelLength = cfg.levelLength,
      minGap = cfg.minGap,
      maxGap = cfg.maxGap,
      maxDeltaY = cfg.maxDeltaY,
      worldTitle = cfg.worldTitle,
      platforms = listOf(
        Platform(
          startX = -80f,
          endX = 560f,
          topY = groundY,
          label = "START"
        ),
        Platform(
          startX = 700f,
          endX = 1280f,
          topY = groundY,
          label = "CHECKPOINT"
        ),
        Platform(
          startX = 1420f,
          endX = 2020f,
          topY = groundY,
          label = "FINISH"
        )
      ),
      guideStrokes = listOf(
        GuideStroke(
          points = listOf(
            Vec2(545f, groundY - 4f),
            Vec2(630f, groundY - 20f),
            Vec2(715f, groundY - 4f)
          ),
          caption = "DRAG HERE TO BRIDGE GAP 1"
        ),
        GuideStroke(
          points = listOf(
            Vec2(1265f, groundY - 4f),
            Vec2(1350f, groundY - 22f),
            Vec2(1435f, groundY - 4f)
          ),
          caption = "BRIDGE GAP 2 TO FINISH"
        )
      ),
      chalkBits = listOf(
        ChalkBit(id = 1, x = 360f, y = groundY - 34f),
        ChalkBit(id = 2, x = 605f, y = groundY - 42f),
        ChalkBit(id = 3, x = 655f, y = groundY - 42f),
        ChalkBit(id = 4, x = 990f, y = groundY - 36f),
        ChalkBit(id = 5, x = 1320f, y = groundY - 46f),
        ChalkBit(id = 6, x = 1350f, y = groundY - 72f, isStar = true, inkAmount = 220f),
        ChalkBit(id = 7, x = 1385f, y = groundY - 46f)
      ),
      calloutScript = listOf(
        ScriptedCallout(triggerX = 730f, type = CalloutMoveType.BLITZ)
      )
    )
  }

  /**
   * Generates Levels 2..50 deterministically using `getLevelConfig(levelNumber)`.
   */
  private fun createScaledLevel(levelNumber: Int): LevelDefinition {
    val cfg = getLevelConfig(levelNumber)
    val platforms = mutableListOf<Platform>()
    val bits = mutableListOf<ChalkBit>()
    val thumbtacks = mutableListOf<ThumbtackCluster>()
    val scribblers = mutableListOf<ScribblerEnemy>()
    val callouts = mutableListOf<ScriptedCallout>()
    val guides = mutableListOf<GuideStroke>()

    val spawnX = 110f
    var currentY = WorldConstants.DEFAULT_GROUND_Y
    val startPlatformEnd = 460f
    platforms.add(
      Platform(
        startX = -80f,
        endX = startPlatformEnd,
        topY = currentY,
        label = "START"
      )
    )

    var cursorX = startPlatformEnd
    val targetFinishX = startPlatformEnd + cfg.levelLength
    var segmentIndex = 0

    while (cursorX < targetFinishX - cfg.maxGap - 180f) {
      // Deterministic gap width in [cfg.minGap, cfg.maxGap]
      val hashRatio = ((levelNumber * 37 + segmentIndex * 73) % 101) / 100f
      val gapWidth = cfg.minGap + (cfg.maxGap - cfg.minGap) * hashRatio
      val prevY = currentY

      // Elevation variance deltaY: 0px for Levels 1..3, scaling up to ±160px by Level 50
      val nextY = if (cfg.maxDeltaY <= 0.5f) {
        WorldConstants.DEFAULT_GROUND_Y
      } else {
        val wave = sin((levelNumber * 1.7f + segmentIndex * 2.3f).toDouble()).toFloat()
        val rawDelta = wave * cfg.maxDeltaY
        (currentY + rawDelta).coerceIn(210f, 455f)
      }

      val midGapX = cursorX + gapWidth * 0.5f
      val arcPeakY = min(prevY, nextY) - 48f

      // Floating Chalk Bits & Gold Star over the gap
      bits.add(
        ChalkBit(
          id = levelNumber * 100 + segmentIndex * 2,
          x = midGapX - gapWidth * 0.18f,
          y = arcPeakY.coerceAtLeast(150f),
          inkAmount = 130f
        )
      )
      bits.add(
        ChalkBit(
          id = levelNumber * 100 + segmentIndex * 2 + 1,
          x = midGapX + gapWidth * 0.18f,
          y = (arcPeakY - 18f).coerceAtLeast(140f),
          inkAmount = if (segmentIndex % 3 == 1) 220f else 130f,
          isStar = (segmentIndex % 3 == 1)
        )
      )

      // Guide hint on Levels 2..3
      if (levelNumber <= 3 && segmentIndex == 0) {
        guides.add(
          GuideStroke(
            points = listOf(
              Vec2(cursorX - 15f, prevY - 4f),
              Vec2(midGapX, min(prevY, nextY) - 24f),
              Vec2(cursorX + gapWidth + 15f, nextY - 4f)
            ),
            caption = "DRAW SHORT RAMP ACROSS GAP"
          )
        )
      }

      // Scripted Callout Move before select gaps
      if (segmentIndex % 2 == 1) {
        val moveType = CalloutMoveType.entries[(levelNumber + segmentIndex) % CalloutMoveType.entries.size]
        callouts.add(
          ScriptedCallout(
            triggerX = (cursorX - 85f).coerceAtLeast(spawnX + 120f),
            type = moveType
          )
        )
      }

      cursorX += gapWidth
      currentY = nextY

      val remainingToTarget = (targetFinishX - cursorX).coerceAtLeast(220f)
      val platWidth = min(
        remainingToTarget,
        240f + ((levelNumber * 19 + segmentIndex * 61) % 150)
      )

      val platLabel = if (segmentIndex == 1) "CHECKPOINT" else null
      platforms.add(
        Platform(
          startX = cursorX,
          endX = cursorX + platWidth,
          topY = currentY,
          label = platLabel
        )
      )

      // Add Red Scribblers & Thumbtack hazards on wider platforms from Level 2+
      if (levelNumber >= 2 && platWidth >= 250f && segmentIndex % 2 == 0) {
        scribblers.add(
          ScribblerEnemy(
            id = levelNumber * 1000 + segmentIndex,
            x = cursorX + platWidth * 0.72f,
            y = currentY,
            speed = 110f + min(95f, levelNumber * 1.8f)
          )
        )
      } else if (levelNumber >= 4 && platWidth >= 270f && segmentIndex % 2 == 1) {
        thumbtacks.add(
          ThumbtackCluster(
            x = cursorX + platWidth * 0.52f,
            y = currentY - 2f,
            count = if (levelNumber >= 25) 4 else 3
          )
        )
      }

      cursorX += platWidth
      segmentIndex++
    }

    // Final gap before Finish Platform
    val finalGapRatio = ((levelNumber * 53) % 101) / 100f
    val finalGap = cfg.minGap + (cfg.maxGap - cfg.minGap) * finalGapRatio
    val finishTopY = if (cfg.maxDeltaY <= 0.5f) {
      WorldConstants.DEFAULT_GROUND_Y
    } else {
      val finalDelta = sin((levelNumber * 3.1).toDouble()).toFloat() * (cfg.maxDeltaY * 0.65f)
      (currentY + finalDelta).coerceIn(240f, 440f)
    }

    bits.add(
      ChalkBit(
        id = levelNumber * 100 + 99,
        x = cursorX + finalGap * 0.5f,
        y = (min(currentY, finishTopY) - 56f).coerceAtLeast(150f),
        inkAmount = 240f,
        isStar = true
      )
    )

    cursorX += finalGap
    val finishX = max(cursorX + 160f, startPlatformEnd + cfg.levelLength)
    val finishPlatformEnd = finishX + 360f

    platforms.add(
      Platform(
        startX = cursorX,
        endX = finishPlatformEnd,
        topY = finishTopY,
        label = "FINISH"
      )
    )

    return LevelDefinition(
      levelNumber = levelNumber,
      title = cfg.levelTitle,
      subtitle = cfg.levelSubtitle,
      runSpeed = cfg.runSpeed,
      spawnPosition = Vec2(x = spawnX, y = WorldConstants.DEFAULT_GROUND_Y),
      finishLineX = finishX,
      levelEndX = finishPlatformEnd - 80f,
      maxInkPerLevel = cfg.maxInkPerLevel,
      inkRechargeRate = cfg.inkRechargeRate,
      parInkUsage = cfg.parInkUsage,
      maxStrokeLength = cfg.maxStrokeLength,
      lineLifespan = cfg.lineLifespan,
      baseSpeed = cfg.baseSpeed,
      levelLength = cfg.levelLength,
      minGap = cfg.minGap,
      maxGap = cfg.maxGap,
      maxDeltaY = cfg.maxDeltaY,
      worldTitle = cfg.worldTitle,
      platforms = platforms,
      guideStrokes = guides,
      chalkBits = bits,
      thumbtacks = thumbtacks,
      scribblers = scribblers,
      calloutScript = callouts
    )
  }
}
