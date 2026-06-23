package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.CatalogItem

internal enum class CatalogTitleCompletionTrigger {
    EmptyOrNumericReplyCount
}

internal data class CatalogTitleCompletionPolicy(
    val enabled: Boolean,
    val trigger: CatalogTitleCompletionTrigger = CatalogTitleCompletionTrigger.EmptyOrNumericReplyCount,
    val allowFallbackHeadScan: Boolean = true
)

private val titleCompletionHosts = setOf(
    "img.2chan.net"
)

private const val TITLE_COMPLETION_MIN_SAMPLE_SIZE = 5
private const val TITLE_COMPLETION_PLACEHOLDER_RATIO_THRESHOLD = 0.5

internal fun shouldResolveCatalogThreadTitleFromHead(
    boardUrl: String?,
    currentTitle: String?,
    replyCount: Int,
    inferredPolicy: CatalogTitleCompletionPolicy? = null
): Boolean {
    val policy = inferredPolicy ?: resolveCatalogTitleCompletionPolicy(boardUrl)
    if (!policy.enabled) return false
    return matchesCatalogTitleCompletionTrigger(policy.trigger, currentTitle, replyCount)
}

internal fun shouldResolveCatalogItemTitleFromHead(
    currentTitle: String?,
    replyCount: Int
): Boolean {
    return shouldResolveEmptyOrNumericReplyCountTitle(currentTitle, replyCount)
}

internal fun shouldUseCatalogTitleFallbackHeadScan(boardUrl: String?): Boolean {
    val policy = resolveCatalogTitleCompletionPolicy(boardUrl)
    return policy.enabled && policy.allowFallbackHeadScan
}

internal fun inferCatalogTitleCompletionPolicy(items: List<CatalogItem>): CatalogTitleCompletionPolicy? {
    if (items.size < TITLE_COMPLETION_MIN_SAMPLE_SIZE) {
        return null
    }
    val placeholderCount = items.count { item ->
        shouldResolveCatalogItemTitleFromHead(item.title, item.replyCount)
    }
    val placeholderRatio = placeholderCount.toDouble() / items.size.toDouble()
    return CatalogTitleCompletionPolicy(
        enabled = placeholderRatio >= TITLE_COMPLETION_PLACEHOLDER_RATIO_THRESHOLD,
        allowFallbackHeadScan = true
    )
}

internal fun resolveCatalogTitleCompletionPolicy(boardUrl: String?): CatalogTitleCompletionPolicy {
    val host = extractCatalogTitleCompletionHost(boardUrl)
    return CatalogTitleCompletionPolicy(
        enabled = host in titleCompletionHosts,
        allowFallbackHeadScan = true
    )
}

private fun matchesCatalogTitleCompletionTrigger(
    trigger: CatalogTitleCompletionTrigger,
    currentTitle: String?,
    replyCount: Int
): Boolean {
    return when (trigger) {
        CatalogTitleCompletionTrigger.EmptyOrNumericReplyCount ->
            shouldResolveEmptyOrNumericReplyCountTitle(currentTitle, replyCount)
    }
}

private fun shouldResolveEmptyOrNumericReplyCountTitle(
    currentTitle: String?,
    replyCount: Int
): Boolean {
    val trimmedTitle = currentTitle?.trim().orEmpty()
    if (trimmedTitle.isEmpty()) return true
    val numericTitle = trimmedTitle.toIntOrNull() ?: return false
    return numericTitle == replyCount || replyCount <= 0
}

private fun extractCatalogTitleCompletionHost(boardUrl: String?): String {
    return boardUrl
        ?.substringAfter("://", boardUrl)
        ?.substringBefore('/')
        ?.substringBefore(':')
        ?.lowercase()
        ?.trim()
        .orEmpty()
}
