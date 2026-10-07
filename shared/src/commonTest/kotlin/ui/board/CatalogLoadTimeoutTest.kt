package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A load that ends in its own withTimeout must reach Error/failure, never stay "Loading". */
class CatalogLoadTimeoutTest {
    private val board = BoardSummary(
        id = "b",
        name = "Board",
        category = "cat",
        url = "https://may.2chan.net/b/futaba.php",
        description = ""
    )

    private suspend fun timeoutException(): TimeoutCancellationException = try {
        withTimeout(1) { awaitCancellation() }
    } catch (e: TimeoutCancellationException) {
        e
    }

    @Test
    fun initialLoadTimeoutEndsInErrorInsteadOfStayingLoading() = runBlocking {
        val timeout = timeoutException()
        var generation = 0L
        var runningJob: Job? = null
        var captured: Job? = null
        var uiState: CatalogUiState = CatalogUiState.Error("before")
        val bindings = buildCatalogInitialLoadBindings(
            coroutineScope = this,
            currentBoard = { board },
            currentCatalogMode = { CatalogMode.default },
            currentCatalogLoadGeneration = { generation },
            setCatalogLoadGeneration = { generation = it },
            currentCatalogLoadJob = { runningJob },
            setCatalogLoadJob = { runningJob = it; if (it != null) captured = it },
            setIsRefreshing = {},
            setCatalogUiState = { uiState = it },
            setLastCatalogItems = {},
            loadCatalogItems = { _, _, _ -> throw timeout }
        )

        bindings.loadInitialCatalog()
        captured!!.join()

        assertEquals(CatalogUiState.Error("タイムアウト: サーバーが応答しません"), uiState)
        assertEquals(null, runningJob)
    }

    @Test
    fun cancellingTheLoadItselfStillStaysSilent() = runBlocking {
        var generation = 0L
        var runningJob: Job? = null
        var captured: Job? = null
        var uiState: CatalogUiState = CatalogUiState.Error("before")
        val bindings = buildCatalogInitialLoadBindings(
            coroutineScope = this,
            currentBoard = { board },
            currentCatalogMode = { CatalogMode.default },
            currentCatalogLoadGeneration = { generation },
            setCatalogLoadGeneration = { generation = it },
            currentCatalogLoadJob = { runningJob },
            setCatalogLoadJob = { runningJob = it; if (it != null) captured = it },
            setIsRefreshing = {},
            setCatalogUiState = { uiState = it },
            setLastCatalogItems = {},
            loadCatalogItems = { _, _, _ -> awaitCancellation() }
        )

        bindings.loadInitialCatalog()
        yield()
        captured!!.cancel()
        captured!!.join()

        assertEquals(CatalogUiState.Loading, uiState)
    }

    @Test
    fun manualRefreshTimeoutReportsAFailureAndClearsRefreshing() = runBlocking {
        val timeout = timeoutException()
        var generation = 0L
        var runningJob: Job? = null
        var captured: Job? = null
        var isRefreshing = false
        val messages = mutableListOf<String>()
        val bindings = buildCatalogExecutionBindings(
            coroutineScope = this,
            currentIsHistoryRefreshing = { false },
            setIsHistoryRefreshing = {},
            onHistoryRefresh = {},
            showSnackbar = { messages += it },
            currentBoard = { board },
            currentCatalogMode = { CatalogMode.default },
            currentIsRefreshing = { isRefreshing },
            currentCatalogLoadGeneration = { generation },
            setCatalogLoadGeneration = { generation = it },
            setIsRefreshing = { isRefreshing = it },
            currentCatalogLoadJob = { runningJob },
            setCatalogLoadJob = { runningJob = it; if (it != null) captured = it },
            setCatalogUiState = {},
            setLastCatalogItems = { _: List<CatalogItem> -> },
            loadCatalogItems = { _, _, _ -> throw timeout },
            currentPastSearchRuntimeState = { CatalogPastSearchRuntimeState() },
            setPastSearchRuntimeState = {},
            httpClient = null,
            archiveSearchJson = Json
        )

        bindings.performRefresh()
        captured!!.join()

        assertEquals(listOf(buildCatalogRefreshFailureMessage()), messages)
        assertFalse(isRefreshing)
        assertTrue(runningJob == null)
    }
}
