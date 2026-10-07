package com.valoser.futacha.shared.compat

enum class CompatExtractionKind {
    OWN,
    MANY_SAIDANE,
    MANY_REPLIES,
    DELETED,
    CONTAINS_URL,
    HAS_IMAGE,
    KEYWORD,
    NG
}

enum class CompatNgExtractionAction {
    REQUEST_DEL,
    REQUEST_USER_DELETE
}

fun compatNgExtractionAction(isLongClick: Boolean): CompatNgExtractionAction =
    if (isLongClick) CompatNgExtractionAction.REQUEST_USER_DELETE else CompatNgExtractionAction.REQUEST_DEL

private val COMPAT_EXTRACTION_NUMBER_REGEX = Regex("[0-9]+")
private val COMPAT_EXTRACTION_URL_REGEX = Regex("https?://", RegexOption.IGNORE_CASE)

data class CompatThreadNgRuleIndex(
    val postNos: Set<String>,
    val posterIds: Set<String>,
    val bodyAndHeaderWords: List<String>,
    val bodyWords: List<String>,
    val headerWords: List<String>,
    val imageUrls: Set<String>,
    val imagePhashes: List<String>
)

fun buildCompatThreadNgRuleIndex(
    rules: List<CompatNgRule>,
    scopeKey: String,
    boardKey: String? = null
): CompatThreadNgRuleIndex {
    val scoped = rules.asSequence().filter { rule ->
        when (rule.kind) {
            CompatNgKind.THREAD_IMAGE,
            CompatNgKind.THREAD_IMAGE_PHASH -> boardKey?.let { rule.appliesToThreadImage(it, scopeKey) }
                ?: (rule.scopeKey == scopeKey || rule.scopeKey == "*")
            else -> rule.scopeKey == scopeKey || rule.scopeKey == "*"
        }
    }.toList()
    fun normalizedValues(kind: CompatNgKind): List<String> = scoped.asSequence()
        .filter { it.kind == kind }
        .map { normalizeCompatSearchText(it.normalizedValue) }
        .filter(String::isNotBlank)
        .distinct()
        .toList()
    return CompatThreadNgRuleIndex(
        postNos = scoped.asSequence().filter { it.kind == CompatNgKind.THREAD_POST_NO }
            .map(CompatNgRule::normalizedValue).toSet(),
        posterIds = scoped.asSequence().filter { it.kind == CompatNgKind.THREAD_POSTER_ID }
            .mapNotNull { parseCompatPosterIdentity(it.normalizedValue)?.display }.toSet(),
        bodyAndHeaderWords = normalizedValues(CompatNgKind.THREAD_WORD),
        bodyWords = normalizedValues(CompatNgKind.THREAD_IGNORE),
        headerWords = normalizedValues(CompatNgKind.THREAD_REFUSE),
        imageUrls = scoped.asSequence().filter { it.kind == CompatNgKind.THREAD_IMAGE }
            .map(CompatNgRule::normalizedValue).toSet(),
        imagePhashes = scoped.asSequence().filter { it.kind == CompatNgKind.THREAD_IMAGE_PHASH }
            .map(CompatNgRule::normalizedValue).toList()
    )
}

fun CompatPostSnapshot.matchesCompatThreadNg(
    index: CompatThreadNgRuleIndex,
    imagePhash: String? = null,
    imagePhashThreshold: Int = CompatImagePhash.DEFAULT_THRESHOLD
): Boolean {
    if (matchesCompatThreadNgWithoutText(index, imagePhash, imagePhashThreshold)) return true
    if (!index.hasTextWords()) return false

    val plainText = normalizeCompatSearchText(messageHtml.toCompatPlainText())
    return compatThreadNgTextMatches(index, plainText) { compatThreadNgHeaderText() }
}

/** Post number, poster ID, image URL and image hash rules: everything but the word rules. */
private fun CompatPostSnapshot.matchesCompatThreadNgWithoutText(
    index: CompatThreadNgRuleIndex,
    imagePhash: String?,
    imagePhashThreshold: Int
): Boolean {
    if (postNo in index.postNos) return true
    if (compatPosterIdentity(this)?.display in index.posterIds) return true
    if (imageUrl in index.imageUrls || thumbnailUrl in index.imageUrls) return true
    if (imagePhash != null && index.imagePhashes.any {
            CompatImagePhash.isSimilar(imagePhash, it, imagePhashThreshold)
        }
    ) return true
    return false
}

