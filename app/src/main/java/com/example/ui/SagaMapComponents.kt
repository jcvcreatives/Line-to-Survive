package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.level.LevelCatalog
import com.example.game.model.DifficultyMode
import com.example.game.model.LevelConfig
import com.example.game.model.MapBiome
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.FinishBannerGreen
import com.example.ui.theme.InkCyanAccent
import com.example.ui.theme.LavaDeepRed
import com.example.ui.theme.LavaFlameYellow
import com.example.ui.theme.LavaOrange
import com.example.ui.theme.PencilGraphite
import com.example.ui.theme.SketchCharcoalBg
import com.example.ui.theme.SketchPaperCard

/**
 * PART 4: Map & Difficulty Selector inside the MAIN MENU modal so any Map theme
 * (`MAP_1_VOLCANO`, `MAP_2_CRYO_LAB`, `MAP_3_CYBER_GRID`) and Difficulty mode
 * (`Easy / Normal / Hard / Nightmare`) can be switched and previewed immediately.
 */
@Composable
fun MapAndDifficultySelectorBar(
  selectedMap: MapBiome,
  selectedDifficulty: DifficultyMode,
  onSelectMap: (MapBiome) -> Unit,
  onSelectDifficulty: (DifficultyMode) -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("map_difficulty_selector_bar"),
    color = SketchPaperCard.copy(alpha = 0.95f),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, ChalkWhite.copy(alpha = 0.22f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      // 1. Map Biome Selector Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "MAP:",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          color = PencilGraphite
        )
        for (biome in MapBiome.entries) {
          val isSelected = biome == selectedMap
          val accent = when (biome) {
            MapBiome.MAP_1_VOLCANO -> LavaOrange
            MapBiome.MAP_2_CRYO_LAB -> InkCyanAccent
            MapBiome.MAP_3_CYBER_GRID -> Color(0xFFE040FB)
          }
          Surface(
            onClick = { onSelectMap(biome) },
            modifier = Modifier
              .weight(1f)
              .testTag("select_map_${biome.id}"),
            shape = RoundedCornerShape(50),
            color = if (isSelected) accent.copy(alpha = 0.24f) else SketchCharcoalBg.copy(alpha = 0.85f),
            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else ChalkWhite.copy(alpha = 0.24f))
          ) {
            Text(
              text = biome.shortLabel,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
              color = if (isSelected) accent else ChalkWhite.copy(alpha = 0.8f),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
          }
        }
      }

      // 2. Difficulty Mode Selector Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "DIFF:",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          color = PencilGraphite
        )
        for (diff in DifficultyMode.entries) {
          val isSelected = diff == selectedDifficulty
          val accent = when (diff) {
            DifficultyMode.EASY -> FinishBannerGreen
            DifficultyMode.NORMAL -> InkCyanAccent
            DifficultyMode.HARD -> LavaFlameYellow
            DifficultyMode.NIGHTMARE -> LavaDeepRed
          }
          Surface(
            onClick = { onSelectDifficulty(diff) },
            modifier = Modifier
              .weight(1f)
              .testTag("select_difficulty_${diff.id}"),
            shape = RoundedCornerShape(50),
            color = if (isSelected) accent.copy(alpha = 0.24f) else SketchCharcoalBg.copy(alpha = 0.85f),
            border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) accent else ChalkWhite.copy(alpha = 0.24f))
          ) {
            Text(
              text = diff.displayName,
              fontSize = 11.sp,
              fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
              color = if (isSelected) accent else ChalkWhite.copy(alpha = 0.8f),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
            )
          }
        }
      }
    }
  }
}

/**
 * Candy Crush-style 50-Level Winding Saga Map.
 * Every node (1..50) is tappable so the user can test any level immediately!
 */
