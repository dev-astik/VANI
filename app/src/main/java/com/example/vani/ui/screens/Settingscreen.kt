package com.example.vani.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vani.ui.components.IconTile
import com.example.vani.ui.components.VaniCard
import com.example.vani.ui.theme.VaniTheme

// ---------- Models ----------

enum class SettingsAction { Theme, Voice, Notifications, QuickActions, Help, Privacy, About }

private data class SettingItem(
    val action: SettingsAction,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val appSettings = listOf(
    SettingItem(SettingsAction.Theme, "Light / Dark Theme", "System theme", Icons.Filled.DarkMode),
    SettingItem(SettingsAction.Voice, "Voice & Speech", "Language, voice, recognition", Icons.Filled.Mic),
    SettingItem(SettingsAction.Notifications, "Notifications", "Command alerts, status", Icons.Filled.Notifications),
    SettingItem(SettingsAction.QuickActions, "Quick Actions", "Customize home shortcuts", Icons.Filled.Widgets),
)

private val aboutSettings = listOf(
    SettingItem(SettingsAction.Help, "Help & Support", "Get help or feedback", Icons.AutoMirrored.Filled.HelpOutline),
    SettingItem(SettingsAction.Privacy, "Privacy Policy", "Read our policy", Icons.Filled.Shield),
    SettingItem(SettingsAction.About, "About VANI", "Version 1.0.0", Icons.Filled.Info),
)

// ---------- Screen ----------

@Composable
fun SettingsScreen(
    userName: String = "Astik",
    onProfileClick: () -> Unit = {},
    onSettingClick: (SettingsAction) -> Unit = {},
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
        Spacer(Modifier.height(20.dp))
        Text(
            "Settings",
            color = c.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp),
        )
        Spacer(Modifier.height(16.dp))

        ProfileCard(userName, onProfileClick)

        SectionTitle("App Settings")
        SettingsGroup(appSettings, onSettingClick)

        SectionTitle("About")
        SettingsGroup(aboutSettings, onSettingClick)

        Spacer(Modifier.height(16.dp))
    }
}

// ---------- Pieces ----------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = VaniTheme.colors.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 2.dp),
    )
}

@Composable
private fun ProfileCard(name: String, onClick: () -> Unit) {
    val c = VaniTheme.colors
    VaniCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Brush.linearGradient(listOf(c.primaryLight, c.primaryDark)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.firstOrNull()?.uppercase() ?: "V",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = c.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("Voice Controller", color = c.textSecondary, fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary)
        }
    }
}

@Composable
private fun SettingsGroup(
    items: List<SettingItem>,
    onClick: (SettingsAction) -> Unit,
) {
    val c = VaniTheme.colors
    VaniCard {
        Column {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(color = c.divider, modifier = Modifier.padding(horizontal = 12.dp))
                SettingRow(item) { onClick(item.action) }
            }
        }
    }
}

@Composable
private fun SettingRow(item: SettingItem, onClick: () -> Unit) {
    val c = VaniTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(item.icon, c.torchBg, c.torch, size = 40.dp, cornerRadius = 12.dp, iconSize = 22.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(item.subtitle, color = c.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary)
    }
}

// ---------- Previews ----------

@Preview(showBackground = true, widthDp = 393, heightDp = 780)
@Composable
private fun SettingsLightPreview() {
    VaniTheme(darkTheme = false) { SettingsScreen() }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 780)
@Composable
private fun SettingsDarkPreview() {
    VaniTheme(darkTheme = true) { SettingsScreen() }
}