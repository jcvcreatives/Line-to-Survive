package com.example.game.model

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 2D point or vector in logical world coordinates.
 * The logical world height is fixed at [WorldConstants.WORLD_HEIGHT] (600f),
 * while the visible world width scales dynamically with the device's aspect ratio.
 */
data class Vec2(
  val x: Float,
  val y: Float
) {
  fun distanceTo(other: Vec2): Float = hypot(other.x - x, other.y - y)
}

object WorldConstants {
  const val WORLD_HEIGHT = 600f
  const val LAVA_SURFACE_Y = 532f
  const val DEFAULT_GROUND_Y = 325f

  const val STICKMAN_HEIGHT = 58f
  const val STICKMAN_FOOT_RADIUS = 14f
  const val STICKMAN_HEAD_RADIUS = 10f

  const val GRAVITY = 960f
  const val MAX_FALL_SPEED = 680f

  // Camera keeps the stickman around 28% from the left edge of the screen
  const val CAMERA_LEAD_RATIO = 0.28f

  // Maximum length of a single drawn line per finger drag attempt
  const val MAX_STROKE_LENGTH = 760f
  const val MIN_POINT_DISTANCE = 10f

  // Default lifetime for fading chalk lines before they crumble
  const val DEFAULT_LINE_LIFETIME = 5.5f
}

/**
 * Pure Line-Drawing & Rune Classification Engine output types:
 * 1. [STANDARD_INK]: Straight bridge (`straightnessRatio > 0.88` and `directionFlips < 2`).
 * 2. [STAR_SPARK]: Closed circle on runner (`isClosedLoop` near player) -> +25% Ink & 5.0s Super Aura Sonic Dash.
 * 3. [ZIGZAG_COIL]: Zig-zag / W-shape (`directionFlips >= 2`) -> Neon Cyan trampoline (`1.8x` bounce) & +15% Ink.
 * 4. [RAMP_SLOPE]: Curved ramp / loop ahead -> Neon Green turbo conveyor (`1.9x` speed) & +12% Ink.
 */
enum class RuneType(
  val badgeLabel: String,
  val description: String
) {
  STANDARD_INK("BRIDGE", "Solid White/Cyan Ink Bridge"),
  STAR_SPARK("STAR SPARK", "Gold Super Aura Dash (+25% Ink)"),
  ZIGZAG_COIL("BOUNCE COIL", "Neon Cyan Trampoline (+15% Ink)"),
  RAMP_SLOPE("TURBO RAMP", "Neon Green Conveyor (+12% Ink)")
}

data class RuneClassification(
  val runeType: RuneType,
  val effectiveTool: ChalkTool,
  val inkRefundRatio: Float = 0f,
  val triggersSuperAura: Boolean = false,
  val triggersImmediateBounce: Boolean = false
)

/**
 * Breakable stone pillar hazard that blocks/kills normal runners,
 * but is smashed apart by Super Aura or Turbo runners for +25 Score and flying rock debris.
 */
data class BreakableRockWall(
  val id: Int,
  val x: Float,
  val topY: Float,
  val bottomY: Float,
  val width: Float = 30f,
  val isSmashed: Boolean = false
)

/**
 * Spiky magma imp that leaps vertically out of lava gaps.
 * Normal contact triggers lethal hazard/knockback, while Super Aura blasts the monster for +35 Score & +15% Ink.
 */
data class LeapingLavaMonster(
  val id: Int,
  val x: Float,
  val baseY: Float = WorldConstants.LAVA_SURFACE_Y,
  val peakY: Float = 250f,
  val cycleSeconds: Float = 2.6f,
  val phaseOffset: Float = 0f,
  val radius: Float = 18f,
  val isDefeated: Boolean = false
) {
  fun currentYAt(timeSeconds: Float): Float {
    val safeCycle = cycleSeconds.coerceAtLeast(1.0f)
    val prog = (((timeSeconds + phaseOffset) % safeCycle).let { if (it < 0f) it + safeCycle else it }) / safeCycle
    if (prog < 0.35f) return baseY + 12f
    val leapProg = (prog - 0.35f) / 0.65f
    val arc = sin(leapProg * Math.PI.toFloat()).coerceIn(0f, 1f)
    return baseY - (baseY - peakY) * arc
  }
}

/**
 * Colossal Lava Dragon Boss (Ignis Titan) chasing from the left with decoupled velocity and i-frame recoil.
 */