private fun CompatThreadNgRuleIndex.hasTextWords(): Boolean =
    bodyWords.isNotEmpty() || headerWords.isNotEmpty() || bodyAndHeaderWords.isNotEmpty()

private fun CompatPostSnapshot.compatThreadNgHeaderText(): String =
    listOfNotNull(subject, author, mail, timestamp)
        .joinToString(" ")
        .let(::normalizeCompatSearchText)

/** Word rules against an already normalized body; the header text is only built when the body does not match. */
private inline fun compatThreadNgTextMatches(
    index: CompatThreadNgRuleIndex,
    plainText: String,
    headerText: () -> String
): Boolean {
    if (index.bodyWords.any(plainText::contains)) return true
    val header = headerText()
    if (index.headerWords.any(header::contains)) return true
    return index.bodyAndHeaderWords.any { plainText.contains(it) || header.contains(it) }
}

/**
 * Normalized text of one post and, for the word rules it was last judged against, the verdict.
 * [matched] is null when the verdict has not been computed for the cache's current words.
 */
internal class CompatThreadNgTextEntry(
    val post: CompatPostSnapshot,
    val plainText: String,
    val headerText: String,
    val matched: Boolean?
) {
    /** The only fields the word rules read; a refreshed snapshot with the same text keeps the entry valid. */
    fun isFor(other: CompatPostSnapshot): Boolean = post === other || (
        post.messageHtml == other.messageHtml && post.subject == other.subject &&
            post.author == other.author && post.mail == other.mail && post.timestamp == other.timestamp
        )
}

/**
 * Per-thread memory of the word-rule work, handed from one [filterCompatThreadPostsCached]
 * run to the next. Re-filtering after an append-only update (auto-scroll reloads, read-aloud
 * reloads, a progressive image-hash result) then only normalizes and judges the new posts;
 * a changed word list re-judges every post but still reuses the normalized texts.
 */
class CompatThreadNgFilterCache internal constructor(
    internal val bodyWords: List<String>,
    internal val headerWords: List<String>,
    internal val bodyAndHeaderWords: List<String>,
    internal val entries: Map<String, CompatThreadNgTextEntry>
) {
    internal fun sameWords(index: CompatThreadNgRuleIndex): Boolean =
        bodyWords == index.bodyWords && headerWords == index.headerWords &&
            bodyAndHeaderWords == index.bodyAndHeaderWords
}

/** Holds the latest cache between runs of one thread screen's filter effect. */
class CompatThreadNgFilterCacheHolder {
    var cache: CompatThreadNgFilterCache? = null
}

class CompatThreadNgFilterResult(
    val posts: List<CompatPostSnapshot>,
    val cache: CompatThreadNgFilterCache?
)

/**
 * Same posts as [filterCompatThreadPosts], reusing [previous] for posts whose text did not
 * change. The returned cache replaces [previous] for the next run (it only holds this run's posts).
 */
fun filterCompatThreadPostsCached(
    posts: List<CompatPostSnapshot>,
    ngEnabled: Boolean,
    index: CompatThreadNgRuleIndex,
    aiHiddenPostNos: Set<String> = emptySet(),
    previous: CompatThreadNgFilterCache? = null,
    imagePhashes: Map<String, String> = emptyMap(),
    imagePhashThreshold: Int = CompatImagePhash.DEFAULT_THRESHOLD
): CompatThreadNgFilterResult {
    if (!ngEnabled) return CompatThreadNgFilterResult(posts, previous)
    val hasWords = index.hasTextWords()
    val sameWords = previous?.sameWords(index) == true
    val entries = HashMap<String, CompatThreadNgTextEntry>()
    val visible = posts.filter { post ->
        if (post.postNo in aiHiddenPostNos) return@filter false
        if (post.matchesCompatThreadNgWithoutText(index, imagePhashes[post.postNo], imagePhashThreshold)) {
            return@filter false
        }
        if (!hasWords) return@filter true
        val reusable = previous?.entries?.get(post.postNo)?.takeIf { it.isFor(post) }
        val entry: CompatThreadNgTextEntry = if (reusable != null && sameWords && reusable.matched != null) {
            reusable
        } else {
            val plain = reusable?.plainText ?: normalizeCompatSearchText(post.messageHtml.toCompatPlainText())
            val header = reusable?.headerText ?: post.compatThreadNgHeaderText()
            val matched = compatThreadNgTextMatches(index, plain) { header }
            CompatThreadNgTextEntry(post, plain, header, matched)
        }
        entries[post.postNo] = entry
        entry.matched != true
    }
    return CompatThreadNgFilterResult(
        visible,
        CompatThreadNgFilterCache(index.bodyWords, index.headerWords, index.bodyAndHeaderWords, entries)
    )
}

