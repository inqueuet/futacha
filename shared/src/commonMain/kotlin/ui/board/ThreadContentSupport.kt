package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import com.valoser.futacha.shared.model.EmbeddedHtmlPlacement
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

internal data class ThreadPostDerivedData(
    val posterIdLabels: Map<String, PosterIdLabel> = emptyMap(),
    val postIndex: Map<String, Post> = emptyMap(),
    val referencedByMap: Map<String, List<Post>> = emptyMap(),
    val postsByPosterId: Map<String, List<Post>> = emptyMap()
)

internal fun buildThreadPostDerivedData(posts: List<Post>): ThreadPostDerivedData {
    return buildThreadPostDerivedDataCombined(posts)
}

internal suspend fun buildThreadPostDerivedDataCancellable(posts: List<Post>): ThreadPostDerivedData {
    coroutineContext.ensureActive()
    if (posts.isEmpty()) return ThreadPostDerivedData()
    return buildThreadPostDerivedDataCombinedCancellable(posts)
}

private const val THREAD_POST_DERIVED_DATA_CANCELLATION_CHECK_INTERVAL = 64

private fun buildThreadPostDerivedDataCombined(posts: List<Post>): ThreadPostDerivedData {
    if (posts.isEmpty()) return ThreadPostDerivedData()
    val posterIdTotals = mutableMapOf<String, Int>()
    val postsByPosterId = mutableMapOf<String, MutableList<Post>>()
    val postIndex = LinkedHashMap<String, Post>(posts.size)
    posts.forEach { post ->
        postIndex[post.id] = post
        val normalized = normalizePosterIdValue(post.posterId) ?: return@forEach
        posterIdTotals[normalized] = (posterIdTotals[normalized] ?: 0) + 1
        postsByPosterId.getOrPut(normalized) { mutableListOf() }.add(post)
    }

    val runningPosterIdCounts = mutableMapOf<String, Int>()
    val posterIdLabels = mutableMapOf<String, PosterIdLabel>()
    val referencedBy = mutableMapOf<String, MutableList<Post>>()
    val seenSourceIdsByTarget = mutableMapOf<String, MutableSet<String>>()
    posts.forEach { source ->
        val normalized = normalizePosterIdValue(source.posterId)
        if (normalized != null) {
            val nextIndex = (runningPosterIdCounts[normalized] ?: 0) + 1
            runningPosterIdCounts[normalized] = nextIndex
            val total = posterIdTotals.getValue(normalized)
            posterIdLabels[source.id] = PosterIdLabel(
                text = formatPosterIdLabel(normalized, nextIndex, total),
                highlight = total > 1 && nextIndex > 1
            )
        }
        source.quoteReferences.forEach { reference ->
            reference.targetPostIds.forEach targetLoop@{ targetId ->
                val seenSourceIds = seenSourceIdsByTarget.getOrPut(targetId) { mutableSetOf() }
                if (!seenSourceIds.add(source.id)) return@targetLoop
                referencedBy.getOrPut(targetId) { mutableListOf() }.add(source)
            }
        }
    }
    return ThreadPostDerivedData(
        posterIdLabels = posterIdLabels,
        postIndex = postIndex,
        referencedByMap = referencedBy.mapValues { (_, value) -> value.toList() },
        postsByPosterId = postsByPosterId.mapValues { (_, value) -> value.toList() }
    )
}

private suspend fun buildThreadPostDerivedDataCombinedCancellable(posts: List<Post>): ThreadPostDerivedData {
    val posterIdTotals = mutableMapOf<String, Int>()
    val postsByPosterId = mutableMapOf<String, MutableList<Post>>()
    val postIndex = LinkedHashMap<String, Post>(posts.size)
    posts.forEachIndexed { index, post ->
        checkThreadPostDerivedDataCancellation(index)
        postIndex[post.id] = post
        val normalized = normalizePosterIdValue(post.posterId) ?: return@forEachIndexed
        posterIdTotals[normalized] = (posterIdTotals[normalized] ?: 0) + 1
        postsByPosterId.getOrPut(normalized) { mutableListOf() }.add(post)
    }

    val runningPosterIdCounts = mutableMapOf<String, Int>()
    val posterIdLabels = mutableMapOf<String, PosterIdLabel>()
    val referencedBy = mutableMapOf<String, MutableList<Post>>()
    val seenSourceIdsByTarget = mutableMapOf<String, MutableSet<String>>()
    posts.forEachIndexed { index, source ->
        checkThreadPostDerivedDataCancellation(posts.size + index)
        val normalized = normalizePosterIdValue(source.posterId)
        if (normalized != null) {
            val nextIndex = (runningPosterIdCounts[normalized] ?: 0) + 1
            runningPosterIdCounts[normalized] = nextIndex
            val total = posterIdTotals.getValue(normalized)
            posterIdLabels[source.id] = PosterIdLabel(
                text = formatPosterIdLabel(normalized, nextIndex, total),
                highlight = total > 1 && nextIndex > 1
            )
        }
        source.quoteReferences.forEach { reference ->
            reference.targetPostIds.forEach targetLoop@{ targetId ->
                val seenSourceIds = seenSourceIdsByTarget.getOrPut(targetId) { mutableSetOf() }
                if (!seenSourceIds.add(source.id)) return@targetLoop
                referencedBy.getOrPut(targetId) { mutableListOf() }.add(source)
            }
        }
    }
    coroutineContext.ensureActive()
    return ThreadPostDerivedData(
        posterIdLabels = posterIdLabels,
        postIndex = postIndex,
        referencedByMap = referencedBy.mapValues { (_, value) -> value.toList() },
        postsByPosterId = postsByPosterId.mapValues { (_, value) -> value.toList() }
    )
}

