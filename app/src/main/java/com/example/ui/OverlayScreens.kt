package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.game.model.ChalkTool
import com.example.game.model.GameUiState
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.FinishBannerGreen
import com.example.ui.theme.InkCyanAccent
import com.example.ui.theme.LavaDeepRed
import com.example.ui.theme.LavaFlameYellow
import com.example.ui.theme.LavaOrange
import com.example.ui.theme.PencilGraphite
import com.example.ui.theme.SketchCharcoalBg
import com.example.ui.theme.SketchPaperCard
import com.example.ui.theme.SketchPaperSurface
import kotlin.math.roundToInt

import com.example.game.model.GameplayHudState

/**
 * Top HUD bar displayed during active gameplay:
 * - Level indicator & title
 * - Level progress bar & percentage
 * - Remaining pencil ink meter
 * - Clear Drawn Lines button, Sound toggle button, and Pause button
 * - Floating "DRAW A PATH TO SURVIVE!" instruction banner
 */
@Composable
fun GameplayHudOverlay(
  hudState: GameplayHudState,
  onPauseClick: () -> Unit,
  onClearLinesClick: () -> Unit,
  onToggleSoundClick: () -> Unit,
  onSelectToolClick: (ChalkTool) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top control bar
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("gameplay_hud_bar"),
      color = SketchPaperSurface.copy(alpha = 0.90f),
      shape = RoundedCornerShape(18.dp),
      border = BorderStroke(1.5.dp, ChalkWhite.copy(alpha = 0.25f)),
      tonalElevation = 6.dp
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        // Left: Level badge
        Column(
          modifier = Modifier.testTag("level_indicator")
        ) {
          Text(
            text = stringResource(R.string.label_level, hudState.currentLevel),
            style = MaterialTheme.typography.titleLarge,
            color = LavaFlameYellow
          )
          Text(
            text = hudState.levelTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = PencilGraphite
          )
        }

        // Center: Progress indicator & Ink bar
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          val pct = (hudState.levelProgress * 100f).roundToInt().coerceIn(0, 100)
          val inkPct = (hudState.inkRemainingRatio * 100).roundToInt().coerceIn(0, 100)
          val inkColor = when {
            hudState.inkRemainingRatio <= 0.15f -> LavaDeepRed
            hudState.inkRemainingRatio <= 0.35f -> LavaOrange
            hudState.inkRemainingRatio <= 0.60f -> LavaFlameYellow
            else -> InkCyanAccent
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(R.string.label_progress, pct),
              style = MaterialTheme.typography.bodyMedium,
              color = ChalkWhite,
              modifier = Modifier.testTag("progress_indicator_text")
            )
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "${stringResource(R.string.label_ink_meter)}: $inkPct%",
                style = MaterialTheme.typography.bodyMedium,
                color = inkColor,
                modifier = Modifier.testTag("ink_indicator_text")
              )
              if (hudState.isInkRecharging && hudState.inkRemainingRatio < 0.98f) {
                Text(
                  text = "(+⚡)",
                  style = MaterialTheme.typography.bodySmall,
                  color = LavaFlameYellow
                )
              }
            }
          }

          LinearProgressIndicator(
            progress = { hudState.levelProgress },
            modifier = Modifier
              .fillMaxWidth()
              .height(8.dp)
              .clip(RoundedCornerShape(4.dp))
              .testTag("level_progress_bar"),
            color = FinishBannerGreen,
            trackColor = SketchCharcoalBg
          )

          LinearProgressIndicator(
            progress = { hudState.inkRemainingRatio },
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
              .testTag("ink_progress_bar"),
            color = inkColor,
            trackColor = SketchCharcoalBg
          )
        }

        // Right: Action buttons (Clear Ink, Sound, Pause)
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilledIconButton(
            onClick = onClearLinesClick,
            modifier = Modifier
              .size(48.dp)
              .testTag("clear_lines_button"),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = SketchPaperCard,
              contentColor = ChalkWhite
            )
          ) {
            Icon(
              imageVector = Icons.Default.DeleteSweep,
              contentDescription = stringResource(R.string.action_clear_lines)
            )
          }

          FilledIconButton(
            onClick = onToggleSoundClick,
            modifier = Modifier
              .size(48.dp)
              .testTag("sound_toggle_button"),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = SketchPaperCard,
              contentColor = ChalkWhite
            )
          ) {
            Icon(
              imageVector = if (hudState.soundEnabled) {
                Icons.AutoMirrored.Filled.VolumeUp
              } else {
                Icons.AutoMirrored.Filled.VolumeOff
              },
              contentDescription = stringResource(R.string.action_sound_toggle)
            )
          }

          FilledIconButton(
            onClick = onPauseClick,
            modifier = Modifier
              .size(48.dp)
              .testTag("pause_button"),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = LavaOrange,
              contentColor = ChalkWhite
            )
          ) {
            Icon(
              imageVector = Icons.Default.Pause,
              contentDescription = stringResource(R.string.action_pause)
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Multi-Chalk Tool Switcher & Chalk Bits / Combo Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("chalk_palette_bar"),
      horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically
    ) {
      for (tool in ChalkTool.entries) {
        val isSelected = hudState.selectedTool == tool
        val chipColor = when (tool) {
          ChalkTool.WHITE -> ChalkWhite
          ChalkTool.SPRING -> InkCyanAccent
          ChalkTool.BOOST -> LavaFlameYellow
          ChalkTool.CUTTER -> LavaOrange
        }
        Surface(
          onClick = { onSelectToolClick(tool) },
          modifier = Modifier.testTag("tool_chip_${tool.name}"),
          shape = RoundedCornerShape(50),
          color = if (isSelected) chipColor.copy(alpha = 0.25f) else SketchCharcoalBg.copy(alpha = 0.85f),
          border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) chipColor else ChalkWhite.copy(alpha = 0.3f))
        ) {
          Text(
            text = tool.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) chipColor else ChalkWhite.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }

      if (hudState.totalBitsInLevel > 0) {
        Surface(
          shape = RoundedCornerShape(50),
          color = SketchCharcoalBg.copy(alpha = 0.88f),
          border = BorderStroke(1.dp, LavaFlameYellow.copy(alpha = 0.5f))
        ) {
          Text(
            text = "✎ ${hudState.bitsCollectedCount}/${hudState.totalBitsInLevel}" +
              if (hudState.comboCount >= 2) " (x${hudState.comboCount} MAGNET)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = LavaFlameYellow,
            modifier = Modifier
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("bits_indicator_text")
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Active Callout Move Bubble Banner or Instruction Banner
    val callout = hudState.activeCallout
    if (callout != null) {
      Surface(
        modifier = Modifier.testTag("callout_banner"),
        color = SketchCharcoalBg.copy(alpha = 0.92f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(2.dp, LavaFlameYellow)
      ) {
        Text(
          text = "⚡ ${callout.type.label} INCOMING!",
          style = MaterialTheme.typography.titleMedium,
          color = LavaFlameYellow,
          modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
        )
      }
    } else {
      AnimatedVisibility(
        visible = hudState.showInstructionBanner || !hudState.hasCompletedLines,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Surface(
          modifier = Modifier.testTag("instruction_banner"),
          color = SketchCharcoalBg.copy(alpha = 0.86f),
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.5.dp, LavaFlameYellow.copy(alpha = 0.85f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Edit,
              contentDescription = null,
              tint = LavaFlameYellow,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = stringResource(R.string.instruction_draw_path),
              style = MaterialTheme.typography.titleMedium,
              color = ChalkWhite
            )
          }
        }
      }
    }
  }
}

@Composable
fun GameplayHudOverlay(
  uiState: GameUiState,
  onPauseClick: () -> Unit,
  onClearLinesClick: () -> Unit,
  onToggleSoundClick: () -> Unit,
  onSelectToolClick: (ChalkTool) -> Unit = {},
  modifier: Modifier = Modifier
) {
  GameplayHudOverlay(
    hudState = GameplayHudState(
      currentLevel = uiState.currentLevel,
      levelTitle = uiState.levelDefinition.title,
      levelProgress = uiState.levelProgress,
      inkRemainingRatio = uiState.inkRemainingRatio,
      isInkRecharging = uiState.isInkRecharging,
      soundEnabled = uiState.soundEnabled,
      selectedTool = uiState.selectedTool,
      totalBitsInLevel = uiState.totalBitsInLevel,
      bitsCollectedCount = uiState.bitsCollectedCount,
      comboCount = uiState.comboCount,
      activeCallout = uiState.activeCallout,
      hasCompletedLines = uiState.completedLines.isNotEmpty(),
      showInstructionBanner = uiState.showInstructionBanner
    ),
    onPauseClick = onPauseClick,
    onClearLinesClick = onClearLinesClick,
    onToggleSoundClick = onToggleSoundClick,
    onSelectToolClick = onSelectToolClick,
    modifier = modifier
  )
}

/**
 * Start screen with the title DRAW TO SURVIVE, 50-Level Candy Crush Saga Map, scaling stats inspector, and Play button.
 */
@Composable
fun StartMenuOverlay(
  uiState: GameUiState,
  onPlayClick: () -> Unit,
  onSelectLevelClick: (Int) -> Unit,
  onToggleSoundClick: () -> Unit
) {
  val selectedConfig = com.example.game.level.LevelCatalog.getLevelConfig(uiState.currentLevel)
  val selectedStars = uiState.levelStars[uiState.currentLevel] ?: if (uiState.currentLevel < uiState.bestLevel) 3 else 0
  val isSelectedCompleted = (uiState.currentLevel < uiState.bestLevel) || (selectedStars > 0)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SketchCharcoalBg.copy(alpha = 0.82f))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 620.dp)
        .border(2.5.dp, ChalkWhite, RoundedCornerShape(26.dp))
        .testTag("start_screen_card"),
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = SketchPaperSurface.copy(alpha = 0.97f)),
      elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Top Header: Title + Best Level & Stars + Sound Toggle
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Whatshot,
              contentDescription = null,
              tint = LavaOrange,
              modifier = Modifier.size(28.dp)
            )
            Text(
              text = stringResource(R.string.title_draw_to_survive),
              style = MaterialTheme.typography.headlineLarge,
              color = ChalkWhite,
              modifier = Modifier.testTag("start_title_text")
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              color = SketchPaperCard,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, LavaFlameYellow.copy(alpha = 0.55f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.EmojiEvents,
                  contentDescription = null,
                  tint = LavaFlameYellow,
                  modifier = Modifier.size(18.dp)
                )
                Text(
                  text = "${stringResource(R.string.label_best_level, uiState.bestLevel)} • ★ ${uiState.totalStarsEarned}/150",
                  style = MaterialTheme.typography.labelLarge,
                  color = LavaFlameYellow,
                  modifier = Modifier.testTag("best_level_text")
                )
              }
            }

            OutlinedButton(
              onClick = onToggleSoundClick,
              modifier = Modifier
                .height(40.dp)
                .testTag("start_sound_toggle_button"),
              shape = CircleShape,
              border = BorderStroke(1.dp, PencilGraphite.copy(alpha = 0.6f))
            ) {
              Icon(
                imageVector = if (uiState.soundEnabled) {
                  Icons.AutoMirrored.Filled.VolumeUp
                } else {
                  Icons.AutoMirrored.Filled.VolumeOff
                },
                contentDescription = stringResource(R.string.action_sound_toggle),
                tint = ChalkWhite,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Candy Crush-Style 50-Level Winding Saga Map
        CandyCrushSagaMap(
          selectedLevel = uiState.currentLevel,
          bestLevel = uiState.bestLevel,
          levelStars = uiState.levelStars,
          onSelectLevel = onSelectLevelClick,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .height(230.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Selected Level Scaling Formula Summary
        SelectedLevelConfigBanner(
          config = selectedConfig,
          isCompleted = isSelectedCompleted,
          stars = selectedStars
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Large Play Button
        Button(
          onClick = onPlayClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("play_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = LavaOrange,
            contentColor = ChalkWhite
          )
        ) {
          Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(26.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "${stringResource(R.string.action_play)} (LVL ${uiState.currentLevel})",
            style = MaterialTheme.typography.headlineSmall
          )
        }
      }
    }
  }
}

/**
 * Pause overlay screen with Resume, Retry, and Main Menu buttons.
 */
@Composable
fun PauseModalOverlay(
  onResumeClick: () -> Unit,
  onRetryClick: () -> Unit,
  onHomeClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SketchCharcoalBg.copy(alpha = 0.80f))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 460.dp)
        .border(2.dp, ChalkWhite, RoundedCornerShape(24.dp))
        .testTag("pause_modal_card"),
      shape = RoundedCornerShape(24.dp),
      colors = CardDefaults.cardColors(containerColor = SketchPaperSurface)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text(
          text = stringResource(R.string.paused_title),
          style = MaterialTheme.typography.headlineLarge,
          color = LavaFlameYellow,
          modifier = Modifier.testTag("paused_title_text")
        )

        Text(
          text = stringResource(R.string.paused_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = PencilGraphite,
          textAlign = TextAlign.Center
        )

        Button(
          onClick = onResumeClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("resume_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = FinishBannerGreen,
            contentColor = SketchCharcoalBg
          )
        ) {
          Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_resume),
            style = MaterialTheme.typography.titleLarge
          )
        }

        Button(
          onClick = onRetryClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("pause_retry_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = LavaOrange,
            contentColor = ChalkWhite
          )
        ) {
          Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_retry),
            style = MaterialTheme.typography.titleLarge
          )
        }

        OutlinedButton(
          onClick = onHomeClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("pause_home_button"),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.5.dp, PencilGraphite)
        ) {
          Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = ChalkWhite)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_home),
            style = MaterialTheme.typography.titleMedium,
            color = ChalkWhite
          )
        }
      }
    }
  }
}

