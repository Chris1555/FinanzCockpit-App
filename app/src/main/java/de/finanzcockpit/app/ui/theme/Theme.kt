package de.finanzcockpit.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import de.finanzcockpit.app.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF102A43),
    onPrimary = Color.White,
    secondary = Color(0xFF16B8A6),
    tertiary = Color(0xFF16B8A6),
    error = Color(0xFFB42318),
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF7F5),
    onSurfaceVariant = Color(0xFF667085),
    outline = Color(0xFFB7C3D2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB6C9DB),
    secondary = Color(0xFF58D8C8),
    tertiary = Color(0xFF58D8C8),
    background = Color(0xFF0B1725),
    surface = Color(0xFF122235),
    surfaceVariant = Color(0xFF183044),
    outline = Color(0xFF6F8199),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun FinanzCockpitTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
