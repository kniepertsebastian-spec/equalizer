package com.auracurve.app.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.min

// The base modifiers of the Cyber look (Design/DESIGN_SPEC.md §3). Everything that is built from
// the size (paths, brushes, paints) is built in drawWithCache, once per size; the draw pass only
// draws. No RenderEffect and no real background blur: "glass" is a translucent fill over the
// static backdrop, a gradient border and a faint shine at the top edge.

/**
 * The app background: deep violet-black, ambient light in violet (top left), cyan (bottom right)
 * and magenta (bottom left), and a faint 24 dp tech grid. Static: drawn once per size, never
 * animated, so it costs nothing while the app runs.
 */
fun Modifier.cyberBackdrop(strongTop: Boolean = false): Modifier =
    drawWithCache {
        val base = Brush.verticalGradient(listOf(CyberVoidRaised, CyberVoid), endY = size.height * 0.6f)
        val violet =
            Brush.radialGradient(
                listOf(CyberPurple.copy(alpha = if (strongTop) 0.34f else 0.22f), Color.Transparent),
                center = Offset(if (strongTop) size.width * 0.5f else 0f, 0f),
                radius = max(size.width * 0.9f, 1f),
            )
        val cyan =
            Brush.radialGradient(
                listOf(CyberCyan.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(size.width, size.height),
                radius = max(size.width * 0.8f, 1f),
            )
        val magenta =
            Brush.radialGradient(
                listOf(CyberMagenta.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(0f, size.height),
                radius = max(size.width * 0.7f, 1f),
            )
        val step = CyberDims.gridStep.toPx()
        val grid =
            Path().apply {
                var x = step
                while (x < size.width) {
                    moveTo(x, 0f)
                    lineTo(x, size.height)
                    x += step
                }
                var y = step
                while (y < size.height) {
                    moveTo(0f, y)
                    lineTo(size.width, y)
                    y += step
                }
            }
        val gridStroke = Stroke(width = 1f)
        onDrawBehind {
            drawRect(base)
            drawRect(violet)
            drawRect(cyan)
            drawRect(magenta)
            drawPath(grid, CyberGridLine, style = gridStroke)
        }
    }

/**
 * A soft light behind the element in [shape], [radius] wide, for the few things §3.5 allows
 * (power and play keys, thumbs, active pills, the navigation indicator). [alpha] is read in the
 * draw pass, so an animated glow only redraws and never recomposes. A blur mask filter on a
 * paint: hardware accelerated from API 28, the app's minSdk.
 */
fun Modifier.cyberGlow(
    color: Color,
    radius: Dp,
    shape: Shape = RectangleShape,
    alpha: () -> Float,
): Modifier =
    drawWithCache {
        val radiusPx = radius.toPx()
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val paint =
            Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    this.color = color.toArgb()
                    if (radiusPx > 0f) maskFilter = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
                }
            }
        onDrawBehind {
            val a = alpha().coerceIn(0f, 1f)
            if (a > 0f) {
                paint.alpha = a * color.alpha
                drawIntoCanvas { it.drawPath(outline, paint) }
            }
        }
    }

/** A glow with a fixed strength. */
fun Modifier.cyberGlow(
    color: Color,
    glow: GlowSpec,
    shape: Shape = RectangleShape,
): Modifier = cyberGlow(color, glow.radius, shape) { glow.alpha }

/** The 1 dp gradient border of cards and HUD frames. */
fun Modifier.cyberBorder(
    shape: Shape,
    width: Dp = CyberDims.border,
    brush: Brush = CyberBrushes.border,
): Modifier = border(width, brush, shape)

/**
 * Glass: the translucent [color] over the backdrop, a gradient [border] (none when null) and the
 * 1 px shine along the top edge.
 */
fun Modifier.glassSurface(
    shape: Shape,
    color: Color = CyberSurface,
    border: Brush? = CyberBrushes.border,
    borderWidth: Dp = CyberDims.border,
): Modifier {
    val filled = background(color, shape)
    val bordered = if (border != null) filled.border(borderWidth, border, shape) else filled
    return bordered.drawWithCache {
        // The shine stays clear of rounded corners: start and end a corner's width in.
        val inset = min(size.height / 2f, 16f * density)
        val start = Offset(inset, 1f)
        val end = Offset(size.width - inset, 1f)
        onDrawWithContent {
            drawContent()
            if (end.x > start.x) drawLine(CyberColors.glassShine, start, end, strokeWidth = 1f)
        }
    }
}

/**
 * HUD frame corners: an L at each corner, [length] long, [stroke] wide, in [brush]. Decoration
 * only; it says nothing on its own and is not read by TalkBack.
 */
fun Modifier.hudCorners(
    brush: Brush = CyberBrushes.border,
    length: Dp = CyberDims.hudCornerLength,
    stroke: Dp = CyberDims.hudCornerStroke,
): Modifier =
    drawWithCache {
        val l = min(length.toPx(), min(size.width, size.height) / 2f)
        val half = stroke.toPx() / 2f
        val w = size.width
        val h = size.height
        val corners =
            Path().apply {
                moveTo(half, l)
                lineTo(half, half)
                lineTo(l, half)
                moveTo(w - l, half)
                lineTo(w - half, half)
                lineTo(w - half, l)
                moveTo(w - half, h - l)
                lineTo(w - half, h - half)
                lineTo(w - l, h - half)
                moveTo(l, h - half)
                lineTo(half, h - half)
                lineTo(half, h - l)
            }
        val style = Stroke(width = stroke.toPx(), cap = StrokeCap.Square)
        onDrawWithContent {
            drawContent()
            drawPath(corners, brush, style = style)
        }
    }
