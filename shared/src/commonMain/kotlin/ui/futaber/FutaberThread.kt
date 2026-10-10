package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.toCompatPlainText
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import com.valoser.futacha.shared.ui.isIosReviewPlatform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import com.valoser.futacha.shared.audio.createTextSpeaker
import com.valoser.futacha.shared.compat.CompatImageNgSource
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.appliesToThreadImage
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.ui.board.FutachaImageNgRegistration
import com.valoser.futacha.shared.ui.board.ThreadImageSearchDialog
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.compat.collectCompatImagePhashHiddenPostNos
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.board.FutachaPageSaveDialog
import com.valoser.futacha.shared.ui.board.FutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.LocalFutachaSharedFeatures
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.ui.board.PosterIdLabel
import com.valoser.futacha.shared.ui.board.ThreadTreeNode
import com.valoser.futacha.shared.ui.board.buildThreadTreeNodes
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtSaveFlow
import com.valoser.futacha.shared.ui.board.DEFAULT_DEL_REASON_CODE
import com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository
import com.valoser.futacha.shared.ui.board.resolveThreadLoadFallbackState
import com.valoser.futacha.shared.ui.board.buildThreadActionFailureMessage
import com.valoser.futacha.shared.ui.board.ThreadActionRunResult
import com.valoser.futacha.shared.ui.board.requireWritableThreadBoard
import com.valoser.futacha.shared.ui.board.performThreadAction
import com.valoser.futacha.shared.ui.board.buildPosterIdLabels
import com.valoser.futacha.shared.ui.board.messageHtmlToLines
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.ui.util.ThreadDrawerBackGestureHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/** What the thread screen currently holds. The page survives a failed refresh. */
internal data class FutaberThreadLoad(
    val page: ThreadPage? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class, ExperimentalFoundationApi::class)
