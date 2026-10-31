package com.streamdesk.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** How many channel tiles fit on the first TV screen: the columns and rows are chosen so they fill it. */
enum class TileCount(val count: Int, val columns: Int, val rows: Int) {
    Six(6, 3, 2), Nine(9, 3, 3), Twelve(12, 4, 3), Fifteen(15, 5, 3), Twenty(20, 5, 4);

    val label: String get() = "$count"

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        private fun prefs(context: Context) = context.getSharedPreferences("launch", Context.MODE_PRIVATE)
        fun load(context: Context): TileCount =
            entries.firstOrNull { it.name == prefs(context).getString("tiles", null) } ?: Twelve
        fun save(context: Context, size: TileCount) = prefs(context).edit().putString("tiles", size.name).apply()
    }
}

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
    onGuide: (() -> Unit)? = null,
    fillHeight: Boolean = false,
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
    val shape = RoundedCornerShape(14.dp)
    val ink = MaterialTheme.colorScheme.onSurface
    val soft = MaterialTheme.colorScheme.onSurfaceVariant
    // "Glow": the tile the remote is on stays dark, grows a little and gets a white frame with a soft white glow
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "tile scale")
    val hasLogo = channel.logo.startsWith("http") || channel.logo.startsWith("file:///android_asset/")

    Box(
        modifier
            .fillMaxWidth()
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focused) Modifier
                    .shadow(20.dp, shape, ambientColor = Color.White, spotColor = Color.White)
                    .border(3.dp, Color.White, shape)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                if (e.type == KeyEventType.KeyDown && ok && e.nativeKeyEvent.repeatCount == 1) {
                    heldOpen = true
                    menu = true
                    true
                } else false
            }
            .background(if (focused) FocusCard else MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (heldOpen) heldOpen = false else onClick() }
            .padding(10.dp),
    ) {
        Column(if (fillHeight) Modifier.fillMaxSize() else Modifier) {
            Box(
                (if (fillHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(84.dp)).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
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
                // The number shows until the logo arrives (or for good if there is none); a plain AsyncImage is much
                // cheaper than SubcomposeAsyncImage, which matters for drawing the first screen on a slow TV box
                var logoShown by remember(channel.logo) { mutableStateOf(false) }
                if (!logoShown) numberText()
                if (hasLogo) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                        onSuccess = { logoShown = true },
                        onError = { logoShown = false },
                    )
                }
            }
            Text(
                channel.displayName(),
                color = ink,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                tint = Amber,
                modifier = Modifier.align(Alignment.TopEnd).size(22.dp),
            )
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            if (onGuide != null) AppMenuItem(text = { Text("TV guide") }, onClick = { menu = false; onGuide() })
            AppMenuItem(text = { Text(if (channel.favorite) "Remove from favorites" else "Add to favorites") }, onClick = { menu = false; onToggleFavorite() })
            if (saved) AppMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
            else AppMenuItem(text = { Text("Save to channels") }, onClick = { menu = false; onSave() })
            AppMenuItem(text = { Text("Copy link") }, onClick = { menu = false; onCopy() })
            AppMenuItem(text = { Text(removeLabel) }, onClick = { menu = false; onRemove() })
        }
    }
}

/**
 * The TV's tabs: the chosen one is an orange pill, the one the remote is on gets the same white glow frame as a tile.
 * Scrolls by itself so the chosen tab is always in view.
 */
@Composable
fun TvTabBar(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    LaunchedEffect(selected) { list.animateScrollToItem((selected - 1).coerceAtLeast(0)) }
    LazyRow(modifier, state = list, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        itemsIndexed(labels) { i, label ->
            var focused by remember { mutableStateOf(false) }
            val pill = RoundedCornerShape(50)
            val chosen = i == selected
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .then(if (focused) Modifier.shadow(12.dp, pill, ambientColor = Color.White, spotColor = Color.White).border(2.dp, Color.White, pill) else Modifier)
                    .background(if (chosen) Amber else if (focused) FocusCard else Color.Transparent, pill)
                    .clip(pill)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    color = if (chosen) FocusInk else if (focused) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
