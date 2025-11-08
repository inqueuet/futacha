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
        id = "t",
        name = "料理＠ふたば",
        category = "食文化 / 雑談",
        url = "https://dat.2chan.net/t/futaba.php",
        description = "調理や食材談義、ローカルルール共有まで行われる本家料理板。スレごとに削除ルールが明確です。",
        pinned = true
    ),
    BoardSummary(
        id = "may",
        name = "株板＠may",
        category = "市況・投資",
        url = "https://may.2chan.net/b/futaba.php",
        description = "日経平均や個別銘柄を実況する株クラの拠点。そうだね文化と画像付きチャートが特徴。",
        pinned = false
    ),
    BoardSummary(
        id = "pink",
        name = "政治と芸能板",
        category = "時事・カルチャー",
        url = "https://may.2chan.net/b/res/353429219.htm",
        description = "政治スキャンダルと芸能ニュースをポジティブに語る実況スレ。雑談＋まとめ画像が流れます。",
        pinned = false
    )
)

val mockThreadHistory = listOf(
    ThreadHistoryEntry(
        threadId = "354621",
        boardId = "t",
        title = "料理板利用時のルール",
        titleImageUrl = "https://dat.2chan.net/t/thumb/1762145224666s.jpg",
        boardName = "料理＠ふたば",
        boardUrl = "https://dat.2chan.net/t/res/354621.htm",
        lastVisitedEpochMillis = dateMillis(2025, 11, 7, 19, 5),
        replyCount = 17
    ),
    ThreadHistoryEntry(
        threadId = "354711",
        boardId = "may",
        title = "株スレ観測",
        titleImageUrl = "https://dat.2chan.net/t/cat/1762436883775s.jpg",
        boardName = "株板＠may",
        boardUrl = "https://dat.2chan.net/t/res/354711.htm",
        lastVisitedEpochMillis = dateMillis(2025, 11, 7, 19, 10),
        replyCount = 1
    ),
    ThreadHistoryEntry(
        threadId = "354693",
        boardId = "pink",
        title = "政治と芸能の専門板",
        titleImageUrl = "https://dat.2chan.net/t/cat/1762427960314s.jpg",
        boardName = "政治と芸能板",
        boardUrl = "https://dat.2chan.net/t/res/354693.htm",
        lastVisitedEpochMillis = dateMillis(2025, 11, 7, 19, 30),
        replyCount = 2
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
