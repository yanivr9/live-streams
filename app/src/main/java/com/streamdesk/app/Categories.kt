package com.streamdesk.app

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** One entry of the All tab's category list. [key] is the channels' group ("" for channels without one, [ALL] for every channel). */
data class Category(val key: String, val name: String, val count: Int, val favorite: Boolean) {
    companion object {
        /** The "All channels" entry at the top of the list. */
        const val ALL = "\u0000all"
    }
}

object Categories {
    /**
     * The All tab's list: "All channels" first, then the favorite categories, then the rest, each part by name.
     * Favorites are kept by group name, so they work for every playlist that has a group by that name.
     */
    fun build(channels: List<Channel>, favorites: Set<String>): List<Category> {
        val counts = LinkedHashMap<String, Int>()
        channels.forEach { counts[it.group.trim()] = (counts[it.group.trim()] ?: 0) + 1 }
        val list = counts.map { (g, n) -> Category(g, g.ifBlank { t("Other") }, n, g.isNotBlank() && g in favorites) }
            .sortedWith(compareBy<Category>({ !it.favorite }, { it.key.isBlank() }, { it.name.lowercase() }))
        return listOf(Category(Category.ALL, t("All channels"), channels.size, false)) + list
    }

    /** Whether [channel] belongs to the category [key] (see [Category.key]). */
    fun matches(channel: Channel, key: String?): Boolean = key == null || key == Category.ALL || channel.group.trim() == key

    /** A small picture for the category, guessed from its name. */
    fun icon(name: String): String {
        val n = name.lowercase()
        fun has(vararg words: String) = words.any { n.contains(it) }
        return when {
            name == Category.ALL -> "📺"
            has("sport", "ספורט", "football", "soccer", "nba", "liga", "league", "espn", "dazn", "beinsport", "bein") -> "⚽"
            has("kid", "child", "cartoon", "ילדים", "junior", "disney", "nick") -> "🧸"
            has("movie", "film", "cinema", "סרט", "vod") -> "🎬"
            has("news", "חדשות", "cnn", "bbc") -> "📰"
            has("music", "מוזיק", "radio", "רדיו", "mtv") -> "🎵"
            has("doc", "nature", "discovery", "national geo", "טבע", "דוקו") -> "🌍"
            has("series", "סדר", "show", "drama") -> "🎞"
            has("israel", "hebrew", "עברית", "ישראל") || Regex("\\bil\\b").containsMatchIn(n) -> "🇮🇱"
            has("adult", "xxx", "18+") -> "🔞"
            has("religio", "dini", "דת") -> "🕍"
            else -> "📺"
        }
    }
}

/** The categories marked with a star, saved on this device. */
object FavoriteCategories {
    private val _names = MutableStateFlow<Set<String>>(emptySet())
    val names: StateFlow<Set<String>> = _names
    private var loaded = false

    private fun prefs(context: Context) = context.getSharedPreferences("categories", Context.MODE_PRIVATE)

    fun load(context: Context): StateFlow<Set<String>> {
        if (!loaded) {
            loaded = true
            _names.value = prefs(context).getStringSet("favorites", emptySet()).orEmpty().toSet()
        }
        return names
    }

    /** Stars or un-stars [name]; returns whether it's a favorite now. */
    fun toggle(context: Context, name: String): Boolean {
        load(context)
        val now = name !in _names.value
        _names.value = if (now) _names.value + name else _names.value - name
        prefs(context).edit().putStringSet("favorites", _names.value).apply()
        AppLog.i("categories", (if (now) "starred " else "unstarred ") + name)
        return now
    }

    /** For tests: forget what was loaded. */
    internal fun reset() { loaded = false; _names.value = emptySet() }
}

/** A heading above part of the category list ("★ Favorite categories", "All categories · 38"). */
@Composable
fun CategoryHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = Amber,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** The phone's row for a category: picture, name, how many channels, star, arrow. */
@Composable
fun CategoryRow(category: Category, onClick: () -> Unit, onToggleFavorite: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(Categories.icon(category.key.ifEmpty { category.name }), fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (category.key == Category.ALL) FontWeight.SemiBold else FontWeight.Normal)
            Text(channelsCount(category.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (category.key != Category.ALL && category.key.isNotBlank()) {
            IconButton(onClick = onToggleFavorite) {
                if (category.favorite) Icon(Icons.Default.Star, contentDescription = t("Remove category from favorites"), tint = Amber)
                else Icon(Icons.Outlined.StarBorder, contentDescription = t("Add category to favorites"))
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The TV's tile for a category; [selected] is the remote's cursor (see HomeKeys). */
@Composable
fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val scale by animateFloatAsState(if (selected) 1.06f else 1f, label = "category scale")
    Box(
        modifier
            .fillMaxWidth()
            .zIndex(if (selected) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (selected) 24.dp else 0.dp, shape, ambientColor = Color.White, spotColor = Color.White)
            .border(4.dp, if (selected) Color.White else Color.Transparent, shape)
            .background(if (selected) FocusCard else MaterialTheme.colorScheme.surfaceContainer, shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Categories.icon(category.key.ifEmpty { category.name }), fontSize = 26.sp)
                Spacer(Modifier.weight(1f))
                if (category.favorite) Icon(Icons.Default.Star, contentDescription = t("Favorite category"), tint = Amber)
            }
            Column {
                Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(channelsCount(category.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
