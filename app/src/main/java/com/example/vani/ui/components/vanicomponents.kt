package com.example.vani.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vani.ui.theme.VaniTheme

/** White / dark rounded card used across all screens. */
@Composable
fun VaniCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = VaniTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = c.card,
        border = BorderStroke(1.dp, c.cardBorder),
        shadowElevation = if (c.isDark) 0.dp else 2.dp,
        content = content,
    )
}

/** Rounded square tile holding an icon (Torch, Wi-Fi, list rows, etc.). */
@Composable
fun IconTile(
    icon: ImageVector,
    background: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    cornerRadius: Dp = 14.dp,
    iconSize: Dp = 26.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(background, RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

enum class BottomTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Commands("Commands", Icons.AutoMirrored.Filled.List),
    History("History", Icons.Filled.History),
    Settings("Settings", Icons.Filled.Settings),
}

@Composable
fun VaniBottomBar(
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
) {
    val c = VaniTheme.colors
    Column(Modifier.fillMaxWidth().background(c.navBar)) {
        HorizontalDivider(color = c.divider)
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val tint = if (isSelected) c.primary else c.textSecondary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(
                                if (isSelected) c.primary else Color.Transparent,
                                RoundedCornerShape(1.dp),
                            )
                    )
                }
            }
        }
    }
}