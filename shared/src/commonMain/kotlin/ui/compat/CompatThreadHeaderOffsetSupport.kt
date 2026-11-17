package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.ScrollAnchor

/**
 * The thread list may start with one non-post row (the deleted-posts notice header), so a raw
 * LazyColumn index is `postIndex + headerCount`. These helpers keep that conversion in one place.
 */
internal data class CompatThreadListTarget(val index: Int, val offsetPx: Int)

/**
 * Anchor for the first visible row. While the header row itself is the first visible item the
 * anchor holds no post ([ScrollAnchor.postNo] == null, index 0): treating the header as post 0
 * made the restore skip over it, so the notice was pushed off screen right after opening the
 * thread or refreshing it.
 */
internal fun buildCompatThreadScrollAnchor(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    headerCount: Int,
    postNoAt: (Int) -> String?,
    snapshotRevision: Long
): ScrollAnchor {
    val offset = firstVisibleItemScrollOffset.coerceAtLeast(0)
    if (headerCount > 0 && firstVisibleItemIndex < headerCount) {
        return ScrollAnchor(postNo = null, offsetPx = offset, fallbackIndex = 0, snapshotRevision = snapshotRevision)
    }
    val index = (firstVisibleItemIndex - headerCount).coerceAtLeast(0)
    return ScrollAnchor(
        postNo = postNoAt(index),
        offsetPx = offset,
        fallbackIndex = index,
        snapshotRevision = snapshotRevision
    )
}

/** The LazyColumn position to restore [anchor] at, or null while there is nothing to scroll to. */
internal fun resolveCompatThreadListTarget(
    anchor: ScrollAnchor,
    postNos: List<String>,
    headerCount: Int
): CompatThreadListTarget? {
    if (postNos.isEmpty()) return null
    val offset = anchor.offsetPx.coerceAtLeast(0)
    if (headerCount > 0 && anchor.postNo == null && anchor.fallbackIndex <= 0) {
        return CompatThreadListTarget(index = 0, offsetPx = offset)
    }
    val byPostNo = anchor.postNo?.let { postNo -> postNos.indexOf(postNo) }?.takeIf { it >= 0 }
    val postIndex = (byPostNo ?: anchor.fallbackIndex).coerceIn(0, postNos.lastIndex)
    return CompatThreadListTarget(index = postIndex + headerCount, offsetPx = offset)
}

/** Raw list index of the post at [postIndex]. */
internal fun compatThreadListIndexOfPost(postIndex: Int, headerCount: Int): Int =
    (postIndex + headerCount).coerceAtLeast(0)

/** Post index shown by raw list row [listIndex], or null for the header row. */
internal fun compatThreadPostIndexOfListIndex(listIndex: Int, headerCount: Int): Int? =
    (listIndex - headerCount).takeIf { it >= 0 }

/** Raw index of the last row: header + posts + the optional expiry footer. */
internal fun compatThreadLastListIndex(postCount: Int, headerCount: Int, hasFooter: Boolean): Int =
    (headerCount + postCount - 1 + if (hasFooter) 1 else 0).coerceAtLeast(0)
