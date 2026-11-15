package com.auracurve.app.ui.player.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.auracurve.app.player.model.TrackItem

// Flavor "store": no online catalogs (AuraCurve roadmap 02, 1.3; radio and Audius need their own
// permission check before they can go into the store build).
@Suppress("UNUSED_PARAMETER")
@Composable
fun rememberCatalogTabs(
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
): CatalogTabs = remember { CatalogTabs(emptyList()) {} }
