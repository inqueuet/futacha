package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.board.FutachaSharedBoardRepository
import com.valoser.futacha.shared.ui.board.edgeSwipeRefresh
import com.valoser.futacha.shared.ui.board.rememberEdgeSwipeRefreshBinding
import com.valoser.futacha.shared.ui.board.statusCodeOrNull
import com.valoser.futacha.shared.ui.futaber.futaberArchivedThread
import com.valoser.futacha.shared.ui.futaber.futaberCompatThreadUrl
import com.valoser.futacha.shared.ui.futaber.futaberFriendlyLoadError
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.resolveThreadTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.ui.futaber.asFutaberEngine

/** 開いたスレッド。題名・サムネはカタログの値（取得後は本文から決め直す）。 */
internal data class FtutterThreadRef(
    val boardId: String,
    val threadId: String,
    val title: String,
    val thumbnailUrl: String = "",
    val catalogReplyCount: Int = 0
)

/** スレッドURLから番号を取る（`…/res/123.htm`）。 */
internal fun ftutterThreadIdFromUrl(url: String): String? =
    Regex("/res/([0-9]+)\\.htm").find(url)?.groupValues?.get(1)

/** 原作どおり、2chan のスレッドURLならアプリ内で開く。 */
internal fun ftutterIsThreadUrl(url: String): Boolean =
    url.startsWith("http") && url.contains(".2chan.net/") && url.contains("/res/") && url.substringBefore('?').endsWith(".htm")

/**
 * メール欄のタップ：http で始まればそのURL、`fu1234567.jpg` のような あぷ・あぷ小 のファイル名なら、その画像のURL。
 * それ以外は null（何もしない）。
 */
internal fun ftutterMailTarget(mail: String): String? {
    val text = mail.trim()
    if (text.startsWith("http")) return text
    if (!text.startsWith("f")) return null
    val match = Regex("(fu\\d{7,}|f\\d{6,})\\.(jpg|jpeg|png|webp|gif)").findAll(text).lastOrNull() ?: return null
    val name = match.value
    val dir = if (name.startsWith("fu")) "up2" else "up"
    return "https://dec.2chan.net/$dir/src/$name"
}

/** ポップアップ1枚。[items]は（レスの番号, 深さ）。 */
internal data class FtutterPopupLayer(
    val items: List<Pair<Int, Int>>,
    val highlight: Int? = null,
    /** タップした所の上端（ルート座標px）。ポップアップの下端をそこへ合わせる。null は上詰め。 */
    val anchorTop: Float? = null,
    /** 隠したレス（削除）も出す（置換行のタップ、メニューの「削除されたレス」）。 */
    val forceShow: Boolean = false
)

/** レスの描画に要るスレッド全体の情報。 */
// 中身は読み取り専用（作り直す時は新しいもの）。同じものなら行の描き直しを省けるようにする（監査L3）。
@androidx.compose.runtime.Immutable
private class FtutterThreadContext(
    val posts: List<Post>,
    val replies: Map<String, List<Int>>,
    val idCounts: Map<String, FtutterIdCount>,
    val selfIds: Set<String>,
    val settings: FtutterThreadSettings,
    val design: FtutterDesignSettings,
    val query: String,
    val hidden: FtutterHidden = FtutterHidden()
)

/** レスに対する操作（本体とポップアップで共通）。 */
private class FtutterPostActions(
    val onPostTap: (index: Int, anchorTop: Float) -> Unit,
    val onPostLongPress: (index: Int) -> Unit,
    val onRepliesTap: (index: Int, anchorTop: Float) -> Unit,
    val onIdTap: (index: Int, anchorTop: Float) -> Unit,
    val onIdLongPress: (index: Int) -> Unit,
    val onQuoteTap: (index: Int, line: String, anchorTop: Float) -> Unit,
    val onUrlTap: (String) -> Unit,
    val onImageTap: (index: Int) -> Unit,
    val onHiddenTap: (index: Int, anchorTop: Float) -> Unit,
    /** 画像の長押し（拡張「画像の長押しメニュー」がONの時だけ何かする）。 */
    val onImageLongPress: (index: Int) -> Unit = {},
    val onInlineImageTap: (index: Int, url: String) -> Unit = { _, _ -> },
    /** 手動で読み込んだあぷ小の URL（スレ単位。行が画面外へ出て作り直されても読み込み済みのまま。L24）。 */
    val apuLoaded: MutableSet<String> = androidx.compose.runtime.mutableStateSetOf()
)

/**
 * スレ画面より長く生きる、送信と過去ログ取得の受け皿（再監査M3）。スレ画面はスレッドごとに作り直す（監査A2）ので、
 * 画面のスコープで送るとタブを切り替えた時に取り消され、そうだね・del・削除・過去ログ取得の結果が出なかった。
 * アプリのスコープで最後まで送り、送信中の印と取れた過去ログはスレッドごとに残す（戻った画面が拾う）。
 */
@androidx.compose.runtime.Stable
internal class FtutterThreadActions(private val scope: kotlinx.coroutines.CoroutineScope) {
    private val busy = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    private val archivedPages = androidx.compose.runtime.mutableStateMapOf<String, ThreadPage>()
    // 状態の Map は入れた順を持たないので、捨てる順は別に持つ（L133）。
    private val archiveOrder = ArrayDeque<String>()

    fun isBusy(key: String): Boolean = busy[key] == true

    /** [key]の処理が進行中なら何もせず false を返す（呼び出し側が断ったことを知らせる。L16）。 */
    fun run(key: String, block: suspend () -> Unit): Boolean {
        if (busy[key] == true) return false
        busy[key] = true
        scope.launch {
            try { block() } finally { busy.remove(key) }
        }
        return true
    }

    fun archived(thread: String): ThreadPage? = archivedPages[thread]

    fun putArchived(thread: String, page: ThreadPage) {
        archivedPages.remove(thread)
        archiveOrder.remove(thread)
        while (archiveOrder.size >= FTUTTER_HELD_ARCHIVES) archivedPages.remove(archiveOrder.removeFirst())
        archivedPages[thread] = page
        archiveOrder.addLast(thread)
    }
}

private const val FTUTTER_HELD_ARCHIVES = 8

