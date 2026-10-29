package com.example.game.physics

import com.example.game.model.BreakableRockWall
import com.example.game.model.ChalkBit
import com.example.game.model.ChalkTool
import com.example.game.model.DrawnLine
import com.example.game.model.DynamicHazard
import com.example.game.model.EmberParticle
import com.example.game.model.HazardType
import com.example.game.model.IgnisTitanBoss
import com.example.game.model.LeapingLavaMonster
import com.example.game.model.LedgeStyle
import com.example.game.model.LevelDefinition
import com.example.game.model.MapBiome
import com.example.game.model.Platform
import com.example.game.model.RuneClassification
import com.example.game.model.RuneType
import com.example.game.model.ScribblerEnemy
import com.example.game.model.SketchParticle
import com.example.game.model.SketchParticleType
import com.example.game.model.StickmanState
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Pure 2D physics, path-smoothing, Rune Classification, and swept collision engine for
 * Draw to Survive / Ink Bridge Runner: Ignis Titan Edition.
 *
 * Supports:
 * - Multi-substep swept collision resolution so the stickman never tunnels through thin drawn lines
 *   or fast-moving obstacles
 * - One-way non-blocking ink lines (never blocks horizontal velocity, ignores steep vertical segments
 *   unless bouncy trampoline)
 * - Pure Line-Drawing & Rune Classification Engine (Straight Bridge, Closed Circle Super Aura,
 *   Zig-Zag Bouncy Coil, Curved Turbo Ramp)
 * - Continuous swept capsule hitboxes for all 8 modular obstacles + Breakable Rock Walls,
 *   Leaping Lava Monsters, and the Colossal Lava Dragon Boss (Ignis Titan)
 */
object PhysicsEngine {

  private const val MAX_SUBSTEP_DT = 1f / 120f
  private const val FOOT_SUPPORT_HALF_WIDTH = 12f
  private const val LINE_ENDPOINT_MARGIN = 6f
  private const val STEP_UP_TOLERANCE = 34f
  private const val GROUND_SNAP_TOLERANCE = 22f
  private const val FALL_CROSSING_TOLERANCE = 24f
  private const val PLATFORM_MANTLE_TOLERANCE = 115f
  private const val MAX_CLIMBABLE_SLOPE_RADIANS = 1.18f // ~67 degrees

  data class PhysicsTickResult(
    val stickman: StickmanState,
    val justJumped: Boolean = false,
    val justLanded: Boolean = false,
    val justBouncedSpring: Boolean = false,
    val shieldJustBroken: Boolean = false,
    val hitLava: Boolean = false,
    val reachedFinish: Boolean = false
  )

  data class CombatStepResult(
    val stickman: StickmanState,
    val rockWalls: List<BreakableRockWall>,
    val lavaMonsters: List<LeapingLavaMonster>,
    val ignisBoss: IgnisTitanBoss,
    val scoreDelta: Int = 0,
    val inkDelta: Float = 0f,
    val bossTookDamage: Boolean = false,
    val playerTookBossBite: Boolean = false,
    val wallSmashed: Boolean = false,
    val monsterBlasted: Boolean = false,
    val shieldBroken: Boolean = false,
    val extraParticles: List<SketchParticle> = emptyList()
  )

  data class MatterBody2D(
    val id: Long,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val angleRadians: Float = 0f,
    val isStatic: Boolean = true,
    val restitution: Float = 0f,
    val friction: Float = 0f,
    val tool: ChalkTool = ChalkTool.WHITE
  )

  fun createMatterBodiesForLine(line: DrawnLine, thickness: Float = 8f): List<MatterBody2D> {
    return line.segments.mapIndexedNotNull { index, seg ->
      val dx = seg.end.x - seg.start.x
      val dy = seg.end.y - seg.start.y
      val len = sqrt(dx * dx + dy * dy)
      if (len < 2f) {
        null
      } else {
        MatterBody2D(
          id = line.id * 1000L + index,
          centerX = (seg.start.x + seg.end.x) * 0.5f,
          centerY = (seg.start.y + seg.end.y) * 0.5f,
          width = len + 4f,
          height = thickness,
          angleRadians = atan2(dy, dx),
          isStatic = true,
          restitution = if (line.tool == ChalkTool.SPRING) 1.8f else 0f,
          friction = 0f,
          tool = line.tool
        )
      }
    }
  }

  /**
   * Pure Line-Drawing & Rune Classification Engine:
   * Evaluates stroke geometry upon finger release:
   * 1. CLOSED CIRCLE ON RUNNER (`STAR_SPARK`): start-to-end distance < 110px, low straightness,
   *    and center within 130px of runner -> +25% Ink & 5.0s Golden Super Aura Sonic Dash.
   * 2. ZIG-ZAG / W-SHAPE (`ZIGZAG_COIL`): `directionFlips >= 2` -> Neon Cyan 1.8x trampoline & +15% Ink.
   * 3. CURVED RAMP / LOOP AHEAD (`RAMP_SLOPE`): arched ramp -> Neon Green 1.9x turbo conveyor & +12% Ink.
   * 4. STRAIGHT BRIDGE (`STANDARD_INK`): `straightnessRatio > 0.88` and `directionFlips < 2` -> Standard Ink.
   */
  fun classifyRuneStroke(
    points: List<Vec2>,
    stickman: StickmanState,
    selectedTool: ChalkTool = ChalkTool.WHITE
  ): RuneClassification {
    if (selectedTool == ChalkTool.SPRING) {
      return RuneClassification(
        runeType = RuneType.ZIGZAG_COIL,
        effectiveTool = ChalkTool.SPRING,
        inkRefundRatio = 0f
      )
    }
    if (selectedTool == ChalkTool.BOOST) {
      return RuneClassification(
        runeType = RuneType.RAMP_SLOPE,
        effectiveTool = ChalkTool.BOOST,
        inkRefundRatio = 0f
      )
    }
    if (points.size < 3) {
      return RuneClassification(
        runeType = RuneType.STANDARD_INK,
        effectiveTool = ChalkTool.WHITE
      )
    }

    val totalLength = calculatePolylineLength(points)
    if (totalLength < 70f) {
      return RuneClassification(
        runeType = RuneType.STANDARD_INK,
        effectiveTool = ChalkTool.WHITE
      )
    }

    val first = points.first()
    val last = points.last()
    val directDist = first.distanceTo(last)
    val straightnessRatio = directDist / totalLength.coerceAtLeast(1f)

    var sumX = 0f
    var sumY = 0f
    for (pt in points) {
      sumX += pt.x
      sumY += pt.y
    }
    val center = Vec2(sumX / points.size, sumY / points.size)
    val runnerCenter = Vec2(stickman.footX, stickman.footY - WorldConstants.STICKMAN_HEIGHT * 0.5f)
    val distToRunner = center.distanceTo(runnerCenter)

    // Count significant vertical direction flips for Zig-Zag / W-Shape detection
    var directionFlips = 0
    var prevSignY = 0
    val stepStride = max(1, points.size / 8)
    var i = 0
    while (i + stepStride < points.size) {
      val dy = points[i + stepStride].y - points[i].y
      if (abs(dy) >= 14f) {
        val signY = if (dy > 0f) 1 else -1
        if (prevSignY != 0 && signY != prevSignY) {
          directionFlips++
        }
        prevSignY = signY
      }
      i += stepStride
    }

    // 1. CLOSED CIRCLE ON RUNNER -> STAR_SPARK (Golden Super Aura Sonic Dash)
    val isClosedLoop = directDist < 110f && straightnessRatio < 0.44f && totalLength >= 90f
    if (isClosedLoop && distToRunner <= 135f) {
      return RuneClassification(
        runeType = RuneType.STAR_SPARK,
        effectiveTool = ChalkTool.BOOST,
        inkRefundRatio = 0.25f,
        triggersSuperAura = true,
        triggersImmediateBounce = false
      )
    }

    // 2. ZIG-ZAG / W-SHAPE -> ZIGZAG_COIL (Neon Cyan Bouncy Trampoline)
    if (directionFlips >= 2 && straightnessRatio in 0.25f..0.78f) {
      return RuneClassification(
        runeType = RuneType.ZIGZAG_COIL,
        effectiveTool = ChalkTool.SPRING,
        inkRefundRatio = 0.15f,
        triggersSuperAura = false,
        triggersImmediateBounce = distToRunner <= 115f
      )
    }

    // 3. CURVED RAMP / UPWARD RAMP AHEAD -> RAMP_SLOPE (Neon Green Turbo Conveyor)
    val netClimb = first.y - last.y
    val isUpwardRamp = (netClimb >= 36f && last.x > first.x + 40f) ||
      (straightnessRatio in 0.65f..0.88f && netClimb >= 20f)
    if (directionFlips < 2 && totalLength >= 95f && isUpwardRamp) {
      return RuneClassification(
        runeType = RuneType.RAMP_SLOPE,
        effectiveTool = ChalkTool.BOOST,
        inkRefundRatio = 0.12f,
        triggersSuperAura = false,
        triggersImmediateBounce = false
      )
    }

    // 4. STRAIGHT BRIDGE -> STANDARD_INK
    return RuneClassification(
      runeType = RuneType.STANDARD_INK,
      effectiveTool = ChalkTool.WHITE,
      inkRefundRatio = 0f
    )
  }

