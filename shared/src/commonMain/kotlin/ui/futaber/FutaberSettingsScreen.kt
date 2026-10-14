package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

private enum class SettingsRoute(val title: String) {
    Root("設定"), Theme("テーマ"), Catalog("カタログ"), Thread("スレッド"), Gesture("ジェスチャー"), Mode("モード"),
    Ng("NG設定"), NgList("NG"), AppIcon("アプリアイコン"), Extras("拡張機能"), Media("メディア機能")
}

/** The screen the "‹" of [this] goes back to. */
private fun SettingsRoute.parent(): SettingsRoute = if (this == SettingsRoute.NgList) SettingsRoute.Ng else SettingsRoute.Root

/**
 * The NG lists that can be edited here. They are the lists ふたちゃ uses, so an entry made in either
 * mode applies in both. (The regular-expression and per-kind lists of the original app need
 * matching the shared engine does not have, so they are not offered.)
 */
internal enum class FutaberNgListKind(val title: String, val hint: String) {
    CatalogThreads("NGスレッド", "カタログの長押しで「スレッドをNG」にしたスレッドです。外すとカタログに戻ります"),
    CatalogWords("カタログのNGワード", "題名にこの語句を含むスレッドをカタログから隠します（更新後に反映）"),
    ResWords("レスのNGワード", "本文にこの語句を含むレスを隠します"),
    ResHeaders("レスのNGネーム・ID等", "名前・題名・ID・日時にこの語句を含むレスを隠します"),
    ResImages("NG画像", "スレッドの画像の長押しで「画像NGに登録」した画像です。似た画像のレスを隠します。外すとレスが戻ります"),
    CatalogWordRules("NGワード（詳細）", "ふたちゃ・としあき(仮)のNG管理で登録した、題名に語句を含むスレッドを隠すルールです。外すとカタログに戻ります（更新後に反映）"),
    CatalogImages("NG画像（カタログ）", "ふたちゃ・としあき(仮)で登録した、サムネイルの画像で隠すルールです。外すとカタログに戻ります（更新後に反映）"),
    ThreadDetails("スレッド別NG（詳細）", "ふたちゃ・としあき(仮)のNG管理で登録した、レス番号・ID・語句・画像アドレスで隠すルールです。スレッドを開いたときに適用されます。外すとレスが戻ります"),
    /** B7a: not an NG list, but the same add / remove screen (the alert's watch words, shared with ふたちゃ). */
    WatchWords("監視ワード（アラート用）", "")
}

/** One entry of an NG list: [id] is what removing it goes by (the word itself, or the id of the rule). */
internal data class FutaberNgRow(val id: String, val label: String)

/** The rows of a list, the first of every [FutaberNgRow.id] only (a lazy list refuses two items with one key). */
internal fun futaberNgRows(rows: List<FutaberNgRow>): List<FutaberNgRow> = rows.distinctBy { it.id }

