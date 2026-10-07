package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.media.FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN
import com.valoser.futacha.shared.media.normalizeFutabaArchiveApuViewLabelHtml
import com.valoser.futacha.shared.media.normalizeFutabaArchiveApuViewLabelText
import com.valoser.futacha.shared.parser.HtmlEntityDecoder

private val compatNumericQuote = Regex(
    "^[>＞]+\\s*(?:No\\s*\\.\\s*)?([0-9]{1,20})(?=\\D|$)",
    RegexOption.IGNORE_CASE
)
private val compatIdentityQuote = Regex(
    "^[>＞]+\\s*(ID|IP)\\s*:\\s*([^\\s<>]+)",
    RegexOption.IGNORE_CASE
)
private val compatMediaFileName = Regex(
    "(?<![A-Za-z0-9._/-])([A-Za-z0-9._-]+\\.(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN))(?![A-Za-z0-9._-])",
    RegexOption.IGNORE_CASE
)
private val compatMediaUrlFileName = Regex(
    "https?://[^\\s<>]+/([A-Za-z0-9._-]+\\.(?:$FUTABA_COMPAT_MEDIA_EXTENSION_PATTERN))(?:[?#][^\\s<>]*)?",
    RegexOption.IGNORE_CASE
)

private val compatPlainBreak = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)
private val compatPlainTag = Regex("<[^>]+>")

/**
 * A `no:` query may carry the line's own text after this separator (`no:3\ntext:3時のおやつ`).
 * The text is normalized, so it never contains a newline of its own. It is used only when no
 * earlier post has that number, like the reference APK that treats `>3時のおやつ` as body text.
 */
private const val COMPAT_QUOTE_TEXT_FALLBACK_SEPARATOR = "\ntext:"

/** The post number of a `no:` query (without the text fallback), or null for any other query. */
fun compatQuoteQueryPostNo(query: String): String? {
    if (!query.startsWith("no:", ignoreCase = true)) return null
    return query.substringAfter(':').substringBefore('\n').trim()
}

private fun compatQuoteQueryTextFallback(query: String): String? {
    val index = query.indexOf(COMPAT_QUOTE_TEXT_FALLBACK_SEPARATOR)
    if (index < 0) return null
    return query.substring(index + COMPAT_QUOTE_TEXT_FALLBACK_SEPARATOR.length)
        .normalizeCompatQuoteText()
        .takeIf(String::isNotBlank)
}

/** Converts the small HTML subset retained by the compatibility snapshot into tappable text. */
fun String.toCompatPlainText(): String = HtmlEntityDecoder.decode(
    normalizeFutabaArchiveApuViewLabelHtml(this)
        .replace(compatPlainBreak, "\n")
        .replace(compatPlainTag, "")
).let(::normalizeFutabaArchiveApuViewLabelText)

private val compatQuoteWhitespace = Regex("\\s+")

fun String.normalizeCompatQuoteText(): String = trim().replace(compatQuoteWhitespace, " ")

/** 1.apk intentionally does nothing when a tapped quote has no source post. */
fun compatMissingQuoteNotice(): String? = null

