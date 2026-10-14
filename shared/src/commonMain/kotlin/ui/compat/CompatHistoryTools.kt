package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.compat.COMPAT_OWN_POST_PREFERENCE_PREFIX
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.compat.toModernThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.HistoryArchiveAction
import com.valoser.futacha.shared.ui.board.HistoryArchiveActionDialog
import com.valoser.futacha.shared.ui.board.HistoryExportSelectionDialog
import com.valoser.futacha.shared.ui.board.HistoryFilterSheet
import com.valoser.futacha.shared.ui.board.HistoryImportErrorDialog
import com.valoser.futacha.shared.ui.board.HistoryImportLoadingDialog
import com.valoser.futacha.shared.ui.board.HistoryImportSelectionDialog
import com.valoser.futacha.shared.ui.board.HistoryImportSelectionState
import com.valoser.futacha.shared.ui.board.HistorySortOption
import com.valoser.futacha.shared.ui.board.HistoryViewSettings
import com.valoser.futacha.shared.ui.board.LatestOnlyLoad
import com.valoser.futacha.shared.ui.board.applyHistoryViewSettings
import com.valoser.futacha.shared.ui.board.buildBoardManagementHistoryArchiveFailureMessage
import com.valoser.futacha.shared.ui.board.buildBoardManagementHistoryRefreshBusyMessage
import com.valoser.futacha.shared.ui.board.buildBoardManagementHistoryRefreshFailureMessage
import com.valoser.futacha.shared.ui.board.buildBoardManagementHistoryRefreshSuccessMessage
import com.valoser.futacha.shared.ui.board.buildHistoryBoardFilterOptions
import com.valoser.futacha.shared.ui.board.buildHistoryViewSummaryLabels
import com.valoser.futacha.shared.ui.board.countHistoryViewSettingsMatches
import com.valoser.futacha.shared.ui.board.loadHistoryImportSelectionState
import com.valoser.futacha.shared.ui.exportAllFutachaHistoryArchive
import com.valoser.futacha.shared.ui.exportSelectedFutachaHistoryArchive
import com.valoser.futacha.shared.ui.futaber.futaberHistoryMarks
import com.valoser.futacha.shared.ui.importLatestFutachaHistoryArchive
import com.valoser.futacha.shared.ui.loadLatestFutachaHistoryArchivePreview
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.FileSystem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/*
 * としあき(仮)'s history tools (feature-parity batches 4, 6 and 8), behind one switch on the control page
 * ("履歴の絞り込み・並べ替え・印", off by default) and only where the shared AppStateStore exists:
 *  - B4: filter / sort the drawer's history with ふたちゃ's sheet, and marks (落ちた / 書き込み済み / 保存済み) under a row.
 *  - B6: export / import of the history archive (ふたちゃ's own functions and dialogs). The bodies an import brings
 *        are kept for ふたちゃ (ふたばー風's offline copy reads only the auto-saved copies); としあき(仮) opens a
 *        thread from the network as before.
 *  - B8: one refresh of the shared history (ふたちゃ's HistoryRefresher).
 * With the switch off the drawer, its long-press menu and its order are exactly as before.
 */

internal const val COMPAT_HISTORY_TOOLS_PREFERENCE_KEY = "controlHistoryTools"
internal const val COMPAT_HISTORY_TOOLS_STORAGE_KEY = "compat.control.$COMPAT_HISTORY_TOOLS_PREFERENCE_KEY"

internal const val COMPAT_HISTORY_CHOICE_DELETE = "削除する"
internal const val COMPAT_HISTORY_CHOICE_CLEAR = "全て削除する"
internal const val COMPAT_HISTORY_CHOICE_FILTER = "絞り込み・並べ替え…"
internal const val COMPAT_HISTORY_CHOICE_ARCHIVE = "履歴のエクスポート／インポート…"
internal const val COMPAT_HISTORY_CHOICE_REFRESH = "履歴を一括更新"

internal const val COMPAT_HISTORY_IMPORT_NOTICE =
    "としあき(仮)では履歴の一覧だけが戻り、保存した本文はふたちゃで開けます。" +
        "としあき(仮)の履歴は新しい順に200件まで表示されます。"

