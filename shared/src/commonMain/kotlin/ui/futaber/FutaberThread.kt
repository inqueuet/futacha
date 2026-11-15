package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.compat.isFallenSitePage
import com.valoser.futacha.shared.repo.aimoge.withAimogeAutomaticFetch
import com.valoser.futacha.shared.ui.util.ScrollableToolbarRow

import com.valoser.futacha.shared.compat.toCompatPlainText
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PhotoSizeSelectLarge
import androidx.compose.material.icons.outlined.PhotoSizeSelectSmall
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.valoser.futacha.shared.ui.compat.CompatFastScrollbar
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import com.valoser.futacha.shared.ui.isIosReviewPlatform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.em
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
import com.valoser.futacha.shared.ui.board.aimogeSpoilerAt
import com.valoser.futacha.shared.ui.board.toggleAimogeSpoiler
import com.valoser.futacha.shared.ui.board.withAimogeDecorations
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
    /** The thread was confirmed gone (404/410) while shown (L108). */
    onConfirmedGone: () -> Unit = {},
    onScrollSettled: (index: Int, offset: Int, postId: String?, total: Int) -> Unit,
    ngWords: List<String>,
    ngHeaders: List<String>,
    ngRules: List<CompatNgRule>,
    onChangeNgWords: ((List<String>) -> List<String>) -> Unit,
    onChangeNgHeaders: ((List<String>) -> List<String>) -> Unit,
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
    /** Something drawn over the thread by the host (the board drawer, the manage panel); the volume keys then do nothing. */
    volumeKeysCovered: Boolean = false,
    /** The shared features the borrowed dialogs read, made once by the mode (null: made here, as before). */
    features: FutachaSharedFeatures? = null,
    /** The volume keys' "タブのスレッドを切り替え": opens the next ([forward]) or previous tab's thread. */
    onVolumeKeyThread: ((forward: Boolean) -> Unit)? = null,
    /** F-B3 E16: writes one preference (the tree view's switch from the operation menu). */
    onWritePreference: ((key: String, value: String) -> Unit)? = null,
    tabStrip: @Composable () -> Unit = {},
    /** B7a: the operation menu's "キャッシュ検索" (the extension "キャッシュ検索・ブラウザ"); null leaves it out. */
    onOpenCacheSearch: (() -> Unit)? = null,
    /** False on a board that is not an official one: "キャッシュ検索" is shown disabled. */
    cacheSearchEnabled: Boolean = true,
    /** B7b: the operation menu's "NGの詳細管理" (the extension "NGの詳細管理"); null leaves it out. */
    ngDetail: FutaberNgDetailEntry? = null,
    /** B8: the shared AI switches (summary, hiding) are read from it; null runs no AI. */
    stateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    /** B8: the AI command forwarded to this thread (FutaberAiHost.kt); null receives none. */
    ai: FutaberAiThreadLink? = null,
    /** B9: auto-saves the live page as ふたちゃ does (the extension "閲覧中のスレッドを自動保存"); null saves nothing. */
    autoSave: FutaberAutoSaveLink? = null,
    initialKnownPostCount: Int? = null,
    initialKnownLastPostId: String? = null,
    /** L-B6 (M16): the menu's 「NG編集」 opens the full-screen NG settings; null opens the NG dialog as before. */
    onOpenNgSettings: (() -> Unit)? = null,
    /** L-B7 (S22・F03): the page the catalog loaded before opening, and whether a tab opened it; null fetches as before. */
    handoff: FutaberThreadHandoff? = null
) {
    val colors = LocalFutaberColors.current
    var load by remember(board.id, ref.threadId) { mutableStateOf(FutaberThreadLoad()) }
    var liveConfirmed by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var confirmedGone by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var refreshTick by remember(board.id, ref.threadId) { mutableIntStateOf(0) }
    // Set by the automatic re-reads (auto-scroll) so the あいもげ repository can hold them back; Futaba ignores it.
    val nextReloadAutomatic = remember(board.id, ref.threadId) { booleanArrayOf(false) }
    // One list state per thread: switching threads (tabs, history) must not carry the previous thread's scroll position over.
    val listState = key(board.id, ref.threadId) { rememberLazyListState() }
    // The picture list keeps its place while this thread is open (closing and reopening it no longer goes to the top).
    val galleryState = key(board.id, ref.threadId) { androidx.compose.foundation.lazy.grid.rememberLazyGridState() }
    val scope = rememberCoroutineScope()
    // On iOS the left edge of this screen opens the board list (the swipes below), so the system back swipe stays off there.
    PlatformBackHandler(iosEdgeGestureEnabled = !isIosReviewPlatform(), onBack = onBack)
    var filter by remember(board.id, ref.threadId) { mutableStateOf(FutaberViewFilter()) }
    var extractOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // The menu of a long-pressed picture ("画像検索" / "画像NGに登録"), apart from the menu of the post.
    var imagePost by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    var imageSearchUrl by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var imageNgTarget by remember(board.id, ref.threadId) { mutableStateOf<Pair<Post, String>?>(null) }
    var autoScrolling by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // "URL・アーカイブ" (an extension): the external archives' sheet.
    var archiveOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }

    // The page that was on screen before the last reload changed it ("更新前に戻す"); null when nothing changed.
    var previousPage by remember(board.id, ref.threadId) { mutableStateOf<ThreadPage?>(null) }
    val shareText = com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher()
    val openInBrowser = com.valoser.futacha.shared.util.rememberUrlLauncher()
    // L-B12 (C05): the person's URL schemes (the browser's, and the action's).
    val urlSchemeOpener = rememberFutaberUrlSchemeOpener(LocalFutaberDisplaySettings.current.urlSchemes)
    // Set while a dead thread is shown from an archive: the thread fell off the board (404/410) and an archive had it.
    var archiveNotice by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    // Set while the auto-saved copy is shown because the live page could not be read (no connection, a timeout).
    var offlineCopyNotice by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    val archiveSearchJson = remember { kotlinx.serialization.json.Json { ignoreUnknownKeys = true } }
    val archiveHttpClient = mediaServices?.httpClient
    val autoSaver = rememberFutaberThreadAutoSaver(board.id, ref.threadId)
    // S22: the page loaded before opening stands for the first fetch only; F03: 「新着 N」 after a refresh / a tab's opening.
    val prefetched = remember(board.id, ref.threadId) { arrayOf(handoff?.content) }
    val newCount = remember(board.id, ref.threadId) { FutaberNewCountNotice(ref.seenCount.takeIf { handoff?.fromTab == true }) }
    LaunchedEffect(board.id, board.url, ref.threadId, refreshTick, refreshSignal) {
        load = load.copy(isLoading = true, errorMessage = null)
        try {
            val handed = prefetched[0].also { prefetched[0] = null }
            val automatic = nextReloadAutomatic[0].also { nextReloadAutomatic[0] = false }
            val fetched = handed?.getOrThrow() ?: if (automatic) {
                withAimogeAutomaticFetch { repository.getThreadContent(board.url, ref.threadId) }
            } else {
                repository.getThreadContent(board.url, ref.threadId)
            }
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
            // あいもげ answers a fallen thread with its posts (ThreadPage.archived): shown as fallen, replies closed.
            val siteFallen = content.page.isFallenSitePage()
            liveConfirmed = !siteFallen
            confirmedGone = siteFallen
            archiveNotice = if (siteFallen) "スレッドは落ちました" else null
            offlineCopyNotice = null
            val shown = load.page
            // Only a reload that changed the posts leaves something to go back to.
            previousPage = if (shown != null && shown.posts.size != content.page.posts.size) shown else previousPage
            load = FutaberThreadLoad(page = content.page, isLoading = false)
            newCount.onLivePage(content.page.posts.size)
            onPageLoaded(content.page.posts)
            if (siteFallen) {
                newCount.disarm()
                onConfirmedGone()
            } else {
                onLivePageConfirmed()
                autoSaver.onLivePage(content.page)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            // A thread that has fallen off the board (404/410) is looked up in the archives ふたちゃ and としあき(仮)
            // use; the page on screen, if any, is kept when none has it.
            val fallback = resolveThreadLoadFallbackState(error, allowOfflineFallback = true)
            newCount.disarm()
            liveConfirmed = false
            confirmedGone = fallback.shouldTryArchiveFallback
            if (fallback.shouldTryArchiveFallback && offlineNotice == null) onConfirmedGone()
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
    FutaberThreadAutoSaveEffect(
        autoSave, autoSaver, board, ref, load.page,
        showingCopy = offlineNotice != null || archiveNotice != null || offlineCopyNotice != null
    )
    val posts = load.page?.posts.orEmpty()
    val mediaPostCount = remember(posts) { futaberMediaPostCount(posts) }
    // The title row: the first post's first line once the page is read (as futaber), the opened title before (L-B1).
    val titleRow = remember(ref.title, posts.firstOrNull()) { futaberThreadTitle(ref.title, posts.firstOrNull()) }
    val idLabels = remember(posts) { buildPosterIdLabels(posts) }
    val ipCounts = remember(posts) { futaberIpCountLabels(posts) }
    val replyIndex = remember(posts) { futaberReplyIndex(posts) }
    var menuOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var pendingScrollOrdinal by remember(board.id, ref.threadId) { mutableStateOf<Int?>(null) }
    val speechRuntime = com.valoser.futacha.shared.audio.LocalSpeechRuntime.current
    val platformContext = LocalPlatformContext.current
    val liveSpeechSettings = rememberUpdatedState(com.valoser.futacha.shared.audio.speechSettingsFromPreferences(
        (mediaServices?.store?.preferences ?: kotlinx.coroutines.flow.flowOf(emptyMap<String, String>())).collectAsState(emptyMap()).value))
    // C24: the quoted lines are read at this pitch (null while the engine is not the system voice, which alone has a pitch).
    val liveQuotePitch = rememberUpdatedState(LocalFutaberDisplaySettings.current.quotePitchTenths / 10f)
    val reader = remember(board.id, ref.threadId) {
        FutaberReadAloud(scope, { createTextSpeaker(platformContext, speechRuntime).asFutaberEngine(quotePitch = { liveQuotePitch.value }) { liveSpeechSettings.value } })
    }
    // Leaving the screen, or locking the app, stops the voice and releases the speaker.
    DisposableEffect(reader) { onDispose { reader.dispose() } }
    FutaberReadAloudAppLockEffect(reader)
    var markTarget by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var actionPost by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    // L-B5: the copy panel, the report's sheet, the IP's sheet and the direct picture searches (FutaberThreadSheets.kt).
    val sheets = remember(board.id, ref.threadId) { FutaberThreadSheetState() }
    // The poster's ID (else the name) of the post whose "NG登録" opened the NG dialog, ready to add.
    var ngDialogInitialHeader by remember(board.id, ref.threadId) { mutableStateOf("") }
    val longPressHaptic = rememberFutaberLongPressHaptic()
    var remoteConfirm by remember(board.id, ref.threadId) { mutableStateOf<Pair<FutaberRemoteAction, Post>?>(null) }
    var remoteNotice by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var remoteBusyHere by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // Sends the action through the shared repository calls ふたちゃ uses; the result is shown as a notice. It runs in the
    // app's scope and tells its result as the app's notice when this thread has been left meanwhile (re-audit L3: leaving
    // cancelled it, and after moving to another thread the result never showed).
    val appTasks = LocalFutaberAppTasks.current
    val currentThreadKey by rememberUpdatedState(board.id to ref.threadId)
    var threadScreenShown by remember { mutableStateOf(true) }
    DisposableEffect(Unit) { onDispose { threadScreenShown = false } }
    // A result goes to the state of the thread shown now when it is the one that sent it (L109: it was written to the
    // state captured when sending, which is replaced when the thread changes, so after A→B→A it never showed and A was
    // not reloaded), else to the app's notice.
    val deliverHere by rememberUpdatedState { message: String, reload: Boolean ->
        remoteNotice = message
        if (reload) refreshTick += 1
    }
    fun deliverThreadResult(sentFrom: Pair<String, String>, message: String, reload: Boolean = false) {
        if (threadScreenShown && currentThreadKey == sentFrom) deliverHere(message, reload) else appTasks?.notify(message)
    }
    // Read when pressed, so a second press before the next frame is refused too.
    fun isRemoteBusy(thread: Pair<String, String>): Boolean = appTasks?.let { thread in it.busyThreads } ?: remoteBusyHere
    fun setRemoteBusy(thread: Pair<String, String>, busy: Boolean) {
        if (appTasks != null) { if (busy) appTasks.busyThreads[thread] = true else appTasks.busyThreads.remove(thread) }
        else remoteBusyHere = busy
    }
    val runRemoteAction: (FutaberRemoteAction, Post, Boolean) -> Unit = { action, target, imageOnly ->
        val sentFrom = board.id to ref.threadId
        if (!isRemoteBusy(sentFrom)) {
            setRemoteBusy(sentFrom, true)
            remoteNotice = "処理中です…"
            (appTasks?.scope ?: scope).launch {
                var reportResult: String? = null
                val result = performThreadAction {
                    requireWritableThreadBoard(board.url)
                    if (action == FutaberRemoteAction.Report) {
                        reportResult = com.valoser.futacha.shared.ui.board.sendReportAndOptionalNg(
                            alsoNg = imageOnly,
                            send = { repository.requestDeletion(board.url, ref.threadId, target.id, DEFAULT_DEL_REASON_CODE) },
                            registerNg = {
                                val store = mediaServices?.store ?: features?.store ?: error("設定の保存先を利用できません")
                                com.valoser.futacha.shared.ui.board.registerReportedThreadPostNg(store, board, ref.threadId, target.id)
                            }
                        )
                    }
                    when (action) {
                        FutaberRemoteAction.Saidane -> repository.voteSaidane(board.url, ref.threadId, target.id)
                        FutaberRemoteAction.DeleteOwn -> repository.deleteByUser(
                            board = board.url, threadId = ref.threadId, postId = target.id,
                            password = deleteKey, imageOnly = imageOnly
                        )
                        FutaberRemoteAction.Report -> Unit
                    }
                }
                val message = when (result) {
                    is ThreadActionRunResult.Success -> reportResult ?: futaberRemoteSuccessMessage(action, imageOnly)
                    is ThreadActionRunResult.Failure -> buildThreadActionFailureMessage(futaberRemoteFailurePrefix(action, imageOnly), result.error)
                }
                setRemoteBusy(sentFrom, false)
                deliverThreadResult(sentFrom, message,
                    reload = result is ThreadActionRunResult.Success && action != FutaberRemoteAction.Report)
            }
        }
    }
    PlatformBackHandler(enabled = actionPost != null) { actionPost = null }
    PlatformBackHandler(enabled = extractOpen) { extractOpen = false }
    PlatformBackHandler(enabled = imagePost != null) { imagePost = null }
    // The auto-scroll (an extension): the same loop ふたちゃ and としあき(仮) run, with the amount and the
    // interval they share. It waits while the app is in the background and while a finger drags the list,
    // reloads at the bottom, and stops when the thread is gone.
    // Behind the writing screen or the settings the thread neither scrolls by itself nor reads aloud.
    LaunchedEffect(paused) { if (paused) reader.stop() }
    FutaberAutoScrollEffect(
        active = autoScrolling && !paused,
        pixel = LocalFutaberDisplaySettings.current.autoScrollPixel,
        intervalMillis = LocalFutaberDisplaySettings.current.autoScrollSpeedMillis.toLong(),
        isDead = archiveNotice != null,
        listState = listState,
        onReload = { nextReloadAutomatic[0] = true; refreshTick += 1 },
        onStop = { autoScrolling = false }
    )
    var ngDisabled by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var ngDialogOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var mediaOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // The picture "ビューアで表示" (the image menu extension) opens the viewer on; null opens the picture list.
    var mediaInitialPostNo by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var mediaInitialUrl by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var saveOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // M15: the MHT format chosen in the alert over the menu (false: thumbnails only, true: the pictures too); null = none.
    var mhtSaveFull by remember(board.id, ref.threadId) { mutableStateOf<Boolean?>(null) }
    var mhtChoiceOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // M14: the alert asking whether the favourite keeps the thread's pictures.
    // M19: the address the player window opens with while it is up (null = closed).
    var playerUrl by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var favoriteAskOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    val mhtLibrary = remember(mediaServices) {
        mediaServices?.fileSystem?.let { FutaberMhtLibrary(it, mediaServices.httpClient) }
    }
    val display = LocalFutaberDisplaySettings.current
    val privacyMode = com.valoser.futacha.shared.ui.privacy.LocalPrivacyMode.current
    // The device image / video editors of the media features (null while switched off in the settings: no menu entry then).
    val launchImageEditor = com.valoser.futacha.shared.ui.media.LocalLaunchDeviceImageEditor.current
    val launchVideoEditor = com.valoser.futacha.shared.ui.media.LocalLaunchDeviceVideoEditor.current
    // M10's 「画像を小さくする」 for this thread; the settings give the usual size itself (C21: the ratio, L-B9).
    var smallImages by remember(board.id, ref.threadId) { mutableStateOf(false) }
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
    val sharedFeatures = features ?: remember(mediaServices, repository) {
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
    // Saving / sharing a post's picture from its long-press menu (the image menu extension), the way the viewer does it.
    val imageSaver = rememberFutaberThreadImageSaver(
        display.extImageMenu, mediaServices, sharedPreferences, boardKeyForNg, ref.threadId
    ) { remoteNotice = it }
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
    val sharedNgRaw = sharedPreferences[com.valoser.futacha.shared.ui.ftutter.FtutterKeys.NG_LIST]
    val sharedNgOn = com.valoser.futacha.shared.ui.compat.compatCommonNgApplyEnabled(sharedPreferences)
    val sharedNgMatcher = remember(sharedNgRaw, sharedNgOn, board) {
        futaberSharedNgMatcher(sharedNgRaw, sharedNgOn, futaberCanonicalBoardUrl(board))
    }
    val ngPrepared = remember(ngWords, ngHeaders, ngRules, tabKeyForNg, boardKeyForNg, sharedNgMatcher) {
        futaberNgPrepare(ngWords, ngHeaders, ngRules, tabKeyForNg, boardKeyForNg, sharedNgMatcher)
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
    // B8 (FutaberThreadAi.kt): the posts the AI hides join the NG ones.
    val threadAi = rememberFutaberThreadAiHolder(board.id, ref.threadId)
    val hidden = remember(ngBaseHidden, phashHidden, threadAi.hidden, display.extNgChain, replyIndex) {
        // F-B3 E12: with the chain on, whoever replies to a hidden post is hidden too.
        futaberHiddenWithAi(ngBaseHidden, phashHidden, threadAi.hidden).let { base ->
            if (display.extNgChain) futaberNgChain(base, replyIndex) else base
        }
    }
    val activeHidden = if (ngDisabled) emptySet() else hidden
    val visibleReplyIndex = remember(replyIndex, activeHidden) {
        if (activeHidden.isEmpty()) replyIndex else replyIndex.mapValues { (_, replies) ->
            replies.filterNot { it.id in activeHidden }
        }
    }
    com.valoser.futacha.shared.ui.board.ThreadStatusObservationEffect(
        stateStore, board.id, ref.threadId, load.page, liveConfirmed && offlineNotice == null,
        gone = confirmedGone && offlineNotice == null
    )
    com.valoser.futacha.shared.ui.board.ThreadOpeningSummaryEffect(
        tabKeyForNg, initialKnownPostCount, posts, selfPostIds, activeHidden,
        enabled = display.extOpenSummary,
        live = liveConfirmed && !load.isLoading && !ngWaiting && offlineNotice == null && archiveNotice == null && offlineCopyNotice == null,
        truncated = load.page?.let { it.isTruncated || !it.quoteReferencesComplete } == true,
        onNotice = { remoteNotice = it }, baselineLastPostId = initialKnownLastPostId
    )
    com.valoser.futacha.shared.ui.board.ThreadViewedObservationEffect(
        stateStore, board.id, ref.threadId, load.page,
        live = liveConfirmed && !paused && !load.isLoading && offlineNotice == null && archiveNotice == null && offlineCopyNotice == null
    )
    // The tree view (an extension, off by default): replies listed under the post they quote.
    val treeNodes by produceState(emptyList<ThreadTreeNode>(), posts, display.extTree) {
        value = if (display.extTree) buildThreadTreeNodes(posts) else emptyList()
    }
    // The search word takes effect a moment after the last key, so a fast typist does not search after every letter.
    val appliedQuery = rememberFutaberDebouncedQuery(filter.query)
    val appliedFilter = remember(filter, appliedQuery) { if (filter.query == appliedQuery) filter else filter.copy(query = appliedQuery) }
    // The found post the "▲ n/N ▼" of the search chip is on (an extension); a new search starts before the first one.
    var searchHit by remember(board.id, ref.threadId, appliedQuery) { mutableIntStateOf(-1) }
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
    val hideDeletedRows = sharedPreferences["compat.thread.threadAdminDeleteShow"] == "HIDE"
    // F-B3 E10: the popular posts are worked out only while that extract is on.
    val popularIds = remember(posts, appliedFilter.extract, display.manyRepliesThreshold, display.saidaneThreshold) {
        if (appliedFilter.extract == FutaberExtract.Popular) futaberPopularIds(posts, display.manyRepliesThreshold, display.saidaneThreshold) else emptySet()
    }
    val rows = remember(posts, appliedFilter, replyIndex, activeHidden, hidden, display.manyRepliesThreshold, display.saidaneThreshold, popularIds, selfPostIds, display.extTree, treeNodes, postTexts, ngWaiting, hideDeletedRows) {
        if (ngWaiting) emptyList() else {
            // "NGで隠したレス" lists what NG hides even while NG is switched off for the thread.
            val listed = futaberVisibleRows(
                posts, appliedFilter, replyIndex, activeHidden, display.manyRepliesThreshold, selfPostIds, postTexts, ngHidden = hidden,
                saidaneThreshold = display.saidaneThreshold, popular = popularIds
            )
            val visible = if (hideDeletedRows) listed.filterNot { it.post.isDeleted || it.post.isIsolated } else listed
            if (display.extTree) futaberApplyTree(visible, treeNodes) else visible
        }
    }
    val latestRows by rememberUpdatedState(rows)
    FutaberThreadAi(threadAi, stateStore, load.page, tabKeyForNg, ref.title, selfPostIds, listState, rows)
    val liveNgPrepared by rememberUpdatedState(ngPrepared)
    val liveNgDisabled by rememberUpdatedState(ngDisabled)
    val livePhashRules by rememberUpdatedState(phashRules)
    // Back to where reading stopped, once per opened thread, after its first page arrives.
    var restored by remember(board.id, ref.threadId) { mutableStateOf(false) }
    val postsReady = posts.isNotEmpty() && !ngWaiting
    LaunchedEffect(postsReady, restored) {
        if (!postsReady || restored) return@LaunchedEffect
        // F-B3 E14: with "open at the top" on, only a tab switch brings the reading position back.
        if (!display.extOpenAtTop || ref.keepPosition) futaberRestoreRowPosition(posts, latestRows, ref)
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
    FutaberThreadAiCommands(
        link = ai, boardId = board.id, threadId = ref.threadId, ready = restored,
        // A load that ended with no post (and no error) fails the command at once instead of after 90 s (audit L20).
        failed = !load.isLoading && posts.isEmpty() && (load.errorMessage != null || load.page != null),
        listState = listState, reader = reader, rows = rows, query = appliedFilter.query, searchHit = searchHit,
        onSearchHit = { searchHit = it }, onSearch = { filter = filter.copy(query = it) }, onOpenMenu = { menuOpen = true },
        onOpenGallery = { (mediaServices != null && mediaPostCount > 0).also { if (it) mediaOpen = true } },
        onRefresh = { refreshTick += 1 },
        onOpenSave = { (mediaServices != null && offlineNotice == null && offlineCopyNotice == null && posts.isNotEmpty()).also { if (it) saveOpen = true } }
    )
    val clipboard = LocalClipboardManager.current
    // B7b (FutaberParityB7b.kt): the post detail operations' picker and the anchor of their bubbles.
    val postDetailOps = rememberFutaberPostDetailOps(board.id, ref.threadId)
    var quoteStack by remember(board.id, ref.threadId) { mutableStateOf(emptyList<FutaberQuoteLayer>()) }
    // A refreshed thread can drop posts a bubble showed, so bubbles are closed with it.
    LaunchedEffect(refreshTick, refreshSignal) { quoteStack = emptyList() }
    PlatformBackHandler(enabled = quoteStack.isNotEmpty()) { quoteStack = quoteStack.dropLast(1) }
    // Something over the thread (a bubble, the menu, a sheet, 抽出, URL・アーカイブ): the edge gestures and volume keys
    // leave it alone, so back closes it first (L114: Android's left-edge back opened the board list under the menu).
    val threadOverlayOpen = quoteStack.isNotEmpty() || menuOpen || actionPost != null || extractOpen || imagePost != null || archiveOpen ||
        sheets.anyOpen
    FutaberVolumeKeyEffect(
        // Not while something covers the thread: the picture list / viewer, the menus, the drawer or the panel (audit L17:
        // the keys scrolled the hidden thread, and "タブのスレッドを切り替え" closed the viewer), nor under "URL・アーカイブ"
        // or a quote popup (re-audit L2; the tab sheet comes with volumeKeysCovered).
        enabled = LocalFutaberDisplaySettings.current.extVolumeKeys && !paused && !volumeKeysCovered &&
            !mediaOpen && !threadOverlayOpen,
        listState = listState,
        mode = LocalFutaberDisplaySettings.current.volumeKeyMode, onSwitchThread = onVolumeKeyThread
    )
    // A swipe in from the left edge opens the board list; while a bubble is open it must not.
    ThreadDrawerBackGestureHandler(enabled = drawerGestureEnabled && !threadOverlayOpen, onOpenDrawer = onOpenDrawer)
    val openQuote: (FutaberQuoteKind, List<Post>, FutaberAnchor) -> Unit = { kind, related, anchor ->
        val shown = related.filter { it.id !in activeHidden }
        if (shown.isNotEmpty()) quoteStack = quoteStack + FutaberQuoteLayer(kind, shown, anchor)
    }

    // Swipes on the posts, as in the original: from the left edge the board list (Android's gesture navigation keeps that
    // edge for its back gesture, which opens the board list above), further in back to the catalog, to the left the
    // writing screen. Not while something is open on top (a bubble, the menu, a sheet, the board list, the panel, the
    // writing screen and its quote mode): a swipe must not act on the thread behind those.
    // Tapping the upper / lower third of the list scrolls a page (the gestures settings; off by default). Quote mode's taps quote lines.
    val tapScroll = rememberFutaberTapScroll(listState, display.tapScroll && !quoteMode)
    val swipeModifier = rememberFutaberSwipeModifier(drawerGestureEnabled && !threadOverlayOpen && !quoteMode) { swipe ->
        when (swipe) {
            FutaberSwipe.EdgeRight -> onOpenDrawer()
            FutaberSwipe.Right -> onBack()
            FutaberSwipe.Left -> onOpenPost("")
        }
    }
    // V32: the thin bar at the right while the list scrolls; F22: the end of the thread.
    val scrollIndicator = rememberFutaberScrollIndicator(listState)
    // G21・F03: the refresh button and the pull at the end refresh by hand, and 「新着 N」 follows.
    val refreshByHand = { newCount.arm(load.page?.posts?.size); refreshTick += 1 }
    val endPull = rememberFutaberEndPull(listState, enabled = true, onRefresh = refreshByHand)
    val threadEnd = futaberThreadEndLabel(load.page)
    // Made once per page (audit A5: every redraw walked all the posts for it).
    val markPosts = remember(posts) { posts.map { it.id to it.messageHtml } }
    val markContext = mediaServices?.store?.let { store ->
        com.valoser.futacha.shared.ui.board.PostMarkContext(store, futaberHistoryThreadUrl(board, ref.threadId),
            markPosts, { no ->
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
    // The screen, and over it the sheets (M47: they cover the tab strip and the bottom bar too).
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(colors.threadBackground)) {
        // No bar at the top, as in the original: the title in one line under the status area (V05). Back is the right
        // swipe and the system's back; a screen reader finds "カタログへ戻る" on the title.
        FutaberThreadTitleRow(titleRow, onBack)
        (offlineNotice ?: archiveNotice ?: offlineCopyNotice)?.let { notice ->
            Text(
                text = notice, color = colors.onAccent, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(colors.accent).padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("futaber-offline-notice")
            )
        }
        FutaberThreadAiPanel(threadAi)
        // M19: the player, under the title; the thread goes on below it.
        playerUrl?.let { url -> FutaberPlayerWindow(url, onClose = { playerUrl = null }) }
        Box(Modifier.weight(1f).fillMaxWidth().then(swipeModifier).testTag("futaber-thread-body")) {
            when {
                // The first NG result is still being worked out: showing the posts now could show ones it hides.
                ngWaiting -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = colors.accent)
                posts.isNotEmpty() -> if (rows.isEmpty()) {
                    FutaberMessage("条件に合うレスはありません")
                } else LazyColumn(Modifier.fillMaxSize().then(tapScroll?.listModifier ?: Modifier).then(if (display.extPostDetailOps) postDetailOps.anchor.modifier else Modifier).then(scrollIndicator).then(endPull).testTag("futaber-thread-list"), state = listState) {
                    itemsIndexed(rows, key = { _, row -> "${row.ordinal}:${row.post.id}" }) { _, row ->
                        val post = row.post
                        if (row.ordinal == firstNewOrdinal) {
                            Text(
                                "ここから新着", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth().background(colors.fill)
                                    .padding(horizontal = 10.dp, vertical = 4.dp).testTag("futaber-new-marker")
                            )
                        }
                        FutaberPostRow(
                            ordinal = row.ordinal,
                            post = post,
                            idLabel = idLabels[post.id],
                            ipCount = ipCounts[post.id],
                            replyCount = futaberReplyCount(post, visibleReplyIndex),
                            highlighted = reader.currentPostId == post.id,
                            manuallyMarked = post.id in markedPostNos,
                            onSaidaneTap = if (display.extQuickSaidane && archiveNotice == null && offlineNotice == null && offlineCopyNotice == null) ({ runRemoteAction(FutaberRemoteAction.Saidane, post, false) }) else null,
                            onNgTap = if (display.extQuickNg) ({ ngDialogInitialHeader = futaberNgHeaderOf(post); ngDialogOpen = true }) else null,
                            smallImages = smallImages,
                            depth = row.depth,
                            onImageTap = if (mediaServices != null && futaberImageUrlOf(post) != null) {
                                { mediaInitialPostNo = post.id; mediaInitialUrl = null; mediaOpen = true }
                            } else null,
                            onExtraImageTap = if (mediaServices != null) {
                                { url -> mediaInitialPostNo = post.id; mediaInitialUrl = url; mediaOpen = true }
                            } else null,
                            onImageLongPress = if (futaberImageUrlOf(post) != null) {
                                { longPressHaptic(); imagePost = post }
                            } else null,
                            onIdTap = if (display.extIdTap && !post.posterId.isNullOrBlank()) {
                                { filter = filter.copy(sameId = post.posterId.orEmpty()) }
                            } else null,
                            onLongPress = { longPressHaptic(); actionPost = post },
                            onIpLongPress = { ip -> longPressHaptic(); sheets.ip = ip },
                            onTap = tapScroll?.let { it::onTap },
                            quoteMode = quoteMode,
                            onQuoteLine = onQuoteLine,
                            onQuoteTap = { line, anchor ->
                                openQuote(FutaberQuoteKind.Source, futaberPostsById(posts, futaberQuoteTargetIds(post, line)), anchor)
                            },
                            onRepliesTap = { anchor ->
                                openQuote(FutaberQuoteKind.Replies, replyIndex[post.id].orEmpty(), anchor)
                            }
                        )
                        HorizontalDivider(color = colors.postSeparator, thickness = 0.5.dp)
                    }
                    // F22・G21: the grey footer with "…頃消えます" and 「引っ張って...」 after the last post, always.
                    item(key = FUTABER_THREAD_END_KEY) { FutaberThreadEndRow(threadEnd) }
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
                    color = colors.onAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .background(colors.accent).fillMaxWidth().padding(8.dp)
                )
            }
            if (reader.isReading && display.extReadAloudPanel) {
                FutaberReadAloudPanelChip(reader, Modifier.align(Alignment.BottomCenter))
            } else if (reader.isReading) {
                FutaberReadingChip(
                    onPrevious = { reader.skip(-1) }, onNext = { reader.skip(1) }, onStop = reader::stop,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
            if (autoScrolling) {
                FutaberAutoScrollChip(onStop = { autoScrolling = false }, modifier = Modifier.align(Alignment.BottomEnd))
            }
            reader.message?.let { notice ->
                Text(
                    text = notice,
                    color = colors.onAccent,
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
                    modifier = Modifier.align(Alignment.TopCenter),
                    trailing = if (display.extSearchNav && appliedFilter.query.isNotBlank() && rows.isNotEmpty()) {
                        {
                            FutaberSearchHitNav(searchHit, rows.size) { forward ->
                                searchHit = futaberSearchHitStep(searchHit, rows.size, forward)
                                scope.launch { listState.animateScrollToItem(searchHit.coerceIn(0, latestRows.lastIndex.coerceAtLeast(0))) }
                            }
                        }
                    } else null
                )
            }
            // F03: 「新着 N」 at the right above the tab strip, for 2.5 s.
            FutaberToast(newCount.message, onHidden = newCount::hide)
            FutaberPostDetailOpsHost(
                postDetailOps, onCopy = { text -> clipboard.setText(AnnotatedString(text)); remoteNotice = "クリップボードにコピーしました" },
                onOpenUrl = openInBrowser
            )
            remoteNotice?.let { notice ->
                Text(
                    text = notice,
                    color = colors.onAccent,
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
                    onDismiss = { saveOpen = false },
                    offerMediaOnly = LocalFutaberDisplaySettings.current.extMediaOnlySave
                )
            }
            val mediaPage = load.page
            if (mediaOpen && mediaServices != null && mediaPage != null) {
                FutaberMediaOverlay(
                    board = board,
                    ref = ref,
                    page = mediaPage,
                    services = mediaServices,
                    galleryState = galleryState,
                    ipCounts = ipCounts,
                    replyCounts = remember(visibleReplyIndex) { visibleReplyIndex.mapValues { it.value.size } },
                    // Posts hidden by NG (words, IDs, rules and look-alike pictures) leave the gallery and the viewer too.
                    hiddenPostNos = activeHidden,
                    initialPostNo = mediaInitialPostNo,
                    initialMediaUrl = mediaInitialUrl,
                    onShowPost = { postId ->
                        // The post's own number, which stays valid when NG or a search narrows the list.
                        // S11: the post comes back as a bubble over the thread (the viewer closes), not as a scroll.
                        mediaPage.posts.firstOrNull { it.id == postId }?.let { post ->
                            quoteStack = quoteStack + futaberMenuBubble(FutaberQuoteKind.FromViewer, listOf(post))
                        }
                    },
                    onClose = { mediaOpen = false; mediaInitialPostNo = null; mediaInitialUrl = null }
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
            // F01: every bubble of the stack, each under the next one; the ones below stay in sight, darker.
            FutaberQuoteStack(
                stack = quoteStack,
                allPosts = posts,
                idLabels = idLabels,
                ipCounts = ipCounts,
                replyIndex = visibleReplyIndex,
                onNested = openQuote,
                onDismissTop = { quoteStack = quoteStack.dropLast(1) },
                onImageTap = if (mediaServices != null) ({ post -> mediaInitialPostNo = post.id; mediaOpen = true }) else null,
                onImageLongPress = { post -> longPressHaptic(); imagePost = post }
            )
        
            // F-B5 E5: the fast scroll bar (the shared switch the other modes have; off by default).
            if (display.threadFastScroll && rows.isNotEmpty()) CompatFastScrollbar(true, listState)
        }
        tabStrip()
        FutaberThreadToolbar(
            bubblesOpen = quoteStack.isNotEmpty(),
            onCloseOneBubble = { quoteStack = quoteStack.dropLast(1) },
            onCloseAllBubbles = { quoteStack = emptyList() },
            galleryEnabled = mediaServices != null && mediaPostCount > 0,
            onOpenGallery = { if (mediaServices != null && mediaPostCount > 0) mediaOpen = true },
            onScrollToEnd = { if (latestRows.isNotEmpty()) scope.launch { listState.animateScrollToItem(latestRows.lastIndex) } },
            onScrollToNew = {
                longPressHaptic()
                firstNewOrdinal?.let { ordinal ->
                    filter = FutaberViewFilter()
                    pendingScrollOrdinal = ordinal
                }
            },
            filterActive = filter.isActive,
            onOpenMenu = { menuOpen = true },
            isTabbed = isTabbed,
            onToggleTab = { if (offlineNotice == null) { longPressHaptic(); onToggleTab() } },
            onOpenManage = onOpenManage,
            onRefresh = refreshByHand
        )
    }
    // L-B4 (M47): the sheets over the whole screen, the tab strip and the bottom bar included. The picture menu is after
    // the bubbles (in the column), so the one opened from a bubble is on top of it (L145). In a scope of their own (the
    // lambda is its own method: the screen's composable stays under the JVM's method size).
    FutaberScreenLayer {
    // L-B6 (M20・M54・V33): the operation menu over the whole screen, the bottom bar included (it does not react while
    // the menu is open); its card stands on the bar's top. The original's eleven slots, then futaberOperationMenuExtras.
    if (menuOpen) {
        val closeMenu = { menuOpen = false }
        val showBubble: (FutaberQuoteKind, List<Post>) -> Unit = { kind, found ->
            menuOpen = false
            quoteStack = quoteStack + futaberMenuBubble(kind, found)
        }
        FutaberOperationMenu(
            items = futaberOperationMenuItems(
                slots = listOf(
                    FutaberMenuItem("copy-url", "URLをコピーする", Icons.Outlined.ContentCopy) {
                        clipboard.setText(AnnotatedString(futaberHistoryThreadUrl(board, ref.threadId)))
                        closeMenu()
                    },
                    // M10: 0.3 of the usual size, and the label and icon turn to 「画像を大きくする」.
                    FutaberMenuItem(
                        "small-images", futaberSmallImagesLabel(smallImages),
                        if (smallImages) Icons.Outlined.PhotoSizeSelectLarge else Icons.Outlined.PhotoSizeSelectSmall
                    ) {
                        smallImages = !smallImages
                        closeMenu()
                    },
                    // M11: the posts with many replies as a bubble (the old narrowing behind 設定→スレッド「従来の動き」).
                    FutaberMenuItem(
                        "many-replies", "返信が多いレスを表示", Icons.Outlined.Forum,
                        enabled = !display.legacyManyRepliesFilter || posts.isNotEmpty(),
                        active = display.legacyManyRepliesFilter && filter.repliesOnly
                    ) {
                        if (display.legacyManyRepliesFilter) {
                            filter = filter.copy(repliesOnly = !filter.repliesOnly)
                            closeMenu()
                        } else {
                            showBubble(FutaberQuoteKind.ManyReplies,
                                futaberManyRepliesPosts(posts, visibleReplyIndex, activeHidden, display.manyRepliesThreshold))
                        }
                    },
                    FutaberMenuItem(
                        "read-aloud", if (reader.isReading) "読み上げを停止" else "読み上げを開始",
                        Icons.Outlined.RecordVoiceOver, enabled = rows.isNotEmpty(), active = reader.isReading
                    ) {
                        if (reader.isReading) reader.stop()
                        else {
                            // The reader numbers the posts as they were listed when it started; the list may change while
                            // it reads (a search, NG), so each post is looked up in the rows of the moment.
                            futaberStartReadAloudFollowing(reader, rows, listState.firstVisibleItemIndex, { latestRows }) {
                                listState.animateScrollToItem(it)
                            }
                        }
                        closeMenu()
                    },
                    // M13: the thread's address through the registered URL scheme action (nothing registered: it only closes).
                    FutaberMenuItem("url-scheme", "URLスキームアクション", Icons.Outlined.Share) {
                        closeMenu()
                        urlSchemeOpener.action(futaberHistoryThreadUrl(board, ref.threadId))
                    },
                    FutaberMenuItem(
                        "favorite", if (isFavorite) "お気に入り解除" else "お気に入りに追加",
                        if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                        enabled = offlineNotice == null, active = isFavorite
                    ) {
                        // Adding asks about the pictures (when there is something to keep and a place to keep it); removing just removes.
                        if (!isFavorite && mediaServices?.httpClient != null && mediaServices.fileSystem != null && futaberFavoriteHasImages(posts)) {
                            favoriteAskOpen = true
                        } else {
                            onToggleFavorite()
                            closeMenu()
                        }
                    },
                    // M19: the player window under the title (a YouTube address of the thread, else YouTube's front page).
                    FutaberMenuItem(FUTABER_MENU_PLAYER_ID, "プレイヤーを表示", Icons.Outlined.PlayArrow) {
                        // The addresses are looked for off the main thread (audit A5).
                        val shownPosts = posts
                        scope.launch { playerUrl = withContext(AppDispatchers.parsing) { futaberPlayerStartUrl(shownPosts) } }
                        closeMenu()
                    },
                    // M15: the format is asked in an alert over the menu; キャンセル leaves the menu open.
                    FutaberMenuItem(
                        "mht-save", "MHTで保存", Icons.Outlined.ViewInAr,
                        enabled = mhtLibrary != null && offlineNotice == null && offlineCopyNotice == null && posts.isNotEmpty()
                    ) { mhtChoiceOpen = true },
                    // M16: the full-screen NG settings (左上「完了」 comes back here); the dialog where there is none.
                    FutaberMenuItem("ng-edit", "NG編集", Icons.Outlined.Block) {
                        closeMenu()
                        onOpenNgSettings?.invoke() ?: run { ngDialogOpen = true }
                    },
                    // M17: pressable at any time; the label and the icon turn (NG OFF ⇄ NG ON).
                    FutaberMenuItem(
                        "ng-toggle", futaberNgToggleLabel(ngDisabled),
                        if (ngDisabled) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, active = ngDisabled
                    ) { ngDisabled = !ngDisabled; closeMenu() },
                    // M18: pressable at any time; with no new post it goes to the end.
                    FutaberMenuItem("new", "新着へスクロール", Icons.Outlined.NewReleases) {
                        val ordinal = firstNewOrdinal
                        if (ordinal != null) {
                            filter = FutaberViewFilter()
                            pendingScrollOrdinal = ordinal
                        } else if (latestRows.isNotEmpty()) {
                            scope.launch { listState.animateScrollToItem(latestRows.lastIndex) }
                        }
                        closeMenu()
                    }
                ),
                extras = futaberOperationMenuExtras(
                    close = closeMenu,
                    liveReadAloudEnabled = !reader.isReading && !filter.isActive && rows.isNotEmpty() && offlineNotice == null &&
                        offlineCopyNotice == null && archiveNotice == null,
                    onLiveReadAloud = {
                        reader.start(rows.map { it.post }, listState.firstVisibleItemIndex, loadNewPosts = {
                            if (repository.probeThreadGone(futaberHistoryThreadUrl(board, ref.threadId))) null else {
                                val fetched = repository.getThreadContent(board.url, ref.threadId)
                                // As a refresh does: the posts the parser cut short are filled in from the archives,
                                // and 「更新前に戻す」 gets the page before (audit A3).
                                val content = (repository as? FutachaSharedBoardRepository)?.let { shared ->
                                    try {
                                        shared.supplement(futaberCompatThreadUrl(board, ref.threadId), fetched)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Throwable) {
                                        fetched
                                    }
                                } ?: fetched
                                val shown = load.page
                                previousPage = if (shown != null && shown.posts.size != content.page.posts.size) shown else previousPage
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
                    },
                    saveEnabled = mediaServices != null && offlineNotice == null && offlineCopyNotice == null && posts.isNotEmpty(),
                    onSave = { saveOpen = true },
                    onShareUrl = { shareText(futaberHistoryThreadUrl(board, ref.threadId), "text/plain", null) },
                    onOpenBrowser = { openInBrowser(futaberHistoryThreadUrl(board, ref.threadId)) },
                    undoReloadEnabled = previousPage != null,
                    onUndoReload = {
                        previousPage?.let { earlier ->
                            load = load.copy(page = earlier)
                            previousPage = null
                        }
                    },
                    isTabbed = isTabbed,
                    tabEnabled = offlineNotice == null,
                    onToggleTab = onToggleTab,
                    onWrite = { onOpenPost("") },
                    // Extensions (switched in the settings), after this app's own items.
                    extract = if (display.extExtract) FutaberMenuToggle(filter.extract != null, posts.isNotEmpty()) { extractOpen = true } else null,
                    autoScroll = if (display.extAutoScroll) FutaberMenuToggle(autoScrolling, rows.isNotEmpty()) { autoScrolling = !autoScrolling } else null,
                    onArchive = if (display.extArchiveMenu) ({ archiveOpen = true }) else null,
                    onCacheSearch = onOpenCacheSearch,
                    cacheSearchEnabled = cacheSearchEnabled,
                    ngDetail = ngDetail,
                    scrollItems = if (display.extScrollMenu) futaberScrollMenuItems(scope, listState, rows.isNotEmpty(), closeMenu) else emptyList(),
                    onImageEdit = launchImageEditor,
                    onVideoEdit = launchVideoEditor,
                    evolution = futaberEvolutionMenuItems(
                        close = closeMenu,
                        privacyEnabled = privacyMode?.enabled,
                        onTogglePrivacy = { privacyMode?.setEnabled?.invoke(privacyMode.enabled.not()) },
                        onTabStep = onVolumeKeyThread,
                        treeOn = display.extTree,
                        onToggleTree = { onWritePreference?.invoke(FutaberSettingKeys.EXT_TREE, if (display.extTree) "OFF" else "ON") }
                    )
                )
            ),
            search = FutaberMenuSearch(
                legacy = display.legacySearchFilter,
                legacyQuery = filter.query,
                onLegacyQueryChange = { filter = filter.copy(query = it) },
                legacyResultCount = rows.size.takeIf { filter.query.isNotBlank() && appliedQuery == filter.query },
                posts = posts,
                hidden = activeHidden,
                texts = postTexts,
                onShowFound = { found -> showBubble(FutaberQuoteKind.Search, found) }
            ),
            onDismiss = closeMenu
        )
        if (favoriteAskOpen) {
            // M14: futaber's 「画像をダウンロードしますか？」. The favourite is added with any choice but キャンセル; the pictures
            // are kept in the background (the app's own scope: leaving the thread must not stop it).
            val keep: (FutaberFavoriteImageChoice) -> Unit = { choice ->
                favoriteAskOpen = false
                closeMenu()
                onToggleFavorite()
                val http = mediaServices?.httpClient
                val files = mediaServices?.fileSystem
                val tasks = appTasks
                if (choice != FutaberFavoriteImageChoice.None && http != null && files != null && tasks != null) {
                    val savedPosts = posts
                    tasks.scope.launch {
                        futaberSaveFavoriteImages(http, files, board.id, board.name, board.url, ref.threadId, ref.title, savedPosts, choice)
                            .onFailure { tasks.notify("お気に入りの画像を保存できませんでした") }
                    }
                }
            }
            FutaberAlert(
                title = "画像をダウンロードしますか？",
                message = "お気に入りから外すと、ダウンロードした画像は削除されます。",
                buttons = listOf(
                    FutaberAlertButton("favorite-images-none", "ダウンロードしない", tag = "futaber-favorite-images-none") { keep(FutaberFavoriteImageChoice.None) },
                    FutaberAlertButton("favorite-images-thumbs", "サムネイルのみ", tag = "futaber-favorite-images-thumbs") { keep(FutaberFavoriteImageChoice.Thumbnails) },
                    FutaberAlertButton("favorite-images-full", "本体画像もダウンロードする", tag = "futaber-favorite-images-full") { keep(FutaberFavoriteImageChoice.Full) },
                    FutaberAlertButton("favorite-images-cancel", "キャンセル", FutaberAlertStyle.Cancel, tag = "futaber-favorite-images-cancel") { favoriteAskOpen = false }
                )
            )
        }
        if (mhtChoiceOpen) {
            // M15: futaber's 「MHT保存」 alert over the menu. A choice closes the menu and saves; キャンセル keeps the menu.
            FutaberAlert(
                title = "MHT保存",
                message = "サムネイルのみダウンロードしますか？",
                buttons = listOf(
                    FutaberAlertButton("mht-choose-thumbs", "サムネイルのみ", tag = "futaber-mht-choose-thumbs") {
                        mhtChoiceOpen = false; closeMenu(); mhtSaveFull = false
                    },
                    FutaberAlertButton("mht-choose-full", "本体画像もダウンロードする", tag = "futaber-mht-choose-full") {
                        mhtChoiceOpen = false; closeMenu(); mhtSaveFull = true
                    },
                    FutaberAlertButton("mht-choose-cancel", "キャンセル", FutaberAlertStyle.Cancel, tag = "futaber-mht-choose-cancel") {
                        mhtChoiceOpen = false
                    }
                )
            )
        }
    }
    val mhtFull = mhtSaveFull
    val mhtPage = load.page
    if (mhtFull != null && mhtLibrary != null && mhtPage != null) {
        FutaberMhtSaveFlow(mhtLibrary, board, ref, mhtPage, onDismiss = { mhtSaveFull = null }, fullImages = mhtFull)
    }
    if (extractOpen) {
        FutaberActionSheet(
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
            ),
            onDismiss = { extractOpen = false }
        )
    }
    if (archiveOpen) {
        // 「つまんね。に登録」 runs in the app's scope and reports like the remote actions (L107: leaving the
        // thread cancelled it). After leaving, a success opens no browser and is told as the app's notice.
        val sentFrom = board.id to ref.threadId
        FutaberArchiveMenuSheet(
            futaberCompatThreadUrl(board, ref.threadId), ref.title, mediaServices?.httpClient,
            scope = appTasks?.scope ?: scope,
            openUrl = { url -> if (threadScreenShown && currentThreadKey == sentFrom) openInBrowser(url) else Unit },
            onNotice = { deliverThreadResult(sentFrom, it) }, onDismiss = { archiveOpen = false }
        )
    }
    // The dialogs (each a window of its own), in this layer's scope so the screen's composable stays small.
    val markedPostNo = markTarget
    if (markedPostNo != null && markContext != null) com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow {
        androidx.compose.material3.AlertDialog(onDismissRequest = { markTarget = null },
            title = { Text("レスマーク") }, text = {
                com.valoser.futacha.shared.ui.board.ManualPostMarkActions(markedPostNo, markContext)
            }, confirmButton = { androidx.compose.material3.TextButton(onClick = { markTarget = null; actionPost = null }) { Text("閉じる") } })
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
            onConfirm = { imageOnly -> remoteConfirm = null; runRemoteAction(action, target, imageOnly) },
            onDismiss = { remoteConfirm = null },
            offerImageOnly = futaberOffersImageOnlyDelete(display.extPostDetailOps, action, target)
        )
    }
    // M21: the original's seven items, then this app's own and the extensions (futaberPostSheetExtras, for F-B2).
    actionPost?.let { target ->
        val close = { actionPost = null }
        val offline = offlineNotice != null || offlineCopyNotice != null
        FutaberActionSheet(
            title = futaberPostSheetTitle(posts, target),
            actions = futaberPostSheetActions(
                post = target,
                onQuote = onOpenPost,
                // M23: the original's copy panel; the old instant copy stays behind its switch (設定→スレッドの末尾).
                onCopy = {
                    if (display.instantCopy) clipboard.setText(AnnotatedString(futaberPostPlainText(target))) else sheets.copyPost = target
                },
                onRemote = { action ->
                    val blocked = futaberRemoteActionBlockedReason(action, offline, deleteKey)
                    when {
                        blocked != null -> remoteNotice = blocked
                        // M24: the report asks with the original's sheet (「レス:N」 / 「delを送信」).
                        action == FutaberRemoteAction.Report -> sheets.reportPost = target
                        action.confirmTitle == null -> runRemoteAction(action, target, false)
                        else -> remoteConfirm = action to target
                    }
                },
                extras = futaberPostSheetExtras(
                    onNgRegister = {
                        // The ID is the most precise key; without one, the name (unless it is the board's default).
                        ngDialogInitialHeader = futaberNgHeaderOf(target)
                        ngDialogOpen = true
                    },
                    markEnabled = markContext != null,
                    onMark = { markTarget = target.id },
                    detail = futaberPostDetailActions(
                        display.extPostDetailOps, postDetailOps, target, posts, close = close, onOpenPost = onOpenPost, openQuote = openQuote
                    )
                )
            ).map { action -> action.copy(onClick = { close(); action.onClick() }) },
            onDismiss = close
        )
    }
    // M25・C26: the picture's sheet, titled by its file name. Drawn after the bubbles, so the one opened from a bubble is
    // on top of it (L145).
    imagePost?.let { target ->
        val url = futaberImageUrlOf(target)
        val close = { imagePost = null }
        FutaberActionSheet(
            title = futaberImageSheetTitle(target, posts),
            actions = futaberImageSheetActions(
                url = url,
                viewerAvailable = mediaServices != null && load.page != null,
                servicesAvailable = mediaServices != null,
                lens = display.googleLens,
                onView = { mediaInitialPostNo = target.id; mediaOpen = true },
                onBrowser = urlSchemeOpener::browser,
                onAscii2d = { sheets.directSearch = futaberAscii2dSearch(it) },
                // M13: the picture's address through the registered URL scheme action.
                onUrlScheme = { url?.let(urlSchemeOpener::action) },
                onCopyUrl = { clipboard.setText(AnnotatedString(it)) },
                onNg = { imageNgTarget = target to it },
                onLens = { sheets.directSearch = futaberLensSearch(it) },
                extras = futaberImageSheetExtras(
                    url, mediaServices != null, onSearch = { imageSearchUrl = it },
                    menu = futaberImageMenuExtraActions(display.extImageMenu, url, imageSaver)
                )
            ).map { action -> action.copy(onClick = { close(); action.onClick() }) },
            onDismiss = close
        )
    }
    FutaberThreadSheetsHost(
        sheets = sheets, posts = posts, ngWords = ngWords, ngHeaders = ngHeaders, hiddenCount = hidden.size,
        onChangeNgWords = onChangeNgWords, onChangeNgHeaders = onChangeNgHeaders,
        reportNgOffered = display.extReportNg,
        onReport = { target, alsoNg -> runRemoteAction(FutaberRemoteAction.Report, target, alsoNg) },
        onOpenPost = onOpenPost,
        onCopy = { clipboard.setText(AnnotatedString(it)) },
        onShare = { shareText(it, "text/plain", null) },
        onNotice = { remoteNotice = it },
        mediaServices = mediaServices, preferences = sharedPreferences, openUrl = openInBrowser
    )
    }
    }
}

/**
 * The thread's title under the status area (V05): one line in the title colour, centred, cut with "…", over the
 * hairline that starts the posts. It replaces the bar with "←"; the action that button had stays here for a screen
 * reader ("カタログへ戻る").
 */
@Composable
private fun FutaberThreadTitleRow(title: String, onBack: () -> Unit) {
    val colors = LocalFutaberColors.current
    Column(Modifier.fillMaxWidth().background(colors.navBar).statusBarsPadding()) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = FUTABER_THREAD_TITLE_ROW_DP.dp).padding(horizontal = 24.dp)
                .testTag("futaber-thread-title-row"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = colors.navTitle,
                fontSize = 14.sp,
                lineHeight = 1.4.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction("カタログへ戻る") { onBack(); true })
                    }
                    .testTag("futaber-thread-title")
            )
        }
        HorizontalDivider(color = colors.statusSeparator, thickness = 0.5.dp)
    }
}

/**
 * The thread's bottom bar (V03): the original's images, bottom, operation menu, panel and refresh in five equal slots;
 * while bubbles are open, only the fourth and fifth slots, which close them. The panel button keeps its colour when
 * the thread is in the tabs (V36).
 */
@Composable
private fun FutaberThreadToolbar(
    bubblesOpen: Boolean,
    onCloseOneBubble: () -> Unit,
    onCloseAllBubbles: () -> Unit,
    galleryEnabled: Boolean,
    onOpenGallery: () -> Unit,
    onScrollToEnd: () -> Unit,
    onScrollToNew: () -> Unit,
    filterActive: Boolean,
    onOpenMenu: () -> Unit,
    isTabbed: Boolean,
    onToggleTab: () -> Unit,
    onOpenManage: () -> Unit,
    onRefresh: () -> Unit
) {
    val colors = LocalFutaberColors.current
    FutaberToolbarRow {
        if (bubblesOpen) {
            // While bubbles are open the bar only closes them: the fourth and fifth slots, as in the original.
            repeat(3) { Spacer(Modifier.weight(1f)) }
            FutaberToolbarButton(
                FutaberCloseBubbleIcon, "引用を1枚閉じる", "futaber-quote-close-one", Modifier.weight(1f), onClick = onCloseOneBubble
            )
            FutaberToolbarButton(
                FutaberCloseAllBubblesIcon, "引用をすべて閉じる", "futaber-quote-close-all", Modifier.weight(1f), onClick = onCloseAllBubbles
            )
        } else {
            // The original's order: images, bottom, menu, panel, refresh.
            FutaberToolbarButton(
                Icons.Outlined.Image, "画像一覧", "futaber-thread-gallery", Modifier.weight(1f),
                enabled = galleryEnabled, onClick = onOpenGallery
            )
            // A tap goes to the last post; a long press goes to the first new one, as in the original.
            FutaberToolbarButton(
                Icons.Outlined.VerticalAlignBottom, "末尾へ移動", "futaber-thread-bottom", Modifier.weight(1f),
                onClickLabel = "末尾へ移動", onLongClickLabel = "新着へスクロール",
                onLongClick = onScrollToNew, onClick = onScrollToEnd
            )
            FutaberToolbarButton(
                FutaberOperationMenuIcon, "操作メニュー", "futaber-thread-menu", Modifier.weight(1f),
                tint = if (filterActive) colors.accent else null, onClick = onOpenMenu
            )
            // A tap opens the panel; a long press adds the thread to the tabs (or takes it out), as in the original.
            FutaberToolbarButton(
                FutaberTwoPaneIcon, "履歴とタブ", "futaber-manage", Modifier.weight(1f),
                onClickLabel = "履歴とタブを開く",
                onLongClickLabel = if (isTabbed) "このスレッドをタブから外す" else "このスレッドをタブに追加",
                onLongClick = onToggleTab, onClick = onOpenManage
            )
            FutaberToolbarButton(
                Icons.Outlined.Refresh, "スレッドを更新", "futaber-thread-refresh", Modifier.weight(1f), onClick = onRefresh
            )
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
    /** G28: a long press on the header's IP opens its sheet (null: the IP is plain text, as in a bubble). */
    onIpLongPress: ((String) -> Unit)? = null,
    /** A tap on the post's own text (not a quote, ID, link, picture or counter), in root coordinates; null takes no tap. */
    onTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null,
    quoteMode: Boolean = false,
    onQuoteLine: (line: String) -> Unit = {},
    /** Opens this post's picture in the thread's existing media viewer. */
    onImageTap: (() -> Unit)? = null,
    onExtraImageTap: ((String) -> Unit)? = null,
    /** "n/N" when this post's IP posts more than once in the thread ([futaberIpCountLabels]); null otherwise. */
    ipCount: String? = null
) {
    val colors = LocalFutaberColors.current
    val display = LocalFutaberDisplaySettings.current
    // Kept across rows leaving and coming back while scrolling (re-audit L29: each row made its lines and links again).
    val lines = remember(post.messageHtml) { FutaberBodyLineCache.lines(post.messageHtml) }
    val bodyUrlLauncher = com.valoser.futacha.shared.util.rememberUrlLauncher()
    val bodyUrlMenu = com.valoser.futacha.shared.ui.rememberBodyUrlMenu(bodyUrlLauncher)
    // Where the post is, for a tap of the tap scroll (only measured while that is on).
    val placed = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    Column(
        Modifier.fillMaxWidth()
            // The post being read aloud: a thin bar at its start (a tinted ground would take the grey text under 4.5:1).
            .then(if (highlighted) Modifier.drawBehind {
                drawRect(colors.accent, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
            } else Modifier)
            .then(
                if (onLongPress == null) Modifier else Modifier
                    .then(if (onTap == null) Modifier else Modifier.onPlaced { placed[0] = it })
                    // A long press (and the tap scroll's tap when it is on) is all that is taken here, so taps on quotes
                    // and counters still reach them.
                    .pointerInput(onLongPress, onTap) {
                        detectTapGestures(
                            onLongPress = { onLongPress() },
                            onTap = onTap?.let { tap ->
                                { position -> placed[0]?.takeIf { it.isAttached }?.let { tap(it.localToRoot(position)) } }
                            }
                        )
                    }
                    .semantics { onLongClick(label = "レスの操作を開く") { onLongPress(); true } }
            )
            .padding(
                start = 10.dp + (depth.coerceIn(0, FUTABER_TREE_MAX_INDENT_LEVELS) * 14).dp,
                end = 10.dp, top = display.postGapDp.dp, bottom = display.postGapDp.dp
            )
            .testTag("futaber-post")
    ) {
        if (manuallyMarked) Text("★ マーク", color = colors.body, fontSize = display.sp(12f))
        // The header in futaber's order, as one flowing text (V08・V25); the replies' mark and the ID are tapped in it.
        FutaberPostHeader(
            ordinal, post, idLabel, ipCount, replyCount, onRepliesTap, onIdTap, onSaidaneTap, onNgTap, onLongPress,
            onIpLongPress = onIpLongPress, onTap = onTap
        )
        // あいもげ: dice results in bold, hidden text covered until tapped (null for a Futaba body).
        val decorations = remember(post.messageHtml) { com.valoser.futacha.shared.ui.board.aimogeLineDecorations(post.messageHtml) }
        val firstSpoilers = remember(decorations) { decorations?.let { com.valoser.futacha.shared.ui.board.aimogeFirstSpoilerIndexes(it) } }
        var revealedSpoilers by remember(post.messageHtml) { mutableStateOf(emptySet<Int>()) }
        Column(Modifier.padding(top = FUTABER_POST_HEADER_GAP_DP.dp)) {
            lines.forEachIndexed { lineIndex, line ->
                val links = remember(line) { FutaberBodyLineCache.links(line) }
                val lineDecorations = decorations?.getOrNull(lineIndex).orEmpty()
                val hasSpoiler = lineDecorations.any { it.kind == com.valoser.futacha.shared.ui.board.AimogeDecorationKind.SPOILER }
                val annotatedLine = remember(line, links, colors.link, lineDecorations, revealedSpoilers) { androidx.compose.ui.text.buildAnnotatedString {
                    append(line)
                    links.forEach { link ->
                        addStringAnnotation("body-url", link.url, link.start, link.endExclusive)
                        addStyle(androidx.compose.ui.text.SpanStyle(color = colors.link,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), link.start, link.endExclusive)
                    }
                }.withAimogeDecorations(lineDecorations, revealedSpoilers, colors.body, firstSpoilers?.getOrNull(lineIndex) ?: 0) }
                var lineLayout by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                val isQuote = isFutaberQuoteLine(line)
                val canOpen = isQuote && !post.isDeleted && futaberQuoteTargetIds(post, line).isNotEmpty()
                // Read at the tap, not written to a state on every frame of a scroll (re-audit L9).
                val placed = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
                Text(
                    text = annotatedLine,
                    onTextLayout = { lineLayout = it },
                    color = when {
                        post.isDeleted || post.isIsolated -> colors.deleted
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
                            .onPlaced { placed[0] = it }
                            .clickable(onClickLabel = "引用元を表示") {
                                placed[0]?.takeIf { it.isAttached }?.boundsInRoot()?.let {
                                    onQuoteTap(line, FutaberAnchor(it.left + 24f, it.top, it.bottom))
                                }
                            }
                            .testTag("futaber-quote-line")
                    } else if (links.isNotEmpty() || hasSpoiler) Modifier.pointerInput(annotatedLine, bodyUrlLauncher) {
                        detectTapGestures(
                            onTap = { position -> lineLayout?.getOffsetForPosition(position)?.let { offset ->
                                annotatedLine.aimogeSpoilerAt(offset)?.let { index ->
                                    revealedSpoilers = toggleAimogeSpoiler(revealedSpoilers, index)
                                    return@let
                                }
                                annotatedLine.getStringAnnotations("body-url", offset, offset).firstOrNull()?.item?.let(bodyUrlLauncher)
                            } },
                            onLongPress = { position ->
                                val url = lineLayout?.getOffsetForPosition(position)?.let { offset ->
                                    annotatedLine.getStringAnnotations("body-url", offset, offset).firstOrNull()?.item
                                }
                                if (url != null) bodyUrlMenu(url) else onLongPress?.invoke()
                            }
                        )
                    } else Modifier
                )
            }
        }
        FutaberPostAttachment(post, smallImages, onImageTap, onImageLongPress, onLongPress)
        // 本文のあぷ・あぷ小（fu123.jpg 等）：他モードと同じく、レスの中にサムネイルを並べ、押すとビューアのその頁（2026-11-12）。
        if (display.extApuPreview) {
            val apuUrls = remember(post.messageHtml, post.imageUrl, post.thumbnailUrl) { futaberInlineApuUrls(post) }
            if (apuUrls.isNotEmpty()) {
                Box(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp).testTag("futaber-apu-previews")) {
                    com.valoser.futacha.shared.ui.compat.CompatInlineApuSmallPreviews(apuUrls,
                        if (smallImages) 60 else 120, 1f) { url -> onExtraImageTap?.invoke(url) }
                }
            }
        }
        // あいもげ: the post's 2nd and 3rd files, each opening its own page of the viewer.
        val extraAttachments = remember(post) { com.valoser.futacha.shared.service.threadSaveExtraAttachments(post) }
        if (extraAttachments.isNotEmpty()) {
            com.valoser.futacha.shared.ui.image.PostExtraAttachments(
                attachments = extraAttachments,
                postKey = post.id,
                size = if (smallImages) 48.dp else 96.dp,
                onClick = { url, _ -> onExtraImageTap?.invoke(url) },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 8.dp, end = 8.dp, bottom = 6.dp)
            )
        }
    }
}

/** 本文のあぷ・あぷ小のうち、レスの添付そのものでない物（引用行の分は除く）。 */
internal fun futaberInlineApuUrls(post: Post): List<String> {
    val main = setOfNotNull(
        com.valoser.futacha.shared.ui.compat.compatMediaFileIdentity(post.imageUrl),
        com.valoser.futacha.shared.ui.compat.compatMediaFileIdentity(post.thumbnailUrl)
    )
    return com.valoser.futacha.shared.ui.compat.compatInlineApuSmallMediaUrls(post.messageHtml)
        .filterNot { com.valoser.futacha.shared.ui.compat.compatMediaFileIdentity(it) in main }
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
    modifier: Modifier = Modifier,
    /** Drawn after the count (the search's "▲ n/N ▼"); nothing when null. */
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    val what = buildList {
        if (filter.query.isNotBlank()) add("「${filter.query.trim()}」")
        if (filter.repliesOnly) add("返信が多いレス")
        filter.extract?.let { add(it.label) }
        if (filter.sameId.isNotBlank()) add("ID:${filter.sameId}")
    }.joinToString(" ")
    Row(
        modifier.padding(8.dp).background(colors.bar, FutaberShapes.card)
            .border(0.5.dp, colors.separator, FutaberShapes.card)
            .clickable(onClickLabel = "絞り込みを解除", onClick = onClear)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("futaber-filter-chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$what ${count}件", color = colors.body, fontSize = 12.sp)
        trailing?.invoke()
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
            .background(colors.bar, FutaberShapes.card)
            .border(0.5.dp, colors.separator, FutaberShapes.card)
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

/** The lines of a post's body and the links of a line, built lately (re-audit L29). Bounded; used from composition only. */
private object FutaberBodyLineCache {
    private const val MAX = 400
    private val lines = LinkedHashMap<String, List<String>>()

    fun lines(html: String): List<String> = lines.cached(html) { messageHtmlToLines(it) }

    fun links(line: String) = com.valoser.futacha.shared.compat.compatInlineLinksCached(
        line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    )

    private fun <V> LinkedHashMap<String, V>.cached(key: String, make: (String) -> V): V {
        val value = remove(key) ?: make(key)
        this[key] = value
        if (size > MAX) remove(keys.first())
        return value
    }
}
