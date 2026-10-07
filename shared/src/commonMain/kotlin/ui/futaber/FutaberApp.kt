package com.valoser.futacha.shared.ui.futaber

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.buildCompatCatalogRuleIndex
import com.valoser.futacha.shared.compat.compatCatalogRulesForBoard
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.compat.normalizeCompatNgValue
import com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository
import com.valoser.futacha.shared.ui.compat.fetchDefaultCompatBoardsFromMenu
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.toThreadPage
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.ui.compat.compatManualSaveLocation
import com.valoser.futacha.shared.ui.compat.createCompatSavedThreadRepository
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.ui.compat.CompatWatcherManager
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.compat.rememberCompatShareLauncher
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtBox
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtEntry
import com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary
import com.valoser.futacha.shared.ui.futaber.mht.futaberBoardForMht
import com.valoser.futacha.shared.ui.futaber.mht.futaberMhtCanPost
import com.valoser.futacha.shared.ui.futaber.mht.rememberFutaberMhtPickerLauncher
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.state.AppStateHistoryScrollUpdateRequest
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.isMockBoard
import com.valoser.futacha.shared.ui.board.requireWritableThreadBoard
import com.valoser.futacha.shared.ui.board.performThreadReplyAction
import com.valoser.futacha.shared.ui.board.performThreadAction
import com.valoser.futacha.shared.ui.board.normalizeDeleteKeyForSubmit
import com.valoser.futacha.shared.ui.board.checkPostingNoticeIfNeeded
import com.valoser.futacha.shared.ui.board.buildThreadReplyActionConfig
import com.valoser.futacha.shared.ui.board.buildThreadReplyActionCallbacks
import com.valoser.futacha.shared.ui.board.buildThreadActionFailureMessage
import com.valoser.futacha.shared.ui.board.ThreadReplyDraft
import com.valoser.futacha.shared.ui.board.ThreadActionRunResult
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.dismissHistoryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val FUTABER_TAG = "FutaberApp"

/** Share of the width the board list takes; the main screen slides right by the same amount. */
internal const val FUTABER_DRAWER_WIDTH_FRACTION = 0.7f

private val FutaberThreadRefSaver = Saver<FutaberThreadRef?, List<String>>(
    save = { ref ->
        if (ref == null) emptyList()
        else listOf(
            ref.boardId, ref.threadId, ref.title, ref.thumbnailUrl, ref.replyCount.toString(),
            ref.seenCount.toString(), ref.resumePostId, ref.resumeIndex.toString(), ref.resumeOffset.toString()
        )
    },
    restore = { parts ->
        if (parts.size != 9) null
        else FutaberThreadRef(
            parts[0], parts[1], parts[2], parts[3], parts[4].toIntOrNull() ?: 0,
            parts[5].toIntOrNull() ?: 0, parts[6], parts[7].toIntOrNull() ?: 0, parts[8].toIntOrNull() ?: 0
        )
    }
)

private val FutaberPostTargetSaver = Saver<FutaberPostTarget?, List<String>>(
    save = { futaberPostTargetToStrings(it) },
    restore = { futaberPostTargetFromStrings(it) }
)

/**
 * Root of the ふたばー mode: board drawer -> catalog -> thread. It reads and writes the
 * same boards and history as ふたちゃ (AppStateStore); its own choices (layout, sort,
 * theme, last board) live under `compat.futaber.*`.
 */