data class IgnisTitanBoss(
  val x: Float = -240f,
  val y: Float = WorldConstants.DEFAULT_GROUND_Y - 36f,
  val hp: Int = 5,
  val maxHp: Int = 5,
  val hurtCooldown: Float = 0f,
  val biteCooldown: Float = 0f,
  val knockbackVx: Float = 0f,
  val isDefeated: Boolean = false
) {
  val hpRatio: Float
    get() = if (maxHp > 0) (hp.toFloat() / maxHp).coerceIn(0f, 1f) else 0f
}

/**
 * Alchemist's Bazaar Shop Item definitions (Skins, Trails, and Perks).
 */
enum class ShopCategory {
  SKIN,
  TRAIL,
  PERK
}

data class ShopItem(
  val id: String,
  val name: String,
  val description: String,
  val cost: Int,
  val category: ShopCategory
)

object ShopCatalog {
  val SKINS = listOf(
    ShopItem("DEFAULT", "Crimson Striker", "Classic fighting stickman with spiky crimson hair", 0, ShopCategory.SKIN),
    ShopItem("CYBER_NINJA", "Cyber-Ninja", "Neon cyan visor & plasma scarf", 350, ShopCategory.SKIN),
    ShopItem("MAGMA_STRIKER", "Magma Striker", "Molten obsidian core & ember trail", 500, ShopCategory.SKIN),
    ShopItem("GOLDEN_TITAN", "Golden Titan God", "Radiant sovereign gold aura", 800, ShopCategory.SKIN)
  )

  val TRAILS = listOf(
    ShopItem("STANDARD_INK", "Standard Chalk", "Crisp sketchbook white & cyan ink", 0, ShopCategory.TRAIL),
    ShopItem("RAINBOW_PRISM", "Rainbow Prism", "Iridescent prismatic bridge strokes", 300, ShopCategory.TRAIL),
    ShopItem("SAKURA_BLOSSOM", "Sakura Blossom", "Vivid cherry-blossom pink ink", 400, ShopCategory.TRAIL),
    ShopItem("LIGHTNING_SPARK", "Lightning Spark", "High-voltage electric gold & cyan", 600, ShopCategory.TRAIL)
  )

  val PERKS = listOf(
    ShopItem("INK_MAGNET", "Ink Magnet", "+35% magnetic pull radius on orbs & chalk", 300, ShopCategory.PERK),
    ShopItem("REFINED_CATALYST", "Refined Catalyst", "-18% Magic Ink drain when drawing", 450, ShopCategory.PERK),
    ShopItem("TITAN_SHIELD", "Titan's Shield", "Absorbs 1 lethal obstacle hit per run", 650, ShopCategory.PERK)
  )
}

/**
 * PART 1: Scalable Difficulty Modes (`DIFFICULTY_MODES` config object).
 */
enum class DifficultyMode(
  val id: String,
  val displayName: String,
  val speedMult: Float,
  val inkDrainMult: Float,
  val hazardSpeedMult: Float,
  val incomingFreq: Float
) {
  EASY(
    id = "EASY",
    displayName = "Easy",
    speedMult = 0.8f,
    inkDrainMult = 0.7f,
    hazardSpeedMult = 0.8f,
    incomingFreq = 0.6f
  ),
  NORMAL(
    id = "NORMAL",
    displayName = "Normal",
    speedMult = 1.0f,
    inkDrainMult = 1.0f,
    hazardSpeedMult = 1.0f,
    incomingFreq = 1.0f
  ),
  HARD(
    id = "HARD",
    displayName = "Hard",
    speedMult = 1.25f,
    inkDrainMult = 1.3f,
    hazardSpeedMult = 1.3f,
    incomingFreq = 1.4f
  ),
  NIGHTMARE(
    id = "NIGHTMARE",
    displayName = "Nightmare",
    speedMult = 1.5f,
    inkDrainMult = 1.6f,
    hazardSpeedMult = 1.6f,
    incomingFreq = 1.8f
  );

  companion object {
    val DIFFICULTY_MODES: Map<String, DifficultyMode> = entries.associateBy { it.id }
  }
}

/**
 * PART 1: Biome / Map Registry (`MAPS_CONFIG`), decoupling visual themes and environmental physics
 * from level numbers.
 */
