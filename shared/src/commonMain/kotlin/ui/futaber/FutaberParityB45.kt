package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.COMPAT_FOREGROUND_TICK_MILLIS
import com.valoser.futacha.shared.compat.COMPAT_WATCH_EXTERNAL_KEY
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.refreshSharedFeatures
import com.valoser.futacha.shared.compat.sharedFeatureRefreshEnabled
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.HistoryArchiveAction
import com.valoser.futacha.shared.ui.board.HistoryArchiveActionDialog
import com.valoser.futacha.shared.ui.board.HistoryExportSelectionDialog
import com.valoser.futacha.shared.ui.board.HistoryImportErrorDialog
import com.valoser.futacha.shared.ui.board.HistoryImportLoadingDialog
import com.valoser.futacha.shared.ui.board.HistoryImportSelectionDialog
import com.valoser.futacha.shared.ui.board.HistoryImportSelectionState
import com.valoser.futacha.shared.ui.board.LatestOnlyLoad
import com.valoser.futacha.shared.ui.board.buildBoardManagementHistoryArchiveFailureMessage
import com.valoser.futacha.shared.ui.board.enqueueViewedThreadArchiveReport
import com.valoser.futacha.shared.ui.board.loadHistoryImportSelectionState
import com.valoser.futacha.shared.ui.clearHistory
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcher
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcherEntry
import com.valoser.futacha.shared.ui.compat.CompatForegroundLifecycleEffect
import com.valoser.futacha.shared.ui.compat.isCompatWifiConnected
import com.valoser.futacha.shared.ui.compat.rememberCompatExternalWatcher
import com.valoser.futacha.shared.ui.exportAllFutachaHistoryArchive
import com.valoser.futacha.shared.ui.exportSelectedFutachaHistoryArchive
import com.valoser.futacha.shared.ui.importLatestFutachaHistoryArchive
import com.valoser.futacha.shared.ui.isMockBoard
import com.valoser.futacha.shared.ui.loadLatestFutachaHistoryArchivePreview
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.isAndroid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/*
 * The ふたばー風 mode's batches B4 (history and tabs) and B5 (patrol and the outside services) of the 3-mode feature
 * parity: ふたちゃ's history archive, "全件削除" of the history, a check of every tab, the patrol while this mode is on
 * screen, the external にじろぐ and "閲覧スレ通知". Everything but "閲覧スレ通知" sits behind an extension switch that is
 * off by default (設定 → 拡張機能); none of it sends analytics.
 */

private const val B45_TAG = "FutaberParityB45"

// ---- The history band's tools (H04 and "全件削除") ----

/** The history band's extra chips; a null one is not shown. */
internal class FutaberHistoryTools(
    val onClearAll: (() -> Unit)? = null,
    val onOpenArchive: (() -> Unit)? = null
)

internal sealed interface FutaberHistoryDialog {
    data object ConfirmClear : FutaberHistoryDialog
    data object ArchiveChooser : FutaberHistoryDialog
    data class ArchiveAction(val action: HistoryArchiveAction) : FutaberHistoryDialog
    data class ExportSelection(val history: List<ThreadHistoryEntry>) : FutaberHistoryDialog
    data class Import(val state: HistoryImportSelectionState) : FutaberHistoryDialog
}

/** What the person is told about an import here: the rows come back, the saved bodies open in ふたちゃ. */
internal const val FUTABER_HISTORY_IMPORT_NOTICE =
    "ふたばー風では履歴の一覧が戻ります。アーカイブに入っていたスレッドの本文は、ふたちゃで開けます。"

internal const val FUTABER_HISTORY_CLEAR_CONFIRMATION =
    "閲覧履歴をすべて削除しますか？\n" +
        "ふたちゃ・としあき(仮)の履歴、自分の書き込みの記録、自動保存したコピーも消えます。" +
        "タブ・お気に入り・保存箱は消えません（履歴の無いタブは、履歴が戻るまで表示されません）。"

