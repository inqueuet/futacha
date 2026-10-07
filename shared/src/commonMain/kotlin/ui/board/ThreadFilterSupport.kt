package com.valoser.futacha.shared.ui.board

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReplyAll
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

internal fun applyNgFilters(
    page: ThreadPage,
    ngHeaders: List<String>,
    ngWords: List<String>,
    enabled: Boolean,
    precomputedLowerBodyByPost: Map<Post, String>? = null
): ThreadPage {
    if (!enabled) return page
    val rules = CanonicalNgRules.of(ngHeaders, ngWords)
    if (rules.isEmpty) return page
    val lowerBodyByPost = if (rules.hasWords) {
        precomputedLowerBodyByPost ?: buildLowerBodyByPost(page.posts)
    } else {
        emptyMap()
    }
    val filteredPosts = page.posts.filterNot { post ->
        rules.matches(post) { lowerBodyByPost[post] ?: "" }
    }
    return page.copy(posts = filteredPosts)
}

internal fun applyThreadFilters(
    page: ThreadPage,
    criteria: ThreadFilterCriteria,
    precomputedLowerBodyByPost: Map<Post, String>? = null
): ThreadPage {
    if (criteria.options.isEmpty()) return page
    val normalizedSelfPostIdentifiers = criteria.selfPostIdentifiers
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    val needsLowerBodyByPost = criteria.options.any {
        it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
    }
    val lowerBodyByPost = if (needsLowerBodyByPost) {
        precomputedLowerBodyByPost ?: buildLowerBodyByPost(page.posts)
    } else {
        emptyMap()
    }
    val filteredPosts = page.posts.filter { post ->
        matchesThreadFilters(
            post = post,
            criteria = criteria,
            lowerBodyByPost = lowerBodyByPost,
            normalizedSelfPostIdentifiers = normalizedSelfPostIdentifiers
        )
    }
    val sortedPosts = sortThreadPosts(filteredPosts, criteria.sortOption)
    return page.copy(posts = sortedPosts)
}

internal data class ThreadFilterResult(
    val postIndices: List<Int>
) {
    fun toThreadPage(page: ThreadPage): ThreadPage {
        val filteredPosts = postIndices.mapNotNull { index -> page.posts.getOrNull(index) }
        return page.copy(posts = filteredPosts)
    }
}

/** Which posts one filter computation kept, and for which cache key. */
internal data class ThreadFilterOutcome(
    val key: ThreadFilterCacheKey,
    val result: ThreadFilterResult,
    val sourcePostCount: Int
)

/** Last page shown while a new filter computation is still running. */
internal class ThreadFilteredPageHolder {
    var page: ThreadPage? = null
}

/**
 * Builds the displayed page from the latest loaded page.
 *
 * Without filters the loaded page is shown as is. A finished computation for
 * the current key is applied to the latest page, so page and post fields
 * (bodies, deletions, saidane, expiry) always come from the newest load. While
 * a computation for a new key is running the previously shown page stays, as
 * before, instead of flashing posts that the filters would hide.
 */
internal fun resolveNormallyFilteredThreadPage(
    page: ThreadPage,
    outcome: ThreadFilterOutcome?,
    currentKey: ThreadFilterCacheKey,
    filtersActive: Boolean,
    previous: ThreadPage?
): ThreadPage = when {
    !filtersActive -> page
    outcome != null && outcome.key == currentKey && outcome.sourcePostCount == page.posts.size ->
        outcome.result.toThreadPage(page)
    else -> previous ?: page
}

internal fun applyThreadFilterResult(
    page: ThreadPage,
    criteria: ThreadFilterCriteria,
    ngHeaders: List<String>,
    ngWords: List<String>,
    ngEnabled: Boolean,
    precomputedLowerBodyByPost: Map<Post, String>? = null
): ThreadFilterResult {
    val indexedPosts = page.posts.mapIndexed { index, post -> IndexedValue(index, post) }
    val ngFilteredPosts = applyNgFiltersToIndexedPosts(
        posts = indexedPosts,
        ngHeaders = ngHeaders,
        ngWords = ngWords,
        enabled = ngEnabled,
        precomputedLowerBodyByPost = precomputedLowerBodyByPost
    )
    val threadFilteredPosts = applyThreadFiltersToIndexedPosts(
        posts = ngFilteredPosts,
        criteria = criteria,
        precomputedLowerBodyByPost = precomputedLowerBodyByPost
    )
    return ThreadFilterResult(threadFilteredPosts.map { it.index })
}

