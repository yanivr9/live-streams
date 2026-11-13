package com.streamdesk.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Context
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

/** The same app in white: light backgrounds, dark text, the same orange. */
private val LightColors = lightColorScheme(
    primary = Color(0xFFB86200),            // orange dark enough to read on white
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2BF),
    onPrimaryContainer = Color(0xFF5A3000),
    secondaryContainer = Color(0xFFFFE2BF),
    onSecondaryContainer = Color(0xFF5A3000),
    background = Color(0xFFF6F7F8),
    onBackground = Color(0xFF15191C),
    surface = Color(0xFFF6F7F8),
    onSurface = Color(0xFF15191C),
    surfaceVariant = Color(0xFFE6EAEC),
    onSurfaceVariant = Color(0xFF55616A),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEFF2F4),
    surfaceContainerHighest = Color(0xFFE6EAEC),
    outline = Color(0xFFB9C2C8),
    outlineVariant = Color(0xFFDCE1E4),
    error = Color(0xFFD32F2F),
)

/** ☰ → Settings → Theme: like the phone (default), always dark, or always light. The TV and the player stay dark. */
enum class ThemeMode(val label: String) { System("Like the phone"), Dark("Dark"), Light("Light") }

object ThemePrefs {
    private fun prefs(context: Context) = context.getSharedPreferences("look", Context.MODE_PRIVATE)
    /** The choice in force, read by every screen so a change shows at once. */
    var mode by mutableStateOf(ThemeMode.System)
        private set

    fun load(context: Context) {
        mode = runCatching { ThemeMode.valueOf(prefs(context).getString("theme", null).orEmpty()) }.getOrDefault(ThemeMode.System)
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()   // screens see the change at once (also before the first screen exists)
    }

    fun set(context: Context, value: ThemeMode) {
        mode = value
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()   // screens see the change at once (also before the first screen exists)
        prefs(context).edit().putString("theme", value.name).apply()
    }
}

/** Whether the app is drawn dark right now. */
@Composable
fun isAppDark(isTv: Boolean = LocalIsTv.current): Boolean = isTv || when (ThemePrefs.mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Dark -> true
    ThemeMode.Light -> false
}

@Composable
fun StreamDeskTheme(isTv: Boolean = false, alwaysDark: Boolean = false, content: @Composable () -> Unit) {
    // A TV is read from the sofa: scale everything up, and show the remote's selection
    val base = LocalDensity.current
    val density = if (isTv) Density(base.density * 1.3f, base.fontScale) else base
    // Hebrew reads right to left: the whole screen mirrors (the player keeps its controls as they are)
    val direction = if (Lang.hebrew && !alwaysDark) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr
    CompositionLocalProvider(LocalIsTv provides isTv, LocalDensity provides density, androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
        val dark = alwaysDark || isAppDark(isTv)
        MaterialTheme(colorScheme = if (isTv) DeskColors.copy(onSurfaceVariant = Color(0xFFC4CDD2)) else if (dark) DeskColors else LightColors) {
            if (isTv) CompositionLocalProvider(LocalIndication provides TvFocusIndication, content = content) else content()
        }
    }
}
