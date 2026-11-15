package com.auracurve.app.ui.player.catalog

import androidx.compose.foundation.lazy.LazyListScope
import com.auracurve.app.ui.player.PlayerTab

// Player tabs that come from an online catalog (radio, Audius). Each flavor provides
// rememberCatalogTabs(): the full flavor with its view models and items, the store flavor none.
class CatalogTabs(
    val tabs: List<PlayerTab>,
    val content: LazyListScope.(PlayerTab) -> Unit,
)
