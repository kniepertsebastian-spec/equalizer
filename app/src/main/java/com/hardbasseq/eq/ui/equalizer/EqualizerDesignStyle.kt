package com.hardbasseq.eq.ui.equalizer

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hardbasseq.eq.R
import com.hardbasseq.eq.preset.PresetDesign

/** Visual tokens for the five reference designs. Audio settings stay independent. */
internal data class EqualizerDesignStyle(
    val name: String,
    val background: Color,
    val backgroundEnd: Color,
    val backgroundRes: Int?,
    val surface: Color,
    val surfaceVariant: Color,
    val text: Color,
    val mutedText: Color,
    val accent: Color,
    val secondaryAccent: Color,
    val border: Color,
    val warningBackground: Color,
    val warningText: Color,
    val cardCorner: Dp,
    val presetCorner: Dp,
    val bandColors: List<Color>,
    val isLight: Boolean = false,
) {
    val colorScheme: ColorScheme
        get() {
            val base = if (isLight) lightColorScheme() else darkColorScheme()
            return base.copy(
                primary = accent,
                onPrimary = background,
                primaryContainer = surfaceVariant,
                onPrimaryContainer = text,
                secondary = secondaryAccent,
                onSecondary = background,
                background = background,
                onBackground = text,
                surface = surface,
                onSurface = text,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = mutedText,
                error = Color(0xFFF06A56),
                errorContainer = warningBackground,
                onErrorContainer = warningText,
            )
        }
}

internal fun styleFor(design: PresetDesign): EqualizerDesignStyle =
    when (design) {
        PresetDesign.UPTEMPO_HARDCORE ->
            EqualizerDesignStyle(
                name = "UPTEMPO HARDCORE",
                background = Color(0xFF0C0718),
                backgroundEnd = Color(0xFF201039),
                backgroundRes = R.drawable.bg_uptempo,
                surface = Color(0xF0191029),
                surfaceVariant = Color(0xFF301940),
                text = Color(0xFFF7EFFF),
                mutedText = Color(0xFFCCAFDA),
                accent = Color(0xFFE749CF),
                secondaryAccent = Color(0xFF9639EF),
                border = Color(0xFF8640A1),
                warningBackground = Color(0xF02A1039),
                warningText = Color(0xFFFFD8F8),
                cardCorner = 8.dp,
                presetCorner = 5.dp,
                bandColors = listOf(Color(0xFFE749CF)),
            )
        PresetDesign.TERRORCORE ->
            EqualizerDesignStyle(
                name = "TERRORCORE",
                background = Color(0xFF0D0707),
                backgroundEnd = Color(0xFF330D0D),
                backgroundRes = R.drawable.bg_terrorcore,
                surface = Color(0xED1F1311),
                surfaceVariant = Color(0xFF42251F),
                text = Color(0xFFFCE8D9),
                mutedText = Color(0xFFD4AC9D),
                accent = Color(0xFFDB3A32),
                secondaryAccent = Color(0xFF90231A),
                border = Color(0xFF7A4438),
                warningBackground = Color(0xE0A31B16),
                warningText = Color.White,
                cardCorner = 5.dp,
                presetCorner = 3.dp,
                bandColors = listOf(Color(0xFFDD4439)),
            )
        PresetDesign.GABBER ->
            EqualizerDesignStyle(
                name = "GABBER",
                background = Color(0xFFB8A578),
                backgroundEnd = Color(0xFF7B684A),
                backgroundRes = R.drawable.bg_gabber,
                surface = Color(0xEEDBD0AD),
                surfaceVariant = Color(0xFF50402F),
                text = Color(0xFF201A12),
                mutedText = Color(0xFFF3E8CA),
                accent = Color(0xFF829B22),
                secondaryAccent = Color(0xFF9F612B),
                border = Color(0xFF554B32),
                warningBackground = Color(0xEED9A849),
                warningText = Color(0xFF251708),
                cardCorner = 10.dp,
                presetCorner = 9.dp,
                bandColors = listOf(Color(0xFF7C9225)),
                isLight = true,
            )
        PresetDesign.HARD_DANCE ->
            EqualizerDesignStyle(
                name = "HARD DANCE",
                background = Color(0xFF08191B),
                backgroundEnd = Color(0xFF0E3033),
                backgroundRes = null,
                surface = Color(0xED10272A),
                surfaceVariant = Color(0xFF1C4143),
                text = Color(0xFFF4F6DE),
                mutedText = Color(0xFFB2D2CC),
                accent = Color(0xFFE1F269),
                secondaryAccent = Color(0xFF57D5D7),
                border = Color(0xFF3A777A),
                warningBackground = Color(0xEE282F22),
                warningText = Color(0xFFF8F8BA),
                cardCorner = 13.dp,
                presetCorner = 10.dp,
                bandColors = listOf(Color(0xFFE1F269), Color(0xFF59D3D1)),
            )
        PresetDesign.FLAT ->
            EqualizerDesignStyle(
                name = "FLAT",
                background = Color(0xFF14191D),
                backgroundEnd = Color(0xFF252A2D),
                backgroundRes = null,
                surface = Color(0xEE24292F),
                surfaceVariant = Color(0xFF383A43),
                text = Color(0xFFECEDEF),
                mutedText = Color(0xFFB8C0C3),
                accent = Color(0xFF60DCC5),
                secondaryAccent = Color(0xFFD75BCE),
                border = Color(0xFF62676D),
                warningBackground = Color(0xEF21282D),
                warningText = Color(0xFFE7E7D4),
                cardCorner = 17.dp,
                presetCorner = 13.dp,
                bandColors =
                    listOf(
                        Color(0xFFD75BCE),
                        Color(0xFF56D4DB),
                        Color(0xFF58D8AB),
                        Color(0xFFE7E36D),
                        Color(0xFFE29A56),
                    ),
            )
    }