internal const val FUTABER_HISTORY_CLEARED_NOTICE = "履歴をすべて削除しました"
internal const val FUTABER_HISTORY_CLEARED_PARTIAL_NOTICE = "履歴を削除しました。自動保存したコピーの一部は削除できませんでした"

/** The import's result line, with what this mode can and cannot open afterwards. */
internal fun futaberHistoryImportMessage(message: String): String =
    if (message.startsWith("履歴アーカイブを読み込みました")) "$message。$FUTABER_HISTORY_IMPORT_NOTICE" else message

/**
 * The history archive (ふたちゃ's own: a fixed folder of the app, no file picker) and "全件削除", run on the screen's
 * scope; each result is one line for [onMessage]. Only one of them runs at a time.
 */
@Stable
internal class FutaberHistoryController(private val scope: CoroutineScope, private val stateStore: AppStateStore) {
    var dialog by mutableStateOf<FutaberHistoryDialog?>(null)
    var running by mutableStateOf(false)
        private set
    internal var fileSystem: FileSystem? = null
    internal var autoSavedThreadRepository: SavedThreadRepository? = null
    internal var savedRepository: SavedThreadRepository? = null
    internal var importedHistoryRepository: SavedThreadRepository? = null
    internal var compatibilityStore: CompatibilityStore? = null
    internal var appVersion: String? = null
    internal var onMessage: (String) -> Unit = {}
    private val importPreviewLoad = LatestOnlyLoad()

    /** Where the exported rows' saved bodies are copied from: ふたちゃ's auto-saved copies and the saved threads. */
    private val sourceRepositories: List<SavedThreadRepository>
        get() = listOfNotNull(autoSavedThreadRepository, savedRepository).distinct()

    fun openArchive() { if (!running) dialog = FutaberHistoryDialog.ArchiveChooser }
    fun openClear() { if (!running) dialog = FutaberHistoryDialog.ConfirmClear }
    fun dismiss() {
        importPreviewLoad.cancel()
        dialog = null
    }

    fun exportAll() = run("エクスポート") {
        exportAllFutachaHistoryArchive(stateStore, fileSystem, sourceRepositories, appVersion)
    }

    fun openExportSelection() {
        scope.launch {
            val history = try {
                stateStore.history.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                dialog = null
                onMessage(buildBoardManagementHistoryArchiveFailureMessage("エクスポート", failure))
                return@launch
            }
            dialog = FutaberHistoryDialog.ExportSelection(history)
        }
    }

    fun exportSelected(entries: List<ThreadHistoryEntry>) = run("エクスポート") {
        exportSelectedFutachaHistoryArchive(stateStore, fileSystem, sourceRepositories, appVersion, entries)
    }

    fun importAll() = run("インポート") { futaberHistoryImportMessage(importLatest(null)) }

    fun openImportSelection() {
        dialog = FutaberHistoryDialog.Import(HistoryImportSelectionState.Loading)
        importPreviewLoad.start(
            scope = scope,
            load = { loadHistoryImportSelectionState { loadLatestFutachaHistoryArchivePreview(stateStore, fileSystem) } },
            onResult = { dialog = FutaberHistoryDialog.Import(it) }
        )
    }

    fun importSelected(snapshotIds: Set<String>) = run("インポート") { futaberHistoryImportMessage(importLatest(snapshotIds)) }

    private suspend fun importLatest(snapshotIds: Set<String>?): String = importLatestFutachaHistoryArchive(
        stateStore = stateStore,
        fileSystem = fileSystem,
        destinationRepository = importedHistoryRepository,
        selectedSnapshotIds = snapshotIds
    )

