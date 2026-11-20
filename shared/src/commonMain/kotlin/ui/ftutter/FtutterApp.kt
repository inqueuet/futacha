package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.shownSiteTabs
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.appliesToThreadImage
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.GlobalSettingsModeSection
import com.valoser.futacha.shared.ui.board.statusCodeOrNull
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.ui.futaber.FutaberAiInbox
import com.valoser.futacha.shared.ui.futaber.FutaberMediaServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.valoser.futacha.shared.ui.isMockBoard

/** 画面の行き先。段階ごとに増やす。 */
internal sealed interface FtutterScreen {
    data object Boards : FtutterScreen
    data class Catalog(val boardId: String) : FtutterScreen
    data class Thread(val boardId: String, val ref: FtutterThreadRef, val back: FtutterScreen) : FtutterScreen
    data class HiddenAndNg(val back: FtutterScreen) : FtutterScreen
    data class Settings(val back: FtutterScreen) : FtutterScreen
}

/**
 * The slide of a screen change: boards → catalog → thread → settings go deeper. In two panes the catalog and the thread are
 * one screen (they are drawn by the same call; re-audit L27), and the MIX does not slide. One id for every thread and every
 * catalog: another thread (a tab) or board keeps the screen as before, without a slide.
 */
internal fun ftutterTransitionScreen(screen: FtutterScreen, twoPane: Boolean, mixOpen: Boolean): com.valoser.futacha.shared.ui.PushPopScreen<FtutterScreen> =
    when (screen) {
        FtutterScreen.Boards -> com.valoser.futacha.shared.ui.PushPopScreen(if (mixOpen) "mix" else "boards", if (mixOpen) -1 else 0, screen)
        is FtutterScreen.Catalog ->
            if (twoPane) com.valoser.futacha.shared.ui.PushPopScreen("two-pane", 1, screen)
            else com.valoser.futacha.shared.ui.PushPopScreen("catalog", 1, screen)
        is FtutterScreen.Thread ->
            if (twoPane) com.valoser.futacha.shared.ui.PushPopScreen("two-pane", 1, screen)
            else com.valoser.futacha.shared.ui.PushPopScreen("thread", 2, screen)
        is FtutterScreen.HiddenAndNg -> com.valoser.futacha.shared.ui.PushPopScreen("hidden-ng", 3, screen)
        is FtutterScreen.Settings -> com.valoser.futacha.shared.ui.PushPopScreen("settings", 3, screen)
    }

internal const val FTUTTER_HANDWRITING_COPY_FAILURE = "手書き画像のコピーを保存できませんでした（添付はそのままです）"

/** AI 操作はこのモードでは動かさない。答えにはこのモードの名前を出す（L120。以前はふたばー風の文を流用していた）。 */
internal fun ftutterAiUnsupportedNotice(command: com.valoser.futacha.shared.ai.FutachaAiCommand): String =
    "「${command.action.label}」はふたったー風モードでは使えません。ふたちゃモードで実行してください"

/**
 * ふたったー風モードの画面の根。
 *
 * 第3段階まで：板一覧・カタログ・スレッド。タブ・書き込み等は後の段階で足す
 * （docs/ftutter-mode-implementation-plan-2026-10-13.md）。
 */