  /**
   * Checks whether [candidatePoint] is blocked by any `NO_DRAW_ZONE` hazard when using standard `Chalk`.
   */
  fun isPointBlockedByNoDrawZone(
    candidatePoint: Vec2,
    tool: ChalkTool,
    hazards: List<DynamicHazard>
  ): Boolean {
    if (tool == ChalkTool.CUTTER) return false
    if (tool != ChalkTool.WHITE) return false
    return hazards.any { it.type == HazardType.NO_DRAW_ZONE && it.containsNoDrawPoint(candidatePoint) }
  }

  /**
   * Appends a new touch point in world coordinates while respecting both the per-stroke
   * maximum length, the remaining level ink budget, and any `NO_DRAW_ZONE` fields.
   */
  fun appendPointToStroke(
    existingPoints: List<Vec2>,
    candidatePoint: Vec2,
    maxStrokeLength: Float,
    availableInkForRemaining: Float,
    tool: ChalkTool = ChalkTool.WHITE,
    hazards: List<DynamicHazard> = emptyList()
  ): List<Vec2> {
    val clampedCandidate = Vec2(
      x = candidatePoint.x,
      y = candidatePoint.y.coerceIn(4f, WorldConstants.WORLD_HEIGHT - 4f)
    )

    if (isPointBlockedByNoDrawZone(clampedCandidate, tool, hazards)) {
      return existingPoints
    }

    if (existingPoints.isEmpty()) {
      return listOf(clampedCandidate)
    }

    val currentLength = calculatePolylineLength(existingPoints)
    val remainingStrokeBudget = (maxStrokeLength - currentLength).coerceAtLeast(0f)
    val remainingBudget = min(remainingStrokeBudget, availableInkForRemaining).coerceAtLeast(0f)
    if (remainingBudget <= 1f) {
      return existingPoints
    }

    val lastPoint = existingPoints.last()
    val dist = lastPoint.distanceTo(clampedCandidate)
    if (dist < WorldConstants.MIN_POINT_DISTANCE) {
      return existingPoints
    }

    return if (dist <= remainingBudget) {
      existingPoints + clampedCandidate
    } else {
      val ratio = (remainingBudget / dist).coerceIn(0f, 1f)
      val truncated = Vec2(
        x = lastPoint.x + (clampedCandidate.x - lastPoint.x) * ratio,
        y = lastPoint.y + (clampedCandidate.y - lastPoint.y) * ratio
      )
      existingPoints + truncated
    }
  }

  fun smoothStrokePoints(rawPoints: List<Vec2>): List<Vec2> {
    if (rawPoints.size <= 2) return rawPoints

    val smoothed = mutableListOf<Vec2>()
    smoothed.add(rawPoints.first())

    for (i in 0 until rawPoints.size - 1) {
      val p0 = rawPoints[i]
      val p1 = rawPoints[i + 1]
      val dist = p0.distanceTo(p1)
      if (dist > 18f) {
        val q = Vec2(
          x = 0.75f * p0.x + 0.25f * p1.x,
          y = 0.75f * p0.y + 0.25f * p1.y
        )
        val r = Vec2(
          x = 0.25f * p0.x + 0.75f * p1.x,
          y = 0.25f * p0.y + 0.75f * p1.y
        )
        if (smoothed.last().distanceTo(q) >= 6f) {
          smoothed.add(q)
        }
        if (smoothed.last().distanceTo(r) >= 6f) {
          smoothed.add(r)
        }
      } else if (i > 0 && smoothed.last().distanceTo(p0) >= 6f) {
        smoothed.add(p0)
      }
    }

    val last = rawPoints.last()
    if (smoothed.last().distanceTo(last) >= 2f) {
      smoothed.add(last)
    } else if (smoothed.size == 1) {
      smoothed.add(last)
    }

    return smoothed
  }

  fun calculatePolylineLength(points: List<Vec2>): Float {
    if (points.size < 2) return 0f
    var sum = 0f
    for (i in 0 until points.size - 1) {
      sum += points[i].distanceTo(points[i + 1])
    }
    return sum
  }

  fun stepPhysics(
    stickman: StickmanState,
    level: LevelDefinition,
    completedLines: List<DrawnLine>,
    deltaSeconds: Float,
    animTime: Float = 0f
  ): PhysicsTickResult {
    if (!stickman.isAlive || stickman.hasReachedFinish) {
      return PhysicsTickResult(
        stickman = stickman,
        justLanded = false,
        hitLava = !stickman.isAlive,
        reachedFinish = stickman.hasReachedFinish
      )
    }

    val clampedDt = deltaSeconds.coerceIn(0f, 0.08f)
    if (clampedDt <= 0f) {
      return PhysicsTickResult(
        stickman = stickman,
        justJumped = false,
        justLanded = false,
        hitLava = false,
        reachedFinish = false
      )
    }

    val steps = max(1, (clampedDt / MAX_SUBSTEP_DT).toInt() + 1)
    val subDt = clampedDt / steps

    var current = stickman
    var jumpedInFrame = false
    var landedInFrame = false
    var bouncedSpringInFrame = false
    var shieldBrokenInFrame = false

    for (step in 0 until steps) {
      val prevGrounded = current.isGrounded
      val prevFootY = current.footY
      val prevVy = current.velocityY
      val prevHadShield = current.hasTitanShield

      current = stepSingleSubstep(
        state = current,
        level = level,
        platforms = level.platforms,
        completedLines = completedLines,
        dt = subDt,
        animTime = animTime + step * subDt
      )

      if (prevHadShield && !current.hasTitanShield) {
        shieldBrokenInFrame = true
      }

      if (current.velocityY < -440f && prevVy >= -200f) {
        bouncedSpringInFrame = true
        jumpedInFrame = true
      } else if (prevGrounded && !current.isGrounded && current.isAlive) {
        jumpedInFrame = true
      } else if (prevGrounded && current.isGrounded && current.footY < prevFootY - 3.5f) {
        jumpedInFrame = true
      }

      if (!prevGrounded && current.isGrounded) {
        landedInFrame = true
      }

      if (!current.isAlive || current.hasReachedFinish) {
        break
      }
    }

    return PhysicsTickResult(
      stickman = current,
      justJumped = jumpedInFrame,
      justLanded = landedInFrame,
      justBouncedSpring = bouncedSpringInFrame,
      shieldJustBroken = shieldBrokenInFrame,
      hitLava = !current.isAlive,
      reachedFinish = current.hasReachedFinish
    )
  }

