package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.valoser.futacha.shared.media.normalizeFutabaArchiveApuViewLabelHtml
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
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

/**
 * Whether [canonicalPath] lies inside one of the app's internal storage roots.
 * The activity is exported, so another app could otherwise make it render (and
 * reveal) files from the app's private data directory through a file:// URI.
 */
internal fun isInsidePrivateAppStorage(canonicalPath: String, privateRoots: Collection<String>): Boolean {
    val normalized = canonicalPath.trimEnd('/')
    return privateRoots.any { root ->
        val normalizedRoot = root.trimEnd('/')
        normalizedRoot.isNotEmpty() &&
            (normalized == normalizedRoot || normalized.startsWith("$normalizedRoot/"))
    }
}

internal enum class SavedHtmlNavigation { LOAD_IN_VIEW, OPEN_EXTERNALLY, BLOCK }

/**
 * What the viewer does with a navigation. The activity is exported, so any app
 * can hand it a page; a `<meta http-equiv="refresh">` (or any navigation the
 * user did not start) must not open its URL in the browser the moment the file
 * is shown, which would reveal the IP address and the time (S4-4). Only a
 * user's tap on an http/https link in the main frame, not a redirect, leaves
 * the app. Pages of the same document provider still load in the viewer.
 */
internal fun savedHtmlNavigation(
    scheme: String?,
    isMainFrame: Boolean,
    hasGesture: Boolean,
    isRedirect: Boolean,
    isSameDocumentProvider: Boolean
): SavedHtmlNavigation = when {
    scheme == "content" && isSameDocumentProvider -> SavedHtmlNavigation.LOAD_IN_VIEW
    isMainFrame && hasGesture && !isRedirect && scheme?.lowercase() in setOf("http", "https") ->
        SavedHtmlNavigation.OPEN_EXTERNALLY
    else -> SavedHtmlNavigation.BLOCK
}

/** Opens an exported/saved thread HTML file without enabling script or remote resources. */
class SavedHtmlViewerActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (
            intent?.action != Intent.ACTION_VIEW ||
            uri == null ||
            uri.scheme !in setOf("content", "file") ||
            !isSupportedSavedHtmlDocument(intent.type, uri.path) ||
            isPrivateAppUri(uri)
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
                    if (isPrivateAppUri(target)) return true
                    val navigation = savedHtmlNavigation(
                        scheme = target.scheme,
                        isMainFrame = request.isForMainFrame,
                        hasGesture = request.hasGesture(),
                        isRedirect = request.isRedirect,
                        isSameDocumentProvider = target.authority == sourceUri?.authority
                    )
                    if (navigation == SavedHtmlNavigation.LOAD_IN_VIEW) return false
                    if (navigation == SavedHtmlNavigation.OPEN_EXTERNALLY) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    }
                    return true
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val target = request?.url
                    return if (target == null || isPrivateAppUri(target) ||
                        target.scheme in setOf("http", "https") ||
                        (target.scheme == "content" && target.authority != sourceUri?.authority)) {
                        WebResourceResponse("text/plain", "UTF-8", null)
                    } else {
                        super.shouldInterceptRequest(view, request)
                    }
                }
            }
        }
        setContentView(webView)
        lifecycleScope.launch {
            val sanitizedHtml = withContext(Dispatchers.IO) {
                readSavedHtmlDocument(uri)?.let(::sanitizeSavedHtmlDocument)
            }
            if (sanitizedHtml == null) {
                finish()
                return@launch
            }
            webView.loadDataWithBaseURL(
                savedHtmlDocumentBaseUrl(uri),
                sanitizedHtml,
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    private fun isPrivateAppFileUri(uri: Uri): Boolean {
        val path = uri.path ?: return true
        val canonical = runCatching { java.io.File(path).canonicalPath }.getOrNull() ?: return true
        val roots = buildList {
            add(applicationInfo.dataDir)
            add(filesDir.parentFile?.path)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                add(applicationInfo.deviceProtectedDataDir)
            }
        }.filterNotNull().flatMap { root ->
            listOfNotNull(root, runCatching { java.io.File(root).canonicalPath }.getOrNull())
        }
        return isInsidePrivateAppStorage(canonical, roots)
    }

    private fun isPrivateAppUri(uri: Uri): Boolean {
        if (uri.scheme == "file") return isPrivateAppFileUri(uri)
        if (uri.scheme != "content" || uri.authority != "$packageName.fileprovider") return false
        // The provider exposes internal files/cache as well as external saves.
        // Resolve the descriptor so aliases and links obey the same rule as file://.
        return runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val path = android.system.Os.readlink("/proc/self/fd/${descriptor.fd}")
                isPrivateAppFileUri(Uri.fromFile(java.io.File(path)))
            } ?: true
        }.getOrDefault(true)
    }

    private suspend fun readSavedHtmlDocument(uri: Uri): String? {
        return runSuspendCatchingPreservingCancellation {
            contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0
                while (true) {
                    coroutineContext.ensureActive()
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
