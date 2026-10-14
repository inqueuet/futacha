package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked
import com.valoser.futacha.shared.ui.board.AUTO_SAVE_INTERVAL_MS
import com.valoser.futacha.shared.ui.board.ThreadAutoSaveLaunchEffect
import com.valoser.futacha.shared.ui.board.ThreadScreenAutoSaveDependencies
import com.valoser.futacha.shared.ui.board.ThreadScreenAutoSaveStateBindings
import com.valoser.futacha.shared.ui.board.buildThreadScreenAutoSaveBindings
import com.valoser.futacha.shared.ui.board.indexSavedThreadOrLog
import com.valoser.futacha.shared.ui.board.rememberThreadAutoSaveEffectState
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/*
 * ふたばー風モードの機能そろえ B9（拡張機能のスイッチの後ろ、既定OFF）。
 *  - C06 カタログの細かな調整：グリッド（横3〜横8）の列数、題名の行数・文字数。ふたばー風専用のキー（compat.futaber.catalog.*）で、
 *    他モードの compat.catalog.* は読まない。拡張OFF・未保存なら今の表示と同じ。
 *  - 閲覧中のスレッドの自動保存コピー：ふたちゃのスレ画面と同じ仕組み（buildThreadScreenAutoSaveBindings・ThreadAutoSaveLaunchEffect、
 *    AUTO_SAVE_DIRECTORY、最初は30秒後・以後60秒ごと、メディア1,200件・180秒、原寸は従量制でない回線だけ）。
 */

/** C06: the keys of the grid's own adjustments (this mode's own; the other modes' compat.catalog.* are not read). */
internal object FutaberCatalogFineKeys {
    fun gridColumns(style: FutaberCatalogDisplayStyle): String = "compat.futaber.catalog.gridColumns.${style.persistedValue}"
    const val GRID_TITLE_LINES = "compat.futaber.catalog.gridTitleLines"
    const val GRID_TITLE_CHARS = "compat.futaber.catalog.gridTitleChars"
}

internal const val FUTABER_GRID_COLUMNS_MIN = 2
internal const val FUTABER_GRID_COLUMNS_MAX = 8
internal const val FUTABER_GRID_TITLE_LINES_MIN = 1
internal const val FUTABER_GRID_TITLE_LINES_MAX = 3
internal const val FUTABER_GRID_TITLE_CHARS_MAX = 20

/** The grid styles whose column count can be changed (the row styles have no columns). */
internal val futaberGridStyles: List<FutaberCatalogDisplayStyle> = FutaberCatalogDisplayStyle.entries.filter { it.isGrid }

/**
 * C06: what the grid draws differently. Only what was stored (and is in range) is kept; nothing stored draws the grid
 * as before (the style's own columns, one title line, as many letters as fit).
 */
internal data class FutaberCatalogFineTune(
    val gridColumns: Map<FutaberCatalogDisplayStyle, Int> = emptyMap(),
    val gridTitleLines: Int = FUTABER_GRID_TITLE_LINES_MIN,
    /** null: as many letters as fit (as before); 0: no title (the picture and the count only). */
    val gridTitleChars: Int? = null
) {
    companion object {
        /** Nothing while the extension is off; a missing, damaged or out-of-range value reads as the default. */
        fun from(preferences: Map<String, String>): FutaberCatalogFineTune {
            if (preferences[FutaberSettingKeys.EXT_CATALOG_FINE_TUNE] != "ON") return FutaberCatalogFineTune()
            return FutaberCatalogFineTune(
                gridColumns = futaberGridStyles.mapNotNull { style ->
                    preferences[FutaberCatalogFineKeys.gridColumns(style)]?.toIntOrNull()
                        ?.takeIf { it in FUTABER_GRID_COLUMNS_MIN..FUTABER_GRID_COLUMNS_MAX }?.let { style to it }
                }.toMap(),
                gridTitleLines = preferences[FutaberCatalogFineKeys.GRID_TITLE_LINES]?.toIntOrNull()
                    ?.takeIf { it in FUTABER_GRID_TITLE_LINES_MIN..FUTABER_GRID_TITLE_LINES_MAX } ?: FUTABER_GRID_TITLE_LINES_MIN,
                gridTitleChars = preferences[FutaberCatalogFineKeys.GRID_TITLE_CHARS]?.toIntOrNull()
                    ?.takeIf { it in 0..FUTABER_GRID_TITLE_CHARS_MAX }
            )
        }
    }
}

