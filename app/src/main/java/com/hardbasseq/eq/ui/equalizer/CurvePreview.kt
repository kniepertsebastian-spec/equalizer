package com.hardbasseq.eq.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.preset.TargetPoint
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

private const val MIN_HZ = 20f
private const val MAX_HZ = 20_000f
private const val MIN_RANGE_DB = 6f

// The resulting curve of a correction profile: frequency on a logarithmic axis (20 Hz - 20 kHz), gain
// up and down from the 0 dB line. A picture to judge by, not a measurement.
@Composable
fun CurvePreview(
    curve: List<TargetPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    val points = curve.sortedBy { it.frequencyHz }
    val rangeDb = max(MIN_RANGE_DB, points.maxOfOrNull { abs(it.gainDb) } ?: 0f)
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val middle = size.height / 2f
            drawLine(axisColor, Offset(0f, middle), Offset(size.width, middle), strokeWidth = 1.dp.toPx())
            if (points.isEmpty()) return@Canvas
            val logMin = log10(MIN_HZ)
            val logSpan = log10(MAX_HZ) - logMin
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = ((log10(point.frequencyHz.coerceIn(MIN_HZ, MAX_HZ)) - logMin) / logSpan) * size.width
                val y = middle - (point.gainDb / rangeDb) * (size.height / 2f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
        }
        Text(
            text = stringResource(R.string.curve_preview_caption, rangeDb.toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
