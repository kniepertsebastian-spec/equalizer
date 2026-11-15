package com.auracurve.app.ui.update

import androidx.compose.runtime.Composable

// Flavor "store": updates come from Google Play, so there is no self-update (AuraCurve
// roadmap 02, 1.3). Same name as the full flavor's notice, so MainScreen needs no switch.
@Composable
fun ReleaseUpdateNotice() = Unit
