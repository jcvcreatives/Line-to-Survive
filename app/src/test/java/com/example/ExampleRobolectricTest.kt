package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SoundEffectManager
import com.example.data.ProgressRepository
import com.example.game.GameViewModel
import com.example.game.model.ScreenState
import com.example.game.model.Vec2
import com.example.game.model.WorldConstants
import com.example.ui.DrawToSurviveScreen
import com.example.ui.theme.DrawToSurviveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `app name and start screen launch into gameplay and support pause retry and level completion`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Draw to Survive", context.getString(R.string.app_name))

    val repository = ProgressRepository(context)
    val soundManager = SoundEffectManager(context = context, initialEnabled = true)
    val viewModel = GameViewModel(repository, soundManager)

    composeTestRule.setContent {
      DrawToSurviveTheme {
        DrawToSurviveScreen(viewModel = viewModel, enableFrameLoop = false)
      }
    }

    composeTestRule.waitForIdle()

    // 1. Verify Start Screen with DRAW TO SURVIVE title, Daily Challenge, Bazaar Shop, and Play button
    composeTestRule.onNodeWithTag("start_title_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("daily_challenge_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("shop_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("alchemists_bazaar_modal").assertIsDisplayed()
    composeTestRule.onNodeWithTag("close_shop_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithTag("play_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertEquals(ScreenState.PLAYING, viewModel.uiState.value.screenState)
    assertTrue("Background music should start playing during gameplay", soundManager.isBackgroundMusicPlaying)

    // 2. Verify Gameplay 2x2 Grid HUD elements and "DRAW A PATH TO SURVIVE!" instruction banner
    composeTestRule.onNodeWithTag("level_indicator").assertIsDisplayed()
    composeTestRule.onNodeWithTag("progress_indicator_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("boss_hp_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("rune_inspector_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("score_indicator_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("instruction_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pause_button").assertIsDisplayed()

    // 3. Test Pause and Resume buttons are not blocked and pause/resume background music
    composeTestRule.onNodeWithTag("pause_button").performClick()
    composeTestRule.waitForIdle()
    assertEquals(ScreenState.PAUSED, viewModel.uiState.value.screenState)
    composeTestRule.onNodeWithTag("resume_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertEquals(ScreenState.PLAYING, viewModel.uiState.value.screenState)
    assertTrue(soundManager.isBackgroundMusicPlaying)

    // 4. Simulate falling into lava -> triggers jump/airborne & Game Over sound -> Retry button works
    for (i in 0 until 320) {
      viewModel.stepFrame(1f / 60f, visibleWorldWidth = 900f)
      if (viewModel.uiState.value.screenState == ScreenState.GAME_OVER) break
    }
    composeTestRule.waitForIdle()
    assertEquals(ScreenState.GAME_OVER, viewModel.uiState.value.screenState)
    assertTrue("Jump/airborne sound trigger should fire when leaving platform", soundManager.jumpTriggerCount > 0)
    assertTrue("Game Over sound trigger should fire when hitting lava", soundManager.gameOverTriggerCount > 0)
    composeTestRule.onNodeWithTag("game_over_title_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("retry_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()
    assertEquals(ScreenState.PLAYING, viewModel.uiState.value.screenState)

    // 5. Draw lines across the open platform-free sky through floating chalk -> Complete Level 1 -> Next Level button works
    val y = WorldConstants.DEFAULT_GROUND_Y
    viewModel.onDrawStart(Vec2(500f, y))
    viewModel.onDrawMove(Vec2(720f, y - 24f))
    viewModel.onDrawMove(Vec2(940f, y))
    viewModel.onDrawEnd()

    viewModel.onDrawStart(Vec2(900f, y))
    viewModel.onDrawMove(Vec2(1130f, y - 24f))
    viewModel.onDrawMove(Vec2(1360f, y))
    viewModel.onDrawEnd()

    assertTrue("Drawing sound trigger should fire when sketching lines", soundManager.drawTriggerCount > 0)
    assertEquals(2, viewModel.uiState.value.completedLines.size)

    for (i in 0 until 850) {
      viewModel.stepFrame(1f / 60f, visibleWorldWidth = 900f)
      if (viewModel.uiState.value.screenState == ScreenState.LEVEL_COMPLETE) break
    }
    composeTestRule.waitForIdle()
    assertEquals(ScreenState.LEVEL_COMPLETE, viewModel.uiState.value.screenState)
    assertTrue("Earned stars must be between 1 and 3", viewModel.uiState.value.starsEarned in 1..3)
    composeTestRule.onNodeWithTag("level_complete_title_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("level_complete_stars_row").assertIsDisplayed()
    composeTestRule.onNodeWithTag("level_complete_stars_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("level_complete_ink_efficiency_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("level_complete_collectibles_text").assertIsDisplayed()
    composeTestRule.onNodeWithTag("next_level_button").assertIsDisplayed().performClick()
    composeTestRule.waitForIdle()

    assertEquals(ScreenState.PLAYING, viewModel.uiState.value.screenState)
    assertEquals(2, viewModel.uiState.value.currentLevel)
    assertTrue(repository.getBestLevel() >= 2)
    assertTrue("Completed Level 1 star rating should be persisted", (repository.getAllLevelStars()[1] ?: 0) in 1..3)
    soundManager.release()
  }

  @Test
  fun `ink meter depletes as user draws lines and recharges slowly over time`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repository = ProgressRepository(context)
    val soundManager = SoundEffectManager(context = context, initialEnabled = false)
    val viewModel = GameViewModel(repository, soundManager)

    viewModel.startGame(1)
    val initialInk = viewModel.uiState.value.currentInk
    val maxInk = viewModel.uiState.value.levelDefinition.maxInkPerLevel
    assertEquals(maxInk, initialInk, 0.1f)
    assertEquals(1.0f, viewModel.uiState.value.inkRemainingRatio, 0.01f)
    assertFalse("Ink should not report recharging when full", viewModel.uiState.value.isInkRecharging)

    // Draw line of length 200f
    viewModel.onDrawStart(Vec2(200f, 300f))
    viewModel.onDrawMove(Vec2(400f, 300f))
    val inkAfterDraw = viewModel.uiState.value.currentInk
    assertTrue("Ink should deplete as line is drawn", inkAfterDraw < initialInk)
    assertEquals(initialInk - 200f, inkAfterDraw, 1.0f)

    // Finish stroke
    viewModel.onDrawEnd()
    assertTrue("Ink should report recharging when finger is lifted and ink is below max", viewModel.uiState.value.isInkRecharging)

    // Step frames to simulate time passing (0.5 seconds at 60 FPS)
    val beforeRecharge = viewModel.uiState.value.currentInk
    repeat(30) {
      viewModel.stepFrame(deltaSeconds = 1f / 60f, visibleWorldWidth = 900f)
    }
    val afterRecharge = viewModel.uiState.value.currentInk

    assertTrue("Ink must recharge over time during gameplay", afterRecharge > beforeRecharge)
    val expectedRecharge = 0.5f * viewModel.uiState.value.levelDefinition.inkRechargeRate
    assertEquals(beforeRecharge + expectedRecharge, afterRecharge, 2.0f)
    soundManager.release()
  }
}