  private data class SurfaceHit(
    val y: Float,
    val slopeRadians: Float,
    val tool: ChalkTool = ChalkTool.WHITE
  )

  private fun stepSingleSubstep(
    state: StickmanState,
    level: LevelDefinition,
    platforms: List<Platform>,
    completedLines: List<DrawnLine>,
    dt: Float,
    animTime: Float = 0f
  ): StickmanState {
    val nextBoostTimer = (state.boostTimer - dt).coerceAtLeast(0f)
    val nextLowGravTimer = (state.lowGravityTimer - dt).coerceAtLeast(0f)
    val nextBlitzTimer = (state.blitzTimer - dt).coerceAtLeast(0f)
    val nextSuperAuraTimer = (state.superAuraTimer - dt).coerceAtLeast(0f)
    val nextInvulnTimer = (state.invulnerableTimer - dt).coerceAtLeast(0f)

    val slopeFactor = if (state.isGrounded && state.slopeRadians < 0f) {
      cos(state.slopeRadians.coerceIn(-1.05f, 0f)).coerceIn(0.62f, 1f)
    } else {
      1f
    }

    val boostMult = when {
      nextBlitzTimer > 0f -> 1.85f
      nextSuperAuraTimer > 0f -> 1.65f
      nextBoostTimer > 0f -> 1.5f
      else -> 1f
    }

    val vx = level.runSpeed * slopeFactor * boostMult
    val prevX = state.footX
    val prevY = state.footY
    var resolvedX = prevX + vx * dt

    var vy = if (state.isGrounded) {
      if (state.slopeRadians < -0.15f) {
        sin(state.slopeRadians) * vx * 0.85f
      } else {
        0f
      }
    } else {
      state.velocityY
    }

    // Auto-vaulting over top 30px..115px of slightly higher cliff ledges (Swept platform collision)
    if (state.isGrounded) {
      for (platform in platforms) {
        if (
          prevX <= platform.startX + 4f &&
            resolvedX + 18f >= platform.startX &&
            prevY > platform.topY + STEP_UP_TOLERANCE &&
            prevY <= platform.topY + PLATFORM_MANTLE_TOLERANCE
        ) {
          val climbHeight = (prevY - platform.topY).coerceIn(24f, PLATFORM_MANTLE_TOLERANCE)
          vy = -sqrt(2f * WorldConstants.GRAVITY * (climbHeight + 26f)).coerceIn(260f, 520f)
          break
        }
      }
    }

    // Apply MapBiome gravity multiplier + Milestone 25 micro-gravity + LOW GRAV callout modifier
    val milestoneGravMult = if (level.levelNumber == 25 && nextLowGravTimer > 0f) 0.26f else 1f
    val gravityScale = (if (nextLowGravTimer > 0f) 0.48f else 1f) * level.mapBiome.gravityMult * milestoneGravMult
    vy = (vy + WorldConstants.GRAVITY * gravityScale * dt).coerceAtMost(WorldConstants.MAX_FALL_SPEED)

    // Apply WIND_UPDRAFT upward lift if stickman is inside an active Wind Updraft column
    var inUpdraft = false
    for (hazard in level.hazards) {
      if (hazard.type == HazardType.WIND_UPDRAFT) {
        if (resolvedX in hazard.startX..hazard.endX && prevY >= hazard.topY && prevY - WorldConstants.STICKMAN_HEIGHT <= hazard.bottomY) {
          vy = (vy - hazard.updraftAcceleration * dt).coerceAtLeast(-285f)
          inUpdraft = true
        }
      }
    }

    // Low-Altitude Super Aura Combat Hover: keeps Super Aura runner safely at combat height
    if (nextSuperAuraTimer > 0f && !state.isGrounded) {
      val hoverTargetY = WorldConstants.DEFAULT_GROUND_Y - 12f
      if (prevY >= hoverTargetY && vy > 0f) {
        vy = (hoverTargetY - prevY) * 8f
      }
    }

    var freeFallY = prevY + vy * dt
    val grounded = if (inUpdraft && vy < -40f) false else state.isGrounded

    // Check overhead player-drawn ceiling lines when rising (protects stickman in WIND_UPDRAFT from hitting overhead hazards!)
    if (vy < 0f) {
      val headY = freeFallY - WorldConstants.STICKMAN_HEIGHT
      val ceilingY = findOverheadCeilingY(resolvedX, prevY - WorldConstants.STICKMAN_HEIGHT, headY, completedLines)
      if (ceilingY != null) {
        freeFallY = ceilingY + WorldConstants.STICKMAN_HEIGHT + 2f
        vy = 0f
      }
    }

    val candidateHits = collectSupportingSurfaces(
      footX = resolvedX,
      platforms = platforms,
      completedLines = completedLines,
      hazards = level.hazards,
      animTime = animTime,
      mapBiome = level.mapBiome
    )

    var resolvedY = freeFallY
    var resolvedVy = vy
    var isGroundedNow = false
    var resolvedSlope = 0f
    var activeSurfaceTool = ChalkTool.WHITE

    if (grounded && vy >= -100f) {
      val reachable = candidateHits
        .filter { hit ->
          hit.y >= prevY - STEP_UP_TOLERANCE &&
            hit.y <= max(prevY, freeFallY) + GROUND_SNAP_TOLERANCE
        }
        .minByOrNull { it.y }

      if (reachable != null) {
        resolvedY = reachable.y
        resolvedVy = 0f
        isGroundedNow = true
        resolvedSlope = reachable.slopeRadians
        activeSurfaceTool = reachable.tool
      }
    }

    // One-way top surface landing when descending (vy >= -2f)
    if (!isGroundedNow && vy >= -2f) {
      val landingHit = candidateHits
        .filter { hit ->
          prevY <= hit.y + FALL_CROSSING_TOLERANCE &&
            freeFallY >= hit.y - FALL_CROSSING_TOLERANCE
        }
        .minByOrNull { it.y }

      if (landingHit != null) {
        resolvedY = landingHit.y
        resolvedVy = 0f
        isGroundedNow = true
        resolvedSlope = landingHit.slopeRadians
        activeSurfaceTool = landingHit.tool
      }
    }

    // Swept Anti-Sink Platform Collision
    for (platform in platforms) {
      if (resolvedX + FOOT_SUPPORT_HALF_WIDTH >= platform.startX && resolvedX - FOOT_SUPPORT_HALF_WIDTH <= platform.endX) {
        if (
          resolvedY >= platform.topY &&
            (prevY <= platform.topY + 95f || resolvedY <= platform.topY + 95f) &&
            vy >= 0f
        ) {
          resolvedY = platform.topY
          resolvedVy = 0f
          isGroundedNow = true
          resolvedSlope = 0f
          activeSurfaceTool = ChalkTool.WHITE
        } else if (
          resolvedY > platform.topY &&
            prevY > platform.topY &&
            resolvedY <= platform.topY + 30f
        ) {
          // Auto-vault top 30px of slightly higher cliff ledge
          resolvedY = platform.topY
          resolvedVy = -180f
          isGroundedNow = true
          resolvedSlope = 0f
          activeSurfaceTool = ChalkTool.WHITE
        }
      }
    }

    var updatedBoostTimer = nextBoostTimer
    var nextLandingSquash = (state.landingSquashTimer - dt).coerceAtLeast(0f)
    if (isGroundedNow) {
      if (!state.isGrounded && state.velocityY > 110f) {
        nextLandingSquash = 0.26f
      }
      if (activeSurfaceTool == ChalkTool.SPRING) {
        resolvedVy = -540f
        isGroundedNow = false
        nextLandingSquash = 0f
      } else if (activeSurfaceTool == ChalkTool.BOOST) {
        updatedBoostTimer = max(updatedBoostTimer, 0.55f)
      }
    }

    // =========================================================================
    // EXACT SWEPT OBSTACLE HITBOX COLLISION (ALWAYS ACTIVE - STOPS GAME ON HIT)
    // =========================================================================
    var currentHasShield = state.hasTitanShield
    var currentInvuln = nextInvulnTimer

    fun handleLethalObstacleHit(): StickmanState? {
      if (currentInvuln > 0f) return null
      if (currentHasShield) {
        currentHasShield = false
        currentInvuln = 1.1f
        resolvedVy = -320f
        isGroundedNow = false
        return null
      }
      return killStickman(state, resolvedX, resolvedY)
    }

    val sweptMinX = min(prevX, resolvedX) - 13f
    val sweptMaxX = max(prevX, resolvedX) + 13f
    val stickmanTopY = resolvedY - WorldConstants.STICKMAN_HEIGHT + 4f
    val stickmanBotY = resolvedY - 2f
    val bodySegTop = Vec2(resolvedX, stickmanTopY)
    val bodySegBot = Vec2(resolvedX, stickmanBotY)
    val midX = (prevX + resolvedX) * 0.5f
    val midSegTop = Vec2(midX, stickmanTopY)
    val midSegBot = Vec2(midX, stickmanBotY)

    // 1. Thumbtack clusters
    for (cluster in level.thumbtacks) {
      for (i in 0 until cluster.count) {
        val offset = (i - (cluster.count - 1) / 2f) * cluster.spacing
        val tx = cluster.x + offset
        val ty = cluster.y
        if (sweptMaxX >= tx - 9f && sweptMinX <= tx + 9f && resolvedY >= ty - 8f && stickmanTopY <= ty + 8f) {
          handleLethalObstacleHit()?.let { return it }
        }
      }
    }

    // 2. All Modular Dynamic Hazards (Continuous Swept Capsule / AABB Hitboxes)
    for (hazard in level.hazards) {
      when (hazard.type) {
        HazardType.PENDULUM -> {
          val bobPos = hazard.pendulumBobPositionAt(animTime)
          val hitRadius = hazard.bobRadius + 13f
          val distNow = distancePointToSegment(bobPos, bodySegTop, bodySegBot)
          val distMid = distancePointToSegment(bobPos, midSegTop, midSegBot)
          if (distNow <= hitRadius || distMid <= hitRadius) {
            handleLethalObstacleHit()?.let { return it }
          }
        }

        HazardType.LAVA_GEYSER -> {
          val ratio = hazard.geyserEruptionRatioAt(animTime)
          if (ratio > 0.08f) {
            val geyserTop = hazard.geyserCurrentTopYAt(animTime)
            val halfW = hazard.geyserWidth * 0.45f + 6f
            val overlapsX = sweptMaxX >= hazard.x - halfW && sweptMinX <= hazard.x + halfW
            val overlapsY = resolvedY >= geyserTop + 4f && stickmanTopY <= WorldConstants.LAVA_SURFACE_Y
            if (overlapsX && overlapsY) {
              // Only safe if standing on a drawn bridge strictly above the erupting geyser tip
              val safeOnOverheadBridge = isGroundedNow && resolvedY <= geyserTop + 8f
              if (!safeOnOverheadBridge) {
                handleLethalObstacleHit()?.let { return it }
              }
            }
          }
        }

        HazardType.FALLING_SPIKE -> {
          val overlapsX = sweptMaxX >= hazard.x - 14f && sweptMinX <= hazard.x + 14f
          if (overlapsX) {
            val (spikeTipY, blockedByRoof) = hazard.fallingSpikeTipY(resolvedX, completedLines)
            val spikeTopY = (spikeTipY - hazard.spikeHeight).coerceAtLeast(hazard.y)
            if (!blockedByRoof && spikeTipY >= stickmanTopY + 2f && spikeTopY <= resolvedY) {
              handleLethalObstacleHit()?.let { return it }
            }
          }
        }

        HazardType.LASER_GATE -> {
          if (hazard.isLaserActiveAt(animTime) && nextSuperAuraTimer <= 0f) {
            val overlapsX = sweptMaxX >= hazard.x - 9f && sweptMinX <= hazard.x + 9f
            val overlapsY = resolvedY >= hazard.topY + 4f && stickmanTopY <= hazard.bottomY - 4f
            if (overlapsX && overlapsY) {
              handleLethalObstacleHit()?.let { return it }
            }
          }
        }

        HazardType.SAW_TRACK -> {
          val sawPos = hazard.sawPositionAt(animTime)
          val hitRadius = hazard.bobRadius + 13f
          val distNow = distancePointToSegment(sawPos, bodySegTop, bodySegBot)
          val distMid = distancePointToSegment(sawPos, midSegTop, midSegBot)
          if (distNow <= hitRadius || distMid <= hitRadius) {
            handleLethalObstacleHit()?.let { return it }
          }
        }

        HazardType.LEDGE, HazardType.NO_DRAW_ZONE, HazardType.WIND_UPDRAFT -> Unit
      }
    }

    // Check if stickman fell into the bottom hazard pit (Lava / Cryo Acid / Laser Floor)
    if (resolvedY >= WorldConstants.LAVA_SURFACE_Y) {
      if (currentHasShield) {
        currentHasShield = false
        currentInvuln = 1.2f
        resolvedY = WorldConstants.LAVA_SURFACE_Y - 18f
        resolvedVy = -520f
        isGroundedNow = false
      } else {
        return state.copy(
          footX = resolvedX,
          footY = WorldConstants.LAVA_SURFACE_Y,
          velocityX = 0f,
          velocityY = 0f,
          isGrounded = false,
          isAlive = false,
          landingSquashTimer = 0f,
          deathTimer = 0.01f
        )
      }
    }

    // Check if stickman crossed the finish line above the bottom hazard pit
    if (resolvedX >= level.finishLineX && resolvedY < WorldConstants.LAVA_SURFACE_Y - 10f) {
      return state.copy(
        footX = resolvedX,
        footY = resolvedY,
        velocityX = vx,
        velocityY = resolvedVy,
        isGrounded = isGroundedNow,
        slopeRadians = resolvedSlope,
        runCyclePhase = state.runCyclePhase + dt * 10.5f,
        hasReachedFinish = true,
        boostTimer = updatedBoostTimer,
        lowGravityTimer = nextLowGravTimer,
        blitzTimer = nextBlitzTimer,
        superAuraTimer = nextSuperAuraTimer,
        invulnerableTimer = currentInvuln,
        hasTitanShield = currentHasShield,
        landingSquashTimer = nextLandingSquash
      )
    }

    val phaseAdvance = if (isGroundedNow) dt * (vx / 22.0f) else dt * 6.0f

    return state.copy(
      footX = resolvedX,
      footY = resolvedY,
      velocityX = vx,
      velocityY = resolvedVy,
      isGrounded = isGroundedNow,
      slopeRadians = resolvedSlope,
      runCyclePhase = state.runCyclePhase + phaseAdvance,
      boostTimer = updatedBoostTimer,
      lowGravityTimer = nextLowGravTimer,
      blitzTimer = nextBlitzTimer,
      superAuraTimer = nextSuperAuraTimer,
      invulnerableTimer = currentInvuln,
      hasTitanShield = currentHasShield,
      landingSquashTimer = nextLandingSquash
    )
  }

