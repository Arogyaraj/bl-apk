package com.bucketlist.app

import android.content.Context
import org.json.JSONObject

/** Keeps the last snapshot the app published, so the widget can draw even when the app is closed. */
object WidgetStore {
    private const val PREFS = "bucket_widget"
    private const val KEY = "snapshot"

    fun save(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, json)
            .apply()
    }

    fun read(context: Context): JSONObject? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (e: Exception) {
            null
        }
    }
}
