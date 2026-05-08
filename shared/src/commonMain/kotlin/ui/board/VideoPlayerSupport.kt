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
    val isBuffering = playbackState == VideoPlayerState.Buffering
    val showsError = playbackState == VideoPlayerState.Error
    val showsCloseButton = playbackState != VideoPlayerState.Ready
    return VideoPreviewChromeState(
        isBuffering = isBuffering,
        showsError = showsError,
        showsCloseButton = showsCloseButton
    )
}

internal fun extractVideoUrlExtension(videoUrl: String): String =
    parseMediaUrlInfo(videoUrl)?.extension.orEmpty()

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
        <video controls playsinline src="$sanitizedUrl"></video>
        <script>
        (function(){
            var v = document.querySelector('video');
            function post(value){
                try {
                    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.futachaVideoState) {
                        window.webkit.messageHandlers.futachaVideoState.postMessage(value);
                    }
                } catch(e) {}
            }
            if (!v) { post('error'); return; }
            v.addEventListener('loadedmetadata', function(){
                post('size:' + (v.videoWidth || 0) + ',' + (v.videoHeight || 0));
                post('idle');
            });
            v.addEventListener('waiting', function(){ post('buffering'); });
            v.addEventListener('stalled', function(){ post('buffering'); });
            v.addEventListener('playing', function(){ post('ready'); });
            v.addEventListener('pause', function(){ post('idle'); });
            v.addEventListener('ended', function(){ post('idle'); });
            v.addEventListener('error', function(){ post('error'); });
        })();
        </script>
        </body>
        </html>
        """.trimIndent()
}