fun CompatPostSnapshot.matchesCompatThreadNg(
    rules: List<CompatNgRule>,
    scopeKey: String,
    imagePhash: String? = null,
    imagePhashThreshold: Int = CompatImagePhash.DEFAULT_THRESHOLD,
    boardKey: String? = null
): Boolean {
    return matchesCompatThreadNg(
        index = buildCompatThreadNgRuleIndex(rules, scopeKey, boardKey),
        imagePhash = imagePhash,
        imagePhashThreshold = imagePhashThreshold
    )
}

/** Display filtering is temporary; it never adds AI decisions to persisted NG rules. */
fun filterCompatThreadPosts(
    posts: List<CompatPostSnapshot>,
    ngEnabled: Boolean,
    index: CompatThreadNgRuleIndex,
    aiHiddenPostNos: Set<String> = emptySet(),
    imagePhashes: Map<String, String> = emptyMap(),
    imagePhashThreshold: Int = CompatImagePhash.DEFAULT_THRESHOLD
): List<CompatPostSnapshot> = if (!ngEnabled) posts else posts.filter { post ->
    post.postNo !in aiHiddenPostNos && !post.matchesCompatThreadNg(
        index, imagePhash = imagePhashes[post.postNo], imagePhashThreshold = imagePhashThreshold
    )
}

fun extractCompatPosts(
    posts: List<CompatPostSnapshot>,
    kind: CompatExtractionKind,
    scopeKey: String,
    ngRules: List<CompatNgRule> = emptyList(),
    boardKey: String? = null,
    ownPostNos: Set<String> = emptySet(),
    keyword: String = "",
    saidaneThreshold: Int = 3,
    quoteThreshold: Int = 3,
    aiHiddenPostNos: Set<String> = emptySet()
): List<CompatPostSnapshot> {
    val ngIndex = if (kind == CompatExtractionKind.NG) {
        buildCompatThreadNgRuleIndex(ngRules, scopeKey, boardKey)
    } else {
        null
    }
    return posts.filter { post ->
        when (kind) {
            CompatExtractionKind.OWN -> post.postNo in ownPostNos
            CompatExtractionKind.MANY_SAIDANE ->
                COMPAT_EXTRACTION_NUMBER_REGEX.findAll(post.saidaneLabel.orEmpty()).lastOrNull()?.value?.toIntOrNull()?.let {
                    it >= saidaneThreshold
                } == true
            CompatExtractionKind.MANY_REPLIES -> post.referencedCount >= quoteThreshold
            CompatExtractionKind.DELETED -> post.isDeleted
            CompatExtractionKind.CONTAINS_URL ->
                COMPAT_EXTRACTION_URL_REGEX.containsMatchIn(post.messageHtml.toCompatPlainText())
            CompatExtractionKind.HAS_IMAGE -> post.imageUrl != null || post.thumbnailUrl != null
            CompatExtractionKind.KEYWORD -> keyword.isNotEmpty() && (
                post.messageHtml.toCompatPlainText().contains(keyword) || post.mail.orEmpty().contains(keyword)
            )
            CompatExtractionKind.NG -> post.postNo in aiHiddenPostNos || post.matchesCompatThreadNg(checkNotNull(ngIndex))
        }
    }
}
