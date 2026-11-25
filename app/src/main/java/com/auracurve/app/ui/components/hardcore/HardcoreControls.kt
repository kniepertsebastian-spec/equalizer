package com.auracurve.app.ui.components.hardcore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auracurve.app.ui.theme.HardcoreColors
import com.auracurve.app.ui.theme.HardcoreType
import com.auracurve.app.ui.theme.cyberGlow
import com.auracurve.app.ui.theme.drawInsetShade
import com.auracurve.app.ui.theme.drawStripes
import com.auracurve.app.ui.theme.hcKeyFace
import com.auracurve.app.ui.theme.ledHalo
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// The larger hardcore controls: the power key, the hold pad, the bat-handle toggles with their
// tape labels, the sound goal's rotary switch and display, the device keys. As in
// HardcoreParts.kt, only drawing changes; roles, states and actions stay those of the component
// each one stands in for.

/**
 * The master switch: a knurled steel ring, a dark face, a red ring and icon when on (gray when
 * off) and a faint red glow. A switch for TalkBack, with [description] as its name.
 */
@Composable
fun HcPowerKey(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 62.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .cyberGlow(HardcoreColors.bloodHi, 10.dp, CircleShape) { if (checked) 0.35f else 0f }
                .drawWithCache {
                    val r = this.size.minDimension / 2f
                    val c = this.size.center
                    val faceRadius = r - 5.dp.toPx()
                    val face =
                        Brush.radialGradient(
                            listOf(PowerFaceHi, PowerFaceLo),
                            center = Offset(c.x, c.y - faceRadius * 0.35f),
                            radius = faceRadius * 1.3f,
                        )
                    val ring = 2.dp.toPx()
                    val knurl = Path()
                    for (k in 0 until KNURL_TEETH) {
                        val a0 = (k * 8f) * PI.toFloat() / 180f
                        val a1 = (k * 8f + 4f) * PI.toFloat() / 180f
                        knurl.moveTo(c.x, c.y)
                        knurl.lineTo(c.x + r * sin(a0), c.y - r * cos(a0))
                        knurl.lineTo(c.x + r * sin(a1), c.y - r * cos(a1))
                        knurl.close()
                    }
                    onDrawBehind {
                        drawCircle(KnurlDark, r, c)
                        drawPath(knurl, KnurlLight)
                        drawCircle(Color.Black.copy(alpha = 0.55f), faceRadius + 1.dp.toPx(), c)
                        drawCircle(face, faceRadius, c)
                        drawCircle(Color.Black, faceRadius - ring / 2f, c, style = Stroke(ring))
                        drawCircle(
                            if (checked) HardcoreColors.bloodHi else HardcoreColors.ash2,
                            faceRadius - ring * 1.5f - 1.dp.toPx(),
                            c,
                            style = Stroke(ring),
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
            tint = if (checked) HardcoreColors.bloodHi else HardcoreColors.ash,
            modifier = Modifier.size(24.dp),
        )
    }
}

private const val KNURL_TEETH = 45
private val KnurlDark = Color(0xFF1E1C1A)
private val KnurlLight = Color(0xFF4A4640)
private val PowerFaceHi = Color(0xFF2A2825)
private val PowerFaceLo = Color(0xFF0C0C0B)

/**
 * The face of the hold-to-compare pad: a rubber pad in a hazard-striped frame, red while held.
 * Display only; the caller keeps the press gesture and the semantics.
 */
@Composable
fun HcHoldPadFace(
    pressed: Boolean,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(4.dp))
                .drawBehind {
                    drawRect(HoldFrameDark)
                    drawStripes(HardcoreColors.hazard, 12.dp.toPx(), 6.dp.toPx(), rising = false, alpha = 0.95f)
                }.padding(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .drawWithCache {
                    val face =
                        Brush.radialGradient(
                            if (pressed) listOf(HoldPressedHi, HoldPressedLo) else listOf(HoldHi, HoldLo),
                            center = Offset(size.width / 2f, 0f),
                            radius = maxOf(size.width, size.height) * 1.2f,
                        )
                    val bottomShade =
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                            startY = size.height - 6.dp.toPx(),
                            endY = size.height,
                        )
                    onDrawBehind {
                        drawRect(face)
                        if (pressed) {
                            drawInsetShade(8.dp.toPx(), 0.8f)
                        } else {
                            drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, 1f), Offset(size.width, 1f), 1.dp.toPx())
                            drawRect(bottomShade)
                        }
                    }
                }.padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        HcText(
            text = text,
            style = HardcoreType.key.copy(letterSpacing = HardcoreType.option.letterSpacing, lineHeight = 16.sp),
            color = if (pressed) HardcoreColors.bloodHi else HardcoreColors.bone,
            textAlign = TextAlign.Center,
            maxLines = 3,
        )
    }
}

