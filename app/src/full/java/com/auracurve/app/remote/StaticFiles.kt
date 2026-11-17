package com.auracurve.app.remote

import java.io.File

/**
 * Serving the web remote's files (Pixel-Car module 04a, U-3: until module 10 they are pushed with
 * adb into the app's external files dir "web"). Pure functions, tested without Android.
 */
object StaticFiles {
    /**
     * The file for a request path inside [root], or null if there is none or it would leave [root]
     * (".." segments, a symlink pointing out). "/" and paths ending in "/" mean index.html.
     */
    fun resolve(
        root: File,
        requestPath: String,
    ): File? {
        val rel = requestPath.substringBefore('?').removePrefix("/").let { if (it.isEmpty() || it.endsWith("/")) it + "index.html" else it }
        val segments = rel.split('/')
        if (segments.any { it == ".." || it == "." || it.isEmpty() || it.contains('\\') || it.contains('\u0000') }) return null
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val file = runCatching { File(canonicalRoot, rel).canonicalFile }.getOrNull() ?: return null
        // canonicalFile follows symlinks: a link out of the root ends up outside and is refused.
        if (!file.path.startsWith(canonicalRoot.path + File.separator)) return null
        return file.takeIf { it.isFile }
    }

    /** Content-Type by extension; text types carry UTF-8. */
    fun contentType(file: File): String =
        when (file.extension.lowercase()) {
            "html", "htm" -> "text/html; charset=utf-8"
            "js", "mjs" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "webmanifest" -> "application/manifest+json; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "ico" -> "image/x-icon"
            "woff2" -> "font/woff2"
            "txt" -> "text/plain; charset=utf-8"
            else -> "application/octet-stream"
        }

    /**
     * Every file is revalidated, as with the devserver: index.html and sw.js must be (or an update
     * never arrives), and the service worker caches the rest for offline use anyway.
     */
    const val CACHE_CONTROL = "no-cache"
}
