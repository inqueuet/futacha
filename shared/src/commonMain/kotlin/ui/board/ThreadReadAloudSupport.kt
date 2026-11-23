package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.COMPAT_URL_BODY_CHAR_CLASS
import com.valoser.futacha.shared.model.Post
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

private const val READ_ALOUD_CANCELLATION_CHECK_INTERVAL = 32

internal data class ReadAloudSegment(
    val postIndex: Int,
    val postId: String,
    val body: String
)

internal sealed interface ReadAloudStatus {
    data object Idle : ReadAloudStatus
    data class Speaking(val segment: ReadAloudSegment) : ReadAloudStatus
    data class Paused(val segment: ReadAloudSegment) : ReadAloudStatus
}

private val READ_ALOUD_SKIPPED_PHRASES = listOf(
    "スレッドを立てた人によって削除されました",
    "書き込みをした人によって削除されました",
    "管理者によって削除されました"
)
// ASCII URL characters only, so the Japanese text after "https://x/y.jpg　これ" is still read.
private val READ_ALOUD_URL_REGEX = Regex(
    "(?i)\\b(?:https?|ftp)://$COMPAT_URL_BODY_CHAR_CLASS+|\\bttps?://$COMPAT_URL_BODY_CHAR_CLASS+|\\bttp://$COMPAT_URL_BODY_CHAR_CLASS+"
)
private val READ_ALOUD_WHITESPACE_REGEX = Regex("\\s{2,}")

internal suspend fun buildReadAloudSegments(
    posts: List<Post>,
    textCache: ThreadPostTextCache? = null,
    /** Posts collapsed by the AI filter; they are skipped unless the user revealed them. */
    skippedPostIds: Set<String> = emptySet(),
    /**
     * may 1449267646: read the quoted lines too, each marked so the speaker can read it in a voice of its own
     * ([speakWithQuoteVoice]). False skips them as before.
     */
    includeQuotes: Boolean = false
): List<ReadAloudSegment> {
    val segments = ArrayList<ReadAloudSegment>()
    posts.forEachIndexed { index, post ->
        if (index % READ_ALOUD_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        if (post.isDeleted || post.id in skippedPostIds) return@forEachIndexed
        val lines = (if (post.messageHtml.contains(com.valoser.futacha.shared.network.aimoge.AIMOGE_SPOILER_CLASS)) messageHtmlToLines(aimogeHtmlForSpeech(post.messageHtml)) else textCache?.get(post)?.lines ?: messageHtmlToLines(post.messageHtml))
            .map { stripUrlsForReadAloud(it).trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val quoted = line.startsWith(">") || line.startsWith("＞")
                when {
                    !quoted -> line
                    includeQuotes -> com.valoser.futacha.shared.ui.futaber.futaberQuoteLineToSpeak(line)
                        .takeIf { it.isNotEmpty() }?.let { com.valoser.futacha.shared.ui.futaber.FUTABER_QUOTE_MARK + it }
                    else -> null
                }
            }
        if (lines.isEmpty()) return@forEachIndexed
        if (containsDeletionNotice(lines)) return@forEachIndexed
        val body = lines.joinToString("\n")
        segments += ReadAloudSegment(index, post.id, body)
    }
    return segments
}

internal fun stripUrlsForReadAloud(value: String): String {
    val withoutUrls = READ_ALOUD_URL_REGEX.replace(value, "")
    return withoutUrls.replace(READ_ALOUD_WHITESPACE_REGEX, " ")
}

internal fun containsDeletionNotice(lines: List<String>): Boolean {
    if (lines.isEmpty()) return false
    return lines.any { line ->
        READ_ALOUD_SKIPPED_PHRASES.any { phrase ->
            line.contains(phrase)
        }
    }
}

internal fun normalizeReadAloudCurrentIndex(
    currentIndex: Int,
    segmentCount: Int
): Int {
    return currentIndex.coerceIn(0, segmentCount.coerceAtLeast(0))
}

internal fun findFirstVisibleReadAloudSegmentIndex(
    segments: List<ReadAloudSegment>,
    firstVisibleItemIndex: Int
): Int {
    if (segments.isEmpty()) return -1
    var left = 0
    var right = segments.lastIndex
    var result = -1
    while (left <= right) {
        val mid = left + (right - left) / 2
        val postIndex = segments[mid].postIndex
        if (postIndex >= firstVisibleItemIndex) {
            result = mid
            right = mid - 1
        } else {
            left = mid + 1
        }
    }
    return result
}

/** ふたちゃ: read the quoted lines too (may 1449267646). Off by default: quotes are skipped as before. */
internal const val FUTACHA_SPEECH_READ_QUOTES_KEY = "compat.futacha.speech.readQuotes"
/** ふたちゃ: the quoted lines in a lower voice (on unless "OFF"); only while [FUTACHA_SPEECH_READ_QUOTES_KEY] is on. */
internal const val FUTACHA_SPEECH_QUOTE_LOW_VOICE_KEY = "compat.futacha.speech.quoteLowVoice"

internal fun futachaReadsQuotes(preferences: Map<String, String>): Boolean = preferences[FUTACHA_SPEECH_READ_QUOTES_KEY] == "ON"
internal fun futachaQuoteLowVoice(preferences: Map<String, String>): Boolean = preferences[FUTACHA_SPEECH_QUOTE_LOW_VOICE_KEY] != "OFF"

/** The lower voice of the quoted lines: the OS voice's pitch 0.5 (as ふたばー風), ずんだもん's lowest pitch. */
internal fun quoteVoiceSettings(base: com.valoser.futacha.shared.audio.SpeechSettings): com.valoser.futacha.shared.audio.SpeechSettings =
    base.copy(pitch = 0.5f, voicevoxPitch = -0.15f)

/**
 * Speaks [text] (a segment's body): the quoted lines apart, in [quoteSettings] when it is not null; the rest in [base].
 * A body without quote marks is one call, as before.
 */
internal suspend fun speakWithQuoteVoice(
    speaker: com.valoser.futacha.shared.audio.TextSpeaker,
    text: String,
    base: com.valoser.futacha.shared.audio.SpeechSettings,
    quoteSettings: com.valoser.futacha.shared.audio.SpeechSettings?
) {
    if (!text.contains(com.valoser.futacha.shared.ui.futaber.FUTABER_QUOTE_MARK)) { speaker.speak(text); return }
    for (run in com.valoser.futacha.shared.ui.futaber.futaberSpeechRuns(text)) {
        val settings = if (run.quoted && quoteSettings != null) quoteSettings else base
        speaker.configure(settings)
        speaker.speak(run.text)
    }
    speaker.configure(base)
}

/** What ずんだもん makes ahead for [text]: its first run, without the marks. */
internal fun readAloudHintText(text: String?): String? =
    text?.let { if (it.contains(com.valoser.futacha.shared.ui.futaber.FUTABER_QUOTE_MARK)) com.valoser.futacha.shared.ui.futaber.futaberSpeechRuns(it).firstOrNull()?.text else it }