    /** "全件削除": what ふたちゃ's "全て削除する" does (the other modes' rows, the own-post records, the saved copies). */
    fun clearAll() = run("削除") {
        var partial = false
        clearHistory(
            stateStore = stateStore,
            autoSavedThreadRepository = autoSavedThreadRepository,
            importedHistoryRepository = importedHistoryRepository,
            compatibilityStore = compatibilityStore,
            onSkippedThreadsCleared = {},
            onAutoSavedThreadDeleteFailure = {
                Logger.e(B45_TAG, "Failed to delete an auto-saved copy while clearing the history", it)
                partial = true
            }
        )
        if (partial) FUTABER_HISTORY_CLEARED_PARTIAL_NOTICE else FUTABER_HISTORY_CLEARED_NOTICE
    }

    private fun run(actionLabel: String, block: suspend () -> String) {
        dialog = null
        if (running) return
        running = true
        scope.launch {
            val message = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.e(B45_TAG, "History $actionLabel failed", failure)
                buildBoardManagementHistoryArchiveFailureMessage(actionLabel, failure)
            } finally {
                running = false
            }
            if (message.isNotBlank()) onMessage(message)
        }
    }
}

@Composable
internal fun rememberFutaberHistoryController(
    stateStore: AppStateStore,
    fileSystem: FileSystem?,
    autoSavedThreadRepository: SavedThreadRepository?,
    savedRepository: SavedThreadRepository?,
    compatibilityStore: CompatibilityStore?,
    appVersion: String,
    onMessage: (String) -> Unit
): FutaberHistoryController {
    val scope = rememberCoroutineScope()
    val controller = remember(stateStore, scope) { FutaberHistoryController(scope, stateStore) }
    val imported = remember(fileSystem) { com.valoser.futacha.shared.ui.buildImportedHistoryRepository(fileSystem) }
    SideEffect {
        controller.fileSystem = fileSystem
        controller.autoSavedThreadRepository = autoSavedThreadRepository
        controller.savedRepository = savedRepository
        controller.importedHistoryRepository = imported
        controller.compatibilityStore = compatibilityStore
        controller.appVersion = appVersion
        controller.onMessage = onMessage
    }
    return controller
}

/** The band's chips for the switches that are on (and nothing at all with both off). */
internal fun futaberHistoryTools(
    controller: FutaberHistoryController,
    clearEnabled: Boolean,
    archiveEnabled: Boolean
): FutaberHistoryTools = FutaberHistoryTools(
    onClearAll = if (clearEnabled) controller::openClear else null,
    onOpenArchive = if (archiveEnabled) controller::openArchive else null
)

