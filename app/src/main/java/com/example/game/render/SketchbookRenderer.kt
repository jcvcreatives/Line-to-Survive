package com.example.game.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.game.model.BreakableRockWall
import com.example.game.model.CalloutMove
import com.example.game.model.ChalkBit
import com.example.game.model.ChalkTool
import com.example.game.model.DrawnLine
import com.example.game.model.DynamicHazard
import com.example.game.model.EmberParticle
import com.example.game.model.GuideStroke
import com.example.game.model.HazardType
import com.example.game.model.IgnisTitanBoss
import com.example.game.model.LeapingLavaMonster
import com.example.game.model.LevelDefinition
import com.example.game.model.Platform
import com.example.game.model.ScribblerEnemy
import com.example.game.model.SketchParticle
import com.example.game.model.SketchParticleType
import com.example.game.model.StickmanState
import com.example.game.model.ThumbtackCluster
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.FinishBannerGreen
import com.example.ui.theme.InkCyanAccent
import com.example.ui.theme.LavaDeepRed
import com.example.ui.theme.LavaFlameYellow
import com.example.ui.theme.LavaOrange
import com.example.ui.theme.PencilGraphite
import com.example.ui.theme.SketchCharcoalBg
import com.example.ui.theme.SketchGridLine
import com.example.ui.theme.SketchPaperSurface
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Programmatically renders the entire 2D game world with a hand-drawn sketchbook aesthetic:
 * - Dark charcoal paper background with parallax grid & cross-hatch sketch lines
 * - Animated multi-layer orange/red/yellow lava with flame tongues and rising embers
 * - Sketch-shaded platforms with chalk double outlines
 * - Checkered finish line flag & posts
 * - Rough pencil-like white lines for player-drawn surfaces
 * - Hand-drawn chalk & graphite particle effects for running footsteps and drawn lines
 * - Animated white stickman with articulated running limbs
 */
object SketchbookRenderer {

  // Pre-allocated static brushes to eliminate per-frame allocations
  private val paperBackgroundBrush = Brush.verticalGradient(
    colors = listOf(SketchCharcoalBg, SketchPaperSurface, Color(0xFF1E1718))
  )
  private val lavaGlowBrush = Brush.verticalGradient(
    colors = listOf(
      Color.Transparent,
      LavaOrange.copy(alpha = 0.18f),
      LavaDeepRed.copy(alpha = 0.55f)
    )
  )

  // Reusable Path objects reset every frame (avoiding 1,000+ Path allocations per second)
  private val reusableGuidePath = Path()
  private val reusablePlatformPath = Path()
  private val reusableBackWavePath = Path()
  private val reusableMidFlamePath = Path()
  private val reusableCoreWavePath = Path()
  private val reusablePrimaryLinePath = Path()
  private val reusableSecondaryLinePath = Path()
  private val reusableStarPath = Path()
  private val reusableBandanaPath = Path()
  private val reusableAuraOuterPath = Path()
  private val reusableAuraInnerPath = Path()
  private val reusableHairPath = Path()
  private val reusableLightningPath = Path()

  fun DrawScope.renderGameWorld(
    level: LevelDefinition,
    stickman: StickmanState,
    cameraX: Float,
    completedLines: List<DrawnLine>,
    activeStrokePoints: List<Vec2>,
    selectedTool: ChalkTool = ChalkTool.WHITE,
    chalkBits: List<ChalkBit> = emptyList(),
    scribblers: List<ScribblerEnemy> = emptyList(),
    rockWalls: List<BreakableRockWall> = emptyList(),
    lavaMonsters: List<LeapingLavaMonster> = emptyList(),
    ignisBoss: IgnisTitanBoss = IgnisTitanBoss(isDefeated = true),
    dragonSpriteSheet: ImageBitmap? = null,
    equippedSkin: String = "DEFAULT",
    equippedTrail: String = "STANDARD_INK",
    activeCallout: CalloutMove? = null,
    lavaAnimTime: Float,
    embers: List<EmberParticle>,
    sketchParticles: List<SketchParticle> = emptyList(),
    worldScale: Float
  ) {
    fun toScreenX(worldX: Float): Float = (worldX - cameraX) * worldScale
    fun toScreenY(worldY: Float): Float = worldY * worldScale

    val visibleWorldWidth = if (worldScale > 0f) size.width / worldScale else 1000f

    // 1. Biome Background + Subtle Graph Grid
    drawSketchbookPaper(cameraX = cameraX, worldScale = worldScale, mapBiome = level.mapBiome)

    // 2. Faint Guide Strokes on Level 1 / Early Levels
    if (completedLines.isEmpty()) {
      for (guide in level.guideStrokes) {
        drawGuideStroke(guide, ::toScreenX, ::toScreenY, worldScale, lavaAnimTime)
      }
    }

    // 3. Animated Bottom Hazard Pit (Volcano Lava / Cryo Acid / Cyber Laser Floor)
    drawLavaPit(
      cameraX = cameraX,
      visibleWorldWidth = visibleWorldWidth,
      lavaAnimTime = lavaAnimTime,
      embers = embers,
      mapBiome = level.mapBiome,
      toScreenX = ::toScreenX,
      toScreenY = ::toScreenY,
      worldScale = worldScale
    )

    // 4. Sketchbook Platforms
    for (platform in level.platforms) {
      if (platform.endX >= cameraX - 100f && platform.startX <= cameraX + visibleWorldWidth + 100f) {
        drawSketchPlatform(
          platform = platform,
          toScreenX = ::toScreenX,
          toScreenY = ::toScreenY,
          worldScale = worldScale
        )
      }
    }

    // 5. Finish Line Banner & Posts on the Finish Platform
    val finishPlatform = level.platforms.lastOrNull()
    val finishGroundY = finishPlatform?.topY ?: WorldConstants.DEFAULT_GROUND_Y
    drawFinishLine(
      finishX = level.finishLineX,
      groundY = finishGroundY,
      animTime = lavaAnimTime,
      toScreenX = ::toScreenX,
      toScreenY = ::toScreenY,
      worldScale = worldScale
    )

    // 6. Thumbtack & All 8 Modular Obstacles + Combat Rock Walls & Lava Monsters
    for (cluster in level.thumbtacks) {
      drawThumbtackCluster(cluster, ::toScreenX, ::toScreenY, worldScale)
    }
    for (hazard in level.hazards) {
      drawDynamicHazard(
        hazard = hazard,
        animTime = lavaAnimTime,
        runnerX = stickman.footX,
        completedLines = completedLines,
        mapBiome = level.mapBiome,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        worldScale = worldScale
      )
    }
    for (wall in rockWalls) {
      if (!wall.isSmashed) {
        drawBreakableRockWall(wall, stickman.superAuraTimer > 0f, ::toScreenX, ::toScreenY, worldScale)
      }
    }
    for (monster in lavaMonsters) {
      if (!monster.isDefeated) {
        drawLeapingLavaMonster(monster, lavaAnimTime, ::toScreenX, ::toScreenY, worldScale)
      }
    }

    // 7. Collectible Chalk Bits & Gold Stars
    for (bit in chalkBits) {
      if (!bit.collected) {
        drawChalkBitOrStar(bit, lavaAnimTime, ::toScreenX, ::toScreenY, worldScale)
      }
    }

    // 8. Completed Player-Drawn Chalk Platforms (White, Blue Bounce, Gold Turbo + Trail Cosmetics)
    for (line in completedLines) {
      drawPencilLine(
        points = line.points,
        tool = line.tool,
        equippedTrail = equippedTrail,
        alpha = line.alpha,
        isCompleted = true,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        worldScale = worldScale
      )
    }

    // 9. Active Finger-Drawn Line in Progress
    if (activeStrokePoints.isNotEmpty()) {
      drawPencilLine(
        points = activeStrokePoints,
        tool = selectedTool,
        equippedTrail = equippedTrail,
        alpha = 1f,
        isCompleted = false,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        worldScale = worldScale
      )
    }

    // 10. Red Scribbler Enemies & Colossal Lava Dragon Boss (Ignis Titan)
    for (enemy in scribblers) {
      if (enemy.isAlive) {
        drawScribblerEnemy(enemy, ::toScreenX, ::toScreenY, worldScale)
      }
    }
    drawIgnisTitanBoss(
      boss = ignisBoss,
      stickman = stickman,
      animTime = lavaAnimTime,
      dragonSpriteSheet = dragonSpriteSheet,
      toScreenX = ::toScreenX,
      toScreenY = ::toScreenY,
      worldScale = worldScale
    )

    // 11. Hand-drawn Chalk Dust & Pencil Spark Particles
    if (sketchParticles.isNotEmpty()) {
      drawSketchParticles(
        particles = sketchParticles,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        worldScale = worldScale
      )
    }

    // 12. Animated White Stickman + Flowing Scarf + Active Aura / Titan Shield
    drawAnimatedStickman(
      stickman = stickman,
      animTime = lavaAnimTime,
      toScreenX = ::toScreenX,
      toScreenY = ::toScreenY,
      worldScale = worldScale
    )
    if (stickman.hasTitanShield || stickman.superAuraTimer > 0f || equippedSkin != "DEFAULT") {
      drawHeroCosmeticsAndShield(
        stickman = stickman,
        equippedSkin = equippedSkin,
        animTime = lavaAnimTime,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        worldScale = worldScale
      )
    }

    // 13. Callout Move Speech Bubble & Landing X Telegraph
    if (activeCallout != null) {
      drawCalloutBubbleAndTelegraph(
        callout = activeCallout,
        stickman = stickman,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        worldScale = worldScale
      )
    }

    // 14. Thick Fog Daily Modifier Overlay
    if (level.dailyModifier == "Thick Fog") {
      drawRect(
        brush = Brush.horizontalGradient(
          colors = listOf(Color.Transparent, Color(0xCC0D1117), Color(0xEE0D1117))
        ),
        size = size
      )
    }
  }

  private fun DrawScope.drawSketchbookPaper(
    cameraX: Float,
    worldScale: Float,
    mapBiome: com.example.game.model.MapBiome = com.example.game.model.MapBiome.MAP_1_VOLCANO
  ) {
    val bgBrush = when (mapBiome) {
      com.example.game.model.MapBiome.MAP_1_VOLCANO -> paperBackgroundBrush
      com.example.game.model.MapBiome.MAP_2_CRYO_LAB -> Brush.verticalGradient(
        colors = listOf(Color(0xFF0A1422), Color(0xFF102238), Color(0xFF0B2B3C))
      )
      com.example.game.model.MapBiome.MAP_3_CYBER_GRID -> Brush.verticalGradient(
        colors = listOf(Color(0xFF110922), Color(0xFF1C1036), Color(0xFF280D3B))
      )
    }
    val gridColor = when (mapBiome) {
      com.example.game.model.MapBiome.MAP_1_VOLCANO -> SketchGridLine
      com.example.game.model.MapBiome.MAP_2_CRYO_LAB -> Color(0xFF1D3E5E)
      com.example.game.model.MapBiome.MAP_3_CYBER_GRID -> Color(0xFF3E1F68)
    }

    drawRect(
      brush = bgBrush,
      size = size
    )

    val gridStepWorld = 100f
    val gridStepPx = (gridStepWorld * worldScale).coerceAtLeast(32f)
    val parallaxOffset = (cameraX * 0.35f * worldScale) % gridStepPx

    var x = -parallaxOffset
    while (x < size.width) {
      drawLine(
        color = gridColor,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1f
      )
      x += gridStepPx
    }

    var y = 0f
    while (y < size.height) {
      drawLine(
        color = gridColor,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1f
      )
      y += gridStepPx
    }
  }

  private fun DrawScope.drawGuideStroke(
    guide: GuideStroke,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float,
    animTime: Float
  ) {
    if (guide.points.size < 2) return
    val path = reusableGuidePath.apply {
      reset()
      val first = guide.points.first()
      moveTo(toScreenX(first.x), toScreenY(first.y))
      for (i in 1 until guide.points.size) {
        val pt = guide.points[i]
        lineTo(toScreenX(pt.x), toScreenY(pt.y))
      }
    }

    val pulseAlpha = 0.25f + 0.15f * sin(animTime * 4f)
    drawPath(
      path = path,
      color = InkCyanAccent.copy(alpha = pulseAlpha),
      style = Stroke(
        width = 4.5f * worldScale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = PathEffect.dashPathEffect(
          intervals = floatArrayOf(18f * worldScale, 14f * worldScale),
          phase = -animTime * 40f * worldScale
        )
      )
    )
  }