/** Encodes a displayed quote line into the resolver query used by recursive quote popups. */
fun compatQuoteQueryForLine(line: String): String? {
    val trimmed = line.trimStart()
    if (!trimmed.startsWith(">") && !trimmed.startsWith("＞")) return null
    compatIdentityQuote.find(trimmed)?.let { match ->
        val kind = match.groupValues[1].lowercase()
        val value = match.groupValues[2]
            .trimEnd('.', ',', '。', '、', '！', '!')
            .takeIf(String::isNotBlank)
            ?: return@let
        return "$kind:$value"
    }
    val text = trimmed.trimStart('>', '＞').normalizeCompatQuoteText()
    compatMediaFileName.find(text)?.groupValues?.getOrNull(1)?.let { return "file:$it" }
    compatMediaUrlFileName.find(text)?.groupValues?.getOrNull(1)?.let { return "file:$it" }
    // Futaba source names are commonly numeric timestamps such as
    // `1786103362453.jpg`. Check media before the numeric No. shorthand, or
    // the filename would be misread as `No.1786103362453` and fail to resolve.
    compatNumericQuote.find(trimmed)?.let { match ->
        val number = match.groupValues[1]
        // `>3時のおやつ` / `>100円ショップ`: a single `>` with text glued to the number is a body
        // quotation unless post 3 / 100 exists. Keep the text so the resolver can fall back to it.
        // `>>3` / `>No.3` stay plain number references.
        val markerCount = trimmed.takeWhile { it == '>' || it == '＞' }.length
        val explicitNo = trimmed.trimStart('>', '＞').trimStart().startsWith("No", ignoreCase = true)
        val trailing = trimmed.substring(match.groups[1]!!.range.last + 1)
        if (markerCount == 1 && !explicitNo && trailing.isNotBlank() && text.isNotBlank()) {
            return "no:$number$COMPAT_QUOTE_TEXT_FALLBACK_SEPARATOR$text"
        }
        return "no:$number"
    }
    return text.takeIf { it.isNotBlank() }?.let { "text:$it" }
}

/**
 * Returns every media filename which is visible or attached to a post.
 *
 * The old viewer stores a dedicated `strFileName`, but a live Futaba page can
 * expose the same value through `/src/`, `/thumb/`, an external uploader link,
 * or just the filename in the response body.  Keeping these forms together
 * makes file-name quotations resolve identically for fresh and cached posts.
 */
fun compatPostMediaFileNames(
    post: CompatPostSnapshot,
    includeThumbnail: Boolean = true
): List<String> = buildList {
    fun addUrlFileName(url: String?) {
        val fileName = url
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
        if (fileName != null && compatMediaFileName.matches(fileName)) add(fileName)
    }

    addUrlFileName(post.imageUrl)
    if (includeThumbnail) addUrlFileName(post.thumbnailUrl)
    val plainMessage = post.messageHtml.toCompatPlainText()
    compatInlineLinks(post.messageHtml)
        .filter { link ->
            val lineStart = plainMessage.lastIndexOf('\n', (link.start - 1).coerceAtLeast(0)) + 1
            val linePrefix = plainMessage.substring(lineStart, link.start.coerceIn(lineStart, plainMessage.length))
            val trimmedPrefix = linePrefix.trimStart()
            !trimmedPrefix.startsWith(">") && !trimmedPrefix.startsWith("＞")
        }
        .forEach { link -> addUrlFileName(link.url) }
    plainMessage.lineSequence()
        .filterNot { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith(">") || trimmed.startsWith("＞")
        }
        .forEach { line ->
            compatMediaFileName.findAll(line).forEach { match ->
                add(match.groupValues[1])
            }
        }
}.distinctBy(String::lowercase)

/**
 * Resolves only posts before the source post. Text quotations intentionally scan newest-first,
 * matching the legacy app when identical text occurs more than once.
 */
