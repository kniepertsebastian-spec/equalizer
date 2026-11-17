package com.auracurve.app.ui.theme

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
        primary = SonicViolet,
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

// Radii follow hierarchy: tags and wells are slightly rounded, cards and tiles soft,
// sheets and dialogs softest. Chips, tabs and switches are pills (see PillShape).
private val SonicShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

/** Fully rounded ends: chips, tabs, status pills, the navigation bar. */
val PillShape = RoundedCornerShape(percent = 50)

@Composable
fun AuraCurveTheme(content: @Composable () -> Unit) {
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
