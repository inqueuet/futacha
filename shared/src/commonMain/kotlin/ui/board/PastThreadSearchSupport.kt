package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.network.ArchiveSearchItem

internal fun normalizePastThreadSearchQuery(query: String): String = query.trim()

internal fun buildPastThreadSearchErrorMessage(error: Throwable): String {
    return error.message ?: "検索に失敗しました"
}

internal fun buildPastThreadSearchIdleMessage(): String {
    return "検索ワードを入力するとここに結果が表示されます。"
}

internal fun buildPastThreadSearchEmptyMessage(): String {
    return "見つかりませんでした"
}

internal fun archiveSearchItemToCatalogItem(item: ArchiveSearchItem): CatalogItem {
    return CatalogItem(
        id = item.threadId,
        threadUrl = item.htmlUrl,
        title = item.title,
        thumbnailUrl = item.thumbUrl,
        fullImageUrl = item.thumbUrl,
        replyCount = 0
    )
}
