package com.bucketlist.app

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.webkit.JavascriptInterface

/**
 * Methods the web app can call through window.AndroidBridge:
 *  - saveFile(name, mimeType, content): saves an export to Downloads
 *  - updateWidget(json): stores the snapshot and redraws the home screen widget
 */
class NativeBridge(private val context: Context) {

    @JavascriptInterface
    fun saveFile(name: String, mimeType: String, content: String): Boolean {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: return false
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun updateWidget(json: String): Boolean {
        return try {
            WidgetStore.save(context, json)
            BucketWidget.updateAll(context)
            true
        } catch (e: Exception) {
            false
        }
    }
}