@Composable
internal fun FtutterApp(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    repository: BoardRepository,
    stateStore: AppStateStore,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    mediaServices: FutaberMediaServices?,
    autoSavedThreadRepository: SavedThreadRepository?,
    appVersion: String,
    initialThreadDeepLink: String?,
    onThreadDeepLinkConsumed: (String) -> Unit,
    initialBoardDeepLink: String?,
    onBoardDeepLinkConsumed: (String) -> Unit,
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)?,
    externalNotice: String?,
    onExternalNoticeShown: () -> Unit,
    onArchiveReportEnqueued: ((Int) -> Unit)?,
    onArchiveReportEnabledChanged: (Boolean) -> Unit,
    aiInbox: FutaberAiInbox?,
    mediaHost: @Composable (@Composable () -> Unit) -> Unit,
    onRefreshHistory: suspend () -> Unit,
    savePreference: (String, String) -> Unit,
    /** 保存されている値に変更を当てる（監査M2）。変換が null なら書かない。 */
    editPreference: (String, (String?) -> String?) -> Unit,
    /** Writes an import's settings in turn with the other writes (re-audit L28); null writes them directly as before. */
    importPreferences: (suspend (Map<String, (String?) -> String?>) -> Unit)? = null
) {
    val design = remember(preferences) { parseFtutterDesign(preferences) }
    var screen by remember { mutableStateOf<FtutterScreen>(FtutterScreen.Boards) }
    // 同じ文の通知が続いても、出し直して時間を数え直す（監査L7：同じ文字列では前の4秒のまま消えていた）。
    val noticeHolder = remember { FtutterNoticeHolder() }
    var notice by noticeHolder
    // 保存の入口：1つの設定の上限（2万字）を超える値は送らず、保存できなかったことを知らせる（監査A9。以前はストアの
    // 例外がログに出るだけで、「登録しました」と出たまま保存されていなかった）。このモードの保存はすべてここを通る。
    val storeSave = savePreference
    @Suppress("NAME_SHADOWING")
    val savePreference: (String, String) -> Unit = { key, value ->
        if (com.valoser.futacha.shared.compat.isValidCompatPreference(key, value)) storeSave(key, value) else notice = FTUTTER_SAVE_REFUSED_NOTICE
    }
    val storeEdit = editPreference
    @Suppress("NAME_SHADOWING")
    val editPreference: (String, (String?) -> String?) -> Unit = { key, transform ->
        storeEdit(key) { stored ->
            transform(stored)?.let { value ->
                if (com.valoser.futacha.shared.compat.isValidCompatPreference(key, value)) value else {
                    notice = FTUTTER_SAVE_REFUSED_NOTICE
                    null
                }
            }
        }
    }

    // AI commands: this stage runs none of them, so every one is answered (left alone they would pile up and replay).
    val currentInbox by rememberUpdatedState(aiInbox)
    DisposableEffect(aiInbox) {
        currentInbox?.runner = { command -> notice = ftutterAiUnsupportedNotice(command) }
        onDispose { currentInbox?.runner = null }
    }
    LaunchedEffect(externalNotice) {
        val text = externalNotice ?: return@LaunchedEffect
        notice = text
        onExternalNoticeShown()
    }
    LaunchedEffect(noticeHolder.serial) {
        if (notice != null) {
            delay(4_000)
            notice = null
        }
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val openUrl = rememberFtutterUrlLauncher(preferences)
    val catalogHolder = remember { FtutterCatalogHolder() }
    val threadMemory = remember { FtutterThreadMemory() }
    // スレ画面・タブ帯より長く持つもの（再監査M3）：送信・過去ログ取得の受け皿と、タブ帯の位置。
    val threadActions = remember(scope) { FtutterThreadActions(scope) }
    // タブと対になる履歴の書き込みは、すぐに始めて取り消されない形で最後まで書く（L152：書き込みの途中で画面が作り直される・
    // モードを切り替えると[scope]と一緒に取り消され、履歴の行が入らないまま、次にこのモードを開いた時の整理でそのタブが
    // 「履歴に無いタブ」として閉じられた）。
    fun launchLasting(block: suspend () -> Unit) {
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { block() }
        }
    }
    // Plan B7a: the tab bar is a view of the shared tabs (their order under this mode's mark, the ★ as protection). This
    // mode's old rows (`compat.ftutter.tabs` / `protected`) are only read for the threads with no shared place and the
    // tabs the migration stage has not taken in yet.
    val tabStore = mediaServices?.store
    val sharedTabSource = remember(tabStore) { tabStore?.tabs?.shownSiteTabs() ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val sharedTabs by sharedTabSource.collectAsState(emptyList())
    val tabViewSource = remember(tabStore) { tabStore?.tabViews ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val allTabViews by tabViewSource.collectAsState(emptyList())
    val migrationsSettled by (tabStore?.sharedMigrationsSettled ?: remember { kotlinx.coroutines.flow.MutableStateFlow(true) }).collectAsState()
    var consumedTabs by remember(tabStore) { mutableStateOf<Set<String>?>(if (tabStore == null) emptySet() else null) }
    LaunchedEffect(tabStore, migrationsSettled, preferences[FtutterKeys.TABS]) {
        val store = tabStore ?: return@LaunchedEffect
        if (!migrationsSettled) return@LaunchedEffect
        try {
            val data = store.loadSharedData()
            consumedTabs = ftutterConsumedTabs(data)
            data[FTUTTER_TABS_UNSTARRED_KEY]?.toIntOrNull()?.takeIf { it > 0 }?.let { count ->
                store.saveSharedData(mapOf(FTUTTER_TABS_UNSTARRED_KEY to null))
                notice = ftutterUnstarredNotice(count)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to read the tab migration record", error)
        }
    }
    val tabSnapshot = remember(sharedTabs, allTabViews, preferences[FtutterKeys.TABS], preferences[FtutterKeys.PROTECTED], consumedTabs) {
        ftutterTabSnapshot(sharedTabs, allTabViews, parseFtutterTabs(preferences).urls, parseFtutterProtected(preferences), consumedTabs)
    }
    val latestHistoryForTabs by rememberUpdatedState(history)
    val latestBoardsState = rememberUpdatedState(boards)
    val tabWriter = remember(tabStore) {
        FtutterStoreTabWriter(
            store = tabStore,
            boards = { latestBoardsState.value },
            history = { latestHistoryForTabs },
            editRow = { key, transform ->
                editPreference(key) { stored -> encodeFtutterList(transform(decodeFtutterList(stored).orEmpty())) }
            },
            notice = { notice = it }
        )
    }
    val tabState = remember(tabWriter) {
        FtutterTabState(tabSnapshot, preferences[FtutterKeys.OPENING]?.let(::ftutterCanonicalThreadUrl).orEmpty(), tabWriter) { block ->
            launchLasting {
                try {
                    block()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to write the tabs", error)
                    notice = "タブを保存できませんでした"
                }
            }
        }
    }
    LaunchedEffect(tabState, tabSnapshot) { tabState.sync(tabSnapshot) }
    // Every write done: show the stores' newest snapshot a little later (the write's own snapshot may not have come yet).
    LaunchedEffect(tabState, tabState.settledWrites) {
        if (tabState.settledWrites == 0) return@LaunchedEffect
        delay(300)
        tabState.showLatest()
    }
    // The open tab is this mode's own row (`compat.ftutter.opening`): it is not a shared tab's property.
    val heldOpening = tabState.opening
    LaunchedEffect(tabState, heldOpening) {
        if (heldOpening.isNotEmpty() && preferences[FtutterKeys.OPENING] != heldOpening) savePreference(FtutterKeys.OPENING, heldOpening)
    }
    // B4: the shown tab is held from the shared tab trim while this mode is on screen.
    DisposableEffect(tabStore, heldOpening) {
        val store = tabStore
        val key = com.valoser.futacha.shared.compat.canonicalizeThreadUrl(heldOpening)
            ?.let { com.valoser.futacha.shared.compat.compatTabKey(it.canonicalUrl) }
        store?.setScreenHeldTabs(com.valoser.futacha.shared.compat.ExperienceProfile.FTUTTER, setOfNotNull(key))
        onDispose { store?.setScreenHeldTabs(com.valoser.futacha.shared.compat.ExperienceProfile.FTUTTER, emptySet()) }
    }
    val tabStripState = androidx.compose.foundation.lazy.rememberLazyListState()
    val mockRepository = remember { com.valoser.futacha.shared.repo.mock.FakeBoardRepository() }
    // スレッドは、ふたちゃ・ふたばー風と同じ共有の層を通す（キャッシュサーバーの設定、長いスレッドの欠けの補完）。
    val threadRepository = remember(repository, mediaServices?.store, mediaServices?.httpClient) {
        mediaServices?.let { com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository(repository, it.store, it.httpClient) } ?: repository
    }
    val httpClient = mediaServices?.httpClient
    // 「板一覧から一括追加」：公式の板一覧（ふたちゃ・ふたばー風と同じ取得）から、未登録の板を足す。
    val bulkAddBoards: (suspend () -> Result<Int>)? = httpClient?.let { client ->
        {
            try {
                val discovered = com.valoser.futacha.shared.ui.compat.fetchDefaultCompatBoardsFromMenu(
                    client, com.valoser.futacha.shared.compat.modernBoardsToCompatibility(boards)
                ).getOrThrow()
                var added = 0
                stateStore.updateBoards { current ->
                    val next = com.valoser.futacha.shared.ui.futaber.futaberAddDiscoveredBoards(current, discovered.map { it.name to it.originalUrl })
                    added = next.size - current.size
                    next
                }
                Result.success(added)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }
    val editBoards: ((List<BoardSummary>) -> List<BoardSummary>) -> Unit = { transform ->
        scope.launch { stateStore.updateBoards(transform) }
    }
    var mixId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(com.valoser.futacha.shared.compat.MixAvailability.enabled) {
        if (!com.valoser.futacha.shared.compat.MixAvailability.enabled) mixId = null
    }
    var lastCatalogBoardId by remember { mutableStateOf(preferences[FtutterKeys.LAST_BOARD].orEmpty()) }
    // Whether the screen is drawn in two panes now (set by the layout; audit C8).
    var twoPaneNow by remember { mutableStateOf(false) }
    fun openCatalog(board: BoardSummary, tabMove: Boolean = false) {
        mixId = null
        catalogHolder.openBoard(board.id, tabMove,
            preferences.ftutterBool(FtutterKeys.REFRESH_ON_TAB_MOVE_CATALOG, false))
        savePreference(FtutterKeys.LAST_BOARD, board.id)
        // Two panes with this board's open tab beside the catalog: the catalog opening would put that same thread back
        // at once (M21), so the thread pane is kept instead of being closed and built again (audit C8).
        val shown = screen
        if (shown is FtutterScreen.Thread && ftutterTwoPaneKeepsThread(twoPaneNow,
                preferences.ftutterBool(FTUTTER_LEGACY_TWO_PANE_EMPTY_KEY, false), shown.boardId, board.id,
                ftutterTwoPaneReopenId(tabState.opening, tabState.urls, board, legacy = false), shown.ref.threadId)) {
            lastCatalogBoardId = board.id
            return
        }
        screen = FtutterScreen.Catalog(board.id)
    }
    // 統計（端末の中だけ）。
    fun countStat(key: String) {
        editPreference(key, ::ftutterIncrementedCount)
    }
    val selfPostMap by stateStore.selfPostIdentifiersByThread.collectAsState(emptyMap<String, List<String>>())
    // スレッドを開くと履歴の先頭へ（原作：開く・個別に更新するたびに先頭へ移る）。閲覧時刻は開いた時だけ書く。
    // [startAtTop]: an opening from the catalog, the history or a link (the extension 「スレッドを先頭から開く」 then shows the
    // first post); moving between tabs passes false and keeps the place.
    fun openThread(board: BoardSummary, ref: FtutterThreadRef, back: FtutterScreen, refreshOnOpen: Boolean = true, startAtTop: Boolean = true) {
        if (back != FtutterScreen.Boards) mixId = null
        val openingUrl = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, ref.threadId))
        val baseline = parseFtutterReadCounts(preferences[FtutterKeys.READ_COUNTS])[openingUrl]?.let(::ftutterPostsReadFromStored)
        val viewed = com.valoser.futacha.shared.ui.futaber.findFutaberHistoryEntry(history, board, ref.threadId)?.lastViewedSnapshot
        threadMemory.open(openingUrl, refreshOnOpen, baseline,
            ftutterReadBoundaryId(preferences[FTUTTER_READ_BOUNDARIES_KEY], openingUrl, baseline)
                ?: viewed?.lastPostId?.takeIf { viewed.postCount == baseline },
            startAtTop = startAtTop)
        screen = FtutterScreen.Thread(board.id, ref, back)
        countStat(FtutterKeys.STATS_OPENED)
        val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, ref.threadId))
        tabState.open(url, FtutterTabSeed(ref.title, ref.thumbnailUrl, ref.catalogReplyCount))
        val existing = com.valoser.futacha.shared.ui.futaber.findFutaberHistoryEntry(history, board, ref.threadId)
        // Taken now: a deletion made before the write below lands wins over it (audit N3, as ふたばー風's F8).
        val openedAtTicket = stateStore.historyDeletionTicket()
        launchLasting {
            try {
                stateStore.prependOrReplaceHistoryEntry(
                    com.valoser.futacha.shared.ui.futaber.buildFutaberOpenHistoryEntry(
                        existing = existing,
                        board = board,
                        ref = com.valoser.futacha.shared.ui.futaber.FutaberThreadRef(
                            boardId = board.id, threadId = ref.threadId, title = ref.title,
                            thumbnailUrl = ref.thumbnailUrl, replyCount = ref.catalogReplyCount
                        ),
                        nowEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    ),
                    openedAtTicket = openedAtTicket
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to record history", error)
            }
        }
    }
    var aimogeCalendarOpen by remember { mutableStateOf(false) }
    // The MIX stays chosen while a thread opened from it is read: Back returns to the board list, which shows it again.
    val mixStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val confirmMixMembers = com.valoser.futacha.shared.ui.board.rememberMixR18Confirmation(mediaServices?.store)
    if (aimogeCalendarOpen) {
        com.valoser.futacha.shared.ui.aimoge.AimogeEventCalendarDialog(
            onOpenThread = { row ->
                val target = com.valoser.futacha.shared.ui.aimoge.aimogeCalendarBoardIn(boards, row)
                val threadId = row.threadId
                if (target == null || threadId == null) {
                    notice = "この板（/${row.slug}/）は登録していません。板追加から登録してください"
                } else {
                    aimogeCalendarOpen = false
                    openThread(target, FtutterThreadRef(target.id, threadId, title = row.title), FtutterScreen.Boards)
                }
            },
            onDismiss = { aimogeCalendarOpen = false }
        )
    }
    // アプリ内で開くスレッドURL（本文のリンク、URLから開く）。登録していない板は開けない。
    fun openThreadUrl(url: String, back: FtutterScreen) {
        val threadId = ftutterThreadIdFromUrl(url)
        val target = ftutterBoardForUrl(boards, url)
        when {
            target == null -> notice = "登録していない板です"
            threadId == null -> openCatalog(target)
            else -> openThread(target, FtutterThreadRef(target.id, threadId, title = ""), back)
        }
    }

    // Browser links, single-thread notifications and watch-to-phone requests use this same entry.
    // RuntimeHost waits for stored boards/history before composing this app, so an empty list is
    // a loaded empty list. Handle it with a notice too, rather than leaving the link pending forever.
    // Key only by the request: later preference/board/history updates must not reopen it.
    LaunchedEffect(initialThreadDeepLink) {
        val raw = initialThreadDeepLink ?: return@LaunchedEffect
        val parsed = com.valoser.futacha.shared.compat.canonicalizeThreadUrl(raw)
        val target = parsed?.let { link ->
            boards.firstOrNull { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it.url) == link.canonicalBoardUrl }
        }
        when {
            parsed == null -> notice = "スレッドURLを解釈できませんでした"
            target == null -> notice = "登録していない板です"
            else -> {
                openCatalog(target)
                openThread(target, FtutterThreadRef(target.id, parsed.threadNo, title = ""), FtutterScreen.Catalog(target.id))
            }
        }
        onThreadDeepLinkConsumed(raw)
    }
    LaunchedEffect(initialBoardDeepLink) {
        val raw = initialBoardDeepLink ?: return@LaunchedEffect
        val canonical = com.valoser.futacha.shared.compat.canonicalizeBoardUrl(raw)
        val target = canonical?.let { url ->
            boards.firstOrNull { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it.url) == url }
        }
        when {
            canonical == null -> notice = "板URLを解釈できませんでした"
            target == null -> notice = "登録していない板です"
            else -> openCatalog(target)
        }
        onBoardDeepLinkConsumed(raw)
    }

    // ---- 拡張機能（既定OFF） ----
    // 他モードと共有のNG（画像NGのルールを含む）。下の`ngRules`（このモードのNG）と名前が重なるので先に取っておく。
    val compatNgRules = ngRules
    val extensions = remember(preferences) { parseFtutterExtensions(preferences) }
    val commonNgOn = FtutterExtension.COMMON_NG in extensions
    val sharedNgWords by stateStore.ngWords.collectAsState(emptyList())
    val sharedNgHeaders by stateStore.ngHeaders.collectAsState(emptyList())
    val sharedCatalogNgWords by stateStore.catalogNgWords.collectAsState(emptyList())

    // ---- NG・マーク・削除キー・書き込み ----
    val ngRules = remember(preferences[FtutterKeys.NG_LIST]) { parseFtutterNgList(preferences[FtutterKeys.NG_LIST]) }
    // 既定値を書かない形で保存し、それでも上限に入らなければ断って理由を出す（監査A9）。変更は保存されている一覧へ当てる
    // （監査M2。画面の一覧は保存より遅れて届くので、続けて消す・足すと1回目が戻っていた）。保存できた時だけ[savedNotice]を出す。
    val saveNgRules: FtutterNgEdit = { transform, savedNotice ->
        editPreference(FtutterKeys.NG_LIST) { stored ->
            val before = parseFtutterNgList(stored)
            val after = transform(before)
            val problem = ftutterNgCapacityError(after)
            when {
                after == before -> null
                problem != null -> { notice = problem; null }
                else -> {
                    // M55：原作は足した時に「abc を追加しました」と知らせる。
                    (savedNotice ?: ftutterNgAddedNotice(before, after))?.let { notice = it }
                    encodeFtutterNgListForStore(after)
                }
            }
        }
    }
    val manualMarks = remember(preferences[com.valoser.futacha.shared.compat.MANUAL_POST_MARKS_KEY]) {
        com.valoser.futacha.shared.compat.decodeManualPostMarks(preferences[com.valoser.futacha.shared.compat.MANUAL_POST_MARKS_KEY])
    }
    // 削除キー：初回に英数字8文字を作る（原作どおり。他モードの削除キーとは別に持つ）。
    val deleteKey = preferences[FtutterKeys.DELETE_KEY]?.takeIf { it.isNotBlank() }
    // D-B6 (J6)：削除キーは4モードで1つ。共有の最後に使った削除キーと同じ値にそろえる（初回は共有側が勝つ）。
    val sharedDeleteKey by stateStore.observedLastUsedDeleteKey.collectAsState(null)
    var syncedDeleteKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(deleteKey, sharedDeleteKey) {
        val plan = ftutterDeleteKeySync(deleteKey, sharedDeleteKey, syncedDeleteKey) { ftutterGenerateDeleteKey() }
        plan.own?.let { savePreference(FtutterKeys.DELETE_KEY, it) }
        plan.shared?.let { stateStore.setLastUsedDeleteKey(it) }
        syncedDeleteKey = plan.own ?: plan.shared ?: deleteKey
    }
    var postRequest by remember { mutableStateOf<Pair<FtutterPostTarget, String?>?>(null) }
    // シートで送信に失敗した時の添付（下書きにはファイルが残らないので、次に同じ所へ開いた時に戻す。T-B3）。
    var failedAttachment by remember { mutableStateOf<Pair<String, com.valoser.futacha.shared.util.ImageData>?>(null) }
    var threadRefreshSignal by remember { mutableStateOf(0) }
    val pickerPreference by stateStore.attachmentPickerPreference.collectAsState(com.valoser.futacha.shared.util.AttachmentPickerPreference.MEDIA)
    val preferredFileManager by stateStore.getPreferredFileManager().collectAsState(null)
    fun repositoryFor(board: BoardSummary) = if (board.isMockBoard()) mockRepository else repository
    // 送信は、ふたちゃ・ふたばー風と同じ共有の処理（公式の送信前処理・フォームの取得・自動再送の抑止を含む）。
    suspend fun sendPost(target: FtutterPostTarget, input: FtutterPostInput): Result<String?> {
        val originalBoard = boards.firstOrNull { it.id == target.boardId } ?: return Result.failure(IllegalStateException("板が登録されていません"))
        val board = originalBoard.copy(url = ftutterNetworkUrl(originalBoard.url))
        val noticeAgreed = try {
            com.valoser.futacha.shared.ui.board.checkPostingNoticeIfNeeded(stateStore)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return Result.failure(IllegalStateException("投稿のご案内を確認できませんでした。もう一度お試しください", error))
        }
        if (!noticeAgreed) return Result.failure(IllegalStateException("投稿のご案内に同意すると書き込めます"))
        val repo = repositoryFor(board)
        val password = com.valoser.futacha.shared.ui.board.normalizeDeleteKeyForSubmit(input.deleteKey)
        val outcome = when (target) {
            is FtutterPostTarget.Reply -> com.valoser.futacha.shared.ui.board.performThreadReplyAction(
                com.valoser.futacha.shared.ui.board.buildThreadReplyActionConfig(
                    boardUrl = board.url,
                    threadId = target.threadId,
                    draft = com.valoser.futacha.shared.ui.board.ThreadReplyDraft(
                        name = input.draft.name, email = input.draft.mail, subject = input.draft.title,
                        comment = input.draft.comment, password = password, imageData = input.image
                    ),
                    normalizedPassword = password
                ),
                com.valoser.futacha.shared.ui.board.buildThreadReplyActionCallbacks(repo)
            )
            is FtutterPostTarget.Create -> com.valoser.futacha.shared.ui.board.performThreadAction {
                com.valoser.futacha.shared.ui.board.requireWritableThreadBoard(board.url)
                repo.createThread(
                    board = board.url, name = input.draft.name, email = input.draft.mail,
                    subject = input.draft.title, comment = input.draft.comment, password = password,
                    imageFile = input.image?.bytes, imageFileName = input.image?.fileName,
                    textOnly = input.image == null, handwriting = input.image?.isHandwriting == true
                )
            }
        }
        return when (outcome) {
            is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Success -> {
                val posted = outcome.value
                if (target is FtutterPostTarget.Reply && !posted.isNullOrBlank()) {
                    try {
                        // 自分の書き込みの記録（左の線・「自分のレス」・返信の数え方に使う）。ふたちゃと共有。
                        stateStore.addSelfPostIdentifier(target.threadId, posted, board.id)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to note a sent post", error)
                    }
                }
                Result.success(posted)
            }
            is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Failure -> Result.failure(
                IllegalStateException(
                    com.valoser.futacha.shared.ui.board.buildThreadActionFailureMessage(
                        if (target is FtutterPostTarget.Reply) "返信の送信に失敗しました" else "スレッド作成に失敗しました", outcome.error
                    ),
                    outcome.error
                )
            )
        }
    }
    // 原作のバックアップ（.ftt）の取り込み：選ぶ→復号→読み替え→確認→今の設定に足す。
    var backupPlan by remember { mutableStateOf<FtutterBackupPlan?>(null) }
    // 復号・読み替えは重い（数十MBになり得る）ので、メインスレッドの外で行い、その間は「読み込み中」を出す（取り消せる）。
    var backupLoading by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val pickBackup = com.valoser.futacha.shared.ui.futaber.mht.rememberFutaberMhtPickerLauncher(
        onSelected = { file ->
            backupLoading?.cancel()
            val currentBoards = boards
            val currentNg = ngRules
            val currentPreferences = preferences
            val currentHistoryOrder = tabState.historyOrder
            backupLoading = scope.launch {
                val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                try {
                    val plan = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        val ensureActive = { job?.ensureActive(); Unit }
                        ftutterDecryptBackup(file.bytes, ensureActive = ensureActive)?.let { xml ->
                            ftutterBackupPlan(
                                parseFtutterPreferencesXml(xml, ensureActive), currentBoards, currentNg, currentPreferences,
                                kotlin.time.Clock.System.now().toEpochMilliseconds(), ensureActive, currentHistoryOrder
                            )
                        }
                    }
                    if (plan == null) notice = "ふたったーのバックアップとして読めませんでした" else backupPlan = plan
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to read a backup", error)
                    notice = "ふたったーのバックアップとして読めませんでした"
                } finally {
                    if (backupLoading === job) backupLoading = null
                }
            }
        },
        onError = { notice = it }
    )
    fun applyBackup(plan: FtutterBackupPlan) {
        scope.launch {
            try {
                // 1つの設定の上限に入らない分（多すぎるNG・非表示・タブ・下書き）は、入る所まで取り込み、残りを知らせる（監査A9。
                // 以前はストアが全体を断り、何も取り込めなかった）。
                // The tabs go to the shared tabs below (plan B7a, audit L1): the old rows are not written any more.
                val fitted = ftutterFitPreferencesForStore(plan.preferences - FTUTTER_IMPORT_TAB_KEYS)
                val importer = importPreferences
                var draftImport = plan.draftImport
                fun mergeDrafts(stored: String?): String = ftutterMergeImportedDrafts(stored, plan.importedDrafts)
                    .also { draftImport = it }.encoded
                if (importer != null) {
                    // The read counts are merged with what is stored when they are written, so a count written while the
                    // import was being confirmed stays (re-audit L28).
                    val importedUrls = plan.history.mapTo(HashSet()) { it.boardUrl }
                    importer(fitted.preferences.mapValues { (key, value) ->
                        if (key == FtutterKeys.READ_COUNTS) { stored: String? -> ftutterMergeImportedReadCounts(value, stored, importedUrls) }
                        else if (key == FtutterKeys.DRAFTS && plan.importedDrafts.isNotEmpty()) { stored: String? -> mergeDrafts(stored) }
                        else { _: String? -> value }
                    })
                } else {
                    val values = fitted.preferences.toMutableMap()
                    if (plan.importedDrafts.isNotEmpty()) {
                        val store = mediaServices?.store
                        val stored = if (store != null) store.loadPreference(FtutterKeys.DRAFTS) else preferences[FtutterKeys.DRAFTS]
                        values[FtutterKeys.DRAFTS] = mergeDrafts(stored)
                    }
                    mediaServices?.store?.savePreferences(values) ?: values.forEach { (k, v) -> savePreference(k, v) }
                }
                stateStore.updateBoards { current -> ftutterMergeBackupBoards(current, plan.boards) }
                if (plan.history.isNotEmpty()) stateStore.importHistoryEntries(plan.history)
                val tabsUnstarred = ftutterImportTabs(
                    tabStore, stateStore.boards.first(), stateStore.history.first(), plan.preferences,
                    kotlin.time.Clock.System.now().toEpochMilliseconds()
                ) { key, transform -> editPreference(key) { stored -> encodeFtutterList(transform(decodeFtutterList(stored).orEmpty())) } }
                val supplements = ftutterApplyBackupSupplements(plan.cookieHeader, mediaServices?.cookieRepository) {
                    // Cookie failure must not skip the marks; report each optional stage independently.
                    if (plan.marks.isEmpty()) 0 else {
                        val store = checkNotNull(mediaServices?.store)
                        plan.marks.sumOf { (url, no) -> com.valoser.futacha.shared.compat.setManualPostMark(store, url, no, true) }
                    }
                }
                notice = listOfNotNull(
                    ftutterImportNotice(fitted.leftOut, supplements.droppedMarks, supplements), draftImport?.notice,
                    tabsUnstarred.takeIf { it > 0 }?.let(::ftutterUnstarredNotice)
                ).joinToString("。")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to import a backup", error)
                notice = "取り込めませんでした"
            }
        }
    }
    val sharedTabEntries = remember(sharedTabs) { sharedTabs.associate { it.canonicalUrl to it.toFtutterHistoryEntry() } }
    val tabSettings = remember(preferences) { parseFtutterTabSettings(preferences) }
    val historyByUrl = rememberFtutterHistoryByUrl(history, boards)
    // The original's 「履歴の保存数（保護したタブは除く）」 limits what this mode shows (plan B7a, Q3): the shared tabs are
    // not closed by it (the shared tab limit trims them).
    val historyOrder = remember(historyByUrl) { historyByUrl.keys.toList() }
    val historyLimit = remember(preferences) { ftutterHistoryLimit(preferences) }
    androidx.compose.runtime.SideEffect {
        tabState.historyOrder = historyOrder
        tabState.limit = historyLimit
    }
    val readCounts = remember(preferences) { parseFtutterReadCounts(preferences[FtutterKeys.READ_COUNTS]) }
    val tabUrlSet = remember(tabState.urls) { tabState.urls.toHashSet() }
    fun tabView(url: String) = FtutterTabView(
        url = url,
        entry = historyByUrl[url] ?: sharedTabEntries[url],
        numbers = ftutterTabNumbers(historyByUrl[url] ?: sharedTabEntries[url], readCounts[url]),
        protected = url in tabState.protected,
        isTab = url in tabUrlSet
    )
    // タブのURLからスレッドを開く（戻るとその板のカタログ）。ドロワーの「履歴」から開く時だけ[startAtTop]。
    fun openTabUrl(url: String, startAtTop: Boolean = false) {
        val threadId = ftutterThreadIdFromUrl(url) ?: return
        val board = ftutterBoardForUrl(boards, url)
        if (board == null) {
            notice = "登録していない板です"
            return
        }
        val entry = historyByUrl[url]
        val current = screen as? FtutterScreen.Thread
        if (current?.boardId == board.id && current.ref.threadId == threadId &&
            preferences.ftutterBool(FtutterKeys.REFRESH_ON_TAB_MOVE_THREAD, false)) threadRefreshSignal++
        openThread(board, FtutterThreadRef(board.id, threadId, entry?.title.orEmpty(), entry?.titleImageUrl.orEmpty()), ftutterTabBack(screen, mixId, board.id),
            refreshOnOpen = preferences.ftutterBool(
                FtutterKeys.REFRESH_ON_TAB_MOVE_THREAD, false), startAtTop = startAtTop)
    }
    // 開いているスレッドのタブが閉じられた時：残りがあれば同じ位置のタブへ、無ければ（設定ON）カタログへ1段だけ戻る。
    fun afterClose(before: List<String>) {
        val current = screen as? FtutterScreen.Thread ?: return
        val board = boards.firstOrNull { it.id == current.boardId } ?: return
        val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, current.ref.threadId))
        if (url in tabState.urls || url !in before) return
        val next = ftutterNextOpening(before, tabState.urls, url)
        when {
            next != null -> openTabUrl(next)
            tabSettings.backWhenNoTab -> screen = ftutterTabBack(current, mixId, board.id)
        }
    }
    fun closeTabNow(url: String) {
        val before = tabState.urls
        tabState.close(listOf(url))
        afterClose(before)
    }
    var protectedConfirm by remember { mutableStateOf<String?>(null) }
    // 1つ閉じる時だけ、保護したタブは確認する。
    fun closeTab(url: String) {
        if (url in tabState.protected) protectedConfirm = url else closeTabNow(url)
    }
    // Closing several: only the tabs this mode shows, and never a protected one (audit M1, Q7).
    fun closeMany(targets: List<String>) {
        val shown = tabState.urls.toHashSet()
        val before = tabState.urls
        tabState.close(targets.filter { it in shown && it !in tabState.protected })
        afterClose(before)
    }
    // The record of closed tabs is shared and kept 24 hours (plan B6): this mode brings back what it closed or showed.
    var closedTabCount by remember { mutableStateOf(0) }
    LaunchedEffect(tabStore, sharedTabs, tabState.settledWrites) {
        closedTabCount = try {
            (tabStore?.countClosedTabBatches(FTUTTER_TAB_PROFILE, kotlin.time.Clock.System.now().toEpochMilliseconds()) ?: 0) +
                tabState.legacyClosedCount
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            0
        }
    }
    fun restoreClosed(): String? {
        if (closedTabCount <= 0) return null
        tabState.restore { restored ->
            if (restored == null) {
                notice = "戻せるタブがありません"
                return@restore
            }
            val url = restored.url
            val tab = restored.tab
            if (historyByUrl[url] == null && tab != null) {
                // 履歴に無ければ履歴にも戻す（原作どおり）。題名・サムネイルは閉じた時のタブから。
                launchLasting { runCatching { stateStore.prependOrReplaceHistoryEntry(
                    tab.toFtutterHistoryEntry().copy(lastVisitedEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds())
                ) } }
            }
            if (screen is FtutterScreen.Thread) openTabUrl(url)
            notice = "タブを戻しました：" + (tab?.title?.ifBlank { tab.threadNo } ?: historyByUrl[url]?.title?.ifBlank { null } ?: url)
        }
        return "タブを戻しています"
    }
    // 履歴の行を消す：ふたちゃ・ふたばー風の削除と同じく、他モードの履歴（共有ストア。残すと次の起動の統合で戻る）、
    // 自分の書き込みの記録、自動保存した本文も消す（監査A8。以前は共有の履歴だけを消していた）。
    val importedHistoryRepository = remember(mediaServices?.fileSystem) {
        com.valoser.futacha.shared.ui.buildImportedHistoryRepository(mediaServices?.fileSystem)
    }
    fun dismissHistoryRows(entries: List<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            try {
                com.valoser.futacha.shared.ui.dismissHistoryEntries(
                    stateStore = stateStore,
                    autoSavedThreadRepository = autoSavedThreadRepository,
                    importedHistoryRepository = importedHistoryRepository,
                    compatibilityStore = mediaServices?.store,
                    entries = entries,
                    onAutoSavedThreadDeleteFailure = {
                        com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to delete the auto-saved copy", it)
                        notice = com.valoser.futacha.shared.ui.futaber.FUTABER_AUTO_SAVE_DELETE_FAILED_NOTICE
                    }
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to delete history", error)
                notice = "履歴を削除できませんでした"
            }
        }
    }
    // 履歴とタブは独立して管理する。履歴の削除では開いているタブを閉じない。
    fun deleteHistoryUrls(urls: Collection<String>) {
        val targets = urls.toSet()
        if (targets.isEmpty()) return
        val entries = ftutterHistoryEntriesFor(history, boards, targets)
        dismissHistoryRows(entries)
    }
    // 「すべて削除」（拡張）：保護したスレッドの履歴は残す。
    fun deleteAllHistory() {
        val entries = ftutterHistoryEntriesExcept(history, boards, tabState.protected)
        dismissHistoryRows(entries)
    }
    var refreshingTabs by remember { mutableStateOf(false) }
    // 「タブを全て更新」：履歴の並びは変えない（原作の pushToRireki:false）。落ちたスレには印を付ける。
    // 進み具合（済み・全件）。出ている間は「中止」で止められる（L131）。
    var tabRefreshProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var tabRefreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun refreshAllTabs() {
        if (refreshingTabs) return
        refreshingTabs = true
        val targets = tabState.urls.mapNotNull { url ->
            val threadId = ftutterThreadIdFromUrl(url) ?: return@mapNotNull null
            val board = ftutterBoardForUrl(boards, url) ?: return@mapNotNull null
            FtutterTabRefreshTarget(url, board.id, threadId)
        }
        val boardsById = boards.associateBy { it.id }
        tabRefreshJob = scope.launch {
            var completed = false
            try {
                // 結果はまとめて当てる（L131：以前はタブごとに共有の履歴全体を書き直した）。
                val failed = ftutterRefreshTabs(
                    targets,
                    fetch = { target ->
                        val board = boardsById.getValue(target.boardId)
                        val repo = if (board.isMockBoard()) mockRepository else threadRepository
                        repo.getThreadContent(board.url, target.threadId).page.also { threadMemory.put(target.url, it) }.posts
                    },
                    isGone = { error -> error.statusCodeOrNull().let { it == 404 || it == 410 } },
                    apply = { results ->
                        try {
                            stateStore.updateHistory {
                                ftutterHistoryAfterTabRefresh(it, results, kotlin.time.Clock.System.now().toEpochMilliseconds())
                            }
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to apply refreshed tabs", error)
                        }
                    },
                    onProgress = { done, total -> tabRefreshProgress = done to total }
                )
                completed = true
                // M72：原作は成功を知らせない。取得できなかったタブがある時だけ知らせる。
                if (failed > 0) notice = "タブを全て更新しました（$failed 件は取得できませんでした）"
            } finally {
                if (!completed) notice = "タブの更新を中止しました（取得できた分は反映しました）"
                tabRefreshProgress = null
                refreshingTabs = false
                tabRefreshJob = null
            }
        }
    }
    // ドロワーはカタログ・スレッドだけ。それ以外の画面へ移ったら閉じた状態に戻す（監査A3：板一覧へ移っても開いたまま残り、
    // 次にカタログへ入るとドロワーが開いた状態で出た）。
    val drawerAvailable = screen is FtutterScreen.Catalog || screen is FtutterScreen.Thread
    // 画面の幅が変わる（回転）と作り直す：古い閉じた位置が開いた位置に近くなり、ドロワーが開いた（M22）。
    val drawerState = rememberFtutterDrawerState(drawerAvailable, androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width)
    // ドロワー「無効」では描かれないので、開く側も戻る側もそれを見る（L23：見えないドロワーが開いた扱いになり、次の戻るが空振りした）。
    val drawerUsable = drawerAvailable && tabSettings.drawerSide != FtutterDrawerSide.NONE
    val drawerShowing = drawerUsable && (drawerState.isOpen || drawerState.targetValue == androidx.compose.material3.DrawerValue.Open)
    // 戻るの順（ftutterBackStep）：ドロワー → 二画面のカタログ側の検索 → 画面を戻る。どの受け手もこの順で決めるので、
    // 受け手を登録した順（後から置いたものが先に受ける）に左右されない。
    fun handleBack(twoPane: Boolean, leave: () -> Unit) {
        when (ftutterBackStep(drawerShowing, twoPane && mixId == null && catalogHolder.paneBack != null)) {
            FtutterBackStep.CLOSE_DRAWER -> scope.launch { drawerState.close() }
            FtutterBackStep.CATALOG_PANE -> catalogHolder.paneBack?.invoke()
            FtutterBackStep.LEAVE -> leave()
        }
    }
    var drawerContent by remember(tabSettings.drawerContent) { mutableStateOf(tabSettings.drawerContent) }
    var drawerFilter by remember { mutableStateOf(FtutterDrawerFilter()) }
    var tabDialog by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // ドロワーの「履歴」の行の長押しメニューと、その確認・NGの登録。
    var historyMenu by remember { mutableStateOf<String?>(null) }
    var historyDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var historyDelConfirm by remember { mutableStateOf<String?>(null) }
    var historyNgDraft by remember { mutableStateOf<FtutterNgRule?>(null) }
    val openDrawer: () -> Unit = { if (drawerUsable) scope.launch { drawerState.open() } else notice = FTUTTER_DRAWER_DISABLED_NOTICE }
    val currentThreadUrl = (screen as? FtutterScreen.Thread)?.let { t ->
        boards.firstOrNull { it.id == t.boardId }?.let { ftutterCanonicalThreadUrl(ftutterThreadUrl(it, t.ref.threadId)) }
    }
    val tabStrip: @Composable () -> Unit = {
        if (tabSettings.showTab) {
            FtutterTabStrip(
                tabs = tabState.urls.map(::tabView),
                opening = currentThreadUrl,
                listState = tabStripState,
                showStatus = FtutterExtension.TAB_STATUS in extensions,
                legacyLook = ftutterLegacyThreadLook(preferences),
                settings = tabSettings,
                design = design,
                onOpen = ::openTabUrl,
                onLongPress = { url -> tabDialog = url to false },
                onSwipeUp = { url -> tabState.toggleProtect(url) },
                onSwipeDown = { url -> closeTab(url) },
                onMove = if (preferences.ftutterBool(FtutterKeys.DISABLE_TAB_DRAG, false)) null else { from, to -> tabState.move(from, to) }
            )
        }
    }

    val gesture = remember(preferences) { parseFtutterGestureSettings(preferences) }
    val tablet = remember(preferences) { parseFtutterTabletSettings(preferences) }
    var toolbarEditor by remember { mutableStateOf<FtutterOrderEditorKind?>(null) }
    // スレッドの「前へ・次へ」は隣のタブ（原作のスレッドはタブを横に並べたページ）。
    // 実行しなかった時は理由を返す（L17。メニューから選んだ時だけ知らせる）。
    fun threadGesture(action: FtutterGestureAction, current: FtutterScreen.Thread, board: BoardSummary, twoPane: Boolean): String? {
        val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, current.ref.threadId))
        val unavailable = ftutterGestureUnavailableReason(action, isThread = true)
        when (action) {
            FtutterGestureAction.PREVIOUS -> {
                val prev = ftutterAdjacentTab(tabState.urls, url, forward = false)
                when {
                    prev != null -> openTabUrl(prev)
                    // 先頭のタブで「前へ」：二画面でなく、設定（既定ON）ならカタログへ戻る。
                    gesture.backByPreviousInThread && !twoPane -> screen = current.back
                    else -> return unavailable
                }
            }
            FtutterGestureAction.NEXT -> ftutterAdjacentTab(tabState.urls, url, forward = true)?.let(::openTabUrl) ?: return unavailable
            FtutterGestureAction.BACK -> screen = if (twoPane) FtutterScreen.Boards else current.back
            FtutterGestureAction.CLOSE_TAB -> closeTab(url)
            FtutterGestureAction.CLOSE_TAB_AND_BACK -> {
                // 閉じて戻る（再監査L27：閉じる処理が隣のタブを開くと、戻らずに隣のタブを見せていた）。保護したタブは
                // 確認を出すだけで、戻らない（従来どおり）。
                // 二画面では従来どおり閉じるだけ（戻る先の画面を積まない）。
                if (twoPane || url in tabState.protected) closeTab(url) else {
                    tabState.close(listOf(url))
                    screen = current.back
                }
            }
            FtutterGestureAction.REOPEN_TAB -> restoreClosed()?.let { notice = it } ?: return unavailable
            FtutterGestureAction.MOST_LEFT -> tabState.urls.firstOrNull()?.let(::openTabUrl) ?: return unavailable
            FtutterGestureAction.MOST_RIGHT -> tabState.urls.lastOrNull()?.let(::openTabUrl) ?: return unavailable
            // 左隣・右隣のタブ（追加）：先頭・末尾ではカタログへ戻らず、何もしない。
            FtutterGestureAction.LEFT_TAB, FtutterGestureAction.RIGHT_TAB ->
                ftutterAdjacentTab(tabState.urls, url, forward = action == FtutterGestureAction.RIGHT_TAB)?.let(::openTabUrl) ?: return unavailable
            FtutterGestureAction.REFRESH_ALL_TABS -> refreshAllTabs()
            FtutterGestureAction.OPEN_DRAWER -> if (drawerUsable) openDrawer() else return unavailable
            else -> Unit
        }
        return null
    }
    // カタログの「前へ・次へ」はお気に入りの隣の板（原作のカタログはお気に入りの板を横に並べたページ）。
    fun catalogGesture(action: FtutterGestureAction, board: BoardSummary): String? {
        val favorites = boards.filter { it.pinned }.map { it.id }
        val unavailable = ftutterGestureUnavailableReason(action, isThread = false, hasFavorites = favorites.isNotEmpty())
        when (action) {
            FtutterGestureAction.PREVIOUS, FtutterGestureAction.NEXT -> {
                when (val step = ftutterBoardStep(favorites, board.id, action == FtutterGestureAction.NEXT, gesture.backByPreviousInCatalog)) {
                    is FtutterBoardStep.To -> boards.firstOrNull { it.id == step.boardId }?.let { openCatalog(it, tabMove = true) }
                    FtutterBoardStep.Back -> screen = FtutterScreen.Boards
                    FtutterBoardStep.Stay -> return unavailable
                }
            }
            FtutterGestureAction.BACK -> screen = FtutterScreen.Boards
            // カタログで「タブを閉じる」は、最後に開いたスレッドのタブを閉じる（画面は移らない）。
            FtutterGestureAction.CLOSE_TAB, FtutterGestureAction.CLOSE_TAB_AND_BACK ->
                preferences[FtutterKeys.OPENING]?.let(::ftutterCanonicalThreadUrl)?.takeIf { it in tabState.urls }?.let(::closeTab)
                    ?: return unavailable
            FtutterGestureAction.REOPEN_TAB -> restoreClosed()?.let { notice = it } ?: return unavailable
            FtutterGestureAction.MOST_LEFT -> tabState.urls.firstOrNull()?.let(::openTabUrl) ?: return unavailable
            FtutterGestureAction.MOST_RIGHT -> tabState.urls.lastOrNull()?.let(::openTabUrl) ?: return unavailable
            // カタログでは、最後に開いたスレッドのタブの隣（そのタブが無ければ何もしない）。
            FtutterGestureAction.LEFT_TAB, FtutterGestureAction.RIGHT_TAB ->
                ftutterAdjacentTab(tabState.urls, preferences[FtutterKeys.OPENING]?.let(::ftutterCanonicalThreadUrl), forward = action == FtutterGestureAction.RIGHT_TAB)
                    ?.let(::openTabUrl) ?: return unavailable
            FtutterGestureAction.REFRESH_ALL_TABS -> refreshAllTabs()
            FtutterGestureAction.OPEN_DRAWER -> if (drawerUsable) openDrawer() else return unavailable
            else -> Unit
        }
        return null
    }

    // スレへのdel（スレの本文＝No.がスレ番号）。確認は呼ぶ側で出す。
    fun sendThreadDel(board: BoardSummary, threadId: String) {
        scope.launch {
            val result = com.valoser.futacha.shared.ui.board.performThreadAction {
                com.valoser.futacha.shared.ui.board.requireWritableThreadBoard(board.url)
                repositoryFor(board).requestDeletion(ftutterNetworkUrl(board.url), threadId, threadId, com.valoser.futacha.shared.ui.board.DEFAULT_DEL_REASON_CODE)
            }
            notice = when (result) {
                is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Success -> "登録しました"
                is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Failure ->
                    com.valoser.futacha.shared.ui.board.buildThreadActionFailureMessage("delに失敗しました", result.error)
            }
        }
    }

    @Composable
    fun CatalogPane(board: BoardSummary, current: FtutterScreen, columnsOverride: Int?, strip: @Composable () -> Unit) {
        val board = board.copy(url = ftutterNetworkUrl(board.url))
        LaunchedEffect(board.id) { lastCatalogBoardId = board.id }
        FtutterCatalogScreen(
            board = board,
            boards = boards,
            repository = if (board.isMockBoard()) mockRepository else repository,
            history = history,
            preferences = preferences,
            holder = catalogHolder,
            savePreference = savePreference,
            editPreference = editPreference,
            onBack = { screen = FtutterScreen.Boards },
            onSwitchBoard = { openCatalog(it) },
            onOpenSettings = { screen = FtutterScreen.Settings(current) },
            onOpenThread = { item ->
                openThread(board, FtutterThreadRef(
                    boardId = board.id, threadId = item.id, title = item.title.orEmpty(),
                    thumbnailUrl = item.thumbnailUrl.orEmpty(), catalogReplyCount = item.replyCount
                ), current)
            },
            onOpenHiddenAndNg = { screen = FtutterScreen.HiddenAndNg(current) },
            onOpenUrl = { url ->
                val target = ftutterBoardForUrl(boards, url)
                when {
                    target != null && !url.contains("/res/") -> openCatalog(target)
                    target != null || ftutterLegacyBoards(preferences) -> openThreadUrl(url, current)
                    // M63：原作は登録していない板のURLならその板を板一覧に足して開き、ふたばでないURLは知らせるだけ。
                    else -> when (val kind = ftutterOpenUrlBoard(url, boards)) {
                        null -> notice = "URLが対応していない形式です"
                        else -> scope.launch {
                            val name = fetchFtutterBoardName(httpClient, kind)
                            if (name == null) {
                                notice = "板を読み込めませんでした"
                                return@launch
                            }
                            val added = com.valoser.futacha.shared.ui.board.createCustomBoardSummary(name = name, url = kind, existingBoards = boards)
                            var board = added
                            stateStore.updateBoards { latest ->
                                ftutterBoardForUrl(latest, kind)?.let { board = it; latest } ?: (latest + added)
                            }
                            // 画面の板一覧に届くまで待つ（届く前に開くと「板が無い」として板一覧へ戻るため）。
                            kotlinx.coroutines.withTimeoutOrNull(3_000) {
                                androidx.compose.runtime.snapshotFlow { latestBoardsState.value }.first { list -> list.any { it.id == board.id } }
                            }
                            val threadId = ftutterThreadIdFromUrl(url)
                            if (threadId == null) openCatalog(board)
                            else openThread(board, FtutterThreadRef(board.id, threadId, title = ""), current)
                        }
                    }
                }
            },
            onDeleteHistory = { entry ->
                // 原作：タブを閉じて履歴から削除する（ドロワーの「履歴から削除」と同じ後片付け）。
                val url = ftutterHistoryThreadUrl(entry, boards)
                if (url != null) deleteHistoryUrls(listOf(url)) else dismissHistoryRows(listOf(entry))
            },
            onNotice = { notice = it },
            onOpenDrawer = openDrawer,
            onAddTab = { item ->
                val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, item.id))
                tabState.push(url, FtutterTabSeed(item.title.orEmpty(), item.thumbnailUrl.orEmpty(), item.replyCount))
                // 原作ではタブは履歴の中にしか無いので、履歴にも入れる（再監査M2：入れないと、次にこのモードを開いた時の整理で
                // 「履歴に無いタブ」として黙って消えた）。題名・サムネ・レス数はカタログのもの。
                val openedAtTicket = stateStore.historyDeletionTicket()
                if (historyByUrl[url] == null) launchLasting {
                    runCatching {
                        stateStore.prependOrReplaceHistoryEntry(
                            com.valoser.futacha.shared.ui.futaber.buildFutaberOpenHistoryEntry(
                                null, board,
                                com.valoser.futacha.shared.ui.futaber.FutaberThreadRef(
                                    board.id, item.id, item.title.orEmpty(), item.thumbnailUrl.orEmpty(), item.replyCount + 1
                                ),
                                kotlin.time.Clock.System.now().toEpochMilliseconds()
                            ),
                            openedAtTicket = openedAtTicket
                        )
                    }.onFailure { com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to record the added tab in the history", it) }
                }
            },
            tabOnTop = tabSettings.tabOnTop,
            tabStrip = strip,
            gesture = gesture,
            onAppGesture = { action -> catalogGesture(action, board) },
            onAppMenuGesture = { action -> catalogGesture(action, board)?.let { notice = it } },
            onSwipeBoard = { forward -> catalogGesture(if (forward) FtutterGestureAction.NEXT else FtutterGestureAction.PREVIOUS, board) },
            onEditToolbar = { toolbarEditor = FtutterOrderEditorKind.CATALOG },
            columnsOverride = columnsOverride,
            openTabUrls = tabUrlSet,
            commonCatalogNgWords = if (commonNgOn) sharedCatalogNgWords else emptyList(),
            serverSearchClient = if (board.isMockBoard()) null else httpClient,
            archiveClient = if (FtutterExtension.ARCHIVE_SEARCH in extensions && !board.isMockBoard()) httpClient else null,
            ngRules = ngRules,
            onSaveNgRules = saveNgRules,
            onCreateThread = { postRequest = FtutterPostTarget.Create(board.id) to null },
            onDelThread = { item -> sendThreadDel(board, item.id) }
        )
    }

    @Composable
    fun ThreadPane(board: BoardSummary, current: FtutterScreen.Thread, strip: @Composable () -> Unit, twoPane: Boolean) {
        val board = board.copy(url = ftutterNetworkUrl(board.url))
        val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, current.ref.threadId))
        val refreshOnOpen = remember(current) { threadMemory.takeRefresh(url) }
        val startAtTop = remember(current) { threadMemory.takeStartAtTop(url) } && FtutterExtension.OPEN_AT_TOP in extensions
        FtutterThreadHost(
            board = board,
            ref = current.ref,
            initialPage = threadMemory.page(url),
            openingVisit = threadMemory.opening(url),
            refreshOnOpen = refreshOnOpen,
            startAtTop = startAtTop,
            onPageSnapshot = { threadMemory.put(url, it) },
            onThreadPageRead = { readUrl, page -> tabWriter.pageRead(readUrl, page) },
            repository = if (board.isMockBoard()) mockRepository else threadRepository,
            httpClient = httpClient,
            stateStore = stateStore,
            history = history,
            preferences = preferences,
            design = design,
            selfPostMap = selfPostMap,
            store = mediaServices?.store,
            onArchiveReportEnqueued = onArchiveReportEnqueued,
            savePreference = savePreference,
            editPreference = editPreference,
            onOpenThreadUrl = { url -> openThreadUrl(url, current) },
            onOpenSettings = { screen = FtutterScreen.Settings(current) },
            onBack = { handleBack(twoPane) { screen = if (twoPane) FtutterScreen.Boards else current.back } },
            onNotice = { notice = it },
            onOpenDrawer = openDrawer,
            onFallen = { scope.launch { markFallen(stateStore, board.id, current.ref.threadId) } },
            tabOnTop = tabSettings.tabOnTop,
            tabStrip = strip,
            ngRules = ngRules,
            boards = boards,
            onSaveNgRules = saveNgRules,
            manualMarks = manualMarks,
            deleteKey = deleteKey.orEmpty(),
            onReply = { quote -> postRequest = FtutterPostTarget.Reply(board.id, current.ref.threadId) to quote },
            refreshSignal = threadRefreshSignal,
            gesture = gesture,
            onAppGesture = { action -> threadGesture(action, current, board, twoPane) },
            onAppMenuGesture = { action -> threadGesture(action, current, board, twoPane)?.let { notice = it } },
            onSwipeTab = { forward -> threadGesture(if (forward) FtutterGestureAction.NEXT else FtutterGestureAction.PREVIOUS, current, board, twoPane) },
            onEditToolbar = { toolbarEditor = FtutterOrderEditorKind.THREAD },
            mediaServices = mediaServices,
            onSoudaneSent = { countStat(FtutterKeys.STATS_SOUDANE) },
            extensions = extensions,
            commonNgWords = sharedNgWords,
            commonNgHeaders = sharedNgHeaders,
            compatNgRules = compatNgRules,
            threadActions = threadActions
        )
    }

    FtutterTheme(mainColor = design.mainColor, brightness = design.brightness, overrides = design.overrides, disableStretch = design.disableStretchAnimation,
        fontFamily = LocalFtutterFont.current, preferences = preferences) {
        val colors = LocalFtutterColors.current
        // iOSのホームバーの帯（Composeが描かない下端）を、下のツールバーと同じ色にする（ふたばー風と同じ仕組み。iOS以外は何もしない）。
        com.valoser.futacha.shared.ui.futaber.ApplyFutaberHostBackground(colors.toolBar)
        val historyUrls = remember(historyByUrl, tabState.protected, historyLimit) {
            ftutterLimitedHistoryUrls(historyByUrl.keys.toList(), tabState.protected, historyLimit)
        }
        FtutterDrawerHost(
            drawerState = drawerState,
            settings = tabSettings,
            enabled = screen is FtutterScreen.Catalog || screen is FtutterScreen.Thread,
            drawer = {
                // 題名の検索は、打ち終わってから効かせ、正規化した題名は履歴が変わった時に1回だけ裏で作る（監査M11）。
                // できるまでの間は前の行を出したままにする。
                val appliedFilter = drawerFilter.copy(query = com.valoser.futacha.shared.ui.futaber.rememberFutaberDebouncedQuery(drawerFilter.query))
                val searchingTitles = appliedFilter.query.isNotBlank()
                val titleKeys by androidx.compose.runtime.produceState<Pair<Map<String, ThreadHistoryEntry>, Map<String, String>>?>(null, historyByUrl, searchingTitles) {
                    value = if (!searchingTitles) null else historyByUrl to kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        ftutterDrawerTitleKeys(historyByUrl)
                    }
                }
                val keysNow = titleKeys?.takeIf { it.first === historyByUrl }?.second
                // 行は履歴（最大2万件）が変わるたびに全件作るので、解析用のスレッドで作る（再監査M7：ドロワーは閉じていても
                // 組み立てるので、履歴の保存のたびにメインで作っていた）。できるまでの間は前の行を出したままにする。
                val tabUrls = tabState.urls
                val drawerEntries = sharedTabEntries + historyByUrl
                val protectedUrls = tabState.protected
                val rows by androidx.compose.runtime.produceState(
                    emptyList<FtutterTabView>(), drawerContent, appliedFilter, historyUrls, tabUrls, protectedUrls, readCounts, drawerEntries, keysNow
                ) {
                    if (searchingTitles && keysNow == null) return@produceState
                    value = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        ftutterDrawerRows(drawerContent, historyUrls, tabUrls, protectedUrls, drawerEntries, readCounts, appliedFilter, keysNow)
                    }
                }
                FtutterDrawerPanel(
                    showStatus = FtutterExtension.TAB_STATUS in extensions,
                    content = drawerContent,
                    rows = rows,
                    filter = drawerFilter,
                    closedCount = closedTabCount,
                    dismissDirection = tabSettings.dismissDirection,
                    design = design,
                    onContentChange = { drawerContent = it; savePreference(FtutterKeys.DRAWER_CONTENT, it.stored.toString()) },
                    onFilterChange = { drawerFilter = it },
                    onRestoreClosed = ::restoreClosed,
                    onOpen = { url ->
                        scope.launch { drawerState.close() }
                        openTabUrl(url, startAtTop = drawerContent == FtutterDrawerContent.HISTORY)
                    },
                    // 原作：「履歴」の行の長押しはカタログと同じメニュー（タブの行でも）。「タブ」の表示はタブのメニュー。
                    onLongPress = { url ->
                        if (drawerContent == FtutterDrawerContent.HISTORY) historyMenu = url
                        else tabDialog = url to true
                    },
                    onClose = ::closeTab,
                    onToggleProtect = { url -> tabState.toggleProtect(url) },
                    historyTools = FtutterExtension.HISTORY_TOOLS in extensions,
                    privacy = preferences.ftutterBool(FtutterKeys.IS_PRIVACY, false),
                    selectionReset = drawerState.isOpen,
                    onDeleteHistory = ::deleteHistoryUrls,
                    onDeleteAllHistory = ::deleteAllHistory
                )
            }
        ) {
            @Composable
            fun MixPane() {
                if (com.valoser.futacha.shared.compat.MixAvailability.enabled && mixId != null && mediaServices != null) {
                    val hidden = remember(preferences[FtutterKeys.HIDDEN_THREADS]) {
                        parseFtutterHiddenThreads(preferences[FtutterKeys.HIDDEN_THREADS]).toSet()
                    }
                    val words = if (commonNgOn) sharedCatalogNgWords else emptyList()
                    val projection: suspend (List<com.valoser.futacha.shared.mix.MixEntry>) -> List<com.valoser.futacha.shared.mix.MixEntry> = remember(ngRules, hidden, words) {
                        { entries ->
                            val job = kotlinx.coroutines.currentCoroutineContext()
                            ftutterMixEntries(entries, ngRules, hidden, words) { job.ensureActive() }
                        }
                    }
                    com.valoser.futacha.shared.ui.board.MixScreenHost(
                        mixId = mixId ?: "",
                        store = mediaServices.store,
                        repository = repository,
                        boards = boards,
                        stateStore = stateStore,
                        httpClient = mediaServices.httpClient,
                        onExit = { mixId = null; screen = FtutterScreen.Boards },
                        onSwitch = { mixId = it },
                        stateHolder = mixStateHolder,
                        fileSystem = mediaServices.fileSystem,
                        modePolicy = com.valoser.futacha.shared.ui.board.MixModePolicy.FTUTTER,
                        gridColumns = remember(preferences) { ftutterMixGridColumns(preferences) },
                        projectEntries = projection,
                        catalogWordNgEnabled = commonNgOn,
                        onOpen = { entry ->
                            openThread(entry.board, FtutterThreadRef(entry.board.id, entry.item.id,
                                title = entry.item.title.orEmpty(), thumbnailUrl = entry.item.thumbnailUrl.orEmpty(),
                                catalogReplyCount = entry.replyCountOrNull ?: 0), FtutterScreen.Boards)
                        }
                    )
                }
            }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().testTag("ftutter-root")) {
            val twoPane = ftutterIsTwoPane(tablet, maxWidth.value)
            androidx.compose.runtime.SideEffect { twoPaneNow = twoPane }
            // 二画面の列数は、ペインではなく画面全体の向きで縦・横を選ぶ（原作）。
            val tabletColumns = if (maxWidth > maxHeight) tablet.columnsLandscape else tablet.columnsPortrait
            if (com.valoser.futacha.shared.compat.MixAvailability.enabled && twoPane && ftutterShowsMixPane(screen, mixId) && mediaServices != null) {
                val thread = screen as? FtutterScreen.Thread
                val threadBoard = thread?.let { current -> boards.firstOrNull { it.id == current.boardId } }
                FtutterTwoPane(tablet,
                    catalog = { MixPane() },
                    thread = if (thread != null && threadBoard != null) { strip -> ThreadPane(threadBoard, thread, strip, true) } else null,
                    tabStrip = tabStrip)
            } else com.valoser.futacha.shared.ui.PushPopTransition(ftutterTransitionScreen(screen, twoPane, mixId != null)) { shown ->
                // 画面の切り替えは iOS の push のように横へ滑る（App Storeのレビュー 2026-11-16）。描く画面は shown から読む。
                when (val current = shown.value) {
                FtutterScreen.Boards -> if (com.valoser.futacha.shared.compat.MixAvailability.enabled && mixId != null && mediaServices != null) MixPane() else FtutterBoardListScreen(
                    onOpenMix = if (com.valoser.futacha.shared.compat.MixAvailability.enabled && mediaServices != null && com.valoser.futacha.shared.ui.board.mixOfferable(boards.map { it.url }, preferences[com.valoser.futacha.shared.mix.MIX_DEFINITIONS_KEY])) {
                        {
                            scope.launch {
                                mixId = com.valoser.futacha.shared.ui.board.firstMixIdOrCreate(
                                    mediaServices.store, boards.map { it.url }, kotlin.time.Clock.System.now().toEpochMilliseconds(), confirmMixMembers
                                ).idOrReport { notice = it }
                            }
                        }
                    } else null,
                    legacy = ftutterLegacyBoards(preferences),
                    boardsFromBottom = com.valoser.futacha.shared.ui.board.boardsFromBottom(preferences),
                    boards = boards,
                    httpClient = httpClient,
                    onOpenBoard = { openCatalog(it) },
                    onOpenSettings = { screen = FtutterScreen.Settings(FtutterScreen.Boards) },
                    onEditBoards = editBoards,
                    onBulkAdd = bulkAddBoards,
                    onOpenUrl = openUrl,
                    onNotice = { notice = it },
                    onOpenAimogeCalendar = if (com.valoser.futacha.shared.ui.aimoge.aimogeEventCalendarAvailable(boards.map { it.url })) {
                        { aimogeCalendarOpen = true }
                    } else null
                )
                is FtutterScreen.Catalog, is FtutterScreen.Thread -> {
                    val boardId = if (current is FtutterScreen.Catalog) current.boardId else (current as FtutterScreen.Thread).boardId
                    val board = boards.firstOrNull { it.id == boardId }
                    if (board == null) {
                        LaunchedEffect(Unit) { screen = FtutterScreen.Boards }
                    } else if (twoPane) {
                        // 二画面：左（入れ替えで右）は最後に開いたカタログ、反対側はスレッド。スレッドを開いても画面を積まない。
                        // カタログだけの時とスレッドを開いた時を同じ呼び出しで描く（再監査L27：別の場所で描いていたので、
                        // 最初のスレを開くとカタログが作り直され、検索などが消えた）。
                        val thread = current as? FtutterScreen.Thread
                        val catalogBoard = if (thread == null) board else boards.firstOrNull { it.id == lastCatalogBoardId } ?: board
                        val catalogScreen = current as? FtutterScreen.Catalog ?: FtutterScreen.Catalog(catalogBoard.id)
                        PlatformBackHandler(enabled = true) { handleBack(true) { screen = FtutterScreen.Boards } }
                        if (thread == null) {
                            // 原作：二画面でカタログを開くと、最後に開いたスレッドを反対側に出す（M21）。
                            val reopenId = ftutterTwoPaneReopenId(tabState.opening, tabState.urls, board,
                                preferences.ftutterBool(FTUTTER_LEGACY_TWO_PANE_EMPTY_KEY, false))
                            LaunchedEffect(board.id, reopenId) {
                                val id = reopenId ?: return@LaunchedEffect
                                if (screen != current) return@LaunchedEffect
                                lastCatalogBoardId = board.id
                                val title = com.valoser.futacha.shared.ui.futaber.findFutaberHistoryEntry(history, board, id)?.title.orEmpty()
                                openThread(board, FtutterThreadRef(board.id, id, title), current, refreshOnOpen = false, startAtTop = false)
                            }
                        }
                        FtutterTwoPane(
                            tablet,
                            catalog = { strip -> CatalogPane(catalogBoard, catalogScreen, tabletColumns, strip) },
                            thread = thread?.let { t -> { strip -> ThreadPane(board, t, strip, true) } },
                            tabStrip = tabStrip
                        )
                    } else if (current is FtutterScreen.Catalog) {
                        PlatformBackHandler(enabled = true) { handleBack(false) { screen = FtutterScreen.Boards } }
                        CatalogPane(board, current, null, tabStrip)
                    } else {
                        ThreadPane(board, current as FtutterScreen.Thread, tabStrip, false)
                    }
                }
                is FtutterScreen.HiddenAndNg -> {
                    PlatformBackHandler(enabled = true) { screen = current.back }
                    FtutterHiddenAndNgScreen(preferences, editPreference, onBack = { screen = current.back }, ngRemoved = catalogHolder.ngRemoved, ngReasons = catalogHolder.ngReasons)
                }
                is FtutterScreen.Settings -> {
                    com.valoser.futacha.shared.ui.board.SettingsFontScale(preferences) {
                        FtutterSettingsHost(
                            preferences = preferences,
                            save = savePreference,
                            edit = editPreference,
                            appVersion = appVersion,
                            boards = boards,
                            ngRules = ngRules,
                            onSaveNgRules = saveNgRules,
                            deleteKey = deleteKey.orEmpty(),
                            ngRemoved = catalogHolder.ngRemoved, ngReasons = catalogHolder.ngReasons,
                            services = mediaServices,
                            stateStore = stateStore,
                            onEditToolbar = { toolbarEditor = it },
                            onImportBackup = pickBackup,
                            onBack = { screen = current.back },
                            repository = repository,
                            onOpenThread = { target, threadId, title ->
                                openThread(target, FtutterThreadRef(target.id, threadId, title), current.back)
                            },
                            appScope = scope,
                            onNotice = { notice = it },
                            onArchiveReportEnabledChanged = onArchiveReportEnabledChanged
                        )
                    }
                }
            }
            }
            tabRefreshProgress?.let { (done, total) ->
                // 「タブを全て更新」の進み具合と中止（L131）。知らせの1段上に出す。
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 160.dp).testTag("ftutter-refresh-tabs-progress"),
                    action = {
                        androidx.compose.material3.TextButton(onClick = { tabRefreshJob?.cancel() },
                            modifier = Modifier.testTag("ftutter-refresh-tabs-cancel")) { Text("中止", color = colors.text) }
                    },
                    containerColor = colors.popUp,
                    contentColor = colors.text
                ) { Text("タブを更新しています $done/$total") }
            }
            val centred = !ftutterLegacyThreadLook(preferences)
            notice?.takeIf { centred }?.let { text ->
                // M7：原作の新着の知らせは、画面の中央やや下の濃い青のカプセル。may 1447424787：ほかの知らせも同じ所・同じ形に
                // まとめる（下に出るのと真ん中に出るのとがばらばらだった）。新着だけ青、ほかはポップアップの色。
                val newCount = ftutterIsNewCountNotice(text)
                Text(
                    text,
                    color = if (newCount) androidx.compose.ui.graphics.Color.White else colors.text,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(top = 240.dp, start = 24.dp, end = 24.dp)
                        .background(if (newCount) androidx.compose.ui.graphics.Color(0xFF1A3A8F) else colors.popUp,
                            androidx.compose.foundation.shape.RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 8.dp).testTag("ftutter-notice")
                )
            }
            notice?.takeIf { !centred }?.let { text ->
                // 従来の見た目：原作のトーストと同じく下寄せだが、下部のツールバー（最大80dp）を覆って押せなくしないよう、その上に出す。
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 96.dp).testTag("ftutter-notice"),
                    containerColor = colors.popUp,
                    contentColor = colors.text
                ) { Text(text) }
            }
        }
        }
        // ドロワーが開いている間の戻るは、ドロワーを閉じる。開いた時に登録し直すので、先に置いたカタログ・スレッドの
        // 戻るより先に受ける（ふたばー風の2026-10-07監査A10と同じ直し方。以前は最初に1度だけ登録していて負けていた）。
        if (drawerShowing) PlatformBackHandler(enabled = true) { scope.launch { drawerState.close() } }
        tabDialog?.let { (url, fromDrawer) ->
            FtutterTabDialog(
                protected = url in tabState.protected,
                fromDrawer = fromDrawer,
                onDismiss = { tabDialog = null },
                onAction = { action ->
                    tabDialog = null
                    when (action) {
                        FtutterTabAction.CLOSE -> closeTab(url)
                        FtutterTabAction.PROTECT -> tabState.toggleProtect(url)
                        FtutterTabAction.REFRESH_ALL -> refreshAllTabs()
                        FtutterTabAction.CLOSE_FALLEN, FtutterTabAction.CLOSE_AFTER, FtutterTabAction.CLOSE_ALL -> {
                            closeMany(ftutterSharedDrawerCloseTargets(action, url,
                                if (fromDrawer) drawerContent else FtutterDrawerContent.TABS,
                                historyUrls, tabState.urls, emptyList(), sharedTabEntries + historyByUrl))
                        }
                    }
                }
            )
        }
        historyMenu?.let { url ->
            FtutterThumbDialog(
                hasHistory = historyByUrl[url] != null,
                onDismiss = { historyMenu = null },
                onAction = { action ->
                    historyMenu = null
                    val board = ftutterBoardForUrl(boards, url)
                    val title = historyByUrl[url]?.title.orEmpty()
                    when (action) {
                        FtutterThumbAction.HIDE -> editPreference(FtutterKeys.HIDDEN_THREADS) { stored ->
                            val (next, dropped) = ftutterHiddenThreadsWith(parseFtutterHiddenThreads(stored), url)
                            notice = ftutterHiddenNotice(dropped)
                            encodeFtutterList(next)
                        }
                        FtutterThumbAction.DELETE_HISTORY -> historyDeleteConfirm = url
                        FtutterThumbAction.ADD_TAB -> { tabState.push(url); notice = "タブに追加しました" }
                        FtutterThumbAction.DEL -> if (board == null) notice = "登録していない板です" else historyDelConfirm = url
                        FtutterThumbAction.PICKUP, FtutterThumbAction.NG_TITLE -> historyNgDraft = FtutterNgRule(
                            title,
                            if (action == FtutterThumbAction.PICKUP) FtutterNgType.PICKUP.stored else FtutterNgType.TITLE.stored,
                            ita = board?.let { FtutterNgBoard(it.name, it.url) }
                        )
                    }
                }
            )
        }
        historyDeleteConfirm?.let { url ->
            FtutterHistoryDeleteConfirm(historyByUrl[url]?.title.orEmpty(), onDismiss = { historyDeleteConfirm = null }) {
                historyDeleteConfirm = null
                deleteHistoryUrls(listOf(url))
            }
        }
        historyDelConfirm?.let { url ->
            FtutterConfirmDialog(
                title = "delを送りますか？",
                message = "${historyByUrl[url]?.title.orEmpty()}\n掲示板の管理側へ削除依頼（del）を送ります。",
                confirmLabel = "送る",
                onDismiss = { historyDelConfirm = null }
            ) {
                historyDelConfirm = null
                val board = ftutterBoardForUrl(boards, url)
                val threadId = ftutterThreadIdFromUrl(url)
                if (board != null && threadId != null) sendThreadDel(board, threadId)
            }
        }
        historyNgDraft?.let { draft ->
            FtutterNgDialog(
                initial = draft,
                boards = boards,
                existing = ngRules,
                onDismiss = { historyNgDraft = null },
                legacy = preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false),
                onRejected = { notice = it },
                onSave = { rule ->
                    historyNgDraft = null
                    saveNgRules({ it + rule }, if (preferences.ftutterBool(FTUTTER_LEGACY_DIALOGS_KEY, false)) "登録しました" else null)
                }
            )
        }
        protectedConfirm?.let { url ->
            FtutterProtectedCloseDialog(
                onCancel = { protectedConfirm = null },
                onConfirm = { protectedConfirm = null; closeTabNow(url) }
            )
        }
        toolbarEditor?.let { kind ->
            FtutterOrderEditorHost(kind, preferences, savePreference, onClose = { toolbarEditor = null })
        }
        if (backupLoading != null) {
            FutachaAppLockAwareWindow { androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                modifier = Modifier.testTag("ftutter-backup-loading"),
                title = { Text("読み込み中…") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Text("ふたったーのバックアップを読み込んでいます", modifier = Modifier.padding(start = 16.dp))
                    }
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { backupLoading?.cancel(); backupLoading = null },
                        modifier = Modifier.testTag("ftutter-backup-loading-cancel")) { Text("キャンセル") }
                }
            ) }
        }
        backupPlan?.let { plan ->
            FutachaAppLockAwareWindow { androidx.compose.material3.AlertDialog(
                onDismissRequest = { backupPlan = null },
                modifier = Modifier.testTag("ftutter-backup-confirm"),
                title = { Text("ふたったーのバックアップを取り込みますか？") },
                // 監査L14：取り込んだ履歴は今の履歴の上に並ぶ（確認文と実際を合わせた）。
                text = { Text("${plan.summary}\n\n今の設定・板・履歴は残し、足りないものを足します（同じ設定は取り込んだ値になります）。取り込んだ履歴は、今の履歴の上に並びます。",
                    modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { backupPlan = null; applyBackup(plan) },
                        modifier = Modifier.testTag("ftutter-backup-confirm-ok")) { Text("取り込む") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { backupPlan = null }) { Text("キャンセル") } }
            ) }
        }
        postRequest?.let { (target, quote) ->
            val board = boards.firstOrNull { it.id == target.boardId }
            if (board == null) {
                postRequest = null
            } else {
                val draftKey = ftutterDraftKey(target, board.url)
                val legacyPostDialog = ftutterLegacyPostDialog(preferences)
                val drafts = remember(draftKey) { parseFtutterDrafts(preferences[FtutterKeys.DRAFTS]) }
                var capabilities by remember(draftKey) { mutableStateOf(com.valoser.futacha.shared.network.defaultBoardPostingCapabilities(board.url)) }
                LaunchedEffect(draftKey) {
                    if (board.isMockBoard()) return@LaunchedEffect
                    try {
                        capabilities = repository.getPostingCapabilities(ftutterNetworkUrl(board.url), (target as? FtutterPostTarget.Reply)?.threadId)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        // 取れなければ板の既定の上限のまま。
                    }
                }
                FtutterPostDialog(
                    target = target,
                    boardUrl = board.url,
                    boardName = board.name,
                    initialDraft = drafts[draftKey] ?: FtutterDraft(),
                    quote = quote,
                    deleteKey = deleteKey.orEmpty(),
                    confirmBeforePost = preferences.ftutterBool(FtutterKeys.CONFIRM_BEFORE_POST, false),
                    capabilities = capabilities,
                    pickerPreference = pickerPreference,
                    preferredFileManagerPackage = preferredFileManager?.packageName,
                    deviceInfo = {
                        val platform = com.valoser.futacha.shared.ui.compat.compatPostDeviceInfo(appVersion)
                        if (legacyPostDialog) com.valoser.futacha.shared.ui.futaber.futaberDeviceInfoText(platform)
                        else ftutterDeviceInfoText(appVersion, platform)
                    },
                    uploadUps = httpClient?.let { client ->
                        { image, key -> com.valoser.futacha.shared.ui.compat.uploadCompatUps(client, image, "", key, appVersion) }
                    },
                    onDeleteKeyChange = { savePreference(FtutterKeys.DELETE_KEY, it) },
                    onSaveDraft = { draft, announce ->
                        // 下書きは件数・長さ・全体の大きさに収めて残す（監査A9）。切った・古いものを落とした時は、閉じた時だけ知らせる。
                        // 保存されている下書きに当てる（監査M5：打っている間も残すので、画面の古い値から作ると他の下書きを戻してしまう）。
                        editPreference(FtutterKeys.DRAFTS) { stored ->
                            val update = ftutterDraftsUpdate(parseFtutterDrafts(stored), draftKey, draft)
                            if (announce) ftutterDraftsNotice(update)?.let { notice = it }
                            if (update.encoded == stored) null else update.encoded
                        }
                    },
                    onSend = { input -> sendPost(target, input) },
                    onSent = { posted ->
                        postRequest = null
                        failedAttachment = null
                        when (target) {
                            is FtutterPostTarget.Reply -> {
                                countStat(FtutterKeys.STATS_REPLIED)
                                notice = "書き込みました"
                                threadRefreshSignal++
                            }
                            is FtutterPostTarget.Create -> {
                                countStat(FtutterKeys.STATS_CREATED)
                                catalogHolder.afterThreadCreated()
                                notice = "書き込みました"
                                // スレ立ての後は新しいスレッドを開く（原作は新順のカタログから探して開く）。
                                if (!posted.isNullOrBlank()) openThread(board, FtutterThreadRef(board.id, posted, title = ""), FtutterScreen.Catalog(board.id))
                                else catalogHolder.refreshTick++
                            }
                        }
                    },
                    onDismiss = { postRequest = null; failedAttachment = null },
                    legacyDialog = legacyPostDialog,
                    initialImage = failedAttachment?.takeIf { it.first == draftKey }?.second,
                    onFailed = { toast, image ->
                        failedAttachment = image?.let { draftKey to it }
                        postRequest = null
                        notice = toast.text
                        // 原作はipの拒否の後に、続けて別のトーストで案内を出す（M37）。
                        toast.followUp?.let { followUp ->
                            scope.launch {
                                delay(FTUTTER_POST_FOLLOW_UP_DELAY_MS)
                                notice = followUp
                            }
                        }
                    },
                    handwritingEnabled = FtutterExtension.HANDWRITING in extensions,
                    legacyMenus = ftutterLegacyMenus(preferences),
                    onHandwritingSaved = { image ->
                        // 失敗の文は書き込み画面の中に出す（L22。アプリの知らせはダイアログの背面に隠れ、4秒で消えていた）。
                        // コピーはこのモードのスコープで書き、書き込み画面が先に閉じても最後まで書く。その時の失敗はアプリの知らせ（L39）。
                        val fs = mediaServices?.fileSystem
                        val location = com.valoser.futacha.shared.ui.futaber.futaberDrawingCopyLocation(preferences)
                        if (fs == null || location == null) null else {
                            val copy = kotlinx.coroutines.CompletableDeferred<String?>()
                            com.valoser.futacha.shared.ui.compat.launchCompatDrawingCopy(scope, fs, location, image,
                                kotlin.time.Clock.System.now().toEpochMilliseconds()) { copy.complete(FTUTTER_HANDWRITING_COPY_FAILURE) }
                                ?.invokeOnCompletion { copy.complete(null) }
                            try {
                                copy.await()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                scope.launch { copy.await()?.let { notice = it } }
                                throw cancelled
                            }
                        }
                    },
                    // アプリと一緒に消えた手書きを、次に開いた時に復元できるよう残す（L69）。
                    drawingRecoveryFileSystem = mediaServices?.fileSystem
                )
            }
        }
    }
}

