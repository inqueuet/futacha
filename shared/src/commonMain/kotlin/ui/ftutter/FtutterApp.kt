package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.appliesToThreadImage
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.GlobalSettingsModeSection
import com.valoser.futacha.shared.ui.board.statusCodeOrNull
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.ui.futaber.FutaberAiInbox
import com.valoser.futacha.shared.ui.futaber.FutaberMediaServices
import com.valoser.futacha.shared.ui.futaber.futaberAiUnsupportedNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.valoser.futacha.shared.ui.isMockBoard

/** 画面の行き先。段階ごとに増やす。 */
internal sealed interface FtutterScreen {
    data object Boards : FtutterScreen
    data class Catalog(val boardId: String) : FtutterScreen
    data class Thread(val boardId: String, val ref: FtutterThreadRef, val back: FtutterScreen) : FtutterScreen
    data class HiddenAndNg(val back: FtutterScreen) : FtutterScreen
    data class Settings(val back: FtutterScreen) : FtutterScreen
}

/**
 * ふたったー風モードの画面の根。
 *
 * 第3段階まで：板一覧・カタログ・スレッド。タブ・書き込み等は後の段階で足す
 * （docs/ftutter-mode-implementation-plan-2026-10-13.md）。
 */
@Composable
internal fun FtutterApp(
    boards: List<BoardSummary>,
    history: List<ThreadHistoryEntry>,
    repository: BoardRepository,
    stateStore: AppStateStore,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    mediaServices: FutaberMediaServices?,
    autoSavedThreadRepository: SavedThreadRepository?,
    appVersion: String,
    initialThreadDeepLink: String?,
    onThreadDeepLinkConsumed: (String) -> Unit,
    initialBoardDeepLink: String?,
    onBoardDeepLinkConsumed: (String) -> Unit,
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)?,
    externalNotice: String?,
    onExternalNoticeShown: () -> Unit,
    onArchiveReportEnqueued: ((Int) -> Unit)?,
    onArchiveReportEnabledChanged: (Boolean) -> Unit,
    aiInbox: FutaberAiInbox?,
    mediaHost: @Composable (@Composable () -> Unit) -> Unit,
    onRefreshHistory: suspend () -> Unit,
    savePreference: (String, String) -> Unit,
    /** 保存されている値に変更を当てる（監査M2）。変換が null なら書かない。 */
    editPreference: (String, (String?) -> String?) -> Unit,
    /** Writes an import's settings in turn with the other writes (re-audit L28); null writes them directly as before. */
    importPreferences: (suspend (Map<String, (String?) -> String?>) -> Unit)? = null
) {
    val design = remember(preferences) { parseFtutterDesign(preferences) }
    var screen by remember { mutableStateOf<FtutterScreen>(FtutterScreen.Boards) }
    // 同じ文の通知が続いても、出し直して時間を数え直す（監査L7：同じ文字列では前の4秒のまま消えていた）。
    val noticeHolder = remember { FtutterNoticeHolder() }
    var notice by noticeHolder
    // 保存の入口：1つの設定の上限（2万字）を超える値は送らず、保存できなかったことを知らせる（監査A9。以前はストアの
    // 例外がログに出るだけで、「登録しました」と出たまま保存されていなかった）。このモードの保存はすべてここを通る。
    val storeSave = savePreference
    @Suppress("NAME_SHADOWING")
    val savePreference: (String, String) -> Unit = { key, value ->
        if (com.valoser.futacha.shared.compat.isValidCompatPreference(key, value)) storeSave(key, value) else notice = FTUTTER_SAVE_REFUSED_NOTICE
    }
    val storeEdit = editPreference
    @Suppress("NAME_SHADOWING")
    val editPreference: (String, (String?) -> String?) -> Unit = { key, transform ->
        storeEdit(key) { stored ->
            transform(stored)?.let { value ->
                if (com.valoser.futacha.shared.compat.isValidCompatPreference(key, value)) value else {
                    notice = FTUTTER_SAVE_REFUSED_NOTICE
                    null
                }
            }
        }
    }
    val tabState = remember { FtutterTabState(parseFtutterTabs(preferences), parseFtutterProtected(preferences), savePreference) }
    // 保存値が後から届いた時（起動直後の読み込み、バックアップの取り込み）は、自分が書いた値でなければ取り込む。
    LaunchedEffect(preferences[FtutterKeys.TABS], preferences[FtutterKeys.PROTECTED]) { tabState.sync(preferences) }

    // AI commands: this stage runs none of them, so every one is answered (left alone they would pile up and replay).
    val currentInbox by rememberUpdatedState(aiInbox)
    DisposableEffect(aiInbox) {
        currentInbox?.runner = { command -> notice = futaberAiUnsupportedNotice(command) }
        onDispose { currentInbox?.runner = null }
    }
    // Links are consumed so a later mode switch does not replay them; opening them comes with the thread screen.
    LaunchedEffect(initialThreadDeepLink) { initialThreadDeepLink?.let(onThreadDeepLinkConsumed) }
    LaunchedEffect(initialBoardDeepLink) { initialBoardDeepLink?.let(onBoardDeepLinkConsumed) }
    LaunchedEffect(externalNotice) {
        val text = externalNotice ?: return@LaunchedEffect
        notice = text
        onExternalNoticeShown()
    }
    LaunchedEffect(noticeHolder.serial) {
        if (notice != null) {
            delay(4_000)
            notice = null
        }
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val openUrl = rememberFtutterUrlLauncher(preferences)
    val catalogHolder = remember { FtutterCatalogHolder() }
    val threadMemory = remember { FtutterThreadMemory() }
    // スレ画面・タブ帯より長く持つもの（再監査M3）：送信・過去ログ取得の受け皿と、タブ帯の位置。
    val threadActions = remember(scope) { FtutterThreadActions(scope) }
    val tabStripState = androidx.compose.foundation.lazy.rememberLazyListState()
    val mockRepository = remember { com.valoser.futacha.shared.repo.mock.FakeBoardRepository() }
    // スレッドは、ふたちゃ・ふたばー風と同じ共有の層を通す（キャッシュサーバーの設定、長いスレッドの欠けの補完）。
    val threadRepository = remember(repository, mediaServices?.store, mediaServices?.httpClient) {
        mediaServices?.let { com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository(repository, it.store, it.httpClient) } ?: repository
    }
    val httpClient = mediaServices?.httpClient
    // 「板一覧から一括追加」：公式の板一覧（ふたちゃ・ふたばー風と同じ取得）から、未登録の板を足す。
    val bulkAddBoards: (suspend () -> Result<Int>)? = httpClient?.let { client ->
        {
            try {
                val discovered = com.valoser.futacha.shared.ui.compat.fetchDefaultCompatBoardsFromMenu(
                    client, com.valoser.futacha.shared.compat.modernBoardsToCompatibility(boards)
                ).getOrThrow()
                var added = 0
                stateStore.updateBoards { current ->
                    val next = com.valoser.futacha.shared.ui.futaber.futaberAddDiscoveredBoards(current, discovered.map { it.name to it.originalUrl })
                    added = next.size - current.size
                    next
                }
                Result.success(added)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }
    val editBoards: ((List<BoardSummary>) -> List<BoardSummary>) -> Unit = { transform ->
        scope.launch { stateStore.updateBoards(transform) }
    }
    fun openCatalog(board: BoardSummary) {
        savePreference(FtutterKeys.LAST_BOARD, board.id)
        screen = FtutterScreen.Catalog(board.id)
    }
    // 統計（端末の中だけ）。
    fun countStat(key: String) {
        editPreference(key, ::ftutterIncrementedCount)
    }
    val selfPostMap by stateStore.selfPostIdentifiersByThread.collectAsState(emptyMap<String, List<String>>())
    // スレッドを開くと履歴の先頭へ（原作：開く・個別に更新するたびに先頭へ移る）。閲覧時刻は開いた時だけ書く。
    fun openThread(board: BoardSummary, ref: FtutterThreadRef, back: FtutterScreen, refreshOnOpen: Boolean = true) {
        threadMemory.open(ftutterCanonicalThreadUrl(ftutterThreadUrl(board, ref.threadId)), refreshOnOpen)
        screen = FtutterScreen.Thread(board.id, ref, back)
        countStat(FtutterKeys.STATS_OPENED)
        val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, ref.threadId))
        tabState.open(url)
        val existing = com.valoser.futacha.shared.ui.futaber.findFutaberHistoryEntry(history, board, ref.threadId)
        scope.launch {
            try {
                stateStore.prependOrReplaceHistoryEntry(
                    com.valoser.futacha.shared.ui.futaber.buildFutaberOpenHistoryEntry(
                        existing = existing,
                        board = board,
                        ref = com.valoser.futacha.shared.ui.futaber.FutaberThreadRef(
                            boardId = board.id, threadId = ref.threadId, title = ref.title,
                            thumbnailUrl = ref.thumbnailUrl, replyCount = ref.catalogReplyCount
                        ),
                        nowEpochMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    )
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to record history", error)
            }
        }
    }
    // アプリ内で開くスレッドURL（本文のリンク、URLから開く）。登録していない板は開けない。
    fun openThreadUrl(url: String, back: FtutterScreen) {
        val threadId = ftutterThreadIdFromUrl(url)
        val target = ftutterBoardForUrl(boards, url)
        when {
            target == null -> notice = "登録していない板です"
            threadId == null -> openCatalog(target)
            else -> openThread(target, FtutterThreadRef(target.id, threadId, title = ""), back)
        }
    }

    // ---- 拡張機能（既定OFF） ----
    // 他モードと共有のNG（画像NGのルールを含む）。下の`ngRules`（このモードのNG）と名前が重なるので先に取っておく。
    val compatNgRules = ngRules
    val extensions = remember(preferences) { parseFtutterExtensions(preferences) }
    val commonNgOn = FtutterExtension.COMMON_NG in extensions
    val sharedNgWords by stateStore.ngWords.collectAsState(emptyList())
    val sharedNgHeaders by stateStore.ngHeaders.collectAsState(emptyList())
    val sharedCatalogNgWords by stateStore.catalogNgWords.collectAsState(emptyList())

    // ---- NG・マーク・削除キー・書き込み ----
    val ngRules = remember(preferences[FtutterKeys.NG_LIST]) { parseFtutterNgList(preferences[FtutterKeys.NG_LIST]) }
    // 既定値を書かない形で保存し、それでも上限に入らなければ断って理由を出す（監査A9）。変更は保存されている一覧へ当てる
    // （監査M2。画面の一覧は保存より遅れて届くので、続けて消す・足すと1回目が戻っていた）。保存できた時だけ[savedNotice]を出す。
    val saveNgRules: FtutterNgEdit = { transform, savedNotice ->
        editPreference(FtutterKeys.NG_LIST) { stored ->
            val before = parseFtutterNgList(stored)
            val after = transform(before)
            val problem = ftutterNgCapacityError(after)
            when {
                after == before -> null
                problem != null -> { notice = problem; null }
                else -> { savedNotice?.let { notice = it }; encodeFtutterNgListForStore(after) }
            }
        }
    }
    val manualMarks = remember(preferences[com.valoser.futacha.shared.compat.MANUAL_POST_MARKS_KEY]) {
        com.valoser.futacha.shared.compat.decodeManualPostMarks(preferences[com.valoser.futacha.shared.compat.MANUAL_POST_MARKS_KEY])
    }
    // 削除キー：初回に英数字8文字を作る（原作どおり。他モードの削除キーとは別に持つ）。
    val deleteKey = preferences[FtutterKeys.DELETE_KEY]?.takeIf { it.isNotBlank() }
    LaunchedEffect(deleteKey == null) { if (deleteKey == null) savePreference(FtutterKeys.DELETE_KEY, ftutterGenerateDeleteKey()) }
    var postRequest by remember { mutableStateOf<Pair<FtutterPostTarget, String?>?>(null) }
    var threadRefreshSignal by remember { mutableStateOf(0) }
    val pickerPreference by stateStore.attachmentPickerPreference.collectAsState(com.valoser.futacha.shared.util.AttachmentPickerPreference.MEDIA)
    val preferredFileManager by stateStore.getPreferredFileManager().collectAsState(null)
    fun repositoryFor(board: BoardSummary) = if (board.isMockBoard()) mockRepository else repository
    // 送信は、ふたちゃ・ふたばー風と同じ共有の処理（公式の送信前処理・フォームの取得・自動再送の抑止を含む）。
    suspend fun sendPost(target: FtutterPostTarget, input: FtutterPostInput): Result<String?> {
        val originalBoard = boards.firstOrNull { it.id == target.boardId } ?: return Result.failure(IllegalStateException("板が登録されていません"))
        val board = originalBoard.copy(url = ftutterNetworkUrl(originalBoard.url, preferences.ftutterBool(FtutterKeys.USE_HTTPS, false)))
        val noticeAgreed = try {
            com.valoser.futacha.shared.ui.board.checkPostingNoticeIfNeeded(stateStore)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            return Result.failure(IllegalStateException("投稿のご案内を確認できませんでした。もう一度お試しください", error))
        }
        if (!noticeAgreed) return Result.failure(IllegalStateException("投稿のご案内に同意すると書き込めます"))
        val repo = repositoryFor(board)
        val password = com.valoser.futacha.shared.ui.board.normalizeDeleteKeyForSubmit(input.deleteKey)
        val outcome = when (target) {
            is FtutterPostTarget.Reply -> com.valoser.futacha.shared.ui.board.performThreadReplyAction(
                com.valoser.futacha.shared.ui.board.buildThreadReplyActionConfig(
                    boardUrl = board.url,
                    threadId = target.threadId,
                    draft = com.valoser.futacha.shared.ui.board.ThreadReplyDraft(
                        name = input.draft.name, email = input.draft.mail, subject = input.draft.title,
                        comment = input.draft.comment, password = password, imageData = input.image
                    ),
                    normalizedPassword = password
                ),
                com.valoser.futacha.shared.ui.board.buildThreadReplyActionCallbacks(repo)
            )
            is FtutterPostTarget.Create -> com.valoser.futacha.shared.ui.board.performThreadAction {
                com.valoser.futacha.shared.ui.board.requireWritableThreadBoard(board.url)
                repo.createThread(
                    board = board.url, name = input.draft.name, email = input.draft.mail,
                    subject = input.draft.title, comment = input.draft.comment, password = password,
                    imageFile = input.image?.bytes, imageFileName = input.image?.fileName,
                    textOnly = input.image == null, handwriting = input.image?.isHandwriting == true
                )
            }
        }
        return when (outcome) {
            is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Success -> {
                val posted = outcome.value
                if (target is FtutterPostTarget.Reply && !posted.isNullOrBlank()) {
                    try {
                        // 自分の書き込みの記録（左の線・「自分のレス」・返信の数え方に使う）。ふたちゃと共有。
                        stateStore.addSelfPostIdentifier(target.threadId, posted, board.id)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to note a sent post", error)
                    }
                }
                Result.success(posted)
            }
            is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Failure -> Result.failure(
                IllegalStateException(
                    com.valoser.futacha.shared.ui.board.buildThreadActionFailureMessage(
                        if (target is FtutterPostTarget.Reply) "返信の送信に失敗しました" else "スレッド作成に失敗しました", outcome.error
                    ),
                    outcome.error
                )
            )
        }
    }
    // 原作のバックアップ（.ftt）の取り込み：選ぶ→復号→読み替え→確認→今の設定に足す。
    var backupPlan by remember { mutableStateOf<FtutterBackupPlan?>(null) }
    // 復号・読み替えは重い（数十MBになり得る）ので、メインスレッドの外で行い、その間は「読み込み中」を出す（取り消せる）。
    var backupLoading by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val pickBackup = com.valoser.futacha.shared.ui.futaber.mht.rememberFutaberMhtPickerLauncher(
        onSelected = { file ->
            backupLoading?.cancel()
            val currentBoards = boards
            val currentNg = ngRules
            val currentPreferences = preferences
            backupLoading = scope.launch {
                val job = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                try {
                    val plan = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        val ensureActive = { job?.ensureActive(); Unit }
                        ftutterDecryptBackup(file.bytes, ensureActive = ensureActive)?.let { xml ->
                            ftutterBackupPlan(
                                parseFtutterPreferencesXml(xml, ensureActive), currentBoards, currentNg, currentPreferences,
                                kotlin.time.Clock.System.now().toEpochMilliseconds(), ensureActive
                            )
                        }
                    }
                    if (plan == null) notice = "ふたったーのバックアップとして読めませんでした" else backupPlan = plan
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to read a backup", error)
                    notice = "ふたったーのバックアップとして読めませんでした"
                } finally {
                    if (backupLoading === job) backupLoading = null
                }
            }
        },
        onError = { notice = it }
    )
    fun applyBackup(plan: FtutterBackupPlan) {
        scope.launch {
            try {
                // 1つの設定の上限に入らない分（多すぎるNG・非表示・タブ・下書き）は、入る所まで取り込み、残りを知らせる（監査A9。
                // 以前はストアが全体を断り、何も取り込めなかった）。
                val fitted = ftutterFitPreferencesForStore(plan.preferences, tabState.urls.toSet())
                val importer = importPreferences
                if (importer != null) {
                    // The read counts are merged with what is stored when they are written, so a count written while the
                    // import was being confirmed stays (re-audit L28).
                    val importedUrls = plan.history.mapTo(HashSet()) { it.boardUrl }
                    importer(fitted.preferences.mapValues { (key, value) ->
                        if (key == FtutterKeys.READ_COUNTS) { stored: String? -> ftutterMergeImportedReadCounts(value, stored, importedUrls) }
                        else { _: String? -> value }
                    })
                } else {
                    mediaServices?.store?.savePreferences(fitted.preferences) ?: fitted.preferences.forEach { (k, v) -> savePreference(k, v) }
                }
                stateStore.updateBoards { current ->
                    val favorites = plan.boards.filter { it.pinned }.map { it.url }
                    current.map { board ->
                        if (favorites.any { ftutterBoardForUrl(listOf(board), it) != null }) board.copy(pinned = true) else board
                    } + plan.boards.filter { candidate -> current.none { it.id == candidate.id } && ftutterBoardForUrl(current, candidate.url) == null }
                }
                if (plan.history.isNotEmpty()) stateStore.prependOrReplaceHistoryEntries(plan.history)
                plan.cookieHeader?.let { header ->
                    mediaServices?.cookieRepository?.let { cookies ->
                        ftutterImportCookies(cookies, header)
                    }
                }
                // Marks beyond the limit push the oldest out: said with the rest, not dropped without a word (re-audit L22).
                val droppedMarks = mediaServices?.store?.let { store ->
                    plan.marks.sumOf { (url, no) -> com.valoser.futacha.shared.compat.setManualPostMark(store, url, no, true) }
                } ?: 0
                notice = ftutterImportNotice(fitted.leftOut, droppedMarks)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to import a backup", error)
                notice = "取り込めませんでした"
            }
        }
    }
    val tabSettings = remember(preferences) { parseFtutterTabSettings(preferences) }
    val historyByUrl = rememberFtutterHistoryByUrl(history, boards)
    // タブは原作の「履歴の保存数（保護したタブは除く）」に合わせる（監査A1。以前は開いたスレがすべてタブに残り続けた）。
    // 起動時（このモードを開いた時）だけ、履歴に無いタブも捨てる（原作どおり）。
    val historyOrder = remember(historyByUrl) { historyByUrl.keys.toList() }
    val historyLimit = remember(preferences) { ftutterHistoryLimit(preferences) }
    androidx.compose.runtime.SideEffect {
        tabState.historyOrder = historyOrder
        tabState.limit = historyLimit
    }
    val tabsPruned = remember { booleanArrayOf(false) }
    LaunchedEffect(historyLimit) {
        tabState.historyOrder = historyOrder
        tabState.limit = historyLimit
        tabState.enforceLimit(dropMissing = !tabsPruned[0])
        tabsPruned[0] = true
    }
    val readCounts = remember(preferences) { parseFtutterReadCounts(preferences[FtutterKeys.READ_COUNTS]) }
    val tabUrlSet = remember(tabState.urls) { tabState.urls.toHashSet() }
    fun tabView(url: String) = FtutterTabView(
        url = url,
        entry = historyByUrl[url],
        numbers = ftutterTabNumbers(historyByUrl[url], readCounts[url]),
        protected = url in tabState.protected,
        isTab = url in tabUrlSet
    )
    // タブのURLからスレッドを開く（戻るとその板のカタログ）。
    fun openTabUrl(url: String) {
        val threadId = ftutterThreadIdFromUrl(url) ?: return
        val board = ftutterBoardForUrl(boards, url)
        if (board == null) {
            notice = "登録していない板です"
            return
        }
        val entry = historyByUrl[url]
        val current = screen as? FtutterScreen.Thread
        if (current?.boardId == board.id && current.ref.threadId == threadId &&
            preferences.ftutterBool(FtutterKeys.REFRESH_ON_TAB_MOVE_THREAD, false)) threadRefreshSignal++
        openThread(board, FtutterThreadRef(board.id, threadId, entry?.title.orEmpty(), entry?.titleImageUrl.orEmpty()), FtutterScreen.Catalog(board.id),
            refreshOnOpen = preferences.ftutterBool(
                FtutterKeys.REFRESH_ON_TAB_MOVE_THREAD, false))
    }
    // 開いているスレッドのタブが閉じられた時：残りがあれば同じ位置のタブへ、無ければ（設定ON）カタログへ1段だけ戻る。
    fun afterClose(before: List<String>) {
        val current = screen as? FtutterScreen.Thread ?: return
        val board = boards.firstOrNull { it.id == current.boardId } ?: return
        val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, current.ref.threadId))
        if (url in tabState.urls || url !in before) return
        val next = ftutterNextOpening(before, tabState.urls, url)
        when {
            next != null -> openTabUrl(next)
            tabSettings.backWhenNoTab -> screen = FtutterScreen.Catalog(board.id)
        }
    }
    fun closeTabNow(url: String) {
        val before = tabState.urls
        tabState.close(listOf(url))
        afterClose(before)
    }
    var protectedConfirm by remember { mutableStateOf<String?>(null) }
    // 1つ閉じる時だけ、保護したタブは確認する。
    fun closeTab(url: String) {
        if (url in tabState.protected) protectedConfirm = url else closeTabNow(url)
    }
    fun closeMany(targets: List<String>) {
        val before = tabState.urls
        tabState.close(targets.filter { it !in tabState.protected })
        afterClose(before)
    }
    fun restoreClosed(): String? {
        val url = tabState.restore() ?: return null
        if (historyByUrl[url] == null) {
            // 履歴に無ければ履歴にも戻す（原作どおり）。
            val threadId = ftutterThreadIdFromUrl(url)
            val board = ftutterBoardForUrl(boards, url)
            if (threadId != null && board != null) scope.launch {
                runCatching {
                    stateStore.prependOrReplaceHistoryEntry(
                        com.valoser.futacha.shared.ui.futaber.buildFutaberOpenHistoryEntry(
                            null, board,
                            com.valoser.futacha.shared.ui.futaber.FutaberThreadRef(board.id, threadId, "", "", 0),
                            kotlin.time.Clock.System.now().toEpochMilliseconds()
                        )
                    )
                }
            }
        }
        if (screen is FtutterScreen.Thread) openTabUrl(url)
        val title = historyByUrl[url]?.title?.takeIf { it.isNotBlank() } ?: ftutterThreadIdFromUrl(url).orEmpty()
        val message = "タブを戻しました：$title"
        return message
    }
    // 履歴の行を消す：ふたちゃ・ふたばー風の削除と同じく、他モードの履歴（共有ストア。残すと次の起動の統合で戻る）、
    // 自分の書き込みの記録、自動保存した本文も消す（監査A8。以前は共有の履歴だけを消していた）。
    val importedHistoryRepository = remember(mediaServices?.fileSystem) {
        com.valoser.futacha.shared.ui.buildImportedHistoryRepository(mediaServices?.fileSystem)
    }
    fun dismissHistoryRows(entries: List<ThreadHistoryEntry>) {
        if (entries.isEmpty()) return
        scope.launch {
            try {
                com.valoser.futacha.shared.ui.dismissHistoryEntries(
                    stateStore = stateStore,
                    autoSavedThreadRepository = autoSavedThreadRepository,
                    importedHistoryRepository = importedHistoryRepository,
                    compatibilityStore = mediaServices?.store,
                    entries = entries,
                    onAutoSavedThreadDeleteFailure = {
                        com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to delete the auto-saved copy", it)
                        notice = com.valoser.futacha.shared.ui.futaber.FUTABER_AUTO_SAVE_DELETE_FAILED_NOTICE
                    }
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to delete history", error)
                notice = "履歴を削除できませんでした"
            }
        }
    }
    // 履歴の削除：そのスレのタブも閉じる（原作の「履歴から削除」と同じ。1件・選択した分は保護したタブも閉じる）。
    fun deleteHistoryUrls(urls: Collection<String>) {
        val targets = urls.toSet()
        if (targets.isEmpty()) return
        val entries = ftutterHistoryEntriesFor(history, boards, targets)
        val before = tabState.urls
        tabState.close(before.filter { it in targets })
        afterClose(before)
        dismissHistoryRows(entries)
    }
    // 「すべて削除」（拡張）：保護したスレッドの履歴は残す。
    fun deleteAllHistory() {
        val entries = ftutterHistoryEntriesExcept(history, boards, tabState.protected)
        val urls = entries.mapNotNull { ftutterHistoryThreadUrl(it, boards) }.toSet()
        val before = tabState.urls
        tabState.close(before.filter { it in urls && it !in tabState.protected })
        afterClose(before)
        dismissHistoryRows(entries)
    }
    var refreshingTabs by remember { mutableStateOf(false) }
    // 「タブを全て更新」：履歴の並びは変えない（原作の pushToRireki:false）。落ちたスレには印を付ける。
    fun refreshAllTabs() {
        if (refreshingTabs) return
        refreshingTabs = true
        val targets = tabState.urls
        scope.launch {
            var failed = 0
            targets.forEach { url ->
                val threadId = ftutterThreadIdFromUrl(url) ?: return@forEach
                val board = ftutterBoardForUrl(boards, url) ?: return@forEach
                val repo = if (board.isMockBoard()) mockRepository else threadRepository
                try {
                    val page = repo.getThreadContent(board.url, threadId).page
                    stateStore.updateHistory { com.valoser.futacha.shared.ui.futaber.futaberHistoryAfterLoad(it, board.id, threadId, page.posts) }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    failed++
                    val code = error.statusCodeOrNull()
                    if (code == 404 || code == 410) markFallen(stateStore, board.id, threadId)
                }
            }
            refreshingTabs = false
            notice = if (failed == 0) "タブを全て更新しました" else "タブを全て更新しました（$failed 件は取得できませんでした）"
        }
    }
    // ドロワーはカタログ・スレッドだけ。それ以外の画面へ移ったら閉じた状態に戻す（監査A3：板一覧へ移っても開いたまま残り、
    // 次にカタログへ入るとドロワーが開いた状態で出た）。
    val drawerAvailable = screen is FtutterScreen.Catalog || screen is FtutterScreen.Thread
    val drawerState = remember(drawerAvailable) { androidx.compose.material3.DrawerState(androidx.compose.material3.DrawerValue.Closed) }
    val drawerShowing = drawerState.isOpen || drawerState.targetValue == androidx.compose.material3.DrawerValue.Open
    // 戻るの順（ftutterBackStep）：ドロワー → 二画面のカタログ側の検索 → 画面を戻る。どの受け手もこの順で決めるので、
    // 受け手を登録した順（後から置いたものが先に受ける）に左右されない。
    fun handleBack(twoPane: Boolean, leave: () -> Unit) {
        when (ftutterBackStep(drawerShowing, twoPane && catalogHolder.paneBack != null)) {
            FtutterBackStep.CLOSE_DRAWER -> scope.launch { drawerState.close() }
            FtutterBackStep.CATALOG_PANE -> catalogHolder.paneBack?.invoke()
            FtutterBackStep.LEAVE -> leave()
        }
    }
    var drawerContent by remember(tabSettings.drawerContent) { mutableStateOf(tabSettings.drawerContent) }
    var drawerFilter by remember { mutableStateOf(FtutterDrawerFilter()) }
    var tabDialog by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // ドロワーの「履歴」の行の長押しメニューと、その確認・NGの登録。
    var historyMenu by remember { mutableStateOf<String?>(null) }
    var historyDeleteConfirm by remember { mutableStateOf<String?>(null) }
    var historyDelConfirm by remember { mutableStateOf<String?>(null) }
    var historyNgDraft by remember { mutableStateOf<FtutterNgRule?>(null) }
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val currentThreadUrl = (screen as? FtutterScreen.Thread)?.let { t ->
        boards.firstOrNull { it.id == t.boardId }?.let { ftutterCanonicalThreadUrl(ftutterThreadUrl(it, t.ref.threadId)) }
    }
    val tabStrip: @Composable () -> Unit = {
        if (tabSettings.showTab) {
            FtutterTabStrip(
                tabs = tabState.urls.map(::tabView),
                opening = currentThreadUrl,
                listState = tabStripState,
                showStatus = FtutterExtension.TAB_STATUS in extensions,
                settings = tabSettings,
                design = design,
                onOpen = ::openTabUrl,
                onLongPress = { url -> tabDialog = url to false },
                onMove = if (preferences.ftutterBool(FtutterKeys.DISABLE_TAB_DRAG, false)) null else { from, to -> tabState.move(from, to) }
            )
        }
    }

    val gesture = remember(preferences) { parseFtutterGestureSettings(preferences) }
    val tablet = remember(preferences) { parseFtutterTabletSettings(preferences) }
    var toolbarEditor by remember { mutableStateOf<FtutterOrderEditorKind?>(null) }
    var lastCatalogBoardId by remember { mutableStateOf(preferences[FtutterKeys.LAST_BOARD].orEmpty()) }
    // スレッドの「前へ・次へ」は隣のタブ（原作のスレッドはタブを横に並べたページ）。
    fun threadGesture(action: FtutterGestureAction, current: FtutterScreen.Thread, board: BoardSummary, twoPane: Boolean) {
        val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, current.ref.threadId))
        when (action) {
            FtutterGestureAction.PREVIOUS -> {
                val prev = ftutterAdjacentTab(tabState.urls, url, forward = false)
                when {
                    prev != null -> openTabUrl(prev)
                    // 先頭のタブで「前へ」：二画面でなく、設定（既定ON）ならカタログへ戻る。
                    gesture.backByPreviousInThread && !twoPane -> screen = current.back
                }
            }
            FtutterGestureAction.NEXT -> ftutterAdjacentTab(tabState.urls, url, forward = true)?.let(::openTabUrl)
            FtutterGestureAction.BACK -> screen = if (twoPane) FtutterScreen.Boards else current.back
            FtutterGestureAction.CLOSE_TAB -> closeTab(url)
            FtutterGestureAction.CLOSE_TAB_AND_BACK -> {
                // 閉じて戻る（再監査L27：閉じる処理が隣のタブを開くと、戻らずに隣のタブを見せていた）。保護したタブは
                // 確認を出すだけで、戻らない（従来どおり）。
                // 二画面では従来どおり閉じるだけ（戻る先の画面を積まない）。
                if (twoPane || url in tabState.protected) closeTab(url) else {
                    tabState.close(listOf(url))
                    screen = current.back
                }
            }
            FtutterGestureAction.REOPEN_TAB -> restoreClosed()?.let { notice = it }
            FtutterGestureAction.MOST_LEFT -> tabState.urls.firstOrNull()?.let(::openTabUrl)
            FtutterGestureAction.MOST_RIGHT -> tabState.urls.lastOrNull()?.let(::openTabUrl)
            FtutterGestureAction.REFRESH_ALL_TABS -> refreshAllTabs()
            FtutterGestureAction.OPEN_DRAWER -> openDrawer()
            else -> Unit
        }
    }
    // カタログの「前へ・次へ」はお気に入りの隣の板（原作のカタログはお気に入りの板を横に並べたページ）。
    fun catalogGesture(action: FtutterGestureAction, board: BoardSummary) {
        when (action) {
            FtutterGestureAction.PREVIOUS, FtutterGestureAction.NEXT -> {
                val favorites = boards.filter { it.pinned }.map { it.id }
                when (val step = ftutterBoardStep(favorites, board.id, action == FtutterGestureAction.NEXT, gesture.backByPreviousInCatalog)) {
                    is FtutterBoardStep.To -> boards.firstOrNull { it.id == step.boardId }?.let(::openCatalog)
                    FtutterBoardStep.Back -> screen = FtutterScreen.Boards
                    FtutterBoardStep.Stay -> Unit
                }
            }
            FtutterGestureAction.BACK -> screen = FtutterScreen.Boards
            // カタログで「タブを閉じる」は、最後に開いたスレッドのタブを閉じる（画面は移らない）。
            FtutterGestureAction.CLOSE_TAB, FtutterGestureAction.CLOSE_TAB_AND_BACK ->
                preferences[FtutterKeys.OPENING]?.let(::ftutterCanonicalThreadUrl)?.takeIf { it in tabState.urls }?.let(::closeTab)
            FtutterGestureAction.REOPEN_TAB -> restoreClosed()?.let { notice = it }
            FtutterGestureAction.MOST_LEFT -> tabState.urls.firstOrNull()?.let(::openTabUrl)
            FtutterGestureAction.MOST_RIGHT -> tabState.urls.lastOrNull()?.let(::openTabUrl)
            FtutterGestureAction.REFRESH_ALL_TABS -> refreshAllTabs()
            FtutterGestureAction.OPEN_DRAWER -> openDrawer()
            else -> Unit
        }
    }

    // スレへのdel（スレの本文＝No.がスレ番号）。確認は呼ぶ側で出す。
    fun sendThreadDel(board: BoardSummary, threadId: String) {
        scope.launch {
            val result = com.valoser.futacha.shared.ui.board.performThreadAction {
                com.valoser.futacha.shared.ui.board.requireWritableThreadBoard(board.url)
                repositoryFor(board).requestDeletion(board.url, threadId, threadId, com.valoser.futacha.shared.ui.board.DEFAULT_DEL_REASON_CODE)
            }
            notice = when (result) {
                is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Success -> "登録しました"
                is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Failure ->
                    com.valoser.futacha.shared.ui.board.buildThreadActionFailureMessage("delに失敗しました", result.error)
            }
        }
    }

    @Composable
    fun CatalogPane(board: BoardSummary, current: FtutterScreen, columnsOverride: Int?, strip: @Composable () -> Unit) {
        val board = board.copy(url = ftutterNetworkUrl(board.url, preferences.ftutterBool(FtutterKeys.USE_HTTPS, false)))
        LaunchedEffect(board.id) { lastCatalogBoardId = board.id }
        FtutterCatalogScreen(
            board = board,
            boards = boards,
            repository = if (board.isMockBoard()) mockRepository else repository,
            history = history,
            preferences = preferences,
            holder = catalogHolder,
            savePreference = savePreference,
            editPreference = editPreference,
            onBack = { screen = FtutterScreen.Boards },
            onSwitchBoard = ::openCatalog,
            onOpenSettings = { screen = FtutterScreen.Settings(current) },
            onOpenThread = { item ->
                openThread(board, FtutterThreadRef(
                    boardId = board.id, threadId = item.id, title = item.title.orEmpty(),
                    thumbnailUrl = item.thumbnailUrl.orEmpty(), catalogReplyCount = item.replyCount
                ), current)
            },
            onOpenHiddenAndNg = { screen = FtutterScreen.HiddenAndNg(current) },
            onOpenUrl = { url ->
                val target = ftutterBoardForUrl(boards, url)
                if (target != null && !url.contains("/res/")) openCatalog(target) else openThreadUrl(url, current)
            },
            onDeleteHistory = { entry ->
                // 原作：タブを閉じて履歴から削除する（ドロワーの「履歴から削除」と同じ後片付け）。
                val url = ftutterHistoryThreadUrl(entry, boards)
                if (url != null) deleteHistoryUrls(listOf(url)) else dismissHistoryRows(listOf(entry))
            },
            onNotice = { notice = it },
            onOpenDrawer = openDrawer,
            onAddTab = { item ->
                val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, item.id))
                tabState.push(url)
                // 原作ではタブは履歴の中にしか無いので、履歴にも入れる（再監査M2：入れないと、次にこのモードを開いた時の整理で
                // 「履歴に無いタブ」として黙って消えた）。題名・サムネ・レス数はカタログのもの。
                if (historyByUrl[url] == null) scope.launch {
                    runCatching {
                        stateStore.prependOrReplaceHistoryEntry(
                            com.valoser.futacha.shared.ui.futaber.buildFutaberOpenHistoryEntry(
                                null, board,
                                com.valoser.futacha.shared.ui.futaber.FutaberThreadRef(
                                    board.id, item.id, item.title.orEmpty(), item.thumbnailUrl.orEmpty(), item.replyCount + 1
                                ),
                                kotlin.time.Clock.System.now().toEpochMilliseconds()
                            )
                        )
                    }.onFailure { com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to record the added tab in the history", it) }
                }
            },
            tabOnTop = tabSettings.tabOnTop,
            tabStrip = strip,
            gesture = gesture,
            onAppGesture = { action -> catalogGesture(action, board) },
            onSwipeBoard = { forward -> catalogGesture(if (forward) FtutterGestureAction.NEXT else FtutterGestureAction.PREVIOUS, board) },
            onEditToolbar = { toolbarEditor = FtutterOrderEditorKind.CATALOG },
            columnsOverride = columnsOverride,
            openTabUrls = tabUrlSet,
            commonCatalogNgWords = if (commonNgOn) sharedCatalogNgWords else emptyList(),
            archiveClient = if (FtutterExtension.ARCHIVE_SEARCH in extensions && !board.isMockBoard()) httpClient else null,
            ngRules = ngRules,
            onSaveNgRules = saveNgRules,
            onCreateThread = { postRequest = FtutterPostTarget.Create(board.id) to null },
            onDelThread = { item -> sendThreadDel(board, item.id) }
        )
    }

    @Composable
    fun ThreadPane(board: BoardSummary, current: FtutterScreen.Thread, strip: @Composable () -> Unit, twoPane: Boolean) {
        val board = board.copy(url = ftutterNetworkUrl(board.url, preferences.ftutterBool(FtutterKeys.USE_HTTPS, false)))
        val url = ftutterCanonicalThreadUrl(ftutterThreadUrl(board, current.ref.threadId))
        val refreshOnOpen = remember(current) { threadMemory.takeRefresh(url) }
        FtutterThreadHost(
            board = board,
            ref = current.ref,
            initialPage = threadMemory.page(url),
            refreshOnOpen = refreshOnOpen,
            onPageSnapshot = { threadMemory.put(url, it) },
            repository = if (board.isMockBoard()) mockRepository else threadRepository,
            httpClient = httpClient,
            stateStore = stateStore,
            history = history,
            preferences = preferences,
            design = design,
            selfPostMap = selfPostMap,
            store = mediaServices?.store,
            onArchiveReportEnqueued = onArchiveReportEnqueued,
            savePreference = savePreference,
            editPreference = editPreference,
            onOpenThreadUrl = { url -> openThreadUrl(url, current) },
            onOpenSettings = { screen = FtutterScreen.Settings(current) },
            onBack = { handleBack(twoPane) { screen = if (twoPane) FtutterScreen.Boards else current.back } },
            onNotice = { notice = it },
            onOpenDrawer = openDrawer,
            onFallen = { scope.launch { markFallen(stateStore, board.id, current.ref.threadId) } },
            tabOnTop = tabSettings.tabOnTop,
            tabStrip = strip,
            ngRules = ngRules,
            boards = boards,
            onSaveNgRules = saveNgRules,
            manualMarks = manualMarks,
            deleteKey = deleteKey.orEmpty(),
            onReply = { quote -> postRequest = FtutterPostTarget.Reply(board.id, current.ref.threadId) to quote },
            refreshSignal = threadRefreshSignal,
            gesture = gesture,
            onAppGesture = { action -> threadGesture(action, current, board, twoPane) },
            onSwipeTab = { forward -> threadGesture(if (forward) FtutterGestureAction.NEXT else FtutterGestureAction.PREVIOUS, current, board, twoPane) },
            onEditToolbar = { toolbarEditor = FtutterOrderEditorKind.THREAD },
            mediaServices = mediaServices,
            onSoudaneSent = { countStat(FtutterKeys.STATS_SOUDANE) },
            extensions = extensions,
            commonNgWords = sharedNgWords,
            commonNgHeaders = sharedNgHeaders,
            compatNgRules = compatNgRules,
            threadActions = threadActions
        )
    }

    FtutterTheme(mainColor = design.mainColor, brightness = design.brightness, overrides = design.overrides, disableStretch = design.disableStretchAnimation,
        fontFamily = LocalFtutterFont.current, preferences = preferences) {
        val colors = LocalFtutterColors.current
        // iOSのホームバーの帯（Composeが描かない下端）を、下のツールバーと同じ色にする（ふたばー風と同じ仕組み。iOS以外は何もしない）。
        com.valoser.futacha.shared.ui.futaber.ApplyFutaberHostBackground(colors.toolBar)
        val historyUrls = remember(historyByUrl, tabState.protected, historyLimit) {
            ftutterLimitedHistoryUrls(historyByUrl.keys.toList(), tabState.protected, historyLimit)
        }
        FtutterDrawerHost(
            drawerState = drawerState,
            settings = tabSettings,
            enabled = screen is FtutterScreen.Catalog || screen is FtutterScreen.Thread,
            drawer = {
                // 題名の検索は、打ち終わってから効かせ、正規化した題名は履歴が変わった時に1回だけ裏で作る（監査M11）。
                // できるまでの間は前の行を出したままにする。
                val appliedFilter = drawerFilter.copy(query = com.valoser.futacha.shared.ui.futaber.rememberFutaberDebouncedQuery(drawerFilter.query))
                val searchingTitles = appliedFilter.query.isNotBlank()
                val titleKeys by androidx.compose.runtime.produceState<Pair<Map<String, ThreadHistoryEntry>, Map<String, String>>?>(null, historyByUrl, searchingTitles) {
                    value = if (!searchingTitles) null else historyByUrl to kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        ftutterDrawerTitleKeys(historyByUrl)
                    }
                }
                val keysNow = titleKeys?.takeIf { it.first === historyByUrl }?.second
                // 行は履歴（最大2万件）が変わるたびに全件作るので、解析用のスレッドで作る（再監査M7：ドロワーは閉じていても
                // 組み立てるので、履歴の保存のたびにメインで作っていた）。できるまでの間は前の行を出したままにする。
                val tabUrls = tabState.urls
                val protectedUrls = tabState.protected
                val rows by androidx.compose.runtime.produceState(
                    emptyList<FtutterTabView>(), drawerContent, appliedFilter, historyUrls, tabUrls, protectedUrls, readCounts, historyByUrl, keysNow
                ) {
                    if (searchingTitles && keysNow == null) return@produceState
                    value = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        ftutterDrawerRows(drawerContent, historyUrls, tabUrls, protectedUrls, historyByUrl, readCounts, appliedFilter, keysNow)
                    }
                }
                FtutterDrawerPanel(
                    showStatus = FtutterExtension.TAB_STATUS in extensions,
                    content = drawerContent,
                    rows = rows,
                    filter = drawerFilter,
                    closedCount = tabState.closed.size,
                    dismissDirection = tabSettings.dismissDirection,
                    design = design,
                    onContentChange = { drawerContent = it; savePreference(FtutterKeys.DRAWER_CONTENT, it.stored.toString()) },
                    onFilterChange = { drawerFilter = it },
                    onRestoreClosed = ::restoreClosed,
                    onOpen = { url -> scope.launch { drawerState.close() }; openTabUrl(url) },
                    // 原作：「履歴」の行の長押しはカタログと同じメニュー（タブの行でも）。「タブ」の表示はタブのメニュー。
                    onLongPress = { url ->
                        if (drawerContent == FtutterDrawerContent.HISTORY) historyMenu = url
                        else if (url in tabState.urls) tabDialog = url to true
                    },
                    onClose = ::closeTab,
                    onToggleProtect = { url -> tabState.toggleProtect(url) },
                    historyTools = FtutterExtension.HISTORY_TOOLS in extensions,
                    privacy = preferences.ftutterBool(FtutterKeys.IS_PRIVACY, false),
                    selectionReset = drawerState.isOpen,
                    onDeleteHistory = ::deleteHistoryUrls,
                    onDeleteAllHistory = ::deleteAllHistory
                )
            }
        ) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize().testTag("ftutter-root")) {
            val twoPane = ftutterIsTwoPane(tablet, maxWidth.value)
            // 二画面の列数は、ペインではなく画面全体の向きで縦・横を選ぶ（原作）。
            val tabletColumns = if (maxWidth > maxHeight) tablet.columnsLandscape else tablet.columnsPortrait
            when (val current = screen) {
                FtutterScreen.Boards -> FtutterBoardListScreen(
                    boardsFromBottom = com.valoser.futacha.shared.ui.board.boardsFromBottom(preferences),
                    boards = boards,
                    httpClient = httpClient,
                    onOpenBoard = ::openCatalog,
                    onOpenSettings = { screen = FtutterScreen.Settings(FtutterScreen.Boards) },
                    onEditBoards = editBoards,
                    onBulkAdd = bulkAddBoards,
                    onOpenUrl = openUrl,
                    onNotice = { notice = it }
                )
                is FtutterScreen.Catalog, is FtutterScreen.Thread -> {
                    val boardId = if (current is FtutterScreen.Catalog) current.boardId else (current as FtutterScreen.Thread).boardId
                    val board = boards.firstOrNull { it.id == boardId }
                    if (board == null) {
                        LaunchedEffect(Unit) { screen = FtutterScreen.Boards }
                    } else if (twoPane) {
                        // 二画面：左（入れ替えで右）は最後に開いたカタログ、反対側はスレッド。スレッドを開いても画面を積まない。
                        // カタログだけの時とスレッドを開いた時を同じ呼び出しで描く（再監査L27：別の場所で描いていたので、
                        // 最初のスレを開くとカタログが作り直され、検索などが消えた）。
                        val thread = current as? FtutterScreen.Thread
                        val catalogBoard = if (thread == null) board else boards.firstOrNull { it.id == lastCatalogBoardId } ?: board
                        val catalogScreen = current as? FtutterScreen.Catalog ?: FtutterScreen.Catalog(catalogBoard.id)
                        PlatformBackHandler(enabled = true) { handleBack(true) { screen = FtutterScreen.Boards } }
                        FtutterTwoPane(
                            tablet,
                            catalog = { strip -> CatalogPane(catalogBoard, catalogScreen, tabletColumns, strip) },
                            thread = thread?.let { t -> { strip -> ThreadPane(board, t, strip, true) } },
                            tabStrip = tabStrip
                        )
                    } else if (current is FtutterScreen.Catalog) {
                        PlatformBackHandler(enabled = true) { handleBack(false) { screen = FtutterScreen.Boards } }
                        CatalogPane(board, current, null, tabStrip)
                    } else {
                        ThreadPane(board, current as FtutterScreen.Thread, tabStrip, false)
                    }
                }
                is FtutterScreen.HiddenAndNg -> {
                    PlatformBackHandler(enabled = true) { screen = current.back }
                    FtutterHiddenAndNgScreen(preferences, editPreference, onBack = { screen = current.back }, ngRemoved = catalogHolder.ngRemoved, ngReasons = catalogHolder.ngReasons)
                }
                is FtutterScreen.Settings -> {
                    FtutterSettingsHost(
                        preferences = preferences,
                        save = savePreference,
                        edit = editPreference,
                        appVersion = appVersion,
                        boards = boards,
                        ngRules = ngRules,
                        onSaveNgRules = saveNgRules,
                        deleteKey = deleteKey.orEmpty(),
                        ngRemoved = catalogHolder.ngRemoved, ngReasons = catalogHolder.ngReasons,
                        services = mediaServices,
                        stateStore = stateStore,
                        onEditToolbar = { toolbarEditor = it },
                        onImportBackup = pickBackup,
                        onBack = { screen = current.back },
                        repository = repository,
                        onOpenThread = { target, threadId, title ->
                            openThread(target, FtutterThreadRef(target.id, threadId, title), current.back)
                        }
                    )
                }
            }
            notice?.let { text ->
                // 原作のトーストと同じく下寄せだが、下部のツールバー（最大80dp）を覆って押せなくしないよう、その上に出す。
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 96.dp).testTag("ftutter-notice"),
                    containerColor = colors.popUp,
                    contentColor = colors.text
                ) { Text(text) }
            }
        }
        }
        // ドロワーが開いている間の戻るは、ドロワーを閉じる。開いた時に登録し直すので、先に置いたカタログ・スレッドの
        // 戻るより先に受ける（ふたばー風の2026-10-07監査A10と同じ直し方。以前は最初に1度だけ登録していて負けていた）。
        if (drawerShowing) PlatformBackHandler(enabled = true) { scope.launch { drawerState.close() } }
        tabDialog?.let { (url, fromDrawer) ->
            FtutterTabDialog(
                protected = url in tabState.protected,
                fromDrawer = fromDrawer,
                onDismiss = { tabDialog = null },
                onAction = { action ->
                    tabDialog = null
                    when (action) {
                        FtutterTabAction.PROTECT -> tabState.toggleProtect(url)
                        FtutterTabAction.REFRESH_ALL -> refreshAllTabs()
                        FtutterTabAction.CLOSE_FALLEN -> closeMany(tabState.urls.filter { historyByUrl[it]?.isAutoRefreshDisabled == true })
                        FtutterTabAction.CLOSE_AFTER -> {
                            val order = if (fromDrawer && drawerContent == FtutterDrawerContent.TABS_BY_ACCESS) {
                                ftutterDrawerUrls(drawerContent, historyUrls, tabState.urls)
                            } else tabState.urls
                            closeMany(ftutterTabsAfter(order, url))
                        }
                        FtutterTabAction.CLOSE_ALL -> closeMany(tabState.urls)
                    }
                }
            )
        }
        historyMenu?.let { url ->
            FtutterThumbDialog(
                hasHistory = historyByUrl[url] != null,
                onDismiss = { historyMenu = null },
                onAction = { action ->
                    historyMenu = null
                    val board = ftutterBoardForUrl(boards, url)
                    val title = historyByUrl[url]?.title.orEmpty()
                    when (action) {
                        FtutterThumbAction.HIDE -> editPreference(FtutterKeys.HIDDEN_THREADS) { stored ->
                            val (next, dropped) = ftutterHiddenThreadsWith(parseFtutterHiddenThreads(stored), url)
                            notice = ftutterHiddenNotice(dropped)
                            encodeFtutterList(next)
                        }
                        FtutterThumbAction.DELETE_HISTORY -> historyDeleteConfirm = url
                        FtutterThumbAction.ADD_TAB -> { tabState.push(url); notice = "タブに追加しました" }
                        FtutterThumbAction.DEL -> if (board == null) notice = "登録していない板です" else historyDelConfirm = url
                        FtutterThumbAction.PICKUP, FtutterThumbAction.NG_TITLE -> historyNgDraft = FtutterNgRule(
                            title,
                            if (action == FtutterThumbAction.PICKUP) FtutterNgType.PICKUP.stored else FtutterNgType.TITLE.stored,
                            ita = board?.let { FtutterNgBoard(it.name, it.url) }
                        )
                    }
                }
            )
        }
        historyDeleteConfirm?.let { url ->
            FtutterHistoryDeleteConfirm(historyByUrl[url]?.title.orEmpty(), onDismiss = { historyDeleteConfirm = null }) {
                historyDeleteConfirm = null
                deleteHistoryUrls(listOf(url))
            }
        }
        historyDelConfirm?.let { url ->
            FtutterConfirmDialog(
                title = "delを送りますか？",
                message = "${historyByUrl[url]?.title.orEmpty()}\n掲示板の管理側へ削除依頼（del）を送ります。",
                confirmLabel = "送る",
                onDismiss = { historyDelConfirm = null }
            ) {
                historyDelConfirm = null
                val board = ftutterBoardForUrl(boards, url)
                val threadId = ftutterThreadIdFromUrl(url)
                if (board != null && threadId != null) sendThreadDel(board, threadId)
            }
        }
        historyNgDraft?.let { draft ->
            FtutterNgDialog(
                initial = draft,
                boards = boards,
                existing = ngRules,
                onDismiss = { historyNgDraft = null },
                onSave = { rule -> historyNgDraft = null; saveNgRules({ it + rule }, "登録しました") }
            )
        }
        protectedConfirm?.let { url ->
            FtutterProtectedCloseDialog(
                onCancel = { protectedConfirm = null },
                onConfirm = { protectedConfirm = null; closeTabNow(url) }
            )
        }
        toolbarEditor?.let { kind ->
            if (kind == FtutterOrderEditorKind.POST_MENU) {
                FtutterOrderEditorDialog(
                    title = "レス長押しメニューの並び",
                    entries = parseFtutterPostMenu(preferences),
                    label = { it.label },
                    onDismiss = { toolbarEditor = null },
                    onReset = { toolbarEditor = null; savePreference(FtutterKeys.POST_MENU_ORDER, ""); savePreference(FtutterKeys.POST_MENU_ENABLED, "") },
                    onSave = { saved ->
                        toolbarEditor = null
                        val (names, flags) = encodeFtutterToolbar(saved) { it.stored }
                        savePreference(FtutterKeys.POST_MENU_ORDER, names)
                        savePreference(FtutterKeys.POST_MENU_ENABLED, flags)
                    }
                )
            } else if (kind == FtutterOrderEditorKind.CATALOG) {
                FtutterOrderEditorDialog(
                    title = "ツールバー編集（カタログ）",
                    entries = parseFtutterCatalogToolbar(preferences),
                    label = { it.label },
                    onDismiss = { toolbarEditor = null },
                    onReset = { toolbarEditor = null; savePreference(FtutterKeys.CATALOG_TOOLBAR_ORDER, ""); savePreference(FtutterKeys.CATALOG_TOOLBAR_ENABLED, "") },
                    onSave = { saved ->
                        toolbarEditor = null
                        val (names, flags) = encodeFtutterToolbar(saved) { it.stored }
                        savePreference(FtutterKeys.CATALOG_TOOLBAR_ORDER, names)
                        savePreference(FtutterKeys.CATALOG_TOOLBAR_ENABLED, flags)
                    }
                )
            } else {
                FtutterOrderEditorDialog(
                    title = "ツールバー編集（スレッド）",
                    entries = parseFtutterThreadToolbar(preferences),
                    label = { it.label },
                    onDismiss = { toolbarEditor = null },
                    onReset = { toolbarEditor = null; savePreference(FtutterKeys.THREAD_TOOLBAR_ORDER, ""); savePreference(FtutterKeys.THREAD_TOOLBAR_ENABLED, "") },
                    onSave = { saved ->
                        toolbarEditor = null
                        val (names, flags) = encodeFtutterToolbar(saved) { it.stored }
                        savePreference(FtutterKeys.THREAD_TOOLBAR_ORDER, names)
                        savePreference(FtutterKeys.THREAD_TOOLBAR_ENABLED, flags)
                    }
                )
            }
        }
        if (backupLoading != null) {
            FutachaAppLockAwareWindow { androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                modifier = Modifier.testTag("ftutter-backup-loading"),
                title = { Text("読み込み中…") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Text("ふたったーのバックアップを読み込んでいます", modifier = Modifier.padding(start = 16.dp))
                    }
                },
                confirmButton = {},
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { backupLoading?.cancel(); backupLoading = null },
                        modifier = Modifier.testTag("ftutter-backup-loading-cancel")) { Text("キャンセル") }
                }
            ) }
        }
        backupPlan?.let { plan ->
            FutachaAppLockAwareWindow { androidx.compose.material3.AlertDialog(
                onDismissRequest = { backupPlan = null },
                modifier = Modifier.testTag("ftutter-backup-confirm"),
                title = { Text("ふたったーのバックアップを取り込みますか？") },
                // 監査L14：取り込んだ履歴は今の履歴の上に並ぶ（確認文と実際を合わせた）。
                text = { Text("${plan.summary}\n\n今の設定・板・履歴は残し、足りないものを足します（同じ設定は取り込んだ値になります）。取り込んだ履歴は、今の履歴の上に並びます。",
                    modifier = Modifier.verticalScroll(rememberScrollState())) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { backupPlan = null; applyBackup(plan) },
                        modifier = Modifier.testTag("ftutter-backup-confirm-ok")) { Text("取り込む") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { backupPlan = null }) { Text("キャンセル") } }
            ) }
        }
        postRequest?.let { (target, quote) ->
            val board = boards.firstOrNull { it.id == target.boardId }
            if (board == null) {
                postRequest = null
            } else {
                val draftKey = ftutterDraftKey(target, board.url)
                val drafts = remember(draftKey) { parseFtutterDrafts(preferences[FtutterKeys.DRAFTS]) }
                var capabilities by remember(draftKey) { mutableStateOf(com.valoser.futacha.shared.network.defaultBoardPostingCapabilities(board.url)) }
                LaunchedEffect(draftKey) {
                    if (board.isMockBoard()) return@LaunchedEffect
                    try {
                        capabilities = repository.getPostingCapabilities(board.url, (target as? FtutterPostTarget.Reply)?.threadId)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        // 取れなければ板の既定の上限のまま。
                    }
                }
                FtutterPostDialog(
                    target = target,
                    boardUrl = board.url,
                    boardName = board.name,
                    initialDraft = drafts[draftKey] ?: FtutterDraft(),
                    quote = quote,
                    deleteKey = deleteKey.orEmpty(),
                    confirmBeforePost = preferences.ftutterBool(FtutterKeys.CONFIRM_BEFORE_POST, false),
                    capabilities = capabilities,
                    pickerPreference = pickerPreference,
                    preferredFileManagerPackage = preferredFileManager?.packageName,
                    deviceInfo = {
                        com.valoser.futacha.shared.ui.futaber.futaberDeviceInfoText(com.valoser.futacha.shared.ui.compat.compatPostDeviceInfo(appVersion))
                    },
                    uploadUps = httpClient?.let { client ->
                        { image, key -> com.valoser.futacha.shared.ui.compat.uploadCompatUps(client, image, "", key, appVersion) }
                    },
                    onDeleteKeyChange = { savePreference(FtutterKeys.DELETE_KEY, it) },
                    onSaveDraft = { draft, announce ->
                        // 下書きは件数・長さ・全体の大きさに収めて残す（監査A9）。切った・古いものを落とした時は、閉じた時だけ知らせる。
                        // 保存されている下書きに当てる（監査M5：打っている間も残すので、画面の古い値から作ると他の下書きを戻してしまう）。
                        editPreference(FtutterKeys.DRAFTS) { stored ->
                            val update = ftutterDraftsUpdate(parseFtutterDrafts(stored), draftKey, draft)
                            if (announce) ftutterDraftsNotice(update)?.let { notice = it }
                            if (update.encoded == stored) null else update.encoded
                        }
                    },
                    onSend = { input -> sendPost(target, input) },
                    onSent = { posted ->
                        postRequest = null
                        when (target) {
                            is FtutterPostTarget.Reply -> {
                                countStat(FtutterKeys.STATS_REPLIED)
                                notice = "書き込みました"
                                threadRefreshSignal++
                            }
                            is FtutterPostTarget.Create -> {
                                countStat(FtutterKeys.STATS_CREATED)
                                notice = "書き込みました"
                                // スレ立ての後は新しいスレッドを開く（原作は新順のカタログから探して開く）。
                                if (!posted.isNullOrBlank()) openThread(board, FtutterThreadRef(board.id, posted, title = ""), FtutterScreen.Catalog(board.id))
                                else catalogHolder.refreshTick++
                            }
                        }
                    },
                    onDismiss = { postRequest = null },
                    handwritingEnabled = FtutterExtension.HANDWRITING in extensions,
                    onHandwritingSaved = { image ->
                        val fs = mediaServices?.fileSystem
                        if (fs != null) scope.launch {
                            try {
                                com.valoser.futacha.shared.ui.compat.persistCompatDrawingCopy(
                                    fs, com.valoser.futacha.shared.ui.futaber.futaberDrawingCopyLocation(preferences),
                                    image, kotlin.time.Clock.System.now().toEpochMilliseconds()
                                )?.getOrThrow()
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Throwable) {
                                notice = "手書き画像のコピーを保存できませんでした（添付はそのままです）"
                            }
                        }
                    }
                )
            }
        }
    }
}

