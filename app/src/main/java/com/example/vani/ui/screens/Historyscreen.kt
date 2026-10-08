package com.example.vani.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vani.ui.components.IconTile
import com.example.vani.ui.components.VaniCard
import com.example.vani.ui.components.VaniChipRow
import com.example.vani.ui.components.VaniHeader
import com.example.vani.ui.theme.VaniTheme

// ---------- Models ----------

enum class HistoryKind { YouTube, Torch, WiFi, Volume, Home, Instagram, Scroll }

data class HistoryItem(
    val title: String,
    val time: String,
    val kind: HistoryKind,
    val category: CommandCategory,
)

private val sampleHistory = listOf(
    HistoryItem("Open YouTube", "Today, 12:40 AM", HistoryKind.YouTube, CommandCategory.Apps),
    HistoryItem("Turn on torch", "Today, 11:52 PM", HistoryKind.Torch, CommandCategory.Device),
    HistoryItem("Turn on Wi-Fi", "Today, 11:30 PM", HistoryKind.WiFi, CommandCategory.Device),
    HistoryItem("Volume up", "Today, 11:12 PM", HistoryKind.Volume, CommandCategory.Device),
    HistoryItem("Go home", "Today, 10:45 PM", HistoryKind.Home, CommandCategory.Navigation),
    HistoryItem("Open Instagram", "Today, 10:20 PM", HistoryKind.Instagram, CommandCategory.Apps),
    HistoryItem("Scroll down", "Today, 09:50 PM", HistoryKind.Scroll, CommandCategory.Navigation),
)

// ---------- Screen ----------

@Composable
fun HistoryScreen(
    history: List<HistoryItem> = sampleHistory,
    onItemClick: (HistoryItem) -> Unit = {},
) {
    val c = VaniTheme.colors
    var selected by remember { mutableStateOf<CommandCategory?>(null) } // null = All
    val labels = remember { listOf("All") + CommandCategory.entries.map { it.chipLabel } }
    val selectedIndex = selected?.let { it.ordinal + 1 } ?: 0

    val filtered = remember(history, selected) {
        history.filter { selected == null || it.category == selected }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .statusBarsPadding(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(12.dp))
            VaniHeader {
                Icon(Icons.Filled.History, "History", tint = c.textSecondary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Command History",
                color = c.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp),
            )
            Spacer(Modifier.height(14.dp))
            VaniChipRow(
                labels = labels,
                selectedIndex = selectedIndex,
                onSelect = { selected = if (it == 0) null else CommandCategory.entries[it - 1] },
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No history yet",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
            } else {
                item {
                    VaniCard {
                        Column {
                            filtered.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(
                                        color = c.divider,
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                    )
                                }
                                HistoryRow(item) { onItemClick(item) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Row ----------

@Composable
private fun HistoryRow(item: HistoryItem, onClick: () -> Unit) {
    val c = VaniTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryIcon(item.kind)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(item.time, color = c.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary)
    }
}

@Composable
private fun HistoryIcon(kind: HistoryKind) {
    val c = VaniTheme.colors
    when (kind) {
        HistoryKind.YouTube -> IconTile(Icons.Filled.PlayArrow, c.youtube, Color.White, size = 44.dp, cornerRadius = 12.dp, iconSize = 26.dp)
        HistoryKind.Torch -> IconTile(Icons.Filled.FlashlightOn, c.torchBg, c.torch, size = 44.dp, cornerRadius = 12.dp)
        HistoryKind.WiFi -> IconTile(Icons.Filled.Wifi, c.wifiBg, c.wifi, size = 44.dp, cornerRadius = 12.dp)
        HistoryKind.Volume -> IconTile(Icons.AutoMirrored.Filled.VolumeUp, c.volUpBg, c.volUp, size = 44.dp, cornerRadius = 12.dp)
        HistoryKind.Home -> IconTile(Icons.Filled.Home, c.wifiBg, c.wifi, size = 44.dp, cornerRadius = 12.dp)
        HistoryKind.Scroll -> IconTile(Icons.Filled.SwapVert, c.wifiBg, c.wifi, size = 44.dp, cornerRadius = 12.dp)
        HistoryKind.Instagram -> Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976),
                            Color(0xFF962FBF), Color(0xFF4F5BD5),
                        ),
                    ),
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ---------- Previews ----------

@Preview(showBackground = true, widthDp = 393, heightDp = 780)
@Composable
private fun HistoryLightPreview() {
    VaniTheme(darkTheme = false) { HistoryScreen() }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 780)
@Composable
private fun HistoryDarkPreview() {
    VaniTheme(darkTheme = true) { HistoryScreen() }
}