package com.auracurve.app.ui.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseCatalogTest {
    private val tag = "v0.2.0-beta.2"
    private val apkName = "AuraCurve-$tag.apk"
    private val apkUrl = "https://github.com/kniepertsebastian-spec/equalizer/releases/download/$tag/$apkName"
    private val checksum = "a".repeat(64)
    private val release =
        GithubRelease(
            tag = tag,
            assets = listOf(ReleaseAsset(apkName, apkUrl), ReleaseAsset("update.json", "$apkUrl.json")),
        )

    private fun manifest(versionCode: Int = 1002): String =
        """{"versionCode":$versionCode,"versionName":"0.2.0-beta.2","packageName":"com.auracurve.app","apkAsset":"$apkName","sha256":"$checksum"}"""

    @Test
    fun `valid release is offered only above installed version code`() {
        val update = ReleaseCatalog.eligibleUpdate(release, manifest(), "com.auracurve.app", 1001)

        assertEquals(1002, update?.versionCode)
        assertEquals(apkUrl, update?.apkUrl)
        assertNull(ReleaseCatalog.eligibleUpdate(release, manifest(), "com.auracurve.app", 1002))
    }

    @Test
    fun `rejects wrong package and untrusted download URL`() {
        assertNull(ReleaseCatalog.eligibleUpdate(release, manifest(), "different.package", 1))
        val untrusted = release.copy(assets = listOf(ReleaseAsset(apkName, "https://example.com/app.apk")))
        assertNull(ReleaseCatalog.eligibleUpdate(untrusted, manifest(), "com.auracurve.app", 1))
    }

    @Test
    fun `old releases without update metadata are ignored`() {
        val payload =
            """[{"tag_name":"v0.2.0-beta.1","draft":false,"assets":[{"name":"old-debug.apk","browser_download_url":"https://github.com/example"}]},{"tag_name":"draft","draft":true}]"""
        val releases = ReleaseCatalog.parseReleases(payload)

        assertEquals(1, releases.size)
        assertNull(ReleaseCatalog.manifestAsset(releases.first()))
    }
}