  private fun killStickman(state: StickmanState, x: Float, y: Float): StickmanState {
    return state.copy(
      footX = x,
      footY = y,
      velocityX = 0f,
      velocityY = 0f,
      isGrounded = false,
      isAlive = false,
      landingSquashTimer = 0f,
      deathTimer = 0.01f
    )
  }

  /**
   * Updates Breakable Rock Walls, Leaping Lava Monsters, and the Colossal Lava Dragon Boss (Ignis Titan).
   */
  fun stepCombatEntities(
    stickman: StickmanState,
    level: LevelDefinition,
    rockWalls: List<BreakableRockWall>,
    lavaMonsters: List<LeapingLavaMonster>,
    ignisBoss: IgnisTitanBoss,
    animTime: Float,
    dt: Float
  ): CombatStepResult {
    if (!stickman.isAlive || stickman.hasReachedFinish) {
      return CombatStepResult(
        stickman = stickman,
        rockWalls = rockWalls,
        lavaMonsters = lavaMonsters,
        ignisBoss = ignisBoss
      )
    }

    var updatedStickman = stickman
    var scoreDelta = 0
    var inkDelta = 0f
    var bossTookDamage = false
    var playerTookBossBite = false
    var wallSmashed = false
    var monsterBlasted = false
    var shieldBroken = false
    val extraParticles = mutableListOf<SketchParticle>()

    val isSuperPowered = updatedStickman.superAuraTimer > 0f ||
      updatedStickman.boostTimer > 0f ||
      updatedStickman.blitzTimer > 0f

    val runnerLeftX = updatedStickman.footX - 15f
    val runnerRightX = updatedStickman.footX + 15f
    val runnerTopY = updatedStickman.footY - WorldConstants.STICKMAN_HEIGHT + 4f
    val runnerBotY = updatedStickman.footY
    val bodySegTop = Vec2(updatedStickman.footX, runnerTopY)
    val bodySegBot = Vec2(updatedStickman.footX, runnerBotY)

    // 1. Breakable Rock Walls
    val nextWalls = rockWalls.map { wall ->
      if (wall.isSmashed) return@map wall
      val wallLeft = wall.x - wall.width * 0.5f
      val wallRight = wall.x + wall.width * 0.5f
      val overlapsX = runnerRightX >= wallLeft && runnerLeftX <= wallRight
      val overlapsY = runnerBotY >= wall.topY + 4f && runnerTopY <= wall.bottomY - 4f

      if (overlapsX && overlapsY) {
        if (isSuperPowered) {
          wallSmashed = true
          scoreDelta += (25 * level.scoreMultiplier).toInt()
          extraParticles.addAll(
            createLineCompletedParticles(
              listOf(
                Vec2(wall.x, (wall.topY + wall.bottomY) * 0.5f),
                Vec2(wall.x, wall.topY + 12f)
              )
            )
          )
          wall.copy(isSmashed = true)
        } else if (updatedStickman.invulnerableTimer > 0f) {
          wall
        } else if (updatedStickman.hasTitanShield) {
          shieldBroken = true
          updatedStickman = updatedStickman.copy(
            hasTitanShield = false,
            invulnerableTimer = 1.1f,
            velocityY = -300f,
            isGrounded = false
          )
          wall.copy(isSmashed = true)
        } else {
          updatedStickman = killStickman(updatedStickman, updatedStickman.footX, updatedStickman.footY)
          wall
        }
      } else {
        wall
      }
    }

    // 2. Leaping Lava Monsters
    val nextMonsters = lavaMonsters.map { monster ->
      if (monster.isDefeated || !updatedStickman.isAlive) return@map monster
      val my = monster.currentYAt(animTime)
      if (my >= WorldConstants.LAVA_SURFACE_Y - 6f) return@map monster
      val mCenter = Vec2(monster.x, my)
      val dist = distancePointToSegment(mCenter, bodySegTop, bodySegBot)

      if (dist <= monster.radius + 13f) {
        if (isSuperPowered) {
          monsterBlasted = true
          scoreDelta += (35 * level.scoreMultiplier).toInt()
          inkDelta += level.maxInkPerLevel * 0.15f
          extraParticles.addAll(createLineCompletedParticles(listOf(mCenter)))
          monster.copy(isDefeated = true)
        } else if (updatedStickman.invulnerableTimer > 0f) {
          monster
        } else if (updatedStickman.hasTitanShield) {
          shieldBroken = true
          updatedStickman = updatedStickman.copy(
            hasTitanShield = false,
            invulnerableTimer = 1.1f,
            velocityY = -320f,
            isGrounded = false
          )
          monster.copy(isDefeated = true)
        } else {
          updatedStickman = killStickman(updatedStickman, updatedStickman.footX, updatedStickman.footY)
          monster
        }
      } else {
        monster
      }
    }

    // 3. Colossal Lava Dragon Boss (Ignis Titan) with Decoupled Velocity & I-Frame Recoil
    var nextBoss = ignisBoss
    if (!nextBoss.isDefeated && updatedStickman.isAlive) {
      val nextHurtCd = (nextBoss.hurtCooldown - dt).coerceAtLeast(0f)
      val nextBiteCd = (nextBoss.biteCooldown - dt).coerceAtLeast(0f)

      // Decoupled chase velocity: rubber-bands to a menacing ~155..220px stalking distance
      // behind a full-speed runner and only closes in for a bite if the runner slows down or stalls.
      val referenceSpeed = max(level.runSpeed, updatedStickman.velocityX)
      val chaseRatio = when {
        level.dailyModifier == "Turbo Ignis" -> 1.06f
        level.levelNumber == 1 -> 1.01f
        else -> 1.03f
      }
      val gap = updatedStickman.footX - nextBoss.x

      val catchupBoost = when {
        gap > 380f -> referenceSpeed * 1.28f
        gap > 165f -> level.runSpeed * chaseRatio
        gap > 75f -> level.runSpeed * 0.98f
        else -> level.runSpeed * 0.94f
      }

      val nextKnockbackVx = nextBoss.knockbackVx * (1f - 3.2f * dt).coerceIn(0f, 1f)
      val effectiveVx = catchupBoost + nextKnockbackVx
      var nextBossX = nextBoss.x + effectiveVx * dt
      // Prevent boss from teleporting ahead of the runner
      if (nextBossX > updatedStickman.footX - 18f) {
        nextBossX = updatedStickman.footX - 18f
      }

      val targetBossY = (updatedStickman.footY - 34f + sin(animTime * 3.4f) * 18f)
        .coerceIn(130f, WorldConstants.LAVA_SURFACE_Y - 56f)
      val nextBossY = nextBoss.y + (targetBossY - nextBoss.y) * (4.5f * dt).coerceIn(0f, 1f)

      var nextBossHp = nextBoss.hp
      var finalHurtCd = nextHurtCd
      var finalBiteCd = nextBiteCd
      var finalKnockbackVx = nextKnockbackVx

      val distX = abs(updatedStickman.footX - nextBossX)
      val distY = abs((updatedStickman.footY - 28f) - nextBossY)
      val hitRangeX = if (isSuperPowered) 115f else 58f

      if (distX < hitRangeX && distY < 72f) {
        if (isSuperPowered) {
          if (finalHurtCd <= 0f) {
            finalHurtCd = 0.9f
            finalKnockbackVx = -360f
            nextBossHp = (nextBossHp - 1).coerceAtLeast(0)
            bossTookDamage = true
            val pts = if (nextBossHp == 0) 300 else 100
            scoreDelta += (pts * level.scoreMultiplier).toInt()
            extraParticles.addAll(createLineCompletedParticles(listOf(Vec2(nextBossX + 24f, nextBossY))))
          }
        } else if (finalBiteCd <= 0f && updatedStickman.invulnerableTimer <= 0f) {
          finalBiteCd = 2.6f
          finalKnockbackVx = -280f
          playerTookBossBite = true
          if (updatedStickman.hasTitanShield) {
            shieldBroken = true
            updatedStickman = updatedStickman.copy(
              hasTitanShield = false,
              invulnerableTimer = 1.2f
            )
          } else {
            inkDelta -= level.maxInkPerLevel * 0.18f
            updatedStickman = updatedStickman.copy(
              velocityY = if (level.levelNumber > 1 && updatedStickman.isGrounded) -210f else updatedStickman.velocityY,
              isGrounded = if (level.levelNumber > 1 && updatedStickman.isGrounded) false else updatedStickman.isGrounded,
              invulnerableTimer = 1.2f
            )
          }
        }
      }

      nextBoss = nextBoss.copy(
        x = nextBossX,
        y = nextBossY,
        hp = nextBossHp,
        hurtCooldown = finalHurtCd,
        biteCooldown = finalBiteCd,
        knockbackVx = finalKnockbackVx,
        isDefeated = nextBossHp <= 0
      )
    }

    return CombatStepResult(
      stickman = updatedStickman,
      rockWalls = nextWalls,
      lavaMonsters = nextMonsters,
      ignisBoss = nextBoss,
      scoreDelta = scoreDelta,
      inkDelta = inkDelta,
      bossTookDamage = bossTookDamage,
      playerTookBossBite = playerTookBossBite,
      wallSmashed = wallSmashed,
      monsterBlasted = monsterBlasted,
      shieldBroken = shieldBroken,
      extraParticles = extraParticles
    )
  }

