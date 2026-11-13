package com.hardbasseq.eq.quick

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hardbasseq.eq.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

// Roadmap 06, P5-1. The tile can only be tapped on a screen the user sees, so opening the app as a
// fallback when no view model is alive is allowed (and what the user expects).
abstract class QuickTile : TileService() {
    @Inject
    lateinit var controls: QuickControls

    @Inject
    lateinit var reader: QuickStateReader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected abstract val command: QuickCommand

    protected abstract fun render(
        tile: Tile,
        state: QuickState?,
    )

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val tile = qsTile ?: return@launch
            render(tile, reader.current())
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (controls.send(command)) return
        openAppWithCommand()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openAppWithCommand() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(QuickCommandIntents.pendingIntent(this, command))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(QuickCommandIntents.intent(this, command))
        }
    }
}

@AndroidEntryPoint
class EqToggleTileService : QuickTile() {
    override val command = QuickCommand.TOGGLE_MASTER

    override fun render(
        tile: Tile,
        state: QuickState?,
    ) {
        tile.label = getString(R.string.tile_eq_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_eq)
        tile.state = if (state?.masterEnabled == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle =
                getString(
                    if (state?.masterEnabled == true) R.string.tile_eq_on else R.string.tile_eq_off,
                )
        }
    }
}

@AndroidEntryPoint
class NextPresetTileService : QuickTile() {
    override val command = QuickCommand.NEXT_PRESET

    override fun render(
        tile: Tile,
        state: QuickState?,
    ) {
        tile.label = getString(R.string.tile_preset_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification_eq)
        // Never "active": it is an action, not a switch. The current preset is the subtitle.
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = state?.presetName?.takeIf { it.isNotBlank() }
        }
    }
}
