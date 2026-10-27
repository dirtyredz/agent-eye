package local.agenteye.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFFE08A3C)
private val Danger = Color(0xFFE5534B)

private val Dark = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1B1209),
    secondary = Color(0xFF8FB8DE),
    background = Color(0xFF101418),
    surface = Color(0xFF161B21),
    surfaceVariant = Color(0xFF212833),
    onBackground = Color(0xFFE8E4DC),
    onSurface = Color(0xFFE8E4DC),
    onSurfaceVariant = Color(0xFFB4B9C2),
    error = Danger
)

private val Light = lightColorScheme(
    primary = Color(0xFFB4621C),
    secondary = Color(0xFF3D6B94),
    onBackground = Color(0xFF1B1F24),
    onSurface = Color(0xFF1B1F24),
    error = Color(0xFFC0362C)
)

@Composable
fun AgentEyeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = colorSchemeFor(dark)
    MaterialTheme(colorScheme = scheme, shapes = AgentEyeShapes) {
        // A bare Text outside any Scaffold/Surface reads LocalContentColor (black by default);
        // the root Surface makes every screen inherit onBackground.
        Surface(color = scheme.background, contentColor = scheme.onBackground, content = content)
    }
}

internal fun colorSchemeFor(dark: Boolean): ColorScheme = if (dark) Dark else Light
