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
import androidx.compose.runtime.staticCompositionLocalOf
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
    return mode == Configuration.UI_MODE_TYPE_TELEVISION || !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

val LocalIsTv = staticCompositionLocalOf { false }

/** What a remote-control key does while watching a channel on a TV. */
enum class TvAction { ChannelUp, ChannelDown, Select, Menu, ShowInfo }

object TvKeys {
    fun map(keyCode: Int): TvAction? = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> TvAction.ChannelUp
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> TvAction.ChannelDown
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TvAction.Select
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> TvAction.Menu
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_INFO -> TvAction.ShowInfo
        else -> null
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
            if (focused) drawRoundRect(Amber.copy(alpha = 0.30f), cornerRadius = corner)
            drawContent()
            if (focused) {
                // A white ring with an amber ring inside it: easy to see on any TV, whatever its contrast settings
                val outer = 4.dp.toPx()
                val inner = 2.dp.toPx()
                drawRoundRect(
                    Color.White, topLeft = Offset(outer / 2, outer / 2), size = Size(size.width - outer, size.height - outer),
                    cornerRadius = corner, style = Stroke(width = outer),
                )
                drawRoundRect(
                    Amber, topLeft = Offset(outer + inner / 2, outer + inner / 2), size = Size(size.width - 2 * outer - inner, size.height - 2 * outer - inner),
                    cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(width = inner),
                )
            } else if (pressed) {
                drawRoundRect(Color.White.copy(alpha = 0.10f), cornerRadius = corner)
            }
        }
    }
}