internal const val FTUTTER_REMOTE_BUSY_NOTICE = "別の送信が終わってからもう一度お試しください"
internal const val FTUTTER_REMOTE_BUSY_REPORT_NG_NOTICE = "送信中のため、delとNGの登録は行いませんでした。別の送信が終わってからもう一度お試しください"

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FtutterThreadScreen(
    board: BoardSummary,
    ref: FtutterThreadRef,
    repository: BoardRepository,
    httpClient: io.ktor.client.HttpClient?,
    preferences: Map<String, String>,
    design: FtutterDesignSettings,
    history: ThreadHistoryEntry?,
    selfIds: Set<String>,
    storedReadCount: Int?,
    savePreference: (String, String) -> Unit,
    onReadCount: (Int) -> Unit,
    onPageLoaded: (List<Post>) -> Unit,
    onLivePageConfirmed: () -> Unit,
    onSavePosition: (index: Int, offset: Int, postId: String?, total: Int) -> Unit,
    onOpenThreadUrl: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
    onOpenDrawer: () -> Unit = {},
    /** 取得が404・410だった（落ちた）。履歴の印に使う。 */
    onFallen: () -> Unit = {},
    tabOnTop: Boolean = false,
    tabStrip: @Composable () -> Unit = {},
    /** このモードのNG（`compat.ftutter.NGList`）と、その保存。 */
    ngRules: List<FtutterNgRule> = emptyList(),
    boards: List<BoardSummary> = emptyList(),
    /** 保存できた時だけ true（上限を超えて断った時は、呼ばれた側が理由を出す）。 */
    onSaveNgRules: FtutterNgEdit = { _, _ -> },
    /** 手動のマーク（No.）。自分の書き込みと同じく扱う（原作の marked==1）。 */
    manualMarks: Set<String> = emptySet(),
    onToggleMark: (postId: String, marked: Boolean) -> Unit = { _, _ -> },
    deleteKey: String = "",
    /** 書き込み（[quote]は引用文。null は引用なし）。 */
    onReply: (quote: String?) -> Unit = {},
    /** 書き込みに成功した時などに外から取り直させる。 */
    refreshSignal: Int = 0,
    /** 折れ線ジェスチャーの設定と、タブや板をまたぐ操作（前へ・次へ・タブを閉じる・ドロワーなど）。 */
    gesture: FtutterGestureSettings = FtutterGestureSettings(enabled = false),
    onAppGesture: (FtutterGestureAction) -> Unit = {},
    /** 「ジェスチャーの操作を選ぶ」から選んだ時（実行できなければ理由を知らせる。L17）。 */
    onAppMenuGesture: (FtutterGestureAction) -> Unit = onAppGesture,
    /** ジェスチャーがOFFの時の左右スワイプ（隣のタブへ）。 */
    onSwipeTab: ((forward: Boolean) -> Unit)? = null,
    /** ツールバー編集を開く（並びの保存はアプリ側）。 */
    onEditToolbar: () -> Unit = {},
    /** 画像の保存の道具（無ければ保存のボタンは何もしない）。 */
    mediaTools: FtutterMediaSaveTools? = null,
    onSearchImage: (String) -> Unit = {},
    onSaveThread: (ThreadPage) -> Unit = {},
    /** そうだねを送れた時（統計）。 */
    onSoudaneSent: () -> Unit = {},
    /** 拡張機能（既定はすべてOFF＝原作どおり）。 */
    extensions: FtutterExtensions = FtutterExtensions(),
    /** 拡張「共通のNGを使う」：共有のNGワード・NGネーム等（OFFなら空）。 */
    commonNgWords: List<String> = emptyList(),
    commonNgHeaders: List<String> = emptyList(),
    /** 拡張「画像の長押しメニュー」：似た画像を隠すための共有の画像NG（OFFなら空）と、その照合に使うストア。 */
    phashRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    phashThreshold: Int = com.valoser.futacha.shared.compat.CompatImagePhash.DEFAULT_THRESHOLD,
    compatStore: com.valoser.futacha.shared.compat.CompatibilityStore? = null,
    onRegisterImageNg: (imageUrl: String, postId: String) -> Unit = { _, _ -> },
    /** 拡張「MHTで保存」。 */
    onSaveMht: (ThreadPage) -> Unit = {},
    /** 拡張「AIの要約・荒らし非表示」がONの時だけ渡す（共有のAIスイッチを読む）。 */
    aiStateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    /** 送信と過去ログ取得をこの画面より長く続ける受け皿（無ければこの画面のスコープで作る）。 */
    threadActions: FtutterThreadActions? = null,
    initialPage: ThreadPage? = null,
    refreshOnOpen: Boolean = true,
    onPageSnapshot: (ThreadPage) -> Unit = {},
    historyStateStore: com.valoser.futacha.shared.state.AppStateStore? = null,
    reportedPostNgRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    openingTracker: com.valoser.futacha.shared.ui.board.ThreadOpeningSummaryTracker? = null,
    onReadBoundary: (Int, String) -> Unit = { _, _ -> },
    confirmedFallen: Boolean = ftutterHistoryIsFallen(history)
) {
    val colors = LocalFtutterColors.current
    val settings = remember(preferences) { parseFtutterThreadSettings(preferences) }
    val toolbar = remember(preferences) { parseFtutterThreadToolbar(preferences) }
    val metrics = remember(preferences) { parseFtutterThreadToolbarMetrics(preferences) }
    val scope = rememberCoroutineScope()
    val remoteActions = threadActions ?: remember(scope) { FtutterThreadActions(scope) }
    val actionKey = "${board.id}/${ref.threadId}"
    val clipboard = LocalClipboardManager.current
    val openUrl = rememberFtutterUrlLauncher()

    // Keep the held body as the refresh baseline (and offline fallback), but
    // never let stale history suppress the required fetch below.
    var page by remember(board.id, ref.threadId) { mutableStateOf(initialPage) }
    var liveConfirmed by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var loading by remember(board.id, ref.threadId) { mutableStateOf(true) }
    var errorText by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    var fallen by remember(board.id, ref.threadId) { mutableStateOf(confirmedFallen) }
    LaunchedEffect(confirmedFallen) {
        if (confirmedFallen) fallen = true
    }
    var archived by remember(board.id, ref.threadId) { mutableStateOf(false) }
    val archiveBusy = remoteActions.isBusy("$actionKey:archive")
    var refreshTick by remember(board.id, ref.threadId) { mutableIntStateOf(0) }
    var filter by remember(board.id, ref.threadId) { mutableStateOf(FtutterThreadFilter()) }
    var searching by remember(board.id, ref.threadId) { mutableStateOf(false) }
    // 「ここまで読んだ」の件数。開いた時の記録で決め、保存する時（更新・戻る）だけ動かす（スクロール中は動かさない）。
    var readCount by remember(board.id, ref.threadId) { mutableIntStateOf(storedReadCount ?: 0) }
    var restored by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var highlight by remember(board.id, ref.threadId) { mutableStateOf<Int?>(null) }
    val layers = remember(board.id, ref.threadId) { mutableStateListOf<FtutterPopupLayer>() }
    // The post (by number) whose ID was long-pressed: a position would point at another post once the list changed
    // (re-audit L26).
    var idMenu by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    val listState = remember(board.id, ref.threadId) { LazyListState() }

    val localOpeningTracker = remember(board.id, ref.threadId) {
        com.valoser.futacha.shared.ui.board.ThreadOpeningSummaryTracker(storedReadCount,
            history?.lastViewedSnapshot?.lastPostId?.takeIf { history.lastViewedSnapshot?.postCount == storedReadCount })
    }
    val visitTracker = openingTracker ?: localOpeningTracker
    val loadedPosts = page?.posts.orEmpty()
    val ngChain = remember(preferences) { preferences.ftutterBool(FtutterKeys.NG_CHAIN, false) }
    val matcher = remember(ngRules, board.url) { FtutterNgMatcher(ngRules, board.url) }
    // 拡張：共通のNG・画像NG（似た画像）・AIの荒らし非表示は、このモードのNGと同じく「NG」として隠す。
    val tabKeyForNg = remember(board, ref.threadId) { com.valoser.futacha.shared.ui.futaber.futaberCompatTabKey(board, ref.threadId) }
    var phashHidden by remember(board.id, ref.threadId) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(page, phashRules, phashThreshold, compatStore) {
        val current = page
        if (current == null || phashRules.isEmpty() || httpClient == null || compatStore == null) {
            phashHidden = emptySet()
            return@LaunchedEffect
        }
        val snapshotPosts = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
            current.toCompatThreadSnapshot(tabKeyForNg, 0).posts
        }
        com.valoser.futacha.shared.ui.compat.collectCompatImagePhashHiddenPostNos(httpClient, compatStore, snapshotPosts, phashRules, phashThreshold) {
            phashHidden = it
        }
    }
    val aiHolder = com.valoser.futacha.shared.ui.futaber.rememberFutaberThreadAiHolder(board.id, ref.threadId)
    val reportedPrepared = remember(reportedPostNgRules, tabKeyForNg, board) {
        com.valoser.futacha.shared.ui.futaber.futaberNgPrepare(emptyList(), emptyList(), reportedPostNgRules,
            tabKeyForNg, com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey(board))
    }
    val reportedHidden by androidx.compose.runtime.produceState<Pair<List<Post>, Set<String>>?>(null, loadedPosts, reportedPrepared) {
        value = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
            loadedPosts to com.valoser.futacha.shared.ui.futaber.futaberNgEvaluate(loadedPosts, reportedPrepared)
        }
    }
    val extraHiddenIds = (if (aiStateStore != null) phashHidden + aiHolder.hidden else phashHidden) + reportedHidden?.second.orEmpty()
    // 検索語は、打ち終わってから少し後に効かせる（ふたちゃ・ふたばー風と同じ待ち時間。空にした時はすぐ）。
    val appliedQuery = com.valoser.futacha.shared.ui.futaber.rememberFutaberDebouncedQuery(filter.query)
    val appliedFilter = remember(filter, appliedQuery) { if (filter.query == appliedQuery) filter else filter.copy(query = appliedQuery) }
    // NG（正規表現を含む）・削除の連鎖・行の組み立て・検索は、メインスレッドの外で行う。レスごとのNGの照合はレスとNGが
    // 変わった時だけやり直し、画像NG・AIの結果が少しずつ届く時・検索語や既読が変わる時は、その結果に足すだけにする。
    val work = remember(board.id, ref.threadId) { FtutterThreadWork() }
    val view by androidx.compose.runtime.produceState<FtutterThreadView?>(
        null, loadedPosts, settings, appliedFilter, readCount, matcher, commonNgWords, commonNgHeaders, extraHiddenIds, ngChain
    ) {
        val job = kotlinx.coroutines.currentCoroutineContext()
        value = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
            work.view(loadedPosts, settings, appliedFilter, readCount, matcher, commonNgWords, commonNgHeaders, extraHiddenIds, ngChain) { job.ensureActive() }
        }
    }
    val hidden = view?.hidden ?: FtutterHidden()
    val rows = view?.rows.orEmpty()
    // 行（の中のレスの番号）と読むレスは、同じ組み立ての結果から取る（再監査M4）。取り直しや過去ログでレスが減った直後は、
    // 行の組み立てが追いつくまで古い行が残るので、新しいレスの並びで読むと番号が外れて落ちた（戻る・スワイプ・読み上げ）。
    val posts = view?.posts ?: loadedPosts
    com.valoser.futacha.shared.ui.board.ThreadStatusObservationEffect(
        historyStateStore, board.id, ref.threadId, if (archived) null else page,
        live = liveConfirmed && !archived, gone = fallen
    )
    com.valoser.futacha.shared.ui.board.ThreadOpeningSummaryEffect(
        tabKeyForNg, storedReadCount, loadedPosts, selfIds,
        hiddenIds = loadedPosts.indices.filter { hidden.isHidden(it) }.mapTo(mutableSetOf()) { loadedPosts[it].id },
        enabled = FtutterExtension.OPEN_SUMMARY in extensions,
        live = (liveConfirmed || (initialPage != null && !refreshOnOpen)) && !loading && !archived && !fallen && view?.posts === loadedPosts &&
            reportedHidden?.first === loadedPosts,
        truncated = page?.isTruncated == true, onNotice = onNotice, openingTracker = visitTracker
    )
    com.valoser.futacha.shared.ui.board.ThreadViewedObservationEffect(
        historyStateStore, board.id, ref.threadId, page,
        live = (liveConfirmed || (initialPage != null && !refreshOnOpen)) && !loading && !archived && !fallen
    )
    // The popups hold positions in the list: kept while new posts are only added at the end, closed when the list was
    // replaced by another (an archive, a shorter page), where the same positions are other posts (re-audit L26).
    var popupPosts by remember(board.id, ref.threadId) { mutableStateOf<List<Post>?>(null) }
    LaunchedEffect(posts) {
        val before = popupPosts
        if (before != null && layers.isNotEmpty() && !ftutterPostsOnlyGrew(before, posts)) layers.clear()
        popupPosts = posts
    }
    // 照合に時間がかかりすぎて使わなかったNGの正規表現は、一度だけ知らせる（設定のNGの一覧にも出る）。
    var slowNgNoticed by remember(matcher) { mutableStateOf(emptySet<FtutterNgRule>()) }
    LaunchedEffect(view?.slowNgRules) {
        val fresh = view?.slowNgRules.orEmpty().filter { it !in slowNgNoticed }
        if (fresh.isNotEmpty()) {
            slowNgNoticed = slowNgNoticed + fresh
            ftutterSlowNgNotice(fresh)?.let(onNotice)
        }
    }
    // 自分の書き込みと手動のマークは、どちらも原作の marked==1（左の線・「自分のレス」・書込の絞り込み）。
    val markedIds = remember(selfIds, manualMarks) { selfIds + manualMarks }
    // 返信の対応とIDの件数は、レスが変わった時だけ作る（検索語では作り直さない）。
    val replyIndex = remember(posts) { ftutterReplyIndex(posts) }
    val idCounts = remember(posts) { ftutterIdCounts(posts) }
    val context = remember(posts, replyIndex, idCounts, markedIds, settings, design, appliedQuery, hidden) {
        FtutterThreadContext(
            posts = posts,
            replies = replyIndex,
            idCounts = idCounts,
            selfIds = markedIds,
            settings = settings,
            design = design,
            query = appliedQuery,
            hidden = hidden
        )
    }

    // 読み位置と既読を保存する（原作の storeKKE：更新・戻る・タブの切替の時だけ）。
    val currentRows by rememberUpdatedState(rows)
    val currentPosts by rememberUpdatedState(posts)
    fun lastSeenPostIndex(): Int? {
        val info = listState.layoutInfo
        val bottom = info.viewportEndOffset
        return info.visibleItemsInfo.filter { it.offset < bottom }.mapNotNull { item ->
            (currentRows.getOrNull(item.index) as? FtutterRow.PostRow)?.index
                ?: (currentRows.getOrNull(item.index) as? FtutterRow.HiddenRow)?.index
        }.maxOrNull()
    }
    fun storePosition() {
        val all = currentPosts
        if (all.isEmpty() || !restored) return
        val first = listState.firstVisibleItemIndex
        val postIndex = currentRows.drop(first).firstNotNullOfOrNull {
            (it as? FtutterRow.PostRow)?.index ?: (it as? FtutterRow.HiddenRow)?.index
        }
        onSavePosition(first, listState.firstVisibleItemScrollOffset, postIndex?.let { all.getOrNull(it)?.id }, all.size)
        lastSeenPostIndex()?.let { seen ->
            val next = ftutterReadCountAfter(readCount, seen + 1)
            if (next != readCount) {
                readCount = next
            }
            onReadCount(next)
            if (next == seen + 1) all.getOrNull(seen)?.id?.let { onReadBoundary(next, it) }
        }
    }
    val storeOnLeave by rememberUpdatedState(::storePosition)
    DisposableEffect(ref.threadId) { onDispose { storeOnLeave() } }

    val initialRefreshSignal = remember(board.id, ref.threadId) { refreshSignal }
    LaunchedEffect(board.id, ref.threadId, refreshTick, refreshSignal) {
        val held = page
        if (held != null && !ftutterThreadPageIsStale(held, history) && !refreshOnOpen &&
            refreshTick == 0 && refreshSignal == initialRefreshSignal) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val before = page?.posts.orEmpty()
        val openingFetch = !visitTracker.isCompleted && FtutterExtension.OPEN_SUMMARY in extensions &&
            storedReadCount != null && storedReadCount > 0
        try {
            val fetched = repository.getThreadContent(board.url, ref.threadId)
            val content = (repository as? FutachaSharedBoardRepository)?.let { shared ->
                try {
                    shared.supplement(futaberCompatThreadUrl(board, ref.threadId), fetched)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    fetched
                }
            } ?: fetched
            if (!archived) {
                liveConfirmed = true
                fallen = false
                page = content.page
                onPageSnapshot(content.page)
                errorText = null
                onPageLoaded(content.page.posts)
                onLivePageConfirmed()
                if (before.isNotEmpty() && !openingFetch) {
                    ftutterRefreshNotice(before, content.page.posts, selfIds, settings)?.let(onNotice)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            liveConfirmed = false
            val status = error.statusCodeOrNull()
            if (status == 404 || status == 410) {
                // 落ち：手元のデータは残し、末尾の帯と「過去ログを取得」で知らせる（原作は404だけ、ふたちゃどおり410も）。
                fallen = true
                onFallen()
                if (page == null) errorText = "スレッドは落ちています"
                onNotice("スレッドは落ちています")
            } else {
                val message = futaberFriendlyLoadError(error, "スレッド")
                if (page == null) errorText = message
                onNotice(message)
            }
        } finally {
            loading = false
        }
    }

    // 開いた時の位置：前回の読み位置（レス番号で探し、無ければ行の番号）。
    LaunchedEffect(ref.threadId, rows) {
        if (restored || rows.isEmpty() || posts.isEmpty()) return@LaunchedEffect
        val postId = history?.lastReadPostId
        val target = postId?.let { id ->
            val index = posts.indexOfFirst { it.id == id }
            rows.indexOfFirst { (it as? FtutterRow.PostRow)?.index == index || (it as? FtutterRow.HiddenRow)?.index == index }
                .takeIf { index >= 0 && it >= 0 }
        } ?: history?.lastReadItemIndex?.takeIf { it > 0 }?.coerceAtMost(rows.lastIndex)
        if (target != null) listState.scrollToItem(target, history?.lastReadItemOffset ?: 0)
        restored = true
    }

    fun jumpAndHighlight(index: Int) {
        val row = rows.indexOfFirst { (it as? FtutterRow.PostRow)?.index == index || (it as? FtutterRow.HiddenRow)?.index == index }
        if (row < 0) return
        scope.launch {
            listState.scrollToItem(row)
            highlight = index
            kotlinx.coroutines.delay(1_000)
            if (highlight == index) highlight = null
        }
    }
    fun refresh(fromBottom: Boolean) {
        if (fromBottom) {
            // 下へ引っ張る更新は、既読を最後まで進めてから取る（原作の saveKokomadeLast）。
            readCount = maxOf(readCount, posts.size)
            onReadCount(readCount)
            storePosition()
        } else {
            storePosition()
        }
        refreshTick++
    }
    fun pushLayer(layer: FtutterPopupLayer) {
        if (layer.items.isNotEmpty()) layers.add(layer)
    }
    val selfIndexes = remember(posts, markedIds) { posts.indices.filter { posts[it].id in markedIds } }
    // 長押ししたレスはNo.で持ち、表示のたびに今の位置を引く（監査M3：位置で持つと、更新でレスが減った・途中に入った時に
    // 落ちる、または別のレスを指していた）。メニューから開くダイアログは、選んだ時のレスそのものを持つ。
    var menuPost by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    // 画像一覧・ビューア（スレッドの上に全画面で重ねる）。ビューアを一覧から開いた時は、閉じると一覧へ戻る。
    var galleryOpen by remember(board.id, ref.threadId) { mutableStateOf(false) }
    var viewer by remember(board.id, ref.threadId) { mutableStateOf<Pair<List<com.valoser.futacha.shared.compat.CompatPostSnapshot>, Int>?>(null) }
    val mediaSettings = remember(preferences) { parseFtutterMediaSettings(preferences) }
    val mediaHiddenIds = remember(posts, hidden) {
        hidden.ngIncludingChained.mapNotNullTo(HashSet()) { posts.getOrNull(it)?.id }
    }
    val galleryMemo = remember(board.id, ref.threadId) {
        com.valoser.futacha.shared.ui.OffMainMemo<List<Any?>, List<com.valoser.futacha.shared.compat.CompatPostSnapshot>?>()
    }
    var ngDraft by remember(board.id, ref.threadId) { mutableStateOf<FtutterNgRule?>(null) }
    var ngKindFor by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    var copyFor by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    var chooseQuoteFor by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    var delConfirm by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    var deleteFor by remember(board.id, ref.threadId) { mutableStateOf<Post?>(null) }
    var reorderMenu by remember(board.id, ref.threadId) { mutableStateOf(false) }
    val postMenu = remember(preferences) { parseFtutterPostMenu(preferences) }
    // そうだね・del・削除は、ふたちゃと共有の通信（requireWritableThreadBoard・repository）で送る。
    // 送信は画面より長く続ける（再監査M3）。結果の知らせはアプリ側、取り直しは開いている画面だけ。
    val currentRefresh by rememberUpdatedState { refreshTick++ }
    fun runRemote(label: String, success: String, refreshAfter: Boolean, resultMessage: () -> String = { success },
        busyMessage: String = FTUTTER_REMOTE_BUSY_NOTICE, block: suspend () -> Unit) {
        // 別の送信の途中に押した時は、送らなかったことを知らせる（L16。以前は何も起きず知らせも無かった）。
        val started = remoteActions.run("$actionKey:remote") {
            val result = com.valoser.futacha.shared.ui.board.performThreadAction {
                com.valoser.futacha.shared.ui.board.requireWritableThreadBoard(board.url)
                block()
            }
            when (result) {
                is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Success -> {
                    onNotice(resultMessage())
                    if (refreshAfter) currentRefresh()
                }
                is com.valoser.futacha.shared.ui.board.ThreadActionRunResult.Failure ->
                    onNotice(com.valoser.futacha.shared.ui.board.buildThreadActionFailureMessage(label, result.error))
            }
        }
        if (!started) onNotice(busyMessage)
    }
    // 拡張「画像の長押しメニュー」：長押しした画像のレス。
    var imageMenuFor by remember(board.id, ref.threadId) { mutableStateOf<String?>(null) }
    val imageMenuOn by rememberUpdatedState(FtutterExtension.IMAGE_MENU in extensions)
    val showMyself: (Float?) -> Unit = { anchor ->
        pushLayer(FtutterPopupLayer(selfIndexes.map { it to 0 }, anchorTop = anchor, forceShow = true))
    }
    val apuLoaded = remember(board.id, ref.threadId) { androidx.compose.runtime.mutableStateSetOf<String>() }
    val actions = remember(posts, context, mediaSettings, mediaHiddenIds, apuLoaded) {
        FtutterPostActions(
            apuLoaded = apuLoaded,
            onPostTap = { index, anchor ->
                scope.launch {
                    val tree = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        ftutterConversationOf(posts, index)
                    }
                    if (tree.size >= 2) pushLayer(FtutterPopupLayer(tree, highlight = index, anchorTop = anchor))
                }
            },
            onPostLongPress = { index -> menuPost = posts.getOrNull(index)?.id },
            onRepliesTap = { index, anchor ->
                pushLayer(FtutterPopupLayer(context.replies[posts[index].id].orEmpty().map { it to 0 }, anchorTop = anchor))
            },
            onIdTap = { index, anchor ->
                val identity = context.idCounts[posts[index].id]
                if (identity != null) {
                    pushLayer(FtutterPopupLayer(
                        posts.indices.filter { context.idCounts[posts[it].id]?.let { id -> id.kind == identity.kind && id.id == identity.id } == true }.map { it to 0 },
                        highlight = index, anchorTop = anchor
                    ))
                }
            },
            onIdLongPress = { index -> idMenu = posts.getOrNull(index)?.id },
            onQuoteTap = { index, line, anchor ->
                scope.launch {
                    val targets = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        if (line.trim() == ">....") ftutterQuoteTargets(posts, index)
                        else ftutterQuoteLineTargets(posts, index, line)
                    }
                    pushLayer(FtutterPopupLayer(targets.map { it to 0 }, anchorTop = anchor))
                }
            },
            onUrlTap = { url -> if (ftutterIsThreadUrl(url)) onOpenThreadUrl(url) else openUrl(url) },
            onImageTap = { index ->
                // 「スレ内の画像を全て繋げて表示する」（既定ON）なら全画像、OFFならそのレスだけ。削除レスの画像は除くが、開いた画像は残す。
                // 全レスの変換は解析用のスレッドで行う（再監査M23：長いスレではタップのたびにメインで止まった）。
                val post = posts.getOrNull(index)
                if (post != null) {
                    val tappedPosts = posts
                    val excludeDeleted = mediaSettings.excludeDeleted
                    val connectAll = mediaSettings.connectAll
                    scope.launch {
                        val media = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                            ftutterMediaPosts(tappedPosts, excludeDeleted, keepPostId = post.id, onlyPostId = if (connectAll) null else post.id)
                        }
                        val at = media.indexOfFirst { it.position == index && it.postNo !in mediaHiddenIds }
                        if (at >= 0) viewer = media to at
                    }
                }
            },
            onHiddenTap = { index, anchor -> pushLayer(FtutterPopupLayer(listOf(index to 0), anchorTop = anchor, forceShow = true)) },
            onImageLongPress = { index -> if (imageMenuOn) imageMenuFor = posts.getOrNull(index)?.id },
            onInlineImageTap = { index, url ->
                val post = posts.getOrNull(index)
                if (post != null) scope.launch {
                    val media = kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) {
                        ftutterMediaPosts(posts, mediaSettings.excludeDeleted, keepPostId = post.id,
                            onlyPostId = if (mediaSettings.connectAll) null else post.id)
                    }
                    val at = media.indexOfFirst { it.postNo !in mediaHiddenIds && com.valoser.futacha.shared.ui.compat.resolveCompatViewerMediaUrl(it) == url }
                    if (at >= 0) viewer = media to at
                }
            }
        )
    }

    // ---- 拡張：読み上げ・オートスクロール・AI ----
    val speechRuntime = com.valoser.futacha.shared.audio.LocalSpeechRuntime.current
    val platformContext = coil3.compose.LocalPlatformContext.current
    val liveSpeechSettings = rememberUpdatedState(com.valoser.futacha.shared.audio.speechSettingsFromPreferences(preferences))
    val reader = remember(board.id, ref.threadId) {
        com.valoser.futacha.shared.ui.futaber.FutaberReadAloud(scope, {
            com.valoser.futacha.shared.audio.createTextSpeaker(platformContext, speechRuntime).asFutaberEngine { liveSpeechSettings.value }
        })
    }
    DisposableEffect(reader) { onDispose { reader.dispose() } }
    com.valoser.futacha.shared.ui.futaber.FutaberReadAloudAppLockEffect(reader)
    LaunchedEffect(FtutterExtension.READ_ALOUD in extensions) { if (FtutterExtension.READ_ALOUD !in extensions) reader.stop() }
    reader.message?.let { message -> LaunchedEffect(message) { onNotice(message); reader.clearMessage() } }
    var autoScrolling by remember(board.id, ref.threadId) { mutableStateOf(false) }
    LaunchedEffect(FtutterExtension.AUTO_SCROLL in extensions) { if (FtutterExtension.AUTO_SCROLL !in extensions) autoScrolling = false }
    val (autoScrollPixel, autoScrollInterval) = remember(preferences) { ftutterAutoScrollSettings(preferences) }
    fun startReading() {
        val listed = ftutterListedPostIndexes(rows)
        val readPosts = listed.mapNotNull { posts.getOrNull(it) }
        reader.start(readPosts, ftutterListedBefore(rows, listState.firstVisibleItemIndex)) { i ->
            val postIndex = listed.getOrNull(i) ?: return@start
            val row = currentRows.indexOfFirst { (it as? FtutterRow.PostRow)?.index == postIndex }
            if (row >= 0) listState.animateScrollToItem(row)
        }
    }

    // 画面の中で済む操作はここで、タブや板をまたぐ操作はアプリへ（原作 SurePage の割り当て先）。
    fun handleGesture(action: FtutterGestureAction, fromMenu: Boolean = false) {
        when (action) {
            FtutterGestureAction.TOP -> scope.launch { listState.scrollToItem(0) }
            FtutterGestureAction.BOTTOM -> scope.launch { listState.scrollToItem((rows.size + 1).coerceAtLeast(0)) }
            FtutterGestureAction.REFRESH -> refresh(fromBottom = false)
            FtutterGestureAction.EDIT -> onReply(null)
            FtutterGestureAction.SEARCH -> searching = true
            FtutterGestureAction.TREE -> savePreference(FtutterKeys.IS_TREE, (!settings.isTree).toString())
            FtutterGestureAction.POPULAR -> filter = filter.copy(popular = !filter.popular)
            FtutterGestureAction.IMAGES -> filter = filter.copy(imagesOnly = !filter.imagesOnly)
            FtutterGestureAction.NONE -> Unit
            else -> { storePosition(); if (fromMenu) onAppMenuGesture(action) else onAppGesture(action) }
        }
    }
    PlatformBackHandler(enabled = layers.isNotEmpty()) { layers.removeAt(layers.lastIndex) }
    PlatformBackHandler(enabled = layers.isEmpty() && searching) { searching = false; filter = filter.copy(query = "") }
    PlatformBackHandler(enabled = layers.isEmpty() && !searching) { storePosition(); onBack() }

    val title = remember(posts, ref.title) { posts.firstOrNull()?.let { resolveThreadTitle(it, ref.title) } ?: ref.title }
    val endBand = ftutterEndBand(page?.expiresAtLabel, fallen, archived)
    val canFetchArchive = fallen && !archived && httpClient != null
    // 過去ログは画面より長く取り続け、取れたものはスレッドごとに残す（再監査M3）。開いている画面（戻ってきた画面を含む）が拾う。
    fun fetchArchive() {
        val client = httpClient ?: return
        remoteActions.run("$actionKey:archive") {
            val got = futaberArchivedThread(client, repository, ref.threadId, board.url, ftutterArchiveJson)
            if (got != null) remoteActions.putArchived(actionKey, got) else onNotice("過去ログが見つかりませんでした")
        }
    }
    val heldArchive = remoteActions.archived(actionKey)
    LaunchedEffect(heldArchive) {
        if (heldArchive != null && page !== heldArchive) {
            page = heldArchive
            archived = true
            errorText = null
            onPageLoaded(heldArchive.posts)
        }
    }

    // 拡張：AIの要約・荒らし非表示（共有のAIスイッチがONの時だけ動く）。
    if (aiStateStore != null) {
        val aiOrdered = remember(rows, posts) { ftutterListedPostIndexes(rows).mapNotNull { posts.getOrNull(it)?.id } }
        val aiKeys = remember(rows, posts) { ftutterAiItemKeys(rows, posts) }
        com.valoser.futacha.shared.ui.futaber.FutaberThreadAiByIds(
            aiHolder, aiStateStore, page, tabKeyForNg, title, markedIds, listState, aiOrdered, aiKeys
        )
    }
    // 拡張：オートスクロール（他モードと同じ処理。ポップアップ・画像の表示中は進めない）。
    com.valoser.futacha.shared.ui.futaber.FutaberAutoScrollEffect(
        active = autoScrolling && layers.isEmpty() && !galleryOpen && viewer == null,
        pixel = autoScrollPixel,
        intervalMillis = autoScrollInterval,
        isDead = fallen || archived,
        listState = listState,
        onReload = { refreshTick++ },
        onStop = { autoScrolling = false }
    )

    Box(Modifier.fillMaxSize().background(colors.background).testTag("ftutter-thread")) {
        Column(Modifier.fillMaxSize()) {
            if (searching) {
                FtutterSearchBar(
                    query = filter.query,
                    onQueryChange = { filter = filter.copy(query = it) },
                    onClose = { searching = false; filter = filter.copy(query = "") }
                )
            } else {
                FtutterTopBar(
                    title = title, onBack = { storePosition(); onBack() },
                    onOpenDrawer = if (preferences.ftutterBool(FtutterKeys.DRAWER_BUTTON_TOP_LEFT, false)) onOpenDrawer else null
                ) {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("ftutter-thread-settings")) {
                        Icon(Icons.Rounded.Settings, contentDescription = "設定", tint = colors.onToolBar)
                    }
                }
            }
            if (tabOnTop) tabStrip()
            if (aiStateStore != null) com.valoser.futacha.shared.ui.futaber.FutaberThreadAiPanel(aiHolder)
            FtutterGestureLayer(
                settings = gesture,
                enabled = layers.isEmpty(),
                onAction = { action -> handleGesture(action) },
                onSwipe = onSwipeTab,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                val current = page
                if (current == null) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (loading) CircularProgressIndicator(color = colors.indicator)
                        errorText?.let {
                            Text(it, color = colors.text, modifier = Modifier.padding(16.dp).testTag("ftutter-thread-error"))
                            TextButton(onClick = { refreshTick++ }) { Text("再読み込み") }
                        }
                        if (canFetchArchive) FtutterArchiveButton(archiveBusy, ::fetchArchive)
                    }
                } else {
                    val binding = rememberEdgeSwipeRefreshBinding(listState, loading, "ftutterThread")
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                            .offset { IntOffset(0, binding.visualState.overscrollOffset.value.toInt()) }
                            .edgeSwipeRefresh(
                                isRefreshing = loading,
                                isAtTop = { settings.pullTop && binding.edgeState.isAtTop() },
                                isAtBottom = { settings.pullBottom && binding.edgeState.isAtBottom() },
                                maxOverscrollPx = binding.metrics.maxOverscrollPx,
                                refreshTriggerPx = binding.metrics.refreshTriggerPx,
                                onOverscrollTargetChanged = binding.visualState::onOverscrollTargetChanged,
                                onRefresh = { refresh(fromBottom = binding.edgeState.isAtBottom() && !binding.edgeState.isAtTop()) }
                            )
                            .testTag("ftutter-thread-list")
                    ) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                FtutterRow.ReadMark -> FtutterBandCard("ここまで読んだ", colors.dividerCard, colors.onDividerCard,
                                    Modifier.testTag("ftutter-kokomade")) { showMyself(null) }
                                is FtutterRow.PostRow -> FtutterPostView(
                                    index = row.index, depth = row.depth, context = context, actions = actions,
                                    highlighted = highlight == row.index, inPopup = false
                                )
                                is FtutterRow.HiddenRow -> FtutterHiddenRowView(row.index, row.depth, design, row.isNg) { anchor ->
                                    actions.onHiddenTap(row.index, anchor)
                                }
                            }
                        }
                        if (canFetchArchive) item(key = "archive") {
                            Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                                FtutterArchiveButton(archiveBusy, ::fetchArchive)
                            }
                        }
                        item(key = "end") {
                            val (bg, fg) = when (endBand.state) {
                                FtutterEndState.ARCHIVED -> Color(0xFF69F0AE) to Color(0xFF000000)
                                FtutterEndState.DANGER -> Color(0xFFF44336) to Color(0xFFFFFFFF)
                                FtutterEndState.NORMAL -> colors.dividerCard to colors.onDividerCard
                            }
                            FtutterBandCard(endBand.text, bg, fg, Modifier.testTag("ftutter-end-band")) { showMyself(null) }
                        }
                    }
                }
                FtutterScrollbar(settings.scrollbar, listState)
                // 拡張：読み上げ中・オートスクロール中の帯。
                if (reader.isReading) {
                    FtutterRunningChip(
                        "読み上げ中", "ftutter-reading",
                        listOf("前へ" to { reader.skip(-1) }, "次へ" to { reader.skip(1) }, "停止" to { reader.stop() }),
                        Modifier.align(Alignment.BottomCenter)
                    )
                } else if (autoScrolling) {
                    FtutterRunningChip("オートスクロール中", "ftutter-auto-scroll", listOf("停止" to { autoScrolling = false }),
                        Modifier.align(Alignment.BottomCenter))
                }
            }
            if (!tabOnTop) tabStrip()
            FtutterThreadToolbar(
                entries = toolbar,
                metrics = metrics,
                settings = settings,
                filter = filter,
                deletedCount = hidden.deleted.size,
                ngCount = hidden.ng.size,
                selfCount = selfIndexes.size,
                extensions = extensions,
                reading = reader.isReading,
                autoScrolling = autoScrolling,
                onGesture = { handleGesture(it, fromMenu = true) },
                onTool = { tool ->
                    when (tool) {
                        FtutterThreadTool.DRAWER -> onOpenDrawer()
                        FtutterThreadTool.TREE -> savePreference(FtutterKeys.IS_TREE, (!settings.isTree).toString())
                        FtutterThreadTool.POPULAR -> filter = filter.copy(popular = !filter.popular)
                        FtutterThreadTool.IMAGE_POSTS -> filter = filter.copy(imagesOnly = !filter.imagesOnly)
                        FtutterThreadTool.TAB -> savePreference(FtutterKeys.SHOW_TAB, (!settings.showTab).toString())
                        FtutterThreadTool.PRIVACY -> savePreference(FtutterKeys.IS_PRIVACY, (!settings.isPrivacy).toString())
                        FtutterThreadTool.UP -> scope.launch { listState.scrollToItem(0) }
                        FtutterThreadTool.DOWN -> scope.launch { listState.scrollToItem((rows.size + 1).coerceAtLeast(0)) }
                        FtutterThreadTool.GALLERY -> galleryOpen = true
                        FtutterThreadTool.REPLY -> onReply(null)
                        FtutterThreadTool.SEARCH -> searching = !searching
                        FtutterThreadTool.REFRESH -> refresh(fromBottom = false)
                        else -> Unit
                    }
                },
                onImagePostsLongPress = { galleryOpen = true },
                onMenu = { item ->
                    when (item) {
                        FtutterThreadMenuItem.DELETED -> pushLayer(FtutterPopupLayer(hidden.deleted.sorted().map { it to 0 }, forceShow = true))
                        FtutterThreadMenuItem.NG -> pushLayer(FtutterPopupLayer(hidden.ng.sorted().map { it to 0 }, forceShow = true))
                        FtutterThreadMenuItem.MINE -> showMyself(null)
                        FtutterThreadMenuItem.TOOLBAR_EDIT -> onEditToolbar()
                        FtutterThreadMenuItem.PRIVACY -> savePreference(FtutterKeys.IS_PRIVACY, (!settings.isPrivacy).toString())
                        FtutterThreadMenuItem.SAVE -> page?.let(onSaveThread) ?: onNotice("スレが未取得です")
                        FtutterThreadMenuItem.COPY_URL -> {
                            clipboard.setText(AnnotatedString(ftutterThreadUrl(board, ref.threadId)))
                            onNotice("URLをコピーしました")
                        }
                        FtutterThreadMenuItem.BROWSER -> openUrl(ftutterThreadUrl(board, ref.threadId))
                        FtutterThreadMenuItem.READ_ALOUD -> if (reader.isReading) reader.stop() else { autoScrolling = false; startReading() }
                        FtutterThreadMenuItem.AUTO_SCROLL -> { if (!autoScrolling) reader.stop(); autoScrolling = !autoScrolling }
                        FtutterThreadMenuItem.MHT -> page?.let(onSaveMht) ?: onNotice("スレが未取得です")
                    }
                }
            )
        }
        if (layers.isNotEmpty()) {
            // ポップアップの中のジェスチャー（原作 TreeDialog2）：前へ・戻る・次へは一番上の1枚を閉じ、一番上へ・一番下へは
            // その1枚の中を動かす。
            val popupScroll = remember { arrayOfNulls<LazyListState>(1) }
            FtutterGestureLayer(
                settings = gesture,
                enabled = true,
                onAction = { action ->
                    when (action) {
                        FtutterGestureAction.PREVIOUS, FtutterGestureAction.BACK, FtutterGestureAction.NEXT ->
                            if (layers.isNotEmpty()) layers.removeAt(layers.lastIndex)
                        FtutterGestureAction.TOP -> scope.launch { popupScroll[0]?.scrollToItem(0) }
                        FtutterGestureAction.BOTTOM -> scope.launch { popupScroll[0]?.let { it.scrollToItem((it.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } }
                        else -> Unit
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                FtutterPopupStack(
                    layers = layers,
                    context = context,
                    actions = actions,
                    toolbarHeightDp = metrics.iconHeight,
                    onDismissTop = { if (layers.isNotEmpty()) layers.removeAt(layers.lastIndex) },
                    onTopListState = { popupScroll[0] = it }
                )
            }
        }
        val tools = mediaTools ?: FtutterMediaSaveTools("既定の保存先", {}, {}, { _, _ -> FtutterBatchSaveResult(0, 0) }, false)
        val mediaAlpha = if (settings.isPrivacy) (1.0 - design.privacyThreadZoom).toFloat() else 1f
        // 番号カードのタップ：一覧・ビューアを閉じて、スレのそのレスへ移って強調する。
        val backToPost: (Int) -> Unit = { position ->
            viewer = null
            galleryOpen = false
            layers.clear()
            jumpAndHighlight(position)
        }
        if (galleryOpen) {
            // 解析用のスレッドで作り、スレの画面の間は持っておく（再監査M23）。できるまでは一覧を出さない。
            val heldGalleryMedia = com.valoser.futacha.shared.ui.rememberOffMain<List<Any?>, List<com.valoser.futacha.shared.compat.CompatPostSnapshot>?>(
                galleryMemo, listOf(com.valoser.futacha.shared.ui.SameInstance(posts), mediaSettings.excludeDeleted), null
            ) { _, _ -> ftutterMediaPosts(posts, mediaSettings.excludeDeleted) }
            // Keep the conversion cached independently of NG, but never show that raw snapshot.
            // Filtering here also applies late image/AI results immediately, and restores
            // media on unhide without losing the gallery selection or rebuilding every post.
            val galleryMedia = remember(heldGalleryMedia, mediaHiddenIds) {
                heldGalleryMedia?.let { ftutterVisibleMedia(it, mediaHiddenIds) }
            }
            if (galleryMedia != null) FtutterGalleryScreen(
                gesture = gesture,
                onRefresh = { refresh(fromBottom = false) },
                media = galleryMedia,
                columns = mediaSettings.galleryColumns,
                settings = mediaSettings,
                privacyAlpha = if (settings.isPrivacy) (1.0 - design.privacyThread).toFloat() else 1f,
                tools = tools,
                onColumns = { savePreference(FtutterKeys.GALLERY_COLUMNS, it.toString()) },
                // 一覧から開くビューアは、設定に関係なくスレ全体（原作）。
                onOpenViewer = { index ->
                    val base = heldGalleryMedia.orEmpty()
                    val originalIndex = galleryMedia.getOrNull(index)?.let(base::indexOf) ?: -1
                    if (originalIndex >= 0) viewer = base to originalIndex
                },
                onBackToPost = backToPost,
                onClose = { galleryOpen = false }
            )
        }
        viewer?.let { (media, index) ->
            val visibleMedia = remember(media, mediaHiddenIds) { ftutterVisibleMedia(media, mediaHiddenIds) }
            if (visibleMedia.isEmpty()) {
                LaunchedEffect(media, mediaHiddenIds) { viewer = null }
            } else FtutterViewerScreen(
                media = visibleMedia,
                initialIndex = media.getOrNull(index)?.let(visibleMedia::indexOf)?.coerceAtLeast(0) ?: 0,
                settings = mediaSettings,
                privacyAlpha = mediaAlpha,
                tools = tools,
                posts = posts,
                conversationOf = { position ->
                    kotlinx.coroutines.withContext(com.valoser.futacha.shared.util.AppDispatchers.parsing) { ftutterConversationOf(posts, position) }
                },
                onToggleStrip = { savePreference(FtutterKeys.SHOW_GALLERY_TAB, (!mediaSettings.showStrip).toString()) },
                onSearch = onSearchImage,
                onOpenBrowser = openUrl,
                onBackToPost = backToPost,
                onClose = { viewer = null }
            )
        }
    }

    idMenu?.let { postId ->
        val index = ftutterIndexOfPost(posts, postId)
        val id = context.idCounts[posts.getOrNull(index)?.id]?.id
        if (id == null) {
            idMenu = null
        } else {
            FutachaAppLockAwareWindow { AlertDialog(
                onDismissRequest = { idMenu = null },
                title = { Text(id) },
                text = {
                    Column {
                        Text("コピー", modifier = Modifier.fillMaxWidth().clickable {
                            idMenu = null
                            clipboard.setText(AnnotatedString(id))
                            onNotice("$id\nをコピーしました")
                        }.padding(vertical = 12.dp).testTag("ftutter-id-copy"))
                        Text("NG", modifier = Modifier.fillMaxWidth().clickable {
                            idMenu = null
                            ngDraft = FtutterNgRule(ftutterPosterIdentity(posts[index])?.display.orEmpty(), FtutterNgType.ID.stored, ita = FtutterNgBoard(board.name, board.url))
                        }.padding(vertical = 12.dp).testTag("ftutter-id-ng"))
                    }
                },
                confirmButton = {}
            ) }
        }
    }

    // ---- レスの長押しメニューと、そこから開くダイアログ ----
    menuPost?.let { postId ->
        val index = ftutterIndexOfPost(posts, postId)
        val post = posts.getOrNull(index)
        if (post == null) {
            LaunchedEffect(postId) { menuPost = null }
        } else {
            val screenHeight = with(LocalDensity.current) {
                androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp().value
            }
            FtutterPostMenuDialog(
                header = { FtutterPostView(index, 0, context.copyForMenu(), FTUTTER_INERT_ACTIONS, highlighted = false, inPopup = true) },
                entries = postMenu,
                marked = post.id in manualMarks,
                maxPreviewHeightDp = (screenHeight * 0.3f).toInt(),
                onDismiss = { menuPost = null },
                onReorder = { reorderMenu = true },
                onItem = { item ->
                    menuPost = null
                    val lines = com.valoser.futacha.shared.ui.board.messageHtmlToLines(post.messageHtml)
                    when (item) {
                        FtutterPostMenuItem.QUOTE -> onReply(ftutterQuoteLines(lines))
                        FtutterPostMenuItem.QUOTE_NO -> onReply(">No.${post.id}\n")
                        FtutterPostMenuItem.QUOTE_SELECT -> chooseQuoteFor = post
                        // そうだねは原作どおり確認なしで送る。
                        FtutterPostMenuItem.SOUDANE -> runRemote("そうだねに失敗しました", "そうだねしました", refreshAfter = true) {
                            repository.voteSaidane(board.url, ref.threadId, post.id)
                            onSoudaneSent()
                        }
                        FtutterPostMenuItem.DELETE -> deleteFor = post
                        FtutterPostMenuItem.DEL -> delConfirm = post
                        FtutterPostMenuItem.MARK -> onToggleMark(post.id, post.id !in manualMarks)
                        FtutterPostMenuItem.COPY -> copyFor = post
                        FtutterPostMenuItem.NG -> if (ftutterPosterIdentity(post) == null) {
                            ngDraft = FtutterNgRule(lines.joinToString("\n"), FtutterNgType.WORD.stored, ita = FtutterNgBoard(board.name, board.url))
                        } else ngKindFor = post
                    }
                }
            )
        }
    }
    if (reorderMenu) {
        FtutterOrderEditorDialog(
            title = "並び替え",
            entries = postMenu,
            label = { it.label },
            onDismiss = { reorderMenu = false },
            onReset = {
                reorderMenu = false
                savePreference(FtutterKeys.POST_MENU_ORDER, "")
                savePreference(FtutterKeys.POST_MENU_ENABLED, "")
            },
            onSave = { saved ->
                reorderMenu = false
                val (names, flags) = encodeFtutterToolbar(saved) { it.stored }
                savePreference(FtutterKeys.POST_MENU_ORDER, names)
                savePreference(FtutterKeys.POST_MENU_ENABLED, flags)
            }
        )
    }
    chooseQuoteFor?.let { post ->
        val candidates = buildList {
            addAll(ftutterPosterIdentities(post).map { it.display })
            post.imageUrl?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(com.valoser.futacha.shared.ui.board.messageHtmlToLines(post.messageHtml).filter { it.isNotBlank() })
        }
        FtutterChooseQuoteDialog(candidates, onDismiss = { chooseQuoteFor = null }) { quote ->
            chooseQuoteFor = null
            onReply(quote)
        }
    }
    imageMenuFor?.let { postId ->
        val post = posts.getOrNull(ftutterIndexOfPost(posts, postId))
        val imageUrl = post?.let { it.imageUrl?.takeIf(String::isNotBlank) ?: it.thumbnailUrl?.takeIf(String::isNotBlank) }
        if (post == null || imageUrl == null) {
            LaunchedEffect(postId) { imageMenuFor = null }
        } else {
            FutachaAppLockAwareWindow { AlertDialog(
                onDismissRequest = { imageMenuFor = null },
                title = { Text("No.${post.id} の画像") },
                text = {
                    Column {
                        TextButton(onClick = { imageMenuFor = null; onSearchImage(imageUrl) }, modifier = Modifier.testTag("ftutter-image-menu-search")) {
                            Text("画像検索")
                        }
                        TextButton(onClick = { imageMenuFor = null; onRegisterImageNg(imageUrl, post.id) }, modifier = Modifier.testTag("ftutter-image-menu-ng")) {
                            Text("画像NGに登録")
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { imageMenuFor = null }) { Text("キャンセル") } },
                modifier = Modifier.testTag("ftutter-image-menu")
            ) }
        }
    }
    delConfirm?.let { post ->
        com.valoser.futacha.shared.ui.board.ReportPostConfirmation(
            postNo = post.id, offerNg = FtutterExtension.REPORT_NG in extensions,
            onDismiss = { delConfirm = null }
        ) { alsoNg ->
            delConfirm = null
            var outcome = ""
            runRemote("delに失敗しました", "", refreshAfter = false, resultMessage = { outcome },
                busyMessage = if (alsoNg) FTUTTER_REMOTE_BUSY_REPORT_NG_NOTICE else FTUTTER_REMOTE_BUSY_NOTICE) {
                outcome = com.valoser.futacha.shared.ui.board.sendReportAndOptionalNg(alsoNg,
                    send = { repository.requestDeletion(board.url, ref.threadId, post.id, com.valoser.futacha.shared.ui.board.DEFAULT_DEL_REASON_CODE) },
                    registerNg = {
                        com.valoser.futacha.shared.ui.board.registerReportedThreadPostNg(
                            compatStore ?: error("設定の保存先を利用できません"), board, ref.threadId, post.id)
                    })
            }
        }
    }
    deleteFor?.let { post ->
        FtutterDeletePostDialog(post.id, deleteKey, onDismiss = { deleteFor = null }) { key, imageOnly ->
            deleteFor = null
            runRemote("削除に失敗しました", "削除しました", refreshAfter = true) {
                repository.deleteByUser(board.url, ref.threadId, post.id, key, imageOnly)
            }
        }
    }
    copyFor?.let { post ->
        val values = buildList {
            post.mail?.takeIf { it.isNotBlank() }?.let { add("E-Mail" to it) }
            add("No" to "No.${post.id}")
            ftutterPosterIdentities(post).forEach { add(it.kind.name to it.display) }
            post.imageUrl?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { add("ファイル" to it) }
            add("本文" to com.valoser.futacha.shared.ui.board.messageHtmlToPlainText(post.messageHtml))
        }
        FtutterCopyDialog(values, onDismiss = { copyFor = null }) { value ->
            copyFor = null
            clipboard.setText(AnnotatedString(value))
            onNotice("$value\nをコピーしました")
        }
    }
    ngKindFor?.let { post ->
        FtutterChoiceDialog(null, listOf("NGワード", "NGID・IP"), onDismiss = { ngKindFor = null }) { choice ->
            ngKindFor = null
            val ngBoard = FtutterNgBoard(board.name, board.url)
            ngDraft = if (choice == 0) {
                FtutterNgRule(com.valoser.futacha.shared.ui.board.messageHtmlToPlainText(post.messageHtml), FtutterNgType.WORD.stored, ita = ngBoard)
            } else {
                FtutterNgRule(ftutterPosterIdentity(post)?.display.orEmpty(), FtutterNgType.ID.stored, ita = ngBoard)
            }
        }
    }
    ngDraft?.let { draft ->
        FtutterNgDialog(
            initial = draft,
            boards = boards,
            existing = ngRules,
            onDismiss = { ngDraft = null },
            onSave = { rule ->
                ngDraft = null
                onSaveNgRules({ it + rule }, "登録しました")
            }
        )
    }
}

/** 長押しメニューの上に出すレスは押せない（文字の選択だけ）。 */
private val FTUTTER_INERT_ACTIONS = FtutterPostActions(
    onPostTap = { _, _ -> }, onPostLongPress = {}, onRepliesTap = { _, _ -> }, onIdTap = { _, _ -> }, onIdLongPress = {},
    onQuoteTap = { _, _, _ -> }, onUrlTap = {}, onImageTap = {}, onHiddenTap = { _, _ -> }
)

/** メニューの上のレスは、検索語の強調をしない。 */
private fun FtutterThreadContext.copyForMenu() = FtutterThreadContext(posts, replies, idCounts, selfIds, settings, design, "", hidden)

private val ftutterArchiveJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

internal fun ftutterThreadUrl(board: BoardSummary, threadId: String): String =
    com.valoser.futacha.shared.ui.futaber.futaberHistoryThreadUrl(board, threadId)

@Composable
private fun FtutterArchiveButton(busy: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = !busy, modifier = Modifier.testTag("ftutter-archive")) {
        Text(if (busy) "取得中…" else "過去ログを取得")
    }
}

/** 「ここまで読んだ」・末尾の帯（原作 KokomadeCard：上下の区切り線と高さ25の帯）。 */
@Composable
private fun FtutterBandCard(text: String, background: Color, content: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalFtutterColors.current
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outline)
        Box(
            Modifier.fillMaxWidth().height(25.dp).background(background).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Text(text, color = content, fontSize = 13.sp, maxLines = 1) }
        HorizontalDivider(color = colors.outline)
    }
}