/** タブの状態（メモリにも持ち、保存が追いつく前の続けての操作でも崩れないようにする）。閉じたタブはメモリだけ。 */
/** 落ちたスレの印（共有の履歴の`isAutoRefreshDisabled`。ふたばー風の「スレ落ちしたタブ」と同じ）。 */
private suspend fun markFallen(stateStore: AppStateStore, boardId: String, threadId: String) {
    try {
        stateStore.updateHistory { list ->
            list.map { if (it.threadId == threadId && it.boardId == boardId) ftutterHistoryEntryMarkFallen(it) else it }
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to mark a fallen thread", error)
    }
}

/** 原作の上部バー：端まで続く平らな帯。 */
@Composable
internal fun FtutterTopBar(
    title: String,
    /** 原作のスレッドの上部バー（H8）：題名の下の小さな2行目。ある時は題名とまとめて中央に小さく出す。 */
    subtitle: String? = null,
    /** 原作の形の更新の失敗：題名の代わりに「！」だけを出す。値は読み上げの理由。 */
    failed: String? = null,
    onBack: (() -> Unit)? = null,
    /** 設定「左上にドロワーを開くボタンを表示」がONの時に渡す。 */
    onOpenDrawer: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val colors = LocalFtutterColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.toolBar).statusBarsPadding().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onOpenDrawer != null) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("ftutter-topbar-drawer")) {
                Icon(Icons.Rounded.Menu, contentDescription = "ドロワーを開く", tint = colors.onToolBar)
            }
        }
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("ftutter-back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る", tint = colors.onToolBar)
            }
        } else {
            Box(Modifier.padding(start = 16.dp))
        }
        if (failed != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Error, contentDescription = failed, tint = colors.onToolBar,
                    modifier = Modifier.testTag("ftutter-thread-title-failed"))
            }
        } else if (subtitle != null) {
            Column(Modifier.weight(1f).padding(horizontal = 4.dp).testTag("ftutter-thread-title"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = colors.onToolBar, fontSize = 15.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(subtitle, color = colors.onToolBar, fontSize = 12.sp, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        } else Text(
            title,
            color = colors.onToolBar,
            fontSize = 22.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        actions()
    }
}

/** スレッド画面と、履歴・読み位置・既読・外部アーカイブ通知の保存をつなぐ。 */
@Composable
private fun FtutterThreadHost(
    board: BoardSummary,
    ref: FtutterThreadRef,
    initialPage: com.valoser.futacha.shared.model.ThreadPage?,
    refreshOnOpen: Boolean,
    startAtTop: Boolean,
    openingVisit: com.valoser.futacha.shared.ui.board.ThreadOpeningVisit,
    onPageSnapshot: (com.valoser.futacha.shared.model.ThreadPage) -> Unit,
    onThreadPageRead: suspend (String, FtutterTabSeed) -> Unit,
    repository: BoardRepository,
    httpClient: io.ktor.client.HttpClient?,
    stateStore: AppStateStore,
    history: List<ThreadHistoryEntry>,
    preferences: Map<String, String>,
    design: FtutterDesignSettings,
    selfPostMap: Map<String, List<String>>,
    store: com.valoser.futacha.shared.compat.CompatibilityStore?,
    onArchiveReportEnqueued: ((Int) -> Unit)?,
    savePreference: (String, String) -> Unit,
    editPreference: (String, (String?) -> String?) -> Unit,
    onOpenThreadUrl: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onFallen: () -> Unit,
    tabOnTop: Boolean,
    tabStrip: @Composable () -> Unit,
    ngRules: List<FtutterNgRule>,
    boards: List<BoardSummary>,
    onSaveNgRules: FtutterNgEdit,
    manualMarks: List<com.valoser.futacha.shared.compat.ManualPostMark>,
    deleteKey: String,
    onReply: (String?) -> Unit,
    refreshSignal: Int,
    gesture: FtutterGestureSettings,
    onAppGesture: (FtutterGestureAction) -> Unit,
    onAppMenuGesture: (FtutterGestureAction) -> Unit,
    onSwipeTab: (Boolean) -> Unit,
    onEditToolbar: () -> Unit,
    mediaServices: FutaberMediaServices?,
    onSoudaneSent: () -> Unit,
    extensions: FtutterExtensions = FtutterExtensions(),
    commonNgWords: List<String> = emptyList(),
    commonNgHeaders: List<String> = emptyList(),
    compatNgRules: List<CompatNgRule> = emptyList(),
    threadActions: FtutterThreadActions? = null
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val colors = LocalFtutterColors.current
    // 画像の保存・画像検索・スレの保存は、ふたちゃ・ふたばー風と同じ共有の部品と保存先。
    val sharedFeatures = com.valoser.futacha.shared.ui.futaber.rememberFutaberSharedFeatures(mediaServices, repository)
    val mediaTab = remember(board, ref.threadId) {
        com.valoser.futacha.shared.ui.futaber.futaberMediaTab(
            board, com.valoser.futacha.shared.ui.futaber.FutaberThreadRef(board.id, ref.threadId, ref.title, ref.thumbnailUrl, 0), 0L
        )
    }
    val mediaStore = mediaServices?.store
    val saver = if (mediaStore != null) {
        com.valoser.futacha.shared.ui.futaber.rememberFutaberMediaSaver(
            mediaStore, preferences, mediaServices.httpClient, mediaServices.fileSystem, mediaTab.boardKey, mediaTab.key
        ) { onNotice(it) }
    } else null
    val destinationKey = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("storage", "dummyDownloadDir")
    val chooseDestination = com.valoser.futacha.shared.ui.board.rememberDirectoryPickerLauncher(
        onDirectorySelected = { location ->
            val target = mediaStore ?: return@rememberDirectoryPickerLauncher
            scope.launch {
                runCatching {
                    target.savePreference(destinationKey, with(com.valoser.futacha.shared.model.SaveLocation.Companion) { location.toRawString() })
                }.onFailure { onNotice("保存先を記録できませんでした") }
            }
        }
    )
    val saveService = remember(mediaServices) {
        val client = mediaServices?.httpClient
        val fs = mediaServices?.fileSystem
        if (client != null && fs != null) com.valoser.futacha.shared.service.SingleMediaSaveService(client, fs) else null
    }
    val currentPreferencesForSave by rememberUpdatedState(preferences)
    val mediaTools = FtutterMediaSaveTools(
        destinationLabel = ftutterDestinationLabel(com.valoser.futacha.shared.ui.compat.parseCompatSaveLocation(preferences[destinationKey])),
        chooseDestination = chooseDestination,
        saveOne = { url -> saver?.saveOrShare(url, shareAfterSave = false) ?: onNotice("保存機能を初期化できませんでした") },
        saveMany = { urls, onProgress ->
            val service = saveService
            val fs = mediaServices?.fileSystem
            if (service == null || fs == null) FtutterBatchSaveResult(0, urls.size, "保存機能を初期化できませんでした") else {
                val location = com.valoser.futacha.shared.ui.compat.parseCompatSaveLocation(currentPreferencesForSave[destinationKey])
                ftutterSaveMediaBatch(service, fs, urls, mediaTab.boardKey, mediaTab.key, location, mediaTab.title, onProgress)
            }
        },
        isSaving = saver?.isSaving == true
    )
    var searchUrl by remember { mutableStateOf<String?>(null) }
    var searchStart by remember { mutableStateOf<com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget?>(null) }
    var savePage by remember { mutableStateOf<com.valoser.futacha.shared.model.ThreadPage?>(null) }
    val features = sharedFeatures
    searchUrl?.let { url ->
        val client = mediaServices?.httpClient
        if (client != null && features != null) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides ftutterMediaPalette(colors),
                com.valoser.futacha.shared.ui.board.LocalFutachaSharedFeatures provides features
            ) {
                com.valoser.futacha.shared.ui.board.ThreadImageSearchDialog(url, client, mediaServices.cookieRepository,
                    startTarget = searchStart,
                    onDismiss = { searchUrl = null; searchStart = null }, openExternal = rememberFtutterUrlLauncher(preferences), preferredTarget = if (preferences.ftutterBool(FtutterKeys.IMAGE_SEARCH_ASCII2D, false))
                        com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget.ASCII2D_URL else com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget.GOOGLE_URL)
            }
        } else {
            LaunchedEffect(url) { searchUrl = null; onNotice("画像検索は使えません") }
        }
    }
    savePage?.let { page ->
        if (features != null) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides ftutterMediaPalette(colors)
            ) {
                FtutterHtmlSaveDialog(
                    preferences = preferences, save = savePreference,
                    features = features,
                    page = page,
                    boardKey = com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey(board),
                    boardName = board.name,
                    boardUrl = com.valoser.futacha.shared.ui.futaber.futaberCanonicalBoardUrl(board),
                    title = ref.title.ifBlank { page.posts.firstOrNull()?.let { com.valoser.futacha.shared.util.resolveThreadTitle(it, "") }.orEmpty() },
                    onDismiss = { savePage = null }
                )
            }
        } else {
            LaunchedEffect(page) { savePage = null; onNotice("保存機能を初期化できませんでした") }
        }
    }
    // 拡張「画像の長押しメニュー」の画像NG登録（ふたちゃ・ふたばー風と共有のルール）と、似た画像の判定。
    val boardKeyForNg = remember(board) { com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey(board) }
    val tabKeyForNg = remember(board, ref.threadId) { com.valoser.futacha.shared.ui.futaber.futaberCompatTabKey(board, ref.threadId) }
    val imageMenuOn = FtutterExtension.IMAGE_MENU in extensions
    val phashRules = remember(compatNgRules, boardKeyForNg, tabKeyForNg, imageMenuOn) {
        if (!imageMenuOn) emptyList() else compatNgRules.filter {
            it.kind == com.valoser.futacha.shared.compat.CompatNgKind.THREAD_IMAGE_PHASH &&
                it.appliesToThreadImage(boardKeyForNg, tabKeyForNg)
        }
    }
    val sharedPreferences by (mediaServices?.store?.preferences ?: kotlinx.coroutines.flow.flowOf(emptyMap<String, String>()))
        .collectAsState(emptyMap())
    val phashThreshold = sharedPreferences[com.valoser.futacha.shared.ui.futaber.futaberImageNgThresholdKey]?.toIntOrNull()
        ?: com.valoser.futacha.shared.compat.CompatImagePhash.DEFAULT_THRESHOLD
    var imageNgTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    imageNgTarget?.let { (imageUrl, postId) ->
        val registerStore = mediaServices?.store
        if (features != null && registerStore != null) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides ftutterMediaPalette(colors)
            ) {
                com.valoser.futacha.shared.ui.board.FutachaImageNgRegistration(
                    features, boardKeyForNg, com.valoser.futacha.shared.compat.CompatImageNgSource.THREAD, imageUrl, "No.$postId",
                    { imageNgTarget = null },
                    // 「この板のみ」の時は、このモードの板を共有ストアへ取り込んでから書く（ふたばー風と同じ）。
                    prepareBoardScope = com.valoser.futacha.shared.ui.futaber.futaberCatalogImageNgPreparation(registerStore, listOf(board))
                )
            }
        } else {
            LaunchedEffect(imageUrl) { imageNgTarget = null; onNotice("画像NGは使えません") }
        }
    }
    // 拡張「MHTで保存」：形式を選んで、ふたちゃ・ふたばー風と共有のMHTの保存へ。
    var mhtPage by remember { mutableStateOf<com.valoser.futacha.shared.model.ThreadPage?>(null) }
    var mhtFull by remember { mutableStateOf<Boolean?>(null) }
    val mhtFileSystem = mediaServices?.fileSystem
    mhtPage?.let { page ->
        val full = mhtFull
        if (mhtFileSystem == null) {
            LaunchedEffect(page) { mhtPage = null; onNotice("MHTの保存は使えません") }
        } else if (full == null) {
            FutachaAppLockAwareWindow { androidx.compose.material3.AlertDialog(
                onDismissRequest = { mhtPage = null },
                title = { androidx.compose.material3.Text("MHTで保存") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.TextButton(onClick = { mhtFull = false }, modifier = Modifier.testTag("ftutter-mht-thumbs")) {
                            androidx.compose.material3.Text("サムネイルのみ")
                        }
                        androidx.compose.material3.TextButton(onClick = { mhtFull = true }, modifier = Modifier.testTag("ftutter-mht-full")) {
                            androidx.compose.material3.Text("本体画像もダウンロードする")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { androidx.compose.material3.TextButton(onClick = { mhtPage = null }) { androidx.compose.material3.Text("キャンセル") } },
                modifier = Modifier.testTag("ftutter-mht-choice")
            ) }
        } else {
            val mhtClient = mediaServices?.httpClient
            val library = remember(mhtFileSystem, mhtClient) {
                com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary(mhtFileSystem, mhtClient)
            }
            val request = remember(page) {
                com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtSaveRequest(
                    boardKey = boardKeyForNg,
                    boardName = board.name,
                    boardUrl = com.valoser.futacha.shared.ui.futaber.futaberCanonicalBoardUrl(board),
                    threadId = ref.threadId,
                    title = ref.title.ifBlank { page.posts.firstOrNull()?.let { com.valoser.futacha.shared.util.resolveThreadTitle(it, "") }.orEmpty() },
                    threadUrl = ftutterThreadUrl(board, ref.threadId),
                    page = page
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(
                com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides ftutterMediaPalette(colors)
            ) {
                com.valoser.futacha.shared.ui.board.FutachaMhtSaveRun(library, request, full) { mhtPage = null; mhtFull = null }
            }
        }
    }
    val entry = remember(history, board.id, ref.threadId) {
        com.valoser.futacha.shared.ui.futaber.findFutaberHistoryEntry(history, board, ref.threadId)
    }
    // 開いた時の履歴（読み位置）。開いている間に書き換わっても、復元には最初の値を使う。
    val openedEntry = remember(board.id, ref.threadId) { entry }
    val threadUrl = remember(board, ref.threadId) { ftutterCanonicalThreadUrl(ftutterThreadUrl(board, ref.threadId)) }
    val currentPreferences by rememberUpdatedState(preferences)
    // 保存は原作の`kokomade`と同じ「読んだ返信の数」（OPを含まない）。画面は「読んだレスの数」（OPを含む）で扱う。
    val storedRead = remember(board.id, ref.threadId) {
        parseFtutterReadCounts(preferences[FtutterKeys.READ_COUNTS])[threadUrl]?.let(::ftutterPostsReadFromStored)
    }
    openingVisit.initialize(storedRead,
        ftutterReadBoundaryId(preferences[FTUTTER_READ_BOUNDARIES_KEY], threadUrl, storedRead)
            ?: openedEntry?.lastViewedSnapshot?.lastPostId?.takeIf { openedEntry.lastViewedSnapshot?.postCount == storedRead })
    val selfIds = remember(selfPostMap, board.id, ref.threadId) {
        com.valoser.futacha.shared.ui.board.buildSelfPostIdentifierSet(
            com.valoser.futacha.shared.ui.board.buildPersistedSelfPostIdentifiers(
                selfPostMap,
                com.valoser.futacha.shared.ui.board.buildThreadScopedSelfPostKey(board.id, ref.threadId),
                ref.threadId
            )
        )
    }
    fun launchStore(what: String, block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) { com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to $what", error) }
        }
    }
    // 画面を離れる時の書き込み（読み位置）。この画面ごと外れる時（設定を開く・戻る・モードの切り替え）も同じ破棄の中で
    // [scope]が取り消されるので、すぐに始めて取り消されない形で最後まで書く（2026-10-14の監査B：設定から戻ると読み位置が消えた）。
    fun launchLastingStore(what: String, block: suspend () -> Unit) {
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Throwable) { com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to $what", error) }
            }
        }
    }
    val reportedPostNgRules = rememberFtutterReportedPostNgRules(compatNgRules)
    // スレッドごとに画面を作り直す（監査A2）。同じ位置のまま別のスレッドへ替えると、離れる時の保存が新しいスレッドの
    // ものへ差し替わってから呼ばれ、前のスレッドの読み位置・既読数が保存されなかった。作り直せば、前のスレッドの画面は
    // 前のスレッドの状態と保存先のまま破棄される。
    androidx.compose.runtime.key(board.id, ref.threadId) {
    FtutterThreadScreen(
        board = board,
        ref = ref,
        initialPage = initialPage,
        refreshOnOpen = refreshOnOpen,
        startAtTop = startAtTop,
        onPageSnapshot = onPageSnapshot,
        repository = repository,
        httpClient = httpClient,
        preferences = preferences,
        design = design,
        history = openedEntry,
        confirmedFallen = ftutterHistoryIsFallen(entry),
        selfIds = selfIds,
        storedReadCount = storedRead,
        openingTracker = openingVisit.tracker,
        onReadBoundary = { count, postId ->
            editPreference(FTUTTER_READ_BOUNDARIES_KEY) { stored ->
                ftutterReadBoundariesAfter(stored, threadUrl, count, postId, ftutterReadRecordsToKeep(preferences))
            }
        },
        savePreference = savePreference,
        onReadCount = { count ->
            // 保存されている記録に当てる（監査M2：スレを切り替えた直後に、前のスレの記録が古い値で上書きされて消えていた）。
            editPreference(FtutterKeys.READ_COUNTS) { stored ->
                val counts = parseFtutterReadCounts(stored)
                val next = ftutterReadCountAfter(counts[threadUrl], ftutterStoredFromPostsRead(count))
                if (counts[threadUrl] == next) null else {
                    // 新しい記録を先頭に置き、保存値に入らない分は今のタブ・保護したスレ以外の古いものから落とす（L129）。
                    val updated = linkedMapOf(threadUrl to next).apply { counts.forEach { (k, v) -> if (k != threadUrl) put(k, v) } }
                    encodeFtutterReadCounts(ftutterTrimReadCounts(updated, ftutterReadRecordsToKeep(preferences) + threadUrl))
                }
            }
        },
        onPageLoaded = { posts ->
            launchStore("update history after load") {
                stateStore.updateHistory { ftutterHistoryAfterLoad(it, board.id, ref.threadId, posts) }
            }
            ftutterTabSeedFromPage(ref.title, posts)?.let { page ->
                launchStore("fill the tab from the page") { onThreadPageRead(ftutterCanonicalThreadUrl(ftutterThreadUrl(board, ref.threadId)), page) }
            }
        },
        onLivePageConfirmed = {
            launchStore("mark the thread alive") {
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                stateStore.updateHistory { ftutterHistoryMarkAlive(it, board.id, ref.threadId, now) }
            }
            // 閲覧スレの外部アーカイブ通知（他の3モードと同じ共有キー・既定OFF）。
            launchStore("note the viewed thread") {
                com.valoser.futacha.shared.ui.futaber.futaberNoteViewedThread(store, currentPreferences, board, ref.threadId, onArchiveReportEnqueued)
            }
        },
        onSavePosition = { index, offset, postId, total ->
            launchLastingStore("save reading position") {
                stateStore.updateHistoryScrollPositionImmediately(
                    com.valoser.futacha.shared.state.AppStateHistoryScrollUpdateRequest(
                        threadId = ref.threadId,
                        index = index,
                        offset = offset,
                        postId = postId,
                        boardId = board.id,
                        title = entry?.title ?: ref.title,
                        titleImageUrl = entry?.titleImageUrl ?: ref.thumbnailUrl,
                        boardName = board.name,
                        boardUrl = ftutterThreadUrl(board, ref.threadId),
                        replyCount = total
                    )
                )
            }
        },
        onOpenThreadUrl = onOpenThreadUrl,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        onNotice = onNotice,
        onOpenDrawer = onOpenDrawer,
        onFallen = onFallen,
        tabOnTop = tabOnTop,
        tabStrip = tabStrip,
        ngRules = ngRules,
        boards = boards,
        onSaveNgRules = onSaveNgRules,
        manualMarks = remember(manualMarks, threadUrl) {
            val markUrl = com.valoser.futacha.shared.compat.manualMarkThreadUrl(threadUrl)
            manualMarks.filter { it.threadUrl == markUrl }.map { it.postNo }.toSet()
        },
        onToggleMark = { postId, marked ->
            val markStore = store
            if (markStore != null) launchStore("toggle a mark") {
                com.valoser.futacha.shared.compat.manualPostMarksDroppedNotice(
                    com.valoser.futacha.shared.compat.setManualPostMark(markStore, threadUrl, postId, marked)
                )?.let(onNotice)
            }
        },
        deleteKey = deleteKey,
        onReply = onReply,
        refreshSignal = refreshSignal,
        gesture = gesture,
        onAppGesture = onAppGesture,
        onAppMenuGesture = onAppMenuGesture,
        onSwipeTab = onSwipeTab,
        onEditToolbar = onEditToolbar,
        mediaTools = mediaTools,
        onSearchImage = { searchStart = null; searchUrl = it },
        onSearchImageWith = { url, engine ->
            searchStart = when (engine) { FtutterViewerSearchEngine.ASCII2D -> com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget.ASCII2D_URL }
            searchUrl = url
        },
        onSaveThread = { savePage = it },
        onSoudaneSent = onSoudaneSent,
        extensions = extensions,
        commonNgWords = if (FtutterExtension.COMMON_NG in extensions) commonNgWords else emptyList(),
        commonNgHeaders = if (FtutterExtension.COMMON_NG in extensions) commonNgHeaders else emptyList(),
        phashRules = phashRules,
        phashThreshold = phashThreshold,
        compatStore = mediaServices?.store,
        onRegisterImageNg = { url, postId -> imageNgTarget = url to postId },
        onSaveMht = { mhtPage = it; mhtFull = null },
        historyStateStore = stateStore,
        reportedPostNgRules = reportedPostNgRules,
        aiStateStore = if (FtutterExtension.AI in extensions) stateStore else null,
        threadActions = threadActions
    )
    }
}

