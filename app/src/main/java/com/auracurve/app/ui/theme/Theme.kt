package com.auracurve.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Dark only: the app is used in dark rooms and cars, and the palette is built for it.
// Cyber-Audio (Design/DESIGN_SPEC.md §3.1): surfaces stay opaque here, because rows that cover
// something (a swiped row over its delete background, a dragged queue entry) draw with them. The
// translucent glass is CyberSurface, used by the Cyber components on purpose. Sheets, menus and
// dialogs are one step above the background so they stand out from it.
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
        surfaceContainerLow = CyberVoidRaised,
        surfaceContainer = SonicGraphiteRaised,
        surfaceContainerHigh = Color(0xFF1A1433),
        surfaceContainerHighest = SonicGraphiteControl,
        outline = CyberOutline,
        outlineVariant = SonicRule,
        error = SonicClip,
        onError = SonicGraphite,
        errorContainer = SonicClipContainer,
        onErrorContainer = SonicPaper,
        scrim = Color(0xFF03020A),
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
