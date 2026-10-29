package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import com.example.game.model.RuneType
import com.example.game.model.ShopCategory
import com.example.game.model.ShopCatalog
import com.example.game.model.ShopItem

/**
 * Top HUD bar displayed during active gameplay:
 * - Compact 2x2 Grid HUD (Magic Ink, Ignis Titan Boss HP, AI Rune Inspector badge, DIST & SCORE)
 * - Level indicator & title
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
      .padding(horizontal = 14.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top control bar with Compact 2x2 Grid HUD
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("gameplay_hud_bar"),
      color = SketchPaperSurface.copy(alpha = 0.92f),
      shape = RoundedCornerShape(18.dp),
      border = BorderStroke(1.5.dp, ChalkWhite.copy(alpha = 0.25f)),
      tonalElevation = 6.dp
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
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
            style = MaterialTheme.typography.bodySmall,
            color = PencilGraphite,
            maxLines = 1
          )
        }

        // Center: Compact 2x2 Grid HUD
        // Row 1 Left: Magic Ink | Row 1 Right: Ignis Titan Boss HP
        // Row 2 Left: AI Rune Inspector Badge | Row 2 Right: DIST & SCORE
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 12.dp),
          verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
          val pct = (hudState.levelProgress * 100f).roundToInt().coerceIn(0, 100)
          val inkPct = (hudState.inkRemainingRatio * 100).roundToInt().coerceIn(0, 100)
          val inkColor = when {
            hudState.inkRemainingRatio <= 0.15f -> LavaDeepRed
            hudState.inkRemainingRatio <= 0.35f -> LavaOrange
            hudState.inkRemainingRatio <= 0.60f -> LavaFlameYellow
            else -> InkCyanAccent
          }
          val bossRatio = if (hudState.bossMaxHp > 0) {
            (hudState.bossHp.toFloat() / hudState.bossMaxHp.toFloat()).coerceIn(0f, 1f)
          } else {
            0f
          }
          val animatedBossRatio by animateFloatAsState(
            targetValue = bossRatio,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            label = "bossHpProgressTransition"
          )
          var previousBossHp by remember(hudState.currentLevel) { mutableIntStateOf(hudState.bossHp) }
          val bossDamagePulse = remember { Animatable(0f) }
          LaunchedEffect(hudState.bossHp, hudState.currentLevel) {
            if (hudState.bossHp < previousBossHp) {
              previousBossHp = hudState.bossHp
              bossDamagePulse.snapTo(1f)
              bossDamagePulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing)
              )
            } else {
              previousBossHp = hudState.bossHp
            }
          }
          val pulseVal = bossDamagePulse.value
          val isBossDefeated = hudState.bossHp <= 0
          val isSuperAuraActive = hudState.superAuraSeconds > 0f

          // 2x2 Grid Row 1: Magic Ink (Left) & Ignis Titan Boss HP (Right)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Row 1 Left: Magic Ink
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "${stringResource(R.string.label_ink_meter)}: $inkPct%",
                  style = MaterialTheme.typography.labelSmall,
                  color = inkColor,
                  modifier = Modifier.testTag("ink_indicator_text")
                )
                if (hudState.isInkRecharging && hudState.inkRemainingRatio < 0.98f) {
                  Text(
                    text = "(+⚡)",
                    style = MaterialTheme.typography.labelSmall,
                    color = LavaFlameYellow
                  )
                }
              }
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

            // Row 1 Right: Ignis Titan Boss HP (with damage pulse & flash animation)
            val baseBossTextColor = if (isBossDefeated) FinishBannerGreen else LavaOrange
            val animatedBossTextColor = lerp(baseBossTextColor, ChalkWhite, (pulseVal * 1.25f).coerceIn(0f, 1f))
            val animatedBossBarColor = lerp(LavaDeepRed, LavaFlameYellow, (pulseVal * 1.35f).coerceIn(0f, 1f))
            val animatedBossTrackColor = lerp(SketchCharcoalBg, LavaDeepRed.copy(alpha = 0.55f), pulseVal)
            Column(
              modifier = Modifier
                .weight(1f)
                .graphicsLayer {
                  val scaleBoost = kotlin.math.sin(pulseVal * Math.PI.toFloat())
                  scaleX = 1f + scaleBoost * 0.08f
                  scaleY = 1f + scaleBoost * 0.16f
                },
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Image(
                  painter = painterResource(id = R.drawable.dragon_boss_icon),
                  contentDescription = null,
                  modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                      rotationZ = kotlin.math.sin(pulseVal * Math.PI.toFloat() * 4f) * 14f
                    }
                )
                Text(
                  text = if (isBossDefeated) {
                    "IGNIS SLAIN!"
                  } else {
                    "BOSS HP: ${hudState.bossHp}/${hudState.bossMaxHp}"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = animatedBossTextColor,
                  modifier = Modifier.testTag("boss_hp_text")
                )
              }
              LinearProgressIndicator(
                progress = { animatedBossRatio },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp))
                  .then(
                    if (pulseVal > 0.02f) {
                      Modifier.border(
                        width = 1.dp,
                        color = ChalkWhite.copy(alpha = pulseVal.coerceIn(0f, 1f)),
                        shape = RoundedCornerShape(3.dp)
                      )
                    } else {
                      Modifier
                    }
                  )
                  .testTag("boss_hp_bar"),
                color = animatedBossBarColor,
                trackColor = animatedBossTrackColor
              )
            }
          }

          // 2x2 Grid Row 2: AI Rune Inspector Badge (Left) & DIST / SCORE (Right)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            // Row 2 Left: AI Rune Inspector Badge
            val runeColor = when {
              isSuperAuraActive || hudState.lastRuneType == RuneType.STAR_SPARK -> LavaFlameYellow
              hudState.lastRuneType == RuneType.ZIGZAG_COIL -> InkCyanAccent
              hudState.lastRuneType == RuneType.RAMP_SLOPE -> FinishBannerGreen
              else -> ChalkWhite
            }
            Surface(
              modifier = Modifier
                .weight(1f)
                .testTag("rune_inspector_badge"),
              color = SketchCharcoalBg.copy(alpha = 0.85f),
              shape = RoundedCornerShape(6.dp),
              border = BorderStroke(1.dp, runeColor.copy(alpha = 0.65f))
            ) {
              Text(
                text = if (isSuperAuraActive) {
                  "✨ SUPER AURA!"
                } else {
                  hudState.lastRuneType.badgeLabel
                },
                style = MaterialTheme.typography.labelSmall,
                color = runeColor,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }

            // Row 2 Right: Compact DIST & SCORE counters + progress bar
            Column(
              modifier = Modifier.weight(1f),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = stringResource(R.string.label_progress, pct),
                  style = MaterialTheme.typography.labelSmall,
                  color = ChalkWhite,
                  modifier = Modifier.testTag("progress_indicator_text")
                )
                Text(
                  text = "PTS ${hudState.score}",
                  style = MaterialTheme.typography.labelSmall,
                  color = LavaFlameYellow,
                  modifier = Modifier.testTag("score_indicator_text")
                )
              }
              LinearProgressIndicator(
                progress = { hudState.levelProgress },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
                  .clip(RoundedCornerShape(3.dp))
                  .testTag("level_progress_bar"),
                color = FinishBannerGreen,
                trackColor = SketchCharcoalBg
              )
            }
          }
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

      if (hudState.hasTitanShield) {
        Surface(
          shape = RoundedCornerShape(50),
          color = SketchCharcoalBg.copy(alpha = 0.88f),
          border = BorderStroke(1.dp, InkCyanAccent)
        ) {
          Text(
            text = "🛡️ SHIELD",
            style = MaterialTheme.typography.bodySmall,
            color = InkCyanAccent,
            modifier = Modifier
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("shield_indicator_text")
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
      lastRuneType = uiState.lastRuneType,
      superAuraSeconds = uiState.stickman.superAuraTimer,
      hasTitanShield = uiState.stickman.hasTitanShield,
      bossHp = uiState.ignisBoss.hp,
      bossMaxHp = uiState.ignisBoss.maxHp,
      score = uiState.score,
      currency = uiState.currency,
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
 * Start screen with the title DRAW TO SURVIVE, 50-Level Candy Crush Saga Map, scaling stats inspector,
 * Daily Challenge launcher, Alchemist's Bazaar Shop, and Play button.
 */
