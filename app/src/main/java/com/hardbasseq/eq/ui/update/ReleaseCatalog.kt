package com.hardbasseq.eq.ui.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class ReleaseAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
)

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tag: String,
    val draft: Boolean = false,
    val assets: List<ReleaseAsset> = emptyList(),
)

@Serializable
internal data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val packageName: String,
    val apkAsset: String,
    val sha256: String,
)

internal data class AvailableUpdate(
    val tag: String,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
)

/** Only releases with a matching signed-APK manifest are eligible for in-app updates. */
internal object ReleaseCatalog {
    private val json = Json { ignoreUnknownKeys = true }
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")

    fun parseReleases(payload: String): List<GithubRelease> = json.decodeFromString<List<GithubRelease>>(payload).filterNot { it.draft }

    fun manifestAsset(release: GithubRelease): ReleaseAsset? = release.assets.firstOrNull { it.name == "update.json" }

    fun eligibleUpdate(
        release: GithubRelease,
        manifestPayload: String,
        packageName: String,
        currentVersionCode: Long,
    ): AvailableUpdate? {
        val manifest = json.decodeFromString<UpdateManifest>(manifestPayload)
        if (release.draft || manifest.packageName != packageName || manifest.versionCode <= currentVersionCode) return null
        if (release.tag != "v${manifest.versionName}" || !sha256Pattern.matches(manifest.sha256)) return null
        if (!manifest.apkAsset.endsWith(".apk") || '/' in manifest.apkAsset || '\\' in manifest.apkAsset) return null
        val asset = release.assets.firstOrNull { it.name == manifest.apkAsset } ?: return null
        if (!isRepositoryReleaseUrl(asset.downloadUrl)) return null
        return AvailableUpdate(release.tag, manifest.versionCode, manifest.versionName, asset.downloadUrl, manifest.sha256.lowercase())
    }

    fun isRepositoryReleaseUrl(url: String): Boolean =
        url.startsWith("https://github.com/kniepertsebastian-spec/equalizer/releases/download/")
}