/**
 * Game Over screen when the stickman falls into the lava pit, featuring a large Retry button.
 */
@Composable
fun GameOverOverlay(
  uiState: GameUiState,
  onRetryClick: () -> Unit,
  onHomeClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SketchCharcoalBg.copy(alpha = 0.84f))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 460.dp)
        .border(2.5.dp, LavaOrange, RoundedCornerShape(26.dp))
        .testTag("game_over_card"),
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = SketchPaperSurface)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Whatshot,
          contentDescription = null,
          tint = LavaDeepRed,
          modifier = Modifier.size(48.dp)
        )

        Text(
          text = stringResource(R.string.game_over_title),
          style = MaterialTheme.typography.displayMedium,
          color = LavaOrange,
          modifier = Modifier.testTag("game_over_title_text")
        )

        Text(
          text = stringResource(R.string.game_over_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = ChalkWhite,
          textAlign = TextAlign.Center
        )

        val pct = (uiState.levelProgress * 100f).roundToInt().coerceIn(0, 100)
        Text(
          text = stringResource(R.string.label_progress, pct),
          style = MaterialTheme.typography.titleMedium,
          color = LavaFlameYellow
        )

        Button(
          onClick = onRetryClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("retry_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = LavaOrange,
            contentColor = ChalkWhite
          )
        ) {
          Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.action_retry),
            style = MaterialTheme.typography.headlineMedium
          )
        }

        OutlinedButton(
          onClick = onHomeClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("game_over_home_button"),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.5.dp, PencilGraphite)
        ) {
          Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = ChalkWhite)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.action_home),
            style = MaterialTheme.typography.titleMedium,
            color = ChalkWhite
          )
        }
      }
    }
  }
}

