package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = HighDensityBg,
    secondary = EmeraldLight,
    onSecondary = HighDensityBg,
    tertiary = EmeraldLight,
    background = HighDensityBg,
    onBackground = TextPrimary,
    surface = Slate900,
    onSurface = TextPrimary,
    surfaceVariant = Slate800,
    onSurfaceVariant = TextSecondary
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    secondary = EmeraldDark,
    onSecondary = Color.White,
    tertiary = EmeraldDark,
    background = Color(0xFFF0FDF4), // Very light mint
    onBackground = Color(0xFF0F1412),
    surface = Color.White,
    onSurface = Color(0xFF0F1412),
    surfaceVariant = Color(0xFFE8F5E9),
    onSurfaceVariant = Color(0xFF334155)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Let's disable dynamicColor to preserve our exact custom branding
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