private fun applyNgFiltersToIndexedPosts(
    posts: List<IndexedValue<Post>>,
    ngHeaders: List<String>,
    ngWords: List<String>,
    enabled: Boolean,
    precomputedLowerBodyByPost: Map<Post, String>?
): List<IndexedValue<Post>> {
    if (!enabled) return posts
    val rules = CanonicalNgRules.of(ngHeaders, ngWords)
    if (rules.isEmpty) return posts
    val lowerBodyByPost = if (rules.hasWords) {
        precomputedLowerBodyByPost ?: buildLowerBodyByPost(posts.map { it.value })
    } else {
        emptyMap()
    }
    return posts.filterNot { indexedPost ->
        rules.matches(indexedPost.value) { lowerBodyByPost[indexedPost.value] ?: "" }
    }
}

private fun applyThreadFiltersToIndexedPosts(
    posts: List<IndexedValue<Post>>,
    criteria: ThreadFilterCriteria,
    precomputedLowerBodyByPost: Map<Post, String>?
): List<IndexedValue<Post>> {
    if (criteria.options.isEmpty()) return posts
    val normalizedSelfPostIdentifiers = criteria.selfPostIdentifiers
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    val needsLowerBodyByPost = criteria.options.any {
        it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
    }
    val lowerBodyByPost = if (needsLowerBodyByPost) {
        precomputedLowerBodyByPost ?: buildLowerBodyByPost(posts.map { it.value })
    } else {
        emptyMap()
    }
    val filteredPosts = posts.filter { indexedPost ->
        matchesThreadFilters(
            post = indexedPost.value,
            criteria = criteria,
            lowerBodyByPost = lowerBodyByPost,
            normalizedSelfPostIdentifiers = normalizedSelfPostIdentifiers
        )
    }
    return sortIndexedThreadPosts(filteredPosts, criteria.sortOption)
}

internal fun matchesThreadFilters(
    post: Post,
    criteria: ThreadFilterCriteria,
    lowerBodyByPost: Map<Post, String>,
    normalizedSelfPostIdentifiers: Set<String>
): Boolean {
    val filterOptions = criteria.options.filter { it.sortOption == null }
    if (filterOptions.isEmpty()) return true
    val lowerText = lowerBodyByPost[post] ?: ""
    return filterOptions.any { option ->
        when (option) {
            ThreadFilterOption.SelfPosts ->
                matchesSelfFilter(post, normalizedSelfPostIdentifiers)
            ThreadFilterOption.Deleted -> post.isDeleted
            ThreadFilterOption.Url -> THREAD_FILTER_URL_REGEX.containsMatchIn(lowerText)
            ThreadFilterOption.Image -> post.imageUrl?.isNotBlank() == true
            ThreadFilterOption.Keyword -> matchesKeyword(lowerText, post.subject ?: "", criteria.keyword)
            else -> true
        }
    }
}

internal fun matchesSelfFilter(
    post: Post,
    normalizedStoredIdentifiers: Set<String>
): Boolean {
    if (normalizedStoredIdentifiers.isEmpty()) return false
    return post.id in normalizedStoredIdentifiers
}

internal fun parseSaidaneCount(label: String?): Int? {
    val source = label ?: return null
    return THREAD_FILTER_SAIDANE_COUNT_REGEX.find(source)?.value?.toIntOrNull()
}

internal fun sortThreadPosts(
    posts: List<Post>,
    sortOption: ThreadFilterSortOption?
): List<Post> {
    return when (sortOption) {
        ThreadFilterSortOption.Saidane -> posts.sortedByDescending { parseSaidaneCount(it.saidaneLabel) ?: 0 }
        ThreadFilterSortOption.Replies -> posts.sortedByDescending { it.referencedCount }
        null -> posts
    }
}

private fun sortIndexedThreadPosts(
    posts: List<IndexedValue<Post>>,
    sortOption: ThreadFilterSortOption?
): List<IndexedValue<Post>> {
    return when (sortOption) {
        ThreadFilterSortOption.Saidane -> posts.sortedByDescending { parseSaidaneCount(it.value.saidaneLabel) ?: 0 }
        ThreadFilterSortOption.Replies -> posts.sortedByDescending { it.value.referencedCount }
        null -> posts
    }
}