private const val COMPAT_HISTORY_REFRESH_TIMEOUT_MILLIS = 60_000L
private const val COMPAT_HISTORY_REFRESH_MAX_THREADS = 5
private const val COMPAT_HISTORY_REFRESH_THREAD_TIMEOUT_MILLIS = 6_000L
private const val COMPAT_HISTORY_FILTER_COUNT_DEBOUNCE_MS = 150L

internal fun compatHistoryToolsEnabled(preferences: Map<String, String>): Boolean =
    preferences[COMPAT_HISTORY_TOOLS_STORAGE_KEY] == "ON"

/** The history long-press choices: the two existing ones, then the tools when they are on. */
internal fun compatHistoryContextChoices(toolsAvailable: Boolean, refreshAvailable: Boolean): List<String> =
    listOf(COMPAT_HISTORY_CHOICE_DELETE, COMPAT_HISTORY_CHOICE_CLEAR) + if (toolsAvailable) {
        listOfNotNull(
            COMPAT_HISTORY_CHOICE_FILTER,
            COMPAT_HISTORY_CHOICE_ARCHIVE,
            COMPAT_HISTORY_CHOICE_REFRESH.takeIf { refreshAvailable }
        )
    } else {
        emptyList()
    }

/** This mode keeps no confirmed-alive or own-post time, so sorting by them would only put everything "missing". */
internal val COMPAT_HISTORY_SORT_OPTIONS: List<HistorySortOption> = HistorySortOption.entries.filterNot {
    it == HistorySortOption.LastConfirmedAlive || it == HistorySortOption.LastSelfPost
}

/** A sort this mode does not offer (a restored state from elsewhere) falls back to the visit time. */
internal fun HistoryViewSettings.compatSupported(): HistoryViewSettings =
    if (sortOption in COMPAT_HISTORY_SORT_OPTIONS) this else copy(sortOption = HistorySortOption.LastVisited)

/** Tab keys with an own-post marker ("compat.ownpost.<tabKey>.<postNo>"); tab keys contain no '.'. */
internal fun compatOwnPostTabKeys(preferences: Map<String, String>): Set<String> =
    preferences.keys.asSequence()
        .filter { it.startsWith(COMPAT_OWN_POST_PREFERENCE_PREFIX) }
        .map { it.substring(COMPAT_OWN_POST_PREFERENCE_PREFIX.length).substringBefore('.') }
        .filter(String::isNotEmpty)
        .toSet()

internal data class CompatHistoryToolRow(val entry: CompatHistoryEntry, val view: ThreadHistoryEntry)

/**
 * The history rows of this mode as the shared filter sees them. Title, board and visit time are this mode's own
 * (what the drawer shows); the state (fell off, saved copy, confirmed alive) comes from the AppState row of the same
 * thread when there is one, matched on the canonical thread URL. "Written in" is this mode's own-post marker, or the
 * AppState flag.
 */
internal fun buildCompatHistoryToolRows(
    histories: List<CompatHistoryEntry>,
    appStateHistory: List<ThreadHistoryEntry>,
    ownPostTabKeys: Set<String>
): List<CompatHistoryToolRow> {
    val sharedByUrl = HashMap<String, ThreadHistoryEntry>(appStateHistory.size)
    appStateHistory.forEach { entry ->
        entry.toCompatHistoryEntry()?.canonicalUrl?.let { url -> if (url !in sharedByUrl) sharedByUrl[url] = entry }
    }
    return histories.map { entry ->
        val base = entry.toModernThreadHistoryEntry() ?: ThreadHistoryEntry(
            threadId = entry.threadNo,
            boardId = entry.boardKey,
            title = entry.title,
            titleImageUrl = entry.thumbnailUrl.orEmpty(),
            boardName = entry.boardName,
            boardUrl = entry.canonicalUrl,
            lastVisitedEpochMillis = entry.lastVisitedEpochMillis,
            replyCount = entry.replyCount
        )
        val shared = sharedByUrl[entry.canonicalUrl]
        val ownPost = runCatching { compatTabKey(entry.canonicalUrl) }.getOrNull()?.let { it in ownPostTabKeys } == true
        CompatHistoryToolRow(
            entry = entry,
            view = base.copy(
                isAutoRefreshDisabled = shared?.isAutoRefreshDisabled == true,
                lastConfirmedAliveEpochMillis = shared?.lastConfirmedAliveEpochMillis,
                hasAutoSave = shared?.hasAutoSave == true,
                hasSelfPost = ownPost || shared?.hasSelfPost == true,
                lastSelfPostEpochMillis = shared?.lastSelfPostEpochMillis
            )
        )
    }
}

