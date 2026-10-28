package com.example.game.physics

import com.example.game.model.ChalkBit
import com.example.game.model.ChalkTool
import com.example.game.model.DrawnLine
import com.example.game.model.DynamicHazard
import com.example.game.model.EmberParticle
import com.example.game.model.HazardType
import com.example.game.model.LedgeStyle
import com.example.game.model.LevelDefinition
import com.example.game.model.MapBiome
import com.example.game.model.Platform
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
 * Pure 2D physics, path-smoothing, and collision engine for Draw to Survive.
 *
 * Supports:
 * - Multi-substep collision resolution so the stickman never tunnels through thin drawn lines
 * - Overhead ceiling collision so drawn roofs protect against FALLING_SPIKE and WIND_UPDRAFT
 * - All 8 modular obstacles: PENDULUM, LEDGE, LAVA_GEYSER, FALLING_SPIKE, LASER_GATE,
 *   SAW_TRACK, NO_DRAW_ZONE, and WIND_UPDRAFT
 * - Multi-Map environmental physics (MAP_1_VOLCANO, MAP_2_CRYO_LAB low gravity, MAP_3_CYBER_GRID moving platforms)
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
    val hitLava: Boolean = false,
    val reachedFinish: Boolean = false
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
          restitution = if (line.tool == ChalkTool.SPRING) 1.15f else 0f,
          friction = 0f,
          tool = line.tool
        )
      }
    }
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
    // Standard Chalk (WHITE) is blocked inside NO_DRAW_ZONE; players must place Bounce before the zone
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

    for (step in 0 until steps) {
      val prevGrounded = current.isGrounded
      val prevFootY = current.footY
      val prevVy = current.velocityY

      current = stepSingleSubstep(
        state = current,
        level = level,
        platforms = level.platforms,
        completedLines = completedLines,
        dt = subDt,
        animTime = animTime + step * subDt
      )

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

    val slopeFactor = if (state.isGrounded && state.slopeRadians < 0f) {
      cos(state.slopeRadians.coerceIn(-1.05f, 0f)).coerceIn(0.62f, 1f)
    } else {
      1f
    }

    val boostMult = when {
      nextBlitzTimer > 0f -> 1.85f
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

    if (state.isGrounded) {
      val vaultHeight = findVaultWallHeight(prevX, resolvedX + 8f, prevY, completedLines)
      if (vaultHeight != null) {
        vy = -sqrt(2f * WorldConstants.GRAVITY * (vaultHeight + 24f)).coerceIn(280f, 520f)
      } else {
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
    }

    // Apply MapBiome gravity multiplier (e.g. MAP_2_CRYO_LAB low gravity) + LOW GRAV callout modifier
    val gravityScale = (if (nextLowGravTimer > 0f) 0.48f else 1f) * level.mapBiome.gravityMult
    vy = (vy + WorldConstants.GRAVITY * gravityScale * dt).coerceAtMost(WorldConstants.MAX_FALL_SPEED)

    // Apply WIND_UPDRAFT upward lift if stickman is inside an active Wind Updraft column
    var inUpdraft = false
    for (hazard in level.hazards) {
      if (hazard.type == HazardType.WIND_UPDRAFT) {
        if (resolvedX in hazard.startX..hazard.endX && prevY >= hazard.topY && prevY - WorldConstants.STICKMAN_HEIGHT <= hazard.bottomY) {
          vy = (vy - hazard.updraftAcceleration * dt).coerceAtLeast(-390f)
          inUpdraft = true
        }
      }
    }

    var freeFallY = prevY + vy * dt
    var grounded = if (inUpdraft && vy < -40f) false else state.isGrounded

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

    if (!isGroundedNow && vy >= 0f) {
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

    for (platform in platforms) {
      if (resolvedX + FOOT_SUPPORT_HALF_WIDTH >= platform.startX && resolvedX - FOOT_SUPPORT_HALF_WIDTH <= platform.endX) {
        if (
          resolvedY > platform.topY &&
            (prevY <= platform.topY + PLATFORM_MANTLE_TOLERANCE || resolvedY <= platform.topY + PLATFORM_MANTLE_TOLERANCE)
        ) {
          resolvedY = platform.topY
          resolvedVy = 0f
          isGroundedNow = true
          resolvedSlope = 0f
          activeSurfaceTool = ChalkTool.WHITE
        } else if (
          resolvedY > platform.topY + PLATFORM_MANTLE_TOLERANCE &&
            prevX + 6f <= platform.startX &&
            resolvedX + 6f > platform.startX
        ) {
          resolvedX = platform.startX - 6f
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
        resolvedVy = -530f
        isGroundedNow = false
        nextLandingSquash = 0f
      } else if (activeSurfaceTool == ChalkTool.BOOST) {
        updatedBoostTimer = max(updatedBoostTimer, 0.55f)
      }
    }

    // Check modular obstacle collisions (Turbo Boost or Blitz shields the runner)
    if (updatedBoostTimer <= 0f && nextBlitzTimer <= 0f) {
      for (cluster in level.thumbtacks) {
        for (i in 0 until cluster.count) {
          val offset = (i - (cluster.count - 1) / 2f) * cluster.spacing
          val tx = cluster.x + offset
          val ty = cluster.y
          if (abs(resolvedX - tx) < 13f && resolvedY >= ty - 6f && resolvedY - WorldConstants.STICKMAN_HEIGHT <= ty + 8f) {
            return killStickman(state, resolvedX, resolvedY)
          }
        }
      }

      val footSample = Vec2(resolvedX, resolvedY - 12f)
      val torsoSample = Vec2(resolvedX, resolvedY - WorldConstants.STICKMAN_HEIGHT * 0.5f)
      val headSample = Vec2(resolvedX, resolvedY - WorldConstants.STICKMAN_HEIGHT + 10f)
      val stickmanTopY = resolvedY - WorldConstants.STICKMAN_HEIGHT

      for (hazard in level.hazards) {
        when (hazard.type) {
          HazardType.PENDULUM -> {
            val bobPos = hazard.pendulumBobPositionAt(animTime)
            val hitRadius = hazard.bobRadius + 10f
            if (
              bobPos.distanceTo(footSample) <= hitRadius ||
                bobPos.distanceTo(torsoSample) <= hitRadius ||
                bobPos.distanceTo(headSample) <= hitRadius
            ) {
              return killStickman(state, resolvedX, resolvedY)
            }
          }

          HazardType.LAVA_GEYSER -> {
            val ratio = hazard.geyserEruptionRatioAt(animTime)
            if (ratio > 0.14f) {
              val geyserTop = hazard.geyserCurrentTopYAt(animTime)
              val halfW = hazard.geyserWidth * 0.5f + 8f
              if (abs(resolvedX - hazard.x) <= halfW && resolvedY >= geyserTop + 6f) {
                // Allow standing safely ON TOP of a drawn bridge above the geyser top
                if (stickmanTopY <= WorldConstants.LAVA_SURFACE_Y && resolvedY > geyserTop + 10f) {
                  return killStickman(state, resolvedX, resolvedY)
                }
              }
            }
          }

          HazardType.FALLING_SPIKE -> {
            if (abs(resolvedX - hazard.x) <= 18f) {
              val (spikeTipY, blockedByRoof) = hazard.fallingSpikeTipY(resolvedX, completedLines)
              val spikeTopY = (spikeTipY - hazard.spikeHeight).coerceAtLeast(hazard.y)
              if (!blockedByRoof && spikeTipY >= stickmanTopY + 4f && spikeTopY <= resolvedY - 4f) {
                return killStickman(state, resolvedX, resolvedY)
              }
            }
          }

          HazardType.LASER_GATE -> {
            if (hazard.isLaserActiveAt(animTime)) {
              if (abs(resolvedX - hazard.x) <= 13f && resolvedY >= hazard.topY + 6f && stickmanTopY <= hazard.bottomY - 6f) {
                return killStickman(state, resolvedX, resolvedY)
              }
            }
          }

          HazardType.SAW_TRACK -> {
            val sawPos = hazard.sawPositionAt(animTime)
            val hitRadius = hazard.bobRadius + 9f
            if (
              sawPos.distanceTo(footSample) <= hitRadius ||
                sawPos.distanceTo(torsoSample) <= hitRadius ||
                sawPos.distanceTo(headSample) <= hitRadius
            ) {
              return killStickman(state, resolvedX, resolvedY)
            }
          }

          HazardType.LEDGE, HazardType.NO_DRAW_ZONE, HazardType.WIND_UPDRAFT -> Unit
        }
      }
    }

    // Check if stickman fell into the bottom hazard pit (Lava / Cryo Acid / Laser Floor)
    if (resolvedY >= WorldConstants.LAVA_SURFACE_Y) {
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

    // 2. Player-drawn line segments
    for (line in completedLines) {
      val tool = line.tool
      for (seg in line.segments) {
        val left = if (seg.start.x <= seg.end.x) seg.start else seg.end
        val right = if (seg.start.x <= seg.end.x) seg.end else seg.start
        val dx = right.x - left.x
        val dy = right.y - left.y

        if (maxFootX < left.x - LINE_ENDPOINT_MARGIN || minFootX > right.x + LINE_ENDPOINT_MARGIN) continue

        if (dx < 3f) {
          if (abs(dy) <= 36f) {
            hits.add(SurfaceHit(y = min(left.y, right.y), slopeRadians = 0f, tool = tool))
          }
          continue
        }

        val slopeAngle = atan2(dy, dx)
        if (abs(slopeAngle) > 1.44f && abs(dy) > 42f) {
          continue
        }

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
            if (updatedStickman.boostTimer > 0f || updatedStickman.blitzTimer > 0f || updatedStickman.velocityY > 120f) {
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
    activeLines: List<DrawnLine> = emptyList()
  ): Pair<List<ChalkBit>, List<ChalkBit>> {
    if (bits.isEmpty()) return bits to emptyList()

    val newlyCollected = mutableListOf<ChalkBit>()
    val magnetRadius = if (comboCount >= 2 || stickman.blitzTimer > 0f) 135f else 56f
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
        val pullSpeed = 280f * dt
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
