package com.example.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.audio.SoundEffectManager
import com.example.data.ProgressRepository
import com.example.game.level.LevelCatalog
import com.example.game.model.CalloutMove
import com.example.game.model.CalloutMoveType
import com.example.game.model.ChalkTool
import com.example.game.model.DrawnLine
import com.example.game.model.GameUiState
import com.example.game.model.LevelDefinition
import com.example.game.model.ScreenState
import com.example.game.model.StickmanState
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import com.example.game.physics.PhysicsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Manages the game state, level progression, multi-chalk palette tools, callout move bubbles,
 * chalk bit magnet collection, red scribbler enemies, and 60 FPS simulation for Draw to Survive.
 */
class GameViewModel(
  private val progressRepository: ProgressRepository,
  private val soundManager: SoundEffectManager
) : ViewModel() {

  private var nextLineId = 1L

  private val _uiState = MutableStateFlow(createInitialState())
  val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

  private val _screenState = MutableStateFlow(ScreenState.START_MENU)
  val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

  private val _hudState = MutableStateFlow(createInitialHudState(_uiState.value))
  val hudState: StateFlow<com.example.game.model.GameplayHudState> = _hudState.asStateFlow()

  fun currentGameState(): GameUiState = _uiState.value
  fun currentCameraX(): Float = _uiState.value.cameraX

  private fun createInitialHudState(state: GameUiState): com.example.game.model.GameplayHudState {
    return com.example.game.model.GameplayHudState(
      currentLevel = state.currentLevel,
      levelTitle = state.levelDefinition.title,
      levelProgress = state.levelProgress,
      inkRemainingRatio = state.inkRemainingRatio,
      isInkRecharging = state.isInkRecharging,
      soundEnabled = state.soundEnabled,
      selectedTool = state.selectedTool,
      totalBitsInLevel = state.totalBitsInLevel,
      bitsCollectedCount = state.bitsCollectedCount,
      comboCount = state.comboCount,
      activeCallout = state.activeCallout,
      hasCompletedLines = state.completedLines.isNotEmpty(),
      showInstructionBanner = state.showInstructionBanner
    )
  }

  private inline fun updateState(crossinline transform: (GameUiState) -> GameUiState) {
    _uiState.update { current ->
      val next = transform(current)
      if (_screenState.value != next.screenState) {
        _screenState.value = next.screenState
      }
      syncHudStateIfChanged(next)
      next
    }
  }

  private fun syncHudStateIfChanged(state: GameUiState) {
    val cur = _hudState.value
    val curInkPct = (cur.inkRemainingRatio * 100f).toInt()
    val nextInkPct = (state.inkRemainingRatio * 100f).toInt()
    val curProgPct = (cur.levelProgress * 100f).toInt()
    val nextProgPct = (state.levelProgress * 100f).toInt()
    val hasLines = state.completedLines.isNotEmpty()

    if (cur.currentLevel != state.currentLevel ||
      cur.selectedTool != state.selectedTool ||
      cur.soundEnabled != state.soundEnabled ||
      cur.comboCount != state.comboCount ||
      cur.bitsCollectedCount != state.bitsCollectedCount ||
      cur.totalBitsInLevel != state.totalBitsInLevel ||
      cur.activeCallout != state.activeCallout ||
      cur.hasCompletedLines != hasLines ||
      cur.showInstructionBanner != state.showInstructionBanner ||
      cur.isInkRecharging != state.isInkRecharging ||
      curInkPct != nextInkPct ||
      curProgPct != nextProgPct ||
      cur.levelTitle != state.levelDefinition.title
    ) {
      _hudState.value = com.example.game.model.GameplayHudState(
        currentLevel = state.currentLevel,
        levelTitle = state.levelDefinition.title,
        levelProgress = state.levelProgress,
        inkRemainingRatio = state.inkRemainingRatio,
        isInkRecharging = state.isInkRecharging,
        soundEnabled = state.soundEnabled,
        selectedTool = state.selectedTool,
        totalBitsInLevel = state.totalBitsInLevel,
        bitsCollectedCount = state.bitsCollectedCount,
        comboCount = state.comboCount,
        activeCallout = state.activeCallout,
        hasCompletedLines = hasLines,
        showInstructionBanner = state.showInstructionBanner
      )
    }
  }

  private fun createInitialState(): GameUiState {
    val bestLevel = progressRepository.getBestLevel()
    val savedStars = progressRepository.getAllLevelStars()
    val soundOn = progressRepository.isSoundEnabled()
    soundManager.setSoundEnabledState(enabled = soundOn, shouldPlayBgm = false)
    val level1 = LevelCatalog.getLevel(1)
    return GameUiState(
      screenState = ScreenState.START_MENU,
      currentLevel = 1,
      bestLevel = bestLevel,
      levelStars = savedStars,
      levelDefinition = level1,
      stickman = createInitialStickman(level1),
      cameraX = 0f,
      selectedTool = ChalkTool.WHITE,
      currentInk = level1.maxInkPerLevel,
      chalkBits = level1.chalkBits,
      scribblers = level1.scribblers,
      soundEnabled = soundOn,
      showInstructionBanner = true
    )
  }

  private fun createInitialStickman(level: LevelDefinition): StickmanState {
    return StickmanState(
      footX = level.spawnPosition.x,
      footY = level.spawnPosition.y,
      velocityX = level.runSpeed,
      velocityY = 0f,
      isGrounded = true
    )
  }

  fun selectChalkTool(tool: ChalkTool) {
    soundManager.playButtonClick()
    if (_uiState.value.activeStrokePoints.size >= 2) {
      commitActiveStrokeIfValid()
    }
    updateState { it.copy(selectedTool = tool, activeStrokePoints = emptyList()) }
  }

  fun selectMapLevel(levelNumber: Int) {
    val clamped = levelNumber.coerceIn(1, LevelCatalog.TOTAL_LEVELS)
    if (clamped <= _uiState.value.bestLevel) {
      soundManager.playButtonClick()
      loadLevel(clamped, targetScreenState = ScreenState.START_MENU)
    }
  }

  fun startGame(levelNumber: Int = _uiState.value.currentLevel) {
    soundManager.playButtonClick()
    loadLevel(levelNumber.coerceIn(1, LevelCatalog.TOTAL_LEVELS), targetScreenState = ScreenState.PLAYING)
  }

  fun retryCurrentLevel() {
    soundManager.playButtonClick()
    loadLevel(_uiState.value.currentLevel, targetScreenState = ScreenState.PLAYING)
  }

  fun startNextLevel() {
    soundManager.playButtonClick()
    val nextLevel = (_uiState.value.currentLevel + 1).coerceIn(1, LevelCatalog.TOTAL_LEVELS)
    progressRepository.recordLevelReached(nextLevel)
    loadLevel(nextLevel, targetScreenState = ScreenState.PLAYING)
  }

  fun pauseGame() {
    if (_uiState.value.screenState == ScreenState.PLAYING) {
      soundManager.playButtonClick()
      soundManager.pauseBackgroundMusic()
      commitActiveStrokeIfValid()
      updateState { it.copy(screenState = ScreenState.PAUSED) }
    }
  }

  fun resumeGame() {
    if (_uiState.value.screenState == ScreenState.PAUSED) {
      soundManager.playButtonClick()
      soundManager.startBackgroundMusic()
      updateState { it.copy(screenState = ScreenState.PLAYING) }
    }
  }

  fun returnToStartMenu() {
    soundManager.playButtonClick()
    soundManager.pauseBackgroundMusic()
    val current = _uiState.value
    loadLevel(current.currentLevel, targetScreenState = ScreenState.START_MENU)
  }

  fun clearDrawnLines() {
    soundManager.playButtonClick()
    updateState {
      it.copy(
        completedLines = emptyList(),
        activeStrokePoints = emptyList(),
        activeStrokeRemainingRatio = 1f,
        currentInk = it.levelDefinition.maxInkPerLevel,
        sketchParticles = emptyList()
      )
    }
  }

  fun toggleSound() {
    val newEnabled = !_uiState.value.soundEnabled
    val isPlaying = _uiState.value.screenState == ScreenState.PLAYING
    soundManager.setSoundEnabledState(enabled = newEnabled, shouldPlayBgm = isPlaying)
    progressRepository.setSoundEnabled(newEnabled)
    if (newEnabled) {
      soundManager.playButtonClick()
    }
    updateState { it.copy(soundEnabled = newEnabled) }
  }

  private fun loadLevel(levelNumber: Int, targetScreenState: ScreenState) {
    val validLevel = levelNumber.coerceIn(1, LevelCatalog.TOTAL_LEVELS)
    val levelDef = LevelCatalog.getLevel(validLevel)
    val best = max(progressRepository.getBestLevel(), validLevel).coerceIn(1, LevelCatalog.TOTAL_LEVELS)
    progressRepository.recordLevelReached(best)
    val savedStars = progressRepository.getAllLevelStars()

    if (targetScreenState == ScreenState.PLAYING) {
      soundManager.startBackgroundMusic()
    } else {
      soundManager.pauseBackgroundMusic()
    }

    updateState { state ->
      state.copy(
        screenState = targetScreenState,
        currentLevel = validLevel,
        bestLevel = best,
        levelStars = savedStars,
        levelDefinition = levelDef,
        stickman = createInitialStickman(levelDef),
        cameraX = 0f,
        selectedTool = ChalkTool.WHITE,
        completedLines = emptyList(),
        activeStrokePoints = emptyList(),
        activeStrokeRemainingRatio = 1f,
        currentInk = levelDef.maxInkPerLevel,
        totalInkDrawn = 0f,
        chalkBits = levelDef.chalkBits,
        scribblers = levelDef.scribblers,
        activeCallout = null,
        triggeredCalloutIndices = emptySet(),
        comboCount = 0,
        starsEarned = 3,
        levelProgress = 0f,
        sketchParticles = emptyList(),
        showInstructionBanner = true
      )
    }
  }

  /**
   * Begins a new player-drawn line or performs a shock-cut if the Cutter tool is active.
   */
  fun onDrawStart(worldPoint: Vec2) {
    val state = _uiState.value
    if (state.screenState != ScreenState.PLAYING) return

    if (state.selectedTool == ChalkTool.CUTTER) {
      performCutAndShockwave(worldPoint)
      return
    }

    if (state.currentInk <= WorldConstants.MIN_POINT_DISTANCE) {
      soundManager.playButtonClick()
      return
    }

    val clamped = Vec2(
      x = worldPoint.x,
      y = worldPoint.y.coerceIn(4f, WorldConstants.WORLD_HEIGHT - 4f)
    )

    val (bitsAfterStart, collectedAtStart) = PhysicsEngine.collectChalkBitsAlongStroke(
      bits = state.chalkBits,
      strokePoints = listOf(clamped)
    )
    val startBonusInk = collectedAtStart.sumOf { it.inkAmount.toDouble() }.toFloat()
    val startSparkParticles = if (collectedAtStart.isNotEmpty()) {
      soundManager.playLineCompleted()
      PhysicsEngine.createLineCompletedParticles(collectedAtStart.map { Vec2(it.x, it.y) })
    } else {
      emptyList()
    }

    soundManager.playDrawingScratch()
    val tipParticles = PhysicsEngine.createPencilTipParticles(clamped, count = 1) + startSparkParticles

    updateState {
      it.copy(
        activeStrokePoints = listOf(clamped),
        activeStrokeRemainingRatio = 1f,
        currentInk = (it.currentInk + startBonusInk).coerceAtMost(it.levelDefinition.maxInkPerLevel * 1.5f),
        chalkBits = bitsAfterStart,
        comboCount = it.comboCount + collectedAtStart.size,
        sketchParticles = (it.sketchParticles + tipParticles).takeLast(45)
      )
    }
  }

  /**
   * Appends a dragged finger point in world coordinates. Automatically solidifies the line
   * if the player reaches the maximum drawing length per attempt or depletes the ink meter.
   * Drawing the line to floating Chalk pickups immediately refills additional chalk level!
   */
  fun onDrawMove(worldPoint: Vec2) {
    val state = _uiState.value
    if (state.screenState != ScreenState.PLAYING) return

    if (state.selectedTool == ChalkTool.CUTTER) {
      performCutAndShockwave(worldPoint)
      return
    }

    if (state.activeStrokePoints.isEmpty()) return

    val toolCostMult = state.selectedTool.inkCostMultiplier.coerceAtLeast(1f)
    val maxStrokeLen = state.levelDefinition.maxStrokeLength
    val remainingInk = state.currentInk.coerceAtLeast(0f)
    val maxAffordableDistance = remainingInk / toolCostMult

    if (remainingInk <= 1f) {
      if (state.activeStrokePoints.size >= 2) {
        commitActiveStrokeIfValid()
      }
      return
    }

    val updatedPoints = PhysicsEngine.appendPointToStroke(
      existingPoints = state.activeStrokePoints,
      candidatePoint = worldPoint,
      maxStrokeLength = maxStrokeLen,
      availableInkForRemaining = maxAffordableDistance
    )

    val addedNewPoint = updatedPoints.size > state.activeStrokePoints.size
    val prevStrokeLen = PhysicsEngine.calculatePolylineLength(state.activeStrokePoints)
    val strokeLen = PhysicsEngine.calculatePolylineLength(updatedPoints)
    val deltaLen = (strokeLen - prevStrokeLen).coerceAtLeast(0f)
    val deltaInk = deltaLen * toolCostMult

    // Check if the player's drawn line reached any floating Chalk pickups for additional chalk level
    val (updatedBits, newlyCollectedBits) = PhysicsEngine.collectChalkBitsAlongStroke(
      bits = state.chalkBits,
      strokePoints = updatedPoints
    )
    val bonusChalkInk = newlyCollectedBits.sumOf { it.inkAmount.toDouble() }.toFloat()

    val newInk = (state.currentInk - deltaInk + bonusChalkInk)
      .coerceIn(0f, state.levelDefinition.maxInkPerLevel * 1.5f)

    val remainingStrokeRatio = ((maxStrokeLen - strokeLen) / maxStrokeLen).coerceIn(0f, 1f)

    val pickupSparks = if (newlyCollectedBits.isNotEmpty()) {
      soundManager.playLineCompleted()
      PhysicsEngine.createLineCompletedParticles(newlyCollectedBits.map { Vec2(it.x, it.y) })
    } else {
      emptyList()
    }

    val tipParticles = if (addedNewPoint) {
      soundManager.playDrawingScratch()
      PhysicsEngine.createPencilTipParticles(updatedPoints.last(), count = 1) + pickupSparks
    } else {
      pickupSparks
    }

    updateState {
      it.copy(
        activeStrokePoints = updatedPoints,
        activeStrokeRemainingRatio = remainingStrokeRatio,
        currentInk = newInk,
        totalInkDrawn = it.totalInkDrawn + deltaInk,
        chalkBits = updatedBits,
        comboCount = it.comboCount + newlyCollectedBits.size,
        sketchParticles = (it.sketchParticles + tipParticles).takeLast(45)
      )
    }

    if ((strokeLen >= maxStrokeLen - 1f || newInk <= 1f) && updatedPoints.size >= 2) {
      commitActiveStrokeIfValid()
    }
  }

  /**
   * Completes the current finger stroke and turns it into a solid physical collision surface,
   * or triggers tap-to-cut if the user tapped on an existing line.
   */
  fun onDrawEnd() {
    val state = _uiState.value
    if (state.selectedTool == ChalkTool.CUTTER) return
    if (state.activeStrokePoints.size == 1) {
      val tapPt = state.activeStrokePoints.first()
      updateState { it.copy(activeStrokePoints = emptyList(), activeStrokeRemainingRatio = 1f) }
      performCutAndShockwave(tapPt)
      return
    }
    commitActiveStrokeIfValid()
  }

  private fun performCutAndShockwave(point: Vec2) {
    val state = _uiState.value
    val (remainingLines, removedLen) = PhysicsEngine.cutLineNearPoint(
      lines = state.completedLines,
      tapPoint = point,
      reach = 42f
    )
    if (removedLen <= 0f) return

    val refundedInk = removedLen * 0.6f
    val burstParticles = PhysicsEngine.createLineCompletedParticles(listOf(point))
    soundManager.playLineCompleted()

    // Shockwave stuns/tumbles nearby Red Scribblers within 140 units
    val updatedScribblers = state.scribblers.map { scribbler ->
      if (!scribbler.isTumbling && hypotDistance(scribbler.x, scribbler.y, point.x, point.y) <= 140f) {
        scribbler.copy(isTumbling = true, tumbleVy = -380f)
      } else {
        scribbler
      }
    }

    updateState { current ->
      current.copy(
        completedLines = remainingLines,
        currentInk = (current.currentInk + refundedInk).coerceAtMost(current.levelDefinition.maxInkPerLevel),
        scribblers = updatedScribblers,
        sketchParticles = (current.sketchParticles + burstParticles).takeLast(30)
      )
    }
  }

  private fun hypotDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    return Vec2(x1, y1).distanceTo(Vec2(x2, y2))
  }

  private fun commitActiveStrokeIfValid() {
    val state = _uiState.value
    val rawPoints = state.activeStrokePoints
    if (rawPoints.size < 2) {
      updateState {
        it.copy(activeStrokePoints = emptyList(), activeStrokeRemainingRatio = 1f)
      }
      return
    }

    val smoothedPoints = PhysicsEngine.smoothStrokePoints(rawPoints)
    val lifetime = if (state.selectedTool == ChalkTool.BOOST) {
      9999f
    } else {
      state.levelDefinition.lineLifespan
    }
    val newLine = DrawnLine(
      id = nextLineId++,
      points = smoothedPoints,
      isCompleted = true,
      tool = state.selectedTool,
      ageSeconds = 0f,
      maxLifetime = lifetime
    )

    val lineBurstParticles = PhysicsEngine.createLineCompletedParticles(smoothedPoints)

    soundManager.playLineCompleted()

    updateState { current ->
      current.copy(
        completedLines = current.completedLines + newLine,
        activeStrokePoints = emptyList(),
        activeStrokeRemainingRatio = 1f,
        sketchParticles = (current.sketchParticles + lineBurstParticles).takeLast(30),
        showInstructionBanner = false
      )
    }
  }

  /**
   * Advances the game simulation by [deltaSeconds] (targeted at 60 FPS).
   * [visibleWorldWidth] is the current screen width expressed in logical world units.
   */
  fun stepFrame(deltaSeconds: Float, visibleWorldWidth: Float = 1000f) {
    val state = _uiState.value
    val clampedDt = deltaSeconds.coerceIn(0f, 0.05f)
    if (clampedDt <= 0f) return

    val nextLavaTime = state.lavaAnimTime + clampedDt
    val nextEmbers = PhysicsEngine.updateEmbers(
      existing = state.embers,
      cameraX = state.cameraX,
      visibleWorldWidth = visibleWorldWidth,
      dt = clampedDt
    )

    if (state.screenState != ScreenState.PLAYING) {
      val idleParticles = PhysicsEngine.updateSketchParticles(
        existing = state.sketchParticles,
        stickman = state.stickman,
        justLanded = false,
        dt = clampedDt,
        emitFootsteps = false
      )
      val updatedStickman = if (!state.stickman.isAlive) {
        state.stickman.copy(deathTimer = (state.stickman.deathTimer + clampedDt).coerceAtMost(1.45f))
      } else {
        state.stickman
      }
      updateState {
        it.copy(
          stickman = updatedStickman,
          lavaAnimTime = nextLavaTime,
          embers = nextEmbers,
          sketchParticles = idleParticles
        )
      }
      return
    }

    val level = state.levelDefinition

    // 1. Age lines that the stickman has reached or passed; Turbo Gold lines never expire
    val agedLines = state.completedLines.mapNotNull { line ->
      if (line.tool == ChalkTool.BOOST) {
        line
      } else {
        val minLineX = line.points.minOfOrNull { it.x } ?: 0f
        val shouldAge = state.stickman.footX >= minLineX - 90f
        val nextAge = if (shouldAge) line.ageSeconds + clampedDt else line.ageSeconds
        if (nextAge >= line.maxLifetime) null else line.copy(ageSeconds = nextAge)
      }
    }

    // 2. Check & advance scripted Callout Move bubbles
    var nextCallout = state.activeCallout
    val nextTriggeredIndices = state.triggeredCalloutIndices.toMutableSet()
    var preStickman = state.stickman

    if (nextCallout == null) {
      val script = level.calloutScript
      for (idx in script.indices) {
        if (idx !in nextTriggeredIndices && preStickman.footX >= script[idx].triggerX) {
          nextTriggeredIndices.add(idx)
          val moveType = script[idx].type
          val landingX = if (moveType == CalloutMoveType.JUMP) {
            preStickman.footX + level.runSpeed * 1.45f
          } else null
          nextCallout = CalloutMove(
            type = moveType,
            remainingSeconds = 0.95f,
            totalSeconds = 0.95f,
            triggerWorldX = preStickman.footX,
            landingTargetX = landingX,
            landingTargetY = preStickman.footY
          )
          break
        }
      }
    } else {
      val rem = nextCallout.remainingSeconds - clampedDt
      if (rem <= 0f) {
        // Execute callout move!
        when (nextCallout.type) {
          CalloutMoveType.JUMP -> {
            if (preStickman.isGrounded) {
              preStickman = preStickman.copy(velocityY = -430f, isGrounded = false)
              soundManager.playJump()
            }
          }
          CalloutMoveType.BLITZ -> {
            preStickman = preStickman.copy(blitzTimer = 1.35f)
          }
          CalloutMoveType.GRAVITY -> {
            preStickman = preStickman.copy(lowGravityTimer = 2.6f)
          }
          CalloutMoveType.WIPEOUT -> {
            // Wipeout ages lines behind the runner
          }
        }
        nextCallout = null
      } else {
        nextCallout = nextCallout.copy(remainingSeconds = rem)
      }
    }

    // 3. Step stickman physics
    val activeLines = if (state.activeStrokePoints.size >= 2) {
      agedLines + DrawnLine(
        id = -1L,
        points = PhysicsEngine.smoothStrokePoints(state.activeStrokePoints),
        isCompleted = false,
        tool = state.selectedTool
      )
    } else {
      agedLines
    }

    val tickResult = PhysicsEngine.stepPhysics(
      stickman = preStickman,
      level = level,
      completedLines = activeLines,
      deltaSeconds = clampedDt,
      animTime = state.lavaAnimTime
    )

    if (tickResult.justJumped || tickResult.justBouncedSpring) {
      soundManager.playJump()
    }
    if (tickResult.justLanded) {
      soundManager.playStepLand()
    }

    // 4. Step Red Scribbler enemies
    val (nextScribblers, stickmanAfterEnemies) = PhysicsEngine.stepScribblers(
      scribblers = state.scribblers,
      stickman = tickResult.stickman,
      platforms = level.platforms,
      completedLines = activeLines,
      dt = clampedDt
    )

    // 5. Update Chalk Bits & Magnet Combo collection (by stickman OR by player-drawn lines)
    val (nextChalkBits, newlyCollectedBits) = PhysicsEngine.updateChalkBits(
      bits = state.chalkBits,
      stickman = stickmanAfterEnemies,
      comboCount = state.comboCount,
      dt = clampedDt,
      activeLines = activeLines
    )

    val collectedInkBonus = newlyCollectedBits.sumOf { it.inkAmount.toDouble() }.toFloat()
    val nextCombo = if (newlyCollectedBits.isNotEmpty()) {
      soundManager.playLineCompleted()
      state.comboCount + newlyCollectedBits.size
    } else {
      state.comboCount
    }

    // 6. Ink recharges slowly over time (plus combo bonus) while not actively drawing
    val comboBonusMult = 1f + min(nextCombo, 5) * 0.08f
    val currentInk = state.currentInk
    val inkRecharge = if (state.activeStrokePoints.isEmpty() && currentInk < level.maxInkPerLevel) {
      level.inkRechargeRate * comboBonusMult * clampedDt
    } else {
      0f
    }
    val maxInkCap = if (collectedInkBonus > 0f || currentInk > level.maxInkPerLevel) {
      level.maxInkPerLevel * 1.5f
    } else {
      level.maxInkPerLevel
    }
    val updatedInk = (currentInk + inkRecharge + collectedInkBonus).coerceAtMost(maxInkCap)

    val nextSketchParticles = PhysicsEngine.updateSketchParticles(
      existing = state.sketchParticles,
      stickman = stickmanAfterEnemies,
      justLanded = tickResult.justLanded,
      dt = clampedDt,
      emitFootsteps = true
    )

    val desiredCameraX = (stickmanAfterEnemies.footX - visibleWorldWidth * WorldConstants.CAMERA_LEAD_RATIO)
      .coerceIn(0f, (level.levelEndX - visibleWorldWidth * 0.5f).coerceAtLeast(0f))

    val newCameraX = state.cameraX + (desiredCameraX - state.cameraX) * 0.25f

    val totalRunDistance = (level.finishLineX - level.spawnPosition.x).coerceAtLeast(1f)
    val coveredDistance = (stickmanAfterEnemies.footX - level.spawnPosition.x).coerceAtLeast(0f)
    val progress = (coveredDistance / totalRunDistance).coerceIn(0f, 1f)

    when {
      tickResult.hitLava || !stickmanAfterEnemies.isAlive -> {
        soundManager.playLavaGameOver()
        updateState {
          it.copy(
            screenState = ScreenState.GAME_OVER,
            stickman = stickmanAfterEnemies,
            cameraX = newCameraX,
            completedLines = agedLines,
            activeStrokePoints = emptyList(),
            currentInk = updatedInk,
            chalkBits = nextChalkBits,
            scribblers = nextScribblers,
            activeCallout = null,
            comboCount = 0,
            levelProgress = progress,
            lavaAnimTime = nextLavaTime,
            embers = nextEmbers,
            sketchParticles = nextSketchParticles
          )
        }
      }

      tickResult.reachedFinish || stickmanAfterEnemies.hasReachedFinish -> {
        soundManager.playLevelComplete()
        val unlockedNext = (state.currentLevel + 1).coerceIn(1, LevelCatalog.TOTAL_LEVELS)
        progressRepository.recordLevelReached(unlockedNext)
        val newBest = max(state.bestLevel, unlockedNext).coerceIn(1, LevelCatalog.TOTAL_LEVELS)

        // Compute 3-Star rating (1: Flag reached, 2: Under chalk par, 3: Collected bits/stars)
        var stars = 1
        if (state.totalInkDrawn <= level.parInkUsage) stars++
        val totalBits = nextChalkBits.size
        val gotBits = nextChalkBits.count { it.collected }
        if (totalBits == 0 || gotBits >= max(1, totalBits / 2)) stars++

        progressRepository.recordLevelStars(state.currentLevel, stars)
        val updatedLevelStars = progressRepository.getAllLevelStars()

        updateState {
          it.copy(
            screenState = ScreenState.LEVEL_COMPLETE,
            bestLevel = newBest,
            levelStars = updatedLevelStars,
            stickman = stickmanAfterEnemies,
            cameraX = newCameraX,
            completedLines = agedLines,
            activeStrokePoints = emptyList(),
            currentInk = updatedInk,
            chalkBits = nextChalkBits,
            scribblers = nextScribblers,
            activeCallout = null,
            starsEarned = stars,
            levelProgress = 1f,
            lavaAnimTime = nextLavaTime,
            embers = nextEmbers,
            sketchParticles = nextSketchParticles
          )
        }
      }

      else -> {
        updateState {
          it.copy(
            stickman = stickmanAfterEnemies,
            cameraX = newCameraX,
            completedLines = agedLines,
            currentInk = updatedInk,
            chalkBits = nextChalkBits,
            scribblers = nextScribblers,
            activeCallout = nextCallout,
            triggeredCalloutIndices = nextTriggeredIndices,
            comboCount = nextCombo,
            levelProgress = progress,
            lavaAnimTime = nextLavaTime,
            embers = nextEmbers,
            sketchParticles = nextSketchParticles
          )
        }
      }
    }
  }

  class Factory(
    private val progressRepository: ProgressRepository,
    private val soundManager: SoundEffectManager
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return GameViewModel(progressRepository, soundManager) as T
    }
  }
}