fun resolveCompatQuotePosts(
    posts: List<CompatPostSnapshot>,
    sourcePosition: Int,
    query: String
): List<CompatPostSnapshot> {
    val candidates = posts.asSequence()
        .filter { it.position < sourcePosition }
        .sortedByDescending { it.position }
        .toList()
    when {
        query.startsWith("no:", ignoreCase = true) -> {
            val postNo = compatQuoteQueryPostNo(query).orEmpty()
            candidates.firstOrNull { it.postNo == postNo }?.let { return listOf(it) }
            val fallback = compatQuoteQueryTextFallback(query) ?: return emptyList()
            return candidates.filter { it.matchesCompatQuote(fallback) }
        }
        query.startsWith("id:", ignoreCase = true) -> {
            val id = query.substringAfter(':').trim()
            return candidates.firstOrNull { post ->
                compatPosterIdentities(post).any { identity ->
                    identity.kind == CompatHeaderExtractionKind.ID && identity.value == id
                }
            }?.let(::listOf).orEmpty()
        }
        query.startsWith("ip:", ignoreCase = true) -> {
            val ip = query.substringAfter(':').trim()
            return candidates.firstOrNull { post ->
                compatPosterIdentities(post).any { identity ->
                    identity.kind == CompatHeaderExtractionKind.IP && identity.value == ip
                }
            }?.let(::listOf).orEmpty()
        }
        query.startsWith("file:", ignoreCase = true) -> {
            val fileName = query.substringAfter(':').trim()
            return candidates.firstOrNull { post ->
                compatPostMediaFileNames(post).any { it.equals(fileName, ignoreCase = true) }
            }?.let(::listOf).orEmpty()
        }
    }
    val normalizedQuery = query.removePrefix("text:").normalizeCompatQuoteText()
    if (normalizedQuery.isBlank()) return emptyList()
    return candidates.filter { it.matchesCompatQuote(normalizedQuery) }
}

internal fun CompatPostSnapshot.matchesCompatQuote(query: String): Boolean =
    CompatQuoteTextMatcher(this).matches(query)

/**
 * Text-quotation matcher for one post. The normalized message lines and the header string
 * are computed once, so matching many queries against the same post (thread tree, related
 * posts, reply extraction) no longer re-parses the HTML and re-scans the media file names.
 */
internal class CompatQuoteTextMatcher(private val post: CompatPostSnapshot) {
    private val messageLines: List<String> by lazy(LazyThreadSafetyMode.NONE) {
        post.messageHtml.toCompatPlainText()
            .lineSequence()
            .map(String::normalizeCompatQuoteText)
            .toList()
    }
    private val header: String by lazy(LazyThreadSafetyMode.NONE) {
        // The reference APK also checks the uploaded file-name column when a
        // quoted line is `>fu12345.jpg`/`>f12345.png`. Include every media form,
        // not only the primary source/thumbnail pair.
        val identityHeaders = compatPosterIdentities(post).map(CompatPosterIdentity::display)
        listOfNotNull(post.subject, post.author, post.mail, post.posterId, post.postNo)
            .plus(identityHeaders)
            .plus(compatPostMediaFileNames(post))
            .joinToString(" ")
            .normalizeCompatQuoteText()
    }

    fun matches(query: String): Boolean {
        if (messageLines.any { it == query || it.contains(query) }) return true
        return header.contains(query)
    }
}

/** Case folding used by `String.equals(ignoreCase = true)`: upper-case first, then lower-case. */
private fun compatFileNameKey(name: String): String = buildString(name.length) {
    for (ch in name) append(ch.uppercaseChar().lowercaseChar())
}

/**
 * Lookup tables over one snapshot, so that resolving many quotations (thread tree, related
 * posts) does not filter/sort the whole thread and re-derive every candidate's plain text,
 * identities and file names for each query.
 *
 * [resolve] returns exactly what [resolveCompatQuotePosts] returns for the same arguments.
 * The tables are built lazily on first use of each query kind. Not thread-safe: use one
 * instance from one coroutine at a time.
 */
class CompatQuoteIndex(posts: List<CompatPostSnapshot>) {
    private val newestFirst: List<CompatPostSnapshot> = posts.sortedByDescending { it.position }

    /** Number of posts whose text/header matcher has been created (test hook for scan counts). */
    internal var matcherCount: Int = 0
        private set

    private val matchers = arrayOfNulls<CompatQuoteTextMatcher>(newestFirst.size)

