package com.example.vani.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun VaniTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkVaniColors else LightVaniColors

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            background = colors.bgBottom,
            surface = colors.card,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            background = colors.bgBottom,
            surface = colors.card,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    }

    CompositionLocalProvider(LocalVaniColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object VaniTheme {
    val colors: VaniColors
        @Composable @ReadOnlyComposable get() = LocalVaniColors.current
}