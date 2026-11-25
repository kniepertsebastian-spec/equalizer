package com.auracurve.app.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// The hardcore textures (decision H1): concrete grain on the page, scratches on the plates, worn
// holes in the stamped lettering. All static: generated once per process from fixed seeds,
// drawn from caches, never animated, so they cost nothing per frame.

/** Seeded noise tiles, generated once. One pixel of a tile is drawn as one dp. */
internal object HcNoise {
    /** Large soft blotches, the light patches of a concrete wall. */
    val concrete: Bitmap by lazy {
        val size = 140
        val noise = tileNoise(size, baseCells = 5, octaves = 5, seed = 7)
        pixels(size) { i ->
            val alpha = (1.6f * noise[i] - 0.78f).coerceIn(0f, 1f) * CONCRETE_ALPHA
            argb(alpha, 0x8C, 0x85, 0x7A)
        }
    }

    /** Fine light grain over everything. */
    val grain: Bitmap by lazy {
        val size = 120
        val random = java.util.Random(3)
        pixels(size) {
            val v = (random.nextFloat() + random.nextFloat()) / 2f
            val alpha = (2.2f * v - 1.25f).coerceIn(0f, 1f) * GRAIN_ALPHA
            argb(alpha, 0xFF, 0xFF, 0xFF)
        }
    }

    /** Where stamped lettering has worn off: the alpha removes ink. */
    val wear: Bitmap by lazy {
        val size = 128
        val noise = tileNoise(size, baseCells = 64, octaves = 2, seed = 11)
        val coarse = tileNoise(size, baseCells = 16, octaves = 2, seed = 12)
        pixels(size) { i ->
            val v = noise[i] * 0.7f + coarse[i] * 0.3f
            val keep = (2.35f - 3f * v).coerceIn(0f, 1f)
            argb(1f - keep, 0, 0, 0)
        }
    }

    private const val CONCRETE_ALPHA = 0.24f * 0.55f
    private const val GRAIN_ALPHA = 0.11f

    private fun argb(
        alpha: Float,
        r: Int,
        g: Int,
        b: Int,
    ): Int = ((alpha * 255f).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b

    private inline fun pixels(
        size: Int,
        color: (Int) -> Int,
    ): Bitmap {
        val data = IntArray(size * size) { color(it) }
        return Bitmap.createBitmap(data, size, size, Bitmap.Config.ARGB_8888)
    }

    // Value noise that tiles seamlessly: a random lattice per octave, wrapped at the edges,
    // smoothly interpolated. Values stay around 0.5, like SVG's fractal noise.
    private fun tileNoise(
        size: Int,
        baseCells: Int,
        octaves: Int,
        seed: Long,
    ): FloatArray {
        val out = FloatArray(size * size)
        val random = java.util.Random(seed)
        var cells = baseCells
        var amplitude = 1f
        var total = 0f
        repeat(octaves) {
            val lattice = FloatArray(cells * cells) { random.nextFloat() }
            for (y in 0 until size) {
                val fy = y.toFloat() * cells / size
                val y0 = fy.toInt() % cells
                val y1 = (y0 + 1) % cells
                val ty = smooth(fy - fy.toInt())
                for (x in 0 until size) {
                    val fx = x.toFloat() * cells / size
                    val x0 = fx.toInt() % cells
                    val x1 = (x0 + 1) % cells
                    val tx = smooth(fx - fx.toInt())
                    val top = lattice[y0 * cells + x0] + (lattice[y0 * cells + x1] - lattice[y0 * cells + x0]) * tx
                    val bottom = lattice[y1 * cells + x0] + (lattice[y1 * cells + x1] - lattice[y1 * cells + x0]) * tx
                    out[y * size + x] += (top + (bottom - top) * ty) * amplitude
                }
            }
            total += amplitude
            amplitude *= 0.5f
            cells *= 2
        }
        for (i in out.indices) out[i] /= total
        return out
    }

    private fun tileNoise(
        size: Int,
        baseCells: Int,
        octaves: Int,
        seed: Int,
    ) = tileNoise(size, baseCells, octaves, seed.toLong())

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
}

/** A noise tile as a repeating brush, one tile pixel per [scale] screen pixels. */
internal fun noiseBrush(
    bitmap: Bitmap,
    scale: Float,
): ShaderBrush {
    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    shader.setLocalMatrix(Matrix().apply { setScale(scale, scale) })
    return ShaderBrush(shader)
}

/**
 * The scratch tile of the plates (393 x 700 dp, seed 5): fine light and dark scratches and a few
 * dark blotches, as in the approved draft. Kept in dp; drawn scaled to the screen's density.
 */
internal object HcScratches {
    const val TILE_WIDTH = 393f
    const val TILE_HEIGHT = 700f

    class Bucket(
        val path: Path,
        val color: Color,
        val width: Float,
    )

