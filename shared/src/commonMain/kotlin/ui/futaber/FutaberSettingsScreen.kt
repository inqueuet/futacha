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
    Ng("NG設定"), NgList("NG"), AppIcon("アプリアイコン"), Extras("拡張機能")
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
    CatalogImages("NG画像（カタログ）", "ふたちゃ・としあき(仮)で登録した、サムネイルの画像で隠すルールです。外すとカタログに戻ります（更新後に反映）")
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
    /** Tells the person something went wrong (a setting that could not be saved). */
    onNotice: (String) -> Unit = {}
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
    PlatformBackHandler(onBack = { if (route == SettingsRoute.Root) onClose() else route = route.parent() })

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
                    TextButton(onClick = { route = route.parent() }, modifier = Modifier.testTag("futaber-settings-back")) {
                        Text(if (route == SettingsRoute.NgList) "‹ NG設定" else "‹ 設定", color = colors.link)
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
                FutaberNgListKind.CatalogWords -> NgWordListScreen(ngKind, catalogNgWords, onEditCatalogNgWords)
                FutaberNgListKind.ResWords -> NgWordListScreen(ngKind, ngWords, onEditNgWords)
                FutaberNgListKind.ResHeaders -> NgWordListScreen(ngKind, ngHeaders, onEditNgHeaders)
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
                            NavRow("メディア機能", "media") { sharedPath = "media" }
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
                            SwitchRow("軽量モード", "lightweight", lightweightEnabled) { next -> store { it.setLightweightModeEnabled(next) } }
                            NavRow("起動ロック", "app-lock", last = true, detail = if (lockHash.isNullOrBlank()) "無効" else "有効") { lockDialog = true }
                        }
                    }
                    SettingsSection("このアプリ") {
                        NavRow("拡張機能", "extras") { route = SettingsRoute.Extras }
                        if (stateStore != null) NavRow("アプリアイコン", "app-icon", detail = appIcon.label) { route = SettingsRoute.AppIcon }
                        NavRow("モード", "mode", last = true) { route = SettingsRoute.Mode }
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
                        NavRow("NG画像", "ng-res-images", last = true, detail = imageNgRules.size.toString()) {
                            ngKind = FutaberNgListKind.ResImages; route = SettingsRoute.NgList
                        }
                    }
                }
                SettingsRoute.NgList -> Unit // drawn above, outside the scrolling column
                SettingsRoute.Gesture -> {
                    SettingsSection(null) {
                        SwitchRow(
                            "画像ビューアを↓スワイプで閉じる", "gesture-viewer-swipe", settings.viewerSwipeClose, last = true
                        ) { onSettingChange(FutaberSettingKeys.VIEWER_SWIPE_CLOSE, if (it) "ON" else "OFF") }
                    }
                    Text(
                        "他\n" +
                            "・スレッド内で左端からスワイプで直接メニュー表示（iPhone。Androidでは板一覧）\n" +
                            "・スレッド内でレス長押しで引用その他アクション\n" +
                            "・スレッド内ツールバーの最下部スクロールボタンを長押しで新着へスクロール\n" +
                            "・書き込み画面で下スワイプで引用モード",
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
                    SettingsSection("書き込み") {
                        SwitchRow("メール欄の「ID表示・IP表示・sage」ボタン", "ext-mail-presets", settings.extMailPresets, last = true) {
                            onSettingChange(FutaberSettingKeys.EXT_MAIL_PRESETS, if (it) "ON" else "OFF")
                        }
                    }
                    SettingsSection("管理パネル") {
                        SwitchRow("履歴の検索・並べ替え・一括更新・印", "ext-history", settings.extHistory) {
                            onSettingChange(FutaberSettingKeys.EXT_HISTORY, if (it) "ON" else "OFF")
                        }
                        SwitchRow("閉じたタブを元に戻す（タブの長押し）", "ext-tabs", settings.extTabs, last = true) {
                            onSettingChange(FutaberSettingKeys.EXT_TABS, if (it) "ON" else "OFF")
                        }
                    }
                    SettingsSection("画像ビューア") {
                        SwitchRow("動画をビューアで再生", "ext-video", settings.extVideo, last = true) {
                            onSettingChange(FutaberSettingKeys.EXT_VIDEO, if (it) "ON" else "OFF")
                        }
                    }
                    Text(
                        "ふたちゃ・としあき(仮)にある機能のうち、元のアプリには無いものです。動画ビューアを除き、オフのままなら画面や操作は元のアプリと同じです。\n" +
                            "・ツリー表示：引用先のレスの下に返信を並べます\n" +
                            "・レスを抽出：自分の書き込み、そうだねが多い、削除されたレス、URLを含む、画像のあるレス\n" +
                            "・履歴の検索・並べ替え・一括更新・印：管理パネルの「履歴」の上に帯が出て、行には落ちた・書き込み済み・保存済みの印が付きます\n" +
                            "・閉じたタブを元に戻す：タブを長押しした一覧に項目が出ます（この起動中に閉じたタブ）\n" +
                            "・動画をビューアで再生：画像ビューアの動画ページが動画ビューア（再生・一時停止・停止・音量）になります。最初からオンで、開いた時は停止した状態です\n" +
                            "・メールのボタン：書き込み設定のメール欄に、ふたちゃ・としあき(仮)と同じ値を入れます\n" +
                            "・オートスクロールの量と間隔は、ふたちゃ・としあき(仮)と共通の値です",
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
                    Text(
                        "読み上げの声の高さ・速さは、この端末の読み上げ機能が設定を持たないため、まだ変えられません。",
                        color = colors.meta, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("futaber-settings-thread-note")
                    )
                }
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
private fun FutaberSharedSettingsHost(
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
                            stateStore = stateStore
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
    onEdit: ((List<String>) -> List<String>) -> Unit
) {
    NgListScreen(
        kind = kind,
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
    onRemove: (FutaberNgRow) -> Unit
) {
    val colors = LocalFutaberColors.current
    var input by rememberSaveable(kind) { mutableStateOf("") }
    val canAddNow = onAdd != null && canAdd(input)
    val corner = 16.dp
    LazyColumn(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
        item(key = "hint") {
            Text(
                kind.hint, color = colors.meta, fontSize = 13.sp,
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
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.body, fontSize = 16.sp),
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