/** The rows the settings keep, in their order. The default settings keep the drawer's own order untouched. */
internal fun applyCompatHistoryTools(
    rows: List<CompatHistoryToolRow>,
    settings: HistoryViewSettings
): List<CompatHistoryToolRow> {
    val supported = settings.compatSupported()
    if (supported.isDefault) return rows
    val byView = rows.associateBy { it.view }
    return applyHistoryViewSettings(rows.map { it.view }, supported).mapNotNull { byView[it] }
}

/** What the drawer shows with the tools on. */
internal class CompatHistoryToolsView(
    val entries: List<CompatHistoryEntry>,
    val marks: Map<String, String>,
    val totalCount: Int,
    val settings: HistoryViewSettings,
    val labels: List<String>
)

internal sealed interface CompatHistoryToolsDialog {
    data object Filter : CompatHistoryToolsDialog
    data object ArchiveChooser : CompatHistoryToolsDialog
    data class ArchiveAction(val action: HistoryArchiveAction) : CompatHistoryToolsDialog
    data class ExportSelection(val history: List<ThreadHistoryEntry>) : CompatHistoryToolsDialog
    data class Import(val state: HistoryImportSelectionState) : CompatHistoryToolsDialog
}

@Stable
internal class CompatHistoryTools(
    private val appliedSettings: MutableState<HistoryViewSettings>,
    private val scope: CoroutineScope,
    private val stateStore: AppStateStore
) {
    var settings: HistoryViewSettings
        get() = appliedSettings.value
        set(value) { appliedSettings.value = value.compatSupported() }
    var appStateHistory by mutableStateOf<List<ThreadHistoryEntry>>(emptyList())
        internal set
    var ownPostTabKeys by mutableStateOf<Set<String>>(emptySet())
        internal set
    var dialog by mutableStateOf<CompatHistoryToolsDialog?>(null)
    var refreshing by mutableStateOf(false)
        private set
    internal var fileSystem: FileSystem? = null
    internal var sourceRepositories: List<SavedThreadRepository> = emptyList()
    internal var importedHistoryRepository: SavedThreadRepository? = null
    internal var historyRefresher: HistoryRefresher? = null
    internal var appVersion: String? = null
    internal var onMessage: (String) -> Unit = {}
    internal val importPreviewLoad = LatestOnlyLoad()

    val refreshAvailable: Boolean get() = historyRefresher != null

    fun openFilter() { dialog = CompatHistoryToolsDialog.Filter }
    fun openArchive() { dialog = CompatHistoryToolsDialog.ArchiveChooser }

    fun exportAll() = runArchive("エクスポート") {
        exportAllFutachaHistoryArchive(stateStore, fileSystem, sourceRepositories, appVersion)
    }

    fun openExportSelection() {
        scope.launch {
            val history = try {
                stateStore.history.first()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                onMessage(buildBoardManagementHistoryArchiveFailureMessage("エクスポート", failure))
                return@launch
            }
            dialog = CompatHistoryToolsDialog.ExportSelection(history)
        }
    }

    fun exportSelected(entries: List<ThreadHistoryEntry>) = runArchive("エクスポート") {
        exportSelectedFutachaHistoryArchive(stateStore, fileSystem, sourceRepositories, appVersion, entries)
    }

    fun importAll() = runArchive("インポート") { compatHistoryImportMessage(importLatest(null)) }

    fun openImportSelection() {
        dialog = CompatHistoryToolsDialog.Import(HistoryImportSelectionState.Loading)
        importPreviewLoad.start(
            scope = scope,
            load = { loadHistoryImportSelectionState { loadLatestFutachaHistoryArchivePreview(stateStore, fileSystem) } },
            onResult = { dialog = CompatHistoryToolsDialog.Import(it) }
        )
    }

    fun importSelected(snapshotIds: Set<String>) = runArchive("インポート") {
        compatHistoryImportMessage(importLatest(snapshotIds))
    }

    private suspend fun importLatest(snapshotIds: Set<String>?): String = importLatestFutachaHistoryArchive(
        stateStore = stateStore,
        fileSystem = fileSystem,
        destinationRepository = importedHistoryRepository,
        selectedSnapshotIds = snapshotIds
    )

    private fun runArchive(actionLabel: String, block: suspend () -> String) {
        dialog = null
        scope.launch {
            val message = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                buildBoardManagementHistoryArchiveFailureMessage(actionLabel, failure)
            }
            if (message.isNotBlank()) onMessage(message)
        }
    }

    /**
     * One refresh of the shared (AppState) history, bounded like ふたちゃ's drawer refresh: a few threads, no
     * auto-saved copies, a timeout. The refresher writes the AppState history; the existing bridge of the host
     * (MainActivity / the iOS host) carries part of that (reply counts, titles) back into this mode's history.
     */
    fun refresh() {
        val refresher = historyRefresher ?: return
        if (refreshing) {
            onMessage(buildBoardManagementHistoryRefreshBusyMessage())
            return
        }
        refreshing = true
        scope.launch {
            val startedAt = Clock.System.now().toEpochMilliseconds()
            val message = try {
                val completed = withTimeoutOrNull(COMPAT_HISTORY_REFRESH_TIMEOUT_MILLIS) {
                    refresher.refresh(
                        boardsSnapshot = stateStore.boards.first(),
                        historySnapshot = stateStore.history.first(),
                        autoSaveBudgetMillis = 0L,
                        maxThreadsPerRun = COMPAT_HISTORY_REFRESH_MAX_THREADS,
                        maxAutoSavesPerRun = 0,
                        threadFetchTimeoutMillisOverride = COMPAT_HISTORY_REFRESH_THREAD_TIMEOUT_MILLIS
                    )
                }
                if (completed == null) {
                    "履歴更新がタイムアウトしました。時間をおいて再度お試しください"
                } else {
                    compatHistoryRefreshResultMessage(refresher.lastRefreshError.value, startedAt)
                }
            } catch (busy: HistoryRefresher.RefreshAlreadyRunningException) {
                buildBoardManagementHistoryRefreshBusyMessage()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                buildBoardManagementHistoryRefreshFailureMessage(failure)
            } finally {
                refreshing = false
            }
            onMessage(message)
        }
    }
}

