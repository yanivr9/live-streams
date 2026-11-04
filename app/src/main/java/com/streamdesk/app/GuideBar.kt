package com.streamdesk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/** What the TV's guide bar shows: the channel, its place in the list, and what's on now and next (null when there's no guide). */
data class GuideBannerInfo(val channel: Channel, val number: Int, val total: Int, val air: NowNext?)

/**
 * The glass bar along the bottom of the picture when a channel starts or OK/▶ is pressed: logo, channel number and
 * name with a LIVE tag, what's on now with a progress bar and its end time, and what's next. Sits inside the TV's
 * safe margins so nothing is cut off at the edge.
 */
@Composable
fun GuideBar(info: GuideBannerInfo, modifier: Modifier = Modifier) = BoxWithConstraints(modifier.fillMaxWidth()) {
    val ch = info.channel
    val air = info.air
    val shape = RoundedCornerShape(24.dp)
    val roomForNext = maxWidth >= 760.dp   // on a narrower screen the guide for "now" gets all the room
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 48.dp, bottom = 40.dp)
            .background(Color(0xC20E1113), shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Logo on a white card, as it is on the tiles; the number when the channel has none
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
            if (ch.logo.startsWith("http") || ch.logo.startsWith("file:///android_asset/")) {
                AsyncImage(model = ch.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().padding(8.dp))
            } else {
                Text("${info.number}", color = FocusInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${info.number}", color = FocusInk, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.background(Amber, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 1.dp),
                )
                Text(ch.displayName(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Row(Modifier.background(OnAir, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color.White, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (air?.title != null) {
                Text(air.title, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LinearProgressIndicator(
                        progress = { air.progress ?: 0f },
                        color = Amber, trackColor = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Text("until ${air.until ?: ""}  ·  ${info.number}/${info.total}", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            } else {
                Text("No guide for this channel", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
        if (roomForNext && air?.nextTitle != null) {
            Box(Modifier.width(1.dp).height(84.dp).background(Color.White.copy(alpha = 0.18f)))
            Column(Modifier.width(280.dp)) {
                Text("NEXT · ${air.nextTime ?: ""}", color = Amber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text(air.nextTitle, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
