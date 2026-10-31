package com.streamdesk.app

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** True on a TV box such as the Xiaomi Mi Box: operated with a remote, not by touch. */
fun Context.isTelevision(): Boolean {
    val mode = (getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)?.currentModeType
    return mode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

val LocalIsTv = staticCompositionLocalOf { false }

/** What a remote-control key does while watching a channel on a TV. */
enum class TvAction { ChannelUp, ChannelDown, Select, Menu, ShowInfo }

object TvKeys {
    fun map(keyCode: Int): TvAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> TvAction.ChannelUp
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> TvAction.ChannelDown
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TvAction.Select
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> TvAction.Menu
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> TvAction.ShowInfo
        else -> null
    }
}

/** The number keys: type 3 to go to channel 3, 1 then 2 for channel 12. */
object NumberKeys {
    fun digit(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }
}

/** The digits typed so far. A fourth digit starts a new number. */
class NumberEntry(private val maxDigits: Int = 3) {
    var text = ""
        private set

    fun press(digit: Int) {
        text = if (text.length >= maxDigits) digit.toString() else text + digit
    }

    /** The number typed, and clears it. */
    fun take(): Int? = text.toIntOrNull().also { text = "" }

    fun clear() { text = "" }
}

/** The selected row on a TV is solid white, so its text and icons turn near-black. */
val FocusInk = Color(0xFF0E1113)
val FocusInkSoft = Color(0xFF3A444A)
val FocusAmberDark = Color(0xFFA65F00)
/** A TV tile the remote is on: a shade lighter than the others, inside its white glow. */
val FocusCard = Color(0xFF26313A)   // the amber of the favorite star and "now playing", dark enough on white

/** Menu entry: on a TV it turns solid white when the remote is on it; on a phone it's the usual menu item. */
@Composable
fun AppMenuItem(text: @Composable () -> Unit, onClick: () -> Unit) {
    if (!LocalIsTv.current) {
        DropdownMenuItem(text = text, onClick = onClick)
        return
    }
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .onFocusChanged { focused = it.isFocused }
            .background(if (focused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (focused) FocusInk else MaterialTheme.colorScheme.onSurface) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge, text)
        }
    }
}

/** On a TV the remote moves a highlight around the screen: an amber frame on whatever is selected. */
object TvFocusIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusNode(interactionSource)

    // One shared instance: all uses are equal
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)

    private class FocusNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private var focused = false
        private var pressed = false

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is FocusInteraction.Focus -> focused = true
                        is FocusInteraction.Unfocus -> focused = false
                        is PressInteraction.Press -> pressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            val corner = CornerRadius(8.dp.toPx())
            drawContent()
            if (focused) {
                // A thick white ring: visible on buttons, tabs and icons (channel rows and menus also fill with white)
                val ring = 4.dp.toPx()
                drawRoundRect(
                    Color.White, topLeft = Offset(ring / 2, ring / 2), size = Size(size.width - ring, size.height - ring),
                    cornerRadius = corner, style = Stroke(width = ring),
                )
            } else if (pressed) {
                drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = corner)
            }
        }
    }
}
