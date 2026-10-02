package com.soundcloud.equalizer.player

import android.content.Context
import androidx.annotation.StringRes

// Translated texts for the player module's code that has no Context at hand (API clients
// that throw errors the user may read). The app sets the context once at start-up; before
// that (and in plain JVM tests) a text falls back to its resource number.
object PlayerText {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun get(
        @StringRes id: Int,
        vararg args: Any,
    ): String = appContext?.getString(id, *args) ?: "string#$id"
}