/** The columns the catalog draws [style] with: the adjusted count of a grid style, null for a row style. */
internal fun futaberCatalogColumns(style: FutaberCatalogDisplayStyle, fine: FutaberCatalogFineTune): Int? =
    style.columns?.let { fine.gridColumns[style] ?: it }

/** The title a grid cell shows: null when the letters are set to 0 (only the count is shown). */
internal fun futaberGridTitle(title: String, fine: FutaberCatalogFineTune): String? {
    val limit = fine.gridTitleChars ?: return title
    if (limit <= 0) return null
    if (title.length <= limit) return title
    // A surrogate pair (an emoji) is not cut in half.
    val end = if (title[limit - 1].isHighSurrogate()) limit - 1 else limit
    return title.substring(0, end)
}

/**
 * The letters after a "−" ([delta] −1) or "+" (+1) press. "標準" (null) sits above the largest number: "−" from it goes
 * to [FUTABER_GRID_TITLE_CHARS_MAX], "+" from the largest goes back to it; 0 is the smallest.
 */
internal fun futaberGridTitleCharsAfter(current: Int?, delta: Int): Int? = when {
    current == null -> if (delta < 0) FUTABER_GRID_TITLE_CHARS_MAX else null
    current + delta > FUTABER_GRID_TITLE_CHARS_MAX -> null
    else -> (current + delta).coerceAtLeast(0)
}

/** How the settings row says it. */
internal fun futaberGridTitleCharsLabel(value: Int?): String = when (value) {
    null -> "標準"
    0 -> "表示しない"
    else -> "${value}字"
}

// ---------------------------------------------------------------------------------------------------------------
// 閲覧中のスレッドの自動保存コピー
// ---------------------------------------------------------------------------------------------------------------

private const val FUTABER_AUTO_SAVE_TAG = "FutaberAutoSave"

/**
 * What the thread screen needs to auto-save its live page; made by [futaberAutoSaveLink] (null while the extension is
 * off or the host cannot save).
 */
internal class FutaberAutoSaveLink(
    val repository: SavedThreadRepository,
    val httpClient: HttpClient,
    val fileSystem: FileSystem,
    /** As in ふたちゃ, only a thread with a history row is saved (deleting the row deletes its copy). */
    val inHistory: Boolean,
    /** Marks the history row as having an auto-saved copy (the history's "保存済み" mark). */
    val markSaved: suspend () -> Unit
)

internal fun futaberAutoSaveLink(
    enabled: Boolean,
    repository: SavedThreadRepository?,
    mediaServices: FutaberMediaServices?,
    stateStore: AppStateStore,
    history: List<ThreadHistoryEntry>,
    board: BoardSummary,
    threadId: String
): FutaberAutoSaveLink? {
    if (!enabled) return null
    val savedRepository = repository ?: return null
    val client = mediaServices?.httpClient ?: return null
    val fileSystem = mediaServices.fileSystem ?: return null
    return FutaberAutoSaveLink(
        repository = savedRepository,
        httpClient = client,
        fileSystem = fileSystem,
        inHistory = findFutaberHistoryEntry(history, board, threadId) != null,
        markSaved = { stateStore.updateHistory { futaberHistoryMarkedAutoSaved(it, board, threadId) } }
    )
}

/** The history with the thread's row marked as auto-saved; the same list when there is no row or it is marked already. */
internal fun futaberHistoryMarkedAutoSaved(
    history: List<ThreadHistoryEntry>,
    board: BoardSummary,
    threadId: String
): List<ThreadHistoryEntry> {
    val entry = findFutaberHistoryEntry(history, board, threadId) ?: return history
    if (entry.hasAutoSave) return history
    return history.map { if (it === entry) it.copy(hasAutoSave = true) else it }
}

/**
 * The page to auto-save: the page the last live load brought, while it is the one on screen. Nothing while the
 * extension is off, while a copy is shown (a saved copy, an MHT, an archive, the auto-saved copy) or while "更新前に戻す"
 * shows an older page (saving it would replace the newer copy, as ふたちゃ also skips it until the next load).
 */
internal fun futaberAutoSaveTarget(
    enabled: Boolean,
    livePage: ThreadPage?,
    visiblePage: ThreadPage?,
    showingCopy: Boolean
): ThreadPage? = if (!enabled || showingCopy || livePage == null || visiblePage !== livePage) null else livePage

/** Remembers the page the last live load brought (set by the thread screen right after a load succeeded). */
internal class FutaberThreadAutoSaver {
    var livePage by mutableStateOf<ThreadPage?>(null)
        private set

    fun onLivePage(page: ThreadPage) {
        livePage = page
    }
}