internal fun matchesKeyword(lowerText: String, subject: String, keywordInput: String): Boolean {
    val keywords = keywordInput
        .split(',')
        .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    if (keywords.isEmpty()) return false
    val lowerSubject = subject.lowercase()
    return keywords.any { keyword ->
        lowerText.contains(keyword) || lowerSubject.contains(keyword)
    }
}

internal val THREAD_FILTER_URL_REGEX =
    Regex("""https?://[^\s"'<>]+|www\.[^\s"'<>]+""", RegexOption.IGNORE_CASE)
private val THREAD_FILTER_SAIDANE_COUNT_REGEX = Regex("""\d+""")

private fun canonicalNgFilters(values: List<String>): List<String> =
    values.mapNotNull { value ->
        value.trim().takeIf { it.isNotBlank() }?.lowercase()?.let(::canonicalThreadSearchText)
    }

/**
 * NG rules, each canonicalized once ([canonicalThreadSearchText]) when built, so matching a post
 * never re-normalizes a rule. The post text is canonicalized once per post, not once per rule.
 */
internal class CanonicalNgRules private constructor(
    private val headers: List<String>,
    private val words: List<String>
) {
    val isEmpty: Boolean get() = headers.isEmpty() && words.isEmpty()
    val hasWords: Boolean get() = words.isNotEmpty()

    fun matchesHeader(post: Post): Boolean {
        if (headers.isEmpty()) return false
        val headerText = canonicalThreadSearchText(buildPostHeaderText(post))
        return headers.any { headerText.contains(it) }
    }

    /** [lowerBody] is the lowercased plain body of the post. */
    fun matchesBody(lowerBody: String): Boolean {
        if (words.isEmpty()) return false
        val bodyText = canonicalThreadSearchText(lowerBody)
        return words.any { bodyText.contains(it) }
    }

    /** The body is only produced when the header rules did not already match and there are word rules. */
    inline fun matches(post: Post, lowerBody: () -> String): Boolean =
        matchesHeader(post) || (hasWords && matchesBody(lowerBody()))

    companion object {
        /** Trims, lowercases and canonicalizes; blank rules are dropped. */
        fun of(ngHeaders: List<String>, ngWords: List<String>): CanonicalNgRules =
            CanonicalNgRules(canonicalNgFilters(ngHeaders), canonicalNgFilters(ngWords))

        /** Rules used as given (only canonicalized), for callers that already normalized them. */
        fun ofRaw(headerFilters: List<String>, wordFilters: List<String>): CanonicalNgRules =
            CanonicalNgRules(
                headerFilters.map(::canonicalThreadSearchText),
                wordFilters.map(::canonicalThreadSearchText)
            )
    }
}

internal fun matchesNgFilters(
    post: Post,
    headerFilters: List<String>,
    wordFilters: List<String>,
    lowerBodyByPost: Map<Post, String>
): Boolean = CanonicalNgRules.ofRaw(headerFilters, wordFilters)
    .matches(post) { lowerBodyByPost[post] ?: "" }

/** Checks for cancellation and lets other coroutines run once per this many posts. */
private const val THREAD_FILTER_CANCELLATION_CHECK_INTERVAL = 64

/**
 * Same result as filtering with [CanonicalNgRules.matches], but cancellable: the rules are
 * canonicalized once and the posts are walked with a cancellation check every
 * [THREAD_FILTER_CANCELLATION_CHECK_INTERVAL] posts, so a superseded run stops early.
 * [lowerBodyOf] may be backed by [ThreadPostTextCache].
 */