internal fun compatHistoryImportMessage(message: String): String =
    if (message.startsWith("履歴アーカイブを読み込みました")) "$message\n\n$COMPAT_HISTORY_IMPORT_NOTICE" else message

internal fun compatHistoryRefreshResultMessage(error: HistoryRefresher.RefreshError?, startedAtEpochMillis: Long): String {
    val failed = error?.takeIf { it.timestamp >= startedAtEpochMillis && it.errorCount > 0 }
        ?: return buildBoardManagementHistoryRefreshSuccessMessage()
    return "${buildBoardManagementHistoryRefreshSuccessMessage()}（${failed.totalThreads}件中${failed.errorCount}件は取得できませんでした）"
}

/**
 * The tools, or null when they are off or there is no shared store. The filter is kept for the session
 * (rememberSaveable), not stored.
 */
@Composable
internal fun rememberCompatHistoryTools(
    stateStore: AppStateStore?,
    preferences: Map<String, String>,
    fileSystem: FileSystem?,
    sourceRepositories: List<SavedThreadRepository>,
    importedHistoryRepository: SavedThreadRepository?,
    historyRefresher: HistoryRefresher?,
    appVersion: String,
    onMessage: (String) -> Unit
): CompatHistoryTools? {
    val applied = rememberSaveable(stateSaver = HistoryViewSettings.Saver) { mutableStateOf(HistoryViewSettings.Default) }
    val scope = rememberCoroutineScope()
    val enabled = stateStore != null && compatHistoryToolsEnabled(preferences)
    val tools = remember(stateStore, applied, scope) {
        stateStore?.let { CompatHistoryTools(applied, scope, it) }
    }
    val ownPostTabKeys = remember(preferences) { compatOwnPostTabKeys(preferences) }
    SideEffect {
        tools?.apply {
            this.ownPostTabKeys = ownPostTabKeys
            this.fileSystem = fileSystem
            this.sourceRepositories = sourceRepositories
            this.importedHistoryRepository = importedHistoryRepository
            this.historyRefresher = historyRefresher
            this.appVersion = appVersion
            this.onMessage = onMessage
        }
    }
    LaunchedEffect(tools, enabled) {
        val store = stateStore
        if (tools == null || store == null || !enabled) return@LaunchedEffect
        store.history.collect { tools.appStateHistory = it }
    }
    LaunchedEffect(enabled) {
        // Turned off: nothing of it stays open, and the drawer is back to its own order.
        if (!enabled) tools?.dialog = null
    }
    return tools?.takeIf { enabled }
}

