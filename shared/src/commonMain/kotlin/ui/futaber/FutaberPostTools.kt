package com.valoser.futacha.shared.ui.futaber

import kotlin.math.abs

/*
 * L-B8 (docs/futaber-evolution-look-2026-10-31.md §2.8, §4.2 B8): what the writing screen of the original has, in its
 * order, and this app's own tools after it. Pure, so the order and the decisions are fixed by the common JVM tests.
 */

/** One place of the writing screen's tool row (M36). [id] is also the end of the tag `futaber-post-<tag>`. */
internal data class FutaberPostToolSlot(
    val id: String,
    /** The test tag of the button (the tags the earlier screen used are kept). */
    val tag: String,
    /** After the original's last tool: this app's own (F-B2 may put these behind 「その他の機能」). */
    val extra: Boolean = false,
    /** A place kept empty so the tools after it stay where the original has them (紙飛行機, M40, until it is captured). */
    val empty: Boolean = false
)

/** The original's six places: カメラ／ｶﾅ／用紙／紙飛行機／×／歯車 (`P/out-post/90-01-post.txt`, 44.2pt each). */
internal fun futaberPostOriginalTools(): List<FutaberPostToolSlot> = listOf(
    FutaberPostToolSlot("camera", "futaber-post-attach"),
    FutaberPostToolSlot("kana", "futaber-post-kana"),
    FutaberPostToolSlot("paper", "futaber-post-pasteboard"),
    // M40: what the paper plane does is not captured yet (L-B0); its place stays empty so × and the gear keep theirs.
    FutaberPostToolSlot("plane", "futaber-post-plane", empty = true),
    FutaberPostToolSlot("clear", "futaber-post-clear"),
    FutaberPostToolSlot("settings", "futaber-post-settings")
)

/**
 * This app's tools after the original's last one (§2.8: 画像の貼り付け, 引用モード). One function, so F-B2 can put the
 * result behind 「その他の機能」. [canQuote]: the thread the reply is for is on screen behind the writing.
 */
internal fun futaberPostToolExtras(canQuote: Boolean): List<FutaberPostToolSlot> = buildList {
    add(FutaberPostToolSlot("paste", "futaber-post-paste", extra = true))
    if (canQuote) add(FutaberPostToolSlot("quote-mode", "futaber-post-quote-mode", extra = true))
}

/** The whole row: the original's six places, then this app's tools. */
internal fun futaberPostTools(canQuote: Boolean): List<FutaberPostToolSlot> =
    futaberPostOriginalTools() + futaberPostToolExtras(canQuote)

/** The label of the original's kana tool: written in half-width katakana as the original shows it. */
internal const val FUTABER_POST_KANA_LABEL = "ｶﾅ"

/** One tool of the row is 44pt wide in the original (265 / 6 ≈ 44.2); "送信" is a 90×30 frame on the right. */
internal const val FUTABER_POST_TOOL_DP = 44
internal const val FUTABER_POST_SEND_WIDTH_DP = 90
internal const val FUTABER_POST_SEND_HEIGHT_DP = 30

// ---- M37: the camera's black menu ----

/** One item of the camera's menu. */
internal data class FutaberCameraMenuEntry(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val extra: Boolean = false,
    /** The board refuses what this item would pick (an img reply): pressing it says why and where the drawing is. */
    val refused: Boolean = false
)

/**
 * The original's items (`P/out-post/90-08-camera.png`: 最近の画像／ファイル／ライブラリ／カメラ) without its 「カメラ」
 * (2026-11-04, the user's decision: the app takes no picture). 「最近の画像」 is the OS photo picker (plan §4.2: no
 * permission to read the photo library), as is 「ライブラリ」; 「ファイル」 is the file picker of before. [filesRefused]: a reply on a board whose replies take no file (img's) — only a drawing can go.
 */
internal fun futaberCameraMenuOriginals(filesRefused: Boolean): List<FutaberCameraMenuEntry> = listOf(
    FutaberCameraMenuEntry("recent", "最近の画像", refused = filesRefused),
    FutaberCameraMenuEntry("file", "ファイル", refused = filesRefused),
    FutaberCameraMenuEntry("library", "ライブラリ", refused = filesRefused)
)

/**
 * This app's writing aids after the original's items (they hung off the camera's long press before L-B8): 手書き where it
 * is needed or with the extension 「書き込みツール」, and with the extension あぷ小・音声入力・回線情報・機種情報. One function,
 * so F-B2 can regroup them.
 */
internal fun futaberCameraMenuExtras(filesRefused: Boolean, toolsEnabled: Boolean, canUpload: Boolean): List<FutaberCameraMenuEntry> =
    buildList {
        if (filesRefused || toolsEnabled) add(FutaberCameraMenuEntry("tool-drawing", "手書き", extra = true))
        if (toolsEnabled) {
            add(FutaberCameraMenuEntry("tool-ups", "あぷ小", enabled = canUpload, extra = true))
            add(FutaberCameraMenuEntry("tool-voice", "音声入力", extra = true))
            add(FutaberCameraMenuEntry("tool-network-info", "回線情報", extra = true))
            add(FutaberCameraMenuEntry("tool-device-info", "機種情報", extra = true))
        }
    }