/**
 * The settings as the original app lays them out: a list with a "完了" button, sections of
 * rows, and sub-screens for the theme, the catalog, the thread and the mode. What has its own
 * screen elsewhere (posting, NG, the image viewer, notifications) opens that screen from here.
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
    /** The image NG rules (registered from a picture's long press in any mode). */
    imageNgRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    /** The catalog rules of the other modes that hide threads by a word of the title / by their picture. */
    catalogWordRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    catalogImageRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    /** The thread rules of the other modes (post number, poster id, words, picture address) that hide posts here, and the titles their scopes stand for. */
    threadDetailRules: List<com.valoser.futacha.shared.compat.CompatNgRule> = emptyList(),
    threadNgScopeTitles: () -> Map<String, String> = { emptyMap() },
    /** Tells the person something went wrong (a setting that could not be saved). */
    onNotice: (String) -> Unit = {},
    /** The shared "閲覧スレ通知" switch was changed (the host starts or stops its sender). */
    onArchiveReportEnabledChanged: (Boolean) -> Unit = {},
    /** B7a: the watch words of "監視ワード自動アラート" (every board) and how many boards keep their own; null = no editor. */
    watchWords: List<String> = emptyList(),
    boardWatchWordCount: Int = 0,
    onEditWatchWords: (((List<String>) -> List<String>) -> Unit)? = null
) {
    val colors = LocalFutaberColors.current
    var route by rememberSaveable { mutableStateOf(SettingsRoute.Root) }
    var postingOpen by rememberSaveable { mutableStateOf(false) }
    var ngKind by rememberSaveable { mutableStateOf(FutaberNgListKind.ResWords) }
    // The shared settings page (or help / change log / licence / cookies) shown over this screen; null = none.
    var sharedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val updateCheckEnabled by (stateStore?.isUpdateCheckEnabled ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(true)
    val backgroundRefreshEnabled by (stateStore?.isBackgroundRefreshEnabled ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)
    val watchAlertEnabled by (stateStore?.isWatchAlertEnabled ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)
    val lightweightEnabled by (stateStore?.isLightweightModeEnabled ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)
    val lockHash by (stateStore?.appLockPasswordHash ?: kotlinx.coroutines.flow.flowOf<String?>(null)).collectAsState(null)
    val appIcon by (stateStore?.appIconVariant ?: kotlinx.coroutines.flow.flowOf(com.valoser.futacha.shared.model.AppIconVariant.Current))
        .collectAsState(com.valoser.futacha.shared.model.AppIconVariant.Current)
    var lockDialog by rememberSaveable { mutableStateOf(false) }
    var fontDialog by rememberSaveable { mutableStateOf(false) }
    var fileManagerDialog by rememberSaveable { mutableStateOf(false) }
    val showFileManagerRow = stateStore != null && com.valoser.futacha.shared.util.isAndroid()
    fun store(block: suspend (AppStateStore) -> Unit) {
        val target = stateStore ?: return
        scope.launch {
            try { block(target) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) {
                com.valoser.futacha.shared.util.Logger.e("FutaberSettings", "Failed to save a setting", error)
                onNotice(FUTABER_STORE_FAILURE_NOTICE)
            }
        }
    }
    val goBack: () -> Unit = { route = if (route == SettingsRoute.NgList && ngKind == FutaberNgListKind.WatchWords) SettingsRoute.Root else route.parent() }
    PlatformBackHandler(onBack = { if (route == SettingsRoute.Root) onClose() else goBack() })

    // The page is a step away from the cards: warm grey under white cards, the page grey under lighter dark cards.
    val pageColor = if (colors.isDark) colors.background else colors.catalogGap
    // The screen takes every touch itself: the header's empty parts and the gaps between rows would otherwise let a touch
    // fall through to the catalog's top bar (the "＋" and the gear) under it.
    Column(Modifier.fillMaxSize().background(pageColor).pointerInput(Unit) {}.testTag("futaber-settings")) {
        Row(
            Modifier.fillMaxWidth().background(colors.bar).statusBarsPadding().heightIn(min = FUTABER_TOP_BAR_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.widthIn(min = 96.dp), contentAlignment = Alignment.CenterStart) {
                if (route == SettingsRoute.Root) {
                    TextButton(onClick = onClose, modifier = Modifier.testTag("futaber-settings-done")) {
                        Text("完了", color = colors.link, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = goBack, modifier = Modifier.testTag("futaber-settings-back")) {
                        Text(if (route == SettingsRoute.NgList && ngKind != FutaberNgListKind.WatchWords) "‹ NG設定" else "‹ 設定", color = colors.link)
                    }
                }
            }
            Text(
                if (route == SettingsRoute.NgList) ngKind.title else route.title,
                color = colors.body, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f).testTag("futaber-settings-title")
            )
            Spacer(Modifier.widthIn(min = 96.dp))
        }
        HorizontalDivider(color = colors.separator, thickness = 0.5.dp)
        if (route == SettingsRoute.NgList) {
            // A long list scrolls lazily, so it is not inside the scrolling column of the other pages.
            when (ngKind) {
                FutaberNgListKind.ResImages -> NgListScreen(
                    ngKind,
                    futaberNgRows(imageNgRules.mapIndexed { index, rule -> FutaberNgRow(rule.id, "${index + 1}. " + futaberImageNgRuleLabel(rule)) }),
                    onAdd = null, canAdd = { false }, onRemove = { onDeleteNgRule(it.id) }
                )
                FutaberNgListKind.CatalogThreads -> NgListScreen(
                    ngKind,
                    futaberNgRows(catalogThreadRules.map { FutaberNgRow(it.id, futaberCatalogThreadRuleLabel(it)) }),
                    onAdd = null, canAdd = { false }, onRemove = { onDeleteNgRule(it.id) }
                )
                FutaberNgListKind.CatalogWordRules -> NgListScreen(
                    ngKind,
                    futaberNgRows(catalogWordRules.map { FutaberNgRow(it.id, futaberCatalogWordRuleLabel(it)) }),
                    onAdd = null, canAdd = { false }, onRemove = { onDeleteNgRule(it.id) }
                )
                FutaberNgListKind.CatalogImages -> NgListScreen(
                    ngKind,
                    futaberNgRows(catalogImageRules.map { FutaberNgRow(it.id, futaberCatalogImageRuleLabel(it)) }),
                    onAdd = null, canAdd = { false }, onRemove = { onDeleteNgRule(it.id) }
                )
                FutaberNgListKind.ThreadDetails -> NgListScreen(
                    ngKind,
                    run {
                        val titles = remember { threadNgScopeTitles() }
                        futaberNgRows(threadDetailRules.map { FutaberNgRow(it.id, futaberThreadDetailNgRuleLabel(it, titles)) })
                    },
                    onAdd = null, canAdd = { false }, onRemove = { onDeleteNgRule(it.id) }
                )
                FutaberNgListKind.CatalogWords -> NgWordListScreen(ngKind, catalogNgWords, onEditCatalogNgWords)
                FutaberNgListKind.ResWords -> NgWordListScreen(ngKind, ngWords, onEditNgWords)
                FutaberNgListKind.ResHeaders -> NgWordListScreen(ngKind, ngHeaders, onEditNgHeaders)
                FutaberNgListKind.WatchWords -> NgWordListScreen(
                    ngKind, watchWords, onEditWatchWords ?: {}, hint = futaberWatchWordsHint(boardWatchWordCount)
                )
            }
        } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            when (route) {
                SettingsRoute.Root -> {
                    SettingsSection("全般") {
                        NavRow("テーマ", "theme") { route = SettingsRoute.Theme }
                        NavRow("カタログ", "catalog") { route = SettingsRoute.Catalog }
                        NavRow("スレッド", "thread") { route = SettingsRoute.Thread }
                        if (mediaServices != null) NavRow("画像ビューア", "viewer") { sharedPath = "viewer" }
                        StepperRow(
                            label = "フォントサイズ = ${settings.fontSize}", tag = "font-size",
                            canDecrease = settings.fontSize > FUTABER_FONT_SIZE_MIN,
                            canIncrease = settings.fontSize < FUTABER_FONT_SIZE_MAX,
                            onDecrease = {
                                onSettingChange(
                                    FutaberSettingKeys.FONT_SIZE,
                                    futaberStep(settings.fontSize, -1, FUTABER_FONT_SIZE_MIN, FUTABER_FONT_SIZE_MAX).toString()
                                )
                            },
                            onIncrease = {
                                onSettingChange(
                                    FutaberSettingKeys.FONT_SIZE,
                                    futaberStep(settings.fontSize, 1, FUTABER_FONT_SIZE_MIN, FUTABER_FONT_SIZE_MAX).toString()
                                )
                            }
                        )
                        NavRow("書き込み設定", "posting") { postingOpen = true }
                        NavRow("ジェスチャー", "gesture") { route = SettingsRoute.Gesture }
                        NavRow("NG", "ng", last = true) { route = SettingsRoute.Ng }
                    }
                    if (mediaServices != null) {
                        SettingsSection("通知") {
                            SwitchRow("通知を有効にする", "patrol-enabled", settings.patrolEnabled) {
                                onSettingChange(FutaberSettingKeys.PATROL_ENABLED, if (it) "ON" else "OFF")
                            }
                            NavRow("キーワード通知・巡回", "notifications", last = true) { onOpenNotifications() }
                        }
                    }
                    if (mediaServices != null) {
                        // These open the settings pages every mode shares (the same values, the same look as the viewer settings).
                        SettingsSection("データ・通信") {
                            NavRow("保存先・キャッシュ", "storage") { sharedPath = "storage" }
                            NavRow("ネットワーク", "network") { sharedPath = "network" }
                            NavRow("バックグラウンド", "background") { sharedPath = "background" }
                            NavRow("バックアップ・復元", "backup", last = mediaServices.cookieRepository == null) { sharedPath = "backup" }
                            if (mediaServices.cookieRepository != null) NavRow("Cookie管理", "cookies", last = true) { sharedPath = "cookies" }
                        }
                        SettingsSection("機能") {
                            NavRow("画像検索", "image-search") { sharedPath = "image_search" }
                            NavRow("メディア機能", "media") { route = SettingsRoute.Media }
                            NavRow("プライバシー表示", "privacy") { sharedPath = "privacy" }
                            NavRow("AI・補助機能", "ai", last = true) { sharedPath = "ai" }
                        }
                    }
                    if (stateStore != null) {
                        SettingsSection("動作") {
                            SwitchRow("アップデート確認", "update-check", updateCheckEnabled) { next -> store { it.setUpdateCheckEnabled(next) } }
                            SwitchRow("バックグラウンド更新", "background-refresh", backgroundRefreshEnabled) { next -> store { it.setBackgroundRefreshEnabled(next) } }
                            SwitchRow("監視ワード自動アラート", "watch-alert", watchAlertEnabled) { next ->
                                onWatchAlertSettingChangeRequested?.invoke(next) ?: store { it.setWatchAlertEnabled(next) }
                            }
                            // B7a: the words the alert looks for (shared with ふたちゃ); nothing else in this mode edits them.
                            if (onEditWatchWords != null) {
                                NavRow("監視ワード（アラート用）", "watch-words", detail = watchWords.size.toString()) {
                                    ngKind = FutaberNgListKind.WatchWords; route = SettingsRoute.NgList
                                }
                            }
                            SwitchRow("軽量モード", "lightweight", lightweightEnabled) { next -> store { it.setLightweightModeEnabled(next) } }
                            NavRow("起動ロック", "app-lock", last = true, detail = if (lockHash.isNullOrBlank()) "無効" else "有効") { lockDialog = true }
                        }
                    }
                    SettingsSection("このアプリ") {
                        NavRow("拡張機能", "extras") { route = SettingsRoute.Extras }
                        if (stateStore != null) NavRow("アプリアイコン", "app-icon", detail = appIcon.label) { route = SettingsRoute.AppIcon }
                        NavRow("モード", "mode", last = !showFileManagerRow) { route = SettingsRoute.Mode }
                        if (showFileManagerRow && stateStore != null) {
                            NavRow("優先ファイルマネージャー", "preferred-file-manager", last = true,
                                detail = rememberFutaberPreferredFileManagerLabel(stateStore)) { fileManagerDialog = true }
                        }
                    }
                    // "閲覧スレ通知" is on unless turned off (the value every mode shares); the desktop app sends nothing.
                    if (mediaServices != null && !com.valoser.futacha.shared.util.isDesktop()) {
                        SettingsSection("閲覧スレ通知") {
                            SwitchRow(
                                com.valoser.futacha.shared.ui.board.VIEWED_THREAD_ARCHIVE_REPORT_LABEL,
                                "archive-report", settings.archiveReportEnabled, last = true
                            ) {
                                onSettingChange(FutaberSettingKeys.ARCHIVE_REPORT, if (it) "ON" else "OFF")
                                onArchiveReportEnabledChanged(it)
                            }
                        }
                        Text(
                            com.valoser.futacha.shared.ui.board.VIEWED_THREAD_ARCHIVE_REPORT_DESCRIPTION,
                            color = colors.meta, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp).testTag("futaber-settings-archive-report-note")
                        )
                    }
                    if (mediaServices != null) {
                        SettingsSection("情報・サポート") {
                            NavRow("ヘルプ", "help") { sharedPath = "help" }
                            NavRow("更新情報", "changelog") { sharedPath = "changelog" }
                            NavRow("ライセンス", "license") { sharedPath = "license" }
                            InfoRow("バージョン", "version", appVersion, last = true)
                        }
                    }
                }
                SettingsRoute.Ng -> {
                    SettingsSection("カタログNG（編集した後更新で反映）") {
                        NavRow("NGワード", "ng-catalog-words", detail = catalogNgWords.size.toString()) {
                            ngKind = FutaberNgListKind.CatalogWords; route = SettingsRoute.NgList
                        }
                        NavRow("NGスレッド", "ng-catalog-threads", detail = catalogThreadRules.size.toString()) {
                            ngKind = FutaberNgListKind.CatalogThreads; route = SettingsRoute.NgList
                        }
                        // What the other modes registered also hides threads here, so it is listed and can be taken back here.
                        NavRow("NGワード（詳細）", "ng-catalog-word-rules", detail = catalogWordRules.size.toString()) {
                            ngKind = FutaberNgListKind.CatalogWordRules; route = SettingsRoute.NgList
                        }
                        NavRow("NG画像", "ng-catalog-images", last = true, detail = catalogImageRules.size.toString()) {
                            ngKind = FutaberNgListKind.CatalogImages; route = SettingsRoute.NgList
                        }
                    }
                    SettingsSection("レスNG") {
                        NavRow("NGワード", "ng-res-words", detail = ngWords.size.toString()) {
                            ngKind = FutaberNgListKind.ResWords; route = SettingsRoute.NgList
                        }
                        NavRow("NGネーム・ID等", "ng-res-headers", detail = ngHeaders.size.toString()) {
                            ngKind = FutaberNgListKind.ResHeaders; route = SettingsRoute.NgList
                        }
                        NavRow("NG画像", "ng-res-images", last = threadDetailRules.isEmpty(), detail = imageNgRules.size.toString()) {
                            ngKind = FutaberNgListKind.ResImages; route = SettingsRoute.NgList
                        }
                        // Shown only to a person who has such rules: they hide posts here, so they must be visible and removable.
                        if (threadDetailRules.isNotEmpty()) {
                            NavRow("スレッド別NG（詳細）", "ng-res-thread-details", last = true, detail = threadDetailRules.size.toString()) {
                                ngKind = FutaberNgListKind.ThreadDetails; route = SettingsRoute.NgList
                            }
                        }
                    }
                }
                SettingsRoute.NgList -> Unit // drawn above, outside the scrolling column
                SettingsRoute.Gesture -> {
                    SettingsSection(null) {
                        SwitchRow("画面をタップしてスクロール", "gesture-tap-scroll", settings.tapScroll) {
                            onSettingChange(FutaberSettingKeys.TAP_SCROLL, if (it) "ON" else "OFF")
                        }
                        SwitchRow(
                            "画像ビューアを↓スワイプで閉じる", "gesture-viewer-swipe", settings.viewerSwipeClose, last = true
                        ) { onSettingChange(FutaberSettingKeys.VIEWER_SWIPE_CLOSE, if (it) "ON" else "OFF") }
                    }
                    Text(
                        "他\n" +
                            "・スレッド内で左端からスワイプで直接メニュー表示（iPhone。Androidでは板一覧）\n" +
                            "・スレッド内でレス長押しで引用その他アクション\n" +
                            "・スレッド内ツールバーの最下部スクロールボタンを長押しで新着へスクロール\n" +
                            "・書き込み画面で下スワイプで引用モード\n" +
                            "・「画面をタップしてスクロール」：スレッドの上1/3をタップで1ページ上、下1/3で1ページ下（引用・ID・URL・画像・返信数のタップはそれぞれの操作）",
                        color = colors.meta, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).testTag("futaber-settings-gesture-note")
                    )
                }
                SettingsRoute.Extras -> {
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
                        SwitchRow("検索結果の前後移動（▲▼）", "ext-search-nav", settings.extSearchNav) {
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
                        SwitchRow("画像の長押しメニューに保存・共有など", "ext-image-menu", settings.extImageMenu) {
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
                        SwitchRow("タブの管理（お気に入りのタブを残す・全タブを確認）", "ext-tabs-manage", settings.extTabsManage) {
                            onSettingChange(FutaberSettingKeys.EXT_TABS_MANAGE, if (it) "ON" else "OFF")
                        }
                        SwitchRow("保存箱の保存状態（長押し）", "ext-saved-detail", settings.extSavedDetail) {
                            onSettingChange(FutaberSettingKeys.EXT_SAVED_DETAIL, if (it) "ON" else "OFF")
                        }
                        SwitchRow("他のモードのお気に入りを表示", "ext-other-favorites", settings.extOtherFavorites) {
                            onSettingChange(FutaberSettingKeys.EXT_OTHER_FAVORITES, if (it) "ON" else "OFF")
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
                                detail = mediaServices?.let { rememberFutaberCustomFontName(it.store) } ?: "未選択") { fontDialog = true }
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
                            "・タブの管理（お気に入りのタブを残す・全タブを確認）：タブの長押しの「右側／他／スレ落ち／すべて削除」と管理パネルの「すべて削除」で、お気に入りのスレッドのタブを閉じません。また、タブを長押しした一覧に「全タブを確認」が出ます。タブのスレッドを読み直して、落ちたスレに印を付けます（チュートリアル板と落ちたスレは確認しません）\n" +
                            "・保存箱の保存状態：保存したスレッドを長押しすると、件数・容量・未保存の数を表示します\n" +
                            "・他のモードのお気に入り：ふたちゃ・としあき(仮)で星を付けたスレッドを、管理パネルのお気に入りの下に並べます（ここでは編集できません）\n" +
                            "・ボリュームキー：1画面スクロール（今までどおり）、1レスずつ、タブのスレッドの切り替え（タブに無いスレッドでは何もしません）から選べます\n" +
                            "・レスを抽出の「NGで隠したレス」：NGで隠れているレスだけを表示します（NGを無効にしている時も対象は同じです）\n" +
                            "・画像だけ消す：レス削除の確認で選ぶと、本文を残して画像だけを削除します（画像のあるレスだけ）\n" +
                            "・画像の長押しメニュー：保存・共有・URLをコピー・外部で開く・ビューアで表示が加わります\n" +
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
                        color = colors.meta, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).testTag("futaber-settings-extras-note")
                    )
                }
                SettingsRoute.Theme -> SettingsSection("テーマ") {
                    val entries = FutaberThemeMode.entries
                    entries.forEachIndexed { index, mode ->
                        CheckRow(mode.label, "theme-${mode.persistedValue}", mode == themeMode, last = index == entries.lastIndex) {
                            onThemeModeChange(mode)
                        }
                    }
                }
                SettingsRoute.Catalog -> {
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
                SettingsRoute.Thread -> {
                    SettingsSection("題名と名前の表示") {
                        CheckRow("デフォルト名じゃない時は表示", "thread-title-default", !settings.alwaysShowTitleAndName) {
                            onSettingChange(FutaberSettingKeys.TITLE_NAME_ALWAYS, "OFF")
                        }
                        CheckRow("常に表示", "thread-title-always", settings.alwaysShowTitleAndName, last = true) {
                            onSettingChange(FutaberSettingKeys.TITLE_NAME_ALWAYS, "ON")
                        }
                    }
                    SettingsSection("画像表示サイズ") {
                        CheckRow("オリジナルサイズで表示", "thread-image-original", !settings.smallImages) {
                            onSettingChange(FutaberSettingKeys.SMALL_IMAGES, "OFF")
                        }
                        CheckRow("小さく表示", "thread-image-small", settings.smallImages, last = true) {
                            onSettingChange(FutaberSettingKeys.SMALL_IMAGES, "ON")
                        }
                    }
                    SettingsSection("返信") {
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
                    if (mediaServices != null) {
                        NavRow("読み上げ", "speech") { sharedPath = "speech" }
                        NavRow("削除レスの表示方法", "deleted-posts") { sharedPath = "thread" }
                    }
                }
                // Prompt display, image / video editing and their help, on a page of this mode's own (the shared "media"
                // page was empty here: its switches need the settings writer the runtime host now provides).
                SettingsRoute.Media -> FutaberMediaSettingsPage()
                SettingsRoute.Mode -> ModeSection()
                SettingsRoute.AppIcon -> SettingsSection("アプリアイコン") {
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
        }
    }

    if (postingOpen) {
        FutaberPostSettingsDialog(
            settings = postSettings,
            deleteKey = deleteKey,
            onSettingsChange = onPostSettingsChange,
            onDeleteKeyChange = onDeleteKeyChange,
            onDismiss = { postingOpen = false }
        )
    }
    if (fontDialog && mediaServices != null) {
        FutaberCustomFontDialog(mediaServices, rememberFutaberCustomFontName(mediaServices.store), onNotice) { fontDialog = false }
    }
    if (fileManagerDialog && stateStore != null) {
        FutaberPreferredFileManagerDialog(stateStore, onNotice) { fileManagerDialog = false }
    }
    if (lockDialog) {
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

@Composable
private fun SettingsSection(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalFutaberColors.current
    if (title != null) {
        Text(
            title, color = colors.meta, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 24.dp, bottom = 8.dp)
        )
    } else {
        Spacer(Modifier.height(24.dp))
    }
    // Grouped rows sit on a rounded card inset from the screen edge, as in the current system lists.
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(FutaberShapes.card).background(colors.bar)) {
        content()
    }
}

/** The thin rule between rows, inset like the lists of the original app. */
@Composable
private fun RowDivider(last: Boolean) {
    if (!last) HorizontalDivider(color = LocalFutaberColors.current.separator, thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
}

@Composable
private fun NavRow(label: String, id: String, last: Boolean = false, detail: String? = null, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clickable(onClickLabel = label, onClick = onClick)
                .padding(horizontal = 16.dp).testTag("futaber-settings-row-$id"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, color = colors.meta, fontSize = 16.sp, modifier = Modifier.padding(end = 4.dp).testTag("futaber-settings-detail-$id"))
            FutaberIcon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = colors.meta)
        }
        RowDivider(last)
    }
}

