package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.shownSiteTabs
import com.valoser.futacha.shared.model.CatalogMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.buildCompatCatalogRuleIndex
import com.valoser.futacha.shared.compat.compatCatalogRulesForBoard
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.compat.normalizeCompatNgValue
import com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository
import com.valoser.futacha.shared.ui.compat.fetchDefaultCompatBoardsFromMenu
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.ui.compat.compatManualSaveLocation
import com.valoser.futacha.shared.ui.compat.createCompatSavedThreadRepository
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.ui.compat.CompatWatcherManager
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtBox
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtEntry
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary
import com.valoser.futacha.shared.ui.futaber.mht.futaberBoardForMht
import com.valoser.futacha.shared.ui.futaber.mht.futaberClearSavedBox
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtCanPost
import com.valoser.futacha.shared.ui.futaber.mht.rememberFutaberMhtPickerLauncher
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.state.AppStateHistoryScrollUpdateRequest
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.isMockBoard
import com.valoser.futacha.shared.ui.board.requireWritableThreadBoard
import com.valoser.futacha.shared.ui.board.performThreadReplyAction
import com.valoser.futacha.shared.ui.board.performThreadAction
import com.valoser.futacha.shared.ui.board.normalizeDeleteKeyForSubmit
import com.valoser.futacha.shared.ui.board.checkPostingNoticeIfNeeded
import com.valoser.futacha.shared.ui.board.buildThreadReplyActionConfig
import com.valoser.futacha.shared.ui.board.buildThreadReplyActionCallbacks
import com.valoser.futacha.shared.ui.board.buildThreadActionFailureMessage
import com.valoser.futacha.shared.ui.board.ThreadReplyDraft
import com.valoser.futacha.shared.ui.board.ThreadActionRunResult
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.dismissHistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val FUTABER_TAG = "FutaberApp"

/** Share of the width the board list takes; the main screen slides right by the same amount. */
internal const val FUTABER_DRAWER_WIDTH_FRACTION = 0.7f

private val FutaberThreadRefSaver = Saver<FutaberThreadRef?, List<String>>(
    save = { ref ->
        if (ref == null) emptyList()
        else listOf(
            ref.boardId, ref.threadId, ref.title, ref.thumbnailUrl, ref.replyCount.toString(),
            ref.seenCount.toString(), ref.resumePostId, ref.resumeIndex.toString(), ref.resumeOffset.toString(), ref.seenLastPostId.orEmpty(), ref.keepPosition.toString()
        )
    },
    restore = { parts ->
        if (parts.size !in 9..11) null
        else FutaberThreadRef(
            parts[0], parts[1], parts[2], parts[3], parts[4].toIntOrNull() ?: 0,
            parts[5].toIntOrNull() ?: 0, parts[6], parts[7].toIntOrNull() ?: 0, parts[8].toIntOrNull() ?: 0, parts.getOrNull(9)?.takeIf { it.isNotEmpty() }, parts.getOrNull(10) == "true"
        )
    }
)

private val FutaberPostTargetSaver = Saver<FutaberPostTarget?, List<String>>(
    save = { futaberPostTargetToStrings(it) },
    restore = { futaberPostTargetFromStrings(it) }
)

/**
 * Root of the ふたばー mode: board drawer -> catalog -> thread. It reads and writes the
 * same boards and history as ふたちゃ (AppStateStore); its own choices (layout, sort,
 * theme, last board) live under `compat.futaber.*`.
 */
