package com.valoser.futacha.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

// FIX: タイムアウト設定を定数化して管理しやすく
private const val REQUEST_TIMEOUT_MS = 30_000L // 30秒 - リクエスト全体のタイムアウト
private const val CONNECT_TIMEOUT_MS = 10_000L // 10秒 - 接続確立のタイムアウト
private const val SOCKET_TIMEOUT_MS = 30_000L  // 30秒 - データ読み書きのタイムアウト

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
                // Connection pool with timeout to prevent resource leaks
                connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))

                // Follow redirects
                followRedirects(true)

                // Retry on connection failure
                retryOnConnectionFailure(true)
            }
        }
    }
}
