package com.example.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffectManager
import com.example.data.ProgressRepository
import com.example.data.local.GameRepository
import com.example.game.level.LevelCatalog
import com.example.game.model.CalloutMove
import com.example.game.model.CalloutMoveType
import com.example.game.model.ChalkTool
import com.example.game.model.DifficultyMode
import com.example.game.model.DrawnLine
import com.example.game.model.GameUiState
import com.example.game.model.GameplayHudState
import com.example.game.model.IgnisTitanBoss
import com.example.game.model.LevelDefinition
import com.example.game.model.MapBiome
import com.example.game.model.RuneType
import com.example.game.model.ScreenState
import com.example.game.model.ShopCategory
import com.example.game.model.ShopItem
import com.example.game.model.StickmanState
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import com.example.game.physics.PhysicsEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Manages the game state, 50-level progression matrix, DifficultyModes, MapBiomes,
 * Pure Line-Drawing & Rune Classification Engine, Colossal Lava Dragon Boss (Ignis Titan),
 * Daily Challenge modifiers, Room Database Alchemist's Bazaar Shop, and 60 FPS simulation.
 */
class GameViewModel(
  private val progressRepository: ProgressRepository,
  private val soundManager: SoundEffectManager,
  private val gameRepository: GameRepository = GameRepository()
) : ViewModel() {

  private var nextLineId = 1L

  private val _uiState = MutableStateFlow(createInitialState())
  val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

  private val _screenState = MutableStateFlow(ScreenState.START_MENU)
  val screenState: StateFlow<ScreenState> = _screenState.asStateFlow()

  private val _hudState = MutableStateFlow(createInitialHudState(_uiState.value))
  val hudState: StateFlow<GameplayHudState> = _hudState.asStateFlow()

  init {
    viewModelScope.launch {
      val (initialPlayer, initialChallenge) = gameRepository.ensureInitialized()
      updateState { state ->
        state.copy(
          currency = initialPlayer.currency,
          equippedSkin = initialPlayer.equippedSkin,
          equippedTrail = initialPlayer.equippedTrail,
          equippedPerk = initialPlayer.equippedPerk,
          unlockedSkins = initialPlayer.unlockedSkins,
          unlockedTrails = initialPlayer.unlockedTrails,
          unlockedPerks = initialPlayer.unlockedPerks,
          dailyChallengeDate = initialChallenge.dateKey,
          dailyChallengeModifier = initialChallenge.modifierName,
          dailyChallengeCompleted = initialChallenge.completed
        )
      }
    }

    viewModelScope.launch {
      gameRepository.observePlayerProfile().collect { player ->
        if (player != null) {
          updateState { state ->
            state.copy(
              currency = player.currency,
              equippedSkin = player.equippedSkin,
              equippedTrail = player.equippedTrail,
              equippedPerk = player.equippedPerk,
              unlockedSkins = player.unlockedSkins,
              unlockedTrails = player.unlockedTrails,
              unlockedPerks = player.unlockedPerks
            )
          }
        }
      }
    }

    viewModelScope.launch {
      gameRepository.observeTodayChallenge().collect { challenge ->
        if (challenge != null) {
          updateState { state ->
            state.copy(
              dailyChallengeDate = challenge.dateKey,
              dailyChallengeModifier = challenge.modifierName,
              dailyChallengeCompleted = challenge.completed
            )
          }
        }
      }
    }
  }

  fun currentGameState(): GameUiState = _uiState.value
  fun currentCameraX(): Float = _uiState.value.cameraX

  private fun createInitialHudState(state: GameUiState): GameplayHudState {
    return GameplayHudState(
      currentLevel = state.currentLevel,
      levelTitle = state.levelDefinition.title,
      levelProgress = state.levelProgress,
      inkRemainingRatio = state.inkRemainingRatio,
      isInkRecharging = state.isInkRecharging,
      soundEnabled = state.soundEnabled,
      selectedTool = state.selectedTool,
      lastRuneType = state.lastRuneType,
      superAuraSeconds = state.stickman.superAuraTimer,
      hasTitanShield = state.stickman.hasTitanShield,
      bossHp = state.ignisBoss.hp,
      bossMaxHp = state.ignisBoss.maxHp,
      score = state.score,
      currency = state.currency,
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
    val curAuraTenths = (cur.superAuraSeconds * 10f).toInt()
    val nextAuraTenths = (state.stickman.superAuraTimer * 10f).toInt()
    val hasLines = state.completedLines.isNotEmpty()

    if (cur.currentLevel != state.currentLevel ||
      cur.selectedTool != state.selectedTool ||
      cur.lastRuneType != state.lastRuneType ||
      curAuraTenths != nextAuraTenths ||
      cur.hasTitanShield != state.stickman.hasTitanShield ||
      cur.bossHp != state.ignisBoss.hp ||
      cur.bossMaxHp != state.ignisBoss.maxHp ||
      cur.score != state.score ||
      cur.currency != state.currency ||
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
      _hudState.value = GameplayHudState(
        currentLevel = state.currentLevel,
        levelTitle = state.levelDefinition.title,
        levelProgress = state.levelProgress,
        inkRemainingRatio = state.inkRemainingRatio,
        isInkRecharging = state.isInkRecharging,
        soundEnabled = state.soundEnabled,
        selectedTool = state.selectedTool,
        lastRuneType = state.lastRuneType,
        superAuraSeconds = state.stickman.superAuraTimer,
        hasTitanShield = state.stickman.hasTitanShield,
        bossHp = state.ignisBoss.hp,
        bossMaxHp = state.ignisBoss.maxHp,
        score = state.score,
        currency = state.currency,
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
    val defaultDifficulty = DifficultyMode.NORMAL
    val defaultMap = MapBiome.MAP_1_VOLCANO
    val level1 = LevelCatalog.getLevel(1, defaultDifficulty, defaultMap)
    val todayKey = gameRepository.currentUtcDateKey()
    val todayChallenge = gameRepository.createDefaultDailyChallenge(todayKey)
    return GameUiState(
      screenState = ScreenState.START_MENU,
      currentLevel = 1,
      bestLevel = bestLevel,
      levelStars = savedStars,
      selectedDifficulty = defaultDifficulty,
      selectedMap = defaultMap,
      levelDefinition = level1,
      stickman = createInitialStickman(level1, equippedPerk = ""),
      cameraX = 0f,
      selectedTool = ChalkTool.WHITE,
      currentInk = level1.maxInkPerLevel,
      chalkBits = level1.chalkBits,
      scribblers = level1.scribblers,
      rockWalls = level1.rockWalls,
      lavaMonsters = level1.lavaMonsters,
      ignisBoss = createInitialBoss(level1),
      soundEnabled = soundOn,
      showInstructionBanner = true,
      dailyChallengeDate = todayChallenge.dateKey,
      dailyChallengeModifier = todayChallenge.modifierName,
      dailyChallengeCompleted = todayChallenge.completed
    )
  }

  private fun createInitialStickman(level: LevelDefinition, equippedPerk: String): StickmanState {
    return StickmanState(
      footX = level.spawnPosition.x,
      footY = level.spawnPosition.y,
      velocityX = level.runSpeed,
      velocityY = 0f,
      isGrounded = true,
      hasTitanShield = (equippedPerk == "TITAN_SHIELD")
    )
  }

  private fun createInitialBoss(level: LevelDefinition): IgnisTitanBoss {
    return IgnisTitanBoss(
      x = level.spawnPosition.x - 245f,
      y = level.spawnPosition.y - 36f,
      hp = level.bossMaxHp,
      maxHp = level.bossMaxHp
    )
  }

  fun openShop() {
    soundManager.playButtonClick()
    updateState { it.copy(showShopModal = true) }
  }

  fun closeShop() {
    soundManager.playButtonClick()
    updateState { it.copy(showShopModal = false) }
  }

  fun buyOrEquipShopItem(item: ShopItem) {
    soundManager.playButtonClick()
    viewModelScope.launch {
      when (item.category) {
        ShopCategory.SKIN -> gameRepository.purchaseOrEquipSkin(item.id, item.cost)
        ShopCategory.TRAIL -> gameRepository.purchaseOrEquipTrail(item.id, item.cost)
        ShopCategory.PERK -> gameRepository.purchaseOrEquipPerk(item.id, item.cost)
      }
    }
  }

  fun startDailyChallenge() {
    soundManager.playButtonClick()
    val cur = _uiState.value
    val dailyLevelIndex = (( kotlin.math.abs(cur.dailyChallengeDate.hashCode()) % 40) + 10).coerceIn(5, 50)
    loadLevel(
      levelNumber = dailyLevelIndex,
      targetScreenState = ScreenState.PLAYING,
      difficulty = DifficultyMode.HARD,
      mapBiome = cur.selectedMap,
      isDailyChallenge = true
    )
  }

  fun selectChalkTool(tool: ChalkTool) {
    soundManager.playButtonClick()
    if (_uiState.value.activeStrokePoints.size >= 2) {
      commitActiveStrokeIfValid()
    }
    updateState { it.copy(selectedTool = tool, activeStrokePoints = emptyList()) }
  }

  fun selectDifficulty(difficulty: DifficultyMode) {
    soundManager.playButtonClick()
    val cur = _uiState.value
    loadLevel(
      levelNumber = cur.currentLevel,
      targetScreenState = cur.screenState,
      difficulty = difficulty,
      mapBiome = cur.selectedMap
    )
  }

  fun selectMapBiome(mapBiome: MapBiome) {
    soundManager.playButtonClick()
    val cur = _uiState.value
    loadLevel(
      levelNumber = cur.currentLevel,
      targetScreenState = cur.screenState,
      difficulty = cur.selectedDifficulty,
      mapBiome = mapBiome
    )
  }

  /**
   * Allows selecting any level (1..50) from the Main Menu so the user can test any level immediately.
   */
  fun selectMapLevel(levelNumber: Int) {
    val clamped = levelNumber.coerceIn(1, LevelCatalog.TOTAL_LEVELS)
    soundManager.playButtonClick()
    loadLevel(clamped, targetScreenState = ScreenState.START_MENU)
  }

  fun startGame(levelNumber: Int = _uiState.value.currentLevel) {
    soundManager.playButtonClick()
    loadLevel(levelNumber.coerceIn(1, LevelCatalog.TOTAL_LEVELS), targetScreenState = ScreenState.PLAYING)
  }

  fun retryCurrentLevel() {
    soundManager.playButtonClick()
    val cur = _uiState.value
    loadLevel(
      levelNumber = cur.currentLevel,
      targetScreenState = ScreenState.PLAYING,
      difficulty = cur.selectedDifficulty,
      mapBiome = cur.selectedMap,
      isDailyChallenge = cur.isDailyChallengeRun
    )
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

  private fun loadLevel(
    levelNumber: Int,
    targetScreenState: ScreenState,
    difficulty: DifficultyMode = _uiState.value.selectedDifficulty,
    mapBiome: MapBiome = _uiState.value.selectedMap,
    isDailyChallenge: Boolean = false
  ) {
    val validLevel = levelNumber.coerceIn(1, LevelCatalog.TOTAL_LEVELS)
    val rawLevelDef = LevelCatalog.getLevel(validLevel, difficulty, mapBiome)
    val modifierName = if (isDailyChallenge) _uiState.value.dailyChallengeModifier else null
    val levelDef = when (modifierName) {
      "Glass Cannon" -> rawLevelDef.copy(
        title = "DAILY: GLASS CANNON",
        maxInkPerLevel = rawLevelDef.maxInkPerLevel * 0.75f,
        scoreMultiplier = rawLevelDef.scoreMultiplier * 2.0f,
        dailyModifier = modifierName
      )
      "Zero Gravity Ink" -> rawLevelDef.copy(
        title = "DAILY: ZERO GRAV INK",
        mapBiome = MapBiome.MAP_2_CRYO_LAB,
        dailyModifier = modifierName
      )
      "Turbo Ignis" -> rawLevelDef.copy(
        title = "DAILY: TURBO IGNIS",
        runSpeed = rawLevelDef.runSpeed * 1.15f,
        dailyModifier = modifierName
      )
      "Thick Fog" -> rawLevelDef.copy(
        title = "DAILY: THICK FOG",
        dailyModifier = modifierName
      )
      else -> rawLevelDef
    }

    val savedBest = progressRepository.getBestLevel().coerceIn(1, LevelCatalog.TOTAL_LEVELS)
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
        bestLevel = savedBest,
        levelStars = savedStars,
        selectedDifficulty = difficulty,
        selectedMap = mapBiome,
        levelDefinition = levelDef,
        stickman = createInitialStickman(levelDef, state.equippedPerk),
        cameraX = 0f,
        selectedTool = ChalkTool.WHITE,
        lastRuneType = RuneType.STANDARD_INK,
        completedLines = emptyList(),
        activeStrokePoints = emptyList(),
        activeStrokeRemainingRatio = 1f,
        currentInk = levelDef.maxInkPerLevel,
        totalInkDrawn = 0f,
        chalkBits = levelDef.chalkBits,
        scribblers = levelDef.scribblers,
        rockWalls = levelDef.rockWalls,
        lavaMonsters = levelDef.lavaMonsters,
        ignisBoss = createInitialBoss(levelDef),
        bossFlawless = true,
        activeCallout = null,
        triggeredCalloutIndices = emptySet(),
        comboCount = 0,
        score = 0,
        starsEarned = 3,
        levelProgress = 0f,
        sketchParticles = emptyList(),
        showInstructionBanner = true,
        isDailyChallengeRun = isDailyChallenge,
        showShopModal = false
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

    if (PhysicsEngine.isPointBlockedByNoDrawZone(clamped, state.selectedTool, state.levelDefinition.hazards)) {
      soundManager.playButtonClick()
      return
    }

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
        score = it.score + collectedAtStart.size * 15,
        sketchParticles = (it.sketchParticles + tipParticles).takeLast(45)
      )
    }
  }

  /**
   * Appends a dragged finger point in world coordinates.
   */
  fun onDrawMove(worldPoint: Vec2) {
    val state = _uiState.value
    if (state.screenState != ScreenState.PLAYING) return

    if (state.selectedTool == ChalkTool.CUTTER) {
      performCutAndShockwave(worldPoint)
      return
    }

    if (state.activeStrokePoints.isEmpty()) return

    val catalystDiscount = if (state.equippedPerk == "REFINED_CATALYST") 0.82f else 1.0f
    val toolCostMult = (state.selectedTool.inkCostMultiplier.coerceAtLeast(1f)) *
      state.selectedDifficulty.inkDrainMult * catalystDiscount
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
      availableInkForRemaining = maxAffordableDistance,
      tool = state.selectedTool,
      hazards = state.levelDefinition.hazards
    )

    val addedNewPoint = updatedPoints.size > state.activeStrokePoints.size
    val prevStrokeLen = PhysicsEngine.calculatePolylineLength(state.activeStrokePoints)
    val strokeLen = PhysicsEngine.calculatePolylineLength(updatedPoints)
    val deltaLen = (strokeLen - prevStrokeLen).coerceAtLeast(0f)
    val deltaInk = deltaLen * toolCostMult

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
        score = it.score + newlyCollectedBits.size * 15,
        sketchParticles = (it.sketchParticles + tipParticles).takeLast(45)
      )
    }

    if ((strokeLen >= maxStrokeLen - 1f || newInk <= 1f) && updatedPoints.size >= 2) {
      commitActiveStrokeIfValid()
    }
  }

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
    val classification = PhysicsEngine.classifyRuneStroke(
      points = smoothedPoints,
      stickman = state.stickman,
      selectedTool = state.selectedTool
    )

    val effectiveTool = classification.effectiveTool
    val lifetime = if (effectiveTool == ChalkTool.BOOST) {
      9999f
    } else {
      state.levelDefinition.lineLifespan
    }

    val newLine = DrawnLine(
      id = nextLineId++,
      points = smoothedPoints,
      isCompleted = true,
      tool = effectiveTool,
      runeType = classification.runeType,
      ageSeconds = 0f,
      maxLifetime = lifetime
    )

    val lineBurstParticles = PhysicsEngine.createLineCompletedParticles(smoothedPoints)
    val inkRefund = state.levelDefinition.maxInkPerLevel * classification.inkRefundRatio

    if (classification.triggersImmediateBounce) {
      soundManager.playJump()
    } else {
      soundManager.playLineCompleted()
    }

    updateState { current ->
      var nextStickman = current.stickman
      if (classification.triggersSuperAura) {
        nextStickman = nextStickman.copy(
          superAuraTimer = 5.0f,
          boostTimer = max(nextStickman.boostTimer, 1.5f)
        )
      }
      if (classification.triggersImmediateBounce) {
        nextStickman = nextStickman.copy(
          velocityY = -540f,
          isGrounded = false
        )
      }
      current.copy(
        stickman = nextStickman,
        lastRuneType = classification.runeType,
        completedLines = current.completedLines + newLine,
        activeStrokePoints = emptyList(),
        activeStrokeRemainingRatio = 1f,
        currentInk = (current.currentInk + inkRefund).coerceAtMost(current.levelDefinition.maxInkPerLevel * 1.5f),
        sketchParticles = (current.sketchParticles + lineBurstParticles).takeLast(30),
        showInstructionBanner = false
      )
    }
  }

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
    var agedLines = state.completedLines.mapNotNull { line ->
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
            agedLines = agedLines.mapNotNull { line ->
              if (line.tool == ChalkTool.BOOST) line
              else {
                val acceleratedAge = line.ageSeconds + 1.4f
                if (acceleratedAge >= line.maxLifetime) null else line.copy(ageSeconds = acceleratedAge)
              }
            }
          }
        }
        nextCallout = null
      } else {
        nextCallout = nextCallout.copy(remainingSeconds = rem)
      }
    }

    // 3. Step stickman physics (includes continuous swept obstacle hitboxes)
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
    val (nextScribblers, stickmanAfterScribblers) = PhysicsEngine.stepScribblers(
      scribblers = state.scribblers,
      stickman = tickResult.stickman,
      platforms = level.platforms,
      completedLines = activeLines,
      dt = clampedDt
    )

    // 4b. Step Ignis Titan Boss, Breakable Rock Walls & Leaping Lava Monsters
    val combatResult = PhysicsEngine.stepCombatEntities(
      stickman = stickmanAfterScribblers,
      level = level,
      rockWalls = state.rockWalls,
      lavaMonsters = state.lavaMonsters,
      ignisBoss = state.ignisBoss,
      animTime = state.lavaAnimTime,
      dt = clampedDt
    )
    val stickmanAfterEnemies = combatResult.stickman
    if (combatResult.bossTookDamage || combatResult.wallSmashed || combatResult.monsterBlasted) {
      soundManager.playLineCompleted()
    }

    // 5. Update Chalk Bits & Magnet Combo collection
    val (nextChalkBits, newlyCollectedBits) = PhysicsEngine.updateChalkBits(
      bits = state.chalkBits,
      stickman = stickmanAfterEnemies,
      comboCount = state.comboCount,
      dt = clampedDt,
      activeLines = activeLines,
      hasInkMagnetPerk = state.equippedPerk == "INK_MAGNET"
    )

    val collectedInkBonus = newlyCollectedBits.sumOf { it.inkAmount.toDouble() }.toFloat() + combatResult.inkDelta
    val nextCombo = if (newlyCollectedBits.isNotEmpty()) {
      soundManager.playLineCompleted()
      state.comboCount + newlyCollectedBits.size
    } else {
      state.comboCount
    }

    // 6. Ink recharges slowly over time while not actively drawing (Milestone 40: -40%, Milestone 50: 0 natural regen)
    val milestoneRegenMult = when (level.levelNumber) {
      50 -> 0f
      40 -> 0.6f
      else -> 1f
    }
    val comboBonusMult = 1f + min(nextCombo, 5) * 0.08f
    val currentInk = state.currentInk
    val inkRecharge = if (state.activeStrokePoints.isEmpty() && currentInk < level.maxInkPerLevel) {
      level.inkRechargeRate * comboBonusMult * milestoneRegenMult * clampedDt
    } else {
      0f
    }
    val maxInkCap = if (collectedInkBonus > 0f || currentInk > level.maxInkPerLevel) {
      level.maxInkPerLevel * 1.5f
    } else {
      level.maxInkPerLevel
    }
    val updatedInk = (currentInk + inkRecharge + collectedInkBonus).coerceIn(0f, maxInkCap)

    val nextSketchParticles = (PhysicsEngine.updateSketchParticles(
      existing = state.sketchParticles,
      stickman = stickmanAfterEnemies,
      justLanded = tickResult.justLanded,
      dt = clampedDt,
      emitFootsteps = true
    ) + combatResult.extraParticles).takeLast(120)

    val desiredCameraX = (stickmanAfterEnemies.footX - visibleWorldWidth * WorldConstants.CAMERA_LEAD_RATIO)
      .coerceIn(0f, (level.levelEndX - visibleWorldWidth * 0.5f).coerceAtLeast(0f))

    val newCameraX = state.cameraX + (desiredCameraX - state.cameraX) * 0.25f

    val totalRunDistance = (level.finishLineX - level.spawnPosition.x).coerceAtLeast(1f)
    val coveredDistance = (stickmanAfterEnemies.footX - level.spawnPosition.x).coerceAtLeast(0f)
    val progress = (coveredDistance / totalRunDistance).coerceIn(0f, 1f)
    val nextScore = state.score + combatResult.scoreDelta + newlyCollectedBits.size * 15
    val nextBossFlawless = state.bossFlawless && !combatResult.playerTookBossBite

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
            rockWalls = combatResult.rockWalls,
            lavaMonsters = combatResult.lavaMonsters,
            ignisBoss = combatResult.ignisBoss,
            bossFlawless = nextBossFlawless,
            activeCallout = null,
            comboCount = 0,
            score = nextScore,
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

        var stars = 1
        if (state.totalInkDrawn <= level.parInkUsage) stars++
        val totalBits = nextChalkBits.size
        val gotBits = nextChalkBits.count { it.collected }
        val targetBits = if (totalBits == 0) 0 else max(1, (totalBits + 1) / 2)
        if ((totalBits == 0 || gotBits >= targetBits) && nextBossFlawless) stars++

        progressRepository.recordLevelStars(state.currentLevel, stars)
        val updatedLevelStars = progressRepository.getAllLevelStars()

        val levelCurrencyReward = stars * 120 + (if (combatResult.ignisBoss.isDefeated) 250 else 0)
        viewModelScope.launch {
          if (state.isDailyChallengeRun) {
            gameRepository.markDailyChallengeCompleted()
          }
          gameRepository.addCurrency(levelCurrencyReward)
        }

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
            rockWalls = combatResult.rockWalls,
            lavaMonsters = combatResult.lavaMonsters,
            ignisBoss = combatResult.ignisBoss,
            bossFlawless = nextBossFlawless,
            activeCallout = null,
            score = nextScore + stars * 150,
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
            rockWalls = combatResult.rockWalls,
            lavaMonsters = combatResult.lavaMonsters,
            ignisBoss = combatResult.ignisBoss,
            bossFlawless = nextBossFlawless,
            activeCallout = nextCallout,
            triggeredCalloutIndices = nextTriggeredIndices,
            comboCount = nextCombo,
            score = nextScore,
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
    private val soundManager: SoundEffectManager,
    private val gameRepository: GameRepository = GameRepository()
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return GameViewModel(progressRepository, soundManager, gameRepository) as T
    }
  }
}