@OptIn(ExperimentalTime::class, FlowPreview::class)
@Composable
internal fun FutaberApp(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    repository: BoardRepository,
    stateStore: AppStateStore,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    savePreference: (key: String, value: String) -> Unit,
    mediaServices: FutaberMediaServices? = null,
    appVersion: String = "1.0",
    /** Refreshes the threads of the history (the history band's "一括更新"); null when the host cannot. */
    onRefreshHistory: (suspend () -> Unit)? = null,
    /** The auto-saved copies of the threads: deleting a history row deletes its copy too (as in ふたちゃ); null = none to clean. */
    autoSavedThreadRepository: SavedThreadRepository? = null,
    /** A thread link from another app (a `*.2chan.net/…/res/N.htm` URL); [onThreadDeepLinkConsumed] is called once it was handled. */
    initialThreadDeepLink: String? = null,
    onThreadDeepLinkConsumed: (String) -> Unit = {},
    /** A board link from another app; opens the board's catalog. */
    initialBoardDeepLink: String? = null,
    onBoardDeepLinkConsumed: (String) -> Unit = {},
    /** The host's handling of the watch-word alert switch (it asks for the notification permission); null = just store it. */
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)? = null,
    /** A line for the person from the host (e.g. an AI command this mode does not run); shown once, then [onExternalNoticeShown]. */
    externalNotice: String? = null,
    onExternalNoticeShown: () -> Unit = {},
    /**
     * Wraps everything inside the mode's theme, so the dialogs of the shared media editors (picked from the operation
     * menu or the settings) take this mode's colours. The default draws the content as it is.
     */
    mediaHost: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
    /** "閲覧スレ通知": the host's sender (the number waiting to be sent); null = nothing is sent from here. */
    onArchiveReportEnqueued: ((Int) -> Unit)? = null,
    onArchiveReportEnabledChanged: (Boolean) -> Unit = {},
    /** B8: the AI commands received at the app root (FutaberAiCommands.kt); null runs none here. */
    aiInbox: FutaberAiInbox? = null
) {
    val scope = rememberCoroutineScope()
    // The manage panel's history rows outlive the panel (re-audit M9).
    val manageHistoryRowsMemo = remember { com.valoser.futacha.shared.ui.OffMainMemo<List<Any?>, List<ThreadHistoryEntry>?>() }
    val mockRepository = remember { futaberTutorialRepository() }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val themeMode = FutaberThemeMode.fromPersistedValue(preferences[FutaberPreferenceKeys.THEME])
    // Read again only when the settings change (re-audit M22: it was made again on every composition of the app).
    val displaySettings = remember(preferences) { FutaberDisplaySettings.from(preferences) }
    val displayStyle = FutaberCatalogDisplayStyle.fromPersistedValue(preferences[FutaberPreferenceKeys.CATALOG_DISPLAY_STYLE])

    var selectedBoardId by rememberSaveable { mutableStateOf<String?>(null) }
    var openThread by rememberSaveable(stateSaver = FutaberThreadRefSaver) { mutableStateOf<FutaberThreadRef?>(null) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    // L-B10: the alerts and sheets of the board list and the manage panel, drawn over the whole screen.
    val screenOverlay = remember { FutaberScreenOverlay() }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var managePanelOpen by rememberSaveable { mutableStateOf(false) }
    // A line at the bottom for what the person should know (a failed save, a full tab list); it goes away by itself.
    var appNotice by remember { mutableStateOf<String?>(null) }
    val notify: (String) -> Unit = { appNotice = it }
    // Work that outlives the screen that started it (re-audit M19).
    val appTasks = remember(scope) { FutaberAppTasks(scope) { appNotice = it } }
    LaunchedEffect(externalNotice) {
        val text = externalNotice ?: return@LaunchedEffect
        appNotice = text
        onExternalNoticeShown()
    }
    val openInBrowser = com.valoser.futacha.shared.util.rememberUrlLauncher()
    val urlSchemeOpener = rememberFutaberUrlSchemeOpener(displaySettings.urlSchemes)
    var catalogReport by remember { mutableStateOf<Pair<BoardSummary, CatalogItem>?>(null) }
    var reportingCatalog by remember { mutableStateOf(false) }
    catalogReport?.let { (reportBoard, item) ->
        com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = { if (!reportingCatalog) catalogReport = null },
            title = { Text("スレッドを通報") },
            text = { Text("No.${item.id} のスレッドをふたば☆ちゃんねるの掲示板管理者へ削除依頼します。") },
            confirmButton = { TextButton(enabled = !reportingCatalog, onClick = {
                reportingCatalog = true
                scope.launch {
                    try {
                        val result = com.valoser.futacha.shared.ui.board.performThreadAction {
                            com.valoser.futacha.shared.ui.board.requireWritableThreadBoard(reportBoard.url)
                            (if (reportBoard.isMockBoard()) mockRepository else repository).requestDeletion(
                                reportBoard.url, item.id, item.id, com.valoser.futacha.shared.ui.board.DEFAULT_DEL_REASON_CODE)
                        }
                        when (result) {
                            is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Success -> { notify("削除依頼を送りました"); catalogReport = null }
                            is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Failure -> notify(result.error.message ?: "削除依頼を送れませんでした")
                        }
                    } finally { reportingCatalog = false }
                }
            }) { Text(if (reportingCatalog) "送信中…" else "通報する") } },
            dismissButton = { TextButton(enabled = !reportingCatalog, onClick = { catalogReport = null }) { Text("キャンセル") } }) }
    }

    val board = boards.firstOrNull { it.id == selectedBoardId }
        ?: boards.firstOrNull { it.id == preferences[FutaberPreferenceKeys.LAST_BOARD_ID] }
        ?: boards.firstOrNull()
    // The catalog's sort: one value for every board, or (an extension) each board's own with that value as the fallback.
    val sortMode = futaberCatalogSortFor(
        preferences, board?.id, displaySettings.extSortByBoard, displaySettings.extCatalogModes, board?.let(::futaberStoredBoardId)
    )
    // A thread of a board that was deleted or is no longer selected is closed.
    val thread = openThread?.takeIf { it.boardId == board?.id }
    // Applied to the latest stored list, not the one the drawer showed.
    val editBoards: (transform: (List<BoardSummary>) -> List<BoardSummary>) -> Unit = { transform ->
        scope.launch {
            try {
                stateStore.updateBoards(transform)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to edit boards", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }

    // The catalog NG rules (threads, words, images) made in the other modes apply to this board's catalog too.
    val catalogNgIndex = remember(ngRules, board) {
        // A board with no key of its own (the tutorial board) still gets the rules made for every board ("*").
        board?.let { buildCompatCatalogRuleIndex(compatCatalogRulesForBoard(ngRules, futaberNgScopeKey(it).orEmpty())) }
    }
    val registerNgThread: (CatalogItem) -> Unit = register@{ item ->
        val target = board ?: return@register
        val key = futaberNgScopeKey(target) ?: return@register
        val store = mediaServices?.store ?: return@register
        scope.launch {
            try {
                // The rule belongs to the board in the shared store, which learns of this mode's boards here.
                store.importModernBoards(boards)
                val value = item.threadUrl.trim().normalizeCompatNgValue()
                if (value.isNotEmpty()) {
                    val saved = store.upsertNgRule(
                        CompatNgRule(
                            id = compatNgRuleId(CompatNgKind.CATALOG_REFUSE, key, value),
                            kind = CompatNgKind.CATALOG_REFUSE,
                            scopeKey = key,
                            normalizedValue = value,
                            memo = futaberSafeTake(item.title.orEmpty(), 4),
                            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                    // False: the board of the rule is not in the shared store (nothing was written).
                    if (!saved) Logger.e(FUTABER_TAG, "The NG thread rule was not saved: its board is not in the shared store (key=$key)")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to register an NG thread", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    // C33 (L-B9): 設定→NG→NGスレッドURL adds a thread by its address, the same rule as the catalog's 「スレッドをNG」.
    val registerNgThreadTarget: (FutaberNgThreadTarget) -> Unit = register@{ target ->
        val store = mediaServices?.store ?: return@register
        scope.launch {
            try {
                store.importModernBoards(boards)
                val value = target.threadUrl.normalizeCompatNgValue()
                val saved = store.upsertNgRule(
                    CompatNgRule(
                        id = compatNgRuleId(CompatNgKind.CATALOG_REFUSE, target.scopeKey, value),
                        kind = CompatNgKind.CATALOG_REFUSE,
                        scopeKey = target.scopeKey,
                        normalizedValue = value,
                        memo = "",
                        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                    )
                )
                if (!saved) Logger.e(FUTABER_TAG, "The NG thread rule was not saved: its board is not in the shared store (key=${target.scopeKey})")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to register an NG thread by its address", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    val catalogThreadRules = remember(ngRules) {
        ngRules.filter { it.kind == CompatNgKind.CATALOG_REFUSE || it.kind == CompatNgKind.CATALOG_THREAD }
            .sortedByDescending { it.createdAtEpochMillis }
    }
    // The rules of the other modes that hide catalog threads by a word of the title or by their picture also apply here,
    // so they are listed in the settings and can be taken back there.
    val catalogSharedNg = remember(preferences[com.valoser.futacha.shared.ui.ftutter.FtutterKeys.NG_LIST], preferences[com.valoser.futacha.shared.ui.compat.COMPAT_COMMON_NG_APPLY_STORAGE_KEY], board) {
        futaberSharedNgMatcher(
            preferences[com.valoser.futacha.shared.ui.ftutter.FtutterKeys.NG_LIST],
            com.valoser.futacha.shared.ui.compat.compatCommonNgApplyEnabled(preferences),
            board?.let(::futaberCanonicalBoardUrl)
        )
    }
    val catalogWordRules = remember(ngRules) { futaberCatalogWordRules(ngRules) }
    val catalogImageRules = remember(ngRules) { futaberCatalogImageRules(ngRules) }
    // The rules by look of the picture that apply to this board's catalog (checked by hashing the catalog's pictures).
    val catalogPhashRules = remember(ngRules, board) {
        board?.let { compatCatalogRulesForBoard(ngRules, futaberNgScopeKey(it).orEmpty()) }
            .orEmpty().filter { it.kind == CompatNgKind.CATALOG_IMAGE_PHASH }
    }
    val catalogPhashThreshold = preferences[futaberImageNgThresholdKey]?.toIntOrNull()
        ?: com.valoser.futacha.shared.compat.CompatImagePhash.DEFAULT_THRESHOLD
    val imageNgRules = remember(ngRules) {
        ngRules.filter { it.kind == CompatNgKind.THREAD_IMAGE_PHASH }.sortedByDescending { it.createdAtEpochMillis }
    }
    // The other thread rules (post number, poster id, words, picture address) also hide posts here; they are listed too.
    val threadDetailRules = remember(ngRules) { futaberThreadDetailNgRules(ngRules) }
    val deleteNgRule: (String) -> Unit = { ruleId ->
        val store = mediaServices?.store
        if (store != null) scope.launch {
            try { store.deleteNgRule(ruleId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to delete an NG rule", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    val deleteNgRules: (Collection<String>) -> Unit = { ruleIds ->
        val store = mediaServices?.store
        if (store != null) scope.launch {
            try { store.deleteNgRules(ruleIds) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to delete NG rules", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    // Threads are fetched through the shared layer ふたちゃ uses: the cache server when the user turned
    // 通信の軽量化 on (off by default), and the archives fill in posts a long thread's page is missing.
    // The catalog keeps the plain repository, so its layout and size stay as they are.
    val threadRepository = remember(repository, mediaServices?.store, mediaServices?.httpClient) {
        mediaServices?.let { FutachaSharedBoardRepository(repository, it.store, it.httpClient) } ?: repository
    }
    // The shared features the borrowed dialogs read (image NG, image search, page save), made once for the catalog and the thread.
    val sharedFeatures = rememberFutaberSharedFeatures(mediaServices, threadRepository)
    // B7a (FutaberParityB7a.kt): the catalog's extensions (そ順・監視, watch words, reply priority, generations, cache search).
    val catalogExtras = rememberFutaberCatalogExtras(
        board, sortMode, displaySettings, preferences, ngRules, boards, mediaServices?.store, openInBrowser, notify
    )
    // B7b (FutaberParityB7b.kt): the request of "NGの詳細管理" (thread menu / catalog refresh sheet).
    val parityB7b = remember { FutaberParityB7bState() }
    // The catalog thread whose picture is being registered as an image NG (the catalog long-press extension).
    var catalogImageNg by remember { mutableStateOf<FutaberCatalogImageNgTarget?>(null) }
    // "板一覧から一括追加": the official list's boards that are not registered yet (the list ふたちゃ uses).
    val bulkAddBoards: (suspend () -> Result<Int>)? = mediaServices?.httpClient?.let { client ->
        {
            try {
                val discovered = fetchDefaultCompatBoardsFromMenu(client, modernBoardsToCompatibility(boards)).getOrThrow()
                var added = 0
                stateStore.updateBoards { current ->
                    val next = futaberAddDiscoveredBoards(current, discovered.map { it.name to it.originalUrl })
                    added = next.size - current.size
                    next
                }
                Result.success(added)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }
    val ngWords by stateStore.observedNgWords.collectAsState(emptyList<String>())
    val ngHeaders by stateStore.observedNgHeaders.collectAsState(emptyList<String>())
    val catalogNgWords by stateStore.observedCatalogNgWords.collectAsState(emptyList<String>())
    // The posts this person wrote, per thread (written when a post succeeds): "レスを抽出" → 自分の書き込み.
    val selfPostMap by stateStore.selfPostIdentifiersByThread.collectAsState(emptyMap<String, List<String>>())
    val launchStore: (what: String, block: suspend () -> Unit) -> Unit = { what, block ->
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to $what", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    // Settings of this mode are written one at a time, in the order they were asked for, straight to the shared store (a
    // failure is shown to the person); without the store, the host's own writer is used.
    val preferenceMutex = remember { Mutex() }
    val writePreference: (key: String, value: String) -> Unit = { key, value ->
        val store = mediaServices?.store
        if (store == null) savePreference(key, value) else scope.launch {
            try {
                preferenceMutex.withLock { store.savePreference(key, value) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to save $key", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    // A list kept in one setting (the tabs, the favourites) is changed by a function applied to the value stored at that
    // moment, not to the one the screen last showed: two quick changes ("×" twice) both count. A null result writes nothing.
    // [afterSaved] runs only once the changed value is stored (not when nothing changed or the save failed).
    val editPreferenceThen: (key: String, transform: (String?) -> String?, afterSaved: () -> Unit) -> Unit = { key, transform, afterSaved ->
        val store = mediaServices?.store
        if (store == null) {
            transform(preferences[key])?.let { savePreference(key, it); afterSaved() }
        } else scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            try {
                preferenceMutex.withLock {
                    transform(store.loadPreference(key))?.let { store.savePreference(key, it); afterSaved() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to change $key", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
            }
        }
    }
    val editPreference: (key: String, transform: (String?) -> String?) -> Unit = { key, transform -> editPreferenceThen(key, transform) {} }
    // ---- The writing screen's "ペーストボード" (FutaberPasteboard.kt): kept texts and pictures of this device ----
    val pasteboardFileSystem = mediaServices?.fileSystem
    val pasteboardItems = remember(preferences[FutaberPreferenceKeys.PASTEBOARD]) {
        decodeFutaberPasteboard(preferences[FutaberPreferenceKeys.PASTEBOARD])
    }
    // Changed against the stored list; the pictures of the rows that went (removed, or pushed out by the size limit) are
    // deleted once the list without them is saved.
    val editPasteboard: ((List<FutaberPasteItem>) -> List<FutaberPasteItem>) -> Unit = { transform ->
        var dropped: List<String> = emptyList()
        editPreferenceThen(FutaberPreferenceKeys.PASTEBOARD, { stored ->
            val before = decodeFutaberPasteboard(stored)
            val changed = transform(before)
            if (changed == before) null else {
                val (encoded, pushedOut) = encodeFutaberPasteboard(changed)
                dropped = (before.filter { old -> changed.none { it.id == old.id } } + pushedOut)
                    .mapNotNull { it.imageLocator }.filter(::futaberOwnsPasteboardFile)
                encoded
            }
        }) {
            val fileSystem = pasteboardFileSystem
            if (fileSystem != null && dropped.isNotEmpty()) scope.launch {
                dropped.forEach { locator ->
                    try {
                        fileSystem.delete(locator).onFailure { Logger.e(FUTABER_TAG, "Failed to delete a pasteboard picture", it) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Logger.e(FUTABER_TAG, "Failed to delete a pasteboard picture", error)
                    }
                }
            }
        }
    }
    val pasteboardLink = remember(pasteboardItems, pasteboardFileSystem) {
        val fileSystem = pasteboardFileSystem ?: return@remember null
        FutaberPasteboardLink(
            items = pasteboardItems,
            // The kept row shows in the list at once; only a failure is told (a notice would cover the writing's tools).
            onAddText = { text, folder -> editPasteboard { futaberPasteboardWithText(it, text, futaberNewPasteId(), folder) } },
            onAddImage = { image, folder ->
                scope.launch {
                    val id = futaberNewPasteId()
                    val locator = futaberPasteboardImageLocator(id, image.fileName)
                    val written = try {
                        fileSystem.writeBytes(locator, image.bytes)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Result.failure(error)
                    }
                    written.onSuccess {
                        editPasteboard { futaberPasteboardWithImage(it, locator, image.fileName, id, folder) }
                    }.onFailure {
                        Logger.e(FUTABER_TAG, "Failed to keep a pasteboard picture", it)
                        notify(FUTABER_PASTEBOARD_SAVE_FAILED)
                    }
                }
            },
            onRemove = { item -> editPasteboard { futaberPasteboardWithout(it, item.id) } },
            onEdit = editPasteboard,
            loadImage = { item ->
                val locator = item.imageLocator
                if (locator == null || !futaberOwnsPasteboardFile(locator)) {
                    Result.failure(IllegalArgumentException("Not a pasteboard picture"))
                } else fileSystem.readBytes(locator).map { bytes ->
                    com.valoser.futacha.shared.util.ImageData(bytes, item.imageName.ifBlank { locator.substringAfterLast('/') })
                }
            }
        )
    }
    // ---- Writing: a reply to the open thread, or a new thread on the board ----
    var postTarget by rememberSaveable(stateSaver = FutaberPostTargetSaver) { mutableStateOf<FutaberPostTarget?>(null) }
    var postSubject by rememberSaveable { mutableStateOf("") }
    var postComment by rememberSaveable { mutableStateOf("") }
    // Quote mode: the thread is shown for tapping lines while the draft waits in a card.
    var quoteMode by rememberSaveable { mutableStateOf(false) }
    val postAttachment = remember { FutaberAttachment(scope) }
    // The Cookie page opened from the write screen's guidance after a failed post (the extension "書き込みツール").
    var cookiesOpen by remember { mutableStateOf(false) }
    var attachmentOwnerKey by remember { mutableStateOf<String?>(null) }
    var postCapabilities by remember { mutableStateOf<com.valoser.futacha.shared.network.BoardPostingCapabilities?>(null) }
    val pickerPreference by stateStore.attachmentPickerPreference.collectAsState(com.valoser.futacha.shared.util.AttachmentPickerPreference.MEDIA)
    val preferredFileManager by stateStore.getPreferredFileManager().collectAsState(null)
    var threadRefreshSignal by remember { mutableStateOf(0) }
    var catalogRefreshSignal by remember { mutableStateOf(0) }
    val deleteKey by stateStore.observedLastUsedDeleteKey.collectAsState("")
    val postSettings = remember(
        preferences[FutaberPreferenceKeys.POST_NAME], preferences[FutaberPreferenceKeys.POST_EMAIL],
        preferences[FutaberPreferenceKeys.POST_CONFIRM], preferences[FutaberPreferenceKeys.POST_IMAGE_QUALITY]
    ) { FutaberPostSettings.from(preferences) }
    // M42 (L-B8): what × cleared last, kept while the same writing is up (through quote mode), forgotten with it.
    val postCleared = remember(postTarget) { mutableStateOf<FutaberClearedText?>(null) }
    val drafts = remember(preferences[FutaberPreferenceKeys.DRAFTS]) { decodeFutaberDrafts(preferences[FutaberPreferenceKeys.DRAFTS]) }
    val quoteTarget = (postTarget as? FutaberPostTarget.Reply)?.takeIf { quoteMode && thread?.threadId == it.threadId }
    /** Opens the write screen with the saved draft for [target], plus [quote] on its own line. */
    // 送信中の書き込みの宛先（監査M13）。送信はこの画面より長く続くスコープで動き、書き込み画面が外れても止まらない。
    var postInFlight by remember { mutableStateOf<String?>(null) }
    val openPost: (FutaberPostTarget, String) -> Unit = open@{ target, quote ->
        // 送信中は、別の宛先の書き込み画面に差し替えない（送っている本文の画面が消え、結果も分からなくなる）。
        if (futaberPostRefusedWhileSending(postInFlight, target)) {
            notify(FUTABER_POST_IN_FLIGHT_NOTICE)
            return@open
        }
        // The text still on the screen wins over the saved draft when the same post is being written (a round trip through
        // quote mode keeps the target, and the draft is saved only a moment after typing stops).
        val start = futaberPostStart(futaberDraftForTarget(drafts, target, boards), target, postTarget, postSubject, postComment)
        postSubject = start.subject
        postComment = futaberAppendQuote(start.comment, quote)
        quoteMode = false
        // A file picked for one post is not carried into another thread's reply.
        if (attachmentOwnerKey != target.draftKey) postAttachment.clear()
        attachmentOwnerKey = target.draftKey
        postTarget = target
        // D-B6 (J5): a draft another mode left for this thread is taken over when this mode has none of its own.
        val sharedStore = mediaServices?.store
        val sharedBoard = (target as? FutaberPostTarget.Reply)?.let { reply -> boards.firstOrNull { it.id == reply.boardId } }
            ?.takeIf { futaberNgScopeKey(it) != null }
        if (sharedStore != null && sharedBoard != null && target is FutaberPostTarget.Reply) {
            val ownDraft = futaberDraftForTarget(drafts, target, boards)
            val tabKey = futaberCompatTabKey(sharedBoard, target.threadId)
            scope.launch {
                val adopt = try {
                    futaberSharedDraftToAdopt(ownDraft, sharedStore.loadDraft(tabKey), postSubject, postComment)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e(FUTABER_TAG, "Failed to read the shared draft", error)
                    null
                }
                if (adopt != null && postTarget?.draftKey == target.draftKey) {
                    postSubject = adopt.subject
                    postComment = adopt.comment
                }
            }
        }
    }
    // Applied to the drafts stored now, so saving this one does not bring back a draft another screen just cleared. The
    // files of drafts that no longer note them go (the extension "下書きに添付も残す").
    val editDrafts: ((List<FutaberDraft>) -> List<FutaberDraft>) -> Unit = { transform ->
        futaberEditDrafts(editPreferenceThen, { dropped -> futaberDeleteDraftFiles(scope, mediaServices?.fileSystem, dropped) }, transform)
    }
    // Once per opening of this mode: draft files no draft notes go (audit M14). Done under the settings lock and only while
    // nothing is attached on the write screen, so a file being kept right now is never taken.
    LaunchedEffect(mediaServices?.fileSystem, mediaServices?.store) {
        val fileSystem = mediaServices?.fileSystem ?: return@LaunchedEffect
        val store = mediaServices?.store ?: return@LaunchedEffect
        try {
            preferenceMutex.withLock {
                val stored = decodeFutaberDrafts(store.loadPreference(FutaberPreferenceKeys.DRAFTS))
                futaberSweepDraftFiles(fileSystem, stored) {
                    postTarget == null && postAttachment.image == null && !postAttachment.processing
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to sweep stale draft files", error)
        }
    }
    val saveDraftNow: (FutaberPostTarget, String, String) -> Unit = { target, subject, comment ->
        val now = Clock.System.now().toEpochMilliseconds()
        editDrafts { futaberDraftsWithText(it, target, boards, subject, comment, now) }
    }
    // The draft follows the text shortly after typing stops, in the write screen and in quote mode alike.
    LaunchedEffect(postTarget?.draftKey) {
        val target = postTarget ?: return@LaunchedEffect
        snapshotFlow { postSubject to postComment }.debounce(400).collect { (subject, comment) ->
            saveDraftNow(target, subject, comment)
        }
    }
    // The board's own limits (size, formats, whether replies may carry a file), refreshed when writing starts.
    LaunchedEffect(postTarget?.draftKey) {
        val target = postTarget ?: return@LaunchedEffect
        val targetBoard = boards.firstOrNull { it.id == target.boardId } ?: return@LaunchedEffect
        val fallback = com.valoser.futacha.shared.network.defaultBoardPostingCapabilities(targetBoard.url)
        postCapabilities = fallback
        if (targetBoard.isMockBoard()) return@LaunchedEffect
        try {
            postCapabilities = repository.getPostingCapabilities(targetBoard.url, (target as? FutaberPostTarget.Reply)?.threadId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Posting capabilities unavailable; using the board's known limits", error)
        }
    }
    // The extension "下書きに添付も残す": the attachment goes with the draft (FutaberParityB3.kt).
    FutaberDraftAttachmentSync(
        enabled = displaySettings.extDraftAttachment,
        fileSystem = mediaServices?.fileSystem,
        scope = scope,
        target = postTarget,
        attachment = postAttachment,
        boards = boards,
        currentTargetKey = { postTarget?.draftKey },
        capabilitiesFor = { target ->
            postCapabilities ?: boards.firstOrNull { it.id == target.boardId }
                ?.let { com.valoser.futacha.shared.network.defaultBoardPostingCapabilities(it.url) }
        },
        loadDrafts = {
            decodeFutaberDrafts(mediaServices?.store?.loadPreference(FutaberPreferenceKeys.DRAFTS) ?: preferences[FutaberPreferenceKeys.DRAFTS])
        },
        editDrafts = editDrafts,
        adoptOwner = { attachmentOwnerKey = it },
        notify = notify
    )
    val sendPostNow: suspend (FutaberPostTarget, FutaberPostInput) -> Result<String?> = send@{ target, input ->
        val targetBoard = boards.firstOrNull { it.id == target.boardId }
            ?: return@send Result.failure(IllegalStateException("板が登録されていません"))
        val repo = if (targetBoard.isMockBoard()) mockRepository else repository
        // Reading or writing the "agreed to the notice" flag can fail like any store access; nothing was sent yet, so it is
        // a plain failure the write screen shows (and keeps the text).
        val noticeAgreed = try {
            checkPostingNoticeIfNeeded(stateStore)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to check the posting notice", error)
            return@send Result.failure(IllegalStateException("投稿のご案内を確認できませんでした。もう一度お試しください", error))
        }
        if (!noticeAgreed) {
            return@send Result.failure(IllegalStateException("投稿のご案内に同意すると書き込めます"))
        }
        val password = normalizeDeleteKeyForSubmit(deleteKey)
        val outcome = when (target) {
            is FutaberPostTarget.Reply -> {
                val config = buildThreadReplyActionConfig(
                    boardUrl = targetBoard.url,
                    threadId = target.threadId,
                    draft = ThreadReplyDraft(
                        name = postSettings.name, email = postSettings.email, subject = input.subject,
                        comment = input.comment, password = password, imageData = input.image
                    ),
                    normalizedPassword = password
                )
                performThreadReplyAction(config, buildThreadReplyActionCallbacks(repo))
            }
            is FutaberPostTarget.CreateThread -> performThreadAction {
                requireWritableThreadBoard(targetBoard.url)
                repo.createThread(
                    board = targetBoard.url, name = postSettings.name, email = postSettings.email,
                    subject = input.subject, comment = input.comment, password = password,
                    imageFile = input.image?.bytes, imageFileName = input.image?.fileName,
                    textOnly = input.image == null, handwriting = input.image?.isHandwriting == true
                )
            }
        }
        when (outcome) {
            is ThreadActionRunResult.Success -> {
                val posted = outcome.value
                // The post is already out: a failure to note it on this device must not turn the result into a failure
                // (the person would send it again), so it is only told.
                try {
                    if (target is FutaberPostTarget.Reply && !posted.isNullOrBlank()) {
                        // Remembered so "そうだね" and the like know the post is the user's own.
                        stateStore.addSelfPostIdentifier(target.threadId, posted, targetBoard.id)
                    }
                    stateStore.setLastUsedDeleteKey(password)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e(FUTABER_TAG, "Failed to note a sent post", error)
                    notify(FUTABER_POST_NOTE_FAILED_NOTICE)
                }
                Result.success(posted)
            }
            is ThreadActionRunResult.Failure -> Result.failure(
                IllegalStateException(
                    buildThreadActionFailureMessage(
                        if (target is FutaberPostTarget.Reply) "返信の送信に失敗しました" else "スレッド作成に失敗しました",
                        outcome.error
                    ),
                    outcome.error
                )
            )
        }
    }
    // 送信は書き込み画面のスコープでなく、この画面のスコープで動かす（監査M13：板が消える・AIの命令などで書き込み画面が外れると、
    // 送信が途中で取り消され、結果も「送れたか分からない」案内も出ず、送り直すと二重に投稿されることがあった）。書き込み画面が
    // 残っていれば結果はそちらが受け、外れていればここで知らせる（成功なら下書きを消す）。
    val currentPostTarget by rememberUpdatedState(postTarget)
    val sendPost: suspend (FutaberPostTarget, FutaberPostInput) -> Result<String?> = { target, input ->
        postInFlight = target.draftKey
        futaberSendOutlivingScreen(
            scope = scope,
            send = {
                try {
                    sendPostNow(target, input)
                } finally {
                    postInFlight = null
                }
            },
            screenStillOpen = { currentPostTarget?.draftKey == target.draftKey },
            onSentAway = {
                editDrafts { futaberDiscardDraft(it, target, boards) }
                // The text and the file still held for this post go too, or they come back in the next reply to the same
                // thread (re-audit L1). Held for another post (opened since), they stay.
                if (futaberHeldTextBelongsTo(attachmentOwnerKey, target)) {
                    postSubject = ""
                    postComment = ""
                    postAttachment.clear()
                }
                notify(FUTABER_POST_SENT_AWAY_NOTICE)
            },
            onFailedAway = { notify(futaberPostFailedAwayNotice(it)) }
        )
    }

    // Plan B8a: the strip is a view of the shared tabs (this mode's order under its mark, then the tabs the other modes
    // opened, Q1); the old row `compat.futaber.tabs` is only read for the threads with no shared place and the tabs the
    // migration stage has not taken in yet.
    val tabStore = mediaServices?.store
    val sharedTabSource = remember(tabStore) { tabStore?.tabs?.shownSiteTabs() ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val sharedTabs by sharedTabSource.collectAsState(emptyList())
    val tabViewSource = remember(tabStore) { tabStore?.tabViews ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val allTabViews by tabViewSource.collectAsState(emptyList())
    val migrationsSettled by (tabStore?.sharedMigrationsSettled ?: remember { kotlinx.coroutines.flow.MutableStateFlow(true) }).collectAsState()
    var consumedTabs by remember(tabStore) { mutableStateOf<Set<String>?>(if (tabStore == null) emptySet() else null) }
    LaunchedEffect(tabStore, migrationsSettled, preferences[FutaberPreferenceKeys.TABS]) {
        val store = tabStore ?: return@LaunchedEffect
        if (!migrationsSettled) return@LaunchedEffect
        try {
            consumedTabs = futaberConsumedTabs(store.loadSharedData())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to read the tab migration record", error)
        }
    }
    val tabSnapshot = remember(sharedTabs, allTabViews, preferences[FutaberPreferenceKeys.TABS], boards, consumedTabs) {
        futaberTabSnapshot(sharedTabs, allTabViews, futaberTabsFromStore(preferences[FutaberPreferenceKeys.TABS], boards), consumedTabs, boards)
    }
    val tabKeys = tabSnapshot.keys
    val tabViews = remember(tabSnapshot, history, displaySettings.extTabStatus) {
        futaberTabViews(tabKeys, history, displaySettings.extTabStatus, tabSnapshot.shared)
    }
    val latestBoardsForTabs by rememberUpdatedState(boards)
    val latestHistoryForTabs by rememberUpdatedState(history)
    val latestConsumedTabs by rememberUpdatedState(consumedTabs)
    val latestTabKeys by rememberUpdatedState(tabKeys)
    val tabWriter = remember(tabStore) {
        FutaberStoreTabWriter(
            tabStore, { latestBoardsForTabs }, { latestHistoryForTabs },
            editRow = { transform ->
                editPreference(FutaberPreferenceKeys.TABS) { stored ->
                    val current = futaberTabsFromStore(stored, latestBoardsForTabs)
                    val next = transform(current)
                    if (next == current) null else futaberTabsToStore(next, latestBoardsForTabs)
                }
            },
            notice = notify
        )
    }
    val tabWriteMutex = remember { Mutex() }
    // Changes the strip by a function applied to the strip the stores hold at that moment (two quick changes both count).
    val saveTabs: ((List<FutaberTabKey>) -> List<FutaberTabKey>) -> Unit = { transform ->
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    tabWriteMutex.withLock {
                        val current = tabStore?.let { futaberStoreTabSnapshot(it, latestBoardsForTabs, latestConsumedTabs).keys } ?: latestTabKeys
                        tabWriter.apply(current, transform(current))
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e(FUTABER_TAG, "Failed to change the tabs", error)
                    notify(FUTABER_STORE_FAILURE_NOTICE)
                }
            }
        }
    }
    val toggleTab: (FutaberTabKey) -> Unit = { key -> saveTabs { current -> futaberToggleTab(current, key) } }
    // "閉じたタブを元に戻す" (an extension): the shared record of the tabs this mode closed or showed (plan B6), and this
    // run's closes of threads with no shared place.
    var restorableTabCount by remember { mutableStateOf(0) }
    LaunchedEffect(tabWriter, sharedTabs, preferences[FutaberPreferenceKeys.TABS]) {
        restorableTabCount = try {
            tabWriter.restorableCount()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to count the closed tabs", error)
            0
        }
    }
    val restoreTab: () -> Unit = {
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try {
                    tabWriteMutex.withLock { tabWriter.restore() }
                    restorableTabCount = tabWriter.restorableCount()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e(FUTABER_TAG, "Failed to bring back a tab", error)
                    notify(FUTABER_STORE_FAILURE_NOTICE)
                }
            }
        }
    }
    val favorites = remember(preferences[FutaberPreferenceKeys.FAVORITES], boards) {
        futaberFavoritesFromStore(preferences[FutaberPreferenceKeys.FAVORITES], boards)
    }
    val favoriteViews = remember(favorites, history) { futaberFavoriteViews(favorites, history) }
    // M14: the pictures kept for a favourite go when the favourite does (a star taken off, a board deleted, the list edited).
    var keptFavoriteKeys by remember { mutableStateOf<Set<FutaberTabKey>?>(null) }
    LaunchedEffect(favorites) {
        val now = favorites.mapTo(HashSet()) { it.key }
        val before = keptFavoriteKeys
        keptFavoriteKeys = now
        val files = mediaServices?.fileSystem
        if (before != null && files != null) (before - now).forEach { futaberDeleteFavoriteImages(files, it.boardId, it.threadId) }
    }
    // B4: the thread open here is held from the shared tab trim while it is shown.
    val heldThread = openThread
    val heldTabKey = remember(heldThread, boards) {
        heldThread?.let { ref -> boards.firstOrNull { it.id == ref.boardId }?.takeIf { futaberNgScopeKey(it) != null }?.let { futaberCompatTabKey(it, ref.threadId) } }
    }
    DisposableEffect(mediaServices?.store, heldTabKey) {
        val store = mediaServices?.store
        store?.setScreenHeldTabs(ExperienceProfile.FUTABER, setOfNotNull(heldTabKey))
        onDispose { store?.setScreenHeldTabs(ExperienceProfile.FUTABER, emptySet()) }
    }
    // D-B6: the reply drafts too (the other modes' write screens show them; their own drafts are not overwritten).
    var mirroredDrafts by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(drafts, boards, mediaServices?.store) {
        val store = mediaServices?.store ?: return@LaunchedEffect
        delay(1_800)
        try {
            mirroredDrafts = futaberApplySharedDrafts(store, futaberSharedDraftsPlan(drafts, boards), mirroredDrafts)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to share the drafts", error)
        }
    }
    // Closing several tabs at once keeps the starred threads' tabs with the extension on (they are not "closed" either).
    // Plan B5 (Q7): a shared tab with a ★ is kept too, wherever the star was set.
    val sharedStarredKeys by produceState(emptySet<String>(), mediaServices?.store) {
        val store = mediaServices?.store ?: return@produceState
        store.tabs.map(::futaberStarredTabKeys).distinctUntilChanged().collect { value = it }
    }
    val closeTabsInBulk: ((List<FutaberTabKey>) -> List<FutaberTabKey>) -> Unit = { transform ->
        val starred = sharedStarredKeys
        saveTabs { current ->
            futaberKeepFavoriteTabs(current, transform(current), favorites, displaySettings.extTabsManage) { key ->
                boards.firstOrNull { it.id == key.boardId }?.takeIf { futaberNgScopeKey(it) != null }
                    ?.let { futaberCompatTabKey(it, key.threadId) in starred } == true
            }
        }
    }
    val editFavorites: ((List<FutaberFavorite>) -> List<FutaberFavorite>) -> Unit = { transform ->
        editPreference(FutaberPreferenceKeys.FAVORITES) { stored ->
            val current = futaberFavoritesFromStore(stored, boards)
            val next = transform(current)
            if (next == current) null else futaberFavoritesToStore(next, boards)
        }
    }
    // Patrol results (keyword matches) are shared with the other modes; this mode only lists them.
    val watcher = remember(mediaServices?.store) { mediaServices?.store?.let(::CompatWatcherRepository) }
    val watchResults by produceState(emptyList<CompatWatchResult>(), watcher) {
        val store = mediaServices?.store
        if (watcher == null || store == null) return@produceState
        store.preferences.map { futaberWatchResultSlots(it) }.distinctUntilChanged().collect {
            value = try {
                watcher.load(Clock.System.now().toEpochMilliseconds())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to read the patrol results", error)
                emptyList()
            }
        }
    }
    var watcherOpen by rememberSaveable { mutableStateOf(false) }
    // The extension "外部にじろぐ" (Android): the patrol manager offers it, and "通知" lists its results while chosen.
    val externalWatcher = rememberFutaberExternalWatcher(
        displaySettings.extExternalWatcher, mediaServices?.store, preferences, managePanelOpen to watcherOpen
    )
    // ---- Saved threads (保存箱): the shared manual-save index, wherever the user chose to save ----
    val saveLocation = preferences.compatManualSaveLocation()
    val savedFileSystem = mediaServices?.fileSystem
    val savedRepository = remember(savedFileSystem, saveLocation) {
        savedFileSystem?.let { createCompatSavedThreadRepository(it, saveLocation) }
    }
    var savedBox by remember { mutableStateOf(FutaberSavedBoxState()) }
    var savedTick by remember { mutableStateOf(0) }
    var savedNotice by remember { mutableStateOf<String?>(null) }
    var offlineView by remember { mutableStateOf<FutaberOfflineView?>(null) }
    // A saved [] means that the last board really was deleted; an unloaded / invalid list must keep favourites.
    val boardsExplicitlyEmpty by stateStore.boardsExplicitlyEmpty.collectAsState(false)
    LaunchedEffect(favorites, boards, boardsExplicitlyEmpty) {
        val pruned = if (boardsExplicitlyEmpty && boards.isEmpty()) emptyList() else futaberPruneFavorites(favorites, boards)
        if (pruned != favorites) editFavorites {
            if (boardsExplicitlyEmpty && boards.isEmpty()) emptyList() else futaberPruneFavorites(it, boards)
        }
    }
    // Plan B8a: a removed board's shared tabs go with the board in the shared store, and the strip shows only tabs of
    // registered boards; the old row is left alone (taking a shared tab's key out of it would close the tab everywhere).
    val openKey = thread?.let { FutaberTabKey(it.boardId, it.threadId) }
    // Opening a screen (from the catalog, a tab or the history) is what writes the history.
    val viewedLastPostIds = remember { mutableMapOf<FutaberTabKey?, Pair<Int, String>>() }
    // L-B7 (S22・F03, FutaberOpenRefresh.kt): a catalog row loads its thread first; a tab's opening shows 「新着 N」.
    val catalogOpener = remember { FutaberCatalogOpener() }
    var mixId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(com.valoser.futacha.shared.compat.MixAvailability.enabled) {
        if (!com.valoser.futacha.shared.compat.MixAvailability.enabled) mixId = null
    }
    val openRef: (BoardSummary, FutaberThreadRef) -> Unit = { target, ref ->
        mixId = null
        catalogOpener.reset()
        val existing = findFutaberHistoryEntry(history, target, ref.threadId)
        selectedBoardId = target.id
        offlineView = null
        // Carries what the history said before this visit (posts seen, reading position).
        // The posts seen when the thread was last left are this mode's own record: the history row's count also moves
        // with background refreshes and the other modes, and would leave nothing marked as new.
        openThread = futaberRefWithHistory(
            ref, existing, futaberSeenCountForBoard(preferences[FutaberPreferenceKeys.SEEN], target, ref.threadId)
        ).copy(seenLastPostId = futaberSeenBoundaryForBoard(preferences[FutaberPreferenceKeys.SEEN], target, ref.threadId)?.lastPostId)
        managePanelOpen = false
        writePreference(FutaberPreferenceKeys.LAST_BOARD_ID, target.id)
        // Taken now: a deletion made before the write below lands wins over it (audit F8).
        val openedAtTicket = stateStore.historyDeletionTicket()
        scope.launch {
            try {
                // upsertHistoryEntry only updates a row that exists; opening must also add one.
                stateStore.prependOrReplaceHistoryEntry(
                    buildFutaberOpenHistoryEntry(
                        existing = existing,
                        board = target,
                        ref = ref,
                        nowEpochMillis = Clock.System.now().toEpochMilliseconds()
                    ),
                    openedAtTicket = openedAtTicket
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to record history", error)
            }
        }
    }
    // A row or a link of a board that is not registered asks to add it, then opens it (official boards only).
    val boardAdder = remember { FutaberBoardAddController() }
    val openBoardCatalog: (BoardSummary) -> Unit = { target ->
        mixId = null
        selectedBoardId = target.id
        openThread = null
        offlineView = null
        managePanelOpen = false
        writePreference(FutaberPreferenceKeys.LAST_BOARD_ID, target.id)
    }
    // F-B6 E3: a command that opens the panel on one category; forgotten when the panel closes.
    var manageStartCategory by remember { mutableStateOf<FutaberManageCategory?>(null) }
    LaunchedEffect(managePanelOpen) { if (!managePanelOpen) manageStartCategory = null }
    val addBoardAndOpen: (FutaberBoardAddRequest) -> Unit = { request ->
        boardAdder.openRequest(request, boards, openRef, openBoardCatalog)
    }
    var aimogeCalendarOpen by remember { mutableStateOf(false) }
    val mixStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    val confirmMixMembers = com.valoser.futacha.shared.ui.board.rememberMixR18Confirmation(mediaServices?.store)
    if (aimogeCalendarOpen) {
        com.valoser.futacha.shared.ui.aimoge.AimogeEventCalendarDialog(
            onOpenThread = { row ->
                val threadId = row.threadId
                val owner = com.valoser.futacha.shared.ui.aimoge.aimogeCalendarBoardIn(boards, row)
                val request = if (threadId != null && owner == null) {
                    futaberBoardAddRequest(row.threadUrl.orEmpty(), threadId = threadId, title = row.title)
                } else null
                when {
                    threadId == null -> Unit
                    owner != null -> {
                        aimogeCalendarOpen = false
                        openRef(owner, FutaberThreadRef(owner.id, threadId, row.title, row.thumbnailUrl.orEmpty(), 0))
                    }
                    request != null -> {
                        aimogeCalendarOpen = false
                        addBoardAndOpen(request)
                    }
                    // Kept open, as in the other modes: the reason is shown and another event can be picked.
                    else -> notify("この板（/${row.slug}/）を開けません")
                }
            },
            onDismiss = { aimogeCalendarOpen = false }
        )
    }
    // Links from other apps. The board must be registered here; a link to one that is not says so instead of doing nothing.
    LaunchedEffect(initialThreadDeepLink, boards) {
        val raw = initialThreadDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (boards.isEmpty()) return@LaunchedEffect
        val parsed = com.valoser.futacha.shared.compat.canonicalizeThreadUrl(raw)
        val target = parsed?.let { link ->
            boards.firstOrNull { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it.url) == link.canonicalBoardUrl }
        }
        if (parsed != null && target != null) {
            openRef(target, FutaberThreadRef(target.id, parsed.threadNo, "", "", 0))
        } else {
            futaberBoardAddRequest(raw)?.let(addBoardAndOpen)
                ?: notify("このリンクの板が登録されていません。板一覧から追加してください")
        }
        onThreadDeepLinkConsumed(raw)
    }
    LaunchedEffect(initialBoardDeepLink, boards) {
        val raw = initialBoardDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (boards.isEmpty()) return@LaunchedEffect
        val canonical = com.valoser.futacha.shared.compat.canonicalizeBoardUrl(raw)
        val target = canonical?.let { link -> boards.firstOrNull { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it.url) == link } }
        if (target != null) {
            openBoardCatalog(target)
        } else {
            futaberBoardAddRequest(raw)?.let(addBoardAndOpen)
                ?: notify("このリンクの板が登録されていません。板一覧から追加してください")
        }
        onBoardDeepLinkConsumed(raw)
    }
    val openTab: (FutaberTabView) -> Unit = { tab ->
        boards.firstOrNull { it.id == tab.key.boardId }?.let { target ->
            openRef(target, FutaberThreadRef(target.id, tab.key.threadId, tab.title, tab.thumbnailUrl, tab.replyCount, keepPosition = true))
            catalogOpener.openedFromTab(FutaberTabKey(target.id, tab.key.threadId))
        }
    }
    var tabSheet by remember { mutableStateOf<FutaberTabView?>(null) }
    // "全タブを確認" (the extension "タブの管理（お気に入りのタブを残す・全タブを確認）", FutaberParityB45.kt).
    val tabChecker = rememberFutaberTabChecker(stateStore, repository)
    val tabStrip: @Composable () -> Unit = {
        FutaberTabStrip(tabViews, openKey, openTab, onLongPress = { tabSheet = it })
    }

    LaunchedEffect(managePanelOpen, savedRepository, savedTick) {
        val repo = savedRepository
        if (!managePanelOpen || repo == null) return@LaunchedEffect
        savedBox = savedBox.copy(isLoading = true, errorMessage = null)
        savedBox = try {
            FutaberSavedBoxState(futaberSavedOrder(repo.getAllThreads()), isLoading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to list the saved threads", error)
            FutaberSavedBoxState(emptyList(), isLoading = false, errorMessage = futaberFriendlyLoadError(error, "保存箱", "保存箱を読み込めませんでした"))
        }
    }
    val openSaved: (SavedThread) -> Unit = { saved ->
        val target = futaberBoardForSaved(saved, boards)
        val repo = savedRepository
        val fileSystem = savedFileSystem
        if (target != null && repo != null && fileSystem != null) scope.launch {
            try {
                val metadata = repo.loadThreadMetadata(saved.threadId, saved.boardId).getOrThrow()
                // Every post is converted and the quotes rebuilt: off the main thread, as としあき(仮) does (L101).
                val location = saveLocation
                val page = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                    metadata.toThreadPage(fileSystem, (location as? SaveLocation.Path)?.path ?: MANUAL_SAVE_DIRECTORY, location)
                }
                offlineView = FutaberOfflineView(target, futaberRefForSaved(saved, target), page)
                managePanelOpen = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to open a saved thread", error)
                savedNotice = futaberFriendlyLoadError(error, "保存したスレッド", "保存したスレッドを開けませんでした")
            }
        }
    }
    val deleteSaved: (SavedThread) -> Unit = { saved ->
        val repo = savedRepository
        if (repo != null) scope.launch {
            try {
                repo.deleteThread(saved.threadId, saved.boardId).getOrThrow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to delete a saved thread", error)
                savedNotice = futaberFriendlyLoadError(error, "保存したスレッド", "削除できませんでした")
            }
            savedTick += 1
        }
    }

    // ---- MHT files: one file per thread, listed in the same box, opened in the app or handed to another app ----
    val mhtLibrary = remember(savedFileSystem, mediaServices?.httpClient) {
        savedFileSystem?.let { FutaberMhtLibrary(it, mediaServices?.httpClient) }
    }
    var mhtEntries by remember { mutableStateOf<List<FutaberMhtEntry>>(emptyList()) }
    var mhtBusy by remember { mutableStateOf<String?>(null) }
    var mhtTick by remember { mutableStateOf(0) }
    LaunchedEffect(managePanelOpen, mhtLibrary, mhtTick) {
        val library = mhtLibrary
        if (!managePanelOpen || library == null) return@LaunchedEffect
        mhtEntries = try {
            // The headers of every file are read off the main thread (re-audit L9).
            kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.io) { library.list() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to list the MHT files", error)
            emptyList()
        }
    }
    val shareFile = rememberCompatShareLauncher()
    val openMht: (FutaberMhtEntry) -> Unit = { entry ->
        val library = mhtLibrary
        if (library != null && mhtBusy == null) scope.launch {
            mhtBusy = "開いています…"
            library.open(entry)
                .onSuccess { opened ->
                    val target = futaberBoardForMht(opened.thread, boards)
                    offlineView = FutaberOfflineView(
                        target,
                        FutaberThreadRef(target.id, opened.thread.threadId, opened.thread.title, "", opened.page.posts.size),
                        opened.page
                    )
                    managePanelOpen = false
                }
                .onFailure { error ->
                    Logger.e(FUTABER_TAG, "Failed to open an MHT file", error)
                    savedNotice = futaberFriendlyLoadError(error, "MHTファイル", "MHTファイルを開けませんでした")
                }
            mhtBusy = null
        }
    }
    val importMht: (com.valoser.futacha.shared.util.ImageData) -> Unit = { picked ->
        val library = mhtLibrary
        if (library != null && mhtBusy == null) scope.launch {
            mhtBusy = "取り込んでいます…"
            library.import(picked.bytes, picked.fileName, Clock.System.now().toEpochMilliseconds())
                .onSuccess { savedNotice = "MHTファイルを追加しました"; mhtTick += 1 }
                .onFailure { error -> savedNotice = futaberFriendlyLoadError(error, "MHTファイル", "MHTファイルを取り込めませんでした") }
            mhtBusy = null
        }
    }
    val pickMht = rememberFutaberMhtPickerLauncher(onSelected = importMht, onError = { savedNotice = it })
    val mhtBox = FutaberMhtBox(
        available = mhtLibrary != null,
        entries = mhtEntries,
        busyMessage = mhtBusy,
        onOpen = openMht,
        onShare = { entry -> mhtLibrary?.let { shareFile(entry.title, "multipart/related", it.absolutePath(entry)) } },
        onDelete = { entry ->
            val library = mhtLibrary
            if (library != null) scope.launch {
                library.delete(entry).onFailure { savedNotice = futaberFriendlyLoadError(it, "MHTファイル", "削除できませんでした") }
                mhtTick += 1
            }
        },
        onImport = pickMht
    )

    val clearSavedBox: (List<FutaberMhtEntry>, List<SavedThread>) -> Unit = { mhtNow, savedNow ->
        val library = mhtLibrary
        val repo = savedRepository
        scope.launch {
            val failed = futaberClearSavedBox(
                mht = if (library != null) mhtNow else emptyList(),
                saved = if (repo != null) savedNow else emptyList(),
                deleteMht = { entry -> library!!.delete(entry).map { } },
                deleteSaved = { thread -> repo!!.deleteThread(thread.threadId, thread.boardId) }
            )
            if (failed > 0) savedNotice = "${failed}件を削除できませんでした"
            mhtTick += 1
            savedTick += 1
        }
    }

    // What Back and the left-edge swipe of the thread screens do depends on what is on top of it.
    val dialogOpen = savedNotice != null || watcherOpen
    val drawerGestureEnabled = futaberDrawerGestureAllowed(
        drawerOpen = drawerOpen,
        managePanelOpen = managePanelOpen,
        settingsOpen = settingsOpen,
        writing = postTarget != null,
        tabSheetOpen = tabSheet != null,
        dialogOpen = dialogOpen
    )
    // Back on a thread closes the board list first when it is open (it is on top of the thread).
    val threadBack: () -> Unit = {
        if (futaberThreadBack(drawerOpen) == FutaberThreadBack.CloseDrawer) drawerOpen = false else openThread = null
    }
    // The catalog is kept (list, scroll, search) while a thread is open over it.
    val catalogSearch = remember(board?.id) { FutaberCatalogSearchState() }
    val catalogView = remember(board?.id, sortMode) { FutaberCatalogViewState() }
    // Writing is dropped when its board is gone (state is not written while composing, so it is done here).
    LaunchedEffect(postTarget, boards) {
        val target = postTarget
        if (target != null && boards.none { it.id == target.boardId }) postTarget = null
    }
    // The write screen is restored after the screen was rebuilt, the attached file is not (its bytes are not kept): say so.
    var attachmentWasKept by rememberSaveable { mutableStateOf(false) }
    var attachmentChecked by remember { mutableStateOf(false) }
    val hasAttachment = postAttachment.image != null
    LaunchedEffect(hasAttachment) {
        if (!attachmentChecked) {
            attachmentChecked = true
            // With the draft keeping its file, the file comes back from the draft instead (and says so if it cannot).
            val keptByDraft = displaySettings.extDraftAttachment && mediaServices?.fileSystem != null
            if (!keptByDraft && futaberAttachmentLostNoticeDue(attachmentWasKept, postTarget != null, hasAttachment)) notify(FUTABER_ATTACHMENT_LOST_NOTICE)
        }
        attachmentWasKept = hasAttachment
    }
    // The history archive and "全件削除" of the history band (FutaberParityB45.kt).
    val historyController = rememberFutaberHistoryController(
        stateStore, savedFileSystem, autoSavedThreadRepository, savedRepository, mediaServices?.store, appVersion, notify
    )
    // Deleting a history row cleans up as ふたちゃ does: the other modes' copy of the row (or the bridge would bring it back
    // at the next start), the person's own post numbers of the thread, and the auto-saved copy.
    val deleteHistory: (ThreadHistoryEntry) -> Unit = { entry ->
        launchStore("delete a history row") {
            dismissHistoryEntry(
                stateStore = stateStore,
                autoSavedThreadRepository = autoSavedThreadRepository,
                // The copy an imported archive brought for the row goes with it too.
                importedHistoryRepository = historyController.importedHistoryRepository,
                compatibilityStore = mediaServices?.store,
                entry = entry,
                onAutoSavedThreadDeleteFailure = {
                    Logger.e(FUTABER_TAG, "Failed to delete the auto-saved copy", it)
                    notify(FUTABER_AUTO_SAVE_DELETE_FAILED_NOTICE)
                }
            )
        }
    }

    // B8 (FutaberAiHost.kt): the AI commands forwarded to the thread screen and the catalog.
    val aiScreens = rememberFutaberAiScreens()
    FutaberTheme(themeMode, statusFollowsNavBar = !(settingsOpen || postTarget != null),
        fontFamily = rememberFutaberCustomFontFamily(displaySettings.extCustomFont, mediaServices?.fileSystem, preferences)) {
        CompositionLocalProvider(
            LocalFutaberDisplaySettings provides displaySettings, LocalFutaberAppTasks provides appTasks,
            LocalFutaberWallpaperFiles provides mediaServices?.fileSystem
        ) {
        mediaHost {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val drawerWidth = maxWidth * FUTABER_DRAWER_WIDTH_FRACTION
            val drawerProgress by animateFloatAsState(
                targetValue = if (drawerOpen) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "futaber-drawer"
            )
            val density = androidx.compose.ui.platform.LocalDensity.current
            val drawerWidthPx = with(density) { drawerWidth.toPx() }
            // The list sits at the start edge, so in a right-to-left layout the screen is pushed the other way.
            val drawerDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

            // Only whether the drawer is drawn is read here; the slide itself is read where the screen is laid out, so the
            // animation does not compose the whole app on every frame (re-audit M22).
            val drawerDrawn by remember { androidx.compose.runtime.derivedStateOf { drawerProgress > 0f } }
            if (drawerDrawn) {
                FutaberBoardDrawer(
                    boardsFromBottom = com.valoser.futacha.shared.ui.board.boardsFromBottom(preferences),
                    boards = boards,
                    selectedBoardId = board?.id,
                    modifier = Modifier.width(drawerWidth).fillMaxHeight(),
                    onSelect = { selected ->
                        mixId = null
                        // A write screen left open would stay on top of another board's catalog.
                        postTarget?.let { saveDraftNow(it, postSubject, postComment) }
                        postTarget = null
                        quoteMode = false
                        tabSheet = null
                        selectedBoardId = selected.id
                        openThread = null
                        offlineView = null
                        drawerOpen = false
                        writePreference(FutaberPreferenceKeys.LAST_BOARD_ID, selected.id)
                    },
                    onAddBoard = { name, url -> editBoards { futaberAddBoard(it, name, url) } },
                    onDeleteBoard = { target ->
                        editBoards { futaberDeleteBoard(it, target.id) }
                        if (favorites.any { it.boardId == target.id }) editFavorites { futaberFavoritesWithoutBoard(it, target.id) }
                    },
                    onMoveBoard = { target, up -> editBoards { futaberMoveBoard(it, target.id, up) } },
                    onRenameBoard = { target, name -> editBoards { futaberRenameBoard(it, target.id, name) } },
                    onBulkAdd = bulkAddBoards,
                    onTogglePin = if (displaySettings.boardPin) { target -> editBoards { futaberTogglePin(it, target.id) } } else null,
                    overlay = screenOverlay,
                    onReorderBoards = { ids -> editBoards { futaberReorderBoards(it, ids) } },
                    onOpenAimogeCalendar = if (com.valoser.futacha.shared.ui.aimoge.aimogeEventCalendarAvailable(boards.map { it.url })) {
                        { drawerOpen = false; aimogeCalendarOpen = true }
                    } else null,
                    onOpenMix = if (com.valoser.futacha.shared.compat.MixAvailability.enabled && mediaServices != null && com.valoser.futacha.shared.ui.board.mixOfferable(boards.map { it.url }, preferences[com.valoser.futacha.shared.mix.MIX_DEFINITIONS_KEY])) {
                        {
                            drawerOpen = false
                            launchStore("open mix") {
                                val id = com.valoser.futacha.shared.ui.board.firstMixIdOrCreate(
                                    mediaServices.store, boards.map { it.url }, kotlin.time.Clock.System.now().toEpochMilliseconds(), confirmMixMembers
                                ).idOrReport(notify)
                                if (id != null) {
                                    catalogOpener.reset()
                                    openThread = null
                                    offlineView = null
                                    managePanelOpen = false
                                    mixId = id
                                }
                            }
                        }
                    } else null
                )
            }
            Box(
                Modifier.fillMaxSize().offset { IntOffset(drawerDirection * (drawerWidthPx * drawerProgress).roundToInt(), 0) }
            ) {
                // Catalog ⇄ thread slide like iOS's push (App Store review 2026-11-16). The thread a screen shows comes from the
                // screen itself, so the thread sliding out on "back" keeps drawing its thread. One id for every thread (and one
                // for every catalog): going from a thread to another (a tab) keeps the screen as before, without a slide.
                // The screen keeps everything it draws (board, MIX, saved copy, thread): a thread sliding out after another board
                // was chosen must not read the new board and fetch its own thread number there (audit 2026-11-17 M2).
                val shownState = FutaberShownScreen(mixId, offlineView, board, thread,
                    FutaberShownCatalog(sortMode, catalogNgIndex, catalogSharedNg, catalogPhashRules, catalogSearch, catalogView, catalogExtras))
                val transitionScreen = when {
                    com.valoser.futacha.shared.compat.MixAvailability.enabled && mixId != null && mediaServices != null ->
                        com.valoser.futacha.shared.ui.PushPopScreen("mix:$mixId", -1, shownState)
                    offlineView != null -> com.valoser.futacha.shared.ui.PushPopScreen("offline:${offlineView?.ref?.threadId}", -1, shownState)
                    board == null -> com.valoser.futacha.shared.ui.PushPopScreen("none", -1, shownState)
                    thread != null -> com.valoser.futacha.shared.ui.PushPopScreen("thread", 1, shownState)
                    else -> com.valoser.futacha.shared.ui.PushPopScreen("catalog", 0, shownState)
                }
                com.valoser.futacha.shared.ui.PushPopTransition(transitionScreen) { shown ->
                @Suppress("NAME_SHADOWING") val thread = shown.value.thread
                @Suppress("NAME_SHADOWING") val board = shown.value.board
                val shownCatalog = requireNotNull(shown.value.catalog)
                @Suppress("NAME_SHADOWING") val sortMode = shownCatalog.sortMode
                @Suppress("NAME_SHADOWING") val catalogNgIndex = shownCatalog.ngIndex
                @Suppress("NAME_SHADOWING") val catalogSharedNg = shownCatalog.sharedNg
                @Suppress("NAME_SHADOWING") val catalogPhashRules = shownCatalog.phashRules
                @Suppress("NAME_SHADOWING") val catalogSearch = shownCatalog.search
                @Suppress("NAME_SHADOWING") val catalogView = shownCatalog.view
                @Suppress("NAME_SHADOWING") val catalogExtras = shownCatalog.extras
                @Suppress("NAME_SHADOWING") val openKey = thread?.let { FutaberTabKey(it.boardId, it.threadId) }
                val shownMixId = shown.value.mixId
                val shownOffline = shown.value.offline
                when {
                    com.valoser.futacha.shared.compat.MixAvailability.enabled && shownMixId != null && mediaServices != null -> com.valoser.futacha.shared.ui.board.MixScreenHost(
                        mixId = shownMixId,
                        store = mediaServices.store,
                        repository = repository,
                        boards = boards,
                        stateStore = stateStore,
                        httpClient = mediaServices.httpClient,
                        onExit = { mixId = null },
                        onSwitch = { mixId = it },
                        stateHolder = mixStateHolder,
                        fileSystem = mediaServices.fileSystem,
                        modePolicy = com.valoser.futacha.shared.ui.board.MixModePolicy.FUTABER,
                        gridColumns = futaberMixGridColumns(displayStyle, displaySettings.catalogFine),
                        onUnavailable = notify,
                        onOpen = { entry ->
                            // Back from the thread lands on that board's catalog, not on the MIX (plan: Back closes the MIX).
                            mixId = null
                            openRef(entry.board, FutaberThreadRef(entry.board.id, entry.item.id, entry.item.title.orEmpty(), entry.item.thumbnailUrl.orEmpty(), entry.replyCountOrNull ?: 0))
                        }
                    )
                    shownOffline != null -> shownOffline.let { view ->
                        val viewRepository = if (view.board.isMockBoard()) mockRepository else repository
                        val offlineRepository = remember(view) {
                            FutaberOfflineRepository(viewRepository, view.ref.threadId, view.page)
                        }
                        FutaberThreadScreen(
                            board = view.board,
                            ref = view.ref,
                            repository = offlineRepository,
                            isTabbed = false,
                            onToggleTab = {},
                            onOpenManage = { managePanelOpen = !managePanelOpen },
                            onOpenPost = { quote ->
                                // A copy of a board that is not registered here has no board to write to.
                                if (!futaberMhtCanPost(view.board)) notify("この板は登録されていないため、書き込めません")
                                else openPost(FutaberPostTarget.Reply(view.board.id, view.ref.threadId, view.ref.title), quote)
                            },
                            refreshSignal = 0,
                            quoteMode = false,
                            onQuoteLine = {},
                            onOpenDrawer = { drawerOpen = true },
                            drawerGestureEnabled = drawerGestureEnabled,
                            ngWords = ngWords,
                            ngHeaders = ngHeaders,
                            ngRules = ngRules,
                            onChangeNgWords = { edit -> launchStore("save NG words") { stateStore.updateNgWords(edit) } },
                            onChangeNgHeaders = { edit -> launchStore("save NG headers") { stateStore.updateNgHeaders(edit) } },
                            // A saved copy is not a visit: nothing is written to the history or the reading position.
                            onPageLoaded = {},
                            onScrollSettled = { _, _, _, _ -> },
                            onBack = { if (drawerOpen) drawerOpen = false else offlineView = null },
                            mediaServices = mediaServices,
                            offlineNotice = "保存したコピーを表示中です。最新のスレッドとは異なる場合があります",
                            features = sharedFeatures,
                            tabStrip = tabStrip,
                            stateStore = stateStore,
                            ai = aiScreens.threadLink()
                        )
                    }
                    board == null -> FutaberNoBoards(
                        onAddBoard = { drawerOpen = true },
                        onOpenSettings = { settingsOpen = true }
                    )
                    thread != null -> FutaberThreadScreen(
                        initialKnownPostCount = thread.seenCount.takeIf { it > 0 },
                        initialKnownLastPostId = thread.seenLastPostId ?: history.firstOrNull { it.boardId == board.id && it.threadId == thread.threadId }
                            ?.lastViewedSnapshot?.takeIf { it.postCount == thread.seenCount }?.lastPostId,
                        board = board,
                        ref = thread,
                        repository = if (board.isMockBoard()) mockRepository else threadRepository,
                        deleteKey = deleteKey,
                        // The auto-saved copy ふたちゃ falls back to when the thread cannot be read.
                        loadOfflineCopy = remember(autoSavedThreadRepository, savedFileSystem, board, thread.threadId) {
                            futaberAutoSavedCopyLoader(autoSavedThreadRepository, savedFileSystem, board, thread.threadId)
                        },
                        // Behind the writing screen (not quote mode, which shows the thread) and the settings the thread waits.
                        paused = (postTarget != null && quoteTarget == null) || settingsOpen,
                        // The board drawer and the manage panel cover the thread: the volume keys leave it alone (audit L17).
                        volumeKeysCovered = drawerOpen || managePanelOpen || tabSheet != null,
                        selfPostIds = remember(selfPostMap, board.id, thread.threadId) {
                            com.valoser.futacha.shared.ui.board.buildSelfPostIdentifierSet(
                                com.valoser.futacha.shared.ui.board.buildPersistedSelfPostIdentifiers(
                                    selfPostMap,
                                    com.valoser.futacha.shared.ui.board.buildThreadScopedSelfPostKey(board.id, thread.threadId),
                                    thread.threadId
                                )
                            )
                        },
                        isTabbed = openKey in tabKeys,
                        onToggleTab = { openKey?.let(toggleTab) },
                        isFavorite = openKey?.let { futaberIsFavorite(favorites, it) } == true,
                        onToggleFavorite = {
                            val favorite = futaberFavoriteFor(board, thread, Clock.System.now().toEpochMilliseconds())
                            editFavorites { futaberToggleFavorite(it, favorite) }
                        },
                        onOpenManage = { managePanelOpen = !managePanelOpen },
                        onOpenPost = { quote ->
                            openPost(FutaberPostTarget.Reply(board.id, thread.threadId, thread.title), quote)
                        },
                        refreshSignal = threadRefreshSignal,
                        quoteMode = quoteTarget != null,
                        onQuoteLine = { line -> postComment = futaberAppendQuote(postComment, ">$line") },
                        onOpenDrawer = { drawerOpen = true },
                        drawerGestureEnabled = drawerGestureEnabled,
                        ngWords = ngWords,
                        ngHeaders = ngHeaders,
                        ngRules = ngRules,
                        onChangeNgWords = { edit -> launchStore("save NG words") { stateStore.updateNgWords(edit) } },
                        onChangeNgHeaders = { edit -> launchStore("save NG headers") { stateStore.updateNgHeaders(edit) } },
                        onPageLoaded = { posts ->
                            viewedLastPostIds.clear()
                            posts.lastOrNull()?.id?.let { viewedLastPostIds[openKey] = posts.size to it }
                            launchStore("update history after load") {
                                stateStore.updateHistory { futaberHistoryAfterLoad(it, board.id, thread.threadId, posts) }
                            }
                        },
                        onConfirmedGone = {
                            launchStore("mark the thread fallen") {
                                stateStore.updateHistory { futaberHistoryMarkFallen(it, board.id, thread.threadId) }
                            }
                        },
                        onLivePageConfirmed = {
                            launchStore("mark the thread alive") {
                                val now = Clock.System.now().toEpochMilliseconds()
                                stateStore.updateHistory { futaberHistoryMarkAlive(it, board.id, thread.threadId, now) }
                            }
                            scope.launch {
                                futaberNoteViewedThread(mediaServices?.store, preferences, board, thread.threadId, onArchiveReportEnqueued)
                            }
                        },
                        onScrollSettled = { index, offset, postId, total ->
                            // How far this person has read is written only when the reading position settles (or the
                            // screen is left): this is the count the next visit marks new posts from.
                            // (A value that is already stored is not written again.)
                            editPreference(FutaberPreferenceKeys.SEEN) { stored ->
                                futaberSeenCountsAfterForBoard(stored, board, thread.threadId, total, viewedLastPostIds[openKey]?.takeIf { it.first == total }?.second)
                            }
                            launchStore("save reading position") {
                                stateStore.updateHistoryScrollPositionImmediately(
                                    AppStateHistoryScrollUpdateRequest(
                                        threadId = thread.threadId,
                                        index = index,
                                        offset = offset,
                                        postId = postId,
                                        boardId = board.id,
                                        title = thread.title,
                                        titleImageUrl = thread.thumbnailUrl,
                                        boardName = board.name,
                                        boardUrl = futaberHistoryThreadUrl(board, thread.threadId),
                                        replyCount = total
                                    )
                                )
                            }
                        },
                        onBack = threadBack,
                        mediaServices = mediaServices,
                        features = sharedFeatures,
                        // The volume keys' "タブのスレッドを切り替え": the neighbour in the tab strip's order.
                        onVolumeKeyThread = { forward ->
                            futaberAdjacentTab(tabViews.map { it.key }, openKey, forward)
                                ?.let { key -> tabViews.firstOrNull { it.key == key } }?.let(openTab)
                        },
                        onWritePreference = writePreference,
                        tabStrip = tabStrip,
                        onOpenCacheSearch = catalogExtras.openCacheSearch,
                        cacheSearchEnabled = catalogExtras.cacheSearchEnabled,
                        ngDetail = futaberNgDetailEntry(displaySettings.extNgDetail, sharedFeatures != null, board, thread.threadId) { parityB7b.ngDetail = it },
                        stateStore = stateStore,
                        ai = aiScreens.threadLink(),
                        onOpenNgSettings = { parityB7b.settingsAtNg = true; settingsOpen = true },
                        handoff = remember(openKey) { catalogOpener.take(openKey) },
                        // Made again only when what it is made from changes (re-audit M22: every composition, a keystroke on
                        // the write screen included, searched the whole history for the thread).
                        autoSave = remember(
                            displaySettings.extAutoSaveView, autoSavedThreadRepository, mediaServices, stateStore, history, board, thread.threadId
                        ) {
                            futaberAutoSaveLink(
                                displaySettings.extAutoSaveView, autoSavedThreadRepository, mediaServices, stateStore, history, board, thread.threadId
                            )
                        }
                    )
                    else -> FutaberCatalogScreen(
                        // V12: the rows of the threads in the shared history are grey.
                        readThreadIds = remember(history, board.id, board.url) { futaberReadThreadIds(history, board) },
                        viewedFirstEnabled = preferences[com.valoser.futacha.shared.ui.board.viewedThreadFirstKey(board.url)] == "ON",
                        lastViewedThreadId = history.filter { it.boardId == board.id }.maxByOrNull { it.lastVisitedEpochMillis }?.threadId,
                        onViewedFirstChange = { writePreference(com.valoser.futacha.shared.ui.board.viewedThreadFirstKey(board.url), if (it) "ON" else "OFF") },
                        board = board,
                        repository = if (board.isMockBoard()) mockRepository else repository,
                        displayStyle = displayStyle,
                        sortMode = sortMode,
                        onDisplayStyleChange = { writePreference(FutaberPreferenceKeys.CATALOG_DISPLAY_STYLE, it.persistedValue) },
                        onSortChange = { mode ->
                            if (displaySettings.extSortByBoard) {
                                val boardIds = boards.map { it.id } + boards.map(::futaberStoredBoardId)
                                editPreference(FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD) { stored ->
                                    futaberSortByBoardAfter(stored, board.id, mode, boardIds, futaberStoredBoardId(board))
                                }
                            } else writePreference(FutaberPreferenceKeys.CATALOG_SORT, mode.name)
                        },
                        onOpenDrawer = { drawerOpen = true },
                        // The swipes leave the catalog alone while the board list, the panel, the settings, the writing
                        // screen, a tab sheet or a dialog is on top (the same conditions as the thread's).
                        swipeEnabled = drawerGestureEnabled,
                        // As in the original, a swipe to the left goes back into the thread last read on this board.
                        onSwipeForward = {
                            history.filter { it.boardId == board.id }.maxByOrNull { it.lastVisitedEpochMillis }
                                ?.let { entry -> futaberRefForHistory(entry, boards) }
                                ?.let { ref -> openRef(board, ref) }
                        },
                        onOpenSettings = { settingsOpen = true },
                        onOpenManage = { managePanelOpen = !managePanelOpen },
                        onCreateThread = { openPost(FutaberPostTarget.CreateThread(board.id), "") },
                        refreshSignal = catalogRefreshSignal,
                        catalogNgWords = catalogNgWords,
                        catalogSharedNg = catalogSharedNg,
                        catalogNgIndex = catalogNgIndex,
                        onOpenThread = { item -> openRef(board, item.toFutaberThreadRef(board.id)) },
                        opener = catalogOpener,
                        onOpenRow = { item ->
                            catalogOpener.open(scope, board.id, item.id, fetch = {
                                (if (board.isMockBoard()) mockRepository else threadRepository).getThreadContent(board.url, item.id)
                            }) { openRef(board, item.toFutaberThreadRef(board.id)) }
                        },
                        archiveHttpClient = mediaServices?.httpClient,
                        // M05: the original's items (履歴から削除 only for a thread in the history), then this app's own
                        // and the extension (futaberCatalogSheetExtras, for F-B2).
                        longPressActions = { item ->
                            val inHistory = history.firstOrNull { it.boardId == board.id && it.threadId == item.id }
                            futaberCatalogSheetActions(
                                inHistory = inHistory != null,
                                ngThreadEnabled = mediaServices != null && futaberNgScopeKey(board) != null,
                                // M13: the thread's address through the registered URL scheme action.
                                onUrlScheme = { urlSchemeOpener.action(futaberHistoryThreadUrl(board, item.id)) },
                                onCopyUrl = { clipboardManager.setText(AnnotatedString(futaberHistoryThreadUrl(board, item.id))) },
                                // Hides this thread from the catalog; the rule is the one ふたちゃ and としあき(仮) use, and
                                // can be taken back in the settings (NG > NGスレッド).
                                onNgThread = { registerNgThread(item) },
                                onRemoveHistory = { inHistory?.let(deleteHistory) },
                                extras = futaberCatalogSheetExtras(
                                    onBrowser = { openInBrowser(futaberHistoryThreadUrl(board, item.id)) },
                                    onReport = { catalogReport = board to item },
                                    // An extension: the picture of this thread hides look-alike catalog pictures (the shared rule).
                                    imageNg = futaberCatalogImageNgAction(
                                        displaySettings.extCatalogLongPress, item, futaberNgScopeKey(board), sharedFeatures != null
                                    ) { catalogImageNg = it }
                                )
                            )
                        },
                        phashRules = catalogPhashRules,
                        phashThreshold = catalogPhashThreshold,
                        phashStore = mediaServices?.store,
                        view = catalogView,
                        search = catalogSearch,
                        tabStrip = tabStrip,
                        extraSorts = catalogExtras.extraSorts,
                        watchWords = catalogExtras.watchWords,
                        projection = catalogExtras.projection,
                        refreshActions = catalogExtras.refreshActions + listOfNotNull(
                            if (!displaySettings.extOpenUrl) null else FutaberPostAction("open-url", "URLから開く", extra = true) {
                                screenOverlay.show {
                                    FutaberOpenUrlAlert(screenOverlay::dismiss) { raw ->
                                        // The same reading as a link from another app: a registered board opens, an unregistered one is offered.
                                        val parsed = com.valoser.futacha.shared.compat.canonicalizeThreadUrl(raw)
                                        val owner = parsed?.let { link ->
                                            boards.firstOrNull { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it.url) == link.canonicalBoardUrl }
                                        }
                                        val boardOnly = com.valoser.futacha.shared.compat.canonicalizeBoardUrl(raw)
                                            ?.let { c -> boards.firstOrNull { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it.url) == c } }
                                        when {
                                            parsed != null && owner != null -> openRef(owner, FutaberThreadRef(owner.id, parsed.threadNo, "", "", 0))
                                            parsed == null && boardOnly != null -> openBoardCatalog(boardOnly)
                                            else -> futaberBoardAddRequest(raw)?.let(addBoardAndOpen) ?: notify("このURLを開けません")
                                        }
                                    }
                                }
                            },
                            futaberNgDetailCatalogAction(
                            futaberNgDetailEntry(displaySettings.extNgDetail, sharedFeatures != null, board, null) { parityB7b.ngDetail = it }
                        )),
                        perGenerationBack = catalogExtras.perGenerationBack
                    )
                }
                }
                FutaberCatalogImageNgHost(catalogImageNg, sharedFeatures, boards) { catalogImageNg = null }
                FutaberNgDetailHost(parityB7b.ngDetail, sharedFeatures, boards) { parityB7b.ngDetail = null }
                FutaberCatalogExtrasHost(catalogExtras, board, mediaServices, preferences) { target, item ->
                    openRef(target, item.toFutaberThreadRef(target.id))
                }
                if (managePanelOpen) {
                    // The panel sits between the top (the catalog's navigation bar, or the thread's title row) and the bottom
                    // bar, which stay visible but dimmed; tapping either closes it, and so does the panel button again.
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val topInset = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
                    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
                    val topEdge = topInset + futaberManagePanelTopDp(threadShown = thread != null || offlineView != null).dp
                    val bottomEdge = bottomInset + FUTABER_BOTTOM_BAR_HEIGHT_DP.dp
                    Box(Modifier.fillMaxSize().padding(top = topEdge, bottom = bottomEdge)) {
                        FutaberManagePanel(
                            onRefreshHistory = onRefreshHistory,
                            history = history,
                            boards = boards,
                            tabs = tabViews,
                            savedBox = savedBox,
                            savedBoxAvailable = savedRepository != null,
                            onOpenSaved = openSaved,
                            onDeleteSaved = deleteSaved,
                            mht = mhtBox,
                            onClearSavedBox = clearSavedBox,
                            notifications = externalWatcher?.results ?: watchResults,
                            notificationsAvailable = watcher != null,
                            notificationsStatus = externalWatcher?.results?.let { externalWatcher.status } ?: futaberNotificationsStatus(preferences),
                            onOpenNotification = { result ->
                                futaberRefForWatchResult(result, boards)?.let { ref ->
                                    boards.firstOrNull { it.id == ref.boardId }?.let { target -> openRef(target, ref) }
                                }
                            },
                            onRemoveNotification = { result ->
                                if (externalWatcher?.results != null) externalWatcher.remove(result)
                                else launchStore("remove a patrol result") { watcher?.delete(result.history.canonicalUrl) }
                            },
                            onClearNotifications = {
                                if (externalWatcher?.results != null) externalWatcher.clear()
                                else launchStore("clear the patrol results") { watcher?.deleteAll() }
                            },
                            onOpenWatcherManager = { watcherOpen = true },
                            favorites = favoriteViews,
                            onOpenFavorite = { view ->
                                futaberRefForFavorite(view, boards)?.let { ref ->
                                    boards.firstOrNull { it.id == ref.boardId }?.let { target -> openRef(target, ref) }
                                }
                            },
                            onRemoveFavorite = { view -> editFavorites { futaberRemoveFavorite(it, view.favorite.key) } },
                            onClearFavorites = { editFavorites { emptyList() } },
                            onOpenHistory = { entry ->
                                futaberRefForHistory(entry, boards)?.let { ref ->
                                    boards.firstOrNull { it.id == ref.boardId }?.let { target -> openRef(target, ref) }
                                }
                            },
                            onDeleteHistory = deleteHistory,
                            onOpenTab = openTab,
                            onRemoveTab = { view -> saveTabs { futaberRemoveTab(it, view.key) } },
                            onClearTabs = { closeTabsInBulk { emptyList() } },
                            onClose = { managePanelOpen = false },
                            onAddBoardAndOpen = addBoardAndOpen,
                            onReopenSaved = if (displaySettings.extSavedDetail) { saved ->
                                futaberBoardForSaved(saved, boards)?.let { target -> openRef(target, futaberRefForSaved(saved, target)) }
                            } else null,
                            otherModeTabs = if (displaySettings.extOtherFavorites) mediaServices?.store?.tabs else null,
                            onOpenOtherFavorite = { target, ref -> openRef(target, ref) },
                            historyRowsMemo = manageHistoryRowsMemo,
                            historyTools = futaberHistoryTools(
                                historyController,
                                clearEnabled = displaySettings.extHistory,
                                archiveEnabled = displaySettings.extHistoryArchive && savedFileSystem != null
                            ),
                            // L-B10: the category chosen last (S24), 「すべて削除」 of the history (M29) and the alerts over the screen.
                            initialCategory = manageStartCategory ?: futaberManageCategoryFromStored(preferences[FutaberPreferenceKeys.MANAGE_CATEGORY]),
                            onCategoryChange = { writePreference(FutaberPreferenceKeys.MANAGE_CATEGORY, it.persisted) },
                            onClearHistory = historyController::openClear,
                            overlay = screenOverlay
                        )

                    }
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topEdge)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f))
                            .clickable(onClickLabel = "パネルを閉じる") { managePanelOpen = false }
                            .testTag("futaber-manage-close-top")
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomEdge)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f))
                            .clickable(onClickLabel = "パネルを閉じる") { managePanelOpen = false }
                            .testTag("futaber-manage-close")
                    )
                }
                tabSheet?.let { tab -> FutaberTabSheetHost(tab, tabViews, tabKeys, onClose = { tabSheet = null }, onRemoveTab = {
                    saveTabs { list -> futaberRemoveTab(list, tab.key) }
                }, closeTabsInBulk = closeTabsInBulk, restore = if (displaySettings.extTabs) {
                    FutaberPostAction(
                        "tab-restore", "閉じたタブを元に戻す（${restorableTabCount}）", enabled = restorableTabCount > 0
                    ) { restoreTab() }
                } else null, checkAll = futaberTabCheckAction(displaySettings.extTabsCheck, tabChecker) {
                    tabChecker.check(futaberTabCheckTargets(tabKeys, history, boards), boards, onMessage = notify)
                }, closeFallen = futaberTabCloseFallenAction(displaySettings.extTabsCloseFallen, tabChecker) {
                    // F-B4 E2: check every tab, then close the ones the check found fallen (those already fallen go too).
                    val already = futaberFallenTabKeys(tabKeys, history)
                    if (already.isNotEmpty()) closeTabsInBulk { futaberRemoveFallenTabs(it, already) }
                    tabChecker.check(futaberTabCheckTargets(tabKeys, history, boards), boards, onChecked = { after ->
                        val fallenNow = futaberFallenTabKeys(tabKeys, after)
                        if (fallenNow.isNotEmpty()) closeTabsInBulk { futaberRemoveFallenTabs(it, fallenNow) }
                    }, onMessage = notify)
                }) }
                // The write screen reads the text being typed in a scope of its own, so a keystroke does not compose the thread or
                // the catalog behind it again (re-audit M22).
                FutaberRecomposeScope {
                    if (quoteTarget != null) {
                        FutaberQuoteStrip(
                            comment = postComment,
                            onBackToWriting = { quoteMode = false },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                        PlatformBackHandler(enabled = true) { quoteMode = false }
                    } else postTarget?.let { target ->
                        val targetBoard = boards.firstOrNull { it.id == target.boardId }
                        // A board that is gone drops the write screen (the effect above); nothing is drawn meanwhile.
                        if (targetBoard != null) {
                            FutaberPostScreen(
                                target = target,
                                // V37: the board's address without its page, as the catalog's heading.
                                boardAddress = futaberCatalogHeading(targetBoard.url),
                                legacySubjectField = displaySettings.legacySubjectField,
                                cleared = postCleared,
                                onImageQualityChange = { writePreference(FutaberPreferenceKeys.POST_IMAGE_QUALITY, it.persisted) },
                                boardUrl = targetBoard.url,
                                destinationConfirm = displaySettings.postDestinationConfirm,
                                subject = postSubject,
                                comment = postComment,
                                onSubjectChange = { postSubject = it },
                                onCommentChange = { postComment = it },
                                // Quoting needs the thread the reply is for to be on screen behind the writing.
                                onEnterQuoteMode = if (target is FutaberPostTarget.Reply && thread?.threadId == target.threadId) {
                                    { quoteMode = true }
                                } else null,
                                attachment = postAttachment,
                                capabilities = postCapabilities
                                    ?: com.valoser.futacha.shared.network.defaultBoardPostingCapabilities(targetBoard.url),
                                pickerPreference = pickerPreference,
                                preferredFileManagerPackage = preferredFileManager?.packageName,
                                settings = postSettings,
                                deleteKey = deleteKey,
                                onSettingsChange = { name, email, confirm ->
                                    writePreference(FutaberPreferenceKeys.POST_NAME, name)
                                    writePreference(FutaberPreferenceKeys.POST_EMAIL, email)
                                    writePreference(FutaberPreferenceKeys.POST_CONFIRM, if (confirm) "ON" else "OFF")
                                },
                                onDeleteKeyChange = { key -> launchStore("save the delete key") { stateStore.setLastUsedDeleteKey(key) } },
                                onSend = { input -> sendPost(target, input) },
                                tools = FutaberPostTools(
                                    enabled = displaySettings.extPostTools,
                                    httpClient = mediaServices?.httpClient,
                                    fileSystem = mediaServices?.fileSystem,
                                    drawingCopyLocation = futaberDrawingCopyLocation(preferences),
                                    appVersion = appVersion,
                                    cookieRepository = mediaServices?.cookieRepository,
                                    openCookies = if (mediaServices?.cookieRepository != null) { { cookiesOpen = true } } else null,
                                    pickerPreference = pickerPreference,
                                    preferredFileManagerPackage = preferredFileManager?.packageName
                                ),
                                pasteboard = pasteboardLink,
                                onSent = { posted ->
                                    // What was typed names a new thread until the thread itself is read.
                                    val sentSubject = postSubject
                                    postSubject = ""
                                    postComment = ""
                                    postAttachment.clear()
                                    editDrafts { futaberDiscardDraft(it, target, boards) }
                                    postTarget = null
                                    when (target) {
                                        is FutaberPostTarget.Reply -> threadRefreshSignal += 1
                                        is FutaberPostTarget.CreateThread -> {
                                            if (board?.id == target.boardId) catalogView.afterThreadCreated()
                                            catalogRefreshSignal += 1
                                            // The new thread opens at once when the server named it.
                                            if (!posted.isNullOrBlank()) {
                                                openRef(targetBoard, FutaberThreadRef(targetBoard.id, posted, futaberNewThreadTitle(sentSubject), "", 0))
                                            }
                                        }
                                    }
                                },
                                onClose = {
                                    saveDraftNow(target, postSubject, postComment)
                                    postTarget = null
                                }
                            )
                        }
                    }
                }
                // L-B12 (C14〜C16): the wallpaper of the screen that is up, faintly over it (it takes no touch).
                FutaberWallpaperOverlay(
                    when {
                        postTarget != null -> FutaberWallpaperSlot.Post
                        thread != null || offlineView != null -> FutaberWallpaperSlot.Thread
                        else -> FutaberWallpaperSlot.Catalog
                    }
                )
                if (drawerOpen) {
                    // Tapping the pushed-aside screen closes the board list; it is dimmed a little meanwhile (V31).
                    Box(
                        Modifier.fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = FUTABER_DRAWER_DIM_ALPHA))
                            .clickable(onClickLabel = "板一覧を閉じる") { drawerOpen = false }
                            .testTag("futaber-drawer-scrim")
                    )
                }
            }
            // L-B10: an alert or a sheet the board list or the panel asked for, over the whole screen.
            FutaberScreenOverlayHost(screenOverlay)
        }
        // Registered while the list is open, so it is the newest handler and Back closes the list before the screen under it
        // (a handler composed once at the start would be older than the thread screen's and lose to it). S27: iOS's edge
        // swipe does not close it (futaber's); Android's system Back does (look doc §1.6).
        if (drawerOpen) PlatformBackHandler(enabled = true, iosEdgeGestureEnabled = false) { drawerOpen = false }
        // First launch without a board choice: show the board list straight away.
        LaunchedEffect(boards.isEmpty()) {
            if (boards.isNotEmpty() && selectedBoardId == null && preferences[FutaberPreferenceKeys.LAST_BOARD_ID] == null) {
                drawerOpen = true
            }
        }
        val manageStore = mediaServices?.store
        if (watcherOpen && manageStore != null) {
            // The patrol scans the boards of the shared store; make sure this mode's boards are in it.
            LaunchedEffect(boards) { launchStore("share the boards with the patrol") { manageStore.importModernBoards(boards) } }
            CompositionLocalProvider(LocalCompatibilityPalette provides futaberMediaPalette(LocalFutaberColors.current)) {
                CompatWatcherManager(
                    store = manageStore,
                    repository = repository,
                    onDismiss = { watcherOpen = false },
                    onResultsChanged = {},
                    onOpenExternal = externalWatcher?.let { it::openManager },
                    resultsLocation = "管理画面の「通知」"
                )
            }
        }
        FutaberBoardAddHost(boardAdder, boards, stateStore, openRef, openBoardCatalog, notify)
        FutaberAiCatalogReceiver(aiScreens, board, catalogView, catalogSearch, shown = (!com.valoser.futacha.shared.compat.MixAvailability.enabled || mixId == null) && offlineView == null && thread == null)
        FutaberAiCommandHost(
            screens = aiScreens,
            inbox = aiInbox,
            stateStore = stateStore,
            context = FutaberAiContext(
                boards = boards, history = history,
                currentBoard = if (!com.valoser.futacha.shared.compat.MixAvailability.enabled || mixId == null) offlineView?.board ?: board else null,
                currentThread = if (!com.valoser.futacha.shared.compat.MixAvailability.enabled || mixId == null) offlineView?.ref ?: thread else null,
                mixVisible = com.valoser.futacha.shared.compat.MixAvailability.enabled && mixId != null,
                historyRefreshAvailable = onRefreshHistory != null,
                cookiesAvailable = mediaServices?.cookieRepository != null,
                savedThreadsAvailable = savedRepository != null,
                appVersion = appVersion
            ),
            actions = FutaberAiAppActions(
                setDrawerOpen = { drawerOpen = it },
                closeOverlays = { settingsOpen = false; parityB7b.settingsAtNg = false; managePanelOpen = false },
                openBoard = openBoardCatalog,
                refreshCatalog = { catalogRefreshSignal += 1 },
                openManage = { managePanelOpen = true },
                refreshHistory = onRefreshHistory,
                openThread = openRef,
                addBoardAndOpen = addBoardAndOpen,
                openSettings = { settingsOpen = true },
                openManageAt = { category -> manageStartCategory = category; managePanelOpen = true },
                openNgSettings = { parityB7b.settingsAtNg = true; settingsOpen = true },
                setCatalogMode = { target, mode ->
                    val offered = mode in futaberCatalogSortsFor(displaySettings.extCatalogModes).map { it.mode }
                    if (offered) {
                        if (displaySettings.extSortByBoard) {
                            val boardIds = boards.map { it.id } + boards.map(::futaberStoredBoardId)
                            editPreference(FutaberPreferenceKeys.CATALOG_SORT_BY_BOARD) { stored ->
                                futaberSortByBoardAfter(stored, target.id, mode, boardIds, futaberStoredBoardId(target))
                            }
                        } else writePreference(FutaberPreferenceKeys.CATALOG_SORT, mode.name)
                    }
                    offered
                },
                openCookies = if (mediaServices?.cookieRepository != null) { { cookiesOpen = true } } else null,
                openUrl = openInBrowser,
                // Carried out within the command, so its "しました" is told only once the change is stored (audit L18).
                deleteHistory = { entry ->
                    dismissHistoryEntry(
                        stateStore = stateStore,
                        autoSavedThreadRepository = autoSavedThreadRepository,
                        importedHistoryRepository = historyController.importedHistoryRepository,
                        compatibilityStore = mediaServices?.store,
                        entry = entry,
                        onAutoSavedThreadDeleteFailure = {
                            Logger.e(FUTABER_TAG, "Failed to delete the auto-saved copy", it)
                            notify(FUTABER_AUTO_SAVE_DELETE_FAILED_NOTICE)
                        }
                    )
                },
                clearHistory = { historyController.clearAll() },
                editBoards = { transform -> stateStore.updateBoards(transform) },
                openDraft = { target, subject, comment ->
                    openPost(target, comment)
                    if (subject.isNotEmpty()) postSubject = subject
                },
                draftRefused = { _ -> futaberAiDraftRefusedWhileSending(postInFlight) },
                savedRepository = savedRepository,
                onSavedChanged = { savedTick += 1 }
            ),
            notify = notify
        )
        FutaberHistoryDialogs(historyController)
        FutaberForegroundPatrol(
            displaySettings.extForegroundPatrol, mediaServices?.store, repository, boards, preferences, notify
        )
        val cookieServices = mediaServices
        if (cookiesOpen && cookieServices != null) {
            FutaberSharedSettingsHost("cookies", cookieServices, appVersion, stateStore) { cookiesOpen = false }
        }
        savedNotice?.let { text ->
            val colors = LocalFutaberColors.current
            FutachaAppLockAwareWindow {
                AlertDialog(
                    onDismissRequest = { savedNotice = null },
                    containerColor = colors.bar,
                    titleContentColor = colors.body,
                    textContentColor = colors.body,
                    text = { Text(text) },
                    confirmButton = { TextButton(onClick = { savedNotice = null }) { Text("閉じる", color = colors.link) } }
                )
            }
        }
        if (settingsOpen) {
            com.valoser.futacha.shared.ui.board.SettingsFontScale(preferences) {
                FutaberSettingsScreen(
                    themeMode = themeMode,
                    onThemeModeChange = { writePreference(FutaberPreferenceKeys.THEME, it.persistedValue) },
                    settings = displaySettings,
                    onSettingChange = writePreference,
                    postSettings = postSettings,
                    deleteKey = deleteKey,
                    onPostSettingsChange = { name, email, confirm ->
                        writePreference(FutaberPreferenceKeys.POST_NAME, name)
                        writePreference(FutaberPreferenceKeys.POST_EMAIL, email)
                        writePreference(FutaberPreferenceKeys.POST_CONFIRM, if (confirm) "ON" else "OFF")
                    },
                    onDeleteKeyChange = { key -> launchStore("save the delete key") { stateStore.setLastUsedDeleteKey(key) } },
                    ngWords = ngWords,
                    ngHeaders = ngHeaders,
                    catalogNgWords = catalogNgWords,
                    catalogThreadRules = catalogThreadRules,
                    catalogWordRules = catalogWordRules,
                    catalogImageRules = catalogImageRules,
                    imageNgRules = imageNgRules,
                    threadDetailRules = threadDetailRules,
                    // Worked out only when the list is opened (it keys every history row).
                    threadNgScopeTitles = { wanted -> futaberThreadNgScopeTitles(history, boards, wanted) },
                    onDeleteNgRule = deleteNgRule,
                    onDeleteNgRules = deleteNgRules,
                    // Each edit is applied to the list stored at that moment, so two quick "×" both count.
                    onEditCatalogNgWords = { edit -> launchStore("save catalog NG words") { stateStore.updateCatalogNgWords(edit) } },
                    onEditNgWords = { edit -> launchStore("save NG words") { stateStore.updateNgWords(edit) } },
                    onEditNgHeaders = { edit -> launchStore("save NG headers") { stateStore.updateNgHeaders(edit) } },
                    mediaServices = mediaServices,
                    appVersion = appVersion,
                    stateStore = stateStore,
                    onOpenNotifications = { watcherOpen = true },
                    onWatchAlertSettingChangeRequested = onWatchAlertSettingChangeRequested,
                    onNotice = notify,
                    onArchiveReportEnabledChanged = onArchiveReportEnabledChanged,
                    onClose = { settingsOpen = false; parityB7b.settingsAtNg = false },
                    startAtNg = parityB7b.settingsAtNg,
                    watchWords = stateStore.observedWatchWords.collectAsState(emptyList<String>()).value,
                    boardWatchWordCount = stateStore.observedBoardWatchWords.collectAsState(emptyMap<String, List<String>>()).value.size,
                    onEditWatchWords = { edit -> launchStore("save watch words") { stateStore.updateWatchWords(edit) } },
                    // C33: an NG thread typed by its address, kept as the catalog's long press keeps it.
                    ngThreadTarget = { input -> futaberNgThreadTarget(input, boards) },
                    onAddNgThread = { target -> registerNgThreadTarget(target) },
                    onShareBoardsWithPatrol = {
                        mediaServices?.store?.let { shared -> launchStore("share the boards with the patrol") { shared.importModernBoards(boards) } }
                    }
                )
            }
        }
        // The notice goes over everything, the settings screen included.
        appNotice?.let { text ->
            LaunchedEffect(text) {
                delay(FUTABER_NOTICE_MILLIS)
                if (appNotice == text) appNotice = null
            }
            val noticeColors = LocalFutaberColors.current
            // Above the bottom bar (and the tab strip) of the catalog and the thread; the other screens have none.
            val aboveBars = if (settingsOpen || postTarget != null) 0 else
                FUTABER_BOTTOM_BAR_HEIGHT_DP + if (tabViews.isNotEmpty()) FUTABER_TAB_STRIP_HEIGHT_DP else 0
            Box(Modifier.fillMaxSize()) {
                Text(
                    text = text,
                    color = noticeColors.onAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = aboveBars.dp)
                        .fillMaxWidth().background(noticeColors.accent)
                        .clickable(onClickLabel = "閉じる") { appNotice = null }
                        .padding(8.dp).testTag("futaber-app-notice")
                )
            }
        }
        }
        }
    }
}

/**
 * The screen with no board: a top bar with the settings (which also hold the mode switch) and the way to add one. The
 * board list opens in its edit mode, which has the "板を追加" row.
 */
@Composable
private fun FutaberNoBoards(onAddBoard: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = LocalFutaberColors.current
    Column(Modifier.fillMaxSize().background(colors.background).testTag("futaber-no-boards")) {
        Row(
            Modifier.fillMaxWidth().background(colors.navBar).statusBarsPadding().height(FUTABER_TOP_BAR_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ふたばー", color = colors.navTitle, fontSize = 17.sp,
                modifier = Modifier.weight(1f).padding(start = 16.dp)
            )
            FutaberNavButton(Icons.Outlined.Settings, "設定", "futaber-open-settings", 50, onOpenSettings)
        }
        HorizontalDivider(color = colors.navSeparator, thickness = 0.5.dp)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("板が登録されていません", color = colors.body, fontSize = 14.sp, textAlign = TextAlign.Center)
            TextButton(onClick = onAddBoard, modifier = Modifier.testTag("futaber-no-boards-add")) {
                Text("板を追加", color = colors.link)
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("futaber-no-boards-settings")) {
                Text("設定を開く", color = colors.link)
            }
        }
    }
}

/**
 * How far below the status area the manage panel starts: under the catalog's navigation bar, or under the thread's
 * title row (the thread has no bar at the top).
 */
internal fun futaberManagePanelTopDp(threadShown: Boolean): Int =
    if (threadShown) FUTABER_THREAD_TITLE_ROW_DP else FUTABER_TOP_BAR_HEIGHT_DP

/** Its own recomposition scope: state read in [content] composes only [content] again (re-audit M22). */
@Composable
private fun FutaberRecomposeScope(content: @Composable () -> Unit) {
    content()
}

/** What one screen of the catalog ⇄ thread slide draws, kept while it slides out. */
internal data class FutaberShownScreen(
    val mixId: String?,
    val offline: FutaberOfflineView?,
    val board: BoardSummary?,
    val thread: FutaberThreadRef?,
    val catalog: FutaberShownCatalog? = null
)

internal data class FutaberShownCatalog(
    val sortMode: com.valoser.futacha.shared.model.CatalogMode,
    val ngIndex: com.valoser.futacha.shared.compat.CompatCatalogRuleIndex?,
    val sharedNg: com.valoser.futacha.shared.ui.ftutter.FtutterNgMatcher?,
    val phashRules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    val search: FutaberCatalogSearchState,
    val view: FutaberCatalogViewState,
    val extras: FutaberCatalogExtras
)