@Composable
private fun InfoRow(label: String, id: String, value: String, last: Boolean = false) {
    val colors = LocalFutaberColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(horizontal = 16.dp).testTag("futaber-settings-row-$id"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(value, color = colors.meta, fontSize = 16.sp, modifier = Modifier.testTag("futaber-settings-detail-$id"))
        }
        RowDivider(last)
    }
}

@Composable
private fun CheckRow(label: String, id: String, selected: Boolean, last: Boolean = false, onClick: () -> Unit) {
    val colors = LocalFutaberColors.current
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .clickable(onClickLabel = label, onClick = onClick)
                .semantics(mergeDescendants = true) { this.selected = selected; role = Role.RadioButton }
                .padding(horizontal = 16.dp).testTag("futaber-settings-row-$id"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (selected) FutaberIcon(Icons.Outlined.Check, contentDescription = "選択中", tint = colors.link)
        }
        RowDivider(last)
    }
}


/**
 * The first lines of the "拡張機能" page: most extensions are off by default and leave the screens as the original
 * app has them, but four things work from the start (each named here, with where it can be turned off if it can).
 */
internal const val FUTABER_EXTRAS_NOTE_INTRO =
    "ふたちゃ・としあき(仮)にある機能のうち、元のアプリには無いものです。ほとんどは最初オフで、オフのままなら画面や操作は元のアプリと同じです。\n" +
        "ただし次の4つは、最初から働きます：動画をビューアで再生（このページでオフにできます）、" +
        "閲覧したスレのURLの過去ログ保存サービスへの通知（設定の「閲覧スレ通知」でオフにできます）、" +
        "登録していない板のスレッドを開く時の「板を追加して開く」、" +
        "画像を添付できない板（img板の返信など）での添付ボタンからの手書き。"