/** タブの状態（メモリにも持ち、保存が追いつく前の続けての操作でも崩れないようにする）。閉じたタブはメモリだけ。 */
/** 落ちたスレの印（共有の履歴の`isAutoRefreshDisabled`。ふたばー風の「スレ落ちしたタブ」と同じ）。 */
private suspend fun markFallen(stateStore: AppStateStore, boardId: String, threadId: String) {
    try {
        stateStore.updateHistory { list ->
            list.map { if (it.threadId == threadId && it.boardId == boardId) it.copy(isAutoRefreshDisabled = true,
                threadStatus = (it.threadStatus ?: com.valoser.futacha.shared.model.ThreadStatus()).copy(isDead = true)) else it }
        }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to mark a fallen thread", error)
    }
}

/** 原作の上部バー：端まで続く平らな帯。 */
@Composable
internal fun FtutterTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    /** 設定「左上にドロワーを開くボタンを表示」がONの時に渡す。 */
    onOpenDrawer: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val colors = LocalFtutterColors.current
    Row(
        Modifier.fillMaxWidth().background(colors.toolBar).statusBarsPadding().height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onOpenDrawer != null) {
            IconButton(onClick = onOpenDrawer, modifier = Modifier.testTag("ftutter-topbar-drawer")) {
                Icon(Icons.Rounded.Menu, contentDescription = "ドロワーを開く", tint = colors.onToolBar)
            }
        }
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("ftutter-back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "戻る", tint = colors.onToolBar)
            }
        } else {
            Box(Modifier.padding(start = 16.dp))
        }
        Text(
            title,
            color = colors.onToolBar,
            fontSize = 22.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        actions()
    }
}

