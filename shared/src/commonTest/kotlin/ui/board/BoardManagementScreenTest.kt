package com.valoser.futacha.shared.ui.board

import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHostState
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardManagementScreenTest {
    @Test
    fun incrementSaidaneLabel_handlesNullOrBlank() {
        assertEquals("そうだねx1", incrementSaidaneLabel(null))
        assertEquals("そうだねx1", incrementSaidaneLabel(""))
        assertEquals("そうだねx1", incrementSaidaneLabel("+"))
    }

    @Test
    fun incrementSaidaneLabel_incrementsNumericSuffix() {
        assertEquals("そうだねx2", incrementSaidaneLabel("そうだねx1"))
        assertEquals("そうだねx6", incrementSaidaneLabel("そうだねx5"))
    }

    @Test
    fun buildAddBoardValidationState_validatesUrlSchemeDuplicateAndSubmitState() {
        val existingBoards = listOf(
            board(name = "img", url = "https://may.2chan.net/img/futaba.php")
        )

        val missingScheme = buildAddBoardValidationState(
            name = "画像",
            url = "may.2chan.net/img",
            existingBoards = existingBoards
        )
        assertFalse(missingScheme.canSubmit)
        assertEquals("http:// もしくは https:// から始まるURLを入力してください", missingScheme.helperText)

        val duplicate = buildAddBoardValidationState(
            name = "画像",
            url = "https://may.2chan.net/img",
            existingBoards = existingBoards
        )
        assertFalse(duplicate.canSubmit)
        assertEquals("同じURLの板が既に登録されています", duplicate.helperText)

        val valid = buildAddBoardValidationState(
            name = "画像",
            url = "https://may.2chan.net/dat",
            existingBoards = existingBoards
        )
        assertTrue(valid.canSubmit)
        assertEquals("https://may.2chan.net/dat/futaba.php", valid.normalizedInputUrl)
        assertEquals(null, valid.helperText)

        val localhost = buildAddBoardValidationState(
            name = "ローカル",
            url = "http://localhost/test",
            existingBoards = emptyList()
        )
        assertTrue(localhost.canSubmit)

        val invalid = buildAddBoardValidationState(
            name = "壊れたURL",
            url = "https://bad host",
            existingBoards = emptyList()
        )
        assertFalse(invalid.canSubmit)
        assertEquals("有効なURLを入力してください（例: https://example.com/board）", invalid.helperText)
    }

    @Test
    fun createCustomBoardSummary_generatesStableIdsFromUrlAndName() {
        val existingBoards = listOf(
            board(id = "img", name = "既存", url = "https://may.2chan.net/img/futaba.php"),
            board(id = "newboard", name = "既存2", url = "https://jun.2chan.net/newboard/futaba.php")
        )

        val fromPath = createCustomBoardSummary(
            name = "画像",
            url = "https://may.2chan.net/dat/futaba.php",
            existingBoards = existingBoards
        )
        val fallbackName = createCustomBoardSummary(
            name = "New Board!",
            url = "https://example.invalid",
            existingBoards = existingBoards
        )

        assertEquals("dat", fromPath.id)
        assertEquals("画像", fromPath.name)
        assertEquals("New Board!", fallbackName.name)
        assertEquals("newboard1", fallbackName.id)
    }

    @Test
    fun moveBoardSummary_movesWithinBoundsAndKeepsOutOfRangeUntouched() {
        val boards = listOf(
            board(id = "a", name = "A"),
            board(id = "b", name = "B"),
            board(id = "c", name = "C")
        )

        assertEquals(listOf("b", "a", "c"), moveBoardSummary(boards, index = 1, moveUp = true).map { it.id })
        assertEquals(listOf("a", "c", "b"), moveBoardSummary(boards, index = 1, moveUp = false).map { it.id })
        assertEquals(listOf("a", "b", "c"), moveBoardSummary(boards, index = 0, moveUp = true).map { it.id })
        assertEquals(listOf("a", "b", "c"), moveBoardSummary(boards, index = 2, moveUp = false).map { it.id })
        assertEquals(listOf("a", "b", "c"), moveBoardSummary(boards, index = 9, moveUp = true).map { it.id })
    }

    @Test
    fun shouldShowBoardManagementScreenAction_skipsSavedThreadsOnly() {
        assertFalse(shouldShowBoardManagementScreenAction(BoardManagementMenuAction.SAVED_THREADS))
        assertTrue(shouldShowBoardManagementScreenAction(BoardManagementMenuAction.ADD))
        assertTrue(shouldShowBoardManagementScreenAction(BoardManagementMenuAction.SETTINGS))
    }

    @Test
    fun resolveBoardManagementChromeState_mapsTitleAndBackButton() {
        assertEquals(
            BoardManagementChromeState(
                title = "ふたば",
                showsBackButton = false
            ),
            resolveBoardManagementChromeState(
                isDeleteMode = false,
                isReorderMode = false
            )
        )
        assertEquals(
            BoardManagementChromeState(
                title = "削除する板を選択",
                showsBackButton = true
            ),
            resolveBoardManagementChromeState(
                isDeleteMode = true,
                isReorderMode = false
            )
        )
        assertEquals(
            BoardManagementChromeState(
                title = "板の順序を変更",
                showsBackButton = true
            ),
            resolveBoardManagementChromeState(
                isDeleteMode = false,
                isReorderMode = true
            )
        )
    }

    @Test
    fun clearBoardManagementEditModes_resetsBothFlags() {
        assertEquals(
            BoardManagementEditModeState(
                isDeleteMode = false,
                isReorderMode = false
            ),
            clearBoardManagementEditModes()
        )
    }

    @Test
    fun resolveBoardManagementMenuActionState_togglesModesAndRoutesDialogs() {
        assertEquals(
            BoardManagementMenuActionState(
                editModeState = BoardManagementEditModeState(
                    isDeleteMode = false,
                    isReorderMode = false
                ),
                shouldShowAddDialog = true,
                shouldShowGlobalSettings = false,
                shouldHandleInternally = true
            ),
            resolveBoardManagementMenuActionState(
                isDeleteMode = false,
                isReorderMode = false,
                action = BoardManagementMenuAction.ADD
            )
        )
        assertEquals(
            BoardManagementMenuActionState(
                editModeState = BoardManagementEditModeState(
                    isDeleteMode = true,
                    isReorderMode = false
                ),
                shouldShowAddDialog = false,
                shouldShowGlobalSettings = false,
                shouldHandleInternally = true
            ),
            resolveBoardManagementMenuActionState(
                isDeleteMode = false,
                isReorderMode = true,
                action = BoardManagementMenuAction.DELETE
            )
        )
        assertEquals(
            BoardManagementMenuActionState(
                editModeState = BoardManagementEditModeState(
                    isDeleteMode = false,
                    isReorderMode = false
                ),
                shouldShowAddDialog = false,
                shouldShowGlobalSettings = false,
                shouldHandleInternally = true
            ),
            resolveBoardManagementMenuActionState(
                isDeleteMode = false,
                isReorderMode = true,
                action = BoardManagementMenuAction.REORDER
            )
        )
        assertEquals(
            BoardManagementMenuActionState(
                editModeState = BoardManagementEditModeState(
                    isDeleteMode = true,
                    isReorderMode = false
                ),
                shouldShowAddDialog = false,
                shouldShowGlobalSettings = true,
                shouldHandleInternally = true
            ),
            resolveBoardManagementMenuActionState(
                isDeleteMode = true,
                isReorderMode = false,
                action = BoardManagementMenuAction.SETTINGS
            )
        )
        assertEquals(
            BoardManagementMenuActionState(
                editModeState = BoardManagementEditModeState(
                    isDeleteMode = false,
                    isReorderMode = true
                ),
                shouldShowAddDialog = false,
                shouldShowGlobalSettings = false,
                shouldHandleInternally = false
            ),
            resolveBoardManagementMenuActionState(
                isDeleteMode = false,
                isReorderMode = true,
                action = BoardManagementMenuAction.SAVED_THREADS
            )
        )
    }

    @Test
    fun resolveBoardManagementBackAction_prioritizesDrawerThenEditModes() {
        assertEquals(
            BoardManagementBackAction.CLOSE_DRAWER,
            resolveBoardManagementBackAction(
                isDrawerOpen = true,
                isDeleteMode = true,
                isReorderMode = true
            )
        )
        assertEquals(
            BoardManagementBackAction.CLEAR_EDIT_MODES,
            resolveBoardManagementBackAction(
                isDrawerOpen = false,
                isDeleteMode = true,
                isReorderMode = false
            )
        )
        assertEquals(
            BoardManagementBackAction.CLEAR_EDIT_MODES,
            resolveBoardManagementBackAction(
                isDrawerOpen = false,
                isDeleteMode = false,
                isReorderMode = true
            )
        )
        assertEquals(
            BoardManagementBackAction.NONE,
            resolveBoardManagementBackAction(
                isDrawerOpen = false,
                isDeleteMode = false,
                isReorderMode = false
            )
        )
    }

    @Test
    fun resolveBoardManagementDrawerOpenState_checksCurrentAndTargetValues() {
        assertTrue(
            resolveBoardManagementDrawerOpenState(
                currentValue = DrawerValue.Open,
                targetValue = DrawerValue.Closed
            )
        )
        assertTrue(
            resolveBoardManagementDrawerOpenState(
                currentValue = DrawerValue.Closed,
                targetValue = DrawerValue.Open
            )
        )
        assertFalse(
            resolveBoardManagementDrawerOpenState(
                currentValue = DrawerValue.Closed,
                targetValue = DrawerValue.Closed
            )
        )
    }

    @Test
    fun boardManagementOverlayHelpers_updateDialogAndSettingsVisibility() {
        val base = BoardManagementOverlayState()
        val board = board(name = "target")

        assertEquals(
            BoardManagementOverlayState(isAddDialogVisible = true),
            openBoardManagementAddDialog(base)
        )
        assertEquals(
            base,
            dismissBoardManagementAddDialog(openBoardManagementAddDialog(base))
        )
        assertEquals(
            BoardManagementOverlayState(boardToDelete = board),
            openBoardManagementDeleteDialog(base, board)
        )
        assertEquals(
            base,
            dismissBoardManagementDeleteDialog(openBoardManagementDeleteDialog(base, board))
        )
        assertEquals(
            BoardManagementOverlayState(isGlobalSettingsVisible = true),
            openBoardManagementGlobalSettings(base)
        )
        assertEquals(
            base,
            closeBoardManagementGlobalSettings(openBoardManagementGlobalSettings(base))
        )
        assertEquals(
            BoardManagementOverlayState(
                isGlobalSettingsVisible = false,
                isCookieManagementVisible = true
            ),
            openBoardManagementCookieManagement(
                BoardManagementOverlayState(isGlobalSettingsVisible = true)
            )
        )
        assertEquals(
            base,
            closeBoardManagementCookieManagement(
                BoardManagementOverlayState(isCookieManagementVisible = true)
            )
        )
    }

    @Test
    fun boardManagementBindingsSupport_buildsHistoryMessages() {
        assertEquals("履歴を更新しました", buildBoardManagementHistoryRefreshSuccessMessage())
        assertEquals("履歴更新はすでに実行中です", buildBoardManagementHistoryRefreshBusyMessage())
        assertEquals(
            "履歴の更新に失敗しました: boom",
            buildBoardManagementHistoryRefreshFailureMessage(IllegalStateException("boom"))
        )
        assertEquals("履歴を一括削除しました", buildBoardManagementHistoryBatchDeleteMessage())
        assertEquals("\"img\" を追加しました", buildBoardManagementAddBoardSuccessMessage("img"))
        assertEquals("\"img\" を削除しました", buildBoardManagementDeleteBoardSuccessMessage(board()))
    }

    @Test
    fun boardManagementBindingsSupport_menuCallbacks_routeEditModesAndDialogs() = runBlocking {
        var openedDrawer = false
        val forwarded = mutableListOf<BoardManagementMenuAction>()
        var isDeleteMode = true
        var isReorderMode = true
        var isAddDialogVisible = false
        var isGlobalSettingsVisible = false
        val callbacks = buildBoardManagementMenuActionCallbacks(
            coroutineScope = this,
            openDrawer = { openedDrawer = true },
            onExternalMenuAction = { forwarded += it },
            currentIsDeleteMode = { isDeleteMode },
            currentIsReorderMode = { isReorderMode },
            setIsDeleteMode = { isDeleteMode = it },
            setIsReorderMode = { isReorderMode = it },
            setIsAddDialogVisible = { isAddDialogVisible = it },
            setIsGlobalSettingsVisible = { isGlobalSettingsVisible = it }
        )

        callbacks.onBackClick()
        callbacks.onNavigationClick()
        yield()
        callbacks.onMenuActionSelected(BoardManagementMenuAction.ADD)
        callbacks.onMenuActionSelected(BoardManagementMenuAction.SETTINGS)

        assertFalse(isDeleteMode)
        assertFalse(isReorderMode)
        assertTrue(openedDrawer)
        assertEquals(
            listOf(BoardManagementMenuAction.ADD, BoardManagementMenuAction.SETTINGS),
            forwarded
        )
        assertTrue(isAddDialogVisible)
        assertTrue(isGlobalSettingsVisible)
    }

    @Test
    fun boardManagementBindingsSupport_historyDrawerCallbacks_refreshDeleteAndSelect() = runBlocking {
        var drawerClosedCount = 0
        var selectedThreadId: String? = null
        var refreshCount = 0
        var clearedCount = 0
        var snackbarMessage: String? = null
        var isHistoryRefreshing = false
        val callbacks = buildBoardManagementHistoryDrawerCallbacks(
            coroutineScope = this,
            closeDrawer = { drawerClosedCount += 1 },
            onHistoryEntrySelected = { selectedThreadId = it.threadId },
            onHistoryRefresh = { refreshCount += 1 },
            onHistoryCleared = { clearedCount += 1 },
            showSnackbar = { snackbarMessage = it },
            currentIsHistoryRefreshing = { isHistoryRefreshing },
            setIsHistoryRefreshing = { isHistoryRefreshing = it }
        )
        val entry = ThreadHistoryEntry(
            threadId = "123",
            title = "title",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://may.2chan.net/b/futaba.php",
            lastVisitedEpochMillis = 0L,
            replyCount = 1
        )

        callbacks.onHistoryEntrySelected(entry)
        callbacks.onRefreshClick()
        callbacks.onBatchDeleteClick()
        yield()
        yield()

        assertEquals("123", selectedThreadId)
        assertEquals(1, refreshCount)
        assertEquals(1, clearedCount)
        assertEquals("履歴を一括削除しました", snackbarMessage)
        assertFalse(isHistoryRefreshing)
        assertEquals(2, drawerClosedCount)
    }

    @Test
    fun boardManagementBindingsSupport_dialogCallbacks_submitAndDelete() = runBlocking {
        val added = mutableListOf<Pair<String, String>>()
        var deletedBoard: BoardSummary? = null
        var isAddDialogVisible = true
        var clearedDeleteTarget = false
        var snackbarMessage: String? = null
        val callbacks = buildBoardManagementDialogCallbacks(
            coroutineScope = this,
            onAddBoard = { name, url -> added += name to url },
            onBoardDeleted = { deletedBoard = it },
            setIsAddDialogVisible = { isAddDialogVisible = it },
            clearBoardToDelete = { clearedDeleteTarget = true },
            showSnackbar = { snackbarMessage = it }
        )
        val targetBoard = board(name = "削除対象")

        callbacks.onAddBoardSubmitted("img", "https://may.2chan.net/img/futaba.php")
        yield()
        assertEquals(listOf("img" to "https://may.2chan.net/img/futaba.php"), added)
        assertFalse(isAddDialogVisible)
        assertEquals("\"img\" を追加しました", snackbarMessage)

        callbacks.onDeleteBoardConfirmed(targetBoard)
        yield()
        assertEquals(targetBoard, deletedBoard)
        assertTrue(clearedDeleteTarget)
        assertEquals("\"削除対象\" を削除しました", snackbarMessage)
    }

    @Test
    fun boardManagementRuntimeSupport_lifecycleBindings_routeBackAction() = runBlocking {
        var closedDrawer = false
        var clearedEditModes = false
        var backAction = BoardManagementBackAction.NONE
        val bindings = buildBoardManagementLifecycleBindings(
            coroutineScope = this,
            currentBackAction = { backAction },
            closeDrawer = { closedDrawer = true },
            clearEditModes = { clearedEditModes = true }
        )

        assertEquals(BoardManagementBackAction.NONE, bindings.backAction)

        backAction = BoardManagementBackAction.CLOSE_DRAWER
        bindings.onBack()
        yield()
        assertTrue(closedDrawer)

        backAction = BoardManagementBackAction.CLEAR_EDIT_MODES
        bindings.onBack()
        assertTrue(clearedEditModes)
    }

    @Test
    fun performBoardManagementBackAction_routesDrawerAndEditModes() = runBlocking {
        var closedDrawer = false
        var clearedEditModes = false

        performBoardManagementBackAction(
            action = BoardManagementBackAction.NONE,
            coroutineScope = this,
            closeDrawer = { closedDrawer = true },
            clearEditModes = { clearedEditModes = true }
        )
        yield()
        assertFalse(closedDrawer)
        assertFalse(clearedEditModes)

        performBoardManagementBackAction(
            action = BoardManagementBackAction.CLOSE_DRAWER,
            coroutineScope = this,
            closeDrawer = { closedDrawer = true },
            clearEditModes = { clearedEditModes = true }
        )
        yield()
        assertTrue(closedDrawer)

        performBoardManagementBackAction(
            action = BoardManagementBackAction.CLEAR_EDIT_MODES,
            coroutineScope = this,
            closeDrawer = { closedDrawer = false },
            clearEditModes = { clearedEditModes = true }
        )
        assertTrue(clearedEditModes)
    }

    @Test
    fun boardManagementInteractionBindings_bundle_routesOverlayCallbacks() = runBlocking {
        var overlayState = BoardManagementOverlayState()
        var deletedBoard: BoardSummary? = null
        var isMenuExpanded = false
        var selectedBoard: BoardSummary? = null
        var reorderedBoards: List<BoardSummary>? = null
        val bundle = buildBoardManagementInteractionBindingsBundle(
            historyInputs = BoardManagementHistoryInteractionInputs(
                coroutineScope = this,
                closeDrawer = {},
                openDrawer = {},
                onExternalMenuAction = {},
                onHistoryEntrySelected = {},
                onHistoryRefresh = {},
                onHistoryCleared = {},
                showSnackbar = {}
            ),
            stateInputs = BoardManagementStateInteractionInputs(
                currentIsDeleteMode = { false },
                currentIsReorderMode = { false },
                currentIsHistoryRefreshing = { false },
                setIsDeleteMode = {},
                setIsReorderMode = {},
                setIsHistoryRefreshing = {},
                currentIsMenuExpanded = { isMenuExpanded },
                setIsMenuExpanded = { isMenuExpanded = it }
            ),
            overlayInputs = BoardManagementOverlayInteractionInputs(
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                hasCookieRepository = true
            ),
            boardInputs = BoardManagementBoardInteractionInputs(
                onAddBoard = { _, _ -> },
                onBoardDeleted = { deletedBoard = it },
                onBoardSelected = { selectedBoard = it },
                onBoardsReordered = { reorderedBoards = it }
            )
        )
        val board = board(name = "delete")
        val boards = listOf(board(id = "a", name = "A"), board(id = "b", name = "B"))

        bundle.onHistorySettingsClick()
        assertTrue(overlayState.isGlobalSettingsVisible)

        bundle.onGlobalSettingsBack()
        assertFalse(overlayState.isGlobalSettingsVisible)

        bundle.onDeleteRequested(board)
        assertEquals(board, overlayState.boardToDelete)

        bundle.onDismissDeleteDialog()
        assertEquals(null, overlayState.boardToDelete)

        bundle.onDismissAddDialog()
        assertFalse(overlayState.isAddDialogVisible)

        bundle.topBarCallbacks.onOpenMenu()
        assertTrue(isMenuExpanded)
        bundle.topBarCallbacks.onDismissMenu()
        assertFalse(isMenuExpanded)

        bundle.onOpenCookieManagement?.invoke()
        assertTrue(overlayState.isCookieManagementVisible)
        assertFalse(overlayState.isGlobalSettingsVisible)

        bundle.onCookieManagementBack()
        assertFalse(overlayState.isCookieManagementVisible)

        bundle.dialogCallbacks.onDeleteBoardConfirmed(board)
        yield()
        assertEquals(board, deletedBoard)

        bundle.boardListCallbacks.onBoardClick(board)
        assertEquals(board, selectedBoard)

        bundle.boardListCallbacks.onDeleteClick(board)
        assertEquals(board, overlayState.boardToDelete)

        bundle.boardListCallbacks.onMoveDown(boards, 0)
        assertEquals(listOf("b", "a"), reorderedBoards?.map { it.id })
    }

    @Test
    fun boardManagementBindings_contractsStayAlignedAcrossHosts() = runBlocking {
        val drawerState = DrawerState(initialValue = DrawerValue.Closed)
        val snackbarHostState = SnackbarHostState()
        val boards = listOf(board(id = "a", name = "A"))
        val history = listOf(
            ThreadHistoryEntry(
                threadId = "123",
                title = "title",
                titleImageUrl = "",
                boardName = "board",
                boardUrl = "https://may.2chan.net/b/futaba.php",
                lastVisitedEpochMillis = 0L,
                replyCount = 1
            )
        )
        val preferencesState = ScreenPreferencesState(appVersion = "1.0.0")
        val preferencesCallbacks = ScreenPreferencesCallbacks()
        val interactionBindings = buildBoardManagementInteractionBindingsBundle(
            historyInputs = BoardManagementHistoryInteractionInputs(
                coroutineScope = this,
                closeDrawer = {},
                openDrawer = {},
                onExternalMenuAction = {},
                onHistoryEntrySelected = {},
                onHistoryRefresh = {},
                onHistoryCleared = {},
                showSnackbar = {}
            ),
            stateInputs = BoardManagementStateInteractionInputs(
                currentIsDeleteMode = { false },
                currentIsReorderMode = { true },
                currentIsHistoryRefreshing = { false },
                setIsDeleteMode = {},
                setIsReorderMode = {},
                setIsHistoryRefreshing = {},
                currentIsMenuExpanded = { true },
                setIsMenuExpanded = {}
            ),
            overlayInputs = BoardManagementOverlayInteractionInputs(
                currentOverlayState = { BoardManagementOverlayState(isGlobalSettingsVisible = true) },
                setOverlayState = {},
                hasCookieRepository = false
            ),
            boardInputs = BoardManagementBoardInteractionInputs(
                onAddBoard = { _, _ -> },
                onBoardDeleted = {},
                onBoardSelected = {},
                onBoardsReordered = {}
            )
        )
        val onHistoryDismissed: (ThreadHistoryEntry) -> Unit = {}
        val onDismissDrawerTap = {}

        val scaffoldBindings = BoardManagementScaffoldBindings(
            history = history,
            boards = boards,
            isDeleteMode = false,
            isReorderMode = true,
            isDrawerOpen = false,
            chromeState = resolveBoardManagementChromeState(
                isDeleteMode = false,
                isReorderMode = true
            ),
            isMenuExpanded = true,
            drawerState = drawerState,
            snackbarHostState = snackbarHostState,
            onHistoryEntryDismissed = onHistoryDismissed,
            onDismissDrawerTap = onDismissDrawerTap,
            historyDrawerCallbacks = interactionBindings.historyDrawerCallbacks,
            topBarCallbacks = interactionBindings.topBarCallbacks,
            boardListCallbacks = interactionBindings.boardListCallbacks,
            onHistorySettingsClick = interactionBindings.onHistorySettingsClick
        )
        val overlayBindings = BoardManagementOverlayBindings(
            boards = boards,
            history = history,
            overlayState = BoardManagementOverlayState(isGlobalSettingsVisible = true),
            preferencesState = preferencesState,
            preferencesCallbacks = preferencesCallbacks,
            autoSavedThreadRepository = null,
            fileSystem = null,
            cookieRepository = null,
            onDismissAddDialog = interactionBindings.onDismissAddDialog,
            onAddBoardSubmitted = interactionBindings.dialogCallbacks.onAddBoardSubmitted,
            onDismissDeleteDialog = interactionBindings.onDismissDeleteDialog,
            onDeleteBoardConfirmed = interactionBindings.dialogCallbacks.onDeleteBoardConfirmed,
            onGlobalSettingsBack = interactionBindings.onGlobalSettingsBack,
            onOpenCookieManagement = interactionBindings.onOpenCookieManagement,
            onCookieManagementBack = interactionBindings.onCookieManagementBack
        )

        assertEquals(boards, scaffoldBindings.boards)
        assertEquals(history, scaffoldBindings.history)
        assertTrue(scaffoldBindings.isReorderMode)
        assertTrue(scaffoldBindings.isMenuExpanded)
        assertTrue(scaffoldBindings.topBarCallbacks === interactionBindings.topBarCallbacks)
        assertTrue(scaffoldBindings.boardListCallbacks === interactionBindings.boardListCallbacks)
        assertTrue(scaffoldBindings.historyDrawerCallbacks === interactionBindings.historyDrawerCallbacks)
        assertTrue(scaffoldBindings.onHistoryEntryDismissed === onHistoryDismissed)
        assertTrue(scaffoldBindings.onDismissDrawerTap === onDismissDrawerTap)
        assertTrue(scaffoldBindings.drawerState === drawerState)
        assertTrue(scaffoldBindings.snackbarHostState === snackbarHostState)
        assertEquals(boards, overlayBindings.boards)
        assertEquals(history, overlayBindings.history)
        assertTrue(overlayBindings.overlayState.isGlobalSettingsVisible)
        assertEquals(preferencesState, overlayBindings.preferencesState)
        assertEquals(preferencesCallbacks, overlayBindings.preferencesCallbacks)
        assertTrue(overlayBindings.onAddBoardSubmitted === interactionBindings.dialogCallbacks.onAddBoardSubmitted)
        assertTrue(overlayBindings.onGlobalSettingsBack === interactionBindings.onGlobalSettingsBack)
    }

    private fun board(
        id: String = "img",
        name: String = "img",
        url: String = "https://may.2chan.net/img/futaba.php"
    ): BoardSummary {
        return BoardSummary(
            id = id,
            name = name,
            category = "",
            url = url,
            description = ""
        )
    }
}