@Composable
private fun SwitchRow(label: String, id: String, checked: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    val colors = LocalFutaberColors.current
    Column {
        // The whole row is the switch for touch and for a screen reader (it reads the label, the role and the state); the
        // Switch itself only draws.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp)
                .testTag("futaber-settings-switch-$id"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(checkedTrackColor = colors.action, checkedThumbColor = colors.onAction)
            )
        }
        RowDivider(last)
    }
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
    val colors = LocalFutaberColors.current
    // A button that only says "減らす" tells a screen reader nothing about what it changes.
    val name = label.substringBefore(" =").trim()
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 16.dp, end = 12.dp)
                .testTag("futaber-settings-row-$tag"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = colors.body, fontSize = 16.sp, modifier = Modifier.weight(1f).testTag("futaber-settings-value-$tag"))
            Row(Modifier.clip(FutaberShapes.small).background(colors.catalogGap), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, enabled = canDecrease, modifier = Modifier.testTag("futaber-settings-minus-$tag")) {
                    FutaberIcon(Icons.Outlined.Remove, contentDescription = "${name}を減らす", tint = if (canDecrease) colors.icon else colors.meta)
                }
                Box(Modifier.width(0.5.dp).height(22.dp).background(colors.separator))
                IconButton(onClick = onIncrease, enabled = canIncrease, modifier = Modifier.testTag("futaber-settings-plus-$tag")) {
                    FutaberIcon(Icons.Outlined.Add, contentDescription = "${name}を増やす", tint = if (canIncrease) colors.icon else colors.meta)
                }
            }
        }
        RowDivider(last)
    }
}

