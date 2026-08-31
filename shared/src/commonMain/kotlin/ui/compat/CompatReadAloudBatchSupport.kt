package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.toCompatPlainText

internal const val COMPAT_READ_ALOUD_MAX_BATCH_CHARS = 3_000

internal data class CompatReadAloudBatch(
    val text: String,
    val nextPostIndex: Int,
    val nextCharacterOffset: Int
)

private val compatReadAloudUrlRegex = Regex("(?i)https?://\\S+")

private val compatReadAloudIgnoredPrefixes = listOf(
    "[",
    "IP:",
    "ｷﾀ━━",
    "このスレは古いので",
    "管理人によって削除",
    "書き込みをした人によって削除",
    "隔離",
    "削除された記事が"
)

internal fun resolveCompatReadAloudStartIndex(requestedIndex: Int, postCount: Int): Int =
    requestedIndex.takeIf { postCount > 0 && it in 0 until postCount } ?: 0

/**
 * Builds one post's bounded utterance without dropping the remainder of a
 * large post. ThreadSpeechDialogFragment advances and updates its visible
 * response once per post, so crossing a post boundary in one utterance would
 * make the speech and reference-compatible dialog disagree.
 */
internal fun buildCompatReadAloudBatch(
    posts: List<CompatPostSnapshot>,
    startPostIndex: Int,
    startCharacterOffset: Int,
    maxChars: Int = COMPAT_READ_ALOUD_MAX_BATCH_CHARS
): CompatReadAloudBatch {
    require(maxChars > 0) { "Read-aloud batch size must be positive" }
    var postIndex = startPostIndex.coerceIn(0, posts.size)
    var characterOffset = startCharacterOffset.coerceAtLeast(0)
    val output = StringBuilder(minOf(maxChars, 512))
    while (postIndex < posts.size && output.length < maxChars) {
        val text = compatReadAloudText(posts[postIndex])
        if (characterOffset >= text.length) {
            postIndex += 1
            characterOffset = 0
            continue
        }
        if (output.isNotEmpty()) output.append('\n')
        val available = maxChars - output.length
        if (available <= 0) break
        val end = (characterOffset + available).coerceAtMost(text.length)
        output.append(text, characterOffset, end)
        if (end < text.length) {
            characterOffset = end
            break
        }
        postIndex += 1
        characterOffset = 0
        // Match ThreadSpeechDialogFragment: one response is spoken and shown
        // before the cursor advances to the next response.
        break
    }
    return CompatReadAloudBatch(
        text = output.toString(),
        nextPostIndex = postIndex,
        nextCharacterOffset = characterOffset
    )
}

internal fun compatReadAloudText(post: CompatPostSnapshot): String =
    post.messageHtml.toCompatPlainText()
        .lineSequence()
        .map(String::trim)
        .filter { line ->
            line.isNotEmpty() &&
                !line.startsWith(">") &&
                compatReadAloudIgnoredPrefixes.none(line::startsWith)
        }
        .map { line -> compatReadAloudUrlRegex.replace(line, "").trim() }
        .filter(String::isNotEmpty)
        .joinToString(" ")
        .replace("好き", "スキ")
        .replace("…", "　")