private suspend fun checkThreadPostDerivedDataCancellation(index: Int) {
    if (index % THREAD_POST_DERIVED_DATA_CANCELLATION_CHECK_INTERVAL == 0) {
        coroutineContext.ensureActive()
        yield()
    }
}

internal fun countThreadContentItemsBeforePosts(
    page: ThreadPage?,
    embeddedHtml: List<EmbeddedHtmlContent>,
    hasSummary: Boolean = false
): Int {
    if (page == null) return 0
    var count = 0
    if (hasSummary) {
        count += 1
    }
    if (embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Header }) {
        count += 1
    }
    if (!page.deletedNotice.isNullOrBlank()) {
        count += 1
    }
    return count
}

internal fun countThreadContentItems(
    page: ThreadPage?,
    embeddedHtml: List<EmbeddedHtmlContent>,
    hasSummary: Boolean = false,
    hasAiPostModeration: Boolean = false,
    hasAiHiddenPostsSummary: Boolean = false
): Int {
    if (page == null) return 0
    var count = countThreadContentItemsBeforePosts(
        page = page,
        embeddedHtml = embeddedHtml,
        hasSummary = hasSummary
    )
    if (hasAiPostModeration) {
        count += 1
    }
    if (hasAiHiddenPostsSummary) {
        count += 1
    }
    count += page.posts.size
    if (!page.expiresAtLabel.isNullOrBlank()) {
        count += 1
    }
    if (embeddedHtml.any { it.placement == EmbeddedHtmlPlacement.Footer }) {
        count += 1
    }
    return count
}

internal fun resolveThreadLazyListIndexForPost(
    postIndex: Int,
    page: ThreadPage?,
    embeddedHtml: List<EmbeddedHtmlContent>,
    hasSummary: Boolean = false
): Int {
    return (countThreadContentItemsBeforePosts(page, embeddedHtml, hasSummary) + postIndex).coerceAtLeast(0)
}

internal data class ThreadPostListFingerprint(
    val size: Int,
    val firstPostId: String?,
    val lastPostId: String?,
    val rollingHash: Long
)

private const val THREAD_POST_FINGERPRINT_TEXT_SAMPLE_CHARS = 512
private const val THREAD_POST_FINGERPRINT_CANCELLATION_CHECK_INTERVAL = 64

internal data class ThreadFilterCacheKey(
    val postsFingerprint: ThreadPostListFingerprint,
    val ngEnabled: Boolean,
    val ngHeadersFingerprint: Int,
    val ngWordsFingerprint: Int,
    val filterOptionsFingerprint: Int,
    val keyword: String,
    val selfIdentifiersFingerprint: Int,
    val sortOption: ThreadFilterSortOption?
)

internal fun buildLightweightThreadPostListFingerprint(posts: List<Post>): ThreadPostListFingerprint {
    return ThreadPostListFingerprint(
        size = posts.size,
        firstPostId = posts.firstOrNull()?.id,
        lastPostId = posts.lastOrNull()?.id,
        rollingHash = 0L
    )
}

internal fun buildThreadPostListFingerprint(posts: List<Post>): ThreadPostListFingerprint {
    if (posts.isEmpty()) {
        return buildLightweightThreadPostListFingerprint(posts)
    }
    var rollingHash = 1_469_598_103_934_665_603L
    posts.forEach { post ->
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.id)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.author)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.subject)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.posterId)
        rollingHash = mixThreadPostFingerprintHash(
            rollingHash,
            post.messageHtml.take(THREAD_POST_FINGERPRINT_TEXT_SAMPLE_CHARS)
        )
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.messageHtml.length)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.imageUrl)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.thumbnailUrl)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.saidaneLabel)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.quoteReferences.size)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.referencedCount)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, if (post.isDeleted) 1 else 0)
    }
    return ThreadPostListFingerprint(
        size = posts.size,
        firstPostId = posts.first().id,
        lastPostId = posts.last().id,
        rollingHash = rollingHash
    )
}

