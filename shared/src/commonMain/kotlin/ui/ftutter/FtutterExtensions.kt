package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.compat.CompatWatcherRepository
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.network.ArchiveSearchItem
import com.valoser.futacha.shared.network.extractArchiveSearchScope
import com.valoser.futacha.shared.network.searchInqueuetArchiveThreads
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.board.CatalogPreviewImage
import com.valoser.futacha.shared.ui.board.archiveSearchItemToCatalogItem
import com.valoser.futacha.shared.ui.board.buildPastThreadSearchEmptyMessage
import com.valoser.futacha.shared.ui.board.buildPastThreadSearchErrorMessage
import com.valoser.futacha.shared.ui.compat.CompatAppStateSetting
import com.valoser.futacha.shared.ui.compat.CompatAppStateSettingDialogs
import com.valoser.futacha.shared.ui.compat.CompatWatcherManager
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.compat.rememberCompatAppStateSettings
import com.valoser.futacha.shared.ui.futaber.futaberArchiveItemKey
import com.valoser.futacha.shared.ui.futaber.futaberArchiveItemLine
import com.valoser.futacha.shared.ui.futaber.futaberArchiveNotice
import com.valoser.futacha.shared.ui.futaber.futaberCompatBoardKey
import com.valoser.futacha.shared.ui.futaber.futaberDistinctArchiveItems
import com.valoser.futacha.shared.ui.futaber.futaberNotificationsStatus
import com.valoser.futacha.shared.ui.futaber.futaberWatchResultSlots
import com.valoser.futacha.shared.ui.futaber.futaberWatchSubtitle
import com.valoser.futacha.shared.state.AppStateStore
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 拡張機能（原作に無く、ほかの3モードにある機能）。履歴の道具だけ既定ON。他は既定OFF。
 * 保存は`compat.ftutter.ext.<名前>`（"true"/"false"）。中身は他モードと共有の部品と設定を使う。
 */
internal enum class FtutterExtension(val stored: String, val title: String, val description: String, val enabledByDefault: Boolean = false) {
    HANDWRITING("handwriting", "手書き", "書き込み画面のメニューから手書き画像を添付できます。img板の返信でも使えます"),
    OPEN_SUMMARY("openSummary", "開いた時の新着・自分への返信件数", "前回までの取得記録と比べ、新しいレスと、その中の自分の投稿への直接返信を一度だけ知らせます"),
    OPEN_AT_TOP(
        "openAtTop", "スレッドを先頭から開く",
        "カタログ・履歴・リンクからスレッドを開いた時、前回の読み位置へ戻らず最初のレスから表示します。タブの切り替えと、設定などから戻った時は見ていた位置のままです"
    ),
    TAB_STATUS("tabStatus", "タブの隔離・落ち状態", "取得して確認できた隔離・削除・落ちを、タブ帯とドロワーにも文字で表示します"),
    REPORT_NG("reportNg", "delとこのレスのNGをまとめる", "delの確認画面で、このスレッドのこのレスだけをNGにも登録できます。送信とNGの成否は別々に表示します"),
    COMMON_NG(
        "commonNg", "共通のNGを使う",
        "ふたちゃ・ふたばー風と共有のNGワード・NGネーム等（レス）とカタログNGワードを、このモードのNGに加えて使います。編集は他のモードで行います",
        enabledByDefault = true // D-B7 (J4): the lists entered in any mode show in the others unless switched off
    ),
    IMAGE_MENU(
        "imageMenu", "画像の長押しメニュー",
        "レスの画像を長押しすると、画像検索・画像NGへの登録を出します。登録した画像に似た画像のレスを隠します（画像NGは他のモードと共有）"
    ),
    READ_ALOUD("readAloud", "読み上げ", "スレッドのメニューに「読み上げ」を出します（声・速さは他のモードと共有の設定）"),
    AUTO_SCROLL("autoScroll", "オートスクロール", "スレッドのメニューに「オートスクロール」を出します（量・間隔は他のモードと共有の設定）"),
    MHT("mht", "MHTで保存", "スレッドのメニューに「MHTで保存」を出します。保存したファイルは他のモードの保存済みスレッドでも開けます"),
    AI(
        "ai", "AIの要約・荒らし非表示",
        "他のモードと共有のAIの設定（スレ要約・AIによる荒らし非表示。どちらも既定OFF）に従い、このモードのスレッドでも使います"
    ),
    WATCHER("watcher", "キーワード通知・巡回", "他のモードと共有のキーワード巡回の管理と結果の一覧を、この画面から開けるようにします"),
    HISTORY_TOOLS(
        "historyTools", "履歴の選択削除・すべて削除",
        "ドロワーの「履歴」の上に選択のボタンを出します。選んだ履歴をまとめて消すことと、すべて消すこと（保護したスレッドは残します）ができます",
        enabledByDefault = true
    ),
    // may 1447424787：自分へのレスは原作だと左の細い線だけで気づきにくい。行全体に淡い色を敷く（要望なので最初からON）。
    REPLY_TO_SELF_TINT(
        "replyToSelfTint", "自分へのレスに色を付ける",
        "自分の書き込みへの返信の行に、返信の線と同じ色を淡く敷きます。OFFにすると原作どおり左の線だけです",
        enabledByDefault = true
    ),
    ARCHIVE_SEARCH("archiveSearch", "過去ログ検索", "カタログの検索に語を入れた時、その語で過去ログを探すボタンを出します"),
    APP_LOCK("appLock", "起動ロック・アプリアイコン", "他のモードと共有の起動ロック（パスワード）とアプリアイコンの選択を、この画面から設定できるようにします");

