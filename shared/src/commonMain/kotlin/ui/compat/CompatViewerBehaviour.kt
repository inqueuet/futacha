package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ui.board.VideoPlaybackOptions
import com.valoser.futacha.shared.ui.board.platformVideoAutoPlayByDefault

/** 参考アプリ 18-20's viewer settings (設定 → 画像ビューア → 閲覧). */
internal const val COMPAT_VIEWER_TAP_TOOLBAR_KEY = "viewerTapToolbar"
internal const val COMPAT_VIEWER_VIDEO_AUTO_HIDE_KEY = "viewerVideoControlAutoHide"
internal const val COMPAT_VIEWER_VIDEO_AUTO_PLAY_KEY = "viewerVideoAutoPlay"

/** 18-20 hides the playing video's panel after 3.5 seconds when 「自動で隠す」 is ON. */
internal const val COMPAT_VIEWER_VIDEO_AUTO_HIDE_MILLIS = 3_500

internal data class CompatViewerBehaviour(
    /** ON (18-20's default): a tap on the picture shows or hides the bars, and a video's panel and bars move together. */
    val tapTogglesToolbar: Boolean,
    val videoOptions: VideoPlaybackOptions
)

/**
 * [reference] is としあき(仮), which follows 参考アプリ 18-20. ふたちゃ's shared
 * viewer passes false: the switches still act once saved, but unsaved values
 * keep its own player (no loop, the platform's panel timing, bars apart).
 */
internal fun compatViewerBehaviour(
    preferences: Map<String, String>,
    reference: Boolean = true,
    autoPlayByDefault: Boolean = platformVideoAutoPlayByDefault
): CompatViewerBehaviour {
    fun value(key: String) = preferences.compatPreferenceValue("viewer", key)
    val tap = value(COMPAT_VIEWER_TAP_TOOLBAR_KEY) != "OFF"
    val autoHide = value(COMPAT_VIEWER_VIDEO_AUTO_HIDE_KEY)
    return CompatViewerBehaviour(
        tapTogglesToolbar = tap,
        videoOptions = VideoPlaybackOptions(
            // OFF keeps this app's decision not to start a video by itself; unsaved keeps
            // the platform's own player (the desktop starts by itself).
            autoPlay = when (value(COMPAT_VIEWER_VIDEO_AUTO_PLAY_KEY)) {
                "ON" -> true
                "OFF" -> false
                else -> autoPlayByDefault
            },
            // reference app (18-10b and 18-20) plays a video over and over.
            loop = reference,
            // 18-20's default OFF keeps the panel until a tap; 0 means "never hide" to the player.
            controlsAutoHideMillis = when {
                autoHide == "ON" -> COMPAT_VIEWER_VIDEO_AUTO_HIDE_MILLIS
                reference || autoHide == "OFF" -> 0
                else -> null
            },
            controlsFollowChrome = tap && (reference || value(COMPAT_VIEWER_TAP_TOOLBAR_KEY) == "ON")
        )
    )
}
