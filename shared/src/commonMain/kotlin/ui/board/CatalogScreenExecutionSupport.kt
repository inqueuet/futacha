package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.fetchArchiveSearchResults
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.service.HistoryRefresher
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.ImageData
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

internal fun buildCatalogHistoryRefreshSuccessMessage(): String = "履歴を更新しました"

internal fun buildCatalogHistoryRefreshBusyMessage(): String = "履歴更新はすでに実行中です"

internal fun buildCatalogHistoryRefreshFailureMessage(error: Throwable): String {
    return "履歴の更新に失敗しました: ${error.message ?: "不明なエラー"}"
}

internal fun buildCatalogRefreshSuccessMessage(): String = "カタログを更新しました"

internal fun buildCatalogPastThreadSearchClientUnavailableMessage(): String {
    return "ネットワーククライアントが利用できません"
}

internal data class CatalogPersistenceBindings(
    val persistCatalogNgWords: (List<String>) -> Unit,
    val persistWatchWords: (List<String>) -> Unit
)

internal fun buildCatalogPersistenceBindings(
    coroutineScope: CoroutineScope,
    stateStore: AppStateStore?,
    onFallbackCatalogNgWordsChanged: (List<String>) -> Unit,
    onFallbackWatchWordsChanged: (List<String>) -> Unit
): CatalogPersistenceBindings {
    return CatalogPersistenceBindings(
        persistCatalogNgWords = { updated ->
            if (stateStore != null) {
                coroutineScope.launch {
                    stateStore.setCatalogNgWords(updated)
                }
            } else {
                onFallbackCatalogNgWordsChanged(updated)
            }
        },
        persistWatchWords = { updated ->
            if (stateStore != null) {
                coroutineScope.launch {
                    stateStore.setWatchWords(updated)
                }
            } else {
                onFallbackWatchWordsChanged(updated)
            }
        }
    )
}

internal data class CatalogExecutionBindings(
    val handleHistoryRefresh: () -> Unit,
    val performRefresh: () -> Unit,
    val runPastThreadSearch: (String, ArchiveSearchScope?) -> Boolean
)

internal data class CatalogInitialLoadBindings(
    val loadInitialCatalog: () -> Unit
)

internal data class CatalogCreateThreadBindings(
    val resetCreateThreadDraft: () -> Unit,
    val submitCreateThread: () -> Unit
)

internal fun buildCatalogInitialLoadBindings(
    coroutineScope: CoroutineScope,
    currentBoard: () -> BoardSummary?,
    currentCatalogMode: () -> CatalogMode,
    currentCatalogLoadGeneration: () -> Long,
    setCatalogLoadGeneration: (Long) -> Unit,
    currentCatalogLoadJob: () -> Job?,
    setCatalogLoadJob: (Job?) -> Unit,
    setIsRefreshing: (Boolean) -> Unit,
    setCatalogUiState: (CatalogUiState) -> Unit,
    setLastCatalogItems: (List<CatalogItem>) -> Unit,
    loadCatalogItems: suspend (BoardSummary, CatalogMode) -> List<CatalogItem>
): CatalogInitialLoadBindings {
    return CatalogInitialLoadBindings(
        loadInitialCatalog = load@{
            val board = currentBoard()
            if (board == null) {
                setCatalogLoadGeneration(nextCatalogRequestGeneration(currentCatalogLoadGeneration()))
                currentCatalogLoadJob()?.cancel()
                setCatalogLoadJob(null)
                setIsRefreshing(false)
                setCatalogUiState(CatalogUiState.Error("板が選択されていません"))
                return@load
            }
            val requestGeneration = nextCatalogRequestGeneration(currentCatalogLoadGeneration())
            setCatalogLoadGeneration(requestGeneration)
            currentCatalogLoadJob()?.cancel()
            setIsRefreshing(false)
            setCatalogUiState(CatalogUiState.Loading)
            setCatalogLoadJob(
                coroutineScope.launch {
                    val runningJob = coroutineContext[Job]
                    try {
                        val catalog = loadCatalogItems(board, currentCatalogMode())
                        if (!shouldApplyCatalogRequestResult(isActive, currentCatalogLoadGeneration(), requestGeneration)) {
                            return@launch
                        }
                        setCatalogUiState(CatalogUiState.Success(catalog))
                        setLastCatalogItems(catalog)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (shouldApplyCatalogRequestResult(isActive, currentCatalogLoadGeneration(), requestGeneration)) {
                            setCatalogUiState(CatalogUiState.Error(buildCatalogLoadErrorMessage(e)))
                        }
                    } finally {
                        if (runningJob != null && currentCatalogLoadJob() == runningJob) {
                            setCatalogLoadJob(null)
                        }
                    }
                }
            )
        }
    )
}