/** The dialogs of [controller]; the shared ones are told to send no analytics. */
@Composable
internal fun FutaberHistoryDialogs(controller: FutaberHistoryController) {
    val colors = LocalFutaberColors.current
    when (val dialog = controller.dialog) {
        null -> Unit
        FutaberHistoryDialog.ConfirmClear -> FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = controller::dismiss,
            modifier = Modifier.testTag("futaber-history-clear-dialog"),
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text("履歴の全件削除") },
            text = { Text(FUTABER_HISTORY_CLEAR_CONFIRMATION) },
            confirmButton = {
                TextButton(onClick = controller::clearAll, modifier = Modifier.testTag("futaber-history-clear-confirm")) {
                    Text("すべて削除する", color = colors.link)
                }
            },
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("キャンセル", color = colors.link) } }
        ) }
        FutaberHistoryDialog.ArchiveChooser -> FutachaAppLockAwareWindow { AlertDialog(
            onDismissRequest = controller::dismiss,
            modifier = Modifier.testTag("futaber-history-archive-chooser"),
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text("履歴のエクスポート／インポート") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ふたちゃと同じ履歴アーカイブを、アプリ内の決まった場所に書き出し・読み込みます。")
                    Text(FUTABER_HISTORY_IMPORT_NOTICE)
                    TextButton(
                        onClick = { controller.dialog = FutaberHistoryDialog.ArchiveAction(HistoryArchiveAction.Export) },
                        modifier = Modifier.testTag("futaber-history-archive-export")
                    ) { Text("エクスポート…", color = colors.link) }
                    TextButton(
                        onClick = { controller.dialog = FutaberHistoryDialog.ArchiveAction(HistoryArchiveAction.Import) },
                        modifier = Modifier.testTag("futaber-history-archive-import")
                    ) { Text("インポート…", color = colors.link) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = controller::dismiss) { Text("閉じる", color = colors.link) } }
        ) }
        is FutaberHistoryDialog.ArchiveAction -> HistoryArchiveActionDialog(
            action = dialog.action,
            onDismiss = controller::dismiss,
            onAllClick = { if (dialog.action == HistoryArchiveAction.Export) controller.exportAll() else controller.importAll() },
            // Clearing is the band's own "全件削除".
            onExportThenClearClick = null,
            onSelectedClick = {
                if (dialog.action == HistoryArchiveAction.Export) controller.openExportSelection() else controller.openImportSelection()
            },
            trackAnalytics = false
        )
        is FutaberHistoryDialog.ExportSelection -> HistoryExportSelectionDialog(
            history = dialog.history,
            onDismiss = controller::dismiss,
            onExport = controller::exportSelected,
            trackAnalytics = false
        )
        is FutaberHistoryDialog.Import -> when (val state = dialog.state) {
            HistoryImportSelectionState.Hidden -> Unit
            HistoryImportSelectionState.Loading -> HistoryImportLoadingDialog(onDismiss = controller::dismiss, trackAnalytics = false)
            is HistoryImportSelectionState.Error -> HistoryImportErrorDialog(
                message = state.message, onDismiss = controller::dismiss, trackAnalytics = false
            )
            is HistoryImportSelectionState.Ready -> HistoryImportSelectionDialog(
                preview = state.preview,
                onDismiss = controller::dismiss,
                onImport = controller::importSelected,
                trackAnalytics = false
            )
        }
    }
}

// ---- H08: a check of every tab ----

/** Its own cursor file: a partial list must not move the cursor of the history refresh. */
internal const val FUTABER_TAB_CHECK_CURSOR_NAMESPACE = "futaber_tabs"
private const val FUTABER_TAB_CHECK_TIMEOUT_MILLIS = 120_000L
private const val FUTABER_TAB_CHECK_RUN_BUDGET_MILLIS = 100_000L
private const val FUTABER_TAB_CHECK_THREAD_TIMEOUT_MILLIS = 10_000L

internal const val FUTABER_TAB_CHECK_NONE_NOTICE = "確認できるタブはありません（落ちたスレとチュートリアル板のタブは確認しません）"
internal const val FUTABER_TAB_CHECK_BUSY_NOTICE = "履歴の更新かタブの確認が実行中です。終わってからもう一度お試しください"
internal const val FUTABER_TAB_CHECK_TIMEOUT_NOTICE = "タブの確認が時間内に終わりませんでした。確認できた分は反映されています"
internal const val FUTABER_TAB_CHECK_RUNNING_NOTICE = "タブを確認しています…"

/**
 * The history rows of the tabs to check: a tab whose thread has a row, on a registered board that is read from the
 * server (the tutorial board's threads never fall), and that is not already known to have fallen.
 */
internal fun futaberTabCheckTargets(
    tabs: List<FutaberTabKey>,
    history: List<ThreadHistoryEntry>,
    boards: List<BoardSummary>
): List<ThreadHistoryEntry> = tabs.mapNotNull { key ->
    val board = boards.firstOrNull { it.id == key.boardId } ?: return@mapNotNull null
    if (board.isMockBoard()) return@mapNotNull null
    findFutaberHistoryEntry(history, board, key.threadId)?.takeUnless { it.isAutoRefreshDisabled }
}.distinctBy { it.boardId to it.threadId }

internal data class FutaberTabCheckOutcome(val checked: Int, val newlyFallen: Int, val failed: Int)

