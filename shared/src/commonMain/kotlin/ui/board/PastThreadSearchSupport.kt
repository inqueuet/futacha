package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.ArchiveSearchItem

internal fun normalizePastThreadSearchQuery(query: String): String = query.trim()

internal fun buildPastThreadSearchDialogInitialQuery(
    archiveSearchQuery: String,
    searchQuery: String
): String {
    return archiveSearchQuery.ifBlank { searchQuery }
}

internal data class PastThreadSearchStartState(
    val normalizedQuery: String,
    val scope: ArchiveSearchScope?,
    val shouldShowDialog: Boolean = false,
    val shouldShowSheet: Boolean = true
)

internal fun buildPastThreadSearchStartState(
    query: String,
    scope: ArchiveSearchScope?
): PastThreadSearchStartState {
    return PastThreadSearchStartState(
        normalizedQuery = normalizePastThreadSearchQuery(query),
        scope = scope
    )
}

internal data class PastThreadSearchSheetState(
    val nextGeneration: Long,
    val shouldShowSheet: Boolean = false,
    val shouldClearRunningJob: Boolean = true
)

internal fun dismissPastThreadSearchSheet(currentGeneration: Long): PastThreadSearchSheetState {
    return PastThreadSearchSheetState(nextGeneration = currentGeneration + 1L)
}

internal data class PastThreadSearchSelectionState(
    val sheetState: PastThreadSearchSheetState,
    val selectedCatalogItem: CatalogItem
)

internal fun selectPastThreadSearchItem(
    currentGeneration: Long,
    item: ArchiveSearchItem
): PastThreadSearchSelectionState {
    return PastThreadSearchSelectionState(
        sheetState = dismissPastThreadSearchSheet(currentGeneration),
        selectedCatalogItem = archiveSearchItemToCatalogItem(item)
    )
}

internal fun buildPastThreadSearchErrorMessage(error: Throwable): String {
    return error.message ?: "検索に失敗しました"
}

internal fun buildPastThreadSearchIdleMessage(): String {
    return "スレタイまたはスレNo.を入力するとここに候補が表示されます。"
}

internal fun buildPastThreadSearchEmptyMessage(): String {
    return "一致する過去スレが見つかりません"
}

internal fun buildPastThreadSearchNoticeTitle(): String {
    return "過去ログ検索の注意"
}

internal fun buildPastThreadSearchNoticeMessages(): List<String> {
    return listOf(
        "対応板は限られています。",
        "レス数100未満のスレは保存されません。",
        "保存時刻や反映時間は保証されません。",
        "見つからない場合は未保存または未対応の可能性があります。"
    )
}

internal fun archiveSearchItemToCatalogItem(item: ArchiveSearchItem): CatalogItem {
    return CatalogItem(
        id = item.threadId,
        threadUrl = item.htmlUrl,
        title = item.title,
        thumbnailUrl = item.thumbUrl,
        fullImageUrl = item.thumbUrl,
        replyCount = item.replyCount
    )
}
