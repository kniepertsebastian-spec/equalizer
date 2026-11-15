package com.auracurve.app.discovery

import android.content.Context
import com.auracurve.app.R
import com.auracurve.app.integration.LoadResult
import com.auracurve.app.player.model.TrackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

// Flavor "store": new uploads are looked up on SoundCloud, which this variant does not use. The
// weekly refresh is not scheduled here (AuraCurveApplication), so this only answers a stray call.
class NoDiscoverySource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DiscoverySource {
        override suspend fun tasteTracks(): LoadResult<List<TrackItem>> =
            LoadResult.Error(context.getString(R.string.online_sources_unavailable))

        override suspend fun recentUploads(artist: String): LoadResult<List<TrackItem>> =
            LoadResult.Error(context.getString(R.string.online_sources_unavailable))
    }
