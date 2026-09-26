package com.hardbasseq.eq.autoeq

import com.hardbasseq.eq.correction.CorrectionProfile

// One row of BuiltInAutoEqCatalog: a correction curve plus the name variants
// AutoEqCatalogMatcher tries to recognize a detected Bluetooth/USB device name
// against (the model name on its own, plus common ways devices actually
// advertise it over Bluetooth - "WH-1000XM4" rather than the full "Sony
// WH-1000XM4").
data class AutoEqCatalogEntry(
    val id: String,
    val displayName: String,
    val aliases: List<String> = emptyList(),
    val profile: CorrectionProfile,
)