    class Blotch(
        val center: Offset,
        val radius: Float,
        val alpha: Float,
    )

    val buckets: List<Bucket>
    val blotches: List<Blotch>

    init {
        var seed = 5L

        fun rnd(): Float {
            seed = (seed * 16807L) % 2147483647L
            return seed.toFloat() / 2147483647f
        }
        // color (white, black) x alpha (3 steps) x width (2 steps)
        val paths = HashMap<Int, Path>()
        val specs = HashMap<Int, Pair<Color, Float>>()
        repeat(SCRATCHES) {
            val x = rnd() * TILE_WIDTH
            val y = rnd() * TILE_HEIGHT
            val length = 8f + rnd() * 60f
            val angle = Math.toRadians((rnd() * 50f - 25f).toDouble())
            val white = rnd() < 0.75f
            val opacity = 0.03f + rnd() * 0.05f
            val width = 0.5f + rnd() * 0.7f
            val alphaStep = ((opacity - 0.03f) / 0.05f * 2.999f).toInt()
            val widthStep = if (width < 0.85f) 0 else 1
            val key = (if (white) 0 else 6) + alphaStep * 2 + widthStep
            val path = paths.getOrPut(key) { Path() }
            path.moveTo(x, y)
            path.lineTo(x + length * cos(angle).toFloat(), y + length * sin(angle).toFloat())
            specs[key] =
                (if (white) Color.White else Color.Black).copy(alpha = 0.04f + alphaStep * 0.017f) to
                if (widthStep == 0) 0.65f else 1.0f
        }
        buckets = paths.map { (key, path) -> Bucket(path, specs.getValue(key).first, specs.getValue(key).second) }
        blotches =
            List(BLOTCHES) {
                val x = rnd() * TILE_WIDTH
                val y = rnd() * TILE_HEIGHT
                val r = 10f + rnd() * 40f
                Blotch(Offset(x, y), r, 0.05f + rnd() * 0.08f)
            }
    }

    private const val SCRATCHES = 70
    private const val BLOTCHES = 14

    /** Draws the tile repeated over the whole draw area. */
    fun DrawScope.drawScratches() {
        val d = density
        val stroke = HashMap<Float, Stroke>()
        var ty = 0f
        while (ty * d < size.height) {
            var tx = 0f
            while (tx * d < size.width) {
                scale(d, d, pivot = Offset.Zero) {
                    translate(tx, ty) {
                        blotches.forEach { drawCircle(Color.Black, it.radius, it.center, alpha = it.alpha) }
                        buckets.forEach { b ->
                            drawPath(b.path, b.color, style = stroke.getOrPut(b.width) { Stroke(b.width, cap = StrokeCap.Round) })
                        }
                    }
                }
                tx += TILE_WIDTH
            }
            ty += TILE_HEIGHT
        }
    }
}

/**
 * The page: near-black concrete with soft light patches and fine grain, a faint light from the
 * top and a dark vignette at the bottom. Opaque; replaces the screen background.
 */
fun Modifier.hcConcrete(): Modifier =
    drawWithCache {
        val concrete = noiseBrush(HcNoise.concrete, density * 3f)
        val grain = noiseBrush(HcNoise.grain, density)
        val w = size.width
        val h = size.height
        onDrawBehind {
            drawRect(HardcoreColors.void)
            drawRect(concrete)
            drawRect(grain)
            drawEllipseGlow(Offset(w / 2f, 0f), w * 1.2f, h * 0.6f, Color.White.copy(alpha = 0.035f), 0.6f)
            drawEllipseGlow(Offset(w / 2f, h * 1.2f), w * 1.4f, h * 0.9f, Color.Black.copy(alpha = 0.6f), 0.7f)
        }
    }

// An elliptical radial gradient from [color] at the center to transparent at [stop] of the radii.
private fun DrawScope.drawEllipseGlow(
    center: Offset,
    rx: Float,
    ry: Float,
    color: Color,
    stop: Float,
) {
    if (rx <= 0f || ry <= 0f) return
    val k = ry / rx
    val brush = Brush.radialGradient(0f to color, stop to Color.Transparent, center = center, radius = rx)
    scale(1f, k, pivot = center) {
        val top = center.y + (0f - center.y) / k
        val bottom = center.y + (size.height - center.y) / k
        drawRect(brush, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
    }
}

/**
 * Worn stamped lettering: ink rubbed off in a fixed noise pattern. Draws the content into its own
 * layer and punches the wear out of it. For headings only; body text stays clean.
 */
fun Modifier.hcWorn(): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val wear = noiseBrush(HcNoise.wear, density)
            onDrawWithContent {
                drawContent()
                drawRect(wear, blendMode = BlendMode.DstOut)
            }
        }

private const val SQRT2 = 1.4142135f

/**
 * Diagonal stripes over the whole draw area: [stripe] wide, repeating every [pitch], measured
 * across the stripes. [rising] draws "/" stripes, otherwise "\".
 */
internal fun DrawScope.drawStripes(
    color: Color,
    pitch: Float,
    stripe: Float,
    rising: Boolean = true,
    alpha: Float = 1f,
) {
    val step = pitch * SQRT2
    val h = size.height
    clipRect {
        var x = -h - step
        while (x < size.width + step) {
            if (rising) {
                drawLine(color, Offset(x, h), Offset(x + h, 0f), stripe, alpha = alpha)
            } else {
                drawLine(color, Offset(x, 0f), Offset(x + h, h), stripe, alpha = alpha)
            }
            x += step
        }
    }
}

/** A dark band along the top and the sides, as if the area were sunk into the panel. */
internal fun DrawScope.drawInsetShade(
    depth: Float,
    alpha: Float = 0.9f,
) {
    val shade = Color.Black.copy(alpha = alpha)
    drawRect(Brush.verticalGradient(listOf(shade, Color.Transparent), 0f, depth), size = Size(size.width, depth))
    val side = depth * 0.6f
    drawRect(
        Brush.horizontalGradient(listOf(shade.copy(alpha = alpha * 0.5f), Color.Transparent), 0f, side),
        size = Size(side, size.height),
    )
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, shade.copy(alpha = alpha * 0.5f)), size.width - side, size.width),
        topLeft = Offset(size.width - side, 0f),
        size = Size(side, size.height),
    )
}

