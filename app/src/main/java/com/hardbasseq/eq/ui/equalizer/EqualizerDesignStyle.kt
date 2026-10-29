package com.hardbasseq.eq.ui.equalizer

import androidx.compose.ui.graphics.Color
import com.hardbasseq.eq.ui.theme.SonicAmber
import com.hardbasseq.eq.ui.theme.SonicCaution
import com.hardbasseq.eq.ui.theme.SonicClip
import com.hardbasseq.eq.ui.theme.SonicGraphite
import com.hardbasseq.eq.ui.theme.SonicGraphiteControl
import com.hardbasseq.eq.ui.theme.SonicGraphiteRaised
import com.hardbasseq.eq.ui.theme.SonicPaper
import com.hardbasseq.eq.ui.theme.SonicRule
import com.hardbasseq.eq.ui.theme.SonicSignal
import com.hardbasseq.eq.ui.theme.SonicSteel

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
