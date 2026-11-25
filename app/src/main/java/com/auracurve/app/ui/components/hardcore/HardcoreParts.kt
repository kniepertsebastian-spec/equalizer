package com.auracurve.app.ui.components.hardcore

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.auracurve.app.R
import com.auracurve.app.ui.theme.HardcoreColors
import com.auracurve.app.ui.theme.HardcoreType
import com.auracurve.app.ui.theme.HcCorner
import com.auracurve.app.ui.theme.HcKeyShape
import com.auracurve.app.ui.theme.HcPlateShape
import com.auracurve.app.ui.theme.HcScratches
import com.auracurve.app.ui.theme.LocalHcHeaderSlot
import com.auracurve.app.ui.theme.LocalSectionNumber
import com.auracurve.app.ui.theme.drawInsetShade
import com.auracurve.app.ui.theme.drawStripes
import com.auracurve.app.ui.theme.hcKeyFace
import com.auracurve.app.ui.theme.hcWell
import com.auracurve.app.ui.theme.hcWorn
import com.auracurve.app.ui.theme.ledBrush
import com.auracurve.app.ui.theme.ledHalo
import kotlin.math.abs
import kotlin.math.roundToInt

// The hardcore building blocks (Design/DESIGN_SPEC.md, decision H1). Only drawing changes:
// every block keeps the semantics of the component it stands in for (roles, states, text for
// TalkBack and tests, 48 dp touch targets), and no state rests on color alone - keys sink when
// on, lamps light, knobs move.

/**
 * Stamped lettering: shown in capitals, read out (and found by tests) as written. [worn] rubs
 * some ink off, for headings.
 */
@Composable
fun HcText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    worn: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val label = text
    Box(modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(label) }) {
        Text(
            text = label.uppercase(),
            style = style,
            color = color,
            maxLines = maxLines,
            textAlign = textAlign,
            overflow = overflow,
            modifier = if (worn) Modifier.hcWorn() else Modifier,
        )
    }
}

/** A round lamp: lit in [color] with a halo, dark red when off, or dark gray when [gray]. */
@Composable
fun HcLed(
    on: Boolean,
    modifier: Modifier = Modifier,
    color: Color = HardcoreColors.bloodHi,
    gray: Boolean = false,
    size: Dp = 7.dp,
) {
    Spacer(
        modifier =
            modifier.size(size).drawWithCache {
                val r = this.size.minDimension / 2f
                val c = this.size.center
                val haloRadius = r + 6.dp.toPx()
                val lit = ledBrush(color, c, r)
                val halo = ledHalo(color, c, haloRadius)
                val off =
                    Brush.radialGradient(
                        if (gray) listOf(LedGrayHi, LedGrayLo) else listOf(LedOffHi, LedOffLo),
                        center = c,
                        radius = r,
                    )
                onDrawBehind {
                    if (on) {
                        drawCircle(halo, haloRadius, c)
                        drawCircle(lit, r, c)
                    } else {
                        drawCircle(off, r, c)
                    }
                }
            },
    )
}

private val LedOffHi = Color(0xFF5A1D17)
private val LedOffLo = Color(0xFF2A0D0B)
private val LedGrayHi = Color(0xFF3A3733)
private val LedGrayLo = Color(0xFF151413)

/**
 * A section as a steel plate: an edge, cut corners, a dark face with fine scratches. Drawn into
 * its own layer, so scrolling moves it without drawing it again.
 */
