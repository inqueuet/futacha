package com.valoser.futacha.shared.ui.board

import kotlinx.coroutines.ensureActive

/** Runtime checks, never a device-model/UA allowlist. */
internal fun supportsIosWebmPath(major: Long, minor: Long): Boolean = major > 17 || major == 17L && minor >= 4

internal fun supportsIosAsyncVideoComposition(major: Long): Boolean = major >= 16

internal const val WEB_VIDEO_LOAD_TIMEOUT_MS = 20_000L
internal const val WEB_VIDEO_STALL_TIMEOUT_MS = 15_000L

/** Only URLs supplied by the service/caller; a .webm → .mp4 filename guess is not a fallback. */
internal fun videoPlaybackSources(primary: String, alternatives: List<String>): List<String> =
    listOf(primary) + alternatives.filter {
        it.isNotBlank() && it != primary && extractVideoUrlExtension(it) in setOf("mp4", "m4v", "mov") &&
            (it.startsWith("https://", true) || it.startsWith("http://", true) || it.startsWith("file://", true))
    }.distinct().take(3)

internal expect fun logVideoPlaybackDiagnostic(event: String, detail: String)

/** Called on the existing transfer, including rejected responses. Does not issue a diagnostic GET. */
internal fun logVideoHttpResponse(url: String, status: Int, headers: Map<String, String?>) {
    if (extractVideoUrlExtension(url) !in setOf("webm", "mp4", "mov", "m4v")) return
    logVideoPlaybackDiagnostic("http", "url=$url status=$status " + headers.entries.joinToString(" ") { "${it.key}=${it.value ?: "absent"}" })
}

/** One capability check shared by source acquisition and the platform resource factories. */
internal expect fun platformVideoPlaybackCapabilityError(
    videoUrl: String,
    resolvedInfo: com.valoser.futacha.shared.media.source.OriginalMediaInfo? = null
): VideoPlaybackError?

internal fun iosVideoPlaybackCapabilityError(
    videoUrl: String,
    webmAvailable: Boolean,
    resolvedInfo: com.valoser.futacha.shared.media.source.OriginalMediaInfo? = null
): VideoPlaybackError? {
    val mime = resolvedInfo?.mimeType?.substringBefore(';')?.trim()?.lowercase()
    val webm = extractVideoUrlExtension(videoUrl) == "webm" ||
        resolvedInfo?.resolvedUrl?.let(::extractVideoUrlExtension) == "webm" ||
        mime == "video/webm" || mime == "audio/webm"
    return if (webm && !webmAvailable) VideoPlaybackError("webm_os_version", "WebMの再生にはiOS 17.4以降が必要です") else null
}

internal class UnsupportedVideoPlayback(val error: VideoPlaybackError) : IllegalStateException(error.message)

/** Check before invoking a factory: even a cancelled or unsupported attempt must not acquire resources. */
internal suspend fun <T> prepareVideoPlaybackResource(
    videoUrl: String,
    resolvedInfo: com.valoser.futacha.shared.media.source.OriginalMediaInfo? = null,
    capability: (String, com.valoser.futacha.shared.media.source.OriginalMediaInfo?) -> VideoPlaybackError? = ::platformVideoPlaybackCapabilityError,
    factory: suspend () -> T
): T {
    kotlinx.coroutines.currentCoroutineContext().ensureActive()
    capability(videoUrl, resolvedInfo)?.let { throw UnsupportedVideoPlayback(it) }
    return factory()
}
