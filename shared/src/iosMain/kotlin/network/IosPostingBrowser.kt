@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.network

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
    override suspend fun userAgent(): String = evaluate("https://www.2chan.net/", "<html></html>", "navigator.userAgent")

    override suspend fun evaluate(pageUrl: String, html: String, script: String): String = mutex.withLock {
        withContext(Dispatchers.Main) {
            val result = CompletableDeferred<String>()
            val view = WKWebView(CGRectMake(0.0, 0.0, 1.0, 1.0), WKWebViewConfiguration())
            val delegate = object : NSObject(), WKNavigationDelegateProtocol {
                override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                    webView.evaluateJavaScript(script) { value, error ->
                        if (error != null || value !is String) result.completeExceptionally(NetworkException("投稿準備の実行に失敗しました"))
                        else result.complete(value)
                    }
                }
                override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) {
                    result.completeExceptionally(NetworkException("投稿準備画面を読み込めませんでした"))
                }
            }
            try {
                view.navigationDelegate = delegate
                view.loadHTMLString(html, NSURL.URLWithString(pageUrl))
                withTimeoutOrNull(15_000) { result.await() } ?: throw NetworkException("投稿準備が時間内に完了しなかったため送信していません")
            } finally {
                view.stopLoading()
                view.navigationDelegate = null
            }
        }
    }
}
