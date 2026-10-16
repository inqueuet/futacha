package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.ManageSearch
import com.valoser.futacha.shared.compat.CompatCatalogPreference
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatCatalogMatchedWords
import com.valoser.futacha.shared.compat.compatCatalogRulesForBoard
import com.valoser.futacha.shared.compat.compatWatchWordsForBoard
import com.valoser.futacha.shared.compat.projectCompatCatalogItems
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.model.normalizeWatchWords
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.buildCatalogExternalAppUrl
import com.valoser.futacha.shared.ui.board.buildVisibleCatalogItems
import com.valoser.futacha.shared.ui.board.loadCatalogItemsForMode
import com.valoser.futacha.shared.ui.compat.COMPAT_CACHE_SEARCH_HISTORY_KEY
import com.valoser.futacha.shared.ui.compat.CompatCatalogCacheSearchDialog
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.compat.compatCatalogWatchModeWords
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue
import com.valoser.futacha.shared.ui.compat.normalizeCompatCacheSearchHistory
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * ふたばー風 機能そろえ B7a（カタログ系）。どれも拡張機能のスイッチ（既定OFF）の後ろにあり、オフなら画面・操作は元のまま。
 * - C04 「そ順」「監視」：表示順序のメニューに2つ足す。監視の語は巡回のキーワード（compat.watcher.rules）と、その板の
 *   カタログ抽出ルール（CATALOG_EXTRACT）を合わせたもの（としあき(仮)の「監視」と同じ出所）。取得はふたちゃと同じ
 *   loadCatalogItemsForMode（新順・古順の2本）と buildVisibleCatalogItems。
 * - C07 監視ワードの強調・先頭へ：一致したスレに小さなバッジを付け、先頭へ寄せる（projectCompatCatalogItems）。
 * - C08 レス数による優先表示：他モードと共有の板ごとの設定（CompatCatalogPreference）と、共有のしきい値
 *   （compat.catalog.delayFewReplies）。
 * - C09 更新前のカタログを世代で選ぶ（メモリ上の4世代。保存しない）。
 * - C12 キャッシュサーバー検索（共有のダイアログ。Analyticsなし）、ブラウザでカタログを開く。
 */

private const val B7A_TAG = "FutaberParityB7a"

// ---- C04: "そ順" / "監視" ----

/** The two sorts the extension adds after the six of the original app. */
internal val futaberExtraCatalogSorts: List<FutaberCatalogSort> = listOf(
    FutaberCatalogSort("そ順", CatalogMode.So),
    FutaberCatalogSort("監視", CatalogMode.WatchWords)
)

/** The sort menu's entries: the original six, and with [extraModes] also "そ順" and "監視". */
internal fun futaberCatalogSortsFor(extraModes: Boolean): List<FutaberCatalogSort> =
    if (extraModes) futaberCatalogSorts + futaberExtraCatalogSorts else futaberCatalogSorts

internal fun futaberIsExtraCatalogMode(mode: CatalogMode): Boolean = mode == CatalogMode.So || mode == CatalogMode.WatchWords

/**
 * Where the watch words of a board come from: the patrol keywords that apply to it (every board, or this one) and the
 * board's catalog extraction rules. A board the shared store has no key for (the tutorial board) gets only the
 * keywords and rules made for every board.
 */
internal data class FutaberCatalogWatchSource(
    val patrolWords: List<String> = emptyList(),
    val extractRules: List<CompatNgRule> = emptyList()
) {
    /** The words of "監視", each once (the patrol's first). */
    val words: List<String> get() = compatCatalogWatchModeWords(patrolWords, extractRules)
    val isEmpty: Boolean get() = patrolWords.isEmpty() && extractRules.isEmpty()
}

internal fun futaberCatalogWatchSource(
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    board: BoardSummary
): FutaberCatalogWatchSource {
    val key = futaberNgScopeKey(board).orEmpty()
    return FutaberCatalogWatchSource(
        patrolWords = compatWatchWordsForBoard(preferences, key),
        extractRules = compatCatalogRulesForBoard(ngRules, key).filter { it.kind == CompatNgKind.CATALOG_EXTRACT }
    )
}

