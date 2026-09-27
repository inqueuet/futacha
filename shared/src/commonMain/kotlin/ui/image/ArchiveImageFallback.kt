package com.valoser.futacha.shared.ui.image

import coil3.Extras
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import io.ktor.http.Url
import kotlinx.coroutines.withTimeoutOrNull

private val SkipArchiveImageFallbackKey = Extras.Key(false)

/** Guessed extensions must not restart the entire archive search. */
internal fun ImageRequest.Builder.skipArchiveImageFallback(): ImageRequest.Builder = apply {
    extras[SkipArchiveImageFallbackKey] = true
}

private val boardMediaPath = Regex("""^/([a-z0-9]+)/(?:(src)|(thumb))/([0-9]+s?\.[a-zA-Z0-9]+)$""")
private val bucketMediaPath = Regex("""^/scdev2/cont/(may|img)\.2chan\.net_([a-z0-9]+)_res_([0-9]+)/(img|thumb)/([0-9]+s?\.[a-zA-Z0-9]+)$""")
private val futapoMediaPath = Regex("""^/futa/(may|img)_([a-z0-9]+)/([0-9]+)/([0-9]+s?\.[a-zA-Z0-9]+)$""")

/** Only public Futaba attachment names with a known board mapping have mirror URLs. */
internal fun archiveImageFallbackCandidates(value: String): List<String> {
    val url = runCatching { Url(value) }.getOrNull() ?: return emptyList()
    if (url.protocol.name !in setOf("http", "https") || url.parameters.names().isNotEmpty() ||
        url.user != null || url.password != null) return emptyList()
    var threadId: String? = null
    val server: String
    val board: String
    val file: String
    val thumbnail: Boolean
    when (url.host) {
        "futabaforest.net", "may.inqueuet.com", "img.inqueuet.com", "may.2chan.net", "img.2chan.net" -> {
            val match = boardMediaPath.matchEntire(url.encodedPath) ?: return emptyList()
            server = if (url.host.startsWith("img.")) "img" else "may"
            board = match.groupValues[1]
            file = match.groupValues[4]
            thumbnail = match.groupValues[3].isNotEmpty()
        }
        "dev2.ftbucket.info" -> {
            val match = bucketMediaPath.matchEntire(url.encodedPath) ?: return emptyList()
            server = match.groupValues[1]
            board = match.groupValues[2]
            threadId = match.groupValues[3]
            thumbnail = match.groupValues[4] == "thumb"
            file = match.groupValues[5]
        }
        "kako.futakuro.com" -> {
            val match = futapoMediaPath.matchEntire(url.encodedPath) ?: return emptyList()
            server = match.groupValues[1]
            board = match.groupValues[2]
            threadId = match.groupValues[3]
            file = match.groupValues[4]
            thumbnail = file.substringBeforeLast('.').endsWith('s')
        }
        else -> return emptyList()
    }
    if (file.substringAfterLast('.').lowercase() !in
        com.valoser.futacha.shared.media.FUTABA_COMPAT_IMAGE_EXTENSIONS) return emptyList()
    val directory = if (thumbnail) "thumb" else "src"
    return buildList {
        if (server == "may" && board == "b") add("https://futabaforest.net/b/$directory/$file")
        add("https://$server.inqueuet.com/$board/$directory/$file")
        threadId?.let {
            add("https://dev2.ftbucket.info/scdev2/cont/$server.2chan.net_${board}_res_$it/${if (thumbnail) "thumb" else "img"}/$file")
        }
    }.distinct().filter { it != value.substringBefore('#') }
}

/** A missing image is independent of whether its archive still has the thread text. */
internal class ArchiveImageFallbackInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val result = chain.proceed()
        if (chain.request.getExtra(SkipArchiveImageFallbackKey)) return result
        if (result !is ErrorResult || !isMissingImage(result.throwable)) return result
        val data = chain.request.data
        val url = if (data is OriginalMediaRef) data.request.url else data.toString()
        val candidates = archiveImageFallbackCandidates(url)
        if (candidates.isEmpty()) return result
        return withTimeoutOrNull(30_000) {
            for (candidate in candidates) {
                val nextData = if (data is OriginalMediaRef) {
                    OriginalMediaRef(data.request.copy(url = candidate, headers = emptyMap()))
                } else candidate
                // Use the image transport's socket budget, not the short HTML
                // probe budget: a full attachment may be several megabytes.
                val recovered = withTimeoutOrNull(IMAGE_SOCKET_TIMEOUT_MILLIS) {
                    chain.withRequest(chain.request.newBuilder().data(nextData).build()).proceed()
                }
                if (recovered is SuccessResult) return@withTimeoutOrNull recovered
            }
            result
        } ?: result
    }
}
