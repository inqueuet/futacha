package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatHeaderExtractionKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.compatGoogleSearchTerms
import com.valoser.futacha.shared.compat.compatPostActionCandidates
import com.valoser.futacha.shared.compat.compatPosterIdentities
import com.valoser.futacha.shared.compat.compatQuickQuoteText
import com.valoser.futacha.shared.compat.extractCompatHeaderPosts
import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.board.FutachaNgManagementDialog
import com.valoser.futacha.shared.ui.board.FutachaPostItemCandidate
import com.valoser.futacha.shared.ui.board.FutachaPostItemSelection
import com.valoser.futacha.shared.ui.board.FutachaPostItemSelectionMode
import com.valoser.futacha.shared.ui.board.FutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.futachaGoogleSearchUrl
import com.valoser.futacha.shared.ui.board.futachaSelectedItemsText
import com.valoser.futacha.shared.ui.board.relatedThreadPosts
import com.valoser.futacha.shared.ui.compat.CompatApngMarkerCache
import com.valoser.futacha.shared.ui.compat.CompatGalleryScreen
import com.valoser.futacha.shared.ui.compat.CompatibilityPalette
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.compat.compatMediaIdentity
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/*
 * ふたばー風 機能そろえ B7b。どれも拡張機能のスイッチ（既定OFF）の後ろにあり、オフなら画面・操作は元のまま。
 * - N02 NGの詳細管理（ngDetail）：他モードと共有の詳細ルール（CompatNgRule）の一覧と編集。ダイアログはふたちゃの
 *   FutachaNgManagementDialog（Analyticsなし）。このモードは他モードのタブ（CompatTab）を書かないので、スレッドの
 *   文字のルールは「全てのスレッド」だけを作る（threadTextRulesGlobalOnly）。画像は「この板のみ」も選べる。
 * - A05 レスの詳細操作（postDetailOps）：項目を選んでコピー／語句を選んでGoogle検索／クイック引用返信。
 * - T09 同じスイッチ：関連レス／同じID／同じIPのレスを吹き出しで見る。
 * - M03 画像一覧の選択保存（gallerySelectSave）：画像一覧を共有の CompatGalleryScreen（選択保存・長押しメニュー・列数）にする。
 * - R04 読み上げの一時停止・位置（readAloudPanel）：「読み上げ中」をタップすると位置のスライダーと一時停止／再開のパネル。
 */

private const val B7B_TAG = "FutaberParityB7b"

// ---- N02: NGの詳細管理 ----

internal const val FUTABER_NG_DETAIL_LABEL = "NGの詳細管理"

/** What the detailed NG dialog opens on: a board's catalog rules, or (with [threadTabKey]) the thread rules. */
internal data class FutaberNgDetailRequest(
    val boardKey: String,
    val boardName: String,
    /** The thread's shared key (rules other modes made for this thread are listed); null for the catalog rules. */
    val threadTabKey: String?
)

/**
 * The request for [board] (and [threadId] for the thread rules), or null for a board the shared store has no key for
 * (the tutorial board, an address that is not an official board): its rules could not be kept.
 */
internal fun futaberNgDetailRequest(board: BoardSummary, threadId: String?): FutaberNgDetailRequest? {
    val boardKey = futaberNgScopeKey(board) ?: return null
    return FutaberNgDetailRequest(boardKey, board.name, threadId?.let { futaberCompatTabKey(board, it) })
}

/** An entry of the thread menu or the catalog's refresh sheet: shown while the extension is on, usable with a board key. */
internal class FutaberNgDetailEntry(val enabled: Boolean, val open: () -> Unit)

internal fun futaberNgDetailEntry(
    extensionOn: Boolean,
    featuresAvailable: Boolean,
    board: BoardSummary?,
    threadId: String?,
    onOpen: (FutaberNgDetailRequest) -> Unit
): FutaberNgDetailEntry? {
    if (!extensionOn || board == null) return null
    val request = if (featuresAvailable) futaberNgDetailRequest(board, threadId) else null
    return FutaberNgDetailEntry(request != null) { request?.let(onOpen) }
}

