@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.network

import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.Foundation.NSError
import platform.WebKit.*
import platform.darwin.NSObject

internal class IosPostingBrowser : PostingBrowser {
    private val mutex = Mutex()
    // WKWebView.navigationDelegate is weak. Retain it across the suspended await;
    // a local whose last use is the assignment can be collected before didFinish.
    // Access is serialized by mutex and confined to Dispatchers.Main.
    private var activeNavigationDelegate: NSObject? = null
    override suspend fun userAgent(): String = evaluate("https://www.2chan.net/", "<html></html>", "navigator.userAgent")

    override suspend fun evaluate(pageUrl: String, html: String, script: String): String = mutex.withLock {
        withContext(Dispatchers.Main) {
            val result = CompletableDeferred<String>()
            val view = WKWebView(CGRectMake(0.0, 0.0, 1.0, 1.0), WKWebViewConfiguration())
            val delegate = IosPostingNavigationDelegate(script, result)
            try {
                activeNavigationDelegate = delegate
                view.navigationDelegate = delegate
                view.loadHTMLString(html, NSURL.URLWithString(pageUrl))
                withTimeoutOrNull(15_000) { result.await() } ?: throw NetworkException("投稿準備が時間内に完了しなかったため送信していません")
            } finally {
                view.stopLoading()
                view.navigationDelegate = null
                activeNavigationDelegate = null
            }
        }
    }
}

/**
 * Runs the preparation script once the page has loaded and ends the wait as soon as WebKit reports that it cannot
 * finish: a page that fails to load before or after it was committed, and a web content process that ended (low
 * memory, a crash). Without the latter two the person waited the whole timeout and was told it took too long
 * (audit L76). Nothing is sent in any of these cases.
 */
internal class IosPostingNavigationDelegate(
    private val script: String,
    private val result: CompletableDeferred<String>
) : NSObject(), WKNavigationDelegateProtocol {
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        webView.evaluateJavaScript(script) { value, error ->
            if (error != null || value !is String) result.completeExceptionally(NetworkException("投稿準備の実行に失敗しました"))
            else result.complete(value)
        }
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
        result.completeExceptionally(NetworkException("投稿準備画面を読み込めませんでした"))
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) {
        result.completeExceptionally(NetworkException("投稿準備画面を読み込めませんでした"))
    }

    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        result.completeExceptionally(NetworkException("投稿の準備中にブラウザー部品が停止したため送信していません"))
    }
}