enum class MapBiome(
  val id: String,
  val displayName: String,
  val shortLabel: String,
  val description: String,
  val gravityMult: Float,
  val slipSpeedMult: Float,
  val hasMovingPlatforms: Boolean
) {
  MAP_1_VOLCANO(
    id = "MAP_1_VOLCANO",
    displayName = "Map 1: Volcano",
    shortLabel = "Volcano",
    description = "Lava pit at bottom, dark cavern background, ember particles",
    gravityMult = 1.0f,
    slipSpeedMult = 1.0f,
    hasMovingPlatforms = false
  ),
  MAP_2_CRYO_LAB(
    id = "MAP_2_CRYO_LAB",
    displayName = "Map 2: Cryo Lab",
    shortLabel = "Cryo Lab",
    description = "Acid/Freezing water at bottom, slippery cyan platforms, low gravity",
    gravityMult = 0.76f,
    slipSpeedMult = 1.12f,
    hasMovingPlatforms = false
  ),
  MAP_3_CYBER_GRID(
    id = "MAP_3_CYBER_GRID",
    displayName = "Map 3: Cyber Grid",
    shortLabel = "Cyber Grid",
    description = "Laser floor, neon grid background, moving platforms",
    gravityMult = 1.0f,
    slipSpeedMult = 1.04f,
    hasMovingPlatforms = true
  );

  companion object {
    val MAPS_CONFIG: Map<String, MapBiome> = entries.associateBy { it.id }
  }
}

/**
 * Switchable chalk tools introducing the evolved mechanics:
 * - WHITE: Standard chalk floor + Rune classification on finger release
 * - SPRING: Blue bouncy trampoline chalk that launches the stickman into a high arc
 * - BOOST: Gold steel turbo chalk that never crumbles, speeds up the runner, and rams enemies
 * - CUTTER: Snips a drawn line to refund 60% chalk and emit an enemy-stunning shockwave
 */
enum class ChalkTool(
  val displayName: String,
  val inkCostMultiplier: Float
) {
  WHITE("Chalk", 1.0f),
  SPRING("Bounce", 1.25f),
  BOOST("Turbo", 1.5f),
  CUTTER("Cut/Erase", 0f)
}

/**
 * Autonomous callout moves announced by a speech bubble above the stickman.
 */
enum class CalloutMoveType(val label: String) {
  JUMP("JUMP!"),
  BLITZ("BLITZ!"),
  GRAVITY("LOW GRAV!"),
  WIPEOUT("ERASER!")
}

/**
 * Active callout bubble state with countdown fuse and optional landing telegraph.
 */
data class CalloutMove(
  val type: CalloutMoveType,
  val remainingSeconds: Float,
  val totalSeconds: Float,
  val triggerWorldX: Float,
  val landingTargetX: Float? = null,
  val landingTargetY: Float? = null
) {
  val fuseProgress: Float
    get() = if (totalSeconds > 0f) (remainingSeconds / totalSeconds).coerceIn(0f, 1f) else 0f
}

/**
 * A collectible chalk stub or gold star/orb floating in the level.
 */
data class ChalkBit(
  val id: Int,
  val x: Float,
  val y: Float,
  val inkAmount: Float = 135f,
  val isStar: Boolean = false,
  val collected: Boolean = false
)

/**
 * A cluster of sharp thumbtacks pinned into the board.
 */
data class ThumbtackCluster(
  val x: Float,
  val y: Float,
  val count: Int = 3,
  val spacing: Float = 26f
)

/**
 * Red-chalk scribbler enemy that charges along platforms/lines and bonks off steep walls.
 */
data class ScribblerEnemy(
  val id: Int,
  val x: Float,
  val y: Float,
  val speed: Float = 115f,
  val facing: Float = -1f,
  val runPhase: Float = 0f,
  val isAlive: Boolean = true,
  val isTumbling: Boolean = false,
  val tumbleVy: Float = 0f
)

/**
 * A single collision segment connecting two smoothed points of a drawn path.
 */
data class LineSegment(
  val start: Vec2,
  val end: Vec2
) {
  val length: Float get() = start.distanceTo(end)
}

/**
 * A completed or in-progress hand-drawn chalk line in world coordinates.
 */
data class DrawnLine(
  val id: Long,
  val points: List<Vec2>,
  val isCompleted: Boolean = true,
  val tool: ChalkTool = ChalkTool.WHITE,
  val runeType: RuneType = RuneType.STANDARD_INK,
  val ageSeconds: Float = 0f,
  val maxLifetime: Float = WorldConstants.DEFAULT_LINE_LIFETIME,
  val segments: List<LineSegment> =
    if (points.size < 2) emptyList()
    else points.zipWithNext { a, b -> LineSegment(a, b) },
  val totalLength: Float = segments.sumOf { it.length.toDouble() }.toFloat()
) {
  val alpha: Float
    get() = if (tool == ChalkTool.BOOST || maxLifetime >= 999f) {
      1f
    } else {
      ((maxLifetime - ageSeconds) / 1.5f).coerceIn(0.15f, 1f)
    }
}