/** 隠したレスの置換行「N  削除」「N  NG」。タップでそのレスだけを隠さずに出す。 */
@Composable
private fun FtutterHiddenRowView(index: Int, depth: Int, design: FtutterDesignSettings, isNg: Boolean = false, onTap: (Float) -> Unit) {
    val colors = LocalFtutterColors.current
    var top by remember { mutableStateOf(0f) }
    Column(
        Modifier.fillMaxWidth()
            .onGloballyPositioned { top = it.boundsInRoot().top }
            .clickable { onTap(top) }
            .padding(start = ftutterIndentDp(depth).dp)
            .testTag("ftutter-hidden-post")
    ) {
        HorizontalDivider(color = colors.outline)
        Text(if (isNg) "$index  NG" else "$index  削除", color = colors.headerText, fontSize = design.resuHeaderFontSize.sp, modifier = Modifier.padding(5.dp))
        HorizontalDivider(color = colors.outline)
    }
}

/** レス1件（原作 Resu）。区切り線→見出し→本文と画像→区切り線。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FtutterPostView(
    index: Int,
    depth: Int,
    context: FtutterThreadContext,
    actions: FtutterPostActions,
    highlighted: Boolean,
    inPopup: Boolean
) {
    val colors = LocalFtutterColors.current
    // 行の組み立てはメインスレッドの外で行うので、レスが入れ替わった直後の1回は前の行が残り得る。
    val post = context.posts.getOrNull(index) ?: return
    val settings = context.settings
    val design = context.design
    val mark = ftutterPostMark(post, context.selfIds, depth)
    val lineColor = when (mark) {
        FtutterPostMark.SELF -> colors.kakikomi
        FtutterPostMark.REPLY_TO_SELF -> colors.henshin
        FtutterPostMark.CHILD -> colors.outline
        FtutterPostMark.NONE -> Color.Transparent
    }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    fun rowTop(): Float = coords[0]?.takeIf { it.isAttached }?.boundsInRoot()?.top ?: 0f
    Column(
        Modifier.fillMaxWidth()
            .padding(start = ftutterIndentDp(depth).dp)
            .onGloballyPositioned { coords[0] = it }
            .background(if (highlighted) colors.highlight.copy(alpha = 0.2f) else Color.Transparent)
            .drawBehind {
                if (lineColor != Color.Transparent) drawRect(lineColor, size = androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height))
            }
            .combinedClickable(
                onClick = { actions.onPostTap(index, rowTop()) },
                onLongClick = { actions.onPostLongPress(index) }
            )
            .testTag(if (inPopup) "ftutter-popup-post" else "ftutter-post")
    ) {
        HorizontalDivider(color = colors.outline)
        Column(
            Modifier.padding(
                start = settings.paddingLeft.dp, end = settings.paddingRight.dp,
                top = settings.paddingTop.dp, bottom = settings.paddingBottom.dp
            )
        ) {
            FtutterPostHeader(index, post, context, actions) { rowTop() }
            Spacer(Modifier.height(settings.paddingHeaderText.dp))
            val hasImage = !post.thumbnailUrl.isNullOrBlank()
            val image: @Composable () -> Unit = { FtutterPostImage(index, post, settings, design, actions) }
            if (hasImage && settings.imagePosition == FtutterImagePosition.TOP) {
                image(); Spacer(Modifier.height(settings.paddingTextFile.dp))
            }
            Row {
                if (hasImage && settings.imagePosition == FtutterImagePosition.LEFT) {
                    image(); Spacer(Modifier.width(settings.paddingTextFile.dp))
                }
                Box(Modifier.weight(1f, fill = false)) {
                    FtutterPostBody(index, post, context, actions, shorten = settings.quoteShorten && depth > 0 && !inPopup) { rowTop() }
                }
                if (hasImage && settings.imagePosition == FtutterImagePosition.RIGHT) {
                    Spacer(Modifier.width(settings.paddingTextFile.dp)); image()
                }
            }
            if (hasImage && settings.imagePosition == FtutterImagePosition.BOTTOM) {
                Spacer(Modifier.height(settings.paddingTextFile.dp)); image()
            }
            FtutterApuPreviews(post.messageHtml, settings,
                if (settings.isPrivacy) (1.0 - design.privacyThread).toFloat() else 1f, actions.apuLoaded) { url -> actions.onInlineImageTap(index, url) }
        }
        HorizontalDivider(color = colors.outline)
    }
}

/** 見出し（原作 ResuHeader）：番号と返信数｜題名｜名前｜メール｜日時｜ID (n/m)｜No.｜そうだね｜ファイル名。 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FtutterPostHeader(
    index: Int,
    post: Post,
    context: FtutterThreadContext,
    actions: FtutterPostActions,
    rowTop: () -> Float
) {
    val colors = LocalFtutterColors.current
    val size = context.design.resuHeaderFontSize.sp
    val base = colors.headerText
    val openUrl = rememberFtutterUrlLauncher()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val replyCount = context.replies[post.id]?.size ?: 0
        val replyColor = ftutterReplyCountColor(replyCount) ?: base
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (replyCount > 0) Modifier.clickable { actions.onRepliesTap(index, rowTop()) }.testTag("ftutter-reply-count") else Modifier
        ) {
            Text("$index", color = replyColor, fontSize = size)
            if (replyCount > 0) {
                Spacer(Modifier.width(1.dp))
                Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = null, tint = replyColor,
                    modifier = Modifier.size(with(LocalDensity.current) { size.toDp() }))
                Text("$replyCount", color = replyColor, fontSize = size)
            }
        }
        post.subject?.takeIf { it.isNotBlank() }?.let { Text(it, color = base, fontSize = size) }
        post.author?.takeIf { it.isNotBlank() }?.let { Text(it, color = base, fontSize = size) }
        post.mail?.takeIf { it.isNotBlank() }?.let { mail ->
            Text(
                mail, color = Color(0xFF03A9F4), fontSize = size, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { ftutterMailTarget(mail)?.let(openUrl) }
            )
        }
        Text(ftutterTimestampWithoutIdentity(post), color = base, fontSize = size)
        context.idCounts[post.id]?.let { id ->
            val label = if (id.total > 1) "${id.display} (${id.nth}/${id.total})" else id.display
            Text(
                label, color = ftutterIdCountColor(id.total) ?: base, fontSize = size,
                modifier = Modifier.combinedClickable(
                    onClick = { actions.onIdTap(index, rowTop()) },
                    onLongClick = { actions.onIdLongPress(index) }
                ).testTag("ftutter-post-id")
            )
        }
        Text("No.${post.id}", color = base, fontSize = size)
        val soudane = ftutterSoudaneCount(post.saidaneLabel)
        if (soudane > 0) Text("そうだねx$soudane", color = ftutterSoudaneColor(soudane, colors.text), fontSize = size)
        post.imageUrl?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color(0xFF448AFF), fontSize = size)
        }
    }
}

/** 本文（原作 ResuText）：通常・赤（削除）・リンク・引用 #789922、検索語は背景で強調。引用とリンクだけタップできる。 */
@Composable
private fun FtutterPostBody(
    index: Int,
    post: Post,
    context: FtutterThreadContext,
    actions: FtutterPostActions,
    shorten: Boolean,
    rowTop: () -> Float
) {
    val colors = LocalFtutterColors.current
    // 行と色付きの本文は、画面の外へ出た行が戻ってきた時に作り直さないよう、本文・色ごとに覚えておく（再監査L29：速く
    // スクロールすると、行ごとに本文の行とリンクを作り直していた）。
    val bodyKey = FtutterBodyKey(post.messageHtml, shorten, post.isDeleted || post.isIsolated, colors)
    val cached = remember(bodyKey) { FtutterBodyCache.get(bodyKey) }
    val lines = remember(bodyKey) { cached?.first ?: ftutterBodyLines(post, shorten) }
    val query = context.query.trim()
    // 検索語に関係しない部分（色・引用・リンク）は検索語が変わっても作り直さず、強調だけを重ねる。
    val base = remember(bodyKey) { cached?.second ?: buildAnnotatedString {
            lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) append("\n")
                val start = length
                val isQuote = ftutterIsQuoteLine(line)
                val color = when {
                    post.isDeleted || post.isIsolated -> colors.deleted
                    isQuote -> colors.quote
                    else -> colors.text
                }
                append(line)
                addStyle(SpanStyle(color = color), start, length)
                if (isQuote) {
                    addStringAnnotation("quote", line, start, length)
                } else {
                    com.valoser.futacha.shared.compat.compatInlineLinksCached(
                        line.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    ).forEach { link ->
                        addStringAnnotation("url", link.url, start + link.start, start + link.endExclusive)
                        addStyle(SpanStyle(color = colors.link), start + link.start, start + link.endExclusive)
                    }
                }
            }
        }
    }
    if (cached == null) androidx.compose.runtime.SideEffect { FtutterBodyCache.put(bodyKey, lines to base) }
    val text = remember(base, lines, query, colors) {
        if (query.isEmpty()) base else buildAnnotatedString {
            append(base)
            var start = 0
            lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) start += 1
                var from = 0
                while (true) {
                    val at = line.indexOf(query, from, ignoreCase = true)
                    if (at < 0) break
                    addStyle(SpanStyle(background = colors.highlight.copy(alpha = 0.4f)), start + at, start + at + query.length)
                    from = at + query.length
                }
                start += line.length
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // 押した時の動作・位置は最新を使う（監査L2：本文が同じなら最初の動作のままで、更新の後に古いレス一覧で開いていた）。
    val currentActions by rememberUpdatedState(actions)
    val currentIndex by rememberUpdatedState(index)
    val currentRowTop by rememberUpdatedState(rowTop)
    Text(
        text = text,
        fontSize = context.design.resuContentFontSize.sp,
        onTextLayout = { layout = it },
        modifier = Modifier
            .onGloballyPositioned { coords[0] = it }
            .pointerInput(text) {
                detectTapGestures(
                    onTap = { position ->
                        val offset = layout?.getOffsetForPosition(position)
                        val quote = offset?.let { text.getStringAnnotations("quote", it, it).firstOrNull() }
                        val url = offset?.let { text.getStringAnnotations("url", it, it).firstOrNull() }
                        val top = coords[0]?.takeIf { it.isAttached }?.let { c ->
                            val lineIndex = layout?.getLineForOffset(offset ?: 0) ?: 0
                            c.localToRoot(Offset(0f, layout?.getLineTop(lineIndex) ?: 0f)).y
                        } ?: currentRowTop()
                        when {
                            quote != null -> currentActions.onQuoteTap(currentIndex, quote.item, top)
                            url != null -> currentActions.onUrlTap(url.item)
                            else -> currentActions.onPostTap(currentIndex, currentRowTop())
                        }
                    },
                    onLongPress = { currentActions.onPostLongPress(currentIndex) }
                )
            }
            .testTag("ftutter-post-body")
    )
}