/**
 * One catalog fetch. Every sort but "監視" is the board's own catalog, as before; "監視" reads the new and the old
 * catalogs and keeps the threads whose titles hold one of [watchWords], best matches first (empty without words).
 */
internal suspend fun futaberFetchCatalogPage(
    repository: BoardRepository,
    boardUrl: String,
    mode: CatalogMode,
    watchWords: List<String>
): CatalogPageContent {
    if (mode != CatalogMode.WatchWords) return repository.getCatalogPage(boardUrl, mode)
    if (normalizeWatchWords(watchWords).isEmpty()) return CatalogPageContent(emptyList())
    val page = loadCatalogItemsForMode(boardUrl, CatalogMode.WatchWords, watchWords) { url, sourceMode ->
        repository.getCatalogPage(url, sourceMode)
    }
    return page.copy(
        items = buildVisibleCatalogItems(
            items = page.items,
            mode = CatalogMode.WatchWords,
            watchWords = watchWords,
            catalogNgWords = emptyList(),
            catalogNgFilteringEnabled = false,
            query = ""
        )
    )
}

/** What an empty "監視" says: no words at all, or no thread holds one. Null for the other sorts (the usual message). */
internal fun futaberWatchModeEmptyMessage(mode: CatalogMode, words: List<String>): String? = when {
    mode != CatalogMode.WatchWords -> null
    normalizeWatchWords(words).isEmpty() ->
        "監視ワードが登録されていません\n（設定→キーワード通知・巡回で登録したキーワードと、カタログの抽出ルールを使います）"
    else -> "監視ワードに一致するスレッドはありません"
}

// ---- C07 / C08: the catalog projection ----

/** How the catalog is reordered after the NG rules and before the search; null (or nothing on) leaves it as it is. */
internal data class FutaberCatalogProjection(
    /** C07: matching threads get a badge and come first. */
    val highlight: Boolean = false,
    val watch: FutaberCatalogWatchSource = FutaberCatalogWatchSource(),
    /** C08 is in force (the extension is on and the board's own setting was read). */
    val replyPriority: Boolean = false,
    /** The board's shared settings (CompatCatalogPreference) and the shared threshold. */
    val replyPriorityEnabled: Boolean = true,
    val showNonPriority: Boolean = true,
    val threshold: Int = 0,
    /** False while the board's setting is still being read: the catalog waits instead of flashing another order. */
    val ready: Boolean = true
)

/** The list as shown and the badge of each matching thread (thread id -> words). */
internal data class FutaberProjectedCatalog(
    val items: List<CatalogItem>,
    val badges: Map<String, String> = emptyMap()
)

/**
 * The last projection kept while [items] are matched again (re-audit M12): its threads that are still listed, in its order
 * and with its badges. Threads not in it yet come once the new projection is ready.
 */
internal fun futaberCarryProjection(previous: FutaberProjectedCatalog, items: List<CatalogItem>): FutaberProjectedCatalog {
    val listed = items.associateBy { it.id }
    return FutaberProjectedCatalog(
        previous.items.mapNotNull { listed[it.id] },
        previous.badges.filterKeys { it in listed }
    )
}

/** "語" or "語 +2": the first matching word (cut short) and how many more matched. */
internal fun futaberWatchBadgeLabel(words: List<String>): String {
    val first = words.firstOrNull()?.trim().orEmpty()
    if (first.isEmpty()) return ""
    val head = futaberSafeTake(first, FUTABER_WATCH_BADGE_MAX_CHARS)
    return if (words.size > 1) "$head +${words.size - 1}" else head
}

private const val FUTABER_WATCH_BADGE_MAX_CHARS = 8

internal fun futaberProjectCatalog(items: List<CatalogItem>, projection: FutaberCatalogProjection?): FutaberProjectedCatalog {
    if (projection == null || (!projection.highlight && !projection.replyPriority)) return FutaberProjectedCatalog(items)
    val matched: Map<String, List<String>> = if (projection.highlight && !projection.watch.isEmpty) {
        buildMap {
            items.forEach { item ->
                val words = compatCatalogMatchedWords(item, projection.watch.patrolWords, projection.watch.extractRules)
                if (words.isNotEmpty()) put(item.id, words)
            }
        }
    } else emptyMap()
    val ordered = projectCompatCatalogItems(
        items = items,
        replyPriorityEnabled = projection.replyPriority && projection.replyPriorityEnabled,
        replyThreshold = projection.threshold,
        showNonPriority = !projection.replyPriority || projection.showNonPriority,
        isExtracted = { it.id in matched }
    )
    return FutaberProjectedCatalog(ordered, matched.mapValues { futaberWatchBadgeLabel(it.value) })
}

