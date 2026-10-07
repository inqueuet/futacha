package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.util.canonicalCp932Character
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

private const val THREAD_SEARCH_MAX_HIGHLIGHT_RANGES_PER_POST = 64
private const val THREAD_SEARCH_CANCELLATION_CHECK_INTERVAL = 32

internal fun nextThreadSearchResultIndex(currentIndex: Int, matchCount: Int): Int {
    if (matchCount <= 0) return 0
    return if (currentIndex + 1 >= matchCount) 0 else currentIndex + 1
}

internal fun previousThreadSearchResultIndex(currentIndex: Int, matchCount: Int): Int {
    if (matchCount <= 0) return 0
    return if (currentIndex - 1 < 0) matchCount - 1 else currentIndex - 1
}

internal fun normalizeThreadSearchResultIndex(currentIndex: Int, matchCount: Int): Int {
    if (matchCount <= 0) return 0
    return if (currentIndex in 0 until matchCount) currentIndex else 0
}

internal data class ThreadSearchNavigationState(
    val nextIndex: Int,
    val targetPostIndex: Int?,
    val shouldScroll: Boolean
)

internal fun focusThreadSearchMatch(
    currentIndex: Int,
    matches: List<ThreadSearchMatch>
): ThreadSearchNavigationState {
    val target = matches.getOrNull(currentIndex)
    return ThreadSearchNavigationState(
        nextIndex = normalizeThreadSearchResultIndex(currentIndex, matches.size),
        targetPostIndex = target?.postIndex,
        shouldScroll = target != null
    )
}

internal fun moveToNextThreadSearchMatch(
    currentIndex: Int,
    matches: List<ThreadSearchMatch>
): ThreadSearchNavigationState {
    if (matches.isEmpty()) {
        return ThreadSearchNavigationState(
            nextIndex = 0,
            targetPostIndex = null,
            shouldScroll = false
        )
    }
    val nextIndex = nextThreadSearchResultIndex(currentIndex, matches.size)
    return ThreadSearchNavigationState(
        nextIndex = nextIndex,
        targetPostIndex = matches.getOrNull(nextIndex)?.postIndex,
        shouldScroll = true
    )
}

internal fun moveToPreviousThreadSearchMatch(
    currentIndex: Int,
    matches: List<ThreadSearchMatch>
): ThreadSearchNavigationState {
    if (matches.isEmpty()) {
        return ThreadSearchNavigationState(
            nextIndex = 0,
            targetPostIndex = null,
            shouldScroll = false
        )
    }
    val previousIndex = previousThreadSearchResultIndex(currentIndex, matches.size)
    return ThreadSearchNavigationState(
        nextIndex = previousIndex,
        targetPostIndex = matches.getOrNull(previousIndex)?.postIndex,
        shouldScroll = true
    )
}

internal data class ThreadSearchMatch(
    val postId: String,
    val postIndex: Int,
    val post: Post,
    val highlightRanges: List<IntRange>
)

internal data class ThreadSearchTarget(
    val postId: String,
    val postIndex: Int,
    val post: Post,
    val searchableText: String,
    val messagePlainText: String,
    /** [searchableText] canonicalized once here, not on every query (the same instance when nothing folds). */
    val canonicalSearchableText: String = canonicalThreadSearchText(searchableText)
)