@Composable
internal fun FutaberThreadScreen(
    board: BoardSummary,
    ref: FutaberThreadRef,
    repository: BoardRepository,
    isTabbed: Boolean,
    onToggleTab: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onOpenManage: () -> Unit,
    onOpenPost: (quote: String) -> Unit,
    refreshSignal: Int,
    quoteMode: Boolean,
    onQuoteLine: (line: String) -> Unit,
    onOpenDrawer: () -> Unit,
    drawerGestureEnabled: Boolean,
    onPageLoaded: (List<Post>) -> Unit,
    /** Called when the page just loaded is the live thread (not an archived copy), so its history row can be marked alive. */
    onLivePageConfirmed: () -> Unit = {},
    onScrollSettled: (index: Int, offset: Int, postId: String?, total: Int) -> Unit,
    ngWords: List<String>,
    ngHeaders: List<String>,
    ngRules: List<CompatNgRule>,
    onChangeNgWords: (List<String>) -> Unit,
    onChangeNgHeaders: (List<String>) -> Unit,
    onBack: () -> Unit,
    mediaServices: FutaberMediaServices? = null,
    /** Set while a saved copy is shown: the thread may be older than the live one, and the visit is not recorded. */
    offlineNotice: String? = null,
    /** The delete key of the write settings, used by "レス削除". */
    deleteKey: String = "",
    /** The numbers of the posts this person wrote in this thread ("レスを抽出" → 自分の書き込み). */
    selfPostIds: Set<String> = emptySet(),
    /**
     * The auto-saved copy of this thread, shown (with a notice) when the live page cannot be read and no page is on
     * screen; null when there is none to fall back to.
     */
    loadOfflineCopy: (suspend () -> ThreadPage?)? = null,
    /** True while something covers the thread (the writing screen, the settings): the auto-scroll, the volume keys and the read-aloud wait. */
    paused: Boolean = false,
    tabStrip: @Composable () -> Unit = {}
) {
    val colors = LocalFutaberColors.current
    var load by remember(board.id, ref.threadId) { mutableStateOf(FutaberThreadLoad()) }
    var refreshTick by remember(board.id, ref.threadId) { mutableIntStateOf(0) }
    // One list state per thread: switching threads (tabs, history) must not carry the previous thread's scroll position over.
    val listState = key(board.id, ref.threadId) { rememberLazyListState() }
    val scope = rememberCoroutineScope()
    // On iOS the left edge of this screen opens the operation menu (below), so the system back swipe stays off there.
    PlatformBackHandler(iosEdgeGestureEnabled = !isIosReviewPlatform(), onBack = onBack)
    var filter by remember(board.id, ref.threadId) { mutableStateOf(FutaberViewFilter()) }
    var extractOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // The menu of a long-pressed picture ("画像検索" / "画像NGに登録"), apart from the menu of the post.
    var imagePost by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    var imageSearchUrl by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var imageNgTarget by remember(board.id, ref.threadId) { mutableStateOf<Pair<Post, String>?>(null) }
    var autoScrolling by remember(board.id, ref.threadId) { mutableStateOf(false) }

    // The page that was on screen before the last reload changed it ("更新前に戻す"); null when nothing changed.
    var previousPage by remember(board.id, ref.threadId) { mutableStateOf<ThreadPage?>(null) }
    val shareText = com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher()
    val openInBrowser = com.valoser.futacha.shared.util.rememberUrlLauncher()
    // Set while a dead thread is shown from an archive: the thread fell off the board (404/410) and an archive had it.
    var archiveNotice by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    // Set while the auto-saved copy is shown because the live page could not be read (no connection, a timeout).
    var offlineCopyNotice by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    val archiveSearchJson = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    val archiveHttpClient = mediaServices?.httpClient
    LaunchedEffect(board.id, board.url, ref.threadId, refreshTick, refreshSignal) {
        load = load.copy(isLoading = true, errorMessage = null)
        try {
            val fetched = repository.getThreadContent(board.url, ref.threadId)
            // A page the parser cut short, or one with fewer posts than the tabs of the other modes expect, is filled in
            // from the archives (only the shared repository does; others return it as it is).
            val content = (repository as? FutachaSharedBoardRepository)?.let { shared ->
                try {
                    shared.supplement(futaberCompatThreadUrl(board, ref.threadId), fetched)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    fetched
                }
            } ?: fetched
            archiveNotice = null
            offlineCopyNotice = null
            val shown = load.page
            // Only a reload that changed the posts leaves something to go back to.
            previousPage = if (shown != null && shown.posts.size != content.page.posts.size) shown else previousPage
            load = FutaberThreadLoad(page = content.page, isLoading = false)
            onPageLoaded(content.page.posts)
            onLivePageConfirmed()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // A thread that has fallen off the board (404/410) is looked up in the archives ふたちゃ and としあき(仮)
            // use; the page on screen, if any, is kept when none has it.
            val fallback = resolveThreadLoadFallbackState(error, allowOfflineFallback = true)
            val archived = if (fallback.shouldTryArchiveFallback) {
                futaberArchivedThread(archiveHttpClient, repository, ref.threadId, board.url, archiveSearchJson)
            } else null
            if (archived != null) {
                archiveNotice = "スレッドは落ちました（過去ログから表示中）"
                offlineCopyNotice = null
                load = FutaberThreadLoad(page = archived, isLoading = false)
                onPageLoaded(archived.posts)
            } else {
                // No connection, a timeout or a server error: with nothing on screen yet, the auto-saved copy is shown
                // (as in ふたちゃ); a page already on screen is kept as it is.
                val copy = if (load.page == null && fallback.shouldTryOfflineFallback && loadOfflineCopy != null) {
                    try {
                        withTimeoutOrNull(FUTABER_OFFLINE_COPY_TIMEOUT_MILLIS) { loadOfflineCopy() }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        null
                    }
                } else null
                if (copy != null && copy.posts.isNotEmpty()) {
                    offlineCopyNotice = FUTABER_AUTO_SAVED_COPY_NOTICE
                    load = FutaberThreadLoad(page = copy, isLoading = false)
                } else {
                    load = load.copy(isLoading = false, errorMessage = futaberFriendlyLoadError(error, "スレッド"))
                }
            }
        }
    }
    val posts = load.page?.posts.orEmpty()
    val idLabels = remember(posts) { buildPosterIdLabels(posts) }
    val replyIndex = remember(posts) { futaberReplyIndex(posts) }
    var menuOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var pendingScrollOrdinal by remember(board.id, ref.threadId) { mutableStateOf<Int?>(null) }
    val platformContext = LocalPlatformContext.current
    val reader = remember(board.id, ref.threadId) {
        FutaberReadAloud(scope, { createTextSpeaker(platformContext).asFutaberEngine() })
    }
    // Leaving the screen, or locking the app, stops the voice and releases the speaker.
    DisposableEffect(reader) { onDispose { reader.dispose() } }
    val appUnlocked = LocalFutachaAppUnlocked.current
    LaunchedEffect(appUnlocked) { if (!appUnlocked) reader.stop() }
    var markTarget by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var actionPost by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    // The poster's ID (else the name) of the post whose "NG登録" opened the NG dialog, ready to add.
    var ngDialogInitialHeader by remember(board.id, ref.threadId) { mutableStateOf("") }
    val longPressHaptic = rememberFutaberLongPressHaptic()
    var remoteConfirm by remember(board.id, ref.threadId) { mutableStateOf<Pair<FutaberRemoteAction, Post>?>(null) }
    var remoteNotice by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var remoteBusy by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // Sends the action through the shared repository calls ふたちゃ uses; the result is shown as a notice.
    val runRemoteAction: (FutaberRemoteAction, Post) -> Unit = { action, target ->
        if (!remoteBusy) {
            remoteBusy = true
            remoteNotice = "処理中です…"
            scope.launch {
                val result = performThreadAction {
                    requireWritableThreadBoard(board.url)
                    when (action) {
                        FutaberRemoteAction.Saidane -> repository.voteSaidane(board.url, ref.threadId, target.id)
                        FutaberRemoteAction.DeleteOwn -> repository.deleteByUser(
                            board = board.url, threadId = ref.threadId, postId = target.id,
                            password = deleteKey, imageOnly = false
                        )
                        FutaberRemoteAction.Report -> repository.requestDeletion(
                            board.url, ref.threadId, target.id, DEFAULT_DEL_REASON_CODE
                        )
                    }
                }
                remoteBusy = false
                remoteNotice = when (result) {
                    is ThreadActionRunResult.Success -> {
                        if (action != FutaberRemoteAction.Report) refreshTick += 1
                        action.successMessage
                    }
                    is ThreadActionRunResult.Failure -> buildThreadActionFailureMessage(action.failurePrefix, result.error)
                }
            }
        }
    }
    PlatformBackHandler(enabled = actionPost != null) { actionPost = null }
    PlatformBackHandler(enabled = extractOpen) { extractOpen = false }
    PlatformBackHandler(enabled = imagePost != null) { imagePost = null }
    // The auto-scroll (an extension): the same loop ふたちゃ and としあき(仮) run, with the amount and the
    // interval they share. It waits while the app is in the background and while a finger drags the list,
    // reloads at the bottom, and stops when the thread is gone.
    FutaberVolumeKeyEffect(enabled = LocalFutaberDisplaySettings.current.extVolumeKeys && !paused, listState = listState)
    // Behind the writing screen or the settings the thread neither scrolls by itself nor reads aloud.
    LaunchedEffect(paused) { if (paused) reader.stop() }
    FutaberAutoScrollEffect(
        active = autoScrolling && !paused,
        pixel = LocalFutaberDisplaySettings.current.autoScrollPixel,
        intervalMillis = LocalFutaberDisplaySettings.current.autoScrollSpeedMillis.toLong(),
        isDead = archiveNotice != null,
        listState = listState,
        onReload = { refreshTick += 1 },
        onStop = { autoScrolling = false }
    )
    var ngDisabled by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var ngDialogOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var mediaOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var saveOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var mhtSaveOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    val mhtLibrary = remember(mediaServices) {
        mediaServices?.fileSystem?.let { FutaberMhtLibrary(it, mediaServices.httpClient) }
    }
    val display = LocalFutaberDisplaySettings.current
    var smallImages by remember(board.id, ref.threadId, display.smallImages) { mutableStateOf(display.smallImages) }
    // NG is matched on the thread's own keys, so rules made in other modes hide the same posts.
    // Pictures that look like a registered NG image (the perceptual-hash rules shared with the other modes) are found
    // by hashing the thread's pictures; the posts are hidden as the hashes arrive.
    val tabKeyForNg = futaberCompatTabKey(board, ref.threadId)
    val boardKeyForNg = futaberCompatBoardKey(board)
    val phashRules = remember(ngRules, tabKeyForNg, boardKeyForNg) {
        ngRules.filter { it.kind == CompatNgKind.THREAD_IMAGE_PHASH && it.appliesToThreadImage(boardKeyForNg, tabKeyForNg) }
    }
    val sharedPreferencesState = (mediaServices?.store?.preferences ?: kotlinx.coroutines.flow.flowOf(emptyMap<String, String>()))
        .collectAsState(emptyMap<String, String>())
    val sharedPreferences by sharedPreferencesState
    val markedPostNos = remember(sharedPreferences[com.valoser.futacha.shared.compat.MANUAL_POST_MARKS_KEY], board, ref.threadId) {
        com.valoser.futacha.shared.compat.decodeManualPostMarks(sharedPreferences[com.valoser.futacha.shared.compat.MANUAL_POST_MARKS_KEY])
            .filter { it.threadUrl == com.valoser.futacha.shared.compat.manualMarkThreadUrl(futaberHistoryThreadUrl(board, ref.threadId)) }.mapTo(mutableSetOf()) { it.postNo }
    }
    // The shared features the borrowed screens (image search, image NG, page save) read; one instance per services/repository.
    val sharedFeatures = remember(mediaServices, repository) {
        mediaServices?.let {
            FutachaSharedFeatures(
                store = it.store,
                preferencesState = sharedPreferencesState,
                httpClient = it.httpClient,
                repository = repository,
                fileSystem = it.fileSystem,
                cookieRepository = it.cookieRepository,
                appVersion = "",
                openSettings = {}
            )
        }
    }
    val phashThreshold = sharedPreferences[futaberImageNgThresholdKey]?.toIntOrNull() ?: CompatImagePhash.DEFAULT_THRESHOLD
    var phashHidden by remember(board.id, ref.threadId) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(load.page, phashRules, phashThreshold, mediaServices) {
        val page = load.page
        if (page == null || phashRules.isEmpty() || mediaServices == null) {
            phashHidden = emptySet()
            return@LaunchedEffect
        }
        // Building the snapshot walks every post, so it is not done on the main thread.
        val snapshotPosts = withContext(AppDispatchers.parsing) { page.toCompatThreadSnapshot(tabKeyForNg, 0).posts }
        collectCompatImagePhashHiddenPostNos(
            mediaServices.httpClient, mediaServices.store, snapshotPosts,
            phashRules, phashThreshold
        ) { phashHidden = it }
    }
    // The word / header / rule part is worked out apart from the picture hashes, which arrive one batch after another:
    // a new batch of hashes must not match every post against every NG word again. It is also worked out off the main
    // thread (a long NG list over a long thread takes seconds): what the last result hid stays hidden meanwhile, the
    // posts it did not know wait, and with no earlier result the list waits for the first one (a small job is done at once).
    val ngPrepared = remember(ngWords, ngHeaders, ngRules, tabKeyForNg, boardKeyForNg) {
        futaberNgPrepare(ngWords, ngHeaders, ngRules, tabKeyForNg, boardKeyForNg)
    }
    var ngSnapshot by remember(board.id, ref.threadId) { mutableStateOf<FutaberNgSnapshot?>(null) }
    val ngQuick = remember(posts, ngPrepared) { futaberNgQuick(ngSnapshot, posts, ngPrepared) }
    LaunchedEffect(posts, ngPrepared) {
        val hiddenNow = if (ngQuick.complete) ngQuick.hidden else {
            val context = currentCoroutineContext()
            val previous = ngSnapshot
            withContext(AppDispatchers.parsing) {
                futaberNgResolve(previous, posts, ngPrepared) { context.ensureActive() }
            }
        }
        ngSnapshot = FutaberNgSnapshot(posts, ngPrepared, hiddenNow)
    }
    val ngCurrent = ngSnapshot?.takeIf { it.posts === posts && it.prepared === ngPrepared }
    val ngBaseHidden = ngCurrent?.hidden ?: ngQuick.hidden
    val ngWaiting = ngCurrent == null && ngQuick.hold && !ngDisabled
    val hidden = remember(ngBaseHidden, phashHidden) {
        if (phashHidden.isEmpty()) ngBaseHidden else ngBaseHidden + phashHidden
    }
    val activeHidden = if (ngDisabled) emptySet() else hidden
    // The tree view (an extension, off by default): replies listed under the post they quote.
    val treeNodes by produceState(emptyList<ThreadTreeNode>(), posts, display.extTree) {
        value = if (display.extTree) buildThreadTreeNodes(posts) else emptyList()
    }
    // The search word takes effect a moment after the last key, so a fast typist does not search after every letter.
    val appliedQuery = rememberFutaberDebouncedQuery(filter.query)
    val appliedFilter = remember(filter, appliedQuery) { if (filter.query == appliedQuery) filter else filter.copy(query = appliedQuery) }
    // Each post's text for the search, worked out once off the main thread (when the search menu opens, or a search or
    // the "URLを含むレス" extract is on) and kept while the thread only grows.
    val wantPostTexts = menuOpen || filter.query.isNotBlank() || filter.extract == FutaberExtract.Url
    val postTexts by produceState<FutaberPostTexts?>(null, posts, wantPostTexts) {
        if (!wantPostTexts || posts.isEmpty()) return@produceState
        val previous = value
        if (previous != null && previous.posts === posts) return@produceState
        val context = currentCoroutineContext()
        value = withContext(AppDispatchers.parsing) { futaberBuildPostTexts(previous, posts) { context.ensureActive() } }
    }
    val rows = remember(posts, appliedFilter, replyIndex, activeHidden, display.manyRepliesThreshold, selfPostIds, display.extTree, treeNodes, postTexts, ngWaiting) {
        if (ngWaiting) emptyList() else {
            val listed = futaberVisibleRows(
                posts, appliedFilter, replyIndex, activeHidden, display.manyRepliesThreshold, selfPostIds, postTexts
            )
            if (display.extTree) futaberApplyTree(listed, treeNodes) else listed
        }
    }
    val latestRows by rememberUpdatedState(rows)
    val liveNgPrepared by rememberUpdatedState(ngPrepared)
    val liveNgDisabled by rememberUpdatedState(ngDisabled)
    val livePhashRules by rememberUpdatedState(phashRules)
    // Back to where reading stopped, once per opened thread, after its first page arrives.
    var restored by remember(board.id, ref.threadId) { mutableStateOf(false) }
    val postsReady = posts.isNotEmpty() && !ngWaiting
    LaunchedEffect(postsReady, restored) {
        if (!postsReady || restored) return@LaunchedEffect
        futaberRestoreRowPosition(posts, latestRows, ref)
            ?.let { listState.scrollToItem(it, ref.resumeOffset) }
        restored = true
    }
    // The position is saved after scrolling settles, never while a restore is pending.
    val latestSettled by rememberUpdatedState(onScrollSettled)
    val latestPosts by rememberUpdatedState(posts)
    val latestNarrowed by rememberUpdatedState(filter.isActive)
    // The newest position the list reached and that is not saved yet. Leaving the screen writes it, so the last
    // half second of reading is not lost.
    val pendingSave = remember(board.id, ref.threadId) { FutaberPendingScrollSave() }
    LaunchedEffect(restored) {
        if (!restored) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .onEach { (index, offset) ->
                // A search or "many replies" list shows other rows than the thread, so nothing is saved then.
                // The saved number is the post's own number, which stays valid when NG hides posts.
                pendingSave.record(futaberScrollPositionToSave(latestRows, index, offset, latestPosts.size, latestNarrowed))
            }
            .debounce(500)
            .collect {
                pendingSave.take()?.let { latestSettled(it.ordinal, it.offset, it.postId, it.total) }
            }
    }
    // Taken now, not at dispose time: by then the callback and the state already belong to the next thread.
    DisposableEffect(board.id, ref.threadId) {
        val holder = pendingSave
        val save = onScrollSettled
        onDispose { holder.take()?.let { save(it.ordinal, it.offset, it.postId, it.total) } }
    }
    // Posts that arrived since the previous visit; the baseline is what the history said on opening.
    val firstNewOrdinal = remember(posts.size, ref.seenCount) { futaberFirstNewIndex(ref.seenCount, posts.size) }
    // Scrolling to a post number waits until any narrowing is cleared, then lands on the next listed row.
    LaunchedEffect(pendingScrollOrdinal, rows, filter) {
        val target = pendingScrollOrdinal ?: return@LaunchedEffect
        if (filter.isActive) return@LaunchedEffect
        futaberRowPositionAtOrAfter(rows, target)?.let { listState.animateScrollToItem(it) }
        pendingScrollOrdinal = null
    }
    PlatformBackHandler(enabled = menuOpen) { menuOpen = false }
    val clipboard = LocalClipboardManager.current
    var quoteStack by remember(board.id, ref.threadId) { mutableStateOf(emptyList<FutaberQuoteLayer>()) }
    // A refreshed thread can drop posts a bubble showed, so bubbles are closed with it.
    LaunchedEffect(refreshTick, refreshSignal) { quoteStack = emptyList() }
    PlatformBackHandler(enabled = quoteStack.isNotEmpty()) { quoteStack = quoteStack.dropLast(1) }
    // A swipe in from the left edge opens the board list; while a bubble is open it must not.
    ThreadDrawerBackGestureHandler(enabled = drawerGestureEnabled && quoteStack.isEmpty(), onOpenDrawer = onOpenDrawer)
    val openQuote: (FutaberQuoteKind, List<Post>, FutaberAnchor) -> Unit = { kind, related, anchor ->
        val shown = related.filter { it.id !in activeHidden }
        if (shown.isNotEmpty()) quoteStack = quoteStack + FutaberQuoteLayer(kind, shown, anchor)
    }

    // On iOS a swipe in from the left edge opens the operation menu directly, as in the original
    // (Android keeps the board list there: its left edge is the system's back gesture).
    // Not while something is already open on top (a bubble, the menu, a sheet): the edge swipe then closes that
    // (the system back swipe is on for those handlers), and must not open the menu at the same time.
    val edgeMenuAllowed = quoteStack.isEmpty() && !menuOpen && actionPost == null && !extractOpen && imagePost == null
    val edgeMenuModifier = if (isIosReviewPlatform()) {
        Modifier.pointerInput(edgeMenuAllowed) {
            if (!edgeMenuAllowed) return@pointerInput
            val edgePx = 20.dp.toPx()
            val triggerPx = 56.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.position.x > edgePx) return@awaitEachGesture
                var dx = 0f
                var dy = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed) return@awaitEachGesture
                    dx += change.positionChange().x
                    dy += change.positionChange().y
                    if (kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f && kotlin.math.abs(dy) > 24.dp.toPx()) return@awaitEachGesture
                    if (dx > triggerPx) {
                        change.consume()
                        menuOpen = true
                        return@awaitEachGesture
                    }
                }
            }
        }
    } else Modifier
    Column(Modifier.fillMaxSize().background(colors.threadBackground).then(edgeMenuModifier)) {
        Row(
            Modifier.fillMaxWidth().background(colors.topBar).statusBarsPadding().height(FUTABER_TOP_BAR_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("futaber-thread-back")) {
                FutaberIcon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "カタログへ戻る", tint = colors.onTopBar)
            }
            Text(
                text = ref.title,
                color = colors.topBarTitle,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag("futaber-thread-title")
            )
            Box(Modifier.width(48.dp))
        }
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        (offlineNotice ?: archiveNotice ?: offlineCopyNotice)?.let { notice ->
            Text(
                text = notice, color = colors.onAction, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(colors.accent).padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("futaber-offline-notice")
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                // The first NG result is still being worked out: showing the posts now could show ones it hides.
                ngWaiting -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
                posts.isNotEmpty() -> if (rows.isEmpty()) {
                    FutaberMessage("条件に合うレスはありません")
                } else LazyColumn(Modifier.fillMaxSize().testTag("futaber-thread-list"), state = listState) {
                    itemsIndexed(rows, key = { _, row -> "${row.ordinal}:${row.post.id}" }) { _, row ->
                        val post = row.post
                        if (row.ordinal == firstNewOrdinal) {
                            Text(
                                "ここから新着", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().background(colors.catalogGap)
                                    .padding(horizontal = 10.dp, vertical = 4.dp).testTag("futaber-new-marker")
                            )
                        }
                        FutaberPostRow(
                            ordinal = row.ordinal,
                            post = post,
                            idLabel = idLabels[post.id],
                            replyCount = futaberReplyCount(post, replyIndex),
                            highlighted = reader.currentPostId == post.id,
                            manuallyMarked = post.id in markedPostNos,
                            onSaidaneTap = if (display.extQuickSaidane && archiveNotice == null && offlineNotice == null && offlineCopyNotice == null) ({ runRemoteAction(FutaberRemoteAction.Saidane, post) }) else null,
                            onNgTap = if (display.extQuickNg) ({ ngDialogInitialHeader = futaberNgHeaderOf(post); ngDialogOpen = true }) else null,
                            smallImages = smallImages,
                            depth = row.depth,
                            onImageLongPress = if (futaberImageUrlOf(post) != null) {
                                { longPressHaptic(); imagePost = post }
                            } else null,
                            onIdTap = if (display.extIdTap && !post.posterId.isNullOrBlank()) {
                                { filter = filter.copy(sameId = post.posterId.orEmpty()) }
                            } else null,
                            onLongPress = { longPressHaptic(); actionPost = post },
                            quoteMode = quoteMode,
                            onQuoteLine = onQuoteLine,
                            onQuoteTap = { line, anchor ->
                                openQuote(FutaberQuoteKind.Source, futaberPostsById(posts, futaberQuoteTargetIds(post, line)), anchor)
                            },
                            onRepliesTap = { anchor ->
                                openQuote(FutaberQuoteKind.Replies, replyIndex[post.id].orEmpty(), anchor)
                            }
                        )
                        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
                    }
                }
                load.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
                load.errorMessage != null -> FutaberMessage(
                    text = load.errorMessage.orEmpty(),
                    actionLabel = "再読み込み",
                    onAction = { refreshTick += 1 }
                )
                else -> FutaberMessage("レスがありません")
            }
            if (load.isLoading && posts.isNotEmpty()) {
                CircularProgressIndicator(
                    Modifier.align(Alignment.TopEnd).padding(8.dp).width(20.dp).height(20.dp),
                    color = colors.accent,
                    strokeWidth = 2.dp
                )
            }
            if (load.errorMessage != null && posts.isNotEmpty()) {
                Text(
                    text = load.errorMessage.orEmpty(),
                    color = colors.onAction,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .background(colors.accent).fillMaxWidth().padding(8.dp)
                )
            }
            if (reader.isReading) {
                FutaberReadingChip(
                    onPrevious = { reader.skip(-1) }, onNext = { reader.skip(1) }, onStop = reader::stop,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            if (autoScrolling) {
                FutaberAutoScrollChip(onStop = { autoScrolling = false }, modifier = Modifier.align(Alignment.BottomEnd))
            }
            if (extractOpen) {
                FutaberPostActionSheet(
                    title = "レスを抽出",
                    actions = FutaberExtract.entries.map { kind ->
                        FutaberPostAction("extract-${kind.id}", kind.label) {
                            filter = filter.copy(extract = kind)
                            extractOpen = false
                        }
                    } + listOfNotNull(
                        if (filter.extract != null) FutaberPostAction("extract-clear", "抽出を解除") {
                            filter = filter.copy(extract = null)
                            extractOpen = false
                        } else null
                    ) + FutaberPostAction("cancel", "キャンセル") { extractOpen = false },
                    onDismiss = { extractOpen = false }
                )
            }
            reader.message?.let { notice ->
                Text(
                    text = notice,
                    color = colors.onAction,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .background(colors.accent).fillMaxWidth()
                        .clickable(onClickLabel = "閉じる", onClick = reader::clearMessage)
                        .padding(8.dp).testTag("futaber-reading-message")
                )
            }
            if (appliedFilter.isActive && !menuOpen) {
                FutaberFilterChip(
                    filter = appliedFilter,
                    count = rows.size,
                    onClear = { filter = FutaberViewFilter() },
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
            if (menuOpen) {
                FutaberOperationMenu(
                    query = filter.query,
                    onQueryChange = { filter = filter.copy(query = it) },
                    resultCount = rows.size.takeIf { filter.query.isNotBlank() && appliedQuery == filter.query },
                    // The order of the original app's menu; the entries it does not have follow.
                    items = listOf(
                        FutaberMenuItem("copy-url", "URLをコピーする", Icons.Outlined.ContentCopy) {
                            clipboard.setText(AnnotatedString(futaberHistoryThreadUrl(board, ref.threadId)))
                            menuOpen = false
                        },
                        FutaberMenuItem(
                            "small-images", if (smallImages) "画像を元の大きさにする" else "画像を小さくする",
                            Icons.Outlined.PhotoSizeSelectSmall, active = smallImages
                        ) {
                            smallImages = !smallImages
                            menuOpen = false
                        },
                        FutaberMenuItem(
                            "many-replies", "返信が多いレスを表示", Icons.Outlined.Forum,
                            enabled = posts.isNotEmpty(), active = filter.repliesOnly
                        ) { filter = filter.copy(repliesOnly = !filter.repliesOnly); menuOpen = false },
                        FutaberMenuItem(
                            "read-aloud", if (reader.isReading) "読み上げを停止" else "読み上げを開始",
                            Icons.Outlined.RecordVoiceOver, enabled = rows.isNotEmpty(), active = reader.isReading
                        ) {
                            if (reader.isReading) reader.stop()
                            else {
                                // The reader numbers the posts as they were listed when it started; the list may change while
                                // it reads (a search, NG), so each post is looked up in the rows of the moment.
                                val readPosts = rows.map { it.post }
                                reader.start(readPosts, listState.firstVisibleItemIndex) { index ->
                                    futaberRowPositionOfPost(latestRows, readPosts.getOrNull(index)?.id)
                                        ?.let { listState.animateScrollToItem(it) }
                                }
                            }
                            menuOpen = false
                        },
                        FutaberMenuItem("live-read-aloud", "新着を待って読み上げ", Icons.Outlined.RecordVoiceOver,
                            enabled = !reader.isReading && !filter.isActive && rows.isNotEmpty() && offlineNotice == null && offlineCopyNotice == null && archiveNotice == null) {
                            reader.start(rows.map { it.post }, listState.firstVisibleItemIndex, loadNewPosts = {
                                if (repository.probeThreadGone(futaberHistoryThreadUrl(board, ref.threadId))) null else {
                                    val content = repository.getThreadContent(board.url, ref.threadId)
                                    load = FutaberThreadLoad(page = content.page, isLoading = false)
                                    onPageLoaded(content.page.posts)
                                    val newPosts = content.page.posts
                                    val hiddenNow = if (liveNgDisabled) emptySet() else withContext(AppDispatchers.parsing) {
                                        futaberNgEvaluate(newPosts, liveNgPrepared)
                                    }
                                    var imageHidden = emptySet<String>()
                                    if (!liveNgDisabled && livePhashRules.isNotEmpty()) collectCompatImagePhashHiddenPostNos(
                                        mediaServices?.httpClient, mediaServices?.store,
                                        withContext(AppDispatchers.parsing) { content.page.toCompatThreadSnapshot(tabKeyForNg, 0).posts },
                                        livePhashRules, phashThreshold) { imageHidden = it }
                                    newPosts.filterNot { it.id in hiddenNow || it.id in imageHidden }
                                }
                            }) {
                                futaberRowPositionOfPost(latestRows, reader.currentPostId)?.let { index -> listState.animateScrollToItem(index) }
                            }
                            menuOpen = false
                        },
                        FutaberMenuItem(
                            "favorite", if (isFavorite) "お気に入り解除" else "お気に入りに追加",
                            if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                            enabled = offlineNotice == null, active = isFavorite
                        ) {
                            onToggleFavorite()
                            menuOpen = false
                        },
                        FutaberMenuItem(
                            "save", "スレッドを保存", Icons.Outlined.SaveAlt,
                            enabled = mediaServices != null && offlineNotice == null && offlineCopyNotice == null && posts.isNotEmpty()
                        ) {
                            saveOpen = true
                            menuOpen = false
                        },
                        FutaberMenuItem(
                            "mht-save", "MHTで保存", Icons.Outlined.ViewInAr,
                            enabled = mhtLibrary != null && offlineNotice == null && offlineCopyNotice == null && posts.isNotEmpty()
                        ) {
                            mhtSaveOpen = true
                            menuOpen = false
                        },
                        FutaberMenuItem("ng-edit", "NG編集", Icons.Outlined.Block) {
                            ngDialogOpen = true
                            menuOpen = false
                        },
                        FutaberMenuItem(
                            "ng-toggle", if (ngDisabled) "NGを有効に戻す" else "NGを無効にする", Icons.Outlined.VisibilityOff,
                            enabled = hidden.isNotEmpty() || ngDisabled, active = ngDisabled
                        ) { ngDisabled = !ngDisabled; menuOpen = false },
                        FutaberMenuItem(
                            "new", "新着へスクロール", Icons.Outlined.NewReleases, enabled = firstNewOrdinal != null
                        ) {
                            firstNewOrdinal?.let { ordinal ->
                                filter = FutaberViewFilter()
                                pendingScrollOrdinal = ordinal
                            }
                            menuOpen = false
                        },
                        FutaberMenuItem("share-url", "URLを共有", Icons.Outlined.IosShare) {
                            shareText(futaberHistoryThreadUrl(board, ref.threadId), "text/plain", null)
                            menuOpen = false
                        },
                        FutaberMenuItem("open-browser", "ブラウザで開く", Icons.Outlined.OpenInBrowser) {
                            openInBrowser(futaberHistoryThreadUrl(board, ref.threadId))
                            menuOpen = false
                        },
                        FutaberMenuItem(
                            "undo-reload", "更新前に戻す", Icons.AutoMirrored.Outlined.Undo, enabled = previousPage != null
                        ) {
                            previousPage?.let { earlier ->
                                load = load.copy(page = earlier)
                                previousPage = null
                            }
                            menuOpen = false
                        },
                        FutaberMenuItem(
                            "tab", if (isTabbed) "タブから外す" else "タブに追加", Icons.Outlined.Tab,
                            enabled = offlineNotice == null, active = isTabbed
                        ) {
                            onToggleTab()
                            menuOpen = false
                        },
                        FutaberMenuItem("write", "書き込む", Icons.Outlined.Edit) {
                            onOpenPost("")
                            menuOpen = false
                        }
                    ) + listOfNotNull(
                        // Extensions (off by default in the settings), after the entries the original app has.
                        if (display.extExtract) FutaberMenuItem(
                            "extract", "レスを抽出", Icons.Outlined.FilterList,
                            enabled = posts.isNotEmpty(), active = filter.extract != null
                        ) { extractOpen = true; menuOpen = false } else null,
                        if (display.extAutoScroll) FutaberMenuItem(
                            "auto-scroll", if (autoScrolling) "オートスクロールを停止" else "オートスクロール",
                            Icons.Outlined.KeyboardDoubleArrowDown, enabled = rows.isNotEmpty(), active = autoScrolling
                        ) { autoScrolling = !autoScrolling; menuOpen = false } else null
                    ),
                    onDismiss = { menuOpen = false }
                )
            }
            val markContext = mediaServices?.store?.let { store ->
                com.valoser.futacha.shared.ui.board.PostMarkContext(store, futaberHistoryThreadUrl(board, ref.threadId),
                    posts.map { it.id to it.messageHtml }, { no ->
                        actionPost = null
                        markTarget = null
                        filter = FutaberViewFilter()
                        scope.launch {
                            kotlinx.coroutines.yield()
                            futaberRowPositionOfPost(latestRows, no)?.let { listState.animateScrollToItem(it) }
                                ?: run { remoteNotice = "NG設定で、このレスは非表示です" }
                        }
                    })
            }
            val markedPostNo = markTarget
            if (markedPostNo != null && markContext != null) com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow {
                androidx.compose.material3.AlertDialog(onDismissRequest = { markTarget = null },
                    title = { Text("レスマーク") }, text = {
                        com.valoser.futacha.shared.ui.board.ManualPostMarkActions(markedPostNo, markContext)
                    }, confirmButton = { androidx.compose.material3.TextButton(onClick = { markTarget = null; actionPost = null }) { Text("閉じる") } })
            }
            actionPost?.let { target ->
                FutaberPostActionSheet(
                    title = "レス:${posts.indexOfFirst { it.id == target.id }.coerceAtLeast(0)}",
                    actions = futaberPostActions(
                        post = target,
                        onQuote = { quote -> actionPost = null; onOpenPost(quote) },
                        onCopy = { text ->
                            clipboard.setText(AnnotatedString(text))
                            actionPost = null
                        }
                    ) + FutaberPostAction("ng-register", "NG登録") {
                        // The ID is the most precise key; without one, the name (unless it is the board's default).
                        ngDialogInitialHeader = futaberNgHeaderOf(target)
                        actionPost = null
                        ngDialogOpen = true
                    } + FutaberPostAction("mark", "レスをマーク／一覧", enabled = markContext != null) {
                        markTarget = target.id
                        actionPost = null
                    } + FutaberRemoteAction.entries.map { action ->
                        val blocked = futaberRemoteActionBlockedReason(action, offlineNotice != null || offlineCopyNotice != null, deleteKey)
                        FutaberPostAction(action.id, action.sheetLabel) {
                            actionPost = null
                            when {
                                blocked != null -> remoteNotice = blocked
                                action.confirmTitle == null -> runRemoteAction(action, target)
                                else -> remoteConfirm = action to target
                            }
                        }
                    } + FutaberPostAction("cancel", "キャンセル") { actionPost = null },
                    onDismiss = { actionPost = null }
                )
            }
            imagePost?.let { target ->
                val url = futaberImageUrlOf(target)
                FutaberPostActionSheet(
                    title = "レス:${posts.indexOfFirst { it.id == target.id }.coerceAtLeast(0)}の画像",
                    actions = listOf(
                        FutaberPostAction("image-search", "画像検索", enabled = url != null && mediaServices != null) {
                            imagePost = null
                            imageSearchUrl = url
                        },
                        FutaberPostAction("image-ng", "画像NGに登録", enabled = url != null && mediaServices != null) {
                            imagePost = null
                            url?.let { imageNgTarget = target to it }
                        },
                        FutaberPostAction("cancel", "キャンセル") { imagePost = null }
                    ),
                    onDismiss = { imagePost = null }
                )
            }
            val searchUrl = imageSearchUrl
            if (searchUrl != null && mediaServices != null) {
                // The search screens read the compat palette; the picture viewer's mapping keeps them in this mode's colours.
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalCompatibilityPalette provides futaberMediaPalette(colors),
                    // The search targets chosen in the settings are read from the shared features.
                    LocalFutachaSharedFeatures provides sharedFeatures
                ) {
                    ThreadImageSearchDialog(searchUrl, mediaServices.httpClient, mediaServices.cookieRepository) { imageSearchUrl = null }
                }
            }
            val ngTarget = imageNgTarget
            if (ngTarget != null && sharedFeatures != null) {
                androidx.compose.runtime.CompositionLocalProvider(LocalCompatibilityPalette provides futaberMediaPalette(colors)) {
                    FutachaImageNgRegistration(
                        sharedFeatures, boardKeyForNg, CompatImageNgSource.THREAD, ngTarget.second,
                        "No.${ngTarget.first.id}", { imageNgTarget = null }
                    )
                }
            }
            remoteConfirm?.let { (action, target) ->
                FutaberRemoteActionDialog(
                    action = action,
                    postNumber = target.id,
                    onConfirm = { remoteConfirm = null; runRemoteAction(action, target) },
                    onDismiss = { remoteConfirm = null }
                )
            }
            remoteNotice?.let { notice ->
                Text(
                    text = notice,
                    color = colors.onAction,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .background(colors.accent).fillMaxWidth()
                        .clickable(onClickLabel = "閉じる") { remoteNotice = null }
                        .padding(8.dp).testTag("futaber-remote-notice")
                )
            }
            val savePage = load.page
            if (saveOpen && sharedFeatures != null && savePage != null) {
                // The shared page save: format choice, destination, progress and the saved-thread index.
                FutachaPageSaveDialog(
                    features = sharedFeatures,
                    page = savePage,
                    boardKey = futaberCompatBoardKey(board),
                    boardName = board.name,
                    boardUrl = futaberCanonicalBoardUrl(board),
                    title = ref.title,
                    onDismiss = { saveOpen = false }
                )
            }
            val mhtPage = load.page
            if (mhtSaveOpen && mhtLibrary != null && mhtPage != null) {
                FutaberMhtSaveFlow(mhtLibrary, board, ref, mhtPage, onDismiss = { mhtSaveOpen = false })
            }
            val mediaPage = load.page
            if (mediaOpen && mediaServices != null && mediaPage != null) {
                FutaberMediaOverlay(
                    board = board,
                    ref = ref,
                    page = mediaPage,
                    services = mediaServices,
                    // Posts hidden by NG (words, IDs, rules and look-alike pictures) leave the gallery and the viewer too.
                    hiddenPostNos = activeHidden,
                    onShowPost = { postId ->
                        // The post's own number, which stays valid when NG or a search narrows the list.
                        mediaPage.posts.indexOfFirst { it.id == postId }.takeIf { it >= 0 }?.let { ordinal ->
                            filter = FutaberViewFilter()
                            pendingScrollOrdinal = ordinal
                        }
                    },
                    onClose = { mediaOpen = false }
                )
            }
            if (ngDialogOpen) {
                FutaberNgDialog(
                    words = ngWords,
                    headers = ngHeaders,
                    hiddenCount = hidden.size,
                    onChangeWords = onChangeNgWords,
                    onChangeHeaders = onChangeNgHeaders,
                    onDismiss = { ngDialogOpen = false; ngDialogInitialHeader = "" },
                    initialHeader = ngDialogInitialHeader
                )
            }
            quoteStack.lastOrNull()?.let { layer ->
                FutaberQuoteOverlay(
                    layer = layer,
                    allPosts = posts,
                    idLabels = idLabels,
                    replyIndex = replyIndex,
                    onNested = openQuote,
                    onDismiss = { quoteStack = quoteStack.dropLast(1) }
                )
            }
        }
        tabStrip()
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        Row(
            Modifier.futaberFloatingBar(colors).height(FUTABER_BOTTOM_BAR_HEIGHT_DP.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (quoteStack.isNotEmpty()) {
                // While bubbles are open the bar only closes them: the fourth and fifth slots, as in the original.
                repeat(3) { Spacer(Modifier.weight(1f)) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { quoteStack = quoteStack.dropLast(1) },
                        modifier = Modifier.testTag("futaber-quote-close-one")
                    ) {
                        FutaberIcon(FutaberCloseBubbleIcon, contentDescription = "引用を1枚閉じる", tint = colors.icon)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = { quoteStack = emptyList() },
                        modifier = Modifier.testTag("futaber-quote-close-all")
                    ) {
                        FutaberIcon(FutaberCloseAllBubblesIcon, contentDescription = "引用をすべて閉じる", tint = colors.icon)
                    }
                }
            } else {
                // The original's order: images, bottom, menu, panel, refresh.
                IconButton(
                    onClick = { if (mediaServices != null && futaberMediaPostCount(posts) > 0) mediaOpen = true },
                    enabled = mediaServices != null && futaberMediaPostCount(posts) > 0,
                    modifier = Modifier.testTag("futaber-thread-gallery")
                ) {
                    FutaberIcon(
                        Icons.Outlined.Image, contentDescription = "画像一覧",
                        tint = if (mediaServices != null && futaberMediaPostCount(posts) > 0) colors.icon else colors.meta
                    )
                }
                // A tap goes to the last post; a long press goes to the first new one, as in the original.
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .combinedClickable(
                            onClickLabel = "末尾へ移動",
                            onLongClickLabel = "新着へスクロール",
                            onClick = { if (latestRows.isNotEmpty()) scope.launch { listState.animateScrollToItem(latestRows.lastIndex) } },
                            onLongClick = {
                                longPressHaptic()
                                firstNewOrdinal?.let { ordinal ->
                                    filter = FutaberViewFilter()
                                    pendingScrollOrdinal = ordinal
                                }
                            }
                        )
                        .testTag("futaber-thread-bottom"),
                    contentAlignment = Alignment.Center
                ) {
                    FutaberIcon(Icons.Outlined.VerticalAlignBottom, contentDescription = "末尾へ移動", tint = colors.icon)
                }
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag("futaber-thread-menu")) {
                    FutaberIcon(
                        Icons.Outlined.IosShare,
                        contentDescription = "操作メニュー",
                        tint = if (filter.isActive) colors.accent else colors.icon
                    )
                }
                // A tap opens the panel; a long press adds the thread to the tabs (or takes it out), as in the original.
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .combinedClickable(
                            role = Role.Button,
                            onClickLabel = "履歴とタブを開く",
                            onLongClickLabel = if (isTabbed) "このスレッドをタブから外す" else "このスレッドをタブに追加",
                            onLongClick = { if (offlineNotice == null) { longPressHaptic(); onToggleTab() } },
                            onClick = onOpenManage
                        )
                        .semantics { contentDescription = "履歴とタブ" }
                        .testTag("futaber-manage"),
                    contentAlignment = Alignment.Center
                ) {
                    FutaberIcon(FutaberTwoPaneIcon, contentDescription = null, tint = if (isTabbed) colors.accent else colors.icon)
                }
                IconButton(onClick = { refreshTick += 1 }, modifier = Modifier.testTag("futaber-thread-refresh")) {
                    FutaberIcon(Icons.Outlined.Refresh, contentDescription = "スレッドを更新", tint = colors.icon)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FutaberPostRow(
    ordinal: Int,
    post: Post,
    idLabel: PosterIdLabel?,
    replyCount: Int,
    onQuoteTap: (line: String, anchor: FutaberAnchor) -> Unit,
    onRepliesTap: (anchor: FutaberAnchor) -> Unit,
    highlighted: Boolean = false,
    manuallyMarked: Boolean = false,
    onSaidaneTap: (() -> Unit)? = null,
    onNgTap: (() -> Unit)? = null,
    smallImages: Boolean = false,
    /** Tree view indent level (0 outside the tree view). */
    depth: Int = 0,
    /** Set when tapping the poster's ID lists that poster's posts (an extension, off by default). */
    onIdTap: (() -> Unit)? = null,
    /** Set when a long press on the picture opens the picture's own menu (the rest of the post keeps the post's menu). */
    onImageLongPress: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    quoteMode: Boolean = false,
    onQuoteLine: (line: String) -> Unit = {}
) {
    val colors = LocalFutaberColors.current
    val display = LocalFutaberDisplaySettings.current
    val lines = remember(post.messageHtml) { messageHtmlToLines(post.messageHtml) }
    val titleLine = remember(post.subject, post.author, display.alwaysShowTitleAndName) { futaberPostTitleLine(post, display.alwaysShowTitleAndName) }
    Column(
        Modifier.fillMaxWidth()
            .background(if (highlighted) colors.catalogGap else androidx.compose.ui.graphics.Color.Transparent)
            .then(
                if (onLongPress == null) Modifier else Modifier
                    // A long press is the only gesture taken here, so taps on quotes and counters still reach them.
                    .pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) }
                    .semantics { onLongClick(label = "レスの操作を開く") { onLongPress(); true } }
            )
            .padding(
                start = 10.dp + (depth.coerceIn(0, FUTABER_TREE_MAX_INDENT_LEVELS) * 14).dp,
                end = 10.dp, top = 6.dp, bottom = 6.dp
            )
            .testTag("futaber-post")
    ) {
        if (manuallyMarked) Text("★ マーク", color = colors.body, fontSize = display.sp(12f))
        Row(verticalAlignment = Alignment.Top) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(ordinal.toString(), color = colors.ordinal, fontSize = display.sp(12f), fontWeight = FontWeight.Bold)
            Text(futaberTimestampWithoutId(post.timestamp), color = colors.meta, fontSize = display.sp(12f))
            if (replyCount > 0) {
                var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .onGloballyPositioned { bounds = it.boundsInRoot() }
                        .clickable(onClickLabel = "このレスへの返信を表示", role = Role.Button) {
                            bounds?.let { onRepliesTap(FutaberAnchor(it.left + it.width / 2f, it.top, it.bottom)) }
                        }
                        .semantics(mergeDescendants = true) { contentDescription = "返信$replyCount" }
                        // A thin outlined pill makes the count read as a control and gives it a larger touch area.
                        .border(1.dp, colors.separator, FutaberShapes.pill)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("futaber-reply-count")
                ) {
                    FutaberIcon(
                        Icons.Outlined.ChatBubbleOutline, contentDescription = null, tint = colors.ordinal,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(" $replyCount", color = colors.ordinal, fontSize = display.sp(12f))
                }
            }
            idLabel?.let {
                val posterId = post.posterId.orEmpty()
                Text(
                    it.text,
                    color = if (posterId.isBlank()) colors.meta else futaberPosterColor(posterId, colors.isDark),
                    fontSize = display.sp(12f),
                    fontWeight = if (it.highlight) FontWeight.Bold else FontWeight.Normal,
                    modifier = if (onIdTap == null) Modifier else Modifier
                        .clickable(onClickLabel = "同じIDのレスを表示", role = Role.Button, onClick = onIdTap)
                        .testTag("futaber-id-tap")
                )
            }
            // One item, so the number is never split across lines.
            Text("No.${post.id}", color = colors.meta, fontSize = display.sp(12f))
        }
        if (onNgTap != null) androidx.compose.material3.TextButton(onClick = onNgTap) { Text("NG", color = colors.action) }
        if (onSaidaneTap != null) androidx.compose.material3.TextButton(onClick = onSaidaneTap) {
            Text("そうだね ${futaberSaidaneCount(post.saidaneLabel) ?: 0}", color = colors.saidane)
        } else {
        // The そうだね count at the right end of the header, as in the original.
        futaberSaidaneCount(post.saidaneLabel)?.let { count ->
            Text(
                "×$count", color = colors.saidane, fontSize = display.sp(12f),
                modifier = Modifier.padding(start = 6.dp).testTag("futaber-saidane-count")
            )
        }
        }
        }
        titleLine?.let {
            Text(it, color = colors.accent, fontSize = display.sp(13f), fontWeight = FontWeight.Medium)
        }
        Column(Modifier.padding(top = 4.dp)) {
            lines.forEach { line ->
                val isQuote = isFutaberQuoteLine(line)
                val canOpen = isQuote && !post.isDeleted && futaberQuoteTargetIds(post, line).isNotEmpty()
                var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
                Text(
                    text = line,
                    color = when {
                        post.isDeleted || post.isIsolated -> colors.danger
                        isQuote -> colors.quote
                        else -> colors.body
                    },
                    fontSize = display.sp(14f),
                    modifier = if (quoteMode && !isQuote && line.isNotBlank() && !post.isDeleted) {
                        // Quote mode: every line of the post's own text can be tapped to quote it.
                        Modifier.clickable(onClickLabel = "この行を引用") { onQuoteLine(line.trim()) }
                            .testTag("futaber-quote-target")
                    } else if (canOpen) {
                        Modifier
                            .onGloballyPositioned { bounds = it.boundsInRoot() }
                            .clickable(onClickLabel = "引用元を表示") {
                                bounds?.let { onQuoteTap(line, FutaberAnchor(it.left + 24f, it.top, it.bottom)) }
                            }
                            .testTag("futaber-quote-line")
                    } else Modifier
                )
            }
        }
        post.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { thumbnail ->
            val ratio = futaberThumbnailAspectRatio(post.thumbnailWidth, post.thumbnailHeight)
            CatalogPreviewImage(
                thumbnailUrl = thumbnail,
                fullImageUrl = post.imageUrl,
                targetSizePx = 500,
                contentDescription = "添付画像",
                modifier = Modifier.padding(top = 6.dp).width(if (smallImages) 66.dp else 132.dp).aspectRatio(ratio)
                    .clip(FutaberShapes.small).background(colors.catalogGap)
                    .then(
                        if (onImageLongPress == null) Modifier else Modifier
                            // Taken here, so the long press does not reach the post's own menu.
                            .pointerInput(onImageLongPress) { detectTapGestures(onLongPress = { onImageLongPress() }) }
                            .semantics { onLongClick(label = "画像の操作を開く") { onImageLongPress(); true } }
                            .testTag("futaber-post-image")
                    ),
                fallbackTint = colors.meta
            )
        }
    }
}

