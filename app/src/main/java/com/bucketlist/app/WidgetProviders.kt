package com.bucketlist.app

import android.content.Context
import android.widget.RemoteViews
import org.json.JSONObject

/** Progress: finished dreams out of all dreams, with a bar. */
class ProgressWidget : BucketWidgetBase() {
    override val layoutRes: Int = R.layout.widget_progress

    override fun bind(views: RemoteViews, context: Context, snap: JSONObject?) {
        if (snap == null) {
            views.text(R.id.w_value, "—")
            views.text(R.id.w_sub, "Open the app to start")
            views.setProgressBar(R.id.w_bar, 1, 0, false)
            return
        }
        val total = snap.optInt("total", 0)
        val done = snap.optInt("done", 0)
        val percent = if (total == 0) 0 else done * 100 / total
        views.text(R.id.w_value, "$done of $total")
        views.text(R.id.w_sub, if (total == 0) "Add your first dream" else "$percent% complete")
        views.setProgressBar(R.id.w_bar, maxOf(total, 1), done, false)
    }
}

/** Next up: the single next dream, with its date and countdown. */
class NextUpWidget : BucketWidgetBase() {
    override val layoutRes: Int = R.layout.widget_next

    override fun bind(views: RemoteViews, context: Context, snap: JSONObject?) {
        if (snap == null) {
            views.text(R.id.w_title, "Your list is waiting")
            views.text(R.id.w_meta, "Tap to open Bucket List")
            return
        }
        val total = snap.optInt("total", 0)
        val next = if (snap.isNull("next")) null else snap.optJSONObject("next")
        if (next == null) {
            views.text(R.id.w_title, if (total == 0) "Your list is empty" else "Everything is done")
            views.text(R.id.w_meta, if (total == 0) "Tap to add your first dream" else "Time to dream bigger")
            return
        }
        val title = next.optString("title", "")
        val show = snap.optBoolean("showTitles", true) && title.isNotEmpty()
        views.text(R.id.w_title, if (show) title else "Hidden dream")
        views.text(R.id.w_meta, if (show) next.optString("meta", "") else "Names are hidden in the app menu")
    }
}

/** Coming up: the next three dated dreams, one per row. */
class ComingUpWidget : BucketWidgetBase() {
    override val layoutRes: Int = R.layout.widget_coming
    private val titleIds = intArrayOf(R.id.w_t0, R.id.w_t1, R.id.w_t2)
    private val metaIds = intArrayOf(R.id.w_m0, R.id.w_m1, R.id.w_m2)

    override fun bind(views: RemoteViews, context: Context, snap: JSONObject?) {
        val upcoming = snap?.optJSONArray("upcoming")
        val count = upcoming?.length() ?: 0
        val showTitles = snap?.optBoolean("showTitles", true) ?: true

        for (i in 0 until 3) {
            val item = if (i < count) upcoming?.optJSONObject(i) else null
            views.hide(titleIds[i], item == null)
            views.hide(metaIds[i], item == null)
            if (item != null) {
                val title = item.optString("title", "")
                views.text(titleIds[i], if (showTitles && title.isNotEmpty()) title else "Hidden dream")
                views.text(metaIds[i], item.optString("meta", ""))
            }
        }
        if (count == 0) {
            // Show a single line in the first row when nothing is dated yet.
            views.hide(titleIds[0], false)
            views.text(titleIds[0], "Nothing dated yet")
        }
    }
}

/** Yearly goal: finished this year out of the goal set in the profile. */
class GoalWidget : BucketWidgetBase() {
    override val layoutRes: Int = R.layout.widget_goal

    override fun bind(views: RemoteViews, context: Context, snap: JSONObject?) {
        val goal = if (snap == null || snap.isNull("goal")) null else snap.optJSONObject("goal")
        val target = goal?.optInt("target", 0) ?: 0
        if (goal == null || target <= 0) {
            views.text(R.id.w_value, "No goal")
            views.text(R.id.w_sub, "Set one in your profile")
            views.setProgressBar(R.id.w_bar, 1, 0, false)
            return
        }
        val done = goal.optInt("done", 0)
        val year = goal.optInt("year", 0)
        val percent = minOf(100, done * 100 / target)
        views.text(R.id.w_value, "$done / $target")
        views.text(R.id.w_sub, "$year · $percent% there")
        views.setProgressBar(R.id.w_bar, target, minOf(done, target), false)
    }
}

/** Countdown: days until the nearest dated dream (or days since it passed). */
class CountdownWidget : BucketWidgetBase() {
    override val layoutRes: Int = R.layout.widget_countdown

    override fun bind(views: RemoteViews, context: Context, snap: JSONObject?) {
        val cd = if (snap == null || snap.isNull("countdown")) null else snap.optJSONObject("countdown")
        if (cd == null) {
            views.text(R.id.w_value, "—")
            views.text(R.id.w_sub, "No dates yet")
            views.text(R.id.w_title, "Add a target date to a dream")
            return
        }
        val days = cd.optInt("days", 0)
        val showTitles = snap?.optBoolean("showTitles", true) ?: true
        val title = cd.optString("title", "")
        views.text(
            R.id.w_value,
            when {
                days == 0 -> "Today"
                days > 0 -> days.toString()
                else -> (-days).toString()
            }
        )
        views.text(
            R.id.w_sub,
            when {
                days == 0 -> "is the day"
                days == 1 -> "day to go"
                days > 1 -> "days to go"
                days == -1 -> "day overdue"
                else -> "days overdue"
            }
        )
        views.text(R.id.w_title, if (showTitles && title.isNotEmpty()) title else "Hidden dream")
    }
}
