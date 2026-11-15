package com.auracurve.app.ui.equalizer

import androidx.compose.ui.graphics.Color
import com.auracurve.app.ui.theme.SonicAmber
import com.auracurve.app.ui.theme.SonicCaution
import com.auracurve.app.ui.theme.SonicClip
import com.auracurve.app.ui.theme.SonicGraphite
import com.auracurve.app.ui.theme.SonicGraphiteControl
import com.auracurve.app.ui.theme.SonicGraphiteRaised
import com.auracurve.app.ui.theme.SonicPaper
import com.auracurve.app.ui.theme.SonicRule
import com.auracurve.app.ui.theme.SonicSignal
import com.auracurve.app.ui.theme.SonicSteel

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
    val signal: Color,
    val caution: Color,
    val hot: Color,
    val border: Color,
)

internal val SonicStyle =
    EqualizerDesignStyle(
        background = SonicGraphite,
        surface = SonicGraphiteRaised,
        surfaceVariant = SonicGraphiteControl,
        text = SonicPaper,
        mutedText = SonicSteel,
        accent = SonicAmber,
        signal = SonicSignal,
        caution = SonicCaution,
        hot = SonicClip,
        border = SonicRule,
    )