  /**
   * Finds an overhead player-drawn ceiling line that stops upward motion (e.g. inside WIND_UPDRAFT).
   */
  private fun findOverheadCeilingY(
    footX: Float,
    prevHeadY: Float,
    nextHeadY: Float,
    completedLines: List<DrawnLine>
  ): Float? {
    var lowestCeiling: Float? = null
    for (line in completedLines) {
      for (seg in line.segments) {
        val left = if (seg.start.x <= seg.end.x) seg.start else seg.end
        val right = if (seg.start.x <= seg.end.x) seg.end else seg.start
        if (footX < left.x - 10f || footX > right.x + 10f) continue
        val dx = right.x - left.x
        val segY = if (dx < 2f) {
          max(left.y, right.y)
        } else {
          val t = ((footX - left.x) / dx).coerceIn(0f, 1f)
          left.y + (right.y - left.y) * t
        }
        if (prevHeadY >= segY - 12f && nextHeadY <= segY + 12f) {
          if (lowestCeiling == null || segY > lowestCeiling) {
            lowestCeiling = segY
          }
        }
      }
    }
    return lowestCeiling
  }

  private fun findVaultWallHeight(
    x0: Float,
    x1: Float,
    footY: Float,
    completedLines: List<DrawnLine>
  ): Float? {
    val lo = min(x0, x1) - 4f
    val hi = max(x0, x1) + 4f
    for (line in completedLines) {
      for (seg in line.segments) {
        val dx = abs(seg.end.x - seg.start.x)
        val dy = abs(seg.end.y - seg.start.y)
        if (dx > 0.1f && dy / dx <= MAX_CLIMBABLE_SLOPE_RADIANS) continue
        val midX = (seg.start.x + seg.end.x) * 0.5f
        if (midX < lo || midX > hi) continue
        val topY = min(seg.start.y, seg.end.y)
        val botY = max(seg.start.y, seg.end.y)
        if (botY >= footY - 12f && topY < footY - 14f && footY - topY <= 68f) {
          return (footY - topY).coerceIn(22f, 68f)
        }
      }
    }
    return null
  }