internal fun buildCatalogCreateThreadBindings(
    coroutineScope: CoroutineScope,
    activeRepository: BoardRepository,
    currentBoard: () -> BoardSummary?,
    currentDraft: () -> CreateThreadDraft,
    currentImage: () -> ImageData?,
    setCreateThreadDraft: (CreateThreadDraft) -> Unit,
    setCreateThreadImage: (ImageData?) -> Unit,
    setShowCreateThreadDialog: (Boolean) -> Unit,
    updateLastUsedDeleteKey: (String) -> Unit,
    showSnackbar: suspend (String) -> Unit,
    performRefresh: () -> Unit
): CatalogCreateThreadBindings {
    val resetDraft: () -> Unit = {
        setCreateThreadDraft(emptyCreateThreadDraft())
        setCreateThreadImage(null)
    }
    return CatalogCreateThreadBindings(
        resetCreateThreadDraft = resetDraft,
        submitCreateThread = submit@{
            setShowCreateThreadDialog(false)
            val board = currentBoard()
            if (board == null) {
                coroutineScope.launch {
                    showSnackbar(buildCreateThreadBoardMissingMessage())
                }
                return@submit
            }
            val draft = currentDraft()
            val trimmedPassword = normalizeCreateThreadPasswordForSubmit(draft.password)
            if (trimmedPassword.isNotBlank()) {
                updateLastUsedDeleteKey(trimmedPassword)
            }
            val imageData = currentImage()
            coroutineScope.launch {
                try {
                    val threadId = activeRepository.createThread(
                        board = board.url,
                        name = draft.name,
                        email = draft.email,
                        subject = draft.title,
                        comment = draft.comment,
                        password = trimmedPassword,
                        imageFile = imageData?.bytes,
                        imageFileName = imageData?.fileName,
                        textOnly = imageData == null
                    )
                    showSnackbar(buildCreateThreadSuccessMessage(threadId))
                    resetDraft()
                    performRefresh()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(buildCreateThreadFailureMessage(e))
                }
            }
        }
    )
}