    val key: String get() = FtutterKeys.PREFIX + "ext." + stored
}

/** オンの拡張機能。 */
internal data class FtutterExtensions(val enabled: Set<FtutterExtension> = FtutterExtension.entries.filterTo(mutableSetOf()) { it.enabledByDefault }) {
    operator fun contains(extension: FtutterExtension): Boolean = extension in enabled
}

internal fun parseFtutterExtensions(preferences: Map<String, String>): FtutterExtensions =
    FtutterExtensions(FtutterExtension.entries.filterTo(mutableSetOf()) { preferences.ftutterBool(it.key, it.enabledByDefault) })

/** 拡張機能の画面の冒頭の説明。 */
internal const val FTUTTER_EXTRAS_INTRO = "原作のふたったーに無い機能です。「履歴の選択削除・すべて削除」と「共通のNGを使う」は最初からONです。他の機能は最初はOFFで、必要なものをONにできます。"

/**
 * 設定→拡張機能。スイッチの下に、オンの時だけ開ける画面（巡回・起動ロック・AIの設定）の行を出す。
 */
@Composable
internal fun FtutterExtrasSettingsPage(
    preferences: Map<String, String>,
    save: (String, String) -> Unit,
    stateStore: AppStateStore,
    sharedAvailable: Boolean,
    onOpenWatcher: () -> Unit,
    onOpenSharedAi: () -> Unit,
    onBack: () -> Unit,
    onOpenSharedSpeech: () -> Unit = {},
    compatStore: CompatibilityStore? = null
) {
    val colors = LocalFtutterColors.current
    val extensions = remember(preferences) { parseFtutterExtensions(preferences) }
    val appState = rememberCompatAppStateSettings(stateStore)
    var appStateDialog by remember { mutableStateOf<CompatAppStateSetting?>(null) }
    var postMenuHideOpen by remember { mutableStateOf(false) }
    FtutterSettingsPage("拡張機能", "ftutter-settings-page-extras", onBack) {
        item(key = "intro") {
            Text(FTUTTER_EXTRAS_INTRO, color = colors.text, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }
        item(key = "boards-from-bottom") {
            FtutterCheckSetting("板一覧を下寄せ", com.valoser.futacha.shared.ui.board.BOARDS_FROM_BOTTOM_KEY,
                false, preferences, save, note = com.valoser.futacha.shared.ui.board.BOARDS_FROM_BOTTOM_NOTE)
        }
        item(key = "settings-font-scale") {
            com.valoser.futacha.shared.ui.board.SettingsFontScaleSetting(preferences, colors.text) {
                save(com.valoser.futacha.shared.ui.board.SETTINGS_FONT_SCALE_KEY, it)
            }
        }
        item(key = "dice-color") {
            com.valoser.futacha.shared.ui.board.DiceColorSetting(preferences, colors.text) {
                save(com.valoser.futacha.shared.ui.board.DICE_COLOR_KEY, it)
            }
        }
        // Plan B9: the 4 modes' one tab limit (ふたったー風 has no such setting in the original, so it is an own row).
        if (compatStore != null) item(key = "tab-limit") { FtutterTabLimitSetting(compatStore, preferences, save) }
        FtutterExtension.entries.forEach { extension ->
            item(key = extension.stored) {
                FtutterCheckSetting(extension.title, extension.key, extension.enabledByDefault, preferences, save, note = extension.description)
                if (extension == FtutterExtension.REPORT_NG) com.valoser.futacha.shared.ui.board.ReportedPostNgSettings(compatStore)
                if (extension in extensions) {
                    when (extension) {
                        FtutterExtension.WATCHER -> if (sharedAvailable) {
                            FtutterNavRow("　キーワード巡回の結果と管理", "ftutter-ext-open-watcher", onClick = onOpenWatcher)
                        }
                        FtutterExtension.READ_ALOUD -> if (sharedAvailable) {
                            FtutterNavRow("　読み上げの声・速さ（共有）", "ftutter-ext-open-speech", onClick = onOpenSharedSpeech)
                        }
                        FtutterExtension.AUTO_SCROLL -> {
                            FtutterSliderSetting(
                                "　スクロール量（px）", com.valoser.futacha.shared.ui.futaber.FutaberSettingKeys.AUTO_SCROLL_PIXEL,
                                FTUTTER_AUTO_SCROLL_PIXEL_RANGE.first.toDouble(), FTUTTER_AUTO_SCROLL_PIXEL_RANGE.last.toDouble(), 1.0,
                                FTUTTER_AUTO_SCROLL_PIXEL_DEFAULT.toDouble(), preferences, save,
                                note = "他のモードのオートスクロールと共有の値です", own = true
                            )
                            FtutterSliderSetting(
                                "　間隔（ミリ秒）", com.valoser.futacha.shared.ui.futaber.FutaberSettingKeys.AUTO_SCROLL_SPEED,
                                FTUTTER_AUTO_SCROLL_SPEED_RANGE.first.toDouble(), FTUTTER_AUTO_SCROLL_SPEED_RANGE.last.toDouble(), 5.0,
                                FTUTTER_AUTO_SCROLL_SPEED_DEFAULT.toDouble(), preferences, save, own = true
                            )
                        }
                        FtutterExtension.AI -> if (sharedAvailable) {
                            FtutterNavRow("　AI・補助機能の設定（共有）", "ftutter-ext-open-ai", onClick = onOpenSharedAi)
                        }
                        FtutterExtension.APP_LOCK -> {
                            FtutterNavRow("　起動ロック", "ftutter-ext-app-lock", detail = appState.summary(CompatAppStateSetting.AppLock)) {
                                appStateDialog = CompatAppStateSetting.AppLock
                            }
                            FtutterNavRow("　アプリアイコン", "ftutter-ext-app-icon", detail = appState.summary(CompatAppStateSetting.AppIcon)) {
                                appStateDialog = CompatAppStateSetting.AppIcon
                            }
                        }
                        else -> Unit
                    }
                }
                HorizontalDivider(color = colors.outline)
            }
        }
        // M29：原作のレス長押しメニュー編集は並べ替えだけ。項目を隠すのはこちらだけの機能なので、ここに置く。
        item(key = "post-menu-hide") {
            FtutterNavRow(FTUTTER_POST_MENU_HIDE_LABEL, "ftutter-ext-post-menu-hide") { postMenuHideOpen = true }
            HorizontalDivider(color = colors.outline)
        }
        // T-B1：原作の動きを既定にし、前の動きはここで戻せる。
        ftutterSection("従来の動き")
        item(key = "legacy-menus") {
            FtutterCheckSetting(FTUTTER_LEGACY_MENUS_LABEL, FTUTTER_LEGACY_MENUS_KEY, false, preferences, save, note = FTUTTER_LEGACY_MENUS_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_POPUPS_LABEL, FTUTTER_LEGACY_POPUPS_KEY, false, preferences, save, note = FTUTTER_LEGACY_POPUPS_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_POST_DIALOG_LABEL, FTUTTER_LEGACY_POST_DIALOG_KEY, false, preferences, save, note = FTUTTER_LEGACY_POST_DIALOG_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_THREAD_LOOK_LABEL, FTUTTER_LEGACY_THREAD_LOOK_KEY, false, preferences, save, note = FTUTTER_LEGACY_THREAD_LOOK_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_BOARDS_LABEL, FTUTTER_LEGACY_BOARDS_KEY, false, preferences, save, note = FTUTTER_LEGACY_BOARDS_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_CATALOG_PRESS_LABEL, FTUTTER_LEGACY_CATALOG_PRESS_KEY, false, preferences, save, note = FTUTTER_LEGACY_CATALOG_PRESS_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_DEL_CONFIRM_LABEL, FTUTTER_LEGACY_DEL_CONFIRM_KEY, false, preferences, save, note = FTUTTER_LEGACY_DEL_CONFIRM_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_KEEP_ON_FAILURE_LABEL, FTUTTER_LEGACY_KEEP_ON_FAILURE_KEY, false, preferences, save, note = FTUTTER_LEGACY_KEEP_ON_FAILURE_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_POST_BOXES_LABEL, FTUTTER_LEGACY_POST_BOXES_KEY, false, preferences, save, note = FTUTTER_LEGACY_POST_BOXES_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_REMOTE_NOTICE_LABEL, FTUTTER_LEGACY_REMOTE_NOTICE_KEY, false, preferences, save, note = FTUTTER_LEGACY_REMOTE_NOTICE_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_FALLEN_LABEL, FTUTTER_LEGACY_FALLEN_KEY, false, preferences, save, note = FTUTTER_LEGACY_FALLEN_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_GALLERY_SQUARE_LABEL, FTUTTER_LEGACY_GALLERY_SQUARE_KEY, false, preferences, save, note = FTUTTER_LEGACY_GALLERY_SQUARE_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_DIALOGS_LABEL, FTUTTER_LEGACY_DIALOGS_KEY, false, preferences, save, note = FTUTTER_LEGACY_DIALOGS_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_VIEWER_LABEL, FTUTTER_LEGACY_VIEWER_KEY, false, preferences, save, note = FTUTTER_LEGACY_VIEWER_NOTE)
            FtutterCheckSetting(FTUTTER_LEGACY_TWO_PANE_EMPTY_LABEL, FTUTTER_LEGACY_TWO_PANE_EMPTY_KEY, false, preferences, save, note = FTUTTER_LEGACY_TWO_PANE_EMPTY_NOTE)
        }
    }
    CompatAppStateSettingDialogs(stateStore, appStateDialog, appState) { appStateDialog = null }
    if (postMenuHideOpen) {
        FtutterOrderEditorDialog(
            title = FTUTTER_POST_MENU_HIDE_LABEL, entries = parseFtutterPostMenu(preferences), label = { it.label },
            onDismiss = { postMenuHideOpen = false },
            onReset = { postMenuHideOpen = false; save(FtutterKeys.POST_MENU_ORDER, ""); save(FtutterKeys.POST_MENU_ENABLED, "") },
            onSave = { saved ->
                postMenuHideOpen = false
                val (names, flags) = encodeFtutterToolbar(saved) { it.stored }
                save(FtutterKeys.POST_MENU_ORDER, names); save(FtutterKeys.POST_MENU_ENABLED, flags)
            }
        )
    }
}

internal const val FTUTTER_POST_MENU_HIDE_LABEL = "レス長押しメニューの項目を隠す"

// ---- キーワード巡回の結果 ----

/** 巡回の結果の行の板（共有の板キーで探す）。登録していなければ null。 */
internal fun ftutterBoardForWatchResult(result: CompatWatchResult, boards: List<BoardSummary>): BoardSummary? =
    boards.firstOrNull { futaberCompatBoardKey(it) == result.history.boardKey }

/**
 * 巡回の結果（他モードと共有の`compat.watcher.result.*`）の一覧と、共有の巡回管理（キーワードの追加・今すぐ巡回）。
 * このモードは前面の巡回を動かさない。結果を書くのは、両OSのバックグラウンド更新と、管理画面の「今すぐ巡回」。
 */
@Composable
internal fun FtutterWatcherScreen(
    store: CompatibilityStore,
    repository: BoardRepository,
    boards: List<BoardSummary>,
    onOpen: (BoardSummary, threadId: String, title: String) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalFtutterColors.current
    val scope = rememberCoroutineScope()
    val watcher = remember(store) { CompatWatcherRepository(store) }
    val preferences by store.preferences.collectAsState(emptyMap())
    val slots = remember(preferences) { futaberWatchResultSlots(preferences) }
    var results by remember { mutableStateOf<List<CompatWatchResult>?>(null) }
    var managerOpen by remember { mutableStateOf(false) }
    // 「すべて削除」は確認してから（監査L15）。
    var clearAllOpen by remember { mutableStateOf(false) }
    if (clearAllOpen) {
        FtutterConfirmDialog(
            title = "巡回の結果をすべて削除しますか？",
            message = "見つかったスレの一覧を空にします（キーワードは残ります）。",
            confirmLabel = "削除",
            onDismiss = { clearAllOpen = false }
        ) {
            clearAllOpen = false
            scope.launch { runCatching { watcher.deleteAll() } }
        }
    }
    LaunchedEffect(slots) {
        results = runCatching { watcher.load(kotlin.time.Clock.System.now().toEpochMilliseconds()) }.getOrDefault(emptyList())
            .sortedByDescending { it.insertedAtEpochMillis }
    }
    FtutterSettingsPage("キーワード巡回", "ftutter-watcher", onBack) {
        item(key = "manage") { FtutterNavRow("キーワード管理・今すぐ巡回", "ftutter-watcher-manage") { managerOpen = true } }
        item(key = "status") {
            Text(futaberNotificationsStatus(preferences), color = colors.text, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }
        val list = results
        if (list == null) {
            item(key = "loading") { CircularProgressIndicator(Modifier.padding(16.dp), color = colors.gesture) }
        } else {
            if (list.isNotEmpty()) item(key = "clear") {
                TextButton(
                    onClick = { clearAllOpen = true },
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("ftutter-watcher-clear")
                ) { Text("すべて削除", color = colors.text, fontWeight = FontWeight.Bold) }
            }
            items(list, key = { it.history.canonicalUrl }) { result ->
                val board = ftutterBoardForWatchResult(result, boards)
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = board != null) {
                            board?.let { onOpen(it, result.history.threadNo, result.history.title) }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp).testTag("ftutter-watcher-row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CatalogPreviewImage(
                        thumbnailUrl = result.history.thumbnailUrl?.takeIf { it.isNotBlank() }, fullImageUrl = null, targetSizePx = 150,
                        contentDescription = "", modifier = Modifier.size(48.dp), fallbackTint = colors.headerText
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(result.history.title.ifBlank { "(無題)" }, color = colors.text, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(futaberWatchSubtitle(result, board != null), color = colors.text, fontSize = 12.sp)
                    }
                    TextButton(onClick = { scope.launch { runCatching { watcher.delete(result.history.canonicalUrl) } } }) {
                        Text("削除", color = colors.text, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = colors.outline)
            }
        }
    }
    if (managerOpen) {
        // 巡回は共有ストアの板を調べるので、このモードの板を取り込んでおく（ふたばー風と同じ）。
        LaunchedEffect(boards) { runCatching { store.importModernBoards(boards) } }
        androidx.compose.runtime.CompositionLocalProvider(LocalCompatibilityPalette provides ftutterMediaPalette(colors)) {
            CompatWatcherManager(
                store = store,
                repository = repository,
                onDismiss = { managerOpen = false },
                onResultsChanged = {},
                resultsLocation = "設定→拡張機能の「キーワード巡回の結果と管理」"
            )
        }
    }
}

// ---- 過去ログ検索 ----

private sealed interface FtutterArchiveState {
    data object Loading : FtutterArchiveState
    data object Empty : FtutterArchiveState
    data class Error(val message: String) : FtutterArchiveState
    data class Results(val items: List<ArchiveSearchItem>) : FtutterArchiveState
}

/** カタログの検索語で過去ログを探すボタン（検索中、語がある時だけ）。 */
@Composable
internal fun FtutterArchiveSearchButton(query: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalFtutterColors.current
    Text(
        "「${query.trim()}」を過去ログから検索", color = colors.onToolBar, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(12.dp)
            .background(colors.toolBar, RoundedCornerShape(50)).border(1.dp, colors.outline, RoundedCornerShape(50))
            .clickable(onClickLabel = "過去ログ検索", onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp).testTag("ftutter-archive-search")
    )
}

/**
 * 過去ログ検索の結果（ふたちゃ・ふたばー風と同じ共有の検索）。行を押すとスレッドを開く（落ちていれば過去ログを取得できる）。
 */
@Composable
internal fun FtutterArchiveSearchSheet(
    query: String,
    board: BoardSummary,
    httpClient: HttpClient,
    onOpen: (CatalogItem) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalFtutterColors.current
    var state by remember(query, board.id) { mutableStateOf<FtutterArchiveState>(FtutterArchiveState.Loading) }
    var attempt by remember(query, board.id) { mutableIntStateOf(0) }
    var allSources by remember(query, board.id) { mutableStateOf(false) }
    LaunchedEffect(query, board.id, attempt, allSources) {
        state = FtutterArchiveState.Loading
        state = try {
            val found = futaberDistinctArchiveItems(
                searchInqueuetArchiveThreads(httpClient, ftutterArchiveSearchJson, query.trim(), extractArchiveSearchScope(board), includeAllSources = allSources)
            )
            if (found.isEmpty()) FtutterArchiveState.Empty else FtutterArchiveState.Results(found)
        } catch (cancelled: CancellationException) {
            if (!isActive) throw cancelled
            FtutterArchiveState.Error("過去ログ検索がタイムアウトしました")
        } catch (error: Throwable) {
            FtutterArchiveState.Error(buildPastThreadSearchErrorMessage(error))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))
                .clickable(onClickLabel = "過去ログ検索を閉じる", onClick = onDismiss)
                .testTag("ftutter-archive-scrim")
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.7f)
                .padding(8.dp).navigationBarsPadding().testTag("ftutter-archive-sheet"),
            shape = RoundedCornerShape(12.dp), color = colors.popUp, shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    "過去ログ検索「${query.trim()}」", color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    futaberArchiveNotice(), color = colors.text, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
                )
                TextButton(onClick = { allSources = true; attempt++ }, enabled = state !is FtutterArchiveState.Loading) {
                    Text("他の保存先も検索", color = colors.text, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = colors.outline)
                when (val current = state) {
                    FtutterArchiveState.Loading -> Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp).testTag("ftutter-archive-loading"), color = colors.gesture, strokeWidth = 2.dp)
                        Text("検索中…", color = colors.text, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                    FtutterArchiveState.Empty -> Text(
                        buildPastThreadSearchEmptyMessage(), color = colors.text, fontSize = 14.sp,
                        modifier = Modifier.padding(24.dp).testTag("ftutter-archive-message")
                    )
                    is FtutterArchiveState.Error -> Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(current.message, color = colors.text, fontSize = 14.sp, modifier = Modifier.testTag("ftutter-archive-message"))
                        TextButton(onClick = { attempt += 1 }, modifier = Modifier.testTag("ftutter-archive-retry")) {
                            Text("再試行", color = colors.text, fontWeight = FontWeight.Bold)
                        }
                    }
                    is FtutterArchiveState.Results -> LazyColumn(Modifier.fillMaxWidth().testTag("ftutter-archive-list")) {
                        items(current.items, key = ::futaberArchiveItemKey) { item ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 58.dp)
                                    .clickable(onClickLabel = "スレッドを開く") { onOpen(archiveSearchItemToCatalogItem(item)) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp).testTag("ftutter-archive-row"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CatalogPreviewImage(
                                    thumbnailUrl = item.thumbUrl?.takeIf { it.isNotBlank() }, fullImageUrl = null, targetSizePx = 150,
                                    contentDescription = "", modifier = Modifier.size(46.dp), fallbackTint = colors.headerText
                                )
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(
                                        item.title?.takeIf { it.isNotBlank() } ?: "No.${item.threadId}", color = colors.text, fontSize = 14.sp,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(futaberArchiveItemLine(item), color = colors.text, fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider(color = colors.outline)
                        }
                    }
                }
            }
        }
    }
}

