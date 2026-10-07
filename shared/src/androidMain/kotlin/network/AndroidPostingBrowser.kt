package com.valoser.futacha.shared.network

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.ByteArrayInputStream

internal class AndroidPostingBrowser(private val context: Context) : PostingBrowser {
    private val mutex = Mutex()
    override suspend fun userAgent(): String = withContext(Dispatchers.Main) { WebSettings.getDefaultUserAgent(context) }

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun evaluate(pageUrl: String, html: String, script: String): String = mutex.withLock {
        withContext(Dispatchers.Main) {
            val result = CompletableDeferred<String>()
            val view = WebView(context)
            try {
                view.settings.javaScriptEnabled = true
                view.settings.domStorageEnabled = true
                view.settings.allowFileAccess = false
                view.settings.allowContentAccess = false
                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse =
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (result.isCompleted) return
                        view.evaluateJavascript(script) { encoded ->
                            // A script exception makes evaluateJavascript answer null / "null".
                            val decoded = runCatching { Json.parseToJsonElement(encoded ?: "null") }.getOrNull()
                            if (decoded == null || decoded is JsonNull || (decoded as? JsonPrimitive)?.contentOrNull == null) {
                                result.completeExceptionally(NetworkException("投稿の準備に失敗したため送信していません（公式の送信前処理でエラーが発生しました）"))
                            } else {
                                result.complete(decoded.jsonPrimitive.content)
                            }
                        }
                    }
                    // The renderer died (low memory, a crash): without an answer of `true` the whole app is killed with it.
                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                        result.completeExceptionally(NetworkException("投稿の準備中にブラウザー部品が停止したため送信していません"))
                        return true
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) result.completeExceptionally(NetworkException("投稿準備画面を読み込めませんでした"))
                    }
                }
                view.loadDataWithBaseURL(pageUrl, html, "text/html", "UTF-8", null)
                withTimeoutOrNull(15_000) { result.await() } ?: throw NetworkException("投稿準備が時間内に完了しなかったため送信していません")
            } finally {
                view.stopLoading()
                view.destroy()
            }
        }
    }
}
