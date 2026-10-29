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

    // Draw continuous bridges across the open platform-free sky from 500..1360 through floating chalk
    val bridge1Points = PhysicsEngine.smoothStrokePoints(
      listOf(
        Vec2(500f, WorldConstants.DEFAULT_GROUND_Y),
        Vec2(720f, WorldConstants.DEFAULT_GROUND_Y - 18f),
        Vec2(940f, WorldConstants.DEFAULT_GROUND_Y)
      )
    )
    val bridge2Points = PhysicsEngine.smoothStrokePoints(
      listOf(
        Vec2(900f, WorldConstants.DEFAULT_GROUND_Y),
        Vec2(1130f, WorldConstants.DEFAULT_GROUND_Y - 20f),
        Vec2(1360f, WorldConstants.DEFAULT_GROUND_Y)
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

  @Test
  fun `getLevelConfig scales all 50 levels according to core progression formulas`() {
    val cfg1 = LevelCatalog.getLevelConfig(1)
    val cfg3 = LevelCatalog.getLevelConfig(3)
    val cfg25 = LevelCatalog.getLevelConfig(25)
    val cfg50 = LevelCatalog.getLevelConfig(50)

    // Level 1 checks
    assertEquals(800f, cfg1.levelLength, 0.1f)
    assertEquals(3.0f, cfg1.baseSpeed, 0.01f)
    assertEquals(80f, cfg1.minGap, 0.1f)
    assertEquals(140f, cfg1.maxGap, 0.1f)
    assertEquals(0f, cfg1.maxDeltaY, 0.01f)
    assertEquals(5.0f, cfg1.lineLifespan, 0.01f)

    // Levels 1-3 flat platforms (deltaY == 0)
    assertEquals(0f, cfg3.maxDeltaY, 0.01f)
    assertEquals(1100f, cfg3.levelLength, 0.1f)

    // Mid-progression checks (Level 25)
    assertEquals(800f + 24 * 150f, cfg25.levelLength, 0.1f)
    assertTrue(cfg25.baseSpeed > 5.0f && cfg25.baseSpeed < 6.5f)
    assertTrue(cfg25.maxDeltaY > 60f && cfg25.maxDeltaY < 90f)
    assertTrue(cfg25.lineLifespan < 4.0f && cfg25.lineLifespan > 3.0f)

    // Level 50 finale checks
    assertEquals(8150f, cfg50.levelLength, 0.1f)
    assertEquals(8.5f, cfg50.baseSpeed, 0.01f)
    assertEquals(180f, cfg50.minGap, 0.5f)
    assertEquals(380f, cfg50.maxGap, 0.5f)
    assertEquals(160f, cfg50.maxDeltaY, 0.5f)
    assertEquals(1.8f, cfg50.lineLifespan, 0.01f)
    assertTrue("Ink capacity should tighten by Level 50", cfg50.maxInkPerLevel < cfg1.maxInkPerLevel)
    assertTrue("Ink regen rate should tighten by Level 50", cfg50.inkRechargeRate < cfg1.inkRechargeRate)

    // Ensure all 50 levels have no intermediate ground platforms and provide floating chalk pickups
    val allConfigs = LevelCatalog.getAllLevelConfigs()
    assertEquals(50, allConfigs.size)
    for (lvl in 1..50) {
      val def = LevelCatalog.getLevel(lvl)
      assertEquals(lvl, def.levelNumber)
      assertEquals("Level $lvl should have only Start and Finish footholds (no middle platforms)", 2, def.platforms.size)
      assertTrue("Level $lvl must have floating chalk bits for additional chalk level", def.chalkBits.isNotEmpty())
      assertTrue("Level $lvl finishLineX must be positive", def.finishLineX > def.spawnPosition.x)
    }

    // Verify drawing a line to floating chalk collects it for additional chalk level
    val level1 = LevelCatalog.getLevel(1)
    val firstBit = level1.chalkBits.first()
    val strokeToChalk = listOf(
      Vec2(firstBit.x - 80f, firstBit.y),
      Vec2(firstBit.x + 20f, firstBit.y)
    )
    val (updatedBits, newlyCollected) = PhysicsEngine.collectChalkBitsAlongStroke(level1.chalkBits, strokeToChalk)
    assertTrue("Drawing a line to floating chalk should collect it", newlyCollected.any { it.id == firstBit.id })
    assertTrue("Collected chalk bit should be marked collected", updatedBits.first { it.id == firstBit.id }.collected)
    assertTrue("Collected chalk bit should grant additional chalk ink", newlyCollected.sumOf { it.inkAmount.toDouble() } >= 200.0)
  }

  @Test
  fun `getHazards and LEVEL_DEFINITIONS support all 8 modular obstacles and multi-map difficulty architecture`() {
    // 1. Verify DIFFICULTY_MODES config object
    assertEquals(4, com.example.game.model.DifficultyMode.DIFFICULTY_MODES.size)
    assertEquals(0.8f, com.example.game.model.DifficultyMode.EASY.speedMult, 0.001f)
    assertEquals(1.0f, com.example.game.model.DifficultyMode.NORMAL.speedMult, 0.001f)
    assertEquals(1.25f, com.example.game.model.DifficultyMode.HARD.speedMult, 0.001f)
    assertEquals(1.5f, com.example.game.model.DifficultyMode.NIGHTMARE.speedMult, 0.001f)

    // 2. Verify MAPS_CONFIG biome registry
    assertEquals(3, com.example.game.model.MapBiome.MAPS_CONFIG.size)
    assertTrue(com.example.game.model.MapBiome.MAPS_CONFIG.containsKey("MAP_1_VOLCANO"))
    assertTrue(com.example.game.model.MapBiome.MAPS_CONFIG.containsKey("MAP_2_CRYO_LAB"))
    assertTrue(com.example.game.model.MapBiome.MAPS_CONFIG.containsKey("MAP_3_CYBER_GRID"))

    // 3. Verify 50 distinct LEVEL_DEFINITIONS
    assertEquals(50, LevelCatalog.LEVEL_DEFINITIONS.size)
    val level49 = LevelCatalog.getLevel(49)
    val level48 = LevelCatalog.getLevel(48)
    assertEquals(2.0f, LevelCatalog.getBlueprint(49).lengthMultiplier, 0.001f)
    assertTrue("Level 49 should be a 2x Marathon length level", level49.finishLineX > level48.finishLineX * 1.8f)

    val level50 = LevelCatalog.getLevel(50)
    assertTrue("Level 50 should contain at least 20 collectible bits/stars for x20 MAGNET finale", level50.chalkBits.size >= 20)

    // 4. Verify all 8 modular obstacle types are generated across the 50 levels
    val allSpawnedTypes = (1..50).flatMap { lvl -> LevelCatalog.getHazards(lvl).map { it.type } }.toSet()
    assertTrue("Should spawn PENDULUM", allSpawnedTypes.contains(com.example.game.model.HazardType.SWINGING_PENDULUM))
    assertTrue("Should spawn LEDGE", allSpawnedTypes.contains(com.example.game.model.HazardType.VANISHING_PLATFORM))
    assertTrue("Should spawn LAVA_GEYSER", allSpawnedTypes.contains(com.example.game.model.HazardType.LAVA_GEYSER))
    assertTrue("Should spawn FALLING_SPIKE", allSpawnedTypes.contains(com.example.game.model.HazardType.FALLING_SPIKE))
    assertTrue("Should spawn LASER_GATE", allSpawnedTypes.contains(com.example.game.model.HazardType.LASER_GATE))
    assertTrue("Should spawn SAW_TRACK", allSpawnedTypes.contains(com.example.game.model.HazardType.SAW_TRACK))
    assertTrue("Should spawn NO_DRAW_ZONE", allSpawnedTypes.contains(com.example.game.model.HazardType.NO_DRAW_ZONE))
    assertTrue("Should spawn WIND_UPDRAFT", allSpawnedTypes.contains(com.example.game.model.HazardType.WIND_UPDRAFT))

    // 5. Verify Level 50 contains all 8 modular obstacle types
    val l50Types = LevelCatalog.getHazards(50).map { it.type }.toSet()
    assertEquals("Level 50 should feature all 8 modular obstacle types", 8, l50Types.size)

    // 6. Verify NO_DRAW_ZONE point helper
    val l24Hazards = LevelCatalog.getHazards(24)
    val noDraw = l24Hazards.first { it.type == com.example.game.model.HazardType.NO_DRAW_ZONE }
    val insidePt = Vec2((noDraw.startX + noDraw.endX) * 0.5f, (noDraw.topY + noDraw.bottomY) * 0.5f)
    assertTrue("Point inside NO_DRAW_ZONE should be detected", noDraw.containsNoDrawPoint(insidePt))
  }

  @Test
  fun `obstacle hitboxes immediately stop the game on contact even during boost or blitz`() {
    val baseLevel = LevelCatalog.getLevel(1)
    val groundY = WorldConstants.DEFAULT_GROUND_Y

    // 1. Swinging Pendulum directly on the runner's torso stops the game even with boostTimer > 0
    val pendulum = com.example.game.model.DynamicHazard(
      id = 900,
      type = com.example.game.model.HazardType.SWINGING_PENDULUM,
      x = 240f,
      y = groundY - 120f,
      armLength = 95f,
      bobRadius = 22f,
      maxAngleRadians = 0f,
      angularSpeed = 0f,
      phaseOffset = 0f
    )
    val levelWithPendulum = baseLevel.copy(hazards = listOf(pendulum), thumbtacks = emptyList())
    val boostedRunner = StickmanState(
      footX = 235f,
      footY = groundY,
      velocityX = baseLevel.runSpeed,
      isGrounded = true,
      boostTimer = 1.5f,
      blitzTimer = 1.0f
    )
    val pendulumStep = PhysicsEngine.stepPhysics(
      stickman = boostedRunner,
      level = levelWithPendulum,
      completedLines = emptyList(),
      deltaSeconds = 1f / 60f,
      animTime = 0f
    )
    assertFalse("Runner hitting a swinging pendulum must immediately stop (isAlive = false) even during boost/blitz", pendulumStep.stickman.isAlive)
    assertTrue("Obstacle hit should trigger game over flag", pendulumStep.hitLava)

    // 2. Active Laser Gate stops the game on contact
    val laserGate = com.example.game.model.DynamicHazard(
      id = 901,
      type = com.example.game.model.HazardType.LASER_GATE,
      x = 240f,
      y = groundY - 60f,
      topY = groundY - 140f,
      bottomY = groundY + 20f,
      laserHalfPeriodSeconds = 1.8f,
      phaseOffset = 0f
    )
    val levelWithLaser = baseLevel.copy(hazards = listOf(laserGate), thumbtacks = emptyList())
    val laserStep = PhysicsEngine.stepPhysics(
      stickman = boostedRunner,
      level = levelWithLaser,
      completedLines = emptyList(),
      deltaSeconds = 1f / 60f,
      animTime = 0.2f
    )
    assertFalse("Runner hitting an active Laser Gate must immediately stop (isAlive = false)", laserStep.stickman.isAlive)
    assertTrue(laserStep.hitLava)

    // 3. Track Buzzsaw stops the game on contact
    val sawTrack = com.example.game.model.DynamicHazard(
      id = 902,
      type = com.example.game.model.HazardType.SAW_TRACK,
      x = 240f,
      y = groundY - 25f,
      trackStartX = 240f,
      trackStartY = groundY - 25f,
      trackEndX = 240f,
      trackEndY = groundY - 25f,
      bobRadius = 22f,
      trackSpeed = 1f
    )
    val levelWithSaw = baseLevel.copy(hazards = listOf(sawTrack), thumbtacks = emptyList())
    val sawStep = PhysicsEngine.stepPhysics(
      stickman = boostedRunner,
      level = levelWithSaw,
      completedLines = emptyList(),
      deltaSeconds = 1f / 60f,
      animTime = 0f
    )
    assertFalse("Runner hitting a Saw Track must immediately stop (isAlive = false)", sawStep.stickman.isAlive)
    assertTrue(sawStep.hitLava)
  }

  @Test
  fun `rune classification engine identifies bridge, star spark super aura, zigzag coil, and turbo ramp`() {
    val runner = StickmanState(
      footX = 300f,
      footY = 325f,
      velocityX = 180f,
      isGrounded = true
    )

    // 1. Straight Bridge -> STANDARD_INK
    val straightBridge = listOf(Vec2(320f, 325f), Vec2(420f, 325f), Vec2(520f, 325f))
    val bridgeRune = PhysicsEngine.classifyRuneStroke(straightBridge, runner)
    assertEquals(com.example.game.model.RuneType.STANDARD_INK, bridgeRune.runeType)

    // 2. Closed Circle around the runner -> STAR_SPARK (+25% Ink & Golden Super Aura)
    val circlePoints = (0..12).map { idx ->
      val angle = (idx.toFloat() / 12f) * (Math.PI.toFloat() * 2f)
      Vec2(
        x = runner.footX + kotlin.math.cos(angle) * 45f,
        y = (runner.footY - 28f) + kotlin.math.sin(angle) * 45f
      )
    }
    val starRune = PhysicsEngine.classifyRuneStroke(circlePoints, runner)
    assertEquals(com.example.game.model.RuneType.STAR_SPARK, starRune.runeType)
    assertTrue(starRune.triggersSuperAura)
    assertEquals(0.25f, starRune.inkRefundRatio, 0.01f)

    // 3. Zig-Zag / W-shape -> ZIGZAG_COIL (+15% Ink & Spring Bounce)
    val zigZagPoints = listOf(
      Vec2(360f, 330f),
      Vec2(390f, 280f),
      Vec2(420f, 330f),
      Vec2(450f, 280f),
      Vec2(480f, 330f)
    )
    val zigZagRune = PhysicsEngine.classifyRuneStroke(zigZagPoints, runner)
    assertEquals(com.example.game.model.RuneType.ZIGZAG_COIL, zigZagRune.runeType)
    assertEquals(com.example.game.model.ChalkTool.SPRING, zigZagRune.effectiveTool)

    // 4. Upward Ramp ahead of runner -> RAMP_SLOPE (+12% Ink & Turbo Boost)
    val rampPoints = listOf(
      Vec2(330f, 330f),
      Vec2(390f, 305f),
      Vec2(450f, 275f)
    )
    val rampRune = PhysicsEngine.classifyRuneStroke(rampPoints, runner)
    assertEquals(com.example.game.model.RuneType.RAMP_SLOPE, rampRune.runeType)
    assertEquals(com.example.game.model.ChalkTool.BOOST, rampRune.effectiveTool)
  }

  @Test
  fun `ignis titan boss takes damage and recoils with i-frames when struck by super aura runner`() {
    val level = LevelCatalog.getLevel(5)
    val superRunner = StickmanState(
      footX = 400f,
      footY = 325f,
      velocityX = level.runSpeed * 1.65f,
      isGrounded = true,
      superAuraTimer = 4.5f
    )
    val boss = com.example.game.model.IgnisTitanBoss(
      x = 345f,
      y = 295f,
      hp = 5,
      maxHp = 5,
      hurtCooldown = 0f
    )

    val step1 = PhysicsEngine.stepCombatEntities(
      stickman = superRunner,
      level = level,
      rockWalls = emptyList(),
      lavaMonsters = emptyList(),
      ignisBoss = boss,
      animTime = 1.0f,
      dt = 1f / 60f
    )
    assertTrue("Boss should take damage on Super Aura contact", step1.bossTookDamage)
    assertEquals(4, step1.ignisBoss.hp)
    assertTrue("Boss should have i-frame hurtCooldown set (~0.9s)", step1.ignisBoss.hurtCooldown > 0.8f)
    assertEquals(-360f, step1.ignisBoss.knockbackVx, 0.1f)
    assertTrue("Striking Ignis Titan should award bonus score", step1.scoreDelta >= 100)
  }
}



