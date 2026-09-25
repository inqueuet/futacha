package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import okhttp3.Interceptor
import okhttp3.ConnectionPool
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.BufferedSource
import android.os.Looper
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.EmptyCoroutineContext

// FIX: タイムアウト設定を定数化して管理しやすく
private const val REQUEST_TIMEOUT_MS = 75_000L
private const val CONNECT_TIMEOUT_MS = 15_000L
private const val SOCKET_TIMEOUT_MS = 45_000L

// OkHttp does not probe an idle HTTP/1 connection before reusing it for a GET,
// and this client never lets OkHttp replay a request. With the 5-minute
// default, a read after a pause could meet two connections the server had
// already closed and fail both attempts. Keep idle sockets only briefly; a
// burst of catalog/thread requests still reuses them.
private const val IDLE_CONNECTION_KEEP_ALIVE_SECONDS = 30L

/**
 * Creates a properly configured HttpClient with lifecycle management.
 * Note: Callers should manage the lifecycle and call close() when done.
 *
 * FIX: タイムアウト設定について
 * - REQUEST_TIMEOUT_MS: リクエスト全体（接続+読み書き）の最大時間
 * - CONNECT_TIMEOUT_MS: サーバーへの接続確立の最大時間
 * - SOCKET_TIMEOUT_MS: データ読み書きの最大待機時間
 * - 大きなファイルダウンロードでは、個別にタイムアウトを設定することを推奨
 */
actual fun createHttpClient(
    platformContext: Any?,
    cookieStorage: CookiesStorage?
): HttpClient {
    return HttpClient(OkHttp) {
        // Futaba's legacy HTTP servers occasionally close a keep-alive socket
        // before OkHttp receives the next response headers. Retry only
        // idempotent reads here so catalog/thread/media loads recover without
        // ever replaying a reply, thread creation, or deletion POST.
        install(HttpRequestRetry) {
            maxRetries = 2
            exponentialDelay()
            retryIf(maxRetries) { request, response ->
                shouldUseClientAutomaticRetry(
                    method = request.method,
                    higherLayerRetryManaged = request.attributes.getOrNull(HigherLayerRetryManaged) == true
                ) && response.status.value in 500..599
            }
            retryOnExceptionIf { request, cause ->
                shouldUseClientAutomaticRetry(
                    method = request.method,
                    higherLayerRetryManaged = request.attributes.getOrNull(HigherLayerRetryManaged) == true
                ) && cause !is CancellationException
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }

        install(HttpCookies) {
            storage = cookieStorage ?: AcceptAllCookiesStorage()
        }

        engine {
            config {
                connectionPool(ConnectionPool(5, IDLE_CONNECTION_KEEP_ALIVE_SECONDS, TimeUnit.SECONDS))

                // Ktor closes the OkHttp response body from the coroutine completion
                // handler. Compose can cancel that coroutine on the main thread when a
                // lazy item leaves the composition. Conscrypt may perform TLS I/O while
                // closing the socket, which would otherwise throw
                // NetworkOnMainThreadException from the completion handler.
                addInterceptor(MainThreadSafeResponseCloseInterceptor)

                // Follow redirects
                followRedirects(true)

                // The client is shared by reads and non-idempotent posting
                // requests. OkHttp can replay a buffered POST after a route or
                // connection failure, which risks duplicate replies/threads.
                // Higher layers already expose explicit refresh/retry actions.
                retryOnConnectionFailure(false)
            }
        }
    }
}

internal object MainThreadSafeResponseCloseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val response = chain.proceed(chain.request())
        return response.newBuilder()
            .body(MainThreadSafeResponseBody(response.body))
            .build()
    }
}

/**
 * Defers only [ResponseBody.close] when Ktor invokes it on Android's main thread.
 * Reads continue to use OkHttp's original source and therefore keep their normal
 * cancellation and back-pressure behaviour.
 */
internal class MainThreadSafeResponseBody(
    private val delegate: ResponseBody,
    private val isMainThread: () -> Boolean = {
        Looper.getMainLooper().thread === Thread.currentThread()
    },
    private val dispatchClose: (() -> Unit) -> Unit = { close ->
        Dispatchers.IO.dispatch(EmptyCoroutineContext, Runnable(close))
    }
) : ResponseBody() {
    private val closeRequested = AtomicBoolean(false)

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun source(): BufferedSource = delegate.source()

    override fun close() {
        if (!closeRequested.compareAndSet(false, true)) return

        if (isMainThread()) {
            dispatchClose(::closeDelegate)
        } else {
            closeDelegate()
        }
    }

    private fun closeDelegate() {
        runCatching { delegate.close() }
            .onFailure { error ->
                Logger.e(
                    "MainThreadSafeResponseBody",
                    "Failed to close OkHttp response body",
                    error
                )
            }
    }
}