@Composable
internal fun rememberFutaberThreadAutoSaver(boardId: String, threadId: String): FutaberThreadAutoSaver =
    remember(boardId, threadId) { FutaberThreadAutoSaver() }

/**
 * Runs ふたちゃ's screen auto-save for the live page of this thread: the first save some 30 seconds after the page is
 * ready, then again when the posts changed, at most once a minute. Leaving the thread or locking the app stops a save
 * that is running.
 */
@Composable
internal fun FutaberThreadAutoSaveEffect(
    link: FutaberAutoSaveLink?,
    saver: FutaberThreadAutoSaver,
    board: BoardSummary,
    ref: FutaberThreadRef,
    visiblePage: ThreadPage?,
    showingCopy: Boolean
) {
    key(board.id, ref.threadId) {
        FutaberThreadAutoSaveRunner(link, saver, board, ref, visiblePage, showingCopy)
    }
}

@Composable
private fun FutaberThreadAutoSaveRunner(
    link: FutaberAutoSaveLink?,
    saver: FutaberThreadAutoSaver,
    board: BoardSummary,
    ref: FutaberThreadRef,
    visiblePage: ThreadPage?,
    showingCopy: Boolean
) {
    val scope = rememberCoroutineScope()
    val job = remember { mutableStateOf<Job?>(null) }
    val lastSavedAt = remember { mutableStateOf(0L) }
    val lastSavedPosts = remember { mutableStateOf<String?>(null) }
    val unlocked = LocalFutachaAppUnlocked.current
    val active = link != null && unlocked
    val inHistory = link?.inHistory == true
    val target = futaberAutoSaveTarget(active, saver.livePage, visiblePage, showingCopy)
    val blocked by rememberUpdatedState(target == null)
    val latestLink by rememberUpdatedState(link)
    DisposableEffect(Unit) { onDispose { job.value?.cancel() } }
    LaunchedEffect(active, inHistory) { if (!active || !inHistory) job.value?.cancel() }
    if (link == null) return
    val bindings = remember(link.repository, link.httpClient, link.fileSystem, board, ref.threadId, ref.title) {
        buildThreadScreenAutoSaveBindings(
            coroutineScope = scope,
            minIntervalMillis = AUTO_SAVE_INTERVAL_MS,
            stateBindings = ThreadScreenAutoSaveStateBindings(
                currentAutoSaveJob = { job.value },
                setAutoSaveJob = { job.value = it },
                currentLastAutoSaveTimestampMillis = { lastSavedAt.value },
                setLastAutoSaveTimestampMillis = { lastSavedAt.value = it },
                currentLastAutoSavePosts = { lastSavedPosts.value },
                setLastAutoSavePosts = { lastSavedPosts.value = it },
                // A copy came on screen (or the app locked) after the wait began: nothing is started.
                currentIsShowingOfflineCopy = { blocked }
            ),
            dependencies = ThreadScreenAutoSaveDependencies(
                autoSaveRepository = link.repository,
                httpClient = link.httpClient,
                fileSystem = link.fileSystem,
                threadId = ref.threadId,
                threadTitle = ref.title,
                board = board,
                effectiveBoardUrl = board.url,
                indexSavedThread = { repository, savedThread, failureMessage ->
                    val result = indexSavedThreadOrLog(repository, savedThread, FUTABER_AUTO_SAVE_TAG, failureMessage)
                    if (savedThread != null && result?.isSuccess == true) {
                        try {
                            latestLink?.markSaved?.invoke()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Throwable) {
                            Logger.e(FUTABER_AUTO_SAVE_TAG, "Failed to mark the history row as auto-saved", error)
                        }
                    }
                    result
                },
                onFailureLog = { message, failure -> Logger.e(FUTABER_AUTO_SAVE_TAG, message, failure) }
            )
        )
    }
    val effectState = rememberThreadAutoSaveEffectState(
        currentPageForAutoSave = target,
        threadId = ref.threadId,
        isShowingOfflineCopy = false,
        autoSaveRepository = link.repository.takeIf { inHistory },
        httpClient = link.httpClient,
        fileSystem = link.fileSystem,
        autoSaveJob = job.value,
        lastAutoSaveTimestampMillis = lastSavedAt.value
    )
    ThreadAutoSaveLaunchEffect(
        threadId = ref.threadId,
        currentPageForAutoSave = target,
        isShowingOfflineCopy = false,
        httpClient = link.httpClient,
        fileSystem = link.fileSystem,
        autoSaveEffectState = effectState,
        lastAutoSaveTimestampMillis = lastSavedAt.value,
        onStartAutoSave = bindings.startAutoSave
    )
}