/** The shared "レス数で優先ソート" (0〜30; the legacy title is read too, as としあき(仮) does). */
internal fun futaberReplyPriorityThreshold(preferences: Map<String, String>): Int =
    preferences.compatPreferenceValue("catalog", "delayFewReplies", "レス数で優先ソート")
        ?.filter(Char::isDigit)?.toIntOrNull()?.coerceIn(FUTABER_REPLY_PRIORITY_MIN, FUTABER_REPLY_PRIORITY_MAX) ?: 0

internal const val FUTABER_REPLY_PRIORITY_MIN = 0
internal const val FUTABER_REPLY_PRIORITY_MAX = 30

/** The small badge of a thread that holds a watch word, over the corner of its picture (outside the title line). */
@Composable
internal fun FutaberCatalogWatchBadge(label: String?, modifier: Modifier = Modifier) {
    if (label.isNullOrEmpty()) return
    val colors = LocalFutaberColors.current
    Text(
        label,
        color = colors.onAction,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(2.dp).clip(FutaberShapes.small).background(colors.action)
            .padding(horizontal = 4.dp, vertical = 1.dp).testTag("futaber-catalog-watch-badge")
    )
}

// ---- C09: going back several reloads ----

/** Goes back [generations] reloads at once (1 = the last one); null when the catalog does not keep that many. */
internal fun futaberCatalogGoBackTo(
    history: FutaberCatalogHistory,
    generations: Int
): Pair<List<CatalogItem>, FutaberCatalogHistory>? {
    if (generations < 1) return null
    val target = history.earlier.getOrNull(generations - 1) ?: return null
    // The threads of the list brought back are on screen again, so they are no longer "gone"; the lists skipped over
    // are dropped (as the list left is when going back one).
    val shownIds = target.mapTo(HashSet()) { it.id }
    return target to history.copy(
        earlier = history.earlier.drop(generations),
        dropped = history.dropped.filter { it.id !in shownIds }
    )
}

internal fun futaberCatalogGenerationLabel(generations: Int, threads: Int): String =
    "更新前のカタログ（${generations}回前 ${threads}スレ）"

/**
 * The going-back rows of the refresh button's sheet: one row as before, or (the extension) one row per kept catalog.
 * Neither runs while a reload is running (its result would replace the list just brought back). [history] is read when
 * a row is tapped.
 */
internal fun futaberCatalogBackActions(
    shownHistory: FutaberCatalogHistory,
    isLoading: Boolean,
    perGeneration: Boolean,
    history: () -> FutaberCatalogHistory,
    onClose: () -> Unit,
    onRestore: (List<CatalogItem>, FutaberCatalogHistory) -> Unit
): List<FutaberPostAction> {
    if (!perGeneration || shownHistory.earlier.isEmpty()) {
        return listOf(
            FutaberPostAction("catalog-back", "更新前のカタログに戻す", enabled = shownHistory.earlier.isNotEmpty() && !isLoading) {
                onClose()
                futaberCatalogGoBack(history())?.let { (items, rest) -> onRestore(items, rest) }
            }
        )
    }
    return shownHistory.earlier.mapIndexed { index, list ->
        val generations = index + 1
        FutaberPostAction("catalog-back-$generations", futaberCatalogGenerationLabel(generations, list.size), enabled = !isLoading) {
            onClose()
            futaberCatalogGoBackTo(history(), generations)?.let { (items, rest) -> onRestore(items, rest) }
        }
    }
}

/**
 * The thread menu's "キャッシュ検索", or null while it is not offered. A board without an official board key (the
 * bundled tutorial board) shows it disabled ([enabled] false), as Futacha offers the search on official boards only:
 * searching would send the board's address to the outside server.
 */
