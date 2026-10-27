package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SketchDisplayFontFamily = FontFamily(
  Font(R.font.permanent_marker, FontWeight.Normal)
)

val SketchBodyFontFamily = FontFamily(
  Font(R.font.patrick_hand, FontWeight.Normal)
)

val Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = SketchDisplayFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 44.sp,
    lineHeight = 50.sp,
    letterSpacing = 1.sp
  ),
  displayMedium = TextStyle(
    fontFamily = SketchDisplayFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = 0.8.sp
  ),
  headlineLarge = TextStyle(
    fontFamily = SketchDisplayFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = 0.5.sp
  ),
  headlineMedium = TextStyle(
    fontFamily = SketchDisplayFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 22.sp,
    lineHeight = 28.sp
  ),
  titleLarge = TextStyle(
    fontFamily = SketchDisplayFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.5.sp
  ),
  titleMedium = TextStyle(
    fontFamily = SketchBodyFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.4.sp
  ),
  bodyLarge = TextStyle(
    fontFamily = SketchBodyFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 18.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.3.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = SketchBodyFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.25.sp
  ),
  labelLarge = TextStyle(
    fontFamily = SketchDisplayFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.8.sp
  )
)
