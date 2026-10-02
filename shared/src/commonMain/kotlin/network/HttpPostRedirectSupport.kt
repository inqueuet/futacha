package com.valoser.futacha.shared.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpRequestRetryConfig
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.authority
import io.ktor.client.utils.EmptyContent
import io.ktor.http.isSecure
import io.ktor.http.takeFrom
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException

// The statuses OkHttp (and browsers) answer a POST with a GET of Location.
// 307/308 require resending the body, which could duplicate a reply, so they
// are returned to the caller unchanged as before.
private val POST_REDIRECT_TO_GET_STATUSES = setOf(301, 302, 303)

/**
 * Requests sent for one call, redirect hops and HttpRequestRetry attempts included.
 * Same as OkHttp's 20 redirects; a loop fails with SendCountExceedException.
 */
internal const val MAX_SENDS_PER_CALL = 20

internal fun shouldFollowPostRedirectAsGet(method: HttpMethod, status: Int, location: String?): Boolean =
    method == HttpMethod.Post && status in POST_REDIRECT_TO_GET_STATUSES && !location.isNullOrBlank()

// Statuses HttpRedirect follows for GET.
private val GET_REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

/**
 * Ktor's HttpRedirect only follows GET/HEAD. The engines no longer follow
 * redirects themselves (cookies must be handled at every hop), so a Futaba
 * POST answered with 302/303 would be reported as a failure although the
 * server accepted it. Follow it with a body-less GET, exactly as OkHttp did,
 * and follow any further redirects of that GET here too: HttpRedirect wraps
 * this plugin and rebuilds every hop from the original request, so leaving
 * them to it would re-send the POST body to each redirect target. Every hop
 * still goes through HttpSend, whose per-call send limit
 * ([MAX_SENDS_PER_CALL]) ends redirect loops.
 */
@OptIn(InternalAPI::class)
private val FollowPostRedirectAsGet = createClientPlugin("FutachaFollowPostRedirectAsGet") {
    on(Send) { request ->
        val origin = proceed(request)
        val location = origin.response.headers[HttpHeaders.Location]
        if (!shouldFollowPostRedirectAsGet(origin.request.method, origin.response.status.value, location)) {
            return@on origin
        }
        var call = origin
        var hopLocation: String? = location
        var base = request
        while (hopLocation != null) {
            val previousUrl = call.request.url
            val next = HttpRequestBuilder().apply {
                takeFromWithExecutionContext(base)
                method = HttpMethod.Get
                setBody(EmptyContent)
                headers.remove(HttpHeaders.ContentType)
                headers.remove(HttpHeaders.ContentLength)
                headers.remove(HttpHeaders.TransferEncoding)
                url.parameters.clear()
                url.takeFrom(hopLocation)
            }
            // Same security rules as HttpRedirect.
            if (previousUrl.protocol.isSecure() && !next.url.protocol.isSecure()) break
            if (previousUrl.authority != next.url.authority) next.headers.remove(HttpHeaders.Authorization)
            call = proceed(next)
            base = next
            hopLocation = call.response.headers[HttpHeaders.Location]
                .takeIf { call.response.status.value in GET_REDIRECT_STATUSES }
        }
        call
    }
}

/**
 * Redirect policy shared by every engine. The engines never follow redirects
 * themselves (Darwin disables it in Ktor; OkHttp is configured off), so
 * HttpSend's per-call limit is the only redirect cap and is pinned here
 * instead of relying on Ktor's default.
 */
internal fun HttpClientConfig<*>.installPostRedirectFollowing() {
    install(HttpSend) { maxSendCount = MAX_SENDS_PER_CALL }
    install(FollowPostRedirectAsGet)
}

/** Automatic retries of the platform clients; reads only, see [shouldUseClientAutomaticRetry]. */
internal const val CLIENT_AUTOMATIC_MAX_RETRIES = 2

/**
 * Installs the shared POST redirect following and the read retry of every
 * platform client, in the only safe order.
 *
 * HttpSend runs its interceptors in installation order, the first one
 * outermost. HttpRequestRetry judges a response by the request that produced
 * it (`call.request`) but resends its own sub-request. Installed outside
 * FollowPostRedirectAsGet, it saw a POST's redirected GET answer 5xx, judged
 * the GET safe and sent the original POST, body included, again: a duplicate
 * reply or thread. Installed inside, it only ever sees and resends the request
 * it sent itself, so the POST is never retried while a 5xx (or connection
 * failure) of the redirected GET is still retried as a GET.
 *
 * Futaba's legacy HTTP servers occasionally close a keep-alive socket before
 * the next response headers arrive, so idempotent reads are retried here and
 * catalog/thread/media loads recover without ever replaying a reply, thread
 * creation or deletion POST.
 */
internal fun HttpClientConfig<*>.installPostRedirectFollowingAndReadRetry(
    configureRetryDelay: HttpRequestRetryConfig.() -> Unit = { exponentialDelay() }
) {
    installPostRedirectFollowing()
    install(HttpRequestRetry) {
        maxRetries = CLIENT_AUTOMATIC_MAX_RETRIES
        configureRetryDelay()
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
}
