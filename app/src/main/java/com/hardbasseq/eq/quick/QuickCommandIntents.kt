package com.hardbasseq.eq.quick

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.hardbasseq.eq.MainActivity

/**
 * How a command reaches the app when no view model is alive to take it from [QuickControls]: an
 * intent to the main activity, whose view model then performs it once the saved state is loaded.
 */
object QuickCommandIntents {
    const val ACTION = "com.hardbasseq.eq.action.QUICK_COMMAND"
    const val EXTRA_COMMAND = "command"

    fun intent(
        context: Context,
        command: QuickCommand,
    ): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_COMMAND, command.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun pendingIntent(
        context: Context,
        command: QuickCommand,
    ): PendingIntent =
        PendingIntent.getActivity(
            context,
            command.ordinal,
            intent(context, command),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun commandOf(intent: Intent?): QuickCommand? =
        intent
            ?.takeIf { it.action == ACTION }
            ?.getStringExtra(EXTRA_COMMAND)
            ?.let { name -> QuickCommand.entries.firstOrNull { it.name == name } }
}
