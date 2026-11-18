package com.auracurve.app.remote.web

import java.io.File

/**
 * Verified bundles in the app's private storage (Pixel-Car module 10, R-18): one directory per
 * version under [root], the file `current` names the one the server delivers. A new version is
 * written completely to a temporary directory first and only then switched to; the version it
 * replaces stays as `previous`, anything older is removed.
 */
class WebBundleStore(
    private val root: File,
) {
    data class Installed(
        val version: String,
        val dir: File,
    )

    @Volatile
    private var cached: Installed? = null

    @Volatile
    private var loaded = false

    /** The bundle the server delivers, or null if none was installed yet. */
    fun installed(): Installed? {
        if (!loaded) {
            cached = read(CURRENT)
            loaded = true
        }
        return cached
    }

    fun previous(): Installed? = read(PREVIOUS)

    @Synchronized
    fun install(bundle: VerifiedWebBundle): Installed {
        val version = bundle.manifest.version
        root.mkdirs()
        val target = File(root, version)
        val before = installed()
        if (before?.version == version) return before
        val incoming = File(root, ".incoming-$version")
        try {
            incoming.deleteRecursively()
            for ((path, bytes) in bundle.files) {
                val file = File(incoming, path)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
            }
            File(incoming, WebBundleVerifier.MANIFEST).writeBytes(bundle.manifestBytes)
            target.deleteRecursively()
            check(incoming.renameTo(target)) { "Bundle nicht gespeichert" }
        } catch (e: Exception) {
            incoming.deleteRecursively()
            throw e
        }
        // The switch itself: one rename of a small file.
        if (before != null) writeAtomically(PREVIOUS, before.version)
        writeAtomically(CURRENT, version)
        val now = Installed(version, target)
        cached = now
        loaded = true
        prune(keep = setOfNotNull(version, before?.version))
        return now
    }

    private fun read(name: String): Installed? {
        val version = runCatching { File(root, name).readText().trim() }.getOrNull() ?: return null
        if (!WebVersion.isValid(version)) return null
        val dir = File(root, version)
        return Installed(version, dir).takeIf { File(dir, "index.html").isFile && File(dir, WebBundleVerifier.MANIFEST).isFile }
    }

    private fun writeAtomically(
        name: String,
        text: String,
    ) {
        val temporary = File(root, "$name.tmp")
        temporary.writeText(text)
        check(temporary.renameTo(File(root, name))) { "$name nicht geschrieben" }
    }

    private fun prune(keep: Set<String>) {
        root.listFiles()?.forEach { file ->
            val stale = file.isDirectory && file.name !in keep
            if (stale) file.deleteRecursively()
        }
    }

    private companion object {
        const val CURRENT = "current"
        const val PREVIOUS = "previous"
    }
}
