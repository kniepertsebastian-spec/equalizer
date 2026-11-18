package com.auracurve.app.remote.web

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WebBundleTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val keys = generateKeys()
    private val verifier = WebBundleVerifier(keys.public, "v0")

    private val files =
        mapOf(
            "index.html" to "<html>ok</html>".toByteArray(),
            "js/main.js" to "export {}".toByteArray(),
        )

    private fun generateKeys(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    private fun manifest(
        version: String = "0.5.0",
        api: String = "v0",
        hashes: Map<String, String> = files.mapValues { WebBundleVerifier.sha256(it.value) },
        name: String = "pixel-car-remote",
    ): ByteArray {
        val list = hashes.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
        return """{"name":"$name","version":"$version","api":"$api","files":{$list}}""".toByteArray()
    }

    private fun sign(
        bytes: ByteArray,
        pair: KeyPair = keys,
    ): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(pair.private)
            update(bytes)
            sign()
        }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun bundle(
        version: String = "0.5.0",
        content: Map<String, ByteArray> = files,
        manifestBytes: ByteArray = manifest(version),
        signature: ByteArray = sign(manifestBytes),
    ): ByteArray = zip(content + mapOf(WebBundleVerifier.MANIFEST to manifestBytes, WebBundleVerifier.SIGNATURE to signature))

    private fun assertRejected(
        zip: ByteArray,
        reason: String,
    ) {
        try {
            verifier.verify(zip)
            fail("accepted, expected: $reason")
        } catch (e: WebBundleException) {
            assertTrue("${e.message} should mention $reason", e.message!!.contains(reason))
        }
    }

    @Test
    fun `a correctly signed bundle is accepted with all its files`() {
        val verified = verifier.verify(bundle())
        assertEquals("0.5.0", verified.manifest.version)
        assertEquals(files.keys, verified.files.keys)
        assertEquals("export {}", verified.files.getValue("js/main.js").toString(Charsets.UTF_8))
    }

    @Test
    fun `a bundle signed with another key is refused`() {
        val m = manifest()
        assertRejected(bundle(manifestBytes = m, signature = sign(m, generateKeys())), "Signatur")
    }

    @Test
    fun `a changed manifest breaks the signature`() {
        val signature = sign(manifest())
        assertRejected(bundle(manifestBytes = manifest("0.9.0"), signature = signature), "Signatur")
    }

    @Test
    fun `a changed, missing or extra file is refused`() {
        assertRejected(bundle(content = files + ("js/main.js" to "alert(1)".toByteArray())), "Hash falsch")
        assertRejected(bundle(content = files - "js/main.js"), "passen nicht")
        assertRejected(bundle(content = files + ("evil.js" to "x".toByteArray())), "passen nicht")
    }

    @Test
    fun `unsafe paths, other apis and foreign bundles are refused`() {
        assertRejected(zip(files + ("../escape.js" to ByteArray(1))), "Pfad")
        assertRejected(zip(files + ("/abs.js" to ByteArray(1))), "Pfad")
        assertRejected(zip(files + (".hidden" to ByteArray(1))), "Pfad")
        val otherApi = manifest(api = "v1")
        assertRejected(bundle(manifestBytes = otherApi, signature = sign(otherApi)), "API")
        val foreign = manifest(name = "something-else")
        assertRejected(bundle(manifestBytes = foreign, signature = sign(foreign)), "gehört nicht")
        assertRejected(zip(files), "fehlt")
        assertRejected("not a zip".toByteArray(), "leer")
    }

    @Test
    fun `safe paths are plain relative names`() {
        listOf("index.html", "js/views/eq.js", "icons/icon-192.png").forEach { assertTrue(it, WebBundleVerifier.isSafePath(it)) }
        listOf("", "/a", "a//b", "../a", "a/../b", ".env", "a\\b", "a/./b").forEach { assertFalse(it, WebBundleVerifier.isSafePath(it)) }
    }

    @Test
    fun `the public key reads from build-py's base64`() {
        val text = Base64.getEncoder().encodeToString(keys.public.encoded)
        assertEquals(keys.public, WebBundleVerifier.publicKey(text))
        // The key built into the app is a valid P-256 key.
        WebBundleVerifier.publicKey(WebBundleKey.PUBLIC_KEY)
    }

    @Test
    fun `versions compare number by number`() {
        assertTrue(WebVersion.compare("0.10.0", "0.9.9") > 0)
        assertEquals(0, WebVersion.compare("0.4", "0.4.0"))
        assertTrue(WebVersion.compare("0.2", "0.4.0") < 0)
        assertFalse(WebVersion.isValid("../1"))
    }

    @Test
    fun `the store switches only to a complete bundle and keeps the previous one`() {
        val root = tmp.newFolder("bundles")
        val store = WebBundleStore(root)
        assertNull(store.installed())
        store.install(verifier.verify(bundle("0.5.0")))
        assertEquals("0.5.0", store.installed()!!.version)
        assertTrue(File(root, "0.5.0/js/main.js").isFile)
        store.install(verifier.verify(bundle("0.6.0")))
        store.install(verifier.verify(bundle("0.7.0")))
        assertEquals("0.7.0", store.installed()!!.version)
        assertEquals("0.6.0", store.previous()!!.version)
        assertFalse("older than previous is removed", File(root, "0.5.0").exists())
        // A new store (app restart) finds the same.
        assertEquals("0.7.0", WebBundleStore(root).installed()!!.version)
    }

    private class FakeSource(
        var release: WebRelease?,
        var bytes: ByteArray = ByteArray(0),
    ) : WebReleaseSource {
        var downloads = 0

        override suspend fun latest() = release

        override suspend fun download(
            release: WebRelease,
            maxBytes: Long,
        ): ByteArray {
            downloads++
            return bytes
        }
    }

    private fun release(version: String) = WebRelease(version, "https://example/$version.zip", 1000)

    @Test
    fun `the updater installs a newer verified bundle only while online and when due`() =
        runTest {
            val store = WebBundleStore(tmp.newFolder("u"))
            val source = FakeSource(release("0.5.0"), bundle("0.5.0"))
            var online = false
            var now = 0L
            val updater = WebBundleUpdater(source, verifier, store, { online }, "0.2", clock = { now })
            assertEquals(WebBundleUpdater.Result.Offline, updater.updateIfDue())
            online = true
            assertEquals(WebBundleUpdater.Result.Installed("0.5.0"), updater.updateIfDue())
            assertEquals(WebBundleUpdater.Result.NotDue, updater.updateIfDue())
            now += WebBundleUpdater.CHECK_PERIOD_MS
            assertEquals(WebBundleUpdater.Result.UpToDate("0.5.0"), updater.updateIfDue())
            assertEquals(1, source.downloads)
        }

    @Test
    fun `the updater refuses old, renamed and broken bundles and keeps the current one`() =
        runTest {
            val store = WebBundleStore(tmp.newFolder("r"))
            var now = 0L
            val source = FakeSource(release("0.6.0"), bundle("0.6.0"))
            val updater = WebBundleUpdater(source, verifier, store, { true }, "0.2", clock = { now })
            updater.updateIfDue()
            // An old, validly signed bundle under a newer file name.
            source.release = release("0.9.0")
            source.bytes = bundle("0.5.0")
            now += WebBundleUpdater.CHECK_PERIOD_MS
            assertTrue(updater.updateIfDue() is WebBundleUpdater.Result.Rejected)
            // A broken one.
            source.bytes = bundle("0.9.0", content = files + ("js/main.js" to "x".toByteArray()))
            now += WebBundleUpdater.CHECK_PERIOD_MS
            assertTrue(updater.updateIfDue() is WebBundleUpdater.Result.Rejected)
            assertEquals("0.6.0", store.installed()!!.version)
            // Below the app's minimum.
            val oldSource = FakeSource(release("0.1.0"), bundle("0.1.0"))
            val fresh = WebBundleUpdater(oldSource, verifier, WebBundleStore(tmp.newFolder("m")), { true }, "0.2")
            assertTrue(fresh.updateIfDue() is WebBundleUpdater.Result.Rejected)
        }

    @Test
    fun `the release answer gives the newest bundle from the release repository only`() {
        val base = "https://github.com/${GitHubWebReleaseSource.REPOSITORY}/releases/download/v0.5.0"
        val json =
            """
            {"tag_name":"v0.5.0","draft":false,"prerelease":false,"assets":[
              {"name":"pixel-car-remote-0.5.0.zip","size":120000,"browser_download_url":"$base/pixel-car-remote-0.5.0.zip"},
              {"name":"pixel-car-remote-0.6.0.zip","size":1,"browser_download_url":"https://evil.example/pixel-car-remote-0.6.0.zip"},
              {"name":"notes.txt","size":1,"browser_download_url":"$base/notes.txt"}
            ]}
            """.trimIndent()
        assertEquals(WebRelease("0.5.0", "$base/pixel-car-remote-0.5.0.zip", 120000), GitHubWebReleaseSource.parseLatest(json))
        assertNull(GitHubWebReleaseSource.parseLatest(json.replace("\"draft\":false", "\"draft\":true")))
        assertNull(GitHubWebReleaseSource.parseLatest("{\"message\":\"Not Found\"}"))
        assertNull(GitHubWebReleaseSource.parseLatest("nonsense"))
    }
}
