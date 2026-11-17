package com.auracurve.app.remote.playback

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant AuraCurve notification access (U-1): with it,
 * MediaSessionManager.getActiveSessions lets the remote server read and steer Spotify's session.
 * It reads no notifications itself.
 */
class RemoteNotificationListener : NotificationListenerService()
