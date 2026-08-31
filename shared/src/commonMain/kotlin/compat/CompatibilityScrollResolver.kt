package com.valoser.futacha.shared.compat

data class CompatScrollPosition(
    val index: Int,
    val offsetPx: Int
)

/** Uses the stable post number first and falls back to the stored list position. */
fun resolveCompatScrollIndex(snapshot: CompatThreadSnapshot, anchor: ScrollAnchor): Int {
    if (snapshot.posts.isEmpty()) return 0
    val byPostNo = anchor.postNo?.let { no -> snapshot.posts.indexOfFirst { it.postNo == no } } ?: -1
    return (if (byPostNo >= 0) byPostNo else anchor.fallbackIndex)
        .coerceIn(0, snapshot.posts.lastIndex)
}

/** Resolves the same stable post and pixel offset used by the active thread list. */
fun resolveCompatScrollPosition(
    snapshot: CompatThreadSnapshot,
    anchor: ScrollAnchor
): CompatScrollPosition = CompatScrollPosition(
    index = resolveCompatScrollIndex(snapshot, anchor),
    offsetPx = anchor.offsetPx.coerceAtLeast(0)
)
