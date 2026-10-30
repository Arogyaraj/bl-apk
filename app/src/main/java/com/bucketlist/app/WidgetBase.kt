package com.bucketlist.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.json.JSONObject

/**
 * Shared behaviour for every Bucket List widget. Each subclass picks a layout and fills it
 * from the snapshot the app last published. Tapping any widget opens the app.
 */
abstract class BucketWidgetBase : AppWidgetProvider() {

    abstract val layoutRes: Int

    /** snap is null until the app has published a snapshot. */
    abstract fun bind(views: RemoteViews, context: Context, snap: JSONObject?)

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, render(context))
    }

    fun render(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, layoutRes)
        bind(views, context, WidgetStore.read(context))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context))
        return views
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    protected fun RemoteViews.text(id: Int, value: String) {
        setTextViewText(id, value)
    }

    protected fun RemoteViews.hide(id: Int, hidden: Boolean) {
        setViewVisibility(id, if (hidden) View.GONE else View.VISIBLE)
    }
}

/** Redraws every placed widget of every type. Safe to call from any thread. */
object WidgetUpdater {
    private val providers: List<Class<out BucketWidgetBase>> = listOf(
        ProgressWidget::class.java,
        NextUpWidget::class.java,
        ComingUpWidget::class.java,
        GoalWidget::class.java,
        CountdownWidget::class.java
    )

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (cls in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) continue
            val provider = cls.getDeclaredConstructor().newInstance()
            manager.updateAppWidget(ids, provider.render(context))
        }
    }
}