internal fun futaberNgDetailMenuItem(entry: FutaberNgDetailEntry?, onDismiss: () -> Unit): FutaberMenuItem? =
    entry?.let { FutaberMenuItem("ng-detail", FUTABER_NG_DETAIL_LABEL, Icons.Outlined.Tune, enabled = it.enabled) { onDismiss(); it.open() } }

internal fun futaberNgDetailCatalogAction(entry: FutaberNgDetailEntry?): FutaberPostAction? =
    entry?.let { FutaberPostAction("ng-detail", FUTABER_NG_DETAIL_LABEL, enabled = it.enabled) { it.open() } }

/**
 * The shared rule screen writes its top bar in white; this mode's light bars are white, so the bar takes a colour white
 * text is readable on (4.5:1 or more in every theme), as the drawing screen does.
 */
internal fun futaberNgDetailPalette(colors: FutaberColors): CompatibilityPalette = futaberDrawingPalette(colors)

/** What the extensions of B7b keep for the mode (the request of the detailed NG dialog). */
@Stable
internal class FutaberParityB7bState {
    var ngDetail by mutableStateOf<FutaberNgDetailRequest?>(null)
}

/**
 * The detailed NG dialog (ふたちゃ's, no analytics), in this mode's colours. The shared store first learns of this mode's
 * boards: a board-scoped rule is kept only for a board the store knows.
 */
@Composable
internal fun FutaberNgDetailHost(
    request: FutaberNgDetailRequest?,
    features: FutachaSharedFeatures?,
    boards: List<BoardSummary>,
    onDismiss: () -> Unit
) {
    if (request == null || features == null) return
    val colors = LocalFutaberColors.current
    var ready by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request) {
        try {
            features.store.importModernBoards(boards)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Logger.e(B7B_TAG, "Failed to share the boards before the detailed NG", error)
        }
        ready = true
    }
    if (!ready) return
    CompositionLocalProvider(LocalCompatibilityPalette provides futaberNgDetailPalette(colors)) {
        FutachaNgManagementDialog(
            features = features,
            boardKey = request.boardKey,
            tabKey = request.threadTabKey,
            boardName = request.boardName,
            onDismiss = onDismiss,
            threadTextRulesGlobalOnly = true
        )
    }
}

// ---- A05 / T09: レスの詳細操作 ----

/** The post as the shared logic reads it ([position] is its place in the thread, 0 = the first post). */
private fun Post.snapshotAt(position: Int): CompatPostSnapshot = toCompatPostSnapshot(position.coerceAtLeast(0))

/** The rows of the picker: No, ID / IP, file, mail and body lines to copy, or the body's words to search. */
internal fun futaberPostItemSelection(post: Post, position: Int, mode: FutachaPostItemSelectionMode): FutachaPostItemSelection {
    val snapshot = post.snapshotAt(position)
    val candidates = when (mode) {
        FutachaPostItemSelectionMode.COPY -> compatPostActionCandidates(snapshot).map { FutachaPostItemCandidate(it.label, it.value) }
        FutachaPostItemSelectionMode.SEARCH -> compatGoogleSearchTerms(snapshot).map { FutachaPostItemCandidate("", it) }
    }
    return FutachaPostItemSelection(post.id, mode, candidates)
}

/** "クイック引用返信": every body line quoted (or `>No.N` for a post without text), the same text as the other modes. */
internal fun futaberQuickQuote(post: Post, position: Int): String =
    compatQuickQuoteText(post.snapshotAt(position)).trimEnd('\n')

/** The post itself, the posts it quotes and the posts quoting it, in thread order (ふたちゃの「関連レス」). */
internal fun futaberRelatedPosts(post: Post, posts: List<Post>): List<Post> = relatedThreadPosts(post, posts)

