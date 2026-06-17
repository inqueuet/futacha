package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandOutcome
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FutachaAiCommandRouterTest {
    @Test
    fun bridgeCommandLaunchPolicyOnlyRunsHistoryRefreshAsynchronously() {
        assertTrue(shouldLaunchAiCommandFromBridge(FutachaAiCommand(FutachaAiAction.RefreshHistory)))
        assertFalse(shouldLaunchAiCommandFromBridge(FutachaAiCommand(FutachaAiAction.OpenBoardList)))
        assertFalse(shouldLaunchAiCommandFromBridge(FutachaAiCommand(FutachaAiAction.OpenThread)))
    }

    @Test
    fun disabledAiCommandsRejectAppOperationsButAllowSettings() {
        runBlocking {
            val harness = RouterHarness(isAiCommandEnabled = false)

            val rejected = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.EnablePrivacyFilter),
                inputs = harness.inputs()
            )
            val allowed = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.OpenGlobalSettings),
                inputs = harness.inputs()
            )
            val fileManagerSettings = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.OpenFileManagerSettings),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Failed>(rejected)
            assertIs<FutachaAiCommandOutcome.Completed>(allowed)
            assertIs<FutachaAiCommandOutcome.Completed>(fileManagerSettings)
        }
    }

    @Test
    fun disabledAiCommandsAllowSafeWearAndWatchOsCommands() {
        runBlocking {
            val harness = RouterHarness(isAiCommandEnabled = false)

            val openBoard = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenBoard,
                    parameters = mapOf("boardId" to "b"),
                    source = "wear-os"
                ),
                inputs = harness.inputs()
            )
            val watchOsOpenBoard = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenBoard,
                    parameters = mapOf("boardId" to "b"),
                    source = "watchos"
                ),
                inputs = harness.inputs()
            )
            val openThread = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenThread,
                    parameters = mapOf(
                        "boardId" to "b",
                        "boardUrl" to "https://may.2chan.net/b/",
                        "threadId" to "123"
                    ),
                    source = "wear-os"
                ),
                inputs = harness.inputs()
            )
            val readAloud = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.StartThreadReadAloud,
                    parameters = mapOf(
                        "boardId" to "b",
                        "boardUrl" to "https://may.2chan.net/b/",
                        "threadId" to "123"
                    ),
                    source = "wear-os"
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(openBoard)
            assertIs<FutachaAiCommandOutcome.Completed>(watchOsOpenBoard)
            assertIs<FutachaAiCommandOutcome.Completed>(openThread)
            assertIs<FutachaAiCommandOutcome.NeedsForeground>(readAloud)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals("123", harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun disabledAiCommandsStillRejectNonWearAndUnsafeWearCommands() {
        runBlocking {
            val harness = RouterHarness(isAiCommandEnabled = false)

            val nonWearOpenBoard = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenBoard,
                    parameters = mapOf("boardId" to "b"),
                    source = "app-function"
                ),
                inputs = harness.inputs()
            )
            val unsafeWearCommand = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.EnablePrivacyFilter,
                    source = "wear-os"
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Failed>(nonWearOpenBoard)
            assertIs<FutachaAiCommandOutcome.Failed>(unsafeWearCommand)
        }
    }

    @Test
    fun confirmRiskCommandReturnsConfirmationBeforeExecution() {
        runBlocking {
            val harness = RouterHarness(
                navigationState = FutachaNavigationState(
                    selectedBoardId = "b",
                    selectedThreadId = "123"
                )
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.SaveCurrentThread),
                inputs = harness.inputs()
            )

            val confirmation = assertIs<FutachaAiCommandOutcome.NeedsConfirmation>(outcome)
            assertEquals(FutachaAiAction.SaveCurrentThread, confirmation.request.command.action)
            assertTrue(confirmation.request.message.contains("スレ保存"))
        }
    }

    @Test
    fun addBoardConfirmationExplainsBoardListChange() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddBoard,
                    parameters = mapOf("name" to "新板", "url" to "https://may.2chan.net/img/")
                ),
                inputs = harness.inputs()
            )

            val confirmation = assertIs<FutachaAiCommandOutcome.NeedsConfirmation>(outcome)
            assertTrue(confirmation.request.message.contains("板リスト"))
        }
    }

    @Test
    fun confirmedSaveThreadNavigatesToRequestedThread() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.SaveThread,
                    parameters = mapOf("board" to "b", "thread" to "123")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals("123", harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun openBoardResolvesBoardUrlAlias() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenBoard,
                    parameters = mapOf("boardUrl" to "https://may.2chan.net/b/")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
        }
    }

    @Test
    fun openBoardResolvesLinkAlias() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenBoard,
                    parameters = mapOf("link" to "https://may.2chan.net/b/")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
        }
    }

    @Test
    fun openBoardResolvesCatalogUrlWithQuery() {
        runBlocking {
            val harness = RouterHarness(
                boards = listOf(board(url = "https://may.2chan.net/b/futaba.php"))
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenBoard,
                    parameters = mapOf("url" to "https://may.2chan.net/b/futaba.php?mode=cat&sort=3")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
        }
    }

    @Test
    fun confirmedSaveThreadAcceptsThreadUrlAlias() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.SaveThread,
                    parameters = mapOf("threadUrl" to "https://may.2chan.net/b/res/123.htm")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals("123", harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun openThreadUrlResolvesBoardWithFutabaPhpUrl() {
        runBlocking {
            val harness = RouterHarness(
                boards = listOf(board(url = "https://may.2chan.net/b/futaba.php"))
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenThreadFromUrl,
                    parameters = mapOf("url" to "https://may.2chan.net/b/res/123.htm")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals("123", harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun openThreadUrlAcceptsResPathWithoutHtmlExtension() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenThreadFromUrl,
                    parameters = mapOf("url" to "https://may.2chan.net/b/res/123")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals("123", harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun openThreadAcceptsLinkAlias() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenThreadFromUrl,
                    parameters = mapOf("link" to "https://may.2chan.net/b/res/123.htm")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals("123", harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun openThreadAcceptsNoAlias() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.OpenThread,
                    parameters = mapOf("board" to "b", "no" to "123")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals("123", harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun currentThreadCommandDoesNotUseSavedThreadsSelectionAsActiveThread() {
        runBlocking {
            val harness = RouterHarness(
                navigationState = FutachaNavigationState(
                    selectedBoardId = "b",
                    selectedThreadId = "123",
                    isSavedThreadsVisible = true
                )
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.SaveCurrentThread),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Failed>(outcome)
            assertTrue(harness.navigationState.isSavedThreadsVisible)
        }
    }

    @Test
    fun setCatalogModePersistsModeAndSelectsBoard() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.SetCatalogMode,
                    parameters = mapOf("board" to "b", "value" to "New")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals(CatalogMode.New, harness.stateStore.catalogModes.first()["b"])
            assertEquals("b", harness.navigationState.selectedBoardId)
        }
    }

    @Test
    fun setCatalogModeAcceptsSortAlias() {
        runBlocking {
            val harness = RouterHarness(
                navigationState = FutachaNavigationState(selectedBoardId = "b")
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.SetCatalogMode,
                    parameters = mapOf("sort" to "many")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals(CatalogMode.Many, harness.stateStore.catalogModes.first()["b"])
            assertEquals("b", harness.navigationState.selectedBoardId)
        }
    }

    @Test
    fun searchCatalogSelectsBoardWithoutRequiringThread() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.SearchCatalog,
                    parameters = mapOf("board" to "b", "query" to "注目")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.NeedsForeground>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals(null, harness.navigationState.selectedThreadId)
            assertFalse(harness.navigationState.isSavedThreadsVisible)
        }
    }

    @Test
    fun searchCatalogAcceptsKeywordAlias() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.SearchCatalog,
                    parameters = mapOf("board" to "b", "keyword" to "注目")
                ),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.NeedsForeground>(outcome)
            assertTrue(outcome.message.contains("注目"))
        }
    }

    @Test
    fun addWatchWordAppendsDistinctValue() {
        runBlocking {
            val harness = RouterHarness()

            executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddWatchWord,
                    parameters = mapOf("word" to "注目")
                ),
                inputs = harness.inputs()
            )
            executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddWatchWord,
                    parameters = mapOf("word" to "注目")
                ),
                inputs = harness.inputs()
            )

            assertEquals(listOf("注目"), harness.stateStore.watchWords.first())
        }
    }

    @Test
    fun openSavedThreadsSelectsSavedThreadsDestination() {
        runBlocking {
            val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "manual")
            val harness = RouterHarness(
                savedThreadRepository = repository,
                navigationState = FutachaNavigationState(
                    selectedBoardId = "b",
                    selectedThreadId = "123"
                )
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.OpenSavedThreads),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals(null, harness.navigationState.selectedBoardId)
            assertEquals(null, harness.navigationState.selectedThreadId)
            assertTrue(harness.navigationState.isSavedThreadsVisible)
        }
    }

    @Test
    fun openSavedThreadsFailsWhenRepositoryIsUnavailable() {
        runBlocking {
            val harness = RouterHarness()

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.OpenSavedThreads),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Failed>(outcome)
            assertFalse(harness.navigationState.isSavedThreadsVisible)
        }
    }

    @Test
    fun openHistoryDrawerKeepsCurrentThreadDestinationWhenAvailable() {
        runBlocking {
            val harness = RouterHarness(
                navigationState = FutachaNavigationState(
                    selectedBoardId = "b",
                    selectedThreadId = "123"
                )
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.OpenHistoryDrawer),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.NeedsForeground>(outcome)
            assertEquals("b", harness.navigationState.selectedBoardId)
            assertEquals("123", harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun openHistoryDrawerReturnsFromSavedThreadsToBoardManagement() {
        runBlocking {
            val harness = RouterHarness(
                navigationState = FutachaNavigationState(isSavedThreadsVisible = true)
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.OpenHistoryDrawer),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.NeedsForeground>(outcome)
            assertFalse(harness.navigationState.isSavedThreadsVisible)
            assertEquals(null, harness.navigationState.selectedBoardId)
            assertEquals(null, harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun openCookieManagementReturnsFromSavedThreadsToBoardManagement() {
        runBlocking {
            val harness = RouterHarness(
                navigationState = FutachaNavigationState(isSavedThreadsVisible = true)
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.OpenCookieManagement),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertFalse(harness.navigationState.isSavedThreadsVisible)
            assertEquals(null, harness.navigationState.selectedBoardId)
            assertEquals(null, harness.navigationState.selectedThreadId)
        }
    }

    @Test
    fun openCookieManagementFailsWhenRepositoryIsUnavailable() {
        runBlocking {
            val harness = RouterHarness(
                navigationState = FutachaNavigationState(isSavedThreadsVisible = true),
                isCookieManagementAvailable = false
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.OpenCookieManagement),
                inputs = harness.inputs()
            )

            assertIs<FutachaAiCommandOutcome.Failed>(outcome)
            assertTrue(harness.navigationState.isSavedThreadsVisible)
        }
    }

    @Test
    fun privacyAndBackgroundRefreshCommandsPersistToggles() {
        runBlocking {
            val harness = RouterHarness()

            executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.EnablePrivacyFilter),
                inputs = harness.inputs()
            )
            executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.EnableBackgroundRefresh),
                inputs = harness.inputs()
            )

            assertTrue(harness.stateStore.isPrivacyFilterEnabled.first())
            assertTrue(harness.stateStore.isBackgroundRefreshEnabled.first())

            executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.DisablePrivacyFilter),
                inputs = harness.inputs()
            )
            executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.DisableBackgroundRefresh),
                inputs = harness.inputs()
            )

            assertFalse(harness.stateStore.isPrivacyFilterEnabled.first())
            assertFalse(harness.stateStore.isBackgroundRefreshEnabled.first())
        }
    }

    @Test
    fun aiFeatureToggleCommandsPersistToggles() {
        runBlocking {
            val harness = RouterHarness()

            executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.EnableThreadSummaryMode),
                inputs = harness.inputs()
            )
            executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.EnableAiPostFilter),
                inputs = harness.inputs()
            )

            assertTrue(harness.stateStore.isThreadSummaryModeEnabled.first())
            assertTrue(harness.stateStore.isAiPostFilterEnabled.first())

            executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.DisableThreadSummaryMode),
                inputs = harness.inputs()
            )
            executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.DisableAiPostFilter),
                inputs = harness.inputs()
            )

            assertFalse(harness.stateStore.isThreadSummaryModeEnabled.first())
            assertFalse(harness.stateStore.isAiPostFilterEnabled.first())
        }
    }

    @Test
    fun addNgWordAndHeaderAppendDistinctValues() {
        runBlocking {
            val harness = RouterHarness()

            executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddNgWord,
                    parameters = mapOf("word" to "spam")
                ),
                inputs = harness.inputs()
            )
            executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddNgWord,
                    parameters = mapOf("query" to "SPAM")
                ),
                inputs = harness.inputs()
            )
            executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddNgHeader,
                    parameters = mapOf("header" to "ID:abcd")
                ),
                inputs = harness.inputs()
            )

            assertEquals(listOf("spam"), harness.stateStore.ngWords.first())
            assertEquals(listOf("ID:abcd"), harness.stateStore.ngHeaders.first())
        }
    }

    @Test
    fun confirmedDeleteHistoryEntryRemovesMatchedHistory() {
        runBlocking {
            val autoRepository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "auto")
            autoRepository.addThreadToIndex(savedThread(threadId = "123", boardId = "b")).getOrThrow()
            val harness = RouterHarness(autoSavedThreadRepository = autoRepository)
            harness.stateStore.setHistory(harness.history)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.DeleteHistoryEntry,
                    parameters = mapOf("board" to "b", "thread" to "123")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals(emptyList(), harness.stateStore.history.first())
            assertFalse(autoRepository.threadExists("123", "b"))
        }
    }

    @Test
    fun confirmedDeleteHistoryEntryRejectsAmbiguousThreadId() {
        runBlocking {
            val boards = listOf(
                board(id = "b", url = "https://may.2chan.net/b/"),
                board(id = "img", url = "https://may.2chan.net/img/")
            )
            val history = listOf(
                historyEntry(threadId = "999", boardId = "b", boardUrl = "https://may.2chan.net/b/"),
                historyEntry(threadId = "999", boardId = "img", boardUrl = "https://may.2chan.net/img/")
            )
            val harness = RouterHarness(
                boards = boards,
                history = history
            )
            harness.stateStore.setHistory(history)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.DeleteHistoryEntry,
                    parameters = mapOf("thread" to "999")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Failed>(outcome)
            assertEquals(history, harness.stateStore.history.first())
        }
    }

    @Test
    fun confirmedClearHistoryAlsoClearsAutoSavedThreads() {
        runBlocking {
            val autoRepository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "auto")
            autoRepository.addThreadToIndex(savedThread(threadId = "123", boardId = "b")).getOrThrow()
            autoRepository.addThreadToIndex(savedThread(threadId = "456", boardId = "b")).getOrThrow()
            val harness = RouterHarness(autoSavedThreadRepository = autoRepository)
            harness.stateStore.setHistory(harness.history)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.ClearHistory),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals(emptyList(), harness.stateStore.history.first())
            assertEquals(emptyList(), autoRepository.getAllThreads())
        }
    }

    @Test
    fun confirmedAddBoardValidatesUrlBeforePersisting() {
        runBlocking {
            val harness = RouterHarness()
            harness.stateStore.setBoards(harness.boards)

            val invalid = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddBoard,
                    parameters = mapOf("name" to "invalid", "url" to "may.2chan.net/img")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )
            val duplicate = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddBoard,
                    parameters = mapOf("name" to "dup", "url" to "https://may.2chan.net/b/")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )
            val valid = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddBoard,
                    parameters = mapOf("name" to "新板", "url" to "https://may.2chan.net/img")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Failed>(invalid)
            assertIs<FutachaAiCommandOutcome.Failed>(duplicate)
            assertIs<FutachaAiCommandOutcome.Completed>(valid)
            val boards = harness.stateStore.boards.first()
            assertEquals(2, boards.size)
            assertEquals("https://may.2chan.net/img/futaba.php", boards.last().url)
        }
    }

    @Test
    fun confirmedAddBoardUsesPersistedBoardSnapshotForDuplicateCheck() {
        runBlocking {
            val harness = RouterHarness()
            harness.stateStore.setBoards(
                harness.boards + BoardSummary(
                    id = "img",
                    name = "二次元裏 img",
                    category = "futaba",
                    url = "https://may.2chan.net/img/futaba.php",
                    description = "persisted board"
                )
            )

            val duplicate = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddBoard,
                    parameters = mapOf("name" to "dup", "url" to "https://may.2chan.net/img/")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Failed>(duplicate)
            assertEquals(2, harness.stateStore.boards.first().size)
        }
    }

    @Test
    fun confirmedAddBoardAcceptsBoardUrlAlias() {
        runBlocking {
            val harness = RouterHarness()
            harness.stateStore.setBoards(harness.boards)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddBoard,
                    parameters = mapOf("name" to "新板", "boardUrl" to "https://may.2chan.net/img/")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("https://may.2chan.net/img/futaba.php", harness.stateStore.boards.first().last().url)
        }
    }

    @Test
    fun confirmedAddBoardAcceptsLinkAlias() {
        runBlocking {
            val harness = RouterHarness()
            harness.stateStore.setBoards(harness.boards)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.AddBoard,
                    parameters = mapOf("label" to "新板", "link" to "https://may.2chan.net/img/")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals("https://may.2chan.net/img/futaba.php", harness.stateStore.boards.first().last().url)
        }
    }

    @Test
    fun confirmedDeleteBoardRemovesPersistedBoardAndResetsCurrentNavigation() {
        runBlocking {
            val harness = RouterHarness(
                navigationState = FutachaNavigationState(
                    selectedBoardId = "b",
                    selectedThreadId = "123"
                )
            )
            harness.stateStore.setBoards(harness.boards)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.DeleteBoard,
                    parameters = mapOf("board" to "b")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals(emptyList(), harness.stateStore.boards.first())
            assertEquals(FutachaNavigationState(), harness.navigationState)
        }
    }

    @Test
    fun confirmedDeleteBoardFailsWhenPersistedBoardIsMissing() {
        runBlocking {
            val harness = RouterHarness()
            harness.stateStore.setBoards(emptyList())

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.DeleteBoard,
                    parameters = mapOf("board" to "b")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Failed>(outcome)
            assertEquals(emptyList(), harness.stateStore.boards.first())
        }
    }

    @Test
    fun confirmedDeleteSavedThreadInfersCurrentBoard() {
        runBlocking {
            val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "manual")
            repository.addThreadToIndex(savedThread(threadId = "123", boardId = "b")).getOrThrow()
            val harness = RouterHarness(
                savedThreadRepository = repository,
                navigationState = FutachaNavigationState(
                    selectedBoardId = "b",
                    selectedThreadId = "123"
                )
            )

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.DeleteSavedThread),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertFalse(repository.threadExists("123", "b"))
        }
    }

    @Test
    fun confirmedDeleteSavedThreadAcceptsPostIdAlias() {
        runBlocking {
            val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "manual")
            repository.addThreadToIndex(savedThread(threadId = "123", boardId = "b")).getOrThrow()
            val harness = RouterHarness(savedThreadRepository = repository)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.DeleteSavedThread,
                    parameters = mapOf("board" to "b", "postId" to "123")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertFalse(repository.threadExists("123", "b"))
        }
    }

    @Test
    fun confirmedDeleteSavedThreadRejectsAmbiguousThreadId() {
        runBlocking {
            val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "manual")
            repository.addThreadToIndex(savedThread(threadId = "999", boardId = "b")).getOrThrow()
            repository.addThreadToIndex(savedThread(threadId = "999", boardId = "img")).getOrThrow()
            val harness = RouterHarness(savedThreadRepository = repository)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(
                    action = FutachaAiAction.DeleteSavedThread,
                    parameters = mapOf("thread" to "999")
                ),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Failed>(outcome)
            assertEquals(2, repository.getAllThreads().size)
        }
    }

    @Test
    fun confirmedClearSavedThreadsClearsManualRepository() {
        runBlocking {
            val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "manual")
            repository.addThreadToIndex(savedThread(threadId = "123", boardId = "b")).getOrThrow()
            repository.addThreadToIndex(savedThread(threadId = "456", boardId = "b")).getOrThrow()
            val harness = RouterHarness(savedThreadRepository = repository)

            val outcome = executeFutachaAiCommand(
                command = FutachaAiCommand(FutachaAiAction.ClearSavedThreads),
                inputs = harness.inputs(),
                confirmed = true
            )

            assertIs<FutachaAiCommandOutcome.Completed>(outcome)
            assertEquals(emptyList(), repository.getAllThreads())
        }
    }

    private class RouterHarness(
        val stateStore: AppStateStore = AppStateStore(FakePlatformStateStorage()),
        var navigationState: FutachaNavigationState = FutachaNavigationState(),
        val isAiCommandEnabled: Boolean = true,
        val savedThreadRepository: SavedThreadRepository? = null,
        val autoSavedThreadRepository: SavedThreadRepository? = null,
        val isCookieManagementAvailable: Boolean = true,
        val boards: List<BoardSummary> = listOf(
            BoardSummary(
                id = "b",
                name = "二次元裏",
                category = "futaba",
                url = "https://may.2chan.net/b/",
                description = "test board"
            )
        ),
        val history: List<ThreadHistoryEntry> = listOf(
            ThreadHistoryEntry(
                threadId = "123",
                boardId = "b",
                title = "テストスレ",
                titleImageUrl = "",
                boardName = "二次元裏",
                boardUrl = "https://may.2chan.net/b/",
                lastVisitedEpochMillis = 1L,
                replyCount = 10
            )
        )
    ) {
        fun inputs(): FutachaAiRouterInputs {
            return FutachaAiRouterInputs(
                stateStore = stateStore,
                boards = boards,
                history = history,
                navigationState = navigationState,
                updateNavigationState = { navigationState = it },
                historyRefresher = null,
                savedThreadRepository = savedThreadRepository,
                autoSavedThreadRepository = autoSavedThreadRepository,
                isCookieManagementAvailable = isCookieManagementAvailable,
                appVersion = "test",
                isAiCommandEnabled = isAiCommandEnabled
            )
        }
    }

    private fun board(
        id: String = "b",
        url: String = "https://may.2chan.net/b/"
    ): BoardSummary {
        return BoardSummary(
            id = id,
            name = if (id == "b") "二次元裏" else "二次元裏 $id",
            category = "futaba",
            url = url,
            description = "test board"
        )
    }

    private fun historyEntry(
        threadId: String = "123",
        boardId: String = "b",
        boardUrl: String = "https://may.2chan.net/b/"
    ): ThreadHistoryEntry {
        return ThreadHistoryEntry(
            threadId = threadId,
            boardId = boardId,
            title = "テストスレ",
            titleImageUrl = "",
            boardName = if (boardId == "b") "二次元裏" else "二次元裏 $boardId",
            boardUrl = boardUrl,
            lastVisitedEpochMillis = 1L,
            replyCount = 10
        )
    }

    private fun savedThread(threadId: String, boardId: String): SavedThread {
        return SavedThread(
            threadId = threadId,
            boardId = boardId,
            boardName = "board",
            title = "title-$threadId",
            storageId = buildThreadStorageId(boardId, threadId),
            thumbnailPath = null,
            savedAt = 1L,
            postCount = 1,
            imageCount = 0,
            videoCount = 0,
            totalSize = 1L,
            status = SaveStatus.COMPLETED
        )
    }
}
