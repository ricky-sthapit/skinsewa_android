package org.skinsewa.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF087FAF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2F0FC),
    onPrimaryContainer = Color(0xFF06384C),
    secondary = Color(0xFF008B83),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7F2EC),
    onSecondaryContainer = Color(0xFF003B37),
    background = Color(0xFFF3F9FB),
    onBackground = Color(0xFF172D38),
    surface = Color(0xFFF8FCFD),
    onSurface = Color(0xFF172D38),
    surfaceVariant = Color(0xFFE6F0F3),
    onSurfaceVariant = Color(0xFF506773),
    outline = Color(0xFF9AAEB6),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF78D1F4),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF075E7D),
    onPrimaryContainer = Color(0xFFD0F2FF),
    secondary = Color(0xFF63D8CD),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF075E57),
    onSecondaryContainer = Color(0xFFBFF5EE),
    background = Color(0xFF101B20),
    onBackground = Color(0xFFE2EDF1),
    surface = Color(0xFF142329),
    onSurface = Color(0xFFE2EDF1),
    surfaceVariant = Color(0xFF293A40),
    onSurfaceVariant = Color(0xFFBDCDD2),
    outline = Color(0xFF84979E),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SkinSewaTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
