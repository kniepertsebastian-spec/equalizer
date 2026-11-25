package com.auracurve.app.ui.components.cyber

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.auracurve.app.ui.components.hardcore.HcChip
import com.auracurve.app.ui.components.hardcore.HcRocker
import com.auracurve.app.ui.components.hardcore.HcUtilityKey
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberDims
import com.auracurve.app.ui.theme.CyberGlow
import com.auracurve.app.ui.theme.LocalHardcoreLook
import com.auracurve.app.ui.theme.PillShape
import com.auracurve.app.ui.theme.cyberGlow
import com.auracurve.app.ui.theme.glassSurface
import com.auracurve.app.ui.theme.hudCorners
import kotlin.math.roundToInt

// The flat building blocks of the Cyber look (Design/DESIGN_SPEC.md §4): cards, pills, segments,
// the toggle, text fields, outline buttons and icon badges. All stateless: values in, callbacks
// out. Selected and on states differ by fill and shape too, never by color alone (§3.8).

private const val DISABLED_ALPHA = 0.38f

/**
 * A glass card: translucent surface, 1 dp gradient border, the shine at the top. With [hud] it
 * also gets the tech corners of a HUD frame, a little outside its own corners.
 */
@Composable
fun CyberCard(
    modifier: Modifier = Modifier,
    hud: Boolean = false,
    shape: Shape = MaterialTheme.shapes.medium,
    border: Brush? = CyberBrushes.border,
    color: Color = CyberColors.surface,
    glow: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .then(if (hud) Modifier.hudCorners().padding(HudInset) else Modifier)
                .then(if (glow) Modifier.cyberGlow(CyberColors.violet, CyberGlow.card, shape) else Modifier)
                .glassSurface(shape, color, border)
                .padding(contentPadding),
        verticalArrangement = verticalArrangement,
    ) {
        CompositionLocalProvider(LocalContentColor provides CyberColors.text) { content() }
    }
}

private val HudInset = 3.dp

/**
 * A pill: filled with the pill gradient, white text and a cyan glow when [selected]; a dark
 * outline with gray text otherwise. 48 dp to touch, about 40 dp to see. [role] is the one the
 * replaced control had (Tab for tabs, none for chips).
 */
