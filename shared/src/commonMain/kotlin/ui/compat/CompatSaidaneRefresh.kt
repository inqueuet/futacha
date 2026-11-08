package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatPostSnapshot

/**
 * The posts with [postNo]'s そうだね label replaced. The extraction and quote popups hold copies of
 * the posts, so a そうだね sent from them must reach those copies too (FutabaViewer 18-20 redraws
 * the open popup); otherwise the popup kept the old count until it was opened again.
 */
internal fun compatPostsWithSaidane(
    posts: List<CompatPostSnapshot>,
    postNo: String,
    label: String
): List<CompatPostSnapshot> =
    if (posts.none { it.postNo == postNo }) posts
    else posts.map { if (it.postNo == postNo) it.copy(saidaneLabel = label) else it }