internal fun buildCatalogExecutionBindings(
    coroutineScope: CoroutineScope,
    currentIsHistoryRefreshing: () -> Boolean,
    setIsHistoryRefreshing: (Boolean) -> Unit,
    onHistoryRefresh: suspend () -> Unit,
    showSnackbar: suspend (String) -> Unit,
    currentBoard: () -> BoardSummary?,
    currentCatalogMode: () -> CatalogMode,
    currentIsRefreshing: () -> Boolean,
    currentCatalogLoadGeneration: () -> Long,
    setCatalogLoadGeneration: (Long) -> Unit,
    setIsRefreshing: (Boolean) -> Unit,
    currentCatalogLoadJob: () -> Job?,
    setCatalogLoadJob: (Job?) -> Unit,
    setCatalogUiState: (CatalogUiState) -> Unit,
    setLastCatalogItems: (List<CatalogItem>) -> Unit,
    loadCatalogItems: suspend (BoardSummary, CatalogMode) -> List<CatalogItem>,
    currentPastSearchRuntimeState: () -> CatalogPastSearchRuntimeState,
    setPastSearchRuntimeState: (CatalogPastSearchRuntimeState) -> Unit,
    httpClient: HttpClient?,
    archiveSearchJson: Json
): CatalogExecutionBindings {
    return CatalogExecutionBindings(
        handleHistoryRefresh = handleHistoryRefresh@{
            if (currentIsHistoryRefreshing()) return@handleHistoryRefresh
            setIsHistoryRefreshing(true)
            coroutineScope.launch {
                try {
                    onHistoryRefresh()
                    showSnackbar(buildCatalogHistoryRefreshSuccessMessage())
                } catch (e: HistoryRefresher.RefreshAlreadyRunningException) {
                    showSnackbar(buildCatalogHistoryRefreshBusyMessage())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showSnackbar(buildCatalogHistoryRefreshFailureMessage(e))
                } finally {
                    setIsHistoryRefreshing(false)
                }
            }
        },
        performRefresh = refresh@{
            val board = currentBoard() ?: return@refresh
            when (resolveCatalogRefreshAvailability(currentIsRefreshing())) {
                CatalogRefreshAvailability.Busy -> return@refresh
                CatalogRefreshAvailability.Ready -> Unit
            }
            val requestGeneration = nextCatalogRequestGeneration(currentCatalogLoadGeneration())
            setCatalogLoadGeneration(requestGeneration)
            setIsRefreshing(true)
            currentCatalogLoadJob()?.cancel()
            setCatalogLoadJob(
                coroutineScope.launch {
                    val runningJob = coroutineContext[Job]
                    try {
                        val catalog = loadCatalogItems(board, currentCatalogMode())
                        if (!shouldApplyCatalogRequestResult(isActive, currentCatalogLoadGeneration(), requestGeneration)) {
                            return@launch
                        }
                        setCatalogUiState(CatalogUiState.Success(catalog))
                        setLastCatalogItems(catalog)
                        showSnackbar(buildCatalogRefreshSuccessMessage())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (shouldApplyCatalogRequestResult(isActive, currentCatalogLoadGeneration(), requestGeneration)) {
                            showSnackbar(buildCatalogRefreshFailureMessage())
                        }
                    } finally {
                        if (
                            shouldFinalizeCatalogRefresh(
                                isSameRunningJob = runningJob != null && currentCatalogLoadJob() == runningJob,
                                currentGeneration = currentCatalogLoadGeneration(),
                                requestGeneration = requestGeneration
                            )
                        ) {
                            setIsRefreshing(false)
                            setCatalogLoadJob(null)
                        }
                    }
                }
            )
        },
        runPastThreadSearch = runPastThreadSearch@{ query, scope ->
            val client = httpClient
            if (client == null) {
                coroutineScope.launch {
                    showSnackbar(buildCatalogPastThreadSearchClientUnavailableMessage())
                }
                return@runPastThreadSearch false
            }
            val currentRuntime = currentPastSearchRuntimeState()
            val requestGeneration = nextCatalogRequestGeneration(currentRuntime.generation)
            currentRuntime.job?.cancel()
            setPastSearchRuntimeState(
                currentRuntime.copy(
                    state = ArchiveSearchState.Loading,
                    generation = requestGeneration,
                    job = null
                )
            )
            setPastSearchRuntimeState(
                coroutineScope.launch {
                    val runningJob = coroutineContext[Job]
                    try {
                        val items = withContext(AppDispatchers.io) {
                            fetchArchiveSearchResults(client, query, scope, archiveSearchJson)
                        }
                        if (!shouldApplyCatalogRequestResult(isActive, currentPastSearchRuntimeState().generation, requestGeneration)) {
                            return@launch
                        }
                        setPastSearchRuntimeState(
                            currentPastSearchRuntimeState().copy(state = ArchiveSearchState.Success(items))
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (error: Throwable) {
                        if (shouldApplyCatalogRequestResult(isActive, currentPastSearchRuntimeState().generation, requestGeneration)) {
                            setPastSearchRuntimeState(
                                currentPastSearchRuntimeState().copy(
                                    state = ArchiveSearchState.Error(buildPastThreadSearchErrorMessage(error))
                                )
                            )
                        }
                    } finally {
                        if (runningJob != null && currentPastSearchRuntimeState().job == runningJob) {
                            setPastSearchRuntimeState(
                                currentPastSearchRuntimeState().copy(job = null)
                            )
                        }
                    }
                }.let { job ->
                    currentPastSearchRuntimeState().copy(job = job)
                }
            )
            true
        }
    )
}
