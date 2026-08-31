package com.valoser.futacha

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

internal fun isSupportedSavedHtmlDocument(mimeType: String?, path: String?): Boolean {
    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    val normalizedPath = path.orEmpty().substringBefore('?').substringBefore('#').lowercase()
    return normalizedMime in setOf("text/html", "application/xhtml+xml") ||
        normalizedPath.endsWith(".htm") || normalizedPath.endsWith(".html")
}

/** Opens an exported/saved thread HTML file without enabling script or remote resources. */
class SavedHtmlViewerActivity : Activity() {
    private lateinit var webView: WebView
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (
            intent?.action != Intent.ACTION_VIEW ||
            uri == null ||
            uri.scheme !in setOf("content", "file") ||
            !isSupportedSavedHtmlDocument(intent.type, uri.path)
        ) {
            finish()
            return
        }
        sourceUri = uri
        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = uri.scheme == "file"
            settings.allowContentAccess = true
            settings.blockNetworkLoads = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val target = request?.url ?: return true
                    if (target.scheme == "content" && target.authority == sourceUri?.authority) return false
                    if (target.scheme in setOf("http", "https")) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    }
                    return true
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return if (request?.url?.scheme in setOf("http", "https")) {
                        WebResourceResponse("text/plain", "UTF-8", null)
                    } else {
                        super.shouldInterceptRequest(view, request)
                    }
                }
            }
            loadUrl(uri.toString())
        }
        setContentView(webView)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
