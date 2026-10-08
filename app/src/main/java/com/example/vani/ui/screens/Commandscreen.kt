package com.example.vani.ui.screens
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vani.ui.components.IconTile
import com.example.vani.ui.components.VaniCard
import com.example.vani.ui.components.VaniHeader
import com.example.vani.ui.theme.VaniTheme

// ---------- Models ----------

enum class CommandCategory(val chipLabel: String, val sectionTitle: String) {
    Device("Device", "Device Commands"),
    Navigation("Navigation", "Navigation Commands"),
    Apps("Apps", "Apps Commands"),
    Text("Text", "Text Commands"),
}

enum class CommandStyle { Purple, Blue, Green }

data class VoiceCommand(
    val title: String,
    val example: String,
    val icon: ImageVector,
    val category: CommandCategory,
    val style: CommandStyle,
)

private val allCommands = listOf(
    VoiceCommand("Torch on / off", "Turn on the torch", Icons.Filled.FlashlightOn, CommandCategory.Device, CommandStyle.Purple),
    VoiceCommand("Wi-Fi on / off", "Turn on Wi-Fi", Icons.Filled.Wifi, CommandCategory.Device, CommandStyle.Blue),
    VoiceCommand("Volume up / down", "Volume up", Icons.AutoMirrored.Filled.VolumeUp, CommandCategory.Device, CommandStyle.Green),
    VoiceCommand("Go back", "Go back", Icons.AutoMirrored.Filled.ArrowBack, CommandCategory.Navigation, CommandStyle.Blue),
    VoiceCommand("Go home", "Go home", Icons.Filled.Home, CommandCategory.Navigation, CommandStyle.Blue),
    VoiceCommand("Scroll up / down", "Scroll down", Icons.Filled.SwapVert, CommandCategory.Navigation, CommandStyle.Blue),
    VoiceCommand("Open an app", "Open YouTube", Icons.AutoMirrored.Filled.OpenInNew, CommandCategory.Apps, CommandStyle.Purple),
)

// ---------- Screen ----------

@Composable
fun CommandsScreen(
    onCommandClick: (VoiceCommand) -> Unit = {},
) {
    val c = VaniTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<CommandCategory?>(null) } // null = "All"
    val searchFocus = remember { FocusRequester() }

    val filtered = remember(query, selected) {
        allCommands.filter { cmd ->
            (selected == null || cmd.category == selected) &&
                    (query.isBlank() ||
                            cmd.title.contains(query, ignoreCase = true) ||
                            cmd.example.contains(query, ignoreCase = true))
        }
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
                Icon(
                    Icons.Filled.Search, "Search",
                    tint = c.textSecondary,
                    modifier = Modifier
                        .size(26.dp)
                        .clickable { searchFocus.requestFocus() },
                )
                Spacer(Modifier.size(16.dp))
                Icon(Icons.Filled.FilterList, "Filter", tint = c.textSecondary, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(16.dp))
            SearchField(query, { query = it }, searchFocus)
            Spacer(Modifier.height(12.dp))
            FilterChips(selected) { selected = it }
            Spacer(Modifier.height(4.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        ) {
            CommandCategory.entries.forEach { category ->
                val items = filtered.filter { it.category == category }
                if (items.isNotEmpty()) {
                    item(key = "${category.name}_title") {
                        Text(
                            category.sectionTitle,
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 2.dp),
                        )
                    }
                    item(key = category.name) {
                        VaniCard {
                            Column {
                                items.forEachIndexed { index, cmd ->
                                    if (index > 0) HorizontalDivider(color = c.divider, modifier = Modifier.padding(horizontal = 12.dp))
                                    CommandRow(cmd) { onCommandClick(cmd) }
                                }
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No commands found",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ---------- Search field ----------

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    val c = VaniTheme.colors
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(c.card, shape)
            .border(1.dp, c.cardBorder, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text("Search commands...", color = c.textSecondary, fontSize = 14.sp)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = c.textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, "Clear",
                tint = c.textSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .clickable { onQueryChange("") },
            )
        }
    }
}

// ---------- Filter chips ----------

@Composable
private fun FilterChips(
    selected: CommandCategory?,
    onSelect: (CommandCategory?) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip("All", selected == null) { onSelect(null) }
        CommandCategory.entries.forEach { cat ->
            FilterChip(cat.chipLabel, selected == cat) { onSelect(cat) }
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val c = VaniTheme.colors
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (isSelected) c.primary else c.card)
            .then(if (isSelected) Modifier else Modifier.border(BorderStroke(1.dp, c.cardBorder), shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (isSelected) Color.White else c.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

// ---------- Command row ----------

@Composable
private fun CommandRow(cmd: VoiceCommand, onClick: () -> Unit) {
    val c = VaniTheme.colors
    val (bg, tint) = when (cmd.style) {
        CommandStyle.Purple -> c.torchBg to c.torch
        CommandStyle.Blue -> c.wifiBg to c.wifi
        CommandStyle.Green -> c.volUpBg to c.volUp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(cmd.icon, bg, tint, size = 40.dp, cornerRadius = 11.dp, iconSize = 22.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(cmd.title, color = c.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("\"${cmd.example}\"", color = c.textSecondary, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textSecondary)
    }
}

// ---------- Previews ----------

@Preview(showBackground = true, widthDp = 393, heightDp = 780)
@Composable
private fun CommandsLightPreview() {
    VaniTheme(darkTheme = false) { CommandsScreen() }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 780)
@Composable
private fun CommandsDarkPreview() {
    VaniTheme(darkTheme = true) { CommandsScreen() }
}