package com.auracurve.app.remote.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * The web remote's files as one signed bundle (Pixel-Car module 10, E-012): a ZIP built by
 * `pixel-car-remote/tools/build.py --bundle` with every file, `bundle-manifest.json` (name,
 * version, API version, SHA-256 per file) and `bundle-manifest.sig` (ECDSA P-256 over the manifest
 * bytes, DER). Nothing of a bundle is used before all of it is checked.
 */
data class WebBundleManifest(
    val version: String,
    val api: String,
    /** Path inside the bundle → SHA-256, lower-case hex. */
    val files: Map<String, String>,
)

/** A bundle whose signature, manifest and every file were checked. */
class VerifiedWebBundle(
    val manifest: WebBundleManifest,
    val manifestBytes: ByteArray,
    val files: Map<String, ByteArray>,
)

class WebBundleException(
    message: String,
) : Exception(message)

class WebBundleVerifier(
    private val publicKey: PublicKey,
    private val apiVersion: String,
) {
    fun verify(zip: ByteArray): VerifiedWebBundle {
        val entries = unzip(zip)
        val manifestBytes = entries.remove(MANIFEST) ?: fail("$MANIFEST fehlt")
        val signature = entries.remove(SIGNATURE) ?: fail("$SIGNATURE fehlt")
        // The signature first: nothing else of the bundle is read before it is known to be ours.
        val valid =
            runCatching {
                Signature.getInstance(SIGNATURE_ALGORITHM).run {
                    initVerify(publicKey)
                    update(manifestBytes)
                    verify(signature)
                }
            }.getOrDefault(false)
        if (!valid) fail("Signatur ungültig")
        val manifest = parseManifest(manifestBytes)
        if (manifest.api != apiVersion) fail("Bundle spricht API ${manifest.api}, die App $apiVersion")
        if (entries.keys != manifest.files.keys) fail("Dateien passen nicht zum Manifest")
        for ((path, bytes) in entries) {
            if (sha256(bytes) != manifest.files.getValue(path)) fail("Hash falsch: $path")
        }
        if ("index.html" !in entries) fail("index.html fehlt")
        return VerifiedWebBundle(manifest, manifestBytes, entries)
    }

    private fun unzip(zip: ByteArray): MutableMap<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
            while (true) {
                val entry = stream.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (!isSafePath(name)) fail("Unzulässiger Pfad im Bundle")
                if (name in out) fail("Datei doppelt im Bundle: $name")
                if (out.size >= MAX_FILES) fail("Zu viele Dateien im Bundle")
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val count = stream.read(chunk)
                    if (count < 0) break
                    total += count
                    if (total > MAX_UNPACKED_BYTES) fail("Bundle entpackt zu groß")
                    buffer.write(chunk, 0, count)
                }
                out[name] = buffer.toByteArray()
            }
        }
        if (out.isEmpty()) fail("Kein ZIP oder leer")
        return out
    }

    private fun parseManifest(bytes: ByteArray): WebBundleManifest {
        val root =
            runCatching { Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
                .getOrElse { fail("Manifest ist kein JSON") }

        fun text(key: String) = runCatching { root[key]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: fail("Manifest ohne $key")
        if (text("name") != BUNDLE_NAME) fail("Manifest gehört nicht zur Fernbedienung")
        val version = text("version")
        if (!WebVersion.isValid(version)) fail("Version ungültig: $version")
        val files =
            runCatching {
                (root["files"] as JsonObject).mapValues { (_, hash) -> hash.jsonPrimitive.content }
            }.getOrElse { fail("Manifest ohne Dateiliste") }
        if (files.any { (path, hash) -> !isSafePath(path) || !HEX_SHA256.matches(hash) }) fail("Dateiliste ungültig")
        return WebBundleManifest(version, text("api"), files)
    }

    private fun fail(message: String): Nothing = throw WebBundleException(message)

    companion object {
        const val MANIFEST = "bundle-manifest.json"
        const val SIGNATURE = "bundle-manifest.sig"
        const val BUNDLE_NAME = "pixel-car-remote"
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
        const val MAX_FILES = 256
        const val MAX_UNPACKED_BYTES = 8L * 1024 * 1024
        private val HEX_SHA256 = Regex("^[0-9a-f]{64}$")
        private val SEGMENT = Regex("^[A-Za-z0-9_][A-Za-z0-9._-]*$")

        /** Relative, only plain names: no "..", no leading dot, no backslash, no absolute path. */
        fun isSafePath(path: String): Boolean {
            if (path.isEmpty() || path.length > 200) return false
            return path.split('/').all { SEGMENT.matches(it) }
        }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** The public key as `build.py --public-key` prints it (Base64 of X.509 SubjectPublicKeyInfo). */
        fun publicKey(base64: String): PublicKey =
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64)))
    }
}

/** Versions of the web remote: three numbers, "0.4.0". */
object WebVersion {
    private val PATTERN = Regex("^\\d{1,4}(\\.\\d{1,4}){1,2}$")

    fun isValid(version: String): Boolean = PATTERN.matches(version)

    /** Like the PWA's compareVersions: number by number, missing parts count as 0. */
    fun compare(
        a: String,
        b: String,
    ): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }
}
