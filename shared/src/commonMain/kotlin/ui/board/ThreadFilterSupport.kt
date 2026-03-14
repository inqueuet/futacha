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

internal fun applyNgFilters(
    page: ThreadPage,
    ngHeaders: List<String>,
    ngWords: List<String>,
    enabled: Boolean,
    precomputedLowerBodyByPostId: Map<String, String>? = null
): ThreadPage {
    if (!enabled) return page
    val headerFilters = ngHeaders.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    val wordFilters = ngWords.mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() }?.lowercase() }
    if (headerFilters.isEmpty() && wordFilters.isEmpty()) return page
    val lowerBodyByPostId = if (wordFilters.isEmpty()) {
        emptyMap()
    } else {
        precomputedLowerBodyByPostId ?: buildLowerBodyByPostId(page.posts)
    }
    val filteredPosts = page.posts.filterNot { post ->
        matchesNgFilters(post, headerFilters, wordFilters, lowerBodyByPostId)
    }
    return page.copy(posts = filteredPosts)
}

internal fun applyThreadFilters(
    page: ThreadPage,
    criteria: ThreadFilterCriteria,
    precomputedLowerBodyByPostId: Map<String, String>? = null
): ThreadPage {
    if (criteria.options.isEmpty()) return page
    val normalizedSelfPostIdentifiers = criteria.selfPostIdentifiers
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    val needsLowerBodyByPostId = criteria.options.any {
        it == ThreadFilterOption.Url || it == ThreadFilterOption.Keyword
    }
    val lowerBodyByPostId = if (needsLowerBodyByPostId) {
        precomputedLowerBodyByPostId ?: buildLowerBodyByPostId(page.posts)
    } else {
        emptyMap()
    }
    val filteredPosts = page.posts.filter { post ->
        matchesThreadFilters(
            post = post,
            criteria = criteria,
            lowerBodyByPostId = lowerBodyByPostId,
            normalizedSelfPostIdentifiers = normalizedSelfPostIdentifiers
        )
    }
    val sortedPosts = sortThreadPosts(filteredPosts, criteria.sortOption)
    return page.copy(posts = sortedPosts)
}

internal fun matchesThreadFilters(
    post: Post,
    criteria: ThreadFilterCriteria,
    lowerBodyByPostId: Map<String, String>,
    normalizedSelfPostIdentifiers: Set<String>
): Boolean {
    val filterOptions = criteria.options.filter { it.sortOption == null }
    if (filterOptions.isEmpty()) return true
    val lowerText = lowerBodyByPostId[post.id] ?: ""
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
    return Regex("""\d+""").find(source)?.value?.toIntOrNull()
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

internal fun matchesNgFilters(
    post: Post,
    headerFilters: List<String>,
    wordFilters: List<String>,
    lowerBodyByPostId: Map<String, String>
): Boolean {
    if (headerFilters.isNotEmpty()) {
        val headerText = buildPostHeaderText(post)
        if (headerFilters.any { headerText.contains(it) }) {
            return true
        }
    }
    if (wordFilters.isNotEmpty()) {
        val bodyText = lowerBodyByPostId[post.id] ?: ""
        if (wordFilters.any { bodyText.contains(it) }) {
            return true
        }
    }
    return false
}

internal fun buildLowerBodyByPostId(posts: List<Post>): Map<String, String> {
    if (posts.isEmpty()) return emptyMap()
    return posts.associate { post ->
        post.id to messageHtmlToPlainText(post.messageHtml).lowercase()
    }
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
