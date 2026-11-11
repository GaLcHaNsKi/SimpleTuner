package dev.simpletuner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TunerColors = darkColorScheme(
    primary = Color(0xFF43E0B0),
    onPrimary = Color(0xFF0B1018),
    background = Color(0xFF0B1018),
    onBackground = Color(0xFFF3F6FA),
    surface = Color(0xFF151D29),
    onSurface = Color(0xFFF3F6FA),
    onSurfaceVariant = Color(0xFF93A4B8),
    outline = Color(0xFF344253),
    tertiary = Color(0xFFF5B85B),
)

@Composable
fun TunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TunerColors, content = content)
}
