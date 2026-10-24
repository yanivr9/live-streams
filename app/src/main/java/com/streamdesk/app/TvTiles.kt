package com.streamdesk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay

/**
 * A channel as a tile, for the TV: logo on top, name below. OK plays it; holding OK opens the options
 * (favorite, rename, copy link, delete). The tile the remote is on turns solid white.
 */
@Composable
fun ChannelTile(
    channel: Channel,
    number: Int?,
    saved: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onSave: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
    removeLabel: String,
    modifier: Modifier = Modifier,
    nowOn: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var heldOpen by remember { mutableStateOf(false) }   // the menu came from holding OK: ignore the click when OK is released
    LaunchedEffect(menu) {
        if (menu) {
            delay(900)
            heldOpen = false
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val ink = if (focused) FocusInk else MaterialTheme.colorScheme.onSurface
    val soft = if (focused) FocusInkSoft else MaterialTheme.colorScheme.onSurfaceVariant
    val hasLogo = channel.logo.startsWith("http") || channel.logo.startsWith("file:///android_asset/")

    Box(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (e.type == KeyEventType.KeyDown && ok && e.nativeKeyEvent.repeatCount == 1) {
                    heldOpen = true
                    menu = true
                    true
                } else false
            }
            .background(if (focused) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)
            .clickable { if (heldOpen) heldOpen = false else onClick() }
            .padding(10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val numberText: @Composable () -> Unit = {
                    Text(
                        number?.let { "%02d".format(it) } ?: "–",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasLogo) {
                    SubcomposeAsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { numberText() } },
                        error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { numberText() } },
                    )
                } else {
                    numberText()
                }
            }
            Text(
                channel.displayName(),
                color = ink,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            val detail = if (channel.source.isNotEmpty() || channel.url.isBlank()) channel.group else channel.group.ifBlank { "" }
            Text(
                (nowOn ?: detail).ifBlank { " " },
                color = soft,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (channel.favorite) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Favorite",
                tint = if (focused) FocusAmberDark else Amber,
                modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
            )
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            AppMenuItem(text = { Text(if (channel.favorite) "Remove from favorites" else "Add to favorites") }, onClick = { menu = false; onToggleFavorite() })
            if (saved) AppMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            else AppMenuItem(text = { Text("Save to channels") }, onClick = { menu = false; onSave() })
            AppMenuItem(text = { Text("Copy link") }, onClick = { menu = false; onCopy() })
            AppMenuItem(text = { Text(removeLabel) }, onClick = { menu = false; onRemove() })
        }
    }
}
