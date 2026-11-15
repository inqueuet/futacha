package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.CookieManagementScreen
import com.valoser.futacha.shared.ui.compat.CompatChangeLogScreen
import com.valoser.futacha.shared.ui.compat.CompatHelpScreen
import com.valoser.futacha.shared.ui.compat.CompatLicenseScreen
import com.valoser.futacha.shared.ui.compat.CompatSettingsScreen
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

internal enum class FutaberSettingsRoute(val title: String) {
    Root("設定"), Theme("テーマ"), Catalog("カタログ"), Thread("スレッド"), Gesture("ジェスチャー"), Mode("モード"),
    Ng("NG設定"), NgList("NG"), AppIcon("アプリアイコン"), Extras("拡張機能"), Media("メディア機能"),
    /** C11: futaber's 新規スレッド通知 (the keywords per board). */
    NewThreadNotify("新規スレッド通知"),
    /** C12: this app's terms. */
    Eula("EULA"),
    /** L-B12 (C05): futaber's URLスキーム. */
    UrlScheme("URLスキーム")
}

/** The screen the "‹" of [this] goes back to. */
private fun FutaberSettingsRoute.parent(): FutaberSettingsRoute =
    if (this == FutaberSettingsRoute.NgList) FutaberSettingsRoute.Ng else FutaberSettingsRoute.Root

/**
 * The NG lists that can be edited here. [title] is futaber's page title (「カタログNGワード」), [row] the row on the NG
 * page. The word lists are the ones ふたちゃ uses, so an entry made in either mode applies in both.
 */
internal enum class FutaberNgListKind(val title: String, val row: String, val hint: String) {
    CatalogThreads("カタログNGスレッドURL", "NGスレッドURL", "題名の長押しの「スレッドをNG」か、ここにスレッドのURL（https://may.2chan.net/b/res/123.htm など。公式の板だけ）を入れると、そのスレッドをカタログから隠します"),
    CatalogWords("カタログNGワード", "NGワード", "題名にこの語句を含むスレッドをカタログから隠します（更新後に反映）"),
    ResWords("レスNGワード", "NGワード", "本文にこの語句を含むレスを隠します"),
    /** C37: the shared 「NGネーム・ID等」 without the IDs and IPs (they are on their own page, futaber's NGID/IP). */
    ResNames("レスNGネーム", "NGネーム", "名前・題名・日時にこの語句を含むレスを隠します（「ID:」「IP:」で始まる語句はNGID/IPにあります。ふたちゃのNGネーム・ID等と同じ一覧です）"),
    ResIdIp("レスNGID/IP", "NGID/IP", "このIDかIPのレスを隠します。「ID:」「IP:」を付けずに入れると、IPらしい値（106.73.* など）には「IP:」、それ以外には「ID:」を付けます"),
    ResImages("レスNG画像", "NG画像", "スレッドの画像の長押しで「画像をNG」にした画像です。似た画像のレスを隠します。外すとレスが戻ります"),
    CatalogWordRules("NGワード（詳細）", "NGワード（詳細）", "ふたちゃ・としあき(仮)のNG管理で登録した、題名に語句を含むスレッドを隠すルールです。外すとカタログに戻ります（更新後に反映）"),
    CatalogImages("NG画像（カタログ）", "NG画像（カタログ）", "ふたちゃ・としあき(仮)で登録した、サムネイルの画像で隠すルールです。外すとカタログに戻ります（更新後に反映）"),
    ThreadDetails("スレッド別NG（詳細）", "スレッド別NG（詳細）", "ふたちゃ・としあき(仮)のNG管理で登録した、レス番号・ID・語句・画像アドレスで隠すルールです。スレッドを開いたときに適用されます。外すとレスが戻ります"),
    /** B7a: not an NG list, but the same add / remove screen (the alert's watch words, shared with ふたちゃ). */
    WatchWords("監視ワード（アラート用）", "監視ワード（アラート用）", "")
}

/** The tag end of each list's row on the NG page (kept from before the table). */
internal val FutaberNgListKind.rowId: String get() = when (this) {
    FutaberNgListKind.CatalogThreads -> "ng-catalog-threads"
    FutaberNgListKind.CatalogWords -> "ng-catalog-words"
    FutaberNgListKind.ResWords -> "ng-res-words"
    FutaberNgListKind.ResNames -> "ng-res-names"
    FutaberNgListKind.ResIdIp -> "ng-res-id-ip"
    FutaberNgListKind.ResImages -> "ng-res-images"
    FutaberNgListKind.CatalogWordRules -> "ng-catalog-word-rules"
    FutaberNgListKind.CatalogImages -> "ng-catalog-images"
    FutaberNgListKind.ThreadDetails -> "ng-res-thread-details"
    FutaberNgListKind.WatchWords -> "watch-words"
}

/** One entry of an NG list: [id] is what removing it goes by (the word itself, or the id of the rule). */
internal data class FutaberNgRow(val id: String, val label: String)

/** The rows of a list, the first of every [FutaberNgRow.id] only (a lazy list refuses two items with one key). */
internal fun futaberNgRows(rows: List<FutaberNgRow>): List<FutaberNgRow> = rows.distinctBy { it.id }

/**
 * futaber's settings (L-B9, V21): the iOS grouped table over the whole screen, 「完了」 at the top left, futaber's groups
 * first (C47) and this app's own after its last row ([futaberSettingsTopExtras]). What has its own screen elsewhere
 * (the shared pages, the image viewer's, the patrol manager) opens that screen from here.
 */