@Composable
fun StartMenuOverlay(
  uiState: GameUiState,
  onPlayClick: () -> Unit,
  onSelectLevelClick: (Int) -> Unit,
  onToggleSoundClick: () -> Unit,
  onSelectDifficultyClick: (com.example.game.model.DifficultyMode) -> Unit = {},
  onSelectMapClick: (com.example.game.model.MapBiome) -> Unit = {},
  onOpenShopClick: () -> Unit = {},
  onCloseShopClick: () -> Unit = {},
  onBuyOrEquipShopItemClick: (ShopItem) -> Unit = {},
  onStartDailyChallengeClick: () -> Unit = {}
) {
  val selectedConfig = com.example.game.level.LevelCatalog.getLevelConfig(
    level = uiState.currentLevel,
    difficulty = uiState.selectedDifficulty,
    mapBiome = uiState.selectedMap
  )
  val selectedStars = uiState.levelStars[uiState.currentLevel] ?: if (uiState.currentLevel < uiState.bestLevel) 3 else 0
  val isSelectedCompleted = (uiState.currentLevel < uiState.bestLevel) || (selectedStars > 0)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SketchCharcoalBg.copy(alpha = 0.82f))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 16.dp, vertical = 8.dp),
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
          .padding(horizontal = 18.dp, vertical = 12.dp),
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
              modifier = Modifier.size(26.dp)
            )
            Text(
              text = stringResource(R.string.title_draw_to_survive),
              style = MaterialTheme.typography.headlineMedium,
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
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.EmojiEvents,
                  contentDescription = null,
                  tint = LavaFlameYellow,
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = "${stringResource(R.string.label_best_level, uiState.bestLevel)} • ★ ${uiState.totalStarsEarned}/150",
                  style = MaterialTheme.typography.labelMedium,
                  color = LavaFlameYellow,
                  modifier = Modifier.testTag("best_level_text")
                )
              }
            }

            OutlinedButton(
              onClick = onToggleSoundClick,
              modifier = Modifier
                .height(36.dp)
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
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // PART 4: Map Biome & Difficulty Selector Bar inside MAIN MENU
        MapAndDifficultySelectorBar(
          selectedMap = uiState.selectedMap,
          selectedDifficulty = uiState.selectedDifficulty,
          onSelectMap = onSelectMapClick,
          onSelectDifficulty = onSelectDifficultyClick
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Candy Crush-Style 50-Level Winding Saga Map
        CandyCrushSagaMap(
          selectedLevel = uiState.currentLevel,
          bestLevel = uiState.bestLevel,
          levelStars = uiState.levelStars,
          onSelectLevel = onSelectLevelClick,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false)
            .height(162.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Selected Level Scaling Formula Summary + Quick Level Stepper
        SelectedLevelConfigBanner(
          config = selectedConfig,
          isCompleted = isSelectedCompleted,
          stars = selectedStars,
          onStepLevel = onSelectLevelClick
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Daily Challenge & Alchemist's Bazaar Shop Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onStartDailyChallengeClick,
            modifier = Modifier
              .weight(1f)
              .height(40.dp)
              .testTag("daily_challenge_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, InkCyanAccent)
          ) {
            Text(
              text = if (uiState.dailyChallengeCompleted) {
                "✓ DAILY (${uiState.dailyChallengeModifier})"
              } else {
                "⚡ DAILY: ${uiState.dailyChallengeModifier}"
              },
              style = MaterialTheme.typography.labelMedium,
              color = InkCyanAccent,
              maxLines = 1
            )
          }

          OutlinedButton(
            onClick = onOpenShopClick,
            modifier = Modifier
              .weight(1f)
              .height(40.dp)
              .testTag("shop_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, LavaFlameYellow)
          ) {
            Text(
              text = "⚗️ BAZAAR • 🪙 ${uiState.currency}",
              style = MaterialTheme.typography.labelMedium,
              color = LavaFlameYellow,
              maxLines = 1
            )
          }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Large Play Button
        Button(
          onClick = onPlayClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
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
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "${stringResource(R.string.action_play)} (LVL ${uiState.currentLevel} • ${uiState.selectedDifficulty.displayName.uppercase()})",
            style = MaterialTheme.typography.titleLarge
          )
        }
      }
    }

    if (uiState.showShopModal) {
      AlchemistsBazaarModal(
        uiState = uiState,
        onCloseClick = onCloseShopClick,
        onBuyOrEquipItem = onBuyOrEquipShopItemClick
      )
    }
  }
}