/** Whether "関連レスを表示" has something to show besides the post itself. */
internal fun futaberHasRelatedPosts(post: Post, posts: List<Post>): Boolean =
    futaberRelatedPosts(post, posts).any { it.id != post.id }

/** The poster identities (ID / IP) the post's header shows; empty for a post without either. */
internal fun futaberPosterIdentityKinds(post: Post, position: Int): Set<CompatHeaderExtractionKind> =
    compatPosterIdentities(post.snapshotAt(position)).map { it.kind }
        .filter { it == CompatHeaderExtractionKind.ID || it == CompatHeaderExtractionKind.IP }.toSet()

/** The posts whose header shows the same ID ([kind] = ID) or IP as [source], in thread order (the shared header extraction). */
internal fun futaberSameIdentityPosts(posts: List<Post>, source: Post, kind: CompatHeaderExtractionKind): List<Post> {
    val sourceIndex = posts.indexOfFirst { it === source }.takeIf { it >= 0 } ?: posts.indexOfFirst { it.id == source.id }
    if (sourceIndex < 0) return emptyList()
    val snapshots = posts.mapIndexed { index, post -> post.snapshotAt(index) }
    return extractCompatHeaderPosts(snapshots, snapshots[sourceIndex], kind).mapNotNull { posts.getOrNull(it.position) }
}

internal fun futaberIdentityQuoteKind(kind: CompatHeaderExtractionKind): FutaberQuoteKind =
    if (kind == CompatHeaderExtractionKind.IP) FutaberQuoteKind.SameIp else FutaberQuoteKind.SameId

/**
 * Remembers where the last touch went down on the thread list, so a bubble opened from the long-press sheet points at the
 * post that was pressed. Only watches (never consumes) the touches, so scrolling and taps are unchanged.
 */
internal class FutaberPressAnchor {
    private var origin = Offset.Zero
    private var size = IntSize.Zero
    private var lastPress: Offset? = null

    val modifier: Modifier = Modifier
        .onGloballyPositioned { origin = it.positionInRoot(); size = it.size }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                lastPress = down.position
            }
        }

    /** The last press in root coordinates; without one (a long press from a screen reader), the middle of the list. */
    fun anchor(): FutaberAnchor {
        val local = lastPress ?: Offset(FUTABER_PRESS_ANCHOR_FALLBACK_X, size.height / 2f)
        val root = origin + local
        return FutaberAnchor(root.x, root.y, root.y)
    }
}

private const val FUTABER_PRESS_ANCHOR_FALLBACK_X = 48f

/** What the post detail operations of a thread screen keep: the picker, the anchor and a scope for the ID / IP lookups. */
@Stable
internal class FutaberPostDetailOps(val scope: CoroutineScope) {
    var selection by mutableStateOf<FutachaPostItemSelection?>(null)
    val anchor = FutaberPressAnchor()
}

@Composable
internal fun rememberFutaberPostDetailOps(key1: Any?, key2: Any?): FutaberPostDetailOps {
    val scope = rememberCoroutineScope()
    return remember(key1, key2, scope) { FutaberPostDetailOps(scope) }
}

/**
 * The rows the extension adds to a post's long-press sheet (before "キャンセル"); empty while it is off. Rows that would
 * show nothing (no related post, no ID / IP) are left out. [close] closes the sheet.
 */