@Composable
fun HcPlate(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer()
                .drawWithCache {
                    val outer = Path().apply { addOutline(HcPlateShape.createOutline(size, layoutDirection, this@drawWithCache)) }
                    val edge = 1.dp.toPx()
                    val inner =
                        Path().apply {
                            addOutline(
                                PlateInnerShape.createOutline(
                                    Size(size.width - 2f * edge, size.height - 2f * edge),
                                    layoutDirection,
                                    this@drawWithCache,
                                ),
                            )
                            translate(Offset(edge, edge))
                        }
                    val face = Brush.verticalGradient(listOf(PlateTop, PlateBottom))
                    onDrawBehind {
                        drawPath(outer, HardcoreColors.edge)
                        drawPath(inner, face)
                        clipPath(inner) { with(HcScratches) { drawScratches() } }
                    }
                }.padding(start = 15.dp, end = 15.dp, top = 7.dp, bottom = 17.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private val PlateInnerShape = CutCornerShape(topStart = 11.6.dp, bottomEnd = 11.6.dp)
private val PlateTop = Color(0xFF161514)
private val PlateBottom = Color(0xFF111010)

/**
 * A section's header: the running number in red, the title stamped and worn, a rule that ends in
 * a hatch, then the end slot (small keys) and the info key. The info text opens below.
 */
@Composable
fun HcSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    val number = LocalSectionNumber.current
    val glow = with(LocalDensity.current) { 3.dp.toPx() }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (number != null) {
                Text(
                    text = number.toString().padStart(2, '0'),
                    style = HardcoreType.sectionIndex.copy(shadow = Shadow(HardcoreColors.blood.copy(alpha = 0.7f), blurRadius = glow)),
                    color = HardcoreColors.bloodHi,
                    modifier = Modifier.padding(end = 8.dp).clearAndSetSemantics {},
                )
            }
            TitleWithRule(title = title, modifier = Modifier.weight(1f))
            CompositionLocalProvider(LocalHcHeaderSlot provides true) {
                trailing()
            }
            if (info != null) {
                IconButton(onClick = { infoOpen = !infoOpen }) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = stringResource(if (infoOpen) R.string.info_less else R.string.info_more),
                        tint = if (infoOpen) HardcoreColors.bone else HardcoreColors.ash,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
        AnimatedVisibility(visible = infoOpen && info != null) {
            Text(
                text = info.orEmpty(),
                color = HardcoreColors.ash,
                style = HardcoreType.note,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

// The title takes what it needs (at most all but a short rule), the rule fills the rest.
@Composable
private fun TitleWithRule(
    title: String,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            HcText(text = title, style = HardcoreType.sectionTitle, color = HardcoreColors.bone, worn = true)
            HcRule()
        },
    ) { measurables, constraints ->
        val gap = 10.dp.roundToPx()
        val minRule = 12.dp.roundToPx()
        val ruleHeight = 7.dp.roundToPx()
        val titlePlaceable =
            measurables[0].measure(
                constraints.copy(minWidth = 0, maxWidth = (constraints.maxWidth - gap - minRule).coerceAtLeast(0)),
            )
        val ruleWidth = (constraints.maxWidth - titlePlaceable.width - gap * 2).coerceAtLeast(0)
        val rulePlaceable = measurables[1].measure(Constraints.fixed(ruleWidth, ruleHeight))
        val height = maxOf(titlePlaceable.height, ruleHeight, constraints.minHeight)
        layout(constraints.maxWidth, height) {
            titlePlaceable.place(0, (height - titlePlaceable.height) / 2)
            rulePlaceable.place(titlePlaceable.width + gap, (height - ruleHeight) / 2)
        }
    }
}

/** A thin rule ending in a short hatch, after a section title. */
@Composable
fun HcRule(modifier: Modifier = Modifier) {
    Spacer(
        modifier =
            modifier.drawWithCache {
                val hatch = 22.dp.toPx()
                val gap = 6.dp.toPx()
                val line = 1.dp.toPx()
                val pitch = 4.dp.toPx()
                val stripe = 1.5.dp.toPx()
                onDrawBehind {
                    val cy = size.height / 2f
                    val lineEnd = size.width - hatch - gap
                    if (lineEnd > 0f) drawLine(HardcoreColors.edge, Offset(0f, cy), Offset(lineEnd, cy), line)
                    if (size.width >= hatch) {
                        inset(left = size.width - hatch, top = 0f, right = 0f, bottom = 0f) {
                            drawStripes(HardcoreColors.edgeHi, pitch, stripe)
                        }
                    }
                }
            },
    )
}

/**
 * A utility key: a metal key with a cut corner, an icon and a stamped label. Small (34 dp)
 * inside a section header, 42 dp elsewhere; always 48 dp to touch. It sinks while pressed.
 */
@Composable
fun HcUtilityKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    small: Boolean = LocalHcHeaderSlot.current,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val height = if (small) 34.dp else 42.dp
    Row(
        modifier =
            modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).padding(vertical = (48.dp - height) / 2)
                .height(height)
                .hcKeyFace(pressed = pressed, shape = HcKeyShape)
                .padding(horizontal = if (small) 10.dp else 12.dp)
                .offset(y = if (pressed) 1.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HardcoreColors.ash,
                modifier = Modifier.size(if (small) 16.dp else 18.dp),
            )
        }
        HcText(
            text = text,
            style = if (small) HardcoreType.keySmall else HardcoreType.key,
            color = HardcoreColors.bone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A key with only an icon, as wide as its slot: the sound goal's step keys. */
@Composable
fun HcIconKey(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier =
            modifier
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { this.contentDescription = contentDescription }
                .padding(vertical = 4.dp)
                .height(40.dp)
                .hcKeyFace(pressed = pressed, shape = HcKeyShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = HardcoreColors.bone,
            modifier = Modifier.size(20.dp).offset(y = if (pressed) 1.dp else 0.dp),
        )
    }
}

/**
 * A square key (the source picker, the band readout's steps): [size] to see, at least 48 dp to
 * touch. Sinks while pressed or when [selected].
 */
@Composable
fun HcSquareKey(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    size: Dp = 46.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sunk = pressed || selected
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(size)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .hcKeyFace(pressed = sunk, shape = SquareKeyShape)
                .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (selected) HardcoreColors.bloodHi else HardcoreColors.bone
        CompositionLocalProvider(LocalContentColor provides tint) {
            Box(modifier = Modifier.offset(y = if (sunk) 1.dp else 0.dp)) { content() }
        }
    }
}

private val SquareKeyShape = RoundedCornerShape(3.dp)

/**
 * A bank of mechanical keys in a sunk tray: the equalizer's tabs. The chosen key is pressed in,
 * its label turns bone-white and the lamp strip above it lights.
 */
@Composable
fun HcKeyBank(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .hcWell(color = BankTray, edge = HardcoreColors.edge, shape = RoundedCornerShape(3.dp), depth = 6.dp)
                .padding(4.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .hcKeyFace(pressed = selected)
                        .drawWithCache {
                            val strip = Size(26.dp.toPx(), 3.dp.toPx())
                            val topLeft = Offset((size.width - strip.width) / 2f, 7.dp.toPx())
                            val halo =
                                ledHalo(
                                    HardcoreColors.bloodHi,
                                    topLeft + Offset(strip.width / 2f, strip.height / 2f),
                                    strip.width * 0.6f,
                                    0.5f,
                                )
                            onDrawBehind {
                                if (selected) {
                                    drawCircle(halo, strip.width * 0.6f, topLeft + Offset(strip.width / 2f, strip.height / 2f))
                                    drawRect(HardcoreColors.bloodHi, topLeft, strip)
                                } else {
                                    drawRect(HardcoreColors.bloodLo, topLeft, strip)
                                }
                            }
                        }.selectable(selected = selected, role = role, onClick = { onSelect(index) })
                        .padding(top = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                HcText(
                    text = label,
                    style = HardcoreType.tab,
                    color = if (selected) HardcoreColors.bone else HardcoreColors.ash,
                    maxLines = 1,
                    modifier = Modifier.offset(y = if (selected) 1.dp else 0.dp),
                )
            }
        }
    }
}