/**
 * A solid platform in the level.
 */
data class Platform(
  val startX: Float,
  val endX: Float,
  val topY: Float,
  val bottomY: Float = WorldConstants.WORLD_HEIGHT,
  val label: String? = null
) {
  val width: Float get() = endX - startX
}

/**
 * A hint curve shown faintly on early levels to guide players across gaps.
 */
data class GuideStroke(
  val points: List<Vec2>,
  val caption: String
)

/**
 * Scripted callout trigger along the level's horizontal X axis.
 */
data class ScriptedCallout(
  val triggerX: Float,
  val type: CalloutMoveType
)

/**
 * Pendulum configuration styles for `PENDULUM` obstacle.
 */
enum class PendulumStyle {
  SINGLE,
  DOUBLE_SYNC,
  FAST_SHORT
}

/**
 * Ledge configuration styles for `LEDGE` obstacle.
 */
enum class LedgeStyle {
  CYAN_STATIC,
  CYAN_BOUNCY,
  ORANGE_CRUMBLING
}

/**
 * PART 2: Modular Obstacle Pool (8 Obstacle Types for Recycling & Combining):
 * 1. [PENDULUM]: Swinging red spiked ball on a tether (`single`, `double_sync`, or `fast_short`).
 * 2. [LEDGE]: Cyan (static/bouncy) and Orange (crumbling/temporary) mini-platforms.
 * 3. [LAVA_GEYSER]: Warning bubble at the bottom lava surface that shoots a vertical fireball pillar upward every 2.5s.
 * 4. [FALLING_SPIKE]: Ceiling stalactites/meteors with a red vertical warning line that drop when the player approaches.
 * 5. [LASER_GATE]: Vertical red laser beam between two nodes that toggles ON/OFF every 1.5s.
 * 6. [SAW_TRACK]: Spinning red buzzsaw moving horizontally or vertically along a dotted track line.
 * 7. [NO_DRAW_ZONE]: Translucent red rectangular static field where `Chalk` cannot be drawn.
 * 8. [WIND_UPDRAFT]: Vertical particle columns that push the stickman upward toward overhead hazards unless blocked by a drawn ceiling.
 */
enum class HazardType {
  PENDULUM,
  LEDGE,
  LAVA_GEYSER,
  FALLING_SPIKE,
  LASER_GATE,
  SAW_TRACK,
  NO_DRAW_ZONE,
  WIND_UPDRAFT;

  companion object {
    val SWINGING_PENDULUM: HazardType get() = PENDULUM
    val VANISHING_PLATFORM: HazardType get() = LEDGE
  }
}

/**
 * Data-driven level blueprint entry in `LEVEL_DEFINITIONS` (Levels 1..50).
 */
data class LevelBlueprint(
  val level: Int,
  val tier: Int,
  val title: String,
  val subtitle: String,
  val obstaclesAllowed: List<HazardType>,
  val spawnRate: Float,
  val incomingEventsAllowed: List<CalloutMoveType>,
  val pendulumStyle: PendulumStyle = PendulumStyle.SINGLE,
  val ledgeStyle: LedgeStyle = LedgeStyle.CYAN_STATIC,
  val lengthMultiplier: Float = 1.0f,
  val isBossOrMilestone: Boolean = false,
  val hasMagnetFinale: Boolean = false
)

/**
 * Core scaling parameters for a level (1..50) calculated by `getLevelConfig(level)`.
 */
data class LevelConfig(
  val level: Int,
  val worldIndex: Int,
  val worldTitle: String,
  val levelTitle: String,
  val levelSubtitle: String,
  val levelLength: Float,
  val baseSpeed: Float,
  val runSpeed: Float,
  val minGap: Float,
  val maxGap: Float,
  val maxDeltaY: Float,
  val maxInkPerLevel: Float,
  val inkRechargeRate: Float,
  val parInkUsage: Float,
  val maxStrokeLength: Float,
  val lineLifespan: Float,
  val obstaclesAllowed: List<HazardType> = emptyList(),
  val incomingEventsAllowed: List<CalloutMoveType> = emptyList(),
  val spawnRate: Float = 1.0f,
  val difficultyMode: DifficultyMode = DifficultyMode.NORMAL,
  val mapBiome: MapBiome = MapBiome.MAP_1_VOLCANO
)

