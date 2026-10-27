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
    worldScale: Float
  ) {
    val footScreenX = toScreenX(stickman.footX)
    val footScreenY = toScreenY(stickman.footY)
    val phase = stickman.runCyclePhase

    // Slight vertical bounce while running on ground
    val bounce = if (stickman.isGrounded) {
      kotlin.math.abs(sin(phase * 2f)) * 4.5f
    } else {
      0f
    }

    // Forward lean while running
    val leanX = 7f
    val hipWorldX = stickman.footX - 2f
    val hipWorldY = stickman.footY - 24f - bounce
    val shoulderWorldX = hipWorldX + leanX
    val shoulderWorldY = hipWorldY - 21f
    val headWorldX = shoulderWorldX + 2.5f
    val headWorldY = shoulderWorldY - 11f

    val strokeW = 4.2f * worldScale

    // Torso
    drawLine(
      color = ChalkWhite,
      start = Offset(toScreenX(hipWorldX), toScreenY(hipWorldY)),
      end = Offset(toScreenX(shoulderWorldX), toScreenY(shoulderWorldY)),
      strokeWidth = strokeW,
      cap = StrokeCap.Round
    )

    // Articulated Legs (Left + Right phase offset by PI)
    for (legIndex in 0..1) {
      val legPhase = phase + legIndex * PI.toFloat()
      val thighAngle = if (stickman.isGrounded) {
        sin(legPhase) * 0.75f + 0.15f
      } else {
        // Airborne flailing pose
        if (legIndex == 0) 0.55f else -0.45f
      }
      val kneeBend = if (stickman.isGrounded) {
        (-cos(legPhase) * 0.55f - 0.45f).coerceAtMost(-0.08f)
      } else {
        -0.55f
      }

      val thighLen = 13f
      val shinLen = 13f

      val kneeX = hipWorldX + sin(thighAngle) * thighLen
      val kneeY = hipWorldY + cos(thighAngle) * thighLen

      val shinAngle = thighAngle + kneeBend
      val ankleX = kneeX + sin(shinAngle) * shinLen
      val ankleY = kneeY + cos(shinAngle) * shinLen

      val legColor = if (legIndex == 0) ChalkWhite else PencilGraphite

      drawLine(
        color = legColor,
        start = Offset(toScreenX(hipWorldX), toScreenY(hipWorldY)),
        end = Offset(toScreenX(kneeX), toScreenY(kneeY)),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
      )
      drawLine(
        color = legColor,
        start = Offset(toScreenX(kneeX), toScreenY(kneeY)),
        end = Offset(toScreenX(ankleX), toScreenY(ankleY)),
        strokeWidth = strokeW,
        cap = StrokeCap.Round
      )
    }

    // Articulated Arms (swing opposite to legs)
    for (armIndex in 0..1) {
      val armPhase = phase + (armIndex + 1) * PI.toFloat()
      val upperArmAngle = if (stickman.isGrounded) {
        sin(armPhase) * 0.7f + 0.1f
      } else {
        // Arms raised in air when falling
        if (armIndex == 0) 2.1f else -1.9f
      }
      val elbowBend = if (stickman.isGrounded) 0.75f else 0.35f

      val upperLen = 11f
      val foreLen = 11f

      val elbowX = shoulderWorldX + sin(upperArmAngle) * upperLen
      val elbowY = shoulderWorldY + cos(upperArmAngle) * upperLen

      val foreAngle = upperArmAngle + elbowBend
      val handX = elbowX + sin(foreAngle) * foreLen
      val handY = elbowY + cos(foreAngle) * foreLen

      val armColor = if (armIndex == 0) ChalkWhite else PencilGraphite

      drawLine(
        color = armColor,
        start = Offset(toScreenX(shoulderWorldX), toScreenY(shoulderWorldY)),
        end = Offset(toScreenX(elbowX), toScreenY(elbowY)),
        strokeWidth = strokeW * 0.9f,
        cap = StrokeCap.Round
      )
      drawLine(
        color = armColor,
        start = Offset(toScreenX(elbowX), toScreenY(elbowY)),
        end = Offset(toScreenX(handX), toScreenY(handY)),
        strokeWidth = strokeW * 0.9f,
        cap = StrokeCap.Round
      )
    }

    // Sketchbook Head (filled charcoal inside so background grid doesn't show through head)
    val headCenter = Offset(toScreenX(headWorldX), toScreenY(headWorldY))
    val headRadiusPx = WorldConstants.STICKMAN_HEAD_RADIUS * worldScale

    drawCircle(
      color = SketchCharcoalBg,
      radius = headRadiusPx,
      center = headCenter
    )
    drawCircle(
      color = ChalkWhite,
      radius = headRadiusPx,
      center = headCenter,
      style = Stroke(width = strokeW * 0.95f)
    )
    // Determined eyes facing right
    drawCircle(
      color = ChalkWhite,
      radius = 1.8f * worldScale,
      center = Offset(
        headCenter.x + 3.8f * worldScale,
        headCenter.y - 1.5f * worldScale
      )
    )

    // Flowing orange bandana tail for visual flair
    val tailWave = sin(phase * 2.5f) * 4f
    val bandanaPath = reusableBandanaPath.apply {
      reset()
      moveTo(headCenter.x - headRadiusPx * 0.8f, headCenter.y - 2f * worldScale)
      lineTo(headCenter.x - (headRadiusPx + 12f * worldScale), headCenter.y + tailWave * worldScale)
      lineTo(headCenter.x - (headRadiusPx + 9f * worldScale), headCenter.y + (tailWave + 5f) * worldScale)
      close()
    }
    drawPath(path = bandanaPath, color = LavaOrange)
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
