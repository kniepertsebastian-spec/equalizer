package com.auracurve.app.remote.web

/** The newest published bundle: version from the file name, where to load it, its size. */
data class WebRelease(
    val version: String,
    val url: String,
    val size: Long,
)

/** Where releases come from; GitHub in the app, a fake in tests. */
interface WebReleaseSource {
    /** The newest release with a bundle, or null if there is none. */
    suspend fun latest(): WebRelease?

    suspend fun download(
        release: WebRelease,
        maxBytes: Long,
    ): ByteArray
}

/**
 * Keeps the web remote up to date (Pixel-Car module 10, E-012, way 2): while the phone has
 * internet (at home, the "sync mode"), at most once per [periodMs], it looks for a newer bundle,
 * checks it completely and only then switches. In the car, without internet, nothing changes and
 * the last verified version keeps being delivered.
 *
 * Never installs an older or equal version (a replayed old bundle is still validly signed), and
 * none older than the app's minimum ([minVersion]).
 */
class WebBundleUpdater(
    private val source: WebReleaseSource,
    private val verifier: WebBundleVerifier,
    private val store: WebBundleStore,
    private val online: () -> Boolean,
    private val minVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val periodMs: Long = CHECK_PERIOD_MS,
    private val log: (String) -> Unit = {},
    /** The release repository answered (module 08: shown as "web remote checked at"). */
    private val onChecked: () -> Unit = {},
) {
    sealed interface Result {
        data object NotDue : Result

        data object Offline : Result

        data object NoRelease : Result

        data class UpToDate(
            val version: String,
        ) : Result

        data class Installed(
            val version: String,
        ) : Result

        data class Rejected(
            val reason: String,
        ) : Result
    }

    @Volatile
    private var lastCheckMs: Long? = null

    /**
     * [force] (module 08: the phone just came online) skips the hourly period, but never checks
     * more often than every [FORCED_GAP_MS].
     */
    suspend fun updateIfDue(force: Boolean = false): Result {
        val last = lastCheckMs
        val gap = if (force) FORCED_GAP_MS else periodMs
        if (last != null && clock() - last < gap) return Result.NotDue
        if (!online()) return Result.Offline
        lastCheckMs = clock()
        val result =
            try {
                update()
            } catch (e: Exception) {
                // A failed check (no DNS, timeout) does not count: the next turn tries again
                // instead of waiting an hour (device test 18 Nov 2026: UnknownHostException).
                lastCheckMs = last
                throw e
            }
        onChecked()
        log("Web bundle: $result")
        return result
    }

    private suspend fun update(): Result {
        val release = source.latest() ?: return Result.NoRelease
        val installed = store.installed()?.version
        if (installed != null && WebVersion.compare(release.version, installed) <= 0) return Result.UpToDate(installed)
        if (WebVersion.compare(release.version, minVersion) < 0) return Result.Rejected("${release.version} älter als $minVersion")
        if (release.size > MAX_DOWNLOAD_BYTES) return Result.Rejected("${release.version} zu groß")
        val bundle =
            try {
                verifier.verify(source.download(release, MAX_DOWNLOAD_BYTES))
            } catch (e: WebBundleException) {
                return Result.Rejected("${release.version}: ${e.message}")
            }
        // What counts is the signed version, not the file name.
        val version = bundle.manifest.version
        if (installed != null && WebVersion.compare(version, installed) <= 0) return Result.Rejected("signiert als $version, nicht neuer")
        if (WebVersion.compare(version, minVersion) < 0) return Result.Rejected("signiert als $version, älter als $minVersion")
        return Result.Installed(store.install(bundle).version)
    }

    companion object {
        const val CHECK_PERIOD_MS = 60 * 60_000L
        const val FORCED_GAP_MS = 5 * 60_000L
        const val MAX_DOWNLOAD_BYTES = 5L * 1024 * 1024
    }
}
