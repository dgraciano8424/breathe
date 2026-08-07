package com.dgraciano.breathe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.dgraciano.breathe.MainActivity
import com.dgraciano.breathe.R
import com.dgraciano.breathe.data.repository.StatsRepository
import com.dgraciano.breathe.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "PauseCountWidget"

/**
 * Home-screen widget showing how many times the user chose not to open a distracting
 * app today, and how much time that gave them back.
 *
 * Built on RemoteViews rather than Glance: the content is three pieces of text, and
 * Glance would add a dependency with its own Compose-runtime constraints for no gain.
 */
@AndroidEntryPoint
class PauseCountWidget : AppWidgetProvider() {

    @Inject lateinit var statsRepo: StatsRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Room reads cannot happen on the broadcast thread, and the receiver is torn
        // down as soon as onUpdate returns — goAsync() keeps the process alive for it.
        val pending = goAsync()
        appScope.launch {
            try {
                val declined = statsRepo.getTodayDeclined()
                val minutesSaved = statsRepo.getTodayMinutesSaved()
                appWidgetIds.forEach { id ->
                    appWidgetManager.updateAppWidget(
                        id,
                        buildViews(context, declined, minutesSaved)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not refresh widget", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun buildViews(context: Context, declined: Int, minutesSaved: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_pause_count).apply {
            setTextViewText(R.id.widget_count, declined.toString())
            setTextViewText(
                R.id.widget_label,
                context.resources.getQuantityString(
                    R.plurals.widget_pauses_today, declined, declined
                )
            )
            setTextViewText(
                R.id.widget_saved,
                if (minutesSaved > 0) {
                    context.getString(R.string.widget_time_won_back, formatMinutes(minutesSaved))
                } else {
                    context.getString(R.string.widget_no_time_yet)
                }
            )
            setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    companion object {
        /**
         * Nudges every placed widget to redraw. Called when an intervention is recorded,
         * so the count reflects a choice the user just made rather than waiting up to
         * half an hour for the system's own update tick.
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, PauseCountWidget::class.java)
            )
            if (ids.isEmpty()) return

            context.sendBroadcast(
                Intent(context, PauseCountWidget::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}
