package com.hardbasseq.eq.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

// Dark only: the app is used in dark rooms and cars, and the palette is built for it.
private val SonicColorScheme =
    darkColorScheme(
        primary = SonicAmber,
        onPrimary = SonicGraphite,
        primaryContainer = SonicGraphiteControl,
        onPrimaryContainer = SonicPaper,
        secondary = SonicSignal,
        onSecondary = SonicGraphite,
        secondaryContainer = SonicGraphiteControl,
        onSecondaryContainer = SonicPaper,
        tertiary = SonicCaution,
        onTertiary = SonicGraphite,
        tertiaryContainer = SonicGraphiteControl,
        onTertiaryContainer = SonicPaper,
        background = SonicGraphite,
        onBackground = SonicPaper,
        surface = SonicGraphiteRaised,
        onSurface = SonicPaper,
        surfaceVariant = SonicGraphiteControl,
        onSurfaceVariant = SonicSteel,
        surfaceContainerLowest = SonicGraphite,
        surfaceContainerLow = SonicGraphiteRaised,
        surfaceContainer = SonicGraphiteRaised,
        surfaceContainerHigh = SonicGraphiteControl,
        surfaceContainerHighest = SonicGraphiteControl,
        outline = SonicRule,
        outlineVariant = SonicRule,
        error = SonicClip,
        onError = SonicGraphite,
        errorContainer = SonicClipContainer,
        onErrorContainer = SonicPaper,
    )

// Radii follow hierarchy: small controls are nearly square like hardware keys, sheets and
// dialogs a little softer. Nothing is a pill except switches.
private val SonicShapes =
    Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(10.dp),
        extraLarge = RoundedCornerShape(14.dp),
    )

@Composable
fun HardBassEqTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = SonicColorScheme,
            typography = SonicTypography,
            shapes = SonicShapes,
            content = content,
        )
    }
}