  private fun collectSupportingSurfaces(
    footX: Float,
    platforms: List<Platform>,
    completedLines: List<DrawnLine>,
    hazards: List<DynamicHazard> = emptyList(),
    animTime: Float = 0f,
    mapBiome: MapBiome = MapBiome.MAP_1_VOLCANO
  ): List<SurfaceHit> {
    val hits = mutableListOf<SurfaceHit>()
    val minFootX = footX - FOOT_SUPPORT_HALF_WIDTH
    val maxFootX = footX + FOOT_SUPPORT_HALF_WIDTH

    // 1. Static platforms
    for (platform in platforms) {
      if (maxFootX >= platform.startX && minFootX <= platform.endX) {
        hits.add(SurfaceHit(y = platform.topY, slopeRadians = 0f, tool = ChalkTool.WHITE))
      }
    }

    // 1b. Modular LEDGE obstacles (Cyan static/bouncy or Orange crumbling)
    for (hazard in hazards) {
      if (hazard.type == HazardType.LEDGE && hazard.isPlatformSolidAt(animTime)) {
        val (lStartX, lEndX) = hazard.ledgeBoundsAt(animTime, mapBiome)
        if (maxFootX >= lStartX && minFootX <= lEndX) {
          val surfaceTool = if (hazard.ledgeStyle == LedgeStyle.CYAN_BOUNCY) {
            ChalkTool.SPRING
          } else {
            ChalkTool.WHITE
          }
          hits.add(SurfaceHit(y = hazard.topY, slopeRadians = 0f, tool = surfaceTool))
        }
      }
    }

    // 2. Player-drawn line segments (One-Way Non-Blocking Ink Physics)
    for (line in completedLines) {
      val tool = line.tool
      for (seg in line.segments) {
        val left = if (seg.start.x <= seg.end.x) seg.start else seg.end
        val right = if (seg.start.x <= seg.end.x) seg.end else seg.start
        val dx = right.x - left.x
        val dy = right.y - left.y

        if (maxFootX < left.x - LINE_ENDPOINT_MARGIN || minFootX > right.x + LINE_ENDPOINT_MARGIN) continue

        // Ignore near-vertical line segments (abs(segDy) > abs(segDx) * 2.8) unless bouncy trampoline
        if (abs(dy) > abs(dx) * 2.8f && tool != ChalkTool.SPRING) {
          continue
        }

        if (dx < 3f) {
          if (tool == ChalkTool.SPRING || abs(dy) <= 24f) {
            hits.add(SurfaceHit(y = min(left.y, right.y), slopeRadians = 0f, tool = tool))
          }
          continue
        }

        val slopeAngle = atan2(dy, dx)
        val clampedSlope = slopeAngle.coerceIn(-MAX_CLIMBABLE_SLOPE_RADIANS, MAX_CLIMBABLE_SLOPE_RADIANS)
        val sampleX = footX.coerceIn(left.x, right.x)
        val t = ((sampleX - left.x) / dx).coerceIn(0f, 1f)
        var surfaceY = left.y + dy * t
        for (platform in platforms) {
          if (
            sampleX >= platform.startX &&
              sampleX <= platform.endX &&
              surfaceY > platform.topY &&
              surfaceY <= platform.topY + PLATFORM_MANTLE_TOLERANCE
          ) {
            surfaceY = platform.topY
          }
        }
        hits.add(SurfaceHit(y = surfaceY, slopeRadians = clampedSlope, tool = tool))
      }
    }

    return hits
  }