/** レスの画像。OPは最大高さ500、他は300。タップでビューア、長押しは何もしない（原作どおり）。 */
@Composable
private fun FtutterPostImage(
    index: Int,
    post: Post,
    settings: FtutterThreadSettings,
    design: FtutterDesignSettings,
    actions: FtutterPostActions
) {
    val box = ftutterImageBox(post, index == 0, settings)
    var height = box.maxHeight
    var width = height * box.aspect
    if (box.maxWidth != null && width > box.maxWidth) {
        width = box.maxWidth
        height = width / box.aspect
    }
    val alpha = if (settings.isPrivacy) (1.0 - design.privacyThread).toFloat() else 1f
    val currentImageActions by rememberUpdatedState(actions)
    Box(
        Modifier.size(width.dp, height.dp)
            .pointerInput(index) { detectTapGestures(onTap = { currentImageActions.onImageTap(index) }, onLongPress = { currentImageActions.onImageLongPress(index) }) }
            .testTag("ftutter-post-image")
    ) {
        FtutterThumbnail(
            thumbnailUrl = post.thumbnailUrl.orEmpty(),
            fullImageUrl = post.imageUrl,
            quality = if (index == 0) settings.opImageQuality else settings.imageQuality,
            targetSizePx = 500,
            contentDescription = "添付画像",
            modifier = Modifier.fillMaxSize().alpha(alpha)
        )
    }
}

