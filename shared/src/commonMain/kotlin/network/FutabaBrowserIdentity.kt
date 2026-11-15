package com.valoser.futacha.shared.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Every request to Futaba (2chan.net) — reads, pictures, the posting form and the post itself —
 * goes out as the device's own web browser: one User-Agent (the system WebView's) and the
 * headers a browser sends. The app used to send a desktop Chrome for reads, the phone's
 * browser for posts and the HTTP library's own name for pictures, all under one cookie.
 * Without a browser (the desktop app) the requests keep the values their callers set.
 */
internal object FutabaBrowserIdentity {
    @Volatile private var browser: PostingBrowser? = null
    @Volatile private var cached: String? = null
    private val lock = Mutex()

    fun register(browser: PostingBrowser) {
        if (this.browser == null) this.browser = browser
    }

    /** The browser's User-Agent, asked once; null without a browser or when it cannot answer. */
    suspend fun userAgent(): String? {
        cached?.let { return it }
        val current = browser ?: return null
        return lock.withLock {
            cached ?: runCatching { current.userAgent() }.getOrNull()
                ?.takeIf { it.isNotBlank() && '\n' !in it && '\r' !in it }
                ?.also { cached = it }
        }
    }

    internal fun resetForTest(browser: PostingBrowser? = null) {
        this.browser = browser
        cached = null
    }
}

internal fun isFutabaHost(host: String): Boolean {
    val h = host.lowercase().trimEnd('.')
    return h == "2chan.net" || h.endsWith(".2chan.net")
}

internal const val FUTABA_BROWSER_ACCEPT_LANGUAGE = "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7"

private val FutabaBrowserIdentityPlugin = createClientPlugin("FutabaBrowserIdentity") {
    // Runs for every send, redirects and retries included.
    on(SendingRequest) { request, _ ->
        if (!isFutabaHost(request.url.host)) return@on
        val agent = FutabaBrowserIdentity.userAgent() ?: return@on
        request.headers[HttpHeaders.UserAgent] = agent
        if (request.headers[HttpHeaders.AcceptLanguage] == null) {
            request.headers[HttpHeaders.AcceptLanguage] = FUTABA_BROWSER_ACCEPT_LANGUAGE
        }
    }
}

internal fun HttpClientConfig<*>.installFutabaBrowserIdentity() {
    install(FutabaBrowserIdentityPlugin)
}
