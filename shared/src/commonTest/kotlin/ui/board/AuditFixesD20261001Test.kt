package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.ImageData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditFixesD20261001Test {

    @Test
    fun createThread_clearsDraftAndRefreshesWithoutWaitingForSnackbar() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val board = BoardSummary(
            id = "b",
            name = "Board",
            category = "cat",
            url = "https://may.2chan.net/b/futaba.php",
            description = ""
        )
        var draft = CreateThreadDraft(name = "name", title = "title", comment = "comment", password = "key")
        var image: ImageData? = ImageData(byteArrayOf(1), "a.jpg")
        var isSubmitting = false
        var refreshCount = 0
        var snackbarShown = false
        val bindings = buildCatalogCreateThreadBindings(
            coroutineScope = scope,
            activeRepository = FakeBoardRepository(),
            stateStore = null,
            currentBoard = { board },
            currentDraft = { draft },
            currentImage = { image },
            currentIsSubmitting = { isSubmitting },
            setIsSubmitting = { isSubmitting = it },
            setCreateThreadDraft = { draft = it },
            setCreateThreadImage = { image = it },
            setShowCreateThreadDialog = {},
            updateLastUsedDeleteKey = {},
            // A real snackbar suspends until it is dismissed.
            showSnackbar = { snackbarShown = true; awaitCancellation() },
            performRefresh = { refreshCount += 1 }
        )

        bindings.submitCreateThread()
        repeat(5) { yield() }

        assertTrue(snackbarShown)
        assertEquals(emptyCreateThreadDraft(), draft)
        assertEquals(null, image)
        assertEquals(1, refreshCount)
        assertFalse(isSubmitting)
        scope.cancel()
    }

    @Test
    fun ngWordEdits_fromTheSameStaleSnapshotAreBothKept() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val scope = CoroutineScope(coroutineContext + Job())
        val persistence = buildThreadNgPersistenceBindings(
            coroutineScope = scope,
            stateStore = store,
            onFallbackHeadersChanged = {},
            onFallbackWordsChanged = {}
        )
        val callbacks = buildThreadNgMutationCallbacks(
            // The UI has not observed either write yet.
            currentHeaders = { emptyList() },
            currentWords = { emptyList() },
            isFilteringEnabled = { true },
            setFilteringEnabled = {},
            persistHeaders = persistence.persistHeaders,
            persistWords = persistence.persistWords,
            showMessage = {}
        )

        callbacks.onAddWord("first")
        callbacks.onAddWord("second")
        callbacks.onAddHeader("ID:abc")
        scope.coroutineContext[Job]!!.children.toList().joinAll()

        assertEquals(listOf("first", "second"), store.ngWords.first())
        assertEquals(listOf("ID:abc"), store.ngHeaders.first())

        callbacks.onRemoveWord("first")
        callbacks.onRemoveWord("second")
        scope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals(emptyList(), store.ngWords.first())
        scope.cancel()
    }

    @Test
    fun catalogWatchWordEdits_areAppliedToTheLatestStoredList() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val scope = CoroutineScope(coroutineContext + Job())
        val bindings = buildCatalogPersistenceBindings(
            coroutineScope = scope,
            stateStore = store,
            currentBoardWatchWordKey = { "img" },
            onFallbackCatalogNgWordsChanged = {},
            onFallbackWatchWordsChanged = {}
        )

        bindings.persistCatalogNgWords.persistListEdit(listOf("a")) { addCatalogNgWord(it, "a").updatedWords }
        bindings.persistCatalogNgWords.persistListEdit(listOf("b")) { addCatalogNgWord(it, "b").updatedWords }
        bindings.persistGlobalWatchWords.persistListEdit(listOf("w1")) { addWatchWord(it, "w1").updatedWords }
        bindings.persistGlobalWatchWords.persistListEdit(listOf("w2")) { addWatchWord(it, "w2").updatedWords }
        scope.coroutineContext[Job]!!.children.toList().joinAll()
        bindings.persistBoardWatchWords.persistListEdit(listOf("w1", "w2", "x")) { addWatchWord(it, "x").updatedWords }
        scope.coroutineContext[Job]!!.children.toList().joinAll()

        assertEquals(listOf("a", "b"), store.catalogNgWords.first())
        assertEquals(listOf("w1", "w2"), store.watchWords.first())
        assertEquals(listOf("w1", "w2", "x"), store.boardWatchWords.first()["img"])
        scope.cancel()
    }

    @Test
    fun pastThreadSearchFailureMessage_reportsTimeouts() {
        val timeout = runCatching {
            runBlocking { withTimeout(1) { awaitCancellation() } }
        }.exceptionOrNull()
        assertTrue(timeout is TimeoutCancellationException)
        assertEquals("検索がタイムアウトしました", buildCatalogPastThreadSearchFailureMessage(timeout!!))
        assertEquals("boom", buildCatalogPastThreadSearchFailureMessage(IllegalStateException("boom")))
    }

    @Test
    fun cookieMutationFailureMessage_includesDetail() {
        assertEquals("Cookieを削除できませんでした: disk full", buildCookieMutationFailureMessage(IllegalStateException("disk full")))
        assertEquals("Cookieを削除できませんでした", buildCookieMutationFailureMessage(null))
    }
}
