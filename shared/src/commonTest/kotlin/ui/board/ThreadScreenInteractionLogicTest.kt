package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedPost
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.SavedThreadMetadata
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.ArchiveSearchScope
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.service.SavedMediaFile
import com.valoser.futacha.shared.service.SavedMediaType
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.SaveDirectorySelection
import io.ktor.client.HttpClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadScreenInteractionLogicTest {
    @Test
    fun threadScreenBindingSupport_wrapsHistoryGalleryAndQuotePreviewCallbacks() {
        var dismissedEntry: ThreadHistoryEntry? = null
        var selectedEntry: ThreadHistoryEntry? = null
        var boardClicked = false
        var refreshed = false
        var batchDeleted = false
        var settingsOpened = false
        val entry = ThreadHistoryEntry(
            threadId = "1",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 0
        )
        val historyCallbacks = buildThreadScreenHistoryDrawerCallbacks(
            onHistoryEntryDismissed = { dismissedEntry = it },
            onHistoryEntrySelected = { selectedEntry = it },
            onBoardClick = { boardClicked = true },
            onRefreshClick = { refreshed = true },
            onBatchDeleteClick = { batchDeleted = true },
            onSettingsClick = { settingsOpened = true }
        )
        historyCallbacks.onHistoryEntryDismissed(entry)
        historyCallbacks.onHistoryEntrySelected(entry)
        historyCallbacks.onBoardClick()
        historyCallbacks.onRefreshClick()
        historyCallbacks.onBatchDeleteClick()
        historyCallbacks.onSettingsClick()
        assertEquals(entry, dismissedEntry)
        assertEquals(entry, selectedEntry)
        assertTrue(boardClicked)
        assertTrue(refreshed)
        assertTrue(batchDeleted)
        assertTrue(settingsOpened)

        var scrolledIndex: Int? = null
        var galleryDismissed = false
        val posts = listOf(
            Post(id = "1", author = null, subject = null, timestamp = "now", messageHtml = "a", imageUrl = null, thumbnailUrl = null),
            Post(id = "2", author = null, subject = null, timestamp = "now", messageHtml = "b", imageUrl = null, thumbnailUrl = null)
        )
        val galleryCallbacks = buildThreadScreenGalleryCallbacks(
            currentPosts = posts,
            onDismiss = { galleryDismissed = true },
            onScrollToPostIndex = { scrolledIndex = it }
        )
        galleryCallbacks.onImageClick(posts[1])
        galleryCallbacks.onDismiss()
        assertEquals(1, scrolledIndex)
        assertTrue(galleryDismissed)

        var quoteDismissed = false
        var shownQuote: Pair<String, List<Post>>? = null
        val postIndex = posts.associateBy { it.id }
        val quoteCallbacks = buildThreadScreenQuotePreviewCallbacks(
            postIndex = postIndex,
            onDismiss = { quoteDismissed = true },
            onShowQuotePreview = { text, targets -> shownQuote = text to targets }
        )
        val quoteReference = QuoteReference(text = ">>1", targetPostIds = listOf("1"))
        quoteCallbacks.onQuoteClick(quoteReference)
        quoteCallbacks.onDismiss()
        assertEquals(">>1" to listOf(posts[0]), shownQuote)
        assertTrue(quoteDismissed)
    }

    @Test
    fun threadScreenBindingSupport_wrapsQuotePreviewPresenterAndMediaPreviewCallbacks() {
        var quotePreviewState: QuotePreviewState? = null
        val quotePresenter = buildThreadScreenQuotePreviewPresenter(
            isScrolling = { false },
            posterIdLabels = emptyMap(),
            setState = { quotePreviewState = it }
        )
        val targets = listOf(
            Post(id = "1", author = null, subject = null, timestamp = "now", messageHtml = "body", imageUrl = null, thumbnailUrl = null)
        )
        quotePresenter(">>1", targets)
        assertEquals(">>1", quotePreviewState?.quoteText)
        assertEquals(targets, quotePreviewState?.targetPosts)

        var mediaPreviewState = ThreadMediaPreviewState(previewMediaIndex = 0)
        var savedEntry: MediaPreviewEntry? = null
        val mediaCallbacks = buildThreadScreenMediaPreviewDialogCallbacks(
            mediaPreviewState = { mediaPreviewState },
            setMediaPreviewState = { mediaPreviewState = it },
            totalCount = 2,
            onSave = { savedEntry = it }
        )
        val mediaEntry = MediaPreviewEntry(
            url = "https://example.com/a.png",
            mediaType = MediaType.Image,
            postId = "1",
            title = "a"
        )
        mediaCallbacks.onNavigateNext()
        assertEquals(1, mediaPreviewState.previewMediaIndex)
        mediaCallbacks.onNavigatePrevious()
        assertEquals(0, mediaPreviewState.previewMediaIndex)
        mediaCallbacks.onSave(mediaEntry)
        assertEquals(mediaEntry, savedEntry)
        mediaCallbacks.onDismiss()
        assertNull(mediaPreviewState.previewMediaIndex)
    }

    @Test
    fun threadScreenBindingSupport_wrapsHistorySelectionAndMenuEntryHandlers() {
        var closedDrawer = false
        var selectedEntry: ThreadHistoryEntry? = null
        val entry = ThreadHistoryEntry(
            threadId = "1",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 0
        )
        val historySelectionHandler = buildThreadScreenHistorySelectionHandler(
            onCloseDrawer = { closedDrawer = true },
            onHistoryEntrySelected = { selectedEntry = it }
        )
        historySelectionHandler(entry)
        assertTrue(closedDrawer)
        assertEquals(entry, selectedEntry)

        var openedReply = false
        var scrolledTop = false
        var scrolledBottom = false
        var refreshBusyShown = false
        var startedRefresh = false
        var openedGallery = false
        var delegatedSave = false
        var filterShown = false
        var settingsShown = false
        var ngHeaderCleared = false
        var ngManagementShown = false
        var externalOpened = false
        var readAloudShown = false
        var privacyToggled = false
        val menuEntryHandler = buildThreadScreenMenuEntryHandler(
            isRefreshing = { false },
            onOpenReplyDialog = { openedReply = true },
            onScrollTop = { scrolledTop = true },
            onScrollBottom = { scrolledBottom = true },
            onShowRefreshBusyMessage = { refreshBusyShown = true },
            onStartRefresh = { startedRefresh = true },
            onOpenGallery = { openedGallery = true },
            onDelegateToSaveHandler = { delegatedSave = true },
            onShowFilterSheet = { filterShown = true },
            onShowSettingsSheet = { settingsShown = true },
            onClearNgHeaderPrefill = { ngHeaderCleared = true },
            onShowNgManagement = { ngManagementShown = true },
            onOpenExternalApp = { externalOpened = true },
            onShowReadAloudControls = { readAloudShown = true },
            onTogglePrivacy = { privacyToggled = true }
        )
        menuEntryHandler(ThreadMenuEntryId.Reply)
        menuEntryHandler(ThreadMenuEntryId.ScrollToTop)
        menuEntryHandler(ThreadMenuEntryId.ScrollToBottom)
        menuEntryHandler(ThreadMenuEntryId.Refresh)
        menuEntryHandler(ThreadMenuEntryId.Gallery)
        menuEntryHandler(ThreadMenuEntryId.Save)
        menuEntryHandler(ThreadMenuEntryId.Filter)
        menuEntryHandler(ThreadMenuEntryId.Settings)
        menuEntryHandler(ThreadMenuEntryId.NgManagement)
        menuEntryHandler(ThreadMenuEntryId.ExternalApp)
        menuEntryHandler(ThreadMenuEntryId.ReadAloud)
        menuEntryHandler(ThreadMenuEntryId.Privacy)

        assertTrue(openedReply)
        assertTrue(scrolledTop)
        assertTrue(scrolledBottom)
        assertFalse(refreshBusyShown)
        assertTrue(startedRefresh)
        assertTrue(openedGallery)
        assertTrue(delegatedSave)
        assertTrue(filterShown)
        assertTrue(settingsShown)
        assertTrue(ngHeaderCleared)
        assertTrue(ngManagementShown)
        assertTrue(externalOpened)
        assertTrue(readAloudShown)
        assertTrue(privacyToggled)

        val busyMenuEntryHandler = buildThreadScreenMenuEntryHandler(
            isRefreshing = { true },
            onOpenReplyDialog = {},
            onScrollTop = {},
            onScrollBottom = {},
            onShowRefreshBusyMessage = { refreshBusyShown = true },
            onStartRefresh = { startedRefresh = true },
            onOpenGallery = {},
            onDelegateToSaveHandler = {},
            onShowFilterSheet = {},
            onShowSettingsSheet = {},
            onClearNgHeaderPrefill = {},
            onShowNgManagement = {},
            onOpenExternalApp = {},
            onShowReadAloudControls = {},
            onTogglePrivacy = {}
        )
        refreshBusyShown = false
        startedRefresh = false
        busyMenuEntryHandler(ThreadMenuEntryId.Refresh)
        assertTrue(refreshBusyShown)
        assertFalse(startedRefresh)
    }

    @Test
    fun threadScreenBindingSupport_wrapsPostCardCallbacks() {
        val post = Post(
            id = "10",
            author = null,
            subject = null,
            timestamp = "now",
            posterId = "abc",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        val referencedPost = Post(
            id = "20",
            author = null,
            subject = null,
            timestamp = "now",
            messageHtml = "ref",
            imageUrl = null,
            thumbnailUrl = null
        )
        var shownQuote: Pair<String, List<Post>>? = null
        var quotedPost: Post? = null
        var saidanePost: Post? = null
        var longPressedPost: Post? = null
        val callbacks = buildThreadScreenPostCardCallbacks(
            post = post,
            normalizedPosterId = "abc",
            postIndex = mapOf("20" to referencedPost),
            referencedByMap = mapOf(post.id to listOf(referencedPost)),
            postsByPosterId = mapOf("abc" to listOf(post, referencedPost)),
            quotePreviewState = null,
            onShowQuotePreview = { text, targets -> shownQuote = text to targets },
            onQuoteRequestedForPost = { quotedPost = it },
            onSaidaneClick = { saidanePost = it },
            onMediaClick = null,
            onPostLongPress = { longPressedPost = it }
        )

        callbacks.onQuoteClick(QuoteReference(text = ">>20", targetPostIds = listOf("20")))
        assertEquals(">>20" to listOf(referencedPost), shownQuote)

        shownQuote = null
        callbacks.onPosterIdClick?.invoke()
        assertEquals("ID:abc のレス" to listOf(post, referencedPost), shownQuote)

        shownQuote = null
        callbacks.onReferencedByClick?.invoke()
        assertEquals(">>10 を引用したレス" to listOf(referencedPost), shownQuote)

        callbacks.onQuoteRequested()
        callbacks.onSaidaneClick()
        callbacks.onLongPress()
        assertEquals(post, quotedPost)
        assertEquals(post, saidanePost)
        assertEquals(post, longPressedPost)

        val blockedCallbacks = buildThreadScreenPostCardCallbacks(
            post = post,
            normalizedPosterId = null,
            postIndex = emptyMap(),
            referencedByMap = emptyMap(),
            postsByPosterId = emptyMap(),
            quotePreviewState = QuotePreviewState(
                quoteText = "x",
                targetPosts = listOf(post),
                posterIdLabels = emptyMap()
            ),
            onShowQuotePreview = { _, _ -> },
            onQuoteRequestedForPost = {},
            onSaidaneClick = {},
            onMediaClick = null,
            onPostLongPress = { longPressedPost = it }
        )
        longPressedPost = null
        blockedCallbacks.onLongPress()
        assertNull(longPressedPost)
        assertNull(blockedCallbacks.onPosterIdClick)
        assertNull(blockedCallbacks.onReferencedByClick)
    }

    @Test
    fun threadScreenBindingSupport_wrapsPostActionHandlers() = runBlocking {
        val post = Post(
            id = "10",
            author = null,
            subject = null,
            timestamp = "now",
            posterId = "abc",
            saidaneLabel = "1",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        var overlayState = openThreadPostActionOverlay(
            currentState = emptyThreadPostOverlayState(),
            post = post
        )
        val shownMessages = mutableListOf<String>()
        val launchedActions = mutableListOf<Pair<String, String>>()
        val votedPosts = mutableListOf<Post>()
        val deletedPosts = mutableListOf<Post>()
        val updatedLabels = mutableMapOf<String, String>()
        val handlers = buildThreadScreenPostActionHandlers(
            inputs = ThreadScreenPostActionInputs(
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                lastUsedDeleteKey = "stored",
                currentSaidaneLabel = { currentPost -> currentPost.saidaneLabel },
                isSelfPost = { false },
                onShowOptionalMessage = { message ->
                    if (message != null) {
                        shownMessages += message
                    }
                },
                onSaidaneLabelUpdated = { currentPost, updatedLabel ->
                    updatedLabels[currentPost.id] = updatedLabel
                },
                launchUnitAction = { successMessage, failurePrefix, onSuccess, block ->
                    launchedActions += successMessage to failurePrefix
                    runBlocking { block() }
                    onSuccess()
                },
                voteSaidane = { currentPost ->
                    votedPosts += currentPost
                },
                requestDeletion = { currentPost ->
                    deletedPosts += currentPost
                }
            )
        )

        handlers.onSaidane(post)
        assertTrue(overlayState.actionSheetState.targetPost == null)
        assertEquals("そうだねx2", updatedLabels["10"])
        assertEquals(listOf(post), votedPosts)

        overlayState = openThreadPostActionOverlay(emptyThreadPostOverlayState(), post)
        handlers.onDelRequest(post)
        assertTrue(overlayState.actionSheetState.targetPost == null)
        assertEquals(listOf(post), deletedPosts)

        handlers.onOpenDeleteDialog(post)
        assertEquals(post, overlayState.deleteDialogState.targetPost)
        assertEquals("stored", overlayState.deleteDialogState.password)

        handlers.onOpenQuoteSelection(post)
        assertEquals(post, overlayState.quoteSelectionState.targetPost)

        handlers.onNgRegister(post)
        assertTrue(overlayState.isNgManagementVisible)
        assertEquals("abc", overlayState.ngHeaderPrefill)
        assertTrue(shownMessages.isEmpty())
        assertEquals(
            listOf(
                "そうだねを送信しました" to "そうだねに失敗しました",
                "DEL依頼を送信しました" to "DEL依頼に失敗しました"
            ),
            launchedActions
        )
    }

    @Test
    fun threadScreenBindingSupport_wrapsTopBarAndActionBarCallbacks() {
        var searchQuery: String? = null
        var searchedPrev = false
        var searchedNext = false
        var submittedSearch = false
        var closedSearch = false
        var backed = false
        var openedHistory = false
        var startedSearch = false
        var openedSettings = false
        val topBarCallbacks = buildThreadScreenTopBarCallbacks(
            onSearchQueryChange = { searchQuery = it },
            onSearchPrev = { searchedPrev = true },
            onSearchNext = { searchedNext = true },
            onSearchSubmit = { submittedSearch = true },
            onSearchClose = { closedSearch = true },
            onBack = { backed = true },
            onOpenHistory = { openedHistory = true },
            onSearch = { startedSearch = true },
            onMenuSettings = { openedSettings = true }
        )
        topBarCallbacks.onSearchQueryChange("abc")
        topBarCallbacks.onSearchPrev()
        topBarCallbacks.onSearchNext()
        topBarCallbacks.onSearchSubmit()
        topBarCallbacks.onSearchClose()
        topBarCallbacks.onBack()
        topBarCallbacks.onOpenHistory()
        topBarCallbacks.onSearch()
        topBarCallbacks.onMenuSettings()

        assertEquals("abc", searchQuery)
        assertTrue(searchedPrev)
        assertTrue(searchedNext)
        assertTrue(submittedSearch)
        assertTrue(closedSearch)
        assertTrue(backed)
        assertTrue(openedHistory)
        assertTrue(startedSearch)
        assertTrue(openedSettings)

        var actionEntry: ThreadMenuEntryId? = null
        val actionBarCallbacks = buildThreadScreenActionBarCallbacks(
            onAction = { actionEntry = it }
        )
        actionBarCallbacks.onAction(ThreadMenuEntryId.Refresh)
        assertEquals(ThreadMenuEntryId.Refresh, actionEntry)
    }

    @Test
    fun threadScreenBindingSupport_bundlesUiCallbacks() {
        var searchQuery: String? = null
        var searchedPrev = false
        var searchedNext = false
        var submittedSearch = false
        var closedSearch = false
        var backed = false
        var openedHistory = false
        var startedSearch = false
        var openedSettings = false
        var actionEntry: ThreadMenuEntryId? = null

        var overlayState = ThreadPostOverlayState(
            quoteSelectionState = ThreadQuoteSelectionState(
                targetPost = Post(
                    id = "10",
                    author = null,
                    subject = null,
                    timestamp = "now",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        var replyDialogState = ThreadReplyDialogState(
            isVisible = true,
            draft = ThreadReplyDraft(comment = "base")
        )
        val replyDialogBinding = ThreadReplyDialogStateBinding(
            currentState = { replyDialogState },
            setState = { replyDialogState = it }
        )
        var mediaPreviewState = ThreadMediaPreviewState(previewMediaIndex = 0)
        val mediaEntry = MediaPreviewEntry(
            url = "https://example.com/a.png",
            mediaType = MediaType.Image,
            postId = "1",
            title = "a"
        )
        var savedMediaEntry: MediaPreviewEntry? = null
        val posts = listOf(
            Post(id = "1", author = null, subject = null, timestamp = "now", messageHtml = "a", imageUrl = null, thumbnailUrl = null),
            Post(id = "2", author = null, subject = null, timestamp = "now", messageHtml = "b", imageUrl = null, thumbnailUrl = null)
        )
        var scrolledIndex: Int? = null
        var galleryDismissed = false
        var settingsDismissed = false
        var openedNgManagement = false
        var filterState = ThreadFilterUiState()
        val filterBinding = ThreadFilterUiStateBinding(
            currentState = { filterState },
            setState = { filterState = it }
        )
        var filterDismissed = false
        var seekTarget: Pair<Int, Boolean>? = null
        var playedReadAloud = false
        var pausedReadAloud = false
        var stoppedReadAloud = false
        var showedStoppedMessage = false
        var dismissedReadAloud = false
        var addedNgHeader: String? = null
        var dismissedNgManagement = false
        var dismissedSaveProgress = false
        var cancelledSaveProgress = false
        var dismissedGlobalSettings = false
        var backgroundRefreshChanged: Boolean? = null
        var lightweightModeChanged: Boolean? = null
        var manualSaveDirectory: String? = null
        var saveDirectorySelection: SaveDirectorySelection? = null
        var openedDirectoryPicker = false
        var openedCookieManager = false
        var selectedFileManager: Pair<String, String>? = null
        var clearedPreferredFileManager = false
        var updatedThreadMenuEntries: List<ThreadMenuEntryConfig>? = null
        var updatedCatalogNavEntries: List<CatalogNavEntryConfig>? = null
        var dismissedCookieManagement = false

        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = { backgroundRefreshChanged = it },
            onLightweightModeChanged = { lightweightModeChanged = it },
            onManualSaveDirectoryChanged = { manualSaveDirectory = it },
            onSaveDirectorySelectionChanged = { saveDirectorySelection = it },
            onOpenSaveDirectoryPicker = { openedDirectoryPicker = true },
            onFileManagerSelected = { packageName, label ->
                selectedFileManager = packageName to label
            },
            onClearPreferredFileManager = { clearedPreferredFileManager = true },
            onThreadMenuEntriesChanged = { updatedThreadMenuEntries = it },
            onCatalogNavEntriesChanged = { updatedCatalogNavEntries = it }
        )
        val bundle = buildThreadScreenUiBindingsBundle(
            topBarInputs = ThreadScreenTopBarUiInputs(
                searchNavigationCallbacks = ThreadSearchNavigationCallbacks(
                    onSearchSubmit = { submittedSearch = true },
                    onSearchPrev = { searchedPrev = true },
                    onSearchNext = { searchedNext = true }
                ),
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = { closedSearch = true },
                onBack = { backed = true },
                onOpenHistory = { openedHistory = true },
                onSearch = { startedSearch = true },
                onOpenGlobalSettings = { openedSettings = true },
                onAction = { actionEntry = it }
            ),
            overlayInputs = ThreadScreenOverlayUiInputs(
                replyDialogBinding = replyDialogBinding,
                currentPostOverlayState = { overlayState },
                setPostOverlayState = { overlayState = it },
                mediaPreviewState = { mediaPreviewState },
                setMediaPreviewState = { mediaPreviewState = it },
                mediaPreviewEntryCount = 2,
                onSavePreviewMedia = { savedMediaEntry = it },
                galleryPosts = posts,
                onDismissGallery = { galleryDismissed = true },
                onScrollToPostIndex = { scrolledIndex = it },
                threadFilterBinding = filterBinding,
                onDismissSettingsSheet = { settingsDismissed = true },
                onDismissFilterSheet = { filterDismissed = true },
                onApplySettingsActionState = {},
                onOpenNgManagement = { openedNgManagement = true },
                onOpenExternalApp = {},
                onTogglePrivacy = {},
                firstVisibleSegmentIndex = { 1 },
                onSeekToReadAloudIndex = { index, restart -> seekTarget = index to restart },
                onPlayReadAloud = { playedReadAloud = true },
                onPauseReadAloud = { pausedReadAloud = true },
                onStopReadAloud = { stoppedReadAloud = true },
                onShowReadAloudStoppedMessage = { showedStoppedMessage = true },
                onDismissReadAloudControls = { dismissedReadAloud = true },
                onDismissNgManagement = { dismissedNgManagement = true },
                ngMutationCallbacks = ThreadNgMutationCallbacks(
                    onAddHeader = { addedNgHeader = it },
                    onAddWord = {},
                    onRemoveHeader = {},
                    onRemoveWord = {},
                    onToggleFiltering = {}
                ),
                onDismissSaveProgress = { dismissedSaveProgress = true },
                onCancelSaveProgress = { cancelledSaveProgress = true }
            ),
            settingsInputs = ThreadScreenSettingsUiInputs(
                onDismissGlobalSettings = { dismissedGlobalSettings = true },
                screenPreferencesCallbacks = preferencesCallbacks,
                onOpenCookieManager = { openedCookieManager = true },
                onDismissCookieManagement = { dismissedCookieManagement = true }
            )
        )

        bundle.topBarCallbacks.onSearchQueryChange("abc")
        bundle.topBarCallbacks.onSearchPrev()
        bundle.topBarCallbacks.onSearchNext()
        bundle.topBarCallbacks.onSearchSubmit()
        bundle.topBarCallbacks.onSearchClose()
        bundle.topBarCallbacks.onBack()
        bundle.topBarCallbacks.onOpenHistory()
        bundle.topBarCallbacks.onSearch()
        bundle.topBarCallbacks.onMenuSettings()
        bundle.actionBarCallbacks.onAction(ThreadMenuEntryId.Refresh)
        bundle.quoteSelectionConfirm(listOf(">No.1"))
        bundle.mediaPreviewDialogCallbacks.onNavigateNext()
        bundle.mediaPreviewDialogCallbacks.onSave(mediaEntry)
        bundle.galleryCallbacks?.onImageClick(posts[1])
        bundle.galleryCallbacks?.onDismiss()
        bundle.settingsSheetCallbacks.onAction(ThreadMenuEntryId.NgManagement)
        bundle.settingsSheetCallbacks.onDismiss()
        bundle.filterSheetCallbacks.onKeywordChange("filter")
        bundle.filterSheetCallbacks.onDismiss()
        bundle.readAloudControlCallbacks.onSeekToVisible()
        bundle.readAloudControlCallbacks.onPlay()
        bundle.readAloudControlCallbacks.onPause()
        bundle.readAloudControlCallbacks.onStop()
        bundle.readAloudControlCallbacks.onDismiss()
        bundle.ngManagementCallbacks.onAddHeader("header")
        bundle.ngManagementCallbacks.onDismiss()
        bundle.saveProgressDialogCallbacks.onDismissRequest()
        bundle.saveProgressDialogCallbacks.onCancelRequest()
        bundle.globalSettingsCallbacks.onBackgroundRefreshChanged(true)
        bundle.globalSettingsCallbacks.onLightweightModeChanged(false)
        bundle.globalSettingsCallbacks.onManualSaveDirectoryChanged("/tmp/save")
        bundle.globalSettingsCallbacks.onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
        bundle.globalSettingsCallbacks.onOpenSaveDirectoryPicker?.invoke()
        bundle.globalSettingsCallbacks.onOpenCookieManager?.invoke()
        bundle.globalSettingsCallbacks.onFileManagerSelected?.invoke("pkg", "label")
        bundle.globalSettingsCallbacks.onClearPreferredFileManager?.invoke()
        bundle.globalSettingsCallbacks.onThreadMenuEntriesChanged(emptyList())
        bundle.globalSettingsCallbacks.onCatalogNavEntriesChanged(emptyList())
        bundle.globalSettingsCallbacks.onBack()
        bundle.cookieManagementCallbacks.onBack()

        assertEquals("abc", searchQuery)
        assertTrue(searchedPrev)
        assertTrue(searchedNext)
        assertTrue(submittedSearch)
        assertTrue(closedSearch)
        assertTrue(backed)
        assertTrue(openedHistory)
        assertTrue(startedSearch)
        assertTrue(openedSettings)
        assertEquals(ThreadMenuEntryId.Refresh, actionEntry)
        assertEquals("base\n>No.1\n", replyDialogState.draft.comment)
        assertNull(overlayState.quoteSelectionState.targetPost)
        assertEquals(1, mediaPreviewState.previewMediaIndex)
        assertEquals(mediaEntry, savedMediaEntry)
        assertEquals(1, scrolledIndex)
        assertTrue(galleryDismissed)
        assertTrue(openedNgManagement)
        assertTrue(settingsDismissed)
        assertEquals("filter", filterState.keyword)
        assertTrue(filterDismissed)
        assertEquals(1 to true, seekTarget)
        assertTrue(playedReadAloud)
        assertTrue(pausedReadAloud)
        assertTrue(stoppedReadAloud)
        assertTrue(showedStoppedMessage)
        assertTrue(dismissedReadAloud)
        assertEquals("header", addedNgHeader)
        assertTrue(dismissedNgManagement)
        assertTrue(dismissedSaveProgress)
        assertTrue(cancelledSaveProgress)
        assertEquals(true, backgroundRefreshChanged)
        assertEquals(false, lightweightModeChanged)
        assertEquals("/tmp/save", manualSaveDirectory)
        assertEquals(SaveDirectorySelection.PICKER, saveDirectorySelection)
        assertTrue(openedDirectoryPicker)
        assertTrue(openedCookieManager)
        assertEquals("pkg" to "label", selectedFileManager)
        assertTrue(clearedPreferredFileManager)
        assertEquals(emptyList<ThreadMenuEntryConfig>(), updatedThreadMenuEntries)
        assertEquals(emptyList<CatalogNavEntryConfig>(), updatedCatalogNavEntries)
        assertTrue(dismissedGlobalSettings)
        assertTrue(dismissedCookieManagement)
    }

    @Test
    fun threadScreenBindingSupport_wrapsSearchRefreshAndQuoteSelectionHandlers() {
        val matches = listOf(
            ThreadSearchMatch(
                postId = "1",
                postIndex = 2,
                highlightRanges = listOf(0..1)
            ),
            ThreadSearchMatch(
                postId = "2",
                postIndex = 5,
                highlightRanges = listOf(1..2)
            )
        )
        var currentSearchIndex = 0
        val scrollTargets = mutableListOf<Int?>()
        val searchCallbacks = buildThreadScreenSearchNavigationCallbacks(
            currentIndex = { currentSearchIndex },
            setCurrentIndex = { currentSearchIndex = it },
            matches = { matches },
            onScrollToPostIndex = { scrollTargets += it }
        )

        searchCallbacks.onSearchSubmit()
        assertEquals(0, currentSearchIndex)
        assertEquals(listOf<Int?>(2), scrollTargets)

        searchCallbacks.onSearchNext()
        assertEquals(1, currentSearchIndex)
        assertEquals(listOf<Int?>(2, 5), scrollTargets)

        searchCallbacks.onSearchPrev()
        assertEquals(0, currentSearchIndex)
        assertEquals(listOf<Int?>(2, 5, 2), scrollTargets)

        var startedRefresh: Pair<Int, Int>? = null
        val refreshHandler = buildThreadScreenRefreshHandler(
            isRefreshing = { false },
            currentFirstVisibleItemIndex = { 3 },
            currentFirstVisibleItemOffset = { 12 },
            onStartRefresh = { index, offset -> startedRefresh = index to offset }
        )
        refreshHandler()
        assertEquals(3 to 12, startedRefresh)

        startedRefresh = null
        buildThreadScreenRefreshHandler(
            isRefreshing = { true },
            currentFirstVisibleItemIndex = { 7 },
            currentFirstVisibleItemOffset = { 24 },
            onStartRefresh = { index, offset -> startedRefresh = index to offset }
        )()
        assertNull(startedRefresh)

        var overlayState = ThreadPostOverlayState(
            quoteSelectionState = ThreadQuoteSelectionState(
                targetPost = Post(
                    id = "10",
                    author = null,
                    subject = null,
                    timestamp = "now",
                    messageHtml = "body",
                    imageUrl = null,
                    thumbnailUrl = null
                )
            )
        )
        var dialogState = ThreadReplyDialogState(
            isVisible = true,
            draft = ThreadReplyDraft(comment = "base")
        )
        val replyDialogBinding = ThreadReplyDialogStateBinding(
            currentState = { dialogState },
            setState = { dialogState = it }
        )
        val quoteConfirm = buildThreadScreenQuoteSelectionConfirmHandler(
            replyDialogBinding = replyDialogBinding,
            currentOverlayState = { overlayState },
            setOverlayState = { overlayState = it }
        )

        quoteConfirm(listOf(">No.1"))
        assertEquals("base\n>No.1\n", dialogState.draft.comment)
        assertTrue(dialogState.isVisible)
        assertNull(overlayState.quoteSelectionState.targetPost)
    }

    @Test
    fun threadScreenBindingSupport_bundlesInteractionHandlers() = runBlocking {
        var replyOpened = false
        var startedRefreshFromMenu = false
        var startedRefreshFromPull: Pair<Int, Int>? = null
        var selectedHistory: ThreadHistoryEntry? = null
        var drawerClosed = false
        var boardClicked = false
        var historyRefreshed = false
        var batchDeleted = false
        var settingsOpened = false
        val post = Post(
            id = "10",
            author = null,
            subject = null,
            timestamp = "now",
            posterId = "abc",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null
        )
        val historyEntry = ThreadHistoryEntry(
            threadId = "1",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 0
        )
        var overlayState = emptyThreadPostOverlayState()
        var currentSearchIndex = 0
        val scrollTargets = mutableListOf<Int?>()
        val bundle = buildThreadScreenInteractionBindingsBundle(
            menuInputs = ThreadScreenMenuInteractionInputs(
                isRefreshing = { false },
                onOpenReplyDialog = { replyOpened = true },
                onScrollTop = {},
                onScrollBottom = {},
                onShowRefreshBusyMessage = {},
                onStartRefreshFromMenu = { startedRefreshFromMenu = true },
                onOpenGallery = {},
                onDelegateToSaveHandler = {},
                onShowFilterSheet = {},
                onShowSettingsSheet = {},
                onClearNgHeaderPrefill = {},
                onShowNgManagement = {},
                onOpenExternalApp = {},
                onShowReadAloudControls = {},
                onTogglePrivacy = {}
            ),
            searchInputs = ThreadScreenSearchInteractionInputs(
                currentSearchIndex = { currentSearchIndex },
                setCurrentSearchIndex = { currentSearchIndex = it },
                currentSearchMatches = {
                    listOf(
                        ThreadSearchMatch(
                            postId = "1",
                            postIndex = 2,
                            highlightRanges = listOf(0..1)
                        )
                    )
                },
                onScrollToSearchMatchPostIndex = { scrollTargets += it },
                onCloseDrawerAfterHistorySelection = { drawerClosed = true },
                onHistoryEntrySelected = { selectedHistory = it }
            ),
            postActionInputs = ThreadScreenPostActionInputs(
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                lastUsedDeleteKey = "stored",
                currentSaidaneLabel = { null },
                isSelfPost = { false },
                onShowOptionalMessage = {},
                onSaidaneLabelUpdated = { _, _ -> },
                launchUnitAction = { _, _, onSuccess, block ->
                    runBlocking { block() }
                    onSuccess()
                },
                voteSaidane = {},
                requestDeletion = {}
            ),
            refreshInputs = ThreadScreenRefreshInteractionInputs(
                currentFirstVisibleItemIndex = { 3 },
                currentFirstVisibleItemOffset = { 12 },
                onStartRefreshFromPull = { index, offset -> startedRefreshFromPull = index to offset }
            ),
            historyDrawerInputs = ThreadScreenHistoryDrawerInputs(
                onHistoryEntryDismissed = {},
                onBoardClick = { boardClicked = true },
                onHistoryRefreshClick = { historyRefreshed = true },
                onHistoryBatchDeleteClick = { batchDeleted = true },
                onHistorySettingsClick = { settingsOpened = true }
            )
        )

        bundle.menuEntryHandler(ThreadMenuEntryId.Reply)
        bundle.menuEntryHandler(ThreadMenuEntryId.Refresh)
        bundle.searchNavigationCallbacks.onSearchSubmit()
        bundle.historySelectionHandler(historyEntry)
        bundle.refreshHandler()
        bundle.postActionHandlers.onOpenDeleteDialog(post)
        bundle.historyDrawerCallbacks.onBoardClick()
        bundle.historyDrawerCallbacks.onRefreshClick()
        bundle.historyDrawerCallbacks.onBatchDeleteClick()
        bundle.historyDrawerCallbacks.onSettingsClick()

        assertTrue(replyOpened)
        assertTrue(startedRefreshFromMenu)
        assertEquals(listOf<Int?>(2), scrollTargets)
        assertEquals(historyEntry, selectedHistory)
        assertTrue(drawerClosed)
        assertEquals(3 to 12, startedRefreshFromPull)
        assertEquals(post, overlayState.deleteDialogState.targetPost)
        assertTrue(boardClicked)
        assertTrue(historyRefreshed)
        assertTrue(batchDeleted)
        assertTrue(settingsOpened)
    }

    @Test
    fun threadScreenControllerBindingsSupport_bundlesActionAndInteraction() = runBlocking {
        var actionInProgress = false
        var lastBusyNoticeAtMillis = 0L
        var historyRefreshing = false
        var readAloudState = ThreadReadAloudRuntimeState(
            job = null,
            status = ReadAloudStatus.Idle,
            currentIndex = 0,
            cancelRequestedByUser = false
        )
        var replyOpened = false
        var refreshStarted = false
        var historyRefreshCount = 0
        var openedSettings = false
        val entry = ThreadHistoryEntry(
            threadId = "1",
            boardId = "b",
            title = "t",
            titleImageUrl = "",
            boardName = "board",
            boardUrl = "https://example.com/b",
            lastVisitedEpochMillis = 1L,
            replyCount = 0
        )
        var selectedEntry: ThreadHistoryEntry? = null
        var overlayState = emptyThreadPostOverlayState()
        val actionStateBindings = ThreadScreenActionStateBindings(
            currentActionInProgress = { actionInProgress },
            setActionInProgress = { actionInProgress = it },
            currentLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis },
            setLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis = it }
        )
        val actionDependencies = ThreadScreenActionDependencies(
            busyNoticeIntervalMillis = ACTION_BUSY_NOTICE_INTERVAL_MS,
            showMessage = {},
            onDebugLog = {},
            onInfoLog = {},
            onErrorLog = { _, _ -> }
        )
        val historyRefreshStateBindings = ThreadScreenHistoryRefreshStateBindings(
            currentIsHistoryRefreshing = { historyRefreshing },
            setIsHistoryRefreshing = { historyRefreshing = it }
        )
        val readAloudStateBindings = ThreadScreenReadAloudStateBindings(
            currentState = { readAloudState },
            setState = { readAloudState = it }
        )
        val readAloudCallbacks = ThreadScreenReadAloudCallbacks(
            showMessage = {},
            showOptionalMessage = {},
            scrollToPostIndex = {},
            speakText = {},
            cancelActiveReadAloud = {}
        )
        val readAloudDependencies = ThreadScreenReadAloudDependencies(
            currentSegments = { emptyList() }
        )
        val bundle = buildThreadScreenControllerBindingsBundle(
            actionInputs = ThreadScreenControllerActionInputs(
                coroutineScope = this,
                actionStateBindings = actionStateBindings,
                actionDependencies = actionDependencies,
                historyRefreshStateBindings = historyRefreshStateBindings,
                onHistoryRefresh = { historyRefreshCount++ },
                showHistoryRefreshMessage = {},
                readAloudStateBindings = readAloudStateBindings,
                readAloudCallbacks = readAloudCallbacks,
                readAloudDependencies = readAloudDependencies
            ),
            interactionInputs = ThreadScreenControllerInteractionInputs(
                isRefreshing = { false },
                onOpenReplyDialog = { replyOpened = true },
                onScrollTop = {},
                onScrollBottom = {},
                onShowRefreshBusyMessage = {},
                onStartRefreshFromMenu = { refreshStarted = true },
                onOpenGallery = {},
                onDelegateToSaveHandler = {},
                onShowFilterSheet = {},
                onShowSettingsSheet = { openedSettings = true },
                onClearNgHeaderPrefill = {},
                onShowNgManagement = {},
                onOpenExternalApp = {},
                onShowReadAloudControls = {},
                onTogglePrivacy = {},
                currentSearchIndex = { 0 },
                setCurrentSearchIndex = {},
                currentSearchMatches = { emptyList() },
                onScrollToSearchMatchPostIndex = {},
                onCloseDrawerAfterHistorySelection = {},
                onHistoryEntrySelected = { selectedEntry = it },
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                lastUsedDeleteKey = "",
                currentSaidaneLabel = { null },
                isSelfPost = { false },
                onSaidaneLabelUpdated = { _, _ -> },
                repository = FakeBoardRepository(),
                effectiveBoardUrl = "https://example.com/test",
                threadId = "123",
                currentFirstVisibleItemIndex = { 0 },
                currentFirstVisibleItemOffset = { 0 },
                onStartRefreshFromPull = { _, _ -> },
                onHistoryEntryDismissed = {},
                onBoardClick = {},
                onHistoryBatchDeleteClick = {},
                onHistorySettingsClick = { openedSettings = true }
            )
        )

        bundle.interactionBindings.menuEntryHandler(ThreadMenuEntryId.Reply)
        bundle.interactionBindings.menuEntryHandler(ThreadMenuEntryId.Refresh)
        bundle.actionExecutionBindings.historyRefreshBindings.handleHistoryRefresh()
        yield()
        bundle.interactionBindings.historySelectionHandler(entry)
        bundle.interactionBindings.historyDrawerCallbacks.onSettingsClick()

        assertTrue(replyOpened)
        assertTrue(refreshStarted)
        assertEquals(1, historyRefreshCount)
        assertEquals(entry, selectedEntry)
        assertTrue(openedSettings)
    }

    @Test
    fun threadScreenInteractionUiAggregateSupport_bundlesMediaControllerAndUiBindings() = runBlocking {
        var previewState = ThreadMediaPreviewState(previewMediaIndex = 0)
        val mediaEntries = listOf(
            MediaPreviewEntry(
                url = "https://example.com/1.png",
                mediaType = MediaType.Image,
                postId = "1",
                title = "one"
            ),
            MediaPreviewEntry(
                url = "https://example.com/2.png",
                mediaType = MediaType.Image,
                postId = "2",
                title = "two"
            )
        )
        var actionInProgress = false
        var lastBusyNoticeAtMillis = 0L
        var historyRefreshing = false
        var readAloudState = ThreadReadAloudRuntimeState(
            job = null,
            status = ReadAloudStatus.Idle,
            currentIndex = 0,
            cancelRequestedByUser = false
        )
        var searchQuery = ""
        var searchClosed = false
        var historyOpened = false
        var searchOpened = false
        var globalSettingsOpened = false
        var replyDialogState = ThreadReplyDialogState(isVisible = false)
        val replyDialogBinding = ThreadReplyDialogStateBinding(
            currentState = { replyDialogState },
            setState = { replyDialogState = it }
        )
        var overlayState = emptyThreadPostOverlayState()
        var dismissedGallery = false
        var scrolledToPostIndex: Int? = null
        var filterState = ThreadFilterUiState()
        val filterBinding = ThreadFilterUiStateBinding(
            currentState = { filterState },
            setState = { filterState = it }
        )
        var dismissedNgManagement = false
        var dismissedSaveProgress = false
        var cancelledSaveProgress = false
        var dismissedGlobalSettings = false
        var backgroundRefreshChanged: Boolean? = null
        var lightweightModeChanged: Boolean? = null
        var manualSaveDirectory: String? = null
        var saveDirectorySelection: SaveDirectorySelection? = null
        var openedDirectoryPicker = false
        var openedCookieManager = false
        var selectedFileManager: Pair<String, String>? = null
        var clearedPreferredFileManager = false
        var updatedThreadMenuEntries: List<ThreadMenuEntryConfig>? = null
        var updatedCatalogNavEntries: List<CatalogNavEntryConfig>? = null
        var dismissedCookieManagement = false
        var addedNgHeader: String? = null
        var replyOpened = false
        var startedRefreshFromMenu = false
        val galleryPosts = listOf(
            Post(
                id = "1",
                author = null,
                subject = null,
                timestamp = "now",
                messageHtml = "first",
                imageUrl = null,
                thumbnailUrl = null
            ),
            Post(
                id = "2",
                author = null,
                subject = null,
                timestamp = "now",
                messageHtml = "second",
                imageUrl = null,
                thumbnailUrl = null
            )
        )
        val searchMatches = listOf(
            ThreadSearchMatch(
                postId = "1",
                postIndex = 3,
                highlightRanges = listOf(0..1)
            )
        )
        var currentSearchIndex = 0
        val historyEntry = ThreadHistoryEntry(
            threadId = "thread-1",
            boardId = "board",
            title = "title",
            titleImageUrl = "",
            boardName = "Board",
            boardUrl = "https://example.com/board",
            lastVisitedEpochMillis = 1L,
            replyCount = 2
        )
        var selectedHistoryEntry: ThreadHistoryEntry? = null
        var drawerClosed = false
        val actionStateBindings = ThreadScreenActionStateBindings(
            currentActionInProgress = { actionInProgress },
            setActionInProgress = { actionInProgress = it },
            currentLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis },
            setLastBusyNoticeAtMillis = { lastBusyNoticeAtMillis = it }
        )
        val actionDependencies = ThreadScreenActionDependencies(
            busyNoticeIntervalMillis = ACTION_BUSY_NOTICE_INTERVAL_MS,
            showMessage = {},
            onDebugLog = {},
            onInfoLog = {},
            onErrorLog = { _, _ -> }
        )
        val historyRefreshStateBindings = ThreadScreenHistoryRefreshStateBindings(
            currentIsHistoryRefreshing = { historyRefreshing },
            setIsHistoryRefreshing = { historyRefreshing = it }
        )
        val readAloudStateBindings = ThreadScreenReadAloudStateBindings(
            currentState = { readAloudState },
            setState = { readAloudState = it }
        )
        val readAloudCallbacks = ThreadScreenReadAloudCallbacks(
            showMessage = {},
            showOptionalMessage = {},
            scrollToPostIndex = {},
            speakText = {},
            cancelActiveReadAloud = {}
        )
        val readAloudDependencies = ThreadScreenReadAloudDependencies(
            currentSegments = { emptyList() }
        )
        val preferencesCallbacks = ScreenPreferencesCallbacks(
            onBackgroundRefreshChanged = { backgroundRefreshChanged = it },
            onLightweightModeChanged = { lightweightModeChanged = it },
            onManualSaveDirectoryChanged = { manualSaveDirectory = it },
            onSaveDirectorySelectionChanged = { saveDirectorySelection = it },
            onOpenSaveDirectoryPicker = { openedDirectoryPicker = true },
            onFileManagerSelected = { packageName, label ->
                selectedFileManager = packageName to label
            },
            onClearPreferredFileManager = { clearedPreferredFileManager = true },
            onThreadMenuEntriesChanged = { updatedThreadMenuEntries = it },
            onCatalogNavEntriesChanged = { updatedCatalogNavEntries = it }
        )

        val bundle = buildThreadScreenInteractionUiAggregateBundle(
            mediaInputs = ThreadScreenAggregateMediaInputs(
                currentPreviewState = { previewState },
                setPreviewState = { previewState = it },
                currentMediaEntries = { mediaEntries }
            ),
            controllerActionInputs = ThreadScreenControllerActionInputs(
                coroutineScope = this,
                actionStateBindings = actionStateBindings,
                actionDependencies = actionDependencies,
                historyRefreshStateBindings = historyRefreshStateBindings,
                onHistoryRefresh = {},
                showHistoryRefreshMessage = {},
                readAloudStateBindings = readAloudStateBindings,
                readAloudCallbacks = readAloudCallbacks,
                readAloudDependencies = readAloudDependencies
            ),
            controllerInteractionInputs = ThreadScreenControllerInteractionInputs(
                isRefreshing = { false },
                onOpenReplyDialog = { replyOpened = true },
                onScrollTop = {},
                onScrollBottom = {},
                onShowRefreshBusyMessage = {},
                onStartRefreshFromMenu = { startedRefreshFromMenu = true },
                onOpenGallery = {},
                onDelegateToSaveHandler = {},
                onShowFilterSheet = {},
                onShowSettingsSheet = {},
                onClearNgHeaderPrefill = {},
                onShowNgManagement = {},
                onOpenExternalApp = {},
                onShowReadAloudControls = {},
                onTogglePrivacy = {},
                currentSearchIndex = { currentSearchIndex },
                setCurrentSearchIndex = { currentSearchIndex = it },
                currentSearchMatches = { searchMatches },
                onScrollToSearchMatchPostIndex = {},
                onCloseDrawerAfterHistorySelection = { drawerClosed = true },
                onHistoryEntrySelected = { selectedHistoryEntry = it },
                currentOverlayState = { overlayState },
                setOverlayState = { overlayState = it },
                lastUsedDeleteKey = "stored",
                currentSaidaneLabel = { null },
                isSelfPost = { false },
                onSaidaneLabelUpdated = { _, _ -> },
                repository = FakeBoardRepository(),
                effectiveBoardUrl = "https://example.com/board",
                threadId = "thread-1",
                currentFirstVisibleItemIndex = { 0 },
                currentFirstVisibleItemOffset = { 0 },
                onStartRefreshFromPull = { _, _ -> },
                onHistoryEntryDismissed = {},
                onBoardClick = {},
                onHistoryBatchDeleteClick = {},
                onHistorySettingsClick = {}
            ),
            uiInputs = ThreadScreenAggregateUiInputs(
                onSearchQueryChange = { searchQuery = it },
                onSearchClose = { searchClosed = true },
                onBack = {},
                onOpenHistory = { historyOpened = true },
                onSearch = { searchOpened = true },
                onOpenGlobalSettings = { globalSettingsOpened = true },
                replyDialogBinding = replyDialogBinding,
                mediaPreviewEntryCount = mediaEntries.size,
                onSavePreviewMedia = {},
                galleryPosts = galleryPosts,
                onDismissGallery = { dismissedGallery = true },
                onScrollToPostIndex = { scrolledToPostIndex = it },
                threadFilterBinding = filterBinding,
                onDismissSettingsSheet = {},
                onDismissFilterSheet = {},
                onApplySettingsActionState = {},
                firstVisibleSegmentIndex = { 1 },
                onPauseReadAloud = {},
                onStopReadAloud = {},
                onShowReadAloudStoppedMessage = {},
                onDismissReadAloudControls = {},
                onDismissNgManagement = { dismissedNgManagement = true },
                ngMutationCallbacks = ThreadNgMutationCallbacks(
                    onAddHeader = { addedNgHeader = it },
                    onAddWord = {},
                    onRemoveHeader = {},
                    onRemoveWord = {},
                    onToggleFiltering = {}
                ),
                onDismissSaveProgress = { dismissedSaveProgress = true },
                onCancelSaveProgress = { cancelledSaveProgress = true },
                onDismissGlobalSettings = { dismissedGlobalSettings = true },
                screenPreferencesCallbacks = preferencesCallbacks,
                onOpenCookieManager = { openedCookieManager = true },
                onDismissCookieManagement = { dismissedCookieManagement = true }
            )
        )

        bundle.mediaBindings.onMediaClick(mediaEntries[1].url, mediaEntries[1].mediaType)
        bundle.controllerBindings.interactionBindings.menuEntryHandler(ThreadMenuEntryId.Reply)
        bundle.controllerBindings.interactionBindings.menuEntryHandler(ThreadMenuEntryId.Refresh)
        bundle.controllerBindings.interactionBindings.historySelectionHandler(historyEntry)
        bundle.uiBindings.topBarCallbacks.onSearchQueryChange("abc")
        bundle.uiBindings.topBarCallbacks.onOpenHistory()
        bundle.uiBindings.topBarCallbacks.onSearch()
        bundle.uiBindings.topBarCallbacks.onMenuSettings()
        bundle.uiBindings.galleryCallbacks?.onImageClick(galleryPosts[1])
        bundle.uiBindings.galleryCallbacks?.onDismiss()
        bundle.uiBindings.ngManagementCallbacks.onAddHeader("header")
        bundle.uiBindings.ngManagementCallbacks.onDismiss()
        bundle.uiBindings.saveProgressDialogCallbacks.onDismissRequest()
        bundle.uiBindings.saveProgressDialogCallbacks.onCancelRequest()
        bundle.uiBindings.globalSettingsCallbacks.onBackgroundRefreshChanged(true)
        bundle.uiBindings.globalSettingsCallbacks.onLightweightModeChanged(false)
        bundle.uiBindings.globalSettingsCallbacks.onManualSaveDirectoryChanged("/tmp/save")
        bundle.uiBindings.globalSettingsCallbacks.onSaveDirectorySelectionChanged(SaveDirectorySelection.PICKER)
        bundle.uiBindings.globalSettingsCallbacks.onOpenSaveDirectoryPicker?.invoke()
        bundle.uiBindings.globalSettingsCallbacks.onOpenCookieManager?.invoke()
        bundle.uiBindings.globalSettingsCallbacks.onFileManagerSelected?.invoke("pkg", "label")
        bundle.uiBindings.globalSettingsCallbacks.onClearPreferredFileManager?.invoke()
        bundle.uiBindings.globalSettingsCallbacks.onThreadMenuEntriesChanged(emptyList())
        bundle.uiBindings.globalSettingsCallbacks.onCatalogNavEntriesChanged(emptyList())
        bundle.uiBindings.globalSettingsCallbacks.onBack()
        bundle.uiBindings.cookieManagementCallbacks.onBack()

        assertEquals(1, previewState.previewMediaIndex)
        assertTrue(replyOpened)
        assertTrue(startedRefreshFromMenu)
        assertEquals(historyEntry, selectedHistoryEntry)
        assertTrue(drawerClosed)
        assertEquals("abc", searchQuery)
        assertTrue(searchClosed.not())
        assertTrue(historyOpened)
        assertTrue(searchOpened)
        assertTrue(globalSettingsOpened)
        assertTrue(dismissedGallery)
        assertEquals(1, scrolledToPostIndex)
        assertEquals("header", addedNgHeader)
        assertTrue(dismissedNgManagement)
        assertTrue(dismissedSaveProgress)
        assertTrue(cancelledSaveProgress)
        assertTrue(dismissedGlobalSettings)
        assertEquals(true, backgroundRefreshChanged)
        assertEquals(false, lightweightModeChanged)
        assertEquals("/tmp/save", manualSaveDirectory)
        assertEquals(SaveDirectorySelection.PICKER, saveDirectorySelection)
        assertTrue(openedDirectoryPicker)
        assertTrue(openedCookieManager)
        assertEquals("pkg" to "label", selectedFileManager)
        assertTrue(clearedPreferredFileManager)
        assertEquals(emptyList<ThreadMenuEntryConfig>(), updatedThreadMenuEntries)
        assertEquals(emptyList<CatalogNavEntryConfig>(), updatedCatalogNavEntries)
        assertTrue(dismissedCookieManagement)
    }

    @Test
    fun appendQuoteSelectionToReplyDraft_updatesCommentOnlyWhenSelectionExists() {
        val draft = ThreadReplyDraft(
            name = "name",
            email = "sage",
            subject = "subject",
            comment = "既存",
            password = "pass"
        )

        val updated = appendQuoteSelectionToReplyDraft(draft, listOf(">No.1", ">引用"))

        assertEquals(
            ThreadReplyDraft(
                name = "name",
                email = "sage",
                subject = "subject",
                comment = "既存\n>No.1\n>引用\n",
                password = "pass"
            ),
            updated
        )
        assertEquals(null, appendQuoteSelectionToReplyDraft(draft, emptyList()))
    }

    @Test
    fun threadPostOverlayHelpers_reduceActionSheetDialogQuoteAndNgState() {
        val post = Post(
            id = "123",
            order = 1,
            author = "name",
            subject = null,
            timestamp = "now",
            posterId = "abc123",
            messageHtml = "body",
            imageUrl = null,
            thumbnailUrl = null,
            saidaneLabel = null
        )
        val initial = emptyThreadPostOverlayState()

        val openedAction = openThreadPostActionOverlay(initial, post)
        assertEquals(post, openedAction.actionSheetState.targetPost)
        assertTrue(openedAction.actionSheetState.isActionSheetVisible)

        val openedDelete = openThreadDeleteOverlay(openedAction, post, "stored")
        assertEquals(post, openedDelete.deleteDialogState.targetPost)
        assertEquals("stored", openedDelete.deleteDialogState.password)
        assertFalse(openedDelete.actionSheetState.isActionSheetVisible)

        val updatedDelete = updateThreadDeleteOverlayInput(
            currentState = openedDelete,
            password = "next",
            imageOnly = true
        )
        assertEquals("next", updatedDelete.deleteDialogState.password)
        assertTrue(updatedDelete.deleteDialogState.imageOnly)

        val deleteConfirm = confirmThreadDeleteDialog(" next ", true)
        val confirmedDelete = applyThreadDeleteConfirmState(updatedDelete, deleteConfirm)
        assertEquals(null, confirmedDelete.deleteDialogState.targetPost)
        assertEquals("", confirmedDelete.deleteDialogState.password)

        val openedQuote = openThreadQuoteOverlay(confirmedDelete, post)
        assertEquals(post, openedQuote.quoteSelectionState.targetPost)
        assertEquals(null, dismissThreadQuoteOverlay(openedQuote).quoteSelectionState.targetPost)
        assertFalse(openedQuote.actionSheetState.isActionSheetVisible)
        assertEquals(null, openedQuote.deleteDialogState.targetPost)

        val ngOverlay = resolveThreadNgRegistrationOverlayState(initial, post)
        assertTrue(ngOverlay.overlayState.isNgManagementVisible)
        assertEquals("abc123", ngOverlay.overlayState.ngHeaderPrefill)
        assertEquals(null, ngOverlay.message)

        val dismissedNg = dismissThreadNgManagementOverlay(ngOverlay.overlayState)
        assertFalse(dismissedNg.isNgManagementVisible)
        assertEquals(null, dismissedNg.ngHeaderPrefill)
    }

    @Test
    fun threadSheetOverlayHelpers_reduceSettingsFilterAndReadAloudState() {
        val initial = emptyThreadSheetOverlayState()

        val openedSettings = openThreadSettingsOverlay(initial)
        assertTrue(openedSettings.isSettingsVisible)
        assertFalse(openedSettings.isFilterVisible)

        val openedFilter = openThreadFilterOverlay(openedSettings)
        assertTrue(openedFilter.isFilterVisible)

        val openedReadAloud = openThreadReadAloudOverlay(openedFilter)
        assertTrue(openedReadAloud.isReadAloudControlsVisible)

        val appliedSettingsAction = applyThreadSettingsActionOverlayState(
            currentState = openedSettings,
            actionState = ThreadSettingsActionState(
                closeSheet = true,
                showReadAloudControls = true,
                reopenSettingsSheet = true
            )
        )
        assertTrue(appliedSettingsAction.isSettingsVisible)
        assertTrue(appliedSettingsAction.isReadAloudControlsVisible)

        val dismissedSettings = dismissThreadSettingsOverlay(appliedSettingsAction)
        assertFalse(dismissedSettings.isSettingsVisible)
        val dismissedFilter = dismissThreadFilterOverlay(openedFilter)
        assertFalse(dismissedFilter.isFilterVisible)
        val dismissedReadAloud = dismissThreadReadAloudOverlay(openedReadAloud)
        assertFalse(dismissedReadAloud.isReadAloudControlsVisible)
    }

    @Test
    fun threadModalOverlayHelpers_reduceGallerySettingsAndCookieState() {
        val initial = emptyThreadModalOverlayState()

        val galleryOpened = openThreadGalleryOverlay(initial)
        assertTrue(galleryOpened.isGalleryVisible)
        assertFalse(galleryOpened.isGlobalSettingsVisible)

        val galleryDismissed = dismissThreadGalleryOverlay(galleryOpened)
        assertFalse(galleryDismissed.isGalleryVisible)

        val settingsOpened = openThreadGlobalSettingsOverlay(initial)
        assertTrue(settingsOpened.isGlobalSettingsVisible)
        assertFalse(settingsOpened.isCookieManagementVisible)

        val cookieOpened = openThreadCookieManagementOverlay(settingsOpened)
        assertFalse(cookieOpened.isGlobalSettingsVisible)
        assertTrue(cookieOpened.isCookieManagementVisible)

        val cookieDismissed = dismissThreadCookieManagementOverlay(cookieOpened)
        assertFalse(cookieDismissed.isCookieManagementVisible)

        val settingsDismissed = dismissThreadGlobalSettingsOverlay(settingsOpened)
        assertFalse(settingsDismissed.isGlobalSettingsVisible)
    }

    @Test
    fun threadFilterUiStateHelpers_toggleKeywordAndClear() {
        val initial = ThreadFilterUiState()
        val toggledSort = toggleThreadFilterOption(initial, ThreadFilterOption.HighSaidane)
        assertEquals(setOf(ThreadFilterOption.HighSaidane), toggledSort.options)
        assertEquals(ThreadFilterSortOption.Saidane, toggledSort.sortOption)

        val toggledPlain = toggleThreadFilterOption(toggledSort, ThreadFilterOption.Image)
        assertEquals(
            setOf(ThreadFilterOption.HighSaidane, ThreadFilterOption.Image),
            toggledPlain.options
        )
        assertEquals(ThreadFilterSortOption.Saidane, toggledPlain.sortOption)

        val updatedKeyword = updateThreadFilterKeyword(toggledPlain, "abc")
        assertEquals("abc", updatedKeyword.keyword)

        val cleared = clearThreadFilterUiState(updatedKeyword)
        assertEquals(ThreadFilterUiState(), cleared)
    }

    @Test
    fun readAloudHelpers_resolveStartPauseSeekAndIndexNormalization() {
        val segments = listOf(
            ReadAloudSegment(postIndex = 1, postId = "10", body = "a"),
            ReadAloudSegment(postIndex = 3, postId = "30", body = "b")
        )

        assertEquals(
            ReadAloudStartState(
                canStart = false,
                normalizedIndex = 0,
                message = "読み上げ対象がありません"
            ),
            resolveReadAloudStartState(segmentCount = 0, currentIndex = 5, isJobRunning = false)
        )
        assertEquals(
            ReadAloudStartState(
                canStart = false,
                normalizedIndex = 1,
                message = null
            ),
            resolveReadAloudStartState(segmentCount = 2, currentIndex = 1, isJobRunning = true)
        )
        assertEquals(
            ReadAloudStartState(
                canStart = true,
                normalizedIndex = 0,
                message = null
            ),
            resolveReadAloudStartState(segmentCount = 2, currentIndex = 5, isJobRunning = false)
        )
        assertEquals(
            ReadAloudPauseState(
                status = ReadAloudStatus.Paused(segments[0]),
                message = "読み上げを一時停止しました"
            ),
            resolveReadAloudPauseState(ReadAloudStatus.Speaking(segments[0]))
        )
        assertEquals(null, resolveReadAloudPauseState(ReadAloudStatus.Idle))
        assertEquals(
            ReadAloudSeekState(
                targetIndex = 1,
                targetSegment = segments[1],
                shouldRestart = true
            ),
            resolveReadAloudSeekState(
                segments = segments,
                status = ReadAloudStatus.Paused(segments[0]),
                targetIndex = 5
            )
        )
        assertEquals(
            ReadAloudSeekState(
                targetIndex = 0,
                targetSegment = segments[0],
                shouldRestart = false
            ),
            resolveReadAloudSeekState(
                segments = segments,
                status = ReadAloudStatus.Idle,
                targetIndex = -1
            )
        )
        assertEquals(null, resolveReadAloudSeekState(emptyList(), ReadAloudStatus.Idle, 0))
        assertEquals(2, normalizeReadAloudCurrentIndex(currentIndex = 5, segmentCount = 2))
        assertEquals(0, normalizeReadAloudCurrentIndex(currentIndex = -1, segmentCount = 2))
    }

    @Test
    fun threadNgHelpers_validateDeduplicateRemoveAndToggle() {
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("ID:abc"),
                message = "NGヘッダーに含める文字列を入力してください",
                shouldPersist = false
            ),
            addThreadNgHeader(existingEntries = listOf("ID:abc"), input = "   ")
        )
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("ID:abc"),
                message = "そのNGヘッダーはすでに登録されています",
                shouldPersist = false
            ),
            addThreadNgHeader(existingEntries = listOf("ID:abc"), input = " id:ABC ")
        )
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("ID:abc", "ID:def"),
                message = "NGヘッダーを追加しました",
                shouldPersist = true
            ),
            addThreadNgHeader(existingEntries = listOf("ID:abc"), input = " ID:def ")
        )
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("spam"),
                message = "NGワードを削除しました",
                shouldPersist = true
            ),
            removeThreadNgWord(existingEntries = listOf("spam", "ads"), entry = "ads")
        )
        assertEquals(
            ThreadNgMutationState(
                updatedEntries = listOf("spam"),
                message = "そのNGワードはすでに登録されています",
                shouldPersist = false
            ),
            addThreadNgWord(existingEntries = listOf("spam"), input = " Spam ")
        )
        assertEquals(
            ThreadNgFilterToggleState(
                isEnabled = false,
                message = "NG表示を無効にしました"
            ),
            toggleThreadNgFiltering(currentEnabled = true)
        )
    }

    @Test
    fun normalizeArchiveQuery_trimsWhitespaceAndMaxLength() {
        assertEquals("hello world", normalizeArchiveQuery("  hello   world  ", maxLength = 32))
        assertEquals("abc d", normalizeArchiveQuery("abc   def", maxLength = 5))
        assertEquals("", normalizeArchiveQuery("   ", maxLength = 10))
        assertEquals(
            listOf("12345", "thread title"),
            buildArchiveFallbackQueryCandidates(
                threadId = " 12345 ",
                threadTitle = " thread   title "
            )
        )
        assertEquals(
            listOf("same"),
            buildArchiveFallbackQueryCandidates(
                threadId = "same",
                threadTitle = " same "
            )
        )
    }

    @Test
    fun resolveThreadRemoteFetchRequest_prefersMatchingThreadUrlOnly() {
        assertEquals(
            ThreadRemoteFetchRequest.ByUrl("https://may.2chan.net/b/res/123.htm"),
            resolveThreadRemoteFetchRequest(
                threadUrl = " https://may.2chan.net/b/res/123.htm ",
                targetThreadId = "123",
                boardUrl = "https://may.2chan.net/b"
            )
        )
        assertEquals(
            ThreadRemoteFetchRequest.ByBoard(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123"
            ),
            resolveThreadRemoteFetchRequest(
                threadUrl = "https://may.2chan.net/b/res/999.htm",
                targetThreadId = "123",
                boardUrl = "https://may.2chan.net/b"
            )
        )
        assertEquals(
            ThreadRemoteFetchRequest.ByBoard(
                boardUrl = "https://may.2chan.net/b",
                threadId = "123"
            ),
            resolveThreadRemoteFetchRequest(
                threadUrl = "https://may.2chan.net/b/futaba.php?mode=cat",
                targetThreadId = "123",
                boardUrl = "https://may.2chan.net/b"
            )
        )
    }

    @Test
    fun isOfflineFallbackCandidate_detectsNetworkConditions() {
        assertTrue(isOfflineFallbackCandidate(NetworkException("HTTP error", statusCode = 404)))
        assertTrue(isOfflineFallbackCandidate(IllegalStateException("connection timeout")))
        assertTrue(isOfflineFallbackCandidate(RuntimeException("wrapper", NetworkException("dns failure"))))
        assertFalse(isOfflineFallbackCandidate(IllegalArgumentException("bad input")))
        assertEquals(
            ThreadLoadFallbackState(
                statusCode = 404,
                shouldTryArchiveFallback = true,
                shouldTryOfflineFallback = true,
                shouldThrowWhenArchiveNotFound = false
            ),
            resolveThreadLoadFallbackState(
                error = NetworkException("HTTP error", statusCode = 404),
                allowOfflineFallback = true
            )
        )
        assertEquals(
            ThreadLoadFallbackState(
                statusCode = 410,
                shouldTryArchiveFallback = true,
                shouldTryOfflineFallback = false,
                shouldThrowWhenArchiveNotFound = true
            ),
            resolveThreadLoadFallbackState(
                error = NetworkException("Gone", statusCode = 410),
                allowOfflineFallback = false
            )
        )
        assertEquals(
            "Archive fallback timed out for threadId=123 after 20000ms",
            buildArchiveFallbackTimeoutMessage("123", 20_000L)
        )
        val page = ThreadPage(
            threadId = "123",
            boardTitle = "may/b",
            expiresAtLabel = null,
            deletedNotice = null,
            posts = emptyList()
        )
        assertEquals(
            ArchiveFallbackOutcome.Success(page, "https://may.2chan.net/b/res/123.htm"),
            resolveArchiveThreadFetchOutcome(
                page = page,
                error = null,
                threadUrl = "https://may.2chan.net/b/res/123.htm"
            )
        )
        assertEquals(
            ArchiveFallbackOutcome.NotFound,
            resolveArchiveThreadFetchOutcome(
                page = null,
                error = NetworkException("Gone", statusCode = 410),
                threadUrl = "https://may.2chan.net/b/res/123.htm"
            )
        )
        assertEquals(
            ArchiveFallbackOutcome.NoMatch,
            resolveArchiveThreadFetchOutcome(
                page = null,
                error = IllegalStateException("boom"),
                threadUrl = "https://may.2chan.net/b/res/123.htm"
            )
        )
        assertEquals(
            "Archive search failed for 123: boom",
            buildArchiveSearchFailureLogMessage("123", IllegalStateException("boom"))
        )
        assertEquals(
            "Archive refresh succeeded for 123",
            buildArchiveRefreshSuccessLogMessage("123")
        )
        assertEquals(
            ThreadArchiveFallbackPlan(
                scope = ArchiveSearchScope(server = "may", board = "b"),
                queryCandidates = listOf("123", "thread title")
            ),
            buildThreadArchiveFallbackPlan(
                threadId = "123",
                threadTitle = " thread   title ",
                boardUrl = "https://dec.2chan.net/img/futaba.php",
                threadUrlOverride = "https://may.2chan.net/b/res/123.htm"
            )
        )
        assertEquals(
            "https://may.2chan.net/b/res/123.htm",
            resolveArchiveFallbackMatchUrl(
                items = listOf(
                    ArchiveSearchItem(
                        threadId = "123",
                        server = "may",
                        board = "b",
                        htmlUrl = "https://may.2chan.net/b/res/123.htm",
                        uploadedAt = 2L
                    ),
                    ArchiveSearchItem(
                        threadId = "123",
                        server = "may",
                        board = "b",
                        htmlUrl = "https://may.2chan.net/b/res/122.htm",
                        uploadedAt = 1L
                    )
                ),
                threadId = "123"
            )
        )
        assertEquals(
            ArchiveFallbackAttemptState(
                outcome = ArchiveFallbackOutcome.Success(page, "https://may.2chan.net/b/res/123.htm"),
                successLogMessage = "Archive refresh succeeded for 123"
            ),
            resolveArchiveFallbackAttemptState(
                threadId = "123",
                threadUrl = "https://may.2chan.net/b/res/123.htm",
                page = page,
                error = null
            )
        )
        assertEquals(
            ArchiveFallbackAttemptState(
                outcome = ArchiveFallbackOutcome.NotFound,
                successLogMessage = null
            ),
            resolveArchiveFallbackAttemptState(
                threadId = "123",
                threadUrl = "https://may.2chan.net/b/res/123.htm",
                page = null,
                error = NetworkException("Gone", statusCode = 410)
            )
        )
        assertEquals(
            ThreadLoadPostArchiveDecision.TryOffline,
            resolveThreadLoadPostArchiveDecision(
                primaryError = NetworkException("HTTP error", statusCode = 404),
                fallbackState = resolveThreadLoadFallbackState(
                    error = NetworkException("HTTP error", statusCode = 404),
                    allowOfflineFallback = true
                ),
                archiveOutcome = ArchiveFallbackOutcome.NotFound
            )
        )
        val archiveFailureDecision = resolveThreadLoadPostArchiveDecision(
            primaryError = NetworkException("Gone", statusCode = 410),
            fallbackState = resolveThreadLoadFallbackState(
                error = NetworkException("Gone", statusCode = 410),
                allowOfflineFallback = false
            ),
            archiveOutcome = ArchiveFallbackOutcome.NotFound
        )
        assertTrue(archiveFailureDecision is ThreadLoadPostArchiveDecision.Fail)
        val archiveError = archiveFailureDecision.error
        assertTrue(archiveError is NetworkException)
        assertEquals("Gone", archiveError.message)
        assertEquals(410, archiveError.statusCode)
        assertEquals(
            ThreadLoadPostArchiveDecision.UseArchive(
                page = page,
                threadUrl = "https://may.2chan.net/b/res/123.htm"
            ),
            resolveThreadLoadPostArchiveDecision(
                primaryError = IllegalStateException("x"),
                fallbackState = resolveThreadLoadFallbackState(
                    error = IllegalStateException("x"),
                    allowOfflineFallback = true
                ),
                archiveOutcome = ArchiveFallbackOutcome.Success(
                    page = page,
                    threadUrl = "https://may.2chan.net/b/res/123.htm"
                )
            )
        )
        assertEquals(
            ThreadLoadPostOfflineDecision.UseOffline(page),
            resolveThreadLoadPostOfflineDecision(
                primaryError = IllegalStateException("x"),
                offlinePage = page
            )
        )
        val offlineFailureDecision = resolveThreadLoadPostOfflineDecision(
            primaryError = IllegalStateException("x"),
            offlinePage = null
        )
        assertTrue(offlineFailureDecision is ThreadLoadPostOfflineDecision.Fail)
        assertEquals("x", (offlineFailureDecision.error as IllegalStateException).message)
    }

}