private val BankTray = Color(0xFF070707)

/**
 * A slide switch: a heavy knob in a sunk track, over the option it selects. Each option is its own
 * touch target (half the track) with its label for TalkBack and tests; the drawn labels are
 * hidden from them, so every option is found exactly once. [selectedIndex] null shows no knob.
 * [hazardIndex] stripes an option that needs care (loud presets). [compact] is the narrow form
 * next to the hold-to-compare pad.
 */
@Composable
fun HcSlideSwitch(
    options: List<String>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Tab,
    hazardIndex: Int? = null,
    compact: Boolean = false,
) {
    val count = options.size.coerceAtLeast(1)
    val position by animateFloatAsState(targetValue = (selectedIndex ?: 0).toFloat(), label = "slideKnob")
    val labelStyle = if (compact) HardcoreType.option.copy(fontSize = HardcoreType.option.fontSize * 0.93f) else HardcoreType.option
    BoxWithConstraints(
        modifier =
            modifier
                .height(if (compact) 56.dp else 50.dp)
                .hcWell(color = SwitchTrack, edge = HardcoreColors.edge, shape = RoundedCornerShape(3.dp), depth = 6.dp),
    ) {
        val segment = maxWidth / count
        // What is seen: the engraved labels, hazard stripes and the knob. Hidden from semantics.
        Box(modifier = Modifier.matchParentSize().clearAndSetSemantics {}) {
            Row(modifier = Modifier.matchParentSize()) {
                options.forEachIndexed { index, label ->
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (index == hazardIndex) {
                                        Modifier.padding(3.dp).drawBehind {
                                            drawStripes(HardcoreColors.hazard, 12.dp.toPx(), 6.dp.toPx(), alpha = 0.16f)
                                        }
                                    } else {
                                        Modifier
                                    },
                                ).padding(horizontal = if (compact) 6.dp else 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        HcText(
                            text = label,
                            style = labelStyle,
                            color = HardcoreColors.ash2,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (selectedIndex != null) {
                val knobWidth = segment - 4.dp
                Row(
                    modifier =
                        Modifier
                            .offset {
                                val first = 3.dp.roundToPx()
                                val step = segment.roundToPx()
                                val x = position * step + first - (position.coerceIn(0f, 1f) * 2.dp.roundToPx())
                                IntOffset(x.roundToInt(), 3.dp.roundToPx())
                            }.width(knobWidth)
                            .fillMaxHeight()
                            .padding(bottom = 6.dp)
                            .hcKeyFace(pressed = false, top = KnobTop, bottom = KnobBottom, edge = KnobEdge)
                            .padding(horizontal = if (compact) 7.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val gripFirst = !compact && selectedIndex == count - 1 && count > 1
                    val gripLast = !compact && selectedIndex == 0 && count > 1
                    if (gripFirst) Grip()
                    HcLed(on = true)
                    HcText(
                        text = options[selectedIndex],
                        style = labelStyle,
                        color = HardcoreColors.bone,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    if (gripLast) Grip()
                }
            }
        }
        // What is touched and read: one transparent target per option.
        Row(modifier = Modifier.matchParentSize().selectableGroup()) {
            options.forEachIndexed { index, label ->
                Spacer(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = index == selectedIndex,
                                interactionSource = null,
                                indication = null,
                                role = role,
                                onClick = { onSelect(index) },
                            ).semantics { this.text = AnnotatedString(label) },
                )
            }
        }
    }
}

private val SwitchTrack = Color(0xFF060606)
private val KnobTop = Color(0xFF3D3A35)
private val KnobBottom = Color(0xFF211F1D)
private val KnobEdge = Color(0xFF4D4842)

// Ridges to push the knob by.
@Composable
private fun Grip() {
    Spacer(
        modifier =
            Modifier.size(width = 12.dp, height = 24.dp).drawBehind {
                val line = 1.dp.toPx()
                var x = 0f
                while (x + line * 2 <= size.width) {
                    drawLine(GripDark, Offset(x + line / 2f, 0f), Offset(x + line / 2f, size.height), line)
                    drawLine(GripLight, Offset(x + line * 1.5f, 0f), Offset(x + line * 1.5f, size.height), line)
                    x += line * 4
                }
            },
    )
}

private val GripDark = Color(0xFF151413)
private val GripLight = Color(0xFF55504A)

/**
 * A rocker switch: "I" and "O" halves in a sunk frame. On, the I half is pressed in and the O half
 * stands up red; off, the reverse in gray. Display only: the row around it is the switch.
 */
@Composable
fun HcRocker(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .size(width = 60.dp, height = 34.dp)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .clearAndSetSemantics {}
                .hcWell(color = RockerFrame, edge = RockerEdge, shape = RoundedCornerShape(5.dp), depth = 4.dp)
                .padding(3.dp),
    ) {
        RockerHalf(
            label = "I",
            pressed = checked,
            top = if (checked) Color(0xFF4E110D) else Color(0xFF3C3935),
            bottom = if (checked) Color(0xFF7E1C15) else Color(0xFF2B2926),
            textColor = if (checked) HardcoreColors.bone else Color(0xFF55504A),
            shape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp),
            modifier = Modifier.weight(1f),
        )
        RockerHalf(
            label = "O",
            pressed = !checked,
            top = if (checked) Color(0xFFC0352A) else Color(0xFF131211),
            bottom = if (checked) Color(0xFFA3271D) else Color(0xFF1C1B19),
            textColor = if (checked) Color(0xFF6A1A13) else HardcoreColors.bone,
            shape = RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RockerHalf(
    label: String,
    pressed: Boolean,
    top: Color,
    bottom: Color,
    textColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .hcKeyFace(
                    pressed = pressed,
                    shape = shape,
                    top = top,
                    bottom = bottom,
                    edge = lerpEdge(top),
                    pressedTop = top,
                    pressedBottom = bottom,
                    shadow = false,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = HardcoreType.key,
            color = textColor,
            modifier = Modifier.offset(y = if (pressed) 1.dp else 0.dp),
        )
    }
}

private fun lerpEdge(top: Color) =
    androidx.compose.ui.graphics
        .lerp(top, Color.White, 0.08f)

private val RockerFrame = Color(0xFF050505)
private val RockerEdge = Color(0xFF36322D)

/**
 * A fader: a ruled scale, a black slot with a thin red fill from the origin (0 dB when the range
 * spans it), and a ridged metal cap with a red center line. A Material slider underneath, so
 * dragging, tapping and the TalkBack value actions stay as they were.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HcFader(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val shown = rememberUpdatedState(value)
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(thumbColor = HardcoreColors.bone, activeTrackColor = HardcoreColors.blood),
        thumb = { FaderCap() },
        track = {
            val span = valueRange.endInclusive - valueRange.start
            val origin = if (valueRange.start < 0f && valueRange.endInclusive > 0f) 0f else valueRange.start
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(FaderHeight)
                        .drawWithCache {
                            val major = 1.dp.toPx()
                            val slotTop = 23.dp.toPx()
                            val slotHeight = 7.dp.toPx()
                            val fillTop = slotTop + 2.5.dp.toPx()
                            val fillHeight = 2.dp.toPx()
                            val corner = CornerRadius(1.dp.toPx())

                            fun x(v: Float) = if (span <= 0f) 0f else ((v - valueRange.start) / span).coerceIn(0f, 1f) * size.width
                            val a = x(origin)
                            onDrawBehind {
                                for (i in 0..12) {
                                    val tx = size.width * i / 12f
                                    if (i % 3 == 0) {
                                        drawLine(ScaleMajor, Offset(tx, 2.dp.toPx()), Offset(tx, 12.dp.toPx()), major)
                                    } else {
                                        drawLine(ScaleMinor, Offset(tx, 4.dp.toPx()), Offset(tx, 10.dp.toPx()), major)
                                    }
                                }
                                drawRoundRect(SlotBlack, Offset(-2.dp.toPx(), slotTop), Size(size.width + 4.dp.toPx(), slotHeight), corner)
                                drawLine(
                                    HardcoreColors.edge,
                                    Offset(-2.dp.toPx(), slotTop + slotHeight),
                                    Offset(size.width + 2.dp.toPx(), slotTop + slotHeight),
                                    major,
                                    alpha = 0.6f,
                                )
                                val b = x(shown.value)
                                val left = minOf(a, b)
                                val width = abs(b - a)
                                if (width > 0f) {
                                    drawRect(FaderFill, Offset(left, fillTop), Size(width, fillHeight))
                                }
                            }
                        },
            )
        },
    )
}

// The cap: 22 x 38 dp, set 8 dp down in a box as tall as the track, so its center sits on the slot.
@Composable
private fun FaderCap() {
    Spacer(
        modifier =
            Modifier
                .size(width = 22.dp, height = FaderHeight)
                .drawWithCache {
                    val top = 8.dp.toPx()
                    val capSize = Size(size.width, size.height - top)
                    val metal =
                        Brush.horizontalGradient(
                            listOf(CapDark, CapLight, CapMid, CapLight, CapDark),
                        )
                    val line = 1.dp.toPx()
                    val corner = CornerRadius(2.dp.toPx())
                    onDrawBehind {
                        drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(0f, top + 2.dp.toPx()), capSize, corner)
                        translate(top = top) {
                            drawRoundRect(metal, Offset.Zero, capSize, corner)
                            // ridges, top and bottom third
                            var y = 4.dp.toPx()
                            while (y < capSize.height - 3.dp.toPx()) {
                                if (y < capSize.height * 0.36f || y > capSize.height * 0.64f) {
                                    drawLine(CapRidgeDark, Offset(2.dp.toPx(), y), Offset(capSize.width - 2.dp.toPx(), y), line)
                                    drawLine(
                                        CapRidgeLight,
                                        Offset(2.dp.toPx(), y + line),
                                        Offset(capSize.width - 2.dp.toPx(), y + line),
                                        line,
                                    )
                                }
                                y += 3.dp.toPx()
                            }
                            drawLine(
                                HardcoreColors.bloodHi,
                                Offset(capSize.width / 2f, 2.dp.toPx()),
                                Offset(capSize.width / 2f, capSize.height - 2.dp.toPx()),
                                2.dp.toPx(),
                            )
                            drawRoundRect(CapEdge, Offset.Zero, capSize, corner, style = Stroke(line))
                        }
                    }
                },
    )
}

private val FaderHeight = 46.dp
private val ScaleMajor = Color(0xFF57524B)
private val ScaleMinor = Color(0xFF3A3632)
private val SlotBlack = Color(0xFF030303)
private val FaderFill = Color(0xFF8F2219)
private val CapDark = Color(0xFF26241F)
private val CapLight = Color(0xFF57524B)
private val CapMid = Color(0xFF3A3732)
private val CapEdge = Color(0xFF0D0D0C)
private val CapRidgeDark = Color(0xFF1A1917)
private val CapRidgeLight = Color(0xFF6A645C)

/** A value in a small sunk box next to a control's name: "+2.5 dB". */
@Composable
fun HcValueBox(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = HardcoreType.value,
        color = HardcoreColors.bone,
        modifier = modifier.hcWell(depth = 3.dp).padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

/** A warning: hazard stripes down the left edge, then the text. */
@Composable
fun HcWarn(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = HardcoreColors.ash,
) {
    Text(
        text = text,
        style = HardcoreType.note,
        color = textColor,
        modifier =
            modifier
                .fillMaxWidth()
                .drawBehind {
                    val bar = 6.dp.toPx()
                    inset(left = 0f, top = 0f, right = size.width - bar, bottom = 0f) {
                        drawRect(HardcoreColors.stripeDark)
                        drawStripes(HardcoreColors.hazard, 8.dp.toPx(), 4.dp.toPx())
                    }
                }.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
    )
}

/** A dark readout line, like a display on a rack unit: an icon and the text. */
@Composable
fun HcLcd(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .hcWell(depth = 4.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) Icon(imageVector = icon, contentDescription = null, tint = HardcoreColors.ash, modifier = Modifier.size(20.dp))
        Text(text = text, style = HardcoreType.fieldLabel, color = HardcoreColors.bone)
    }
}

/**
 * The limiter meter of the head: six bars rising left to right, lit from amber to red. [lit] of
 * them glow; the rest stay dark, so the level shows in the count as well as the color.
 */
@Composable
fun HcLimiterBars(
    lit: Int,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier =
            modifier
                .size(width = 51.dp, height = 24.dp)
                .clearAndSetSemantics {}
                .drawBehind {
                    val bar = 6.dp.toPx()
                    val gap = 3.dp.toPx()
                    BarHeights.forEachIndexed { i, h ->
                        val height = h.dp.toPx()
                        drawRect(
                            color = if (i < lit) BarColors[i] else BarOff,
                            topLeft = Offset(i * (bar + gap), size.height - height),
                            size = Size(bar, height),
                        )
                    }
                },
    )
}

private val BarHeights = listOf(8, 11, 14, 17, 20, 24)
private val BarColors =
    listOf(Color(0xFF8B6A2C), Color(0xFF8B6A2C), Color(0xFF9C5A27), Color(0xFFA94524), Color(0xFFB8321F), Color(0xFFB8321F))
private val BarOff = Color(0xFF2A1714)

/** A hazard tape: diagonal stripes in [color] on near-black, for the left edge of a card. */
internal fun Modifier.hcTape(
    color: Color,
    width: Dp = 12.dp,
): Modifier =
    drawBehind {
        val w = width.toPx()
        inset(left = 0f, top = 0f, right = size.width - w, bottom = 0f) {
            drawRect(HardcoreColors.stripeDark)
            drawStripes(color, 10.dp.toPx(), 5.dp.toPx(), alpha = 0.9f)
        }
    }

/** The status card of the head: a hazard tape in the state's color, a lamp, the status, a hint. */
@Composable
fun HcStatusCard(
    label: String,
    hint: String?,
    tapeColor: Color,
    lamp: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .clip(HcCorner)
                .drawBehind {
                    drawRect(HardcoreColors.plate)
                }.hcTape(tapeColor)
                .drawBehind {
                    drawRect(HardcoreColors.edge, style = Stroke(1.dp.toPx()))
                }.clickable(onClick = onClick)
                .heightIn(min = 58.dp)
                .padding(start = 22.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        lamp()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = HardcoreType.statusTitle, color = HardcoreColors.bone)
            if (hint != null) Text(text = hint, style = HardcoreType.small, color = HardcoreColors.ash)
        }
        trailing?.invoke()
    }
}

/** The pressed-in face of a pad (correction profile) and its plain face. */
internal fun Modifier.hcPad(
    on: Boolean,
    shape: androidx.compose.ui.graphics.Shape = HcPadShape,
): Modifier =
    drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val face =
            Brush.radialGradient(
                if (on) listOf(Color(0xFF3A1612), Color(0xFF1A0F0D)) else listOf(Color(0xFF2A2826), Color(0xFF171615)),
                center = Offset(size.width * 0.3f, size.height * 0.2f),
                radius = maxOf(size.width, size.height),
            )
        val glow =
            Brush.radialGradient(
                listOf(HardcoreColors.blood.copy(alpha = 0.18f), Color.Transparent),
                center = size.center,
                radius =
                    size.maxDimension / 1.6f,
            )
        val stroke = Stroke(1.dp.toPx())
        onDrawBehind {
            drawPath(path, face)
            if (on) drawPath(path, glow)
            if (on) clipPath(path) { drawInsetShade(6.dp.toPx(), 0.7f) }
            drawPath(path, if (on) Color(0xFF6B2219) else Color(0xFF33302B), style = stroke)
        }
    }

/** The shape of a pad (correction profile). */
val HcPadShape = RoundedCornerShape(5.dp)

internal const val DISABLED_ALPHA = 0.38f

/**
 * A choice as a small mechanical key: pressed in with a lit lamp when [selected], raised with a
 * dark lamp otherwise. 48 dp to touch. [role] is the one the replaced chip had.
 */
@Composable
fun HcChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    role: Role? = null,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = role,
                    interactionSource = null,
                    indication = null,
                    onClick = onClick,
                ).alpha(if (enabled) 1f else DISABLED_ALPHA)
                .padding(vertical = 4.dp)
                .hcKeyFace(pressed = selected)
                .padding(horizontal = 12.dp)
                .offset(y = if (selected) 1.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        HcLed(on = selected)
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) HardcoreColors.bone else HardcoreColors.ash,
                modifier = Modifier.size(17.dp),
            )
        }
        HcText(
            text = text,
            style = HardcoreType.key,
            color = if (selected) HardcoreColors.bone else HardcoreColors.ash,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