  fun cutLineNearPoint(
    lines: List<DrawnLine>,
    tapPoint: Vec2,
    reach: Float = 36f
  ): Pair<List<DrawnLine>, Float> {
    var bestLineIndex = -1
    var bestPointIndex = -1
    var bestDist = reach

    for (i in lines.indices) {
      val pts = lines[i].points
      for (j in pts.indices) {
        val d = pts[j].distanceTo(tapPoint)
        if (d <= bestDist) {
          bestDist = d
          bestLineIndex = i
          bestPointIndex = j
        }
      }
    }

    if (bestLineIndex < 0) return lines to 0f

    val targetLine = lines[bestLineIndex]
    val keepCount = if (bestPointIndex > 0 && bestPointIndex < targetLine.points.size - 1) {
      bestPointIndex + 1
    } else {
      bestPointIndex
    }
    val removedPoints = targetLine.points.subList(bestPointIndex, targetLine.points.size)
    val removedLength = calculatePolylineLength(removedPoints).coerceAtLeast(
      if (bestPointIndex > 0) targetLine.points[bestPointIndex - 1].distanceTo(targetLine.points[bestPointIndex]) else 0f
    )

    val keptPoints = targetLine.points.subList(0, keepCount)
    val updatedLines = lines.toMutableList()
    if (keptPoints.size < 2) {
      updatedLines.removeAt(bestLineIndex)
    } else {
      updatedLines[bestLineIndex] = targetLine.copy(points = keptPoints)
    }

    return updatedLines to removedLength
  }

  fun stepScribblers(
    scribblers: List<ScribblerEnemy>,
    stickman: StickmanState,
    platforms: List<Platform>,
    completedLines: List<DrawnLine>,
    dt: Float
  ): Pair<List<ScribblerEnemy>, StickmanState> {
    if (scribblers.isEmpty()) return scribblers to stickman

    var updatedStickman = stickman
    val nextScribblers = scribblers.mapNotNull { enemy ->
      if (!enemy.isAlive) return@mapNotNull null

      if (enemy.isTumbling) {
        val nextVy = enemy.tumbleVy + WorldConstants.GRAVITY * dt
        val nextY = enemy.y + nextVy * dt
        val nextX = enemy.x - enemy.facing * 160f * dt
        if (nextY > WorldConstants.WORLD_HEIGHT + 40f) {
          null
        } else {
          enemy.copy(x = nextX, y = nextY, tumbleVy = nextVy, runPhase = enemy.runPhase + dt * 14f)
        }
      } else {
        val dir = if (stickman.footX < enemy.x) -1f else 1f
        val active = abs(stickman.footX - enemy.x) < 620f
        val moveVx = if (active) dir * enemy.speed else 0f
        val nextX = enemy.x + moveVx * dt

        val wallHit = findVaultWallHeight(enemy.x, nextX + dir * 10f, enemy.y, completedLines)
        if (wallHit != null) {
          return@mapNotNull enemy.copy(
            isTumbling = true,
            tumbleVy = -380f,
            facing = dir
          )
        }

        val support = collectSupportingSurfaces(nextX, platforms, completedLines)
          .filter { it.y >= enemy.y - 20f && it.y <= enemy.y + 20f }
          .minByOrNull { it.y }

        if (support == null) {
          enemy.copy(x = nextX, isTumbling = true, tumbleVy = 60f)
        } else {
          val distX = abs(updatedStickman.footX - nextX)
          val distY = abs(updatedStickman.footY - support.y)
          if (distX < 24f && distY < 44f && updatedStickman.isAlive) {
            if (updatedStickman.boostTimer > 0f || updatedStickman.blitzTimer > 0f || updatedStickman.superAuraTimer > 0f || updatedStickman.velocityY > 120f) {
              if (updatedStickman.velocityY > 120f) {
                updatedStickman = updatedStickman.copy(velocityY = -360f, isGrounded = false)
              }
              return@mapNotNull enemy.copy(x = nextX, y = support.y, isTumbling = true, tumbleVy = -420f)
            } else {
              updatedStickman = updatedStickman.copy(
                velocityY = -340f,
                isGrounded = false
              )
              return@mapNotNull enemy.copy(x = nextX, y = support.y, isTumbling = true, tumbleVy = -320f)
            }
          }
          enemy.copy(
            x = nextX,
            y = support.y,
            facing = dir,
            runPhase = enemy.runPhase + dt * (abs(moveVx) / 15f + 2f)
          )
        }
      }
    }

    return nextScribblers to updatedStickman
  }