internal suspend fun buildThreadSearchTargets(
    posts: List<Post>,
    textCache: ThreadPostTextCache? = null
): List<ThreadSearchTarget> {
    if (posts.isEmpty()) return emptyList()
    val targets = ArrayList<ThreadSearchTarget>(posts.size)
    posts.forEachIndexed { index, post ->
        if (index % THREAD_SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        val messagePlainText = textCache?.get(post)?.plainText
            ?: messageHtmlToPlainText(post.messageHtml)
        targets += ThreadSearchTarget(
            postId = post.id,
            postIndex = index,
            post = post,
            searchableText = buildSearchTextForPost(post, messagePlainText),
            messagePlainText = messagePlainText
        )
    }
    return targets
}

/**
 * Folds full-width ASCII (！..～) and the ideographic space to their half-width forms, one character
 * for one character, so highlight ranges computed on the folded text stay valid for the original.
 * Search and NG matching then ignore the full-width/half-width difference (「ＡＢＣ」 = 「ABC」).
 */
internal fun foldFullWidthAscii(text: String): String {
    if (text.none { it == '\u3000' || it in '\uFF01'..'\uFF5E' }) return text
    return buildString(text.length) {
        text.forEach { char ->
            append(
                when {
                    char == '\u3000' -> ' '
                    char in '\uFF01'..'\uFF5E' -> (char.code - 0xFEE0).toChar()
                    else -> char
                }
            )
        }
    }
}

/**
 * The text that search and NG matching compare, one character for one character (the length never changes,
 * so highlight ranges computed on the result stay valid for the original text). In one pass it
 *  - unifies the CP932 aliases,
 *  - folds full-width ASCII (！..～) and the ideographic space to half-width,
 *  - folds katakana (ァ..ヶ, ヽ, ヾ) to hiragana (ヷヸヹヺ, the long-vowel mark ー and ・ are left as they are),
 *  - folds half-width katakana (ｦ..ﾝ) to hiragana; one that is followed by a half-width (semi-)voiced mark
 *    becomes the voiced (semi-voiced) hiragana and the mark itself stays (ｶﾞ -> が + ﾞ, so 「が」 finds it).
 * Kana types and full-width/half-width forms are therefore not told apart (「ネコ」 = 「ねこ」 = 「ﾈｺ」),
 * but sounds are (「か」≠「が」, 「う」≠「ヴ」). The same instance is returned when nothing needs to change.
 */
internal fun canonicalThreadSearchText(text: String): String {
    var first = -1
    for (index in text.indices) {
        if (foldThreadSearchChar(text[index]) != text[index]) {
            first = index
            break
        }
    }
    if (first < 0) return text
    val chars = text.toCharArray()
    for (index in first until chars.size) {
        val original = text[index]
        var folded = foldThreadSearchChar(original)
        if (original.code in HALF_WIDTH_KANA_FIRST..HALF_WIDTH_KANA_LAST && index + 1 < text.length) {
            when (text[index + 1]) {
                HALF_WIDTH_VOICED_MARK -> folded = voiceKanaBase(folded, semiVoiced = false)
                HALF_WIDTH_SEMI_VOICED_MARK -> folded = voiceKanaBase(folded, semiVoiced = true)
            }
        }
        chars[index] = folded
    }
    return chars.concatToString()
}

private const val HALF_WIDTH_KANA_FIRST = 0xFF66
private const val HALF_WIDTH_KANA_LAST = 0xFF9D
private const val HALF_WIDTH_VOICED_MARK = '\uFF9E'
private const val HALF_WIDTH_SEMI_VOICED_MARK = '\uFF9F'

/** Hiragana for U+FF66 (ｦ) .. U+FF9D (ﾝ), one to one. */
private const val HALF_WIDTH_KANA_TO_HIRAGANA =
    "をぁぃぅぇぉゃゅょっーあいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわん"

private fun foldThreadSearchChar(char: Char): Char {
    val code = char.code
    // Hiragana and the CJK / Hangul blocks (nothing to fold) leave at once.
    if (code in 0x3041..0x309F || code in 0x3100..0xFF00) return char
    if (code < 0x2014) return char
    val canonical = canonicalCp932Character(char)
    val canonicalCode = canonical.code
    return when {
        canonical == '\u3000' -> ' '
        canonicalCode in 0xFF01..0xFF5E -> (canonicalCode - 0xFEE0).toChar()
        canonicalCode in 0x30A1..0x30F6 || canonicalCode == 0x30FD || canonicalCode == 0x30FE ->
            (canonicalCode - 0x60).toChar()
        canonicalCode in HALF_WIDTH_KANA_FIRST..HALF_WIDTH_KANA_LAST ->
            HALF_WIDTH_KANA_TO_HIRAGANA[canonicalCode - HALF_WIDTH_KANA_FIRST]
        else -> canonical
    }
}

private fun voiceKanaBase(base: Char, semiVoiced: Boolean): Char = when {
    semiVoiced -> if (base in "はひふへほ") base + 2 else base
    base in "かきくけこさしすせそたちつてとはひふへほ" -> base + 1
    base == 'う' -> 'ゔ'
    else -> base
}

internal suspend fun buildThreadSearchMatches(
    searchTargets: List<ThreadSearchTarget>,
    query: String
): List<ThreadSearchMatch> {
    if (searchTargets.isEmpty()) return emptyList()
    val normalizedQuery = canonicalThreadSearchText(query.trim()).lowercase()
    if (normalizedQuery.isEmpty()) return emptyList()
    val matches = ArrayList<ThreadSearchMatch>()
    searchTargets.forEachIndexed { index, target ->
        if (index % THREAD_SEARCH_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        if (target.canonicalSearchableText.contains(normalizedQuery)) {
            val ranges = computeHighlightRanges(target.messagePlainText, normalizedQuery)
            matches += ThreadSearchMatch(target.postId, target.postIndex, target.post, ranges)
        }
    }
    return matches
}

internal fun buildSearchTextForPost(post: Post, messagePlainText: String): String {
    val builder = StringBuilder()
    post.subject?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    post.author?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    post.posterId?.takeIf { it.isNotBlank() }?.let {
        builder.appendLine(it)
    }
    builder.appendLine(post.id)
    builder.append(messagePlainText)
    return builder.toString().lowercase()
}

internal fun computeHighlightRanges(text: String, normalizedQuery: String): List<IntRange> {
    if (normalizedQuery.isEmpty()) return emptyList()
    val normalizedText = canonicalThreadSearchText(text).lowercase()
    val ranges = mutableListOf<IntRange>()
    var startIndex = normalizedText.indexOf(normalizedQuery)
    while (startIndex >= 0 && ranges.size < THREAD_SEARCH_MAX_HIGHLIGHT_RANGES_PER_POST) {
        val endIndex = startIndex + normalizedQuery.length - 1
        ranges.add(startIndex..endIndex)
        startIndex = normalizedText.indexOf(normalizedQuery, startIndex + normalizedQuery.length)
    }
    return ranges
}
