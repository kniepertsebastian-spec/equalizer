package com.hardbasseq.eq.quick

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.hardbasseq.eq.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home-screen widget: shows the EQ state and the active preset; one button switches the EQ on or
 * off, the other steps to the next preset (roadmap 06, P5-1).
 *
 * While the app's view model is alive the buttons are broadcasts that it handles silently; when it
 * is not, they open the app with the command instead (a broadcast receiver may not start an
 * activity from the background, and nothing else can drive the engine).
 */
@AndroidEntryPoint
class QuickWidgetProvider : AppWidgetProvider() {
    @Inject
    lateinit var controls: QuickControls

    @Inject
    lateinit var reader: QuickStateReader

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pending = goAsync()
        scope.launch {
            try {
                val state = reader.current()
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views(context, state, controls.hasConsumer)) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val command = intent.takeIf { it.action == ACTION_COMMAND }?.getStringExtra(EXTRA_COMMAND)
        if (command != null) {
            QuickCommand.entries.firstOrNull { it.name == command }?.let { controls.send(it) }
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_COMMAND = "com.hardbasseq.eq.action.WIDGET_COMMAND"
        private const val EXTRA_COMMAND = "command"

        /** Redraws every placed widget with [state] (null: ask for the saved state again). */
        fun updateAll(
            context: Context,
            state: QuickState?,
            hasConsumer: Boolean,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickWidgetProvider::class.java))
            if (ids.isEmpty()) return
            if (state == null) {
                context.sendBroadcast(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .setComponent(ComponentName(context, QuickWidgetProvider::class.java))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
                )
                return
            }
            ids.forEach { manager.updateAppWidget(it, views(context, state, hasConsumer)) }
        }

        private fun views(
            context: Context,
            state: QuickState?,
            hasConsumer: Boolean,
        ): RemoteViews {
            val on = state?.masterEnabled == true
            return RemoteViews(context.packageName, R.layout.widget_quick_controls).apply {
                setTextViewText(R.id.widget_eq_toggle, context.getString(if (on) R.string.tile_eq_on_caps else R.string.tile_eq_off_caps))
                setTextColor(
                    R.id.widget_eq_toggle,
                    ContextCompat.getColor(context, if (on) R.color.widget_accent else R.color.widget_muted),
                )
                setTextViewText(R.id.widget_preset, state?.presetName?.takeIf { it.isNotBlank() } ?: context.getString(R.string.app_name))
                setOnClickPendingIntent(R.id.widget_eq_toggle, commandIntent(context, QuickCommand.TOGGLE_MASTER, hasConsumer))
                setOnClickPendingIntent(R.id.widget_preset, commandIntent(context, QuickCommand.NEXT_PRESET, hasConsumer))
            }
        }

        private fun commandIntent(
            context: Context,
            command: QuickCommand,
            hasConsumer: Boolean,
        ): PendingIntent =
            if (hasConsumer) {
                PendingIntent.getBroadcast(
                    context,
                    command.ordinal,
                    Intent(context, QuickWidgetProvider::class.java)
                        .setAction(ACTION_COMMAND)
                        .putExtra(EXTRA_COMMAND, command.name),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                QuickCommandIntents.pendingIntent(context, command)
            }
    }
}
