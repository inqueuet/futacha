package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding

import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.CookieManagementScreen
import com.valoser.futacha.shared.ui.board.GlobalSettingsModeSection
import com.valoser.futacha.shared.ui.compat.CompatChangeLogScreen
import com.valoser.futacha.shared.ui.compat.CompatHelpScreen
import com.valoser.futacha.shared.ui.compat.CompatLicenseScreen
import com.valoser.futacha.shared.ui.compat.CompatSettingsScreen
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.futaber.FutaberMediaServices
import com.valoser.futacha.shared.ui.util.PlatformBackHandler

/** 設定の中の行き先。 */
private sealed interface FtutterSettingsRoute {
    data object Root : FtutterSettingsRoute
    data class Page(val page: FtutterSettingsPage) : FtutterSettingsRoute
    data object NgList : FtutterSettingsRoute
    data object Hidden : FtutterSettingsRoute
    data object Gesture : FtutterSettingsRoute
    data object Tablet : FtutterSettingsRoute
    data object Extras : FtutterSettingsRoute
    data object Watcher : FtutterSettingsRoute
}

/**
 * 設定（原作の設定ルートの分類）。各分類・NGの一覧・非表示・ジェスチャー・二画面はこの中で行き来する。
 * 他モードと共有の設定（保存先・ネットワーク・Cookie・画像検索・ヘルプなど）は、共有の設定ページをこのモードの色で重ねる。
 */
@Composable
internal fun FtutterSettingsHost(
    preferences: Map<String, String>,
    save: (String, String) -> Unit,
    edit: (String, (String?) -> String?) -> Unit,
    appVersion: String,
    boards: List<BoardSummary>,
    ngRules: List<FtutterNgRule>,
    onSaveNgRules: FtutterNgEdit,
    deleteKey: String,
    ngRemoved: List<CatalogItem>,
    services: FutaberMediaServices?,
    stateStore: AppStateStore,
    onEditToolbar: (FtutterOrderEditorKind) -> Unit,
    onImportBackup: () -> Unit,
    onBack: () -> Unit,
    /** 拡張機能の巡回の結果から開く（設定を閉じてスレッドへ）。 */
    repository: com.valoser.futacha.shared.repo.BoardRepository? = null,
    onOpenThread: (BoardSummary, threadId: String, title: String) -> Unit = { _, _, _ -> },
    ngReasons: Map<String, String> = emptyMap()
) {
    var stack by remember { mutableStateOf(listOf<FtutterSettingsRoute>(FtutterSettingsRoute.Root)) }
    var sharedPath by remember { mutableStateOf<String?>(null) }
    fun push(route: FtutterSettingsRoute) { stack = stack + route }
    val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else onBack() }
    PlatformBackHandler(enabled = sharedPath == null, onBack = back)
    when (val route = stack.last()) {
        FtutterSettingsRoute.Root -> FtutterSettingsPage("設定", "ftutter-settings", onBack) {
            ftutterSection("ふたったー風")
            FtutterSettingsPage.entries.forEach { page ->
                item(key = page.tag) { FtutterNavRow(page.title, "ftutter-settings-${page.tag}") { push(FtutterSettingsRoute.Page(page)) } }
                if (page == FtutterSettingsPage.NETWORK) {
                    item(key = "gesture") { FtutterNavRow("ジェスチャー", "ftutter-settings-gesture") { push(FtutterSettingsRoute.Gesture) } }
                    item(key = "tablet") { FtutterNavRow("二画面", "ftutter-settings-tablet") { push(FtutterSettingsRoute.Tablet) } }
                }
            }
            ftutterSection("このアプリ")
            item(key = "extras") { FtutterNavRow("拡張機能", "ftutter-settings-extras") { push(FtutterSettingsRoute.Extras) } }
            if (services != null) {
                ftutterSection("他のモードと共有の設定")
                item { FtutterNavRow("保存先・キャッシュ", "ftutter-shared-storage") { sharedPath = "storage" } }
                preferences[FtutterKeys.PREFIX + "import.savePath"]?.takeIf { it.isNotBlank() }?.let { original ->
                    item { androidx.compose.material3.Text("取り込み元の保存先: $original\n使用するフォルダを「保存先・キャッシュ」で選択してください。", color = LocalFtutterColors.current.text,
                        modifier = androidx.compose.ui.Modifier.padding(16.dp)) }
                }
                preferences[FtutterKeys.PREFIX + "import.savePathList"]?.let { paths ->
                    decodeFtutterList(paths).orEmpty().forEachIndexed { index, original ->
                        item { FtutterNavRow("取り込み元の保存先 ${index + 1}", "ftutter-import-location-$index", detail = original) { sharedPath = "storage" } }
                    }
                }
                item { FtutterNavRow("ネットワーク", "ftutter-shared-network") { sharedPath = "network" } }
                item { FtutterNavRow("画像検索", "ftutter-shared-image-search") { sharedPath = "image_search" } }
                item { FtutterNavRow("プライバシー表示", "ftutter-shared-privacy") { sharedPath = "privacy" } }
                if (services.cookieRepository != null) item { FtutterNavRow("Cookie管理", "ftutter-shared-cookies") { sharedPath = "cookies" } }
                ftutterSection("情報")
                item { FtutterNavRow("ヘルプ", "ftutter-shared-help") { sharedPath = "help" } }
                item { FtutterNavRow("更新情報", "ftutter-shared-changelog") { sharedPath = "changelog" } }
                item { FtutterNavRow("ライセンス", "ftutter-shared-license") { sharedPath = "license" } }
            }
            item { GlobalSettingsModeSection() }
        }
        is FtutterSettingsRoute.Page -> when (route.page) {
            FtutterSettingsPage.DESIGN -> FtutterDesignSettings(preferences, save, back, onChooseFont = { sharedPath = "design" })
            FtutterSettingsPage.CATALOG -> FtutterCatalogSettingsPage(preferences, save, { onEditToolbar(FtutterOrderEditorKind.CATALOG) }, back)
            FtutterSettingsPage.THREAD -> FtutterThreadSettingsPage(
                preferences, save, { onEditToolbar(FtutterOrderEditorKind.THREAD) }, { onEditToolbar(FtutterOrderEditorKind.POST_MENU) }, back
            )
            FtutterSettingsPage.TAB_DRAWER -> FtutterTabDrawerSettingsPage(preferences, save, back)
            FtutterSettingsPage.MEDIA -> FtutterMediaSettingsPage(preferences, save, back)
            FtutterSettingsPage.NG -> FtutterNgSettingsPage(
                preferences, save, ngRules.size, parseFtutterHiddenThreads(preferences[FtutterKeys.HIDDEN_THREADS]).size,
                onOpenNgList = { push(FtutterSettingsRoute.NgList) }, onOpenHidden = { push(FtutterSettingsRoute.Hidden) }, onBack = back
            )
            FtutterSettingsPage.NETWORK -> FtutterNetworkSettingsPage(
                preferences, save, deleteKey,
                onOpenCookies = services?.cookieRepository?.let { { sharedPath = "cookies" } },
                onOpenSharedNetwork = services?.let { { sharedPath = "network" } },
                onBack = back
            )
            FtutterSettingsPage.BACKUP -> FtutterSettingsPage("バックアップ・復元", "ftutter-settings-page-backup", back) {
                item {
                    FtutterNavRow("ふたったーのバックアップ（.ftt）を取り込む", "ftutter-backup-import", onClick = onImportBackup)
                }
                if (services != null) {
                    item { FtutterNavRow("このアプリのバックアップ・復元（他のモードと共有）", "ftutter-backup-shared") { sharedPath = "backup" } }
                }
            }
            FtutterSettingsPage.STATS -> FtutterStatsPage(preferences, appVersion, back)
        }
        FtutterSettingsRoute.NgList -> FtutterNgListScreen(ngRules, boards, onEdit = { onSaveNgRules(it, null) }, onBack = back)
        FtutterSettingsRoute.Hidden -> FtutterHiddenAndNgScreen(preferences, edit, onBack = back, ngRemoved = ngRemoved, ngReasons = ngReasons)
        FtutterSettingsRoute.Gesture -> FtutterGestureSettingsScreen(preferences, save, onBack = back)
        FtutterSettingsRoute.Tablet -> FtutterTabletSettingsScreen(preferences, save, onBack = back)
        FtutterSettingsRoute.Extras -> FtutterExtrasSettingsPage(
            preferences, save, stateStore,
            sharedAvailable = services != null && repository != null,
            onOpenWatcher = { push(FtutterSettingsRoute.Watcher) },
            onOpenSharedAi = { sharedPath = "ai" },
            onBack = back,
            onOpenSharedSpeech = { sharedPath = "speech" },
            compatStore = services?.store
        )
        FtutterSettingsRoute.Watcher -> {
            val store = services?.store
            if (store != null && repository != null) {
                FtutterWatcherScreen(store, repository, boards, onOpen = onOpenThread, onBack = back)
            } else {
                androidx.compose.runtime.LaunchedEffect(Unit) { back() }
            }
        }
    }
    val path = sharedPath
    if (path != null && services != null) {
        FtutterSharedSettingsHost(path, services, appVersion, stateStore, onClose = { sharedPath = null })
    }
}

