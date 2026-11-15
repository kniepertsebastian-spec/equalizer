package com.auracurve.app.player.catalog

import com.auracurve.app.player.model.TrackItem

// What the player's Radio and Audius tabs need; RadioBrowserClient and AudiusClient implement them,
// tests use fakes.
interface RadioCatalog {
    suspend fun stations(genre: RadioGenre): List<RadioStation>

    suspend fun countClick(uuid: String)
}

interface AudiusCatalog {
    suspend fun trending(genre: AudiusGenre): List<TrackItem>

    suspend fun search(
        query: String,
        genre: AudiusGenre,
    ): List<TrackItem>
}
