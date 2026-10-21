package com.unishuttle.app.presentation.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object UniColors {
    val Ink = Color(0xFF15110F)
    val Canvas = Color(0xFFF4F1EA)
    val Surface = Color(0xFFFFFFFF)
    val Mint = Color(0xFFCDE7E2)
    val Brick = Color(0xFFC2412D)
    val Sun = Color(0xFFFFD23F)
    val Lime = Color(0xFFB6F25C)
    val Sky = Color(0xFF8EB8FF)
    val Alert = Color(0xFFE23B3B)
    val Muted = Color(0xFF5E5651)

    val NightCanvas = Color(0xFF120E0D)
    val NightSurface = Color(0xFF211A18)
    val NightMint = Color(0xFF1F3A36)
    val CoralOnNight = Color(0xFFFFB4A8)
    val NightMuted = Color(0xFFB9AFA9)
    val NightAlert = Color(0xFFFF6B6B)
}

val UniLightScheme = lightColorScheme(
    primary = UniColors.Brick,
    onPrimary = Color.White,
    primaryContainer = UniColors.Sun,
    onPrimaryContainer = UniColors.Ink,
    secondary = UniColors.Ink,
    onSecondary = UniColors.Canvas,
    tertiary = UniColors.Sky,
    onTertiary = UniColors.Ink,
    background = UniColors.Canvas,
    onBackground = UniColors.Ink,
    surface = UniColors.Surface,
    onSurface = UniColors.Ink,
    surfaceVariant = UniColors.Mint,
    onSurfaceVariant = UniColors.Muted,
    outline = UniColors.Ink,
    error = UniColors.Alert,
    onError = Color.White,
)

val UniDarkScheme = darkColorScheme(
    primary = UniColors.CoralOnNight,
    onPrimary = UniColors.Ink,
    primaryContainer = UniColors.Brick,
    onPrimaryContainer = Color.White,
    secondary = UniColors.Canvas,
    onSecondary = UniColors.Ink,
    tertiary = UniColors.Sky,
    onTertiary = UniColors.Ink,
    background = UniColors.NightCanvas,
    onBackground = UniColors.Canvas,
    surface = UniColors.NightSurface,
    onSurface = UniColors.Canvas,
    surfaceVariant = UniColors.NightMint,
    onSurfaceVariant = UniColors.NightMuted,
    outline = UniColors.Canvas,
    error = UniColors.NightAlert,
    onError = UniColors.Ink,
)
