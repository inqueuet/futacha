package com.valoser.futacha.shared.ui.board

/** The viewer opens media ready for a deliberate user tap, never autoplaying. */
internal const val FUTACHA_VIDEO_PREVIEW_AUTOPLAY: Boolean = false
/** Keep the visible transport buttons and double-tap gesture on the same interval. */
internal const val REFERENCE_VIDEO_SEEK_STEP_MILLIS: Long = 10_000L

internal fun resolveReferenceVideoDoubleTapPosition(
    currentPositionMillis: Long,
    durationMillis: Long,
    tappedRightHalf: Boolean
): Long {
    val upperBound = durationMillis.takeIf { it >= 0L } ?: Long.MAX_VALUE
    val current = currentPositionMillis.coerceIn(0L, upperBound)
    return if (tappedRightHalf) {
        // Difference-based clamping also avoids overflowing Long for a live
        // stream whose duration is not known yet.
        if (upperBound - current < REFERENCE_VIDEO_SEEK_STEP_MILLIS) upperBound
        else current + REFERENCE_VIDEO_SEEK_STEP_MILLIS
    } else {
        (current - REFERENCE_VIDEO_SEEK_STEP_MILLIS).coerceAtLeast(0L)
    }
}

internal data class VideoPreviewChromeState(
    val isBuffering: Boolean,
    val showsError: Boolean,
    val showsCloseButton: Boolean,
    val showsControlPanel: Boolean
)

internal data class VideoPlayerWebSyncState(
    val isMuted: Boolean,
    val volume: Float,
    val areControlsVisible: Boolean
)

internal fun resolveReadyVideoPlayerState(isPlaying: Boolean): VideoPlayerState =
    if (isPlaying) VideoPlayerState.Ready else VideoPlayerState.Idle

internal fun normalizeVideoPlayerVolume(
    volume: Float,
    isMuted: Boolean
): Float = if (isMuted) 0f else volume.coerceIn(0f, 1f)

internal fun resolveVideoPlayerWebSyncState(
    volume: Float,
    isMuted: Boolean,
    areControlsVisible: Boolean
): VideoPlayerWebSyncState = VideoPlayerWebSyncState(
    isMuted = isMuted,
    volume = normalizeVideoPlayerVolume(volume, isMuted),
    areControlsVisible = areControlsVisible
)

internal fun shouldApplyVideoPlayerWebSyncState(
    appliedState: VideoPlayerWebSyncState?,
    pendingState: VideoPlayerWebSyncState?,
    nextState: VideoPlayerWebSyncState
): Boolean {
    return nextState != appliedState && nextState != pendingState
}

internal fun resolveVideoPreviewChromeState(
    playbackState: VideoPlayerState,
    controlsVisible: Boolean = playbackState != VideoPlayerState.Ready
): VideoPreviewChromeState {
    val isBuffering = playbackState == VideoPlayerState.Buffering
    val showsError = playbackState == VideoPlayerState.Error
    val showsPlaybackChrome = playbackState != VideoPlayerState.Ready || controlsVisible
    return VideoPreviewChromeState(
        isBuffering = isBuffering,
        showsError = showsError,
        showsCloseButton = showsPlaybackChrome,
        showsControlPanel = showsPlaybackChrome
    )
}

internal fun extractVideoUrlExtension(videoUrl: String): String =
    parseMediaUrlInfo(videoUrl)?.extension.orEmpty()

internal enum class IosVideoPlaybackBackend {
    AV_PLAYER,
    WEB_VIEW
}

/** AVPlayer handles Apple-native containers; WebM keeps the established WKWebView path. */
internal fun resolveIosVideoPlaybackBackend(videoUrl: String): IosVideoPlaybackBackend =
    when (extractVideoUrlExtension(videoUrl)) {
        "mp4", "m4v", "mov" -> IosVideoPlaybackBackend.AV_PLAYER
        else -> IosVideoPlaybackBackend.WEB_VIEW
    }

