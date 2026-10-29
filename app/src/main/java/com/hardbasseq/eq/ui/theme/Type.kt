package com.hardbasseq.eq.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hardbasseq.eq.R

// Barlow descends from DIN road signage, the same lineage as the lettering printed on
// studio gear. Normal width for reading, Semi Condensed for values, axes and band labels,
// where many numbers have to fit side by side.
val Barlow =
    FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
    )

val BarlowSemiCondensed =
    FontFamily(
        Font(R.font.barlow_semicondensed_regular, FontWeight.Normal),
        Font(R.font.barlow_semicondensed_medium, FontWeight.Medium),
        Font(R.font.barlow_semicondensed_semibold, FontWeight.SemiBold),
    )

// Barlow's default digits are proportional; values that change while dragging would jitter.
private const val TABULAR_FIGURES = "tnum"

private fun barlow(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Barlow,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

private fun condensed(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Medium,
) = TextStyle(
    fontFamily = BarlowSemiCondensed,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = TABULAR_FIGURES,
)

// Scale on a 4:5 step (12 - 13 - 15 - 17 - 20 - 24): small enough to keep a dense screen
// calm, distinct enough that a section title never reads like body text.
val SonicTypography =
    Typography(
        headlineSmall = barlow(24, 30, FontWeight.SemiBold, -0.01),
        titleLarge = barlow(20, 26, FontWeight.SemiBold),
        titleMedium = barlow(17, 22, FontWeight.SemiBold),
        titleSmall = barlow(15, 20, FontWeight.Medium),
        bodyLarge = barlow(16, 24),
        bodyMedium = barlow(15, 21),
        bodySmall = barlow(13, 18),
        labelLarge = barlow(15, 20, FontWeight.Medium, 0.01),
        labelMedium = condensed(13, 16),
        labelSmall = condensed(12, 16),
    )

/** Text styles for numbers: always tabular, so a value keeps its width while it changes. */
object SonicNumerals {
    /** Axis ticks of the response graph. */
    val axis = condensed(11, 14, FontWeight.Normal)

    /** A value next to its control, e.g. "+4.5 dB". */
    val value = condensed(15, 20, FontWeight.SemiBold)

    /** The readout of the selected band - the largest number on the screen. */
    val readout = condensed(28, 32, FontWeight.SemiBold)
}
