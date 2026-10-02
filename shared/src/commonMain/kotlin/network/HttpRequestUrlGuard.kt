package com.valoser.futacha.shared.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLBuilder
import kotlinx.io.IOException

/** A request URL whose host other URL parsers would read differently; refused before sending. */
internal class AmbiguousRequestUrlException(message: String) : IOException(message)

/**
 * S4-1 defence in depth for URLs that were stored or passed along before they
 * were validated. Ktor ends the authority at `\`, so `https://evil.com\@may.2chan.net/b/res/1.htm`
 * is sent to evil.com with `\@may.2chan.net/b/res/1.htm` as its path, while a
 * string check reads it as a 2chan.net URL. Userinfo (`user@host`) hides the
 * host the same way. Neither is part of any URL this client is asked to load,
 * so such a request fails as a network error instead of reaching the host.
 */
internal fun isAmbiguousRequestUrl(url: URLBuilder): Boolean =
    url.encodedUser != null ||
        url.encodedPassword != null ||
        url.host.any(::isAmbiguousRequestHostChar) ||
        url.encodedPathSegments.any { '\\' in it }

// A host with `%`-escapes, whitespace or control characters is decoded or
// split differently by the engines (`evil.com%5C%40may.2chan.net`); no request
// host needs them.
private fun isAmbiguousRequestHostChar(char: Char): Boolean =
    char == '%' || char == '\\' || char == '@' || char.isWhitespace() || char.isISOControl()

private val RejectAmbiguousRequestUrl = createClientPlugin("FutachaRejectAmbiguousRequestUrl") {
    onRequest { request, _ ->
        if (isAmbiguousRequestUrl(request.url)) {
            throw AmbiguousRequestUrlException("Refused an ambiguous request URL")
        }
    }
}

internal fun HttpClientConfig<*>.installAmbiguousRequestUrlGuard() {
    install(RejectAmbiguousRequestUrl)
}
