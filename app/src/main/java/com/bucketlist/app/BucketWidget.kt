package com.bucketlist.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Home screen widget: progress, and the next dream to work on.
 * Reads the snapshot the web app saves through AndroidBridge.updateWidget().
 */
class BucketWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) manager.updateAppWidget(id, buildViews(context))
    }

    companion object {
        /** Redraws every placed widget. Safe to call from any thread. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BucketWidget::class.java))
            for (id in ids) manager.updateAppWidget(id, buildViews(context))
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_bucket)
            val snap = WidgetStore.read(context)

            if (snap == null) {
                views.setTextViewText(R.id.widget_count, "Open the app to start")
                views.setProgressBar(R.id.widget_progress, 1, 0, false)
                views.setTextViewText(R.id.widget_next_label, "")
                views.setTextViewText(R.id.widget_next_title, "Your list is waiting")
                views.setTextViewText(R.id.widget_next_meta, "Tap to open Bucket List")
            } else {
                val total = snap.optInt("total", 0)
                val done = snap.optInt("done", 0)
                views.setTextViewText(R.id.widget_count, "$done of $total done")
                views.setProgressBar(R.id.widget_progress, maxOf(total, 1), done, false)

                val next = if (snap.isNull("next")) null else snap.optJSONObject("next")
                if (next == null) {
                    views.setTextViewText(R.id.widget_next_label, "")
                    views.setTextViewText(
                        R.id.widget_next_title,
                        if (total == 0) "Your list is empty" else "Everything is done"
                    )
                    views.setTextViewText(
                        R.id.widget_next_meta,
                        if (total == 0) "Tap to add your first dream" else "Time to dream bigger"
                    )
                } else {
                    val title = next.optString("title", "")
                    val showTitles = snap.optBoolean("showTitles", true) && title.isNotEmpty()
                    views.setTextViewText(R.id.widget_next_label, "NEXT UP")
                    views.setTextViewText(R.id.widget_next_title, if (showTitles) title else "Hidden")
                    views.setTextViewText(
                        R.id.widget_next_meta,
                        if (showTitles) next.optString("meta", "") else "Dream names are hidden in the app menu"
                    )
                }
            }

            val launch = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