internal fun formatVideoPlaybackError(error: VideoPlaybackError?): String? {
    if (error == null) return null
    val code = error.code.orEmpty().trim().take(80)
    val message = error.message.orEmpty()
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .take(180)
    return listOfNotNull(
        code.takeIf(String::isNotBlank)?.let { "エラー: $it" },
        message.takeIf(String::isNotBlank)
    ).joinToString(" / ").ifBlank { null }
}

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
        <video controls playsinline preload="metadata" src="$sanitizedUrl"></video>
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
            if (!v) { post('error:missing_video:video element was not created'); return; }
            function showControls(){
                v.controls = true;
                post('controls_visible');
            }
            function hideControls(){
                v.controls = false;
                post('controls_hidden');
            }
            v.addEventListener('loadedmetadata', function(){
                // Metadata preparation is intentionally not automatic.  The
                // user starts playback through the native video controls.
                v.pause();
                post('size:' + (v.videoWidth || 0) + ',' + (v.videoHeight || 0));
                post('media:' + (v.videoWidth || 0) + ',' + (v.videoHeight || 0) + ',' + (Number.isFinite(v.duration) ? v.duration : -1));
                post('idle');
            });
            v.addEventListener('waiting', function(){ post('buffering'); });
            v.addEventListener('stalled', function(){ post('buffering'); });
            v.addEventListener('playing', function(){ hideControls(); post('ready'); });
            v.addEventListener('pause', function(){ showControls(); post('idle'); });
            v.addEventListener('ended', function(){ post('idle'); });
            v.addEventListener('error', function(){
                var mediaError = v.error;
                var code = mediaError ? String(mediaError.code || 'unknown') : 'unknown';
                var message = mediaError && mediaError.message ? String(mediaError.message) : '';
                post('error:' + code + ':' + message);
            });
            v.addEventListener('touchstart', showControls, { passive: true });
            v.addEventListener('click', showControls);
            var lastTapAt = 0;
            function seekFromDoubleTap(clientX){
                var right = clientX >= (window.innerWidth / 2);
                var next = v.currentTime + (right ? 10 : -10);
                var end = Number.isFinite(v.duration) ? v.duration : next;
                v.currentTime = Math.max(0, Math.min(end, next));
                showControls();
            }
            v.addEventListener('touchend', function(event){
                var now = Date.now();
                if (now - lastTapAt <= 350 && event.changedTouches && event.changedTouches.length === 1) {
                    seekFromDoubleTap(event.changedTouches[0].clientX);
                    lastTapAt = 0;
                    event.preventDefault();
                } else {
                    lastTapAt = now;
                }
            }, { passive: false });
            v.addEventListener('dblclick', function(event){
                seekFromDoubleTap(event.clientX);
                event.preventDefault();
            });
        })();
        </script>
        </body>
        </html>
        """.trimIndent()
}

internal fun formatVideoMediaInfoLines(info: VideoMediaInfo): List<String> = buildList {
    info.videoCodec?.let { add("映像コーデック: $it") }
    info.codecId?.let { add("Codec ID: $it") }
    info.profile?.let { add("Profile: $it") }
    info.level?.let { add("Level: $it") }
    if (info.width != null && info.height != null) add("解像度: ${info.width} × ${info.height}")
    info.frameRate?.let { add("フレームレート: ${it.toInt()} fps") }
    info.bitrate?.let { add("ビットレート: ${it / 1_000} kbps") }
    info.audioCodec?.let { add("音声コーデック: $it") }
    info.sampleRate?.let { add("サンプルレート: ${it} Hz") }
    info.channelCount?.let { add("チャンネル数: $it") }
    info.durationMillis?.let { add("長さ: ${it / 1_000.0} 秒") }
}

internal data class VideoCodecProfileLevel(val profile: String?, val level: String?)

internal fun parseVideoCodecProfileLevel(codecId: String?): VideoCodecProfileLevel {
    val codec = codecId?.substringBefore(',')?.trim().orEmpty()
    val parts = codec.split('.')
    return when (parts.firstOrNull()?.lowercase()) {
        "avc1", "avc2" -> {
            val hex = parts.getOrNull(1).orEmpty()
            val profileCode = hex.take(2).toIntOrNull(16)
            val levelCode = hex.drop(4).take(2).toIntOrNull(16)
            VideoCodecProfileLevel(
                profile = when (profileCode) {
                    0x42 -> "Baseline"
                    0x4D -> "Main"
                    0x58 -> "Extended"
                    0x64 -> "High"
                    0x6E -> "High 10"
                    0x7A -> "High 4:2:2"
                    0xF4 -> "High 4:4:4"
                    else -> profileCode?.toString()
                },
                level = levelCode?.let { "${it / 10}.${it % 10}" }
            )
        }
        "vp09" -> VideoCodecProfileLevel(
            profile = parts.getOrNull(1)?.toIntOrNull()?.toString(),
            level = parts.getOrNull(2)?.toIntOrNull()?.let { "${it / 10}.${it % 10}" }
        )
        "hvc1", "hev1" -> VideoCodecProfileLevel(
            profile = parts.getOrNull(1),
            level = parts.firstOrNull { it.startsWith("L", true) || it.startsWith("H", true) }
                ?.drop(1)
                ?.toIntOrNull()
                ?.let { "${it / 30}.${(it % 30) / 3}" }
        )
        else -> VideoCodecProfileLevel(profile = null, level = null)
    }
}