/** How many of [targets] were found fallen in [after] (the history once the check wrote it). */
internal fun futaberTabCheckOutcome(
    targets: List<ThreadHistoryEntry>,
    after: List<ThreadHistoryEntry>,
    failed: Int
): FutaberTabCheckOutcome = FutaberTabCheckOutcome(
    checked = targets.size,
    newlyFallen = targets.count { target ->
        after.any { it.boardId == target.boardId && it.threadId == target.threadId && it.isAutoRefreshDisabled }
    },
    failed = failed.coerceIn(0, targets.size)
)

internal fun futaberTabCheckMessage(outcome: FutaberTabCheckOutcome): String = buildString {
    append("タブを${outcome.checked}件確認しました")
    append(if (outcome.newlyFallen > 0) "（落ちたスレ ${outcome.newlyFallen}件）" else "（落ちたスレはありません）")
    if (outcome.failed > 0) append("。${outcome.failed}件は確認できませんでした")
}

/**
 * "全タブを確認": the threads of the tabs are read once with the history refresh (no auto-save), which writes reply
 * counts and the fallen mark into the shared history, so the tab strip shows "落ち" by itself. Its own refresher keeps its
 * own cursor; a refresh already running anywhere in the app ([HistoryRefresher.RefreshAlreadyRunningException]) is told.
 */
@Stable
internal class FutaberTabChecker(
    private val scope: CoroutineScope,
    private val stateStore: AppStateStore,
    private val refresher: HistoryRefresher
) {
    var running by mutableStateOf(false)
        private set

    fun check(targets: List<ThreadHistoryEntry>, boards: List<BoardSummary>, onMessage: (String) -> Unit) {
        if (running) {
            onMessage(FUTABER_TAB_CHECK_BUSY_NOTICE)
            return
        }
        if (targets.isEmpty()) {
            onMessage(FUTABER_TAB_CHECK_NONE_NOTICE)
            return
        }
        running = true
        onMessage(FUTABER_TAB_CHECK_RUNNING_NOTICE)
        scope.launch {
            val startedAt = Clock.System.now().toEpochMilliseconds()
            val message = try {
                val completed = withTimeoutOrNull(FUTABER_TAB_CHECK_TIMEOUT_MILLIS) {
                    refresher.refresh(
                        boardsSnapshot = boards,
                        historySnapshot = targets,
                        autoSaveBudgetMillis = 0L,
                        maxAutoSavesPerRun = 0,
                        threadFetchTimeoutMillisOverride = FUTABER_TAB_CHECK_THREAD_TIMEOUT_MILLIS,
                        runBudgetMillis = FUTABER_TAB_CHECK_RUN_BUDGET_MILLIS
                    )
                }
                if (completed == null) {
                    FUTABER_TAB_CHECK_TIMEOUT_NOTICE
                } else {
                    val failed = refresher.lastRefreshError.value?.takeIf { it.timestamp >= startedAt }?.errorCount ?: 0
                    futaberTabCheckMessage(futaberTabCheckOutcome(targets, stateStore.history.first(), failed))
                }
            } catch (busy: HistoryRefresher.RefreshAlreadyRunningException) {
                FUTABER_TAB_CHECK_BUSY_NOTICE
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.e(B45_TAG, "The tab check failed", failure)
                "タブを確認できませんでした：" + futaberFriendlyLoadError(failure, "スレッド", "通信を確認してください")
            } finally {
                running = false
            }
            onMessage(message)
        }
    }
}

@Composable
internal fun rememberFutaberTabChecker(stateStore: AppStateStore, repository: BoardRepository): FutaberTabChecker {
    val scope = rememberCoroutineScope()
    return remember(stateStore, repository, scope) {
        FutaberTabChecker(
            scope, stateStore,
            HistoryRefresher(
                stateStore = stateStore,
                repository = repository,
                dispatcher = AppDispatchers.io,
                maxConcurrency = 2,
                cursorNamespace = FUTABER_TAB_CHECK_CURSOR_NAMESPACE
            )
        )
    }
}