private val HoldFrameDark = Color(0xFF0D0D0C)
private val HoldHi = Color(0xFF2A2826)
private val HoldLo = Color(0xFF151413)
private val HoldPressedHi = Color(0xFF3A1612)
private val HoldPressedLo = Color(0xFF1A0F0D)

/** A strip of torn gaffer tape with a stamped name, slightly crooked. */
@Composable
fun HcGafferLabel(
    text: String,
    modifier: Modifier = Modifier,
    tilt: Float = 0f,
) {
    Box(
        modifier =
            modifier
                .rotate(tilt)
                .drawWithCache {
                    val outline = Path()
                    TornTape.forEachIndexed { i, (fx, fy) ->
                        val x = fx * size.width
                        val y = fy * size.height
                        if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
                    }
                    outline.close()
                    val line = 1.dp.toPx()
                    onDrawBehind {
                        translate(top = line) { drawPath(outline, Color.Black.copy(alpha = 0.8f)) }
                        drawPath(outline, GafferTape)
                        // the weave
                        var y = 0f
                        while (y < size.height) {
                            drawLine(Color.White.copy(alpha = 0.035f), Offset(0f, y), Offset(size.width, y), line / 2f)
                            y += line * 2
                        }
                    }
                }.padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 3.dp),
    ) {
        HcText(text = text, style = HardcoreType.gaffer, color = GafferInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val GafferTape = Color(0xFF34322F)
private val GafferInk = Color(0xFFD9D3C7)

// The torn edges of the tape, as fractions of its size (from the approved draft).
private val TornTape =
    listOf(
        0f to 0.04f,
        0.03f to 0f,
        0.97f to 0.06f,
        1f to 0f,
        0.98f to 0.22f,
        1f to 0.40f,
        0.97f to 0.58f,
        1f to 0.78f,
        0.98f to 1f,
        0.04f to 0.96f,
        0f to 1f,
        0.02f to 0.80f,
        0f to 0.62f,
        0.03f to 0.44f,
        0f to 0.24f,
    )

/**
 * A bat-handle toggle switch, as on old rack gear: a hex nut, a chrome lever with a ball tip,
 * up for on ("I"), down for off ("O"). The lever swings when the state changes. Display only.
 */
@Composable
fun HcBatToggle(
    on: Boolean,
    modifier: Modifier = Modifier,
) {
    val swing by animateFloatAsState(targetValue = if (on) 1f else 0f, label = "batLever")
    val measurer = rememberTextMeasurer()
    val marks = HardcoreType.label.copy(fontSize = 9.sp, letterSpacing = 0.sp)
    Spacer(
        modifier =
            modifier
                .size(width = 66.dp, height = 107.dp)
                .clearAndSetSemantics {}
                .drawWithCache {
                    // Drawn in the draft's 60 x 98 units, scaled to the size.
                    val k = size.width / 60f
                    val cx = 30f
                    val cy = 48f
                    val hex = Path()
                    for (i in 0 until 6) {
                        val a = PI.toFloat() / 6f + i * PI.toFloat() / 3f
                        val x = cx + 15f * cos(a)
                        val y = cy + 15f * sin(a)
                        if (i == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
                    }
                    hex.close()
                    val nut = Brush.linearGradient(listOf(NutHi, NutMid, NutLo), start = Offset(15f, 33f), end = Offset(45f, 63f))
                    val iOn = measurer.measure("I", marks.copy(color = HardcoreColors.bone))
                    val iOff = measurer.measure("I", marks.copy(color = HardcoreColors.ash2))
                    val oOn = measurer.measure("O", marks.copy(color = HardcoreColors.bone))
                    val oOff = measurer.measure("O", marks.copy(color = HardcoreColors.ash2))
                    onDrawBehind {
                        val i = if (swing > 0.5f) iOn else iOff
                        val o = if (swing > 0.5f) oOff else oOn
                        drawText(i, topLeft = Offset((size.width - i.size.width) / 2f, 0f))
                        drawText(o, topLeft = Offset((size.width - o.size.width) / 2f, size.height - o.size.height))
                        scale(k, k, pivot = Offset.Zero) {
                            drawPath(hex, nut)
                            drawPath(hex, NutEdge, style = Stroke(1f))
                            drawCircle(BushingFill, 9f, Offset(cx, cy))
                            drawCircle(BushingEdge, 9f, Offset(cx, cy), style = Stroke(1f))
                            val tipY = 77f + (19f - 77f) * swing
                            drawLever(cx, cy, tipY)
                        }
                    }
                },
    )
}

private fun DrawScope.drawLever(
    cx: Float,
    cy: Float,
    tipY: Float,
) {
    val dir = if (tipY < cy) -1f else 1f
    val lever =
        Path().apply {
            moveTo(cx - 4.5f, cy)
            lineTo(cx - 3.2f, tipY - dir * 2f)
            lineTo(cx + 3.2f, tipY - dir * 2f)
            lineTo(cx + 4.5f, cy)
            close()
        }
    translate(3f, 3f) { drawPath(lever, Color.Black.copy(alpha = 0.5f)) }
    drawPath(
        lever,
        Brush.horizontalGradient(
            0f to ChromeDark,
            0.45f to ChromeHi,
            0.6f to ChromeMid,
            1f to ChromeDark2,
            startX = cx - 4.5f,
            endX = cx + 4.5f,
        ),
    )
    drawCircle(
        Brush.radialGradient(
            0f to BallHi,
            0.5f to BallMid,
            1f to BallLo,
            center = Offset(cx - 5.6f * 0.3f, tipY - 5.6f * 0.4f),
            radius = 5.6f * 1.6f,
        ),
        5.6f,
        Offset(cx, tipY),
    )
}

private val NutHi = Color(0xFF77716A)
private val NutMid = Color(0xFF3B3833)
private val NutLo = Color(0xFF1B1A18)
private val NutEdge = Color(0xFF0B0B0A)
private val BushingFill = Color(0xFF0D0D0C)
private val BushingEdge = Color(0xFF45413B)
private val ChromeDark = Color(0xFF4F4A44)
private val ChromeHi = Color(0xFFE6E0D5)
private val ChromeMid = Color(0xFFA39D94)
private val ChromeDark2 = Color(0xFF3F3B36)
private val BallHi = Color(0xFFF4EFE6)
private val BallMid = Color(0xFF8F8981)
private val BallLo = Color(0xFF2C2A27)

/** The dark panel the toggles sit in. */
@Composable
fun HcModulesPanel(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawWithCache {
                    val stroke = Stroke(1.dp.toPx())
                    val corner = CornerRadius(2.dp.toPx())
                    onDrawBehind {
                        drawRoundRect(ModulesPanel, cornerRadius = corner)
                        drawInsetShade(8.dp.toPx(), 0.8f)
                        drawRoundRect(ModulesEdge, cornerRadius = corner, style = stroke)
                    }
                }.padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        content = content,
    )
}

private val ModulesPanel = Color(0xFF0C0C0B)
private val ModulesEdge = Color(0xFF24221F)

/**
 * One signal module as a toggle: a tape label, a lamp and the icon, the bat-handle switch and
 * the state in words. The whole column is the switch.
 */
@Composable
fun HcModuleTile(
    title: String,
    detail: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    labelTilt: Float = 0f,
) {
    Column(
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        HcGafferLabel(text = title, tilt = labelTilt)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HcLed(on = checked, color = HardcoreColors.toxic)
            Icon(imageVector = icon, contentDescription = null, tint = HardcoreColors.ash, modifier = Modifier.size(15.dp))
        }
        HcBatToggle(on = checked)
        if (detail != null) {
            Text(
                text = detail,
                style = HardcoreType.label.copy(fontSize = 12.5.sp, letterSpacing = HardcoreType.key.letterSpacing),
                color = if (checked) HardcoreColors.bone else HardcoreColors.ash,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The sound goal's rotary switch: [count] stops over 270 degrees, numbered, a ridged pointer
 * knob. Tap or drag to a stop. For TalkBack it is an adjustable control named [description]
 * whose state is [stateDescription]; swiping up and down steps through the stops.
 */
@Composable
fun HcRotarySwitch(
    index: Int,
    count: Int,
    onSelect: (Int) -> Unit,
    description: String,
    stateDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
) {
    val current by rememberUpdatedState(index)
    val select by rememberUpdatedState(onSelect)
    val pointer by animateFloatAsState(targetValue = stopAngle(index, count), label = "rotaryPointer")
    val measurer = rememberTextMeasurer()
    val numberStyle = HardcoreType.label.copy(fontSize = 10.sp, letterSpacing = 0.sp)
    val last = (count - 1).coerceAtLeast(0)

    fun pick(
        position: Offset,
        area: Size,
    ) {
        val dx = position.x - area.width / 2f
        val dy = position.y - area.height / 2f
        val degrees = atan2(dx, -dy) * 180f / PI.toFloat()
        val step = if (last == 0) 1f else 270f / last
        val stop = ((degrees + 135f) / step).roundToInt().coerceIn(0, last)
        if (stop != current) select(stop)
    }
    Spacer(
        modifier =
            modifier
                .size(size)
                .semantics {
                    contentDescription = description
                    this.stateDescription = stateDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(index.toFloat(), 0f..last.toFloat(), steps = (count - 2).coerceAtLeast(0))
                    setProgress { target ->
                        val stop = target.roundToInt().coerceIn(0, last)
                        if (stop != current) select(stop)
                        true
                    }
                }.pointerInput(count) {
                    detectTapGestures { pick(it, Size(this.size.width.toFloat(), this.size.height.toFloat())) }
                }.pointerInput(count) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        pick(change.position, Size(this.size.width.toFloat(), this.size.height.toFloat()))
                    }
                }.drawWithCache {
                    val k = this.size.width / 132f
                    val c = Offset(66f, 66f)
                    val numbers = List(count) { i -> measurer.measure((i + 1).toString(), numberStyle) }
                    val cap =
                        Brush.radialGradient(
                            0f to CapHi,
                            0.6f to CapMid,
                            1f to CapLo,
                            center = Offset(66f - 30f + 60f * 0.38f, 66f - 30f + 60f * 0.3f),
                            radius = 60f * 0.85f,
                        )
                    val pointerShape =
                        Path().apply {
                            moveTo(c.x - 11f, c.y + 12f)
                            quadraticTo(c.x, c.y + 19f, c.x + 11f, c.y + 12f)
                            lineTo(c.x + 4f, c.y - 36f)
                            quadraticTo(c.x, c.y - 40f, c.x - 4f, c.y - 36f)
                            close()
                        }
                    val pointerFill = Brush.verticalGradient(listOf(PointerHi, PointerLo), startY = c.y - 40f, endY = c.y + 19f)
                    onDrawBehind {
                        scale(k, k, pivot = Offset.Zero) {
                            drawCircle(DialWell, 63f, c)
                            drawCircle(HardcoreColors.edge, 63f, c, style = Stroke(1f))
                            for (i in 0 until count) {
                                val a = stopAngle(i, count) * PI.toFloat() / 180f
                                val selected = i == current
                                drawLine(
                                    if (selected) HardcoreColors.bloodHi else HardcoreColors.ash2,
                                    Offset(c.x + 52f * sin(a), c.y - 52f * cos(a)),
                                    Offset(c.x + 59f * sin(a), c.y - 59f * cos(a)),
                                    if (selected) 3f else 1.6f,
                                )
                            }
                        }
                        // The numbers are measured in pixels, so they are placed outside the scale.
                        for (i in 0 until count) {
                            val a = stopAngle(i, count) * PI.toFloat() / 180f
                            val label = numbers[i]
                            val tx = (c.x + 44f * sin(a)) * k
                            val ty = (c.y - 44f * cos(a)) * k
                            drawText(
                                label,
                                color = if (i == current) HardcoreColors.bone else NumberDim,
                                topLeft = Offset(tx - label.size.width / 2f, ty - label.size.height / 2f),
                            )
                        }
                        scale(k, k, pivot = Offset.Zero) {
                            for (i in 0 until 60) {
                                val a = i * 6f * PI.toFloat() / 180f
                                drawLine(
                                    if (i % 2 == 1) KnurlRidge else KnurlGroove,
                                    Offset(c.x + 30f * sin(a), c.y - 30f * cos(a)),
                                    Offset(c.x + 34f * sin(a), c.y - 34f * cos(a)),
                                    2f,
                                )
                            }
                            drawCircle(cap, 30f, c)
                            rotate(pointer, pivot = c) {
                                translate(0f, 2f) { drawPath(pointerShape, Color.Black.copy(alpha = 0.6f)) }
                                drawPath(pointerShape, pointerFill)
                                drawPath(pointerShape, Color.Black, style = Stroke(1f))
                                drawLine(HardcoreColors.bone, Offset(c.x, c.y - 6f), Offset(c.x, c.y - 34f), 2.2f, cap = StrokeCap.Round)
                            }
                            drawCircle(CapCenter, 7f, c)
                            drawCircle(CapCenterEdge, 7f, c, style = Stroke(1f))
                        }
                    }
                },
    )
}

private fun stopAngle(
    index: Int,
    count: Int,
): Float = if (count <= 1) 0f else -135f + index * 270f / (count - 1)

private val DialWell = Color(0xFF0B0B0A)
private val NumberDim = Color(0xFF6D685F)
private val KnurlRidge = Color(0xFF2C2A27)
private val KnurlGroove = Color(0xFF0E0E0D)
private val CapHi = Color(0xFF3E3B37)
private val CapMid = Color(0xFF191816)
private val CapLo = Color(0xFF0C0C0B)
private val PointerHi = Color(0xFF34312D)
private val PointerLo = Color(0xFF121110)
private val CapCenter = Color(0xFF0C0C0B)
private val CapCenterEdge = Color(0xFF3A3632)

/**
 * A vacuum fluorescent display: "POS n / count" small, the name large in glowing amber (shrunk to
 * fit one line), a row of segments with the current one lit, scanlines over it. Read out as the
 * name; the position is said by the rotary switch.
 */
@Composable
fun HcVfd(
    positionLabel: String,
    name: String,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val glow = Shadow(HardcoreColors.amber.copy(alpha = 0.35f), blurRadius = 16f)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(2.dp))
                .drawWithCache {
                    val stroke = Stroke(1.dp.toPx())
                    onDrawBehind {
                        drawRect(VfdGlass)
                        drawInsetShade(8.dp.toPx(), 0.9f)
                        drawRect(VfdEdge, style = stroke)
                    }
                }.drawWithCache {
                    val line = 1.dp.toPx()
                    val pitch = 3.dp.toPx()
                    onDrawWithContent {
                        drawContent()
                        var y = 0f
                        while (y < size.height) {
                            drawRect(Color.Black.copy(alpha = 0.35f), Offset(0f, y), Size(size.width, line))
                            y += pitch
                        }
                    }
                }.padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = positionLabel.uppercase(), style = HardcoreType.displaySmall, color = VfdDim)
            Text(text = "${index + 1} / $count", style = HardcoreType.displaySmall, color = VfdDim)
        }
        BasicText(
            text = name.uppercase(),
            style = HardcoreType.display.copy(color = HardcoreColors.amber, shadow = glow),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = HardcoreType.display.fontSize),
            modifier = Modifier.padding(top = 2.dp).semantics { contentDescription = name },
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            repeat(count) { i ->
                val on = i == index
                Spacer(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .drawBehind {
                                if (on) {
                                    drawRect(
                                        ledHalo(HardcoreColors.amber, size.center, size.width * 0.7f, 0.4f),
                                        topLeft = Offset(-size.width * 0.2f, -size.height * 1.5f),
                                        size = Size(size.width * 1.4f, size.height * 4f),
                                    )
                                }
                                drawRect(if (on) HardcoreColors.amber else VfdSegmentOff)
                            },
                )
            }
        }
    }
}