internal suspend fun <T> filterOutNgPostsCancellable(
    items: List<T>,
    rules: CanonicalNgRules,
    postOf: (T) -> Post,
    lowerBodyOf: suspend (Post) -> String
): List<T> {
    if (rules.isEmpty) return items
    val kept = ArrayList<T>(items.size)
    items.forEachIndexed { index, item ->
        if (index % THREAD_FILTER_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        val post = postOf(item)
        val hidden = rules.matchesHeader(post) || (rules.hasWords && rules.matchesBody(lowerBodyOf(post)))
        if (!hidden) kept += item
    }
    return kept
}

/**
 * Ids of the posts that the modern (header/word) NG rules hide, for the shared-rule projection: a post
 * is hidden when its id is not the id of any post that [applyNgFilters] keeps (as `applyNgFilters`
 * followed by an id comparison did). Rules are canonicalized once; bodies come from [textCache].
 */
internal suspend fun findNgHiddenPostIds(
    posts: List<Post>,
    ngHeaders: List<String>,
    ngWords: List<String>,
    textCache: ThreadPostTextCache?
): Set<String> {
    val rules = CanonicalNgRules.of(ngHeaders, ngWords)
    if (rules.isEmpty || posts.isEmpty()) return emptySet()
    val hiddenIds = HashSet<String>()
    val visibleIds = HashSet<String>()
    posts.forEachIndexed { index, post ->
        if (index % THREAD_FILTER_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        val matched = rules.matchesHeader(post) ||
            (rules.hasWords && rules.matchesBody(ngLowerBodyOf(post, textCache)))
        if (matched) hiddenIds += post.id else visibleIds += post.id
    }
    hiddenIds.removeAll(visibleIds)
    return hiddenIds
}

private suspend fun ngLowerBodyOf(post: Post, textCache: ThreadPostTextCache?): String =
    textCache?.get(post)?.lowerText ?: messageHtmlToPlainText(post.messageHtml).lowercase()

/**
 * [applyThreadFilterResult] for background use: identical result, but the rules are canonicalized
 * once, bodies come from [textCache] when [precomputedLowerBodyByPost] is not given, and the walk
 * over the posts checks for cancellation.
 */
internal suspend fun applyThreadFilterResultCancellable(
    page: ThreadPage,
    criteria: ThreadFilterCriteria,
    ngHeaders: List<String>,
    ngWords: List<String>,
    ngEnabled: Boolean,
    precomputedLowerBodyByPost: Map<Post, String>? = null,
    textCache: ThreadPostTextCache? = null
): ThreadFilterResult {
    val indexedPosts = page.posts.mapIndexed { index, post -> IndexedValue(index, post) }
    val ngFiltered = if (!ngEnabled) {
        indexedPosts
    } else {
        filterOutNgPostsCancellable(
            items = indexedPosts,
            rules = CanonicalNgRules.of(ngHeaders, ngWords),
            postOf = { it.value },
            lowerBodyOf = { post ->
                if (precomputedLowerBodyByPost != null) precomputedLowerBodyByPost[post] ?: ""
                else ngLowerBodyOf(post, textCache)
            }
        )
    }
    if (criteria.options.isEmpty()) return ThreadFilterResult(ngFiltered.map { it.index })
    val normalizedSelfPostIdentifiers = criteria.selfPostIdentifiers
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    val needsLowerBodyByPost = criteria.options.any {
        it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
    }
    val lowerBodyByPost = if (needsLowerBodyByPost) {
        precomputedLowerBodyByPost ?: buildLowerBodyByPost(ngFiltered.map { it.value }, textCache)
    } else {
        emptyMap()
    }
    val threadFiltered = ArrayList<IndexedValue<Post>>(ngFiltered.size)
    ngFiltered.forEachIndexed { index, indexedPost ->
        if (index % THREAD_FILTER_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        if (
            matchesThreadFilters(
                post = indexedPost.value,
                criteria = criteria,
                lowerBodyByPost = lowerBodyByPost,
                normalizedSelfPostIdentifiers = normalizedSelfPostIdentifiers
            )
        ) {
            threadFiltered += indexedPost
        }
    }
    return ThreadFilterResult(sortIndexedThreadPosts(threadFiltered, criteria.sortOption).map { it.index })
}

internal fun buildLowerBodyByPost(posts: List<Post>): Map<Post, String> {
    if (posts.isEmpty()) return emptyMap()
    return posts.associate { post ->
        post to messageHtmlToPlainText(post.messageHtml).lowercase()
    }
}

internal suspend fun buildLowerBodyByPost(
    posts: List<Post>,
    textCache: ThreadPostTextCache?
): Map<Post, String> {
    if (textCache == null) return buildLowerBodyByPost(posts)
    if (posts.isEmpty()) return emptyMap()
    val result = LinkedHashMap<Post, String>(posts.size)
    posts.forEachIndexed { index, post ->
        if (index % THREAD_FILTER_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        result[post] = textCache.get(post).lowerText
    }
    return result
}

internal fun buildPostHeaderText(post: Post): String {
    return listOfNotNull(
        post.subject,
        post.author,
        post.posterId,
        "No.${post.id}",
        post.timestamp
    ).joinToString(" ") { it.lowercase() }
}

internal fun stableNormalizedListFingerprint(values: List<String>): Int {
    var hash = 1
    values.forEach { raw ->
        val normalized = raw.trim().lowercase()
        if (normalized.isNotEmpty()) {
            hash = 31 * hash + normalized.hashCode()
        }
    }
    return hash
}

internal fun stableThreadFilterOptionSetFingerprint(options: Set<ThreadFilterOption>): Int {
    if (options.isEmpty()) return 0
    var hash = 1
    options
        .map { it.name }
        .sorted()
        .forEach { name ->
            hash = 31 * hash + name.hashCode()
        }
    return hash
}

internal data class ThreadFilterCriteria(
    val options: Set<ThreadFilterOption>,
    val keyword: String,
    val selfPostIdentifiers: List<String>,
    val sortOption: ThreadFilterSortOption?
)

internal data class ThreadFilterUiState(
    val options: Set<ThreadFilterOption> = emptySet(),
    val sortOption: ThreadFilterSortOption? = null,
    val keyword: String = ""
)

internal data class ThreadFilterSheetCallbacks(
    val onOptionToggle: (ThreadFilterOption) -> Unit,
    val onKeywordChange: (String) -> Unit,
    val onClear: () -> Unit,
    val onDismiss: () -> Unit
)

internal data class ThreadFilterSelectionUpdateResult(
    val selectedOptions: Set<ThreadFilterOption>,
    val selectedSortOption: ThreadFilterSortOption?
)

internal data class ThreadFilterComputationState(
    val criteria: ThreadFilterCriteria,
    val hasNgFilters: Boolean,
    val hasThreadFilters: Boolean,
    val shouldComputeFullPostFingerprint: Boolean
)

internal fun buildThreadFilterCriteria(
    uiState: ThreadFilterUiState,
    selfPostIdentifiers: List<String>
): ThreadFilterCriteria {
    return ThreadFilterCriteria(
        options = uiState.options,
        keyword = uiState.keyword,
        selfPostIdentifiers = selfPostIdentifiers,
        sortOption = uiState.sortOption
    )
}

internal fun resolveThreadFilterComputationState(
    uiState: ThreadFilterUiState,
    selfPostIdentifiers: List<String>,
    ngHeaders: List<String>,
    ngWords: List<String>,
    ngFilteringEnabled: Boolean
): ThreadFilterComputationState {
    val criteria = buildThreadFilterCriteria(
        uiState = uiState,
        selfPostIdentifiers = selfPostIdentifiers
    )
    val hasNgFilters = ngFilteringEnabled && (
        ngHeaders.any { it.isNotBlank() } ||
            ngWords.any { it.isNotBlank() }
        )
    val hasThreadFilters = criteria.options.isNotEmpty()
    return ThreadFilterComputationState(
        criteria = criteria,
        hasNgFilters = hasNgFilters,
        hasThreadFilters = hasThreadFilters,
        shouldComputeFullPostFingerprint = hasNgFilters || hasThreadFilters
    )
}

internal fun buildThreadFilterCacheKey(
    postsFingerprint: ThreadPostListFingerprint,
    computationState: ThreadFilterComputationState,
    ngHeaders: List<String>,
    ngWords: List<String>
): ThreadFilterCacheKey {
    return ThreadFilterCacheKey(
        postsFingerprint = postsFingerprint,
        ngEnabled = computationState.hasNgFilters,
        ngHeadersFingerprint = stableNormalizedListFingerprint(ngHeaders),
        ngWordsFingerprint = stableNormalizedListFingerprint(ngWords),
        filterOptionsFingerprint = stableThreadFilterOptionSetFingerprint(computationState.criteria.options),
        keyword = computationState.criteria.keyword.trim().lowercase(),
        selfIdentifiersFingerprint = stableNormalizedListFingerprint(computationState.criteria.selfPostIdentifiers),
        sortOption = computationState.criteria.sortOption
    )
}

internal fun buildThreadFilterSheetCallbacks(
    currentState: () -> ThreadFilterUiState,
    setState: (ThreadFilterUiState) -> Unit,
    onDismiss: () -> Unit
): ThreadFilterSheetCallbacks {
    return ThreadFilterSheetCallbacks(
        onOptionToggle = { option ->
            setState(
                toggleThreadFilterOption(
                    state = currentState(),
                    toggledOption = option
                )
            )
        },
        onKeywordChange = { keyword ->
            setState(
                updateThreadFilterKeyword(
                    state = currentState(),
                    keyword = keyword
                )
            )
        },
        onClear = {
            setState(clearThreadFilterUiState(currentState()))
        },
        onDismiss = onDismiss
    )
}

internal fun updateThreadFilterSelection(
    selectedOptions: Set<ThreadFilterOption>,
    selectedSortOption: ThreadFilterSortOption?,
    toggledOption: ThreadFilterOption
): ThreadFilterSelectionUpdateResult {
    val currentlySelected = toggledOption in selectedOptions
    var updatedOptions = if (currentlySelected) {
        selectedOptions - toggledOption
    } else {
        selectedOptions + toggledOption
    }
    if (toggledOption.sortOption != null && !currentlySelected) {
        updatedOptions = updatedOptions.filter { it.sortOption == null || it == toggledOption }.toSet()
    }
    val updatedSortOption = if (toggledOption.sortOption != null) {
        if (currentlySelected) {
            null
        } else {
            toggledOption.sortOption
        }
    } else {
        selectedSortOption
    }
    return ThreadFilterSelectionUpdateResult(
        selectedOptions = updatedOptions,
        selectedSortOption = updatedSortOption
    )
}

internal fun toggleThreadFilterOption(
    state: ThreadFilterUiState,
    toggledOption: ThreadFilterOption
): ThreadFilterUiState {
    val selectionUpdate = updateThreadFilterSelection(
        selectedOptions = state.options,
        selectedSortOption = state.sortOption,
        toggledOption = toggledOption
    )
    return state.copy(
        options = selectionUpdate.selectedOptions,
        sortOption = selectionUpdate.selectedSortOption
    )
}

internal fun updateThreadFilterKeyword(
    state: ThreadFilterUiState,
    keyword: String
): ThreadFilterUiState {
    return state.copy(keyword = keyword)
}

internal fun clearThreadFilterUiState(
    state: ThreadFilterUiState
): ThreadFilterUiState {
    return state.copy(
        options = emptySet(),
        sortOption = null,
        keyword = ""
    )
}

internal enum class ThreadFilterSortOption(val displayLabel: String) {
    Saidane("そうだね数が多い順"),
    Replies("返信数が多い順")
}

internal enum class ThreadFilterOption(
    val label: String,
    val icon: ImageVector,
    val sortOption: ThreadFilterSortOption? = null
) {
    SelfPosts("自分の書き込み", Icons.Rounded.Person),
    HighSaidane("そうだねが多い", Icons.Rounded.ThumbUp, ThreadFilterSortOption.Saidane),
    HighReplies("返信が多い", Icons.AutoMirrored.Rounded.ReplyAll, ThreadFilterSortOption.Replies),
    Deleted("削除されたレス", Icons.Rounded.DeleteSweep),
    Url("URLを含むレス", Icons.Rounded.Link),
    Image("画像レス", Icons.Outlined.Image),
    Keyword("キーワード", Icons.Rounded.Search);

    companion object {
        val entries = values().toList()
    }
}

internal data class ThreadFilterUiStateBinding(
    val currentState: () -> ThreadFilterUiState,
    val setState: (ThreadFilterUiState) -> Unit
)

internal fun buildThreadFilterUiStateBinding(
    currentOptions: () -> Set<ThreadFilterOption>,
    currentSortOption: () -> ThreadFilterSortOption?,
    currentKeyword: () -> String,
    setOptions: (Set<ThreadFilterOption>) -> Unit,
    setSortOption: (ThreadFilterSortOption?) -> Unit,
    setKeyword: (String) -> Unit
): ThreadFilterUiStateBinding {
    return ThreadFilterUiStateBinding(
        currentState = {
            ThreadFilterUiState(
                options = currentOptions(),
                sortOption = currentSortOption(),
                keyword = currentKeyword()
            )
        },
        setState = { state ->
            setOptions(state.options)
            setSortOption(state.sortOption)
            setKeyword(state.keyword)
        }
    )
}
