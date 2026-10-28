package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.game.GameViewModel
import com.example.game.model.ScreenState
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import com.example.game.render.SketchbookRenderer.renderGameWorld
import kotlinx.coroutines.isActive

import androidx.compose.runtime.mutableLongStateOf

/**
 * Main interactive screen for Draw to Survive:
 * - Runs a smooth 60 FPS frame loop coupled to Compose [withFrameNanos]
 * - Renders directly in the Draw phase to eliminate recomposition overhead and CPU lag
 * - Captures continuous touch drawing gestures and converts screen points to world space
 * - Layers HUD controls and modal overlays on top so drawing never blocks buttons
 */
@Composable
fun DrawToSurviveScreen(
  viewModel: GameViewModel,
  enableFrameLoop: Boolean = true
) {
  val screenState by viewModel.screenState.collectAsStateWithLifecycle()
  val hudState by viewModel.hudState.collectAsStateWithLifecycle()

  var canvasWidthPx by remember { mutableFloatStateOf(1920f) }
  var canvasHeightPx by remember { mutableFloatStateOf(1080f) }

  val worldScale = (canvasHeightPx / WorldConstants.WORLD_HEIGHT).coerceAtLeast(0.1f)
  val visibleWorldWidth = (canvasWidthPx / worldScale).coerceAtLeast(320f)

  val currentVisibleWidth by rememberUpdatedState(visibleWorldWidth)
  val latestWorldScale by rememberUpdatedState(worldScale)

  // Handle system back button on all secondary screens
  BackHandler(enabled = screenState != ScreenState.START_MENU) {
    when (screenState) {
      ScreenState.PLAYING -> viewModel.pauseGame()
      ScreenState.PAUSED -> viewModel.resumeGame()
      ScreenState.GAME_OVER, ScreenState.LEVEL_COMPLETE -> viewModel.returnToStartMenu()
      ScreenState.START_MENU -> Unit
    }
  }

  // Smooth 60 FPS game update loop. Invalidate only the Canvas draw phase via frameTick.
  var frameTick by remember { mutableLongStateOf(0L) }
  if (enableFrameLoop) {
    LaunchedEffect(Unit) {
      var lastFrameNanos = withFrameNanos { it }
      while (isActive) {
        val frameNanos = withFrameNanos { it }
        val deltaSeconds = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNanos = frameNanos
        viewModel.stepFrame(deltaSeconds = deltaSeconds, visibleWorldWidth = currentVisibleWidth)
        frameTick = frameNanos
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .testTag("game_root_box")
  ) {
    // 1. 2D Game World Canvas + Continuous Touch Drawing Input
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .testTag("game_canvas")
        .onSizeChanged { intSize ->
          if (intSize.width > 0 && intSize.height > 0) {
            canvasWidthPx = intSize.width.toFloat()
            canvasHeightPx = intSize.height.toFloat()
          }
        }
        .pointerInput(screenState) {
          if (screenState != ScreenState.PLAYING) return@pointerInput
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun currentScale(): Float {
              return if (size.height > 0) {
                (size.height.toFloat() / WorldConstants.WORLD_HEIGHT).coerceAtLeast(0.1f)
              } else {
                latestWorldScale
              }
            }
            fun screenToWorld(screenX: Float, screenY: Float): Vec2 {
              val scale = currentScale()
              val camX = viewModel.currentCameraX()
              return Vec2(
                x = camX + screenX / scale,
                y = screenY / scale
              )
            }

            down.consume()
            viewModel.onDrawStart(screenToWorld(down.position.x, down.position.y))

            do {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
              if (change != null && change.pressed) {
                change.consume()
                viewModel.onDrawMove(screenToWorld(change.position.x, change.position.y))
              }
            } while (event.changes.any { it.pressed })

            viewModel.onDrawEnd()
          }
        }
    ) {
      // Access frameTick inside the Canvas draw lambda so Compose invalidates ONLY the Draw phase!
      @Suppress("UNUSED_VARIABLE")
      val tick = frameTick

      val state = viewModel.currentGameState()
      val drawScale = (size.height / WorldConstants.WORLD_HEIGHT).coerceAtLeast(0.1f)
      renderGameWorld(
        level = state.levelDefinition,
        stickman = state.stickman,
        cameraX = state.cameraX,
        completedLines = state.completedLines,
        activeStrokePoints = state.activeStrokePoints,
        selectedTool = state.selectedTool,
        chalkBits = state.chalkBits,
        scribblers = state.scribblers,
        activeCallout = state.activeCallout,
        lavaAnimTime = state.lavaAnimTime,
        embers = state.embers,
        sketchParticles = state.sketchParticles,
        worldScale = drawScale
      )
    }

    // 2. Top Gameplay HUD Bar (only recomposes on throttled integer % changes or button clicks)
    if (screenState == ScreenState.PLAYING || screenState == ScreenState.PAUSED) {
      GameplayHudOverlay(
        hudState = hudState,
        onPauseClick = { viewModel.pauseGame() },
        onClearLinesClick = { viewModel.clearDrawnLines() },
        onToggleSoundClick = { viewModel.toggleSound() },
        onSelectToolClick = { tool -> viewModel.selectChalkTool(tool) },
        modifier = Modifier.align(Alignment.TopCenter)
      )
    }

    // 3. Fullscreen Modal Overlays (Only collected/evaluated when in a non-playing modal state)
    if (screenState != ScreenState.PLAYING) {
      val uiState by viewModel.uiState.collectAsStateWithLifecycle()
      when (screenState) {
        ScreenState.START_MENU -> {
          StartMenuOverlay(
            uiState = uiState,
            onPlayClick = { viewModel.startGame() },
            onSelectLevelClick = { level ->
              if (uiState.currentLevel == level) {
                viewModel.startGame(level)
              } else {
                viewModel.selectMapLevel(level)
              }
            },
            onToggleSoundClick = { viewModel.toggleSound() }
          )
        }

        ScreenState.PAUSED -> {
          PauseModalOverlay(
            onResumeClick = { viewModel.resumeGame() },
            onRetryClick = { viewModel.retryCurrentLevel() },
            onHomeClick = { viewModel.returnToStartMenu() }
          )
        }

        ScreenState.GAME_OVER -> {
          GameOverOverlay(
            uiState = uiState,
            onRetryClick = { viewModel.retryCurrentLevel() },
            onHomeClick = { viewModel.returnToStartMenu() }
          )
        }

        ScreenState.LEVEL_COMPLETE -> {
          LevelCompleteOverlay(
            uiState = uiState,
            onNextLevelClick = { viewModel.startNextLevel() },
            onRetryClick = { viewModel.retryCurrentLevel() },
            onHomeClick = { viewModel.returnToStartMenu() }
          )
        }

        ScreenState.PLAYING -> Unit
      }
    }
  }
}
