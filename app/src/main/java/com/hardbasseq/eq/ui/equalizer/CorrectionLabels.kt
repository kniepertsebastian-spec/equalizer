package com.hardbasseq.eq.ui.equalizer

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hardbasseq.eq.R
import com.hardbasseq.eq.correction.BuiltInCorrectionProfiles
import com.hardbasseq.eq.correction.CorrectionProfile

// Names and source labels of correction profiles are stored as plain text. The built-in ones and
// the labels the app itself writes ("Manual", "AutoEQ import", and their older German forms) are
// shown in the current language; anything a user typed or an import brought along stays as it is.
@Composable
fun CorrectionProfile.localizedName(): String =
    if (id == BuiltInCorrectionProfiles.None.id) stringResource(R.string.correction_none_name) else name

@Composable
fun CorrectionProfile.localizedSource(): String = localizedSourceLabel(sourceLabel)

@Composable
fun localizedSourceLabel(label: String): String =
    when (label) {
        "Manual", "Manuell" -> stringResource(R.string.source_label_manual)
        "No correction", "Keine Korrektur" -> stringResource(R.string.source_label_none)
        "AutoEQ import", "AutoEQ-Import" -> stringResource(R.string.source_label_autoeq_import)
        else -> label
    }