private val VfdGlass = Color(0xFF0B0A08)
private val VfdEdge = Color(0xFF2B2620)
private val VfdDim = Color(0xFF7D6234)
private val VfdSegmentOff = Color(0xFF2A2116)

/**
 * A device key in the listening-device grid: a lamp strip on the left, the icon and the stamped
 * name. Pressed in, lit and bone-white when [selected].
 */
@Composable
fun HcDeviceKey(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.RadioButton,
) {
    Row(
        modifier =
            modifier
                .height(56.dp)
                .hcKeyFace(pressed = selected)
                .drawWithCache {
                    val strip = Size(3.dp.toPx(), 22.dp.toPx())
                    val topLeft = Offset(6.dp.toPx(), (size.height - strip.height) / 2f)
                    val c = topLeft + Offset(strip.width / 2f, strip.height / 2f)
                    val halo = ledHalo(HardcoreColors.bloodHi, c, strip.height * 0.6f, 0.45f)
                    val corner = CornerRadius(1.dp.toPx())
                    onDrawBehind {
                        if (selected) drawCircle(halo, strip.height * 0.6f, c)
                        drawRoundRect(if (selected) HardcoreColors.bloodHi else HardcoreColors.bloodLo, topLeft, strip, corner)
                    }
                }.selectable(selected = selected, role = role, onClick = onClick)
                .padding(start = 16.dp, end = 10.dp)
                .padding(top = if (selected) 1.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) HardcoreColors.bone else HardcoreColors.ash,
            modifier = Modifier.size(22.dp),
        )
        HcText(
            text = text,
            style = HardcoreType.key.copy(letterSpacing = HardcoreType.option.letterSpacing),
            color = if (selected) HardcoreColors.bone else HardcoreColors.ash,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A genre crate badge: a small sunk square with the genre's icon in its color. */
@Composable
fun HcBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .drawWithCache {
                    val stroke = Stroke(1.dp.toPx())
                    val corner = CornerRadius(2.dp.toPx())
                    onDrawBehind {
                        drawRoundRect(HardcoreColors.void, cornerRadius = corner)
                        drawInsetShade(3.dp.toPx(), 0.9f)
                        drawRoundRect(BadgeEdge, cornerRadius = corner, style = stroke)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

private val BadgeEdge = Color(0xFF322E2A)

/** A dot grille (speaker cloth) that fades in from the left, for the genre crates. */
@Composable
fun HcGrille(modifier: Modifier = Modifier) {
    Spacer(
        modifier =
            modifier
                .width(70.dp)
                .drawBehind {
                    val pitch = 6.dp.toPx()
                    val r = 1.3.dp.toPx()
                    var y = pitch / 2f
                    while (y < size.height) {
                        var x = pitch / 2f
                        while (x < size.width) {
                            drawCircle(GrilleDot, r, Offset(x, y), alpha = 0.55f * (x / size.width))
                            x += pitch
                        }
                        y += pitch
                    }
                },
    )
}

private val GrilleDot = Color(0xFF050505)

/** The colors of the genre badges: rust, bone, hazard and ash for the hardcore genres. */
fun hcGenreTint(genreId: String): Color {
    val id = genreId.lowercase()
    return when {
        "terror" in id -> HardcoreColors.rust
        "uptempo" in id -> UptempoTint
        "gabber" in id -> HardcoreColors.hazard
        "early" in id -> HardcoreColors.ash
        "frenchcore" in id || "french" in id -> HardcoreColors.bloodHi
        "hardstyle" in id || "rawstyle" in id -> HardcoreColors.amber
        "techno" in id -> HardcoreColors.toxic
        else -> HardcoreColors.ash2
    }
}

private val UptempoTint = Color(0xFFCFC8BB)
