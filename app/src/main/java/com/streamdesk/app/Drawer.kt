package com.streamdesk.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ArrowBack

/** The phone's side menu (☰): the app and account at the top, then channels, sharing, settings and help. */
@Composable
fun DrawerColumn(version: String, account: String?, accountDetail: String?, onAccount: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 22.dp, bottom = 16.dp).navigationBarsPadding()) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.splash_icon), contentDescription = null, modifier = Modifier.requiredSize(78.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                BrandName(style = MaterialTheme.typography.titleLarge)
                Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (account != null) {
            Column(
                Modifier
                    .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Amber.copy(alpha = 0.10f))
                    .border(1.dp, Amber.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onAccount)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(account, color = Amber, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (accountDetail != null) Text(accountDetail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        content()
    }
}

@Composable
fun DrawerSection(title: String, divider: Boolean = true) {
    if (divider) HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 22.dp, top = 14.dp, bottom = 4.dp),
    )
}

/** One line of the menu: icon, label, and on the right a count, a switch or a small control. */
@Composable
fun DrawerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val tint = if (selected) Amber else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .padding(end = 14.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(if (selected) Amber.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 22.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Amber else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
fun DrawerCount(n: Int) {
    Text("%,d".format(n), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun DrawerSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked = on,
        onCheckedChange = onChange,
        colors = SwitchDefaults.colors(checkedTrackColor = Amber, checkedThumbColor = FocusInk),
    )
}

/** List | Grid, the chosen one in orange. */
@Composable
fun DrawerSegments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.Start,
    ) {
        options.forEachIndexed { i, o ->
            Text(
                o,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                color = if (i == selected) FocusInk else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(if (i == selected) Amber else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** ☰ → Settings: every setting on one full screen, in groups, with the version at the bottom. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(version: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        androidx.compose.material3.Scaffold(
            topBar = {
                androidx.compose.material3.TopAppBar(
                    navigationIcon = {
                        androidx.compose.material3.IconButton(onClick = onDismiss) {
                            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("Settings") },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                content()
                Text(
                    "Livio $version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp, top = 24.dp),
                )
            }
        }
    }
}