    private val indicesByPostNo: Map<String, List<Int>> by lazy {
        val map = HashMap<String, MutableList<Int>>()
        newestFirst.forEachIndexed { index, post -> map.getOrPut(post.postNo) { ArrayList() }.add(index) }
        map
    }
    private val indicesByIdentity: Map<CompatPosterIdentity, List<Int>> by lazy {
        val map = HashMap<CompatPosterIdentity, MutableList<Int>>()
        newestFirst.forEachIndexed { index, post ->
            compatPosterIdentities(post).forEach { identity -> map.getOrPut(identity) { ArrayList() }.add(index) }
        }
        map
    }
    private val indicesByFileName: Map<String, List<Int>> by lazy {
        val map = HashMap<String, MutableList<Int>>()
        newestFirst.forEachIndexed { index, post ->
            compatPostMediaFileNames(post).forEach { name ->
                val list = map.getOrPut(compatFileNameKey(name)) { ArrayList() }
                if (list.lastOrNull() != index) list.add(index)
            }
        }
        map
    }

    /** First index (newest first) whose position is before [sourcePosition]. */
    private fun firstCandidateIndex(sourcePosition: Int): Int {
        var low = 0
        var high = newestFirst.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (newestFirst[mid].position < sourcePosition) high = mid else low = mid + 1
        }
        return low
    }

    private fun firstAt(indices: List<Int>?, start: Int): CompatPostSnapshot? =
        indices?.firstOrNull { it >= start }?.let(newestFirst::get)

    private fun matcherAt(index: Int): CompatQuoteTextMatcher =
        matchers[index] ?: CompatQuoteTextMatcher(newestFirst[index]).also {
            matchers[index] = it
            matcherCount++
        }

    fun resolve(sourcePosition: Int, query: String): List<CompatPostSnapshot> =
        resolveInternal(sourcePosition, query, firstOnly = false)

    /** Same as `resolve(...).firstOrNull()`, without collecting every text match. */
    fun resolveFirst(sourcePosition: Int, query: String): CompatPostSnapshot? =
        resolveInternal(sourcePosition, query, firstOnly = true).firstOrNull()

    private fun resolveInternal(sourcePosition: Int, query: String, firstOnly: Boolean): List<CompatPostSnapshot> {
        val start = firstCandidateIndex(sourcePosition)
        when {
            query.startsWith("no:", ignoreCase = true) -> {
                val postNo = compatQuoteQueryPostNo(query).orEmpty()
                firstAt(indicesByPostNo[postNo], start)?.let { return listOf(it) }
                val fallback = compatQuoteQueryTextFallback(query) ?: return emptyList()
                return resolveText(start, fallback, firstOnly)
            }
            query.startsWith("id:", ignoreCase = true) -> {
                val id = query.substringAfter(':').trim()
                return firstAt(
                    indicesByIdentity[CompatPosterIdentity(CompatHeaderExtractionKind.ID, id)],
                    start
                )?.let(::listOf).orEmpty()
            }
            query.startsWith("ip:", ignoreCase = true) -> {
                val ip = query.substringAfter(':').trim()
                return firstAt(
                    indicesByIdentity[CompatPosterIdentity(CompatHeaderExtractionKind.IP, ip)],
                    start
                )?.let(::listOf).orEmpty()
            }
            query.startsWith("file:", ignoreCase = true) -> {
                val fileName = query.substringAfter(':').trim()
                return firstAt(indicesByFileName[compatFileNameKey(fileName)], start)?.let(::listOf).orEmpty()
            }
        }
        val normalizedQuery = query.removePrefix("text:").normalizeCompatQuoteText()
        if (normalizedQuery.isBlank()) return emptyList()
        return resolveText(start, normalizedQuery, firstOnly)
    }

    private fun resolveText(start: Int, normalizedQuery: String, firstOnly: Boolean): List<CompatPostSnapshot> {
        val matches = ArrayList<CompatPostSnapshot>()
        for (index in start until newestFirst.size) {
            if (matcherAt(index).matches(normalizedQuery)) {
                matches += newestFirst[index]
                if (firstOnly) break
            }
        }
        return matches
    }
}
