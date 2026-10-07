package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatQuoteIndex
import com.valoser.futacha.shared.compat.compatQuoteQueryForLine
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.ui.board.buildThreadTreeNodes
import kotlinx.coroutines.yield

internal const val COMPAT_THREAD_DISPLAY_MODE_KEY = "threadDisplayMode"

internal fun Map<String, String>.compatThreadTreeEnabled(): Boolean =
    compatPreferenceValue("thread", COMPAT_THREAD_DISPLAY_MODE_KEY) == "tree"

internal data class CompatThreadDisplay(
    val posts: List<CompatPostSnapshot>,
    val depthByPostNo: Map<String, Int> = emptyMap()
)

/** Order only the filtered presentation; keep original positions and cached snapshots intact. */
internal suspend fun buildCompatThreadDisplay(posts: List<CompatPostSnapshot>, tree: Boolean): CompatThreadDisplay {
    if (!tree || posts.isEmpty()) return CompatThreadDisplay(posts)
    val postsByNo = posts.associateBy { it.postNo }
    // One lookup table for the whole snapshot instead of a filter/sort/scan of every post per quote line.
    val quoteIndex = CompatQuoteIndex(posts)
    val nodes = buildThreadTreeNodes(posts.mapIndexed { index, post ->
        if (index % 64 == 0) yield()
        // Older saved snapshots lack parsed references. Resolve their first usable
        // quote through the same resolver as the compatibility quote popup.
        val references = if (post.quoteReferences.isNotEmpty() || post.isContentRedacted) post.quoteReferences else {
            post.messageHtml.toCompatPlainText().lineSequence()
                .mapNotNull(::compatQuoteQueryForLine)
                .mapNotNull { query ->
                    quoteIndex.resolveFirst(post.position, query)
                        ?.let { QuoteReference(query, listOf(it.postNo)) }
                }.firstOrNull()?.let(::listOf).orEmpty()
        }
        // The shared tree builder needs identities and references only. Rows use
        // the original snapshots so media, deletion notices and numbering survive.
        Post(id = post.postNo, author = null, subject = null, timestamp = "",
            messageHtml = "", imageUrl = null, thumbnailUrl = null, quoteReferences = references)
    })
    return CompatThreadDisplay(
        posts = nodes.map { postsByNo.getValue(it.post.id) },
        depthByPostNo = nodes.associate { it.post.id to it.depth }
    )
}

/**
 * Same result as `ui.board.relatedCompatPosts`: the selected post, its direct sources and its
 * direct replies once, in the order of [posts]. The quote resolver index is built once per call.
 */
internal suspend fun relatedCompatPostsIndexed(postNo: String, posts: List<CompatPostSnapshot>): List<CompatPostSnapshot> {
    val selected = posts.firstOrNull { it.postNo == postNo } ?: return emptyList()
    val quoteIndex = CompatQuoteIndex(posts)
    fun targets(post: CompatPostSnapshot): Set<String> {
        if (post.isContentRedacted) return emptySet()
        if (post.quoteReferences.isNotEmpty()) return post.quoteReferences.flatMap { it.targetPostIds }.toSet()
        return post.messageHtml.toCompatPlainText().lineSequence().mapNotNull(::compatQuoteQueryForLine)
            .flatMap { quoteIndex.resolve(post.position, it).asSequence().map { match -> match.postNo } }.toSet()
    }
    val parents = targets(selected)
    return posts.filterIndexed { index, post ->
        if (index % 64 == 0) yield()
        post.postNo == postNo || post.postNo in parents || postNo in targets(post)
    }.distinctBy { it.postNo }
}
