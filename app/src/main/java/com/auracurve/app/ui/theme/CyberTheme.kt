package com.auracurve.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "Cyber-Audio / Neon Dark" design system in one place (Design/DESIGN_SPEC.md §3): colors,
 * gradients, glow and sizes. Plain objects, no composition needed, so a Canvas or a draw cache can
 * read them too. Brushes are built once here and never per frame; a brush that has to follow a
 * plot's frame (the curve, its fill) is made by a function the caller keeps in its draw cache.
 */
object CyberTheme {
    val colors = CyberColors
    val brushes = CyberBrushes
    val glow = CyberGlow
    val dims = CyberDims
}

/** The tokens of CyberColor.kt plus the Sonic ones the redesign keeps, by role. */
object CyberColors {
    val void = CyberVoid
    val voidRaised = CyberVoidRaised
    val surface = CyberSurface
    val surfaceHigh = CyberSurfaceHigh
    val bar = CyberBar
    val outline = CyberOutline
    val grid = CyberGridLine
    val purple = CyberPurple
    val violet = CyberViolet
    val cyan = CyberCyan
    val cyanText = CyberCyanText
    val magenta = CyberMagenta
    val orchid = CyberOrchid
    val orange = CyberOrange
    val red = CyberRed
    val amber = CyberAmber
    val yellow = CyberYellow

    // Kept from Sonic: their meaning does not change with the look.
    val text = SonicPaper
    val mutedText = SonicSteel
    val accentText = SonicViolet
    val signal = SonicSignal
    val error = SonicClip

    /** The 1 px shine at the top edge of glass. */
    val glassShine = Color(0x0FFFFFFF)
}

/** The gradients of §3.2. Sizes follow the element they are drawn in unless a function takes them. */
object CyberBrushes {
    /** 1 dp border of cards and HUD frames, diagonal (135°). */
    val border: Brush =
        Brush.linearGradient(
            listOf(CyberMagenta.copy(alpha = 0.7f), CyberPurple.copy(alpha = 0.7f), CyberCyan.copy(alpha = 0.7f)),
        )

    /** Fill of active pills, segments, chips and switches. White text sits over the blue middle. */
    val pill: Brush = Brush.horizontalGradient(listOf(CyberPillStart, CyberPillMid, CyberPillEnd))

    /** 1.5 dp border of active pills. */
    val pillBorder: Brush = Brush.horizontalGradient(listOf(CyberMagenta, CyberCyan))

    /** The response curve and other frequency strokes over the element's own width. */
    val curve: Brush = Brush.horizontalGradient(listOf(CyberCurveBass, CyberCurveMid, CyberCurveTreble))

    /** Spectrum bars: light at the top, fading toward the floor. */
    val spectrum: Brush = Brush.verticalGradient(listOf(CyberSpectrumTop.copy(alpha = 0.75f), CyberPurple.copy(alpha = 0.25f)))

    /** Brushed metal of knobs and the power and play keys, swept around the center. */
    val metal: Brush =
        Brush.sweepGradient(listOf(CyberMetalDark, CyberMetalLight, CyberMetalShade, CyberMetalShine, CyberMetalDark))

    /** The top edge of the navigation bar. */
    val barEdge: Brush =
        Brush.horizontalGradient(
            listOf(CyberPurple.copy(alpha = 0.8f), CyberCyan.copy(alpha = 0.8f), CyberPurple.copy(alpha = 0.8f)),
        )

    /** The curve gradient laid over a plot frame, so 20 Hz is orchid and 20 kHz cyan wherever the curve ends. */
    fun curve(
        startX: Float,
        endX: Float,
    ): Brush = Brush.horizontalGradient(listOf(CyberCurveBass, CyberCurveMid, CyberCurveTreble), startX = startX, endX = endX)

    /** The area under the curve: the curve's middle tone fading out toward the floor of the plot. */
    fun curveFill(
        top: Float,
        bottom: Float,
    ): Brush =
        Brush.verticalGradient(
            listOf(CyberCurveMid.copy(alpha = 0.28f), CyberCurveMid.copy(alpha = 0f)),
            startY = top,
            endY = bottom,
        )
}

/** How far and how strongly a glow reaches (§3.5). Glow only on these, never on text or backgrounds. */
data class GlowSpec(
    val radius: Dp,
    val alpha: Float,
)

object CyberGlow {
    /** Power key when on; the alpha pulses between [POWER_PULSE_LOW] and its alpha (§3.5). */
    val power = GlowSpec(18.dp, 0.6f)
    const val POWER_PULSE_LOW = 0.35f
    const val POWER_PULSE_MS = 2400

    val play = GlowSpec(20.dp, 0.5f)
    val thumb = GlowSpec(8.dp, 0.6f)
    val activePill = GlowSpec(10.dp, 0.45f)
    val navIndicator = GlowSpec(12.dp, 0.6f)
    val card = GlowSpec(6.dp, 0.12f)
}

/** Sizes of the look (§3.3, §3.4, §3.6). Radii come from the theme shapes, not from here. */
object CyberDims {
    val border = 1.dp
    val activeBorder = 1.5.dp
    val hudCornerLength = 10.dp
    val hudCornerStroke = 2.dp
    val gridStep = 24.dp
    val minTouch = 48.dp
}
