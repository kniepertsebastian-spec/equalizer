package com.auracurve.app.player.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Roadmap 12, P12-4: the WebView sign-in's host and redirect checks.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SoundCloudLoginUrlTest {
    @Test
    fun soundCloudPagesAreRecognizedByTheirHost() {
        assertTrue(SoundCloudLoginActivity.isSoundCloudPage("https://soundcloud.com/discover"))
        assertTrue(SoundCloudLoginActivity.isSoundCloudPage("https://secure.soundcloud.com/connect/callback?x=1"))
        assertTrue(SoundCloudLoginActivity.isSoundCloudPage("HTTPS://SoundCloud.com/"))
    }

    @Test
    fun urlsThatOnlyMentionSoundCloudAreNotSoundCloud() {
        assertFalse(SoundCloudLoginActivity.isSoundCloudPage("https://accounts.google.com/o/oauth2?redirect_uri=https://soundcloud.com/"))
        assertFalse(SoundCloudLoginActivity.isSoundCloudPage("https://evil.example/soundcloud.com"))
        assertFalse(SoundCloudLoginActivity.isSoundCloudPage("https://soundcloud.com.evil.example/"))
        assertFalse(SoundCloudLoginActivity.isSoundCloudPage("https://notsoundcloud.com/"))
        assertFalse(SoundCloudLoginActivity.isSoundCloudPage("http://soundcloud.com/"))
        assertFalse(SoundCloudLoginActivity.isSoundCloudPage(null))
    }

    @Test
    fun onlyHttpsNavigationIsAllowed() {
        assertTrue(SoundCloudLoginActivity.isAllowedNavigation("https://accounts.google.com/signin"))
        assertTrue(SoundCloudLoginActivity.isAllowedNavigation("about:blank"))
        assertFalse(SoundCloudLoginActivity.isAllowedNavigation("http://soundcloud.com/"))
        assertFalse(SoundCloudLoginActivity.isAllowedNavigation("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(SoundCloudLoginActivity.isAllowedNavigation("file:///data/data/com.auracurve.app/shared_prefs/sc_auth_prefs.xml"))
        assertFalse(SoundCloudLoginActivity.isAllowedNavigation("content://com.auracurve.app.updates/x"))
        assertFalse(SoundCloudLoginActivity.isAllowedNavigation(null))
    }
}
