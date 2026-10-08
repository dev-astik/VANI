package com.example.vani.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class VaniColors(
    val isDark: Boolean,
    // backgrounds
    val bgTop: Color,
    val bgBottom: Color,
    val card: Color,
    val cardBorder: Color,
    val divider: Color,
    val navBar: Color,
    // text
    val textPrimary: Color,
    val textSecondary: Color,
    // brand
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    // tile colors (background + icon tint)
    val torchBg: Color, val torch: Color,
    val wifiBg: Color, val wifi: Color,
    val volUpBg: Color, val volUp: Color,
    val volDownBg: Color, val volDown: Color,
    val youtube: Color,
)

val LightVaniColors = VaniColors(
    isDark = false,
    bgTop = Color(0xFFF1F0FF),
    bgBottom = Color(0xFFFAFAFF),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFEDEBFA),
    divider = Color(0xFFF0EFF7),
    navBar = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF1A1B2E),
    textSecondary = Color(0xFF8B8DA3),
    primary = Color(0xFF7C5CFC),
    primaryLight = Color(0xFF9B7BFF),
    primaryDark = Color(0xFF5B3FE0),
    torchBg = Color(0xFFEEE8FF), torch = Color(0xFF7C5CFC),
    wifiBg = Color(0xFFE2F0FF), wifi = Color(0xFF2F8CFF),
    volUpBg = Color(0xFFDDF6EA), volUp = Color(0xFF22C58B),
    volDownBg = Color(0xFFFFEBDD), volDown = Color(0xFFFF8A3D),
    youtube = Color(0xFFFF2D2D),
)

val DarkVaniColors = VaniColors(
    isDark = true,
    bgTop = Color(0xFF14123A),
    bgBottom = Color(0xFF0A0B18),
    card = Color(0xFF151829),
    cardBorder = Color(0xFF23263A),
    divider = Color(0xFF212438),
    navBar = Color(0xFF0F1120),
    textPrimary = Color(0xFFF2F3FA),
    textSecondary = Color(0xFF9A9DB5),
    primary = Color(0xFF8A6BFF),
    primaryLight = Color(0xFF9B7BFF),
    primaryDark = Color(0xFF5B3FE0),
    torchBg = Color(0xFF2A2650), torch = Color(0xFF9B7BFF),
    wifiBg = Color(0xFF16304F), wifi = Color(0xFF3D9BFF),
    volUpBg = Color(0xFF123A33), volUp = Color(0xFF2ED69C),
    volDownBg = Color(0xFF3D2A1F), volDown = Color(0xFFFF9A55),
    youtube = Color(0xFFFF2D2D),
)

val LocalVaniColors = staticCompositionLocalOf { LightVaniColors }