internal suspend fun buildThreadPostListFingerprintCancellable(posts: List<Post>): ThreadPostListFingerprint {
    if (posts.isEmpty()) {
        return buildLightweightThreadPostListFingerprint(posts)
    }
    coroutineContext.ensureActive()
    var rollingHash = 1_469_598_103_934_665_603L
    posts.forEachIndexed { index, post ->
        if (index % THREAD_POST_FINGERPRINT_CANCELLATION_CHECK_INTERVAL == 0) {
            coroutineContext.ensureActive()
            yield()
        }
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.id)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.author)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.subject)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.posterId)
        rollingHash = mixThreadPostFingerprintHash(
            rollingHash,
            post.messageHtml.take(THREAD_POST_FINGERPRINT_TEXT_SAMPLE_CHARS)
        )
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.messageHtml.length)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.imageUrl)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.thumbnailUrl)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.saidaneLabel)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.quoteReferences.size)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.referencedCount)
        rollingHash = mixThreadPostFingerprintHash(rollingHash, if (post.isDeleted) 1 else 0)
    }
    coroutineContext.ensureActive()
    return ThreadPostListFingerprint(
        size = posts.size,
        firstPostId = posts.first().id,
        lastPostId = posts.last().id,
        rollingHash = rollingHash
    )
}

private fun mixThreadPostFingerprintHash(current: Long, value: String?): Long {
    return (current * 1_099_511_628_211L) xor (value?.hashCode()?.toLong() ?: 0L)
}

private fun mixThreadPostFingerprintHash(current: Long, value: Int): Long {
    return (current * 1_099_511_628_211L) xor value.toLong()
}

internal fun buildPostsByPosterId(posts: List<Post>): Map<String, List<Post>> {
    if (posts.isEmpty()) return emptyMap()
    val groups = mutableMapOf<String, MutableList<Post>>()
    posts.forEach { post ->
        val normalized = normalizePosterIdValue(post.posterId) ?: return@forEach
        groups.getOrPut(normalized) { mutableListOf() }.add(post)
    }
    if (groups.isEmpty()) return emptyMap()
    return groups.mapValues { (_, value) -> value.toList() }
}

internal fun buildReferencedPostsMap(posts: List<Post>): Map<String, List<Post>> {
    if (posts.isEmpty()) return emptyMap()
    val orderIndex = posts.mapIndexed { index, post -> post.id to index }.toMap()
    val referencedBy = mutableMapOf<String, MutableList<Post>>()
    val seenSourceIdsByTarget = mutableMapOf<String, MutableSet<String>>()
    posts.forEach { source ->
        source.quoteReferences.forEach { reference ->
            reference.targetPostIds.forEach { targetId ->
                val seenSourceIds = seenSourceIdsByTarget.getOrPut(targetId) { mutableSetOf() }
                if (!seenSourceIds.add(source.id)) return@forEach
                referencedBy.getOrPut(targetId) { mutableListOf() }.add(source)
            }
        }
    }
    if (referencedBy.isEmpty()) return emptyMap()
    return referencedBy.mapValues { (_, value) ->
        value
            .sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
    }
}

@Composable
internal fun ThreadScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(6.dp)
    ) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@Canvas
        val totalItems = layoutInfo.totalItemsCount
        if (totalItems <= 0) return@Canvas
        val viewportHeightPx = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        if (viewportHeightPx <= 0) return@Canvas

        var visibleSizeTotal = 0
        var visibleSizeCount = 0
        visibleItems.forEach { item ->
            if (item.size > 0) {
                visibleSizeTotal += item.size
                visibleSizeCount += 1
            }
        }
        if (visibleSizeCount == 0) return@Canvas

        val avgItemSizePx = visibleSizeTotal.toFloat() / visibleSizeCount.toFloat()
        val contentHeightPx = avgItemSizePx * totalItems
        if (contentHeightPx <= viewportHeightPx) return@Canvas

        val firstVisibleSize = visibleItems.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
        val partialIndex = listState.firstVisibleItemScrollOffset / firstVisibleSize.toFloat()
        val totalScrollableItems = (totalItems - visibleItems.size).coerceAtLeast(1)
        val scrollFraction = ((listState.firstVisibleItemIndex + partialIndex) / totalScrollableItems)
            .coerceIn(0f, 1f)
        val thumbHeightFraction = (viewportHeightPx / contentHeightPx).coerceIn(0.05f, 1f)

        val trackWidth = size.width
        val trackHeight = size.height
        val trackCornerRadius = CornerRadius(trackWidth / 2f, trackWidth / 2f)
        drawRoundRect(
            color = trackColor,
            size = Size(trackWidth, trackHeight),
            cornerRadius = trackCornerRadius
        )
        val thumbHeightPx = (trackHeight * thumbHeightFraction).coerceAtLeast(trackWidth)
        val thumbOffsetPx = (trackHeight - thumbHeightPx) * scrollFraction
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(x = 0f, y = thumbOffsetPx),
            size = Size(trackWidth, thumbHeightPx),
            cornerRadius = trackCornerRadius
        )
    }
}
