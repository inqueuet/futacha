package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.ExperimentalTime

enum class BoardManagementMenuAction(val label: String) {
    ADD("新規追加"),
    REORDER("並び替え"),
    DELETE("削除"),
    SAVED_THREADS("保存済み"),
    SETTINGS("設定"),
}

val mockBoardSummaries = listOf(
    BoardSummary(
        id = "t",
        name = "チュートリアル＠ふたちゃ",
        category = "チュートリアル",
        url = "https://www.example.com/t/futaba.php",
        description = "チュートリアル",
        pinned = false
    ),
)

val mockThreadHistory = listOf(
    ThreadHistoryEntry(
        threadId = "354621",
        boardId = "t",
        title = "チュートリアル",
        titleImageUrl = "https://www.example.com/t/thumb/1762145224666s.jpg",
        boardName = "料理＠ふたば",
        boardUrl = "https://www.example.com/t/res/354621.htm",
        lastVisitedEpochMillis = dateMillis(2025, 11, 7, 19, 5),
        replyCount = 17
    )
)

@OptIn(ExperimentalTime::class)
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