@Composable
fun CandyCrushSagaMap(
  selectedLevel: Int,
  bestLevel: Int,
  levelStars: Map<Int, Int>,
  onSelectLevel: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()
  val rows = (1..LevelCatalog.TOTAL_LEVELS).chunked(3)

  Column(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(SketchCharcoalBg.copy(alpha = 0.92f))
      .border(1.5.dp, ChalkWhite.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
      .verticalScroll(scrollState)
      .padding(horizontal = 14.dp, vertical = 10.dp)
      .testTag("saga_map_scroll"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    rows.forEachIndexed { rowIndex, rowLevels ->
      val firstLevelInRow = rowLevels.first()
      val milestoneLevel = rowLevels.firstOrNull { (it - 1) % 10 == 0 }
      if (milestoneLevel != null) {
        val cfg = LevelCatalog.getLevelConfig(milestoneLevel)
        val worldEnd = (milestoneLevel + 9).coerceAtMost(LevelCatalog.TOTAL_LEVELS)
        var worldStars = 0
        var worldCleared = 0
        for (l in milestoneLevel..worldEnd) {
          val st = levelStars[l] ?: if (l < bestLevel) 2 else 0
          if (st > 0 || l < bestLevel) {
            worldCleared++
            worldStars += st.coerceIn(1, 3)
          }
        }
        val worldTotalPossibleStars = (worldEnd - milestoneLevel + 1) * 3
        WorldBiomeBanner(
          worldIndex = cfg.worldIndex,
          worldTitle = cfg.worldTitle,
          levelRangeText = "LVLS $milestoneLevel–$worldEnd • ★ $worldStars/$worldTotalPossibleStars ($worldCleared/${worldEnd - milestoneLevel + 1})"
        )
        Spacer(modifier = Modifier.height(6.dp))
      }

      val isReversedRow = (rowIndex % 2 == 1)
      val nextRowFirstLevel = rows.getOrNull(rowIndex + 1)?.firstOrNull()
      val isRowUnlocked = firstLevelInRow <= bestLevel || rowLevels.any { (levelStars[it] ?: 0) > 0 }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(94.dp)
          .drawBehind {
            val w = size.width
            val cy = size.height * 0.44f
            val colXs = listOf(w * 0.17f, w * 0.50f, w * 0.83f)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

            if (rowLevels.size >= 2) {
              val startCol = if (isReversedRow) 2 else 0
              val endCol = if (isReversedRow) 3 - rowLevels.size else rowLevels.size - 1
              val x0 = colXs[startCol]
              val x1 = colXs[endCol]

              drawLine(
                color = Color(0xFF2B2F3C),
                start = Offset(x0, cy),
                end = Offset(x1, cy),
                strokeWidth = 18f,
                cap = StrokeCap.Round
              )
              drawLine(
                color = if (isRowUnlocked) FinishBannerGreen.copy(alpha = 0.78f) else PencilGraphite.copy(alpha = 0.35f),
                start = Offset(x0, cy),
                end = Offset(x1, cy),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
                pathEffect = if (isRowUnlocked) null else dashEffect
              )
            }

            if (nextRowFirstLevel != null) {
              val turnCol = if (isReversedRow) (3 - rowLevels.size).coerceIn(0, 2) else (rowLevels.size - 1).coerceIn(0, 2)
              val turnX = colXs[turnCol]
              val nextUnlocked = nextRowFirstLevel <= bestLevel

              drawLine(
                color = Color(0xFF2B2F3C),
                start = Offset(turnX, cy),
                end = Offset(turnX, size.height + 44f),
                strokeWidth = 18f,
                cap = StrokeCap.Round
              )
              drawLine(
                color = if (nextUnlocked) FinishBannerGreen.copy(alpha = 0.78f) else PencilGraphite.copy(alpha = 0.35f),
                start = Offset(turnX, cy),
                end = Offset(turnX, size.height + 44f),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
                pathEffect = if (nextUnlocked) null else dashEffect
              )
            }
          }
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          for (colIndex in 0 until 3) {
            val lvl = if (isReversedRow) {
              val revIdx = 2 - colIndex
              rowLevels.getOrNull(revIdx)
            } else {
              rowLevels.getOrNull(colIndex)
            }

            if (lvl != null) {
              val stars = levelStars[lvl] ?: if (lvl < bestLevel) 2 else 0
              val isCompleted = (lvl < bestLevel) || (stars > 0)
              val isUnlocked = (lvl <= bestLevel) || isCompleted
              val isCurrentFrontier = (lvl == bestLevel) && !isCompleted
              val isSelected = (lvl == selectedLevel)
              val verticalWave = if (colIndex == 1) (-4).dp else 4.dp

              SagaLevelNode(
                level = lvl,
                stars = stars,
                isCompleted = isCompleted,
                isUnlocked = isUnlocked,
                isCurrentFrontier = isCurrentFrontier,
                isSelected = isSelected,
                onClick = { onSelectLevel(lvl) },
                modifier = Modifier.offset(y = verticalWave)
              )
            } else {
              Spacer(modifier = Modifier.size(68.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun WorldBiomeBanner(
  worldIndex: Int,
  worldTitle: String,
  levelRangeText: String
) {
  val accentColor = when (worldIndex) {
    1 -> FinishBannerGreen
    2 -> LavaFlameYellow
    3 -> InkCyanAccent
    4 -> LavaOrange
    else -> LavaDeepRed
  }

  Surface(
    color = SketchPaperCard.copy(alpha = 0.95f),
    shape = RoundedCornerShape(12.dp),
    border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.75f)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = worldTitle.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = accentColor,
        fontWeight = FontWeight.ExtraBold
      )
      Text(
        text = levelRangeText,
        style = MaterialTheme.typography.labelSmall,
        color = LavaFlameYellow,
        fontWeight = FontWeight.Bold
      )
    }
  }
}

@Composable
private fun SagaLevelNode(
  level: Int,
  stars: Int,
  isCompleted: Boolean,
  isUnlocked: Boolean,
  isCurrentFrontier: Boolean,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val displayStars = if (isCompleted) stars.coerceIn(1, 3) else 0
  val isPerfectThreeStar = isCompleted && displayStars == 3

  val nodeFill = when {
    isPerfectThreeStar -> Color(0xFF1A5E3A)
    isCompleted -> Color(0xFF134E32)
    isCurrentFrontier || isSelected -> LavaOrange
    else -> Color(0xFF252834)
  }
  val borderColor = when {
    isSelected -> InkCyanAccent
    isCurrentFrontier -> LavaFlameYellow
    isPerfectThreeStar -> LavaFlameYellow
    isCompleted -> FinishBannerGreen
    else -> PencilGraphite.copy(alpha = 0.38f)
  }
  val borderWidth = if (isSelected || isCurrentFrontier || isPerfectThreeStar) 3.dp else 2.dp

  Column(
    modifier = modifier
      .width(78.dp)
      .clip(RoundedCornerShape(14.dp))
      .clickable { onClick() }
      .padding(vertical = 2.dp)
      .testTag("select_level_${level}_button"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (isCurrentFrontier) {
      Surface(
        color = LavaFlameYellow,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(bottom = 2.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Whatshot,
            contentDescription = null,
            tint = SketchCharcoalBg,
            modifier = Modifier.size(10.dp)
          )
          Text(
            text = "NEXT",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = SketchCharcoalBg
          )
        }
      }
    } else if (isCompleted) {
      Surface(
        color = if (isPerfectThreeStar) LavaFlameYellow else FinishBannerGreen,
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(bottom = 2.dp)
      ) {
        Text(
          text = "$displayStars★",
          fontSize = 8.sp,
          fontWeight = FontWeight.Black,
          color = SketchCharcoalBg,
          modifier = Modifier.padding(horizontal = 5.dp, vertical = 0.5.dp)
        )
      }
    } else {
      Spacer(modifier = Modifier.height(14.dp))
    }

    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(nodeFill)
        .border(borderWidth, borderColor, CircleShape)
        .testTag("map_node_level_$level"),
      contentAlignment = Alignment.Center
    ) {
      if (!isUnlocked && !isSelected) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Level $level",
            tint = PencilGraphite.copy(alpha = 0.65f),
            modifier = Modifier.size(13.dp)
          )
          Text(
            text = "$level",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = PencilGraphite.copy(alpha = 0.7f)
          )
        }
      } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "$level",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = ChalkWhite
          )
          if (isCompleted) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Completed",
              tint = if (isPerfectThreeStar) LavaFlameYellow else FinishBannerGreen,
              modifier = Modifier.size(11.dp)
            )
          }
        }
      }
    }

    // Star Rating Row under each Saga Node
    Surface(
      color = if (isCompleted) SketchPaperCard.copy(alpha = 0.92f) else Color.Transparent,
      shape = RoundedCornerShape(50),
      border = if (isCompleted) {
        BorderStroke(1.dp, if (isPerfectThreeStar) LavaFlameYellow.copy(alpha = 0.7f) else FinishBannerGreen.copy(alpha = 0.45f))
      } else null,
      modifier = Modifier
        .padding(top = 2.dp)
        .height(16.dp)
        .testTag("map_node_stars_$level")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (isCompleted) {
          for (s in 1..3) {
            Icon(
              imageVector = Icons.Default.Star,
              contentDescription = if (s <= displayStars) "Earned Star $s" else "Locked Star $s",
              tint = if (s <= displayStars) LavaFlameYellow else PencilGraphite.copy(alpha = 0.32f),
              modifier = Modifier.size(11.dp)
            )
          }
        } else if (isCurrentFrontier || isSelected) {
          Text(
            text = "☆☆☆",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = LavaFlameYellow
          )
        } else {
          Text(
            text = "☆☆☆",
            fontSize = 9.sp,
            color = PencilGraphite.copy(alpha = 0.45f)
          )
        }
      }
    }
  }
}

