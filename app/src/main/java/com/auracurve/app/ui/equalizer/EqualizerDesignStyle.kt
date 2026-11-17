package com.auracurve.app.ui.equalizer

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.auracurve.app.ui.theme.SonicCaution
import com.auracurve.app.ui.theme.SonicClip
import com.auracurve.app.ui.theme.SonicGradientEnd
import com.auracurve.app.ui.theme.SonicGradientStart
import com.auracurve.app.ui.theme.SonicGraphite
import com.auracurve.app.ui.theme.SonicGraphiteControl
import com.auracurve.app.ui.theme.SonicGraphiteRaised
import com.auracurve.app.ui.theme.SonicLineEnd
import com.auracurve.app.ui.theme.SonicLineStart
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicRule
import com.auracurve.app.ui.theme.SonicSignal
import com.auracurve.app.ui.theme.SonicSteel
import com.auracurve.app.ui.theme.SonicViolet

/**
 * The theme tokens the equalizer and player components draw with, bundled so a Canvas can
 * read them without a composition. There is one style for every preset: the genre shows in
 * the preset's name and curve, not in the colors.
 */
internal data class EqualizerDesignStyle(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val mutedText: Color,
    val accent: Color,
    /** Gradient behind filled controls (selected chip, tab, power key); white text on it. */
    val accentFillStart: Color,
    val accentFillEnd: Color,
    /** Lighter gradient for strokes: the response curve, mini curves, slider fills. */
    val lineStart: Color,
    val lineEnd: Color,
    val signal: Color,
    val caution: Color,
    val hot: Color,
    val border: Color,
)

/** The accent gradient as a brush, diagonal, for filled controls. */
internal val EqualizerDesignStyle.accentFill: Brush
    get() = Brush.linearGradient(listOf(accentFillStart, accentFillEnd))

/** The stroke gradient across a given width, left (bass) to right (treble). */
internal fun EqualizerDesignStyle.lineBrush(
    startX: Float = 0f,
    endX: Float = Float.POSITIVE_INFINITY,
): Brush = Brush.horizontalGradient(listOf(lineStart, lineEnd), startX = startX, endX = endX)

internal val SonicStyle =
    EqualizerDesignStyle(
        background = SonicGraphite,
        surface = SonicGraphiteRaised,
        surfaceVariant = SonicGraphiteControl,
        text = SonicPaper,
        mutedText = SonicSteel,
        accent = SonicViolet,
        accentFillStart = SonicGradientStart,
        accentFillEnd = SonicGradientEnd,
        lineStart = SonicLineStart,
        lineEnd = SonicLineEnd,
        signal = SonicSignal,
        caution = SonicCaution,
        hot = SonicClip,
        border = SonicRule,
    )