/**
 * Level Complete screen with Next Level and Replay buttons.
 */
@Composable
fun LevelCompleteOverlay(
  uiState: GameUiState,
  onNextLevelClick: () -> Unit,
  onRetryClick: () -> Unit,
  onHomeClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SketchCharcoalBg.copy(alpha = 0.82f))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 480.dp)
        .border(2.5.dp, FinishBannerGreen, RoundedCornerShape(26.dp))
        .testTag("level_complete_card"),
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = SketchPaperSurface)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Icon(
          imageVector = Icons.Default.EmojiEvents,
          contentDescription = null,
          tint = LavaFlameYellow,
          modifier = Modifier.size(52.dp)
        )

        Text(
          text = stringResource(R.string.level_complete_title),
          style = MaterialTheme.typography.displayMedium,
          color = FinishBannerGreen,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("level_complete_title_text")
        )

        Text(
          text = stringResource(R.string.level_complete_subtitle),
          style = MaterialTheme.typography.bodyLarge,
          color = ChalkWhite,
          textAlign = TextAlign.Center
        )

        Button(
          onClick = onNextLevelClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .testTag("next_level_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = FinishBannerGreen,
            contentColor = SketchCharcoalBg
          )
        ) {
          Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = stringResource(R.string.action_next_level),
            style = MaterialTheme.typography.headlineMedium
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          OutlinedButton(
            onClick = onRetryClick,
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("complete_retry_button"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, PencilGraphite)
          ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = ChalkWhite)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.action_retry),
              style = MaterialTheme.typography.titleMedium,
              color = ChalkWhite
            )
          }

          OutlinedButton(
            onClick = onHomeClick,
            modifier = Modifier
              .weight(1f)
              .height(50.dp)
              .testTag("complete_home_button"),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, PencilGraphite)
          ) {
            Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = ChalkWhite)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.action_home),
              style = MaterialTheme.typography.titleMedium,
              color = ChalkWhite
            )
          }
        }
      }
    }
  }
}
