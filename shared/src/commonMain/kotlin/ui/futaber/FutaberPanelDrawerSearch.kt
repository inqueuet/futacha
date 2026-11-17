package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.model.BoardSummary
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// L-B10: the manage panel, the board list and the catalog search as futaber has them (look doc §1.3 S24・M29・M31・M32・M50・
// V22・V35・G25・G36・S21・S04・S05・V31・S27・S25・V30・V29・M52). The rules are here as plain functions, the screens use them.

// ---- S24: the panel opens on the category chosen last, also after a restart ----

/** The stored form of a category (its name); anything else reads as the history, where the panel opened before L-B10. */
internal val FutaberManageCategory.persisted: String get() = name

internal fun futaberManageCategoryFromStored(value: String?): FutaberManageCategory =
    FutaberManageCategory.entries.firstOrNull { it.name == value?.trim() } ?: FutaberManageCategory.History

// ---- M29・M50: the heading of each category ----

/** The panel's heading: the title in the middle, the word at the right and, while editing, 「すべて削除」 at the left. */
internal data class FutaberManageHeader(
    val title: String,
    /** 「すべて削除」 or null; drawn even with nothing listed (futaber does), then it cannot be pressed. */
    val leftLabel: String?,
    val leftEnabled: Boolean,
    /** 「編集」／「選択」 and 「完了」 while editing; null for the tabs, which only have 「すべて削除」. */
    val rightLabel: String?
)

/**
 * futaber's heading: the history and the notices are edited with 「編集」, the saved box and the favourites with 「選択」;
 * both turn into 「完了」 with 「すべて削除」 at the left, also with nothing listed. The tabs have 「すべて削除」 only (while
 * something is listed, as before). The saved box is titled 「MHTファイル」, as futaber's.
 */
internal fun futaberManageHeader(category: FutaberManageCategory, editing: Boolean, itemCount: Int): FutaberManageHeader {
    val title = when (category) {
        FutaberManageCategory.SavedBox -> "MHTファイル"
        else -> category.title
    }
    if (category == FutaberManageCategory.Tabs) {
        return FutaberManageHeader(title, if (itemCount > 0) "すべて削除" else null, itemCount > 0, null)
    }
    val word = when (category) {
        FutaberManageCategory.SavedBox, FutaberManageCategory.Favorites -> "選択"
        else -> "編集"
    }
    return FutaberManageHeader(
        title = title,
        leftLabel = if (editing) "すべて削除" else null,
        leftEnabled = editing && itemCount > 0,
        rightLabel = if (editing) "完了" else word
    )
}

/** A row opens its thread only outside the edit (and choose) mode (M29). */
internal fun futaberManageRowOpens(editing: Boolean, openable: Boolean): Boolean = !editing && openable

/** What 「すべて削除」 asks before anything goes (futaber's confirmation is not recorded; this one asks first, to be safe). */
internal fun futaberManageClearMessage(category: FutaberManageCategory, mhtCount: Int = 0, savedCount: Int = 0, count: Int = 0): String =
    when (category) {
        FutaberManageCategory.SavedBox -> buildString {
            append("保存箱から")
            val parts = buildList {
                if (mhtCount > 0) add("MHTファイル${mhtCount}件")
                if (savedCount > 0) add("保存したスレッド${savedCount}件")
            }
            append(parts.ifEmpty { listOf("すべて") }.joinToString("と"))
            append("を削除します。元に戻せません。")
        }
        FutaberManageCategory.Notifications -> "通知の${count}件を削除します。キーワードは消えません。"
        FutaberManageCategory.Favorites -> "お気に入りの${count}件を外します。スレッドの履歴とタブは残ります。"
        FutaberManageCategory.History -> "閲覧履歴をすべて削除します。"
        FutaberManageCategory.Tabs -> "タブをすべて閉じます。"
    }

// ---- M31・S21・§2.10: what this app adds after futaber's list (one function, F-B2 puts it behind 「その他の機能」) ----

/** The panel's own additions, drawn after futaber's list of each category. */
internal enum class FutaberManageExtra(val tag: String) {
    /** The patrol's state (自動巡回が無効／キーワード未登録…), a small line. */
    NotificationsStatus("futaber-notifications-status"),
    /** 「キーワード管理」 (futaber keeps it in the settings; here a small row at the end of the notices). */
    KeywordManager("futaber-watch-manage"),
    /** The other modes' starred threads (read only, an extension). */
    OtherFavorites("futaber-other-favorites-header"),
    /** The threads saved as HTML (this app's save; futaber has the MHT files only). */
    HtmlSaves("saved-header"),
    /** 「ファイルを開く」: an MHT file from outside. */
    MhtImport("futaber-mht-import")
}

/**
 * The additions of [category], in their order, after futaber's list. The history band (search, sort, 一括更新, archive)
 * stays above the list: it filters the list, and the features batch decides where it folds (look doc §2.10).
 */
internal fun futaberManagePanelExtras(
    category: FutaberManageCategory,
    notificationsAvailable: Boolean = true,
    otherFavorites: Boolean = false,
    htmlSaves: Boolean = false,
    mhtAvailable: Boolean = false
): List<FutaberManageExtra> = when (category) {
    FutaberManageCategory.Notifications ->
        if (notificationsAvailable) listOf(FutaberManageExtra.NotificationsStatus, FutaberManageExtra.KeywordManager) else emptyList()
    FutaberManageCategory.Favorites -> if (otherFavorites) listOf(FutaberManageExtra.OtherFavorites) else emptyList()
    FutaberManageCategory.SavedBox -> buildList {
        if (htmlSaves) add(FutaberManageExtra.HtmlSaves)
        if (mhtAvailable) add(FutaberManageExtra.MhtImport)
    }
    FutaberManageCategory.History, FutaberManageCategory.Tabs -> emptyList()
}