  private fun DrawScope.drawSketchPlatform(
    platform: Platform,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val left = toScreenX(platform.startX)
    val right = toScreenX(platform.endX)
    val top = toScreenY(platform.topY)

    val topPath1 = reusablePlatformPath.apply {
      reset()
      moveTo(left, top)
      var cx = platform.startX
      var idx = 0
      while (cx < platform.endX) {
        val nextX = (cx + 35f).coerceAtMost(platform.endX)
        val jitterY = if (idx % 2 == 0) -1.2f else 1.2f
        lineTo(toScreenX(nextX), toScreenY(platform.topY + jitterY))
        cx = nextX
        idx++
      }
    }

    drawPath(
      path = topPath1,
      color = InkCyanAccent.copy(alpha = 0.35f),
      style = Stroke(
        width = 9.0f * worldScale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )

    drawPath(
      path = topPath1,
      color = ChalkWhite,
      style = Stroke(
        width = 5.0f * worldScale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )
  }

  private fun DrawScope.drawLavaPit(
    cameraX: Float,
    visibleWorldWidth: Float,
    lavaAnimTime: Float,
    embers: List<EmberParticle>,
    mapBiome: com.example.game.model.MapBiome = com.example.game.model.MapBiome.MAP_1_VOLCANO,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val lavaTopY = WorldConstants.LAVA_SURFACE_Y
    val startWorldX = cameraX - 60f
    val endWorldX = cameraX + visibleWorldWidth + 60f

    val backColor = when (mapBiome) {
      com.example.game.model.MapBiome.MAP_1_VOLCANO -> LavaDeepRed
      com.example.game.model.MapBiome.MAP_2_CRYO_LAB -> Color(0xFF006080)
      com.example.game.model.MapBiome.MAP_3_CYBER_GRID -> Color(0xFF9C0066)
    }
    val midColor = when (mapBiome) {
      com.example.game.model.MapBiome.MAP_1_VOLCANO -> LavaOrange
      com.example.game.model.MapBiome.MAP_2_CRYO_LAB -> Color(0xFF00B8D4)
      com.example.game.model.MapBiome.MAP_3_CYBER_GRID -> Color(0xFFFF007F)
    }
    val coreColor = when (mapBiome) {
      com.example.game.model.MapBiome.MAP_1_VOLCANO -> LavaFlameYellow
      com.example.game.model.MapBiome.MAP_2_CRYO_LAB -> Color(0xFF84FFFF)
      com.example.game.model.MapBiome.MAP_3_CYBER_GRID -> Color(0xFF00E5FF)
    }

    drawRect(
      brush = lavaGlowBrush,
      topLeft = Offset(0f, toScreenY(lavaTopY - 75f)),
      size = Size(size.width, size.height - toScreenY(lavaTopY - 75f))
    )

    val backWavePath = reusableBackWavePath.apply {
      reset()
      moveTo(0f, size.height)
      var wx = startWorldX
      while (wx <= endWorldX) {
        val wave = sin(wx * 0.045f + lavaAnimTime * 3.8f) * 7f +
          cos(wx * 0.09f - lavaAnimTime * 2.4f) * 4f
        lineTo(toScreenX(wx), toScreenY(lavaTopY + wave - 4f))
        wx += 20f
      }
      lineTo(size.width, size.height)
      close()
    }
    drawPath(path = backWavePath, color = backColor)

    val midFlamePath = reusableMidFlamePath.apply {
      reset()
      moveTo(0f, size.height)
      var wx = startWorldX
      var idx = 0
      while (wx <= endWorldX) {
        val tongue = sin(wx * 0.07f - lavaAnimTime * 5.2f) * 9f +
          (if (idx % 2 == 0) -6f else 4f)
        lineTo(toScreenX(wx), toScreenY(lavaTopY + tongue + 2f))
        wx += 16f
        idx++
      }
      lineTo(size.width, size.height)
      close()
    }
    drawPath(path = midFlamePath, color = midColor)

    val coreWavePath = reusableCoreWavePath.apply {
      reset()
      moveTo(0f, size.height)
      var wx = startWorldX
      while (wx <= endWorldX) {
        val crest = cos(wx * 0.06f + lavaAnimTime * 6.5f) * 5.5f
        lineTo(toScreenX(wx), toScreenY(lavaTopY + 14f + crest))
        wx += 18f
      }
      lineTo(size.width, size.height)
      close()
    }
    drawPath(path = coreWavePath, color = coreColor.copy(alpha = 0.88f))

    for (ember in embers) {
      val sx = toScreenX(ember.worldX)
      val sy = toScreenY(ember.worldY)
      if (sx in -20f..(size.width + 20f)) {
        drawCircle(
          color = (if (ember.isYellow) coreColor else midColor).copy(alpha = ember.alpha),
          radius = ember.radius * worldScale,
          center = Offset(sx, sy)
        )
      }
    }
  }

  private fun DrawScope.drawFinishLine(
    finishX: Float,
    groundY: Float,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val postLeftX = toScreenX(finishX)
    val postRightX = toScreenX(finishX + 64f)
    val bottomY = toScreenY(groundY)
    val topY = toScreenY(groundY - 125f)

    if (postRightX < -50f || postLeftX > size.width + 50f) return

    drawLine(
      color = ChalkWhite,
      start = Offset(postLeftX, bottomY),
      end = Offset(postLeftX, topY),
      strokeWidth = 4.5f * worldScale,
      cap = StrokeCap.Round
    )
    drawLine(
      color = ChalkWhite,
      start = Offset(postRightX, bottomY),
      end = Offset(postRightX, topY),
      strokeWidth = 4.5f * worldScale,
      cap = StrokeCap.Round
    )

    val bannerHeight = 28f * worldScale
    val cols = 6
    val rows = 2
    val cellW = (postRightX - postLeftX) / cols
    val cellH = bannerHeight / rows

    for (r in 0 until rows) {
      for (c in 0 until cols) {
        val waveY = sin(animTime * 5f + c * 0.8f) * 2.5f * worldScale
        val isLight = (r + c) % 2 == 0
        drawRect(
          color = if (isLight) ChalkWhite else SketchCharcoalBg,
          topLeft = Offset(postLeftX + c * cellW, topY + r * cellH + waveY),
          size = Size(cellW + 0.5f, cellH + 0.5f)
        )
      }
    }

    drawRect(
      color = FinishBannerGreen,
      topLeft = Offset(postLeftX, topY),
      size = Size(postRightX - postLeftX, bannerHeight),
      style = Stroke(width = 2.5f * worldScale)
    )
  }

  private fun DrawScope.drawPencilLine(
    points: List<Vec2>,
    tool: ChalkTool = ChalkTool.WHITE,
    equippedTrail: String = "STANDARD_INK",
    alpha: Float = 1f,
    isCompleted: Boolean,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    if (points.isEmpty()) return
    val trailBaseColor = when (equippedTrail) {
      "RAINBOW_PRISM" -> Color(0xFFE040FB)
      "SAKURA_BLOSSOM" -> Color(0xFFFF80AB)
      "LIGHTNING_SPARK" -> Color(0xFF00E5FF)
      else -> ChalkWhite
    }
    val coreColor = when (tool) {
      ChalkTool.WHITE -> trailBaseColor.copy(alpha = alpha)
      ChalkTool.SPRING -> InkCyanAccent.copy(alpha = alpha)
      ChalkTool.BOOST -> LavaFlameYellow.copy(alpha = alpha)
      ChalkTool.CUTTER -> LavaOrange.copy(alpha = alpha)
    }
    val haloColor = when (tool) {
      ChalkTool.WHITE -> if (equippedTrail == "STANDARD_INK") PencilGraphite.copy(alpha = 0.55f * alpha) else trailBaseColor.copy(alpha = 0.42f * alpha)
      ChalkTool.SPRING -> InkCyanAccent.copy(alpha = 0.42f * alpha)
      ChalkTool.BOOST -> LavaOrange.copy(alpha = 0.48f * alpha)
      ChalkTool.CUTTER -> LavaDeepRed.copy(alpha = 0.4f * alpha)
    }

    if (points.size == 1) {
      val pt = points.first()
      drawCircle(
        color = coreColor,
        radius = 4.5f * worldScale,
        center = Offset(toScreenX(pt.x), toScreenY(pt.y))
      )
      return
    }

    val primaryPath = reusablePrimaryLinePath.apply {
      reset()
      val first = points.first()
      moveTo(toScreenX(first.x), toScreenY(first.y))
      for (i in 1 until points.size) {
        val pt = points[i]
        lineTo(toScreenX(pt.x), toScreenY(pt.y))
      }
    }

    val secondarySketchPath = reusableSecondaryLinePath.apply {
      reset()
      val first = points.first()
      moveTo(
        toScreenX(first.x + 1.2f),
        toScreenY(first.y + 1.8f)
      )
      for (i in 1 until points.size) {
        val pt = points[i]
        val sx = toScreenX(pt.x)
        val sy = toScreenY(pt.y)
        val jitter = if (i % 2 == 0) 1.8f else -1.6f
        lineTo(
          sx + jitter * 0.5f * worldScale,
          sy + jitter * worldScale
        )
      }
    }

    drawPath(
      path = secondarySketchPath,
      color = haloColor,
      style = Stroke(
        width = 9.0f * worldScale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )

    drawPath(
      path = primaryPath,
      color = coreColor,
      style = Stroke(
        width = 5.5f * worldScale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )

    if (!isCompleted) {
      val tip = points.last()
      drawCircle(
        color = LavaFlameYellow,
        radius = 6.5f * worldScale,
        center = Offset(toScreenX(tip.x), toScreenY(tip.y))
      )
    }
  }

  private fun DrawScope.drawChalkBitOrStar(
    bit: ChalkBit,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val bob = sin(animTime * 3.2f + bit.id) * 3.5f
    val cx = toScreenX(bit.x)
    val cy = toScreenY(bit.y + bob)
    if (cx < -50f || cx > size.width + 50f) return

    val pulse = 0.85f + 0.15f * sin(animTime * 4.5f + bit.id)

    if (bit.isStar) {
      drawCircle(
        color = LavaFlameYellow.copy(alpha = 0.28f * pulse),
        radius = 28f * worldScale,
        center = Offset(cx, cy)
      )
      drawCircle(
        color = LavaFlameYellow.copy(alpha = 0.55f),
        radius = 22f * worldScale * pulse,
        center = Offset(cx, cy),
        style = Stroke(width = 2.0f * worldScale)
      )
      val starPath = reusableStarPath.apply {
        reset()
        for (i in 0 until 10) {
          val r = (if (i % 2 == 0) 15f else 7f) * worldScale
          val angle = -PI.toFloat() / 2f + i * PI.toFloat() / 5f + sin(animTime * 2f) * 0.1f
          val px = cx + cos(angle) * r
          val py = cy + sin(angle) * r
          if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
      }
      drawPath(path = starPath, color = LavaFlameYellow)
      drawPath(
        path = starPath,
        color = ChalkWhite,
        style = Stroke(width = 2.0f * worldScale)
      )
    } else {
      drawCircle(
        color = InkCyanAccent.copy(alpha = 0.22f * pulse),
        radius = 24f * worldScale,
        center = Offset(cx, cy)
      )
      drawCircle(
        color = InkCyanAccent.copy(alpha = 0.65f),
        radius = 19f * worldScale * pulse,
        center = Offset(cx, cy),
        style = Stroke(width = 1.8f * worldScale)
      )
      drawLine(
        color = Color(0xFF0F172A),
        start = Offset(cx - 11f * worldScale, cy + 5.5f * worldScale),
        end = Offset(cx + 11f * worldScale, cy - 5.5f * worldScale),
        strokeWidth = 10.5f * worldScale,
        cap = StrokeCap.Round
      )
      drawLine(
        color = ChalkWhite,
        start = Offset(cx - 10f * worldScale, cy + 5f * worldScale),
        end = Offset(cx + 10f * worldScale, cy - 5f * worldScale),
        strokeWidth = 7.5f * worldScale,
        cap = StrokeCap.Round
      )
      drawLine(
        color = InkCyanAccent,
        start = Offset(cx - 3f * worldScale, cy + 1.5f * worldScale),
        end = Offset(cx + 3f * worldScale, cy - 1.5f * worldScale),
        strokeWidth = 7.8f * worldScale,
        cap = StrokeCap.Butt
      )
    }
  }

  private fun DrawScope.drawThumbtackCluster(
    cluster: ThumbtackCluster,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    for (i in 0 until cluster.count) {
      val offset = (i - (cluster.count - 1) / 2f) * cluster.spacing
      val tx = toScreenX(cluster.x + offset)
      val ty = toScreenY(cluster.y)
      if (tx < -30f || tx > size.width + 30f) continue

      drawCircle(
        color = Color(0xFFF39CAB).copy(alpha = 0.25f),
        radius = 13f * worldScale,
        center = Offset(tx, ty - 4f * worldScale)
      )
      drawLine(
        color = Color(0xFFF39CAB),
        start = Offset(tx - 8f * worldScale, ty),
        end = Offset(tx + 8f * worldScale, ty),
        strokeWidth = 5f * worldScale,
        cap = StrokeCap.Round
      )
      drawLine(
        color = ChalkWhite,
        start = Offset(tx, ty - 2f * worldScale),
        end = Offset(tx, ty - 14f * worldScale),
        strokeWidth = 2.6f * worldScale,
        cap = StrokeCap.Round
      )
    }
  }

  private fun DrawScope.drawDynamicHazard(
    hazard: DynamicHazard,
    animTime: Float,
    runnerX: Float = 0f,
    completedLines: List<DrawnLine> = emptyList(),
    mapBiome: com.example.game.model.MapBiome = com.example.game.model.MapBiome.MAP_1_VOLCANO,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    when (hazard.type) {
      HazardType.LEDGE -> drawLedgeHazard(
        hazard = hazard,
        animTime = animTime,
        mapBiome = mapBiome,
        toScreenX = toScreenX,
        toScreenY = toScreenY,
        worldScale = worldScale
      )
      HazardType.PENDULUM -> drawSwingingPendulumHazard(
        hazard = hazard,
        animTime = animTime,
        toScreenX = toScreenX,
        toScreenY = toScreenY,
        worldScale = worldScale
      )
      HazardType.LAVA_GEYSER -> drawLavaGeyserHazard(
        hazard = hazard,
        animTime = animTime,
        toScreenX = toScreenX,
        toScreenY = toScreenY,
        worldScale = worldScale
      )
      HazardType.FALLING_SPIKE -> drawFallingSpikeHazard(
        hazard = hazard,
        animTime = animTime,
        runnerX = runnerX,
        completedLines = completedLines,
        toScreenX = toScreenX,
        toScreenY = toScreenY,
        worldScale = worldScale
      )
      HazardType.LASER_GATE -> drawLaserGateHazard(
        hazard = hazard,
        animTime = animTime,
        toScreenX = toScreenX,
        toScreenY = toScreenY,
        worldScale = worldScale
      )
      HazardType.SAW_TRACK -> drawSawTrackHazard(
        hazard = hazard,
        animTime = animTime,
        toScreenX = toScreenX,
        toScreenY = toScreenY,
        worldScale = worldScale
      )
      HazardType.NO_DRAW_ZONE -> drawNoDrawZoneHazard(
        hazard = hazard,
        animTime = animTime,
        toScreenX = toScreenX,
        toScreenY = toScreenY,
        worldScale = worldScale
      )
      HazardType.WIND_UPDRAFT -> drawWindUpdraftHazard(
        hazard = hazard,
        animTime = animTime,
        toScreenX = toScreenX,
        toScreenY = toScreenY,
        worldScale = worldScale
      )
    }
  }

  private fun DrawScope.drawLedgeHazard(
    hazard: DynamicHazard,
    animTime: Float,
    mapBiome: com.example.game.model.MapBiome,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val (lStartX, lEndX) = hazard.ledgeBoundsAt(animTime, mapBiome)
    val leftX = toScreenX(lStartX)
    val rightX = toScreenX(lEndX)
    val topY = toScreenY(hazard.topY)
    if (rightX < -80f || leftX > size.width + 80f) return

    val isSolid = hazard.isPlatformSolidAt(animTime)
    val isWarning = hazard.isPlatformWarningAt(animTime)
    val cycleProg = hazard.vanishingCycleProgressAt(animTime)
    val isOrange = hazard.ledgeStyle == com.example.game.model.LedgeStyle.ORANGE_CRUMBLING

    if (isSolid) {
      val flicker = if (isWarning) {
        0.45f + 0.55f * (0.5f + 0.5f * sin(animTime * 28f))
      } else {
        1f
      }
      val barColor = when {
        isWarning -> LavaFlameYellow.copy(alpha = flicker)
        isOrange -> LavaOrange.copy(alpha = 0.94f)
        else -> InkCyanAccent.copy(alpha = 0.94f)
      }
      val glowColor = when {
        isWarning -> LavaOrange.copy(alpha = 0.35f * flicker)
        isOrange -> LavaOrange.copy(alpha = 0.28f)
        else -> InkCyanAccent.copy(alpha = 0.28f)
      }

      drawLine(
        color = glowColor,
        start = Offset(leftX, topY + 2f * worldScale),
        end = Offset(rightX, topY + 2f * worldScale),
        strokeWidth = 14f * worldScale,
        cap = StrokeCap.Round
      )

      drawLine(
        color = barColor,
        start = Offset(leftX, topY),
        end = Offset(rightX, topY),
        strokeWidth = 6.0f * worldScale,
        cap = StrokeCap.Round
      )
      drawLine(
        color = ChalkWhite.copy(alpha = 0.88f * flicker),
        start = Offset(leftX + 3f * worldScale, topY - 1.2f * worldScale),
        end = Offset(rightX - 3f * worldScale, topY - 1.2f * worldScale),
        strokeWidth = 2.4f * worldScale,
        cap = StrokeCap.Round
      )

      if (isOrange) {
        val solidRemainingRatio = ((hazard.visibleRatio - cycleProg) / hazard.visibleRatio.coerceAtLeast(0.1f))
          .coerceIn(0f, 1f)
        val barSpan = (rightX - leftX) * solidRemainingRatio
        drawLine(
          color = if (isWarning) LavaDeepRed else LavaFlameYellow,
          start = Offset(leftX, topY + 8f * worldScale),
          end = Offset(leftX + barSpan, topY + 8f * worldScale),
          strokeWidth = 2.8f * worldScale,
          cap = StrokeCap.Round
        )
      }
    } else {
      val ghostDash = PathEffect.dashPathEffect(
        intervals = floatArrayOf(10f * worldScale, 8f * worldScale),
        phase = animTime * 24f * worldScale
      )
      drawLine(
        color = (if (isOrange) LavaOrange else InkCyanAccent).copy(alpha = 0.28f),
        start = Offset(leftX, topY),
        end = Offset(rightX, topY),
        strokeWidth = 3.5f * worldScale,
        cap = StrokeCap.Round,
        pathEffect = ghostDash
      )
    }
  }

  private fun DrawScope.drawLavaGeyserHazard(
    hazard: DynamicHazard,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val cx = toScreenX(hazard.x)
    if (cx < -80f || cx > size.width + 80f) return

    val baseY = toScreenY(WorldConstants.LAVA_SURFACE_Y)
    val ratio = hazard.geyserEruptionRatioAt(animTime)
    val isWarning = hazard.isGeyserWarningAt(animTime)
    val halfW = (hazard.geyserWidth * 0.5f) * worldScale

    // Warning bubble & target arch height indicator
    val bubblePulse = 0.65f + 0.35f * sin(animTime * 14f + hazard.id)
    drawCircle(
      color = LavaFlameYellow.copy(alpha = if (isWarning || ratio > 0f) 0.78f else 0.38f),
      radius = (12f + 6f * bubblePulse) * worldScale,
      center = Offset(cx, baseY - 4f * worldScale)
    )
    drawCircle(
      color = LavaOrange,
      radius = (16f + 8f * bubblePulse) * worldScale,
      center = Offset(cx, baseY - 4f * worldScale),
      style = Stroke(width = 2.2f * worldScale)
    )

    if (ratio > 0.02f) {
      val topY = toScreenY(hazard.geyserCurrentTopYAt(animTime))
      drawLine(
        color = LavaDeepRed.copy(alpha = 0.85f),
        start = Offset(cx, baseY),
        end = Offset(cx, topY),
        strokeWidth = halfW * 2.2f,
        cap = StrokeCap.Round
      )
      drawLine(
        color = LavaOrange,
        start = Offset(cx, baseY),
        end = Offset(cx, topY),
        strokeWidth = halfW * 1.5f,
        cap = StrokeCap.Round
      )
      drawLine(
        color = LavaFlameYellow,
        start = Offset(cx, baseY),
        end = Offset(cx, topY + 6f * worldScale),
        strokeWidth = halfW * 0.75f,
        cap = StrokeCap.Round
      )
      drawCircle(
        color = LavaFlameYellow,
        radius = halfW * 1.15f,
        center = Offset(cx, topY)
      )
    }
  }

  private fun DrawScope.drawFallingSpikeHazard(
    hazard: DynamicHazard,
    animTime: Float,
    runnerX: Float,
    completedLines: List<DrawnLine>,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val cx = toScreenX(hazard.x)
    if (cx < -60f || cx > size.width + 60f) return

    val (tipWorldY, blocked) = hazard.fallingSpikeTipY(runnerX, completedLines)
    val topWorldY = (tipWorldY - hazard.spikeHeight).coerceAtLeast(hazard.y)
    val topY = toScreenY(topWorldY)
    val tipY = toScreenY(tipWorldY)
    val lavaY = toScreenY(WorldConstants.LAVA_SURFACE_Y)

    // Red vertical warning laser line below the stalactite
    val warnDash = PathEffect.dashPathEffect(
      intervals = floatArrayOf(8f * worldScale, 8f * worldScale),
      phase = -animTime * 35f * worldScale
    )
    drawLine(
      color = LavaDeepRed.copy(alpha = if (blocked) 0.25f else 0.55f),
      start = Offset(cx, tipY),
      end = Offset(cx, lavaY),
      strokeWidth = 2.0f * worldScale,
      pathEffect = warnDash
    )

    // Stalactite cone body
    val halfW = 13f * worldScale
    val spikePath = reusableGuidePath.apply {
      reset()
      moveTo(cx - halfW, topY)
      lineTo(cx + halfW, topY)
      lineTo(cx, tipY)
      close()
    }
    drawPath(path = spikePath, color = LavaDeepRed)
    drawPath(
      path = spikePath,
      color = ChalkWhite,
      style = Stroke(width = 2.2f * worldScale, join = StrokeJoin.Round)
    )
  }

  private fun DrawScope.drawLaserGateHazard(
    hazard: DynamicHazard,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val cx = toScreenX(hazard.x)
    if (cx < -60f || cx > size.width + 60f) return

    val topY = toScreenY(hazard.topY)
    val botY = toScreenY(hazard.bottomY)
    val isActive = hazard.isLaserActiveAt(animTime)
    val isCharging = hazard.isLaserChargingAt(animTime)

    // Top & bottom emitter nodes
    val nodeRadius = 10f * worldScale
    drawCircle(color = SketchCharcoalBg, radius = nodeRadius, center = Offset(cx, topY))
    drawCircle(color = ChalkWhite, radius = nodeRadius, center = Offset(cx, topY), style = Stroke(width = 2.5f * worldScale))
    drawCircle(color = SketchCharcoalBg, radius = nodeRadius, center = Offset(cx, botY))
    drawCircle(color = ChalkWhite, radius = nodeRadius, center = Offset(cx, botY), style = Stroke(width = 2.5f * worldScale))

    if (isActive) {
      drawLine(
        color = LavaDeepRed.copy(alpha = 0.45f),
        start = Offset(cx, topY),
        end = Offset(cx, botY),
        strokeWidth = 16f * worldScale,
        cap = StrokeCap.Round
      )
      drawLine(
        color = LavaOrange,
        start = Offset(cx, topY),
        end = Offset(cx, botY),
        strokeWidth = 7.5f * worldScale,
        cap = StrokeCap.Round
      )
      drawLine(
        color = ChalkWhite,
        start = Offset(cx, topY),
        end = Offset(cx, botY),
        strokeWidth = 2.6f * worldScale,
        cap = StrokeCap.Round
      )
    } else {
      val alpha = if (isCharging) 0.72f else 0.25f
      val dash = PathEffect.dashPathEffect(
        intervals = floatArrayOf(7f * worldScale, 7f * worldScale),
        phase = animTime * 25f * worldScale
      )
      drawLine(
        color = (if (isCharging) LavaFlameYellow else LavaDeepRed).copy(alpha = alpha),
        start = Offset(cx, topY),
        end = Offset(cx, botY),
        strokeWidth = 2.4f * worldScale,
        pathEffect = dash
      )
    }
  }

  private fun DrawScope.drawSawTrackHazard(
    hazard: DynamicHazard,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val sx0 = toScreenX(hazard.trackStartX)
    val sy0 = toScreenY(hazard.trackStartY)
    val sx1 = toScreenX(hazard.trackEndX)
    val sy1 = toScreenY(hazard.trackEndY)
    if (maxOf(sx0, sx1) < -80f || minOf(sx0, sx1) > size.width + 80f) return

    // Dotted linear track
    val trackDash = PathEffect.dashPathEffect(
      intervals = floatArrayOf(8f * worldScale, 6f * worldScale),
      phase = 0f
    )
    drawLine(
      color = PencilGraphite.copy(alpha = 0.55f),
      start = Offset(sx0, sy0),
      end = Offset(sx1, sy1),
      strokeWidth = 3.0f * worldScale,
      cap = StrokeCap.Round,
      pathEffect = trackDash
    )
    drawCircle(color = ChalkWhite, radius = 4.5f * worldScale, center = Offset(sx0, sy0))
    drawCircle(color = ChalkWhite, radius = 4.5f * worldScale, center = Offset(sx1, sy1))

    // Spinning red buzzsaw
    val sawPos = hazard.sawPositionAt(animTime)
    val cx = toScreenX(sawPos.x)
    val cy = toScreenY(sawPos.y)
    val r = hazard.bobRadius * worldScale

    drawCircle(
      color = LavaDeepRed.copy(alpha = 0.30f),
      radius = r * 1.38f,
      center = Offset(cx, cy)
    )
    val teeth = 10
    val spin = animTime * 7.5f
    for (i in 0 until teeth) {
      val a = spin + i * (2f * PI.toFloat() / teeth)
      drawLine(
        color = ChalkWhite,
        start = Offset(cx + cos(a) * (r * 0.78f), cy + sin(a) * (r * 0.78f)),
        end = Offset(cx + cos(a) * (r * 1.34f), cy + sin(a) * (r * 1.34f)),
        strokeWidth = 3.0f * worldScale,
        cap = StrokeCap.Round
      )
    }
    drawCircle(color = LavaDeepRed, radius = r, center = Offset(cx, cy))
    drawCircle(color = ChalkWhite, radius = r, center = Offset(cx, cy), style = Stroke(width = 2.4f * worldScale))
    drawCircle(color = LavaFlameYellow, radius = r * 0.34f, center = Offset(cx, cy))
  }

  private fun DrawScope.drawNoDrawZoneHazard(
    hazard: DynamicHazard,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val left = toScreenX(hazard.startX)
    val right = toScreenX(hazard.endX)
    if (right < -60f || left > size.width + 60f) return

    val top = toScreenY(hazard.topY)
    val bottom = toScreenY(hazard.bottomY)
    val w = (right - left).coerceAtLeast(4f)
    val h = (bottom - top).coerceAtLeast(4f)
    val pulse = 0.14f + 0.06f * sin(animTime * 4f + hazard.id)

    drawRect(
      color = LavaDeepRed.copy(alpha = pulse),
      topLeft = Offset(left, top),
      size = Size(w, h)
    )
    val borderDash = PathEffect.dashPathEffect(
      intervals = floatArrayOf(12f * worldScale, 8f * worldScale),
      phase = animTime * 22f * worldScale
    )
    drawRect(
      color = LavaOrange.copy(alpha = 0.78f),
      topLeft = Offset(left, top),
      size = Size(w, h),
      style = Stroke(width = 2.4f * worldScale, pathEffect = borderDash)
    )
    // Diagonal warning X inside the No-Draw Zone
    val cx = (left + right) * 0.5f
    val cy = (top + bottom) * 0.5f
    val xSize = 16f * worldScale
    drawLine(LavaDeepRed.copy(alpha = 0.7f), Offset(cx - xSize, cy - xSize), Offset(cx + xSize, cy + xSize), 3.2f * worldScale, StrokeCap.Round)
    drawLine(LavaDeepRed.copy(alpha = 0.7f), Offset(cx + xSize, cy - xSize), Offset(cx - xSize, cy + xSize), 3.2f * worldScale, StrokeCap.Round)
  }

  private fun DrawScope.drawWindUpdraftHazard(
    hazard: DynamicHazard,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val left = toScreenX(hazard.startX)
    val right = toScreenX(hazard.endX)
    if (right < -60f || left > size.width + 60f) return

    val top = toScreenY(hazard.topY)
    val bottom = toScreenY(hazard.bottomY)
    val w = (right - left).coerceAtLeast(4f)
    val h = (bottom - top).coerceAtLeast(4f)

    drawRect(
      color = InkCyanAccent.copy(alpha = 0.09f),
      topLeft = Offset(left, top),
      size = Size(w, h)
    )
    // Rising wind streamlines
    for (col in 0..2) {
      val sx = left + w * (0.22f + col * 0.28f)
      val cycle = ((animTime * 1.9f + col * 0.37f) % 1f)
      val streakBot = bottom - cycle * (h - 35f * worldScale)
      val streakTop = (streakBot - 42f * worldScale).coerceAtLeast(top)
      drawLine(
        color = InkCyanAccent.copy(alpha = 0.65f),
        start = Offset(sx, streakBot),
        end = Offset(sx, streakTop),
        strokeWidth = 2.6f * worldScale,
        cap = StrokeCap.Round
      )
    }
  }

  private fun DrawScope.drawSwingingPendulumHazard(
    hazard: DynamicHazard,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val pivotX = toScreenX(hazard.x)
    val pivotY = toScreenY(hazard.y)
    val bobWorld = hazard.pendulumBobPositionAt(animTime)
    val bobX = toScreenX(bobWorld.x)
    val bobY = toScreenY(bobWorld.y)
    val maxReachPx = (hazard.armLength + hazard.bobRadius + 40f) * worldScale

    if (pivotX + maxReachPx < -60f || pivotX - maxReachPx > size.width + 60f) return

    val arcPath = reusableGuidePath.apply {
      reset()
      val steps = 16
      for (i in 0..steps) {
        val ratio = (i.toFloat() / steps) * 2f - 1f
        val a = ratio * hazard.maxAngleRadians
        val wx = hazard.x + sin(a) * hazard.armLength
        val wy = hazard.y + cos(a) * hazard.armLength
        if (i == 0) moveTo(toScreenX(wx), toScreenY(wy)) else lineTo(toScreenX(wx), toScreenY(wy))
      }
    }
    drawPath(
      path = arcPath,
      color = LavaOrange.copy(alpha = 0.25f),
      style = Stroke(
        width = 2.2f * worldScale,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
          intervals = floatArrayOf(10f * worldScale, 8f * worldScale),
          phase = 0f
        )
      )
    )

    drawCircle(
      color = SketchCharcoalBg,
      radius = 8.5f * worldScale,
      center = Offset(pivotX, pivotY)
    )
    drawCircle(
      color = ChalkWhite,
      radius = 8.5f * worldScale,
      center = Offset(pivotX, pivotY),
      style = Stroke(width = 2.6f * worldScale)
    )
    drawLine(
      color = PencilGraphite.copy(alpha = 0.85f),
      start = Offset(pivotX, pivotY),
      end = Offset(bobX, bobY),
      strokeWidth = 3.6f * worldScale,
      cap = StrokeCap.Round
    )

    val bobR = hazard.bobRadius * worldScale
    drawCircle(
      color = LavaOrange.copy(alpha = 0.28f),
      radius = bobR * 1.45f,
      center = Offset(bobX, bobY)
    )

    val spikeCount = 8
    val spinOffset = animTime * 2.8f
    for (i in 0 until spikeCount) {
      val angle = spinOffset + i * (2f * PI.toFloat() / spikeCount)
      val innerX = bobX + cos(angle) * (bobR * 0.85f)
      val innerY = bobY + sin(angle) * (bobR * 0.85f)
      val outerX = bobX + cos(angle) * (bobR * 1.38f)
      val outerY = bobY + sin(angle) * (bobR * 1.38f)
      drawLine(
        color = ChalkWhite,
        start = Offset(innerX, innerY),
        end = Offset(outerX, outerY),
        strokeWidth = 3.2f * worldScale,
        cap = StrokeCap.Round
      )
    }

    drawCircle(
      color = LavaDeepRed,
      radius = bobR,
      center = Offset(bobX, bobY)
    )
    drawCircle(
      color = LavaFlameYellow,
      radius = bobR * 0.48f,
      center = Offset(bobX, bobY)
    )
    drawCircle(
      color = ChalkWhite,
      radius = bobR,
      center = Offset(bobX, bobY),
      style = Stroke(width = 2.8f * worldScale)
    )
  }

  private fun DrawScope.drawScribblerEnemy(
    enemy: ScribblerEnemy,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val fx = toScreenX(enemy.x)
    val fy = toScreenY(enemy.y)
    if (fx < -60f || fx > size.width + 60f) return

    val redColor = Color(0xFFEE7D72)
    val strokeW = 3.8f * worldScale
    val hipY = fy - 22f * worldScale
    val shoulderY = hipY - 18f * worldScale
    val headY = shoulderY - 10f * worldScale
    val swing = sin(enemy.runPhase) * 9f * worldScale

    // Red glow
    drawCircle(
      color = redColor.copy(alpha = 0.22f),
      radius = 28f * worldScale,
      center = Offset(fx, hipY)
    )
    // Legs
    drawLine(redColor, Offset(fx, hipY), Offset(fx + swing, fy), strokeW, StrokeCap.Round)
    drawLine(redColor, Offset(fx, hipY), Offset(fx - swing, fy), strokeW, StrokeCap.Round)
    // Torso
    drawLine(redColor, Offset(fx, hipY), Offset(fx, shoulderY), strokeW, StrokeCap.Round)
    // Arms
    drawLine(redColor, Offset(fx, shoulderY), Offset(fx - swing, hipY - 4f * worldScale), strokeW * 0.9f, StrokeCap.Round)
    drawLine(redColor, Offset(fx, shoulderY), Offset(fx + swing, hipY - 4f * worldScale), strokeW * 0.9f, StrokeCap.Round)
    // Head
    drawCircle(SketchCharcoalBg, 9.5f * worldScale, Offset(fx, headY))
    drawCircle(redColor, 9.5f * worldScale, Offset(fx, headY), style = Stroke(width = strokeW))
    // Angry V-brow
    drawLine(
      color = ChalkWhite,
      start = Offset(fx - 5f * worldScale, headY - 3f * worldScale),
      end = Offset(fx, headY),
      strokeWidth = 2f * worldScale
    )
    drawLine(
      color = ChalkWhite,
      start = Offset(fx + 5f * worldScale, headY - 3f * worldScale),
      end = Offset(fx, headY),
      strokeWidth = 2f * worldScale
    )
  }

  private fun DrawScope.drawCalloutBubbleAndTelegraph(
    callout: CalloutMove,
    stickman: StickmanState,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val bx = toScreenX(stickman.footX)
    val by = toScreenY(stickman.footY - WorldConstants.STICKMAN_HEIGHT - 28f)
    val bw = 96f * worldScale
    val bh = 28f * worldScale

    // Speech bubble box + burning fuse bar
    drawRect(
      color = SketchCharcoalBg.copy(alpha = 0.92f),
      topLeft = Offset(bx - bw * 0.5f, by - bh),
      size = Size(bw, bh)
    )
    drawRect(
      color = LavaFlameYellow,
      topLeft = Offset(bx - bw * 0.5f, by - bh),
      size = Size(bw, bh),
      style = Stroke(width = 2.4f * worldScale)
    )
    drawLine(
      color = LavaFlameYellow,
      start = Offset(bx - bw * 0.42f, by - 5f * worldScale),
      end = Offset(bx - bw * 0.42f + bw * 0.84f * callout.fuseProgress, by - 5f * worldScale),
      strokeWidth = 3.5f * worldScale,
      cap = StrokeCap.Round
    )

    // Landing X telegraph for JUMP!
    if (callout.landingTargetX != null && callout.landingTargetY != null) {
      val tx = toScreenX(callout.landingTargetX)
      val ty = toScreenY(callout.landingTargetY) - 6f * worldScale
      val sz = 11f * worldScale
      drawLine(LavaFlameYellow, Offset(tx - sz, ty - sz), Offset(tx + sz, ty + sz), 3.2f * worldScale, StrokeCap.Round)
      drawLine(LavaFlameYellow, Offset(tx + sz, ty - sz), Offset(tx - sz, ty + sz), 3.2f * worldScale, StrokeCap.Round)
    }
  }

  private fun DrawScope.drawAnimatedStickman(
    stickman: StickmanState,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val phase = stickman.runCyclePhase
    val outlineBlack = Color(0xFF140707)
    val rimCrimsonGlow = Color(0xFFB71C1C)
    val bodyWhite = Color(0xFFFFFFFF)
    val backLimbGrey = Color(0xFFBEC4CE)
    val hairCrimson = Color(0xFFD50000)
    val hairDarkShadow = Color(0xFF7F0000)
    val hairHighlight = Color(0xFFFF3D00)
    val auraDarkRed = Color(0xFF8E0000)
    val auraCrimson = Color(0xFFE51C23)
    val auraOrange = Color(0xFFFF6D00)
    val auraYellow = Color(0xFFFFEA00)
    val rockDarkBrown = Color(0xFF4E342E)
    val dustTaupe = Color(0xFF8D7B68)

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    // Draws a single 2D cartoon limb segment (glow + black outline + solid fill + optional hand/foot cap)
    fun drawCartoonSegment(
      x1: Float,
      y1: Float,
      x2: Float,
      y2: Float,
      fillColor: Color,
      thicknessWorld: Float,
      pass: Int, // 0 = rim glow, 1 = black outline, 2 = fill
      endCapRadiusWorld: Float = 0f
    ) {
      val s1 = Offset(toScreenX(x1), toScreenY(y1))
      val s2 = Offset(toScreenX(x2), toScreenY(y2))
      when (pass) {
        0 -> {
          drawLine(
            color = rimCrimsonGlow.copy(alpha = 0.28f),
            start = s1,
            end = s2,
            strokeWidth = (thicknessWorld + 7.0f) * worldScale,
            cap = StrokeCap.Round
          )
          if (endCapRadiusWorld > 0f) {
            drawCircle(
              color = rimCrimsonGlow.copy(alpha = 0.28f),
              radius = (endCapRadiusWorld + 3.5f) * worldScale,
              center = s2
            )
          }
        }
        1 -> {
          drawLine(
            color = outlineBlack,
            start = s1,
            end = s2,
            strokeWidth = (thicknessWorld + 4.4f) * worldScale,
            cap = StrokeCap.Round
          )
          if (endCapRadiusWorld > 0f) {
            drawCircle(
              color = outlineBlack,
              radius = (endCapRadiusWorld + 2.2f) * worldScale,
              center = s2
            )
          }
        }
        2 -> {
          drawLine(
            color = fillColor,
            start = s1,
            end = s2,
            strokeWidth = thicknessWorld * worldScale,
            cap = StrokeCap.Round
          )
          if (endCapRadiusWorld > 0f) {
            drawCircle(
              color = fillColor,
              radius = endCapRadiusWorld * worldScale,
              center = s2
            )
          }
        }
      }
    }

    // Renders the Super Saiyan God spiky crimson hair crown with smooth 60fps wind wave & thick black outline
    fun drawFightingGameHair(
      headWorldX: Float,
      headWorldY: Float,
      headTilt: Float,
      windDrag: Float
    ) {
      val hx = toScreenX(headWorldX)
      val hy = toScreenY(headWorldY)
      val r = (WorldConstants.STICKMAN_HEAD_RADIUS + 0.8f) * worldScale

      val spikeAngles = floatArrayOf(-2.88f, -2.52f, -2.14f, -1.76f, -1.42f, -1.08f, -0.74f, -0.38f)
      val spikeLengths = floatArrayOf(17f, 24f, 30f, 32f, 29f, 25f, 20f, 14f)

      val hairPath = reusableHairPath.apply {
        reset()
        moveTo(hx - r * 0.92f, hy + r * 0.15f)
        for (i in spikeAngles.indices) {
          val flutter = sin(animTime * 9.5f + i * 0.85f) * 0.045f
          val ang = spikeAngles[i] + headTilt + windDrag * 0.20f + flutter
          val len = spikeLengths[i] * worldScale
          val midAng = ang - 0.08f
          val midLen = len * 0.68f
          val tipX = hx + cos(ang) * len
          val tipY = hy + sin(ang) * len
          lineTo(hx + cos(midAng) * midLen, hy + sin(midAng) * midLen)
          lineTo(tipX, tipY)
          val valleyAng = ang + 0.20f
          val valleyLen = len * 0.46f
          lineTo(hx + cos(valleyAng) * valleyLen, hy + sin(valleyAng) * valleyLen)
        }
        close()
      }

      drawPath(
        path = hairPath,
        color = outlineBlack,
        style = Stroke(width = 4.2f * worldScale, join = StrokeJoin.Round, cap = StrokeCap.Round)
      )
      drawPath(path = hairPath, color = hairCrimson)
      drawPath(
        path = hairPath,
        color = hairHighlight.copy(alpha = 0.65f),
        style = Stroke(width = 1.6f * worldScale, join = StrokeJoin.Round)
      )

      for (i in 1..5) {
        val ang = spikeAngles[i] + headTilt + windDrag * 0.20f
        val innerStart = Offset(hx + cos(ang) * (r * 0.85f), hy + sin(ang) * (r * 0.85f))
        val innerEnd = Offset(hx + cos(ang) * (spikeLengths[i] * 0.66f * worldScale), hy + sin(ang) * (spikeLengths[i] * 0.66f * worldScale))
        drawLine(
          color = hairDarkShadow,
          start = innerStart,
          end = innerEnd,
          strokeWidth = 1.9f * worldScale,
          cap = StrokeCap.Round
        )
      }
    }

    // Renders the 2D cartoon head & expressive fighting-game face matching Reference Frames 1, 2, 3, and 4–6
    fun drawCartoonHeadAndFace(
      headWorldX: Float,
      headWorldY: Float,
      faceDirX: Float,
      expression: Int,
      hasHair: Boolean,
      headTilt: Float,
      windDrag: Float
    ) {
      if (hasHair) {
        drawFightingGameHair(headWorldX, headWorldY, headTilt, windDrag)
      }

      val hCenter = Offset(toScreenX(headWorldX), toScreenY(headWorldY))
      val hRadius = (WorldConstants.STICKMAN_HEAD_RADIUS + 0.6f) * worldScale

      drawCircle(
        color = rimCrimsonGlow.copy(alpha = 0.28f),
        radius = hRadius + 4.0f * worldScale,
        center = hCenter
      )
      drawCircle(
        color = outlineBlack,
        radius = hRadius + 2.3f * worldScale,
        center = hCenter
      )
      drawCircle(
        color = if (expression == 2) Color(0xFFFFCDD2) else bodyWhite,
        radius = hRadius,
        center = hCenter
      )

      if (expression == 2) {
        drawCircle(
          color = hairCrimson.copy(alpha = 0.42f),
          radius = hRadius * 0.92f,
          center = Offset(hCenter.x, hCenter.y - hRadius * 0.12f)
        )
      }

      val fx = hCenter.x + faceDirX * 2.8f * worldScale
      val fy = hCenter.y + 0.5f * worldScale

      when (expression) {
        1 -> {
          val leftEyeX = fx - 3.2f * worldScale
          val rightEyeX = fx + 3.4f * worldScale
          val eyeY = fy - 1.8f * worldScale

          drawLine(outlineBlack, Offset(leftEyeX - 2.6f * worldScale, eyeY - 2.2f * worldScale), Offset(leftEyeX + 0.8f * worldScale, eyeY), 2.2f * worldScale, StrokeCap.Round)
          drawLine(outlineBlack, Offset(leftEyeX + 0.8f * worldScale, eyeY), Offset(leftEyeX - 2.6f * worldScale, eyeY + 2.0f * worldScale), 2.2f * worldScale, StrokeCap.Round)

          drawLine(outlineBlack, Offset(rightEyeX + 2.6f * worldScale, eyeY - 2.2f * worldScale), Offset(rightEyeX - 0.8f * worldScale, eyeY), 2.2f * worldScale, StrokeCap.Round)
          drawLine(outlineBlack, Offset(rightEyeX - 0.8f * worldScale, eyeY), Offset(rightEyeX + 2.6f * worldScale, eyeY + 2.0f * worldScale), 2.2f * worldScale, StrokeCap.Round)

          val mouthCenter = Offset(fx + 0.4f * worldScale, fy + 4.2f * worldScale)
          drawCircle(color = outlineBlack, radius = 3.8f * worldScale, center = mouthCenter)
          drawCircle(color = Color(0xFF7F0000), radius = 2.8f * worldScale, center = mouthCenter)
          drawLine(
            color = bodyWhite,
            start = Offset(mouthCenter.x - 2.2f * worldScale, mouthCenter.y - 1.6f * worldScale),
            end = Offset(mouthCenter.x + 2.2f * worldScale, mouthCenter.y - 1.6f * worldScale),
            strokeWidth = 1.6f * worldScale,
            cap = StrokeCap.Round
          )
        }

        0, 2 -> {
          val leftEyeX = fx - 2.8f * worldScale
          val rightEyeX = fx + 3.6f * worldScale
          val eyeY = fy - 1.2f * worldScale

          drawCircle(color = bodyWhite, radius = 2.1f * worldScale, center = Offset(leftEyeX, eyeY + 0.6f * worldScale))
          drawCircle(color = bodyWhite, radius = 2.1f * worldScale, center = Offset(rightEyeX, eyeY + 0.6f * worldScale))
          drawCircle(color = hairCrimson, radius = 1.3f * worldScale, center = Offset(leftEyeX + 0.5f * worldScale, eyeY + 0.8f * worldScale))
          drawCircle(color = hairCrimson, radius = 1.3f * worldScale, center = Offset(rightEyeX + 0.5f * worldScale, eyeY + 0.8f * worldScale))
          drawCircle(color = outlineBlack, radius = 0.7f * worldScale, center = Offset(leftEyeX + 0.5f * worldScale, eyeY + 0.8f * worldScale))
          drawCircle(color = outlineBlack, radius = 0.7f * worldScale, center = Offset(rightEyeX + 0.5f * worldScale, eyeY + 0.8f * worldScale))

          drawLine(
            color = outlineBlack,
            start = Offset(leftEyeX - 3.2f * worldScale, eyeY - 3.0f * worldScale),
            end = Offset(leftEyeX + 1.8f * worldScale, eyeY + 0.2f * worldScale),
            strokeWidth = 2.3f * worldScale,
            cap = StrokeCap.Round
          )
          drawLine(
            color = outlineBlack,
            start = Offset(rightEyeX + 3.0f * worldScale, eyeY - 3.0f * worldScale),
            end = Offset(rightEyeX - 1.8f * worldScale, eyeY + 0.2f * worldScale),
            strokeWidth = 2.3f * worldScale,
            cap = StrokeCap.Round
          )

          drawLine(
            color = outlineBlack,
            start = Offset(fx - 2.0f * worldScale, fy + 4.2f * worldScale),
            end = Offset(fx + 2.8f * worldScale, fy + 3.6f * worldScale),
            strokeWidth = 2.0f * worldScale,
            cap = StrokeCap.Round
          )
        }

        else -> {
          drawLine(
            color = outlineBlack,
            start = Offset(fx - 2.5f * worldScale, fy),
            end = Offset(fx + 2.5f * worldScale, fy - 1.2f * worldScale),
            strokeWidth = 2.0f * worldScale,
            cap = StrokeCap.Round
          )
        }
      }
    }

    // 60 FPS continuous fiery red-orange aura driven by animTime
    fun drawFieryFightingAura(
      centerWorldX: Float,
      footWorldY: Float,
      intensity: Float,
      leanBackX: Float,
      isBoosted: Boolean
    ) {
      if (intensity <= 0.05f) return
      val baseSX = toScreenX(centerWorldX)
      val baseSY = toScreenY(footWorldY)
      val auraH = (82f + intensity * 24f) * worldScale
      val auraHalfW = (30f + intensity * 14f) * worldScale
      val windTilt = leanBackX * worldScale

      drawCircle(
        color = auraCrimson.copy(alpha = (0.24f * intensity).coerceAtMost(0.40f)),
        radius = auraHalfW * 1.50f,
        center = Offset(baseSX + windTilt * 0.35f, baseSY - auraH * 0.45f)
      )

      val outerSpikes = 11
      val outerAura = reusableAuraOuterPath.apply {
        reset()
        moveTo(baseSX - auraHalfW * 0.88f, baseSY + 2f * worldScale)
        for (i in 0..outerSpikes) {
          val t = i.toFloat() / outerSpikes
          val angle = PI.toFloat() + t * PI.toFloat()
          val wave = sin(animTime * 11f + i * 1.9f) * 6.0f * worldScale
          val spikeBoost = if (i % 2 == 1) 1.22f else 0.75f
          val rx = cos(angle) * auraHalfW * spikeBoost + windTilt * (1f - kotlin.math.abs(t - 0.5f)) + wave * 0.35f
          val ry = sin(angle) * auraH * spikeBoost - kotlin.math.abs(cos(i * 1.4f + animTime * 8.5f)) * 12f * worldScale
          lineTo(baseSX + rx, baseSY + ry * 0.94f)
        }
        lineTo(baseSX + auraHalfW * 0.88f, baseSY + 2f * worldScale)
        close()
      }
      drawPath(
        path = outerAura,
        color = auraDarkRed.copy(alpha = (0.42f * intensity).coerceAtMost(0.72f)),
        style = Stroke(width = 3.2f * worldScale, join = StrokeJoin.Round)
      )
      drawPath(
        path = outerAura,
        color = auraCrimson.copy(alpha = (0.64f * intensity).coerceAtMost(0.85f))
      )

      val midSpikes = 9
      val innerAura = reusableAuraInnerPath.apply {
        reset()
        moveTo(baseSX - auraHalfW * 0.64f, baseSY + 1f * worldScale)
        for (i in 0..midSpikes) {
          val t = i.toFloat() / midSpikes
          val angle = PI.toFloat() + t * PI.toFloat()
          val wave = cos(animTime * 13.5f + i * 2.3f) * 4.8f * worldScale
          val spikeBoost = if (i % 2 == 0) 1.16f else 0.76f
          val rx = cos(angle) * (auraHalfW * 0.70f * spikeBoost) + windTilt * 0.65f + wave * 0.3f
          val ry = sin(angle) * (auraH * 0.76f * spikeBoost)
          lineTo(baseSX + rx, baseSY + ry)
        }
        lineTo(baseSX + auraHalfW * 0.64f, baseSY + 1f * worldScale)
        close()
      }
      drawPath(
        path = innerAura,
        color = (if (isBoosted) auraYellow else auraOrange).copy(alpha = (0.72f * intensity).coerceAtMost(0.90f))
      )

      for (f in 0..3) {
        val cycle = (animTime * 5.2f + f * 1.57f) % (PI.toFloat() * 2f)
        val rise = (cycle / (PI.toFloat() * 2f))
        val side = if (f % 2 == 0) -1f else 1f
        val fx = baseSX + side * (auraHalfW * (0.65f + 0.22f * (f % 3))) + windTilt * 0.5f
        val fy = baseSY - auraH * (0.35f + rise * 0.65f)
        val flameH = (10f * (1f - rise * 0.5f)) * worldScale
        drawLine(
          color = (if (f % 2 == 0) auraCrimson else auraOrange).copy(alpha = 1f - rise * 0.6f),
          start = Offset(fx, fy),
          end = Offset(fx + windTilt * 0.15f, fy - flameH),
          strokeWidth = 3.5f * (1f - rise * 0.4f) * worldScale,
          cap = StrokeCap.Round
        )
      }

      drawCircle(
        color = auraYellow.copy(alpha = (0.50f * intensity).coerceAtMost(0.80f)),
        radius = 11f * worldScale,
        center = Offset(baseSX, baseSY - 4f * worldScale)
      )

      for (b in 0..1) {
        val side = if (b % 2 == 0) -1f else 1f
        val pulse = 0.55f + 0.45f * sin(animTime * 16f + b * 2.4f)
        val sx = baseSX + side * (7f + b * 4f) * worldScale
        val sy = baseSY - (15f + b * 18f) * worldScale
        val m1x = sx + side * (10f + sin(animTime * 14f + b) * 4.5f) * worldScale
        val m1y = sy - 10f * worldScale
        val m2x = sx - side * 3f * worldScale
        val m2y = m1y - 9f * worldScale
        val ex = m2x + side * 7f * worldScale
        val ey = m2y - 9f * worldScale

        val boltPath = reusableLightningPath.apply {
          reset()
          moveTo(sx, sy)
          lineTo(m1x, m1y)
          lineTo(m2x, m2y)
          lineTo(ex, ey)
        }
        drawPath(
          path = boltPath,
          color = auraCrimson.copy(alpha = (0.65f * intensity * pulse).coerceIn(0f, 1f)),
          style = Stroke(width = 3.4f * worldScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
          path = boltPath,
          color = bodyWhite.copy(alpha = (0.92f * intensity * pulse).coerceIn(0f, 1f)),
          style = Stroke(width = 1.6f * worldScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
      }
    }

    fun drawCartoonCurve(
      x1: Float,
      y1: Float,
      cx: Float,
      cy: Float,
      x2: Float,
      y2: Float,
      fillColor: Color,
      thicknessWorld: Float,
      pass: Int
    ) {
      val path = reusablePrimaryLinePath.apply {
        reset()
        moveTo(toScreenX(x1), toScreenY(y1))
        quadraticTo(toScreenX(cx), toScreenY(cy), toScreenX(x2), toScreenY(y2))
      }
      when (pass) {
        0 -> drawPath(
          path = path,
          color = auraCrimson.copy(alpha = 0.28f),
          style = Stroke(width = (thicknessWorld + 7.2f) * worldScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        1 -> drawPath(
          path = path,
          color = outlineBlack,
          style = Stroke(width = (thicknessWorld + 4.6f) * worldScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        else -> drawPath(
          path = path,
          color = fillColor,
          style = Stroke(width = thicknessWorld * worldScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
      }
    }

    fun renderCartoonBodyPose(
      hipWX: Float,
      hipWY: Float,
      shWX: Float,
      shWY: Float,
      hdWX: Float,
      hdWY: Float,
      frontKnee: Offset,
      frontAnkle: Offset,
      backKnee: Offset,
      backAnkle: Offset,
      frontElbow: Offset,
      frontHand: Offset,
      backElbow: Offset,
      backHand: Offset,
      faceDirX: Float,
      expression: Int,
      hasHair: Boolean,
      headTilt: Float,
      windDrag: Float,
      midWX: Float = (hipWX + shWX) * 0.5f,
      midWY: Float = (hipWY + shWY) * 0.5f,
      nckWX: Float = shWX,
      nckWY: Float = shWY,
      ntpWX: Float = shWX,
      ntpWY: Float = shWY,
      frontToe: Offset? = null,
      backToe: Offset? = null
    ) {
      // 1. Rear Shaded Arm & Leg (+ Articulated Toe)
      for (pass in 0..2) {
        drawCartoonSegment(shWX, shWY, backElbow.x, backElbow.y, backLimbGrey, 4.4f, pass)
        drawCartoonSegment(backElbow.x, backElbow.y, backHand.x, backHand.y, backLimbGrey, 4.0f, pass, endCapRadiusWorld = 2.4f)
        drawCartoonSegment(hipWX, hipWY, backKnee.x, backKnee.y, backLimbGrey, 5.0f, pass)
        drawCartoonSegment(backKnee.x, backKnee.y, backAnkle.x, backAnkle.y, backLimbGrey, 4.5f, pass, endCapRadiusWorld = if (backToe == null) 2.8f else 0f)
        if (backToe != null) {
          drawCartoonSegment(backAnkle.x, backAnkle.y, backToe.x, backToe.y, backLimbGrey, 4.2f, pass, endCapRadiusWorld = 2.2f)
        }
      }

      // 2. Curved Quadratic Spine (HIP -> MID -> NCK -> NTP) + Front Leg (+ Articulated Toe)
      for (pass in 0..2) {
        drawCartoonCurve(hipWX, hipWY, midWX, midWY, nckWX, nckWY, bodyWhite, 6.4f, pass)
        drawCartoonSegment(nckWX, nckWY, ntpWX, ntpWY, bodyWhite, 5.4f, pass)
        drawCartoonSegment(hipWX, hipWY, frontKnee.x, frontKnee.y, bodyWhite, 5.2f, pass)
        drawCartoonSegment(frontKnee.x, frontKnee.y, frontAnkle.x, frontAnkle.y, bodyWhite, 4.7f, pass, endCapRadiusWorld = if (frontToe == null) 3.0f else 0f)
        if (frontToe != null) {
          drawCartoonSegment(frontAnkle.x, frontAnkle.y, frontToe.x, frontToe.y, bodyWhite, 4.4f, pass, endCapRadiusWorld = 2.4f)
        }
      }

      // 3. Head, Spiky Crimson Hair & Expressive Face
      drawCartoonHeadAndFace(
        headWorldX = hdWX,
        headWorldY = hdWY,
        faceDirX = faceDirX,
        expression = expression,
        hasHair = hasHair,
        headTilt = headTilt,
        windDrag = windDrag
      )

      // 4. Foreground White Arm & Hand
      for (pass in 0..2) {
        drawCartoonSegment(shWX, shWY, frontElbow.x, frontElbow.y, bodyWhite, 4.6f, pass)
        drawCartoonSegment(frontElbow.x, frontElbow.y, frontHand.x, frontHand.y, bodyWhite, 4.2f, pass, endCapRadiusWorld = 2.6f)
      }
    }

    // =========================================================================
    // 6-FRAME KNOCKDOWN & EXPLOSION SEQUENCE (Matches Reference Poses 1 -> 6)
    // =========================================================================
    if (!stickman.isAlive) {
      val dt = stickman.deathTimer.coerceIn(0f, 1.45f)
      val bx = stickman.footX
      val by = stickman.footY

      when {
        dt < 0.22f -> {
          drawFieryFightingAura(bx, by, intensity = 1.35f, leanBackX = -4f, isBoosted = false)
          for (d in -1..1 step 2) {
            drawCircle(dustTaupe.copy(alpha = 0.75f), 6.5f * worldScale, Offset(toScreenX(bx + d * 18f), toScreenY(by - 4f)))
            drawCircle(rockDarkBrown, 2.8f * worldScale, Offset(toScreenX(bx + d * 24f), toScreenY(by - 14f)))
          }
          val hipX = bx - 3f
          val hipY = by - 25f
          val shX = hipX + 7f
          val shY = hipY - 21f
          val hdX = shX + 4f
          val hdY = shY - 12f
          renderCartoonBodyPose(
            hipWX = hipX, hipWY = hipY, shWX = shX, shWY = shY, hdWX = hdX, hdWY = hdY,
            frontKnee = Offset(hipX + 6f, hipY + 13f), frontAnkle = Offset(bx + 7f, by),
            backKnee = Offset(hipX - 5f, hipY + 13f), backAnkle = Offset(bx - 9f, by),
            frontElbow = Offset(shX - 11f, shY + 6f), frontHand = Offset(shX - 16f, shY + 18f),
            backElbow = Offset(shX + 6f, shY + 11f), backHand = Offset(shX + 9f, shY + 22f),
            faceDirX = 1f, expression = 1, hasHair = true, headTilt = 0.12f, windDrag = -0.15f
          )
        }

        dt < 0.44f -> {
          drawFieryFightingAura(bx, by, intensity = 1.45f, leanBackX = -6f, isBoosted = false)
          val hipX = bx - 4f
          val hipY = by - 16f
          val shX = hipX + 8f
          val shY = hipY - 19f
          val hdX = shX + 4f
          val hdY = shY - 11f
          renderCartoonBodyPose(
            hipWX = hipX, hipWY = hipY, shWX = shX, shWY = shY, hdWX = hdX, hdWY = hdY,
            frontKnee = Offset(hipX + 12f, hipY + 2f), frontAnkle = Offset(bx + 12f, by),
            backKnee = Offset(hipX - 6f, by - 2f), backAnkle = Offset(hipX - 20f, by - 2f),
            frontElbow = Offset(shX - 12f, shY + 3f), frontHand = Offset(shX - 19f, shY + 13f),
            backElbow = Offset(shX + 5f, shY + 10f), backHand = Offset(shX + 7f, by - 2f),
            faceDirX = 1f, expression = 0, hasHair = true, headTilt = 0.22f, windDrag = -0.25f
          )
          drawCircle(auraYellow, 6.5f * worldScale, Offset(toScreenX(bx + 12f), toScreenY(by - 2f)))
        }

        dt < 0.68f -> {
          drawFieryFightingAura(bx, by, intensity = 1.60f, leanBackX = -10f, isBoosted = true)
          val hipX = bx - 8f
          val hipY = by - 9f
          val shX = bx + 10f
          val shY = by - 11f
          val hdX = shX + 8f
          val hdY = shY - 9f
          renderCartoonBodyPose(
            hipWX = hipX, hipWY = hipY, shWX = shX, shWY = shY, hdWX = hdX, hdWY = hdY,
            frontKnee = Offset(hipX - 12f, by - 3f), frontAnkle = Offset(hipX - 24f, by - 6f),
            backKnee = Offset(hipX - 10f, by - 7f), backAnkle = Offset(hipX - 22f, by - 12f),
            frontElbow = Offset(shX - 3f, by - 2f), frontHand = Offset(shX + 6f, by - 2f),
            backElbow = Offset(shX + 5f, by - 2f), backHand = Offset(shX + 12f, by - 2f),
            faceDirX = 1f, expression = 2, hasHair = true, headTilt = 0.35f, windDrag = -0.35f
          )
        }

        dt < 0.90f -> {
          val hipX = bx + 4f
          val hipY = by - 5f
          val shX = bx - 16f
          val shY = by - 5f
          val hdX = shX - 12f
          val hdY = by - 8f
          renderCartoonBodyPose(
            hipWX = hipX, hipWY = hipY, shWX = shX, shWY = shY, hdWX = hdX, hdWY = hdY,
            frontKnee = Offset(hipX + 14f, by - 4f), frontAnkle = Offset(hipX + 26f, by - 3f),
            backKnee = Offset(hipX + 14f, by - 10f), backAnkle = Offset(hipX + 25f, by - 4f),
            frontElbow = Offset(shX + 8f, by - 2f), frontHand = Offset(shX + 18f, by - 2f),
            backElbow = Offset(shX + 6f, by - 5f), backHand = Offset(shX + 14f, by - 5f),
            faceDirX = -1f, expression = 3, hasHair = true, headTilt = -1.2f, windDrag = -1.1f
          )
        }

        dt < 1.15f -> {
          val burstT = ((dt - 0.90f) / 0.25f).coerceIn(0f, 1f)
          val hipX = bx + 4f
          val hipY = by - 5f
          val shX = bx - 16f
          val shY = by - 5f
          val hdX = shX - 12f
          val hdY = by - 8f

          drawFieryFightingAura(bx - 10f, by, intensity = 1.4f * (1f - burstT * 0.4f), leanBackX = 0f, isBoosted = true)

          renderCartoonBodyPose(
            hipWX = hipX, hipWY = hipY, shWX = shX, shWY = shY, hdWX = hdX, hdWY = hdY,
            frontKnee = Offset(hipX + 14f, by - 4f), frontAnkle = Offset(hipX + 26f, by - 3f),
            backKnee = Offset(hipX + 14f, by - 10f), backAnkle = Offset(hipX + 25f, by - 4f),
            frontElbow = Offset(shX + 8f, by - 2f), frontHand = Offset(shX + 18f, by - 2f),
            backElbow = Offset(shX + 6f, by - 5f), backHand = Offset(shX + 14f, by - 5f),
            faceDirX = -1f, expression = 3, hasHair = true, headTilt = -1.2f, windDrag = -1.1f
          )

          val shardAngles = floatArrayOf(-2.65f, -2.15f, -1.55f, -0.95f, -0.45f)
          for (i in shardAngles.indices) {
            val ang = shardAngles[i]
            val dist = (22f + burstT * 52f + i * 4f) * worldScale
            val rx = toScreenX(bx - 8f) + cos(ang) * dist
            val ry = toScreenY(by - 6f) + sin(ang) * dist
            val rSize = (6.5f - (i % 2) * 1.5f) * worldScale
            drawCircle(outlineBlack, rSize + 2.0f * worldScale, Offset(rx, ry))
            drawCircle(rockDarkBrown, rSize, Offset(rx, ry))
          }
        }

        else -> {
          val hipX = bx + 4f
          val hipY = by - 5f
          val shX = bx - 16f
          val shY = by - 5f
          val hdX = shX - 12f
          val hdY = by - 8f
          renderCartoonBodyPose(
            hipWX = hipX, hipWY = hipY, shWX = shX, shWY = shY, hdWX = hdX, hdWY = hdY,
            frontKnee = Offset(hipX + 14f, by - 4f), frontAnkle = Offset(hipX + 26f, by - 3f),
            backKnee = Offset(hipX + 14f, by - 8f), backAnkle = Offset(hipX + 25f, by - 3f),
            frontElbow = Offset(shX + 8f, by - 2f), frontHand = Offset(shX + 18f, by - 2f),
            backElbow = Offset(shX + 6f, by - 4f), backHand = Offset(shX + 14f, by - 4f),
            faceDirX = -1f, expression = 3, hasHair = false, headTilt = 0f, windDrag = 0f
          )
          val hcx = toScreenX(hdX)
          val hcy = toScreenY(hdY)
          for (s in -1..1) {
            val sx = hcx + s * 5.5f * worldScale
            val sy = hcy - (11f + ((dt * 18f + s * 3f) % 9f)) * worldScale
            drawCircle(rockDarkBrown, 1.8f * worldScale, Offset(sx, sy))
            drawLine(
              color = rockDarkBrown.copy(alpha = 0.65f),
              start = Offset(sx, hcy - 7f * worldScale),
              end = Offset(sx, sy - 5f * worldScale),
              strokeWidth = 1.5f * worldScale,
              cap = StrokeCap.Round
            )
          }
        }
      }
      return
    }

    // =========================================================================
    // EXACT 16-JOINT CHALK-RUNNER RIG & KINEMATICS (From Claude Artifact)
    // =========================================================================
    // Proportions in world units:
    // THIGH = 14, SHIN = 14, UPPER = 11, FORE = 10.5, TORSO = 21, NECK = 3, HEAD_R = 9.2, TOE = 4
    val THIGH = 14f
    val SHIN = 14f
    val UPPER = 11f
    val FORE = 10.5f
    val TORSO = 21f
    val NECK = 3f
    val HEAD_R = 9.2f
    val TOE = 4f

    // Joint indices: x at 2*i, y at 2*i+1
    val HIP = 0
    val MID = 1
    val NCK = 2
    val NTP = 3
    val HED = 4
    val SHO = 5
    val K1 = 6
    val F1 = 7
    val T1 = 8
    val K2 = 9
    val F2 = 10
    val T2 = 11
    val E1 = 12
    val H1 = 13
    val E2 = 14
    val H2 = 15
    val NJ = 16

    // Key poses from art-hero.js (KP)
    val kpStretch = floatArrayOf(0f, -31f, 0.08f, -0.6f, -0.3f, 0.6f, 1.0f, -0.42f, 0.2f, 2.5f, 0.1f, 2.3f, 0.35f, 0.3f, -0.5f)
    val kpTuck = floatArrayOf(0f, -33f, 0.22f, 1.4f, -0.05f, 1.35f, 1.7f, -0.45f, 1.05f, 1.75f, 0.35f, -1.25f, -0.3f, -0.2f, -0.3f)
    val kpReach = floatArrayOf(0f, -30.5f, 0.1f, 0.4f, 0.1f, 0.5f, 0.6f, -0.08f, 0.3f, 2.3f, 0.45f, -2.35f, -0.35f, 0.2f, 0.2f)

    fun smooth01(x: Float): Float {
      val t = x.coerceIn(0f, 1f)
      return t * t * (3f - 2f * t)
    }

    // Run cycle from art-hero.js: thigh swing, knee fold in the swing phase, soft knee in stance phase,
    // pumping arms, hips lowest at mid-stance and highest in flight phase, head nod that lags the body.
    fun runK(P: FloatArray, p: Float, dash: Boolean) {
      val thA = if (dash) 0.85f else 0.66f
      val fold = if (dash) 2.0f else 1.7f
      val armA = if (dash) 1.05f else 0.85f
      P[0] = 0f
      P[1] = (if (dash) -25.6f else -26.3f) - (if (dash) 2.6f else 3.4f) * kotlin.math.abs(sin(p))
      P[2] = (if (dash) 0.5f else 0.24f) + 0.04f * cos(2f * p)
      P[3] = if (dash) 2.0f else 1.2f
      P[4] = (if (dash) -0.38f else -0.16f) + 0.06f * sin(2f * p - 0.8f)
      for (side in 0..1) {
        val q = p + side * PI.toFloat()
        val sq = sin(q)
        val cq = cos(q)
        val sw = kotlin.math.max(0f, cos(q + 0.15f))
        P[5 + side * 2] = 0.05f + thA * sq
        P[6 + side * 2] = 0.2f + fold * sw * sw + 0.35f * kotlin.math.max(0f, -cq)
        P[9 + side * 2] = 0.1f - armA * sq
        P[10 + side * 2] = 1.5f - 0.3f * sq
        P[13 + side] = 0.25f * kotlin.math.max(0f, -cq) - 0.45f * sw
      }
    }

    fun setJ(J: FloatArray, idx: Int, x: Float, y: Float) {
      J[idx * 2] = x
      J[idx * 2 + 1] = y
    }

    fun limbJ(J: FloatArray, r: Int, m: Int, e: Int, a1: Float, a2: Float, l1: Float, l2: Float) {
      val x = J[r * 2] + sin(a1) * l1
      val y = J[r * 2 + 1] + cos(a1) * l1
      setJ(J, m, x, y)
      setJ(J, e, x + sin(a2) * l2, y + cos(a2) * l2)
    }

    fun toeJ(J: FloatArray, f: Int, t: Int, a: Float) {
      setJ(J, t, J[f * 2] + sin(a) * TOE, J[f * 2 + 1] + cos(a) * TOE)
    }

    fun buildRig(
      pose: String,
      ph: Float,
      x: Float,
      y: Float,
      facing: Float = 1f,
      applySquashAndSlope: Boolean = true
    ): Pair<FloatArray, Float> {
      val K = FloatArray(15)
      var sx = 1f
      var sy = 1f
      var px = 0f
      var py = 0f
      var rot = 0f
      var dy = 0f

      when (pose) {
        "dash", "run" -> {
          runK(K, ph, pose == "dash")
        }
        "jump" -> {
          val k = (-stickman.velocityY / 520f).coerceIn(0f, 1f)
          val sm = smooth01(k)
          for (i in 0 until 15) K[i] = lerp(kpTuck[i], kpStretch[i], sm)
          px = K[0]
          py = K[1]
          rot = -0.14f * k
          val st = ((-stickman.velocityY - 340f) / 240f).coerceIn(0f, 1f)
          sy = 1f + 0.16f * st
          sx = 1f - 0.08f * st
        }
        "fall" -> {
          val k = (stickman.velocityY / 480f).coerceIn(0f, 1f)
          val sm = smooth01(k)
          val fl = sin(animTime * 17f) * 0.24f * k
          for (i in 0 until 15) K[i] = lerp(kpTuck[i], kpReach[i], sm)
          K[9] += fl
          K[11] -= fl
          K[10] += fl * 0.5f
          px = K[0]
          py = K[1]
          rot = 0.08f * k
        }
        "cheer" -> {
          val hop = kotlin.math.abs(sin(animTime * 6.5f))
          val land = Math.pow((1f - hop).toDouble(), 6.0).toFloat()
          val pump = 0.18f * sin(animTime * 13f)
          K[0] = 0f; K[1] = -28.5f; K[2] = 0.05f; K[3] = -1.5f; K[4] = -0.12f
          K[5] = 0.25f + 0.6f * hop; K[6] = 0.25f + 0.9f * hop
          K[7] = -0.15f + 0.45f * hop; K[8] = 0.2f + 0.8f * hop
          K[9] = 2.2f + pump; K[10] = 0.35f; K[11] = -1.95f - pump; K[12] = -0.5f
          K[13] = 0.3f; K[14] = 0.3f
          dy = -hop * 11f
          sy = 1f - 0.14f * land
          sx = 1f + 0.09f * land
        }
      }

      if (applySquashAndSlope) {
        // Squash on landing (about his feet) from art-hero.js
        if (stickman.isGrounded && stickman.landingSquashTimer > 0f) {
          val u = (stickman.landingSquashTimer / 0.26f).coerceIn(0f, 1f)
          val s = u * u * 0.85f
          sy *= 1f - 0.24f * s
          sx *= 1f + 0.15f * s
          K[2] += 0.15f * s
          K[9] -= 0.3f * s
          K[11] += 0.3f * s
        }
        if (stickman.isGrounded) {
          rot += (stickman.slopeRadians * 0.45f).coerceIn(-0.35f, 0.35f)
        }
      }

      // Build local skeleton (facing +x, feet at 0,0)
      val J = FloatArray(NJ * 2)
      val hx = K[0]
      val hy = K[1]
      val lean = K[2]
      val curl = K[3]
      val ux = sin(lean)
      val uy = -cos(lean)
      val bx = -cos(lean)
      val by = -sin(lean)
      val nx = hx + ux * TORSO
      val ny = hy + uy * TORSO
      val mx = hx + ux * TORSO * 0.5f + bx * curl
      val my = hy + uy * TORSO * 0.5f + by * curl
      setJ(J, HIP, hx, hy)
      setJ(J, MID, mx, my)
      setJ(J, NCK, nx, ny)
      val t = 0.84f
      val a = (1f - t) * (1f - t)
      val b = 2f * (1f - t) * t
      val c = t * t
      setJ(J, SHO, a * hx + b * mx + c * nx, a * hy + b * my + c * ny)
      val ha = lean + K[4]
      val hdx = sin(ha)
      val hdy = -cos(ha)
      setJ(J, NTP, nx + hdx * NECK, ny + hdy * NECK)
      setJ(J, HED, nx + hdx * (NECK + HEAD_R), ny + hdy * (NECK + HEAD_R))
      limbJ(J, HIP, K1, F1, K[5], K[5] - K[6], THIGH, SHIN)
      toeJ(J, F1, T1, K[5] - K[6] + 1.45f + K[13])
      limbJ(J, HIP, K2, F2, K[7], K[7] - K[8], THIGH, SHIN)
      toeJ(J, F2, T2, K[7] - K[8] + 1.45f + K[14])
      limbJ(J, SHO, E1, H1, K[9], K[9] + K[10], UPPER, FORE)
      limbJ(J, SHO, E2, H2, K[11], K[11] + K[12], UPPER, FORE)

      // Place into world coordinates with squash/stretch & rotation about pivot (px, py)
      val cosR = cos(rot)
      val sinR = sin(rot)
      for (i in 0 until NJ * 2 step 2) {
        val lx = (J[i] - px) * sx
        val ly = (J[i + 1] - py) * sy
        J[i] = x + facing * (px + lx * cosR - ly * sinR)
        J[i + 1] = y + py + lx * sinR + ly * cosR + dy
      }

      val faceAng = K[2] + K[4] + rot
      return J to faceAng
    }

    val isBoosted = stickman.boostTimer > 0f || stickman.blitzTimer > 0f
    val pose = when {
      stickman.hasReachedFinish -> "cheer"
      !stickman.isGrounded && stickman.velocityY < -40f -> "jump"
      !stickman.isGrounded -> "fall"
      isBoosted -> "dash"
      else -> "run"
    }

    // 1. Roaring 60 FPS Fiery Red-Orange Aura
    val auraIntensity = when {
      isBoosted -> 1.40f
      stickman.landingSquashTimer > 0.05f -> 1.25f
      !stickman.isGrounded -> 1.12f
      else -> 1.0f
    }
    drawFieryFightingAura(
      centerWorldX = stickman.footX,
      footWorldY = stickman.footY,
      intensity = auraIntensity,
      leanBackX = -8f,
      isBoosted = isBoosted
    )

    // 2. Chalk Dust & Air Trail Effects from art-hero.js
    val tau = (PI * 2.0).toFloat()
    if (pose == "jump" || pose == "fall") {
      // Parabolic airTrail dots behind the airborne runner
      for (i in 1..7) {
        val dtBack = 0.032f * i
        val tx = stickman.footX - stickman.velocityX * dtBack
        val ty = stickman.footY - 30f - stickman.velocityY * dtBack + 0.5f * WorldConstants.GRAVITY * dtBack * dtBack
        val r = (2.2f * (1f - i / 8f) + 0.4f) * worldScale
        drawCircle(
          color = bodyWhite.copy(alpha = 0.48f * (1f - i / 8f)),
          radius = r,
          center = Offset(toScreenX(tx), toScreenY(ty))
        )
      }
    } else if ((pose == "run" || pose == "dash") && stickman.isGrounded) {
      // Rolling footDust kicks from art-hero.js (one per foot push-off)
      for (side in 0..1) {
        var e = (phase + side * PI.toFloat() - PI.toFloat() * 1.35f) % tau
        if (e < 0f) e += tau
        if (e <= 1.1f) {
          val k = e / 1.1f
          val dx = stickman.footX - (e * 24f + 6f + k * 6f)
          val dy = stickman.footY - 2f - k * 4.5f
          val r = (1.6f + k * (if (pose == "dash") 4.5f else 3.2f)) * worldScale
          drawCircle(
            color = bodyWhite.copy(alpha = 0.52f * (1f - k)),
            radius = r,
            center = Offset(toScreenX(dx), toScreenY(dy)),
            style = Stroke(width = 1.6f * worldScale)
          )
        }
      }
    }

    // Dash afterimages & speed lines from art-hero.js
    if (pose == "dash") {
      for (i in 2 downTo 1) {
        val (gj, _) = buildRig("dash", phase - 0.45f * i, stickman.footX - 15f * i, stickman.footY, 1f, false)
        val ghostAlpha = 0.24f / i
        val ghostColor = auraOrange.copy(alpha = ghostAlpha)
        drawLine(
          color = ghostColor,
          start = Offset(toScreenX(gj[HIP * 2]), toScreenY(gj[HIP * 2 + 1])),
          end = Offset(toScreenX(gj[NCK * 2]), toScreenY(gj[NCK * 2 + 1])),
          strokeWidth = 4.2f * worldScale,
          cap = StrokeCap.Round
        )
        drawLine(
          color = ghostColor,
          start = Offset(toScreenX(gj[HIP * 2]), toScreenY(gj[HIP * 2 + 1])),
          end = Offset(toScreenX(gj[F1 * 2]), toScreenY(gj[F1 * 2 + 1])),
          strokeWidth = 3.6f * worldScale,
          cap = StrokeCap.Round
        )
        drawLine(
          color = ghostColor,
          start = Offset(toScreenX(gj[HIP * 2]), toScreenY(gj[HIP * 2 + 1])),
          end = Offset(toScreenX(gj[F2 * 2]), toScreenY(gj[F2 * 2 + 1])),
          strokeWidth = 3.6f * worldScale,
          cap = StrokeCap.Round
        )
      }
      for (i in 0..3) {
        val sy = toScreenY(stickman.footY - 10f - i * 14f)
        val jit = sin(animTime * 9f + i * 3.7f) * 10f
        val sx0 = toScreenX(stickman.footX - (24f + i * 4f + jit * 0.3f))
        val sx1 = toScreenX(stickman.footX - (50f + i * 10f + jit))
        drawLine(
          color = bodyWhite.copy(alpha = 0.55f),
          start = Offset(sx0, sy),
          end = Offset(sx1, sy),
          strokeWidth = 2.0f * worldScale,
          cap = StrokeCap.Round
        )
      }
    }

    // Landing puff curls from art-hero.js
    if (stickman.isGrounded && stickman.landingSquashTimer > 0f) {
      val k = 1f - (stickman.landingSquashTimer / 0.26f).coerceIn(0f, 1f)
      val alpha = (1f - k) * 0.72f
      for (side in -1..1 step 2) {
        val cx = toScreenX(stickman.footX + side * (5f + 22f * k))
        val cy = toScreenY(stickman.footY - 2.5f - 5f * k)
        val r = (2f + 4.5f * k) * worldScale
        drawCircle(
          color = bodyWhite.copy(alpha = alpha),
          radius = r,
          center = Offset(cx, cy),
          style = Stroke(width = 1.8f * worldScale)
        )
      }
    }

    // 3. Build & Render the 16-Joint Hero Rig
    val (J, faceAng) = buildRig(pose, phase, stickman.footX, stickman.footY, 1f, true)
    val windDrag = (-stickman.velocityX / 190f).coerceIn(-1.2f, -0.35f) + (stickman.velocityY / 700f).coerceIn(-0.35f, 0.45f)
    val expression = if (isBoosted) 1 else 0

    renderCartoonBodyPose(
      hipWX = J[HIP * 2],
      hipWY = J[HIP * 2 + 1],
      shWX = J[SHO * 2],
      shWY = J[SHO * 2 + 1],
      hdWX = J[HED * 2],
      hdWY = J[HED * 2 + 1],
      frontKnee = Offset(J[K1 * 2], J[K1 * 2 + 1]),
      frontAnkle = Offset(J[F1 * 2], J[F1 * 2 + 1]),
      backKnee = Offset(J[K2 * 2], J[K2 * 2 + 1]),
      backAnkle = Offset(J[F2 * 2], J[F2 * 2 + 1]),
      frontElbow = Offset(J[E1 * 2], J[E1 * 2 + 1]),
      frontHand = Offset(J[H1 * 2], J[H1 * 2 + 1]),
      backElbow = Offset(J[E2 * 2], J[E2 * 2 + 1]),
      backHand = Offset(J[H2 * 2], J[H2 * 2 + 1]),
      faceDirX = 1f,
      expression = expression,
      hasHair = true,
      headTilt = faceAng,
      windDrag = windDrag,
      midWX = J[MID * 2],
      midWY = J[MID * 2 + 1],
      nckWX = J[NCK * 2],
      nckWY = J[NCK * 2 + 1],
      ntpWX = J[NTP * 2],
      ntpWY = J[NTP * 2 + 1],
      frontToe = Offset(J[T1 * 2], J[T1 * 2 + 1]),
      backToe = Offset(J[T2 * 2], J[T2 * 2 + 1])
    )
  }

  private fun DrawScope.drawSketchParticles(
    particles: List<SketchParticle>,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    for (p in particles) {
      val sx = toScreenX(p.worldX)
      val sy = toScreenY(p.worldY)
      if (sx < -30f || sx > size.width + 30f || sy < -30f || sy > size.height + 30f) continue

      val radiusPx = (p.size * worldScale).coerceAtLeast(1.5f)
      val alpha = p.alpha.coerceIn(0f, 1f)

      when (p.type) {
        SketchParticleType.FOOTSTEP_CHALK_PUFF -> {
          // Hand-drawn chalk dust ring + graphite fleck kicked up behind running feet
          drawCircle(
            color = ChalkWhite.copy(alpha = alpha * 0.78f),
            radius = radiusPx,
            center = Offset(sx, sy),
            style = Stroke(width = (1.8f * worldScale).coerceAtLeast(1f))
          )
          val dx = cos(p.rotationRadians) * radiusPx * 0.65f
          val dy = sin(p.rotationRadians) * radiusPx * 0.65f
          drawLine(
            color = PencilGraphite.copy(alpha = alpha * 0.85f),
            start = Offset(sx - dx, sy - dy),
            end = Offset(sx + dx, sy + dy),
            strokeWidth = (1.6f * worldScale).coerceAtLeast(1f),
            cap = StrokeCap.Round
          )
        }

        SketchParticleType.PENCIL_SHAVING -> {
          // Angled graphite & yellow-lead shaving tick emitted at the drawing tip
          val dx = cos(p.rotationRadians) * radiusPx * 1.15f
          val dy = sin(p.rotationRadians) * radiusPx * 1.15f
          drawLine(
            color = LavaFlameYellow.copy(alpha = alpha),
            start = Offset(sx - dx, sy - dy),
            end = Offset(sx + dx, sy + dy),
            strokeWidth = (2.2f * worldScale).coerceAtLeast(1.2f),
            cap = StrokeCap.Round
          )
          drawCircle(
            color = ChalkWhite.copy(alpha = alpha * 0.9f),
            radius = radiusPx * 0.45f,
            center = Offset(sx + dy * 0.4f, sy - dx * 0.4f)
          )
        }

        SketchParticleType.LINE_LOCK_SPARK -> {
          // 4-point hand-sketched cross-star along a newly completed solid line
          val arm = radiusPx * 1.35f
          val cosA = cos(p.rotationRadians)
          val sinA = sin(p.rotationRadians)
          val sparkColor = if ((p.size * 10).toInt() % 2 == 0) {
            ChalkWhite.copy(alpha = alpha)
          } else {
            InkCyanAccent.copy(alpha = alpha)
          }

          drawLine(
            color = sparkColor,
            start = Offset(sx - cosA * arm, sy - sinA * arm),
            end = Offset(sx + cosA * arm, sy + sinA * arm),
            strokeWidth = (2.0f * worldScale).coerceAtLeast(1.2f),
            cap = StrokeCap.Round
          )
          drawLine(
            color = sparkColor,
            start = Offset(sx + sinA * arm * 0.75f, sy - cosA * arm * 0.75f),
            end = Offset(sx - sinA * arm * 0.75f, sy + cosA * arm * 0.75f),
            strokeWidth = (2.0f * worldScale).coerceAtLeast(1.2f),
            cap = StrokeCap.Round
          )
        }
      }
    }
  }

  private fun DrawScope.drawBreakableRockWall(
    wall: BreakableRockWall,
    isSuperAuraActive: Boolean,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val cx = toScreenX(wall.x)
    if (cx < -60f || cx > size.width + 60f) return
    val top = toScreenY(wall.topY)
    val bot = toScreenY(wall.bottomY)
    val w = wall.width * worldScale
    val h = (bot - top).coerceAtLeast(8f)

    drawRect(
      color = Color(0xFF3E2723),
      topLeft = Offset(cx - w * 0.5f, top),
      size = Size(w, h)
    )
    drawRect(
      color = if (isSuperAuraActive) LavaFlameYellow else LavaOrange,
      topLeft = Offset(cx - w * 0.5f, top),
      size = Size(w, h),
      style = Stroke(width = 2.6f * worldScale)
    )
    // Glowing magma fissure cracks inside the stone pillar
    drawLine(
      color = LavaFlameYellow,
      start = Offset(cx - w * 0.25f, top + h * 0.2f),
      end = Offset(cx + w * 0.2f, top + h * 0.55f),
      strokeWidth = 2.2f * worldScale,
      cap = StrokeCap.Round
    )
    drawLine(
      color = LavaOrange,
      start = Offset(cx + w * 0.2f, top + h * 0.55f),
      end = Offset(cx - w * 0.18f, top + h * 0.85f),
      strokeWidth = 2.2f * worldScale,
      cap = StrokeCap.Round
    )
  }

  private fun DrawScope.drawLeapingLavaMonster(
    monster: LeapingLavaMonster,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val cx = toScreenX(monster.x)
    if (cx < -60f || cx > size.width + 60f) return
    val wy = monster.currentYAt(animTime)
    if (wy >= WorldConstants.LAVA_SURFACE_Y) return
    val cy = toScreenY(wy)
    val r = monster.radius * worldScale

    drawCircle(
      color = LavaOrange.copy(alpha = 0.34f),
      radius = r * 1.45f,
      center = Offset(cx, cy)
    )
    val spikes = 8
    for (i in 0 until spikes) {
      val a = animTime * 5f + i * (2f * PI.toFloat() / spikes)
      drawLine(
        color = LavaFlameYellow,
        start = Offset(cx + cos(a) * r * 0.7f, cy + sin(a) * r * 0.7f),
        end = Offset(cx + cos(a) * r * 1.35f, cy + sin(a) * r * 1.35f),
        strokeWidth = 3.0f * worldScale,
        cap = StrokeCap.Round
      )
    }
    drawCircle(color = LavaDeepRed, radius = r, center = Offset(cx, cy))
    drawCircle(color = ChalkWhite, radius = r, center = Offset(cx, cy), style = Stroke(width = 2.2f * worldScale))
    // Glowing imp eyes
    drawCircle(color = LavaFlameYellow, radius = 3.2f * worldScale, center = Offset(cx - 5f * worldScale, cy - 2f * worldScale))
    drawCircle(color = LavaFlameYellow, radius = 3.2f * worldScale, center = Offset(cx + 5f * worldScale, cy - 2f * worldScale))
  }

  private fun DrawScope.drawIgnisTitanBoss(
    boss: IgnisTitanBoss,
    stickman: StickmanState,
    animTime: Float,
    dragonSpriteSheet: ImageBitmap? = null,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val distToPlayer = stickman.footX - boss.x
    // Red screen-edge danger vignette when active Ignis Titan is within 400px
    if (!boss.isDefeated && distToPlayer < 400f) {
      val dangerAlpha = ((400f - distToPlayer) / 400f).coerceIn(0f, 1f) * 0.42f
      drawRect(
        brush = Brush.horizontalGradient(
          colors = listOf(LavaDeepRed.copy(alpha = dangerAlpha), Color.Transparent),
          startX = 0f,
          endX = size.width * 0.35f
        ),
        size = size
      )
    }

    val headX = toScreenX(boss.x)
    val headY = toScreenY(boss.y)
    if (headX < -280f * worldScale || headX > size.width + 180f * worldScale) return

    val isFlashingHurt = boss.hurtCooldown > 0f && ((animTime * 20f).toInt() % 2 == 0)

    if (dragonSpriteSheet != null) {
      // Select one of the 10 dragon poses (5 columns x 2 rows, 320x320 per cell):
      // 0: Menacing front stance | 1: Quadruped charge | 2: Flying fireball breath | 3: Aerial swoop | 4: Rearing flame roar
      // 5: Low lunging bite     | 6: Magma ground slam| 7: Upright winged stride  | 8: Hurt recoil  | 9: Defeated curled-up
      val chaseCyclePoses = intArrayOf(1, 7, 5, 3)
      val poseIndex = when {
        boss.isDefeated || boss.hp <= 0 -> 9
        boss.hurtCooldown > 0f -> 8
        boss.biteCooldown > 1.8f -> 6
        distToPlayer < 115f || boss.biteCooldown > 1.0f -> 5
        distToPlayer < 215f -> if (((animTime * 3.2f).toInt() % 2) == 0) 2 else 4
        animTime < 0.75f -> 0
        else -> chaseCyclePoses[((animTime * 5.6f).toInt().let { if (it < 0) -it else it }) % chaseCyclePoses.size]
      }

      val col = poseIndex % 5
      val row = poseIndex / 5
      val cellW = dragonSpriteSheet.width / 5
      val cellH = dragonSpriteSheet.height / 2

      val wingFlapBob = if (boss.isDefeated) {
        0f
      } else {
        sin(animTime * 6.8f) * 7f * worldScale
      }

      val spriteSizePx = (168f * worldScale).toInt().coerceAtLeast(48)
      val dstLeft = (headX - spriteSizePx * 0.62f).toInt()
      val dstTop = (headY - spriteSizePx * 0.54f + wingFlapBob).toInt()

      // Fiery volcanic aura glow behind the dragon
      val auraCenter = Offset(
        x = dstLeft + spriteSizePx * 0.52f,
        y = dstTop + spriteSizePx * 0.54f
      )
      val auraPulse = 0.88f + 0.12f * sin(animTime * 9f)
      if (!boss.isDefeated) {
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(
              (if (isFlashingHurt) ChalkWhite else LavaOrange).copy(alpha = 0.42f * auraPulse),
              LavaDeepRed.copy(alpha = 0.18f * auraPulse),
              Color.Transparent
            ),
            center = auraCenter,
            radius = spriteSizePx * 0.58f
          ),
          radius = spriteSizePx * 0.58f,
          center = auraCenter
        )
      }

      val hurtColorFilter = if (isFlashingHurt) {
        ColorFilter.lighting(multiply = Color.White, add = Color(0xFF7A3800))
      } else {
        null
      }

      drawImage(
        image = dragonSpriteSheet,
        srcOffset = IntOffset(col * cellW, row * cellH),
        srcSize = IntSize(cellW, cellH),
        dstOffset = IntOffset(dstLeft, dstTop),
        dstSize = IntSize(spriteSizePx, spriteSizePx),
        filterQuality = FilterQuality.None,
        colorFilter = hurtColorFilter
      )

      // Orbiting magma embers around the dragon's wings and tail
      if (!boss.isDefeated) {
        for (i in 0..4) {
          val phase = animTime * 4.5f + i * 1.25f
          val ex = auraCenter.x + cos(phase) * (spriteSizePx * (0.28f + (i % 3) * 0.08f))
          val ey = auraCenter.y + sin(phase * 1.3f) * (spriteSizePx * 0.30f) - (phase % 1f) * 14f * worldScale
          drawCircle(
            color = if (i % 2 == 0) LavaFlameYellow else LavaOrange,
            radius = (2.4f + (i % 2) * 1.2f) * worldScale,
            center = Offset(ex, ey)
          )
        }
      }
      return
    }

    // Fallback vector dragon if sprite sheet is unavailable in headless tests
    val bodyColor = if (isFlashingHurt) ChalkWhite else LavaDeepRed
    val coreColor = if (isFlashingHurt) InkCyanAccent else LavaOrange
    for (seg in 5 downTo 1) {
      val segX = headX - seg * 26f * worldScale
      val waveY = sin(animTime * 6.5f - seg * 0.85f) * 14f * worldScale
      val segY = headY + waveY + seg * 4f * worldScale
      val segR = (26f - seg * 2.6f) * worldScale
      drawCircle(color = coreColor.copy(alpha = 0.28f), radius = segR * 1.35f, center = Offset(segX, segY))
      drawCircle(color = bodyColor, radius = segR, center = Offset(segX, segY))
      drawCircle(color = ChalkWhite, radius = segR, center = Offset(segX, segY), style = Stroke(width = 2.2f * worldScale))
    }
  }

  private fun DrawScope.drawHeroCosmeticsAndShield(
    stickman: StickmanState,
    equippedSkin: String,
    animTime: Float,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val cx = toScreenX(stickman.footX)
    val cy = toScreenY(stickman.footY - WorldConstants.STICKMAN_HEIGHT * 0.5f)

    // Golden Super Aura Sonic Dash halo (#FFD700)
    if (stickman.superAuraTimer > 0f || equippedSkin == "GOLDEN_TITAN") {
      val pulse = 0.88f + 0.12f * sin(animTime * 14f)
      drawCircle(
        color = Color(0xFFFFD700).copy(alpha = 0.28f * pulse),
        radius = 42f * worldScale * pulse,
        center = Offset(cx, cy)
      )
      drawCircle(
        color = Color(0xFFFFD700),
        radius = 36f * worldScale * pulse,
        center = Offset(cx, cy),
        style = Stroke(width = 2.8f * worldScale)
      )
    }

    // Cyber-Ninja Skin Visor & Scarf
    if (equippedSkin == "CYBER_NINJA") {
      val headY = toScreenY(stickman.footY - WorldConstants.STICKMAN_HEIGHT + 10f)
      drawLine(
        color = InkCyanAccent,
        start = Offset(cx - 6f * worldScale, headY),
        end = Offset(cx + 9f * worldScale, headY),
        strokeWidth = 4.2f * worldScale,
        cap = StrokeCap.Round
      )
    }

    // Titan's Shield Protective Energy Sphere
    if (stickman.hasTitanShield) {
      drawCircle(
        color = InkCyanAccent.copy(alpha = 0.18f),
        radius = 38f * worldScale,
        center = Offset(cx, cy)
      )
      drawCircle(
        color = InkCyanAccent.copy(alpha = 0.85f),
        radius = 38f * worldScale,
        center = Offset(cx, cy),
        style = Stroke(
          width = 2.4f * worldScale,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * worldScale, 6f * worldScale), animTime * 30f * worldScale)
        )
      )
    }
  }
}
