package com.auracurve.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// "Sonic Control", violet edition: one palette for the whole app. Blue-black panels in
// tonal steps, a violet-to-blue accent for what the user operates, green only when the
// effect really runs. Every color here has one meaning; none is decoration.

/** Screen background: blue-black, not pure black, so the tonal steps above it stay visible. */
val SonicGraphite = Color(0xFF111019)

/** One step up: cards, sections, the navigation bar, sheets. */
val SonicGraphiteRaised = Color(0xFF1C1B2A)

/** Two steps up: controls, icon wells, unselected chips. */
val SonicGraphiteControl = Color(0xFF2A2840)

/** Hairlines: grid, dividers, outlines of unselected controls. */
val SonicRule = Color(0xFF34324A)

/** Primary text and values. */
val SonicPaper = Color(0xFFECEAF6)

/** Secondary text, axis labels, inactive icons. */
val SonicSteel = Color(0xFF8E8CA8)

/**
 * Primary accent as a single color: the curve's base tone, accent text, thumbs, outlines of
 * selected controls. Light enough to read as text on every surface above.
 */
val SonicViolet = Color(0xFFA390FF)

/** Start of the accent gradient behind filled controls. White text on it stays above 4.5:1. */
val SonicGradientStart = Color(0xFF7A5CFA)

/** End of the accent gradient: the blue the violet runs into. */
val SonicGradientEnd = Color(0xFF4A6CF0)

/** Lighter pair of the gradient, for strokes such as the response curve. */
val SonicLineStart = Color(0xFFB59BFF)
val SonicLineEnd = Color(0xFF6FA0FF)

/** Signal green: the effect is confirmed to be running on the audio path. Nothing else. */
val SonicSignal = Color(0xFF4FD08A)

/** Pale yellow for "worth a look" states, e.g. a limiter that works now and then. */
val SonicCaution = Color(0xFFE3CC72)

/** Clipping, heavy limiting and errors. */
val SonicClip = Color(0xFFFF6B62)

/** Background behind an error message. */
val SonicClipContainer = Color(0xFF3A1E26)

/** Fill of selected and primary controls: the violet-to-blue gradient, diagonal. */
val SonicAccentBrush: Brush = Brush.linearGradient(listOf(SonicGradientStart, SonicGradientEnd))
