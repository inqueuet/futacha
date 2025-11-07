package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

enum class BoardManagementMenuAction(val label: String) {
    LIST("板一覧"),
    ADD("新規追加"),
    REORDER("並び替え"),
    DELETE("削除"),
    SETTINGS("設定"),
    HELP("ヘルプ")
}

val mockBoardSummaries = listOf(
    BoardSummary(
        id = "nijura",
        name = "Mock Hub α",
        category = "本スレ風",
        url = "https://alpha.example.com/futaba.php",
        description = "UI プロトタイプ用の汎用板。トップのモックカタログにリンクします。",
        pinned = true
    )
)

val mockThreadHistory = listOf(
    ThreadHistoryEntry(
        threadId = "354621",
        title = "Compose試作メモ",
        titleImageUrl = "https://picsum.photos/seed/compose-memo/200/200",
        boardName = "Mock Hub α",
        boardUrl = "https://alpha.example.com/res/354621.htm",
        lastVisitedEpochMillis = dateMillis(2025, 10, 22, 10, 28),
        replyCount = 128
    )
)

private fun dateMillis(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int
): Long {
    val dateTime = LocalDateTime(year, month, day, hour, minute, 0, 0)
    return dateTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
}
