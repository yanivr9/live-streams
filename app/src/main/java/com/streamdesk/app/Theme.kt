package com.streamdesk.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFF9F1C)   // tally-light amber
val OnAir = Color(0xFFFF4D4D)

private val DeskColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF1A1205),
    primaryContainer = Color(0xFF3A2A12),
    onPrimaryContainer = Amber,
    secondaryContainer = Color(0xFF3A2A12),
    onSecondaryContainer = Amber,
    background = Color(0xFF0E1113),
    onBackground = Color(0xFFE3E8EA),
    surface = Color(0xFF0E1113),
    onSurface = Color(0xFFE3E8EA),
    surfaceVariant = Color(0xFF1D2327),
    onSurfaceVariant = Color(0xFF8A979E),
    surfaceContainer = Color(0xFF161B1E),
    surfaceContainerHigh = Color(0xFF1D2327),
    surfaceContainerHighest = Color(0xFF242C31),
    outline = Color(0xFF3A444A),
    outlineVariant = Color(0xFF262E33),
    error = OnAir,
)

@Composable
fun StreamDeskTheme(isTv: Boolean = false, content: @Composable () -> Unit) {
    // A TV is read from the sofa: scale everything up, and show the remote's selection
    val base = LocalDensity.current
    val density = if (isTv) Density(base.density * 1.3f, base.fontScale) else base
    CompositionLocalProvider(LocalIsTv provides isTv, LocalDensity provides density) {
        MaterialTheme(colorScheme = if (isTv) DeskColors.copy(onSurfaceVariant = Color(0xFFC4CDD2)) else DeskColors) {
            if (isTv) CompositionLocalProvider(LocalIndication provides TvFocusIndication, content = content) else content()
        }
    }
}