/** The drawer's view of [histories] (already de-duplicated) with the tools. */
@Composable
internal fun rememberCompatHistoryToolsView(
    tools: CompatHistoryTools,
    histories: List<CompatHistoryEntry>
): CompatHistoryToolsView {
    val settings = tools.settings
    val appStateHistory = tools.appStateHistory
    val ownPostTabKeys = tools.ownPostTabKeys
    return remember(histories, appStateHistory, ownPostTabKeys, settings) {
        val rows = buildCompatHistoryToolRows(histories, appStateHistory, ownPostTabKeys)
        val shown = applyCompatHistoryTools(rows, settings)
        CompatHistoryToolsView(
            entries = shown.map { it.entry },
            marks = rows.associate { it.entry.canonicalUrl to futaberHistoryMarks(it.view) }.filterValues(String::isNotEmpty),
            totalCount = rows.size,
            settings = settings.compatSupported(),
            labels = buildHistoryViewSummaryLabels(settings.compatSupported(), buildHistoryBoardFilterOptions(rows.map { it.view }))
        )
    }
}

/** The tools' dialogs, drawn once at the app level (the drawer can be composed twice during the edge preview). */
@Composable
internal fun CompatHistoryToolsHost(
    tools: CompatHistoryTools,
    histories: List<CompatHistoryEntry>
) {
    when (val dialog = tools.dialog) {
        null -> Unit
        CompatHistoryToolsDialog.Filter -> {
            val rows = remember(histories, tools.appStateHistory, tools.ownPostTabKeys) {
                buildCompatHistoryToolRows(compatDistinctHistory(histories), tools.appStateHistory, tools.ownPostTabKeys)
                    .map { it.view }
            }
            var draft by remember { mutableStateOf(tools.settings) }
            val filteredCount by produceState<Int?>(null, rows, draft) {
                delay(COMPAT_HISTORY_FILTER_COUNT_DEBOUNCE_MS)
                value = withContext(AppDispatchers.parsing) { countHistoryViewSettingsMatches(rows, draft.compatSupported()) }
            }
            HistoryFilterSheet(
                totalCount = rows.size,
                filteredCount = filteredCount,
                settings = draft,
                boardOptions = remember(rows) { buildHistoryBoardFilterOptions(rows) },
                onSettingsChanged = { draft = it },
                onReset = { draft = HistoryViewSettings.Default },
                onApply = {
                    tools.settings = draft
                    tools.dialog = null
                },
                onDismiss = { tools.dialog = null },
                sortOptions = COMPAT_HISTORY_SORT_OPTIONS
            )
        }
        CompatHistoryToolsDialog.ArchiveChooser -> CompatHistoryArchiveChooserDialog(
            onExport = { tools.dialog = CompatHistoryToolsDialog.ArchiveAction(HistoryArchiveAction.Export) },
            onImport = { tools.dialog = CompatHistoryToolsDialog.ArchiveAction(HistoryArchiveAction.Import) },
            onDismiss = { tools.dialog = null }
        )
        is CompatHistoryToolsDialog.ArchiveAction -> HistoryArchiveActionDialog(
            action = dialog.action,
            onDismiss = { tools.dialog = null },
            onAllClick = { if (dialog.action == HistoryArchiveAction.Export) tools.exportAll() else tools.importAll() },
            // Clearing the whole history from here is not offered: the drawer's own "全て削除する" does that.
            onExportThenClearClick = null,
            onSelectedClick = {
                if (dialog.action == HistoryArchiveAction.Export) tools.openExportSelection() else tools.openImportSelection()
            }
        )
        is CompatHistoryToolsDialog.ExportSelection -> HistoryExportSelectionDialog(
            history = dialog.history,
            onDismiss = { tools.dialog = null },
            onExport = { selected -> tools.exportSelected(selected) }
        )
        is CompatHistoryToolsDialog.Import -> when (val state = dialog.state) {
            HistoryImportSelectionState.Hidden -> Unit
            HistoryImportSelectionState.Loading -> HistoryImportLoadingDialog(onDismiss = {
                // Closing the loading dialog abandons the load, so a late preview never reopens it.
                tools.importPreviewLoad.cancel()
                tools.dialog = null
            })
            is HistoryImportSelectionState.Error -> HistoryImportErrorDialog(
                message = state.message,
                onDismiss = { tools.dialog = null }
            )
            is HistoryImportSelectionState.Ready -> HistoryImportSelectionDialog(
                preview = state.preview,
                onDismiss = { tools.dialog = null },
                onImport = { ids -> tools.importSelected(ids) }
            )
        }
    }
}

