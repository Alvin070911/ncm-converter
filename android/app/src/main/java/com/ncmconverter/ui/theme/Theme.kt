package com.ncmconverter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandPink = Color(0xFFFB7299)
private val BrandPinkDark = Color(0xFFFF85A9)
private val BrandBlue = Color(0xFF00A1D6)
private val BrandBlueDark = Color(0xFF33B5E5)

private val LightColors = lightColorScheme(
    primary = BrandPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8EF),
    onPrimaryContainer = Color(0xFF5C1A2E),
    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F4FF),
    onSecondaryContainer = Color(0xFF003547),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF1F2937),
    surface = Color.White,
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFF1F2F6),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFE5E7EB),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = BrandPinkDark,
    onPrimary = Color(0xFF3A0F1C),
    primaryContainer = Color(0xFF3A2732),
    onPrimaryContainer = Color(0xFFFFD9E4),
    secondary = BrandBlueDark,
    onSecondary = Color(0xFF06232E),
    secondaryContainer = Color(0xFF103A4A),
    onSecondaryContainer = Color(0xFFD9F4FF),
    background = Color(0xFF17181C),
    onBackground = Color(0xFFF3F4F6),
    surface = Color(0xFF212228),
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = Color(0xFF2C2D33),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF33353D),
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun NcmConverterTheme(theme: String, content: @Composable () -> Unit) {
    val darkTheme = when (theme) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
