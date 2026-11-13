package com.hardbasseq.eq.integration

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.hardbasseq.eq.playlist.LocalTrackId
import com.soundcloud.equalizer.player.model.TrackItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads what the queue shows about audio files picked on the device (roadmap 06, P5-7). */
interface LocalTrackReader {
    /** Skips files that cannot be read; keeps the order of [addresses]. */
    suspend fun read(addresses: List<String>): List<TrackItem>
}

object NoLocalTrackReader : LocalTrackReader {
    override suspend fun read(addresses: List<String>): List<TrackItem> = emptyList()
}

@Singleton
class AndroidLocalTrackReader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : LocalTrackReader {
        override suspend fun read(addresses: List<String>): List<TrackItem> =
            withContext(Dispatchers.IO) {
                addresses.mapNotNull { runCatching { readOne(it) }.getOrNull() }
            }

        private fun readOne(address: String): TrackItem? {
            val uri = Uri.parse(address)
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val title =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
                        ?: displayName(uri)
                        ?: return null
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }.orEmpty()
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                return TrackItem(
                    id = LocalTrackId.of(address),
                    title = title,
                    artist = artist,
                    artworkUrl = null,
                    streamUrl = address,
                    durationMs = duration,
                )
            } finally {
                retriever.release()
            }
        }

        private fun displayName(uri: Uri): String? =
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.substringBeforeLast('.') else null
            }
    }
