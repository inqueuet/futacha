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
    if (postNo in index.postNos) return true
    if (compatPosterIdentity(this)?.display in index.posterIds) return true
    if (imageUrl in index.imageUrls || thumbnailUrl in index.imageUrls) return true
    if (imagePhash != null && index.imagePhashes.any {
            CompatImagePhash.isSimilar(imagePhash, it, imagePhashThreshold)
        }
    ) return true
    if (
        index.bodyWords.isEmpty() &&
        index.headerWords.isEmpty() &&
        index.bodyAndHeaderWords.isEmpty()
    ) return false

    val plainText = normalizeCompatSearchText(messageHtml.toCompatPlainText())
    if (index.bodyWords.any(plainText::contains)) return true
    val headerText = listOfNotNull(subject, author, mail, timestamp)
        .joinToString(" ")
        .let(::normalizeCompatSearchText)
    if (index.headerWords.any(headerText::contains)) return true
    return index.bodyAndHeaderWords.any { plainText.contains(it) || headerText.contains(it) }
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

fun extractCompatPosts(
    posts: List<CompatPostSnapshot>,
    kind: CompatExtractionKind,
    scopeKey: String,
    ngRules: List<CompatNgRule> = emptyList(),
    boardKey: String? = null,
    ownPostNos: Set<String> = emptySet(),
    keyword: String = "",
    saidaneThreshold: Int = 3,
    quoteThreshold: Int = 3
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
            CompatExtractionKind.NG -> post.matchesCompatThreadNg(checkNotNull(ngIndex))
        }
    }
}