/** The tab sheet's item (with the extension "まとめて閉じる時…" on), after the items that were there. */
internal fun futaberTabCheckAction(
    enabled: Boolean,
    checker: FutaberTabChecker,
    onCheck: () -> Unit
): FutaberPostAction? = if (!enabled) null else FutaberPostAction(
    "tab-check-all", if (checker.running) "全タブを確認（実行中）" else "全タブを確認", enabled = !checker.running
) { onCheck() }

// ---- W04: the patrol while this mode is on screen ----

/**
 * The shared patrol and tab checks every minute while the app is in front (what ふたちゃ runs from its shared features).
 * The intervals, the Wi-Fi conditions and the last-run times are the shared ones, kept in the store, so the background
 * work and this loop do not both run a check that is not due.
 */
@Composable
internal fun FutaberForegroundPatrol(
    enabled: Boolean,
    store: CompatibilityStore?,
    repository: BoardRepository,
    boards: List<BoardSummary>,
    preferences: Map<String, String>,
    notify: (String) -> Unit
) {
    if (!enabled || store == null) return
    var foreground by remember { mutableStateOf(true) }
    CompatForegroundLifecycleEffect { foreground = it }
    val dueAnything by remember(preferences) { derivedStateOf { sharedFeatureRefreshEnabled(preferences) } }
    val latestBoards by rememberUpdatedState(boards)
    val latestNotify by rememberUpdatedState(notify)
    val context = coil3.compose.LocalPlatformContext.current
    LaunchedEffect(store, repository, foreground, dueAnything) {
        if (!foreground || !dueAnything) return@LaunchedEffect
        while (isActive) {
            try {
                // The patrol reads the boards of the shared store; this mode's boards are added there first.
                store.importModernBoards(latestBoards)
                refreshSharedFeatures(store, repository, isCompatWifiConnected(context),
                    onNewMatches = { latestNotify(futaberPatrolMatchNotice(it.size)) })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Logger.e(B45_TAG, "The foreground patrol failed", failure)
            }
            delay(COMPAT_FOREGROUND_TICK_MILLIS)
        }
    }
}

internal fun futaberPatrolMatchNotice(count: Int): String = "巡回で${count}件のスレッドが見つかりました（管理画面の「通知」）"

// ---- W06: the external にじろぐ (Android) ----

/**
 * A row of the external にじろぐ as a row of this mode's "通知" list. Only an official thread address can be opened here;
 * anything else is left out. The keyword is the one にじろぐ found (blank when it has none).
 */
internal fun futaberWatchResultFromExternal(entry: CompatExternalWatcherEntry): CompatWatchResult? {
    val parsed = canonicalizeThreadUrl(entry.threadUrl) ?: return null
    val updated = entry.updatedAtEpochMillis.takeIf { it > 0 } ?: entry.insertedAtEpochMillis
    return CompatWatchResult(
        history = CompatHistoryEntry(
            canonicalUrl = parsed.canonicalUrl,
            originalUrl = entry.threadUrl,
            boardKey = compatBoardKey(parsed.canonicalBoardUrl),
            boardName = entry.boardName.orEmpty().ifBlank { parsed.boardPath },
            threadNo = parsed.threadNo,
            title = entry.title,
            thumbnailUrl = entry.thumbnailUrl,
            replyCount = entry.replyCount,
            contentUpdatedAtEpochMillis = updated
        ),
        keyword = entry.extractedKeyword?.trim().orEmpty(),
        insertedAtEpochMillis = entry.insertedAtEpochMillis,
        active = entry.active
    )
}

/** The external list as "通知" rows, with the key にじろぐ deletes each by. */
internal fun futaberExternalNotificationRows(entries: List<CompatExternalWatcherEntry>): List<Pair<CompatWatchResult, String>> =
    entries.mapNotNull { entry -> futaberWatchResultFromExternal(entry)?.let { it to entry.key } }