/**
 * Modular dynamic obstacle scaled by level number (`1..50`) and difficulty via `LevelCatalog.getHazards(...)`.
 */
data class DynamicHazard(
  val id: Int,
  val type: HazardType,
  val x: Float,
  val y: Float,
  // 1. PENDULUM parameters
  val armLength: Float = 170f,
  val bobRadius: Float = 20f,
  val maxAngleRadians: Float = 0.65f,
  val angularSpeed: Float = 2.2f,
  val pendulumStyle: PendulumStyle = PendulumStyle.SINGLE,
  // 2. LEDGE / Rect zone parameters
  val startX: Float = x - 65f,
  val endX: Float = x + 65f,
  val topY: Float = y,
  val bottomY: Float = y + 18f,
  val cycleSeconds: Float = 3.6f,
  val visibleRatio: Float = 0.65f,
  val ledgeStyle: LedgeStyle = LedgeStyle.ORANGE_CRUMBLING,
  val movesInBiome: Boolean = false,
  // 3. LAVA_GEYSER parameters (2.5s cycle: bubble warning -> vertical fireball pillar)
  val geyserTargetTopY: Float = 220f,
  val geyserWidth: Float = 34f,
  // 4. FALLING_SPIKE parameters (ceiling stalactite that drops when player approaches)
  val triggerLeadX: Float = 165f,
  val spikeDropSpeed: Float = 420f,
  val spikeHeight: Float = 44f,
  // 5. LASER_GATE parameters (vertical beam between topY and bottomY toggling every 1.5s)
  val laserHalfPeriodSeconds: Float = 1.5f,
  // 6. SAW_TRACK parameters (buzzsaw moving along track between (trackStartX, trackStartY) and (trackEndX, trackEndY))
  val trackStartX: Float = x - 90f,
  val trackStartY: Float = y,
  val trackEndX: Float = x + 90f,
  val trackEndY: Float = y,
  val trackSpeed: Float = 2.0f,
  // 8. WIND_UPDRAFT parameters (upward vertical acceleration)
  val updraftAcceleration: Float = 1480f,
  // Shared phase offset
  val phaseOffset: Float = 0f
) {
  val width: Float get() = endX - startX

  fun pendulumAngleAt(timeSeconds: Float): Float {
    return sin(timeSeconds * angularSpeed + phaseOffset) * maxAngleRadians
  }

  fun pendulumBobPositionAt(timeSeconds: Float): Vec2 {
    val angle = pendulumAngleAt(timeSeconds)
    return Vec2(
      x = x + sin(angle) * armLength,
      y = y + cos(angle) * armLength
    )
  }

  fun vanishingCycleProgressAt(timeSeconds: Float): Float {
    val safeCycle = cycleSeconds.coerceAtLeast(0.5f)
    val raw = ((timeSeconds + phaseOffset) % safeCycle) / safeCycle
    return if (raw < 0f) raw + 1f else raw
  }

  fun isPlatformSolidAt(timeSeconds: Float): Boolean {
    if (type != HazardType.LEDGE) return false
    if (ledgeStyle == LedgeStyle.CYAN_STATIC || ledgeStyle == LedgeStyle.CYAN_BOUNCY) return true
    return vanishingCycleProgressAt(timeSeconds) < visibleRatio
  }

  fun isPlatformWarningAt(timeSeconds: Float): Boolean {
    if (type != HazardType.LEDGE || ledgeStyle == LedgeStyle.CYAN_STATIC || ledgeStyle == LedgeStyle.CYAN_BOUNCY) {
      return false
    }
    if (!isPlatformSolidAt(timeSeconds)) return false
    val prog = vanishingCycleProgressAt(timeSeconds)
    return prog >= (visibleRatio - 0.18f).coerceAtLeast(0.1f)
  }

  fun ledgeBoundsAt(timeSeconds: Float, biome: MapBiome): Pair<Float, Float> {
    if (!movesInBiome && !biome.hasMovingPlatforms) {
      return startX to endX
    }
    val sway = sin(timeSeconds * 2.1f + phaseOffset) * 28f
    return (startX + sway) to (endX + sway)
  }

  /**
   * LAVA_GEYSER cycle: shoots a vertical fireball pillar upward every 2.5s (scaled by cycleSeconds).
   * Returns `0f` when dormant/bubbling, or `0f..1f` eruption height ratio when active.
   */
  fun geyserEruptionRatioAt(timeSeconds: Float): Float {
    val prog = vanishingCycleProgressAt(timeSeconds)
    // First 58% of cycle is warning bubble at lava surface; remaining 42% is vertical fireball eruption
    if (prog < 0.58f) return 0f
    val eruptProg = (prog - 0.58f) / 0.42f
    return sin(eruptProg * Math.PI.toFloat()).coerceIn(0f, 1f)
  }

  fun isGeyserWarningAt(timeSeconds: Float): Boolean {
    if (type != HazardType.LAVA_GEYSER) return false
    val prog = vanishingCycleProgressAt(timeSeconds)
    return prog in 0.25f..0.58f
  }

  fun geyserCurrentTopYAt(timeSeconds: Float): Float {
    val ratio = geyserEruptionRatioAt(timeSeconds)
    return WorldConstants.LAVA_SURFACE_Y - (WorldConstants.LAVA_SURFACE_Y - geyserTargetTopY) * ratio
  }

  /**
   * LASER_GATE toggles ON/OFF every `laserHalfPeriodSeconds` (default 1.5s).
   */
  fun isLaserActiveAt(timeSeconds: Float): Boolean {
    if (type != HazardType.LASER_GATE) return false
    val fullPeriod = (laserHalfPeriodSeconds * 2f).coerceAtLeast(0.8f)
    val t = ((timeSeconds + phaseOffset) % fullPeriod).let { if (it < 0f) it + fullPeriod else it }
    return t < laserHalfPeriodSeconds
  }

  fun isLaserChargingAt(timeSeconds: Float): Boolean {
    if (type != HazardType.LASER_GATE) return false
    val fullPeriod = (laserHalfPeriodSeconds * 2f).coerceAtLeast(0.8f)
    val t = ((timeSeconds + phaseOffset) % fullPeriod).let { if (it < 0f) it + fullPeriod else it }
    return t >= fullPeriod - 0.45f
  }

  /**
   * SAW_TRACK position along its dotted linear track at [timeSeconds].
   */
  fun sawPositionAt(timeSeconds: Float): Vec2 {
    val wave = 0.5f + 0.5f * sin(timeSeconds * trackSpeed + phaseOffset)
    return Vec2(
      x = trackStartX + (trackEndX - trackStartX) * wave,
      y = trackStartY + (trackEndY - trackStartY) * wave
    )
  }

  /**
   * FALLING_SPIKE tip Y position given the runner's current [runnerX], or blocked by an overhead drawn line.
   */
  fun fallingSpikeTipY(runnerX: Float, completedLines: List<DrawnLine>): Pair<Float, Boolean> {
    val distancePastTrigger = runnerX - (x - triggerLeadX)
    val rawTipY = if (distancePastTrigger <= 0f) {
      y + spikeHeight
    } else {
      val fallFraction = (distancePastTrigger / 175f).coerceIn(0f, 1.35f)
      y + spikeHeight + fallFraction * (WorldConstants.LAVA_SURFACE_Y - y)
    }

    // Check if an overhead player-drawn Chalk roof/shield blocks the falling spike
    var shieldY: Float? = null
    for (line in completedLines) {
      for (seg in line.segments) {
        val leftX = minOf(seg.start.x, seg.end.x) - 14f
        val rightX = maxOf(seg.start.x, seg.end.x) + 14f
        if (x in leftX..rightX) {
          val dx = (seg.end.x - seg.start.x)
          val segY = if (kotlin.math.abs(dx) < 1f) {
            minOf(seg.start.y, seg.end.y)
          } else {
            val t = ((x - seg.start.x) / dx).coerceIn(0f, 1f)
            seg.start.y + (seg.end.y - seg.start.y) * t
          }
          if (segY >= y + 20f && (shieldY == null || segY < shieldY)) {
            shieldY = segY
          }
        }
      }
    }

    return if (shieldY != null && rawTipY >= shieldY) {
      shieldY to true
    } else {
      rawTipY.coerceAtMost(WorldConstants.LAVA_SURFACE_Y) to false
    }
  }

  /**
   * Returns true if [point] lies inside a `NO_DRAW_ZONE` rectangle.
   */
  fun containsNoDrawPoint(point: Vec2): Boolean {
    if (type != HazardType.NO_DRAW_ZONE) return false
    return point.x in startX..endX && point.y in topY..bottomY
  }
}

