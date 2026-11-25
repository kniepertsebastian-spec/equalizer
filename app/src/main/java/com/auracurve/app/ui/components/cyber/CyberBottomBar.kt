package com.auracurve.app.ui.components.cyber

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auracurve.app.ui.components.hardcore.HcText
import com.auracurve.app.ui.theme.CyberBrushes
import com.auracurve.app.ui.theme.CyberColors
import com.auracurve.app.ui.theme.CyberGlow
import com.auracurve.app.ui.theme.HardcoreColors
import com.auracurve.app.ui.theme.HardcoreType

/** One tab of [CyberBottomBar]. */
@Immutable
data class CyberNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * The bottom navigation: a dark bar under a fine light edge. The active tab has a cyan
 * trapezoid on the bar's top edge with a glow and a faint light below it, plus a cyan icon and
 * a bright bold label, so where the user is reads from shape and weight, not only from color.
 */
@Composable
fun CyberBottomBar(
    items: List<CyberNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    // The equalizer's hardcore look (decision H1): a perforated steel bar.
    hardcore: Boolean = false,
) {
    if (hardcore) {
        HcBottomBar(items, selectedRoute, onSelect, modifier)
        return
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawWithCache {
                    val edge = 1.dp.toPx()
                    onDrawBehind {
                        drawRect(CyberColors.bar)
                        drawRect(CyberBrushes.barEdge, size = Size(size.width, edge))
                    }
                }.windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val tint = if (selected) CyberColors.cyan else CyberColors.mutedText
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .then(if (selected) Modifier.navIndicator() else Modifier)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.route) })
                        .padding(top = 10.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Icon(imageVector = item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) CyberColors.text else CyberColors.mutedText,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

// The active tab's mark: a trapezoid hanging from the bar's top edge, wide at the top, with a
// cyan glow and a soft cone of light falling onto the icon.
private fun Modifier.navIndicator(): Modifier =
    drawWithCache {
        val topWidth = 56.dp.toPx().coerceAtMost(size.width)
        val bottomWidth = topWidth * 0.7f
        val height = 3.dp.toPx()
        val middle = size.width / 2f
        val shape =
            Path().apply {
                moveTo(middle - topWidth / 2f, 0f)
                lineTo(middle + topWidth / 2f, 0f)
                lineTo(middle + bottomWidth / 2f, height)
                lineTo(middle - bottomWidth / 2f, height)
                close()
            }
        val glow =
            Paint().apply {
                color = CyberColors.cyan
                alpha = CyberGlow.navIndicator.alpha
                nativePaint.maskFilter = BlurMaskFilter(CyberGlow.navIndicator.radius.toPx() / 2f, BlurMaskFilter.Blur.NORMAL)
            }
        val cone =
            Brush.radialGradient(
                listOf(CyberColors.cyan.copy(alpha = 0.16f), CyberColors.cyan.copy(alpha = 0f)),
                center = Offset(middle, 0f),
                radius = size.height * 0.9f,
            )
        onDrawBehind {
            drawRect(cone)
            drawIntoCanvas { it.drawPath(shape, glow) }
            drawPath(shape, CyberColors.cyan)
        }
    }

/**
 * The bottom bar in the hardcore look: perforated steel under a graphite edge. The active tab has
 * a short blood-red bar with a glow on the top edge, a bone icon and a bone stamped label; the
 * others stay ash, so the place reads from the mark and the brightness.
 */
@Composable
private fun HcBottomBar(
    items: List<CyberNavItem>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .drawWithCache {
                    val edge = 1.dp.toPx()
                    val pitch = 5.dp.toPx()
                    val hole = 0.9.dp.toPx()
                    val face = Brush.verticalGradient(listOf(HcBarTop, HcBarBottom))
                    onDrawBehind {
                        drawRect(face)
                        var y = pitch / 2f + edge
                        while (y < size.height) {
                            var x = pitch / 2f
                            while (x < size.width) {
                                drawCircle(HcPerforation, hole, Offset(x, y))
                                x += pitch
                            }
                            y += pitch
                        }
                        drawRect(HcBarEdge, size = Size(size.width, edge))
                    }
                }.windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            val tint = if (selected) HardcoreColors.bone else HardcoreColors.ash
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 74.dp)
                        .then(if (selected) Modifier.hcNavIndicator() else Modifier)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(item.route) })
                        .padding(top = 12.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
            ) {
                Icon(imageVector = item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                HcText(text = item.label, style = HardcoreType.nav, color = tint, maxLines = 1)
            }
        }
    }
}

private val HcBarTop = Color(0xFF1D1C1A)
private val HcBarBottom = Color(0xFF141312)
private val HcBarEdge = Color(0xFF34302B)
private val HcPerforation = Color(0xFF0B0B0A)

private fun Modifier.hcNavIndicator(): Modifier =
    drawWithCache {
        val width = 40.dp.toPx().coerceAtMost(size.width)
        val height = 3.dp.toPx()
        val topLeft = Offset((size.width - width) / 2f, 0f)
        val glow =
            Paint().apply {
                color = HardcoreColors.bloodHi
                alpha = 0.7f
                nativePaint.maskFilter = BlurMaskFilter(5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
        onDrawBehind {
            drawIntoCanvas { it.drawRect(topLeft.x, topLeft.y, topLeft.x + width, topLeft.y + height, glow) }
            drawRect(HardcoreColors.bloodHi, topLeft, Size(width, height))
        }
    }
