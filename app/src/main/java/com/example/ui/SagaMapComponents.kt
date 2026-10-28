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
import com.example.game.model.LevelConfig
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
 * Candy Crush-style 50-Level Winding Saga Map showing:
 * - Completed levels (green/gold node + 1..3 stars + checkmark)
 * - Current unlocked frontier level (pulsing fiery orange node + "NEXT" flame badge)
 * - Locked future levels (padlock node along dashed trail)
 * - Winding S-curve road connecting all 50 levels across 5 themed worlds
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
      .padding(horizontal = 14.dp, vertical = 14.dp)
      .testTag("saga_map_scroll"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    rows.forEachIndexed { rowIndex, rowLevels ->
      val firstLevelInRow = rowLevels.first()
      // Show World Banner whenever a row starts a new 10-level World (or contains level 1, 11, 21, 31, 41)
      val milestoneLevel = rowLevels.firstOrNull { (it - 1) % 10 == 0 }
      if (milestoneLevel != null) {
        val cfg = LevelCatalog.getLevelConfig(milestoneLevel)
        WorldBiomeBanner(
          worldIndex = cfg.worldIndex,
          worldTitle = cfg.worldTitle,
          levelRangeText = "LEVELS $milestoneLevel–${(milestoneLevel + 9).coerceAtMost(50)}"
        )
        Spacer(modifier = Modifier.height(8.dp))
      }

      val isReversedRow = (rowIndex % 2 == 1)
      val orderedRow = if (isReversedRow) rowLevels.reversed() else rowLevels
      val nextRowFirstLevel = rows.getOrNull(rowIndex + 1)?.firstOrNull()
      val isRowUnlocked = firstLevelInRow <= bestLevel

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(98.dp)
          .drawBehind {
            val w = size.width
            val cy = size.height * 0.44f
            val colXs = listOf(w * 0.17f, w * 0.50f, w * 0.83f)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)

            // Horizontal S-curve segment across the 3 nodes in this row
            if (rowLevels.size >= 2) {
              val startCol = if (isReversedRow) 2 else 0
              val endCol = if (isReversedRow) 3 - rowLevels.size else rowLevels.size - 1
              val x0 = colXs[startCol]
              val x1 = colXs[endCol]

              // Outer road track
              drawLine(
                color = Color(0xFF2B2F3C),
                start = Offset(x0, cy),
                end = Offset(x1, cy),
                strokeWidth = 18f,
                cap = StrokeCap.Round
              )
              // Inner chalk path
              drawLine(
                color = if (isRowUnlocked) FinishBannerGreen.copy(alpha = 0.78f) else PencilGraphite.copy(alpha = 0.35f),
                start = Offset(x0, cy),
                end = Offset(x1, cy),
                strokeWidth = 6f,
                cap = StrokeCap.Round,
                pathEffect = if (isRowUnlocked) null else dashEffect
              )
            }

            // Vertical turn connector to the next row
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
              val stars = levelStars[lvl] ?: if (lvl < bestLevel) 3 else 0
              val isCompleted = (lvl < bestLevel) || (stars > 0)
              val isUnlocked = lvl <= bestLevel
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
        .padding(horizontal = 12.dp, vertical = 6.dp),
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
        color = ChalkWhite.copy(alpha = 0.85f)
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
  val nodeFill = when {
    isCompleted -> Color(0xFF134E32)
    isCurrentFrontier -> LavaOrange
    else -> Color(0xFF252834)
  }
  val borderColor = when {
    isSelected -> InkCyanAccent
    isCurrentFrontier -> LavaFlameYellow
    isCompleted -> FinishBannerGreen
    else -> PencilGraphite.copy(alpha = 0.38f)
  }
  val borderWidth = if (isSelected || isCurrentFrontier) 3.dp else 2.dp

  Column(
    modifier = modifier
      .width(76.dp)
      .clip(RoundedCornerShape(14.dp))
      .clickable(enabled = isUnlocked) { onClick() }
      .padding(vertical = 2.dp)
      .testTag("select_level_${level}_button"),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top badge for current frontier or selected node
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
    } else {
      Spacer(modifier = Modifier.height(14.dp))
    }

    // Circular Candy Crush Medallion
    Box(
      modifier = Modifier
        .size(48.dp)
        .clip(CircleShape)
        .background(nodeFill)
        .border(borderWidth, borderColor, CircleShape)
        .testTag("map_node_level_$level"),
      contentAlignment = Alignment.Center
    ) {
      if (!isUnlocked) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Locked Level $level",
            tint = PencilGraphite.copy(alpha = 0.65f),
            modifier = Modifier.size(15.dp)
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
              tint = FinishBannerGreen,
              modifier = Modifier.size(12.dp)
            )
          }
        }
      }
    }

    // Bottom 3-Star bar for Completed / Unlocked nodes
    Row(
      modifier = Modifier
        .padding(top = 3.dp)
        .height(14.dp),
      horizontalArrangement = Arrangement.spacedBy(2.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (isCompleted) {
        val displayStars = stars.coerceIn(1, 3)
        for (s in 1..3) {
          Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = if (s <= displayStars) LavaFlameYellow else PencilGraphite.copy(alpha = 0.3f),
            modifier = Modifier.size(12.dp)
          )
        }
      } else if (isCurrentFrontier) {
        Text(
          text = "OPEN",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = LavaFlameYellow
        )
      } else {
        Text(
          text = "LOCKED",
          fontSize = 8.sp,
          color = PencilGraphite.copy(alpha = 0.45f)
        )
      }
    }
  }
}

/**
 * Compact inspector bar displaying the selected level's scaling parameters from `getLevelConfig(level)`.
 */
@Composable
fun SelectedLevelConfigBanner(
  config: LevelConfig,
  isCompleted: Boolean,
  stars: Int,
  modifier: Modifier = Modifier
) {
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
        .padding(horizontal = 14.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "LVL ${config.level}: ${config.levelTitle.uppercase()}",
          style = MaterialTheme.typography.titleMedium,
          color = LavaFlameYellow,
          fontWeight = FontWeight.ExtraBold
        )
        Text(
          text = if (isCompleted) "COMPLETED (${"★".repeat(stars.coerceIn(1, 3))})" else "READY TO RUN",
          style = MaterialTheme.typography.labelMedium,
          color = if (isCompleted) FinishBannerGreen else InkCyanAccent,
          fontWeight = FontWeight.Bold
        )
      }

      val hazardCount = LevelCatalog.getHazards(config.level, config).size
      Text(
        text = "Dist: ${config.levelLength.toInt()}px • Speed: ${String.format("%.1f", config.baseSpeed)}x • Gaps: ${config.minGap.toInt()}–${config.maxGap.toInt()}px • ΔY: ±${config.maxDeltaY.toInt()}px • Decay: ${String.format("%.1f", config.lineLifespan)}s • Hazards: $hazardCount",
        style = MaterialTheme.typography.bodySmall,
        color = ChalkWhite.copy(alpha = 0.9f),
        textAlign = TextAlign.Center
      )
    }
  }
}
