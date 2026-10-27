package com.example

import com.example.game.level.LevelCatalog
import com.example.game.model.DrawnLine
import com.example.game.model.SketchParticle
import com.example.game.model.SketchParticleType
import com.example.game.model.StickmanState
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import com.example.game.physics.PhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun `stickman automatically runs right and falls into lava on first gap without drawn bridge`() {
    val level1 = LevelCatalog.getLevel(1)
    var stickman = StickmanState(
      footX = level1.spawnPosition.x,
      footY = level1.spawnPosition.y,
      velocityX = level1.runSpeed,
      isGrounded = true
    )

    val startX = stickman.footX
    var hitLava = false

    // Simulate up to 6 seconds without any player-drawn bridges
    for (frame in 0 until 360) {
      val result = PhysicsEngine.stepPhysics(
        stickman = stickman,
        level = level1,
        completedLines = emptyList(),
        deltaSeconds = 1f / 60f
      )
      stickman = result.stickman
      if (result.hitLava) {
        hitLava = true
        break
      }
    }

    assertTrue("Stickman should have moved horizontally to the right", stickman.footX > startX + 400f)
    assertTrue("Stickman must fall into lava when no bridge is drawn over Gap 1", hitLava)
    assertFalse("Stickman should not be alive after touching lava", stickman.isAlive)
    assertEquals(WorldConstants.LAVA_SURFACE_Y, stickman.footY, 0.5f)
  }

  @Test
  fun `stickman collides with drawn bridges across both gaps and completes level 1`() {
    val level1 = LevelCatalog.getLevel(1)
    var stickman = StickmanState(
      footX = level1.spawnPosition.x,
      footY = level1.spawnPosition.y,
      velocityX = level1.runSpeed,
      isGrounded = true
    )

    // Draw bridge across Gap 1 (560..860) and Gap 2 (1280..1760)
    val bridge1Points = PhysicsEngine.smoothStrokePoints(
      listOf(
        Vec2(540f, WorldConstants.DEFAULT_GROUND_Y),
        Vec2(710f, WorldConstants.DEFAULT_GROUND_Y - 18f),
        Vec2(880f, WorldConstants.DEFAULT_GROUND_Y)
      )
    )
    val bridge2Points = PhysicsEngine.smoothStrokePoints(
      listOf(
        Vec2(1260f, WorldConstants.DEFAULT_GROUND_Y),
        Vec2(1520f, WorldConstants.DEFAULT_GROUND_Y - 24f),
        Vec2(1780f, WorldConstants.DEFAULT_GROUND_Y)
      )
    )

    val completedLines = listOf(
      DrawnLine(id = 1L, points = bridge1Points, isCompleted = true),
      DrawnLine(id = 2L, points = bridge2Points, isCompleted = true)
    )

    var reachedFinish = false
    for (frame in 0 until 900) {
      val result = PhysicsEngine.stepPhysics(
        stickman = stickman,
        level = level1,
        completedLines = completedLines,
        deltaSeconds = 1f / 60f
      )
      stickman = result.stickman
      assertFalse("Stickman should not fall into lava when bridges are drawn", result.hitLava)
      if (result.reachedFinish) {
        reachedFinish = true
        break
      }
    }

    assertTrue("Stickman should cross the finish line when both gaps are bridged", reachedFinish)
    assertTrue(stickman.hasReachedFinish)
    assertTrue(stickman.footX >= level1.finishLineX)
  }

  @Test
  fun `stroke drawing enforces maximum length per attempt`() {
    val maxStroke = 300f
    val remainingInk = 1000f
    var points = emptyList<Vec2>()

    points = PhysicsEngine.appendPointToStroke(points, Vec2(100f, 300f), maxStroke, remainingInk)
    points = PhysicsEngine.appendPointToStroke(points, Vec2(250f, 300f), maxStroke, remainingInk)
    points = PhysicsEngine.appendPointToStroke(points, Vec2(600f, 300f), maxStroke, remainingInk)

    val totalLength = PhysicsEngine.calculatePolylineLength(points)
    assertEquals(maxStroke, totalLength, 1.0f)
    assertEquals(400f, points.last().x, 1.0f)
  }

  @Test
  fun `running and creating drawn lines emit hand-drawn sketch particles`() {
    val stickman = StickmanState(
      footX = 200f,
      footY = WorldConstants.DEFAULT_GROUND_Y,
      velocityX = 175f,
      isGrounded = true
    )

    var particles = emptyList<SketchParticle>()
    for (i in 0 until 20) {
      particles = PhysicsEngine.updateSketchParticles(
        existing = particles,
        stickman = stickman,
        justLanded = (i == 0),
        dt = 1f / 60f,
        emitFootsteps = true
      )
    }
    assertTrue(
      "Running and landing should emit FOOTSTEP_CHALK_PUFF particles",
      particles.any { it.type == SketchParticleType.FOOTSTEP_CHALK_PUFF }
    )

    val tipShavings = PhysicsEngine.createPencilTipParticles(Vec2(600f, 400f), count = 3)
    assertEquals(3, tipShavings.size)
    assertTrue(tipShavings.all { it.type == SketchParticleType.PENCIL_SHAVING })

    val lineSparks = PhysicsEngine.createLineCompletedParticles(
      listOf(Vec2(550f, 420f), Vec2(700f, 410f), Vec2(850f, 420f))
    )
    assertTrue(
      "Completing a drawn line should emit LINE_LOCK_SPARK particles",
      lineSparks.isNotEmpty() && lineSparks.all { it.type == SketchParticleType.LINE_LOCK_SPARK }
    )
  }

  @Test
  fun `ink meter capacity is limited and drawing respects remaining ink`() {
    val level1 = LevelCatalog.getLevel(1)
    assertTrue("Max ink capacity must be limited for resource management", level1.maxInkPerLevel <= 1200f)
    assertTrue("Ink recharge rate must be positive", level1.inkRechargeRate > 0f)

    // With 150f remaining ink, a 400f stroke attempt should be clamped to 150f
    val remainingInk = 150f
    val maxStroke = 800f
    var points = emptyList<Vec2>()
    points = PhysicsEngine.appendPointToStroke(points, Vec2(100f, 300f), maxStroke, remainingInk)
    points = PhysicsEngine.appendPointToStroke(points, Vec2(500f, 300f), maxStroke, remainingInk)

    val drawnLength = PhysicsEngine.calculatePolylineLength(points)
    assertEquals(remainingInk, drawnLength, 1.0f)
  }

  @Test
  fun `evolved mechanics support spring bounce chalk turbo boost and tap to cut refund`() {
    val level1 = LevelCatalog.getLevel(1)
    val groundY = WorldConstants.DEFAULT_GROUND_Y

    // 1. Spring Bounce line launches stickman upward
    val springLine = DrawnLine(
      id = 10L,
      points = listOf(Vec2(180f, groundY - 2f), Vec2(260f, groundY - 2f)),
      tool = com.example.game.model.ChalkTool.SPRING
    )
    var stickman = StickmanState(
      footX = 175f,
      footY = groundY,
      velocityX = level1.runSpeed,
      isGrounded = true
    )
    var bounced = false
    for (i in 0 until 15) {
      val res = PhysicsEngine.stepPhysics(stickman, level1, listOf(springLine), 1f / 60f)
      stickman = res.stickman
      if (res.justBouncedSpring || stickman.velocityY < -400f) {
        bounced = true
        break
      }
    }
    assertTrue("Spring bounce chalk should launch stickman into a high jump", bounced)

    // 2. Tap-to-cut snips drawn line from the tapped point onward and returns removed length
    val longLine = DrawnLine(
      id = 11L,
      points = listOf(Vec2(100f, 300f), Vec2(200f, 300f), Vec2(300f, 300f), Vec2(400f, 300f)),
      tool = com.example.game.model.ChalkTool.WHITE
    )
    val (remainingLines, removedLen) = PhysicsEngine.cutLineNearPoint(
      lines = listOf(longLine),
      tapPoint = Vec2(202f, 300f),
      reach = 30f
    )
    assertEquals(1, remainingLines.size)
    assertTrue("Removed length should be ~200f from x=200..400", removedLen >= 190f)
  }
}
