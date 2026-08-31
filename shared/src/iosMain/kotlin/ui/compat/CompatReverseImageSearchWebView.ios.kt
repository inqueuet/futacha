package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieName
import platform.Foundation.NSHTTPCookieOriginURL
import platform.Foundation.NSHTTPCookiePath
import platform.Foundation.NSHTTPCookieSecure
import platform.Foundation.NSHTTPCookieValue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
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
    val delegate = remember { CompatReverseSearchNavigationDelegate() }
    var webView by remember { mutableStateOf<WKWebView?>(null) }
    SideEffect {
        delegate.onStateChanged = onStateChanged
        delegate.onLinkLongPressed = onLinkLongPressed
        delegate.onCookiesChanged = onCookiesChanged
    }
    UIKitView(
        modifier = modifier,
        factory = {
            val userContentController = WKUserContentController().apply {
                addScriptMessageHandler(delegate, name = COMPAT_LINK_LONG_PRESS_HANDLER)
                addUserScript(
                    WKUserScript(
                        source = COMPAT_LINK_LONG_PRESS_SCRIPT,
                        injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentEnd,
                        forMainFrameOnly = false
                    )
                )
            }
            val configuration = WKWebViewConfiguration().apply {
                defaultWebpagePreferences.allowsContentJavaScript = true
                this.userContentController = userContentController
            }
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = configuration
            ).apply {
                navigationDelegate = delegate
                customUserAgent = COMPAT_REVERSE_SEARCH_USER_AGENT
                fun loadInitialContent() {
                    if (initialHtml != null && baseUrl != null) {
                        loadHTMLString(initialHtml, NSURL.URLWithString(baseUrl))
                    } else {
                        initialUrl?.let(NSURL::URLWithString)?.let {
                            loadRequest(NSURLRequest.requestWithURL(it))
                        }
                    }
                }
                val cookieUrl = (initialUrl ?: baseUrl)?.let(NSURL::URLWithString)
                val cookies = cookieUrl?.let { url ->
                    initialCookies.mapNotNull { cookie -> cookie.toIosHttpCookie(url) }
                }.orEmpty()
                if (cookies.isEmpty()) {
                    loadInitialContent()
                } else {
                    var remaining = cookies.size
                    cookies.forEach { cookie ->
                        configuration.websiteDataStore.httpCookieStore.setCookie(cookie) {
                            remaining -= 1
                            if (remaining == 0) loadInitialContent()
                        }
                    }
                }
                webView = this
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.configuration.userContentController.removeScriptMessageHandlerForName(
                COMPAT_LINK_LONG_PRESS_HANDLER
            )
            view.navigationDelegate = null
            if (webView === view) webView = null
        }
    )
    LaunchedEffect(navigationCommand?.serial) {
        when (navigationCommand?.navigation) {
            CompatBrowserNavigation.BACK -> webView?.takeIf { it.canGoBack }?.goBack()
            CompatBrowserNavigation.FORWARD -> webView?.takeIf { it.canGoForward }?.goForward()
            null -> Unit
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class CompatReverseSearchNavigationDelegate : NSObject(), WKNavigationDelegateProtocol,
    WKScriptMessageHandlerProtocol {
    var onStateChanged: (CompatBrowserState) -> Unit = {}
    var onLinkLongPressed: (String) -> Unit = {}
    var onCookiesChanged: (String, String?) -> Unit = { _, _ -> }

    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage
    ) {
        (didReceiveScriptMessage.body as? String)?.let(onLinkLongPressed)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        publish(webView, loading = true)
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        publish(webView, loading = false)
        val url = webView.URL?.absoluteString?.takeIf(::isCompatReverseSearchBrowserUrl) ?: return
        webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { values ->
            val parsedUrl = NSURL.URLWithString(url)
            val host = parsedUrl?.host?.lowercase().orEmpty()
            val requestPath = parsedUrl?.path?.ifBlank { "/" } ?: "/"
            val secureRequest = parsedUrl?.scheme.equals("https", ignoreCase = true)
            val header = values.orEmpty()
                .filterIsInstance<NSHTTPCookie>()
                .filter { cookie ->
                    val cookiePath = cookie.path.ifBlank { "/" }
                    val pathMatches = requestPath == cookiePath ||
                        (requestPath.startsWith(cookiePath) &&
                            (cookiePath.endsWith('/') || requestPath.getOrNull(cookiePath.length) == '/'))
                    cookie.domain.trimStart('.').lowercase().let { domain ->
                        host == domain || host.endsWith(".$domain")
                    } && pathMatches &&
                        (!cookie.isSecure() || secureRequest)
                }
                .joinToString("; ") { cookie -> "${cookie.name}=${cookie.value}" }
                .takeIf(String::isNotBlank)
            onCookiesChanged(url, header)
        }
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailNavigation: WKNavigation?,
        withError: NSError
    ) {
        publish(webView, loading = false)
    }

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError
    ) {
        publish(webView, loading = false)
    }

    private fun publish(webView: WKWebView, loading: Boolean) {
        onStateChanged(
            CompatBrowserState(
                currentUrl = webView.URL?.absoluteString,
                loading = loading,
                canGoBack = webView.canGoBack,
                canGoForward = webView.canGoForward
            )
        )
    }
}

private fun CompatBrowserCookie.toIosHttpCookie(originUrl: NSURL): NSHTTPCookie? =
    NSHTTPCookie.cookieWithProperties(
        buildMap<Any?, Any> {
            put(NSHTTPCookieName, name)
            put(NSHTTPCookieValue, value)
            put(NSHTTPCookieOriginURL, originUrl)
            put(NSHTTPCookiePath, path.ifBlank { "/" })
            if (secure) put(NSHTTPCookieSecure, "TRUE")
        }
    )

private const val COMPAT_REVERSE_SEARCH_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36"

private const val COMPAT_LINK_LONG_PRESS_HANDLER = "compatLinkLongPress"
private const val COMPAT_LINK_LONG_PRESS_SCRIPT = """
document.addEventListener('contextmenu', function(event) {
  var target = event.target;
  var link = target && target.closest ? target.closest('a[href]') : null;
  if (!link || !/^https?:\/\//i.test(link.href)) return;
  event.preventDefault();
  window.webkit.messageHandlers.compatLinkLongPress.postMessage(link.href);
}, true);
"""