private fun compatDistinctHistory(histories: List<CompatHistoryEntry>): List<CompatHistoryEntry> =
    histories.distinctBy { it.canonicalUrl }

@Composable
private fun CompatHistoryArchiveChooserDialog(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    FutachaAppLockAwareWindow { AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("compat-history-archive-chooser"),
        title = { Text("履歴のエクスポート／インポート") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ふたちゃと同じ履歴アーカイブを書き出し・読み込みます。")
                Text(COMPAT_HISTORY_IMPORT_NOTICE)
                TextButton(onClick = onExport, modifier = Modifier.testTag("compat-history-archive-export")) {
                    Text("エクスポート…")
                }
                TextButton(onClick = onImport, modifier = Modifier.testTag("compat-history-archive-import")) {
                    Text("インポート…")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    ) }
}

/** The banner of active settings, or the "nothing matches" line, above the drawer's history rows. */
internal fun androidx.compose.foundation.lazy.LazyListScope.compatHistoryToolsHeaderItems(
    view: CompatHistoryToolsView,
    onReset: () -> Unit
) {
    if (view.settings.isDefault) return
    item(key = "compat-history-tools-banner") {
        CompatHistoryToolsColors {
            androidx.compose.foundation.layout.Box(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp).testTag("compat-history-tools-banner")
            ) {
                com.valoser.futacha.shared.ui.board.HistoryActiveSettingsBanner(
                    visibleCount = view.entries.size,
                    totalCount = view.totalCount,
                    labels = view.labels,
                    onReset = onReset
                )
            }
        }
    }
    if (view.entries.isEmpty() && view.totalCount > 0) {
        item(key = "compat-history-tools-empty") {
            CompatHistoryToolsColors {
                com.valoser.futacha.shared.ui.board.HistoryEmptyFilterResult(onReset = onReset)
            }
        }
    }
}

/** The shared banner reads secondaryContainer, which this mode's scheme leaves at Material's default (purple). */
@Composable
private fun CompatHistoryToolsColors(content: @Composable () -> Unit) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    androidx.compose.material3.MaterialTheme(
        colorScheme = scheme.copy(
            secondaryContainer = scheme.surfaceVariant,
            onSecondaryContainer = scheme.onSurfaceVariant
        ),
        typography = androidx.compose.material3.MaterialTheme.typography,
        content = content
    )
}

/** The marks under a history row: small text in this mode's secondary text colour. */
@Composable
internal fun CompatHistoryMarksLine(canonicalUrl: String, marks: String) {
    Text(
        marks,
        modifier = Modifier
            .padding(start = (COMPAT_REFERENCE_DRAWER_THREAD_THUMBNAIL_DP + 10).dp, end = 5.dp, bottom = 2.dp)
            .testTag("compat-drawer-history-marks-$canonicalUrl"),
        maxLines = 1,
        fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
        color = LocalCompatibilityPalette.current.uiSecondaryText
    )
}
