package com.valoser.futacha.shared.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.valoser.futacha.shared.analytics.AnalyticsTracker
import com.valoser.futacha.shared.analytics.CrashReporter
import com.valoser.futacha.shared.analytics.PerformanceTracker
import com.valoser.futacha.shared.analytics.analyticsCountBucket
import com.valoser.futacha.shared.analytics.analyticsFailureCategory
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.rememberDirectoryPickerLauncher
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.cancellation.CancellationException

private const val FUTACHA_APP_BINDINGS_TAG = "FutachaApp"
private const val UI_HISTORY_REFRESH_TIMEOUT_MILLIS = 60_000L
// The Android application intentionally uses a conservative network concurrency
// for history refresh. Keep the interactive run short enough that a slow/dead
// board cannot make the drawer look frozen; subsequent taps advance the cursor.
private const val UI_HISTORY_REFRESH_MAX_THREADS_PER_RUN = 5
private const val UI_HISTORY_REFRESH_THREAD_TIMEOUT_MILLIS = 6_000L

private class UiHistoryRefreshTimeoutException :
    IllegalStateException("履歴更新がタイムアウトしました。時間をおいて再度お試しください")

internal data class FutachaBindingsRuntimeState(
    val screenBindings: FutachaScreenBindingsBundle
)

@Composable
internal fun rememberFutachaBindingsRuntimeState(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore,
    persistedBoards: List<BoardSummary>,
    persistedHistory: List<ThreadHistoryEntry>,
    observedRuntimeState: FutachaObservedRuntimeState,
    shouldUseLightweightMode: Boolean,
    historyRefresher: HistoryRefresher,
    effectiveAutoSavedThreadRepository: SavedThreadRepository?,
    fileSystem: FileSystem?,
    compatibilityStore: CompatibilityStore? = null,
    navigationState: FutachaNavigationState,
    updateNavigationState: (FutachaNavigationState) -> Unit,
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)? = null
): FutachaBindingsRuntimeState {
    val refreshHistoryEntries: suspend () -> Unit = {
        AnalyticsTracker.event(
            "history_refresh_started",
            mapOf(
                "source" to "drawer",
                "history_count_bucket" to analyticsCountBucket(persistedHistory.size)
            )
        )
        CrashReporter.log("history_refresh_started source=drawer")
        try {
            PerformanceTracker.measureSuspend(
                traceName = "history_refresh_drawer",
                attributes = mapOf(
                    "source" to "drawer",
                    "history_count_bucket" to analyticsCountBucket(persistedHistory.size)
                )
            ) {
                val completed = kotlinx.coroutines.withTimeoutOrNull(UI_HISTORY_REFRESH_TIMEOUT_MILLIS) {
                    historyRefresher.refresh(
                        boardsSnapshot = persistedBoards,
                        historySnapshot = persistedHistory,
                        autoSaveBudgetMillis = 0L,
                        maxThreadsPerRun = UI_HISTORY_REFRESH_MAX_THREADS_PER_RUN,
                        maxAutoSavesPerRun = 0,
                        threadFetchTimeoutMillisOverride = UI_HISTORY_REFRESH_THREAD_TIMEOUT_MILLIS
                    )
                }
                if (completed == null) {
                    throw UiHistoryRefreshTimeoutException()
                }
            }
            val errorSnapshot = historyRefresher.lastRefreshError.value
            AnalyticsTracker.event(
                "history_refresh_result",
                mapOf(
                    "source" to "drawer",
                    "result" to "success",
                    "thread_count_bucket" to analyticsCountBucket(errorSnapshot?.totalThreads ?: 0),
                    "error_count_bucket" to analyticsCountBucket(errorSnapshot?.errorCount ?: 0)
                )
            )
            CrashReporter.setKey("last_history_refresh_result", "success")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val category = analyticsFailureCategory(error)
            AnalyticsTracker.event(
                "history_refresh_result",
                mapOf(
                    "source" to "drawer",
                    "result" to "failure",
                    "failure_category" to category
                )
            )
            CrashReporter.recordNonFatal(
                error,
                keys = mapOf(
                    "last_history_refresh_result" to "failure",
                    "last_history_refresh_category" to category
                )
            )
            throw error
        }
    }
    val preferenceMutations = buildFutachaPreferenceMutationCallbacks(
        coroutineScope = coroutineScope,
        inputs = FutachaPreferenceMutationInputs(
            setUpdateCheckEnabled = stateStore::setUpdateCheckEnabled,
            setBackgroundRefreshEnabled = stateStore::setBackgroundRefreshEnabled,
            setWatchAlertEnabled = { enabled ->
                if (onWatchAlertSettingChangeRequested != null) {
                    onWatchAlertSettingChangeRequested(enabled)
                } else {
                    stateStore.setWatchAlertEnabled(enabled)
                }
            },
            setLightweightModeEnabled = stateStore::setLightweightModeEnabled,
            setThreadSummaryModeEnabled = stateStore::setThreadSummaryModeEnabled,
            setAiPostFilterEnabled = stateStore::setAiPostFilterEnabled,
            setAiCommandEnabled = stateStore::setAiCommandEnabled,
            setTelemetryCollectionEnabled = stateStore::setTelemetryCollectionEnabled,
            setAppLockPassword = stateStore::setAppLockPassword,
            clearAppLockPassword = stateStore::clearAppLockPassword,
            setManualSaveDirectory = stateStore::setManualSaveDirectory,
            setAttachmentPickerPreference = stateStore::setAttachmentPickerPreference,
            setSaveDirectorySelection = stateStore::setSaveDirectorySelection,
            setThreadGalleryTapAction = stateStore::setThreadGalleryTapAction,
            setThreadGalleryThumbnailMode = stateStore::setThreadGalleryThumbnailMode,
            setThemeMode = stateStore::setThemeMode,
            setThemePalette = stateStore::setThemePalette,
            setAppIconVariant = stateStore::setAppIconVariant,
            setThreadDisplayMode = stateStore::setThreadDisplayMode,
            setThreadBodyTextSize = stateStore::setThreadBodyTextSize,
            setThreadPostImageSize = stateStore::setThreadPostImageSize,
            setCompactThreadHeaderEnabled = stateStore::setCompactThreadHeaderEnabled,
            setCatalogFetchRows = stateStore::setCatalogFetchRows,
            setManualSaveLocation = stateStore::setManualSaveLocation,
            setPreferredFileManager = stateStore::setPreferredFileManager,
            setThreadMenuEntries = stateStore::setThreadMenuEntries,
            setCatalogNavEntries = stateStore::setCatalogNavEntries
        )
    )
    val directoryPickerLauncher = rememberDirectoryPickerLauncher(
        onDirectorySelected = { pickedLocation ->
            preferenceMutations.onManualSaveLocationChanged(pickedLocation)
            preferenceMutations.onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
        },
        preferredFileManagerPackage = observedRuntimeState.preferredFileManager?.packageName
    )
    val importedHistoryRepository = remember(fileSystem) {
        buildImportedHistoryRepository(fileSystem)
    }
    val clearHistoryEntries: suspend () -> Unit = {
        clearHistory(
            stateStore = stateStore,
            autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
            importedHistoryRepository = importedHistoryRepository,
            compatibilityStore = compatibilityStore,
            onSkippedThreadsCleared = historyRefresher::clearSkippedThreads,
            onAutoSavedThreadDeleteFailure = {
                Logger.e(FUTACHA_APP_BINDINGS_TAG, "Failed to clear auto saved threads", it)
            }
        )
    }
    val historyMutations = buildFutachaHistoryMutationCallbacks(
        coroutineScope = coroutineScope,
        dismissHistoryEntry = { entry ->
            dismissHistoryEntry(
                stateStore = stateStore,
                autoSavedThreadRepository = effectiveAutoSavedThreadRepository,
                importedHistoryRepository = importedHistoryRepository,
                compatibilityStore = compatibilityStore,
                entry = entry,
                onAutoSavedThreadDeleteFailure = {
                    Logger.e(FUTACHA_APP_BINDINGS_TAG, "Failed to delete auto-saved thread ${entry.threadId}", it)
                }
            )
        },
        updateHistoryEntry = stateStore::upsertHistoryEntry,
        clearHistory = clearHistoryEntries
    )
    val historyArchiveSourceRepositories = remember(
        effectiveAutoSavedThreadRepository,
        observedRuntimeState.activeSavedThreadsRepository
    ) {
        listOfNotNull(
            effectiveAutoSavedThreadRepository,
            observedRuntimeState.activeSavedThreadsRepository
        ).distinct()
    }
    val screenBindings = buildFutachaScreenBindingsBundle(
        coroutineScope = coroutineScope,
        inputs = FutachaScreenBindingsInputs(
            history = persistedHistory,
            currentBoards = { persistedBoards },
            currentNavigationState = { navigationState },
            setNavigationState = updateNavigationState,
            updateBoards = stateStore::updateBoards,
            preferenceMutations = preferenceMutations,
            historyMutations = historyMutations,
            preferencesStateInputs = FutachaScreenPreferencesStateInputs(
                appVersion = observedRuntimeState.appVersion,
                isUpdateCheckEnabled = observedRuntimeState.isUpdateCheckEnabled,
                isBackgroundRefreshEnabled = observedRuntimeState.isBackgroundRefreshEnabled,
                isWatchAlertEnabled = observedRuntimeState.isWatchAlertEnabled,
                isLightweightModeEnabled = shouldUseLightweightMode,
                isThreadSummaryModeEnabled = observedRuntimeState.isThreadSummaryModeEnabled,
                isAiPostFilterEnabled = observedRuntimeState.isAiPostFilterEnabled,
                isAiCommandEnabled = observedRuntimeState.isAiCommandEnabled,
                isTelemetryCollectionEnabled = observedRuntimeState.isTelemetryCollectionEnabled,
                isAppLockEnabled = observedRuntimeState.appLockPasswordHash != null,
                aiAvailability = observedRuntimeState.aiAvailability,
                manualSaveDirectory = observedRuntimeState.manualSaveDirectory,
                manualSaveLocation = observedRuntimeState.manualSaveLocation,
                resolvedManualSaveDirectory = observedRuntimeState.resolvedManualSaveDirectory,
                attachmentPickerPreference = observedRuntimeState.attachmentPickerPreference,
                saveDirectorySelection = observedRuntimeState.saveDirectorySelection,
                threadGalleryTapAction = observedRuntimeState.threadGalleryTapAction,
                threadGalleryThumbnailMode = observedRuntimeState.threadGalleryThumbnailMode,
                themeMode = observedRuntimeState.themeMode,
                themePalette = observedRuntimeState.themePalette,
                appIconVariant = observedRuntimeState.appIconVariant,
                threadDisplayMode = observedRuntimeState.threadDisplayMode,
                threadBodyTextSize = observedRuntimeState.threadBodyTextSize,
                threadPostImageSize = observedRuntimeState.threadPostImageSize,
                isCompactThreadHeaderEnabled = observedRuntimeState.isCompactThreadHeaderEnabled,
                catalogFetchRows = observedRuntimeState.catalogFetchRows,
                preferredFileManagerPackage = observedRuntimeState.preferredFileManager?.packageName,
                preferredFileManagerLabel = observedRuntimeState.preferredFileManager?.label,
                threadMenuEntries = observedRuntimeState.threadMenuEntries,
                catalogNavEntries = observedRuntimeState.catalogNavEntries
            ),
            onOpenSaveDirectoryPicker = directoryPickerLauncher,
            onHistoryRefresh = refreshHistoryEntries,
            onHistoryExport = {
                exportAllFutachaHistoryArchive(
                    stateStore = stateStore,
                    fileSystem = fileSystem,
                    sourceRepositories = historyArchiveSourceRepositories,
                    appVersion = observedRuntimeState.appVersion
                )
            },
            onHistoryExportThenClear = {
                exportAllFutachaHistoryArchiveThenClear(
                    stateStore = stateStore,
                    fileSystem = fileSystem,
                    sourceRepositories = historyArchiveSourceRepositories,
                    appVersion = observedRuntimeState.appVersion,
                    clearHistory = clearHistoryEntries
                )
            },
            onHistoryExportSelected = { selectedEntries ->
                exportSelectedFutachaHistoryArchive(
                    stateStore = stateStore,
                    fileSystem = fileSystem,
                    sourceRepositories = historyArchiveSourceRepositories,
                    appVersion = observedRuntimeState.appVersion,
                    selectedEntries = selectedEntries
                )
            },
            onHistoryLoadImportPreview = {
                loadLatestFutachaHistoryArchivePreview(
                    stateStore = stateStore,
                    fileSystem = fileSystem
                )
            },
            onHistoryImport = {
                importLatestFutachaHistoryArchive(
                    stateStore = stateStore,
                    fileSystem = fileSystem,
                    destinationRepository = importedHistoryRepository
                )
            },
            onHistoryImportSelected = { selectedSnapshotIds ->
                importLatestFutachaHistoryArchive(
                    stateStore = stateStore,
                    fileSystem = fileSystem,
                    destinationRepository = importedHistoryRepository,
                    selectedSnapshotIds = selectedSnapshotIds
                )
            }
        )
    )
    return remember(screenBindings) {
        FutachaBindingsRuntimeState(
            screenBindings = screenBindings
        )
    }
}
