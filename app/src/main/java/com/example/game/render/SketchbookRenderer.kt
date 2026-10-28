package com.example.game.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.game.model.CalloutMove
import com.example.game.model.ChalkBit
import com.example.game.model.ChalkTool
import com.example.game.model.DrawnLine
import com.example.game.model.EmberParticle
import com.example.game.model.GuideStroke
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
    activeCallout: CalloutMove? = null,
    lavaAnimTime: Float,
    embers: List<EmberParticle>,
    sketchParticles: List<SketchParticle> = emptyList(),
    worldScale: Float
  ) {
    fun toScreenX(worldX: Float): Float = (worldX - cameraX) * worldScale
    fun toScreenY(worldY: Float): Float = worldY * worldScale

    val visibleWorldWidth = if (worldScale > 0f) size.width / worldScale else 1000f

    // 1. Dark Slate Chalkboard Background + Subtle Graph Grid
    drawSketchbookPaper(cameraX = cameraX, worldScale = worldScale)

    // 2. Faint Guide Strokes on Level 1 / Early Levels
    if (completedLines.isEmpty()) {
      for (guide in level.guideStrokes) {
        drawGuideStroke(guide, ::toScreenX, ::toScreenY, worldScale, lavaAnimTime)
      }
    }

    // 3. Animated Lava Pit & Rising Flames Underneath Gaps
    drawLavaPit(
      cameraX = cameraX,
      visibleWorldWidth = visibleWorldWidth,
      lavaAnimTime = lavaAnimTime,
      embers = embers,
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

    // 6. Thumbtack Hazards
    for (cluster in level.thumbtacks) {
      drawThumbtackCluster(cluster, ::toScreenX, ::toScreenY, worldScale)
    }

    // 7. Collectible Chalk Bits & Gold Stars
    for (bit in chalkBits) {
      if (!bit.collected) {
        drawChalkBitOrStar(bit, lavaAnimTime, ::toScreenX, ::toScreenY, worldScale)
      }
    }

    // 8. Completed Player-Drawn Chalk Platforms (White, Blue Bounce, Gold Turbo)
    for (line in completedLines) {
      drawPencilLine(
        points = line.points,
        tool = line.tool,
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
        alpha = 1f,
        isCompleted = false,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        worldScale = worldScale
      )
    }

    // 10. Red Scribbler Enemies
    for (enemy in scribblers) {
      if (enemy.isAlive) {
        drawScribblerEnemy(enemy, ::toScreenX, ::toScreenY, worldScale)
      }
    }

    // 11. Hand-drawn Chalk Dust & Pencil Spark Particles
    if (sketchParticles.isNotEmpty()) {
      drawSketchParticles(
        particles = sketchParticles,
        toScreenX = ::toScreenX,
        toScreenY = ::toScreenY,
        worldScale = worldScale
      )
    }

    // 12. Animated White Stickman + Flowing Scarf + Active Aura
    drawAnimatedStickman(
      stickman = stickman,
      toScreenX = ::toScreenX,
      toScreenY = ::toScreenY,
      worldScale = worldScale
    )

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
  }

  private fun DrawScope.drawSketchbookPaper(cameraX: Float, worldScale: Float) {
    drawRect(
      brush = paperBackgroundBrush,
      size = size
    )

    val gridStepWorld = 100f
    val gridStepPx = (gridStepWorld * worldScale).coerceAtLeast(32f)
    val parallaxOffset = (cameraX * 0.35f * worldScale) % gridStepPx

    // Vertical notebook graph lines
    var x = -parallaxOffset
    while (x < size.width) {
      drawLine(
        color = SketchGridLine,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 1f
      )
      x += gridStepPx
    }

    // Horizontal notebook graph lines
    var y = 0f
    while (y < size.height) {
      drawLine(
        color = SketchGridLine,
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
    val bottom = size.height

    val width = (right - left).coerceAtLeast(1f)
    val height = (bottom - top).coerceAtLeast(1f)

    // Charcoal shaded platform block
    drawRect(
      color = Color(0xFF252832),
      topLeft = Offset(left, top),
      size = Size(width, height)
    )

    // Hand-drawn diagonal graphite hatching inside platform
    val hatchSpacing = 22f * worldScale
    var hx = platform.startX
    while (hx < platform.endX + 120f) {
      val sx1 = toScreenX(hx.coerceIn(platform.startX, platform.endX))
      val sy1 = top + 4f * worldScale
      val sx2 = toScreenX((hx - 90f).coerceIn(platform.startX, platform.endX))
      val sy2 = (top + 110f * worldScale).coerceAtMost(bottom)
      drawLine(
        color = PencilGraphite.copy(alpha = 0.16f),
        start = Offset(sx1, sy1),
        end = Offset(sx2, sy2),
        strokeWidth = 2f * worldScale
      )
      hx += 24f
    }

    // Double-sketched chalk outline along top and sides
    val topPath1 = reusablePlatformPath.apply {
      reset()
      moveTo(left, bottom)
      lineTo(left, top)
      var cx = platform.startX
      var idx = 0
      while (cx < platform.endX) {
        val nextX = (cx + 35f).coerceAtMost(platform.endX)
        val jitterY = if (idx % 2 == 0) -1.4f else 1.4f
        lineTo(toScreenX(nextX), toScreenY(platform.topY + jitterY))
        cx = nextX
        idx++
      }
      lineTo(right, bottom)
    }

    drawPath(
      path = topPath1,
      color = ChalkWhite,
      style = Stroke(
        width = 4.2f * worldScale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
    )

    // Secondary sketchy pencil pass
    drawLine(
      color = PencilGraphite.copy(alpha = 0.65f),
      start = Offset(left + 2f * worldScale, top + 3.5f * worldScale),
      end = Offset(right - 2f * worldScale, top + 2.5f * worldScale),
      strokeWidth = 2f * worldScale,
      cap = StrokeCap.Round
    )
  }

  private fun DrawScope.drawLavaPit(
    cameraX: Float,
    visibleWorldWidth: Float,
    lavaAnimTime: Float,
    embers: List<EmberParticle>,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    val lavaTopY = WorldConstants.LAVA_SURFACE_Y
    val startWorldX = cameraX - 60f
    val endWorldX = cameraX + visibleWorldWidth + 60f

    // Ambient orange glow above the lava pit
    drawRect(
      brush = lavaGlowBrush,
      topLeft = Offset(0f, toScreenY(lavaTopY - 75f)),
      size = Size(size.width, size.height - toScreenY(lavaTopY - 75f))
    )

    // Back wave layer (deep red-orange)
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
    drawPath(path = backWavePath, color = LavaDeepRed)

    // Middle flame tongues layer (bright orange)
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
    drawPath(path = midFlamePath, color = LavaOrange)

    // Front hot yellow core wave + sketchy white/yellow crest
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
    drawPath(path = coreWavePath, color = LavaFlameYellow.copy(alpha = 0.88f))

    // Rising embers
    for (ember in embers) {
      val sx = toScreenX(ember.worldX)
      val sy = toScreenY(ember.worldY)
      if (sx in -20f..(size.width + 20f)) {
        drawCircle(
          color = (if (ember.isYellow) LavaFlameYellow else LavaOrange).copy(alpha = ember.alpha),
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

    // Left and right finish poles
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

    // Checkered banner between poles
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

    // Banner green outline
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
    alpha: Float = 1f,
    isCompleted: Boolean,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float
  ) {
    if (points.isEmpty()) return
    val coreColor = when (tool) {
      ChalkTool.WHITE -> ChalkWhite.copy(alpha = alpha)
      ChalkTool.SPRING -> InkCyanAccent.copy(alpha = alpha)
      ChalkTool.BOOST -> LavaFlameYellow.copy(alpha = alpha)
      ChalkTool.CUTTER -> LavaOrange.copy(alpha = alpha)
    }
    val haloColor = when (tool) {
      ChalkTool.WHITE -> PencilGraphite.copy(alpha = 0.55f * alpha)
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
    if (cx < -40f || cx > size.width + 40f) return

    if (bit.isStar) {
      drawCircle(
        color = LavaFlameYellow.copy(alpha = 0.25f),
        radius = 22f * worldScale,
        center = Offset(cx, cy)
      )
      val starPath = reusableStarPath.apply {
        reset()
        for (i in 0 until 10) {
          val r = (if (i % 2 == 0) 14f else 6.5f) * worldScale
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
        style = Stroke(width = 1.8f * worldScale)
      )
    } else {
      drawCircle(
        color = ChalkWhite.copy(alpha = 0.2f),
        radius = 15f * worldScale,
        center = Offset(cx, cy)
      )
      drawLine(
        color = ChalkWhite,
        start = Offset(cx - 8f * worldScale, cy + 4f * worldScale),
        end = Offset(cx + 8f * worldScale, cy - 4f * worldScale),
        strokeWidth = 6.5f * worldScale,
        cap = StrokeCap.Round
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

      // Pink cap + sharp steel spike pointing up
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

  private data class SprintKeyframe(
    val torsoLean: Float,
    val hipOffsetY: Float,
    val frontThigh: Float,
    val frontKnee: Float,
    val backThigh: Float,
    val backKnee: Float,
    val frontArm: Float,
    val frontElbow: Float,
    val backArm: Float,
    val backElbow: Float
  )

  // 6 separated fighting-game sprint frames (deep forward dash lean, high knee drive, explosive push-off)
  private val SPRINT_KEYFRAMES = arrayOf(
    // Frame 0: Power Drive Contact A (Right/Front foot strikes & drives, Left/Back knee rockets up)
    SprintKeyframe(
      torsoLean = 0.64f, hipOffsetY = -21.0f,
      frontThigh = 0.28f, frontKnee = -0.32f,
      backThigh = 1.36f, backKnee = -1.22f,
      frontArm = -0.98f, frontElbow = 0.82f,
      backArm = 0.96f, backElbow = 1.12f
    ),
    // Frame 1: Explosive Push-Off A (Front leg blasts far behind, Back leg extends forward)
    SprintKeyframe(
      torsoLean = 0.68f, hipOffsetY = -25.0f,
      frontThigh = -0.92f, frontKnee = -0.14f,
      backThigh = 1.16f, backKnee = -0.58f,
      frontArm = -1.28f, frontElbow = 0.96f,
      backArm = 1.22f, backElbow = 0.92f
    ),
    // Frame 2: Airborne Dash Flight A (Wide mid-air split stride, peak flight)
    SprintKeyframe(
      torsoLean = 0.62f, hipOffsetY = -27.2f,
      frontThigh = -1.18f, frontKnee = -1.28f,
      backThigh = 0.82f, backKnee = -0.26f,
      frontArm = -0.35f, frontElbow = 1.05f,
      backArm = 0.45f, backElbow = 0.95f
    ),
    // Frame 3: Power Drive Contact B (Back foot strikes & drives, Front knee rockets up)
    SprintKeyframe(
      torsoLean = 0.64f, hipOffsetY = -21.0f,
      frontThigh = 1.36f, frontKnee = -1.22f,
      backThigh = 0.28f, backKnee = -0.32f,
      frontArm = 0.96f, frontElbow = 1.12f,
      backArm = -0.98f, backElbow = 0.82f
    ),
    // Frame 4: Explosive Push-Off B (Back leg blasts far behind, Front leg extends forward)
    SprintKeyframe(
      torsoLean = 0.68f, hipOffsetY = -25.0f,
      frontThigh = 1.16f, frontKnee = -0.58f,
      backThigh = -0.92f, backKnee = -0.14f,
      frontArm = 1.22f, frontElbow = 0.92f,
      backArm = -1.28f, backElbow = 0.96f
    ),
    // Frame 5: Airborne Dash Flight B (Wide mid-air split stride, peak flight)
    SprintKeyframe(
      torsoLean = 0.62f, hipOffsetY = -27.2f,
      frontThigh = 0.82f, frontKnee = -0.26f,
      backThigh = -1.18f, backKnee = -1.28f,
      frontArm = 0.45f, frontElbow = 0.95f,
      backArm = -0.35f, backElbow = 1.05f
    )
  )

  private fun DrawScope.drawAnimatedStickman(
    stickman: StickmanState,
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
            strokeWidth = (thicknessWorld + 7.2f) * worldScale,
            cap = StrokeCap.Round
          )
          if (endCapRadiusWorld > 0f) {
            drawCircle(
              color = rimCrimsonGlow.copy(alpha = 0.28f),
              radius = (endCapRadiusWorld + 3.6f) * worldScale,
              center = s2
            )
          }
        }
        1 -> {
          drawLine(
            color = outlineBlack,
            start = s1,
            end = s2,
            strokeWidth = (thicknessWorld + 4.6f) * worldScale,
            cap = StrokeCap.Round
          )
          if (endCapRadiusWorld > 0f) {
            drawCircle(
              color = outlineBlack,
              radius = (endCapRadiusWorld + 2.3f) * worldScale,
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

    // Renders the reference-accurate Super Saiyan God spiky crimson hair crown with dark inner wedges & thick black outline
    fun drawFightingGameHair(
      headWorldX: Float,
      headWorldY: Float,
      headTilt: Float,
      windDrag: Float
    ) {
      val hx = toScreenX(headWorldX)
      val hy = toScreenY(headWorldY)
      val r = (WorldConstants.STICKMAN_HEAD_RADIUS + 0.8f) * worldScale

      // 8 sculpted anime flame-spikes fanning from left temple across the crown to the right brow
      val spikeAngles = floatArrayOf(-2.88f, -2.52f, -2.14f, -1.76f, -1.42f, -1.08f, -0.74f, -0.38f)
      val spikeLengths = floatArrayOf(18f, 25f, 31f, 34f, 30f, 26f, 21f, 15f)

      val hairPath = reusableHairPath.apply {
        reset()
        moveTo(hx - r * 0.92f, hy + r * 0.15f)
        for (i in spikeAngles.indices) {
          val flutter = sin(phase * 3.2f + i * 0.9f) * 0.05f
          val ang = spikeAngles[i] + headTilt + windDrag * 0.22f + flutter
          val len = spikeLengths[i] * worldScale
          // Slightly curved outer edge toward tip
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

      // Thick black cartoon outline + crimson fill + vermilion highlight + dark crimson inner creases
      drawPath(
        path = hairPath,
        color = outlineBlack,
        style = Stroke(width = 4.4f * worldScale, join = StrokeJoin.Round, cap = StrokeCap.Round)
      )
      drawPath(path = hairPath, color = hairCrimson)
      drawPath(
        path = hairPath,
        color = hairHighlight.copy(alpha = 0.65f),
        style = Stroke(width = 1.8f * worldScale, join = StrokeJoin.Round)
      )

      // Dark red interior hair strand lines (matching the reference sheet's cel-shaded hair)
      for (i in 1..5) {
        val ang = spikeAngles[i] + headTilt + windDrag * 0.22f
        val innerStart = Offset(hx + cos(ang) * (r * 0.85f), hy + sin(ang) * (r * 0.85f))
        val innerEnd = Offset(hx + cos(ang) * (spikeLengths[i] * 0.68f * worldScale), hy + sin(ang) * (spikeLengths[i] * 0.68f * worldScale))
        drawLine(
          color = hairDarkShadow,
          start = innerStart,
          end = innerEnd,
          strokeWidth = 2.0f * worldScale,
          cap = StrokeCap.Round
        )
      }
    }

    // Renders the 2D cartoon head & expressive fighting-game face matching Reference Frames 1, 2, 3, and 4–6
    fun drawCartoonHeadAndFace(
      headWorldX: Float,
      headWorldY: Float,
      faceDirX: Float,
      expression: Int, // 0 = Fierce Glare (\ /), 1 = Power Scream (> <), 2 = Red Flush Glare, 3 = Knocked Out
      hasHair: Boolean,
      headTilt: Float,
      windDrag: Float
    ) {
      if (hasHair) {
        drawFightingGameHair(headWorldX, headWorldY, headTilt, windDrag)
      }

      val hCenter = Offset(toScreenX(headWorldX), toScreenY(headWorldY))
      val hRadius = (WorldConstants.STICKMAN_HEAD_RADIUS + 0.8f) * worldScale

      // Crimson rim glow + thick black cartoon outline + white head fill
      drawCircle(
        color = rimCrimsonGlow.copy(alpha = 0.30f),
        radius = hRadius + 4.2f * worldScale,
        center = hCenter
      )
      drawCircle(
        color = outlineBlack,
        radius = hRadius + 2.4f * worldScale,
        center = hCenter
      )
      drawCircle(
        color = if (expression == 2) Color(0xFFFFCDD2) else bodyWhite,
        radius = hRadius,
        center = hCenter
      )

      // Red energy flush on upper face for Frame 3 overload state
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
          // Expression 1 (Reference Frame 1): Squeezed '> <' power scream eyes + wide shouting mouth with teeth
          val leftEyeX = fx - 3.2f * worldScale
          val rightEyeX = fx + 3.4f * worldScale
          val eyeY = fy - 1.8f * worldScale

          // Left '>' chevron eye
          drawLine(outlineBlack, Offset(leftEyeX - 2.6f * worldScale, eyeY - 2.2f * worldScale), Offset(leftEyeX + 0.8f * worldScale, eyeY), 2.2f * worldScale, StrokeCap.Round)
          drawLine(outlineBlack, Offset(leftEyeX + 0.8f * worldScale, eyeY), Offset(leftEyeX - 2.6f * worldScale, eyeY + 2.0f * worldScale), 2.2f * worldScale, StrokeCap.Round)

          // Right '<' chevron eye
          drawLine(outlineBlack, Offset(rightEyeX + 2.6f * worldScale, eyeY - 2.2f * worldScale), Offset(rightEyeX - 0.8f * worldScale, eyeY), 2.2f * worldScale, StrokeCap.Round)
          drawLine(outlineBlack, Offset(rightEyeX - 0.8f * worldScale, eyeY), Offset(rightEyeX + 2.6f * worldScale, eyeY + 2.0f * worldScale), 2.2f * worldScale, StrokeCap.Round)

          // Wide-open shouting cartoon mouth with white upper teeth bar
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
          // Expression 0 & 2 (Reference Frames 2 & 3): Sharp angled V-brows (\ /), fierce eyes, and clenched mouth
          val leftEyeX = fx - 2.8f * worldScale
          val rightEyeX = fx + 3.6f * worldScale
          val eyeY = fy - 1.2f * worldScale

          // White eye scleras tucked under V-brows
          drawCircle(color = bodyWhite, radius = 2.1f * worldScale, center = Offset(leftEyeX, eyeY + 0.6f * worldScale))
          drawCircle(color = bodyWhite, radius = 2.1f * worldScale, center = Offset(rightEyeX, eyeY + 0.6f * worldScale))
          // Fierce crimson/black pupils
          drawCircle(color = hairCrimson, radius = 1.3f * worldScale, center = Offset(leftEyeX + 0.5f * worldScale, eyeY + 0.8f * worldScale))
          drawCircle(color = hairCrimson, radius = 1.3f * worldScale, center = Offset(rightEyeX + 0.5f * worldScale, eyeY + 0.8f * worldScale))
          drawCircle(color = outlineBlack, radius = 0.7f * worldScale, center = Offset(leftEyeX + 0.5f * worldScale, eyeY + 0.8f * worldScale))
          drawCircle(color = outlineBlack, radius = 0.7f * worldScale, center = Offset(rightEyeX + 0.5f * worldScale, eyeY + 0.8f * worldScale))

          // Thick sharp V-brows sloping down to the center bridge
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

          // Gritted battle grimace / clenched teeth line
          drawLine(
            color = outlineBlack,
            start = Offset(fx - 2.0f * worldScale, fy + 4.2f * worldScale),
            end = Offset(fx + 2.8f * worldScale, fy + 3.6f * worldScale),
            strokeWidth = 2.0f * worldScale,
            cap = StrokeCap.Round
          )
        }

        else -> {
          // Expression 3 (Reference Frames 4 & 6): Knocked-out closed eye line
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

    // Renders the roaring multi-layer fiery red-orange fighting-game aura, ground sparks, and crackling bio-lightning
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
      val auraH = (86f + intensity * 26f) * worldScale
      val auraHalfW = (32f + intensity * 15f) * worldScale
      val windTilt = leanBackX * worldScale

      // 1. Soft crimson outer glow halo
      drawCircle(
        color = auraCrimson.copy(alpha = (0.26f * intensity).coerceAtMost(0.42f)),
        radius = auraHalfW * 1.55f,
        center = Offset(baseSX + windTilt * 0.35f, baseSY - auraH * 0.45f)
      )

      // 2. Tall jagged crimson-red outer flame crown (11 sharp flickering flame tongues)
      val outerSpikes = 11
      val outerAura = reusableAuraOuterPath.apply {
        reset()
        moveTo(baseSX - auraHalfW * 0.92f, baseSY + 2f * worldScale)
        for (i in 0..outerSpikes) {
          val t = i.toFloat() / outerSpikes
          val angle = PI.toFloat() + t * PI.toFloat()
          val wave = sin(phase * 3.8f + i * 1.9f) * 7.5f * worldScale
          val spikeBoost = if (i % 2 == 1) 1.24f else 0.72f
          val rx = cos(angle) * auraHalfW * spikeBoost + windTilt * (1f - kotlin.math.abs(t - 0.5f)) + wave * 0.35f
          val ry = sin(angle) * auraH * spikeBoost - kotlin.math.abs(cos(i * 1.4f + phase * 2.5f)) * 14f * worldScale
          lineTo(baseSX + rx, baseSY + ry * 0.94f)
        }
        lineTo(baseSX + auraHalfW * 0.92f, baseSY + 2f * worldScale)
        close()
      }
      drawPath(
        path = outerAura,
        color = auraDarkRed.copy(alpha = (0.45f * intensity).coerceAtMost(0.75f)),
        style = Stroke(width = 3.5f * worldScale, join = StrokeJoin.Round)
      )
      drawPath(
        path = outerAura,
        color = auraCrimson.copy(alpha = (0.68f * intensity).coerceAtMost(0.88f))
      )

      // 3. Blazing red-orange middle flame layer (9 sharp inner tongues)
      val midSpikes = 9
      val innerAura = reusableAuraInnerPath.apply {
        reset()
        moveTo(baseSX - auraHalfW * 0.66f, baseSY + 1f * worldScale)
        for (i in 0..midSpikes) {
          val t = i.toFloat() / midSpikes
          val angle = PI.toFloat() + t * PI.toFloat()
          val wave = cos(phase * 4.6f + i * 2.3f) * 5.5f * worldScale
          val spikeBoost = if (i % 2 == 0) 1.18f else 0.74f
          val rx = cos(angle) * (auraHalfW * 0.72f * spikeBoost) + windTilt * 0.65f + wave * 0.3f
          val ry = sin(angle) * (auraH * 0.78f * spikeBoost)
          lineTo(baseSX + rx, baseSY + ry)
        }
        lineTo(baseSX + auraHalfW * 0.66f, baseSY + 1f * worldScale)
        close()
      }
      drawPath(
        path = innerAura,
        color = (if (isBoosted) auraYellow else auraOrange).copy(alpha = (0.76f * intensity).coerceAtMost(0.92f))
      )

      // 4. Detached rising flame tongues / embers around the crown (like Reference Frames 1–3)
      for (f in 0..3) {
        val cycle = (phase * 1.8f + f * 1.57f) % (PI.toFloat() * 2f)
        val rise = (cycle / (PI.toFloat() * 2f))
        val side = if (f % 2 == 0) -1f else 1f
        val fx = baseSX + side * (auraHalfW * (0.65f + 0.25f * (f % 3))) + windTilt * 0.5f
        val fy = baseSY - auraH * (0.35f + rise * 0.65f)
        val flameH = (11f * (1f - rise * 0.5f)) * worldScale
        drawLine(
          color = if (f % 2 == 0) auraCrimson else auraOrange,
          start = Offset(fx, fy),
          end = Offset(fx + windTilt * 0.15f, fy - flameH),
          strokeWidth = 3.8f * (1f - rise * 0.4f) * worldScale,
          cap = StrokeCap.Round
        )
      }

      // 5. Golden-white foot energy flare & crackling bio-lightning bolts across torso
      drawCircle(
        color = auraYellow.copy(alpha = (0.55f * intensity).coerceAtMost(0.85f)),
        radius = 12f * worldScale,
        center = Offset(baseSX, baseSY - 4f * worldScale)
      )

      val boltSeed = (phase * 2.8f).toInt()
      for (b in 0..2) {
        val side = if ((boltSeed + b) % 2 == 0) -1f else 1f
        val sx = baseSX + side * (6f + b * 4f) * worldScale
        val sy = baseSY - (14f + b * 18f) * worldScale
        val m1x = sx + side * (11f + sin(phase * 6f + b) * 5f) * worldScale
        val m1y = sy - 10f * worldScale
        val m2x = sx - side * 3f * worldScale
        val m2y = m1y - 9f * worldScale
        val ex = m2x + side * 8f * worldScale
        val ey = m2y - 10f * worldScale

        val boltPath = reusableLightningPath.apply {
          reset()
          moveTo(sx, sy)
          lineTo(m1x, m1y)
          lineTo(m2x, m2y)
          lineTo(ex, ey)
        }
        drawPath(
          path = boltPath,
          color = auraCrimson.copy(alpha = (0.7f * intensity).coerceIn(0f, 1f)),
          style = Stroke(width = 3.8f * worldScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
          path = boltPath,
          color = bodyWhite.copy(alpha = (0.95f * intensity).coerceIn(0f, 1f)),
          style = Stroke(width = 1.8f * worldScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
      }
    }

    // Helper to render the complete 2D cartoon body from joint positions so the white torso/hip/leg merge seamlessly
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
      windDrag: Float
    ) {
      // 1. Rear Shaded Arm & Leg (Rim glow -> Black outline -> Cel-shaded Grey fill)
      for (pass in 0..2) {
        drawCartoonSegment(shWX, shWY, backElbow.x, backElbow.y, backLimbGrey, 4.8f, pass)
        drawCartoonSegment(backElbow.x, backElbow.y, backHand.x, backHand.y, backLimbGrey, 4.4f, pass, endCapRadiusWorld = 3.0f)
        drawCartoonSegment(hipWX, hipWY, backKnee.x, backKnee.y, backLimbGrey, 5.6f, pass)
        drawCartoonSegment(backKnee.x, backKnee.y, backAnkle.x, backAnkle.y, backLimbGrey, 5.0f, pass, endCapRadiusWorld = 3.2f)
      }

      // 2. Unified White Torso + Hip + Front Leg (Pass 0 = glow, Pass 1 = thick black outline, Pass 2 = seamless white fill)
      for (pass in 0..2) {
        drawCartoonSegment(hipWX, hipWY, shWX, shWY, bodyWhite, 7.2f, pass)
        drawCartoonSegment(hipWX, hipWY, frontKnee.x, frontKnee.y, bodyWhite, 5.8f, pass)
        drawCartoonSegment(frontKnee.x, frontKnee.y, frontAnkle.x, frontAnkle.y, bodyWhite, 5.2f, pass, endCapRadiusWorld = 3.4f)
      }

      // 3. Head, Spiky Crimson Hair & Expressive Fighting-Game Face
      drawCartoonHeadAndFace(
        headWorldX = hdWX,
        headWorldY = hdWY,
        faceDirX = faceDirX,
        expression = expression,
        hasHair = hasHair,
        headTilt = headTilt,
        windDrag = windDrag
      )

      // 4. Foreground White Arm & Clenched Fist (overlapping torso cleanly)
      for (pass in 0..2) {
        drawCartoonSegment(shWX, shWY, frontElbow.x, frontElbow.y, bodyWhite, 5.0f, pass)
        drawCartoonSegment(frontElbow.x, frontElbow.y, frontHand.x, frontHand.y, bodyWhite, 4.6f, pass, endCapRadiusWorld = 3.2f)
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
          // REFERENCE FRAME 1: Standing leaned forward in agony, '> <' roar face, towering aura, dust & pebbles at feet
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
          // REFERENCE FRAME 2: Dropping to one knee, front foot sparking, fierce '\ /' glare, wide jagged aura
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
          // Bright golden-white spark at planted front foot
          drawCircle(auraYellow, 6.5f * worldScale, Offset(toScreenX(bx + 12f), toScreenY(by - 2f)))
        }

        dt < 0.68f -> {
          // REFERENCE FRAME 3: Driven down onto stomach/elbows, red-flushed face, huge spiky flame burst & lightning
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
          // REFERENCE FRAME 4: Lying flat horizontally on stomach, aura extinguished, red spiky hair to the left
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
          // REFERENCE FRAME 5: Prone overload detonation! Dome of white-pink lightning ribbons, orange blast spikes & 5 flying rock boulders
          val burstT = ((dt - 0.90f) / 0.25f).coerceIn(0f, 1f)
          val hipX = bx + 4f
          val hipY = by - 5f
          val shX = bx - 16f
          val shY = by - 5f
          val hdX = shX - 12f
          val hdY = by - 8f

          // Sharp orange/yellow impact blast spikes behind prone body
          drawFieryFightingAura(bx - 10f, by, intensity = 1.4f * (1f - burstT * 0.4f), leanBackX = 0f, isBoosted = true)

          renderCartoonBodyPose(
            hipWX = hipX, hipWY = hipY, shWX = shX, shWY = shY, hdWX = hdX, hdWY = hdY,
            frontKnee = Offset(hipX + 14f, by - 4f), frontAnkle = Offset(hipX + 26f, by - 3f),
            backKnee = Offset(hipX + 14f, by - 10f), backAnkle = Offset(hipX + 25f, by - 4f),
            frontElbow = Offset(shX + 8f, by - 2f), frontHand = Offset(shX + 18f, by - 2f),
            backElbow = Offset(shX + 6f, by - 5f), backHand = Offset(shX + 14f, by - 5f),
            faceDirX = -1f, expression = 3, hasHair = true, headTilt = -1.2f, windDrag = -1.1f
          )

          // 5 flying dark-brown outlined angular rock boulders
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
          // REFERENCE FRAME 6: Knocked out flat on stomach, hair blown completely off (bald white head), soot crumbs & rising smoke wisps
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
          // Tiny soot crumbs & rising vertical smoke lines above bald head (exact match to Reference Frame 6)
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
    // ACTIVE GAMEPLAY FRAMES: EXPLOSIVE 6-FRAME SPRINT, JUMP, FALL, LANDING
    // =========================================================================
    val isBoosted = stickman.boostTimer > 0f || stickman.blitzTimer > 0f
    val isLanding = stickman.isGrounded && stickman.landingSquashTimer > 0f
    val isJumping = !stickman.isGrounded && stickman.velocityY < -50f
    val isFalling = !stickman.isGrounded && stickman.velocityY >= -50f

    // Select or interpolate the separated animation frame
    val pose: SprintKeyframe
    val expression: Int
    when {
      isLanding -> {
        // Separated Frame: Superhero 1-Knee Power Landing (Reference Frame 2)
        pose = SprintKeyframe(
          torsoLean = 0.58f, hipOffsetY = -16.5f,
          frontThigh = 0.95f, frontKnee = -1.05f,
          backThigh = -0.52f, backKnee = -1.25f,
          frontArm = -0.95f, frontElbow = 0.85f,
          backArm = 0.55f, backElbow = 0.95f
        )
        expression = 0
      }

      isJumping -> {
        // Separated Frame: Explosive Aerial Knee-Drive & Uppercut Launch
        pose = SprintKeyframe(
          torsoLean = 0.42f, hipOffsetY = -26.5f,
          frontThigh = 1.35f, frontKnee = -1.25f,
          backThigh = -0.78f, backKnee = -0.22f,
          frontArm = 1.25f, frontElbow = 0.95f,
          backArm = -1.15f, backElbow = 0.85f
        )
        expression = if (isBoosted) 1 else 0
      }

      isFalling -> {
        // Separated Frame: Braced Airborne Combat Drop
        val airWave = sin(phase * 2.4f) * 0.14f
        pose = SprintKeyframe(
          torsoLean = 0.48f, hipOffsetY = -24.5f,
          frontThigh = 0.72f + airWave, frontKnee = -0.88f,
          backThigh = -0.55f - airWave, backKnee = -0.95f,
          frontArm = -1.35f + airWave, frontElbow = 0.65f,
          backArm = 1.15f - airWave, backElbow = 0.75f
        )
        expression = 0
      }

      else -> {
        // Separated 6-Frame Fighting-Game Sprint Cycle (snappy anime keyframe stepping!)
        val framePos = ((phase / (PI.toFloat() / 3f)) % 6f + 6f) % 6f
        val frameIdx = framePos.toInt() % 6
        val nextIdx = (frameIdx + 1) % 6
        val rawFrac = framePos - framePos.toInt()
        // Hold each keyframe slightly so individual fighting-game poses read crisply, then snap smoothly
        val snapT = ((rawFrac - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val kfA = SPRINT_KEYFRAMES[frameIdx]
        val kfB = SPRINT_KEYFRAMES[nextIdx]
        pose = SprintKeyframe(
          torsoLean = lerp(kfA.torsoLean, kfB.torsoLean, snapT),
          hipOffsetY = lerp(kfA.hipOffsetY, kfB.hipOffsetY, snapT),
          frontThigh = lerp(kfA.frontThigh, kfB.frontThigh, snapT),
          frontKnee = lerp(kfA.frontKnee, kfB.frontKnee, snapT),
          backThigh = lerp(kfA.backThigh, kfB.backThigh, snapT),
          backKnee = lerp(kfA.backKnee, kfB.backKnee, snapT),
          frontArm = lerp(kfA.frontArm, kfB.frontArm, snapT),
          frontElbow = lerp(kfA.frontElbow, kfB.frontElbow, snapT),
          backArm = lerp(kfA.backArm, kfB.backArm, snapT),
          backElbow = lerp(kfA.backElbow, kfB.backElbow, snapT)
        )
        expression = if (isBoosted) 1 else 0
      }
    }

    val slopePitch = if (stickman.isGrounded) {
      (stickman.slopeRadians * 0.55f).coerceIn(-0.42f, 0.42f)
    } else {
      (stickman.velocityY / 950f).coerceIn(-0.32f, 0.38f)
    }

    val totalTorsoAngle = pose.torsoLean + slopePitch
    val torsoLen = 21.5f
    val hipWX = stickman.footX - 3.5f
    val hipWY = stickman.footY + pose.hipOffsetY
    val shWX = hipWX + sin(totalTorsoAngle) * torsoLen
    val shWY = hipWY - cos(totalTorsoAngle) * torsoLen
    val hdWX = shWX + sin(totalTorsoAngle) * 7.5f + 2.0f
    val hdWY = shWY - cos(totalTorsoAngle) * 9.5f - 2.5f

    // 1. Roaring Fiery Red-Orange Aura, Bio-Lightning & Speed Lines
    val auraIntensity = when {
      isBoosted -> 1.45f
      isLanding -> 1.35f
      isJumping || isFalling -> 1.15f
      else -> 1.02f
    }
    drawFieryFightingAura(
      centerWorldX = stickman.footX,
      footWorldY = stickman.footY,
      intensity = auraIntensity,
      leanBackX = -11f,
      isBoosted = isBoosted
    )

    // High-speed anime dash lines & ground dust puffs while sprinting
    if (stickman.isGrounded) {
      for (s in 0..2) {
        val streakPhase = ((phase * 1.9f + s * 0.8f) % 1f)
        val sx = toScreenX(stickman.footX - 18f - streakPhase * 34f - s * 9f)
        val sy = toScreenY(stickman.footY - 12f - s * 16f)
        drawLine(
          color = (if (s % 2 == 0) auraOrange else bodyWhite).copy(alpha = 0.55f * (1f - streakPhase)),
          start = Offset(sx, sy),
          end = Offset(sx - (18f + s * 6f) * worldScale, sy),
          strokeWidth = 2.2f * worldScale,
          cap = StrokeCap.Round
        )
      }
      // Footstrike dust puff & pebble kick
      val puffAlpha = (1f - ((phase % PI.toFloat()) / PI.toFloat())).coerceIn(0f, 1f)
      val dustX = toScreenX(stickman.footX - 14f - (1f - puffAlpha) * 22f)
      val dustY = toScreenY(stickman.footY - 4f)
      drawCircle(dustTaupe.copy(alpha = 0.65f * puffAlpha), (4.5f + (1f - puffAlpha) * 5f) * worldScale, Offset(dustX, dustY))
      if (isLanding) {
        drawCircle(auraYellow, 7.0f * worldScale, Offset(toScreenX(stickman.footX + 8f), toScreenY(stickman.footY - 2f)))
      }
    }

    // 2. Compute Articulated Joint Coordinates from the Active Keyframe
    val thighLen = 13.8f
    val shinLen = 13.8f
    val upperArmLen = 11.8f
    val foreArmLen = 11.8f

    val fKneX = hipWX + sin(pose.frontThigh) * thighLen
    val fKneY = hipWY + cos(pose.frontThigh) * thighLen
    val fShnAng = pose.frontThigh + pose.frontKnee
    val fAnkX = fKneX + sin(fShnAng) * shinLen
    val fAnkY = fKneY + cos(fShnAng) * shinLen

    val bKneX = hipWX + sin(pose.backThigh) * thighLen
    val bKneY = hipWY + cos(pose.backThigh) * thighLen
    val bShnAng = pose.backThigh + pose.backKnee
    val bAnkX = bKneX + sin(bShnAng) * shinLen
    val bAnkY = bKneY + cos(bShnAng) * shinLen

    val fElbX = shWX + sin(pose.frontArm) * upperArmLen
    val fElbY = shWY + cos(pose.frontArm) * upperArmLen
    val fForAng = pose.frontArm + pose.frontElbow
    val fHndX = fElbX + sin(fForAng) * foreArmLen
    val fHndY = fElbY + cos(fForAng) * foreArmLen

    val bElbX = shWX + sin(pose.backArm) * upperArmLen
    val bElbY = shWY + cos(pose.backArm) * upperArmLen
    val bForAng = pose.backArm + pose.backElbow
    val bHndX = bElbX + sin(bForAng) * foreArmLen
    val bHndY = bElbY + cos(bForAng) * foreArmLen

    val windDrag = (-stickman.velocityX / 170f).coerceIn(-1.45f, -0.35f) + (stickman.velocityY / 600f).coerceIn(-0.4f, 0.5f)

    // 3. Render Complete 2D Cartoon Body with Thick Black Outlines, White Body, and Expressive Face
    renderCartoonBodyPose(
      hipWX = hipWX,
      hipWY = hipWY,
      shWX = shWX,
      shWY = shWY,
      hdWX = hdWX,
      hdWY = hdWY,
      frontKnee = Offset(fKneX, fKneY),
      frontAnkle = Offset(fAnkX, fAnkY),
      backKnee = Offset(bKneX, bKneY),
      backAnkle = Offset(bAnkX, bAnkY),
      frontElbow = Offset(fElbX, fElbY),
      frontHand = Offset(fHndX, fHndY),
      backElbow = Offset(bElbX, bElbY),
      backHand = Offset(bHndX, bHndY),
      faceDirX = 1f,
      expression = expression,
      hasHair = true,
      headTilt = totalTorsoAngle * 0.42f,
      windDrag = windDrag
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
}
