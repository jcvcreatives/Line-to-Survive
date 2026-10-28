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

  private fun DrawScope.drawAnimatedStickman(
    stickman: StickmanState,
    toScreenX: (Float) -> Float,
    toScreenY: (Float) -> Float,
    worldScale: Float,
    waitForPreviousTools: Boolean = false
  ) {
    val phase = stickman.runCyclePhase
    val outlineColor = Color(0xFF1C0B0B)
    val bodyWhite = Color(0xFFFDFBF7)
    val backLimbGrey = Color(0xFFC5C8CE)
    val hairCrimson = Color(0xFFD50000)
    val hairHighlight = Color(0xFFFF3D00)
    val auraRed = Color(0xFFE53935)
    val auraOrange = Color(0xFFFF6D00)
    val auraGold = Color(0xFFFFD54F)

    // Helper for drawing a high-contrast outlined limb capsule (readable on mobile screens)
    fun drawOutlinedLimb(
      x1: Float,
      y1: Float,
      x2: Float,
      y2: Float,
      fillColor: Color,
      thicknessWorld: Float
    ) {
      val s1 = Offset(toScreenX(x1), toScreenY(y1))
      val s2 = Offset(toScreenX(x2), toScreenY(y2))
      val outerW = (thicknessWorld + 3.4f) * worldScale
      val innerW = thicknessWorld * worldScale
      drawLine(
        color = outlineColor,
        start = s1,
        end = s2,
        strokeWidth = outerW,
        cap = StrokeCap.Round
      )
      drawLine(
        color = fillColor,
        start = s1,
        end = s2,
        strokeWidth = innerW,
        cap = StrokeCap.Round
      )
    }

    // Helper to render spiky Goku God crimson hair around the head center
    fun drawSpikyCrimsonHair(
      headWorldX: Float,
      headWorldY: Float,
      headAngleOffset: Float,
      windDrag: Float
    ) {
      val hx = toScreenX(headWorldX)
      val hy = toScreenY(headWorldY)
      val r = WorldConstants.STICKMAN_HEAD_RADIUS * worldScale

      // 7 layered flame-like hair spikes fanning upward and backward
      val spikeAngles = floatArrayOf(-2.75f, -2.35f, -1.95f, -1.55f, -1.15f, -0.78f, -0.42f)
      val spikeLengths = floatArrayOf(17f, 23f, 28f, 26f, 24f, 19f, 14f)

      val hairPath = reusableHairPath.apply {
        reset()
        moveTo(hx - r * 0.85f, hy + r * 0.1f)
        for (i in spikeAngles.indices) {
          val ang = spikeAngles[i] + headAngleOffset + windDrag * 0.18f
          val len = spikeLengths[i] * worldScale
          val tipX = hx + cos(ang) * len
          val tipY = hy + sin(ang) * len
          val valleyAng = ang + 0.22f
          val valleyLen = len * 0.48f
          val valX = hx + cos(valleyAng) * valleyLen
          val valY = hy + sin(valleyAng) * valleyLen
          lineTo(tipX, tipY)
          lineTo(valX, valY)
        }
        close()
      }

      drawPath(
        path = hairPath,
        color = outlineColor,
        style = Stroke(width = 3.2f * worldScale, join = StrokeJoin.Round, cap = StrokeCap.Round)
      )
      drawPath(path = hairPath, color = hairCrimson)
      drawPath(
        path = hairPath,
        color = hairHighlight.copy(alpha = 0.55f),
        style = Stroke(width = 1.4f * worldScale, join = StrokeJoin.Round)
      )
    }

    // Helper to render blazing multi-layered Ki Aura & crackling bio-lightning
    fun drawKiAuraAndLightning(
      centerWorldX: Float,
      footWorldY: Float,
      intensity: Float,
      isBoosted: Boolean
    ) {
      if (intensity <= 0.05f) return
      val baseScreenX = toScreenX(centerWorldX)
      val baseScreenY = toScreenY(footWorldY)
      val auraHeight = (74f + intensity * 22f) * worldScale
      val auraHalfW = (26f + intensity * 14f) * worldScale

      // Ambient radial glow behind character
      drawCircle(
        color = (if (isBoosted) auraGold else auraRed).copy(alpha = 0.24f * intensity.coerceAtMost(1.2f)),
        radius = auraHalfW * 1.45f,
        center = Offset(baseScreenX, baseScreenY - auraHeight * 0.45f)
      )

      // Outer crimson flame aura crown
      val outerAura = reusableAuraOuterPath.apply {
        reset()
        moveTo(baseScreenX - auraHalfW * 0.85f, baseScreenY)
        val spikes = 9
        for (i in 0..spikes) {
          val t = i.toFloat() / spikes
          val angle = PI.toFloat() + t * PI.toFloat()
          val jitter = sin(phase * 4.5f + i * 1.7f) * 6.5f * worldScale
          val rx = cos(angle) * auraHalfW + jitter * 0.35f
          val ry = sin(angle) * auraHeight - kotlin.math.abs(cos(i * 1.3f + phase * 3f)) * 12f * worldScale
          lineTo(baseScreenX + rx, baseScreenY + ry * 0.92f)
        }
        lineTo(baseScreenX + auraHalfW * 0.85f, baseScreenY)
        close()
      }
      drawPath(
        path = outerAura,
        color = (if (isBoosted) auraOrange else auraRed).copy(alpha = (0.52f * intensity).coerceIn(0f, 0.85f))
      )

      // Inner fiery orange-gold flame core
      val innerAura = reusableAuraInnerPath.apply {
        reset()
        moveTo(baseScreenX - auraHalfW * 0.58f, baseScreenY)
        val spikes = 7
        for (i in 0..spikes) {
          val t = i.toFloat() / spikes
          val angle = PI.toFloat() + t * PI.toFloat()
          val jitter = cos(phase * 5.5f + i * 2.1f) * 4.5f * worldScale
          val rx = cos(angle) * (auraHalfW * 0.65f) + jitter * 0.3f
          val ry = sin(angle) * (auraHeight * 0.78f)
          lineTo(baseScreenX + rx, baseScreenY + ry)
        }
        lineTo(baseScreenX + auraHalfW * 0.58f, baseScreenY)
        close()
      }
      drawPath(
        path = innerAura,
        color = (if (isBoosted) auraGold else auraOrange).copy(alpha = (0.62f * intensity).coerceIn(0f, 0.9f))
      )

      // Crackling white/gold bio-lightning arcs around torso & hair
      val boltSeed = (phase * 3.5f).toInt()
      for (b in 0..1) {
        val side = if ((boltSeed + b) % 2 == 0) -1f else 1f
        val startX = baseScreenX + side * 8f * worldScale
        val startY = baseScreenY - (18f + b * 20f) * worldScale
        val midX = startX + side * (10f + sin(phase * 7f + b) * 5f) * worldScale
        val midY = startY - 12f * worldScale
        val endX = startX - side * 4f * worldScale
        val endY = midY - 11f * worldScale

        val boltPath = reusableLightningPath.apply {
          reset()
          moveTo(startX, startY)
          lineTo(midX, midY)
          lineTo(endX, endY)
        }
        drawPath(
          path = boltPath,
          color = ChalkWhite.copy(alpha = (0.85f * intensity).coerceIn(0f, 1f)),
          style = Stroke(width = 1.8f * worldScale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
      }
    }

    // -------------------------------------------------------------------------
    // DEATH ANIMATION SEQUENCE (Matches Frames 1–6 of the Goku God Sprite Sheet)
    // -------------------------------------------------------------------------
    if (!stickman.isAlive) {
      val dt = stickman.deathTimer.coerceIn(0f, 1.25f)
      val baseFootX = stickman.footX
      val baseFootY = stickman.footY

      if (dt < 0.36f) {
        // Stage 1: Strained knee-collapse stagger with surging crimson aura & lightning
        val collapseT = (dt / 0.36f).coerceIn(0f, 1f)
        drawKiAuraAndLightning(baseFootX, baseFootY, intensity = 1.35f, isBoosted = false)

        val hipX = baseFootX - 4f * collapseT
        val hipY = baseFootY - 22f + 8f * collapseT
        val shoulderX = hipX + 6f + 4f * collapseT
        val shoulderY = hipY - 19f + 6f * collapseT
        val headX = shoulderX + 3f
        val headY = shoulderY - 11f

        // Back leg (kneeling down)
        drawOutlinedLimb(hipX, hipY, hipX - 10f, baseFootY - 2f, backLimbGrey, 4.6f)
        drawOutlinedLimb(hipX - 10f, baseFootY - 2f, hipX - 22f, baseFootY - 2f, backLimbGrey, 4.4f)
        // Back arm
        drawOutlinedLimb(shoulderX, shoulderY, shoulderX - 13f, shoulderY + 6f, backLimbGrey, 4.0f)
        // Torso
        drawOutlinedLimb(hipX, hipY, shoulderX, shoulderY, bodyWhite, 5.2f)
        // Front leg braced
        drawOutlinedLimb(hipX, hipY, hipX + 11f, hipY + 4f, bodyWhite, 4.6f)
        drawOutlinedLimb(hipX + 11f, hipY + 4f, hipX + 13f, baseFootY, bodyWhite, 4.4f)
        // Front arm clenched
        drawOutlinedLimb(shoulderX, shoulderY, shoulderX - 9f, shoulderY + 11f, bodyWhite, 4.2f)

        drawSpikyCrimsonHair(headX, headY, headAngleOffset = 0.15f, windDrag = -0.3f)
        val headCenter = Offset(toScreenX(headX), toScreenY(headY))
        val headR = WorldConstants.STICKMAN_HEAD_RADIUS * worldScale
        drawCircle(color = outlineColor, radius = headR + 1.8f * worldScale, center = headCenter)
        drawCircle(color = bodyWhite, radius = headR, center = headCenter)
        // Strained '> <' eyes & open mouth
        drawLine(outlineColor, Offset(headCenter.x + 1f * worldScale, headCenter.y - 3f * worldScale), Offset(headCenter.x + 5f * worldScale, headCenter.y - 1f * worldScale), 2f * worldScale, StrokeCap.Round)
        drawLine(outlineColor, Offset(headCenter.x + 5f * worldScale, headCenter.y - 1f * worldScale), Offset(headCenter.x + 1f * worldScale, headCenter.y + 1f * worldScale), 2f * worldScale, StrokeCap.Round)
      } else if (dt < 0.72f) {
        // Stage 2: Explosive ground impact with flying dark rock shards & radial shock spikes (Frame 5)
        val burstT = ((dt - 0.36f) / 0.36f).coerceIn(0f, 1f)
        drawKiAuraAndLightning(baseFootX, baseFootY, intensity = 1.5f * (1f - burstT * 0.5f), isBoosted = true)

        // Prone body slamming down
        val hipX = baseFootX
        val hipY = baseFootY - 6f
        val shoulderX = hipX - 20f
        val shoulderY = baseFootY - 7f
        val headX = shoulderX - 11f
        val headY = baseFootY - 9f

        drawOutlinedLimb(hipX, hipY, hipX + 20f, baseFootY - 4f, backLimbGrey, 4.5f)
        drawOutlinedLimb(hipX, hipY, shoulderX, shoulderY, bodyWhite, 5.2f)
        drawOutlinedLimb(hipX, hipY, hipX + 22f, baseFootY - 2f, bodyWhite, 4.6f)
        drawOutlinedLimb(shoulderX, shoulderY, shoulderX + 10f, baseFootY - 2f, bodyWhite, 4.0f)

        drawSpikyCrimsonHair(headX, headY, headAngleOffset = -1.1f, windDrag = -0.8f)
        val headCenter = Offset(toScreenX(headX), toScreenY(headY))
        val headR = WorldConstants.STICKMAN_HEAD_RADIUS * worldScale
        drawCircle(color = outlineColor, radius = headR + 1.8f * worldScale, center = headCenter)
        drawCircle(color = bodyWhite, radius = headR, center = headCenter)

        // Flying dark rock debris shards
        val shardAngles = floatArrayOf(-2.5f, -2.0f, -1.4f, -0.9f, -0.5f)
        for (i in shardAngles.indices) {
          val ang = shardAngles[i]
          val dist = (18f + burstT * 48f + i * 5f) * worldScale
          val rx = toScreenX(baseFootX) + cos(ang) * dist
          val ry = toScreenY(baseFootY) + sin(ang) * dist
          drawCircle(color = Color(0xFF3E2F28), radius = 4.5f * worldScale, center = Offset(rx, ry))
          drawCircle(color = outlineColor, radius = 4.5f * worldScale, center = Offset(rx, ry), style = Stroke(width = 1.5f * worldScale))
        }
      } else {
        // Stage 3: Collapsed flat horizontally on ground, aura extinguished (Frames 4 & 6)
        val hipX = baseFootX
        val hipY = baseFootY - 5f
        val shoulderX = hipX - 22f
        val shoulderY = baseFootY - 5f
        val headX = shoulderX - 12f
        val headY = baseFootY - 8f

        drawOutlinedLimb(hipX, hipY, hipX + 16f, baseFootY - 9f, backLimbGrey, 4.4f)
        drawOutlinedLimb(hipX + 16f, baseFootY - 9f, hipX + 25f, baseFootY - 3f, backLimbGrey, 4.2f)
        drawOutlinedLimb(hipX, hipY, shoulderX, shoulderY, bodyWhite, 5.2f)
        drawOutlinedLimb(hipX, hipY, hipX + 24f, baseFootY - 3f, bodyWhite, 4.6f)
        drawOutlinedLimb(shoulderX, shoulderY, shoulderX + 12f, baseFootY - 2f, bodyWhite, 4.0f)

        drawSpikyCrimsonHair(headX, headY, headAngleOffset = -1.25f, windDrag = -1.0f)
        val headCenter = Offset(toScreenX(headX), toScreenY(headY))
        val headR = WorldConstants.STICKMAN_HEAD_RADIUS * worldScale
        drawCircle(color = outlineColor, radius = headR + 1.8f * worldScale, center = headCenter)
        drawCircle(color = bodyWhite, radius = headR, center = headCenter)

        // Rising smoke / daze ticks above head
        for (s in 0..1) {
          val smokeY = headCenter.y - (14f + s * 8f + ((dt * 20f) % 8f)) * worldScale
          val smokeX = headCenter.x + (s * 7f - 3f) * worldScale
          drawCircle(color = Color(0xFF6D4C41).copy(alpha = 0.7f), radius = 2.5f * worldScale, center = Offset(smokeX, smokeY))
        }
      }
      return
    }

    // -------------------------------------------------------------------------
    // ACTIVE GAMEPLAY ANIMATION STATES: RUNNING, JUMPING, FALLING, LANDING
    // -------------------------------------------------------------------------
    val isBoosted = stickman.boostTimer > 0f || stickman.blitzTimer > 0f
    val isLanding = stickman.isGrounded && stickman.landingSquashTimer > 0f
    val isJumping = !stickman.isGrounded && stickman.velocityY < -50f
    val isFalling = !stickman.isGrounded && stickman.velocityY >= -50f

    val squashProgress = if (isLanding) (stickman.landingSquashTimer / 0.26f).coerceIn(0f, 1f) else 0f
    val squashDrop = sin(squashProgress * PI.toFloat()) * 10f

    // Vertical run bounce
    val runBounce = if (stickman.isGrounded && !isLanding) {
      kotlin.math.abs(sin(phase * 2f)) * 4.2f
    } else {
      0f
    }

    // Natural body rotation and pitch responding to slope & vertical velocity
    val pitchAngle = when {
      stickman.isGrounded -> (stickman.slopeRadians * 0.65f).coerceIn(-0.45f, 0.45f)
      else -> (stickman.velocityY / 900f).coerceIn(-0.38f, 0.42f)
    }
    val speedLean = (stickman.velocityX / 180f).coerceIn(0.6f, 1.45f) * 6.5f + squashDrop * 0.4f

    val hipWorldX = stickman.footX - 2f
    val hipWorldY = stickman.footY - 24f - runBounce + squashDrop
    val torsoLen = 21f - squashDrop * 0.25f
    val shoulderWorldX = hipWorldX + speedLean + sin(pitchAngle) * torsoLen
    val shoulderWorldY = hipWorldY - cos(pitchAngle) * torsoLen
    val headWorldX = shoulderWorldX + 2.8f + sin(pitchAngle) * 4f
    val headWorldY = shoulderWorldY - 11f

    // 1. Ki Flame Aura & Bio-Lightning (intensifies on Jump, Landing, or Boost/Blitz)
    val auraIntensity = when {
      isBoosted -> 1.35f
      isLanding -> 1.25f
      isJumping || isFalling -> 1.05f
      else -> 0.85f
    }
    drawKiAuraAndLightning(
      centerWorldX = stickman.footX,
      footWorldY = stickman.footY,
      intensity = auraIntensity,
      isBoosted = isBoosted
    )

    // Compute limb joint angles for Back (index 1) and Front (index 0)
    fun computeLegJoints(legIndex: Int): Pair<Offset, Offset> {
      val legPhase = phase + legIndex * PI.toFloat()
      val thighAngle: Float
      val kneeBend: Float

      when {
        isLanding -> {
          // Superhero 3-point knee-bend landing crouch (Frame 2 of reference)
          thighAngle = if (legIndex == 0) 0.78f else -0.45f
          kneeBend = if (legIndex == 0) -0.95f else -1.15f
        }
        isJumping -> {
          // Explosive upward leap: front knee driven high, back leg extended
          thighAngle = if (legIndex == 0) 0.95f else -0.42f
          kneeBend = if (legIndex == 0) -1.1f else -0.25f
        }
        isFalling -> {
          // Airborne descent: legs braced & tucked slightly underneath
          val flutter = sin(phase * 1.8f + legIndex * PI.toFloat()) * 0.18f
          thighAngle = (if (legIndex == 0) 0.48f else -0.32f) + flutter
          kneeBend = -0.68f
        }
        else -> {
          // Smooth alternating running stride
          thighAngle = sin(legPhase) * 0.82f + 0.16f + pitchAngle * 0.5f
          kneeBend = (-cos(legPhase) * 0.58f - 0.48f).coerceAtMost(-0.08f)
        }
      }

      val thighLen = 13.5f
      val shinLen = 13.5f
      val kneeX = hipWorldX + sin(thighAngle) * thighLen
      val kneeY = hipWorldY + cos(thighAngle) * thighLen
      val shinAngle = thighAngle + kneeBend
      val ankleX = kneeX + sin(shinAngle) * shinLen
      val ankleY = kneeY + cos(shinAngle) * shinLen
      return Offset(kneeX, kneeY) to Offset(ankleX, ankleY)
    }

    fun computeArmJoints(armIndex: Int): Pair<Offset, Offset> {
      val armPhase = phase + (armIndex + 1) * PI.toFloat()
      val upperArmAngle: Float
      val elbowBend: Float

      when {
        isLanding -> {
          // Braced power arms on impact
          upperArmAngle = if (armIndex == 0) 0.45f else -0.85f
          elbowBend = 0.85f
        }
        isJumping -> {
          // Swept back / uppercut launch pose
          upperArmAngle = if (armIndex == 0) 0.85f else -0.95f
          elbowBend = 0.95f
        }
        isFalling -> {
          // Wind-swept raised arms while descending
          val wave = cos(phase * 2.2f + armIndex) * 0.22f
          upperArmAngle = (if (armIndex == 0) 1.85f else -1.65f) + wave
          elbowBend = 0.45f
        }
        else -> {
          // Smooth alternating running arm pump opposite to legs
          upperArmAngle = sin(armPhase) * 0.78f + 0.08f
          elbowBend = 0.82f + cos(armPhase) * 0.25f
        }
      }

      val upperLen = 11.5f
      val foreLen = 11.5f
      val elbowX = shoulderWorldX + sin(upperArmAngle) * upperLen
      val elbowY = shoulderWorldY + cos(upperArmAngle) * upperLen
      val foreAngle = upperArmAngle + elbowBend
      val handX = elbowX + sin(foreAngle) * foreLen
      val handY = elbowY + cos(foreAngle) * foreLen
      return Offset(elbowX, elbowY) to Offset(handX, handY)
    }

    // 2. Draw Rear Arm & Rear Leg first (shaded grey with dark outline for depth readability)
    val (backElbow, backHand) = computeArmJoints(1)
    drawOutlinedLimb(shoulderWorldX, shoulderWorldY, backElbow.x, backElbow.y, backLimbGrey, 3.8f)
    drawOutlinedLimb(backElbow.x, backElbow.y, backHand.x, backHand.y, backLimbGrey, 3.6f)

    val (backKnee, backAnkle) = computeLegJoints(1)
    drawOutlinedLimb(hipWorldX, hipWorldY, backKnee.x, backKnee.y, backLimbGrey, 4.4f)
    drawOutlinedLimb(backKnee.x, backKnee.y, backAnkle.x, backAnkle.y, backLimbGrey, 4.2f)

    // 3. Draw Torso (crisp white with dark outline)
    drawOutlinedLimb(hipWorldX, hipWorldY, shoulderWorldX, shoulderWorldY, bodyWhite, 5.2f)

    // 4. Draw Front Leg (crisp white with dark outline)
    val (frontKnee, frontAnkle) = computeLegJoints(0)
    drawOutlinedLimb(hipWorldX, hipWorldY, frontKnee.x, frontKnee.y, bodyWhite, 4.5f)
    drawOutlinedLimb(frontKnee.x, frontKnee.y, frontAnkle.x, frontAnkle.y, bodyWhite, 4.3f)

    // 5. Draw Spiky Crimson Hair + Outlined White Head + Expressive Eyes/Brows
    val windDrag = (-stickman.velocityX / 220f).coerceIn(-1.2f, 0f) + (stickman.velocityY / 600f).coerceIn(-0.5f, 0.6f)
    drawSpikyCrimsonHair(
      headWorldX = headWorldX,
      headWorldY = headWorldY,
      headAngleOffset = pitchAngle * 0.5f,
      windDrag = windDrag
    )

    val headCenter = Offset(toScreenX(headWorldX), toScreenY(headWorldY))
    val headRadiusPx = WorldConstants.STICKMAN_HEAD_RADIUS * worldScale

    drawCircle(
      color = outlineColor,
      radius = headRadiusPx + 1.8f * worldScale,
      center = headCenter
    )
    drawCircle(
      color = bodyWhite,
      radius = headRadiusPx,
      center = headCenter
    )

    // Intense V-brow and eyes facing right (or strained '> <' during landing/boost)
    val eyeX = headCenter.x + 3.6f * worldScale
    val eyeY = headCenter.y - 0.5f * worldScale
    drawLine(
      color = outlineColor,
      start = Offset(eyeX - 3.5f * worldScale, eyeY - 3.2f * worldScale),
      end = Offset(eyeX + 2.2f * worldScale, eyeY - 1.0f * worldScale),
      strokeWidth = 2.0f * worldScale,
      cap = StrokeCap.Round
    )
    drawCircle(
      color = hairCrimson,
      radius = 1.9f * worldScale,
      center = Offset(eyeX, eyeY + 0.6f * worldScale)
    )

    // 6. Draw Front Arm in foreground
    val (frontElbow, frontHand) = computeArmJoints(0)
    drawOutlinedLimb(shoulderWorldX, shoulderWorldY, frontElbow.x, frontElbow.y, bodyWhite, 4.0f)
    drawOutlinedLimb(frontElbow.x, frontElbow.y, frontHand.x, frontHand.y, bodyWhite, 3.8f)
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