/** 並べ替えの画面の種類（ツールバー2つと、レス長押しメニュー）。 */
internal enum class FtutterOrderEditorKind { CATALOG, THREAD, POST_MENU }

/** 他モードと共有の設定ページ（ふたばー風と同じ仕組み）を、このモードの色で重ねる。 */
@Composable
private fun FtutterSharedSettingsHost(
    startPath: String,
    services: FutaberMediaServices,
    appVersion: String,
    stateStore: AppStateStore?,
    onClose: () -> Unit
) {
    val colors = LocalFtutterColors.current
    val palette = remember(colors) { ftutterMediaPalette(colors) }
    // 読み終えるまでは何も描かない（監査L16：空の設定で既定値が一瞬見えていた）。
    val loadedPreferences by services.store.preferences.collectAsStateCompat()
    val preferences = loadedPreferences ?: return
    var paths by remember { mutableStateOf(listOf(startPath)) }
    val back = { if (paths.size > 1) paths = paths.dropLast(1) else onClose() }
    FutachaAppLockAwareWindow { Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalCompatibilityPalette provides palette) {
            Surface(Modifier.fillMaxSize().testTag("ftutter-shared-settings"), color = colors.background) {
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
                        // 共有のバックアップから復元した板を、このモードが使う板の一覧へ足す（ふたばー風と同じ）。
                        restoreBoardsInto = stateStore
                    )
                }
            }
        }
    } }
}

@Composable
private fun kotlinx.coroutines.flow.Flow<Map<String, String>>.collectAsStateCompat() =
    androidx.compose.runtime.produceState<Map<String, String>?>(null, this) { collect { value = it } }