/** The mode switch, kept from the earlier dialog: a choice of the modes this build offers, then a confirmation. */
@Composable
private fun ColumnScope.ModeSection() {
    val colors = LocalFutaberColors.current
    val controller = LocalExperienceProfileUiController.current
    var requestedProfile by remember { mutableStateOf<ExperienceProfile?>(null) }
    if (!controller.isAvailable) {
        Text("モードを切り替えられません", color = colors.meta, modifier = Modifier.padding(16.dp))
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
                    Text(profile.displayName, color = colors.body, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (profile == controller.activeProfile) FutaberIcon(Icons.Outlined.Check, contentDescription = "選択中", tint = colors.link)
                }
                RowDivider(index == entries.lastIndex)
            }
        }
    }
    controller.lastError?.let {
        Text("切替に失敗しました: $it", color = colors.meta, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
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
                        "help" -> CompatHelpScreen(onBack = back, onOpenChangeLog = { paths = paths + "changelog" })
                        "changelog" -> CompatChangeLogScreen(
                            appVersion = appVersion, store = services.store,
                            onOpenHelp = { paths = paths + "help" }, onBack = back
                        )
                        "license" -> CompatLicenseScreen(onBack = back)
                        "cookies" -> services.cookieRepository?.let { CookieManagementScreen(onBack = back, repository = it) }
                            ?: CompatHelpScreen(onBack = back)
                        else -> CompatSettingsScreen(
                            path = path,
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
                            restoreBoardsInto = stateStore
                        )
                    }
                }
            }
        }
    }
}