internal fun futaberCacheSearchMenuItem(open: (() -> Unit)?, enabled: Boolean, onDismiss: () -> Unit): FutaberMenuItem? =
    open?.let {
        FutaberMenuItem("cache-search", "キャッシュ検索", androidx.compose.material.icons.Icons.Outlined.ManageSearch, enabled = enabled) {
            if (enabled) { onDismiss(); it() }
        }
    }

/** True when the cache server search may run on [board]: only an official board (one with a shared board key). */
internal fun futaberCacheSearchUsable(board: BoardSummary?): Boolean = board != null && futaberNgScopeKey(board) != null

// ---- The catalog's extensions, made once by the mode ----

/** What the catalog and the thread get from the B7a extensions. */
@Stable
internal class FutaberCatalogExtras(
    val extraSorts: Boolean,
    val perGenerationBack: Boolean,
    val projection: FutaberCatalogProjection?,
    /** The words of "監視" (the sort fetches with them). */
    val watchWords: List<String>,
    /** Rows added to the refresh button's sheet (before "キャンセル"). */
    val refreshActions: List<FutaberPostAction>,
    /** The thread menu's "キャッシュ検索"; null while the extension is off or the shared store is missing. */
    val openCacheSearch: (() -> Unit)?,
    /** False on a board that is not an official one (the tutorial board): the entries are shown disabled. */
    val cacheSearchEnabled: Boolean,
    internal val cacheSearchState: androidx.compose.runtime.MutableState<Boolean>
)