/** スレッド画面と、履歴・読み位置・既読・外部アーカイブ通知の保存をつなぐ。 */
@Composable
private fun FtutterThreadHost(
    board: BoardSummary,
    ref: FtutterThreadRef,
    initialPage: com.valoser.futacha.shared.model.ThreadPage?,
    refreshOnOpen: Boolean,
    onPageSnapshot: (com.valoser.futacha.shared.model.ThreadPage) -> Unit,
    repository: BoardRepository,
    httpClient: io.ktor.client.HttpClient?,
    stateStore: AppStateStore,
    history: List<ThreadHistoryEntry>,
    preferences: Map<String, String>,
    design: FtutterDesignSettings,
    selfPostMap: Map<String, List<String>>,
    store: com.valoser.futacha.shared.compat.CompatibilityStore?,
    onArchiveReportEnqueued: ((Int) -> Unit)?,
    savePreference: (String, String) -> Unit,
    editPreference: (String, (String?) -> String?) -> Unit,
    onOpenThreadUrl: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    onFallen: () -> Unit,
    tabOnTop: Boolean,
    tabStrip: @Composable () -> Unit,
    ngRules: List<FtutterNgRule>,
    boards: List<BoardSummary>,
    onSaveNgRules: FtutterNgEdit,
    manualMarks: List<com.valoser.futacha.shared.compat.ManualPostMark>,
    deleteKey: String,
    onReply: (String?) -> Unit,
    refreshSignal: Int,
    gesture: FtutterGestureSettings,
    onAppGesture: (FtutterGestureAction) -> Unit,
    onSwipeTab: (Boolean) -> Unit,
    onEditToolbar: () -> Unit,
    mediaServices: FutaberMediaServices?,
    onSoudaneSent: () -> Unit,
    extensions: FtutterExtensions = FtutterExtensions(),
    commonNgWords: List<String> = emptyList(),
    commonNgHeaders: List<String> = emptyList(),
    compatNgRules: List<CompatNgRule> = emptyList(),
    threadActions: FtutterThreadActions? = null
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val colors = LocalFtutterColors.current
    // 画像の保存・画像検索・スレの保存は、ふたちゃ・ふたばー風と同じ共有の部品と保存先。
    val sharedFeatures = com.valoser.futacha.shared.ui.futaber.rememberFutaberSharedFeatures(mediaServices, repository)
    val mediaTab = remember(board, ref.threadId) {
        com.valoser.futacha.shared.ui.futaber.futaberMediaTab(
            board, com.valoser.futacha.shared.ui.futaber.FutaberThreadRef(board.id, ref.threadId, ref.title, ref.thumbnailUrl, 0), 0L
        )
    }
    val mediaStore = mediaServices?.store
    val saver = if (mediaStore != null) {
        com.valoser.futacha.shared.ui.futaber.rememberFutaberMediaSaver(
            mediaStore, preferences, mediaServices.httpClient, mediaServices.fileSystem, mediaTab.boardKey, mediaTab.key
        ) { onNotice(it) }
    } else null
    val destinationKey = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("storage", "dummyDownloadDir")
    val chooseDestination = com.valoser.futacha.shared.ui.board.rememberDirectoryPickerLauncher(
        onDirectorySelected = { location ->
            val target = mediaStore ?: return@rememberDirectoryPickerLauncher
            scope.launch {
                runCatching {
                    target.savePreference(destinationKey, with(com.valoser.futacha.shared.model.SaveLocation.Companion) { location.toRawString() })
                }.onFailure { onNotice("保存先を記録できませんでした") }
            }
        }
    )
    val saveService = remember(mediaServices) {
        val client = mediaServices?.httpClient
        val fs = mediaServices?.fileSystem
        if (client != null && fs != null) com.valoser.futacha.shared.service.SingleMediaSaveService(client, fs) else null
    }
    val currentPreferencesForSave by rememberUpdatedState(preferences)
    val mediaTools = FtutterMediaSaveTools(
        destinationLabel = ftutterDestinationLabel(com.valoser.futacha.shared.ui.compat.parseCompatSaveLocation(preferences[destinationKey])),
        chooseDestination = chooseDestination,
        saveOne = { url -> saver?.saveOrShare(url, shareAfterSave = false) ?: onNotice("保存機能を初期化できませんでした") },
        saveMany = { urls, onProgress ->
            val service = saveService
            if (service == null) 0 else {
                val location = com.valoser.futacha.shared.ui.compat.parseCompatSaveLocation(currentPreferencesForSave[destinationKey])
                var saved = 0
                urls.forEachIndexed { i, url ->
                    service.saveMedia(url, mediaTab.boardKey, mediaTab.key, baseSaveLocation = location,
                        storageDirectoryOverride = "", useTypeSubdirectory = false).onSuccess { saved++ }
                    onProgress(i + 1)
                }
                saved
            }
        },
        isSaving = saver?.isSaving == true
    )
    var searchUrl by remember { mutableStateOf<String?>(null) }
    var savePage by remember { mutableStateOf<com.valoser.futacha.shared.model.ThreadPage?>(null) }
    val features = sharedFeatures
    searchUrl?.let { url ->
        val client = mediaServices?.httpClient
        if (client != null && features != null) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides ftutterMediaPalette(colors),
                com.valoser.futacha.shared.ui.board.LocalFutachaSharedFeatures provides features
            ) {
                com.valoser.futacha.shared.ui.board.ThreadImageSearchDialog(url, client, mediaServices.cookieRepository,
                    onDismiss = { searchUrl = null }, openExternal = rememberFtutterUrlLauncher(preferences), preferredTarget = if (preferences.ftutterBool(FtutterKeys.IMAGE_SEARCH_ASCII2D, false))
                        com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget.ASCII2D_URL else com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget.GOOGLE_URL)
            }
        } else {
            LaunchedEffect(url) { searchUrl = null; onNotice("画像検索は使えません") }
        }
    }
    savePage?.let { page ->
        if (features != null) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides ftutterMediaPalette(colors)
            ) {
                FtutterHtmlSaveDialog(
                    preferences = preferences, save = savePreference,
                    features = features,
                    page = page,
                    boardKey = com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey(board),
                    boardName = board.name,
                    boardUrl = com.valoser.futacha.shared.ui.futaber.futaberCanonicalBoardUrl(board),
                    title = ref.title.ifBlank { page.posts.firstOrNull()?.let { com.valoser.futacha.shared.util.resolveThreadTitle(it, "") }.orEmpty() },
                    onDismiss = { savePage = null }
                )
            }
        } else {
            LaunchedEffect(page) { savePage = null; onNotice("保存機能を初期化できませんでした") }
        }
    }
    // 拡張「画像の長押しメニュー」の画像NG登録（ふたちゃ・ふたばー風と共有のルール）と、似た画像の判定。
    val boardKeyForNg = remember(board) { com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey(board) }
    val tabKeyForNg = remember(board, ref.threadId) { com.valoser.futacha.shared.ui.futaber.futaberCompatTabKey(board, ref.threadId) }
    val imageMenuOn = FtutterExtension.IMAGE_MENU in extensions
    val phashRules = remember(compatNgRules, boardKeyForNg, tabKeyForNg, imageMenuOn) {
        if (!imageMenuOn) emptyList() else compatNgRules.filter {
            it.kind == com.valoser.futacha.shared.compat.CompatNgKind.THREAD_IMAGE_PHASH &&
                it.appliesToThreadImage(boardKeyForNg, tabKeyForNg)
        }
    }
    val sharedPreferences by (mediaServices?.store?.preferences ?: kotlinx.coroutines.flow.flowOf(emptyMap<String, String>()))
        .collectAsState(emptyMap())
    val phashThreshold = sharedPreferences[com.valoser.futacha.shared.ui.futaber.futaberImageNgThresholdKey]?.toIntOrNull()
        ?: com.valoser.futacha.shared.compat.CompatImagePhash.DEFAULT_THRESHOLD
    var imageNgTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    imageNgTarget?.let { (imageUrl, postId) ->
        val registerStore = mediaServices?.store
        if (features != null && registerStore != null) {
            androidx.compose.runtime.CompositionLocalProvider(
                com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides ftutterMediaPalette(colors)
            ) {
                com.valoser.futacha.shared.ui.board.FutachaImageNgRegistration(
                    features, boardKeyForNg, com.valoser.futacha.shared.compat.CompatImageNgSource.THREAD, imageUrl, "No.$postId",
                    { imageNgTarget = null },
                    // 「この板のみ」の時は、このモードの板を共有ストアへ取り込んでから書く（ふたばー風と同じ）。
                    prepareBoardScope = com.valoser.futacha.shared.ui.futaber.futaberCatalogImageNgPreparation(registerStore, listOf(board))
                )
            }
        } else {
            LaunchedEffect(imageUrl) { imageNgTarget = null; onNotice("画像NGは使えません") }
        }
    }
    // 拡張「MHTで保存」：形式を選んで、ふたちゃ・ふたばー風と共有のMHTの保存へ。
    var mhtPage by remember { mutableStateOf<com.valoser.futacha.shared.model.ThreadPage?>(null) }
    var mhtFull by remember { mutableStateOf<Boolean?>(null) }
    val mhtFileSystem = mediaServices?.fileSystem
    mhtPage?.let { page ->
        val full = mhtFull
        if (mhtFileSystem == null) {
            LaunchedEffect(page) { mhtPage = null; onNotice("MHTの保存は使えません") }
        } else if (full == null) {
            FutachaAppLockAwareWindow { androidx.compose.material3.AlertDialog(
                onDismissRequest = { mhtPage = null },
                title = { androidx.compose.material3.Text("MHTで保存") },
                text = {
                    androidx.compose.foundation.layout.Column {
                        androidx.compose.material3.TextButton(onClick = { mhtFull = false }, modifier = Modifier.testTag("ftutter-mht-thumbs")) {
                            androidx.compose.material3.Text("サムネイルのみ")
                        }
                        androidx.compose.material3.TextButton(onClick = { mhtFull = true }, modifier = Modifier.testTag("ftutter-mht-full")) {
                            androidx.compose.material3.Text("本体画像もダウンロードする")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { androidx.compose.material3.TextButton(onClick = { mhtPage = null }) { androidx.compose.material3.Text("キャンセル") } },
                modifier = Modifier.testTag("ftutter-mht-choice")
            ) }
        } else {
            val mhtClient = mediaServices?.httpClient
            val library = remember(mhtFileSystem, mhtClient) {
                com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtLibrary(mhtFileSystem, mhtClient)
            }
            val request = remember(page) {
                com.valoser.futacha.shared.ui.futaber.mht.FutaberMhtSaveRequest(
                    boardKey = boardKeyForNg,
                    boardName = board.name,
                    boardUrl = com.valoser.futacha.shared.ui.futaber.futaberCanonicalBoardUrl(board),
                    threadId = ref.threadId,
                    title = ref.title.ifBlank { page.posts.firstOrNull()?.let { com.valoser.futacha.shared.util.resolveThreadTitle(it, "") }.orEmpty() },
                    threadUrl = ftutterThreadUrl(board, ref.threadId),
                    page = page
                )
            }
            androidx.compose.runtime.CompositionLocalProvider(
                com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette provides ftutterMediaPalette(colors)
            ) {
                com.valoser.futacha.shared.ui.board.FutachaMhtSaveRun(library, request, full) { mhtPage = null; mhtFull = null }
            }
        }
    }
    val entry = remember(history, board.id, ref.threadId) {
        com.valoser.futacha.shared.ui.futaber.findFutaberHistoryEntry(history, board, ref.threadId)
    }
    // 開いた時の履歴（読み位置）。開いている間に書き換わっても、復元には最初の値を使う。
    val openedEntry = remember(board.id, ref.threadId) { entry }
    val threadUrl = remember(board, ref.threadId) { ftutterCanonicalThreadUrl(ftutterThreadUrl(board, ref.threadId)) }
    val currentPreferences by rememberUpdatedState(preferences)
    // 保存は原作の`kokomade`と同じ「読んだ返信の数」（OPを含まない）。画面は「読んだレスの数」（OPを含む）で扱う。
    val storedRead = remember(board.id, ref.threadId) {
        parseFtutterReadCounts(preferences[FtutterKeys.READ_COUNTS])[threadUrl]?.let(::ftutterPostsReadFromStored)
    }
    val selfIds = remember(selfPostMap, board.id, ref.threadId) {
        com.valoser.futacha.shared.ui.board.buildSelfPostIdentifierSet(
            com.valoser.futacha.shared.ui.board.buildPersistedSelfPostIdentifiers(
                selfPostMap,
                com.valoser.futacha.shared.ui.board.buildThreadScopedSelfPostKey(board.id, ref.threadId),
                ref.threadId
            )
        )
    }
    fun launchStore(what: String, block: suspend () -> Unit) {
        scope.launch {
            try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) { com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to $what", error) }
        }
    }
    // 画面を離れる時の書き込み（読み位置）。この画面ごと外れる時（設定を開く・戻る・モードの切り替え）も同じ破棄の中で
    // [scope]が取り消されるので、すぐに始めて取り消されない形で最後まで書く（2026-10-14の監査B：設定から戻ると読み位置が消えた）。
    fun launchLastingStore(what: String, block: suspend () -> Unit) {
        scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                try { block() } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Throwable) { com.valoser.futacha.shared.util.Logger.e("Ftutter", "Failed to $what", error) }
            }
        }
    }
    // スレッドごとに画面を作り直す（監査A2）。同じ位置のまま別のスレッドへ替えると、離れる時の保存が新しいスレッドの
    // ものへ差し替わってから呼ばれ、前のスレッドの読み位置・既読数が保存されなかった。作り直せば、前のスレッドの画面は
    // 前のスレッドの状態と保存先のまま破棄される。
    androidx.compose.runtime.key(board.id, ref.threadId) {
    FtutterThreadScreen(
        board = board,
        ref = ref,
        initialPage = initialPage,
        refreshOnOpen = refreshOnOpen,
        onPageSnapshot = onPageSnapshot,
        repository = repository,
        httpClient = httpClient,
        preferences = preferences,
        design = design,
        history = openedEntry,
        selfIds = selfIds,
        storedReadCount = storedRead,
        savePreference = savePreference,
        onReadCount = { count ->
            // 保存されている記録に当てる（監査M2：スレを切り替えた直後に、前のスレの記録が古い値で上書きされて消えていた）。
            editPreference(FtutterKeys.READ_COUNTS) { stored ->
                val counts = parseFtutterReadCounts(stored)
                val next = ftutterReadCountAfter(counts[threadUrl], ftutterStoredFromPostsRead(count))
                if (counts[threadUrl] == next) null else {
                    // 新しい記録を先頭に置き、古いものから落とす（保存値が際限なく伸びないように）。
                    val updated = linkedMapOf(threadUrl to next).apply { counts.forEach { (k, v) -> if (k != threadUrl) put(k, v) } }
                    encodeFtutterReadCounts(updated.entries.take(FTUTTER_READ_COUNT_LIMIT).associate { it.key to it.value })
                }
            }
        },
        onPageLoaded = { posts ->
            launchStore("update history after load") {
                stateStore.updateHistory { com.valoser.futacha.shared.ui.futaber.futaberHistoryAfterLoad(it, board.id, ref.threadId, posts) }
            }
        },
        onLivePageConfirmed = {
            launchStore("mark the thread alive") {
                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                stateStore.updateHistory { com.valoser.futacha.shared.ui.futaber.futaberHistoryMarkAlive(it, board.id, ref.threadId, now) }
            }
            // 閲覧スレの外部アーカイブ通知（3モードと同じ共有キー・既定ON）。
            launchStore("note the viewed thread") {
                com.valoser.futacha.shared.ui.futaber.futaberNoteViewedThread(store, currentPreferences, board, ref.threadId, onArchiveReportEnqueued)
            }
        },
        onSavePosition = { index, offset, postId, total ->
            launchLastingStore("save reading position") {
                stateStore.updateHistoryScrollPositionImmediately(
                    com.valoser.futacha.shared.state.AppStateHistoryScrollUpdateRequest(
                        threadId = ref.threadId,
                        index = index,
                        offset = offset,
                        postId = postId,
                        boardId = board.id,
                        title = entry?.title ?: ref.title,
                        titleImageUrl = entry?.titleImageUrl ?: ref.thumbnailUrl,
                        boardName = board.name,
                        boardUrl = ftutterThreadUrl(board, ref.threadId),
                        replyCount = total
                    )
                )
            }
        },
        onOpenThreadUrl = onOpenThreadUrl,
        onOpenSettings = onOpenSettings,
        onBack = onBack,
        onNotice = onNotice,
        onOpenDrawer = onOpenDrawer,
        onFallen = onFallen,
        tabOnTop = tabOnTop,
        tabStrip = tabStrip,
        ngRules = ngRules,
        boards = boards,
        onSaveNgRules = onSaveNgRules,
        manualMarks = remember(manualMarks, threadUrl) {
            val markUrl = com.valoser.futacha.shared.compat.manualMarkThreadUrl(threadUrl)
            manualMarks.filter { it.threadUrl == markUrl }.map { it.postNo }.toSet()
        },
        onToggleMark = { postId, marked ->
            val markStore = store
            if (markStore != null) launchStore("toggle a mark") {
                com.valoser.futacha.shared.compat.manualPostMarksDroppedNotice(
                    com.valoser.futacha.shared.compat.setManualPostMark(markStore, threadUrl, postId, marked)
                )?.let(onNotice)
            }
        },
        deleteKey = deleteKey,
        onReply = onReply,
        refreshSignal = refreshSignal,
        gesture = gesture,
        onAppGesture = onAppGesture,
        onSwipeTab = onSwipeTab,
        onEditToolbar = onEditToolbar,
        mediaTools = mediaTools,
        onSearchImage = { searchUrl = it },
        onSaveThread = { savePage = it },
        onSoudaneSent = onSoudaneSent,
        extensions = extensions,
        commonNgWords = if (FtutterExtension.COMMON_NG in extensions) commonNgWords else emptyList(),
        commonNgHeaders = if (FtutterExtension.COMMON_NG in extensions) commonNgHeaders else emptyList(),
        phashRules = phashRules,
        phashThreshold = phashThreshold,
        compatStore = mediaServices?.store,
        onRegisterImageNg = { url, postId -> imageNgTarget = url to postId },
        onSaveMht = { mhtPage = it; mhtFull = null },
        historyStateStore = stateStore,
        reportedPostNgRules = compatNgRules.filter {
            it.kind == com.valoser.futacha.shared.compat.CompatNgKind.THREAD_POST_NO &&
                com.valoser.futacha.shared.compat.compatPostNgScopeBoardKey(it.scopeKey) != null
        },
        aiStateStore = if (FtutterExtension.AI in extensions) stateStore else null,
        threadActions = threadActions
    )
    }
}

/** 既読の件数を覚えておくスレッドの数（原作の履歴の上限200に合わせる）。 */
internal const val FTUTTER_READ_COUNT_LIMIT = 200

/** 下に出す通知。同じ文でも、出すたびに[serial]が増える（監査L7）。 */
internal class FtutterNoticeHolder {
    private var text by mutableStateOf<String?>(null)
    var serial by mutableIntStateOf(0)
        private set

    operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): String? = text

    operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String?) {
        text = value
        if (value != null) serial++
    }
}
