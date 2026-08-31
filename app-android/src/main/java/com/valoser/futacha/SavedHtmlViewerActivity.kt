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
import com.valoser.futacha.shared.media.normalizeFutabaArchiveApuViewLabelHtml
import java.io.ByteArrayOutputStream

private const val MAX_SAVED_HTML_VIEWER_BYTES = 21 * 1024 * 1024

internal fun isSupportedSavedHtmlDocument(mimeType: String?, path: String?): Boolean {
    val normalizedMime = mimeType?.substringBefore(';')?.trim()?.lowercase()
    val normalizedPath = path.orEmpty().substringBefore('?').substringBefore('#').lowercase()
    return normalizedMime in setOf("text/html", "application/xhtml+xml") ||
        normalizedPath.endsWith(".htm") || normalizedPath.endsWith(".html")
}

internal fun sanitizeSavedHtmlDocument(html: String): String =
    normalizeFutabaArchiveApuViewLabelHtml(html)

internal fun savedHtmlDocumentBaseUrl(uri: Uri): String {
    val value = uri.toString().substringBefore('#').substringBefore('?')
    val separator = value.lastIndexOf('/')
    return if (separator >= 0) value.substring(0, separator + 1) else value
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
        val sanitizedHtml = readSavedHtmlDocument(uri)?.let(::sanitizeSavedHtmlDocument)
        if (sanitizedHtml == null) {
            finish()
            return
        }
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
            loadDataWithBaseURL(
                savedHtmlDocumentBaseUrl(uri),
                sanitizedHtml,
                "text/html",
                "UTF-8",
                null
            )
        }
        setContentView(webView)
    }

    private fun readSavedHtmlDocument(uri: Uri): String? {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read == 0) continue
                    totalBytes += read
                    if (totalBytes > MAX_SAVED_HTML_VIEWER_BYTES) return@use null
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
