package com.auracurve.app.player.playback

import android.content.Context

// Flavor "full": streams come from SoundCloud (see SoundCloudStreamResolver).
object StreamResolvers {
    fun create(context: Context): StreamResolver = SoundCloudStreamResolver(context)
}