@Composable
internal fun FutaberSettingsScreen(
    themeMode: FutaberThemeMode,
    onThemeModeChange: (FutaberThemeMode) -> Unit,
    settings: FutaberDisplaySettings,
    onSettingChange: (key: String, value: String) -> Unit,
    postSettings: FutaberPostSettings,
    deleteKey: String,
    onPostSettingsChange: (name: String, email: String, confirmBeforeSend: Boolean) -> Unit,
    onDeleteKeyChange: (String) -> Unit,
    ngWords: List<String>,
    ngHeaders: List<String>,
    catalogNgWords: List<String>,
    /**
     * The NG lists are edited by a function applied to the latest stored list, not by a whole new list made from what
     * the screen showed: two quick edits (two "×" in a row) must both count.
     */
    onEditNgWords: ((List<String>) -> List<String>) -> Unit,
    onEditNgHeaders: ((List<String>) -> List<String>) -> Unit,
    onEditCatalogNgWords: ((List<String>) -> List<String>) -> Unit,
    mediaServices: FutaberMediaServices?,
    onOpenNotifications: () -> Unit,
    onClose: () -> Unit,
    appVersion: String = "1.0",
    stateStore: AppStateStore? = null,
    /** The NG threads registered in any mode (catalog long press "スレッドをNG"); they can be taken back here. */
    catalogThreadRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    /** The host's own handling of the watch-word alert switch (it asks for the notification permission); null = just store it. */
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)? = null,
    onDeleteNgRule: (String) -> Unit = {},
    onDeleteNgRules: (Collection<String>) -> Unit = { ids -> ids.forEach(onDeleteNgRule) },
    /** The image NG rules (registered from a picture's long press in any mode). */
    imageNgRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    /** The catalog rules of the other modes that hide threads by a word of the title / by their picture. */
    catalogWordRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    catalogImageRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    /** The thread rules of the other modes (post number, poster id, words, picture address) that hide posts here, and the titles their scopes stand for. */
    threadDetailRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    /** The titles of the given rule scopes; called off the main thread (L110). */
    threadNgScopeTitles: (wanted: Set<String>) -> Map<String, String> = { emptyMap() },
    /** Tells the person something went wrong (a setting that could not be saved). */
    onNotice: (String) -> Unit = {},
    /** The shared "閲覧スレ通知" switch was changed (the host starts or stops its sender). */
    onArchiveReportEnabledChanged: (Boolean) -> Unit = {},
    /** B7a: the watch words of "監視ワード自動アラート" (every board) and how many boards keep their own; null = no editor. */
    watchWords: List<String> = emptyList(),
    boardWatchWordCount: Int = 0,
    onEditWatchWords: (((List<String>) -> List<String>) -> Unit)? = null,
    /**
     * L-B6 (M16): opened from the operation menu's 「NG編集」 at the NG settings, as futaber's: 「完了」 at the top left
     * and Back close the screen there (back to the thread); the NG lists come back to it as usual.
     */
    startAtNg: Boolean = false,
    /** C33: the thread an address typed into NGスレッドURL names (null: not a thread of a registered official board). */
    ngThreadTarget: (String) -> FutaberNgThreadTarget? = { null },
    /** C33: registers the NG thread (the shared catalog rule, as the catalog's long press does); null = no field. */
    onAddNgThread: ((FutaberNgThreadTarget) -> Unit)? = null,
    /** C11: puts this mode's boards into the shared store the patrol reads (the 新規スレッド通知 page lists its boards). */
    onShareBoardsWithPatrol: () -> Unit = {}
) {
    val boardPlacementPreferences = mediaServices?.store?.preferences?.collectAsState(emptyMap<String, String>())?.value.orEmpty()
    var route by rememberSaveable { mutableStateOf(if (startAtNg) FutaberSettingsRoute.Ng else FutaberSettingsRoute.Root) }
    // The page the screen was opened at: its 「完了」 and Back close the screen.
    val home = if (startAtNg) FutaberSettingsRoute.Ng else FutaberSettingsRoute.Root
    var postingOpen by rememberSaveable { mutableStateOf(false) }
    var ngKind by rememberSaveable { mutableStateOf(FutaberNgListKind.ResWords) }
    // C45: the 「編集」 of an NG list (and of 新規スレッド通知): the rows get their delete buttons and 「すべて削除」.
    var editing by rememberSaveable { mutableStateOf(false) }
    // The alert of a list (「すべて削除しますか？」, the field of 「新規キーワード」), over the whole screen.
    var alertRequest by remember { mutableStateOf<FutaberSettingsAlertRequest?>(null) }
    // The shared settings page (or help / change log / licence / cookies) shown over this screen; null = none.
    var sharedPath by rememberSaveable { mutableStateOf<String?>(null) }
    // Writes and the font's copy / reset go on in the app's scope: pressing "完了" at once closes this screen and its own
    // scope would cancel them half way (re-audit L4).
    val screenScope = androidx.compose.runtime.rememberCoroutineScope()
    val scope = LocalFutaberAppTasks.current?.scope ?: screenScope
    var lockDialog by rememberSaveable { mutableStateOf(false) }
    var fontDialog by rememberSaveable { mutableStateOf(false) }
    var fileManagerDialog by rememberSaveable { mutableStateOf(false) }
    val store: (suspend (AppStateStore) -> Unit) -> Unit = launch@{ block ->
        val target = stateStore ?: return@launch
        scope.launch {
            try { block(target) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("FutaberSettings", "Failed to save a setting", error)
                onNotice(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    val goTo: (FutaberSettingsRoute) -> Unit = { next -> editing = false; route = next }
    val goBack: () -> Unit = {
        editing = false
        route = if (route == FutaberSettingsRoute.NgList && ngKind == FutaberNgListKind.WatchWords) FutaberSettingsRoute.Root else route.parent()
    }
    val openList: (FutaberNgListKind) -> Unit = { kind -> ngKind = kind; goTo(FutaberSettingsRoute.NgList) }
    // G35: futaber's settings close only with 「完了」; an iPhone's edge swipe goes back from a page but does not close them.
    FutaberBackHandler(onBack = { if (route == home) onClose() else goBack() }, iosEdgeGestureEnabled = route != home)
    FutaberTablePageBand()

    // The screen takes every touch itself: the header's empty parts and the gaps between rows would otherwise let a touch
    // fall through to the catalog's top bar (the "＋" and the gear) under it.
    // L-B8 (S16): 「書き込み設定」 is futaber's full-screen table over this screen, so the two are drawn in one box.
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(LocalFutaberColors.current.settingsPage).pointerInput(Unit) {}.testTag("futaber-settings")) {
        FutaberSettingsTopBar(
            title = if (route == FutaberSettingsRoute.NgList) ngKind.title else route.title,
            atHome = route == home,
            backLabel = if (route == FutaberSettingsRoute.NgList && ngKind != FutaberNgListKind.WatchWords) "NG設定" else "設定",
            editable = route == FutaberSettingsRoute.NewThreadNotify || route == FutaberSettingsRoute.NgList,
            editing = editing,
            onEdit = { editing = !editing },
            onClose = onClose,
            onBack = goBack
        )
        if (route == FutaberSettingsRoute.NgList) {
            FutaberSettingsNgList(
                kind = ngKind,
                model = futaberSettingsNgListModel(
                    kind = ngKind, ngWords = ngWords, ngHeaders = ngHeaders, catalogNgWords = catalogNgWords,
                    onEditNgWords = onEditNgWords, onEditNgHeaders = onEditNgHeaders, onEditCatalogNgWords = onEditCatalogNgWords,
                    watchWords = watchWords, boardWatchWordCount = boardWatchWordCount, onEditWatchWords = onEditWatchWords,
                    catalogThreadRules = catalogThreadRules, imageNgRules = imageNgRules, catalogWordRules = catalogWordRules,
                    catalogImageRules = catalogImageRules, threadDetailRules = threadDetailRules, threadNgScopeTitles = threadNgScopeTitles,
                    onDeleteNgRule = onDeleteNgRule, onDeleteNgRules = onDeleteNgRules, ngThreadTarget = ngThreadTarget,
                    onAddNgThread = onAddNgThread.takeIf { mediaServices != null }
                ),
                editing = editing, onStopEditing = { editing = false }, onAlert = { alertRequest = it }
            )
        } else if (route == FutaberSettingsRoute.NewThreadNotify && mediaServices != null) {
            FutaberNewThreadNotifyPage(
                store = mediaServices.store, scope = scope, editing = editing, onNotice = onNotice,
                onShareBoards = onShareBoardsWithPatrol, onOpenPatrol = onOpenNotifications, onAlert = { alertRequest = it }
            )
        } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            when (route) {
                FutaberSettingsRoute.Root -> FutaberSettingsRootPage(
                    settings = settings, onSettingChange = onSettingChange, mediaServices = mediaServices,
                    stateStore = stateStore, appVersion = appVersion, hasWatchWordsEditor = onEditWatchWords != null,
                    watchWordCount = watchWords.size,
                    onRoute = goTo, onShared = { sharedPath = it }, onPosting = { postingOpen = true },
                    onWatchWords = { openList(FutaberNgListKind.WatchWords) }, onOpenNotifications = onOpenNotifications,
                    onLock = { lockDialog = true }, onFileManager = { fileManagerDialog = true }, store = store,
                    onWatchAlertSettingChangeRequested = onWatchAlertSettingChangeRequested,
                    onArchiveReportEnabledChanged = onArchiveReportEnabledChanged
                )
                FutaberSettingsRoute.Ng -> FutaberSettingsNgPage(
                    ngWords = ngWords, ngHeaders = ngHeaders, catalogNgWords = catalogNgWords,
                    catalogThreadRules = catalogThreadRules, imageNgRules = imageNgRules, catalogWordRules = catalogWordRules,
                    catalogImageRules = catalogImageRules, threadDetailRules = threadDetailRules, onOpen = openList
                )
                FutaberSettingsRoute.Gesture -> FutaberSettingsGesturePage(settings, onSettingChange)
                FutaberSettingsRoute.Extras -> FutaberSettingsExtrasPage(
                    settings, onSettingChange, boardPlacementPreferences, mediaServices
                ) { fontDialog = true }
                FutaberSettingsRoute.Theme -> {
                    FutaberSettingsThemePage(themeMode, onThemeModeChange)
                    // C14〜C17: the wallpapers, after the themes.
                    FutaberWallpaperSections(settings, mediaServices, scope, onSettingChange, onNotice)
                }
                FutaberSettingsRoute.Catalog -> FutaberSettingsCatalogPage(settings, onSettingChange)
                FutaberSettingsRoute.Thread -> FutaberSettingsThreadPage(
                    settings, onSettingChange, services = mediaServices != null
                ) { sharedPath = it }
                // Prompt display, image / video editing and their help, on a page of this mode's own (the shared "media"
                // page was empty here: its switches need the settings writer the runtime host now provides).
                FutaberSettingsRoute.Media -> FutaberMediaSettingsPage()
                FutaberSettingsRoute.Mode -> ModeSection()
                FutaberSettingsRoute.AppIcon -> FutaberSettingsAppIconPage(stateStore, store)
                FutaberSettingsRoute.Eula -> FutaberEulaPage()
                FutaberSettingsRoute.UrlScheme -> FutaberUrlSchemePage(settings.urlSchemes, onSettingChange)
                FutaberSettingsRoute.NgList, FutaberSettingsRoute.NewThreadNotify -> Unit // drawn above, outside the scrolling column
            }
        }
    }
    if (postingOpen) {
        // Opened from the settings: no 「その他」 (端末情報出力 is the writing screen's, as in the original).
        FutaberPostSettingsScreen(
            settings = postSettings,
            deleteKey = deleteKey,
            onSettingsChange = onPostSettingsChange,
            onDeleteKeyChange = onDeleteKeyChange,
            onImageQualityChange = { onSettingChange(FutaberPreferenceKeys.POST_IMAGE_QUALITY, it.persisted) },
            onDone = { postingOpen = false }
        )
    }
    alertRequest?.let { request -> FutaberSettingsAlert(request) { alertRequest = null } }
    }
    // Held here, not by the dialog: the dialog closes before the picked font arrives and is copied (see the function).
    val fontActions = rememberFutaberCustomFontActions(mediaServices, scope, onNotice)
    if (fontDialog && mediaServices != null) {
        FutaberCustomFontDialog(rememberFutaberCustomFontName(mediaServices.store), fontActions) { fontDialog = false }
    }
    if (fileManagerDialog && stateStore != null) {
        FutaberPreferredFileManagerDialog(stateStore, scope, onNotice) { fileManagerDialog = false }
    }
    if (lockDialog) {
        val lockHash by (stateStore?.appLockPasswordHash ?: kotlinx.coroutines.flow.flowOf<String?>(null)).collectAsState(null)
        FutaberAppLockDialog(
            enabled = !lockHash.isNullOrBlank(),
            onSetPassword = { password -> store { it.setAppLockPassword(password) } },
            onClear = { store { it.clearAppLockPassword() } },
            onDismiss = { lockDialog = false }
        )
    }
    val openPath = sharedPath
    if (openPath != null && mediaServices != null) {
        FutaberSharedSettingsHost(openPath, mediaServices, appVersion, stateStore) { sharedPath = null }
    }
}

/** The navigation bar of the settings: 「完了」 at home, else 「‹ 設定」; 「編集」 / 「完了」 at the right of a list. */
@Composable
private fun FutaberSettingsTopBar(
    title: String,
    atHome: Boolean,
    backLabel: String,
    editable: Boolean,
    editing: Boolean,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onBack: () -> Unit
) {
    FutaberTableTopBar(
        title = title,
        titleTag = "futaber-settings-title",
        leading = {
            if (atHome) FutaberTableDoneButton("完了", "futaber-settings-done", onClose)
            else FutaberTableBackButton(backLabel, "futaber-settings-back", onBack)
        },
        trailing = if (editable) {
            { FutaberTableBarButton(if (editing) "完了" else "編集", "futaber-settings-edit", bold = editing, onClick = onEdit) }
        } else null
    )
}

/** The settings top: futaber's groups (C47), then this app's own (the extras, after a wider gap). */
@Composable
private fun ColumnScope.FutaberSettingsRootPage(
    settings: FutaberDisplaySettings,
    onSettingChange: (String, String) -> Unit,
    mediaServices: FutaberMediaServices?,
    stateStore: AppStateStore?,
    appVersion: String,
    hasWatchWordsEditor: Boolean,
    watchWordCount: Int,
    onRoute: (FutaberSettingsRoute) -> Unit,
    onShared: (String) -> Unit,
    onPosting: () -> Unit,
    onWatchWords: () -> Unit,
    onOpenNotifications: () -> Unit,
    onLock: () -> Unit,
    onFileManager: () -> Unit,
    store: (suspend (AppStateStore) -> Unit) -> Unit,
    onWatchAlertSettingChangeRequested: ((Boolean) -> Unit)?,
    onArchiveReportEnabledChanged: (Boolean) -> Unit
) {
    val available = FutaberSettingsAvailability(
        services = mediaServices != null,
        stateStore = stateStore != null,
        cookies = mediaServices?.cookieRepository != null,
        watchWordsEditor = hasWatchWordsEditor,
        fileManager = com.valoser.futacha.shared.util.isAndroid(),
        archiveReport = !com.valoser.futacha.shared.util.isDesktop()
    )
    val values = rememberFutaberSettingsStoreValues(stateStore)
    val open: (FutaberSettingsRow) -> Unit = { row ->
        when (row) {
            FutaberSettingsRow.Theme -> onRoute(FutaberSettingsRoute.Theme)
            FutaberSettingsRow.Catalog -> onRoute(FutaberSettingsRoute.Catalog)
            FutaberSettingsRow.Thread -> onRoute(FutaberSettingsRoute.Thread)
            FutaberSettingsRow.Viewer -> onShared("viewer")
            FutaberSettingsRow.Posting -> onPosting()
            FutaberSettingsRow.Gesture -> onRoute(FutaberSettingsRoute.Gesture)
            FutaberSettingsRow.Ng -> onRoute(FutaberSettingsRoute.Ng)
            FutaberSettingsRow.NewThreadNotify -> onRoute(FutaberSettingsRoute.NewThreadNotify)
            FutaberSettingsRow.Eula -> onRoute(FutaberSettingsRoute.Eula)
            FutaberSettingsRow.UrlScheme -> onRoute(FutaberSettingsRoute.UrlScheme)
            FutaberSettingsRow.Storage -> onShared("storage")
            FutaberSettingsRow.Network -> onShared("network")
            FutaberSettingsRow.Background -> onShared("background")
            FutaberSettingsRow.Backup -> onShared("backup")
            FutaberSettingsRow.Cookies -> onShared("cookies")
            FutaberSettingsRow.Aimoge -> onShared("aimoge")
            FutaberSettingsRow.Notifications -> onOpenNotifications()
            FutaberSettingsRow.ImageSearch -> onShared("image_search")
            FutaberSettingsRow.Media -> onRoute(FutaberSettingsRoute.Media)
            FutaberSettingsRow.Privacy -> onShared("privacy")
            FutaberSettingsRow.Ai -> onShared("ai")
            FutaberSettingsRow.WatchWords -> onWatchWords()
            FutaberSettingsRow.AppLock -> onLock()
            FutaberSettingsRow.Extras -> onRoute(FutaberSettingsRoute.Extras)
            FutaberSettingsRow.AppIcon -> onRoute(FutaberSettingsRoute.AppIcon)
            FutaberSettingsRow.Mode -> onRoute(FutaberSettingsRoute.Mode)
            FutaberSettingsRow.FileManager -> onFileManager()
            FutaberSettingsRow.Help -> onShared("help")
            FutaberSettingsRow.ChangeLog -> onShared("changelog")
            FutaberSettingsRow.License -> onShared("license")
            else -> Unit
        }
    }
    val switch: (FutaberSettingsRow, Boolean) -> Unit = { row, next ->
        when (row) {
            FutaberSettingsRow.PatrolEnabled -> onSettingChange(FutaberSettingKeys.PATROL_ENABLED, if (next) "ON" else "OFF")
            FutaberSettingsRow.UpdateCheck -> store { it.setUpdateCheckEnabled(next) }
            FutaberSettingsRow.BackgroundRefresh -> store { it.setBackgroundRefreshEnabled(next) }
            FutaberSettingsRow.WatchAlert -> onWatchAlertSettingChangeRequested?.invoke(next) ?: store { it.setWatchAlertEnabled(next) }
            FutaberSettingsRow.Lightweight -> store { it.setLightweightModeEnabled(next) }
            FutaberSettingsRow.ArchiveReport -> {
                onSettingChange(FutaberSettingKeys.ARCHIVE_REPORT, if (next) "ON" else "OFF")
                onArchiveReportEnabledChanged(next)
            }
            else -> Unit
        }
    }
    val fileManagerLabel = if (available.fileManager && stateStore != null) rememberFutaberPreferredFileManagerLabel(stateStore) else null
    val groups = futaberSettingsTopGroups(available) + futaberSettingsTopExtras(available)
    var firstExtra = true
    groups.forEach { group ->
        // A wider gap before this app's first group, so it reads apart from futaber's (look plan §2.9).
        val gap = if (group.extra && firstExtra) 48.dp else 28.dp
        if (group.extra) firstExtra = false
        FutaberTableSection(group.title, tag = "futaber-settings-group-${group.id}", gap = gap) {
            group.rows.forEachIndexed { index, row ->
                FutaberSettingsTopRowItem(
                    row, last = index == group.rows.lastIndex, settings = settings, values = values,
                    watchWordCount = watchWordCount, appVersion = appVersion, fileManagerLabel = fileManagerLabel,
                    onSettingChange = onSettingChange, onOpen = open, onSwitch = switch
                )
            }
        }
        when (group.id) {
            "notify" -> FutaberTableNote(FUTABER_SETTINGS_NOTIFY_NOTE, tag = "futaber-settings-notify-note")
            "archive-report" -> FutaberTableNote(
                com.valoser.futacha.shared.ui.board.VIEWED_THREAD_ARCHIVE_REPORT_DESCRIPTION, tag = "futaber-settings-archive-report-note"
            )
        }
    }
    androidx.compose.foundation.layout.Spacer(Modifier.heightIn(min = 32.dp))
}

/** The store's own switches and values the top shows (each read only when there is a store). */
internal data class FutaberSettingsStoreValues(
    val updateCheck: Boolean = true,
    val backgroundRefresh: Boolean = false,
    val watchAlert: Boolean = false,
    val lightweight: Boolean = false,
    val lockEnabled: Boolean = false,
    val appIconLabel: String = com.valoser.futacha.shared.model.AppIconVariant.Current.label
)

@Composable
private fun rememberFutaberSettingsStoreValues(stateStore: AppStateStore?): FutaberSettingsStoreValues {
    val updateCheck by (stateStore?.isUpdateCheckEnabled ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(true)
    val backgroundRefresh by (stateStore?.isBackgroundRefreshEnabled ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)
    val watchAlert by (stateStore?.isWatchAlertEnabled ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)
    val lightweight by (stateStore?.isLightweightModeEnabled ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)
    val lockHash by (stateStore?.appLockPasswordHash ?: kotlinx.coroutines.flow.flowOf<String?>(null)).collectAsState(null)
    val appIcon by (stateStore?.appIconVariant ?: kotlinx.coroutines.flow.flowOf(com.valoser.futacha.shared.model.AppIconVariant.Current))
        .collectAsState(com.valoser.futacha.shared.model.AppIconVariant.Current)
    return FutaberSettingsStoreValues(updateCheck, backgroundRefresh, watchAlert, lightweight, !lockHash.isNullOrBlank(), appIcon.label)
}

/** One row of the settings top, drawn for what it is (a page, a switch, the font size −＋, the version). */
@Composable
private fun FutaberSettingsTopRowItem(
    row: FutaberSettingsRow,
    last: Boolean,
    settings: FutaberDisplaySettings,
    values: FutaberSettingsStoreValues,
    watchWordCount: Int,
    appVersion: String,
    fileManagerLabel: String?,
    onSettingChange: (String, String) -> Unit,
    onOpen: (FutaberSettingsRow) -> Unit,
    onSwitch: (FutaberSettingsRow, Boolean) -> Unit
) {
    when (row) {
        FutaberSettingsRow.FontSize -> StepperRow(
            label = "フォントサイズ = ${settings.fontSize}", tag = "font-size", last = last,
            canDecrease = settings.fontSize > FUTABER_FONT_SIZE_MIN,
            canIncrease = settings.fontSize < FUTABER_FONT_SIZE_MAX,
            onDecrease = {
                onSettingChange(FutaberSettingKeys.FONT_SIZE, futaberStep(settings.fontSize, -1, FUTABER_FONT_SIZE_MIN, FUTABER_FONT_SIZE_MAX).toString())
            },
            onIncrease = {
                onSettingChange(FutaberSettingKeys.FONT_SIZE, futaberStep(settings.fontSize, 1, FUTABER_FONT_SIZE_MIN, FUTABER_FONT_SIZE_MAX).toString())
            }
        )
        FutaberSettingsRow.PatrolEnabled -> SwitchRow(row.label, row.id, settings.patrolEnabled, last) { onSwitch(row, it) }
        FutaberSettingsRow.UpdateCheck -> SwitchRow(row.label, row.id, values.updateCheck, last) { onSwitch(row, it) }
        FutaberSettingsRow.BackgroundRefresh -> SwitchRow(row.label, row.id, values.backgroundRefresh, last) { onSwitch(row, it) }
        FutaberSettingsRow.WatchAlert -> SwitchRow(row.label, row.id, values.watchAlert, last) { onSwitch(row, it) }
        FutaberSettingsRow.Lightweight -> SwitchRow(row.label, row.id, values.lightweight, last) { onSwitch(row, it) }
        FutaberSettingsRow.ArchiveReport -> SwitchRow(row.label, row.id, settings.archiveReportEnabled, last) { onSwitch(row, it) }
        FutaberSettingsRow.Version -> InfoRow(row.label, row.id, appVersion, last)
        FutaberSettingsRow.WatchWords -> NavRow(row.label, row.id, last, detail = watchWordCount.toString()) { onOpen(row) }
        FutaberSettingsRow.AppLock -> NavRow(row.label, row.id, last, detail = if (values.lockEnabled) "有効" else "無効") { onOpen(row) }
        FutaberSettingsRow.AppIcon -> NavRow(row.label, row.id, last, detail = values.appIconLabel) { onOpen(row) }
        FutaberSettingsRow.FileManager -> NavRow(row.label, row.id, last, detail = fileManagerLabel) { onOpen(row) }
        else -> NavRow(row.label, row.id, last) { onOpen(row) }
    }
}

/** The app icon choice. */
@Composable
private fun FutaberSettingsAppIconPage(stateStore: AppStateStore?, store: (suspend (AppStateStore) -> Unit) -> Unit) {
    val appIcon by (stateStore?.appIconVariant ?: kotlinx.coroutines.flow.flowOf(com.valoser.futacha.shared.model.AppIconVariant.Current))
        .collectAsState(com.valoser.futacha.shared.model.AppIconVariant.Current)
    SettingsSection("アプリアイコン") {
        // "ミッドナイト" is not offered: the store turns it back into the default when saved (as ふたちゃ's list shows two).
        val variants = listOf(
            com.valoser.futacha.shared.model.AppIconVariant.Current,
            com.valoser.futacha.shared.model.AppIconVariant.Classic
        )
        variants.forEachIndexed { index, variant ->
            CheckRow(variant.label, "app-icon-${variant.name}", variant == appIcon, last = index == variants.lastIndex) {
                store { it.setAppIconVariant(variant) }
            }
        }
    }
}

/** NG設定 (C45 lists, futaber's rows first: カタログNG・レスNG, then this app's lists of each group). */
@Composable
private fun FutaberSettingsNgPage(
    ngWords: List<String>,
    ngHeaders: List<String>,
    catalogNgWords: List<String>,
    catalogThreadRules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    imageNgRules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    catalogWordRules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    catalogImageRules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    threadDetailRules: List<com.valoser.futacha.shared.compat.CompatNgRule>,
    onOpen: (FutaberNgListKind) -> Unit
) {
    fun count(kind: FutaberNgListKind): Int = when (kind) {
        FutaberNgListKind.CatalogWords -> catalogNgWords.size
        FutaberNgListKind.CatalogThreads -> catalogThreadRules.size
        FutaberNgListKind.ResWords -> ngWords.size
        FutaberNgListKind.ResNames -> ngHeaders.count { !futaberNgHeaderIsIdIp(it) }
        FutaberNgListKind.ResIdIp -> ngHeaders.count(::futaberNgHeaderIsIdIp)
        FutaberNgListKind.ResImages -> imageNgRules.size
        FutaberNgListKind.CatalogWordRules -> catalogWordRules.size
        FutaberNgListKind.CatalogImages -> catalogImageRules.size
        FutaberNgListKind.ThreadDetails -> threadDetailRules.size
        FutaberNgListKind.WatchWords -> 0
    }
    val catalog = futaberNgCatalogRows() + futaberNgCatalogExtras()
    SettingsSection("カタログNG (編集した後更新で反映)") {
        catalog.forEachIndexed { index, kind ->
            NavRow(kind.row, kind.rowId, last = index == catalog.lastIndex, detail = count(kind).toString()) { onOpen(kind) }
        }
    }
    val res = futaberNgResRows() + futaberNgResExtras(threadDetailRules.isNotEmpty())
    SettingsSection("レスNG") {
        res.forEachIndexed { index, kind ->
            NavRow(kind.row, kind.rowId, last = index == res.lastIndex, detail = count(kind).toString()) { onOpen(kind) }
        }
    }
    FutaberTableNote(FUTABER_NG_PAGE_NOTE, tag = "futaber-settings-ng-note")
}

/** What the NG page tells about futaber's kinds this app does not keep yet (data plan §2.7: where they are kept is D-B7's). */
internal const val FUTABER_NG_PAGE_NOTE =
    "NGワード（正規表現）・NGメール・NG題名は、保存先をほかのモードと決めてから加わります。NGネームとNGID/IPは、ふたちゃの「NGネーム・ID等」と同じ一覧を分けて見せています"

/** ジェスチャー (C46): futaber's switch, then 「他」 with futaber's four lines first. Tap-scroll moved to スレッド (C25). */
@Composable
private fun FutaberSettingsGesturePage(settings: FutaberDisplaySettings, onSettingChange: (String, String) -> Unit) {
    SettingsSection(null) {
        SwitchRow("画像ビューアを↓スワイプで閉じる", "gesture-viewer-swipe", settings.viewerSwipeClose, last = true) {
            onSettingChange(FutaberSettingKeys.VIEWER_SWIPE_CLOSE, if (it) "ON" else "OFF")
        }
    }
    FutaberTableNote(futaberGestureNote(), tag = "futaber-settings-gesture-note")
}

/** C12: this app's terms, the same text as the first-launch screen. */
@Composable
private fun FutaberEulaPage() {
    val colors = LocalFutaberColors.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).testTag("futaber-settings-eula-page")) {
        Text(FUTABER_EULA_TITLE, color = colors.body, fontSize = 14.sp, modifier = Modifier.padding(bottom = 2.dp))
        Text(FUTABER_EULA_SUBTITLE, color = colors.body, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        Text(FUTABER_EULA_INTRO, color = colors.body, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        FUTABER_EULA_SECTIONS.forEach { (title, body) ->
            Text(title, color = colors.body, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(body, color = colors.body, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
        }
        Text(FUTABER_EULA_FOOTER, color = colors.body, fontSize = 12.sp)
    }
}

/** 拡張機能: the other modes' features this mode can take on (most are off by default). */
@Composable
private fun FutaberSettingsExtrasPage(
    settings: FutaberDisplaySettings,
    onSettingChange: (String, String) -> Unit,
    boardPlacementPreferences: Map<String, String>,
    mediaServices: FutaberMediaServices?,
    onPickFont: () -> Unit
) {
    val colors = LocalFutaberColors.current
    // Shared by every mode, so not under "スレッド" (L26).
    SettingsSection("板一覧") {
        SwitchRow("板一覧を下寄せ", "boards-from-bottom", com.valoser.futacha.shared.ui.board.boardsFromBottom(boardPlacementPreferences), last = true) {
            onSettingChange(com.valoser.futacha.shared.ui.board.BOARDS_FROM_BOTTOM_KEY, it.toString())
        }
        Text(
            com.valoser.futacha.shared.ui.board.BOARDS_FROM_BOTTOM_NOTE, color = colors.meta, fontSize = 13.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).testTag("futaber-settings-boards-from-bottom-note")
        )
    }
    SettingsSection("スレッド") {
        SwitchRow("そうだねボタンをレスに表示", "ext-quick-saidane", settings.extQuickSaidane) {
            onSettingChange(FutaberSettingKeys.EXT_QUICK_SAIDANE, if (it) "ON" else "OFF")
        }
        SwitchRow("NGボタンをレスに表示", "ext-quick-ng", settings.extQuickNg) {
            onSettingChange(FutaberSettingKeys.EXT_QUICK_NG, if (it) "ON" else "OFF")
        }
        SwitchRow("ツリー表示", "ext-tree", settings.extTree) {
            onSettingChange(FutaberSettingKeys.EXT_TREE, if (it) "ON" else "OFF")
        }
        SwitchRow("レスを抽出（操作メニュー）", "ext-extract", settings.extExtract) {
            onSettingChange(FutaberSettingKeys.EXT_EXTRACT, if (it) "ON" else "OFF")
        }
        SwitchRow("IDをタップして同じIDのレスを表示", "ext-id-tap", settings.extIdTap) {
            onSettingChange(FutaberSettingKeys.EXT_ID_TAP, if (it) "ON" else "OFF")
        }
        if (!com.valoser.futacha.shared.ui.isIosReviewPlatform()) {
            SwitchRow("ボリュームキーで1画面スクロール", "ext-volume-keys", settings.extVolumeKeys) {
                onSettingChange(FutaberSettingKeys.EXT_VOLUME_KEYS, if (it) "ON" else "OFF")
            }
            if (settings.extVolumeKeys) {
                // What the keys do; the first is what they did before there was a choice.
                FutaberVolumeKeyMode.entries.forEach { mode ->
                    CheckRow("　${mode.label}", "volume-key-${mode.persistedValue}", settings.volumeKeyMode == mode) {
                        onSettingChange(FutaberSettingKeys.EXT_VOLUME_KEY_MODE, mode.persistedValue)
                    }
                }
            }
        }
        SwitchRow("開いた時の新着・自分への返信件数", "ext-open-summary", settings.extOpenSummary) {
            onSettingChange(FutaberSettingKeys.EXT_OPEN_SUMMARY, if (it) "ON" else "OFF")
        }
        SwitchRow(FUTABER_REPORT_NG_SWITCH, "ext-report-ng", settings.extReportNg) {
            onSettingChange(FutaberSettingKeys.EXT_REPORT_NG, if (it) "ON" else "OFF")
        }
        SwitchRow(FUTABER_APU_PREVIEW_SWITCH, "ext-apu-preview", settings.extApuPreview) {
            onSettingChange(FutaberSettingKeys.EXT_APU_PREVIEW, if (it) "ON" else "OFF")
        }
        SwitchRow("検索結果の前後移動（▲▼。検索ワードで絞り込む時）", "ext-search-nav", settings.extSearchNav) {
            onSettingChange(FutaberSettingKeys.EXT_SEARCH_NAV, if (it) "ON" else "OFF")
        }
        SwitchRow("1ページ上下・先頭へ（操作メニュー）", "ext-scroll-menu", settings.extScrollMenu) {
            onSettingChange(FutaberSettingKeys.EXT_SCROLL_MENU, if (it) "ON" else "OFF")
        }
        SwitchRow("URL・アーカイブ（操作メニュー）", "ext-archive-menu", settings.extArchiveMenu) {
            onSettingChange(FutaberSettingKeys.EXT_ARCHIVE_MENU, if (it) "ON" else "OFF")
        }
        SwitchRow("レスの詳細操作（画像だけ消す・項目コピー・関連レスなど）", "ext-post-detail-ops", settings.extPostDetailOps) {
            onSettingChange(FutaberSettingKeys.EXT_POST_DETAIL_OPS, if (it) "ON" else "OFF")
        }
        SwitchRow("画像の長押しメニューに保存・共有", "ext-image-menu", settings.extImageMenu) {
            onSettingChange(FutaberSettingKeys.EXT_IMAGE_MENU, if (it) "ON" else "OFF")
        }
        SwitchRow("NGの詳細管理（操作メニュー・カタログ更新の長押し）", "ext-ng-detail", settings.extNgDetail) {
            onSettingChange(FutaberSettingKeys.EXT_NG_DETAIL, if (it) "ON" else "OFF")
        }
        SwitchRow("画像一覧で選択保存（ZIP・フォルダ）", "ext-gallery-select-save", settings.extGallerySelectSave) {
            onSettingChange(FutaberSettingKeys.EXT_GALLERY_SELECT_SAVE, if (it) "ON" else "OFF")
        }
        SwitchRow("読み上げの一時停止・位置（「読み上げ中」をタップ）", "ext-read-aloud-panel", settings.extReadAloudPanel) {
            onSettingChange(FutaberSettingKeys.EXT_READ_ALOUD_PANEL, if (it) "ON" else "OFF")
        }
        SwitchRow("オートスクロール（操作メニュー）", "ext-auto-scroll", settings.extAutoScroll, last = !settings.extAutoScroll) {
            onSettingChange(FutaberSettingKeys.EXT_AUTO_SCROLL, if (it) "ON" else "OFF")
        }
        if (settings.extAutoScroll) {
            StepperRow(
                label = "スクロール量 = ${settings.autoScrollPixel}px", tag = "auto-scroll-pixel",
                canDecrease = settings.autoScrollPixel > FUTABER_AUTO_SCROLL_PIXEL_MIN,
                canIncrease = settings.autoScrollPixel < FUTABER_AUTO_SCROLL_PIXEL_MAX,
                onDecrease = {
                    onSettingChange(
                        FutaberSettingKeys.AUTO_SCROLL_PIXEL,
                        futaberStep(settings.autoScrollPixel, -1, FUTABER_AUTO_SCROLL_PIXEL_MIN, FUTABER_AUTO_SCROLL_PIXEL_MAX).toString()
                    )
                },
                onIncrease = {
                    onSettingChange(
                        FutaberSettingKeys.AUTO_SCROLL_PIXEL,
                        futaberStep(settings.autoScrollPixel, 1, FUTABER_AUTO_SCROLL_PIXEL_MIN, FUTABER_AUTO_SCROLL_PIXEL_MAX).toString()
                    )
                }
            )
            StepperRow(
                label = "間隔 = ${settings.autoScrollSpeedMillis}ミリ秒", tag = "auto-scroll-speed", last = true,
                canDecrease = settings.autoScrollSpeedMillis > FUTABER_AUTO_SCROLL_SPEED_MIN,
                canIncrease = settings.autoScrollSpeedMillis < FUTABER_AUTO_SCROLL_SPEED_MAX,
                onDecrease = {
                    onSettingChange(
                        FutaberSettingKeys.AUTO_SCROLL_SPEED,
                        futaberStep(settings.autoScrollSpeedMillis, -5, FUTABER_AUTO_SCROLL_SPEED_MIN, FUTABER_AUTO_SCROLL_SPEED_MAX).toString()
                    )
                },
                onIncrease = {
                    onSettingChange(
                        FutaberSettingKeys.AUTO_SCROLL_SPEED,
                        futaberStep(settings.autoScrollSpeedMillis, 5, FUTABER_AUTO_SCROLL_SPEED_MIN, FUTABER_AUTO_SCROLL_SPEED_MAX).toString()
                    )
                }
            )
        }
    }
    SettingsSection("カタログ") {
        SwitchRow("板ごとに表示順序を記憶", "ext-sort-by-board", settings.extSortByBoard) {
            onSettingChange(FutaberSettingKeys.EXT_SORT_BY_BOARD, if (it) "ON" else "OFF")
        }
        SwitchRow("カタログ長押しの追加項目（画像NG）", "ext-catalog-long-press", settings.extCatalogLongPress) {
            onSettingChange(FutaberSettingKeys.EXT_CATALOG_LONG_PRESS, if (it) "ON" else "OFF")
        }
        SwitchRow("表示順序に「そ順」「監視」", "ext-catalog-modes", settings.extCatalogModes) {
            onSettingChange(FutaberSettingKeys.EXT_CATALOG_MODES, if (it) "ON" else "OFF")
        }
        SwitchRow("監視ワードの強調・先頭へ", "ext-catalog-watch-highlight", settings.extCatalogWatchHighlight) {
            onSettingChange(FutaberSettingKeys.EXT_CATALOG_WATCH_HIGHLIGHT, if (it) "ON" else "OFF")
        }
        SwitchRow("レス数による優先表示", "ext-reply-priority", settings.extReplyPriority) {
            onSettingChange(FutaberSettingKeys.EXT_REPLY_PRIORITY, if (it) "ON" else "OFF")
        }
        SwitchRow("更新前のカタログを世代で選ぶ", "ext-catalog-history", settings.extCatalogHistory) {
            onSettingChange(FutaberSettingKeys.EXT_CATALOG_HISTORY, if (it) "ON" else "OFF")
        }
        SwitchRow("キャッシュサーバー検索・ブラウザで開く", "ext-catalog-tools", settings.extCatalogTools) {
            onSettingChange(FutaberSettingKeys.EXT_CATALOG_TOOLS, if (it) "ON" else "OFF")
        }
        SwitchRow("カタログの細かな調整（設定→カタログ）", "ext-catalog-fine-tune", settings.extCatalogFineTune, last = true) {
            onSettingChange(FutaberSettingKeys.EXT_CATALOG_FINE_TUNE, if (it) "ON" else "OFF")
        }
    }
    SettingsSection("書き込み") {
        // The value every mode shares (としあき(仮)の「板名の誤投稿確認」).
        SwitchRow("板名の誤投稿確認", "ext-post-destination", settings.postDestinationConfirm) {
            onSettingChange(FutaberSettingKeys.POST_DESTINATION_CONFIRM, if (it) "ON" else "OFF")
        }
        SwitchRow("メール欄の「ID表示・IP表示・sage」ボタン", "ext-mail-presets", settings.extMailPresets) {
            onSettingChange(FutaberSettingKeys.EXT_MAIL_PRESETS, if (it) "ON" else "OFF")
        }
        SwitchRow("書き込みツール（手書き・あぷ小・音声入力など）", "ext-post-tools", settings.extPostTools) {
            onSettingChange(FutaberSettingKeys.EXT_POST_TOOLS, if (it) "ON" else "OFF")
        }
        SwitchRow("下書きに添付も残す", "ext-draft-attachment", settings.extDraftAttachment, last = true) {
            onSettingChange(FutaberSettingKeys.EXT_DRAFT_ATTACHMENT, if (it) "ON" else "OFF")
        }
    }
    SettingsSection("管理パネル") {
        SwitchRow("履歴の検索・並べ替え・一括更新・印", "ext-history", settings.extHistory) {
            onSettingChange(FutaberSettingKeys.EXT_HISTORY, if (it) "ON" else "OFF")
        }
        SwitchRow("履歴のエクスポート・インポート", "ext-history-archive", settings.extHistoryArchive) {
            onSettingChange(FutaberSettingKeys.EXT_HISTORY_ARCHIVE, if (it) "ON" else "OFF")
        }
        SwitchRow("お気に入りのタブを残す（まとめて閉じる時）", "ext-tabs-manage", settings.extTabsManage) {
            onSettingChange(FutaberSettingKeys.EXT_TABS_MANAGE, if (it) "ON" else "OFF")
        }
        SwitchRow("全タブを確認", "ext-tabs-check", settings.extTabsCheck) {
            onSettingChange(FutaberSettingKeys.EXT_TABS_CHECK, if (it) "ON" else "OFF")
        }
        SwitchRow("カタログでボリュームキーを1画面スクロール（Android）", "ext-catalog-volume-key", settings.extCatalogVolumeKey) {
            onSettingChange(FutaberSettingKeys.EXT_CATALOG_VOLUME_KEY, if (it) "ON" else "OFF")
        }
        SwitchRow("URLから開く（カタログ更新の長押し）", "ext-open-url", settings.extOpenUrl) {
            onSettingChange(FutaberSettingKeys.EXT_OPEN_URL, if (it) "ON" else "OFF")
        }
        SwitchRow("スレッドの高速スクロールバー", "ext-thread-fast-scroll", settings.threadFastScroll) {
            onSettingChange(FutaberSettingKeys.THREAD_FAST_SCROLL, if (it) "ON" else "OFF")
        }
        SwitchRow("カタログの高速スクロールバー", "ext-catalog-fast-scroll", settings.catalogFastScroll) {
            onSettingChange(FutaberSettingKeys.CATALOG_FAST_SCROLL, if (it) "ON" else "OFF")
        }
        SwitchRow("板のピン留め（板一覧の編集）", "ext-board-pin", settings.boardPin) {
            onSettingChange(FutaberSettingKeys.BOARD_PIN, if (it) "ON" else "OFF")
        }
        SwitchRow("落ちたタブを確認して閉じる", "ext-tabs-close-fallen", settings.extTabsCloseFallen) {
            onSettingChange(FutaberSettingKeys.EXT_TABS_CLOSE_FALLEN, if (it) "ON" else "OFF")
        }
        SwitchRow("保存箱の保存状態（長押し）", "ext-saved-detail", settings.extSavedDetail) {
            onSettingChange(FutaberSettingKeys.EXT_SAVED_DETAIL, if (it) "ON" else "OFF")
        }
        SwitchRow("他のモードのお気に入りを表示", "ext-other-favorites", settings.extOtherFavorites) {
            onSettingChange(FutaberSettingKeys.EXT_OTHER_FAVORITES, if (it) "ON" else "OFF")
        }
        // The tab strip and this panel's tab list show it, so it sits with the tab items (L26).
        SwitchRow("タブの隔離・落ち状態を表示", "ext-tab-status", settings.extTabStatus) {
            onSettingChange(FutaberSettingKeys.EXT_TAB_STATUS, if (it) "ON" else "OFF")
        }
        SwitchRow("閉じたタブを元に戻す（タブの長押し）", "ext-tabs", settings.extTabs, last = true) {
            onSettingChange(FutaberSettingKeys.EXT_TABS, if (it) "ON" else "OFF")
        }
    }
    SettingsSection("巡回") {
        SwitchRow("アプリを開いている間も巡回する", "ext-foreground-patrol", settings.extForegroundPatrol,
            last = !com.valoser.futacha.shared.util.isAndroid()) {
            onSettingChange(FutaberSettingKeys.EXT_FOREGROUND_PATROL, if (it) "ON" else "OFF")
        }
        if (com.valoser.futacha.shared.util.isAndroid()) {
            SwitchRow("外部のにじろぐと連携", "ext-external-watcher", settings.extExternalWatcher, last = true) {
                onSettingChange(FutaberSettingKeys.EXT_EXTERNAL_WATCHER, if (it) "ON" else "OFF")
            }
        }
    }
    SettingsSection("保存・表示") {
        SwitchRow("保存形式に「メディアのみ」", "ext-media-only-save", settings.extMediaOnlySave) {
            onSettingChange(FutaberSettingKeys.EXT_MEDIA_ONLY_SAVE, if (it) "ON" else "OFF")
        }
        SwitchRow("閲覧中のスレッドを自動保存（容量を使います）", "ext-auto-save-view", settings.extAutoSaveView) {
            onSettingChange(FutaberSettingKeys.EXT_AUTO_SAVE_VIEW, if (it) "ON" else "OFF")
        }
        val fontRow = settings.extCustomFont && mediaServices != null
        SwitchRow("カスタムフォント", "ext-custom-font", settings.extCustomFont, last = !fontRow) {
            onSettingChange(FutaberSettingKeys.EXT_CUSTOM_FONT, if (it) "ON" else "OFF")
        }
        if (fontRow) {
            NavRow("　フォントを選ぶ", "custom-font", last = true,
                detail = mediaServices?.let { rememberFutaberCustomFontName(it.store) } ?: "未選択") { onPickFont() }
        }
    }
    SettingsSection("画像ビューア") {
        SwitchRow("動画をビューアで再生", "ext-video", settings.extVideo) {
            onSettingChange(FutaberSettingKeys.EXT_VIDEO, if (it) "ON" else "OFF")
        }
        SwitchRow("画像一覧の列数を共通設定に合わせる", "ext-gallery-columns", settings.extGalleryColumns, last = true) {
            onSettingChange(FutaberSettingKeys.EXT_GALLERY_COLUMNS, if (it) "ON" else "OFF")
        }
    }
    SettingsSection("スレッド") {
        StepperRow(
            label = "そうだねが多いレス = ${settings.saidaneThreshold}件以上", tag = "saidane-threshold",
            canDecrease = settings.saidaneThreshold > FUTABER_SAIDANE_THRESHOLD_MIN,
            canIncrease = settings.saidaneThreshold < FUTABER_SAIDANE_THRESHOLD_MAX,
            onDecrease = {
                onSettingChange(FutaberSettingKeys.SAIDANE_THRESHOLD,
                    futaberStep(settings.saidaneThreshold, -1, FUTABER_SAIDANE_THRESHOLD_MIN, FUTABER_SAIDANE_THRESHOLD_MAX).toString())
            },
            onIncrease = {
                onSettingChange(FutaberSettingKeys.SAIDANE_THRESHOLD,
                    futaberStep(settings.saidaneThreshold, 1, FUTABER_SAIDANE_THRESHOLD_MIN, FUTABER_SAIDANE_THRESHOLD_MAX).toString())
            }
        )
        SwitchRow("NGのレスへの返信も隠す", "ext-ng-chain", settings.extNgChain) {
            onSettingChange(FutaberSettingKeys.EXT_NG_CHAIN, if (it) "ON" else "OFF")
        }
        SwitchRow("スレッドを先頭から開く（カタログ・履歴・リンクから）", "ext-open-at-top", settings.extOpenAtTop, last = true) {
            onSettingChange(FutaberSettingKeys.EXT_OPEN_AT_TOP, if (it) "ON" else "OFF")
        }
    }
    SettingsSection("出し方") {
        SwitchRow("追加の項目を「その他の機能」にまとめる", "fold-extras", settings.foldExtras, last = true) {
            onSettingChange(FutaberSettingKeys.FOLD_EXTRAS, if (it) "ON" else "OFF")
        }
    }
    Text(
        FUTABER_EXTRAS_NOTE_INTRO + "\n" +
            "・ツリー表示：引用先のレスの下に返信を並べます\n" +
            "・レスを抽出：自分の書き込み、そうだねが多い、削除されたレス、URLを含む、画像のあるレス\n" +
            "・履歴の検索・並べ替え・一括更新・印：管理パネルの「履歴」の上に帯が出て、行には落ちた・書き込み済み・保存済みの印が付きます\n" +
            "・閉じたタブを元に戻す：タブを長押しした一覧に項目が出ます（この起動中に閉じたタブ）\n" +
            "・動画をビューアで再生：画像ビューアの動画ページが動画ビューア（再生・一時停止・停止・音量）になります。最初からオンで、開いた時は停止した状態です\n" +
            "・メールのボタン：書き込み設定のメール欄に、ふたちゃ・としあき(仮)と同じ値を入れます\n" +
            "・オートスクロールの量と間隔は、ふたちゃ・としあき(仮)と共通の値です\n" +
            "・検索結果の前後移動：検索で絞り込んだ時、上のチップの▲▼で一致したレスを順に移動します\n" +
            "・URL・アーカイブ：FTBucket・つまんね。への登録と、ふたばフォレスト・ふたポで開く（つまんね。はスレのURLを送ります）\n" +
            "・板名の誤投稿確認：img板の「としあき」「スレあき」、may板の「」を含む書き込みは送信前に確認します。ふたちゃ・としあき(仮)と共通の設定です\n" +
            "・お気に入りのタブを残す（最初オフ）：タブの長押しの「右側／他／スレ落ち／すべて削除」と管理パネルの「すべて削除」で、お気に入りのスレッドのタブを閉じません\n" +
            "・全タブを確認：タブを長押しした一覧に出ます。タブのスレッドを読み直して、落ちたスレに印を付けます（チュートリアル板と落ちたスレは確認しません）\n" +
            "・保存箱の保存状態：保存したスレッドを長押しすると、件数・容量・未保存の数を表示します\n" +
            "・他のモードのお気に入り：ふたちゃ・としあき(仮)で星を付けたスレッドを、管理パネルのお気に入りの下に並べます（ここでは編集できません）\n" +
            "・ボリュームキー：1画面スクロール（今までどおり）、1レスずつ、タブのスレッドの切り替え（タブに無いスレッドでは何もしません）から選べます\n" +
            "・レスを抽出の「NGで隠したレス」：NGで隠れているレスだけを表示します（NGを無効にしている時も対象は同じです）\n" +
            "・画像だけ消す：レス削除の確認で選ぶと、本文を残して画像だけを削除します（画像のあるレスだけ）\n" +
            "・画像の長押しメニュー：保存・共有が加わります（画像を表示・ブラウザで開く・画像のURLをコピーは、元のアプリと同じ項目として最初からあります）\n" +
            "・板ごとに表示順序を記憶：カタログの表示順序を板ごとに覚えます。まだ選んでいない板は共通の表示順序です\n" +
            "・カタログ長押しの画像NG：スレッドの画像を、ふたちゃ・としあき(仮)と共通の画像NGに登録します（公式の板だけ）\n" +
            "・画像一覧の列数：画像ビューアの設定（縦持ち・横持ちの列数）に合わせます。オフなら3列です\n" +
            "・書き込みツール：書き込み画面の添付ボタン（カメラ）を長押しすると、手書き・あぷ小へのアップロード・音声入力・回線情報・機種情報を選べます。回線情報は、押した時だけ外部のサービス（ipinfo.io・ipify）へ問い合わせます。書き込みがCookieの理由で失敗した時は「Cookieを確認」が出ます。img板の返信のように画像を添付できない板では、オフでも添付ボタンから手書きを選べます\n" +
            "・下書きに添付も残す：書き込み画面を閉じても、添付した画像・動画を下書きと一緒に端末内に残します。開き直すと、その時の板の制限で確かめてから戻します。送信・クリア・オフにした時に消えます\n" +
            "・履歴の全件削除：「履歴の検索・並べ替え…」をオンにすると、履歴の帯に「全件削除」も出ます。ふたちゃ・としあき(仮)の履歴、自分の書き込みの記録、自動保存したコピーも消えます（タブ・お気に入り・保存箱は残ります）\n" +
            "・履歴のエクスポート・インポート：管理パネルの「履歴」の上に「アーカイブ」が出ます。ふたちゃと同じ履歴アーカイブを、アプリ内の決まった場所に書き出し・読み込みます。読み込んだスレッドの本文は、ふたちゃで開けます\n" +
            "・アプリを開いている間も巡回する：ふたちゃと同じく、巡回・タブの自動確認を1分ごとに確かめて、時間になったものを実行します。間隔と通信の条件は「バックグラウンド」「キーワード通知・巡回」の共通の設定に従います\n" +
            "・外部のにじろぐと連携（Android）：キーワード通知・巡回の画面で「外部にじろぐの結果を表示」を選ぶと、管理パネルの「通知」に、にじろぐ(仮)の結果が並びます\n" +
            "・保存形式に「メディアのみ」：操作メニューの「スレッドを保存」で、画像・動画だけをZIPまたはフォルダに保存する形式も選べます（ふたちゃと同じ保存です）\n" +
            "・カスタムフォント：ふたちゃ・としあき(仮)と共通のフォント（TTF・OTF）をこのモードの文字にも使います。「フォントを選ぶ」で変えると、他のモードのフォントも変わります\n" +
            "・表示順序に「そ順」「監視」：カタログの表示順序に2つ加わります。「監視」は、キーワード通知・巡回のキーワードと、ふたちゃ・としあき(仮)のカタログ抽出ルールに一致するスレッドを、新順・古順のカタログから集めます\n" +
            "・監視ワードの強調・先頭へ：同じ語句に一致するスレッドに小さな印を付け、カタログの先頭に寄せます\n" +
            "・レス数による優先表示：カタログの更新ボタンを長押しした一覧で、板ごとにオン・オフと「少ないレスを隠す」を選べます。基準のレス数は設定→カタログで決めます（ふたちゃ・としあき(仮)と共通の設定です。公式の板だけ）\n" +
            "・更新前のカタログを世代で選ぶ：更新ボタンの長押しで、覚えている更新前のカタログ（最大4回前）から選んで戻せます（アプリを閉じると消えます）\n" +
            "・キャッシュサーバー検索・ブラウザで開く：更新ボタンの長押しとスレッドの操作メニューから、キャッシュサーバーの検索（ふたちゃ・としあき(仮)と同じ。検索した時だけ外部のサーバーへ語句を送ります）と、ブラウザでカタログを開けます\n" +
            "・カタログの細かな調整：設定→カタログに、横3〜横8の表示形式ごとの列数（2〜8列）と、グリッドの題名の行数（1〜3行）・文字数（0〜20字。0で題名を出さない）が出ます。値を変えていなければ、今までと同じ表示です\n" +
            "・閲覧中のスレッドを自動保存：ふたちゃと同じく、スレッドを開くたびに本文と画像・動画を端末に保存します（容量を使います）。開いて約30秒後に保存し、その後は内容が変わった時に1分ごとに保存し直します。原寸の画像・動画はWi-Fiなど従量制でない回線の時だけ保存し、それ以外はサムネイルだけです。通信できない時は、この保存したコピーを表示します。保存したコピー・過去ログ・MHTを表示している時は保存しません。履歴を消すと、保存したコピーも消えます",
        color = colors.settingsHeader, fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).testTag("futaber-settings-extras-note")
    )
}

/** テーマ (C13): futaber's four, then this app's "follow the device". */
@Composable
private fun FutaberSettingsThemePage(themeMode: FutaberThemeMode, onThemeModeChange: (FutaberThemeMode) -> Unit) {
    // The original's four themes, in its order and under its heading (C13); this app's "follow the
    // device" comes after them.
    SettingsSection("システムテーマ") {
        val original = FutaberThemeMode.original
        original.forEachIndexed { index, mode ->
            CheckRow(mode.label, "theme-${mode.persistedValue}", mode == themeMode, last = index == original.lastIndex) {
                onThemeModeChange(mode)
            }
        }
    }
    SettingsSection(null) {
        CheckRow(FutaberThemeMode.System.label, "theme-${FutaberThemeMode.System.persistedValue}",
            themeMode == FutaberThemeMode.System, last = true) {
            onThemeModeChange(FutaberThemeMode.System)
        }
    }
}

/** カタログ: futaber's rows, then the extensions' rows (shown only while their extension is on). */
@Composable
private fun FutaberSettingsCatalogPage(settings: FutaberDisplaySettings, onSettingChange: (String, String) -> Unit) {
    SettingsSection("カタログの行表示") {
        CheckRow("デフォルト", "catalog-row-default", !settings.catalogRowWide) {
            onSettingChange(FutaberSettingKeys.CATALOG_ROW_WIDE, "OFF")
        }
        CheckRow("やや広め", "catalog-row-wide", settings.catalogRowWide, last = true) {
            onSettingChange(FutaberSettingKeys.CATALOG_ROW_WIDE, "ON")
        }
    }
    SettingsSection(null) {
    SwitchRow(
        "カタログ更新時に先頭までスクロール", "catalog-scroll-top", settings.scrollCatalogToTopOnRefresh, last = true
    ) { onSettingChange(FutaberSettingKeys.CATALOG_SCROLL_TOP, if (it) "ON" else "OFF") }
    }
    // B7a: the shared threshold of "レス数による優先表示" (shown only while the extension is on).
    if (settings.extReplyPriority) SettingsSection("レス数による優先表示") {
        StepperRow(
            label = "少ないレスの基準 = ${settings.replyPriorityThreshold}", tag = "reply-priority-threshold", last = true,
            canDecrease = settings.replyPriorityThreshold > FUTABER_REPLY_PRIORITY_MIN,
            canIncrease = settings.replyPriorityThreshold < FUTABER_REPLY_PRIORITY_MAX,
            onDecrease = {
                onSettingChange(
                    FutaberSettingKeys.REPLY_PRIORITY_THRESHOLD,
                    futaberStep(settings.replyPriorityThreshold, -1, FUTABER_REPLY_PRIORITY_MIN, FUTABER_REPLY_PRIORITY_MAX).toString()
                )
            },
            onIncrease = {
                onSettingChange(
                    FutaberSettingKeys.REPLY_PRIORITY_THRESHOLD,
                    futaberStep(settings.replyPriorityThreshold, 1, FUTABER_REPLY_PRIORITY_MIN, FUTABER_REPLY_PRIORITY_MAX).toString()
                )
            }
        )
    }
    // B9 (C06, FutaberParityB9.kt): the grid's own adjustments (shown only while the extension is on).
    if (settings.extCatalogFineTune) SettingsSection("カタログの細かな調整") {
        val fine = settings.catalogFine
        futaberGridStyles.forEach { style ->
            val columns = futaberCatalogColumns(style, fine) ?: return@forEach
            StepperRow(
                label = "${style.label}の列数 = $columns", tag = "catalog-columns-${style.persistedValue}",
                canDecrease = columns > FUTABER_GRID_COLUMNS_MIN,
                canIncrease = columns < FUTABER_GRID_COLUMNS_MAX,
                onDecrease = {
                    onSettingChange(
                        FutaberCatalogFineKeys.gridColumns(style),
                        futaberStep(columns, -1, FUTABER_GRID_COLUMNS_MIN, FUTABER_GRID_COLUMNS_MAX).toString()
                    )
                },
                onIncrease = {
                    onSettingChange(
                        FutaberCatalogFineKeys.gridColumns(style),
                        futaberStep(columns, 1, FUTABER_GRID_COLUMNS_MIN, FUTABER_GRID_COLUMNS_MAX).toString()
                    )
                }
            )
        }
        StepperRow(
            label = "グリッドの題名の行数 = ${fine.gridTitleLines}", tag = "catalog-title-lines",
            canDecrease = fine.gridTitleLines > FUTABER_GRID_TITLE_LINES_MIN,
            canIncrease = fine.gridTitleLines < FUTABER_GRID_TITLE_LINES_MAX,
            onDecrease = {
                onSettingChange(
                    FutaberCatalogFineKeys.GRID_TITLE_LINES,
                    futaberStep(fine.gridTitleLines, -1, FUTABER_GRID_TITLE_LINES_MIN, FUTABER_GRID_TITLE_LINES_MAX).toString()
                )
            },
            onIncrease = {
                onSettingChange(
                    FutaberCatalogFineKeys.GRID_TITLE_LINES,
                    futaberStep(fine.gridTitleLines, 1, FUTABER_GRID_TITLE_LINES_MIN, FUTABER_GRID_TITLE_LINES_MAX).toString()
                )
            }
        )
        StepperRow(
            label = "グリッドの題名の文字数 = ${futaberGridTitleCharsLabel(fine.gridTitleChars)}", tag = "catalog-title-chars",
            last = true,
            canDecrease = fine.gridTitleChars != 0,
            canIncrease = fine.gridTitleChars != null,
            onDecrease = {
                onSettingChange(FutaberCatalogFineKeys.GRID_TITLE_CHARS, futaberGridTitleCharsAfter(fine.gridTitleChars, -1)?.toString() ?: "")
            },
            onIncrease = {
                onSettingChange(FutaberCatalogFineKeys.GRID_TITLE_CHARS, futaberGridTitleCharsAfter(fine.gridTitleChars, 1)?.toString() ?: "")
            }
        )
    }
}

/**
 * スレッド: futaber's sections in its order ([futaberThreadSettingsSections]: 題名と名前の表示・画像表示サイズ（C21）・返信・
 * スレッド読み上げ（C23）・画面をタップしてスクロール（C25）・その他（C26）), then this app's ([futaberThreadSettingsExtras]).
 */
@Composable
private fun FutaberSettingsThreadPage(
    settings: FutaberDisplaySettings,
    onSettingChange: (String, String) -> Unit,
    services: Boolean,
    onShared: (String) -> Unit
) {
    futaberThreadSettingsSections().forEach { section ->
        when (section) {
            FutaberThreadSettingsSection.TitleName -> SettingsSection(section.title) {
                CheckRow("デフォルト名じゃない時は表示", "thread-title-default", !settings.alwaysShowTitleAndName) {
                    onSettingChange(FutaberSettingKeys.TITLE_NAME_ALWAYS, "OFF")
                }
                CheckRow("常に表示", "thread-title-always", settings.alwaysShowTitleAndName, last = true) {
                    onSettingChange(FutaberSettingKeys.TITLE_NAME_ALWAYS, "ON")
                }
            }
            FutaberThreadSettingsSection.ImageSize -> FutaberThreadImageSizeSection(settings, onSettingChange)
            FutaberThreadSettingsSection.Replies -> SettingsSection(section.title) {
                StepperRow(
                    label = "返信の多いレスの基準値 = ${settings.manyRepliesThreshold}", tag = "many-replies", last = true,
                    canDecrease = settings.manyRepliesThreshold > FUTABER_MANY_REPLIES_MIN,
                    canIncrease = settings.manyRepliesThreshold < FUTABER_MANY_REPLIES_MAX,
                    onDecrease = {
                        onSettingChange(
                            FutaberSettingKeys.MANY_REPLIES,
                            futaberStep(settings.manyRepliesThreshold, -1, FUTABER_MANY_REPLIES_MIN, FUTABER_MANY_REPLIES_MAX).toString()
                        )
                    },
                    onIncrease = {
                        onSettingChange(
                            FutaberSettingKeys.MANY_REPLIES,
                            futaberStep(settings.manyRepliesThreshold, 1, FUTABER_MANY_REPLIES_MIN, FUTABER_MANY_REPLIES_MAX).toString()
                        )
                    }
                )
            }
            FutaberThreadSettingsSection.ReadAloud -> FutaberThreadReadAloudSection(settings, onSettingChange)
            FutaberThreadSettingsSection.TapScroll -> SettingsSection(section.title) {
                // G20 (L-B7) keeps its key and its on default; futaber shows it as two choices here (C25).
                CheckRow(FUTABER_TAP_SCROLL_ON, "thread-tap-scroll-on", settings.tapScroll) {
                    onSettingChange(FutaberSettingKeys.TAP_SCROLL, "ON")
                }
                CheckRow(FUTABER_TAP_SCROLL_OFF, "thread-tap-scroll-off", !settings.tapScroll, last = true) {
                    onSettingChange(FutaberSettingKeys.TAP_SCROLL, "OFF")
                }
            }
            FutaberThreadSettingsSection.Other -> {
                SettingsSection(section.title) {
                    SwitchRow(FUTABER_GOOGLE_LENS_SWITCH, "thread-google-lens", settings.googleLens, last = true) {
                        onSettingChange(FutaberSettingKeys.GOOGLE_LENS, if (it) "ON" else "OFF")
                    }
                }
                FutaberTableNote(FUTABER_GOOGLE_LENS_NOTE, tag = "futaber-settings-google-lens-note")
            }
        }
    }
    futaberThreadSettingsExtras(services).forEach { extra ->
        when (extra) {
            // The shared page (the voice, ずんだもん, and the same pitch / rate as above).
            FutaberThreadSettingsExtra.SpeechVoice -> SettingsSection("読み上げの声", gap = 48.dp) {
                NavRow("読み上げ", "speech", last = true) { onShared("speech") }
            }
            FutaberThreadSettingsExtra.DeletedPosts -> SettingsSection("削除されたレス") {
                NavRow("削除レスの表示方法", "deleted-posts", last = true) { onShared("thread") }
            }
            FutaberThreadSettingsExtra.PostLayout -> SettingsSection("レスの表示間隔") {
                StepperRow(
                    label = "レス間の余白 = ${settings.postGapDp}dp", tag = "post-gap",
                    canDecrease = settings.postGapDp > FUTABER_POST_GAP_MIN,
                    canIncrease = settings.postGapDp < FUTABER_POST_GAP_MAX,
                    onDecrease = { onSettingChange(FutaberSettingKeys.POST_GAP, futaberStep(settings.postGapDp, -2, FUTABER_POST_GAP_MIN, FUTABER_POST_GAP_MAX).toString()) },
                    onIncrease = { onSettingChange(FutaberSettingKeys.POST_GAP, futaberStep(settings.postGapDp, 2, FUTABER_POST_GAP_MIN, FUTABER_POST_GAP_MAX).toString()) }
                )
                SwitchRow("見出しを1行に収める（入りきらない所は…）", "thread-header-one-line", settings.headerOneLine, last = true) {
                    onSettingChange(FutaberSettingKeys.HEADER_ONE_LINE, if (it) "ON" else "OFF")
                }
            }
            // Plan §4.1 item 3: this app's old result of items futaber does its own way, at the end and off by default.
            FutaberThreadSettingsExtra.Legacy -> SettingsSection("従来の動き") {
                futaberLegacySwitches.forEachIndexed { index, switch ->
                    SwitchRow(switch.label, switch.id, settings.legacySwitchOn(switch), last = index == futaberLegacySwitches.lastIndex) {
                        onSettingChange(switch.key, if (it) "ON" else "OFF")
                    }
                }
            }
        }
    }
}

/** C21: futaber's 「オリジナルサイズで表示」/「画像のサイズ比率を指定」 and, when the ratio is chosen, 「比率 = N%」. */
@Composable
private fun FutaberThreadImageSizeSection(settings: FutaberDisplaySettings, onSettingChange: (String, String) -> Unit) {
    SettingsSection(FutaberThreadSettingsSection.ImageSize.title) {
        CheckRow("オリジナルサイズで表示", "thread-image-original", !settings.smallImages) {
            onSettingChange(FutaberSettingKeys.SMALL_IMAGES, "OFF")
        }
        CheckRow("画像のサイズ比率を指定", "thread-image-small", settings.smallImages, last = !settings.smallImages) {
            // The ratio is stored when it is first chosen, so it reads 70% (not the old 「小さく表示」's 50%) from then on.
            if (!settings.smallImages) onSettingChange(FutaberSettingKeys.IMAGE_RATIO, settings.imageRatioPercent.toString())
            onSettingChange(FutaberSettingKeys.SMALL_IMAGES, "ON")
        }
        if (settings.smallImages) {
            val percent = settings.imageRatioPercent
            StepperRow(
                label = "比率 = $percent%", tag = "image-ratio", last = true,
                canDecrease = percent > FUTABER_IMAGE_RATIO_MIN, canIncrease = percent < FUTABER_IMAGE_RATIO_MAX,
                onDecrease = { onSettingChange(FutaberSettingKeys.IMAGE_RATIO, futaberImageRatioStep(percent, -1).toString()) },
                onIncrease = { onSettingChange(FutaberSettingKeys.IMAGE_RATIO, futaberImageRatioStep(percent, 1).toString()) }
            )
        }
    }
}

/**
 * C23: futaber's 「スレッド読み上げ」 −＋ on the shared compat.speech.pitch / rate (every mode reads them). 「引用部分の声の
 * 高さ」 (C24) is this app's own key.
 */
@Composable
private fun FutaberThreadReadAloudSection(settings: FutaberDisplaySettings, onSettingChange: (String, String) -> Unit) {
    SettingsSection(FutaberThreadSettingsSection.ReadAloud.title) {
        val pitch = settings.speechPitchTenths
        StepperRow(
            label = "スレッド読み上げの声の高さ = ${futaberSpeechPitchShown(pitch)}", tag = "speech-pitch",
            canDecrease = futaberSharedSpeechCanStep(pitch, -1), canIncrease = futaberSharedSpeechCanStep(pitch, 1),
            onDecrease = { onSettingChange(com.valoser.futacha.shared.audio.SPEECH_PITCH_KEY, futaberSharedSpeechStep(pitch, -1)) },
            onIncrease = { onSettingChange(com.valoser.futacha.shared.audio.SPEECH_PITCH_KEY, futaberSharedSpeechStep(pitch, 1)) }
        )
        // C24: the pitch of the quoted lines (futaber reads them, lower than the rest).
        val quote = settings.quotePitchTenths
        StepperRow(
            label = "引用部分の声の高さ = ${futaberSpeechPitchShown(quote)}", tag = "speech-quote-pitch",
            canDecrease = futaberSharedSpeechCanStep(quote, -1), canIncrease = futaberSharedSpeechCanStep(quote, 1),
            onDecrease = { onSettingChange(FutaberSettingKeys.QUOTE_PITCH, (quote - 1).toString()) },
            onIncrease = { onSettingChange(FutaberSettingKeys.QUOTE_PITCH, (quote + 1).toString()) }
        )
        val rate = settings.speechRateTenths
        StepperRow(
            label = "スレッド読み上げのスピード = ${futaberSpeechSpeedShown(rate)}", tag = "speech-rate", last = true,
            canDecrease = futaberSharedSpeechCanStep(rate, -1), canIncrease = futaberSharedSpeechCanStep(rate, 1),
            onDecrease = { onSettingChange(com.valoser.futacha.shared.audio.SPEECH_RATE_KEY, futaberSharedSpeechStep(rate, -1)) },
            onIncrease = { onSettingChange(com.valoser.futacha.shared.audio.SPEECH_RATE_KEY, futaberSharedSpeechStep(rate, 1)) }
        )
    }
}

// ---- The rows of the settings, drawn as futaber's table (FutaberGroupedTable.kt), with the tags they always had ----

@Composable
private fun SettingsSection(title: String?, gap: androidx.compose.ui.unit.Dp = 28.dp, content: @Composable ColumnScope.() -> Unit) {
    FutaberTableSection(title, gap = gap, content = content)
}

@Composable
private fun NavRow(label: String, id: String, last: Boolean = false, detail: String? = null, onClick: () -> Unit) {
    FutaberTableNavRow(label, "futaber-settings-row-$id", detail, "futaber-settings-detail-$id", last, onClick)
}

@Composable
private fun InfoRow(label: String, id: String, value: String, last: Boolean = false) {
    FutaberTableInfoRow(label, "futaber-settings-row-$id", value, "futaber-settings-detail-$id", last)
}

@Composable
private fun CheckRow(label: String, id: String, selected: Boolean, last: Boolean = false, onClick: () -> Unit) {
    FutaberTableCheckRow(label, "futaber-settings-row-$id", selected, last, onClick)
}

@Composable
private fun SwitchRow(label: String, id: String, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    // The whole row is the switch for touch and for a screen reader (it reads the label, the role and the state).
    FutaberTableSwitchRow(label, "futaber-settings-switch-$id", checked, last, onChange)
}

@Composable
private fun StepperRow(
    label: String,
    tag: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    last: Boolean = false
) {
    FutaberTableStepperRow(
        label = label, rowTag = "futaber-settings-row-$tag", valueTag = "futaber-settings-value-$tag",
        minusTag = "futaber-settings-minus-$tag", plusTag = "futaber-settings-plus-$tag",
        canDecrease = canDecrease, canIncrease = canIncrease, onDecrease = onDecrease, onIncrease = onIncrease, last = last
    )
}

/**
 * The first lines of the "拡張機能" page: most extensions are off by default and leave the screens as the original
 * app has them, but four things work from the start (each named here, with where it can be turned off if it can).
 */
internal const val FUTABER_EXTRAS_NOTE_INTRO =
    "ふたちゃ・としあき(仮)・ふたったー風にある機能のうち、元のアプリには無いものです。入口はほとんど最初からオンで、futaberの項目の後ろに出ます。オフにするとfutaberの画面に近づきます。\n" +
        "最初オフなのは次です：ツリー表示、NGのレスへの返信も隠す、スレッドを先頭から開く、レスごとのそうだね・NGのボタン、音量キー、板ごとの表示順序、板名の誤投稿確認、画像一覧の選択保存、お気に入りのタブを残す、板一覧の下寄せ。\n" +
        "このほか、閲覧したスレのURLの過去ログ保存サービスへの通知（設定の「閲覧スレ通知」でオフにできます）は最初から働きます。"

/** M23: the switch that brings back the old 「コピー」 (the post's text copied at once, no copy panel). */
internal const val FUTABER_INSTANT_COPY_SWITCH = "レスの「コピー」で本文をすぐコピー"

/** M08: the switch that brings back the old search word of the operation menu (the thread narrowed, the chip). */
internal const val FUTABER_LEGACY_SEARCH_SWITCH = "操作メニューの検索ワードでスレッドを絞り込む"

/** M11: the switch that brings back the old 「返信が多いレスを表示」 (the thread narrowed, the chip). */
internal const val FUTABER_LEGACY_MANY_REPLIES_SWITCH = "「返信が多いレスを表示」でスレッドを絞り込む"

/** F17: the switch that brings back the subject field of a new thread's writing screen. */
internal const val FUTABER_LEGACY_SUBJECT_SWITCH = "スレッド作成で題名の欄を出す"

/** The extension's switch, also named by the operation guide (L26). */
internal const val FUTABER_APU_PREVIEW_SWITCH = "本文のあぷ・あぷ小の画像をレスの中に表示"
internal const val FUTABER_REPORT_NG_SWITCH = "delの確認でこのレスをNGにも登録"


@Composable
private fun ColumnScope.ModeSection() {
    val colors = LocalFutaberColors.current
    val controller = LocalExperienceProfileUiController.current
    var requestedProfile by remember { mutableStateOf<ExperienceProfile?>(null) }
    if (!controller.isAvailable) {
        Text("モードを切り替えられません", color = colors.settingsHeader, modifier = Modifier.padding(16.dp))
        return
    }
    SettingsSection("現在: ${controller.activeProfile.displayName}") {
        val entries = ExperienceProfile.selectableEntries
        entries.forEachIndexed { index, profile ->
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 50.dp)
                        .clickable(enabled = !controller.switchInProgress, onClickLabel = profile.displayName) {
                            if (profile != controller.activeProfile) requestedProfile = profile
                        }
                        .semantics(mergeDescendants = true) { selected = profile == controller.activeProfile; role = Role.RadioButton }
                        .padding(horizontal = 16.dp).testTag("futaber-settings-row-mode-${profile.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(profile.displayName, color = colors.body, fontSize = FUTABER_TABLE_TEXT_SP.sp, modifier = Modifier.weight(1f))
                    if (profile == controller.activeProfile) FutaberIcon(Icons.Outlined.Check, contentDescription = "選択中", tint = colors.iosBlue)
                }
                FutaberTableDivider(index == entries.lastIndex)
            }
        }
    }
    controller.lastError?.let {
        Text("切替に失敗しました: $it", color = colors.settingsHeader, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
    }
    requestedProfile?.let { target ->
        FutachaAppLockAwareWindow {
            AlertDialog(
                onDismissRequest = { requestedProfile = null },
                containerColor = colors.bar,
                titleContentColor = colors.body,
                textContentColor = colors.body,
                title = { Text("${target.displayName}へ切り替えますか？") },
                text = {
                    Text(
                        "画面構成が切り替わり、現在の画面を閉じて板一覧へ戻ります。" +
                            "板一覧・履歴・スレッドキャッシュはモード間で共有されます。" +
                            "モード固有の表示設定はそれぞれ保持されます。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        requestedProfile = null
                        controller.requestSwitch(target)
                    }) { Text("切り替える", color = colors.link) }
                },
                dismissButton = { TextButton(onClick = { requestedProfile = null }) { Text("キャンセル", color = colors.link) } }
            )
        }
    }
}