/** An NG list of words: typing adds one, "×" removes one; both are applied to the latest stored list. */
@Composable
private fun ColumnScope.NgWordListScreen(
    kind: FutaberNgListKind,
    entries: List<String>,
    onEdit: ((List<String>) -> List<String>) -> Unit,
    hint: String = kind.hint
) {
    NgListScreen(
        kind = kind,
        hint = hint,
        rows = futaberNgRows(entries.map { FutaberNgRow(it, it) }),
        onAdd = { input -> onEdit { latest -> futaberAddNgEntry(latest, input) } },
        canAdd = { input -> futaberAddNgEntry(entries, input) !== entries },
        onRemove = { row -> onEdit { latest -> futaberRemoveNgEntry(latest, row.id) } }
    )
}

/**
 * Adds, lists and removes the entries of one NG list, in the look of the settings rows. The list is lazy: it can hold
 * thousands of entries. [onAdd] is null for a list nothing is typed into (rules made elsewhere); taking an entry back
 * goes by its [FutaberNgRow.id], not by where it was in the list that was on screen.
 */
@Composable
private fun ColumnScope.NgListScreen(
    kind: FutaberNgListKind,
    rows: List<FutaberNgRow>,
    onAdd: ((String) -> Unit)?,
    canAdd: (String) -> Boolean,
    onRemove: (FutaberNgRow) -> Unit,
    hint: String = kind.hint
) {
    val colors = LocalFutaberColors.current
    var input by rememberSaveable(kind) { mutableStateOf("") }
    val canAddNow = onAdd != null && canAdd(input)
    val corner = 16.dp
    LazyColumn(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
        item(key = "hint") {
            Text(
                hint, color = colors.meta, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp)
            )
        }
        if (onAdd != null) item(key = "add") { Column {
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(FutaberShapes.card).background(colors.bar)
                    .heightIn(min = 50.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = input,
                    onValueChange = { input = futaberSafeTake(it, FUTABER_NG_MAX_LENGTH) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.body, fontSize = 16.sp, fontFamily = LocalFutaberCustomFont.current),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.accent),
                    modifier = Modifier.weight(1f).testTag("futaber-ng-input"),
                    decorationBox = { inner ->
                        Box { if (input.isEmpty()) Text("追加する語句", color = colors.meta, fontSize = 16.sp); inner() }
                    }
                )
                TextButton(
                    onClick = { onAdd(input); input = "" },
                    enabled = canAddNow,
                    modifier = Modifier.testTag("futaber-ng-add")
                ) { Text("追加", color = if (canAddNow) colors.link else colors.meta) }
            }
        } }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Text(
                    "登録はありません", color = colors.meta, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("futaber-ng-empty")
                )
            }
        } else {
            item(key = "gap") { Spacer(Modifier.height(24.dp)) }
            itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
                val first = index == 0
                val last = index == rows.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (first) corner else 0.dp, topEnd = if (first) corner else 0.dp,
                    bottomStart = if (last) corner else 0.dp, bottomEnd = if (last) corner else 0.dp
                )
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(shape).background(colors.bar)) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 50.dp).padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(row.label, color = colors.body, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(row) }, modifier = Modifier.testTag("futaber-ng-delete")) {
                            FutaberIcon(
                                androidx.compose.material.icons.Icons.Outlined.RemoveCircleOutline,
                                contentDescription = "「${row.label}」を削除", tint = colors.accent
                            )
                        }
                    }
                    RowDivider(last)
                }
            }
        }
    }
}
