package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SketchbookColorScheme = darkColorScheme(
  primary = LavaOrange,
  onPrimary = ChalkWhite,
  primaryContainer = Color(0xFF4E2315),
  onPrimaryContainer = ChalkWhite,
  secondary = LavaFlameYellow,
  onSecondary = SketchCharcoalBg,
  secondaryContainer = SketchPaperCard,
  onSecondaryContainer = ChalkWhite,
  tertiary = FinishBannerGreen,
  onTertiary = SketchCharcoalBg,
  background = SketchCharcoalBg,
  onBackground = ChalkWhite,
  surface = SketchPaperSurface,
  onSurface = ChalkWhite,
  surfaceVariant = SketchPaperCard,
  onSurfaceVariant = PencilGraphite,
  error = LavaDeepRed,
  onError = ChalkWhite
)

@Composable
fun DrawToSurviveTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = SketchbookColorScheme,
    typography = Typography,
    content = content
  )
}
