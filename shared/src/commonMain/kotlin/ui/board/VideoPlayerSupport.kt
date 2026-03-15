package com.valoser.futacha.shared.ui.board

internal data class VideoPreviewChromeState(
    val isBuffering: Boolean,
    val showsError: Boolean,
    val showsCloseButton: Boolean
)

internal fun resolveReadyVideoPlayerState(isPlaying: Boolean): VideoPlayerState =
    if (isPlaying) VideoPlayerState.Ready else VideoPlayerState.Idle

internal fun normalizeVideoPlayerVolume(
    volume: Float,
    isMuted: Boolean
): Float = if (isMuted) 0f else volume.coerceIn(0f, 1f)

internal fun resolveVideoPreviewChromeState(playbackState: VideoPlayerState): VideoPreviewChromeState {
    val isBuffering = playbackState == VideoPlayerState.Buffering || playbackState == VideoPlayerState.Idle
    val showsError = playbackState == VideoPlayerState.Error
    val showsCloseButton = playbackState != VideoPlayerState.Ready
    return VideoPreviewChromeState(
        isBuffering = isBuffering,
        showsError = showsError,
        showsCloseButton = showsCloseButton
    )
}

internal fun extractVideoUrlExtension(videoUrl: String): String =
    videoUrl.substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('/')
        .substringAfterLast('.', "")
        .lowercase()

internal fun sanitizeVideoHtmlUrl(videoUrl: String): String =
    videoUrl
        .replace("<", "%3C")
        .replace(">", "%3E")
        .replace("\"", "%22")

internal fun buildEmbeddedVideoHtml(videoUrl: String): String {
    val sanitizedUrl = sanitizeVideoHtmlUrl(videoUrl)
    return """
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>
        body,html { margin:0; padding:0; background-color:black; height:100%; }
        video { width:100%; height:100%; object-fit:contain; background-color:black; }
        </style>
        </head>
        <body>
        <video controls playsinline autoplay src="$sanitizedUrl"></video>
        </body>
        </html>
        """.trimIndent()
}