@Composable
internal fun rememberFutaberCatalogExtras(
    board: BoardSummary?,
    sortMode: CatalogMode,
    display: FutaberDisplaySettings,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    boards: List<BoardSummary>,
    store: CompatibilityStore?,
    openInBrowser: (String) -> Unit,
    notify: (String) -> Unit
): FutaberCatalogExtras {
    val scope = rememberCoroutineScope()
    val cacheSearch = remember { mutableStateOf(false) }
    val needsWatch = display.extCatalogModes || display.extCatalogWatchHighlight
    val watch = remember(needsWatch, preferences, ngRules, board) {
        if (needsWatch && board != null) futaberCatalogWatchSource(preferences, ngRules, board) else FutaberCatalogWatchSource()
    }
    // C08: the board's own reply-priority setting, shared with the other modes (only an official board has one).
    val scopeKey = board?.let(::futaberNgScopeKey)
    val needsPreference = display.extReplyPriority && scopeKey != null && store != null
    var preference by remember(scopeKey) { mutableStateOf<CompatCatalogPreference?>(null) }
    LaunchedEffect(scopeKey, needsPreference) {
        if (!needsPreference || scopeKey == null || store == null || preference != null) return@LaunchedEffect
        preference = try {
            store.loadCatalogPreference(scopeKey)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(B7A_TAG, "Failed to read the catalog preference", error)
            CompatCatalogPreference(scopeKey)
        }
    }
    val loaded = preference
    val projection = if (!display.extCatalogWatchHighlight && !display.extReplyPriority) null else FutaberCatalogProjection(
        highlight = display.extCatalogWatchHighlight,
        watch = if (display.extCatalogWatchHighlight) watch else FutaberCatalogWatchSource(),
        replyPriority = needsPreference && loaded != null,
        replyPriorityEnabled = loaded?.replyPriorityEnabled ?: true,
        showNonPriority = loaded?.showNonPriority ?: true,
        threshold = display.replyPriorityThreshold,
        ready = !needsPreference || loaded != null
    )
    val savePreference: (CompatCatalogPreference) -> Unit = { next ->
        preference = next
        if (store != null) scope.launch {
            try {
                // The setting belongs to the board in the shared store, which learns of this mode's boards here.
                store.importModernBoards(boards)
                store.saveCatalogPreference(next)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(B7A_TAG, "Failed to save the catalog preference", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    val refreshActions = buildList {
        if (display.extReplyPriority) {
            val current = loaded
            val usable = needsPreference && current != null
            val on = current?.replyPriorityEnabled ?: false
            add(FutaberPostAction(
                "reply-priority",
                if (usable && on) "レス数による優先表示をオフにする" else "レス数による優先表示をオンにする",
                enabled = usable
            ) { current?.let { savePreference(it.copy(replyPriorityEnabled = !it.replyPriorityEnabled)) } })
            val hiding = current?.showNonPriority == false
            add(FutaberPostAction(
                "reply-priority-hide",
                if (usable && hiding) "少ないレス（${display.replyPriorityThreshold}未満）を表示する"
                else "少ないレス（${display.replyPriorityThreshold}未満）を隠す",
                enabled = usable && on && display.replyPriorityThreshold > 0
            ) { current?.let { savePreference(it.copy(showNonPriority = !it.showNonPriority)) } })
        }
        if (display.extCatalogTools && board != null) {
            // Official boards only, as in Futacha: the search sends the board's address to the outside server.
            add(FutaberPostAction("cache-search", "キャッシュサーバー検索", enabled = store != null && scopeKey != null) {
                if (scopeKey != null) cacheSearch.value = true
            })
            add(FutaberPostAction("catalog-browser", "ブラウザでカタログを開く") {
                openInBrowser(buildCatalogExternalAppUrl(board.url, sortMode))
            })
        }
    }
    return FutaberCatalogExtras(
        extraSorts = display.extCatalogModes,
        perGenerationBack = display.extCatalogHistory,
        projection = projection,
        watchWords = if (display.extCatalogModes) watch.words else emptyList(),
        refreshActions = refreshActions,
        openCacheSearch = if (display.extCatalogTools && store != null && board != null) ({ if (scopeKey != null) cacheSearch.value = true }) else null,
        cacheSearchEnabled = scopeKey != null,
        cacheSearchState = cacheSearch
    )
}

/** The cache server search (the shared dialog of the other modes) over the catalog or the thread. */
@Composable
internal fun FutaberCatalogExtrasHost(
    extras: FutaberCatalogExtras,
    board: BoardSummary?,
    services: FutaberMediaServices?,
    preferences: Map<String, String>,
    onOpenThread: (BoardSummary, CatalogItem) -> Unit
) {
    if (!extras.cacheSearchState.value) return
    val target = board
    val shared = services
    // Official boards only (the search sends the board's address to the outside server).
    val boardKey = target?.let(::futaberNgScopeKey)
    if (target == null || shared == null || boardKey == null) {
        // Nothing to search on (the board went away): the request is dropped, not kept for later.
        LaunchedEffect(Unit) { extras.cacheSearchState.value = false }
        return
    }
    val scope = rememberCoroutineScope()
    val colors = LocalFutaberColors.current
    val palette = remember(colors) { futaberMediaPalette(colors) }
    val history by shared.store.history.collectAsState(emptyList())
    val close = { extras.cacheSearchState.value = false }
    FutachaAppLockAwareWindow {
        CompositionLocalProvider(LocalCompatibilityPalette provides palette) {
            CompatCatalogCacheSearchDialog(
                httpClient = shared.httpClient,
                store = shared.store,
                boardKey = boardKey,
                boardUrl = futaberCanonicalBoardUrl(target),
                localHistory = history,
                initialSearchHistory = normalizeCompatCacheSearchHistory(preferences[COMPAT_CACHE_SEARCH_HISTORY_KEY].orEmpty()),
                onSearchHistoryChanged = { values ->
                    scope.launch {
                        try {
                            shared.store.savePreference(COMPAT_CACHE_SEARCH_HISTORY_KEY, values.joinToString("\n"))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Logger.e(B7A_TAG, "Failed to save the cache search history", error)
                        }
                    }
                },
                onDismiss = close,
                onOpenThread = { item -> close(); onOpenThread(target, item) }
            )
        }
    }
}

// ---- 7: the watch words of the alert (AppState, every board) ----

/** The hint of the alert's watch-word list; [boardsWithOwnWords] boards keep their own list (edited in ふたちゃ). */
internal fun futaberWatchWordsHint(boardsWithOwnWords: Int): String = buildString {
    append("「監視ワード自動アラート」（設定→動作）がオンの時、定期的に登録板のカタログを確かめ、題名にこの語句を含む新しいスレッドを通知します。ふたちゃと共通の一覧です。")
    if (boardsWithOwnWords > 0) append("\n板ごとの監視ワードがある板（${boardsWithOwnWords}板）では、その板の語句が使われます（ふたちゃで編集できます）。")
}