@OptIn(ExperimentalTime::class, FlowPreview::class)
@Composable
internal fun FutaberApp(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    repository: BoardRepository,
    stateStore: AppStateStore,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    savePreference: (key: String, value: String) -> Unit,
    mediaServices: FutaberMediaServices? = null,
    appVersion: String = "1.0",
    /** Refreshes the threads of the history (the history band's "一括更新"); null when the host cannot. */
    onRefreshHistory: (suspend () -> Unit)? = null,
    /** The auto-saved copies of the threads: deleting a history row deletes its copy too (as in ふたちゃ); null = none to clean. */
    autoSavedThreadRepository: SavedThreadRepository? = null,
    /** A thread link from another app (a `*.2chan.net/…/res/N.htm` URL); [onThreadDeepLinkConsumed] is called once it was handled. */
    initialThreadDeepLink: String? = null,
    onThreadDeepLinkConsumed: (String) -> Unit = {},
    /** A board link from another app; opens the board's catalog. */
    initialBoardDeepLink: String? = null,
    onBoardDeepLinkConsumed: (String) -> Unit = {},
    /** The host's handling of the watch-word alert switch (it asks for the notification permission); null = just store it. */
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val themeMode = FutaberThemeMode.fromPersistedValue(preferences[FutaberPreferenceKeys.THEME])
    val displaySettings = FutaberDisplaySettings.from(preferences)
    val displayStyle = FutaberCatalogDisplayStyle.fromPersistedValue(preferences[FutaberPreferenceKeys.CATALOG_DISPLAY_STYLE])
    val sortMode = futaberCatalogModeFromPersisted(preferences[FutaberPreferenceKeys.CATALOG_SORT])

    var selectedBoardId by rememberSaveable { mutableStateOf<String?>(null) }
    var openThread by rememberSaveable(stateSaver = FutaberThreadRefSaver) { mutableStateOf<FutaberThreadRef?>(null) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var managePanelOpen by rememberSaveable { mutableStateOf(false) }
    // A line at the bottom for what the person should know (a failed save, a full tab list); it goes away by itself.
    var appNotice by remember { mutableStateOf<String?>(null) }
    val notify: (String) -> Unit = { appNotice = it }

    val board = boards.firstOrNull { it.id == selectedBoardId }
        ?: boards.firstOrNull { it.id == preferences[FutaberPreferenceKeys.LAST_BOARD_ID] }
        ?: boards.firstOrNull()
    // A thread of a board that was deleted or is no longer selected is closed.
    val thread = openThread?.takeIf { it.boardId == board?.id }
    val mockRepository = remember { FakeBoardRepository() }
    // Applied to the latest stored list, not the one the drawer showed.
    val editBoards: (transform: (List<BoardSummary>) -> List<BoardSummary>) -> Unit = { transform ->
        scope.launch {
            try {
                stateStore.updateBoards(transform)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to edit boards", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }

    // The catalog NG rules (threads, words, images) made in the other modes apply to this board's catalog too.
    val catalogNgIndex = remember(ngRules, board) {
        // A board with no key of its own (the tutorial board) still gets the rules made for every board ("*").
        board?.let { buildCompatCatalogRuleIndex(compatCatalogRulesForBoard(ngRules, futaberNgScopeKey(it).orEmpty())) }
    }
    val registerNgThread: (CatalogItem) -> Unit = register@{ item ->
        val target = board ?: return@register
        val key = futaberNgScopeKey(target) ?: return@register
        val store = mediaServices?.store ?: return@register
        scope.launch {
            try {
                // The rule belongs to the board in the shared store, which learns of this mode's boards here.
                store.importModernBoards(boards)
                val value = item.threadUrl.trim().normalizeCompatNgValue()
                if (value.isNotEmpty()) {
                    val saved = store.upsertNgRule(
                        CompatNgRule(
                            id = compatNgRuleId(CompatNgKind.CATALOG_REFUSE, key, value),
                            kind = CompatNgKind.CATALOG_REFUSE,
                            scopeKey = key,
                            normalizedValue = value,
                            memo = futaberSafeTake(item.title.orEmpty(), 4),
                            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                    // False: the board of the rule is not in the shared store (nothing was written).
                    if (!saved) Logger.e(FUTABER_TAG, "The NG thread rule was not saved: its board is not in the shared store (key=$key)")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to register an NG thread", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    val catalogThreadRules = remember(ngRules) {
        ngRules.filter { it.kind == CompatNgKind.CATALOG_REFUSE || it.kind == CompatNgKind.CATALOG_THREAD }
            .sortedByDescending { it.createdAtEpochMillis }
    }
    // The rules of the other modes that hide catalog threads by a word of the title or by their picture also apply here,
    // so they are listed in the settings and can be taken back there.
    val catalogWordRules = remember(ngRules) { futaberCatalogWordRules(ngRules) }
    val catalogImageRules = remember(ngRules) { futaberCatalogImageRules(ngRules) }
    // The rules by look of the picture that apply to this board's catalog (checked by hashing the catalog's pictures).
    val catalogPhashRules = remember(ngRules, board) {
        board?.let { compatCatalogRulesForBoard(ngRules, futaberNgScopeKey(it).orEmpty()) }
            .orEmpty().filter { it.kind == CompatNgKind.CATALOG_IMAGE_PHASH }
    }
    val catalogPhashThreshold = preferences[futaberImageNgThresholdKey]?.toIntOrNull()
        ?: com.valoser.futacha.shared.compat.CompatImagePhash.DEFAULT_THRESHOLD
    val imageNgRules = remember(ngRules) {
        ngRules.filter { it.kind == CompatNgKind.THREAD_IMAGE_PHASH }.sortedByDescending { it.createdAtEpochMillis }
    }
    val deleteNgRule: (String) -> Unit = { ruleId ->
        val store = mediaServices?.store
        if (store != null) scope.launch {
            try { store.deleteNgRule(ruleId) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to delete an NG rule", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    // Threads are fetched through the shared layer ふたちゃ uses: the cache server when the user turned
    // 通信の軽量化 on (off by default), and the archives fill in posts a long thread's page is missing.
    // The catalog keeps the plain repository, so its layout and size stay as they are.
    val threadRepository = remember(repository, mediaServices?.store, mediaServices?.httpClient) {
        mediaServices?.let { FutachaSharedBoardRepository(repository, it.store, it.httpClient) } ?: repository
    }
    // "板一覧から一括追加": the official list's boards that are not registered yet (the list ふたちゃ uses).
    val bulkAddBoards: (suspend () -> Result<Int>)? = mediaServices?.httpClient?.let { client ->
        {
            try {
                val discovered = fetchDefaultCompatBoardsFromMenu(client, modernBoardsToCompatibility(boards)).getOrThrow()
                var added = 0
                stateStore.updateBoards { current ->
                    val next = futaberAddDiscoveredBoards(current, discovered.map { it.name to it.originalUrl })
                    added = next.size - current.size
                    next
                }
                Result.success(added)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }
    val ngWords by stateStore.observedNgWords.collectAsState(emptyList<String>())
    val ngHeaders by stateStore.observedNgHeaders.collectAsState(emptyList<String>())
    val catalogNgWords by stateStore.observedCatalogNgWords.collectAsState(emptyList<String>())
    // The posts this person wrote, per thread (written when a post succeeds): "レスを抽出" → 自分の書き込み.
    val selfPostMap by stateStore.selfPostIdentifiersByThread.collectAsState(emptyMap<String, List<String>>())
    val launchStore: (what: String, block: suspend () -> Unit) -> Unit = { what, block ->
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to $what", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    // Settings of this mode are written one at a time, in the order they were asked for, straight to the shared store (a
    // failure is shown to the person); without the store, the host's own writer is used.
    val preferenceMutex = remember { Mutex() }
    val writePreference: (key: String, value: String) -> Unit = { key, value ->
        val store = mediaServices?.store
        if (store == null) savePreference(key, value) else scope.launch {
            try {
                preferenceMutex.withLock { store.savePreference(key, value) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to save $key", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    // A list kept in one setting (the tabs, the favourites) is changed by a function applied to the value stored at that
    // moment, not to the one the screen last showed: two quick changes ("×" twice) both count. A null result writes nothing.
    val editPreference: (key: String, transform: (String?) -> String?) -> Unit = { key, transform ->
        val store = mediaServices?.store
        if (store == null) {
            transform(preferences[key])?.let { savePreference(key, it) }
        } else scope.launch {
            try {
                preferenceMutex.withLock {
                    transform(store.loadPreference(key))?.let { store.savePreference(key, it) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to change $key", error)
                notify(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    // ---- Writing: a reply to the open thread, or a new thread on the board ----
    var postTarget by rememberSaveable(stateSaver = FutaberPostTargetSaver) { mutableStateOf<FutaberPostTarget?>(null) }
    var postSubject by rememberSaveable { mutableStateOf("") }
    var postComment by rememberSaveable { mutableStateOf("") }
    // Quote mode: the thread is shown for tapping lines while the draft waits in a card.
    var quoteMode by rememberSaveable { mutableStateOf(false) }
    val postAttachment = remember { FutaberAttachment(scope) }
    var attachmentOwnerKey by remember { mutableStateOf<String?>(null) }
    var postCapabilities by remember { mutableStateOf<com.valoser.futacha.shared.network.BoardPostingCapabilities?>(null) }
    val pickerPreference by stateStore.attachmentPickerPreference.collectAsState(com.valoser.futacha.shared.util.AttachmentPickerPreference.MEDIA)
    val preferredFileManager by stateStore.getPreferredFileManager().collectAsState(null)
    var threadRefreshSignal by remember { mutableStateOf(0) }
    var catalogRefreshSignal by remember { mutableStateOf(0) }
    val deleteKey by stateStore.observedLastUsedDeleteKey.collectAsState("")
    val postSettings = remember(
        preferences[FutaberPreferenceKeys.POST_NAME], preferences[FutaberPreferenceKeys.POST_EMAIL],
        preferences[FutaberPreferenceKeys.POST_CONFIRM]
    ) { FutaberPostSettings.from(preferences) }
    val drafts = remember(preferences[FutaberPreferenceKeys.DRAFTS]) { decodeFutaberDrafts(preferences[FutaberPreferenceKeys.DRAFTS]) }
    val quoteTarget = (postTarget as? FutaberPostTarget.Reply)?.takeIf { quoteMode && thread?.threadId == it.threadId }
    /** Opens the write screen with the saved draft for [target], plus [quote] on its own line. */
    val openPost: (FutaberPostTarget, String) -> Unit = { target, quote ->
        // The text still on the screen wins over the saved draft when the same post is being written (a round trip through
        // quote mode keeps the target, and the draft is saved only a moment after typing stops).
        val start = futaberPostStart(futaberDraftFor(drafts, target.draftKey), target, postTarget, postSubject, postComment)
        postSubject = start.subject
        postComment = futaberAppendQuote(start.comment, quote)
        quoteMode = false
        // A file picked for one post is not carried into another thread's reply.
        if (attachmentOwnerKey != target.draftKey) postAttachment.clear()
        attachmentOwnerKey = target.draftKey
        postTarget = target
    }
    val saveDraftNow: (FutaberPostTarget, String, String) -> Unit = { target, subject, comment ->
        val draft = FutaberDraft(target.draftKey, subject, comment, Clock.System.now().toEpochMilliseconds())
        // Applied to the drafts stored now, so saving this one does not bring back a draft another screen just cleared.
        editPreference(FutaberPreferenceKeys.DRAFTS) { stored ->
            encodeFutaberDrafts(futaberUpsertDraft(decodeFutaberDrafts(stored), draft))
        }
    }
    // The draft follows the text shortly after typing stops, in the write screen and in quote mode alike.
    LaunchedEffect(postTarget?.draftKey) {
        val target = postTarget ?: return@LaunchedEffect
        snapshotFlow { postSubject to postComment }.debounce(400).collect { (subject, comment) ->
            saveDraftNow(target, subject, comment)
        }
    }
    // The board's own limits (size, formats, whether replies may carry a file), refreshed when writing starts.
    LaunchedEffect(postTarget?.draftKey) {
        val target = postTarget ?: return@LaunchedEffect
        val targetBoard = boards.firstOrNull { it.id == target.boardId } ?: return@LaunchedEffect
        val fallback = com.valoser.futacha.shared.network.defaultBoardPostingCapabilities(targetBoard.url)
        postCapabilities = fallback
        if (targetBoard.isMockBoard()) return@LaunchedEffect
        try {
            postCapabilities = repository.getPostingCapabilities(targetBoard.url, (target as? FutaberPostTarget.Reply)?.threadId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Posting capabilities unavailable; using the board's known limits", error)
        }
    }
    val sendPost: suspend (FutaberPostTarget, FutaberPostInput) -> Result<String?> = send@{ target, input ->
        val targetBoard = boards.firstOrNull { it.id == target.boardId }
            ?: return@send Result.failure(IllegalStateException("板が登録されていません"))
        val repo = if (targetBoard.isMockBoard()) mockRepository else repository
        // Reading or writing the "agreed to the notice" flag can fail like any store access; nothing was sent yet, so it is
        // a plain failure the write screen shows (and keeps the text).
        val noticeAgreed = try {
            checkPostingNoticeIfNeeded(stateStore)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to check the posting notice", error)
            return@send Result.failure(IllegalStateException("投稿のご案内を確認できませんでした。もう一度お試しください", error))
        }
        if (!noticeAgreed) {
            return@send Result.failure(IllegalStateException("投稿のご案内に同意すると書き込めます"))
        }
        val password = normalizeDeleteKeyForSubmit(deleteKey)
        val outcome = when (target) {
            is FutaberPostTarget.Reply -> {
                val config = buildThreadReplyActionConfig(
                    boardUrl = targetBoard.url,
                    threadId = target.threadId,
                    draft = ThreadReplyDraft(
                        name = postSettings.name, email = postSettings.email, subject = input.subject,
                        comment = input.comment, password = password, imageData = input.image
                    ),
                    normalizedPassword = password
                )
                performThreadReplyAction(config, buildThreadReplyActionCallbacks(repo))
            }
            is FutaberPostTarget.CreateThread -> performThreadAction {
                requireWritableThreadBoard(targetBoard.url)
                repo.createThread(
                    board = targetBoard.url, name = postSettings.name, email = postSettings.email,
                    subject = input.subject, comment = input.comment, password = password,
                    imageFile = input.image?.bytes, imageFileName = input.image?.fileName,
                    textOnly = input.image == null, handwriting = input.image?.isHandwriting == true
                )
            }
        }
        when (outcome) {
            is ThreadActionRunResult.Success -> {
                val posted = outcome.value
                // The post is already out: a failure to note it on this device must not turn the result into a failure
                // (the person would send it again), so it is only told.
                try {
                    if (target is FutaberPostTarget.Reply && !posted.isNullOrBlank()) {
                        // Remembered so "そうだね" and the like know the post is the user's own.
                        stateStore.addSelfPostIdentifier(target.threadId, posted, targetBoard.id)
                    }
                    stateStore.setLastUsedDeleteKey(password)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e(FUTABER_TAG, "Failed to note a sent post", error)
                    notify(FUTABER_POST_NOTE_FAILED_NOTICE)
                }
                Result.success(posted)
            }
            is ThreadActionRunResult.Failure -> Result.failure(
                IllegalStateException(
                    buildThreadActionFailureMessage(
                        if (target is FutaberPostTarget.Reply) "返信の送信に失敗しました" else "スレッド作成に失敗しました",
                        outcome.error
                    ),
                    outcome.error
                )
            )
        }
    }

    val tabKeys = remember(preferences[FutaberPreferenceKeys.TABS]) {
        decodeFutaberTabs(preferences[FutaberPreferenceKeys.TABS])
    }
    val tabViews = remember(tabKeys, history) { futaberTabViews(tabKeys, history) }
    // The tabs closed during this run, the latest first ("閉じたタブを元に戻す", an extension). Only a person's own
    // closing is kept: the clean-up of tabs whose thread is gone does not remember anything.
    var closedTabs by remember { mutableStateOf(emptyList<FutaberTabKey>()) }
    // Changes the tabs by a function applied to the tabs stored at that moment. [recordClosed] is false for the clean-up.
    val editTabs: (recordClosed: Boolean, transform: (List<FutaberTabKey>) -> List<FutaberTabKey>) -> Unit = { recordClosed, transform ->
        editPreference(FutaberPreferenceKeys.TABS) { stored ->
            val current = decodeFutaberTabs(stored)
            val next = transform(current)
            val removed = current.filter { it !in next }
            if (recordClosed && removed.isNotEmpty()) closedTabs = futaberRememberClosedTabs(closedTabs, removed, next)
            if (next == current) null else encodeFutaberTabs(next)
        }
    }
    val saveTabs: ((List<FutaberTabKey>) -> List<FutaberTabKey>) -> Unit = { transform -> editTabs(true, transform) }
    val toggleTab: (FutaberTabKey) -> Unit = { key ->
        editPreference(FutaberPreferenceKeys.TABS) { stored ->
            val current = decodeFutaberTabs(stored)
            when (futaberTabToggleOutcome(current, key)) {
                FutaberTabToggleOutcome.Full -> { notify(FUTABER_TAB_LIMIT_NOTICE); null }
                FutaberTabToggleOutcome.Added -> encodeFutaberTabs(current + key)
                FutaberTabToggleOutcome.Removed -> {
                    val next = current - key
                    closedTabs = futaberRememberClosedTabs(closedTabs, listOf(key), next)
                    encodeFutaberTabs(next)
                }
            }
        }
    }
    val favorites = remember(preferences[FutaberPreferenceKeys.FAVORITES]) {
        decodeFutaberFavorites(preferences[FutaberPreferenceKeys.FAVORITES])
    }
    val favoriteViews = remember(favorites, history) { futaberFavoriteViews(favorites, history) }
    val editFavorites: ((List<FutaberFavorite>) -> List<FutaberFavorite>) -> Unit = { transform ->
        editPreference(FutaberPreferenceKeys.FAVORITES) { stored ->
            val current = decodeFutaberFavorites(stored)
            val next = transform(current)
            if (next == current) null else encodeFutaberFavorites(next)
        }
    }
    // Patrol results (keyword matches) are shared with the other modes; this mode only lists them.
    val watcher = remember(mediaServices?.store) { mediaServices?.store?.let(::CompatWatcherRepository) }
    val watchResults by produceState(emptyList<CompatWatchResult>(), watcher) {
        val store = mediaServices?.store
        if (watcher == null || store == null) return@produceState
        store.preferences.map { futaberWatchResultSlots(it) }.distinctUntilChanged().collect {
            value = try {
                watcher.load(Clock.System.now().toEpochMilliseconds())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to read the patrol results", error)
                emptyList()
            }
        }
    }
    var watcherOpen by rememberSaveable { mutableStateOf(false) }
    // ---- Saved threads (保存箱): the shared manual-save index, wherever the user chose to save ----
    val saveLocation = preferences.compatManualSaveLocation()
    val savedFileSystem = mediaServices?.fileSystem
    val savedRepository = remember(savedFileSystem, saveLocation) {
        savedFileSystem?.let { createCompatSavedThreadRepository(it, saveLocation) }
    }
    var savedBox by remember { mutableStateOf(FutaberSavedBoxState()) }
    var savedTick by remember { mutableStateOf(0) }
    var savedNotice by remember { mutableStateOf<String?>(null) }
    var offlineView by remember { mutableStateOf<FutaberOfflineView?>(null) }
    // A removed board takes its favourites with it.
    LaunchedEffect(favorites, boards) {
        val pruned = futaberPruneFavorites(favorites, boards)
        if (pruned != favorites) editFavorites { futaberPruneFavorites(it, boards) }
    }
    // Deleting a thread (or its board) cleans its tab up; the thread being opened is kept
    // because its history row may not have been written yet.
    val openKey = thread?.let { FutaberTabKey(it.boardId, it.threadId) }
    LaunchedEffect(tabKeys, history, boards, openKey) {
        val pruned = futaberPruneTabs(tabKeys, history, boards, keep = openKey)
        if (pruned != tabKeys) editTabs(false) { futaberPruneTabs(it, history, boards, keep = openKey) }
    }
    // Opening a screen (from the catalog, a tab or the history) is what writes the history.
    val openRef: (BoardSummary, FutaberThreadRef) -> Unit = { target, ref ->
        val existing = findFutaberHistoryEntry(history, target, ref.threadId)
        selectedBoardId = target.id
        offlineView = null
        // Carries what the history said before this visit (posts seen, reading position).
        // The posts seen when the thread was last left are this mode's own record: the history row's count also moves
        // with background refreshes and the other modes, and would leave nothing marked as new.
        openThread = futaberRefWithHistory(
            ref, existing, futaberSeenCountFor(preferences[FutaberPreferenceKeys.SEEN], target.id, ref.threadId)
        )
        managePanelOpen = false
        writePreference(FutaberPreferenceKeys.LAST_BOARD_ID, target.id)
        scope.launch {
            try {
                // upsertHistoryEntry only updates a row that exists; opening must also add one.
                stateStore.prependOrReplaceHistoryEntry(
                    buildFutaberOpenHistoryEntry(
                        existing = existing,
                        board = target,
                        ref = ref,
                        nowEpochMillis = Clock.System.now().toEpochMilliseconds()
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to record history", error)
            }
        }
    }
    // Links from other apps. The board must be registered here; a link to one that is not says so instead of doing nothing.
    LaunchedEffect(initialThreadDeepLink, boards) {
        val raw = initialThreadDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (boards.isEmpty()) return@LaunchedEffect
        val parsed = com.valoser.futacha.shared.compat.canonicalizeThreadUrl(raw)
        val target = parsed?.let { link ->
            boards.firstOrNull { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it.url) == link.canonicalBoardUrl }
        }
        if (parsed != null && target != null) {
            openRef(target, FutaberThreadRef(target.id, parsed.threadNo, "", "", 0))
        } else {
            notify("このリンクの板が登録されていません。板一覧から追加してください")
        }
        onThreadDeepLinkConsumed(raw)
    }
    LaunchedEffect(initialBoardDeepLink, boards) {
        val raw = initialBoardDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (boards.isEmpty()) return@LaunchedEffect
        val canonical = com.valoser.futacha.shared.compat.canonicalizeBoardUrl(raw)
        val target = canonical?.let { link -> boards.firstOrNull { com.valoser.futacha.shared.compat.canonicalizeBoardUrl(it.url) == link } }
        if (target != null) {
            selectedBoardId = target.id
            openThread = null
            offlineView = null
            managePanelOpen = false
            writePreference(FutaberPreferenceKeys.LAST_BOARD_ID, target.id)
        } else {
            notify("このリンクの板が登録されていません。板一覧から追加してください")
        }
        onBoardDeepLinkConsumed(raw)
    }
    val openTab: (FutaberTabView) -> Unit = { tab ->
        boards.firstOrNull { it.id == tab.key.boardId }?.let { target ->
            openRef(target, FutaberThreadRef(target.id, tab.key.threadId, tab.title, tab.thumbnailUrl, tab.replyCount))
        }
    }
    var tabSheet by remember { mutableStateOf<FutaberTabView?>(null) }
    val tabStrip: @Composable () -> Unit = {
        FutaberTabStrip(tabViews, openKey, openTab, onLongPress = { tabSheet = it })
    }

    LaunchedEffect(managePanelOpen, savedRepository, savedTick) {
        val repo = savedRepository
        if (!managePanelOpen || repo == null) return@LaunchedEffect
        savedBox = savedBox.copy(isLoading = true, errorMessage = null)
        savedBox = try {
            FutaberSavedBoxState(futaberSavedOrder(repo.getAllThreads()), isLoading = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to list the saved threads", error)
            FutaberSavedBoxState(emptyList(), isLoading = false, errorMessage = futaberFriendlyLoadError(error, "保存箱", "保存箱を読み込めませんでした"))
        }
    }
    val openSaved: (SavedThread) -> Unit = { saved ->
        val target = futaberBoardForSaved(saved, boards)
        val repo = savedRepository
        val fileSystem = savedFileSystem
        if (target != null && repo != null && fileSystem != null) scope.launch {
            try {
                val metadata = repo.loadThreadMetadata(saved.threadId, saved.boardId).getOrThrow()
                val page = metadata.toThreadPage(
                    fileSystem, (saveLocation as? SaveLocation.Path)?.path ?: MANUAL_SAVE_DIRECTORY, saveLocation
                )
                offlineView = FutaberOfflineView(target, futaberRefForSaved(saved, target), page)
                managePanelOpen = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to open a saved thread", error)
                savedNotice = futaberFriendlyLoadError(error, "保存したスレッド", "保存したスレッドを開けませんでした")
            }
        }
    }
    val deleteSaved: (SavedThread) -> Unit = { saved ->
        val repo = savedRepository
        if (repo != null) scope.launch {
            try {
                repo.deleteThread(saved.threadId, saved.boardId).getOrThrow()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(FUTABER_TAG, "Failed to delete a saved thread", error)
                savedNotice = futaberFriendlyLoadError(error, "保存したスレッド", "削除できませんでした")
            }
            savedTick += 1
        }
    }

    // ---- MHT files: one file per thread, listed in the same box, opened in the app or handed to another app ----
    val mhtLibrary = remember(savedFileSystem, mediaServices?.httpClient) {
        savedFileSystem?.let { FutaberMhtLibrary(it, mediaServices?.httpClient) }
    }
    var mhtEntries by remember { mutableStateOf<List<FutaberMhtEntry>>(emptyList()) }
    var mhtBusy by remember { mutableStateOf<String?>(null) }
    var mhtTick by remember { mutableStateOf(0) }
    LaunchedEffect(managePanelOpen, mhtLibrary, mhtTick) {
        val library = mhtLibrary
        if (!managePanelOpen || library == null) return@LaunchedEffect
        mhtEntries = try {
            library.list()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(FUTABER_TAG, "Failed to list the MHT files", error)
            emptyList()
        }
    }
    val shareFile = rememberCompatShareLauncher()
    val openMht: (FutaberMhtEntry) -> Unit = { entry ->
        val library = mhtLibrary
        if (library != null && mhtBusy == null) scope.launch {
            mhtBusy = "開いています…"
            library.open(entry)
                .onSuccess { opened ->
                    val target = futaberBoardForMht(opened.thread, boards)
                    offlineView = FutaberOfflineView(
                        target,
                        FutaberThreadRef(target.id, opened.thread.threadId, opened.thread.title, "", opened.page.posts.size),
                        opened.page
                    )
                    managePanelOpen = false
                }
                .onFailure { error ->
                    Logger.e(FUTABER_TAG, "Failed to open an MHT file", error)
                    savedNotice = futaberFriendlyLoadError(error, "MHTファイル", "MHTファイルを開けませんでした")
                }
            mhtBusy = null
        }
    }
    val importMht: (com.valoser.futacha.shared.util.ImageData) -> Unit = { picked ->
        val library = mhtLibrary
        if (library != null && mhtBusy == null) scope.launch {
            mhtBusy = "取り込んでいます…"
            library.import(picked.bytes, picked.fileName, Clock.System.now().toEpochMilliseconds())
                .onSuccess { savedNotice = "MHTファイルを追加しました"; mhtTick += 1 }
                .onFailure { error -> savedNotice = futaberFriendlyLoadError(error, "MHTファイル", "MHTファイルを取り込めませんでした") }
            mhtBusy = null
        }
    }
    val pickMht = rememberFutaberMhtPickerLauncher(onSelected = importMht, onError = { savedNotice = it })
    val mhtBox = FutaberMhtBox(
        available = mhtLibrary != null,
        entries = mhtEntries,
        busyMessage = mhtBusy,
        onOpen = openMht,
        onShare = { entry -> mhtLibrary?.let { shareFile(entry.title, "multipart/related", it.absolutePath(entry)) } },
        onDelete = { entry ->
            val library = mhtLibrary
            if (library != null) scope.launch {
                library.delete(entry).onFailure { savedNotice = futaberFriendlyLoadError(it, "MHTファイル", "削除できませんでした") }
                mhtTick += 1
            }
        },
        onImport = pickMht
    )

    // What Back and the left-edge swipe of the thread screens do depends on what is on top of it.
    val dialogOpen = savedNotice != null || watcherOpen
    val drawerGestureEnabled = futaberDrawerGestureAllowed(
        drawerOpen = drawerOpen,
        managePanelOpen = managePanelOpen,
        settingsOpen = settingsOpen,
        writing = postTarget != null,
        tabSheetOpen = tabSheet != null,
        dialogOpen = dialogOpen
    )
    // Back on a thread closes the board list first when it is open (it is on top of the thread).
    val threadBack: () -> Unit = {
        if (futaberThreadBack(drawerOpen) == FutaberThreadBack.CloseDrawer) drawerOpen = false else openThread = null
    }
    // The catalog is kept (list, scroll, search) while a thread is open over it.
    val catalogSearch = remember(board?.id) { FutaberCatalogSearchState() }
    val catalogView = remember(board?.id, sortMode) { FutaberCatalogViewState() }
    // Writing is dropped when its board is gone (state is not written while composing, so it is done here).
    LaunchedEffect(postTarget, boards) {
        val target = postTarget
        if (target != null && boards.none { it.id == target.boardId }) postTarget = null
    }
    // The write screen is restored after the screen was rebuilt, the attached file is not (its bytes are not kept): say so.
    var attachmentWasKept by rememberSaveable { mutableStateOf(false) }
    var attachmentChecked by remember { mutableStateOf(false) }
    val hasAttachment = postAttachment.image != null
    LaunchedEffect(hasAttachment) {
        if (!attachmentChecked) {
            attachmentChecked = true
            if (futaberAttachmentLostNoticeDue(attachmentWasKept, postTarget != null, hasAttachment)) notify(FUTABER_ATTACHMENT_LOST_NOTICE)
        }
        attachmentWasKept = hasAttachment
    }
    // Deleting a history row cleans up as ふたちゃ does: the other modes' copy of the row (or the bridge would bring it back
    // at the next start), the person's own post numbers of the thread, and the auto-saved copy.
    val deleteHistory: (ThreadHistoryEntry) -> Unit = { entry ->
        launchStore("delete a history row") {
            dismissHistoryEntry(
                stateStore = stateStore,
                autoSavedThreadRepository = autoSavedThreadRepository,
                compatibilityStore = mediaServices?.store,
                entry = entry,
                onAutoSavedThreadDeleteFailure = {
                    Logger.e(FUTABER_TAG, "Failed to delete the auto-saved copy", it)
                    notify(FUTABER_AUTO_SAVE_DELETE_FAILED_NOTICE)
                }
            )
        }
    }

    FutaberTheme(themeMode, statusFollowsTopBar = !(settingsOpen || postTarget != null)) {
        CompositionLocalProvider(LocalFutaberDisplaySettings provides displaySettings) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val drawerWidth = maxWidth * FUTABER_DRAWER_WIDTH_FRACTION
            val drawerProgress by animateFloatAsState(
                targetValue = if (drawerOpen) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "futaber-drawer"
            )
            val density = androidx.compose.ui.platform.LocalDensity.current
            val drawerWidthPx = with(density) { drawerWidth.toPx() }
            // The list sits at the start edge, so in a right-to-left layout the screen is pushed the other way.
            val drawerDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

            if (drawerProgress > 0f) {
                FutaberBoardDrawer(
                    boards = boards,
                    selectedBoardId = board?.id,
                    modifier = Modifier.width(drawerWidth).fillMaxHeight(),
                    onSelect = { selected ->
                        // A write screen left open would stay on top of another board's catalog.
                        postTarget?.let { saveDraftNow(it, postSubject, postComment) }
                        postTarget = null
                        quoteMode = false
                        tabSheet = null
                        selectedBoardId = selected.id
                        openThread = null
                        offlineView = null
                        drawerOpen = false
                        writePreference(FutaberPreferenceKeys.LAST_BOARD_ID, selected.id)
                    },
                    onAddBoard = { name, url -> editBoards { futaberAddBoard(it, name, url) } },
                    onDeleteBoard = { target -> editBoards { futaberDeleteBoard(it, target.id) } },
                    onMoveBoard = { target, up -> editBoards { futaberMoveBoard(it, target.id, up) } },
                    onRenameBoard = { target, name -> editBoards { futaberRenameBoard(it, target.id, name) } },
                    onBulkAdd = bulkAddBoards
                )
            }
            Box(
                Modifier.fillMaxSize().offset { IntOffset(drawerDirection * (drawerWidthPx * drawerProgress).roundToInt(), 0) }
            ) {
                when {
                    offlineView != null -> offlineView?.let { view ->
                        val viewRepository = if (view.board.isMockBoard()) mockRepository else repository
                        val offlineRepository = remember(view) {
                            FutaberOfflineRepository(viewRepository, view.ref.threadId, view.page)
                        }
                        FutaberThreadScreen(
                            board = view.board,
                            ref = view.ref,
                            repository = offlineRepository,
                            isTabbed = false,
                            onToggleTab = {},
                            onOpenManage = { managePanelOpen = !managePanelOpen },
                            onOpenPost = { quote ->
                                // A copy of a board that is not registered here has no board to write to.
                                if (!futaberMhtCanPost(view.board)) notify("この板は登録されていないため、書き込めません")
                                else openPost(FutaberPostTarget.Reply(view.board.id, view.ref.threadId, view.ref.title), quote)
                            },
                            refreshSignal = 0,
                            quoteMode = false,
                            onQuoteLine = {},
                            onOpenDrawer = { drawerOpen = true },
                            drawerGestureEnabled = drawerGestureEnabled,
                            ngWords = ngWords,
                            ngHeaders = ngHeaders,
                            ngRules = ngRules,
                            onChangeNgWords = { launchStore("save NG words") { stateStore.setNgWords(it) } },
                            onChangeNgHeaders = { launchStore("save NG headers") { stateStore.setNgHeaders(it) } },
                            // A saved copy is not a visit: nothing is written to the history or the reading position.
                            onPageLoaded = {},
                            onScrollSettled = { _, _, _, _ -> },
                            onBack = { if (drawerOpen) drawerOpen = false else offlineView = null },
                            mediaServices = mediaServices,
                            offlineNotice = "保存したコピーを表示中です。最新のスレッドとは異なる場合があります",
                            tabStrip = tabStrip
                        )
                    }
                    board == null -> FutaberNoBoards(
                        onAddBoard = { drawerOpen = true },
                        onOpenSettings = { settingsOpen = true }
                    )
                    thread != null -> FutaberThreadScreen(
                        board = board,
                        ref = thread,
                        repository = if (board.isMockBoard()) mockRepository else threadRepository,
                        deleteKey = deleteKey,
                        // The auto-saved copy ふたちゃ falls back to when the thread cannot be read.
                        loadOfflineCopy = remember(autoSavedThreadRepository, savedFileSystem, board, thread.threadId) {
                            futaberAutoSavedCopyLoader(autoSavedThreadRepository, savedFileSystem, board, thread.threadId)
                        },
                        // Behind the writing screen (not quote mode, which shows the thread) and the settings the thread waits.
                        paused = (postTarget != null && quoteTarget == null) || settingsOpen,
                        selfPostIds = remember(selfPostMap, board.id, thread.threadId) {
                            com.valoser.futacha.shared.ui.board.buildSelfPostIdentifierSet(
                                com.valoser.futacha.shared.ui.board.buildPersistedSelfPostIdentifiers(
                                    selfPostMap,
                                    com.valoser.futacha.shared.ui.board.buildThreadScopedSelfPostKey(board.id, thread.threadId),
                                    thread.threadId
                                )
                            )
                        },
                        isTabbed = openKey in tabKeys,
                        onToggleTab = { openKey?.let(toggleTab) },
                        isFavorite = openKey?.let { futaberIsFavorite(favorites, it) } == true,
                        onToggleFavorite = {
                            val favorite = futaberFavoriteFor(board, thread, Clock.System.now().toEpochMilliseconds())
                            editFavorites { futaberToggleFavorite(it, favorite) }
                        },
                        onOpenManage = { managePanelOpen = !managePanelOpen },
                        onOpenPost = { quote ->
                            openPost(FutaberPostTarget.Reply(board.id, thread.threadId, thread.title), quote)
                        },
                        refreshSignal = threadRefreshSignal,
                        quoteMode = quoteTarget != null,
                        onQuoteLine = { line -> postComment = futaberAppendQuote(postComment, ">$line") },
                        onOpenDrawer = { drawerOpen = true },
                        drawerGestureEnabled = drawerGestureEnabled,
                        ngWords = ngWords,
                        ngHeaders = ngHeaders,
                        ngRules = ngRules,
                        onChangeNgWords = { launchStore("save NG words") { stateStore.setNgWords(it) } },
                        onChangeNgHeaders = { launchStore("save NG headers") { stateStore.setNgHeaders(it) } },
                        onPageLoaded = { posts ->
                            launchStore("update history after load") {
                                stateStore.updateHistory { futaberHistoryAfterLoad(it, board.id, thread.threadId, posts) }
                            }
                        },
                        onLivePageConfirmed = {
                            launchStore("mark the thread alive") {
                                val now = Clock.System.now().toEpochMilliseconds()
                                stateStore.updateHistory { futaberHistoryMarkAlive(it, board.id, thread.threadId, now) }
                            }
                        },
                        onScrollSettled = { index, offset, postId, total ->
                            // How far this person has read is written only when the reading position settles (or the
                            // screen is left): this is the count the next visit marks new posts from.
                            // (A value that is already stored is not written again.)
                            editPreference(FutaberPreferenceKeys.SEEN) { stored ->
                                futaberSeenCountsAfter(stored, board.id, thread.threadId, total)
                            }
                            launchStore("save reading position") {
                                stateStore.updateHistoryScrollPositionImmediately(
                                    AppStateHistoryScrollUpdateRequest(
                                        threadId = thread.threadId,
                                        index = index,
                                        offset = offset,
                                        postId = postId,
                                        boardId = board.id,
                                        title = thread.title,
                                        titleImageUrl = thread.thumbnailUrl,
                                        boardName = board.name,
                                        boardUrl = futaberHistoryThreadUrl(board, thread.threadId),
                                        replyCount = total
                                    )
                                )
                            }
                        },
                        onBack = threadBack,
                        mediaServices = mediaServices,
                        tabStrip = tabStrip
                    )
                    else -> FutaberCatalogScreen(
                        board = board,
                        repository = if (board.isMockBoard()) mockRepository else repository,
                        displayStyle = displayStyle,
                        sortMode = sortMode,
                        onDisplayStyleChange = { writePreference(FutaberPreferenceKeys.CATALOG_DISPLAY_STYLE, it.persistedValue) },
                        onSortChange = { writePreference(FutaberPreferenceKeys.CATALOG_SORT, it.name) },
                        onOpenDrawer = { drawerOpen = true },
                        onOpenSettings = { settingsOpen = true },
                        onOpenManage = { managePanelOpen = !managePanelOpen },
                        onCreateThread = { openPost(FutaberPostTarget.CreateThread(board.id), "") },
                        refreshSignal = catalogRefreshSignal,
                        catalogNgWords = catalogNgWords,
                        catalogNgIndex = catalogNgIndex,
                        onOpenThread = { item -> openRef(board, item.toFutaberThreadRef(board.id)) },
                        archiveHttpClient = mediaServices?.httpClient,
                        longPressActions = { item ->
                            val inHistory = history.firstOrNull { it.boardId == board.id && it.threadId == item.id }
                            listOf(
                                FutaberPostAction("copy-url", "URLをコピー") {
                                    clipboardManager.setText(AnnotatedString(futaberHistoryThreadUrl(board, item.id)))
                                },
                                // Hides this thread from the catalog; the rule is the one ふたちゃ and としあき(仮) use, and
                                // can be taken back in the settings (NG > NGスレッド).
                                FutaberPostAction("ng-thread", "スレッドをNG", enabled = mediaServices != null && futaberNgScopeKey(board) != null) {
                                    registerNgThread(item)
                                },
                                FutaberPostAction("remove-history", "履歴から削除", enabled = inHistory != null) {
                                    inHistory?.let(deleteHistory)
                                }
                            )
                        },
                        phashRules = catalogPhashRules,
                        phashThreshold = catalogPhashThreshold,
                        phashStore = mediaServices?.store,
                        view = catalogView,
                        search = catalogSearch,
                        tabStrip = tabStrip
                    )
                }
                if (managePanelOpen) {
                    // The panel sits between the top bar and the bottom bar, which stay visible but dimmed;
                    // tapping either closes it, and so does the panel button again.
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val topInset = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
                    val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
                    val topEdge = topInset + FUTABER_TOP_BAR_HEIGHT_DP.dp
                    val bottomEdge = bottomInset + (FUTABER_BOTTOM_BAR_HEIGHT_DP + FUTABER_FLOATING_BAR_EXTRA_DP).dp
                    Box(Modifier.fillMaxSize().padding(top = topEdge, bottom = bottomEdge)) {
                        FutaberManagePanel(
                            onRefreshHistory = onRefreshHistory,
                            history = history,
                            boards = boards,
                            tabs = tabViews,
                            savedBox = savedBox,
                            savedBoxAvailable = savedRepository != null,
                            onOpenSaved = openSaved,
                            onDeleteSaved = deleteSaved,
                            mht = mhtBox,
                            notifications = watchResults,
                            notificationsAvailable = watcher != null,
                            notificationsStatus = futaberNotificationsStatus(preferences),
                            onOpenNotification = { result ->
                                futaberRefForWatchResult(result, boards)?.let { ref ->
                                    boards.firstOrNull { it.id == ref.boardId }?.let { target -> openRef(target, ref) }
                                }
                            },
                            onRemoveNotification = { result ->
                                launchStore("remove a patrol result") { watcher?.delete(result.history.canonicalUrl) }
                            },
                            onClearNotifications = { launchStore("clear the patrol results") { watcher?.deleteAll() } },
                            onOpenWatcherManager = { watcherOpen = true },
                            favorites = favoriteViews,
                            onOpenFavorite = { view ->
                                futaberRefForFavorite(view, boards)?.let { ref ->
                                    boards.firstOrNull { it.id == ref.boardId }?.let { target -> openRef(target, ref) }
                                }
                            },
                            onRemoveFavorite = { view -> editFavorites { futaberRemoveFavorite(it, view.favorite.key) } },
                            onClearFavorites = { editFavorites { emptyList() } },
                            onOpenHistory = { entry ->
                                futaberRefForHistory(entry, boards)?.let { ref ->
                                    boards.firstOrNull { it.id == ref.boardId }?.let { target -> openRef(target, ref) }
                                }
                            },
                            onDeleteHistory = deleteHistory,
                            onOpenTab = openTab,
                            onRemoveTab = { view -> saveTabs { futaberRemoveTab(it, view.key) } },
                            onClearTabs = { saveTabs { emptyList() } },
                            onClose = { managePanelOpen = false }
                        )

                    }
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(topEdge)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f))
                            .clickable(onClickLabel = "パネルを閉じる") { managePanelOpen = false }
                            .testTag("futaber-manage-close-top")
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomEdge)
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.2f))
                            .clickable(onClickLabel = "パネルを閉じる") { managePanelOpen = false }
                            .testTag("futaber-manage-close")
                    )
                }
                tabSheet?.let { tab ->
                    val fallen = tabViews.filter { it.fallen }.mapTo(HashSet()) { it.key }
                    val close = { tabSheet = null }
                    FutaberPostActionSheet(
                        title = tab.title,
                        actions = listOf(
                            FutaberPostAction("tab-remove", "タブを削除") { close(); saveTabs { futaberRemoveTab(it, tab.key) } },
                            FutaberPostAction(
                                "tab-remove-right", "右側のタブを削除", enabled = tabKeys.lastOrNull() != tab.key
                            ) { close(); saveTabs { futaberRemoveTabsToTheRight(it, tab.key) } },
                            FutaberPostAction(
                                "tab-remove-others", "他のタブを削除", enabled = tabKeys.size > 1
                            ) { close(); saveTabs { futaberKeepOnlyTab(it, tab.key) } },
                            FutaberPostAction(
                                "tab-remove-fallen", "スレ落ちしたタブを削除", enabled = fallen.isNotEmpty()
                            ) { close(); saveTabs { futaberRemoveFallenTabs(it, fallen) } },
                            FutaberPostAction("tab-remove-all", "タブをすべて削除") { close(); saveTabs { emptyList() } },
                        ) + listOfNotNull(
                            if (displaySettings.extTabs) {
                                val restorable = futaberRestorableTabs(closedTabs, tabKeys, history, boards)
                                FutaberPostAction(
                                    "tab-restore", "閉じたタブを元に戻す（${restorable.size}）", enabled = restorable.isNotEmpty()
                                ) {
                                    close()
                                    editPreference(FutaberPreferenceKeys.TABS) { stored ->
                                        val result = futaberRestoreTabs(decodeFutaberTabs(stored), restorable)
                                        // Only the tabs that were written leave the memory; the ones a full list had no room for stay in it.
                                        closedTabs = closedTabs.filter { it !in result.restored }
                                        if (result.restored.size < restorable.size) notify(FUTABER_TAB_LIMIT_NOTICE)
                                        if (result.restored.isEmpty()) null else encodeFutaberTabs(result.tabs)
                                    }
                                }
                            } else null
                        ) + listOf(
                            FutaberPostAction("tab-cancel", "キャンセル") { close() }
                        ),
                        onDismiss = close,
                        avoidNavigationBar = true
                    )
                }
                if (quoteTarget != null) {
                    FutaberQuoteStrip(
                        comment = postComment,
                        onBackToWriting = { quoteMode = false },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    PlatformBackHandler(enabled = true) { quoteMode = false }
                } else postTarget?.let { target ->
                    val targetBoard = boards.firstOrNull { it.id == target.boardId }
                    // A board that is gone drops the write screen (the effect above); nothing is drawn meanwhile.
                    if (targetBoard != null) {
                        FutaberPostScreen(
                            target = target,
                            boardAddress = futaberBoardAddress(targetBoard.url),
                            subject = postSubject,
                            comment = postComment,
                            onSubjectChange = { postSubject = it },
                            onCommentChange = { postComment = it },
                            // Quoting needs the thread the reply is for to be on screen behind the writing.
                            onEnterQuoteMode = if (target is FutaberPostTarget.Reply && thread?.threadId == target.threadId) {
                                { quoteMode = true }
                            } else null,
                            attachment = postAttachment,
                            capabilities = postCapabilities
                                ?: com.valoser.futacha.shared.network.defaultBoardPostingCapabilities(targetBoard.url),
                            pickerPreference = pickerPreference,
                            preferredFileManagerPackage = preferredFileManager?.packageName,
                            settings = postSettings,
                            deleteKey = deleteKey,
                            onSettingsChange = { name, email, confirm ->
                                writePreference(FutaberPreferenceKeys.POST_NAME, name)
                                writePreference(FutaberPreferenceKeys.POST_EMAIL, email)
                                writePreference(FutaberPreferenceKeys.POST_CONFIRM, if (confirm) "ON" else "OFF")
                            },
                            onDeleteKeyChange = { key -> launchStore("save the delete key") { stateStore.setLastUsedDeleteKey(key) } },
                            onSend = { input -> sendPost(target, input) },
                            onSent = { posted ->
                                // What was typed names a new thread until the thread itself is read.
                                val sentSubject = postSubject
                                postSubject = ""
                                postComment = ""
                                postAttachment.clear()
                                saveDraftNow(target, "", "")
                                postTarget = null
                                when (target) {
                                    is FutaberPostTarget.Reply -> threadRefreshSignal += 1
                                    is FutaberPostTarget.CreateThread -> {
                                        catalogRefreshSignal += 1
                                        // The new thread opens at once when the server named it.
                                        if (!posted.isNullOrBlank()) {
                                            openRef(targetBoard, FutaberThreadRef(targetBoard.id, posted, futaberNewThreadTitle(sentSubject), "", 0))
                                        }
                                    }
                                }
                            },
                            onClose = {
                                saveDraftNow(target, postSubject, postComment)
                                postTarget = null
                            }
                        )
                    }
                }
                if (drawerOpen) {
                    // Tapping the pushed-aside screen closes the board list.
                    Box(
                        Modifier.fillMaxSize()
                            .clickable(onClickLabel = "板一覧を閉じる") { drawerOpen = false }
                            .testTag("futaber-drawer-scrim")
                    )
                }
            }
        }
        // Registered while the list is open, so it is the newest handler and Back closes the list before the screen under it
        // (a handler composed once at the start would be older than the thread screen's and lose to it).
        if (drawerOpen) PlatformBackHandler(enabled = true) { drawerOpen = false }
        // First launch without a board choice: show the board list straight away.
        LaunchedEffect(boards.isEmpty()) {
            if (boards.isNotEmpty() && selectedBoardId == null && preferences[FutaberPreferenceKeys.LAST_BOARD_ID] == null) {
                drawerOpen = true
            }
        }
        val manageStore = mediaServices?.store
        if (watcherOpen && manageStore != null) {
            // The patrol scans the boards of the shared store; make sure this mode's boards are in it.
            LaunchedEffect(boards) { launchStore("share the boards with the patrol") { manageStore.importModernBoards(boards) } }
            CompositionLocalProvider(LocalCompatibilityPalette provides futaberMediaPalette(LocalFutaberColors.current)) {
                CompatWatcherManager(
                    store = manageStore,
                    repository = repository,
                    onDismiss = { watcherOpen = false },
                    onResultsChanged = {},
                    resultsLocation = "管理画面の「通知」"
                )
            }
        }
        savedNotice?.let { text ->
            val colors = LocalFutaberColors.current
            FutachaAppLockAwareWindow {
                AlertDialog(
                    onDismissRequest = { savedNotice = null },
                    containerColor = colors.bar,
                    titleContentColor = colors.body,
                    textContentColor = colors.body,
                    text = { Text(text) },
                    confirmButton = { TextButton(onClick = { savedNotice = null }) { Text("閉じる", color = colors.link) } }
                )
            }
        }
        if (settingsOpen) {
            FutaberSettingsScreen(
                themeMode = themeMode,
                onThemeModeChange = { writePreference(FutaberPreferenceKeys.THEME, it.persistedValue) },
                settings = displaySettings,
                onSettingChange = writePreference,
                postSettings = postSettings,
                deleteKey = deleteKey,
                onPostSettingsChange = { name, email, confirm ->
                    writePreference(FutaberPreferenceKeys.POST_NAME, name)
                    writePreference(FutaberPreferenceKeys.POST_EMAIL, email)
                    writePreference(FutaberPreferenceKeys.POST_CONFIRM, if (confirm) "ON" else "OFF")
                },
                onDeleteKeyChange = { key -> launchStore("save the delete key") { stateStore.setLastUsedDeleteKey(key) } },
                ngWords = ngWords,
                ngHeaders = ngHeaders,
                catalogNgWords = catalogNgWords,
                catalogThreadRules = catalogThreadRules,
                catalogWordRules = catalogWordRules,
                catalogImageRules = catalogImageRules,
                imageNgRules = imageNgRules,
                onDeleteNgRule = deleteNgRule,
                // Each edit is applied to the list stored at that moment, so two quick "×" both count.
                onEditCatalogNgWords = { edit -> launchStore("save catalog NG words") { stateStore.updateCatalogNgWords(edit) } },
                onEditNgWords = { edit -> launchStore("save NG words") { stateStore.updateNgWords(edit) } },
                onEditNgHeaders = { edit -> launchStore("save NG headers") { stateStore.updateNgHeaders(edit) } },
                mediaServices = mediaServices,
                appVersion = appVersion,
                stateStore = stateStore,
                onOpenNotifications = { watcherOpen = true },
                onWatchAlertSettingChangeRequested = onWatchAlertSettingChangeRequested,
                onNotice = notify,
                onClose = { settingsOpen = false }
            )
        }
        // The notice goes over everything, the settings screen included.
        appNotice?.let { text ->
            LaunchedEffect(text) {
                delay(FUTABER_NOTICE_MILLIS)
                if (appNotice == text) appNotice = null
            }
            val noticeColors = LocalFutaberColors.current
            // Above the bottom bar (and the tab strip) of the catalog and the thread; the other screens have none.
            val aboveBars = if (settingsOpen || postTarget != null) 0 else
                FUTABER_BOTTOM_BAR_HEIGHT_DP + FUTABER_FLOATING_BAR_EXTRA_DP + if (tabViews.isNotEmpty()) FUTABER_TAB_STRIP_HEIGHT_DP else 0
            Box(Modifier.fillMaxSize()) {
                Text(
                    text = text,
                    color = noticeColors.onAction,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = aboveBars.dp)
                        .fillMaxWidth().background(noticeColors.accent)
                        .clickable(onClickLabel = "閉じる") { appNotice = null }
                        .padding(8.dp).testTag("futaber-app-notice")
                )
            }
        }
        }
    }
}

/**
 * The screen with no board: a top bar with the settings (which also hold the mode switch) and the way to add one. The
 * board list opens in its edit mode, which has the "板を追加" row.
 */
@Composable
private fun FutaberNoBoards(onAddBoard: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = LocalFutaberColors.current
    Column(Modifier.fillMaxSize().background(colors.background).testTag("futaber-no-boards")) {
        Row(
            Modifier.fillMaxWidth().background(colors.topBar).statusBarsPadding().height(FUTABER_TOP_BAR_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ふたばー", color = colors.topBarTitle, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(start = 16.dp)
            )
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("futaber-open-settings")) {
                FutaberIcon(Icons.Outlined.Settings, contentDescription = "設定", tint = colors.onTopBar)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("板が登録されていません", color = colors.body, fontSize = 14.sp, textAlign = TextAlign.Center)
            TextButton(onClick = onAddBoard, modifier = Modifier.testTag("futaber-no-boards-add")) {
                Text("板を追加", color = colors.link)
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.testTag("futaber-no-boards-settings")) {
                Text("設定を開く", color = colors.link)
            }
        }
    }
}
