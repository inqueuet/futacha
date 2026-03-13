package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post

internal data class QuoteSelectionItem(
    val id: String,
    val title: String,
    val preview: String,
    val content: String,
    val isDefault: Boolean = false
)

internal fun buildQuoteSelectionItems(post: Post): List<QuoteSelectionItem> {
    val items = mutableListOf<QuoteSelectionItem>()
    items += QuoteSelectionItem(
        id = "number-${post.id}",
        title = "レスNo.",
        preview = "No.${post.id}",
        content = ">No.${post.id}",
        isDefault = true
    )
    extractFileNameFromUrl(post.imageUrl)?.let { fileName ->
        items += QuoteSelectionItem(
            id = "file-${post.id}",
            title = "ファイル名",
            preview = fileName,
            content = ">$fileName"
        )
    }
    val bodyLines = messageHtmlToLines(post.messageHtml)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    bodyLines.forEachIndexed { index, line ->
        items += QuoteSelectionItem(
            id = "line-$index",
            title = "本文 ${index + 1}行目",
            preview = line,
            content = ">$line"
        )
    }
    return items
}

internal fun defaultQuoteSelectionIds(selectionItems: List<QuoteSelectionItem>): Set<String> {
    return selectionItems
        .filter { it.isDefault }
        .map { it.id }
        .toSet()
        .ifEmpty { selectionItems.firstOrNull()?.let { setOf(it.id) } ?: emptySet() }
}

internal fun appendSelectedQuoteLines(
    existingReplyComment: String,
    selectedLines: List<String>
): String? {
    val quoteBody = selectedLines.joinToString("\n").trimEnd()
    if (quoteBody.isBlank()) return null
    val existing = existingReplyComment.trimEnd()
    return buildString {
        if (existing.isNotBlank()) {
            append(existing)
            append("\n")
        }
        append(quoteBody)
        append("\n")
    }
}

internal fun extractFileNameFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val sanitized = url.substringBefore('#').substringBefore('?')
    val name = sanitized.substringAfterLast('/', "")
    return name.takeIf { it.isNotBlank() }
}

internal fun buildPosterIdLabels(posts: List<Post>): Map<String, PosterIdLabel> {
    if (posts.isEmpty()) return emptyMap()
    val totals = mutableMapOf<String, Int>()
    posts.forEach { post ->
        normalizePosterIdValue(post.posterId)?.let { normalized ->
            totals[normalized] = (totals[normalized] ?: 0) + 1
        }
    }
    if (totals.isEmpty()) return emptyMap()
    val runningCounts = mutableMapOf<String, Int>()
    val labels = mutableMapOf<String, PosterIdLabel>()
    posts.forEach { post ->
        val normalized = normalizePosterIdValue(post.posterId) ?: return@forEach
        val nextIndex = (runningCounts[normalized] ?: 0) + 1
        runningCounts[normalized] = nextIndex
        val total = totals.getValue(normalized)
        labels[post.id] = PosterIdLabel(
            text = formatPosterIdLabel(normalized, nextIndex, total),
            highlight = total > 1 && nextIndex > 1
        )
    }
    return labels
}

internal fun normalizePosterIdValue(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    val withoutPrefix = if (trimmed.startsWith("ID:", ignoreCase = true)) {
        trimmed.substring(3)
    } else {
        trimmed
    }
    return withoutPrefix.trim().takeIf { it.isNotBlank() }
}

internal fun buildNgHeaderPrefillValue(post: Post): String? {
    val normalized = normalizePosterIdValue(post.posterId) ?: return null
    val withoutSlip = normalized.substringBefore('/')
    return withoutSlip.takeIf { it.isNotBlank() }
}

internal data class ThreadSaidaneActionState(
    val shouldProceed: Boolean,
    val blockedMessage: String? = null,
    val successMessage: String = "そうだねを送信しました",
    val failurePrefix: String = "そうだねに失敗しました",
    val updatedLabel: String? = null
)

internal fun resolveThreadSaidaneActionState(
    isSelfPost: Boolean,
    currentLabel: String?
): ThreadSaidaneActionState {
    return if (isSelfPost) {
        ThreadSaidaneActionState(
            shouldProceed = false,
            blockedMessage = buildSelfSaidaneBlockedMessage()
        )
    } else {
        ThreadSaidaneActionState(
            shouldProceed = true,
            updatedLabel = incrementSaidaneLabel(currentLabel)
        )
    }
}

internal data class ThreadDelRequestActionState(
    val successMessage: String = "DEL依頼を送信しました",
    val failurePrefix: String = "DEL依頼に失敗しました"
)

internal fun resolveThreadDelRequestActionState(): ThreadDelRequestActionState {
    return ThreadDelRequestActionState()
}

internal data class ThreadNgRegistrationActionState(
    val prefillValue: String?,
    val message: String?,
    val shouldShowNgManagement: Boolean = true
)

internal fun resolveThreadNgRegistrationActionState(post: Post): ThreadNgRegistrationActionState {
    val prefillValue = buildNgHeaderPrefillValue(post)
    return ThreadNgRegistrationActionState(
        prefillValue = prefillValue,
        message = if (prefillValue == null) buildMissingPosterIdMessage() else null
    )
}

internal data class QuotePreviewState(
    val quoteText: String,
    val targetPosts: List<Post>,
    val posterIdLabels: Map<String, PosterIdLabel>
)

internal fun resolveQuotePreviewTargets(
    targetPostIds: List<String>,
    postIndex: Map<String, Post>
): List<Post> {
    return targetPostIds.mapNotNull { postIndex[it] }
}

internal fun resolveQuotePreviewState(
    isScrolling: Boolean,
    quoteText: String,
    targetPosts: List<Post>,
    posterIdLabels: Map<String, PosterIdLabel>
): QuotePreviewState? {
    if (isScrolling || targetPosts.isEmpty()) return null
    return QuotePreviewState(
        quoteText = quoteText,
        targetPosts = targetPosts,
        posterIdLabels = posterIdLabels
    )
}

internal fun canHandleThreadPostLongPress(
    quotePreviewState: QuotePreviewState?
): Boolean = quotePreviewState == null

internal fun buildSelfSaidaneBlockedMessage(): String = "自分のレスにはそうだねできません"

internal fun buildMissingPosterIdMessage(): String = "IDが見つかりませんでした"

internal fun validateThreadDeletePassword(password: String): String? {
    return if (!hasDeleteKeyForSubmit(password)) {
        buildDeletePasswordRequiredMessage()
    } else {
        null
    }
}

internal fun formatPosterIdLabel(value: String, index: Int, total: Int): String {
    val safeIndex = index.coerceAtLeast(1)
    val safeTotal = total.coerceAtLeast(safeIndex)
    return "ID:$value(${safeIndex}/${safeTotal})"
}

internal data class PosterIdLabel(
    val text: String,
    val highlight: Boolean
)