/** Corners of keys, wells and cards: barely rounded, like machined metal. */
val HcCorner = RoundedCornerShape(2.dp)

/**
 * A sunk well: displays, value boxes, the tab bank, switch tracks. A flat dark fill, a shade
 * along the top and an edge.
 */
fun Modifier.hcWell(
    color: Color = HardcoreColors.well,
    edge: Color = HardcoreColors.wellEdge,
    shape: Shape = HcCorner,
    depth: Dp = 5.dp,
): Modifier =
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val stroke = Stroke(1.dp.toPx())
        val depthPx = depth.toPx()
        onDrawBehind {
            drawPath(path, color)
            clipPath(path) { drawInsetShade(depthPx) }
            drawPath(path, edge, style = stroke)
        }
    }

/**
 * A mechanical key face. Raised: a dark metal gradient with a light top edge, a black bottom
 * edge and a hard shadow below. Pressed: sunk, darker, shaded from the top. The pressed key's
 * content should move down by 1 dp, so the state shows in the shape, not only in the color.
 */
fun Modifier.hcKeyFace(
    pressed: Boolean,
    shape: Shape = HcCorner,
    top: Color = HardcoreColors.keyTop,
    bottom: Color = HardcoreColors.keyBottom,
    edge: Color = HardcoreColors.keyEdge,
    pressedTop: Color = HardcoreColors.pressedTop,
    pressedBottom: Color = HardcoreColors.pressedBottom,
    shadow: Boolean = true,
): Modifier =
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val stroke = Stroke(1.dp.toPx())
        val drop = 2.dp.toPx()
        val line = 1.dp.toPx()
        val face =
            if (pressed) {
                Brush.verticalGradient(listOf(pressedTop, pressedBottom))
            } else {
                Brush.verticalGradient(listOf(top, bottom))
            }
        val shade = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent), 0f, 6.dp.toPx())
        onDrawBehind {
            if (!pressed && shadow) translate(top = drop) { drawPath(path, HardcoreColors.shadow) }
            drawPath(path, face)
            clipPath(path) {
                if (pressed) {
                    drawRect(shade)
                } else {
                    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, line * 1.5f), Offset(size.width, line * 1.5f), line)
                }
            }
            drawPath(path, if (pressed) HardcoreColors.keyEdgeDark else edge, style = stroke)
            if (!pressed) {
                clipPath(path) {
                    drawLine(
                        HardcoreColors.keyEdgeDark,
                        Offset(0f, size.height - line),
                        Offset(size.width, size.height - line),
                        line * 2f,
                    )
                }
            }
        }
    }

/** A lit lamp's colors: a hot center, the color, a dark rim. */
internal fun ledBrush(
    color: Color,
    center: Offset,
    radius: Float,
): Brush =
    Brush.radialGradient(
        0f to lerp(color, Color.White, 0.45f),
        0.45f to color,
        1f to lerp(color, Color.Black, 0.45f),
        center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
        radius = radius * 1.4f,
    )

/** A soft halo around a lit lamp, as a gradient (no blur pass). */
internal fun ledHalo(
    color: Color,
    center: Offset,
    radius: Float,
    alpha: Float = 0.75f,
): Brush = Brush.radialGradient(listOf(color.copy(alpha = alpha), Color.Transparent), center = center, radius = radius)