/**
 * The external にじろぐ while its extension is on (Android only). [openManager] goes to the patrol manager (which then
 * shows the "外部にじろぐの結果を表示" check); while that check is on, [results] holds にじろぐ's list for "通知" and
 * [remove] / [clear] act on にじろぐ, otherwise [results] is null and the list is the app's own patrol.
 */
@Stable
internal class FutaberExternalWatcher(private val watcher: CompatExternalWatcher, private val scope: CoroutineScope) {
    var results by mutableStateOf<List<CompatWatchResult>?>(null)
        internal set
    var status by mutableStateOf<String?>(null)
        internal set
    internal var keys: Map<String, String> = emptyMap()
    internal var reload: () -> Unit = {}

    fun openManager(): Result<Unit> = watcher.openManager()

    fun remove(result: CompatWatchResult) {
        val key = keys[result.history.canonicalUrl] ?: return
        scope.launch {
            watcher.delete(key).onFailure { status = it.message ?: "削除できませんでした" }
            reload()
        }
    }

    fun clear() {
        scope.launch {
            watcher.deleteAll().onFailure { status = it.message ?: "削除できませんでした" }
            reload()
        }
    }

    internal suspend fun load(external: Boolean) {
        if (!external) {
            results = null
            status = null
            keys = emptyMap()
            return
        }
        watcher.load()
            .onSuccess { snapshot ->
                val rows = futaberExternalNotificationRows(snapshot.entries)
                results = rows.map { it.first }
                keys = rows.associate { (result, key) -> result.history.canonicalUrl to key }
                // The provider's own words ("にじろぐ(仮) 未インストール" and the like), as the other modes show them.
                status = snapshot.message ?: "外部にじろぐの結果"
            }
            .onFailure { failure ->
                results = emptyList()
                keys = emptyMap()
                status = failure.message ?: "外部にじろぐの結果を読み込めませんでした"
            }
    }
}

@Composable
internal fun rememberFutaberExternalWatcher(
    enabled: Boolean,
    store: CompatibilityStore?,
    preferences: Map<String, String>,
    reloadKey: Any?
): FutaberExternalWatcher? {
    if (!enabled || store == null || !isAndroid()) return null
    val watcher = rememberCompatExternalWatcher(store)
    val scope = rememberCoroutineScope()
    val state = remember(watcher, scope) { FutaberExternalWatcher(watcher, scope) }
    val external = preferences[COMPAT_WATCH_EXTERNAL_KEY] == "ON"
    var tick by remember { mutableStateOf(0) }
    SideEffect { state.reload = { tick += 1 } }
    LaunchedEffect(state, external, reloadKey, tick) {
        try {
            state.load(external)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Logger.e(B45_TAG, "Failed to read the external watcher", failure)
        }
    }
    return state
}

// ---- W07: "閲覧スレ通知" ----

/**
 * Notes the open thread for the archive service after a page was read from the board (the thread screen's
 * "onLivePageConfirmed"; a saved copy, an MHT file and an archive's copy never call it). The shared switch decides.
 */
internal suspend fun futaberNoteViewedThread(
    store: CompatibilityStore?,
    preferences: Map<String, String>,
    board: BoardSummary,
    threadId: String,
    onEnqueued: ((Int) -> Unit)?,
    nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    desktop: Boolean = com.valoser.futacha.shared.util.isDesktop()
): Boolean {
    store ?: return false
    return enqueueViewedThreadArchiveReport(
        store = store,
        threadUrl = futaberCompatThreadUrl(board, threadId),
        enabledPreference = preferences[FutaberSettingKeys.ARCHIVE_REPORT],
        liveFetch = true,
        nowEpochMillis = nowEpochMillis,
        onEnqueued = onEnqueued,
        desktop = desktop
    )
}
