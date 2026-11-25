package com.auracurve.app.ui.theme

import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Hardcore look (Design/DESIGN_SPEC.md, decision H1): the equalizer's Sound, Fine-tune and Device
// tabs as worn studio hardware in a dark basement. Blackened steel plates, bone-white stamped
// lettering, one blood-red accent for what is on, hazard yellow for what needs care. The Cyber
// tokens stay for the player screens; the shared components switch to this look only where
// [LocalHardcoreLook] is true, so nothing outside the equalizer changes.

/** True inside the equalizer screen: shared components draw their hardcore variant. */
val LocalHardcoreLook = staticCompositionLocalOf { false }

/** The running number a section header shows ("01"), or null for none. */
val LocalSectionNumber = compositionLocalOf<Int?> { null }

/** True inside a section header's end slot: keys there are drawn small. */
val LocalHcHeaderSlot = staticCompositionLocalOf { false }

/** The hardcore palette. Every color has one meaning, as in the Sonic palette. */
object HardcoreColors {
    /** Page background: almost black, a warm hint so the grain shows. */
    val void = Color(0xFF0A0A09)

    /** Plates (sections) and cards. */
    val plate = Color(0xFF121110)
    val plate2 = Color(0xFF191816)

    /** Edges of plates and wells. */
    val edge = Color(0xFF2A2724)
    val edgeHi = Color(0xFF3D3934)

    /** Deep wells: displays, slots, value boxes. */
    val well = Color(0xFF0B0B0A)
    val wellEdge = Color(0xFF26231F)

    /** Stamped lettering and values. */
    val bone = Color(0xFFE2DCCF)

    /** Secondary text and inactive icons. */
    val ash = Color(0xFF8D877D)

    /** Engraved labels of inactive options, the dimmest text that still reads. */
    val ash2 = Color(0xFF5F5A53)

    /** The accent: on, selected, boosted. */
    val blood = Color(0xFFB3281E)
    val bloodHi = Color(0xFFD6392A)
    val bloodLo = Color(0xFF3B1210)

    /** The limiter's label: warm, not the error red. */
    val rust = Color(0xFFA95A2B)

    /** Care: warnings, a sound that can harm hearing, waiting for the audio path. */
    val hazard = Color(0xFFBF9A3C)

    /** The display (VFD) of the sound goal. */
    val amber = Color(0xFFCF9F4A)

    /** Signal: the effect really runs on the audio path, a source is detected. */
    val toxic = Color(0xFF8EA83F)

    /** Raised key faces, top and bottom of the gradient, and their edges. */
    val keyTop = Color(0xFF2A2825)
    val keyBottom = Color(0xFF1C1B19)
    val keyEdge = Color(0xFF393530)
    val keyEdgeDark = Color(0xFF0A0A09)

    /** Pressed key faces. */
    val pressedTop = Color(0xFF121110)
    val pressedBottom = Color(0xFF191816)

    /** The dark of hazard stripes and the black of shadows. */
    val stripeDark = Color(0xFF111111)
    val shadow = Color(0xFF030303)
}

private const val TABULAR_FIGURES = "tnum"

private fun stamp(
    size: Float,
    tracking: Float,
    weight: FontWeight = FontWeight.SemiBold,
    lineHeight: Float = size * 1.2f,
) = TextStyle(
    fontFamily = BarlowSemiCondensed,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = TABULAR_FIGURES,
)

private fun body(
    size: Float,
    lineHeight: Float,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(fontFamily = Barlow, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

/**
 * Stamped lettering: Barlow Semi Condensed, set in capitals with wide tracking (the components
 * capitalize, the strings stay as they are). Body text stays plain Barlow, so reading is easy.
 */
object HardcoreType {
    val route = stamp(11.5f, 0.16f, FontWeight.Medium)
    val title = stamp(27f, 0.01f, lineHeight = 27f)
    val tab = stamp(15f, 0.14f)
    val sectionIndex = stamp(12f, 0.06f)
    val sectionTitle = stamp(15.5f, 0.15f)
    val key = stamp(13f, 0.08f)
    val keySmall = stamp(12f, 0.08f)
    val crate = stamp(17f, 0.12f)
    val patch = stamp(14.5f, 0.1f)
    val pad = stamp(14.5f, 0.08f)
    val label = stamp(11.5f, 0.14f)
    val gaffer = stamp(12f, 0.1f)
    val option = stamp(13.5f, 0.1f)
    val value = stamp(15f, 0.06f)
    val display = stamp(23f, 0.08f)
    val displaySmall = stamp(11f, 0.16f, FontWeight.Medium)
    val axis = stamp(11f, 0.04f, FontWeight.Medium, 14f)
    val nav = stamp(12f, 0.16f)
    val rowTitle = body(16f, 21f, FontWeight.Medium)
    val fieldLabel = body(15.5f, 21f, FontWeight.Medium)
    val statusTitle = body(15f, 19f, FontWeight.SemiBold)
    val note = body(13f, 18f)
    val small = body(12.5f, 17f)
}

/** Plates: cut at the top-left and bottom-right corner, like a stamped steel panel. */
val HcPlateShape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)

/** Utility keys: one cut corner. */
val HcKeyShape = CutCornerShape(topStart = 6.dp)
