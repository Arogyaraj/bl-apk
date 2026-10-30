package com.bucketlist.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Hosts the Bucket List web app inside a WebView.
 * The app files are read from this APK's assets, so the app works fully offline.
 * Everything the user saves is kept on the device by the web layer (IndexedDB).
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val assetLoader by lazy {
        WebViewAssetLoader.Builder()
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // needed for the device-side storage
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.addJavascriptInterface(NativeBridge(this), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            // Serves index.html, app.js, styles.css and icons from the APK's assets folder.
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            // Links to other sites open in the browser instead of inside the app.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.host == APP_HOST) return false
                startActivity(Intent(Intent.ACTION_VIEW, request.url))
                return true
            }
        }

        // Lets the file picker open for the backup import.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), FILE_REQUEST)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        // Always load the page. The list is kept in device storage, so a restarted activity
        // simply shows the PIN screen again instead of a blank WebView.
        webView.loadUrl("https://$APP_HOST/index.html?local=1")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_REQUEST) {
            val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            fileCallback?.onReceiveValue(results)
            fileCallback = null
        }
    }

    /* Back button: first close a dialog or return to the Dreams tab; leave the app only from the Dreams tab. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        webView.evaluateJavascript("(window.blBack && window.blBack()) ? 'true' : 'false'") { result ->
            if (result == null || result.contains("false")) finish()
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val FILE_REQUEST = 1001
    }
}