/**
 * Complete definition for a playable level.
 */
data class LevelDefinition(
  val levelNumber: Int,
  val title: String,
  val subtitle: String,
  val runSpeed: Float,
  val spawnPosition: Vec2,
  val finishLineX: Float,
  val levelEndX: Float,
  val maxInkPerLevel: Float,
  val inkRechargeRate: Float = 120f,
  val parInkUsage: Float = 950f,
  val maxStrokeLength: Float = WorldConstants.MAX_STROKE_LENGTH,
  val lineLifespan: Float = 5.0f,
  val baseSpeed: Float = 3.0f,
  val levelLength: Float = 800f,
  val minGap: Float = 80f,
  val maxGap: Float = 140f,
  val maxDeltaY: Float = 0f,
  val worldTitle: String = "World 1: Chalkboard Meadows",
  val difficultyMode: DifficultyMode = DifficultyMode.NORMAL,
  val mapBiome: MapBiome = MapBiome.MAP_1_VOLCANO,
  val obstaclesAllowed: List<HazardType> = emptyList(),
  val platforms: List<Platform>,
  val guideStrokes: List<GuideStroke> = emptyList(),
  val chalkBits: List<ChalkBit> = emptyList(),
  val thumbtacks: List<ThumbtackCluster> = emptyList(),
  val scribblers: List<ScribblerEnemy> = emptyList(),
  val hazards: List<DynamicHazard> = emptyList(),
  val rockWalls: List<BreakableRockWall> = emptyList(),
  val lavaMonsters: List<LeapingLavaMonster> = emptyList(),
  val bossMaxHp: Int = 5,
  val scoreMultiplier: Float = 1.0f,
  val dailyModifier: String? = null,
  val calloutScript: List<ScriptedCallout> = emptyList()
)

