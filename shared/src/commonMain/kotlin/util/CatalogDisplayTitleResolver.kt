package com.valoser.futacha.shared.util

internal fun shouldResolveCatalogThreadTitleFromHead(
    boardUrl: String?,
    currentTitle: String?,
    replyCount: Int
): Boolean {
    if (!isImgBoard(boardUrl)) return false
    val trimmedTitle = currentTitle?.trim().orEmpty()
    if (trimmedTitle.isEmpty()) return true
    val numericTitle = trimmedTitle.toIntOrNull() ?: return false
    return numericTitle == replyCount || replyCount <= 0
}

internal fun isImgBoard(boardUrl: String?): Boolean {
    val host = boardUrl
        ?.substringAfter("://", boardUrl)
        ?.substringBefore('/')
        ?.substringBefore(':')
        ?.lowercase()
        ?.trim()
        .orEmpty()
    return host == "img.2chan.net"
}
