package com.auracurve.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// "Sonic Control", violet edition: one palette for the whole app. Blue-black panels in
// tonal steps, a violet-to-blue accent for what the user operates, green only when the
// effect really runs. Every color here has one meaning; none is decoration.
//
// Cyber-Audio redesign (Design/DESIGN_SPEC.md, decision E1): the names and meanings stay, the
// values move to the Cyber tokens (CyberColor.kt), so every screen that draws with these follows.

/** Screen background: blue-black, not pure black, so the tonal steps above it stay visible. */
val SonicGraphite = CyberVoid

/** One step up: cards, sections, the navigation bar, sheets. */
val SonicGraphiteRaised = Color(0xFF120D24)

/** Two steps up: controls, icon wells, unselected chips. */
val SonicGraphiteControl = Color(0xFF1E1838)

/** Hairlines: grid, dividers, outlines of unselected controls. */
val SonicRule = Color(0xFF342F5A)

/** Primary text and values. */
val SonicPaper = Color(0xFFECEAF6)

/** Secondary text, axis labels, inactive icons. */
val SonicSteel = Color(0xFF8E8CA8)

/**
 * Primary accent as a single color: the curve's base tone, accent text, thumbs, outlines of
 * selected controls. Light enough to read as text on every surface above.
 */
val SonicViolet = Color(0xFFA390FF)

/** Start of the accent gradient behind filled controls (violet). */
val SonicGradientStart = CyberPillStart

/** Middle of the accent gradient: the blue under the label. White text on it stays above 4.5:1. */
val SonicGradientMid = CyberPillMid

/** End of the accent gradient: the teal the blue runs into. */
val SonicGradientEnd = CyberPillEnd

/** Stroke gradient for the response curve and other frequency lines: bass, middle, treble. */
val SonicLineStart = CyberCurveBass
val SonicLineMid = CyberCurveMid
val SonicLineEnd = CyberCurveTreble

/** Signal green: the effect is confirmed to be running on the audio path. Nothing else. */
val SonicSignal = Color(0xFF4FD08A)

/** Pale yellow for "worth a look" states, e.g. a limiter that works now and then. */
val SonicCaution = Color(0xFFE3CC72)

/** Clipping, heavy limiting and errors. */
val SonicClip = Color(0xFFFF6B62)

/** Background behind an error message. */
val SonicClipContainer = Color(0xFF3A1E26)

/** Fill of selected and primary controls: the violet-blue-teal gradient, left to right. */
val SonicAccentBrush: Brush = CyberBrushes.pill