/**
 * Physics and animation state of the running stickman.
 */
data class StickmanState(
  val footX: Float,
  val footY: Float,
  val velocityX: Float,
  val velocityY: Float = 0f,
  val isGrounded: Boolean = true,
  val slopeRadians: Float = 0f,
  val runCyclePhase: Float = 0f,
  val isAlive: Boolean = true,
  val hasReachedFinish: Boolean = false,
  val boostTimer: Float = 0f,
  val lowGravityTimer: Float = 0f,
  val blitzTimer: Float = 0f,
  val superAuraTimer: Float = 0f,
  val invulnerableTimer: Float = 0f,
  val hasTitanShield: Boolean = false,
  val landingSquashTimer: Float = 0f,
  val deathTimer: Float = 0f
)

/**
 * Rising ember/chalk-dust particle for the bottom hazard zone.
 */
data class EmberParticle(
  val worldX: Float,
  val worldY: Float,
  val velocityY: Float,
  val velocityX: Float,
  val radius: Float,
  val alpha: Float,
  val isYellow: Boolean
)

/**
 * Visual style of hand-drawn particle effects for running and sketching.
 */
enum class SketchParticleType {
  FOOTSTEP_CHALK_PUFF,
  PENCIL_SHAVING,
  LINE_LOCK_SPARK
}

/**
 * Particle in world coordinates rendered on the Canvas when the stickman runs
 * or when a drawn line is sketched/completed.
 */
data class SketchParticle(
  val worldX: Float,
  val worldY: Float,
  val velocityX: Float,
  val velocityY: Float,
  val size: Float,
  val alpha: Float,
  val rotationRadians: Float = 0f,
  val angularVelocity: Float = 0f,
  val type: SketchParticleType = SketchParticleType.FOOTSTEP_CHALK_PUFF
)

/**
 * High-level screen navigation / overlay state.
 */
enum class ScreenState {
  START_MENU,
  PLAYING,
  PAUSED,
  GAME_OVER,
  LEVEL_COMPLETE
}

/**
 * Snapshot of the entire game state observed by the Compose UI.
 */