/** 引用・返信・IDのポップアップ（原作 TreeDialog2）。重ねて開き、Backと外側のタップで一番上の1枚だけ閉じる。 */
@Composable
private fun FtutterPopupStack(
    layers: List<FtutterPopupLayer>,
    context: FtutterThreadContext,
    actions: FtutterPostActions,
    toolbarHeightDp: Int,
    onDismissTop: () -> Unit,
    onTopListState: (LazyListState) -> Unit = {}
) {
    val colors = LocalFtutterColors.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topMargin = with(density) { 56.dp.toPx() } + WindowInsets.statusBars.getTop(density)
        val bottomMargin = with(density) { (toolbarHeightDp + 50).dp.toPx() }
        layers.forEachIndexed { level, layer ->
            Box(
                Modifier.fillMaxSize()
                    .then(if (level == layers.lastIndex) Modifier.pointerInput(Unit) { detectTapGestures { onDismissTop() } } else Modifier)
            )
            Layout(
                content = {
                    val listState = rememberLazyListState()
                    if (level == layers.lastIndex) onTopListState(listState)
                    LaunchedEffect(layer) {
                        layer.highlight?.let { h ->
                            val at = layer.items.indexOfFirst { it.first == h }
                            if (at > 0) listState.scrollToItem(at)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .border(1.5.dp, colors.highlight, RoundedCornerShape(16.dp))
                            .background(colors.popUp, RoundedCornerShape(16.dp))
                            .padding(vertical = 8.dp)
                            .testTag("ftutter-popup")
                    ) {
                        items(layer.items, key = { it.first }) { (index, depth) ->
                            // ポップアップでは、隠したレスは表示形式に関係なく置換行にする（原作）。
                            val isDeleted = index in context.hidden.deleted
                            val isNg = index in context.hidden.ng || index in context.hidden.removed
                            if (!layer.forceShow && (isDeleted || isNg)) {
                                FtutterHiddenRowView(index, depth, context.design, isNg = !isDeleted) { anchor -> actions.onHiddenTap(index, anchor) }
                            } else {
                                FtutterPostView(index, depth, context, actions, highlighted = layer.highlight == index, inPopup = true)
                            }
                        }
                    }
                }
            ) { measurables, constraints ->
                val maxHeight = (constraints.maxHeight - topMargin - bottomMargin).toInt().coerceAtLeast(100)
                val placeable = measurables.first().measure(Constraints(0, constraints.maxWidth, 0, maxHeight))
                // タップした所のすぐ上に下端を合わせ、上に収まらなければ上詰め（原作の候補B→候補A）。
                val y = layer.anchorTop?.let { anchor ->
                    val candidate = anchor - placeable.height
                    if (candidate >= topMargin) candidate else topMargin
                } ?: topMargin
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(0, y.toInt())
                }
            }
        }
    }
}

