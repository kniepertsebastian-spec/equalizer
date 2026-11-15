package com.auracurve.app.player.playback

import android.content.Context
import java.io.IOException

// Flavor "store": no online source is built in, so only files from the device play. A queue
// entry that still points at an online track (restored from an older install, say) fails like
// any unplayable track instead of reaching a service this variant does not use.
object StreamResolvers {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): StreamResolver =
        StreamResolver { trackId -> throw IOException("No online source in this version for track $trackId") }
}
