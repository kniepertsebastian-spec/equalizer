package com.hardbasseq.eq.ui.theme

import androidx.compose.ui.graphics.Color

// "Sonic Control": one palette for the whole app, taken from studio hardware - graphite
// panels, amber for what the user operates, green only when the effect really runs.
// Every color here has one meaning; none is decoration.

/** Screen background: graphite, not black, so the tonal steps above it stay visible. */
val SonicGraphite = Color(0xFF16181B)

/** One step up: sections, the navigation bar, sheets. */
val SonicGraphiteRaised = Color(0xFF1E2125)

/** Two steps up: controls, the plot area, unselected chips. */
val SonicGraphiteControl = Color(0xFF292D32)

/** Hairlines: grid, dividers, outlines of unselected controls. */
val SonicRule = Color(0xFF3A3F46)

/** Primary text and values. */
val SonicPaper = Color(0xFFEAE7E2)

/** Secondary text, axis labels, inactive icons. */
val SonicSteel = Color(0xFF8D949D)

/** Primary accent: the curve, selected and adjustable controls, the main action. */
val SonicAmber = Color(0xFFFFA826)

/** Signal green: the effect is confirmed to be running on the audio path. Nothing else. */
val SonicSignal = Color(0xFF4FD08A)

/** Pale yellow for "worth a look" states, e.g. a limiter that works now and then. */
val SonicCaution = Color(0xFFE3CC72)

/** Clipping, heavy limiting and errors. */
val SonicClip = Color(0xFFFF5A52)

/** Background behind an error message. */
val SonicClipContainer = Color(0xFF3A1F1E)
