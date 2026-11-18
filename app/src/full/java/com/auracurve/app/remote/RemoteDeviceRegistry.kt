package com.auracurve.app.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The one [RemoteDevices] of the app, shared by the server and the settings card (module 05). */
@Singleton
class RemoteDeviceRegistry
    @Inject
    constructor(
        @param:ApplicationContext context: Context,
        private val settings: RemoteServerSettings,
    ) {
        val devices = RemoteDevices(File(context.noBackupFilesDir, FILE_NAME))

        private val migration = Mutex()

        /**
         * Takes over the token of app versions before module 05 as one device, so the phone paired
         * with it keeps working; afterwards the old token is gone from the settings.
         */
        suspend fun migrateLegacyToken() {
            migration.withLock {
                val legacy = settings.legacyToken() ?: return
                devices.importLegacyToken(legacy, LEGACY_NAME)
                settings.clearLegacyToken()
            }
        }

        private companion object {
            const val FILE_NAME = "remote_devices.json"
            const val LEGACY_NAME = "Earlier pairing"
        }
    }
