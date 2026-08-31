package com.valoser.futacha.shared.ui.compat

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal actual fun CompatReverseImageSearchWebView(
    initialUrl: String?,
    initialHtml: String?,
    baseUrl: String?,
    initialCookies: List<CompatBrowserCookie>,
    navigationCommand: CompatBrowserNavigationCommand?,
    modifier: Modifier,
    onStateChanged: (CompatBrowserState) -> Unit,
    onLinkLongPressed: (String) -> Unit,
    onCookiesChanged: (url: String, cookieHeader: String?) -> Unit
) {
    val currentCallback by rememberUpdatedState(onStateChanged)
    val currentLinkLongPressCallback by rememberUpdatedState(onLinkLongPressed)
    val currentCookiesChangedCallback by rememberUpdatedState(onCookiesChanged)
    var webView by remember { mutableStateOf<WebView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = COMPAT_REVERSE_SEARCH_USER_AGENT
                val cookieManager = CookieManager.getInstance().apply { setAcceptCookie(true) }
                cookieManager.setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean = !isCompatReverseSearchBrowserUrl(request?.url?.toString())

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        currentCallback(view.compatBrowserState(url, loading = true))
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        currentCallback(view.compatBrowserState(url, loading = false))
                        url?.takeIf(::isCompatReverseSearchBrowserUrl)?.let { finishedUrl ->
                            currentCookiesChangedCallback(finishedUrl, cookieManager.getCookie(finishedUrl))
                        }
                    }
                }
                setOnLongClickListener {
                    hitTestResult.let { result ->
                        compatReverseSearchLongPressedLink(result.type, result.extra)
                    }
                        ?.let(currentLinkLongPressCallback)
                        ?.let { true }
                        ?: false
                }
                fun loadInitialContent() {
                    if (initialHtml != null && baseUrl != null) {
                        loadDataWithBaseURL(baseUrl, initialHtml, "text/html", "UTF-8", null)
                    } else {
                        initialUrl?.let(::loadUrl)
                    }
                }
                val cookieUrl = initialUrl ?: baseUrl
                if (cookieUrl == null || initialCookies.isEmpty()) {
                    loadInitialContent()
                } else {
                    val remaining = AtomicInteger(initialCookies.size)
                    initialCookies.forEach { cookie ->
                        cookieManager.setCookie(cookieUrl, cookie.toAndroidSetCookieValue()) {
                            if (remaining.decrementAndGet() == 0) {
                                cookieManager.flush()
                                loadInitialContent()
                            }
                        }
                    }
                }
                webView = this
            }
        }
    )
    LaunchedEffect(navigationCommand?.serial) {
        when (navigationCommand?.navigation) {
            CompatBrowserNavigation.BACK -> webView?.takeIf(WebView::canGoBack)?.goBack()
            CompatBrowserNavigation.FORWARD -> webView?.takeIf(WebView::canGoForward)?.goForward()
            null -> Unit
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.webViewClient = WebViewClient()
            webView?.destroy()
            webView = null
        }
    }
}

fun compatReverseSearchLongPressedLink(type: Int, extra: String?): String? =
    extra?.trim()?.takeIf {
        type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
            type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
    }

private fun WebView.compatBrowserState(url: String?, loading: Boolean) = CompatBrowserState(
    currentUrl = url,
    loading = loading,
    canGoBack = canGoBack(),
    canGoForward = canGoForward()
)

private fun CompatBrowserCookie.toAndroidSetCookieValue(): String = buildString {
    append(name)
    append('=')
    append(value)
    append("; Path=")
    append(path.ifBlank { "/" })
    if (secure) append("; Secure")
}

private const val COMPAT_REVERSE_SEARCH_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36"