internal enum class FtutterThreadMenuItem { DELETED, NG, MINE, TOOLBAR_EDIT, PRIVACY, SAVE, COPY_URL, BROWSER, READ_ALOUD, AUTO_SCROLL, MHT }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FtutterThreadToolbar(
    entries: List<FtutterToolbarEntry<FtutterThreadTool>>,
    metrics: FtutterToolbarMetrics,
    settings: FtutterThreadSettings,
    filter: FtutterThreadFilter,
    deletedCount: Int,
    ngCount: Int,
    selfCount: Int,
    extensions: FtutterExtensions,
    reading: Boolean,
    autoScrolling: Boolean,
    onGesture: (FtutterGestureAction) -> Unit,
    onTool: (FtutterThreadTool) -> Unit,
    onImagePostsLongPress: () -> Unit,
    onMenu: (FtutterThreadMenuItem) -> Unit
) {
    val colors = LocalFtutterColors.current
    FtutterBottomToolbar(
        entries = entries,
        metrics = metrics,
        isSpace = { it == FtutterThreadTool.SPACE },
        isOrder = { false },
        modifier = Modifier.testTag("ftutter-thread-toolbar")
    ) { tool, _ ->
        if (tool == FtutterThreadTool.MENU) {
            var open by remember { mutableStateOf(false) }
            var gestureMenu by remember { mutableStateOf(false) }
            if (gestureMenu) FtutterGestureActionDialog(true, { gestureMenu = false }, onGesture)
            Box {
                IconButton(onClick = { open = true }, modifier = Modifier.testTag("ftutter-thread-tool-menu")) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "メニュー", tint = colors.onToolBar)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    if (deletedCount > 0) DropdownMenuItem(
                        text = { Text("削除されたレス ($deletedCount)") },
                        onClick = { open = false; onMenu(FtutterThreadMenuItem.DELETED) },
                        modifier = Modifier.testTag("ftutter-thread-menu-deleted")
                    )
                    if (ngCount > 0) DropdownMenuItem(
                        text = { Text("NG ($ngCount)") },
                        onClick = { open = false; onMenu(FtutterThreadMenuItem.NG) },
                        modifier = Modifier.testTag("ftutter-thread-menu-ng")
                    )
                    if (selfCount > 0) DropdownMenuItem(
                        text = { Text("自分のレス ($selfCount)") },
                        onClick = { open = false; onMenu(FtutterThreadMenuItem.MINE) }
                    )
                    DropdownMenuItem(text = { Text("書き込む") }, onClick = { open = false; onTool(FtutterThreadTool.REPLY) },
                        modifier = Modifier.testTag("ftutter-thread-menu-reply"))
                    DropdownMenuItem(text = { Text("画像一覧") }, onClick = { open = false; onTool(FtutterThreadTool.GALLERY) },
                        modifier = Modifier.testTag("ftutter-thread-menu-gallery"))
                    DropdownMenuItem(text = { Text("ジェスチャーの操作を選ぶ") }, onClick = { open = false; gestureMenu = true },
                        modifier = Modifier.testTag("ftutter-thread-menu-gesture-actions"))
                    DropdownMenuItem(text = { Text("ツールバー編集") }, onClick = { open = false; onMenu(FtutterThreadMenuItem.TOOLBAR_EDIT) })
                    DropdownMenuItem(
                        text = { Text("プライバシー") },
                        leadingIcon = { Checkbox(checked = settings.isPrivacy, onCheckedChange = null) },
                        onClick = { open = false; onMenu(FtutterThreadMenuItem.PRIVACY) }
                    )
                    DropdownMenuItem(text = { Text("スレを保存") }, onClick = { open = false; onMenu(FtutterThreadMenuItem.SAVE) })
                    DropdownMenuItem(
                        text = { Text("URLをコピー") },
                        onClick = { open = false; onMenu(FtutterThreadMenuItem.COPY_URL) },
                        modifier = Modifier.testTag("ftutter-thread-menu-copy-url")
                    )
                    DropdownMenuItem(text = { Text("ブラウザで開く") }, onClick = { open = false; onMenu(FtutterThreadMenuItem.BROWSER) })
                    // 拡張機能（ONの時だけ、原作の項目の後ろに足す）。
                    if (FtutterExtension.MHT in extensions) DropdownMenuItem(
                        text = { Text("MHTで保存") },
                        onClick = { open = false; onMenu(FtutterThreadMenuItem.MHT) },
                        modifier = Modifier.testTag("ftutter-thread-menu-mht")
                    )
                    if (FtutterExtension.READ_ALOUD in extensions) DropdownMenuItem(
                        text = { Text(if (reading) "読み上げを停止" else "読み上げ") },
                        onClick = { open = false; onMenu(FtutterThreadMenuItem.READ_ALOUD) },
                        modifier = Modifier.testTag("ftutter-thread-menu-read-aloud")
                    )
                    if (FtutterExtension.AUTO_SCROLL in extensions) DropdownMenuItem(
                        text = { Text(if (autoScrolling) "オートスクロールを停止" else "オートスクロール") },
                        onClick = { open = false; onMenu(FtutterThreadMenuItem.AUTO_SCROLL) },
                        modifier = Modifier.testTag("ftutter-thread-menu-auto-scroll")
                    )
                }
            }
        } else {
            val active = when (tool) {
                FtutterThreadTool.TREE -> settings.isTree
                FtutterThreadTool.POPULAR -> filter.popular
                FtutterThreadTool.IMAGE_POSTS -> filter.imagesOnly
                FtutterThreadTool.TAB -> settings.showTab
                FtutterThreadTool.PRIVACY -> settings.isPrivacy
                else -> false
            }
            val icon = when (tool) {
                FtutterThreadTool.DRAWER -> Icons.Rounded.Menu
                FtutterThreadTool.TREE -> Icons.Rounded.AccountTree
                FtutterThreadTool.POPULAR -> Icons.Rounded.QueryStats
                FtutterThreadTool.IMAGE_POSTS -> Icons.Rounded.Image
                FtutterThreadTool.TAB -> Icons.Rounded.Tab
                FtutterThreadTool.PRIVACY -> if (active) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility
                FtutterThreadTool.UP -> Icons.Rounded.KeyboardArrowUp
                FtutterThreadTool.DOWN -> Icons.Rounded.KeyboardArrowDown
                FtutterThreadTool.GALLERY -> Icons.Rounded.Collections
                FtutterThreadTool.REPLY -> Icons.Rounded.Edit
                FtutterThreadTool.SEARCH -> Icons.Rounded.Search
                FtutterThreadTool.REFRESH -> Icons.Rounded.Refresh
                else -> Icons.Rounded.MoreVert
            }
            Box(
                Modifier.size(40.dp)
                    .combinedClickable(
                        onClick = { onTool(tool) },
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClickLabel = if (tool == FtutterThreadTool.IMAGE_POSTS) "画像レスを抽出" else tool.label,
                        onLongClickLabel = if (tool == FtutterThreadTool.IMAGE_POSTS) "画像一覧を開く" else null,
                        onLongClick = if (tool == FtutterThreadTool.IMAGE_POSTS) onImagePostsLongPress else null
                    )
                    .testTag("ftutter-thread-tool-${tool.stored}"),
                contentAlignment = Alignment.Center
            ) {
                FtutterToggleIcon(icon, contentDescription = tool.label, active = active)
            }
        }
    }
}

/** What the body of a post looks like without the search: its text, whether it is shortened, its colour set. */
private data class FtutterBodyKey(val html: String, val shorten: Boolean, val deleted: Boolean, val colors: FtutterColors)

/** The bodies built lately (lines and coloured text), kept across rows leaving and coming back (re-audit L29). */
private object FtutterBodyCache {
    private const val MAX = 400
    private val entries = LinkedHashMap<FtutterBodyKey, Pair<List<String>, androidx.compose.ui.text.AnnotatedString>>()
    // Used now: moved to the end, so the limit drops the ones not seen for longest.
    fun get(key: FtutterBodyKey) = entries.remove(key)?.also { entries[key] = it }
    fun put(key: FtutterBodyKey, value: Pair<List<String>, androidx.compose.ui.text.AnnotatedString>) {
        entries[key] = value
        if (entries.size > MAX) entries.remove(entries.keys.first())
    }
}
