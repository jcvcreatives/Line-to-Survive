package com.example.game.level

import com.example.game.model.CalloutMoveType
import com.example.game.model.ChalkBit
import com.example.game.model.DynamicHazard
import com.example.game.model.GuideStroke
import com.example.game.model.HazardType
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
    val parInkUsage = maxInkPerLevel * 1.6f
    val maxStrokeLength = max(1800f, levelLength * 1.25f) // Allow continuous line drawing through chalk pickups

    // 6. Line Lifespan (Decay Timer): 5.0s in Level 1 -> 1.8s by Level 50
    val lineLifespan = 5.0f - t * (5.0f - 1.8f)

    val subtitle = when {
      clampedLevel <= 3 -> "No ground platforms! Draw your line to the floating chalk (+CHALK) to reach the Finish!"
      clampedLevel <= 10 -> "Chalk pickups shift up to ±${maxDeltaY.toInt()}px! Draw lines through floating chalk for extra ink."
      clampedLevel <= 25 -> "Speed ${String.format("%.1f", baseSpeed)}x • Lines decay in ${String.format("%.1f", lineLifespan)}s. Connect chalk in mid-air!"
      clampedLevel <= 40 -> "Wide ${minGap.toInt()}–${maxGap.toInt()}px chalk spacing over open lava. Chain every floating chalk!"
      else -> "Master tier! ±${maxDeltaY.toInt()}px sky waves, ${String.format("%.1f", baseSpeed)}x speed, and ${String.format("%.1f", lineLifespan)}s line decay!"
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

  /**
   * Dynamically generates obstacles (`SWINGING_PENDULUM` and `VANISHING_PLATFORM`) for [level] (`1..50`),
   * scaling both their **frequency** (spacing and total count) and **difficulty** (pendulum angular speed,
   * swing arc, bob radius, and vanishing platform cycle speed & solid duration) as levels progress:
   * - Level 1: 0 dynamic hazards (introductory tutorial level).
   * - Levels 2..50:
   *   - Frequency: Hazard spacing tightens from `680px` at Level 2 down to `310px` at Level 50,
   *     scaling total hazard count from `1` obstacle in Level 2 up to `24+` obstacles in Level 50.
   *   - Pendulum Difficulty: Angular speed scales from `1.85 rad/s` to `4.35 rad/s`, max swing angle
   *     from `0.52 rad` to `1.02 rad`, and bob radius from `18px` to `26px`.
   *   - Vanishing Platform Difficulty: Width shrinks from `150px` to `92px`, cycle time speeds up from
   *     `4.2s` to `1.9s`, and visible solid ratio tightens from `72%` (`0.72f`) down to `42%` (`0.42f`).
   */
  fun getHazards(level: Int, config: LevelConfig = getLevelConfig(level)): List<DynamicHazard> {
    val clampedLevel = level.coerceIn(1, TOTAL_LEVELS)
    if (clampedLevel <= 1) return emptyList()

    val t = (clampedLevel - 1f) / (TOTAL_LEVELS - 1f) // ~0.02 at L2 .. 1.0 at L50
    val startPlatformEnd = 520f
    val finishX = startPlatformEnd + config.levelLength

    // Frequency scaling: spacing tightens from 680px (L2) down to 310px (L50)
    val baseHazardSpacing = 680f - t * 370f

    // Difficulty scaling for Swinging Pendulums
    val pendulumAngularSpeed = 1.85f + t * 2.50f       // 1.85 rad/s -> 4.35 rad/s
    val pendulumMaxAngle = 0.52f + t * 0.50f           // 0.52 rad (~30°) -> 1.02 rad (~58°)
    val pendulumBobRadius = 18f + t * 8f               // 18px -> 26px

    // Difficulty scaling for Vanishing Platforms
    val platformWidth = 150f - t * 58f                 // 150px -> 92px
    val platformCycleSeconds = 4.2f - t * 2.3f         // 4.2s -> 1.9s
    val platformVisibleRatio = 0.72f - t * 0.30f       // 0.72 -> 0.42

    val hazards = mutableListOf<DynamicHazard>()
    var cursorX = startPlatformEnd + 240f
    var hazardIndex = 0

    while (cursorX < finishX - 180f) {
      // Choose obstacle type deterministically:
      // Levels 2..3 introduce Vanishing Platforms first; Level 4+ interleave Swinging Pendulums & Vanishing Platforms
      val isPendulum = when {
        clampedLevel <= 3 -> false
        clampedLevel <= 6 -> (hazardIndex % 2 == 1)
        else -> ((clampedLevel + hazardIndex) % 2 == 0) || (clampedLevel >= 25 && hazardIndex % 3 == 0)
      }

      val elevationShift = if (config.maxDeltaY <= 0.5f) {
        0f
      } else {
        sin((clampedLevel * 1.1f + hazardIndex * 1.45f).toDouble()).toFloat() * (config.maxDeltaY * 0.65f)
      }

      val phaseOffset = ((clampedLevel * 0.65f) + (hazardIndex * 1.15f)) % 6.28f

      if (isPendulum) {
        val armLen = (160f + (hazardIndex % 3) * 14f).coerceIn(150f, 195f)
        val pivotY = (WorldConstants.DEFAULT_GROUND_Y - armLen - 78f + elevationShift * 0.55f)
          .coerceIn(70f, 210f)

        hazards.add(
          DynamicHazard(
            id = clampedLevel * 1000 + hazardIndex,
            type = HazardType.SWINGING_PENDULUM,
            x = cursorX,
            y = pivotY,
            armLength = armLen,
            bobRadius = pendulumBobRadius,
            maxAngleRadians = pendulumMaxAngle,
            angularSpeed = pendulumAngularSpeed,
            phaseOffset = phaseOffset
          )
        )
      } else {
        val topY = (WorldConstants.DEFAULT_GROUND_Y - 12f + elevationShift)
          .coerceIn(195f, 455f)
        val halfW = platformWidth * 0.5f

        hazards.add(
          DynamicHazard(
            id = clampedLevel * 1000 + hazardIndex,
            type = HazardType.VANISHING_PLATFORM,
            x = cursorX,
            y = topY,
            startX = cursorX - halfW,
            endX = cursorX + halfW,
            topY = topY,
            cycleSeconds = platformCycleSeconds,
            visibleRatio = platformVisibleRatio,
            phaseOffset = phaseOffset
          )
        )
      }

      val jitterRatio = (((clampedLevel * 19 + hazardIndex * 43) % 41) - 20) / 100f // -0.20 .. +0.20
      val stepX = (baseHazardSpacing * (1f + jitterRatio * 0.35f)).coerceAtLeast(250f)
      cursorX += stepX
      hazardIndex++
    }

    return hazards
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
   * Level 1 (Homeroom Sketch):
   * - No intermediate platforms! Only the initial starter chalk foothold (`20f..520f`) and Finish gate (`1320f`).
   * - Player draws their line to the floating Chalk pickups across the open lava sea for additional chalk level.
   */
  private fun createLevel1(): LevelDefinition {
    val cfg = getLevelConfig(1)
    val groundY = WorldConstants.DEFAULT_GROUND_Y
    val startEndX = 520f
    val finishX = startEndX + cfg.levelLength // 1320f
    return LevelDefinition(
      levelNumber = 1,
      title = cfg.levelTitle,
      subtitle = cfg.levelSubtitle,
      runSpeed = cfg.runSpeed,
      spawnPosition = Vec2(x = 110f, y = groundY),
      finishLineX = finishX,
      levelEndX = finishX + 260f,
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
          startX = 20f,
          endX = startEndX,
          topY = groundY,
          label = "START"
        ),
        Platform(
          startX = finishX - 30f,
          endX = finishX + 260f,
          topY = groundY,
          label = "FINISH"
        )
      ),
      guideStrokes = listOf(
        GuideStroke(
          points = listOf(
            Vec2(500f, groundY - 4f),
            Vec2(640f, groundY - 28f),
            Vec2(780f, groundY - 28f),
            Vec2(920f, groundY - 42f),
            Vec2(1060f, groundY - 28f),
            Vec2(1200f, groundY - 24f),
            Vec2(finishX + 20f, groundY - 4f)
          ),
          caption = "DRAW LINE TO CHALK FOR +CHALK!"
        )
      ),
      chalkBits = listOf(
        ChalkBit(id = 1, x = 620f, y = groundY - 28f, inkAmount = 240f),
        ChalkBit(id = 2, x = 740f, y = groundY - 28f, inkAmount = 240f),
        ChalkBit(id = 3, x = 860f, y = groundY - 36f, inkAmount = 240f),
        ChalkBit(id = 4, x = 980f, y = groundY - 42f, isStar = true, inkAmount = 340f),
        ChalkBit(id = 5, x = 1100f, y = groundY - 32f, inkAmount = 240f),
        ChalkBit(id = 6, x = 1220f, y = groundY - 26f, inkAmount = 240f)
      ),
      hazards = getHazards(1, cfg),
      calloutScript = listOf(
        ScriptedCallout(triggerX = 760f, type = CalloutMoveType.BLITZ)
      )
    )
  }

  /**
   * Generates Levels 2..50 deterministically using `getLevelConfig(levelNumber)` and `getHazards(levelNumber)`:
   * - No intermediate platforms where the character runs — open sky over lava from Start foothold to Finish line!
   * - Floating Chalk pickups (`ChalkBit`) are spaced by `[minGap, maxGap]` and vary vertically by `±maxDeltaY`
   *   so the player draws their line from chalk to chalk for additional chalk level.
   * - Dynamic obstacles (`getHazards`) add Swinging Pendulums and Vanishing Platforms that scale in frequency
   *   and difficulty across all 50 levels.
   */
  private fun createScaledLevel(levelNumber: Int): LevelDefinition {
    val cfg = getLevelConfig(levelNumber)
    val bits = mutableListOf<ChalkBit>()
    val callouts = mutableListOf<ScriptedCallout>()
    val guides = mutableListOf<GuideStroke>()

    val spawnX = 110f
    var currentY = WorldConstants.DEFAULT_GROUND_Y
    val startPlatformEnd = 520f
    val finishX = startPlatformEnd + cfg.levelLength
    val finishPlatformEnd = finishX + 260f

    var cursorX = startPlatformEnd
    var segmentIndex = 0
    val guidePts = mutableListOf(Vec2(startPlatformEnd - 20f, currentY - 4f))

    while (cursorX < finishX - cfg.minGap * 0.6f) {
      // Deterministic spacing between floating chalk refills in [cfg.minGap, cfg.maxGap]
      val hashRatio = ((levelNumber * 37 + segmentIndex * 73) % 101) / 100f
      val stepX = cfg.minGap + (cfg.maxGap - cfg.minGap) * hashRatio
      cursorX += stepX
      if (cursorX >= finishX - 50f) break

      // Elevation variance deltaY: 0px for Levels 1..3, scaling up to ±160px by Level 50
      val targetY = if (cfg.maxDeltaY <= 0.5f) {
        WorldConstants.DEFAULT_GROUND_Y - 28f
      } else {
        val wave = sin((levelNumber * 1.3f + segmentIndex * 1.15f).toDouble()).toFloat()
        val rawDelta = wave * cfg.maxDeltaY
        (WorldConstants.DEFAULT_GROUND_Y - 30f + rawDelta).coerceIn(160f, 445f)
      }
      currentY = targetY

      val isGoldStar = (segmentIndex % 4 == 2)
      bits.add(
        ChalkBit(
          id = levelNumber * 100 + segmentIndex,
          x = cursorX,
          y = currentY,
          inkAmount = if (isGoldStar) 340f else 240f,
          isStar = isGoldStar
        )
      )

      if (levelNumber <= 3 && guidePts.size < 6) {
        guidePts.add(Vec2(cursorX, currentY))
      }

      if (segmentIndex > 0 && segmentIndex % 5 == 0) {
        val moveType = CalloutMoveType.entries[(levelNumber + segmentIndex) % CalloutMoveType.entries.size]
        callouts.add(
          ScriptedCallout(
            triggerX = (cursorX - 90f).coerceAtLeast(spawnX + 180f),
            type = moveType
          )
        )
      }

      segmentIndex++
    }

    val finishTopY = if (cfg.maxDeltaY <= 0.5f) {
      WorldConstants.DEFAULT_GROUND_Y
    } else {
      val finalDelta = sin((levelNumber * 2.7).toDouble()).toFloat() * (cfg.maxDeltaY * 0.55f)
      (WorldConstants.DEFAULT_GROUND_Y + finalDelta).coerceIn(230f, 440f)
    }

    if (levelNumber <= 3 && guidePts.size >= 2) {
      guidePts.add(Vec2(finishX + 15f, finishTopY - 4f))
      guides.add(
        GuideStroke(
          points = guidePts,
          caption = "DRAW LINE TO CHALK FOR +CHALK!"
        )
      )
    }

    val platforms = listOf(
      Platform(
        startX = 20f,
        endX = startPlatformEnd,
        topY = WorldConstants.DEFAULT_GROUND_Y,
        label = "START"
      ),
      Platform(
        startX = finishX - 30f,
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
      levelEndX = finishPlatformEnd - 40f,
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
      thumbtacks = emptyList(),
      scribblers = emptyList(),
      hazards = getHazards(levelNumber, cfg),
      calloutScript = callouts
    )
  }
}
