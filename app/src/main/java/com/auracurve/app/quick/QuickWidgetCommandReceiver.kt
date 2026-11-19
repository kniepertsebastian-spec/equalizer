package com.auracurve.app.quick

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The widget's buttons while the app's view model is alive (roadmap 06, P5-1). Not exported
 * (AuraCurve roadmap 03, 2.5): only the widget's own PendingIntents reach it, other apps cannot
 * switch the EQ or the preset with a broadcast.
 */
@AndroidEntryPoint
class QuickWidgetCommandReceiver : BroadcastReceiver() {
    @Inject
    lateinit var controls: QuickControls

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        commandOf(intent)?.let { controls.send(it) }
    }

    companion object {
        private const val ACTION_COMMAND = "com.auracurve.app.action.WIDGET_COMMAND"
        private const val EXTRA_COMMAND = "command"

        fun intent(
            context: Context,
            command: QuickCommand,
        ): Intent =
            Intent(context, QuickWidgetCommandReceiver::class.java)
                .setAction(ACTION_COMMAND)
                .putExtra(EXTRA_COMMAND, command.name)

        fun commandOf(intent: Intent?): QuickCommand? =
            intent
                ?.takeIf { it.action == ACTION_COMMAND }
                ?.getStringExtra(EXTRA_COMMAND)
                ?.let { name -> QuickCommand.entries.firstOrNull { it.name == name } }
    }
}
