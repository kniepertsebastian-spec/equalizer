package com.auracurve.app.ui.theme

import androidx.compose.ui.graphics.Color

// "Cyber-Audio / Neon Dark" (Design/DESIGN_SPEC.md §3.1): the tokens the redesign adds on top of
// the Sonic palette in Color.kt. Signal green, error red, paper and steel stay the Sonic ones, so a
// state keeps its color across the redesign. Every token has one job; see the spec for which.

/** App background, the base of the backdrop. */
val CyberVoid = Color(0xFF070510)

/** Upper zone of the backdrop, sheets and dialogs. */
val CyberVoidRaised = Color(0xFF0C071B)

/** Glass cards and list rows: translucent over the static backdrop (no real blur). */
val CyberSurface = Color(0xD1120D24)

/** Inactive pills, fields, knob wells. */
val CyberSurfaceHigh = Color(0xE61A1433)

/** The navigation bar. */
val CyberBar = Color(0xF005040C)

/** Outline of inactive pills, chips and fields. */
val CyberOutline = Color(0xFF4F4C7B)

/** The tech grid of the backdrop (about 5 %). */
val CyberGridLine = Color(0x0D8A7CFF)

/** Primary: ambient light, spectrum bars. */
val CyberPurple = Color(0xFF8A2BE2)

/** Light primary: gradient middle, start of the harshness ring. */
val CyberViolet = Color(0xFF9D4EDD)

/** Accent: active borders, bass ring, navigation indicator. Never for text on dark (too bright for small type). */
val CyberCyan = Color(0xFF00F5FF)

/** Small cyan text: the route line, artist, links. */
val CyberCyanText = Color(0xFF5FD9E6)

/** Start of the border and pill-border gradients, tag chips. */
val CyberMagenta = Color(0xFFFF00AA)

/** Punch ring, bass end of the curve, magenta text (times, "+ Add band"). */
val CyberOrchid = Color(0xFFF64BF0)

/** Limiter LEDs, end of the harshness ring. Clearly apart from the error coral (SonicClip). */
val CyberOrange = Color(0xFFFF5500)

/** Top limiter LED, only for heavy limiting. */
val CyberRed = Color(0xFFFF3B30)

/** Warning icon and border of the status banner. */
val CyberAmber = Color(0xFFFFA35F)

/** Occasional limiting, the HUD status square. */
val CyberYellow = Color(0xFFFFD04A)

// The stops of the gradients (§3.2), named so a Canvas and a Brush use the same values.

/**
 * Active pill fill: violet, blue (under the text, white stays above 4.5:1), teal. The teal is a
 * shade darker than the mockup (#149FD0) so a label running toward the end still reads at 4.5:1.
 */
val CyberPillStart = Color(0xFFA52CF2)
val CyberPillMid = Color(0xFF5A5FE0)
val CyberPillEnd = Color(0xFF1E86C8)

/** Response curve from bass to treble. */
val CyberCurveBass = Color(0xFFF64BF0)
val CyberCurveMid = Color(0xFFA98BFF)
val CyberCurveTreble = Color(0xFF3DF0FF)

/** Light end of the spectrum bars. */
val CyberSpectrumTop = Color(0xFFB9A6FF)

/** Brushed metal of knobs and the power, play keys. */
val CyberMetalDark = Color(0xFF2A2B40)
val CyberMetalLight = Color(0xFF8A88B0)
val CyberMetalShade = Color(0xFF3B3C58)
val CyberMetalShine = Color(0xFFC9C6E8)
