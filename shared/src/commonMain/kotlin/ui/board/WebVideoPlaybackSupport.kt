package com.valoser.futacha.shared.ui.board

/** Runtime checks, never a device-model/UA allowlist. The app currently requires iOS 18.2. */
internal fun supportsIosWebmPath(major: Long, minor: Long): Boolean = major > 17 || major == 17L && minor >= 4

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
