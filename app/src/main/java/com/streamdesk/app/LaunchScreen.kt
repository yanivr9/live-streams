package com.streamdesk.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * What the app shows from its first frame until the channels are ready: the logo (gently pulsing), the name and a
 * moving bar. On a TV box the home screen can take seconds to appear, and the window behind it is plain dark, so
 * without this the screen looks black.
 */
@Composable
fun LaunchScreen(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "logo")
    val scale by pulse.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale",
    )
    Box(modifier.fillMaxSize().background(Color(0xFF0E1113)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.splash_icon), contentDescription = null, modifier = Modifier.size(168.dp).scale(scale))
            Spacer(Modifier.height(8.dp))
            BrandName(style = MaterialTheme.typography.displaySmall, color = Color(0xFFECEFF1))
            Text("Live TV, everywhere", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFC4CDD2))
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(modifier = Modifier.width(200.dp), color = Color(0xFFFF9F1C), trackColor = Color(0xFF1E272C))
            Spacer(Modifier.height(12.dp))
            Text("Loading your channels…", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9AA5AB))
        }
    }
}

/** The app's name as it appears in the app: "Livio", with the last "o" in orange. */
@Composable
fun BrandName(
    style: androidx.compose.ui.text.TextStyle,
    color: Color = androidx.compose.ui.graphics.Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            append("Livi")
            pushStyle(androidx.compose.ui.text.SpanStyle(color = Amber))
            append("o")
            pop()
        },
        style = style,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier,
    )
}
