package com.auracurve.app.ui.components.cyber

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auracurve.app.ui.components.hardcore.HcSquareKey
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberGlow
import com.auracurve.app.ui.theme.LocalHardcoreLook
import com.auracurve.app.ui.theme.cyberGlow

// The round keys: power, play and the secondary transport keys. Brushed metal rings, and the
// only glow besides thumbs and active pills (Design/DESIGN_SPEC.md §3.5): the power halo while
// processing and the play halo.

/**
 * An alpha that breathes between [low] and [high] every [periodMs] while [running]; null when it
 * does not (off, in the background or with animations off), so a static glow can take over.
 */
@Composable
internal fun rememberPulse(
    running: Boolean,
    low: Float,
    high: Float,
    periodMs: Int,
): State<Float>? {
    val active = running && rememberIsResumed() && !rememberReducedMotion()
    if (!active) return null
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = low,
        targetValue = high,
        animationSpec = infiniteRepeatable(tween(periodMs / 2), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
}

/**
 * The EQ's power key: a dark key in a metal bezel. On: a cyan ring and icon inside a breathing
 * violet and cyan halo. Off: a gray icon and no light, so off reads without color too.
 */
@Composable
fun CyberPowerKey(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val pulse = rememberPulse(checked, CyberGlow.POWER_PULSE_LOW, CyberGlow.power.alpha, CyberGlow.POWER_PULSE_MS)
    val halo: () -> Float = { if (checked) pulse?.value ?: CyberGlow.power.alpha else 0f }
    Box(
        modifier =
            modifier
                .size(size)
                .cyberGlow(CyberColors.violet, CyberGlow.power.radius, CircleShape, halo)
                .cyberGlow(CyberColors.cyan, CyberGlow.power.radius / 2, CircleShape) { halo() * 0.6f }
                .drawWithCache {
                    val radius = this.size.minDimension / 2f
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val bezel = Stroke(width = 3.dp.toPx())
                    val ring = Stroke(width = 1.5.dp.toPx())
                    val face =
                        Brush.radialGradient(
                            listOf(CyberColors.surfaceHigh, CyberColors.void),
                            center = Offset(center.x, center.y - radius * 0.3f),
                            radius = radius * 1.2f,
                        )
                    onDrawBehind {
                        drawCircle(face, radius, center)
                        drawCircle(CyberBrushes.metal, radius - bezel.width / 2f, center, style = bezel)
                        drawCircle(
                            color = if (checked) CyberColors.cyan else CyberColors.outline,
                            radius = radius - bezel.width - 3.dp.toPx(),
                            center = center,
                            style = ring,
                        )
                    }
                }.clip(CircleShape)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = null,
            tint = if (checked) CyberColors.cyan else CyberColors.mutedText,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

/**
 * The main play/pause key: a violet disc in a metal ring with a purple halo, which breathes
 * slowly while [playing]. Disabled: a flat dark disc without light. [content] is the icon with
 * its description, as for the PlayKey it replaces. At least 48 dp to touch, also for the small
 * keys in list rows.
 */
@Composable
fun CyberPlayKey(
    playing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    // The halo; off for small keys inside list rows, whose clip would cut it.
    glow: Boolean = true,
    content: @Composable () -> Unit,
) {
    val pulse = rememberPulse(playing && enabled && glow, CyberGlow.play.alpha * 0.6f, CyberGlow.play.alpha, PLAY_PULSE_MS)
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(size)
                .then(
                    if (enabled && glow) {
                        Modifier.cyberGlow(CyberColors.purple, CyberGlow.play.radius, CircleShape) {
                            pulse?.value ?: CyberGlow.play.alpha
                        }
                    } else {
                        Modifier
                    },
                ).drawWithCache {
                    val radius = this.size.minDimension / 2f
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val ringWidth = (radius * 0.12f).coerceAtLeast(2.dp.toPx())
                    val discRadius = radius - ringWidth
                    val disc =
                        Brush.radialGradient(
                            listOf(CyberColors.violet, CyberColors.purple, PlayDiscEdge),
                            center = Offset(center.x - discRadius * 0.25f, center.y - discRadius * 0.35f),
                            radius = discRadius * 1.5f,
                        )
                    val edge = Stroke(width = 1.dp.toPx())
                    onDrawBehind {
                        if (enabled) {
                            drawCircle(CyberBrushes.metal, radius, center)
                            drawCircle(disc, discRadius, center)
                            drawCircle(CyberColors.cyan.copy(alpha = 0.5f), discRadius, center, style = edge)
                        } else {
                            drawCircle(CyberColors.surfaceHigh, radius, center)
                            drawCircle(CyberColors.outline, radius - edge.width / 2f, center, style = edge)
                        }
                    }
                }.clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (enabled) Color.White else CyberColors.mutedText) { content() }
    }
}

private const val PLAY_PULSE_MS = 3200
private val PlayDiscEdge = Color(0xFF3E1470)

/**
 * A secondary round key (previous, next, shuffle, repeat, header actions): a dark glass disc in a
 * thin metal ring, cyan ring and icon when [selected]. At least 48 dp to touch.
 */
@Composable
fun CyberRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 52.dp,
    content: @Composable () -> Unit,
) {
    if (LocalHardcoreLook.current) {
        HcSquareKey(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            selected = selected,
            size = if (size < 46.dp) 46.dp else size,
            content = content,
        )
        return
    }
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(size)
                .drawWithCache {
                    val radius = this.size.minDimension / 2f
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val ring = Stroke(width = 1.5.dp.toPx())
                    val face =
                        Brush.radialGradient(
                            listOf(CyberColors.surfaceHigh, CyberColors.voidRaised),
                            center = Offset(center.x, center.y - radius * 0.4f),
                            radius = radius * 1.3f,
                        )
                    onDrawBehind {
                        drawCircle(face, radius, center)
                        if (selected && enabled) {
                            drawCircle(CyberColors.cyan.copy(alpha = 0.12f), radius, center)
                            drawCircle(CyberColors.cyan, radius - ring.width / 2f, center, style = ring)
                        } else {
                            drawCircle(
                                brush = CyberBrushes.metal,
                                radius = radius - ring.width / 2f,
                                center = center,
                                alpha = if (enabled) 0.8f else 0.35f,
                                style = ring,
                            )
                        }
                    }
                }.clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint =
            when {
                !enabled -> CyberColors.mutedText.copy(alpha = 0.5f)
                selected -> CyberColors.cyan
                else -> CyberColors.text
            }
        CompositionLocalProvider(LocalContentColor provides tint) { content() }
    }
}
