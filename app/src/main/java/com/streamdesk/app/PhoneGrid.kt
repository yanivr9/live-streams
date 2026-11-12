package com.streamdesk.app

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** The phone's channel view, list or grid (kept on the phone). */
object PhoneView {
    private fun prefs(context: Context) = context.getSharedPreferences("view", Context.MODE_PRIVATE)
    fun isGrid(context: Context): Boolean = prefs(context).getBoolean("grid", false)
    fun setGrid(context: Context, grid: Boolean) = prefs(context).edit().putBoolean("grid", grid).apply()
}

/**
 * A channel card for the phone's grid (3 across on a phone held upright): logo, LIVE, a favorite star, the name,
 * what's on now and the quality. Tap plays it; a long press opens the same options as ⋮ in the list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneTile(
    channel: Channel,
    tag: String?,
    onAir: NowNext?,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    menuItems: @Composable (close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val hasLogo = channel.logo.startsWith("http") || channel.logo.startsWith("file:///android_asset/")
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = { menu = true }, onLongClickLabel = "Channel options"),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(78.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                // The name on a coloured badge when there is no logo, or it couldn't be loaded
                var failed by remember(channel.logo) { mutableStateOf(false) }
                if (!hasLogo || failed) NameBadge(channel.displayName(), Modifier.fillMaxSize().padding(top = 18.dp))
                if (hasLogo) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        // Below the LIVE tag and the star, so they never cover the logo
                        modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 30.dp, bottom = 8.dp),
                        onSuccess = { failed = false },
                        onError = { failed = true },
                    )
                }
            }
            Column(Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
                Text(
                    channel.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    onAir?.title ?: onAir?.now ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!tag.isNullOrBlank()) {
                    Text(
                        tag,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FocusInk,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp).background(Amber, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp),
                    )
                }
            }
        }
        Text(
            "LIVE",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(5.dp).background(OnAir, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .combinedClickable(onClick = onToggleFavorite)
                .semantics { contentDescription = if (channel.favorite) t("Remove from favorites") else t("Add to favorites") },
            contentAlignment = Alignment.Center,
        ) {
            if (channel.favorite) Icon(Icons.Default.Star, null, tint = Amber, modifier = Modifier.size(16.dp))
            else Icon(Icons.Outlined.StarBorder, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) { menuItems { menu = false } }
    }
}