internal fun futaberCameraMenu(filesRefused: Boolean, toolsEnabled: Boolean, canUpload: Boolean): List<FutaberCameraMenuEntry> =
    futaberCameraMenuOriginals(filesRefused) + futaberCameraMenuExtras(filesRefused, toolsEnabled, canUpload)

// ---- V17: the send button ----

internal enum class FutaberPostSendStyle {
    /** Nothing written and nothing attached: a blue frame that cannot be pressed (the original's). */
    Outline,
    /** Something to send: the blue fill. A missing delete key and the like are told when it is pressed. */
    Filled,
    /** A post or a picture is on its way: the frame, not pressable. */
    Busy
}

internal fun futaberPostSendStyle(comment: String, hasAttachment: Boolean, busy: Boolean): FutaberPostSendStyle = when {
    busy -> FutaberPostSendStyle.Busy
    comment.isEmpty() && !hasAttachment -> FutaberPostSendStyle.Outline
    else -> FutaberPostSendStyle.Filled
}

// ---- F18: the counter ----

/** 「0行 / 0B」 for an empty text (the original's), else the lines and the Shift_JIS bytes. */
internal fun futaberPostCounterText(lines: Int, bytes: Int): String = "${lines}行 / ${bytes}B"

// ---- M42: × and its long press ----

/** What × last cleared, kept while the writing screen is up (the original's 「最後に削除したテキストを復元」). */
internal data class FutaberClearedText(val subject: String, val comment: String)

/** After × : what is remembered. Clearing nothing keeps what an earlier × cleared. */
internal fun futaberRememberCleared(subject: String, comment: String, previous: FutaberClearedText?): FutaberClearedText? =
    if (subject.isEmpty() && comment.isEmpty()) previous else FutaberClearedText(subject, comment)

/**
 * Restoring: the cleared text comes back. Text typed since then is not lost — it becomes what is remembered (a second
 * restore brings it back).
 */
internal fun futaberRestoreCleared(subject: String, comment: String, remembered: FutaberClearedText): Pair<FutaberClearedText, FutaberClearedText?> =
    remembered to futaberRememberCleared(subject, comment, null)

internal const val FUTABER_RESTORE_CLEARED_LABEL = "最後に削除したテキストを復元"

// ---- G12: pulling the text down into quote mode ----

/**
 * The body's pull into quote mode (G12): only a drag the finger started while the text was at its top counts, and only
 * what the text could not scroll (the nested scroll's leftover downwards). A long text scrolls as before; a text being
 * selected is left to the selection.
 */
internal class FutaberQuotePull(private val thresholdPx: Float) {
    private var startedAtTop = false
    private var pulled = 0f
    private var fired = false

    fun onDown(atTop: Boolean, selecting: Boolean) {
        startedAtTop = atTop && !selecting
        pulled = 0f
        fired = false
    }

    /** The leftover of one move ([leftoverY] > 0 is downwards); true once when the pull is enough. */
    fun onLeftover(leftoverY: Float): Boolean {
        if (!startedAtTop || fired) return false
        pulled = (pulled + leftoverY).coerceAtLeast(0f)
        if (pulled > thresholdPx) {
            fired = true
            return true
        }
        return false
    }

    /** The text scrolled upwards (the finger went up): the pull starts again. */
    fun onScrolledBack() { pulled = 0f }

    /**
     * The finger's whole move since it went down ([dx], [dy]) while the text is still at its top ([atTop]): the same
     * pull read from the touch itself, for a text too short to scroll (its scroll may hand nothing on). Mostly vertical.
     */
    fun onFingerMoved(dx: Float, dy: Float, atTop: Boolean): Boolean {
        if (!startedAtTop || fired) return false
        if (!atTop) { startedAtTop = false; return false }
        if (dy > thresholdPx && dy > abs(dx) * 1.5f) {
            fired = true
            return true
        }
        return false
    }
}

/** The threshold of the pull, the same as the header's drag (it was the only way into quote mode before). */
internal const val FUTABER_QUOTE_DRAG_THRESHOLD_PX = 120f

// ---- G29: a swipe to the right closes the writing ----

/** What a horizontal swipe on the writing screen does: right (from the middle or the left edge) closes it. */
internal fun futaberPostSwipeCloses(swipe: FutaberSwipe, sending: Boolean, selecting: Boolean): Boolean =
    !sending && !selecting && (swipe == FutaberSwipe.Right || swipe == FutaberSwipe.EdgeRight)

// ---- C43: the picture's quality ----

/** 設定→書き込み設定「画像の品質」. 高品質 is how pictures went before (the default); JPG圧縮 draws every picture as JPEG. */
internal enum class FutaberImageQuality(val persisted: String, val label: String) {
    High("HIGH", "高品質"),
    Jpeg("JPEG", "JPG圧縮");

    companion object {
        fun fromPersisted(value: String?): FutaberImageQuality = entries.firstOrNull { it.persisted == value } ?: High
    }
}

/** Whether [fileName] is drawn again as JPEG under 「JPG圧縮」: a picture (not a video, not a drawing, not already JPEG-only rules). */
internal fun futaberRedrawsAsJpeg(quality: FutaberImageQuality, isHandwriting: Boolean, isVideo: Boolean): Boolean =
    quality == FutaberImageQuality.Jpeg && !isHandwriting && !isVideo
