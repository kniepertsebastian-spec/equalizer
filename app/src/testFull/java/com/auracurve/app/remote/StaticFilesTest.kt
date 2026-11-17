package com.auracurve.app.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class StaticFilesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun webRoot(): File {
        val root = tmp.newFolder("web")
        File(root, "index.html").writeText("<html></html>")
        File(root, "js").mkdirs()
        File(root, "js/main.js").writeText("export {}")
        tmp.newFile("secret.txt").writeText("token")
        return root
    }

    @Test
    fun `paths resolve inside the root and slash means index`() {
        val root = webRoot()
        assertEquals("index.html", StaticFiles.resolve(root, "/")?.name)
        assertEquals("main.js", StaticFiles.resolve(root, "/js/main.js")?.name)
        assertEquals("main.js", StaticFiles.resolve(root, "/js/main.js?v=2")?.name)
        assertNull(StaticFiles.resolve(root, "/js/"))
        assertNull(StaticFiles.resolve(root, "/missing.js"))
    }

    @Test
    fun `no path leaves the root`() {
        val root = webRoot()
        listOf("/../secret.txt", "/js/../../secret.txt", "/./index.html", "//secret.txt", "/..\\secret.txt", "/%2e%2e/secret.txt")
            .forEach { assertNull(it, StaticFiles.resolve(root, it)) }
    }

    @Test
    fun `a symlink pointing out of the root is refused`() {
        val root = webRoot()
        Files.createSymbolicLink(File(root, "leak.txt").toPath(), File(tmp.root, "secret.txt").toPath())
        assertNull(StaticFiles.resolve(root, "/leak.txt"))
    }

    @Test
    fun `content types match the web remote's files`() {
        assertEquals("text/javascript; charset=utf-8", StaticFiles.contentType(File("a.js")))
        assertEquals("application/manifest+json; charset=utf-8", StaticFiles.contentType(File("manifest.webmanifest")))
        assertEquals("image/svg+xml", StaticFiles.contentType(File("icon.svg")))
        assertEquals("image/png", StaticFiles.contentType(File("icon.png")))
        assertEquals("text/html; charset=utf-8", StaticFiles.contentType(File("index.html")))
    }
}