/** 既読の件数を覚えておくスレッドの数（原作の履歴の上限200に合わせる）。 */
/**
 * 他モードの「del＋レスNG」（URL範囲のレス番号NG）。ルールが変わった時だけ作る（L25：設定の保存で画面が組み直されるたびに、
 * URLの正規化つきの走査と新しいリストが作られ、受け手の再合成も省けなかった）。
 */
@Composable
internal fun rememberFtutterReportedPostNgRules(compatNgRules: List<CompatNgRule>): List<CompatNgRule> =
    remember(compatNgRules) {
        compatNgRules.filter {
            it.kind == com.valoser.futacha.shared.compat.CompatNgKind.THREAD_POST_NO &&
                com.valoser.futacha.shared.compat.compatPostNgScopeBoardKey(it.scopeKey) != null
        }
    }

/** 既読の記録を優先して残すスレ：今のタブと保護したスレ（L129）。 */
internal fun ftutterReadRecordsToKeep(preferences: Map<String, String>): Set<String> =
    parseFtutterTabs(preferences).urls.toHashSet().apply { addAll(parseFtutterProtected(preferences)) }

/** 下に出す通知。同じ文でも、出すたびに[serial]が増える（監査L7）。 */
internal class FtutterNoticeHolder {
    private var text by mutableStateOf<String?>(null)
    var serial by mutableIntStateOf(0)
        private set

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String? = text

    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String?) {
        text = value
        if (value != null) serial++
    }
}
