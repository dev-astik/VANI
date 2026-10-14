package com.example.vani.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vani.ui.components.QuickActionRunner
import com.example.vani.ui.components.QuickActionPrefs
import com.example.vani.ui.components.QuickAction
import com.example.vani.ui.components.IconTile
import com.example.vani.ui.components.VaniCard
import com.example.vani.ui.theme.VaniTheme
import java.util.Calendar

// ---------- Models ----------

enum class CommandKind { YouTube, Torch }

data class RecentCommand(val title: String, val time: String, val kind: CommandKind)

private val sampleRecent = listOf(
    RecentCommand("Open YouTube", "12:40 AM", CommandKind.YouTube),
    RecentCommand("Turn on torch", "11:52 PM", CommandKind.Torch),
)

private const val QUICK_ACTIONS_PER_ROW = 4

// ---------- Screen ----------

@Composable
fun HomeScreen(
    userName: String = "Astik",
    recentCommands: List<RecentCommand> = sampleRecent,
    onMicClick: () -> Unit = {},
    onThemeToggle: () -> Unit = {},
    onRecentClick: (RecentCommand) -> Unit = {},
) {
    val c = VaniTheme.colors
    val context = LocalContext.current

    // which quick actions are shown (saved on the phone)
    var quickActions by remember { mutableStateOf(QuickActionPrefs.load(context)) }
    var showEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        HomeHeader(
            onThemeToggle = onThemeToggle,
            onNotificationsClick = { openNotificationSettings(context) },
        )
        Spacer(Modifier.height(24.dp))
        Greeting(userName)
        Spacer(Modifier.height(8.dp))
        MicSection(onMicClick)
        Spacer(Modifier.height(16.dp))
        QuickActionsCard(
            actions = quickActions,
            onClick = { action ->
                val result = QuickActionRunner.run(context, action)
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
            },
            onEdit = { showEditor = true },
        )
        Spacer(Modifier.height(16.dp))
        RecentCommandsCard(recentCommands, onRecentClick)
        Spacer(Modifier.height(16.dp))
    }

    if (showEditor) {
        QuickActionEditor(
            selected = quickActions,
            onToggle = { action ->
                val updated = if (action in quickActions) quickActions - action else quickActions + action
                quickActions = updated
                QuickActionPrefs.save(context, updated)
            },
            onDismiss = { showEditor = false },
        )
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent) }
}

// ---------- Header ----------

