package com.example.vani.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vani.ui.components.IconTile
import com.example.vani.ui.components.VaniCard
import com.example.vani.ui.theme.VaniTheme
import java.util.Calendar

// ---------- Models ----------

enum class QuickAction(val label: String, val icon: ImageVector) {
    Torch("Torch", Icons.Filled.FlashlightOn),
    WiFi("Wi-Fi", Icons.Filled.Wifi),
    VolumeUp("Volume +", Icons.AutoMirrored.Filled.VolumeUp),
    VolumeDown("Volume –", Icons.AutoMirrored.Filled.VolumeDown),
}

enum class CommandKind { YouTube, Torch }

data class RecentCommand(val title: String, val time: String, val kind: CommandKind)
private val sampleRecent = listOf(
    RecentCommand("Open YouTube", "12:40 AM", CommandKind.YouTube),
    RecentCommand("Turn on torch", "11:52 PM", CommandKind.Torch),
)

// ---------- Screen ----------

@Composable
fun HomeScreen(
    userName: String = "Astik",
    recentCommands: List<RecentCommand> = sampleRecent,
    onMicClick: () -> Unit = {},
    onThemeToggle: () -> Unit = {},
    onQuickAction: (QuickAction) -> Unit = {},
    onRecentClick: (RecentCommand) -> Unit = {},
) {
    val c = VaniTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        HomeHeader(onThemeToggle)
        Spacer(Modifier.height(24.dp))
        Greeting(userName)
        Spacer(Modifier.height(8.dp))
        MicSection(onMicClick)
        Spacer(Modifier.height(16.dp))
        QuickActionsCard(onQuickAction)
        Spacer(Modifier.height(16.dp))
        RecentCommandsCard(recentCommands, onRecentClick)
        Spacer(Modifier.height(16.dp))
    }
}

// ---------- Header ----------

@Composable
private fun HomeHeader(onSettingsClick: () -> Unit) {
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
            Text("VANI", color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Voice Controller", color = c.textSecondary, fontSize = 12.sp)
        }
        Icon(
            Icons.Filled.Settings,
            contentDescription = "Settings",
            tint = c.textSecondary,
            modifier = Modifier
                .size(26.dp)
                .clickable(onClick = onSettingsClick),
        )
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
private fun QuickActionsCard(onClick: (QuickAction) -> Unit) {
    val c = VaniTheme.colors
    VaniCard {
        Column(Modifier.padding(16.dp)) {
            Text("Quick Actions", color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuickAction.entries.forEach { action ->
                    val (bg, tint) = when (action) {
                        QuickAction.Torch -> c.torchBg to c.torch
                        QuickAction.WiFi -> c.wifiBg to c.wifi
                        QuickAction.VolumeUp -> c.volUpBg to c.volUp
                        QuickAction.VolumeDown -> c.volDownBg to c.volDown
                    }
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
            }
        }
    }
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