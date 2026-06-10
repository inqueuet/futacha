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

internal data class ThreadPostDerivedData(
    val posterIdLabels: Map<String, PosterIdLabel> = emptyMap(),
    val postIndex: Map<String, Post> = emptyMap(),
    val referencedByMap: Map<String, List<Post>> = emptyMap(),
    val postsByPosterId: Map<String, List<Post>> = emptyMap()
)

internal fun buildThreadPostDerivedData(posts: List<Post>): ThreadPostDerivedData {
    return ThreadPostDerivedData(
        posterIdLabels = buildPosterIdLabels(posts),
        postIndex = posts.associateBy { it.id },
        referencedByMap = buildReferencedPostsMap(posts),
        postsByPosterId = buildPostsByPosterId(posts)
    )
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
        rollingHash = mixThreadPostFingerprintHash(rollingHash, post.messageHtml)
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
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 0) return
    val viewportHeightPx = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    if (viewportHeightPx <= 0) return
    val avgItemSizePx = visibleItems
        .map { it.size }
        .filter { it > 0 }
        .takeIf { it.isNotEmpty() }
        ?.average()
        ?.toFloat()
        ?: return
    val contentHeightPx = avgItemSizePx * totalItems
    if (contentHeightPx <= viewportHeightPx) return

    val firstVisibleSize = visibleItems.firstOrNull()?.size?.coerceAtLeast(1) ?: 1
    val partialIndex = listState.firstVisibleItemScrollOffset / firstVisibleSize.toFloat()
    val totalScrollableItems = (totalItems - visibleItems.size).coerceAtLeast(1)
    val scrollFraction = ((listState.firstVisibleItemIndex + partialIndex) / totalScrollableItems)
        .coerceIn(0f, 1f)
    val thumbHeightFraction = (viewportHeightPx / contentHeightPx).coerceIn(0.05f, 1f)

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)

    Canvas(
        modifier = modifier
            .fillMaxHeight()
            .width(6.dp)
    ) {
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