// ---- スレッドの帯（読み上げ中・オートスクロール中） ----

/** 読み上げ中・オートスクロール中に一覧の下に出す帯。押した所の操作をする。 */
@Composable
internal fun FtutterRunningChip(label: String, tag: String, buttons: List<Pair<String, () -> Unit>>, modifier: Modifier = Modifier) {
    val colors = LocalFtutterColors.current
    Row(
        modifier.padding(8.dp)
            .background(colors.toolBar, RoundedCornerShape(50)).border(1.dp, colors.outline, RoundedCornerShape(50))
            .padding(horizontal = 8.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = colors.onToolBar, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
        buttons.forEach { (text, action) ->
            TextButton(onClick = action, modifier = Modifier.testTag("$tag-$text")) {
                Text(text, color = colors.onToolBar, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val ftutterArchiveSearchJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

// ---- 共通のNG ----

/** 共有のカタログNGワード（題名に含む語。検索と同じ比べ方）を、このモードのNGの結果に重ねる。消えたスレはNGの一覧へ。 */
internal fun ftutterApplyCommonCatalogNg(
    result: FtutterCatalogNgResult,
    words: List<String>,
    titleNg: Map<String, FtutterTitleNg>? = null
): FtutterCatalogNgResult {
    if (words.none { it.isNotBlank() }) return result
    val kept = if (titleNg == null) com.valoser.futacha.shared.ui.futaber.futaberApplyCatalogNg(result.items, words) else {
        result.items.filterNot { item ->
            titleNg[item.title.orEmpty()]?.commonHidden
                ?: com.valoser.futacha.shared.ui.futaber.futaberApplyCatalogNg(listOf(item), words).isEmpty()
        }
    }
    if (kept.size == result.items.size) return result
    val keptUrls = kept.mapTo(mutableSetOf()) { it.threadUrl }
    return result.copy(items = kept, ngRemoved = result.ngRemoved + result.items.filterNot { it.threadUrl in keptUrls })
}

/**
 * 共有のNGワード（本文）・NGネーム等（名前・ID・メール等の見出し）に当たるレスの番号（0始まり）。全レスを見るので、画面は
 * メインスレッドの外で呼ぶ（ふたばー風の`futaberNgResolve`と同じ）。[ensureActive]は途中で取り消しを見る。
 */
internal fun ftutterCommonNgPosts(
    posts: List<com.valoser.futacha.shared.model.Post>,
    words: List<String>,
    headers: List<String>,
    ensureActive: () -> Unit = {}
): Set<Int> {
    if (posts.isEmpty() || (words.none { it.isNotBlank() } && headers.none { it.isNotBlank() })) return emptySet()
    val ids = com.valoser.futacha.shared.ui.futaber.futaberNgEvaluate(
        posts, com.valoser.futacha.shared.ui.futaber.futaberNgPrepare(words, headers, emptyList(), "", ""), ensureActive
    )
    if (ids.isEmpty()) return emptySet()
    return posts.indices.filterTo(mutableSetOf()) { posts[it].id in ids }
}

// ---- スレッドの拡張で使う小さな計算 ----

/** オートスクロールの量（px）と間隔（ms）。ふたちゃ・としあき(仮)・ふたばー風と共有の値（範囲外は既定）。 */
internal fun ftutterAutoScrollSettings(preferences: Map<String, String>): Pair<Int, Long> {
    val pixel = preferences[com.valoser.futacha.shared.ui.futaber.FutaberSettingKeys.AUTO_SCROLL_PIXEL]?.filter(Char::isDigit)?.toIntOrNull()
        ?.takeIf { it in FTUTTER_AUTO_SCROLL_PIXEL_RANGE } ?: FTUTTER_AUTO_SCROLL_PIXEL_DEFAULT
    val speed = preferences[com.valoser.futacha.shared.ui.futaber.FutaberSettingKeys.AUTO_SCROLL_SPEED]?.filter(Char::isDigit)?.toIntOrNull()
        ?.takeIf { it in FTUTTER_AUTO_SCROLL_SPEED_RANGE } ?: FTUTTER_AUTO_SCROLL_SPEED_DEFAULT
    return pixel to speed.toLong()
}

internal val FTUTTER_AUTO_SCROLL_PIXEL_RANGE = 1..30
internal const val FTUTTER_AUTO_SCROLL_PIXEL_DEFAULT = 5
internal val FTUTTER_AUTO_SCROLL_SPEED_RANGE = 10..200
internal const val FTUTTER_AUTO_SCROLL_SPEED_DEFAULT = 50

/** 一覧に出ているレス（隠したレス・帯を除く）の番号を、画面の順に。読み上げとAIが使う。 */
internal fun ftutterListedPostIndexes(rows: List<FtutterRow>): List<Int> = rows.mapNotNull { (it as? FtutterRow.PostRow)?.index }

/** 一覧の[firstVisibleRow]行目より前にあるレスの数（読み上げの開始位置）。 */
internal fun ftutterListedBefore(rows: List<FtutterRow>, firstVisibleRow: Int): Int =
    rows.take(firstVisibleRow.coerceIn(0, rows.size)).count { it is FtutterRow.PostRow }

/** AIが画面の位置を追うための、一覧の鍵（`p<番号>`）→レスの番号。 */
internal fun ftutterAiItemKeys(rows: List<FtutterRow>, posts: List<com.valoser.futacha.shared.model.Post>): Map<String, String> =
    rows.mapNotNull { row -> (row as? FtutterRow.PostRow)?.let { r -> posts.getOrNull(r.index)?.let { row.key to it.id } } }.toMap()


/** Plan B9: the shared tab limit; lowering it says how many tabs would close and asks first. */
@Composable
private fun FtutterTabLimitSetting(store: CompatibilityStore, preferences: Map<String, String>, save: (String, String) -> Unit) {
    val tabs by store.tabs.collectAsState(emptyList())
    val boards by store.boards.collectAsState(emptyList())
    val workspace by store.workspace.collectAsState(com.valoser.futacha.shared.compat.CompatWorkspaceRecord())
    val views by store.tabViews.collectAsState(emptyList())
    val current = com.valoser.futacha.shared.compat.compatSharedTabLimit(preferences)
    val floor = com.valoser.futacha.shared.compat.compatTabLimitFloor(tabs)
    FtutterSliderSetting(
        "タブの上限（4モード共通）", com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_KEY,
        minOf(floor, current).toDouble(), com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_MAX.toDouble(), 1.0,
        maxOf(com.valoser.futacha.shared.compat.COMPAT_SHARED_TAB_LIMIT_DEFAULT, floor).toDouble(), preferences, save,
        note = com.valoser.futacha.shared.compat.COMPAT_TAB_LIMIT_NOTE,
        confirm = { value ->
            val limit = value.toInt()
            if (limit >= current) null
            else com.valoser.futacha.shared.compat.compatTabLimitQuestion(limit,
                com.valoser.futacha.shared.compat.compatTabLimitTrimCount(tabs, limit, workspace.activeTabKey, preferences, boards, views))
        },
        own = true
    )
}