/**
 * A settings page the modes share (the image viewer's, storage, network, ...), or the help / change log / licence /
 * cookie screens, over the settings screen. The pages stack: a page can open the help, which has its own way back.
 */
@Composable
internal fun FutaberSharedSettingsHost(
    startPath: String,
    services: FutaberMediaServices,
    appVersion: String,
    stateStore: AppStateStore?,
    onClose: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val palette = remember(colors) { futaberMediaPalette(colors) }
    val preferences by services.store.preferences.collectAsState(emptyMap<String, String>())
    var paths by remember { mutableStateOf(listOf(startPath)) }
    val back = { if (paths.size > 1) paths = paths.dropLast(1) else onClose() }
    FutachaAppLockAwareWindow {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CompositionLocalProvider(LocalCompatibilityPalette provides palette) {
                Surface(Modifier.fillMaxSize().testTag("futaber-viewer-settings"), color = colors.background) {
                    PlatformBackHandler(onBack = back)
                    when (val path = paths.last()) {
                        "help" -> CompatHelpScreen(appVersion = appVersion, onBack = back, onOpenChangeLog = { paths = paths + "changelog" })
                        "changelog" -> CompatChangeLogScreen(
                            appVersion = appVersion, store = services.store,
                            onOpenHelp = { paths = paths + "help" }, onBack = back
                        )
                        "license" -> CompatLicenseScreen(onBack = back)
                        "cookies" -> services.cookieRepository?.let { CookieManagementScreen(onBack = back, repository = it) }
                            ?: CompatHelpScreen(appVersion = appVersion, onBack = back)
                        "aimoge" -> com.valoser.futacha.shared.ui.aimoge.AimogeSettingsDialog(services.store, onDismiss = back)
                        // L-B11 (M34・C04): futaber's 「画像ビューア」 page; its last row opens the shared columns / preloading page.
                        "viewer" -> FutaberViewerSettingsStorePage(
                            services.store, onDone = back, onOpenShared = { paths = paths + FUTABER_VIEWER_SHARED_PATH }
                        )
                        else -> CompatSettingsScreen(
                            path = futaberSharedSettingsPath(path),
                            store = services.store,
                            preferences = preferences,
                            fileSystem = services.fileSystem,
                            httpClient = services.httpClient,
                            cookieRepository = services.cookieRepository,
                            appVersion = appVersion,
                            modernPresentation = true,
                            onOpenHelp = { paths = paths + "help" },
                            onNavigate = { paths = paths + it },
                            onBack = back,
                            stateStore = stateStore,
                            // A restored backup's boards are added to the list this mode keeps (else the next sync drops them).
                            restoreBoardsInto = stateStore,
                            // Work started here goes on after the settings are closed, its result told by the app (L58).
                            lastingScope = LocalFutaberAppTasks.current?.scope,
                            onLastingNotice = futaberLastingNotice(LocalFutaberAppTasks.current)
                        )
                    }
                }
            }
        }
    }
}



/** The app's notice of this mode, for a result that comes after a settings page is left (L58). */
internal fun futaberLastingNotice(tasks: FutaberAppTasks?): (String) -> Unit = { message -> tasks?.notify?.invoke(message) }

