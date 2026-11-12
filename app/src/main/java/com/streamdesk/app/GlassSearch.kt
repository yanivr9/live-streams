package com.streamdesk.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/** The frosted look shared by the search button and bar: see-through white with a light rim and a soft shadow. */
@Composable
private fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape): Modifier {
    // Frosted white over the dark theme; frosted grey over the light one
    val tint = MaterialTheme.colorScheme.onSurface
    val light = !isAppDark()
    return this
        .shadow(if (light) 4.dp else 8.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape)
        .background(Brush.verticalGradient(if (light) listOf(Color.White, Color(0xFFEFF2F4)) else listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.07f))))
        .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = if (light) 0.18f else 0.32f), tint.copy(alpha = if (light) 0.08f else 0.10f))), shape)
}

/** A round glass button for the phone's top bar. */
@Composable
fun GlassButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.padding(horizontal = 4.dp).size(42.dp).glass(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The search bar the glass button opens into: filters as you type; ✕ clears it and closes it. */
@Composable
fun GlassSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClose: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Row(
        modifier.fillMaxWidth().height(46.dp).glass(CircleShape).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Search channels", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(Amber),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)).clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close search", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
        }
    }
}