internal fun futaberPostDetailActions(
    enabled: Boolean,
    ops: FutaberPostDetailOps,
    post: Post,
    posts: List<Post>,
    close: () -> Unit,
    onOpenPost: (quote: String) -> Unit,
    openQuote: (FutaberQuoteKind, List<Post>, FutaberAnchor) -> Unit
): List<FutaberPostAction> {
    if (!enabled) return emptyList()
    val position = posts.indexOfFirst { it.id == post.id }.coerceAtLeast(0)
    val identities = futaberPosterIdentityKinds(post, position)
    return buildList {
        add(FutaberPostAction("select-copy", "項目を選んでコピー") {
            close(); ops.selection = futaberPostItemSelection(post, position, FutachaPostItemSelectionMode.COPY)
        })
        add(FutaberPostAction("select-search", "語句を選んでGoogle検索") {
            close(); ops.selection = futaberPostItemSelection(post, position, FutachaPostItemSelectionMode.SEARCH)
        })
        add(FutaberPostAction("quick-quote", "クイック引用返信") { close(); onOpenPost(futaberQuickQuote(post, position)) })
        if (futaberHasRelatedPosts(post, posts)) add(FutaberPostAction("related", "関連レスを表示") {
            close(); openQuote(FutaberQuoteKind.Related, futaberRelatedPosts(post, posts), ops.anchor.anchor())
        })
        listOf(
            CompatHeaderExtractionKind.ID to ("same-id" to "同じIDのレス"),
            CompatHeaderExtractionKind.IP to ("same-ip" to "同じIPのレス")
        ).forEach { (kind, row) ->
            if (kind in identities) add(FutaberPostAction(row.first, row.second) {
                close()
                val anchor = ops.anchor.anchor()
                ops.scope.launch {
                    // Every post's header is read, so not on the main thread.
                    val found = try {
                        withContext(AppDispatchers.parsing) { futaberSameIdentityPosts(posts, post, kind) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        Logger.e(B7B_TAG, "Failed to list the posts of the same poster", error)
                        emptyList()
                    }
                    openQuote(futaberIdentityQuoteKind(kind), found, anchor)
                }
            })
        }
    }
}

/** The picker of "項目を選んでコピー" / "語句を選んでGoogle検索", in this mode's colours (no analytics). */
@Composable
internal fun FutaberPostDetailOpsHost(
    ops: FutaberPostDetailOps,
    onCopy: (text: String) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val current = ops.selection ?: return
    val colors = LocalFutaberColors.current
    val isCopy = current.mode == FutachaPostItemSelectionMode.COPY
    val close = { ops.selection = null }
    FutachaAppLockAwareWindow {
        AlertDialog(
            onDismissRequest = close,
            modifier = Modifier.testTag("futaber-post-item-dialog"),
            containerColor = colors.bar,
            titleContentColor = colors.body,
            textContentColor = colors.body,
            title = { Text(if (isCopy) "No.${current.postNo} の項目をコピー" else "No.${current.postNo} の語句で検索") },
            text = {
                if (current.candidates.isEmpty()) {
                    Text(if (isCopy) "コピーできる項目がありません" else "検索できる語句がありません")
                } else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    current.candidates.forEachIndexed { index, item ->
                        val checked = index in current.selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                .clickable(onClickLabel = if (checked) "選択を外す" else "選択する") {
                                    ops.selection = current.copy(
                                        selected = if (checked) current.selected - index else current.selected + index
                                    )
                                }
                                .testTag("futaber-post-item-$index")
                        ) {
                            Checkbox(
                                checked = checked, onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent, uncheckedColor = colors.body, checkmarkColor = colors.onAction
                                )
                            )
                            Text(
                                if (item.label.isBlank()) item.value else "${item.label}: ${item.value}",
                                color = colors.body, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = current.selected.isNotEmpty(),
                    modifier = Modifier.testTag("futaber-post-item-confirm"),
                    onClick = {
                        val text = futachaSelectedItemsText(current)
                        if (isCopy) onCopy(text) else futachaGoogleSearchUrl(text)?.let(onOpenUrl)
                        close()
                    }
                ) {
                    Text(
                        if (isCopy) "クリップボードにコピー" else "Google検索",
                        color = if (current.selected.isNotEmpty()) colors.link else colors.meta
                    )
                }
            },
            dismissButton = { TextButton(onClick = close) { Text("キャンセル", color = colors.link) } }
        )
    }
}

// ---- M03: 画像一覧の選択保存 ----

/** The thread's snapshot without the posts NG hides, so the shared list holds what the viewer holds. */
internal fun futaberGallerySnapshot(snapshot: CompatThreadSnapshot, hiddenPostNos: Set<String>): CompatThreadSnapshot =
    if (hiddenPostNos.isEmpty()) snapshot else snapshot.copy(posts = snapshot.posts.filterNot { it.postNo in hiddenPostNos })

/**
 * The page of the viewer for a picture chosen in the shared list: found by the picture's identity (the two lists are made
 * from the same snapshot, but the shared one also applies a few settings of its own), else the list's own index.
 */
internal fun futaberViewerIndexFor(mediaPosts: List<CompatPostSnapshot>, identity: String?, fallbackIndex: Int): Int {
    fun matches(post: CompatPostSnapshot, id: String) =
        post.imageUrl == id || (post.imageUrl == null && post.thumbnailUrl == id) || compatMediaIdentity(post) == id
    // The recorded page wins while it still shows the same picture (L147: a picture posted twice returned to its first
    // copy, since the URL was looked up first).
    if (identity != null && mediaPosts.getOrNull(fallbackIndex)?.let { matches(it, identity) } == true) return fallbackIndex
    // Original URLs distinguish several images from one post even if inline-media indices have shifted.
    val urlIndex = identity?.let { id ->
        mediaPosts.indexOfFirst { it.imageUrl == id || (it.imageUrl == null && it.thumbnailUrl == id) }
    } ?: -1
    val found = if (urlIndex >= 0) urlIndex else identity?.let { id ->
        mediaPosts.indexOfFirst { compatMediaIdentity(it) == id }
    } ?: -1
    return if (found >= 0) found else fallbackIndex.coerceIn(0, (mediaPosts.size - 1).coerceAtLeast(0))
}

/**
 * The shared store as the shared list sees it: "元レスに移動する" writes a scroll position to a tab, which this mode does
 * not keep (and must not write to another mode's tab of the same thread). It goes to the post in this thread instead.
 */
internal class FutaberGalleryStore(
    private val base: CompatibilityStore,
    private val onShowPost: (postNo: String) -> Unit
) : CompatibilityStore by base {
    override suspend fun updateScrollAnchor(tabKey: String, anchor: ScrollAnchor) {
        anchor.postNo?.takeIf { it.isNotBlank() }?.let(onShowPost)
    }
}

/**
 * The shared picture list (selection save as ZIP / folder, its long-press menu, the shared column counts, retries of
 * failed saves), over the same snapshot the viewer reads, without the posts NG hides. Opening a picture opens this
 * mode's viewer.
 */
@Composable
internal fun FutaberSelectSaveGallery(
    tab: CompatTab,
    snapshot: CompatThreadSnapshot?,
    hiddenPostNos: Set<String>,
    mediaPosts: List<CompatPostSnapshot>,
    services: FutaberMediaServices,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    onOpenViewer: (index: Int, identity: String?) -> Unit,
    onShowPost: (postNo: String) -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val scope = rememberCoroutineScope()
    val apngMarkerCache = remember(scope) { CompatApngMarkerCache(scope) }
    val prepared = remember(snapshot, hiddenPostNos) { snapshot?.let { futaberGallerySnapshot(it, hiddenPostNos) } }
    val latestShowPost by rememberUpdatedState(onShowPost)
    val store = remember(services.store) { FutaberGalleryStore(services.store) { latestShowPost(it) } }
    if (prepared == null) {
        // Without the thread's own snapshot the shared list would read another mode's stored one: it waits instead.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accent) }
        return
    }
    Box(Modifier.fillMaxSize().testTag("futaber-select-save-gallery")) {
        CompatGalleryScreen(
            tab = tab,
            preparedSnapshot = prepared,
            gridState = gridState,
            restoreInitialPosition = false,
            store = store,
            preferences = preferences,
            ngRules = ngRules,
            httpClient = services.httpClient,
            apngMarkerCache = apngMarkerCache,
            fileSystem = services.fileSystem,
            cookieRepository = services.cookieRepository,
            onOpenViewer = { index, identity -> onOpenViewer(futaberViewerIndexFor(mediaPosts, identity, index), identity) },
            onOpenSettings = onOpenSettings,
            onOpenCommonSettings = onOpenSettings,
            onBack = onClose
        )
    }
}

// ---- R04: 読み上げの一時停止・位置 ----

/** "n / 総数" of the panel (1-based; "- / N" before the first post is reached). */
internal fun futaberReadAloudPositionLabel(index: Int, count: Int): String =
    "${if (index in 0 until count) (index + 1).toString() else "-"} / $count"

/** The slider's value (a fraction of the run) turned into a post index. */
internal fun futaberReadAloudSliderIndex(value: Float, count: Int): Int =
    if (count <= 1) 0 else value.roundToInt().coerceIn(0, count - 1)

/**
 * The read-aloud chip of the extension: the same "前 / 状態 / 次 / 停止", where the state ("読み上げ中" or "一時停止中")
 * opens a small panel with the position slider and pause / resume.
 */
@Composable
internal fun FutaberReadAloudPanelChip(reader: FutaberReadAloud, modifier: Modifier = Modifier) {
    val colors = LocalFutaberColors.current
    var panelOpen by remember { mutableStateOf(false) }
    Column(modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (panelOpen) FutaberReadAloudPanel(reader)
        Row(
            Modifier.padding(top = if (panelOpen) 6.dp else 0.dp)
                .background(colors.bar, FutaberShapes.pill)
                .border(1.dp, colors.separator, FutaberShapes.pill)
                .padding(horizontal = 4.dp)
                .testTag("futaber-reading-chip"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanelChipAction("前", "前のレスへ", "futaber-reading-previous") { reader.skip(-1) }
            Text(
                if (reader.isPaused) "一時停止中" else "読み上げ中",
                color = colors.body, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.heightIn(min = 40.dp)
                    .clickable(onClickLabel = if (panelOpen) "操作パネルを閉じる" else "操作パネルを開く") { panelOpen = !panelOpen }
                    .padding(horizontal = 6.dp, vertical = 11.dp)
                    .testTag("futaber-reading-state")
            )
            PanelChipAction("次", "次のレスへ", "futaber-reading-next") { reader.skip(1) }
            PanelChipAction("停止", "読み上げを停止", "futaber-reading-stop") { reader.stop() }
        }
    }
}

@Composable
private fun FutaberReadAloudPanel(reader: FutaberReadAloud) {
    val colors = LocalFutaberColors.current
    val count = reader.segmentCount
    val index = reader.currentIndex
    // The thumb follows the finger while dragging; the reader moves only when it is let go.
    var dragging by remember { mutableStateOf<Float?>(null) }
    Surface(
        modifier = Modifier.widthIn(min = 260.dp, max = 420.dp).testTag("futaber-reading-panel"),
        shape = FutaberShapes.card,
        color = colors.bar,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.separator),
        shadowElevation = 4.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val shownIndex = dragging?.let { futaberReadAloudSliderIndex(it, count) } ?: index
            Text(
                futaberReadAloudPositionLabel(shownIndex, count),
                color = colors.body, fontSize = 13.sp, modifier = Modifier.testTag("futaber-reading-position")
            )
            if (count > 1) {
                Slider(
                    value = dragging ?: index.coerceIn(0, count - 1).toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { reader.seekTo(futaberReadAloudSliderIndex(it, count)) }
                        dragging = null
                    },
                    valueRange = 0f..(count - 1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.separator
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).testTag("futaber-reading-slider")
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { if (reader.isPaused) reader.resume() else reader.pause() },
                    modifier = Modifier.testTag("futaber-reading-pause")
                ) {
                    Text(if (reader.isPaused) "再開" else "一時停止", color = colors.link, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PanelChipAction(label: String, description: String, tag: String, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Text(
        label, color = colors.link, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.heightIn(min = 40.dp).clickable(onClickLabel = description, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp).testTag(tag)
    )
}