/**
 * Compact inspector bar displaying the selected level's scaling parameters, star rating record,
 * obstacle pool, and quick level stepper buttons (`-1`, `+1`, `+10`) so any level 1..50 can be tested rapidly.
 */
@Composable
fun SelectedLevelConfigBanner(
  config: LevelConfig,
  isCompleted: Boolean,
  stars: Int,
  onStepLevel: (Int) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val displayStars = if (isCompleted) stars.coerceIn(1, 3) else 0
  val starsBadgeText = if (isCompleted) {
    "★".repeat(displayStars) + "☆".repeat(3 - displayStars)
  } else {
    "☆☆☆"
  }

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("level_config_summary_badge"),
    color = SketchPaperCard,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, InkCyanAccent.copy(alpha = 0.55f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 7.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = "LVL ${config.level}: ${config.levelTitle.uppercase()}",
            style = MaterialTheme.typography.titleSmall,
            color = LavaFlameYellow,
            fontWeight = FontWeight.ExtraBold
          )
          Surface(
            color = SketchCharcoalBg,
            shape = RoundedCornerShape(50),
            border = BorderStroke(
              1.dp,
              if (isCompleted) LavaFlameYellow.copy(alpha = 0.75f) else PencilGraphite.copy(alpha = 0.4f)
            )
          ) {
            Text(
              text = starsBadgeText,
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = if (isCompleted) LavaFlameYellow else PencilGraphite,
              modifier = Modifier
                .padding(horizontal = 7.dp, vertical = 1.dp)
                .testTag("selected_level_stars_badge")
            )
          }
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            onClick = { onStepLevel((config.level - 1).coerceIn(1, 50)) },
            color = SketchCharcoalBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ChalkWhite.copy(alpha = 0.3f))
          ) {
            Text(
              text = "◀",
              fontSize = 10.sp,
              color = ChalkWhite,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
          }
          Surface(
            onClick = { onStepLevel((config.level + 1).coerceIn(1, 50)) },
            color = SketchCharcoalBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, ChalkWhite.copy(alpha = 0.3f))
          ) {
            Text(
              text = "▶",
              fontSize = 10.sp,
              color = ChalkWhite,
              modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
          }
          Surface(
            onClick = { onStepLevel(if (config.level + 10 > 50) 1 else config.level + 10) },
            color = SketchCharcoalBg,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, InkCyanAccent.copy(alpha = 0.45f))
          ) {
            Text(
              text = "+10",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = InkCyanAccent,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }
      }

      val hazardCount = LevelCatalog.getHazards(config.level, config).size
      val obstacleNames = config.obstaclesAllowed.joinToString("+") { it.name.take(5) }
      Text(
        text = "Dist: ${config.levelLength.toInt()}px • Spd: ${String.format("%.1f", config.baseSpeed)}x • Pool: [$obstacleNames] ($hazardCount) • ${config.mapBiome.shortLabel} (${config.difficultyMode.displayName})",
        style = MaterialTheme.typography.bodySmall,
        fontSize = 11.sp,
        color = ChalkWhite.copy(alpha = 0.9f),
        textAlign = TextAlign.Center
      )
    }
  }
}