  fun distancePointToSegment(p: Vec2, a: Vec2, b: Vec2): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val lenSq = abx * abx + aby * aby
    if (lenSq <= 0.0001f) return p.distanceTo(a)
    val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / lenSq).coerceIn(0f, 1f)
    val projX = a.x + t * abx
    val projY = a.y + t * aby
    val dx = p.x - projX
    val dy = p.y - projY
    return sqrt(dx * dx + dy * dy)
  }

  fun collectChalkBitsAlongStroke(
    bits: List<ChalkBit>,
    strokePoints: List<Vec2>,
    collectionRadius: Float = 48f
  ): Pair<List<ChalkBit>, List<ChalkBit>> {
    if (bits.isEmpty() || strokePoints.isEmpty()) return bits to emptyList()

    val newlyCollected = mutableListOf<ChalkBit>()
    val updated = bits.map { bit ->
      if (bit.collected) return@map bit
      val bitPos = Vec2(bit.x, bit.y)
      val touched = if (strokePoints.size == 1) {
        strokePoints.first().distanceTo(bitPos) <= collectionRadius
      } else {
        var hitSegment = false
        for (i in 0 until strokePoints.size - 1) {
          if (distancePointToSegment(bitPos, strokePoints[i], strokePoints[i + 1]) <= collectionRadius) {
            hitSegment = true
            break
          }
        }
        hitSegment
      }

      if (touched) {
        val collectedBit = bit.copy(collected = true)
        newlyCollected.add(collectedBit)
        collectedBit
      } else {
        bit
      }
    }

    return updated to newlyCollected
  }

  fun updateChalkBits(
    bits: List<ChalkBit>,
    stickman: StickmanState,
    comboCount: Int,
    dt: Float,
    activeLines: List<DrawnLine> = emptyList(),
    hasInkMagnetPerk: Boolean = false
  ): Pair<List<ChalkBit>, List<ChalkBit>> {
    if (bits.isEmpty()) return bits to emptyList()

    val newlyCollected = mutableListOf<ChalkBit>()
    val baseMagnet = if (comboCount >= 2 || stickman.blitzTimer > 0f || stickman.superAuraTimer > 0f) 135f else 56f
    val magnetRadius = if (hasInkMagnetPerk) baseMagnet * 1.35f else baseMagnet
    val bodyCenterX = stickman.footX
    val bodyCenterY = stickman.footY - WorldConstants.STICKMAN_HEIGHT * 0.5f

    val updated = bits.map { bit ->
      if (bit.collected) return@map bit
      val bitPos = Vec2(bit.x, bit.y)
      val dx = bodyCenterX - bit.x
      val dy = bodyCenterY - bit.y
      val distToStickman = sqrt(dx * dx + dy * dy)

      var touchedByLine = false
      for (line in activeLines) {
        val pts = line.points
        if (pts.size == 1) {
          if (pts.first().distanceTo(bitPos) <= 48f) {
            touchedByLine = true
            break
          }
        } else {
          for (i in 0 until pts.size - 1) {
            if (distancePointToSegment(bitPos, pts[i], pts[i + 1]) <= 48f) {
              touchedByLine = true
              break
            }
          }
          if (touchedByLine) break
        }
      }

      if (distToStickman <= 42f || touchedByLine) {
        val collectedBit = bit.copy(collected = true)
        newlyCollected.add(collectedBit)
        collectedBit
      } else if (distToStickman <= magnetRadius && distToStickman > 1f) {
        val pullSpeed = 295f * dt
        bit.copy(
          x = bit.x + (dx / distToStickman) * pullSpeed,
          y = bit.y + (dy / distToStickman) * pullSpeed
        )
      } else {
        bit
      }
    }

    return updated to newlyCollected
  }

  fun updateEmbers(
    existing: List<EmberParticle>,
    cameraX: Float,
    visibleWorldWidth: Float,
    dt: Float
  ): List<EmberParticle> {
    val updated = existing.mapNotNull { ember ->
      val nextY = ember.worldY + ember.velocityY * dt
      val nextX = ember.worldX + ember.velocityX * dt
      val nextAlpha = ember.alpha - dt * 0.55f
      if (nextAlpha <= 0.05f || nextY < WorldConstants.DEFAULT_GROUND_Y - 60f) {
        null
      } else {
        ember.copy(worldX = nextX, worldY = nextY, alpha = nextAlpha)
      }
    }.toMutableList()

    val targetCount = 12
    while (updated.size < targetCount) {
      val spawnX = cameraX - 60f + Random.nextFloat() * (visibleWorldWidth + 180f)
      val spawnY = WorldConstants.LAVA_SURFACE_Y - Random.nextFloat() * 14f
      updated.add(
        EmberParticle(
          worldX = spawnX,
          worldY = spawnY,
          velocityY = -(35f + Random.nextFloat() * 65f),
          velocityX = (Random.nextFloat() - 0.5f) * 32f,
          radius = 2.2f + Random.nextFloat() * 3.8f,
          alpha = 0.65f + Random.nextFloat() * 0.35f,
          isYellow = Random.nextBoolean()
        )
      )
    }

    return updated
  }

  fun updateSketchParticles(
    existing: List<SketchParticle>,
    stickman: StickmanState,
    justLanded: Boolean,
    dt: Float,
    emitFootsteps: Boolean = true
  ): List<SketchParticle> {
    val next = existing.mapNotNull { p ->
      val fadeRate = when (p.type) {
        SketchParticleType.FOOTSTEP_CHALK_PUFF -> 2.2f
        SketchParticleType.PENCIL_SHAVING -> 1.9f
        SketchParticleType.LINE_LOCK_SPARK -> 2.4f
      }
      val gravityPull = when (p.type) {
        SketchParticleType.FOOTSTEP_CHALK_PUFF -> -18f
        SketchParticleType.PENCIL_SHAVING -> 140f
        SketchParticleType.LINE_LOCK_SPARK -> 45f
      }
      val nextAlpha = p.alpha - dt * fadeRate
      if (nextAlpha <= 0.04f) {
        null
      } else {
        p.copy(
          worldX = p.worldX + p.velocityX * dt,
          worldY = p.worldY + p.velocityY * dt,
          velocityY = p.velocityY + gravityPull * dt,
          alpha = nextAlpha,
          rotationRadians = p.rotationRadians + p.angularVelocity * dt
        )
      }
    }.toMutableList()

    if (emitFootsteps && stickman.isAlive && !stickman.hasReachedFinish) {
      if (stickman.isGrounded && stickman.velocityX > 20f && (existing.isEmpty() || Random.nextFloat() < 0.22f)) {
        next.add(
          SketchParticle(
            worldX = stickman.footX - 6f - Random.nextFloat() * 8f,
            worldY = stickman.footY - 2f - Random.nextFloat() * 4f,
            velocityX = -(35f + Random.nextFloat() * 55f),
            velocityY = -(12f + Random.nextFloat() * 34f),
            size = 2.6f + Random.nextFloat() * 3.2f,
            alpha = 0.72f + Random.nextFloat() * 0.22f,
            rotationRadians = Random.nextFloat() * 6.28f,
            angularVelocity = (Random.nextFloat() - 0.5f) * 6f,
            type = SketchParticleType.FOOTSTEP_CHALK_PUFF
          )
        )
      }

      if (justLanded) {
        for (i in 0 until 8) {
          val dir = if (i % 2 == 0) -1f else 1f
          next.add(
            SketchParticle(
              worldX = stickman.footX + dir * (2f + Random.nextFloat() * 8f),
              worldY = stickman.footY - 2f,
              velocityX = dir * (25f + Random.nextFloat() * 75f) - 20f,
              velocityY = -(18f + Random.nextFloat() * 48f),
              size = 3.2f + Random.nextFloat() * 3.5f,
              alpha = 0.88f,
              rotationRadians = Random.nextFloat() * 6.28f,
              angularVelocity = (Random.nextFloat() - 0.5f) * 8f,
              type = SketchParticleType.FOOTSTEP_CHALK_PUFF
            )
          )
        }
      }
    }

    return if (next.size > 120) next.takeLast(120) else next
  }

  fun createPencilTipParticles(tip: Vec2, count: Int = 2): List<SketchParticle> {
    return List(count) {
      SketchParticle(
        worldX = tip.x + (Random.nextFloat() - 0.5f) * 8f,
        worldY = tip.y + (Random.nextFloat() - 0.5f) * 8f,
        velocityX = (Random.nextFloat() - 0.5f) * 70f,
        velocityY = -(10f + Random.nextFloat() * 45f),
        size = 3.0f + Random.nextFloat() * 3.0f,
        alpha = 0.85f,
        rotationRadians = Random.nextFloat() * 6.28f,
        angularVelocity = (Random.nextFloat() - 0.5f) * 10f,
        type = SketchParticleType.PENCIL_SHAVING
      )
    }
  }

  fun createLineCompletedParticles(points: List<Vec2>): List<SketchParticle> {
    if (points.isEmpty()) return emptyList()
    val result = mutableListOf<SketchParticle>()

    for (i in points.indices) {
      val pt = points[i]
      val sparkCount = if (points.size <= 4) 3 else 2
      for (s in 0 until sparkCount) {
        result.add(
          SketchParticle(
            worldX = pt.x + (Random.nextFloat() - 0.5f) * 10f,
            worldY = pt.y + (Random.nextFloat() - 0.5f) * 10f,
            velocityX = (Random.nextFloat() - 0.5f) * 85f,
            velocityY = (Random.nextFloat() - 0.5f) * 85f - 15f,
            size = 4.2f + Random.nextFloat() * 3.8f,
            alpha = 0.95f,
            rotationRadians = Random.nextFloat() * 3.14f,
            angularVelocity = (Random.nextFloat() - 0.5f) * 9f,
            type = SketchParticleType.LINE_LOCK_SPARK
          )
        )
      }
    }

    return result
  }
}