/** Width over height of a post thumbnail; unknown or degenerate sizes fall back to a square. */
internal fun futaberThumbnailAspectRatio(width: Int?, height: Int?): Float {
    if (width == null || height == null || width <= 0 || height <= 0) return 1f
    return (width.toFloat() / height.toFloat()).coerceIn(0.25f, 4f)
}

/** Shown while the list is narrowed: what narrows it, how many rows are left, and a way to undo it. */
@Composable
private fun FutaberFilterChip(
    filter: FutaberViewFilter,
    count: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFutaberColors.current
    val what = buildList {
        if (filter.query.isNotBlank()) add("「${filter.query.trim()}」")
        if (filter.repliesOnly) add("返信が多いレス")
        filter.extract?.let { add(it.label) }
        if (filter.sameId.isNotBlank()) add("ID:${filter.sameId}")
    }.joinToString(" ")
    Row(
        modifier.padding(8.dp).background(colors.bar, FutaberShapes.pill)
            .border(1.dp, colors.separator, FutaberShapes.pill)
            .clickable(onClickLabel = "絞り込みを解除", onClick = onClear)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("futaber-filter-chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$what ${count}件", color = colors.body, fontSize = 12.sp)
        FutaberIcon(Icons.Outlined.Close, contentDescription = "絞り込みを解除", tint = colors.icon, modifier = Modifier.padding(start = 6.dp).size(16.dp))
    }
}

/** The pill shown while reading: the state, "前" / "次" to move one readable post, and "停止". */
@Composable
private fun FutaberReadingChip(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalFutaberColors.current
    Row(
        modifier.padding(8.dp)
            .background(colors.bar, FutaberShapes.pill)
            .border(1.dp, colors.separator, FutaberShapes.pill)
            .padding(horizontal = 4.dp)
            .testTag("futaber-reading-chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReadingChipAction("前", "前のレスへ", "futaber-reading-previous", onPrevious)
        Text("読み上げ中", color = colors.body, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
        ReadingChipAction("次", "次のレスへ", "futaber-reading-next", onNext)
        ReadingChipAction("停止", "読み上げを停止", "futaber-reading-stop", onStop)
    }
}

@Composable
private fun ReadingChipAction(label: String, description: String, tag: String, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Text(
        label, color = colors.link, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.heightIn(min = 40.dp).clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp).testTag(tag)
    )
}