data class GameUiState(
  val screenState: ScreenState = ScreenState.START_MENU,
  val currentLevel: Int = 1,
  val bestLevel: Int = 1,
  val levelStars: Map<Int, Int> = emptyMap(),
  val selectedDifficulty: DifficultyMode = DifficultyMode.NORMAL,
  val selectedMap: MapBiome = MapBiome.MAP_1_VOLCANO,
  val levelDefinition: LevelDefinition,
  val stickman: StickmanState,
  val cameraX: Float = 0f,
  val selectedTool: ChalkTool = ChalkTool.WHITE,
  val lastRuneType: RuneType = RuneType.STANDARD_INK,
  val completedLines: List<DrawnLine> = emptyList(),
  val activeStrokePoints: List<Vec2> = emptyList(),
  val activeStrokeRemainingRatio: Float = 1f,
  val currentInk: Float = 0f,
  val totalInkDrawn: Float = 0f,
  val chalkBits: List<ChalkBit> = emptyList(),
  val scribblers: List<ScribblerEnemy> = emptyList(),
  val rockWalls: List<BreakableRockWall> = emptyList(),
  val lavaMonsters: List<LeapingLavaMonster> = emptyList(),
  val ignisBoss: IgnisTitanBoss = IgnisTitanBoss(),
  val bossFlawless: Boolean = true,
  val activeCallout: CalloutMove? = null,
  val triggeredCalloutIndices: Set<Int> = emptySet(),
  val comboCount: Int = 0,
  val score: Int = 0,
  val starsEarned: Int = 3,
  val levelProgress: Float = 0f,
  val lavaAnimTime: Float = 0f,
  val embers: List<EmberParticle> = emptyList(),
  val sketchParticles: List<SketchParticle> = emptyList(),
  val soundEnabled: Boolean = true,
  val showInstructionBanner: Boolean = true,
  val currency: Int = 500,
  val equippedSkin: String = "DEFAULT",
  val equippedTrail: String = "STANDARD_INK",
  val equippedPerk: String = "",
  val unlockedSkins: Set<String> = setOf("DEFAULT"),
  val unlockedTrails: Set<String> = setOf("STANDARD_INK"),
  val unlockedPerks: Set<String> = emptySet(),
  val dailyChallengeDate: String = "2026-10-29",
  val dailyChallengeModifier: String = "Turbo Ignis",
  val dailyChallengeCompleted: Boolean = false,
  val isDailyChallengeRun: Boolean = false,
  val showShopModal: Boolean = false
) {
  val totalStarsEarned: Int
    get() = levelStars.values.sum()

  val completedLevelsCount: Int
    get() = maxOf(levelStars.count { it.value > 0 }, (bestLevel - 1).coerceAtLeast(0))

  val inkRemainingRatio: Float
    get() = if (levelDefinition.maxInkPerLevel > 0f) {
      (currentInk / levelDefinition.maxInkPerLevel).coerceIn(0f, 1f)
    } else {
      0f
    }

  val inkUsed: Float
    get() = (levelDefinition.maxInkPerLevel - currentInk).coerceAtLeast(0f)

  val isInkRecharging: Boolean
    get() = currentInk < levelDefinition.maxInkPerLevel &&
      activeStrokePoints.isEmpty() &&
      screenState == ScreenState.PLAYING

  val bitsCollectedCount: Int
    get() = chalkBits.count { !it.isStar && it.collected }

  val totalBitsInLevel: Int
    get() = chalkBits.count { !it.isStar }

  val allCollectiblesGotCount: Int
    get() = chalkBits.count { it.collected }

  val allCollectiblesCount: Int
    get() = chalkBits.size

  val collectibleTargetCount: Int
    get() = if (allCollectiblesCount == 0) 0 else maxOf(1, (allCollectiblesCount + 1) / 2)

  val earnedInkEfficiencyStar: Boolean
    get() = totalInkDrawn <= levelDefinition.parInkUsage

  val earnedCollectibleStar: Boolean
    get() = (allCollectiblesCount == 0 || allCollectiblesGotCount >= collectibleTargetCount) && bossFlawless

  fun calculateEarnedStars(): Int {
    var stars = 1 // 1st star: Crossing completed
    if (earnedInkEfficiencyStar) stars++ // 2nd star: Ink efficiency within par budget
    if (earnedCollectibleStar) stars++ // 3rd star: Collectibles target & Boss Flawless
    return stars.coerceIn(1, 3)
  }
}

/**
 * Isolated state representation for the top Gameplay 2x2 Grid HUD.
 * Only recomposes when key values change rather than on every 60 FPS physics tick.
 */
data class GameplayHudState(
  val currentLevel: Int = 1,
  val levelTitle: String = "",
  val levelProgress: Float = 0f,
  val inkRemainingRatio: Float = 1f,
  val isInkRecharging: Boolean = false,
  val soundEnabled: Boolean = true,
  val selectedTool: ChalkTool = ChalkTool.WHITE,
  val lastRuneType: RuneType = RuneType.STANDARD_INK,
  val superAuraSeconds: Float = 0f,
  val hasTitanShield: Boolean = false,
  val bossHp: Int = 5,
  val bossMaxHp: Int = 5,
  val score: Int = 0,
  val currency: Int = 500,
  val totalBitsInLevel: Int = 0,
  val bitsCollectedCount: Int = 0,
  val comboCount: Int = 0,
  val activeCallout: CalloutMove? = null,
  val hasCompletedLines: Boolean = false,
  val showInstructionBanner: Boolean = true
)
