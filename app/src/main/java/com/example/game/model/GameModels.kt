package com.example.game.model

import kotlin.math.hypot

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
  const val DEFAULT_GROUND_Y = 420f

  const val STICKMAN_HEIGHT = 58f
  const val STICKMAN_FOOT_RADIUS = 14f
  const val STICKMAN_HEAD_RADIUS = 10f

  const val GRAVITY = 960f
  const val MAX_FALL_SPEED = 680f

  // Camera keeps the stickman around 24% from the left edge of the screen
  const val CAMERA_LEAD_RATIO = 0.24f

  // Maximum length of a single drawn line per finger drag attempt
  const val MAX_STROKE_LENGTH = 760f
  const val MIN_POINT_DISTANCE = 10f

  // Default lifetime for fading chalk lines before they crumble
  const val DEFAULT_LINE_LIFETIME = 7.5f
}

/**
 * Switchable chalk tools introducing the evolved mechanics:
 * - WHITE: Standard chalk floor + steep strokes act as knee-high vault hurdles
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
 * A collectible chalk stub or gold star floating in the level.
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
  val platforms: List<Platform>,
  val guideStrokes: List<GuideStroke> = emptyList(),
  val chalkBits: List<ChalkBit> = emptyList(),
  val thumbtacks: List<ThumbtackCluster> = emptyList(),
  val scribblers: List<ScribblerEnemy> = emptyList(),
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
  val blitzTimer: Float = 0f
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
  val levelDefinition: LevelDefinition,
  val stickman: StickmanState,
  val cameraX: Float = 0f,
  val selectedTool: ChalkTool = ChalkTool.WHITE,
  val completedLines: List<DrawnLine> = emptyList(),
  val activeStrokePoints: List<Vec2> = emptyList(),
  val activeStrokeRemainingRatio: Float = 1f,
  val currentInk: Float = 0f,
  val totalInkDrawn: Float = 0f,
  val chalkBits: List<ChalkBit> = emptyList(),
  val scribblers: List<ScribblerEnemy> = emptyList(),
  val activeCallout: CalloutMove? = null,
  val triggeredCalloutIndices: Set<Int> = emptySet(),
  val comboCount: Int = 0,
  val starsEarned: Int = 3,
  val levelProgress: Float = 0f,
  val lavaAnimTime: Float = 0f,
  val embers: List<EmberParticle> = emptyList(),
  val sketchParticles: List<SketchParticle> = emptyList(),
  val soundEnabled: Boolean = true,
  val showInstructionBanner: Boolean = true
) {
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
}

/**
 * Isolated state representation for the top Gameplay HUD.
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
  val totalBitsInLevel: Int = 0,
  val bitsCollectedCount: Int = 0,
  val comboCount: Int = 0,
  val activeCallout: CalloutMove? = null,
  val hasCompletedLines: Boolean = false,
  val showInstructionBanner: Boolean = true
)