@Composable
fun AlchemistsBazaarModal(
  uiState: GameUiState,
  onCloseClick: () -> Unit,
  onBuyOrEquipItem: (ShopItem) -> Unit
) {
  val selectedCategoryState = androidx.compose.runtime.remember {
    androidx.compose.runtime.mutableStateOf(ShopCategory.SKIN)
  }
  val activeCategory = selectedCategoryState.value
  val displayedItems = when (activeCategory) {
    ShopCategory.SKIN -> ShopCatalog.SKINS
    ShopCategory.TRAIL -> ShopCatalog.TRAILS
    ShopCategory.PERK -> ShopCatalog.PERKS
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SketchCharcoalBg.copy(alpha = 0.88f))
      .padding(16.dp)
      .testTag("alchemists_bazaar_modal"),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 580.dp)
        .border(2.dp, LavaFlameYellow, RoundedCornerShape(22.dp)),
      shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = SketchPaperSurface)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "⚗️ ALCHEMIST'S BAZAAR",
              style = MaterialTheme.typography.titleLarge,
              color = LavaFlameYellow,
              modifier = Modifier.testTag("shop_title_text")
            )
            Text(
              text = "🪙 ${uiState.currency} ORBS",
              style = MaterialTheme.typography.titleMedium,
              color = FinishBannerGreen,
              modifier = Modifier.testTag("shop_currency_text")
            )
          }

          OutlinedButton(
            onClick = onCloseClick,
            modifier = Modifier
              .height(38.dp)
              .testTag("close_shop_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, ChalkWhite)
          ) {
            Text(
              text = "CLOSE",
              style = MaterialTheme.typography.labelLarge,
              color = ChalkWhite
            )
          }
        }

        // Category Switcher Row: SKINS | TRAILS | PERKS
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          for (cat in ShopCategory.entries) {
            val isCatSelected = cat == activeCategory
            Surface(
              onClick = { selectedCategoryState.value = cat },
              modifier = Modifier
                .weight(1f)
                .testTag("shop_tab_${cat.name}"),
              shape = RoundedCornerShape(10.dp),
              color = if (isCatSelected) LavaFlameYellow.copy(alpha = 0.22f) else SketchCharcoalBg.copy(alpha = 0.7f),
              border = BorderStroke(
                width = if (isCatSelected) 1.5.dp else 1.dp,
                color = if (isCatSelected) LavaFlameYellow else ChalkWhite.copy(alpha = 0.25f)
              )
            ) {
              Text(
                text = "${cat.name}S",
                style = MaterialTheme.typography.labelMedium,
                color = if (isCatSelected) LavaFlameYellow else ChalkWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 6.dp)
              )
            }
          }
        }

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          for (item in displayedItems) {
            val isUnlocked = when (item.category) {
              ShopCategory.SKIN -> item.id in uiState.unlockedSkins
              ShopCategory.TRAIL -> item.id in uiState.unlockedTrails
              ShopCategory.PERK -> item.id in uiState.unlockedPerks
            }
            val isEquipped = when (item.category) {
              ShopCategory.SKIN -> uiState.equippedSkin == item.id
              ShopCategory.TRAIL -> uiState.equippedTrail == item.id
              ShopCategory.PERK -> uiState.equippedPerk == item.id
            }
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("shop_item_${item.id}"),
              color = SketchCharcoalBg.copy(alpha = 0.82f),
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(
                1.dp,
                if (isEquipped) FinishBannerGreen else ChalkWhite.copy(alpha = 0.2f)
              )
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "${item.name} (${item.category.name})",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isEquipped) FinishBannerGreen else ChalkWhite
                  )
                  Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = PencilGraphite,
                    maxLines = 1
                  )
                }
                Button(
                  onClick = { onBuyOrEquipItem(item) },
                  enabled = isUnlocked || uiState.currency >= item.cost,
                  modifier = Modifier
                    .height(32.dp)
                    .testTag("shop_action_${item.id}"),
                  colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                      isEquipped -> FinishBannerGreen
                      isUnlocked -> InkCyanAccent
                      else -> LavaOrange
                    },
                    contentColor = SketchCharcoalBg
                  )
                ) {
                  Text(
                    text = when {
                      isEquipped -> "EQUIPPED"
                      isUnlocked -> "EQUIP"
                      else -> "BUY ${item.cost}"
                    },
                    style = MaterialTheme.typography.labelSmall
                  )
                }
              }
            }
          }
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
 * Level Complete screen displaying earned stars (1-3) based on ink efficiency and collectible
 * collection, along with Next Level, Retry, and Main Menu actions.
 */