@Composable
private fun HomeHeader(
    onThemeToggle: () -> Unit,
    onNotificationsClick: () -> Unit,
) {
    val c = VaniTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(listOf(c.primaryLight, c.primaryDark)),
                    RoundedCornerShape(13.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("VANI", color = c.textPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onNotificationsClick) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = "Notification settings",
                tint = c.textSecondary,
                modifier = Modifier.size(26.dp),
            )
        }
        IconButton(onClick = onThemeToggle) {
            Icon(
                imageVector = if (c.isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = "Toggle theme",
                tint = c.textSecondary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

// ---------- Greeting ----------

private fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good Morning,"
        hour < 17 -> "Good Afternoon,"
        else -> "Good Evening,"
    }
}

@Composable
private fun Greeting(name: String) {
    val c = VaniTheme.colors
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(greetingForNow(), color = c.textSecondary, fontSize = 15.sp)
        Text(name, color = c.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("What can I do for you?", color = c.textSecondary, fontSize = 14.sp)
    }
}

// ---------- Mic button ----------

@Composable
private fun MicSection(onClick: () -> Unit) {
    val c = VaniTheme.colors
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            // soft outer glow
            Box(
                Modifier
                    .size(220.dp)
                    .scale(scale)
                    .background(
                        Brush.radialGradient(listOf(c.primary.copy(alpha = 0.25f), Color.Transparent)),
                        CircleShape,
                    )
            )
            // middle ring
            Box(
                Modifier
                    .size(156.dp)
                    .background(c.primary.copy(alpha = if (c.isDark) 0.18f else 0.12f), CircleShape)
            )
            // main button
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .shadow(24.dp, CircleShape, ambientColor = c.primary, spotColor = c.primary)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(c.primaryLight, c.primaryDark)))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Mic, "Tap to speak", tint = Color.White, modifier = Modifier.size(48.dp))
            }
        }
        Text("Tap to speak", color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// ---------- Quick actions ----------

@Composable
private fun QuickAction.tileColors(): Pair<Color, Color> {
    val c = VaniTheme.colors
    return when (this) {
        QuickAction.Torch, QuickAction.Camera -> c.torchBg to c.torch
        QuickAction.WiFi, QuickAction.Bluetooth -> c.wifiBg to c.wifi
        QuickAction.VolumeUp -> c.volUpBg to c.volUp
        QuickAction.VolumeDown, QuickAction.Mute -> c.volDownBg to c.volDown
    }
}

@Composable
private fun QuickActionsCard(
    actions: List<QuickAction>,
    onClick: (QuickAction) -> Unit,
    onEdit: () -> Unit,
) {
    val c = VaniTheme.colors
    VaniCard {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Quick Actions",
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Tune, "Choose quick actions", tint = c.textSecondary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(14.dp))

            if (actions.isEmpty()) {
                Text(
                    "No quick actions yet. Tap the icon above to add some.",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                )
            }

            actions.chunked(QUICK_ACTIONS_PER_ROW).forEachIndexed { rowIndex, rowItems ->
                if (rowIndex > 0) Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth()) {
                    rowItems.forEach { action ->
                        val (bg, tint) = action.tileColors()
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onClick(action) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            IconTile(action.icon, bg, tint, size = 54.dp, cornerRadius = 16.dp, iconSize = 26.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(action.label, color = c.textSecondary, fontSize = 12.sp)
                        }
                    }
                    // keep the last row aligned with the rows above it
                    repeat(QUICK_ACTIONS_PER_ROW - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionEditor(
    selected: List<QuickAction>,
    onToggle: (QuickAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = VaniTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = {
            Text("Quick Actions", color = c.textPrimary, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Choose what appears on your home screen", color = c.textSecondary, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                QuickAction.entries.forEach { action ->
                    val (bg, tint) = action.tileColors()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(action) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(action.icon, bg, tint, size = 40.dp, cornerRadius = 12.dp, iconSize = 22.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(action.label, color = c.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = action in selected,
                            onCheckedChange = { onToggle(action) },
                            colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = c.primary) }
        },
    )
}

// ---------- Recent commands ----------

@Composable
private fun RecentCommandsCard(
    items: List<RecentCommand>,
    onClick: (RecentCommand) -> Unit,
) {
    val c = VaniTheme.colors
    VaniCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, null, tint = c.textPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Recent Commands", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            items.forEachIndexed { index, item ->
                HorizontalDivider(color = c.divider, modifier = Modifier.padding(top = 12.dp))
                RecentCommandRow(item, onClick = { onClick(item) })
                if (index == items.lastIndex) Spacer(Modifier.height(0.dp))
            }
        }
    }
}

@Composable
private fun RecentCommandRow(item: RecentCommand, onClick: () -> Unit) {
    val c = VaniTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item.kind) {
            CommandKind.YouTube -> IconTile(
                Icons.Filled.PlayArrow, c.youtube, Color.White,
                size = 40.dp, cornerRadius = 10.dp, iconSize = 24.dp,
            )
            CommandKind.Torch -> IconTile(
                Icons.Filled.FlashlightOn, c.torchBg, c.torch,
                size = 40.dp, cornerRadius = 10.dp, iconSize = 22.dp,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(item.time, color = c.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary)
    }
}

// ---------- Previews ----------

@Preview(showBackground = true, widthDp = 393, heightDp = 780)
@Composable
private fun HomeScreenLightPreview() {
    VaniTheme(darkTheme = false) { HomeScreen() }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 780)
@Composable
private fun HomeScreenDarkPreview() {
    VaniTheme(darkTheme = true) { HomeScreen() }
}