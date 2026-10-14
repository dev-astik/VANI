package com.example.vani.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.vani.ui.components.IconTile
import com.example.vani.ui.components.VaniCard
import com.example.vani.ui.theme.VaniTheme
import com.example.vani.ui.wakeword.WakeWordController
import com.example.vani.ui.wakeword.WakeWordStatus

// ---------- Models ----------

enum class SettingsAction { Theme, Voice, Notifications, QuickActions, Help, Privacy, About }

private data class SettingItem(
    val action: SettingsAction,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val appSettings = listOf(
    SettingItem(SettingsAction.Voice, "Voice & Speech", "Language, voice, recognition", Icons.Filled.Mic),
)

private val aboutSettings = listOf(
    SettingItem(SettingsAction.Help, "Help & Support", "Get help or feedback", Icons.AutoMirrored.Filled.HelpOutline),
    SettingItem(SettingsAction.Privacy, "Privacy Policy", "Read our policy", Icons.Filled.Shield),
    SettingItem(SettingsAction.About, "About VANI", "Version 1.0.0", Icons.Filled.Info),
)

// ---------- Wake word card ----------

@Composable
private fun WakeWordCard() {
    val c = VaniTheme.colors
    val context = LocalContext.current

    // ON by default (WakeWordController.isEnabled returns true until the user turns it off)
    var enabled by remember { mutableStateOf(WakeWordController.isEnabled(context)) }

    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    fun hasMicPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) {
            Log.d("VaniWake", "Mic permission granted, starting service")
            WakeWordController.start(context)
            enabled = true
        } else {
            Log.d("VaniWake", "Mic permission denied")
            WakeWordStatus.text = "Error: microphone permission denied"
            WakeWordController.stop(context)
            enabled = false
        }
    }

    // Turn on automatically the first time this card appears
    LaunchedEffect(Unit) {
        if (enabled) {
            if (hasMicPermission()) {
                Log.d("VaniWake", "Auto-start: permission already granted")
                WakeWordController.start(context)
            } else {
                Log.d("VaniWake", "Auto-start: asking for permission")
                permissionLauncher.launch(permissions)
            }
        }
    }

    VaniCard {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTile(Icons.Filled.Hearing, c.torchBg, c.torch, size = 40.dp, cornerRadius = 12.dp, iconSize = 22.dp)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hello VANI", color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (enabled) WakeWordStatus.text else "Wake word, listens in background",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        Log.d("VaniWake", "Toggle pressed: $on")
                        if (on) {
                            if (hasMicPermission()) {
                                WakeWordController.start(context)
                                enabled = true
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        } else {
                            WakeWordController.stop(context)
                            enabled = false
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.primary),
                )
            }
            if (enabled && WakeWordStatus.lastHeard.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Last heard: ${WakeWordStatus.lastHeard}", color = c.textSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ---------- Screen ----------

@Composable
fun SettingsScreen(
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

        SectionTitle("Voice Activation")
        WakeWordCard()

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