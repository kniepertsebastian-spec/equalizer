package com.hardbasseq.eq.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = HardBassOrange,
    onPrimary = HardBassDarkBackground,
    primaryContainer = HardBassOrangeVariant,
    onPrimaryContainer = HardBassOnDark,
    secondary = HardBassYellow,
    onSecondary = HardBassDarkBackground,
    background = HardBassDarkBackground,
    onBackground = HardBassOnDark,
    surface = HardBassDarkSurface,
    onSurface = HardBassOnDark,
    surfaceVariant = HardBassDarkSurfaceVariant,
    onSurfaceVariant = HardBassSubtext,
    error = HardBassRed,
)

private val LightColorScheme = lightColorScheme(
    primary = HardBassOrangeVariant,
    onPrimary = HardBassOnDark,
    primaryContainer = HardBassOrange,
    onPrimaryContainer = HardBassDarkBackground,
    secondary = HardBassYellow,
    onSecondary = HardBassDarkBackground,
    background = HardBassDarkBackground,
    onBackground = HardBassOnDark,
    surface = HardBassDarkSurface,
    onSurface = HardBassOnDark,
    surfaceVariant = HardBassDarkSurfaceVariant,
    onSurfaceVariant = HardBassSubtext,
    error = HardBassRed,
)

@Composable
fun HardBassEqTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