@Composable
fun LevelCompleteOverlay(
  uiState: GameUiState,
  onNextLevelClick: () -> Unit,
  onRetryClick: () -> Unit,
  onHomeClick: () -> Unit
) {
  val earnedStars = uiState.starsEarned.coerceIn(1, 3)
  val inkEfficiencyMet = uiState.earnedInkEfficiencyStar
  val collectiblesMet = uiState.earnedCollectibleStar
  val inkUsedInt = uiState.totalInkDrawn.roundToInt()
  val parInkInt = uiState.levelDefinition.parInkUsage.roundToInt().coerceAtLeast(1)
  val inkLeftPct = (uiState.inkRemainingRatio * 100f).roundToInt().coerceIn(0, 100)

  val ratingSummary = when (earnedStars) {
    3 -> "3 / 3 STARS • PERFECT ARCHITECT!"
    2 -> "2 / 3 STARS • GREAT CROSSING!"
    else -> "1 / 3 STARS • LEVEL CLEARED!"
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(SketchCharcoalBg.copy(alpha = 0.84f))
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 20.dp, vertical = 12.dp),
    contentAlignment = Alignment.Center
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 500.dp)
        .border(2.5.dp, FinishBannerGreen, RoundedCornerShape(26.dp))
        .testTag("level_complete_card"),
      shape = RoundedCornerShape(26.dp),
      colors = CardDefaults.cardColors(containerColor = SketchPaperSurface)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = stringResource(R.string.level_complete_title),
          style = MaterialTheme.typography.displayMedium,
          color = FinishBannerGreen,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("level_complete_title_text")
        )

        // 3-Star Podium Display (1-3 Stars Earned)
        Row(
          modifier = Modifier
            .padding(vertical = 2.dp)
            .testTag("level_complete_stars_row"),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          for (starIndex in 1..3) {
            val isEarned = starIndex <= earnedStars
            val badgeSize = if (starIndex == 2) 54.dp else 46.dp
            val iconSize = if (starIndex == 2) 34.dp else 28.dp
            Box(
              modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(
                  if (isEarned) LavaFlameYellow.copy(alpha = 0.22f)
                  else SketchCharcoalBg.copy(alpha = 0.7f)
                )
                .border(
                  width = if (isEarned) 2.5.dp else 1.5.dp,
                  color = if (isEarned) LavaFlameYellow else PencilGraphite.copy(alpha = 0.38f),
                  shape = CircleShape
                )
                .testTag("level_complete_star_$starIndex"),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Star,
                contentDescription = if (isEarned) "Star $starIndex Earned" else "Star $starIndex Locked",
                tint = if (isEarned) LavaFlameYellow else PencilGraphite.copy(alpha = 0.35f),
                modifier = Modifier.size(iconSize)
              )
            }
          }
        }

        Surface(
          color = SketchPaperCard,
          shape = RoundedCornerShape(50),
          border = BorderStroke(1.dp, LavaFlameYellow.copy(alpha = 0.6f))
        ) {
          Text(
            text = ratingSummary,
            style = MaterialTheme.typography.labelLarge,
            color = LavaFlameYellow,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
              .padding(horizontal = 14.dp, vertical = 4.dp)
              .testTag("level_complete_stars_text")
          )
        }

        // Star Rating Criteria Breakdown (Finish + Ink Efficiency + Collectibles)
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("level_complete_stats_card"),
          color = SketchCharcoalBg.copy(alpha = 0.78f),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, ChalkWhite.copy(alpha = 0.18f))
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            // Star 1: Crossing Completed
            StarBreakdownRow(
              earned = true,
              label = "Crossing Complete",
              detail = "LVL ${uiState.currentLevel} Finish Reached",
              tag = "level_complete_crossing_row"
            )
            // Star 2: Ink Efficiency
            StarBreakdownRow(
              earned = inkEfficiencyMet,
              label = "Ink Efficiency",
              detail = "$inkUsedInt / $parInkInt Par ($inkLeftPct% Ink Left)",
              tag = "level_complete_ink_efficiency_text"
            )
            // Star 3: Collectibles
            StarBreakdownRow(
              earned = collectiblesMet,
              label = "Chalk Collectibles",
              detail = "${uiState.allCollectiblesGotCount}/${uiState.allCollectiblesCount} Collected (Need ${uiState.collectibleTargetCount}+)",
              tag = "level_complete_collectibles_text"
            )
          }
        }

        Button(
          onClick = onNextLevelClick,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
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
              .height(48.dp)
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
              .height(48.dp)
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

@Composable
private fun StarBreakdownRow(
  earned: Boolean,
  label: String,
  detail: String,
  tag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(tag),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = if (earned) Icons.Default.Star else Icons.Default.CheckCircle,
        contentDescription = null,
        tint = if (earned) LavaFlameYellow else PencilGraphite.copy(alpha = 0.4f),
        modifier = Modifier.size(16.dp)
      )
      Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (earned) ChalkWhite else PencilGraphite.copy(alpha = 0.7f)
      )
    }
    Text(
      text = detail,
      fontSize = 11.sp,
      fontWeight = FontWeight.SemiBold,
      color = if (earned) FinishBannerGreen else LavaOrange.copy(alpha = 0.85f)
    )
  }
}