/** S21: futaber's line over the MHT files, "0KB 0項目の合計" (a note replaces it while a file is being read or taken in). */
internal fun futaberMhtTotalLine(totalBytes: Long, count: Int, busyMessage: String? = null): String =
    busyMessage ?: "${futaberPanelSize(totalBytes)} ${count}項目の合計"

/** Sizes as futaber writes them in the saved box: whole KB under a megabyte ("0KB", "154KB"), else MB with one decimal. */
internal fun futaberPanelSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0)
    return if (safe < 1024L * 1024L) "${safe / 1024}KB" else {
        val tenths = safe * 10 / (1024L * 1024L)
        "${tenths / 10}.${tenths % 10}MB"
    }
}

// ---- V35: the date of a row ----

/** futaber's date in the panel, "26/10/31 01:08" (two-digit year). The shared [formatLastVisited] stays as the other modes use it. */
@OptIn(ExperimentalTime::class)
internal fun futaberPanelDate(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    fun two(value: Int) = value.toString().padStart(2, '0')
    return "${two(time.year % 100)}/${two(time.month.ordinal + 1)}/${two(time.day)} ${two(time.hour)}:${two(time.minute)}"
}

// ---- S04・S05: the board list's edit ----

/** This app's additions in the board list's edit footer, after futaber's 「完了」 and 「板の追加」. */
internal enum class FutaberDrawerEditExtra(val label: String, val tag: String) {
    BulkAdd("板一覧から一括追加", "futaber-board-bulk-add"),
    Rename("名前を変更", "futaber-board-rename")
}

/** The footer's additions: the bulk add (when the official list can be fetched) and the rename (when there is a board). */
internal fun futaberBoardDrawerEditExtras(
    bulkAvailable: Boolean,
    hasBoards: Boolean
): List<FutaberDrawerEditExtra> = buildList {
    if (bulkAvailable) add(FutaberDrawerEditExtra.BulkAdd)
    if (hasBoards) add(FutaberDrawerEditExtra.Rename)
}

/** What the board-add alert's field holds when it opens (futaber's). */
internal const val FUTABER_ADD_BOARD_PREFILL = "https://"

/**
 * Why 「OK」 adds nothing, or null when the address can be added (named after it, as an add without a name always was).
 * futaber's OK with only 「https://」 does nothing at all; here the alert stays and says why (look doc §1.6).
 */
internal fun futaberAddBoardAlertReason(input: String, existing: List<BoardSummary>): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed == "https://" || trimmed == "http://") return "板のアドレスを入力してください"
    when (val aimoge = com.valoser.futacha.shared.ui.aimoge.classifyAimogeBoardUrlInput(trimmed)) {
        com.valoser.futacha.shared.ui.aimoge.AimogeBoardUrlInput.NotAimoge -> Unit
        com.valoser.futacha.shared.ui.aimoge.AimogeBoardUrlInput.SelectBoards -> return null
        is com.valoser.futacha.shared.ui.aimoge.AimogeBoardUrlInput.Refused ->
            return aimoge.message
        is com.valoser.futacha.shared.ui.aimoge.AimogeBoardUrlInput.Board ->
            return if (existing.any { com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(it.url) == aimoge.url }) {
                "同じURLの板が既に登録されています"
            } else null
    }
    val state = futaberAddBoardState("", trimmed, existing)
    return if (state.canSubmit) null else state.helperText ?: "この板は追加できません"
}

/**
 * Where a board dragged by its handle lands: [from] moved by whole rows of [rowPx] for a drag of [dy] px (a row is
 * passed at half its height), kept inside the list.
 */
internal fun futaberDragTargetIndex(from: Int, dy: Float, rowPx: Float, count: Int): Int {
    if (count <= 0 || rowPx <= 0f) return from
    val steps = if (dy >= 0f) ((dy + rowPx / 2f) / rowPx).toInt() else -((-dy + rowPx / 2f) / rowPx).toInt()
    return (from + steps).coerceIn(0, count - 1)
}

/** V31: the pushed-aside screen is dimmed this much while the board list is open (futaber's white 255 → 245). */
internal const val FUTABER_DRAWER_DIM_ALPHA = 0.04f

// ---- S25・V29: the catalog search and the menus ----

/** S25: with the search open and nothing typed, futaber lists nothing (the list comes back as a word is typed). */
internal fun futaberCatalogSearchShowsNothing(searchOpen: Boolean, query: String): Boolean = searchOpen && query.isEmpty()

/** V29: the popover's labels with full-width digits, as futaber writes them (「行１」「横４」). The stored values stay. */
internal fun futaberPopoverLabel(label: String): String =
    label.map { c -> if (c in '0'..'9') (c.code - '0'.code + '０'.code).toChar() else c }.joinToString("")

// ---- An alert or a sheet over the whole screen, asked for by a part drawn inside a smaller area ----

/**
 * One alert or sheet drawn over the whole screen by [FutaberScreenOverlayHost] (the panel and the board list are drawn
 * inside a part of the screen; [FutaberAlert] and [FutaberActionSheet] want the whole of it).
 */
@Stable
internal class FutaberScreenOverlay {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set
    private var shown by mutableIntStateOf(0)
    val id: Int get() = shown

    fun show(next: @Composable () -> Unit) {
        shown += 1
        content = next
    }

    fun dismiss() {
        content = null
    }
}

/** Draws what [overlay] holds; each request starts with fresh state. */
@Composable
internal fun FutaberScreenOverlayHost(overlay: FutaberScreenOverlay) {
    val content = overlay.content ?: return
    key(overlay.id) { content() }
}