@Composable
fun CyberPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    role: Role? = null,
) {
    if (LocalHardcoreLook.current) {
        HcChip(text, selected, onClick, modifier, enabled, leadingIcon, role)
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val lit = selected && enabled
    Row(
        modifier =
            modifier
                .heightIn(min = CyberDims.minTouch)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = role,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ).alpha(if (enabled) 1f else DISABLED_ALPHA)
                .padding(vertical = 4.dp)
                .then(if (lit) Modifier.cyberGlow(CyberColors.cyan, CyberGlow.activePill, PillShape) else Modifier)
                .clip(PillShape)
                .then(
                    if (selected) {
                        Modifier
                            .background(CyberBrushes.pill)
                            .border(CyberDims.activeBorder, CyberBrushes.pillBorder, PillShape)
                    } else {
                        Modifier
                            .background(CyberColors.surfaceHigh)
                            .border(CyberDims.border, CyberColors.outline, PillShape)
                    },
                ).indication(interaction, ripple())
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        val color = if (selected) Color.White else CyberColors.mutedText
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A row of equal pills where one is selected: tabs and segment switches. */
@Composable
fun CyberSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    role: Role? = Role.Tab,
    enabled: Boolean = true,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            CyberPill(
                text = option,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                enabled = enabled,
                role = role,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val ToggleWidth = 52.dp
private val ToggleHeight = 30.dp
private val ToggleThumb = 24.dp

/**
 * The on/off switch, 52 × 30 dp. On: the pill gradient with a white thumb that shows a check and
 * glows. Off: a dark track with a gray thumb that shows a dash. With [onCheckedChange] null it
 * only displays, for rows that are toggleable as a whole.
 */
@Composable
fun CyberToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (LocalHardcoreLook.current) {
        HcRocker(
            checked = checked,
            enabled = enabled,
            modifier =
                if (onCheckedChange != null) {
                    modifier
                        .minimumInteractiveComponentSize()
                        .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    modifier
                },
        )
        return
    }
    val position = animateFloatAsState(targetValue = if (checked) 1f else 0f, animationSpec = tween(160), label = "toggle")
    val interactive =
        if (onCheckedChange != null) {
            Modifier
                .minimumInteractiveComponentSize()
                .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .then(interactive)
                .size(ToggleWidth, ToggleHeight)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .then(
                    if (checked) {
                        Modifier
                            .background(CyberBrushes.pill, PillShape)
                            .border(CyberDims.activeBorder, CyberBrushes.pillBorder, PillShape)
                    } else {
                        Modifier
                            .background(CyberColors.surfaceHigh, PillShape)
                            .border(CyberDims.border, CyberColors.outline, PillShape)
                    },
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset {
                        val inset = 3.dp.toPx()
                        val travel = (ToggleWidth - ToggleThumb).toPx() - 2f * inset
                        IntOffset((inset + position.value * travel).roundToInt(), 0)
                    }.size(ToggleThumb)
                    .then(if (checked && enabled) Modifier.cyberGlow(CyberColors.cyan, CyberGlow.thumb, CircleShape) else Modifier)
                    .background(if (checked) Color.White else CyberColors.mutedText, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (checked) Icons.Default.Check else Icons.Default.Remove,
                contentDescription = null,
                tint = if (checked) CyberColors.purple else CyberColors.voidRaised,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * The text and search field: a pill on glass with a cyan outline, stronger while focused. The
 * same parameters as the PillTextField it styles.
 */
@Composable
fun CyberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = if (focused) CyberDims.activeBorder else CyberDims.border,
                    color = if (focused) CyberColors.cyan else CyberColors.cyan.copy(alpha = 0.55f),
                    shape = PillShape,
                ),
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        shape = PillShape,
        interactionSource = interaction,
        leadingIcon =
            leadingIcon?.let { icon ->
                { Icon(icon, contentDescription = null, tint = if (focused) CyberColors.cyan else CyberColors.mutedText) }
            },
        trailingIcon = trailing?.let { content -> { Box(modifier = Modifier.padding(end = 6.dp)) { content() } } },
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = CyberColors.surface,
                unfocusedContainerColor = CyberColors.surface,
                disabledContainerColor = CyberColors.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = CyberColors.text,
                unfocusedTextColor = CyberColors.text,
                cursorColor = CyberColors.cyan,
                focusedPlaceholderColor = CyberColors.mutedText,
                unfocusedPlaceholderColor = CyberColors.mutedText,
            ),
    )
}

/**
 * A secondary action as an outlined key in [color], with HUD tech corners when [techCorners]:
 * import, add, connect. No glow; it is not a primary control.
 */
@Composable
fun CyberOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    techCorners: Boolean = true,
    color: Color = CyberColors.cyan,
    enabled: Boolean = true,
) {
    if (LocalHardcoreLook.current) {
        HcUtilityKey(text = text, onClick = onClick, modifier = modifier, icon = icon, enabled = enabled)
        return
    }
    val shape = MaterialTheme.shapes.small
    Row(
        modifier =
            modifier
                .heightIn(min = CyberDims.minTouch)
                .alpha(if (enabled) 1f else DISABLED_ALPHA)
                .then(if (techCorners) Modifier.hudCorners(brush = SolidColor(color)) else Modifier)
                .clip(shape)
                .background(color.copy(alpha = 0.08f))
                .border(CyberDims.border, color.copy(alpha = 0.7f), shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A round icon badge in [tint]: a thin ring around a soft fill of the same color. Decorative, so
 * the icon has no description; the row it sits in names the thing.
 */
@Composable
fun GlowIconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .drawWithCache {
                    val radius = this.size.minDimension / 2f
                    val center = Offset(this.size.width / 2f, this.size.height / 2f)
                    val fill = Brush.radialGradient(listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0.06f)), center, radius)
                    val ring = Stroke(width = 1.5.dp.toPx())
                    onDrawBehind {
                        drawCircle(fill, radius, center)
                        drawCircle(tint.copy(alpha = 0.85f), radius - ring.width / 2f, center, style = ring)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}
