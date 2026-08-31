@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    kotlin.time.ExperimentalTime::class
)

package com.valoser.futacha.shared.ui.compat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import futacha.shared.generated.resources.Res
import futacha.shared.generated.resources.post_video_thumb
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.request.crossfade
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatCatalogPreference
import com.valoser.futacha.shared.compat.CompatBuildDraft
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatSettingsBackupImportReport
import com.valoser.futacha.shared.compat.appliesToThreadImage
import com.valoser.futacha.shared.compat.compatThreadImageNgScopeKey
import com.valoser.futacha.shared.compat.CompatImagePhash
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatToolbarItem
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.COMPAT_REFERENCE_HELP_TITLE
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.CompatLegacyScopedWord
import com.valoser.futacha.shared.compat.CompatLegacyBackupData
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
import com.valoser.futacha.shared.compat.ArchiveReportOutboxStats
import com.valoser.futacha.shared.compat.COMPAT_SETTINGS_BACKUP_FILE_NAME
import com.valoser.futacha.shared.compat.COMPAT_WATCH_NG_BACKUP_FILE_NAME
import com.valoser.futacha.shared.compat.MAX_COMPAT_SETTINGS_BACKUP_BYTES
import com.valoser.futacha.shared.compat.MAX_COMPAT_LEGACY_BACKUP_BYTES
import com.valoser.futacha.shared.compat.MAX_COMPAT_NG_MEMO_CHARS
import com.valoser.futacha.shared.compat.CURRENT_COMPAT_SETTINGS_BACKUP_VERSION
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.compat.LocalExperienceProfileUiController
import com.valoser.futacha.shared.compat.compatToolbarMaster
import com.valoser.futacha.shared.compat.compatToolbarShowsOverflow
import com.valoser.futacha.shared.compat.compatNgRuleId
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.decodeCompatLegacyBackup
import com.valoser.futacha.shared.compat.decodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.decodeCompatWatchNgBackup
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.encodeCompatWatchNgBackup
import com.valoser.futacha.shared.compat.settingsOnly
import com.valoser.futacha.shared.compat.watchAndNgOnly
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.toCompatPlainText
import com.valoser.futacha.shared.compat.formatCompatCacheUsage
import com.valoser.futacha.shared.compat.CompatImageCacheUsage
import com.valoser.futacha.shared.compat.formatCompatImageCacheUsage
import com.valoser.futacha.shared.compat.reconcileCompatToolbar
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.network.defaultBoardPostingCapabilities
import com.valoser.futacha.shared.model.SaveLocation.Companion.toRawString
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.model.SavePhase
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.service.ImageZipSaveService
import com.valoser.futacha.shared.service.MANUAL_SAVE_DIRECTORY
import com.valoser.futacha.shared.service.SingleMediaSaveService
import com.valoser.futacha.shared.service.buildCompatManualImageFolderName
import com.valoser.futacha.shared.ui.board.PlatformVideoPlayer
import com.valoser.futacha.shared.ui.board.VideoMediaInfo
import com.valoser.futacha.shared.ui.board.VideoPlaybackError
import com.valoser.futacha.shared.ui.board.VideoPlayerState
import com.valoser.futacha.shared.ui.board.formatVideoMediaInfoLines
import com.valoser.futacha.shared.ui.board.formatVideoPlaybackError
import com.valoser.futacha.shared.ui.board.formatMediaLoadFailure
import com.valoser.futacha.shared.ui.board.rememberAttachmentPickerLauncher
import com.valoser.futacha.shared.ui.board.rememberDirectoryPickerLauncher
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.CompatibilityCacheLocation
import com.valoser.futacha.shared.ui.image.getPlatformCacheAvailableBytes
import com.valoser.futacha.shared.ui.image.isPlatformRemovableCacheAvailable
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import com.valoser.futacha.shared.util.rememberUrlLauncher
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.random.Random
import kotlin.time.Clock

private val SecondaryTeal: Color @Composable get() = LocalCompatibilityPalette.current.chrome
private val SecondaryAccent: Color @Composable get() =
    compatibilitySettingsCategoryColor(LocalCompatibilityPalette.current)
private val SecondaryBackground: Color @Composable get() = LocalCompatibilityPalette.current.background

private val compatSecondaryPostNumberRegex = Regex("[0-9]+")
private val compatSecondarySageTokenRegex = Regex("(^|\\s)sage($|\\s)", RegexOption.IGNORE_CASE)
private const val COMPAT_POST_NAME_MAX_CHARS = 100
private const val COMPAT_POST_EMAIL_MAX_CHARS = 100
private const val COMPAT_POST_SUBJECT_MAX_CHARS = 100
private const val COMPAT_POST_COMMENT_MAX_CHARS = 10_000
private const val COMPAT_REFERENCE_DATABASE_VERSION = 26
private const val COMPAT_BACKUP_SETTING_IMPORT_DATE_KEY = "compat.root.backupSettingImportDate"
private const val COMPAT_BACKUP_SETTING_EXPORT_DATE_KEY = "compat.root.backupSettingExportDate"
private const val COMPAT_BACKUP_KEYWORD_IMPORT_DATE_KEY = "compat.root.backupKeywordImportDate"
private const val COMPAT_BACKUP_KEYWORD_EXPORT_DATE_KEY = "compat.root.backupKeywordExportDate"

internal val COMPAT_REFERENCE_VERSION_MESSAGES = listOf(
    "エンジョイ＆エキサイティング",
    "ペイパーキャノーーーン！",
    "肩が赤い",
    "完成してるの初めて見た",
    "こいつ、動くぞ・・・",
    "ツァ",
    "なんか寒くね！？",
    "念レス成功",
    "よしなに",
    "やよエな",
    "ねないこだれだ",
    "タキシードクイズ",
    "しもんきん",
    "ワグナス！",
    "教授！！これはいったい？"
)
internal const val COMPAT_REFERENCE_VERSION_RANDOM_BOUND = 14

internal fun compatReferenceVersionMessage(index: Int = Random.nextInt(COMPAT_REFERENCE_VERSION_RANDOM_BOUND)): String =
    COMPAT_REFERENCE_VERSION_MESSAGES[index.coerceIn(COMPAT_REFERENCE_VERSION_MESSAGES.indices)]

internal fun formatCompatBackupTimestamp(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone)
    return buildString {
        append(local.year.toString().padStart(4, '0'))
        append('/')
        append((local.month.ordinal + 1).toString().padStart(2, '0'))
        append('/')
        append(local.day.toString().padStart(2, '0'))
        append(' ')
        append(local.hour.toString().padStart(2, '0'))
        append(':')
        append(local.minute.toString().padStart(2, '0'))
        append(':')
        append(local.second.toString().padStart(2, '0'))
    }
}

internal fun compatBackupDatePreferenceKey(kind: String): String? = when (kind) {
    "settings" -> COMPAT_BACKUP_SETTING_IMPORT_DATE_KEY
    "save_settings" -> COMPAT_BACKUP_SETTING_EXPORT_DATE_KEY
    "ng" -> COMPAT_BACKUP_KEYWORD_IMPORT_DATE_KEY
    "save_ng" -> COMPAT_BACKUP_KEYWORD_EXPORT_DATE_KEY
    else -> null
}

/**
 * Keep the compatibility profile's backup result copy identical to both
 * reference APKs. The NG restore still exposes the imported totals on the
 * following line because those APKs also appended their per-category totals.
 */
internal fun compatBackupSuccessMessage(
    kind: String,
    report: CompatSettingsBackupImportReport? = null
): String = when (kind) {
    "settings" -> "基本的な設定を復元しました"
    "save_settings" -> "基本的な設定を保存しました"
    "ng" -> buildString {
        append("監視･ＮＧワードを復元しました")
        if (report != null) {
            append('\n')
            append("設定項目 ")
            append(report.preferencesImported)
            append("件、ＮＧ項目 ")
            append(report.ngRulesImported)
            append('件')
        }
    }
    "save_ng" -> "監視･ＮＧワードを保存しました"
    else -> report?.let {
        "復元しました（板${it.boardsImported}件、設定${it.preferencesImported}件、NG${it.ngRulesImported}件）"
    } ?: "完了しました"
}

internal fun compatBackupFailureMessage(kind: String, error: Throwable): String = when {
    kind == "save_settings" || kind == "save_ng" -> "書き込みエラーです"
    error is IllegalArgumentException || error is SerializationException -> "ファイルの形式が不明です"
    error.message.orEmpty().contains("ファイルシステム") ||
        error.message.orEmpty().contains("read", ignoreCase = true) ->
        "ファイルの読み込みができません"
    else -> "復元に失敗しました"
}

internal const val COMPAT_IMAGE_SEARCH_DESCRIPTION =
    "File方式は画像そのものを送り、結果をアプリ内蔵ブラウザで表示します。" +
        "サーバから画像が消えた落ちスレでも検索できます。\n" +
        "URL方式は画像のURLを外部ブラウザへ渡します。" +
        "落ちスレやZIPスレでは検索できません。"

@Suppress("UNUSED_PARAMETER")
internal fun applyCompatMailPreset(email: String, preset: String, isBuild: Boolean): String = when (preset) {
    // The reference labels are upper-case, while the values posted to Futaba
    // intentionally use lower-case ASCII command tokens.
    "ID表示" -> "id表示"
    "IP表示" -> "ip表示"
    "sage" -> "sage"
    else -> email
}

internal fun compatPostMailPresets(isBuild: Boolean): List<String> =
    if (isBuild) listOf("ID表示", "IP表示", "sage") else listOf("sage")

internal data class CompatPostResetFields(
    val name: String,
    val email: String,
    val subject: String,
    val comment: String,
    val deleteKey: String
)

internal fun compatPostResetFields(
    isBuild: Boolean,
    currentDeleteKey: String,
    initialDraft: CompatReplyDraft
): CompatPostResetFields = if (isBuild) {
    // PostBuildActivity clears the editable content but deliberately keeps the
    // current deletion key. PostResponseActivity restores its opening draft.
    CompatPostResetFields("", "", "", "", currentDeleteKey)
} else {
    CompatPostResetFields(
        initialDraft.name,
        initialDraft.email,
        initialDraft.subject,
        initialDraft.comment,
        initialDraft.deleteKey
    )
}

internal data class CompatSettingEntry(
    val title: String,
    val summary: String = "タップして設定",
    val route: String? = null,
    val enabled: Boolean = true,
    val preferenceKey: String = title
)

internal fun compatImageSearchRootEntry(): CompatSettingEntry = CompatSettingEntry(
    title = "画像検索",
    summary = "長押しメニューの整理",
    route = "image_search",
    preferenceKey = "customSearchUriMulti"
)

internal fun compatRootSettingsGroups(appVersion: String): List<Pair<String, List<CompatSettingEntry>>> = listOf(
    "基本設定" to listOf(
        CompatSettingEntry("デザイン", "カラーテーマ・フォント", "design"),
        CompatSettingEntry("コントロール", "メニュー・操作・送信確認", "control"),
        CompatSettingEntry("ストレージ", "保存先・キャッシュ", "storage"),
        CompatSettingEntry("バックグラウンド", "スレッドの更新確認", "background"),
        CompatSettingEntry("ネットワーク", "サーバー機能", "network"),
        compatImageSearchRootEntry()
    ),
    "表示オプション" to listOf(
        CompatSettingEntry("カタログ画面", "エコモード・表示数", "catalog"),
        CompatSettingEntry("スレッド画面", "削除レス・抽出の閾値", "thread"),
        CompatSettingEntry("画像ビューア", "一覧の列数・先読み・動画", "viewer")
    ),
    "バックアップ" to listOf(
        CompatSettingEntry("基本的な設定の復元", "", preferenceKey = "dummyBackupSettingImport"),
        CompatSettingEntry("基本的な設定の保存", "", preferenceKey = "dummyBackupSettingExport"),
        CompatSettingEntry("監視･ＮＧワードの復元", "", preferenceKey = "dummyBackupKeywordImport"),
        CompatSettingEntry("監視･ＮＧワードの保存", "", preferenceKey = "dummyBackupKeywordExport"),
        CompatSettingEntry("ptmtクッキーの編集", "", preferenceKey = "ptmtEditor")
    ),
    "その他" to listOf(
        CompatSettingEntry("更新情報", ""),
        CompatSettingEntry("ライセンス", ""),
        CompatSettingEntry("Twitter", "@AndosanDev"),
        CompatSettingEntry(
            "バージョン",
            "$appVersion Database v$COMPAT_REFERENCE_DATABASE_VERSION",
            preferenceKey = "commonAppVersion"
        )
    ),
    "ふたちゃ拡張" to listOf(
        CompatSettingEntry("モード", "現在の表示モード", preferenceKey = "mode"),
        CompatSettingEntry(
            "アップデート確認",
            "起動時に最新リリースを確認します",
            preferenceKey = "updateCheckEnabled"
        ),
        CompatSettingEntry("保存済みスレッド", "保存したスレッドを一覧表示"),
        CompatSettingEntry("旧版設定・NGの復元", "旧としあき(仮)の keyword.cfg / setting.cfg を読み込む"),
        CompatSettingEntry("@create_app_null", "Futacha作者の情報"),
        CompatSettingEntry("開発情報", "Futachaの正規情報へ移動"),
        CompatSettingEntry(
            "閲覧スレ通知",
            "取得に成功して表示した対応板のスレURLだけを通知します",
            preferenceKey = "archiveReportEnabled"
        ),
        CompatSettingEntry(
            "通知データを削除",
            "端末内の未送信・送信済み記録を削除します",
            preferenceKey = "archiveReportClear"
        ),
        CompatSettingEntry(
            "送信内容について",
            "本文・画像・利用者ID・端末IDは送信しません",
            preferenceKey = "archiveReportInfo"
        )
    )
)

@Composable
internal fun CompatSettingsScreen(
    path: String,
    store: CompatibilityStore,
    preferences: Map<String, String>,
    fileSystem: FileSystem?,
    httpClient: HttpClient? = null,
    cookieRepository: CookieRepository? = null,
    appVersion: String = "1.0",
    isUpdateCheckEnabled: Boolean = true,
    onUpdateCheckChanged: (Boolean) -> Unit = {},
    onArchiveReportEnabledChanged: (Boolean) -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenSavedThreads: () -> Unit = {},
    onOpenChangeLog: () -> Unit = {},
    onOpenLicense: () -> Unit = {},
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    initialScrollPosition: Pair<Int, Int>? = null,
    onScrollPositionChanged: (Pair<Int, Int>) -> Unit = {}
) {
    val profileController = LocalExperienceProfileUiController.current
    val scope = rememberCoroutineScope()
    val groups = remember(path, appVersion) {
        if (path == "root") compatRootSettingsGroups(appVersion) else compatSettingsGroups(path)
    }
    var modeDialog by remember { mutableStateOf(false) }
    var editingEntry by remember(path) { mutableStateOf<CompatSettingEntry?>(null) }
    var directoryMenuEntry by remember(path) { mutableStateOf<CompatSettingEntry?>(null) }
    var backgroundAlwaysNotice by remember(path) { mutableStateOf<CompatSettingEntry?>(null) }
    var savedValues by remember(path, preferences) {
        mutableStateOf(compatSettingsSavedValues(path, groups, preferences))
    }
    var threadCacheUsageBytes by remember(path) { mutableStateOf<Long?>(null) }
    var confirmThreadCacheClear by remember(path) { mutableStateOf(false) }
    var threadCacheClearInProgress by remember(path) { mutableStateOf(false) }
    var attachmentCacheUsageBytes by remember(path) { mutableStateOf<Long?>(null) }
    var imageCacheUsage by remember(path) { mutableStateOf<CompatImageCacheUsage?>(null) }
    var cacheLocationChangeInProgress by remember(path) { mutableStateOf(false) }
    var cacheAvailableBytes by remember(path) {
        mutableStateOf<Map<CompatibilityCacheLocation, Long?>>(emptyMap())
    }
    var confirmImageCacheClear by remember(path) { mutableStateOf(false) }
    var imageCacheClearInProgress by remember(path) { mutableStateOf(false) }
    var confirmAttachmentClear by remember(path) { mutableStateOf(false) }
    var attachmentClearInProgress by remember(path) { mutableStateOf(false) }
    var archiveReportEnabled by remember(path, preferences[ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY]) {
        mutableStateOf(preferences[ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY] != "OFF")
    }
    var archiveReportStats by remember(path) { mutableStateOf<ArchiveReportOutboxStats?>(null) }
    var archiveReportSettingInProgress by remember(path) { mutableStateOf(false) }
    var confirmArchiveReportClear by remember(path) { mutableStateOf(false) }
    var archiveReportInfoOpen by remember(path) { mutableStateOf(false) }
    var backupMessage by remember(path) { mutableStateOf<String?>(null) }
    var backupInProgress by remember(path) { mutableStateOf(false) }
    var restoreBackupKind by remember(path) { mutableStateOf("settings") }
    var backupDates by remember(path, preferences) {
        mutableStateOf(
            listOfNotNull(
                COMPAT_BACKUP_SETTING_IMPORT_DATE_KEY,
                COMPAT_BACKUP_SETTING_EXPORT_DATE_KEY,
                COMPAT_BACKUP_KEYWORD_IMPORT_DATE_KEY,
                COMPAT_BACKUP_KEYWORD_EXPORT_DATE_KEY
            ).mapNotNull { key -> preferences[key]?.let { key to it } }.toMap()
        )
    }
    var referenceVersionMessage by remember(path) { mutableStateOf<String?>(null) }
    var infoDialog by remember(path) { mutableStateOf<String?>(null) }
    var transientNotice by remember(path) { mutableStateOf<String?>(null) }
    var cacheEnabled by remember(path, preferences[COMPAT_CACHE_ENABLED_KEY]) {
        mutableStateOf(preferences[COMPAT_CACHE_ENABLED_KEY] == "ON")
    }
    var cacheBaseUrl by remember(path, preferences[COMPAT_CACHE_BASE_URL_KEY]) {
        mutableStateOf(preferences[COMPAT_CACHE_BASE_URL_KEY].orEmpty())
    }
    var cacheStatus by remember(path, preferences[COMPAT_CACHE_STATUS_KEY]) {
        mutableStateOf(preferences[COMPAT_CACHE_STATUS_KEY])
    }
    var cacheWarningOpen by remember(path) { mutableStateOf(false) }
    var cacheEndpointDialogOpen by remember(path) { mutableStateOf(false) }
    var cacheEndpointDraft by remember(path) { mutableStateOf("") }
    var customFontDialogOpen by remember(path) { mutableStateOf(false) }
    var customFontName by remember(path, preferences) {
        mutableStateOf(
            compatSettingsSavedValues(path, groups, preferences)["dummyCustomFont"]
                .orEmpty()
                .ifBlank { "デフォルト" }
        )
    }
    var ptmtDialogOpen by remember(path) { mutableStateOf(false) }
    var ptmtValue by remember(path) { mutableStateOf("") }
    var ptmtCheck by remember(path) { mutableStateOf("") }
    var ptmtMessage by remember(path) { mutableStateOf<String?>(null) }
    var ptmtConfigured by remember(path) { mutableStateOf(false) }
    var selectedCustomSearchEngines by remember(path, preferences[COMPAT_CUSTOM_IMAGE_SEARCH_KEY]) {
        mutableStateOf(
            parseCompatImageSearchTargets(preferences[COMPAT_CUSTOM_IMAGE_SEARCH_KEY]).toSet()
        )
    }
    val imageLoader = LocalFutachaImageLoader.current
    val catalogImageLoader = LocalFutachaCatalogImageLoader.current
    val platformContext = LocalPlatformContext.current
    val removableCacheAvailable = remember(platformContext) {
        isPlatformRemovableCacheAvailable(platformContext)
    }
    LaunchedEffect(editingEntry?.preferenceKey, platformContext, removableCacheAvailable) {
        if (editingEntry?.preferenceKey !in setOf(
                "dummyImageCacheLocation",
                "dummyCatalogImageCacheLocation"
            )
        ) return@LaunchedEffect
        cacheAvailableBytes = withContext(AppDispatchers.io) {
            CompatibilityCacheLocation.entries.associateWith { location ->
                if (location == CompatibilityCacheLocation.EXTERNAL_SD && !removableCacheAvailable) {
                    null
                } else {
                    getPlatformCacheAvailableBytes(platformContext, location)
                }
            }
        }
    }
    val boards by store.boards.collectAsState(emptyList())
    val openUrl = rememberUrlLauncher()
    val backupFilePicker = rememberAttachmentPickerLauncher(
        preference = AttachmentPickerPreference.DOCUMENT,
        mimeType = "*/*",
        maxBytes = MAX_COMPAT_SETTINGS_BACKUP_BYTES.toLong(),
        onImageSelected = { selected ->
            if (backupInProgress) return@rememberAttachmentPickerLauncher
            val backupKind = restoreBackupKind
            backupInProgress = true
            scope.launch {
                val result = runSuspendCatchingPreservingCancellation {
                    val raw = selected.bytes.decodeToString()
                    if (backupKind == "ng") {
                        val currentPayload = runCatching { decodeCompatWatchNgBackup(raw) }
                            .recoverCatching { decodeCompatSettingsBackup(raw).watchAndNgOnly() }
                        if (currentPayload.isSuccess) {
                            val report = store.importSettingsBackup(
                                encodeCompatSettingsBackup(currentPayload.getOrThrow()),
                                restoreUserSettings = true,
                                restoreNgRules = true
                            )
                            compatBackupSuccessMessage(backupKind, report)
                        } else {
                            // 改修版/旧としあき(仮) exports a single
                            // Base64(JSON) keyword.cfg. Accept that file from
                            // the ordinary NG picker instead of requiring a
                            // separately selected directory.
                            importCompatLegacyBackupData(
                                store = store,
                                backups = listOf(decodeCompatLegacyBackup(raw)),
                                boards = boards
                            )
                        }
                    } else {
                        val payload = decodeCompatSettingsBackup(raw).settingsOnly()
                        val report = store.importSettingsBackup(
                            encodeCompatSettingsBackup(payload),
                            restoreUserSettings = true,
                            restoreNgRules = false
                        )
                        compatBackupSuccessMessage(backupKind, report)
                    }
                }
                result.onSuccess {
                    compatBackupDatePreferenceKey(backupKind)?.let { key ->
                        val timestamp = formatCompatBackupTimestamp(Clock.System.now().toEpochMilliseconds())
                        store.savePreference(key, timestamp)
                        backupDates = backupDates + (key to timestamp)
                    }
                }
                backupMessage = result.getOrElse { error ->
                    compatBackupFailureMessage(backupKind, error)
                }
                backupInProgress = false
            }
        }
    )
    val downloadDirectoryPicker = rememberDirectoryPickerLauncher(
        onDirectorySelected = { location ->
            val value = location.toRawString()
            savedValues = savedValues + ("dummyDownloadDir" to value)
            scope.launch {
                store.savePreference(compatPreferenceStorageKey("storage", "dummyDownloadDir"), value)
            }
        }
    )
    val drawingDirectoryPicker = rememberDirectoryPickerLauncher(
        onDirectorySelected = { location ->
            val value = location.toRawString()
            savedValues = savedValues + ("dummyDrawingDir" to value)
            scope.launch {
                store.savePreference(compatPreferenceStorageKey("storage", "dummyDrawingDir"), value)
            }
        }
    )
    val backupDirectoryPicker = rememberDirectoryPickerLauncher(
        onDirectorySelected = { location ->
            if (backupInProgress) return@rememberDirectoryPickerLauncher
            backupInProgress = true
            scope.launch {
                val result = runSuspendCatchingPreservingCancellation {
                    val payload = if (restoreBackupKind == "save_settings") {
                        encodeCompatSettingsBackup(
                            decodeCompatSettingsBackup(store.exportSettingsBackup()).settingsOnly()
                        )
                    } else if (restoreBackupKind == "save_ng") {
                        encodeCompatWatchNgBackup(
                            decodeCompatSettingsBackup(store.exportSettingsBackup())
                        )
                    } else if (restoreBackupKind == "legacy") {
                        importCompatLegacyBackup(store, fileSystem, location, boards)
                    } else {
                        val fs = fileSystem ?: error("ファイルシステムを利用できません")
                        val (raw, isDedicatedWordFile) = if (restoreBackupKind == "ng") {
                            runSuspendCatchingPreservingCancellation {
                                fs.readCompatBackupTextWithLimit(
                                    location,
                                    COMPAT_WATCH_NG_BACKUP_FILE_NAME,
                                    MAX_COMPAT_SETTINGS_BACKUP_BYTES.toLong()
                                )
                            }.fold(
                                onSuccess = { it to true },
                                onFailure = {
                                    // Files made before the split remain usable,
                                    // but only their word subset is ever applied.
                                    fs.readCompatBackupTextWithLimit(
                                        location,
                                        COMPAT_SETTINGS_BACKUP_FILE_NAME,
                                        MAX_COMPAT_SETTINGS_BACKUP_BYTES.toLong()
                                    ) to false
                                }
                            )
                        } else {
                            fs.readCompatBackupTextWithLimit(
                                location,
                                COMPAT_SETTINGS_BACKUP_FILE_NAME,
                                MAX_COMPAT_SETTINGS_BACKUP_BYTES.toLong()
                            ) to false
                        }
                        val importPayload = if (restoreBackupKind == "ng") {
                            encodeCompatSettingsBackup(
                                if (isDedicatedWordFile) {
                                    decodeCompatWatchNgBackup(raw)
                                } else {
                                    decodeCompatSettingsBackup(raw).watchAndNgOnly()
                                }
                            )
                        } else {
                            encodeCompatSettingsBackup(decodeCompatSettingsBackup(raw).settingsOnly())
                        }
                        val report = store.importSettingsBackup(
                            importPayload,
                            // The NG-only shape contains the watch-word
                            // preference, but no board/tab/general settings.
                            restoreUserSettings = true,
                            restoreNgRules = restoreBackupKind == "ng"
                        )
                        compatBackupSuccessMessage(restoreBackupKind, report)
                    }
                    if (restoreBackupKind == "save_settings") {
                        fileSystem?.writeString(location, COMPAT_SETTINGS_BACKUP_FILE_NAME, payload)?.getOrThrow()
                            ?: error("ファイルシステムを利用できません")
                        compatBackupSuccessMessage(restoreBackupKind)
                    } else if (restoreBackupKind == "save_ng") {
                        fileSystem?.writeString(location, COMPAT_WATCH_NG_BACKUP_FILE_NAME, payload)?.getOrThrow()
                            ?: error("ファイルシステムを利用できません")
                        compatBackupSuccessMessage(restoreBackupKind)
                    } else if (restoreBackupKind == "legacy") {
                        payload
                    } else null
                }
                result.onSuccess {
                    compatBackupDatePreferenceKey(restoreBackupKind)?.let { key ->
                        val timestamp = formatCompatBackupTimestamp(Clock.System.now().toEpochMilliseconds())
                        store.savePreference(key, timestamp)
                        backupDates = backupDates + (key to timestamp)
                    }
                }
                val message = result.getOrElse { error ->
                    compatBackupFailureMessage(restoreBackupKind, error)
                }
                backupMessage = message
                backupInProgress = false
            }
        }
    )
    val launchCustomFontPicker = rememberCompatFontPickerLauncher(
        onSelected = { selected ->
            val extension = selected.fileName.substringAfterLast('.', "").lowercase()
            if (extension !in setOf("ttf", "otf")) {
                infoDialog = "フォントファイルではありません"
            } else if (fileSystem == null) {
                infoDialog = "フォントの保存先を利用できません"
            } else {
                scope.launch {
                    runSuspendCatchingPreservingCancellation {
                        // Keep only the selected extension. Otherwise a font
                        // replaced from OTF to TTF could leave two candidates
                        // and make startup select the stale file.
                        fileSystem.deleteRecursively("private/compat_font").getOrThrow()
                        fileSystem.createDirectory("private/compat_font").getOrThrow()
                        fileSystem.writeBytes("private/compat_font/font.$extension", selected.bytes).getOrThrow()
                        store.savePreference(
                            compatPreferenceStorageKey("design", "dummyCustomFont"),
                            selected.fileName
                        )
                    }.onSuccess {
                        customFontName = selected.fileName
                        savedValues = savedValues + ("dummyCustomFont" to selected.fileName)
                        transientNotice = "アプリを再起動してください"
                    }.onFailure {
                        infoDialog = "フォントのコピーに失敗しました"
                    }
                }
            }
        },
        onError = { infoDialog = it }
    )
    // Keep a separate scroll position for every settings level. The previous
    // implementation recreated the root LazyColumn after returning from a
    // child page, so the root jumped by the amount scrolled in that child
    // (#39). This mirrors the APK's nested PreferenceActivity behavior
    // without sharing child offsets with the root page.
    val settingsListState = remember(path) {
        LazyListState(
            firstVisibleItemIndex = initialScrollPosition?.first ?: 0,
            firstVisibleItemScrollOffset = initialScrollPosition?.second ?: 0
        )
    }
    LaunchedEffect(path, settingsListState) {
        snapshotFlow {
            settingsListState.firstVisibleItemIndex to settingsListState.firstVisibleItemScrollOffset
        }.distinctUntilChanged().collect { position ->
            onScrollPositionChanged(position)
        }
    }
    LaunchedEffect(path, groups) {
        if (path == "root") {
            archiveReportStats = runSuspendCatchingPreservingCancellation {
                store.archiveReportOutboxStats()
            }.getOrNull()
            ptmtConfigured = runSuspendCatchingPreservingCancellation {
                cookieRepository?.listCookies()?.any { cookie ->
                    cookie.name == "ptmt" && cookie.domain.trimStart('.').endsWith("2chan.net")
                } == true
            }.getOrDefault(false)
            return@LaunchedEffect
        }
        if (path == "network") {
            return@LaunchedEffect
        }
        if (path == "image_search") {
            return@LaunchedEffect
        }
        if (path == "storage") {
            threadCacheUsageBytes = runSuspendCatchingPreservingCancellation {
                store.threadSnapshotCacheUsageBytes()
            }.getOrNull()
            imageCacheUsage = withContext(AppDispatchers.io) {
                runCatching {
                    val normalBytes = (imageLoader.diskCache?.size ?: 0L) +
                        (imageLoader.memoryCache?.size ?: 0L)
                    val catalogBytes = if (catalogImageLoader === imageLoader) 0L else {
                        (catalogImageLoader.diskCache?.size ?: 0L) +
                            (catalogImageLoader.memoryCache?.size ?: 0L)
                    }
                    CompatImageCacheUsage(normalBytes, catalogBytes)
                }.getOrNull()
            }
            attachmentCacheUsageBytes = runSuspendCatchingPreservingCancellation {
                compatibilityAttachmentCacheUsageBytes(fileSystem)
            }.getOrNull()
        }
    }
    // Keep Android gesture/3-button Back identical to the toolbar arrow.
    // This handler is inside the settings screen so it wins over the
    // compatibility workspace handler, including on Android 11 devices.
    PlatformBackHandler { onBack() }
    Scaffold(
        containerColor = SecondaryBackground,
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(if (path == "root") "設定" else path.compatSettingsTitle(), modifier = Modifier.padding(start = 16.dp)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") } },
                actions = {
                    if (path == "root") {
                        Row {
                            Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                IconButton(onClick = onOpenChangeLog) {
                                    Icon(Icons.Filled.History, contentDescription = "更新情報", tint = Color.White)
                                }
                            }
                            Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                IconButton(onClick = {
                                    openUrl("https://play.google.com/store/apps/details?id=com.valoser.futacha")
                                }) {
                                    Icon(Icons.Filled.BusinessCenter, contentDescription = "ストア", tint = Color.White)
                                }
                            }
                            Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                IconButton(onClick = onOpenHelp) {
                                    Icon(Icons.Filled.HelpOutline, contentDescription = "ヘルプ", tint = Color.White)
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryTeal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            state = settingsListState,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("compat-settings-list-$path")
        ) {
            groups.forEach { (group, entries) ->
                item(key = "group-$group") {
                    Text(
                        group,
                        // PreferenceCategory uses colorAccent in sample/1.apk.
                        // The black theme deliberately has a black chrome bar,
                        // so reusing chrome here made every section title vanish.
                        color = SecondaryAccent,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 25.dp, bottom = 2.dp)
                    )
                }
                itemsIndexed(entries, key = { index, entry -> "$group-${entry.preferenceKey}:$index" }) { _, entry ->
                    val isMode = path == "root" && entry.preferenceKey == "mode"
                    val isBoolean = compatIsBooleanPreference(path, entry)
                    val options = if (path == "root" || isBoolean) emptyList() else compatPreferenceOptions(path, entry)
                    val isThreadCacheClear = path == "storage" && entry.title == "スレッドキャッシュのクリア"
                    val isImageCacheClear = path == "storage" && entry.preferenceKey == "dummyImageCacheClear"
                    val isAttachmentClear = path == "storage" && entry.preferenceKey == "dummyAttachFileClear"
                    val isDirectoryPicker = path == "storage" && entry.preferenceKey in setOf("dummyDownloadDir", "dummyDrawingDir")
                    val isArchiveReportToggle = path == "root" && entry.preferenceKey == "archiveReportEnabled"
                    val isUpdateCheckToggle = path == "root" && entry.preferenceKey == "updateCheckEnabled"
                    val isArchiveReportClear = path == "root" && entry.preferenceKey == "archiveReportClear"
                    val isArchiveReportInfo = path == "root" && entry.preferenceKey == "archiveReportInfo"
                    val isCacheToggle = path == "network" && entry.preferenceKey == COMPAT_CACHE_ENABLED_KEY
                    val isCacheEndpoint = path == "network" && entry.preferenceKey == COMPAT_CACHE_BASE_URL_KEY
                    val isCacheStatus = path == "network" && entry.preferenceKey == COMPAT_CACHE_STATUS_KEY
                    val isCustomImageSearch = path == "image_search" &&
                        entry.preferenceKey.startsWith("customSearchUriMulti.")
                    val isSettingsRestore = path == "root" && entry.title == "基本的な設定の復元"
                    val isSettingsSave = path == "root" && entry.title == "基本的な設定の保存"
                    val isNgRestore = path == "root" && entry.title == "監視･ＮＧワードの復元"
                    val isNgSave = path == "root" && entry.title == "監視･ＮＧワードの保存"
                    val backupDateKey = when {
                        isSettingsRestore -> COMPAT_BACKUP_SETTING_IMPORT_DATE_KEY
                        isSettingsSave -> COMPAT_BACKUP_SETTING_EXPORT_DATE_KEY
                        isNgRestore -> COMPAT_BACKUP_KEYWORD_IMPORT_DATE_KEY
                        isNgSave -> COMPAT_BACKUP_KEYWORD_EXPORT_DATE_KEY
                        else -> null
                    }
                    val isLegacyRestore = path == "root" && entry.title == "旧版設定・NGの復元"
                    val isPtmtEditor = path == "root" && entry.preferenceKey == "ptmtEditor"
                    val isCustomFont = path == "design" && entry.preferenceKey == "dummyCustomFont"
                    val dependencyEnabled = !(
                        path == "catalog" && entry.preferenceKey == "catalogAppendDropped" &&
                            (savedValues["catalogFindThreadDeleted"] ?: "OFF") != "ON"
                        )
                    val infoAction = path == "root" && entry.title in setOf(
                        "更新情報", "ライセンス", "保存済みスレッド", "Twitter", "@create_app_null", "開発情報", "バージョン"
                    )
                    val checkedValue = when {
                        isUpdateCheckToggle -> isUpdateCheckEnabled
                        isArchiveReportToggle -> archiveReportEnabled
                        isCacheToggle -> cacheEnabled
                        isBoolean -> (savedValues[entry.preferenceKey] ?: entry.summary) == "ON"
                        else -> false
                    }
                    val customImageSearchTarget = if (isCustomImageSearch) {
                        CompatImageSearchTarget.entries.firstOrNull { it.label == entry.title }
                    } else {
                        null
                    }
                    val customImageSearchChecked = customImageSearchTarget?.let {
                        it in selectedCustomSearchEngines
                    } == true
                    val applyCustomImageSearch: (Boolean) -> Unit = { selected ->
                        customImageSearchTarget?.let { target ->
                            val next = if (selected) {
                                selectedCustomSearchEngines + target
                            } else {
                                selectedCustomSearchEngines - target
                            }
                            selectedCustomSearchEngines = next
                            scope.launch {
                                store.savePreference(
                                    COMPAT_CUSTOM_IMAGE_SEARCH_KEY,
                                    serializeCompatImageSearchTargets(next)
                                )
                            }
                        }
                    }
                    val applyBoolean: (Boolean) -> Unit = { next ->
                        when {
                            isMode -> modeDialog = true
                            isUpdateCheckToggle -> onUpdateCheckChanged(next)
                            isArchiveReportToggle && !archiveReportSettingInProgress -> {
                                archiveReportSettingInProgress = true
                                scope.launch {
                                    runSuspendCatchingPreservingCancellation {
                                        store.savePreference(
                                            ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY,
                                            if (next) "ON" else "OFF"
                                        )
                                    }.onSuccess {
                                        archiveReportEnabled = next
                                        onArchiveReportEnabledChanged(next)
                                    }
                                    archiveReportSettingInProgress = false
                                }
                            }
                            isCacheToggle -> {
                                if (next) {
                                    cacheWarningOpen = true
                                } else {
                                    cacheEnabled = false
                                    scope.launch { store.savePreference(COMPAT_CACHE_ENABLED_KEY, "OFF") }
                                }
                            }
                            else -> {
                                savedValues = savedValues + (entry.preferenceKey to if (next) "ON" else "OFF")
                                if (path == "design" && entry.preferenceKey == "designNavigationBar") {
                                    transientNotice = "画面の再描画時に反映されます"
                                }
                                scope.launch {
                                    store.savePreference(
                                        compatPreferenceStorageKey(path, entry.preferenceKey),
                                        if (next) "ON" else "OFF"
                                    )
                                }
                            }
                        }
                    }
                    val actionable = entry.enabled && dependencyEnabled && (
                        isMode || isBoolean ||
                        entry.route != null || options.isNotEmpty() || isThreadCacheClear ||
                            isImageCacheClear || isAttachmentClear || isDirectoryPicker ||
                            isUpdateCheckToggle || isArchiveReportToggle || isArchiveReportClear || isArchiveReportInfo ||
                            isCacheToggle || isCacheEndpoint || isCacheStatus || isCustomImageSearch ||
                            ((isSettingsRestore || isSettingsSave || isNgRestore || isNgSave || isLegacyRestore) && fileSystem != null) ||
                            infoAction ||
                            (isPtmtEditor && cookieRepository != null) || isCustomFont
                        )
                    CompatPreferenceRow(
                        modifier = Modifier.testTag("compat-setting-${entry.preferenceKey}"),
                        title = entry.title,
                        summary = when {
                            isMode -> "現在: ${profileController.activeProfile.displayName}"
                            isUpdateCheckToggle -> if (isUpdateCheckEnabled) {
                                "ON・起動時に最新リリースを確認"
                            } else {
                                "OFF・起動時の通信と通知を停止"
                            }
                            isBoolean -> compatBooleanPreferenceSummary(entry.preferenceKey)
                            isThreadCacheClear && threadCacheClearInProgress -> "削除中…"
                            isThreadCacheClear && threadCacheUsageBytes != null ->
                                "現在の使用量:${formatCompatCacheUsage(checkNotNull(threadCacheUsageBytes))}"
                            isThreadCacheClear -> "使用量を計算できません"
                            isImageCacheClear && imageCacheClearInProgress -> "削除中…"
                            isImageCacheClear && imageCacheUsage != null ->
                                "現在の使用量:${formatCompatImageCacheUsage(checkNotNull(imageCacheUsage))}"
                            isImageCacheClear -> "使用量を計算できません"
                            isAttachmentClear && attachmentClearInProgress -> "削除中…"
                            isAttachmentClear && attachmentCacheUsageBytes != null ->
                                "現在の使用量:${formatCompatCacheUsage(checkNotNull(attachmentCacheUsageBytes))}"
                            isAttachmentClear -> "使用量を計算できません"
                            isArchiveReportToggle && archiveReportSettingInProgress -> "変更中…"
                            isArchiveReportToggle -> if (archiveReportEnabled) {
                                "ON・取得成功した対応板のスレURLだけを15秒後に通知"
                            } else {
                                "OFF・新規登録と送信を停止（既存データは端末内に保持）"
                            }
                            isArchiveReportClear -> archiveReportStats?.let { stats ->
                                "端末内 ${stats.total}件（送信待ち ${stats.pendingOrRetry}件）"
                            } ?: "端末内の通知データを確認できません"
                            isArchiveReportInfo -> "HTML本文・画像・レス・利用者ID・端末IDは送信しません"
                            isCacheToggle -> if (cacheEnabled) {
                                "ON・キャッシュGETを先に試し、失敗時は元サイトへ戻します"
                            } else {
                                "OFF・元サイトを優先します"
                            }
                            isCacheEndpoint -> cacheBaseUrl.ifBlank { "板ごとのinqueuet.com endpoint" }
                            isCacheStatus -> cacheStatus ?: " - "
                            isCustomImageSearch -> ""
                            isSettingsRestore || isNgRestore -> if (backupInProgress) {
                                "復元中…"
                            } else {
                                backupDateKey?.let(backupDates::get).orEmpty()
                            }
                            isLegacyRestore -> if (backupInProgress) "復元中…" else entry.summary
                            isSettingsSave || isNgSave -> if (backupInProgress) {
                                "保存中…"
                            } else {
                                backupDateKey?.let(backupDates::get).orEmpty()
                            }
                            isPtmtEditor && !actionable -> "Cookie管理を初期化できません"
                            isPtmtEditor -> if (ptmtConfigured) "設定済み（値は表示しません）" else ""
                            isCustomFont -> customFontName
                            isDirectoryPicker -> compatStorageDirectorySummary(
                                entry.preferenceKey,
                                savedValues[entry.preferenceKey]
                            )
                            savedValues[entry.preferenceKey] != null -> compatPreferenceSummaryValue(
                                entry.preferenceKey,
                                savedValues.getValue(entry.preferenceKey)
                            )
                            options.isNotEmpty() -> compatPreferenceSummaryValue(
                                entry.preferenceKey,
                                entry.summary
                            )
                            !actionable && entry.enabled -> "未実装: ${entry.summary}"
                            else -> entry.summary
                        },
                        enabled = actionable,
                        checked = when {
                            isBoolean || isUpdateCheckToggle -> checkedValue
                            isCustomImageSearch -> customImageSearchChecked
                            else -> null
                        },
                        onCheckedChange = when {
                            isBoolean || isUpdateCheckToggle -> applyBoolean
                            isCustomImageSearch -> applyCustomImageSearch
                            else -> null
                        },
                        onClick = {
                            if (isMode) {
                                modeDialog = true
                            } else if (isBoolean || isUpdateCheckToggle) {
                                applyBoolean(!checkedValue)
                            } else if (isPtmtEditor) {
                                // Opening the reference dialog must never wait on cookie disk I/O.
                                // iOS can serialize NSFileManager access behind other startup work;
                                // waiting here made the row appear inert until that read completed.
                                ptmtValue = ""
                                ptmtCheck = ""
                                ptmtMessage = null
                                ptmtDialogOpen = true
                                scope.launch {
                                    val loadedValue = cookieRepository?.listCookies()
                                        ?.firstOrNull { it.name == "ptmt" && it.domain.trimStart('.').endsWith("2chan.net") }
                                        ?.value.orEmpty()
                                    // Do not overwrite text entered while the background read was pending.
                                    if (ptmtDialogOpen && ptmtValue.isEmpty()) ptmtValue = loadedValue
                                }
                            }
                            else if (isCustomFont) customFontDialogOpen = true
                            else if (entry.route != null) onNavigate(entry.route)
                            else if (isThreadCacheClear) confirmThreadCacheClear = true
                            else if (isImageCacheClear) confirmImageCacheClear = true
                            else if (isAttachmentClear) confirmAttachmentClear = true
                            else if (isDirectoryPicker) directoryMenuEntry = entry
                            else if (isArchiveReportClear) confirmArchiveReportClear = true
                            else if (isArchiveReportInfo) archiveReportInfoOpen = true
                            else if (isCacheToggle) {
                                if (cacheEnabled) {
                                    cacheEnabled = false
                                    scope.launch {
                                        store.savePreference(COMPAT_CACHE_ENABLED_KEY, "OFF")
                                    }
                                } else {
                                    // sample/1.apk asks for confirmation before cache-server
                                    // requests are enabled. Do not persist the switch until
                                    // the user accepts the explanation.
                                    cacheWarningOpen = true
                                }
                            }
                            else if (isCacheEndpoint) {
                                cacheEndpointDraft = cacheBaseUrl
                                cacheEndpointDialogOpen = true
                            }
                            // The reference status row is read-only. Main,
                            // Catalog and Thread hosts refresh it automatically.
                            else if (isCacheStatus) Unit
                            else if (isCustomImageSearch) {
                                applyCustomImageSearch(!customImageSearchChecked)
                            }
                            else if ((isSettingsRestore || isNgRestore) && !backupInProgress) {
                                restoreBackupKind = if (isSettingsRestore) "settings" else "ng"
                                backupFilePicker()
                            }
                            else if (isLegacyRestore && !backupInProgress) {
                                restoreBackupKind = "legacy"
                                backupDirectoryPicker()
                            }
                            else if ((isSettingsSave || isNgSave) && !backupInProgress) {
                                restoreBackupKind = if (isSettingsSave) "save_settings" else "save_ng"
                                backupDirectoryPicker()
                            }
                            else if (infoAction) {
                                when (entry.title) {
                                    "更新情報" -> onOpenChangeLog()
                                    "ライセンス" -> onOpenLicense()
                                    "保存済みスレッド" -> onOpenSavedThreads()
                                    "Twitter" -> openUrl(COMPAT_REFERENCE_AUTHOR_URL)
                                    "@create_app_null" -> openUrl(FUTACHA_AUTHOR_URL)
                                    "開発情報" -> openUrl("https://github.com/inqueuet/futacha")
                                    "バージョン" -> referenceVersionMessage = compatReferenceVersionMessage()
                                }
                            }
                            else editingEntry = entry
                        }
                    )
                }
                if (path == "root") {
                    item(key = "divider-$group") {
                        HorizontalDivider(color = Color(0x22000000))
                    }
                }
            }
        }
    }
    if (confirmThreadCacheClear) {
        AlertDialog(
            onDismissRequest = { if (!threadCacheClearInProgress) confirmThreadCacheClear = false },
            title = { Text("スレッドキャッシュのクリア") },
            text = { Text("保存済みのスレッド本文を削除します。タブ、履歴、下書き、元に戻すための一時データは削除されません。") },
            confirmButton = {
                TextButton(
                    enabled = !threadCacheClearInProgress,
                    onClick = {
                        threadCacheClearInProgress = true
                        scope.launch {
                            runSuspendCatchingPreservingCancellation {
                                store.clearThreadSnapshotCache()
                            }
                                .onSuccess { threadCacheUsageBytes = 0L }
                                .onFailure {
                                    threadCacheUsageBytes = runSuspendCatchingPreservingCancellation {
                                        store.threadSnapshotCacheUsageBytes()
                                    }.getOrNull()
                                }
                            threadCacheClearInProgress = false
                            confirmThreadCacheClear = false
                        }
                    }
                ) { Text(if (threadCacheClearInProgress) "削除中…" else "削除する") }
            },
            dismissButton = {
                TextButton(
                    enabled = !threadCacheClearInProgress,
                    onClick = { confirmThreadCacheClear = false }
                ) { Text("キャンセル") }
            }
        )
    }
    directoryMenuEntry?.let { entry ->
        val isDownload = entry.preferenceKey == "dummyDownloadDir"
        AlertDialog(
            onDismissRequest = { directoryMenuEntry = null },
            title = { Text(if (isDownload) "ダウンロード" else "手書き") },
            text = {
                Text(if (isDownload) "画像の保存などに利用します\n" else "手書き画像の保存に利用します\n")
            },
            confirmButton = {
                TextButton(onClick = {
                    directoryMenuEntry = null
                    if (isDownload) downloadDirectoryPicker() else drawingDirectoryPicker()
                }) { Text("フォルダ選択") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        savedValues = savedValues + (entry.preferenceKey to "")
                        directoryMenuEntry = null
                        scope.launch {
                            store.savePreference(
                                compatPreferenceStorageKey("storage", entry.preferenceKey),
                                ""
                            )
                        }
                    }) { Text("リセット") }
                    TextButton(onClick = { directoryMenuEntry = null }) { Text("キャンセル") }
                }
            }
        )
    }
    if (confirmImageCacheClear) {
        AlertDialog(
            onDismissRequest = { if (!imageCacheClearInProgress) confirmImageCacheClear = false },
            title = { Text("画像キャッシュのクリア") },
            text = { Text("読み込み済みの画像キャッシュを削除します。保存した画像やスレッド本文は削除されません。") },
            confirmButton = {
                TextButton(
                    enabled = !imageCacheClearInProgress,
                    onClick = {
                        imageCacheClearInProgress = true
                        scope.launch {
                            val usage = withContext(AppDispatchers.io) {
                                runCatching {
                                    imageLoader.diskCache?.clear()
                                    imageLoader.memoryCache?.clear()
                                    catalogImageLoader.diskCache?.clear()
                                    catalogImageLoader.memoryCache?.clear()
                                }
                                runCatching {
                                    val normalBytes = (imageLoader.diskCache?.size ?: 0L) +
                                        (imageLoader.memoryCache?.size ?: 0L)
                                    val catalogBytes = if (catalogImageLoader === imageLoader) 0L else {
                                        (catalogImageLoader.diskCache?.size ?: 0L) +
                                            (catalogImageLoader.memoryCache?.size ?: 0L)
                                    }
                                    CompatImageCacheUsage(normalBytes, catalogBytes)
                                }.getOrNull()
                            }
                            imageCacheUsage = usage
                            imageCacheClearInProgress = false
                            confirmImageCacheClear = false
                        }
                    }
                ) { Text(if (imageCacheClearInProgress) "削除中…" else "削除する") }
            },
            dismissButton = {
                TextButton(
                    enabled = !imageCacheClearInProgress,
                    onClick = { confirmImageCacheClear = false }
                ) { Text("キャンセル") }
            }
        )
    }
    if (confirmAttachmentClear) {
        AlertDialog(
            onDismissRequest = { if (!attachmentClearInProgress) confirmAttachmentClear = false },
            title = { Text("その他のクリア") },
            text = { Text("投稿画面で一時保存された添付ファイルを削除します。編集中の下書きから添付を再利用できなくなります。") },
            confirmButton = {
                TextButton(
                    enabled = !attachmentClearInProgress,
                    onClick = {
                        attachmentClearInProgress = true
                        scope.launch {
                            fileSystem?.deleteRecursively("private/compat_post_attachments")
                            attachmentCacheUsageBytes = runSuspendCatchingPreservingCancellation {
                                compatibilityAttachmentCacheUsageBytes(fileSystem)
                            }.getOrDefault(0L)
                            attachmentClearInProgress = false
                            confirmAttachmentClear = false
                        }
                    }
                ) { Text(if (attachmentClearInProgress) "削除中…" else "削除する") }
            },
            dismissButton = {
                TextButton(
                    enabled = !attachmentClearInProgress,
                    onClick = { confirmAttachmentClear = false }
                ) { Text("キャンセル") }
            }
        )
    }
    if (confirmArchiveReportClear) {
        AlertDialog(
            onDismissRequest = { confirmArchiveReportClear = false },
            title = { Text("通知データを削除") },
            text = {
                Text("未送信、再送待ち、受付済み、送信対象外の記録をすべて端末から削除します。この操作は元に戻せません。")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runSuspendCatchingPreservingCancellation {
                            store.clearArchiveReportOutbox()
                        }
                            .onSuccess { archiveReportStats = ArchiveReportOutboxStats(0, 0) }
                        confirmArchiveReportClear = false
                    }
                }) { Text("削除する") }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchiveReportClear = false }) { Text("キャンセル") }
            }
        )
    }
    if (archiveReportInfoOpen) {
        AlertDialog(
            onDismissRequest = { archiveReportInfoOpen = false },
            title = { Text("閲覧スレ通知について") },
            text = {
                Text(
                    "元サイトから正常に取得し、画面へ表示した対応板のスレURLを " +
                        "https://api.inqueuet.com へ通知します。HTML本文、画像、レス内容、閲覧時刻、" +
                        "利用者ID、端末ID、Cookieは送信しません。通常のHTTPS通信なので、送信元IPは" +
                        "サーバー側から確認可能です。OFFにすると新規登録と送信を停止し、既存データは" +
                        "削除操作をするまで端末内に保持します。"
                )
            },
            confirmButton = {
                TextButton(onClick = { archiveReportInfoOpen = false }) { Text("閉じる") }
            }
        )
    }
    if (cacheEndpointDialogOpen) {
        AlertDialog(
            onDismissRequest = { cacheEndpointDialogOpen = false },
            title = { Text("キャッシュサーバー接続先") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("空欄なら板ごとの <server>.inqueuet.com を使用します。HTTPSのサーバーrootを入力してください。")
                    TextField(
                        value = cacheEndpointDraft,
                        onValueChange = { cacheEndpointDraft = it.take(300) },
                        label = { Text("例: https://may.inqueuet.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val normalized = normalizeCompatCacheBaseUrl(cacheEndpointDraft)
                    if (cacheEndpointDraft.isNotBlank() && normalized == null) {
                        cacheStatus = "接続先URLが不正です"
                    } else {
                        cacheBaseUrl = normalized.orEmpty()
                        scope.launch {
                            store.savePreference(COMPAT_CACHE_BASE_URL_KEY, normalized.orEmpty())
                        }
                        cacheEndpointDialogOpen = false
                    }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { cacheEndpointDialogOpen = false }) { Text("キャンセル") } }
        )
    }
    if (cacheWarningOpen) {
        CompatCacheServerWarningDialog(
            onConfirm = {
                cacheWarningOpen = false
                cacheEnabled = true
                scope.launch { store.savePreference(COMPAT_CACHE_ENABLED_KEY, "ON") }
            }
        )
    }
    if (backupMessage != null) {
        AlertDialog(
            onDismissRequest = { backupMessage = null },
            title = { Text("バックアップ") },
            text = { Text(backupMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { backupMessage = null }) { Text("閉じる") }
            }
        )
    }
    if (referenceVersionMessage != null) {
        AlertDialog(
            onDismissRequest = { referenceVersionMessage = null },
            text = { Text(referenceVersionMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { referenceVersionMessage = null }) { Text("閉じる") }
            }
        )
    }
    if (infoDialog != null) {
        val title = infoDialog.orEmpty()
        AlertDialog(
            onDismissRequest = { infoDialog = null },
            title = { Text(title) },
            text = if (title == "バージョン") {
                {
                    Text(
                        "ふたちゃ $appVersion\n互換モードの設定バックアップ schema " +
                            "v${CURRENT_COMPAT_SETTINGS_BACKUP_VERSION}"
                    )
                }
            } else null,
            confirmButton = { TextButton(onClick = { infoDialog = null }) { Text("閉じる") } }
        )
    }
    if (modeDialog) {
        var selected by remember { mutableStateOf(profileController.activeProfile) }
        AlertDialog(
            onDismissRequest = { modeDialog = false },
            title = { Text("モード") },
            text = {
                Column {
                    ExperienceProfile.entries.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { selected = profile },
                                onLongClick = {}
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == profile, onClick = { selected = profile })
                            Column(Modifier.padding(vertical = 8.dp)) {
                                Text(profile.displayName)
                                if (profile == ExperienceProfile.TOSHIAKI_COMPAT) {
                                    Text("非公式表示モード。元アプリや開発者との公式な関係はありません。", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    modeDialog = false
                    if (selected != profileController.activeProfile) profileController.requestSwitch(selected)
                }) { Text("切り替える") }
            },
            dismissButton = { TextButton(onClick = { modeDialog = false }) { Text("キャンセル") } }
        )
    }
    editingEntry?.let { entry ->
        // The dialog occupies the same Compose slot for every preference.
        // Key its scroll state by the actual preference so opening a short
        // option list after scrolling a long one cannot inherit an out-of-
        // range firstVisibleItemIndex and render an apparently empty dialog.
        val optionListState = remember(entry.preferenceKey) { LazyListState() }
        val cacheLocationEntry = path == "storage" && entry.preferenceKey in setOf(
            "dummyImageCacheLocation",
            "dummyCatalogImageCacheLocation"
        )
        val options = if (cacheLocationEntry) {
            compatCacheLocationOptions(
                removableAvailable = removableCacheAvailable,
                includeInternal = entry.preferenceKey == "dummyCatalogImageCacheLocation"
            )
        } else {
            compatPreferenceOptions(path, entry)
        }
        val selectedValue = compatPreferenceDisplayValue(
            entry.preferenceKey,
            savedValues[entry.preferenceKey] ?: entry.summary
        )
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text(compatPreferenceDialogTitle(entry)) },
            text = {
                LazyColumn(
                    state = optionListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compat-setting-options")
                        .heightIn(min = 48.dp, max = 520.dp)
                ) {
                    items(options, key = { it }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (cacheLocationEntry) 60.dp else 48.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (option == "外部SDカード(利用不可)") {
                                            editingEntry = null
                                            infoDialog = "外部SDカードが見つかりません"
                                            return@combinedClickable
                                        }
                                        val storedValue = compatPreferenceStoredValue(entry.preferenceKey, option)
                                        savedValues = savedValues + (entry.preferenceKey to storedValue)
                                        editingEntry = null
                                        if (
                                            path == "design" && entry.preferenceKey in setOf(
                                                "designTheme", "designTabSelectorLocation"
                                            )
                                        ) {
                                            transientNotice = "画面の再描画時に反映されます"
                                        }
                                        if (
                                            path == "background" && option == "常に確認する" &&
                                            entry.preferenceKey in setOf(
                                                "backgroundThreadExistCheck",
                                                "backgroundThreadUpdateCheck"
                                            )
                                        ) {
                                            backgroundAlwaysNotice = entry
                                        }
                                        scope.launch {
                                            if (cacheLocationEntry) cacheLocationChangeInProgress = true
                                            runSuspendCatchingPreservingCancellation {
                                                applyCompatCacheLocationChange(
                                                    preferenceKey = entry.preferenceKey,
                                                    storedValue = storedValue,
                                                    clearOrdinaryImageCache = {
                                                        withContext(AppDispatchers.io) {
                                                            imageLoader.memoryCache?.clear()
                                                            imageLoader.diskCache?.clear()
                                                        }
                                                    },
                                                    savePreference = { value ->
                                                        store.savePreference(
                                                            compatPreferenceStorageKey(path, entry.preferenceKey),
                                                            value
                                                        )
                                                    }
                                                )
                                            }.onFailure { failure ->
                                                infoDialog = "設定の保存に失敗しました: ${failure.message.orEmpty()}"
                                            }
                                            cacheLocationChangeInProgress = false
                                        }
                                    },
                                    onLongClick = {}
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = option == selectedValue, onClick = null)
                            Column(
                                modifier = Modifier.weight(1f).padding(start = 4.dp, end = 16.dp)
                            ) {
                                Text(option)
                                if (cacheLocationEntry) {
                                    Text(
                                        compatCacheLocationNote(
                                            option,
                                            cacheAvailableBytes[compatCacheLocation(option)]
                                        ),
                                        fontSize = 11.sp,
                                        color = LocalCompatibilityPalette.current.text.copy(alpha = 0.65f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editingEntry = null }) { Text("キャンセル") } }
        )
    }
    if (cacheLocationChangeInProgress) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("変更中") },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("画像キャッシュの保存先を変更しています")
                }
            },
            confirmButton = {}
        )
    }
    if (ptmtDialogOpen) {
        val repository = cookieRepository
        AlertDialog(
            modifier = Modifier.testTag("compat-ptmt-dialog"),
            onDismissRequest = { ptmtDialogOpen = false },
            title = { Text("ptmtクッキーの編集") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextField(
                        value = ptmtValue,
                        onValueChange = { ptmtValue = it.take(2048) },
                        label = { Text("ptmtクッキー") },
                        singleLine = true,
                        modifier = Modifier
                            .testTag("compat-ptmt-value")
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = SecondaryTeal,
                            unfocusedIndicatorColor = Color.Gray
                        )
                    )
                    TextField(
                        value = ptmtCheck,
                        onValueChange = { ptmtCheck = it.take(32) },
                        label = { Text("後悔しませんね？") },
                        singleLine = true,
                        modifier = Modifier
                            .testTag("compat-ptmt-check")
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = SecondaryTeal,
                            unfocusedIndicatorColor = Color.Gray
                        )
                    )
                    Text(
                        "・ptmtはキャリア回線で書き込む際に必要なCookieです\n" +
                            "・リセットか空欄にすると削除します\n" +
                            "・誤操作防止の為、「後悔しません」と入力して下さい",
                        fontSize = 18.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                    ptmtMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("compat-ptmt-change"),
                    onClick = {
                        val error = validateCompatPtmtValue(ptmtValue, ptmtCheck)
                        if (error != null) {
                            ptmtMessage = error
                        } else {
                            scope.launch {
                                val existing = repository?.listCookies()?.firstOrNull {
                                    it.name == "ptmt" && it.domain.trimStart('.').endsWith("2chan.net")
                                }
                                val notice = compatPtmtMutationNotice(existing?.value, ptmtValue)
                                if (notice == "変更はありません") {
                                    // No storage mutation is needed, but the reference still shows a toast.
                                } else if (ptmtValue.isBlank()) {
                                    if (existing != null) {
                                        repository.deleteCookie(existing.domain, existing.path, existing.name)
                                    }
                                    ptmtConfigured = false
                                } else {
                                    repository?.setCookie(
                                        requestUrl = "https://www.2chan.net/",
                                        name = "ptmt",
                                        value = ptmtValue,
                                        domain = "2chan.net",
                                        expiresAtMillis = Clock.System.now().toEpochMilliseconds() + 200_261_632L
                                    )
                                    ptmtConfigured = true
                                }
                                transientNotice = notice
                                ptmtDialogOpen = false
                            }
                        }
                    }
                ) { Text("変更する") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        modifier = Modifier.testTag("compat-ptmt-reset"),
                        onClick = {
                            val checkError = validateCompatPtmtCheck(ptmtCheck)
                            if (checkError != null) {
                                ptmtMessage = checkError
                            } else {
                                scope.launch {
                                    val existing = repository?.listCookies()?.firstOrNull {
                                        it.name == "ptmt" && it.domain.trimStart('.').endsWith("2chan.net")
                                    }
                                    if (existing == null) {
                                        ptmtMessage = "既にありません"
                                    } else {
                                        repository?.deleteCookie(existing.domain, existing.path, existing.name)
                                        ptmtConfigured = false
                                        ptmtDialogOpen = false
                                    }
                                }
                            }
                        }
                    ) { Text("リセット") }
                    TextButton(
                        modifier = Modifier.testTag("compat-ptmt-cancel"),
                        onClick = { ptmtDialogOpen = false }
                    ) { Text("キャンセル") }
                }
            }
        )
    }
    if (customFontDialogOpen) {
        AlertDialog(
            onDismissRequest = { customFontDialogOpen = false },
            title = { Text("カスタムフォント") },
            confirmButton = {
                TextButton(onClick = {
                    customFontDialogOpen = false
                    launchCustomFontPicker()
                }) { Text("選択") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        customFontDialogOpen = false
                        scope.launch {
                            runSuspendCatchingPreservingCancellation {
                                fileSystem?.deleteRecursively("private/compat_font")?.getOrThrow()
                                store.savePreference(
                                    compatPreferenceStorageKey("design", "dummyCustomFont"),
                                    "デフォルト"
                                )
                            }.onSuccess {
                                customFontName = "デフォルト"
                                savedValues = savedValues + ("dummyCustomFont" to "デフォルト")
                                transientNotice = "アプリを再起動してください"
                            }.onFailure {
                                infoDialog = "既存フォントの削除に失敗しました"
                            }
                        }
                    }) { Text("リセット") }
                    TextButton(onClick = { customFontDialogOpen = false }) { Text("キャンセル") }
                }
            }
        )
    }
    LaunchedEffect(transientNotice) {
        if (transientNotice == null) return@LaunchedEffect
        delay(2_000)
        transientNotice = null
    }
    transientNotice?.let { notice ->
        Popup(
            alignment = Alignment.BottomCenter,
            offset = IntOffset(0, -96),
            properties = PopupProperties(focusable = false)
        ) {
            Surface(
                modifier = Modifier.testTag("compat-settings-transient-notice"),
                color = Color(0xE62B2B2B),
                contentColor = Color.White,
                shape = RoundedCornerShape(4.dp),
                shadowElevation = 6.dp
            ) {
                Text(notice, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }
    backgroundAlwaysNotice?.let { entry ->
        val message = if (entry.preferenceKey == "backgroundThreadExistCheck") {
            "しばらく更新されていないスレッドを確認して履歴に反映させます\n" +
                "落ちたスレを明確にしておけば履歴の管理や更新の確認に役立ちます\n" +
                "常に確認する場合は通信量などに十分注意してください"
        } else {
            "カタログからレス数を取得して更新分を履歴やツールバーに反映させます\n" +
                "常に確認する場合は通信量などに十分注意してください"
        }
        AlertDialog(
            onDismissRequest = { backgroundAlwaysNotice = null },
            title = { Text("注意事項") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { backgroundAlwaysNotice = null }) { Text("OK") }
            }
        )
    }
}

/**
 * Imports the Base64(JSON) files written by the original toshiaki(仮) APK.
 * The selected location is a directory because the APK exported two files
 * with fixed names: keyword.cfg and setting.cfg. Missing one is fine; this
 * lets users restore just their NG/watch data.
 */
private suspend fun importCompatLegacyBackup(
    store: CompatibilityStore,
    fileSystem: FileSystem?,
    location: SaveLocation,
    boards: List<CompatBoard>
): String {
    val fs = fileSystem ?: error("ファイルシステムを利用できません")
    val backups = listOf("keyword.cfg", "setting.cfg")
        .mapNotNull { fileName ->
            runSuspendCatchingPreservingCancellation {
                fs.readCompatBackupTextWithLimit(
                    location,
                    fileName,
                    MAX_COMPAT_LEGACY_BACKUP_BYTES.toLong()
                )
            }.getOrNull()
        }
        .map(::decodeCompatLegacyBackup)
    require(backups.isNotEmpty()) { "keyword.cfg / setting.cfg が見つかりません" }
    return importCompatLegacyBackupData(store, backups, boards)
}

private suspend fun importCompatLegacyBackupData(
    store: CompatibilityStore,
    backups: List<CompatLegacyBackupData>,
    boards: List<CompatBoard>
): String {
    require(backups.isNotEmpty()) { "旧版バックアップが空です" }
    val now = Clock.System.now().toEpochMilliseconds()
    backups.flatMap { it.boards }.distinctBy { it.canonicalUrl }.forEach { legacyBoard ->
        store.upsertBoard(
            CompatBoard(
                key = compatBoardKey(legacyBoard.canonicalUrl),
                name = legacyBoard.name,
                canonicalUrl = legacyBoard.canonicalUrl,
                originalUrl = legacyBoard.originalUrl,
                sortOrder = legacyBoard.sortOrder
            )
        )
    }
    val availableBoards = (boards + backups.flatMap { it.boards }.map { legacyBoard ->
        CompatBoard(
            key = compatBoardKey(legacyBoard.canonicalUrl),
            name = legacyBoard.name,
            canonicalUrl = legacyBoard.canonicalUrl,
            originalUrl = legacyBoard.originalUrl,
            sortOrder = legacyBoard.sortOrder
        )
    }).distinctBy(CompatBoard::key)
    applyCompatLegacyPortableSettings(
        backups = backups,
        availableBoards = availableBoards,
        savePreference = store::savePreference,
        loadCatalogPreference = store::loadCatalogPreference,
        saveCatalogPreference = store::saveCatalogPreference,
        saveToolbar = store::saveToolbar
    )

    // CatalogExtract belongs to the board recorded by the legacy APK. Never
    // copy it into the current app's global watch-word preference: doing so
    // silently widens a board-scoped rule to every board (#54).
    var catalogExtractImported = 0
    buildCompatLegacyCatalogExtractRules(backups, availableBoards, now).forEach { rule ->
        if (store.upsertNgRule(rule)) catalogExtractImported++
    }

    var catalogNgImported = 0
    backups.flatMap { it.catalogNgWords }.forEach { entry ->
        legacyCatalogRuleScopes(entry.boardUrl, availableBoards).forEach { boardKey ->
            val value = entry.word.trim().lowercase()
            if (value.isBlank()) return@forEach
            if (store.upsertNgRule(
                    CompatNgRule(
                        id = compatNgRuleId(CompatNgKind.CATALOG_IGNORE, boardKey, value),
                        kind = CompatNgKind.CATALOG_IGNORE,
                        scopeKey = boardKey,
                        normalizedValue = value,
                        createdAtEpochMillis = now
                    )
                )
            ) catalogNgImported++
        }
    }

    // The old database allowed a thread NG rule to be attached to a board,
    // while the compatibility model attaches thread rules to tabs. A global
    // rule is the only lossless choice during import and behaves like the old
    // app until the user narrows it from the NG dialog.
    var threadNgImported = 0
    backups.flatMap { it.threadNgHeaders }.forEach { entry ->
        val value = entry.word.trim().lowercase()
        if (value.isBlank()) return@forEach
        if (store.upsertNgRule(
                CompatNgRule(
                    id = compatNgRuleId(CompatNgKind.THREAD_REFUSE, "*", value),
                    kind = CompatNgKind.THREAD_REFUSE,
                    scopeKey = "*",
                    normalizedValue = value,
                    createdAtEpochMillis = now
                )
            )
        ) threadNgImported++
    }
    backups.flatMap { it.threadNgWords }.forEach { entry ->
        val value = entry.word.trim().lowercase()
        if (value.isBlank()) return@forEach
        if (store.upsertNgRule(
                CompatNgRule(
                    id = compatNgRuleId(CompatNgKind.THREAD_IGNORE, "*", value),
                    kind = CompatNgKind.THREAD_IGNORE,
                    scopeKey = "*",
                    normalizedValue = value,
                    createdAtEpochMillis = now
                )
            )
        ) threadNgImported++
    }

    val settingCount = backups.filter { it.fileType == "setting" }.sumOf { it.preferences.size }
    val watchCount = backups.sumOf { it.catalogWatchWords.size }
    return "旧版バックアップを復元しました（設定${settingCount}件、監視${watchCount}件、" +
        "抽出${catalogExtractImported}件、カタログNG${catalogNgImported}件、スレNG${threadNgImported}件）"
}

/**
 * Applies every portable setting decoded from setting.cfg. Keeping this separate from the
 * platform directory picker makes the actual persistence path directly regression-testable;
 * parsing a value without saving it was the source of the original restore gap.
 */
internal suspend fun applyCompatLegacyPortableSettings(
    backups: List<CompatLegacyBackupData>,
    availableBoards: List<CompatBoard>,
    savePreference: suspend (String, String) -> Unit,
    loadCatalogPreference: suspend (String) -> CompatCatalogPreference,
    saveCatalogPreference: suspend (CompatCatalogPreference) -> Unit,
    saveToolbar: suspend (CompatToolbarSurface, List<CompatToolbarItem>) -> Unit
) {
    backups.flatMap { it.preferences.entries }.distinctBy { it.key }
        .forEach { (key, value) -> savePreference(key, value) }
    backups.asSequence().mapNotNull { it.catalogSort }.firstOrNull()?.let { sort ->
        availableBoards.forEach { board ->
            val current = loadCatalogPreference(board.key)
            saveCatalogPreference(current.copy(sort = sort))
        }
    }
    CompatToolbarSurface.entries.forEach { surface ->
        backups.asSequence()
            .mapNotNull { backup -> backup.toolbars[surface] }
            .firstOrNull()
            ?.let { items -> saveToolbar(surface, items) }
    }
}

private suspend fun FileSystem.readCompatBackupTextWithLimit(
    location: SaveLocation,
    fileName: String,
    maxBytes: Long
): String {
    val size = getFileSize(location, fileName)
    require(size in 0L..maxBytes) { "バックアップファイルが大きすぎます" }
    val payload = readString(location, fileName).getOrThrow()
    require(payload.encodeToByteArray().size.toLong() <= maxBytes) {
        "バックアップファイルが大きすぎます"
    }
    return payload
}

private fun legacyCatalogScopes(boardUrl: String?, boards: List<CompatBoard>): List<String> =
    if (boardUrl == null) {
        boards.map(CompatBoard::key)
    } else {
        val canonical = canonicalizeBoardUrl(boardUrl) ?: return emptyList()
        boards.filter { canonicalizeBoardUrl(it.canonicalUrl) == canonical }.map(CompatBoard::key)
    }

private fun legacyCatalogRuleScopes(boardUrl: String?, boards: List<CompatBoard>): List<String> =
    if (boardUrl == null) listOf("*") else legacyCatalogScopes(boardUrl, boards)

/** Builds only scoped rules; legacy CatalogExtract data is never a global preference. */
internal fun buildCompatLegacyCatalogExtractRules(
    backups: List<CompatLegacyBackupData>,
    boards: List<CompatBoard>,
    nowEpochMillis: Long
): List<CompatNgRule> = backups.flatMap { backup ->
    backup.catalogWatchWords.flatMap entryLoop@ { entry ->
        val value = entry.word.trim().lowercase()
        if (value.isBlank()) return@entryLoop emptyList()
        legacyCatalogRuleScopes(entry.boardUrl, boards).map { scopeKey ->
            CompatNgRule(
                id = compatNgRuleId(CompatNgKind.CATALOG_EXTRACT, scopeKey, value),
                kind = CompatNgKind.CATALOG_EXTRACT,
                scopeKey = scopeKey,
                normalizedValue = value,
                createdAtEpochMillis = nowEpochMillis
            )
        }
    }
}

@Composable
internal fun CompatCacheServerWarningDialog(
    onConfirm: () -> Unit
) {
    AlertDialog(
        // sample/1.apk explicitly disables both Back and outside dismissal.
        onDismissRequest = {},
        title = { Text("確認") },
        text = {
            Text(
                "本来のHTMLからタグを削除したり内容をコンパクトにした解析済みのデータを" +
                    "サーバーから取得します\n詳しい仕様と注意点はヘルプを確認して下さい"
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } }
    )
}

@Composable
internal fun CompatHelpScreen(
    onBack: () -> Unit,
    onOpenChangeLog: (() -> Unit)? = null
) {
    val openUrl = rememberUrlLauncher()
    val palette = LocalCompatibilityPalette.current
    val helpHtml = remember(palette) { compatibilityReferenceHelpHtml(palette) }
    Scaffold(
        containerColor = palette.background,
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = { Text(COMPAT_REFERENCE_HELP_TITLE, modifier = Modifier.padding(start = 16.dp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        onOpenChangeLog?.invoke()
                            ?: openUrl("https://github.com/inqueuet/futacha/releases")
                    }) {
                        Icon(Icons.Filled.History, contentDescription = "変更履歴")
                    }
                    IconButton(onClick = {
                        openUrl(COMPAT_CURRENT_STORE_URL)
                    }) {
                        Icon(Icons.Filled.BusinessCenter, contentDescription = "ストア")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryTeal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        CompatReferenceChangeLogView(
            html = helpHtml,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("compat-help-content"),
            onLinkClicked = openUrl
        )
    }
}

internal fun compatPreferenceOptions(path: String, entry: CompatSettingEntry): List<String> {
    val key = entry.preferenceKey
    if (!entry.enabled) return emptyList()
    if (entry.summary in setOf("ON", "OFF")) return listOf("ON", "OFF")
    return when {
        key == "designTheme" -> listOf("デフォルト", "モノクロ", "ふたば", "ブルー", "ピンク", "ブラック")
        key == "designTextColor" -> listOf("自動", "白", "薄い灰", "濃い灰", "黒")
        key == "designLoading" -> listOf("デフォルト", "アイコン")
    key == "designTabSelectorLocation" -> listOf("ツールバーと二段で表示", "ツールバーの上に重ねる")
        key == "controlCatalogVolumeKey" -> listOf("何もしない", "スクロール")
        key == "controlThreadVolumeKey" ->
            listOf("何もしない", "1レス分スクロール", "1画面分スクロール", "スレッドの切り替え")
        key == "controlCatalogLongTap" ->
            listOf("何もしない", "選択メニュー", "NGスレッドに登録", "delを送信する", "タブに追加する")
        key == "controlTabSelectorLongTap" ->
            listOf("何もしない", "選択メニュー", "更新の確認", "再読み込み", "レスを書き込む", "スレを閉じる")
        key == "controlCloseToastDuration" ->
            listOf("0", "500", "1000", "1500", "2000", "7000")
        key in setOf("backgroundThreadExistCheck", "backgroundThreadUpdateCheck") ->
            listOf("常に確認する", "Wi-Fi回線のみ", "利用しない")
        path == "image_search" -> listOf("未選択", "選択")
        key in setOf("commonImageCache", "commonCatalogImageCache", "commonThreadCache") ->
            listOf("32MB", "64MB", "128MB", "256MB", "512MB", "1GB", "2GB", "無制限")
        key in setOf("dummyImageCacheLocation", "dummyCatalogImageCacheLocation") ->
            listOf("端末ストレージ", "外部SDカード")
        key == "networkImageParallel" ->
            listOf("1本(1枚ずつ)", "2本", "3本", "4本", "5本", "6本(既定)", "8本")
        key == "delayFewReplies" -> listOf("0（ソートしない）") + (1..30).map(Int::toString)
        key == "commonPrivacyAlpha" -> (90 downTo 10 step 10).map { "$it%" }
        key in setOf("catalogGridViewTitleLength", "catalogListViewTitleLength") ->
            (0..30).map(Int::toString)
        key in setOf("catalogGridViewTitleFontSize", "catalogListViewTitleFontSize") ->
            (6..16).map(Int::toString)
        key in setOf(
            "catalogGridViewPortraitClmNum", "catalogGridViewLandscapeClmNum",
            "galleryGridViewPortraitClmNum", "galleryGridViewLandscapeClmNum"
        ) -> (2..16).map(Int::toString)
        key == "catalogListViewLineNum" -> (6..20).map(Int::toString)
        key == "catalogThreadSize" ->
            listOf("50スレ", "100スレ", "200スレ", "300スレ", "500スレ", "800スレ", "1000スレ", "2000スレ", "3000スレ")
        key == "catalogTitleLength" -> listOf("10文字", "20文字", "30文字")
        key == "autoScrollPixel" -> (1..30).map(Int::toString)
        key == "autoScrollSpeed" -> ((10..100 step 5) + listOf(150, 200)).map(Int::toString)
        key == "threadHeaderSoudaneDisplay" ->
            listOf("通常", "通常(右寄せ)", "シンプル", "シンプル(右寄せ)", "非表示")
        key == "threadFontSize" -> (10..30).map(Int::toString)
        key in setOf("threadThumbSize", "threadUpsThumbSize") ->
            listOf("150", "200", "250", "300", "360", "410", "480", "640", "720", "800", "1000", "1200")
        key == "threadUpsThumbMethod" ->
            listOf("表示しない", "表示する", "表示する(先読み)", "Wi-Fi回線のみ先読み")
        key == "threadExtractSoudaneNum" -> (1..10).map(Int::toString)
        key == "threadExtractQuoteNum" -> (2..10).map(Int::toString)
        key == "threadImageNgPhashThreshold" -> (0..16).map(Int::toString)
        key == "viewerPreloadMode" -> listOf("常に利用する", "Wi-Fi回線のみ", "利用しない")
        else -> emptyList()
    }
}

internal fun compatPreferenceDialogTitle(entry: CompatSettingEntry): String =
    when (entry.preferenceKey) {
        "backgroundThreadExistCheck", "backgroundThreadUpdateCheck", "viewerPreloadMode" -> "選択"
        "catalogTitleLength" -> "スレッド文の長さ"
        else -> entry.title
    }

internal fun compatCacheLocationOptions(
    removableAvailable: Boolean,
    includeInternal: Boolean = false
): List<String> = buildList {
    if (includeInternal) add("内部ストレージ")
    add("端末ストレージ")
    add(if (removableAvailable) "外部SDカード" else "外部SDカード(利用不可)")
}

internal fun compatCacheLocation(option: String): CompatibilityCacheLocation = when {
    option.startsWith("内部ストレージ") -> CompatibilityCacheLocation.INTERNAL
    option.startsWith("外部SDカード") -> CompatibilityCacheLocation.EXTERNAL_SD
    else -> CompatibilityCacheLocation.DEVICE
}

/** 1.apk clears only the ordinary image cache before moving its location. */
internal suspend fun applyCompatCacheLocationChange(
    preferenceKey: String,
    storedValue: String,
    clearOrdinaryImageCache: suspend () -> Unit,
    savePreference: suspend (String) -> Unit
) {
    if (preferenceKey == "dummyImageCacheLocation") clearOrdinaryImageCache()
    savePreference(storedValue)
}

internal fun compatStorageDirectorySummary(preferenceKey: String, rawValue: String?): String {
    val defaultSummary = if (preferenceKey == "dummyDrawingDir") {
        "未設定時: 一時保存。残す場合は保存先を設定"
    } else {
        "未設定時：標準フォルダに保存"
    }
    val raw = rawValue?.trim().orEmpty()
    if (raw.isBlank()) return defaultSummary
    val location = SaveLocation.fromString(raw)
    val name = when (location) {
        is SaveLocation.Path -> location.path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
        is SaveLocation.TreeUri -> percentDecodeCompatUriSegment(
            location.uri.substringAfterLast('/')
        ).substringAfterLast(':')
        is SaveLocation.Bookmark -> "選択済み"
    }.ifBlank { "選択済み" }
    return "任意フォルダ：$name"
}

internal suspend fun compatibilityAttachmentCacheUsageBytes(fileSystem: FileSystem?): Long {
    if (fileSystem == null) return 0L
    val directory = "private/compat_post_attachments"
    if (!fileSystem.exists(directory)) return 0L
    var total = 0L
    fileSystem.listFiles(directory).forEach { name ->
        val size = runCatching { fileSystem.getFileSize("$directory/$name") }.getOrDefault(0L)
        if (size > 0L && total <= Long.MAX_VALUE - size) total += size
    }
    return total
}

private fun percentDecodeCompatUriSegment(value: String): String {
    val bytes = mutableListOf<Byte>()
    var index = 0
    while (index < value.length) {
        if (value[index] == '%' && index + 2 < value.length) {
            val hex = value.substring(index + 1, index + 3).toIntOrNull(16)
            if (hex != null) {
                bytes += hex.toByte()
                index += 3
                continue
            }
        }
        bytes += value[index].toString().encodeToByteArray().toList()
        index++
    }
    return bytes.toByteArray().decodeToString()
}

internal fun compatCacheLocationNote(option: String, availableBytes: Long? = null): String {
    val characteristic = when {
    option.startsWith("内部ストレージ") -> "最速・小容量"
    option.startsWith("外部SDカード") -> "低速・大容量"
    else -> "高速"
    }
    return availableBytes?.let { "$characteristic・空き ${formatCompatAvailableSpace(it)}" }
        ?: characteristic
}

internal fun formatCompatAvailableSpace(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val gibibyte = 1024L * 1024L * 1024L
    return if (safeBytes >= gibibyte) {
        val tenths = (safeBytes * 10L / gibibyte)
        "${tenths / 10}.${tenths % 10}GB"
    } else {
        "${safeBytes / (1024L * 1024L)}MB"
    }
}

internal fun compatPreferenceStoredValue(preferenceKey: String, displayedValue: String): String =
    when (preferenceKey) {
        "designTheme" -> when (displayedValue.lowercase()) {
            "デフォルト", "default" -> "default"
            "モノクロ", "mono" -> "mono"
            "ふたば", "futaba" -> "futaba"
            "ブルー", "blue" -> "blue"
            "ピンク", "pink" -> "pink"
            "ブラック", "black" -> "black"
            else -> displayedValue
        }
        "designLoading" -> when (displayedValue.lowercase()) {
            "デフォルト", "default" -> "default"
            "アイコン", "icon" -> "icon"
            else -> displayedValue
        }
        "designTabSelectorLocation" -> when (displayedValue.lowercase()) {
            "ツールバーと二段で表示", "above" -> "above"
            "ツールバーの上に重ねる", "over" -> "over"
            else -> displayedValue
        }
        "networkImageParallel", "delayFewReplies", "commonPrivacyAlpha",
        "catalogThreadSize", "catalogTitleLength" ->
            displayedValue.filter(Char::isDigit).takeIf(String::isNotEmpty) ?: displayedValue
        "controlCatalogVolumeKey" -> when (displayedValue) {
            "何もしない" -> "none"
            "スクロール" -> "screen"
            else -> displayedValue
        }
        "controlCatalogLongTap" -> when (displayedValue) {
            "何もしない" -> "none"
            "選択メニュー" -> "menu"
            "NGスレッドに登録" -> "ng"
            "delを送信する" -> "del"
            "タブに追加する" -> "add"
            else -> displayedValue
        }
        "controlThreadVolumeKey" -> when (displayedValue) {
            "何もしない" -> "none"
            "1レス分スクロール" -> "response"
            "1画面分スクロール" -> "screen"
            "スレッドの切り替え" -> "thread"
            else -> displayedValue
        }
        "controlTabSelectorLongTap" -> when (displayedValue) {
            "何もしない" -> "none"
            "選択メニュー" -> "menu"
            "更新の確認" -> "check"
            "再読み込み" -> "reload"
            "レスを書き込む" -> "post"
            "スレを閉じる" -> "close"
            else -> displayedValue
        }
        "backgroundThreadExistCheck", "backgroundThreadUpdateCheck" -> when (displayedValue) {
            "常に確認する" -> "usually"
            "Wi-Fi回線のみ" -> "wifi"
            "利用しない" -> "none"
            else -> displayedValue
        }
        "commonImageCache", "commonCatalogImageCache", "commonThreadCache" -> when (displayedValue) {
            "32MB" -> "32"
            "64MB" -> "64"
            "128MB" -> "128"
            "256MB" -> "256"
            "512MB" -> "512"
            "1GB" -> "1024"
            "2GB" -> "2048"
            "無制限" -> "131072"
            else -> displayedValue
        }
        "dummyImageCacheLocation", "dummyCatalogImageCacheLocation" -> when {
            displayedValue.startsWith("内部ストレージ") -> "internal"
            displayedValue.startsWith("外部SDカード") -> "sdcard"
            displayedValue.startsWith("端末ストレージ") -> "device"
            else -> displayedValue
        }
        "threadHeaderSoudaneDisplay" -> when (displayedValue) {
            "通常" -> "show"
            "通常(右寄せ)" -> "show|right"
            "シンプル" -> "simple"
            "シンプル(右寄せ)" -> "simple|right"
            "非表示" -> "hide"
            else -> displayedValue
        }
        "threadUpsThumbMethod" -> when (displayedValue) {
            "表示しない" -> "none"
            "表示する" -> "load"
            "表示する(先読み)" -> "preload"
            "Wi-Fi回線のみ先読み" -> "wifi"
            else -> displayedValue
        }
        "viewerPreloadMode" -> when (displayedValue.lowercase()) {
            "常に利用する", "usually" -> "usually"
            "wi-fi回線のみ", "wifi" -> "wifi"
            "利用しない", "none", "off" -> "none"
            else -> displayedValue
        }
        else -> displayedValue
    }

internal fun compatPreferenceDisplayValue(preferenceKey: String, storedValue: String): String =
    when (preferenceKey) {
        "designTheme" -> when (storedValue.lowercase()) {
            "default", "デフォルト" -> "デフォルト"
            "mono", "モノクロ" -> "モノクロ"
            "futaba", "ふたば" -> "ふたば"
            "blue", "ブルー" -> "ブルー"
            "pink", "ピンク" -> "ピンク"
            "black", "ブラック" -> "ブラック"
            else -> storedValue
        }
        "designLoading" -> when (storedValue.lowercase()) {
            "default", "デフォルト" -> "デフォルト"
            "icon", "アイコン" -> "アイコン"
            else -> storedValue
        }
        "designTabSelectorLocation" -> when (storedValue.lowercase()) {
            "above", "ツールバーと二段で表示" -> "ツールバーと二段で表示"
            "over", "ツールバーの上に重ねる" -> "ツールバーの上に重ねる"
            else -> storedValue
        }
        "networkImageParallel" -> when (storedValue.filter(Char::isDigit).toIntOrNull()) {
            1 -> "1本(1枚ずつ)"
            2, 3, 4, 5, 8 -> "${storedValue.filter(Char::isDigit)}本"
            6 -> "6本(既定)"
            else -> storedValue
        }
        "controlCatalogVolumeKey" -> when (storedValue.lowercase()) {
            "none" -> "何もしない"
            "screen" -> "スクロール"
            else -> storedValue
        }
        "controlCatalogLongTap" -> when (storedValue.lowercase()) {
            "none" -> "何もしない"
            "menu" -> "選択メニュー"
            "ng" -> "NGスレッドに登録"
            "del" -> "delを送信する"
            "add" -> "タブに追加する"
            else -> storedValue
        }
        "controlThreadVolumeKey" -> when (storedValue.lowercase()) {
            "none" -> "何もしない"
            "response" -> "1レス分スクロール"
            "screen" -> "1画面分スクロール"
            "thread" -> "スレッドの切り替え"
            else -> storedValue
        }
        "controlTabSelectorLongTap" -> when (storedValue.lowercase()) {
            "none" -> "何もしない"
            "menu" -> "選択メニュー"
            "check" -> "更新の確認"
            "reload" -> "再読み込み"
            "post" -> "レスを書き込む"
            "close" -> "スレを閉じる"
            else -> storedValue
        }
        "backgroundThreadExistCheck", "backgroundThreadUpdateCheck" -> when (storedValue.lowercase()) {
            "usually", "always", "常に確認する" -> "常に確認する"
            "wifi", "wi-fi回線のみ" -> "Wi-Fi回線のみ"
            "none", "利用しない" -> "利用しない"
            else -> "なし"
        }
        "commonImageCache", "commonCatalogImageCache", "commonThreadCache" -> when (storedValue) {
            "32" -> "32MB"
            "64" -> "64MB"
            "128" -> "128MB"
            "256" -> "256MB"
            "512" -> "512MB"
            "1024" -> "1GB"
            "2048" -> "2GB"
            "131072" -> "無制限"
            else -> storedValue
        }
        "dummyImageCacheLocation", "dummyCatalogImageCacheLocation" -> when (storedValue.lowercase()) {
            "internal" -> "内部ストレージ"
            "sdcard" -> "外部SDカード"
            "device" -> "端末ストレージ"
            else -> storedValue
        }
        "threadHeaderSoudaneDisplay" -> when (storedValue.lowercase()) {
            "show" -> "通常"
            "show|right" -> "通常(右寄せ)"
            "simple" -> "シンプル"
            "simple|right" -> "シンプル(右寄せ)"
            "hide", "none" -> "非表示"
            else -> storedValue
        }
        "threadUpsThumbMethod" -> when (storedValue.lowercase()) {
            "none" -> "表示しない"
            "load" -> "表示する"
            "preload" -> "表示する(先読み)"
            "wifi" -> "Wi-Fi回線のみ先読み"
            else -> storedValue
        }
        "viewerPreloadMode" -> when (storedValue.lowercase()) {
            "usually", "always", "常に利用する" -> "常に利用する"
            "wifi", "wi-fi回線のみ" -> "Wi-Fi回線のみ"
            "none", "off", "利用しない" -> "利用しない"
            else -> storedValue
        }
        "delayFewReplies" -> storedValue.filter(Char::isDigit).let {
            if (it == "0") "0（ソートしない）" else it.ifBlank { storedValue }
        }
        "commonPrivacyAlpha" -> storedValue.filter(Char::isDigit).takeIf(String::isNotEmpty)
            ?.let { "$it%" } ?: storedValue
        "catalogGridViewTitleLength", "catalogListViewTitleLength",
        "catalogGridViewTitleFontSize", "catalogListViewTitleFontSize",
        "catalogGridViewPortraitClmNum", "catalogGridViewLandscapeClmNum",
        "catalogListViewLineNum", "autoScrollPixel", "autoScrollSpeed",
        "threadFontSize", "threadThumbSize", "threadUpsThumbSize",
        "threadExtractSoudaneNum", "threadExtractQuoteNum", "threadImageNgPhashThreshold",
        "galleryGridViewPortraitClmNum", "galleryGridViewLandscapeClmNum" ->
            storedValue.filter(Char::isDigit).ifBlank { storedValue }
        "catalogThreadSize" -> storedValue.filter(Char::isDigit).takeIf(String::isNotEmpty)
            ?.let { "${it}スレ" } ?: storedValue
        "catalogTitleLength" -> storedValue.filter(Char::isDigit).takeIf(String::isNotEmpty)
            ?.let { "${it}文字" } ?: storedValue
        else -> storedValue
    }

internal fun compatPreferenceSummaryValue(preferenceKey: String, storedValue: String): String {
    val displayed = compatPreferenceDisplayValue(preferenceKey, storedValue)
    val numeric = storedValue.filter(Char::isDigit).ifBlank {
        displayed.filter(Char::isDigit)
    }
    return when (preferenceKey) {
        "designTheme" -> when (displayed) {
            "モノクロ" -> "モノクローム"
            "ブルー" -> "アオいいよね"
            "ピンク" -> "ピンクは○○"
            else -> displayed
        }
        "designTabSelectorLocation" -> when (displayed) {
            "ツールバーと二段で表示" -> "ツールバーの上"
            "ツールバーの上に重ねる" -> "ツールバーに重ねる"
            else -> displayed
        }
        "commonImageCache", "commonCatalogImageCache", "commonThreadCache" -> when {
            storedValue == "無制限" || storedValue == "131072" -> "131072MB"
            storedValue == "1GB" -> "1024MB"
            storedValue == "2GB" -> "2048MB"
            numeric.isNotEmpty() -> "${numeric}MB"
            else -> displayed
        }
        "networkImageParallel" -> numeric.takeIf(String::isNotEmpty)?.let { "${it}本" } ?: displayed
        "delayFewReplies" -> numeric.takeIf(String::isNotEmpty)?.let { "${it}レス以上" } ?: displayed
        "commonPrivacyAlpha" -> numeric.takeIf(String::isNotEmpty)?.let { "$it%" } ?: displayed
        "catalogGridViewTitleLength", "catalogListViewTitleLength", "catalogTitleLength" ->
            numeric.takeIf(String::isNotEmpty)?.let { "${it}文字" } ?: displayed
        "catalogGridViewTitleFontSize", "catalogListViewTitleFontSize", "threadFontSize" ->
            numeric.takeIf(String::isNotEmpty)?.let { "${it}sp" } ?: displayed
        "catalogGridViewPortraitClmNum", "catalogGridViewLandscapeClmNum",
        "galleryGridViewPortraitClmNum", "galleryGridViewLandscapeClmNum" ->
            numeric.takeIf(String::isNotEmpty)?.let { "${it}列" } ?: displayed
        "catalogListViewLineNum" -> numeric.takeIf(String::isNotEmpty)?.let { "${it}行" } ?: displayed
        "catalogThreadSize" -> numeric.takeIf(String::isNotEmpty)?.let { "${it}スレ" } ?: displayed
        "autoScrollPixel" -> numeric.takeIf(String::isNotEmpty)?.let { "${it}px" } ?: displayed
        "autoScrollSpeed" -> numeric.takeIf(String::isNotEmpty)?.let { "${it}ミリ秒" } ?: displayed
        "threadThumbSize", "threadUpsThumbSize" ->
            numeric.takeIf(String::isNotEmpty)?.let { "${it}dp" } ?: displayed
        "threadExtractSoudaneNum", "threadExtractQuoteNum" ->
            numeric.takeIf(String::isNotEmpty)?.let { "${it}件" } ?: displayed
        else -> displayed
    }
}

internal fun compatBooleanPreferenceSummary(preferenceKey: String): String = when (preferenceKey) {
    "catalogReloadScrollTop" ->
        "通信に成功した更新の後だけ先頭へ戻り、キャッシュ表示時は位置を保ちます"
    "catalogAppendDropped" ->
        "今回のリロードで消えたスレをカタログの末尾に継ぎ足します"
    else -> ""
}

internal fun validateCompatPtmtCheck(check: String): String? = when {
    check.isBlank() -> "決意が不足しています"
    check != "後悔しません" -> "決意に誤字があります"
    else -> null
}

internal fun validateCompatPtmtValue(value: String, check: String): String? {
    if (value.any { it.code <= 31 || it.code >= 127 }) {
        return "半角英数字記号以外の文字が使われています"
    }
    return validateCompatPtmtCheck(check)
}

internal fun compatPtmtMutationNotice(existingValue: String?, requestedValue: String): String = when {
    existingValue != null && existingValue == requestedValue -> "変更はありません"
    requestedValue.isBlank() -> "削除しました"
    else -> "変更しました"
}

@Composable
private fun CompatPreferenceRow(
    modifier: Modifier = Modifier,
    title: String,
    summary: String,
    enabled: Boolean,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    onClick: () -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = if (summary.isBlank()) 54.dp else 73.dp).clickable(
            enabled = enabled,
            onClick = onClick
        ).padding(horizontal = 16.dp, vertical = if (checked == null) 11.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = if (enabled) palette.text else Color.Gray)
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    fontSize = 12.sp,
                    color = if (enabled) palette.text.copy(alpha = 0.72f) else Color.Gray
                )
            }
        }
        if (checked != null) {
            Checkbox(
                checked = checked,
                onCheckedChange = if (enabled) onCheckedChange else null,
                modifier = Modifier.size(32.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = SecondaryTeal,
                    uncheckedColor = palette.text.copy(alpha = 0.62f),
                    checkmarkColor = Color.White
                )
            )
        }
    }
}

internal fun String.compatSettingsTitle(): String = when (this) {
    "design" -> "デザイン"
    "control" -> "コントロール"
    "storage" -> "ストレージ"
    "background" -> "バックグラウンド"
    "network" -> "ネットワーク"
    "image_search" -> "画像検索"
    "catalog" -> "カタログ設定"
    "thread" -> "スレッド設定"
    "viewer" -> "画像ビューア設定"
    "ptmt" -> "ptmtクッキーの編集"
    else -> "設定"
}

internal fun String.compatSettingsEntries(): List<CompatSettingEntry> = when (this) {
    "design" -> listOf(
        CompatSettingEntry("カラーテーマ", "デフォルト", preferenceKey = "designTheme"),
        CompatSettingEntry("文字色", "自動", preferenceKey = "designTextColor"),
        CompatSettingEntry("ナビゲーションバー背景色", "OFF", preferenceKey = "designNavigationBar"),
        CompatSettingEntry("ローディング", "デフォルト", preferenceKey = "designLoading"),
        CompatSettingEntry("カスタムフォント", "未選択", preferenceKey = "dummyCustomFont"),
        CompatSettingEntry("表示位置", "ツールバーと二段で表示", preferenceKey = "designTabSelectorLocation"),
        CompatSettingEntry("最初から表示する", "OFF", preferenceKey = "designTabSelectorOpened")
    )
    "control" -> listOf(
        CompatSettingEntry("ボリュームキー", "何もしない", preferenceKey = "controlCatalogVolumeKey"),
        CompatSettingEntry("ロングタップ", "選択メニュー", preferenceKey = "controlCatalogLongTap"),
        CompatSettingEntry("ボリュームキー", "何もしない", preferenceKey = "controlThreadVolumeKey"),
        CompatSettingEntry("タッチスクロール", "OFF", preferenceKey = "controlTouchScroll"),
        CompatSettingEntry("レスをタッチしてドロワー", "OFF", preferenceKey = "controlTouchOpenDrawer"),
        CompatSettingEntry("スレッドを閉じたら前画面に戻る", "OFF", preferenceKey = "controlThreadCloseBack"),
        CompatSettingEntry("タブを閉じた時の通知", "7000", preferenceKey = "controlCloseToastDuration"),
        CompatSettingEntry("タブ一覧のロングタップ", "選択メニュー", preferenceKey = "controlTabSelectorLongTap"),
        CompatSettingEntry("送信時の確認", "ON", preferenceKey = "controlPostConfirm"),
        CompatSettingEntry("板名の誤投稿確認", "OFF", preferenceKey = "controlPostDestinationConfirm"),
        CompatSettingEntry("下にスワイプして閉じる", "ON", preferenceKey = "controlViewerSwipeClose")
    )
    "storage" -> listOf(
        CompatSettingEntry("保存ファイル", "未設定時：標準フォルダに保存", preferenceKey = "dummyDownloadDir"),
        CompatSettingEntry("手書きファイル", "未設定時: 一時保存。残す場合は保存先を設定", preferenceKey = "dummyDrawingDir"),
        CompatSettingEntry("画像キャッシュ上限", "512MB", preferenceKey = "commonImageCache"),
        CompatSettingEntry("画像キャッシュの保存先", "端末ストレージ", preferenceKey = "dummyImageCacheLocation"),
        CompatSettingEntry("画像キャッシュのクリア", "使用量を計算中", preferenceKey = "dummyImageCacheClear"),
        CompatSettingEntry("カタログ画像キャッシュ上限", "128MB", preferenceKey = "commonCatalogImageCache"),
        CompatSettingEntry(
            "カタログ画像キャッシュの保存先",
            "端末ストレージ",
            preferenceKey = "dummyCatalogImageCacheLocation"
        ),
        CompatSettingEntry("スレッドキャッシュ上限", "32MB", preferenceKey = "commonThreadCache"),
        CompatSettingEntry("スレッドキャッシュのクリア", "使用量を計算中", preferenceKey = "dummyThreadCacheClear"),
        CompatSettingEntry("その他のクリア", "添付・一時ファイル", preferenceKey = "dummyAttachFileClear")
    )
    "background" -> listOf(
        CompatSettingEntry("スレッドの生存確認", "利用しない", preferenceKey = "backgroundThreadExistCheck"),
        CompatSettingEntry("スレッドの更新確認", "利用しない", preferenceKey = "backgroundThreadUpdateCheck")
    )
    "network" -> listOf(
        CompatSettingEntry("通信の軽量化", "OFF", preferenceKey = COMPAT_CACHE_ENABLED_KEY),
        CompatSettingEntry("キャッシュサーバー接続先", "板ごとのinqueuet.com endpoint", preferenceKey = COMPAT_CACHE_BASE_URL_KEY),
        CompatSettingEntry("ステータス", " - ", preferenceKey = COMPAT_CACHE_STATUS_KEY),
        CompatSettingEntry("画像の同時取得数", "6本(既定)", preferenceKey = "networkImageParallel"),
        CompatSettingEntry(
            "画像取得数の説明",
            "減らすと1枚あたりの読み込みは速くなりますが、画面全体が出そろうまでは遅くなります。回線が細い場合は少なめが有利なことがあります。",
            preferenceKey = "dummyImageParallelNote",
            enabled = false
        )
    )
    "image_search" -> listOf(
        CompatSettingEntry("", COMPAT_IMAGE_SEARCH_DESCRIPTION, enabled = false, preferenceKey = "imageSearchDescription")
    ) + CompatImageSearchTarget.entries.map {
        CompatSettingEntry(it.label, "未選択", preferenceKey = "customSearchUriMulti.${it.id}")
    }
    "catalog" -> listOf(
        CompatSettingEntry("スクロール更新", "ON", preferenceKey = "catalogPullToRefresh"),
        CompatSettingEntry("高速スクロールバー", "OFF", preferenceKey = "catalogFastScroll"),
        CompatSettingEntry("レス数で優先ソート", "0レス以上", preferenceKey = "delayFewReplies"),
        // sample/1.apk seeds catalogThumbCrop=false. The user can opt into
        // ImageView.ScaleType.CENTER_CROP with this switch.
        CompatSettingEntry(
            "画像のトリミング表示",
            if (CompatCatalogVisualContract.defaultThumbnailCrop) "ON" else "OFF",
            preferenceKey = "catalogThumbCrop"
        ),
        CompatSettingEntry("低画質サムネイル", "OFF", preferenceKey = "catalogEco"),
        CompatSettingEntry("携帯回線時に低画質", "OFF", preferenceKey = "catalogMobileEco"),
        CompatSettingEntry("プライバシー透明度", "20%", preferenceKey = "commonPrivacyAlpha"),
        CompatSettingEntry("画像の上にレス数を重ねる", "ON", preferenceKey = "catalogGridViewResCountOnThumb"),
        CompatSettingEntry("タイトルの長さ", "4文字", preferenceKey = "catalogGridViewTitleLength"),
        CompatSettingEntry("フォントサイズ", "14sp", preferenceKey = "catalogGridViewTitleFontSize"),
        CompatSettingEntry("縦持ちの列数", "5列", preferenceKey = "catalogGridViewPortraitClmNum"),
        CompatSettingEntry("横持ちの列数", "7列", preferenceKey = "catalogGridViewLandscapeClmNum"),
        CompatSettingEntry("タイトルの長さ", "4文字", preferenceKey = "catalogListViewTitleLength"),
        CompatSettingEntry("フォントサイズ", "14sp", preferenceKey = "catalogListViewTitleFontSize"),
        CompatSettingEntry("長辺の列数", "7行", preferenceKey = "catalogListViewLineNum"),
        CompatSettingEntry("カタログを開いた時リロードを行う", "OFF", preferenceKey = "catalogOpenWithReload"),
        CompatSettingEntry(
            "リロード後に先頭へ戻る",
            "OFF",
            preferenceKey = "catalogReloadScrollTop"
        ),
        CompatSettingEntry("スレッド数", "300スレ", preferenceKey = "catalogThreadSize"),
        CompatSettingEntry("スレ落ち・隔離判定を行う", "OFF", preferenceKey = "catalogFindThreadDeleted"),
        CompatSettingEntry(
            "消えたスレを末尾に表示",
            "OFF",
            preferenceKey = "catalogAppendDropped"
        ),
        CompatSettingEntry("スレッド文", "20文字", preferenceKey = "catalogTitleLength")
    )
    "thread" -> listOf(
        CompatSettingEntry("スクロール更新", "ON", preferenceKey = "threadPullToRefresh"),
        CompatSettingEntry("高速スクロールバー", "OFF", preferenceKey = "threadFastScroll"),
        CompatSettingEntry("オートスクロール量", "5px", preferenceKey = "autoScrollPixel"),
        CompatSettingEntry("オートスクロール速度", "50ミリ秒", preferenceKey = "autoScrollSpeed"),
        CompatSettingEntry("NG機能", "ON", preferenceKey = "threadNg"),
        CompatSettingEntry("デフォルトの名前と題名を非表示", "OFF", preferenceKey = "threadHideDefaultNameAndSubject"),
        CompatSettingEntry("返信レス数の簡易表示", "OFF", preferenceKey = "threadHeaderQuoteSimple"),
        CompatSettingEntry("そうだねの表示方法", "通常", preferenceKey = "threadHeaderSoudaneDisplay"),
        CompatSettingEntry("削除されたレスを表示", "OFF", preferenceKey = "threadAdminDeleteShow"),
        CompatSettingEntry("プライバシー透明度", "20%", preferenceKey = "commonPrivacyAlpha"),
        CompatSettingEntry("フォントサイズ", "14sp", preferenceKey = "threadFontSize"),
        CompatSettingEntry("サムネイルサイズ", "250dp", preferenceKey = "threadThumbSize"),
        CompatSettingEntry("あぷ小のサムネイルサイズ", "250dp", preferenceKey = "threadUpsThumbSize"),
        // 1.apk leaves this ListPreference unset on a fresh install.  Its
        // title is visible but no summary/radio choice appears until the user
        // selects a loading policy.
        CompatSettingEntry("あぷ小のサムネイルの読み込み", "", preferenceKey = "threadUpsThumbMethod"),
        CompatSettingEntry("そうだねが多いレス", "3件", preferenceKey = "threadExtractSoudaneNum"),
        CompatSettingEntry("返信が多いレス", "3件", preferenceKey = "threadExtractQuoteNum")
    )
    "viewer" -> listOf(
        CompatSettingEntry("縦持ちの列数", "5列", preferenceKey = "galleryGridViewPortraitClmNum"),
        CompatSettingEntry("横持ちの列数", "7列", preferenceKey = "galleryGridViewLandscapeClmNum"),
        CompatSettingEntry("前後の画像を先読みする", "常に利用する", preferenceKey = "viewerPreloadMode")
    )
    "ptmt" -> listOf(CompatSettingEntry("ptmt値", "値は表示・ログ送信しません"), CompatSettingEntry("誤操作防止", "「後悔しません」の入力が必要"))
    else -> emptyList()
}

private fun List<CompatSettingEntry>.compatKeys(vararg keys: String): List<CompatSettingEntry> =
    keys.mapNotNull { key -> firstOrNull { it.preferenceKey == key } }

/**
 * The original app uses PreferenceCategory headings inside each secondary
 * screen.  Keep those headings and their order even though the implementation
 * is Compose-based.  Extra Futacha-only switches are deliberately placed in a
 * separate section so the sample-compatible surface is not silently changed.
 */
internal fun compatSettingsGroups(path: String): List<Pair<String, List<CompatSettingEntry>>> {
    val entries = path.compatSettingsEntries()
    return when (path) {
        "design" -> listOf(
            "スタイル" to entries.compatKeys(
                "designTheme", "designNavigationBar", "designLoading", "dummyCustomFont"
            ),
            "タブ一覧" to entries.compatKeys("designTabSelectorLocation", "designTabSelectorOpened"),
            "ふたちゃ拡張" to entries.compatKeys("designTextColor")
        ).filter { it.second.isNotEmpty() }
        "control" -> listOf(
            "カタログ画面" to entries.compatKeys("controlCatalogVolumeKey", "controlCatalogLongTap"),
            "スレッド画面" to entries.compatKeys(
                "controlThreadVolumeKey", "controlTouchScroll", "controlTouchOpenDrawer",
                "controlThreadCloseBack"
            ),
            "ツールバー" to entries.compatKeys("controlTabSelectorLongTap"),
            "書き込み画面" to entries.compatKeys("controlPostConfirm"),
            "画面ビューア" to entries.compatKeys("controlViewerSwipeClose"),
            "ふたちゃ拡張" to entries.compatKeys(
                "controlCloseToastDuration", "controlPostDestinationConfirm"
            )
        ).filter { it.second.isNotEmpty() }
        "storage" -> listOf(
            "保存先" to entries.compatKeys("dummyDownloadDir", "dummyDrawingDir"),
            "キャッシュ" to entries.compatKeys(
                "commonImageCache", "dummyImageCacheLocation", "dummyImageCacheClear",
                "commonCatalogImageCache", "dummyCatalogImageCacheLocation",
                "commonThreadCache", "dummyThreadCacheClear", "dummyAttachFileClear"
            )
        )
        "background" -> listOf("スレッド関連" to entries)
        "network" -> listOf(
            "キャッシュサーバー機能" to entries.compatKeys(COMPAT_CACHE_ENABLED_KEY, COMPAT_CACHE_STATUS_KEY),
            "画像の取得" to entries.compatKeys("networkImageParallel", "dummyImageParallelNote"),
            "ふたちゃ拡張" to entries.compatKeys(COMPAT_CACHE_BASE_URL_KEY)
        ).filter { it.second.isNotEmpty() }
        "image_search" -> listOf("長押しメニューに出す検索先" to entries)
        "catalog" -> listOf(
            "全般" to entries.compatKeys("catalogPullToRefresh", "catalogFastScroll", "delayFewReplies"),
            "画面表示" to entries.compatKeys(
                "catalogThumbCrop", "catalogEco", "catalogMobileEco", "commonPrivacyAlpha"
            ),
            "グリッドビュー" to entries.compatKeys(
                "catalogGridViewResCountOnThumb", "catalogGridViewTitleLength",
                "catalogGridViewTitleFontSize", "catalogGridViewPortraitClmNum",
                "catalogGridViewLandscapeClmNum"
            ),
            "リストビュー" to entries.compatKeys(
                "catalogListViewTitleLength", "catalogListViewTitleFontSize", "catalogListViewLineNum"
            ),
            "読み込み" to entries.compatKeys(
                "catalogOpenWithReload", "catalogReloadScrollTop", "catalogThreadSize",
                "catalogFindThreadDeleted", "catalogAppendDropped", "catalogTitleLength"
            )
        )
        "thread" -> listOf(
            "全般" to entries.compatKeys(
                "threadPullToRefresh", "threadFastScroll", "autoScrollPixel", "autoScrollSpeed", "threadNg"
            ),
            "画面表示" to entries.compatKeys(
                "threadHideDefaultNameAndSubject", "threadHeaderQuoteSimple", "threadHeaderSoudaneDisplay",
                "threadAdminDeleteShow", "commonPrivacyAlpha", "threadFontSize", "threadThumbSize",
                "threadUpsThumbSize", "threadUpsThumbMethod"
            ),
            "抽出する閾値" to entries.compatKeys("threadExtractSoudaneNum", "threadExtractQuoteNum")
        ).filter { it.second.isNotEmpty() }
        "viewer" -> listOf(
            "一覧" to entries.compatKeys("galleryGridViewPortraitClmNum", "galleryGridViewLandscapeClmNum"),
            "閲覧" to entries.compatKeys("viewerPreloadMode")
        )
        "ptmt" -> listOf("ptmtクッキー" to entries)
        else -> listOf(path.compatSettingsTitle() to entries)
    }
}

internal fun compatIsBooleanPreference(path: String, entry: CompatSettingEntry): Boolean =
    entry.preferenceKey in setOf(
        "designNavigationBar", "designTabSelectorOpened",
        "controlTouchScroll", "controlTouchOpenDrawer", "controlThreadCloseBack",
        "controlPostConfirm", "controlViewerSwipeClose", "controlPostDestinationConfirm",
        "catalogPullToRefresh", "catalogFastScroll", "catalogThumbCrop", "catalogEco",
        "catalogMobileEco", "catalogGridViewResCountOnThumb", "catalogOpenWithReload",
        "catalogReloadScrollTop", "catalogFindThreadDeleted", "catalogAppendDropped",
        "threadPullToRefresh", "threadFastScroll", "threadNg",
        "threadHideDefaultNameAndSubject", "threadHeaderQuoteSimple", "threadAdminDeleteShow",
        "viewerWebMSwitchMp4"
    ) || (path == "network" && entry.preferenceKey == COMPAT_CACHE_ENABLED_KEY) ||
        (path == "root" && entry.preferenceKey == "archiveReportEnabled")

/**
 * Projects the already-loaded compatibility preference snapshot into one
 * settings page.  The compatibility root waits for this snapshot before it
 * exposes any destination, so using it synchronously avoids a late series of
 * per-row reads overwriting a switch the user has just changed on slower iOS
 * storage.  Legacy title keys remain readable for existing installations.
 */
internal fun compatSettingsSavedValues(
    path: String,
    groups: List<Pair<String, List<CompatSettingEntry>>>,
    preferences: Map<String, String>
): Map<String, String> = buildMap {
    groups.flatMap { it.second }.forEach { entry ->
        val value = preferences[compatPreferenceStorageKey(path, entry.preferenceKey)]
            ?: preferences["compat.$path.${entry.title}"]
            ?: if (entry.preferenceKey == "commonPrivacyAlpha") {
                preferences["compat.catalog.プライバシー透明度"]
                    ?: preferences["compat.thread.プライバシー透明度"]
            } else {
                null
            }
        value?.let { put(entry.preferenceKey, it) }
    }
}

/**
 * The small-upload confirmation used by both compatibility posting screens.
 *
 * Keep this as a directly testable composable: both reference APKs use the
 * same title, field order and button labels, and deliberately ignore an
 * outside tap/back dismissal. Upload progress is shown by the separate
 * non-cancelable waiting dialog after [onSubmit] is invoked.
 */
@Composable
fun CompatUpsUploadDialog(
    fileName: String,
    comment: String,
    deleteKey: String,
    onCommentChange: (String) -> Unit,
    onDeleteKeyChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit
) {
    val commentFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(fileName) {
        delay(150)
        commentFocusRequester.requestFocus()
        keyboard?.show()
    }
    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("compat-ups-upload-dialog"),
        title = { Text("あぷ小アップロード") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("アップロードファイル")
                Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextField(
                    value = comment,
                    onValueChange = { onCommentChange(it.take(COMPAT_POST_COMMENT_MAX_CHARS)) },
                    label = { Text("コメント") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(commentFocusRequester)
                )
                TextField(
                    value = deleteKey,
                    onValueChange = { onDeleteKeyChange(it.take(COMPAT_POST_DELETE_KEY_MAX_LENGTH)) },
                    label = { Text("削除キー") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSubmit) { Text("送信する") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("キャンセル") }
        }
    )
}

/** Attachment block shared by thread creation and reply forms. */
@Composable
fun CompatPostAttachmentPreview(
    attachment: ImageData,
    onImagePreview: () -> Unit,
    onVideoPreview: () -> Unit
) {
    val kind = compatPostAttachmentKind(attachment.fileName)
    Column(
        modifier = Modifier.fillMaxWidth().testTag("compat-post-attachment-preview"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (kind) {
            CompatPostAttachmentKind.IMAGE -> {
                // The reference fixes the width at 150dp. Landscape previews
                // preserve their ratio; portrait previews remain a 150dp square.
                val ratio = compatPostImageAspectRatio(attachment.bytes)?.coerceIn(1f, 20f) ?: 1f
                AsyncImage(
                    model = attachment.bytes,
                    contentDescription = "添付画像をプレビュー",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(150.dp)
                        .aspectRatio(ratio)
                        .background(Color.Black)
                        .clickable(onClick = onImagePreview)
                        .testTag("compat-post-attachment-thumbnail")
                )
            }
            CompatPostAttachmentKind.VIDEO -> {
                Image(
                    painter = painterResource(Res.drawable.post_video_thumb),
                    contentDescription = "添付動画を開く",
                    modifier = Modifier
                        .size(100.dp)
                        .clickable(onClick = onVideoPreview)
                        .testTag("compat-post-attachment-thumbnail")
                )
            }
            CompatPostAttachmentKind.UNSUPPORTED -> Unit
        }
        Text(
            attachment.fileName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(10.dp)
                .clickable(onClick = if (kind == CompatPostAttachmentKind.VIDEO) onVideoPreview else onImagePreview)
                .testTag("compat-post-attachment-file-name")
        )
    }
}

@Composable
internal fun CompatPostScreen(
    tab: CompatTab,
    board: CompatBoard,
    repository: BoardRepository?,
    httpClient: HttpClient? = null,
    store: CompatibilityStore,
    toolbarRefreshToken: Long = 0L,
    onPostSent: () -> Unit = {},
    preferences: Map<String, String>,
    appVersion: String,
    fileSystem: FileSystem?,
    onToolbarEdit: () -> Unit,
    isBuild: Boolean = false,
    onBuildCreated: (String?) -> Unit = {},
    onOpenDrawing: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onBack: () -> Unit
) {
    val ownerKey = if (isBuild) "build:${board.key}" else tab.key
    val scope = rememberCoroutineScope()
    val palette = LocalCompatibilityPalette.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var name by remember(ownerKey) { mutableStateOf("") }
    var email by remember(ownerKey) { mutableStateOf("") }
    var subject by remember(ownerKey) { mutableStateOf("") }
    // Keep the complete TextFieldValue here.  Rebuilding it from only text and
    // selection drops the IME composition range, which prevents Japanese
    // keyboards from converting an unfinished kana/romaji sequence.
    var commentValue by remember(ownerKey) {
        mutableStateOf(TextFieldValue(text = "", selection = TextRange.Zero))
    }
    val comment = commentValue.text

    fun replaceComment(text: String, selection: TextRange = TextRange(text.length)) {
        // Programmatic edits must clear any stale composition belonging to the
        // previous value. User edits below retain it by assigning the complete
        // TextFieldValue supplied by Compose.
        val limitedText = text.take(COMPAT_POST_COMMENT_MAX_CHARS)
        commentValue = TextFieldValue(
            text = limitedText,
            selection = TextRange(
                selection.start.coerceIn(0, limitedText.length),
                selection.end.coerceIn(0, limitedText.length)
            )
        )
    }
    var deleteKey by remember(ownerKey) { mutableStateOf("") }
    var attachment by remember(ownerKey) { mutableStateOf<ImageData?>(null) }
    var attachmentLocator by remember(ownerKey) { mutableStateOf<String?>(null) }
    var initialDraft by remember(ownerKey) {
        mutableStateOf(CompatReplyDraft(tabKey = ownerKey, updatedAtEpochMillis = 0L))
    }
    var initialAttachment by remember(ownerKey) { mutableStateOf<ImageData?>(null) }
    var initialAttachmentLocator by remember(ownerKey) { mutableStateOf<String?>(null) }
    var draftLoaded by remember(ownerKey) { mutableStateOf(false) }
    var sending by remember(ownerKey) { mutableStateOf(false) }
    var message by remember(ownerKey) { mutableStateOf<String?>(null) }
    var pendingCompression by remember(ownerKey) { mutableStateOf<ImageData?>(null) }
    var attachmentPreviewOpen by remember(ownerKey) { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var toolbarOverflowOpen by remember { mutableStateOf(false) }
    var upsUploadRequested by remember(ownerKey) { mutableStateOf(false) }
    var upsAttachment by remember(ownerKey) { mutableStateOf<ImageData?>(null) }
    var upsDialogOpen by remember(ownerKey) { mutableStateOf(false) }
    var upsComment by remember(ownerKey) { mutableStateOf("") }
    var upsDeleteKey by remember(ownerKey) { mutableStateOf("") }
    var upsUploadInProgress by remember(ownerKey) { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    var sendConfirm by remember { mutableStateOf(false) }
    var postDestinationWarning by remember { mutableStateOf<String?>(null) }
    var toolbarItems by remember { mutableStateOf(reconcileCompatToolbar(CompatToolbarSurface.POST, emptyList())) }
    var postingCapabilities by remember(board.canonicalUrl) {
        mutableStateOf(defaultBoardPostingCapabilities(board.canonicalUrl))
    }
    val attachmentLimitBytes = postingCapabilities.maxFileSizeBytes.toInt()
    val storedDeleteKey = preferences.compatStoredPostDeleteKey()

    fun hasDraft(): Boolean = name.isNotEmpty() || email.isNotEmpty() || subject.isNotEmpty() ||
        comment.isNotEmpty() || deleteKey.isNotEmpty() || attachment != null

    suspend fun persistCurrentDraftOrDelete() {
        if (hasDraft()) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (isBuild) {
                store.saveBuildDraft(
                    CompatBuildDraft(
                        boardKey = board.key,
                        name = name,
                        email = email,
                        subject = subject,
                        comment = comment,
                        attachmentUri = attachmentLocator,
                        deleteKey = deleteKey,
                        updatedAtEpochMillis = now
                    )
                )
            } else {
                store.saveDraft(
                    CompatReplyDraft(
                        tabKey = tab.key,
                        name = name,
                        email = email,
                        subject = subject,
                        comment = comment,
                        attachmentUri = attachmentLocator,
                        deleteKey = deleteKey,
                        updatedAtEpochMillis = now
                    )
                )
            }
        } else {
            if (isBuild) store.deleteBuildDraft(board.key) else store.deleteDraft(tab.key)
        }
    }

    LaunchedEffect(ownerKey) {
        val draft = if (isBuild) {
            store.loadBuildDraft(board.key)?.let { build ->
                CompatReplyDraft(
                    tabKey = ownerKey,
                    name = build.name,
                    email = build.email,
                    subject = build.subject,
                    comment = build.comment,
                    attachmentUri = build.attachmentUri,
                    deleteKey = build.deleteKey,
                    updatedAtEpochMillis = build.updatedAtEpochMillis
                )
            }
        } else {
            store.loadDraft(tab.key)
        } ?: CompatReplyDraft(tabKey = ownerKey, updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds())
        val restoredAttachment = draft.attachmentUri?.let { locator ->
            fileSystem?.let { loadCompatPostAttachment(it, locator).getOrNull() }
        }
        val effectiveDraft = if (draft.attachmentUri != null && restoredAttachment == null) {
            draft.copy(attachmentUri = null)
        } else {
            draft
        }
        val draftWithStoredDeleteKey = effectiveDraft.copy(
            deleteKey = effectiveDraft.deleteKey.ifBlank { storedDeleteKey }
        )
        initialDraft = draftWithStoredDeleteKey
        name = draft.name.take(COMPAT_POST_NAME_MAX_CHARS)
        email = draft.email.take(COMPAT_POST_EMAIL_MAX_CHARS)
        subject = draft.subject.take(COMPAT_POST_SUBJECT_MAX_CHARS)
        replaceComment(draft.comment, TextRange(draft.comment.length))
        // Quick replies are inserted before opening this form. Put the caret
        // after the generated quote, matching the legacy app's reply flow.
        deleteKey = draftWithStoredDeleteKey.deleteKey
        attachment = restoredAttachment
        attachmentLocator = effectiveDraft.attachmentUri
        initialAttachment = restoredAttachment
        initialAttachmentLocator = effectiveDraft.attachmentUri
        if (draft.attachmentUri != null && restoredAttachment == null) {
            message = "添付ファイルが\nリセットされました"
        }
        draftLoaded = true
        // Wait until the form is attached before requesting focus. This matches
        // the reference reply screen, which opens directly on the comment field
        // with the IME already visible.
        delay(150)
        focusRequester.requestFocus()
        // On Android the first IME request can race the navigation transition.
        // Request it once more after the focused EditText has reached a frame;
        // the reference app opens the keyboard immediately on entering this form.
        delay(200)
        keyboard?.show()
    }
    // Preferences can arrive one frame after the form's draft. Fill the field once in that
    // case, while preserving a draft or an edit the user has already made.
    LaunchedEffect(ownerKey, storedDeleteKey, draftLoaded) {
        if (!draftLoaded || storedDeleteKey.isBlank() || deleteKey.isNotBlank()) return@LaunchedEffect
        deleteKey = storedDeleteKey
        initialDraft = initialDraft.copy(deleteKey = storedDeleteKey)
    }
    LaunchedEffect(toolbarRefreshToken) { toolbarItems = store.loadToolbar(CompatToolbarSurface.POST) }
    LaunchedEffect(repository, board.originalUrl) {
        postingCapabilities = runSuspendCatchingPreservingCancellation {
            repository?.getPostingCapabilities(board.originalUrl)
        }.getOrNull() ?: defaultBoardPostingCapabilities(board.canonicalUrl)
    }
    LaunchedEffect(name, email, subject, comment, deleteKey, attachment, attachmentLocator, draftLoaded) {
        if (!draftLoaded) return@LaunchedEffect
        delay(300)
        persistCurrentDraftOrDelete()
    }
    LaunchedEffect(message) {
        val shownMessage = message ?: return@LaunchedEffect
        delay(2_500)
        if (message == shownMessage) message = null
    }

    fun leave() {
        keyboard?.hide()
        onBack()
    }

    fun clearAttachment(deleteContainer: Boolean = false) {
        val locator = attachmentLocator
        attachmentLocator = null
        attachment = null
        if (fileSystem != null && locator != null) {
            scope.launch {
                deleteCompatPostAttachment(fileSystem, locator, deleteContainer).onFailure { error ->
                    message = "添付ファイルを削除できませんでした: ${error.message.orEmpty()}"
                }
            }
        }
    }

    fun persistAcceptedAttachment(selected: ImageData) {
        val localFileSystem = fileSystem
        if (localFileSystem == null) {
            attachmentLocator = null
            attachment = selected
            return
        }
        val previousLocator = attachmentLocator
        scope.launch {
            persistCompatPostAttachment(localFileSystem, ownerKey, selected)
                .onSuccess { persistedLocator ->
                    attachmentLocator = persistedLocator
                    attachment = selected
                    if (previousLocator != null && previousLocator != persistedLocator) {
                        deleteCompatPostAttachment(localFileSystem, previousLocator)
                    }
                }
                .onFailure { error ->
                    message = "添付ファイルを一時保存できませんでした: ${error.message.orEmpty()}"
                }
        }
    }

    fun acceptAttachment(selected: ImageData) {
        if (upsUploadRequested) {
            upsUploadRequested = false
            if (!isCompatUpsUploadSizeAllowed(selected.bytes.size)) {
                message = "ファイルサイズ超過です\n3000KBまで"
            } else {
                upsAttachment = selected
                // The reference dialog starts with an empty upload comment and
                // the globally remembered deletion key, not the post body or a
                // not-yet-saved edit in the posting form.
                val initialFields = compatUpsUploadInitialFields(storedDeleteKey)
                upsComment = initialFields.comment
                upsDeleteKey = initialFields.deleteKey
                upsDialogOpen = true
            }
            return
        }
        val decision = decideCompatPostAttachment(
                attachment = selected,
                maxBytes = attachmentLimitBytes,
                supportedExtensions = postingCapabilities.supportedExtensions
            )
        when (decision) {
            CompatPostAttachmentDecision.Accept -> persistAcceptedAttachment(selected)
            CompatPostAttachmentDecision.AskImageCompression -> {
                message = compatPostAttachmentDecisionMessage(decision, selected.fileName, attachmentLimitBytes)
                pendingCompression = selected
            }
            else -> message = compatPostAttachmentDecisionMessage(decision, selected.fileName, attachmentLimitBytes)
        }
    }

    val launchAttachmentPicker = rememberAttachmentPickerLauncher(
        // 1.apk uses ACTION_GET_CONTENT + CATEGORY_OPENABLE in both posting
        // modes and reuses the component selected by a toolbar long press.
        preference = AttachmentPickerPreference.COMPAT_REFERENCE_GET_CONTENT,
        mimeType = "*/*",
        maxBytes = COMPAT_POST_PICKER_MAX_BYTES,
        onImageSelected = ::acceptAttachment,
        onSelectionError = { message = it }
    )
    val launchAttachmentChooser = rememberAttachmentPickerLauncher(
        preference = AttachmentPickerPreference.ALWAYS_ASK,
        mimeType = "*/*",
        maxBytes = COMPAT_POST_PICKER_MAX_BYTES,
        onImageSelected = ::acceptAttachment,
        onSelectionError = { message = it }
    )
    fun attachmentCommand(explicitChooser: Boolean = false) {
        upsUploadRequested = false
        if (attachment != null) {
            clearAttachment()
        } else if (explicitChooser) {
            launchAttachmentChooser()
        } else {
            launchAttachmentPicker()
        }
    }

    fun upsUploadCommand(explicitChooser: Boolean = false) {
        if (httpClient == null) {
            message = "あぷ小連携を初期化できませんでした"
        } else if (upsUploadInProgress) {
            message = "アップロード中です"
        } else {
            upsUploadRequested = true
            if (explicitChooser) launchAttachmentChooser() else launchAttachmentPicker()
        }
    }

    fun postValidationError(): String? = when {
        repository == null -> "通信機能を初期化できませんでした"
        comment.isBlank() && attachment == null -> "コメントが空白です"
        deleteKey.isBlank() -> "削除キーを入力して下さい"
        else -> null
    }

    fun sendPost() {
        val validationError = postValidationError()
        if (validationError != null) {
            message = validationError
        } else if (!sending) {
            scope.launch {
                sending = true
                val currentLocator = attachmentLocator
                if (currentLocator != null && fileSystem != null && !fileSystem.exists(currentLocator)) {
                    message = "添付ファイルが見つかりません"
                    attachment = null
                    attachmentLocator = null
                    sending = false
                    return@launch
                }
                runSuspendCatchingPreservingCancellation {
                    if (isBuild) {
                        checkNotNull(repository).createThread(
                            board = board.originalUrl,
                            name = name,
                            email = email,
                            subject = subject,
                            comment = comment,
                            password = deleteKey,
                            imageFile = attachment?.bytes,
                            imageFileName = attachment?.fileName,
                            textOnly = attachment == null
                        )
                    } else {
                        checkNotNull(repository).replyToThread(
                            board = board.originalUrl,
                            threadId = tab.threadNo,
                            name = name,
                            email = email,
                            subject = subject,
                            comment = comment,
                            password = deleteKey,
                            imageFile = attachment?.bytes,
                            imageFileName = attachment?.fileName,
                            textOnly = attachment == null
                        )
                    }
                }.onSuccess { responseId ->
                    store.savePreference(
                        COMPAT_POST_DELETE_KEY_STORAGE_KEY,
                        compatPostDeleteKeyForStorage(deleteKey)
                    )
                    if (!isBuild) {
                        responseId?.let { raw ->
                            compatSecondaryPostNumberRegex.find(raw)?.value?.let { postNo ->
                                store.savePreference("compat.ownpost.${tab.key}.$postNo", "1")
                            }
                        }
                    }
                    attachmentLocator?.let { locator ->
                        fileSystem?.let { deleteCompatPostAttachment(it, locator, deleteContainer = true) }
                    }
                    if (isBuild) {
                        store.deleteBuildDraft(board.key)
                        onBuildCreated(responseId)
                    } else {
                        store.deleteDraft(tab.key)
                        onPostSent()
                        leave()
                    }
                }.onFailure { message = it.message ?: if (isBuild) "スレッドを立てられませんでした" else "投稿できませんでした" }
                sending = false
            }
        }
    }
    fun requestSend() {
        val destinationWarning = compatPostDestinationWarning(
                boardUrl = board.originalUrl,
                comment = comment,
                enabled = preferences.compatPreferenceValue(
                    "control", "controlPostDestinationConfirm", "板名の誤投稿確認"
                ) == "ON"
            )
        if (destinationWarning != null) {
            postDestinationWarning = destinationWarning
            return
        }
        if (
            preferences.compatPreferenceValue("control", "controlPostConfirm", "送信時の確認") == "OFF"
        ) {
            sendPost()
        } else {
            sendConfirm = true
        }
    }
    val postActions: Map<String, () -> Unit> = mapOf(
        "send" to ::requestSend,
        "attach" to { attachmentCommand() },
        "pallete" to {
            scope.launch {
                persistCurrentDraftOrDelete()
                keyboard?.hide()
                onOpenDrawing()
            }
        },
        "sio" to { upsUploadCommand() },
        "voice_input" to {},
        "network_info" to {
            val commentBeforeLookup = comment
            scope.launch {
                val info = fetchCompatPostNetworkInfo(httpClient, "Futacha/$appVersion")
                replaceComment(appendCompatPostText(commentBeforeLookup, info))
            }
        },
        "model_info" to { replaceComment(appendCompatPostText(comment, compatPostDeviceInfo(appVersion))) },
        "reset" to {
            val resetFields = compatPostResetFields(isBuild, deleteKey, initialDraft)
            name = resetFields.name
            email = resetFields.email
            subject = resetFields.subject
            replaceComment(resetFields.comment)
            deleteKey = resetFields.deleteKey
            val previousLocator = attachmentLocator
            val resetAttachmentLocator = if (isBuild) null else initialAttachmentLocator
            attachmentLocator = resetAttachmentLocator
            attachment = if (isBuild) null else initialAttachment
            if (
                fileSystem != null &&
                previousLocator != null &&
                previousLocator != resetAttachmentLocator
            ) {
                scope.launch { deleteCompatPostAttachment(fileSystem, previousLocator) }
            }
            message = null
        },
        "discard" to { if (hasDraft()) discardConfirm = true else leave() }
    )
    val launchSpeechRecognizer = rememberCompatSpeechRecognizer(
        onResult = { recognized -> replaceComment(appendCompatPostText(comment, normalizeCompatSpeechResult(recognized))) },
        onError = { message = it }
    )
    val launchVideoPreview = rememberCompatVideoAttachmentPreviewLauncher { message = it }
    val resolvedPostActions = postActions + ("voice_input" to launchSpeechRecognizer)
    val formScrollState = rememberScrollState()
    val postTextFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = palette.chrome,
        unfocusedIndicatorColor = palette.divider,
        // Black uses black chrome but a white colorAccent in sample/1.apk.
        // Cursor visibility therefore follows the dedicated input token.
        cursorColor = palette.inputCursor,
        focusedTextColor = palette.text,
        unfocusedTextColor = palette.text,
        focusedLabelColor = palette.text.copy(alpha = 0.78f),
        unfocusedLabelColor = palette.text.copy(alpha = 0.78f),
        focusedPlaceholderColor = palette.text.copy(alpha = 0.62f),
        unfocusedPlaceholderColor = palette.text.copy(alpha = 0.62f)
    )

    CompatPostImePolicyEffect()
    // WaitingDialogFragment in both reference APKs is explicitly non-cancelable.
    // Consume the platform Back action while the network request is in flight.
    PlatformBackHandler(enabled = sending || upsUploadInProgress) {}

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = SecondaryBackground,
            topBar = {
                TopAppBar(
                    expandedHeight = 56.dp,
                    title = { Text(if (isBuild) "スレ立て" else tab.title.ifBlank { "No.${tab.threadNo}" }, maxLines = 1, modifier = Modifier.padding(start = 16.dp)) },
                    navigationIcon = {
                        IconButton(onClick = ::leave) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "その他")
                            }
                            DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                                shape = RoundedCornerShape(2.dp),
                                containerColor = compatibilityPopupSurface(LocalCompatibilityPalette.current),
                                tonalElevation = 0.dp,
                                shadowElevation = 8.dp
                            ) {
                                DropdownMenuItem(text = { Text("ツールバー編集") }, colors = compatibilityMenuItemColors(), onClick = { overflowOpen = false; onToolbarEdit() })
                                DropdownMenuItem(text = { Text("ヘルプ") }, colors = compatibilityMenuItemColors(), onClick = { overflowOpen = false; onOpenHelp() })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SecondaryTeal,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(padding)
                .verticalScroll(formScrollState)
                .padding(horizontal = 10.dp, vertical = 0.dp)
                .padding(bottom = 40.dp)
        ) {
            // Keep the compact APK spacing while retaining enough vertical
            // room for the label, baseline and IME text.  A fixed 45dp box
            // clips the glyphs on current Material3 fonts.
            Spacer(Modifier.height(15.dp))
            // PostResponseActivity puts the comment first, then the byte count,
            // name/mail, sage, subject and delete key. The shared form used to
            // place these in a modern create/reply order and showed a large empty
            // comment box, which was visibly different on the reference APK.
            TextField(
                value = commentValue,
                onValueChange = {
                    // Preserve IME composition while the user is converting
                    // Japanese text. This is intentionally not reconstructed
                    // from it.text/it.selection.
                    commentValue = if (it.text.length <= COMPAT_POST_COMMENT_MAX_CHARS) {
                        it
                    } else {
                        val limited = it.text.take(COMPAT_POST_COMMENT_MAX_CHARS)
                        TextFieldValue(limited, TextRange(limited.length))
                    }
                },
                label = { Text("コメント", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = false,
                minLines = 1,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .focusRequester(focusRequester)
                    .testTag("compat-post-comment-field"),
                colors = postTextFieldColors
            )
            Spacer(Modifier.height(5.dp))
            // The reference form displays an empty form as one line in both
            // reply and thread-creation modes.
            val lineCount = compatPostLineCount(comment, emptyIsOneLine = !isBuild)
            val byteCount = compatPostShiftJisByteCount(comment)
            Text(
                "${lineCount}行 ${byteCount}バイト",
                fontSize = 14.sp,
                color = if (lineCount > 15 || byteCount > 1000) Color.Red else Color.Unspecified,
                modifier = Modifier.height(18.dp).padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(25.dp))
            attachment?.let { selected ->
                CompatPostAttachmentPreview(
                    attachment = selected,
                    onImagePreview = { attachmentPreviewOpen = true },
                    onVideoPreview = { launchVideoPreview(selected) }
                )
            }
            TextField(
                name,
                { name = it.take(COMPAT_POST_NAME_MAX_CHARS) },
                label = { Text("おなまえ", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("compat-post-name-field"),
                colors = postTextFieldColors
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                email,
                { email = it.take(COMPAT_POST_EMAIL_MAX_CHARS) },
                label = { Text("メール", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = postTextFieldColors
            )
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                compatPostMailPresets(isBuild).forEach { preset ->
                    val selected = when (preset) {
                        "sage" -> compatSecondarySageTokenRegex.containsMatchIn(email)
                        else -> email == applyCompatMailPreset("", preset, isBuild)
                    }
                    Text(
                        preset,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        color = if (selected) palette.chrome else palette.text.copy(alpha = 0.82f),
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight()
                            .clickable { email = applyCompatMailPreset(email, preset, isBuild) }
                    )
                }
            }
            TextField(
                subject,
                { subject = it.take(COMPAT_POST_SUBJECT_MAX_CHARS) },
                label = { Text("題名", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = postTextFieldColors
            )
            Spacer(Modifier.height(10.dp))
            TextField(
                deleteKey,
                { deleteKey = it.take(COMPAT_POST_DELETE_KEY_MAX_LENGTH) },
                label = { Text("削除キー", modifier = Modifier.offset(x = (-12).dp)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = postTextFieldColors
            )
            Spacer(Modifier.imePadding())
        }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                .background(SecondaryTeal).navigationBarsPadding()
                .testTag("compat-post-bottom-bar")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().requiredHeight(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                toolbarItems.filter(CompatToolbarItem::active).sortedBy(CompatToolbarItem::position).forEach { item ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (item.key == "attach") {
                            Box(
                                modifier = Modifier.fillMaxSize().combinedClickable(
                                    enabled = !sending && !upsUploadInProgress,
                                    onClick = { attachmentCommand() },
                                    onLongClick = { attachmentCommand(explicitChooser = true) }
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                CompatToolbarArtworkIcon(
                                    artwork = secondaryToolbarIcon(
                                        CompatToolbarSurface.POST,
                                        if (attachment == null) item.key else "attach_clear"
                                    ),
                                    contentDescription = compatPostAttachmentToolbarLabel(attachment != null),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                        .testTag("compat-post-toolbar-icon-${item.key}")
                                )
                            }
                        } else if (item.key == "sio") {
                            Box(
                                modifier = Modifier.fillMaxSize().combinedClickable(
                                    enabled = !sending && !upsUploadInProgress,
                                    onClick = { upsUploadCommand() },
                                    onLongClick = { upsUploadCommand(explicitChooser = true) }
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                CompatToolbarArtworkIcon(
                                    artwork = secondaryToolbarIcon(CompatToolbarSurface.POST, item.key),
                                    contentDescription = compatToolbarLabel(CompatToolbarSurface.POST, item.key),
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                        .testTag("compat-post-toolbar-icon-${item.key}")
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { resolvedPostActions[item.key]?.invoke() },
                                enabled = !sending && !upsUploadInProgress
                            ) {
                                if (item.key == "send" && sending) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    CompatToolbarArtworkIcon(
                                        artwork = secondaryToolbarIcon(CompatToolbarSurface.POST, item.key),
                                        contentDescription = compatToolbarLabel(CompatToolbarSurface.POST, item.key),
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                            .testTag("compat-post-toolbar-icon-${item.key}")
                                    )
                                }
                            }
                        }
                    }
                }
                if (compatToolbarShowsOverflow(CompatToolbarSurface.POST, toolbarItems)) {
                    IconButton(onClick = { toolbarOverflowOpen = true }, modifier = Modifier.weight(1f)) {
                        CompatToolbarArtworkIcon(
                            artwork = secondaryToolbarIcon(CompatToolbarSurface.POST, "other"),
                            contentDescription = "その他",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp).testTag("compat-post-toolbar-icon-other")
                        )
                    }
                }
            }
        }
        message?.let { transientMessage ->
            Text(
                text = transientMessage,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp)
                    .background(Color(0xFF646464))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
        if (sending || upsUploadInProgress) {
            // WaitingDialogFragment is a real non-cancelable dialog in both
            // reference APKs. A plain overlay leaves Android's parent host
            // BackHandler able to win during a busy frame and close the post
            // screen while the request is still active.
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable(onClick = {})
                        .testTag("compat-post-waiting-dialog"),
                    contentAlignment = Alignment.Center
                ) {
                    CompatLoadingIndicator(
                        style = preferences.compatPreferenceValue(
                            "design", "designLoading", "ローディング"
                        ),
                        modifier = Modifier,
                        size = 50.dp
                    )
                }
            }
        }
    }

    pendingCompression?.let { oversizedImage ->
        CompatPostImageCompressConfirmation(
            onCompress = {
                pendingCompression = null
                scope.launch {
                    compressCompatPostImage(
                        oversizedImage,
                        attachmentLimitBytes
                    ).onSuccess(::persistAcceptedAttachment)
                        .onFailure { message = "画像を圧縮できませんでした: ${it.message.orEmpty()}" }
                }
            },
            onCancel = { pendingCompression = null }
        )
    }
    if (attachmentPreviewOpen) {
        val previewAttachment = attachment
        if (previewAttachment == null || compatPostAttachmentKind(previewAttachment.fileName) != CompatPostAttachmentKind.IMAGE) {
            attachmentPreviewOpen = false
        } else {
            Dialog(
                onDismissRequest = { attachmentPreviewOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = previewAttachment.bytes,
                        contentDescription = "添付画像プレビューを閉じる",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f)
                            .background(Color.Black)
                            .clickable { attachmentPreviewOpen = false }
                    )
                }
            }
        }
    }

    if (upsDialogOpen) {
        val selected = upsAttachment
        if (selected != null) {
            CompatUpsUploadDialog(
                fileName = selected.fileName,
                comment = upsComment,
                deleteKey = upsDeleteKey,
                onCommentChange = { upsComment = it },
                onDeleteKeyChange = { upsDeleteKey = it },
                onSubmit = {
                    val client = httpClient
                    upsDialogOpen = false
                    if (client == null) {
                        message = "あぷ小連携を初期化できませんでした"
                        upsAttachment = null
                    } else {
                        upsUploadInProgress = true
                        scope.launch {
                            uploadCompatUps(
                                client = client,
                                attachment = selected,
                                comment = upsComment,
                                deleteKey = upsDeleteKey,
                                appVersion = appVersion
                            ).onSuccess { fileName ->
                                replaceComment(appendCompatPostText(comment, fileName))
                                message = "${fileName}を追記しました"
                            }.onFailure { error ->
                                message = error.message ?: "あぷ小へのアップロードに失敗しました"
                            }
                            upsAttachment = null
                            upsUploadInProgress = false
                        }
                    }
                },
                onCancel = {
                    upsDialogOpen = false
                    upsAttachment = null
                }
            )
        }
    }

    if (discardConfirm) {
        AlertDialog(
            onDismissRequest = { discardConfirm = false },
            title = { Text("投稿内容の破棄") },
            text = { Text("本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = {
                    discardConfirm = false
                    val discardedAttachmentLocator = attachmentLocator
                    name = ""; email = ""; subject = ""; replaceComment(""); deleteKey = ""; attachment = null
                    attachmentLocator = null
                    scope.launch {
                        discardedAttachmentLocator?.let { locator ->
                            fileSystem?.let { deleteCompatPostAttachment(it, locator, deleteContainer = true) }
                        }
                        if (isBuild) store.deleteBuildDraft(board.key) else store.deleteDraft(tab.key)
                        leave()
                    }
                }) { Text("破棄する") }
            },
            dismissButton = { TextButton(onClick = { discardConfirm = false }) { Text("キャンセル") } }
        )
    }
    if (sendConfirm) {
        AlertDialog(
            onDismissRequest = { sendConfirm = false },
            title = { Text("投稿の確認") },
            text = { Text("本当によろしいですか？") },
            confirmButton = {
                TextButton(onClick = { sendConfirm = false; sendPost() }) { Text("送信する") }
            },
            dismissButton = { TextButton(onClick = { sendConfirm = false }) { Text("キャンセル") } }
        )
    }
    postDestinationWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { postDestinationWarning = null },
            title = { Text("投稿先の確認") },
            text = { Text(warning) },
            confirmButton = {
                TextButton(onClick = {
                    postDestinationWarning = null
                    if (preferences.compatPreferenceValue("control", "controlPostConfirm", "送信時の確認") == "OFF") {
                        sendPost()
                    } else {
                        sendConfirm = true
                    }
                }) { Text("確認して続行") }
            },
            dismissButton = {
                TextButton(onClick = { postDestinationWarning = null }) { Text("キャンセル") }
            }
        )
    }
    if (toolbarOverflowOpen) {
        SecondaryToolbarOverflowPopup(
            surface = CompatToolbarSurface.POST,
            items = toolbarItems,
            actions = resolvedPostActions,
            onDismiss = { toolbarOverflowOpen = false }
        )
    }
}

@Composable
fun CompatPostImageCompressConfirmation(
    onCompress: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("確認") },
        text = { Text("画像をリサイズしますか？") },
        confirmButton = {
            TextButton(onClick = onCompress) { Text("圧縮する") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("キャンセル") }
        }
    )
}

@Composable
internal fun CompatGalleryScreen(
    tab: CompatTab,
    initialIndex: Int = 0,
    initialPostNo: String? = null,
    store: CompatibilityStore,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository? = null,
    onOpenViewer: (Int, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCommonSettings: () -> Unit,
    onOpenHelp: () -> Unit = {},
    onBack: () -> Unit
) {
    val tabKey = tab.key
    val scope = rememberCoroutineScope()
    val palette = LocalCompatibilityPalette.current
    val openUrl = rememberUrlLauncher()
    val share = rememberCompatShareLauncher()
    val clipboard = LocalClipboardManager.current
    val imageLoader = LocalFutachaImageLoader.current
    val gridState = rememberLazyGridState()
    var posts by remember(tabKey) { mutableStateOf<List<CompatPostSnapshot>>(emptyList()) }
    var snapshotRevision by remember(tabKey) { mutableStateOf(tab.snapshotRevision) }
    var saveMode by remember { mutableStateOf(false) }
    var selectedMediaKeys by remember(tabKey) { mutableStateOf<Set<String>>(emptySet()) }
    var batchSaveFormatDialog by remember { mutableStateOf(false) }
    var batchSaveProgress by remember { mutableStateOf<SaveProgress?>(null) }
    var batchSaveCancelRequested by remember { mutableStateOf(false) }
    var batchSaveJob by remember { mutableStateOf<Job?>(null) }
    var savingMediaKey by remember { mutableStateOf<String?>(null) }
    var failedBatchMediaKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastBatchSaveFormat by remember { mutableStateOf<CompatGalleryBatchSaveFormat?>(null) }
    var batchRetryAttempt by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var overflowOpen by remember { mutableStateOf(false) }
    var contextPost by remember { mutableStateOf<CompatPostSnapshot?>(null) }
    var imageNgRegistrationPost by remember { mutableStateOf<CompatPostSnapshot?>(null) }
    var ascii2dRegisterPost by remember { mutableStateOf<CompatPostSnapshot?>(null) }
    var ascii2dRegistrationUrl by remember { mutableStateOf("") }
    var reverseSearchResult by remember { mutableStateOf<CompatImageSearchResult?>(null) }
    var thumbnailReloadTokens by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var thumbnailFallbackPostNos by remember { mutableStateOf<Set<String>>(emptySet()) }
    val apngMarkers = remember(tabKey) { mutableStateMapOf<String, Boolean>() }
    val mediaSaver = remember(httpClient, fileSystem) {
        if (httpClient != null && fileSystem != null) SingleMediaSaveService(httpClient, fileSystem) else null
    }
    val mediaZipSaver = remember(httpClient, fileSystem) {
        if (httpClient != null && fileSystem != null) ImageZipSaveService(httpClient, fileSystem) else null
    }
    val portraitColumns = preferences.compatPreferenceValue(
        "viewer", "galleryGridViewPortraitClmNum", "縦持ちの列数"
    )?.toIntOrNull()?.coerceIn(2, 16) ?: 5
    val landscapeColumns = preferences.compatPreferenceValue(
        "viewer", "galleryGridViewLandscapeClmNum", "横持ちの列数"
    )?.toIntOrNull()?.coerceIn(2, 16) ?: 7
    val manualSaveLocation = parseCompatSaveLocation(
        preferences.compatPreferenceValue("storage", "dummyDownloadDir", "保存ファイルの保存先")
    )
    val upsThumbnailMethod = preferences.compatPreferenceValue(
        "thread", "threadUpsThumbMethod", "あぷ小のサムネイルの読み込み", "あぷ小の読み込み"
    ) ?: "利用しない"
    val showDeletedContent = preferences.compatPreferenceValue(
        "thread", "threadAdminDeleteShow", "削除されたレスを表示"
    ) == "ON"
    val wifiConnected = isCompatWifiConnected(LocalPlatformContext.current)
    val threadPrivacyEnabled = preferences.compatPrivacyEnabled()
    val threadPrivacyAlpha = parseCompatPercent(
        preferences.compatPreferenceValue("thread", "commonPrivacyAlpha", "プライバシー透明度")
    )
    val imageNgPhashThreshold = preferences.compatPreferenceValue(
        "thread", "threadImageNgPhashThreshold", "画像NG類似度閾値"
    )?.filter(Char::isDigit)?.toIntOrNull()
        ?.coerceIn(CompatImagePhash.MIN_THRESHOLD, CompatImagePhash.MAX_THRESHOLD)
        ?: CompatImagePhash.DEFAULT_THRESHOLD
    val imageNgPhashRules = remember(ngRules, tabKey) {
        ngRules.filter { it.kind == CompatNgKind.THREAD_IMAGE_PHASH && it.appliesToThreadImage(tab.boardKey, tabKey) }
    }
    fun openSearchResult(url: String, title: String) {
        if (isCompatReverseSearchBrowserUrl(url)) {
            reverseSearchResult = CompatImageSearchResult.RemoteUrl(title, url)
        } else {
            message = "画像検索結果のURLが不正です"
        }
    }
    LaunchedEffect(
        tabKey,
        ngRules,
        upsThumbnailMethod,
        wifiConnected,
        imageNgPhashThreshold,
        imageNgPhashRules,
        httpClient,
        showDeletedContent
    ) {
        val hiddenImages = ngRules.asSequence()
            .filter { it.kind == CompatNgKind.THREAD_IMAGE && it.appliesToThreadImage(tab.boardKey, tabKey) }
            .mapTo(mutableSetOf(), CompatNgRule::normalizedValue)
        val snapshot = store.loadThreadSnapshot(tabKey)?.let {
            withContext(AppDispatchers.parsing) { normalizeCompatThreadSnapshot(it) }
        }
        snapshotRevision = snapshot?.revision ?: tab.snapshotRevision
        val rawPosts = presentCompatPostsForDeletedVisibility(
            posts = snapshot?.posts.orEmpty(),
            showDeletedContent = showDeletedContent
        )
        val hiddenPostNos = compatImagePhashHiddenPostNos(
            httpClient = httpClient,
            posts = rawPosts,
            rules = imageNgPhashRules,
            threshold = imageNgPhashThreshold
        )
        posts = withContext(AppDispatchers.parsing) {
            compatViewerMediaPosts(
                posts = rawPosts,
                hiddenImages = hiddenImages,
                hiddenPostNos = hiddenPostNos,
                upsThumbnailMethod = upsThumbnailMethod,
                wifiConnected = wifiConnected
            )
        }
    }
    LaunchedEffect(posts.size, initialIndex, initialPostNo) {
        if (posts.isNotEmpty()) {
            val target = compatViewerInitialPage(posts, initialPostNo, initialIndex)
            withFrameNanos { }
            gridState.scrollToItem(target)
        }
    }
    LaunchedEffect(posts) {
        val availableKeys = posts.mapTo(mutableSetOf(), ::compatMediaIdentity)
        selectedMediaKeys = selectedMediaKeys.intersect(availableKeys)
    }
    fun savePost(post: CompatPostSnapshot) {
        if (savingMediaKey != null || batchSaveJob != null) return
        scope.launch {
            val saver = mediaSaver
            if (saver == null) {
                message = "保存機能を初期化できませんでした"
                return@launch
            }
            val key = compatMediaIdentity(post)
            val mediaUrl = resolveCompatViewerMediaUrl(post)
            if (mediaUrl == null) {
                message = "保存するメディアがありません"
                return@launch
            }
            savingMediaKey = key
            try {
                message = saver.saveMedia(
                    mediaUrl,
                    tab.boardKey,
                    tab.threadNo,
                    baseSaveLocation = manualSaveLocation,
                    storageDirectoryOverride = "",
                    useTypeSubdirectory = false
                ).fold(
                    onSuccess = { "${it.fileName}を保存しました" },
                    onFailure = { it.toCompatUserMessage("メディアを保存できませんでした") }
                )
            } finally {
                savingMediaKey = null
            }
        }
    }
    fun startBatchSave(
        targets: List<CompatPostSnapshot>,
        format: CompatGalleryBatchSaveFormat,
        isRetry: Boolean = false
    ) {
        if (targets.isEmpty() || batchSaveJob != null || savingMediaKey != null) return
        val targetByUrl = targets.mapNotNull { post ->
            resolveCompatViewerMediaUrl(post)?.let { it to post }
        }.distinctBy { it.first }
        if (targetByUrl.isEmpty()) {
            message = "保存するメディアがありません"
            return
        }
        batchSaveFormatDialog = false
        batchSaveCancelRequested = false
        failedBatchMediaKeys = emptySet()
        lastBatchSaveFormat = format
        if (!isRetry) batchRetryAttempt = 0 else batchRetryAttempt += 1
        batchSaveJob = scope.launch {
            val failedUrls = mutableSetOf<String>()
            var succeeded = 0
            try {
                when (format) {
                    CompatGalleryBatchSaveFormat.ZIP -> {
                        val saver = mediaZipSaver ?: error("ZIP保存機能を初期化できませんでした")
                        val result = saver.save(
                            mediaUrls = targetByUrl.map { it.first },
                            boardId = tab.boardKey,
                            threadId = tab.threadNo,
                            baseSaveLocation = manualSaveLocation,
                            baseDirectory = MANUAL_SAVE_DIRECTORY,
                            fileNameSuffix = batchRetryAttempt.takeIf { isRetry }?.let { "retry$it" },
                            onProgress = { current, total, item, itemBytes, itemTotalBytes ->
                                batchSaveProgress = SaveProgress(
                                    SavePhase.DOWNLOADING,
                                    current,
                                    total,
                                    item,
                                    itemBytes,
                                    itemTotalBytes
                                )
                            }
                        ).getOrThrow()
                        succeeded = result.savedItems
                        failedUrls += result.failedUrls
                        message = buildCompatGalleryBatchSaveMessage(format, succeeded, failedUrls.size)
                    }
                    CompatGalleryBatchSaveFormat.FOLDER -> {
                        val saver = mediaSaver ?: error("保存機能を初期化できませんでした")
                        val folder = buildCompatManualImageFolderName(
                            boardName = tab.boardName,
                            title = tab.title,
                            threadId = tab.threadNo
                        )
                        val outputNames = compatBatchOutputFileNames(targetByUrl.map { it.first })
                        targetByUrl.forEachIndexed { index, (url, _) ->
                            val item = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
                            batchSaveProgress = SaveProgress(SavePhase.DOWNLOADING, index, targetByUrl.size, item)
                            saver.saveMedia(
                                url,
                                tab.boardKey,
                                tab.threadNo,
                                baseSaveLocation = manualSaveLocation,
                                baseDirectory = MANUAL_SAVE_DIRECTORY,
                                storageDirectoryOverride = folder,
                                useTypeSubdirectory = false,
                                outputFileNameOverride = outputNames[url],
                                onProgress = { itemBytes, itemTotalBytes ->
                                    batchSaveProgress = SaveProgress(
                                        SavePhase.DOWNLOADING,
                                        index,
                                        targetByUrl.size,
                                        item,
                                        itemBytes,
                                        itemTotalBytes
                                    )
                                }
                            ).fold(
                                onSuccess = { succeeded += 1 },
                                onFailure = { failedUrls += url }
                            )
                            batchSaveProgress = SaveProgress(SavePhase.DOWNLOADING, index + 1, targetByUrl.size, item)
                        }
                        message = buildCompatGalleryBatchSaveMessage(format, succeeded, failedUrls.size)
                    }
                }
                failedBatchMediaKeys = targetByUrl.asSequence()
                    .filter { it.first in failedUrls }
                    .map { compatMediaIdentity(it.second) }
                    .toSet()
                if (failedBatchMediaKeys.isEmpty()) {
                    selectedMediaKeys = emptySet()
                    saveMode = false
                }
            } catch (cancelled: CancellationException) {
                message = if (succeeded > 0) {
                    "キャンセルしました\n${succeeded}件のメディアをここまで保存しました"
                } else {
                    "キャンセルしました"
                }
            } catch (failure: Throwable) {
                failedBatchMediaKeys = targetByUrl.mapTo(mutableSetOf()) { compatMediaIdentity(it.second) }
                message = failure.toCompatUserMessage("一括保存できませんでした")
            } finally {
                batchSaveProgress = null
                batchSaveCancelRequested = false
                batchSaveJob = null
            }
        }
    }
    fun sharePost(post: CompatPostSnapshot) {
        val mediaUrl = resolveCompatViewerMediaUrl(post) ?: return
        scope.launch {
            val saver = mediaSaver
            val fs = fileSystem
            if (saver == null || fs == null) {
                message = "画像共有を初期化できませんでした"
                return@launch
            }
            saver.saveMedia(
                mediaUrl,
                tab.boardKey,
                tab.threadNo,
                baseSaveLocation = manualSaveLocation
            )
                .onSuccess { saved ->
                    val mime = if (saved.mediaType.name == "VIDEO") "video/*" else "image/*"
                    val localPath = if (manualSaveLocation == null) {
                        fs.resolveAbsolutePath("$MANUAL_SAVE_DIRECTORY/${saved.relativePath}")
                    } else null
                    share(mediaUrl, mime, localPath)
                }
                .onFailure { message = it.toCompatUserMessage("画像を共有できませんでした") }
        }
    }
    fun searchAscii2d(post: CompatPostSnapshot) {
        val mediaUrl = resolveCompatViewerMediaUrl(post)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl, allowGif = false)) {
            message = "GIF・WebM・MP4は検索できません"
            return
        }
        val client = httpClient
        if (client == null) {
            message = "二次元画像検索を初期化できませんでした"
            return
        }
        if (!isCompatAscii2dRegistered(preferences)) {
            ascii2dRegistrationUrl = preferences[COMPAT_ASCII2D_ENDPOINT_KEY]
                ?.trim()
                .orEmpty()
            ascii2dRegisterPost = post
            return
        }
        val endpoint = compatAscii2dEndpoint(preferences)
        message = "二次元画像検索中…"
        scope.launch {
            searchCompatAscii2d(client, endpoint, mediaUrl)
                .onSuccess { resultUrl ->
                    message = null
                    openSearchResult(resultUrl, "二次元画像類似検索")
                }
                .onFailure { failure ->
                    message = failure.toCompatUserMessage("二次元画像検索に失敗しました")
                }
        }
    }
    fun searchGoogle(
        post: CompatPostSnapshot,
        mode: CompatGoogleImageSearchMode = CompatGoogleImageSearchMode.LEGACY
    ) {
        val mediaUrl = resolveCompatViewerMediaUrl(post)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl)) {
            message = "WebM・MP4は検索できません"
            return
        }
        when (mode) {
            CompatGoogleImageSearchMode.LEGACY -> {
                val resultUrl = buildCompatGoogleImageSearchUrl(mediaUrl)
                if (resultUrl == null) message = "検索する画像がありません"
                else openSearchResult(resultUrl, mode.label)
            }
            CompatGoogleImageSearchMode.GOOGLE_FILE -> {
                val client = httpClient
                if (client == null) {
                    message = "Google画像検索の通信機能を初期化できませんでした"
                    return
                }
                message = "Google画像検索に画像を送信中…"
                scope.launch {
                    searchCompatGoogleClassicFile(client, mediaUrl)
                        .onSuccess { resultUrl -> message = null; openSearchResult(resultUrl, mode.label) }
                        .onFailure { failure ->
                            message = failure.toCompatUserMessage("Google画像検索に失敗しました")
                        }
                }
            }
            CompatGoogleImageSearchMode.LENS_URL -> {
                val resultUrl = buildCompatGoogleLensUrl(mediaUrl)
                if (resultUrl == null) message = "検索する画像がありません"
                else openSearchResult(resultUrl, mode.label)
            }
            CompatGoogleImageSearchMode.LENS_FILE -> {
                val client = httpClient
                if (client == null) {
                    message = "Google Lensの通信機能を初期化できませんでした"
                    return
                }
                message = "Google Lensに画像を送信中…"
                scope.launch {
                    searchCompatGoogleLensFile(client, mediaUrl)
                        .onSuccess { resultUrl ->
                            message = null
                            openSearchResult(resultUrl, mode.label)
                        }
                        .onFailure { failure ->
                            message = failure.toCompatUserMessage("Google Lens検索に失敗しました")
                        }
                }
            }
        }
    }
    fun searchUrlTarget(post: CompatPostSnapshot, target: CompatImageSearchTarget) {
        val mediaUrl = resolveCompatViewerMediaUrl(post)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl)) {
            message = "WebM・MP4は検索できません"
            return
        }
        val resultUrl = buildCompatImageSearchTargetUrl(target, mediaUrl)
        if (resultUrl == null) {
            message = "検索する画像がありません"
        } else {
            openSearchResult(resultUrl, target.label)
        }
    }
    fun searchFileTarget(post: CompatPostSnapshot, target: CompatImageSearchTarget) {
        val mediaUrl = resolveCompatViewerMediaUrl(post)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl)) {
            message = "WebM・MP4は検索できません"
            return
        }
        val client = httpClient
        if (client == null) {
            message = "画像検索の通信機能を初期化できませんでした"
            return
        }
        message = "${target.label}に画像を送信中…"
        scope.launch {
            searchCompatImageFileTarget(client, target, mediaUrl)
                .onSuccess { result -> message = null; reverseSearchResult = result }
                .onFailure { failure ->
                    message = failure.toCompatUserMessage("${target.label}に失敗しました")
                }
        }
    }
    val imageSearchTargets = compatImageSearchActionTargets(preferences[COMPAT_CUSTOM_IMAGE_SEARCH_KEY])
    fun searchConfiguredTarget(post: CompatPostSnapshot, target: CompatImageSearchTarget) {
        when (target) {
            CompatImageSearchTarget.GOOGLE_FILE ->
                searchGoogle(post, CompatGoogleImageSearchMode.GOOGLE_FILE)
            CompatImageSearchTarget.GOOGLE_URL ->
                searchGoogle(post, CompatGoogleImageSearchMode.LEGACY)
            CompatImageSearchTarget.LENS_FILE ->
                searchGoogle(post, CompatGoogleImageSearchMode.LENS_FILE)
            CompatImageSearchTarget.LENS_URL ->
                searchGoogle(post, CompatGoogleImageSearchMode.LENS_URL)
            CompatImageSearchTarget.ASCII2D_URL -> searchAscii2d(post)
            else -> if (target.method == CompatImageSearchMethod.FILE) {
                searchFileTarget(post, target)
            } else {
                searchUrlTarget(post, target)
            }
        }
    }
    val galleryOverflowLabels = remember { compatGalleryOverflowLabels() }
    Scaffold(
        containerColor = SecondaryBackground,
        topBar = {
            TopAppBar(
                expandedHeight = 56.dp,
                title = {
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("画像一覧")
                        Text(
                            if (saveMode) "${selectedMediaKeys.size}/${posts.size}件選択" else "${posts.size}枚",
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (saveMode) {
                            saveMode = false
                            selectedMediaKeys = emptySet()
                        } else onBack()
                    }) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") }
                },
                actions = {
                    if (saveMode) {
                        TextButton(onClick = {
                            selectedMediaKeys = if (selectedMediaKeys.size == posts.size) {
                                emptySet()
                            } else {
                                posts.mapTo(mutableSetOf(), ::compatMediaIdentity)
                            }
                        }) {
                            Text(
                                if (selectedMediaKeys.size == posts.size) "全解除" else "全選択",
                                color = Color.White
                            )
                        }
                        IconButton(
                            enabled = selectedMediaKeys.isNotEmpty() && batchSaveJob == null,
                            onClick = { batchSaveFormatDialog = true }
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "選択したメディアを保存")
                        }
                        IconButton(onClick = {
                            saveMode = false
                            selectedMediaKeys = emptySet()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "選択を終了")
                        }
                    } else {
                        IconButton(
                            onClick = { saveMode = true },
                            modifier = Modifier.semantics { stateDescription = "OFF" }
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "一括保存",
                                tint = Color.White.copy(alpha = 0.62f)
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "その他") }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false },
                            shape = RoundedCornerShape(2.dp),
                            containerColor = compatibilityPopupSurface(LocalCompatibilityPalette.current),
                            tonalElevation = 0.dp,
                            shadowElevation = 8.dp
                        ) {
                            DropdownMenuItem(text = { Text(galleryOverflowLabels[0]) }, colors = compatibilityMenuItemColors(), onClick = {
                                overflowOpen = false
                                onOpenSettings()
                            })
                            DropdownMenuItem(text = { Text(galleryOverflowLabels[1]) }, colors = compatibilityMenuItemColors(), onClick = {
                                overflowOpen = false
                                onOpenCommonSettings()
                            })
                            DropdownMenuItem(text = { Text(galleryOverflowLabels[2]) }, colors = compatibilityMenuItemColors(), onClick = {
                                overflowOpen = false
                                onOpenHelp()
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryTeal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {}
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val columns = if (maxWidth > maxHeight) landscapeColumns else portraitColumns
            if (posts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("画像はありません") }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    modifier = Modifier.fillMaxSize()
                ) {
                // A thread may expose the same media URL from more than one
                // post.  The URL remains the selection identity, but the
                // rendered item key must include its snapshot position so a
                // malformed/duplicated fixture cannot crash LazyGrid.
                itemsIndexed(posts, key = { index, post -> "${compatMediaIdentity(post)}:$index" }) { index, post ->
                    val mediaIdentity = compatMediaIdentity(post)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 2.dp).combinedClickable(
                            onClick = {
                                when (compatGalleryTapAction(saveMode)) {
                                    CompatGalleryTapAction.SELECT_MEDIA -> {
                                        selectedMediaKeys = if (mediaIdentity in selectedMediaKeys) {
                                            selectedMediaKeys - mediaIdentity
                                        } else {
                                            selectedMediaKeys + mediaIdentity
                                        }
                                    }
                                    CompatGalleryTapAction.OPEN_VIEWER -> onOpenViewer(index, mediaIdentity)
                                }
                            },
                            onLongClick = {
                                contextPost = post
                            }
                        ).testTag("compat-gallery-item-${post.postNo}")
                    ) {
                        val requestedPreviewUrl = resolveCompatPostPreviewUrl(post)
                        val originalMediaUrl = resolveCompatViewerMediaUrl(post)
                        val isPng = originalMediaUrl
                            ?.substringBefore('?')
                            ?.substringBefore('#')
                            ?.endsWith(".png", ignoreCase = true) == true
                        LaunchedEffect(mediaIdentity, originalMediaUrl, httpClient) {
                            if (isPng && mediaIdentity !in apngMarkers) {
                                apngMarkers[mediaIdentity] = httpClient
                                    ?.let { fetchCompatApngMarker(it, originalMediaUrl).getOrDefault(false) }
                                    ?: false
                            }
                        }
                        val previewUrl = if (
                            mediaIdentity in thumbnailFallbackPostNos &&
                            requestedPreviewUrl != originalMediaUrl
                        ) originalMediaUrl else requestedPreviewUrl
                        val reloadToken = thumbnailReloadTokens[mediaIdentity]
                        Box(
                            Modifier.fillMaxWidth()
                                .aspectRatio(1f)
                                .background(palette.background)
                                .testTag("compat-gallery-image-${post.postNo}")
                        ) {
                            AsyncImage(
                                model = if (reloadToken == null) previewUrl else "$previewUrl#compat-reload=$reloadToken",
                                imageLoader = imageLoader,
                                contentDescription = "No.${post.postNo}",
                                onError = {
                                    if (requestedPreviewUrl != originalMediaUrl) {
                                        thumbnailFallbackPostNos = thumbnailFallbackPostNos + mediaIdentity
                                    }
                                },
                                // sample/1.apk fixes the image itself to a
                                // column-width square and uses fitCenter.
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().compatPrivacyImageEffect(
                                    if (threadPrivacyEnabled) compatPrivacyContentAlpha(threadPrivacyAlpha) else 1f
                                )
                            )
                            Text(
                                post.position.toString(),
                                color = palette.text,
                                fontSize = 14.sp,
                                modifier = Modifier.align(Alignment.TopEnd)
                                    .background(palette.background).padding(horizontal = 2.dp)
                            )
                            val mediaUrl = resolveCompatViewerMediaUrl(post)
                            val mediaBadge = when {
                                apngMarkers[mediaIdentity] == true -> "APNG"
                                mediaUrl != null && isCompatVideoMediaUrl(mediaUrl) ->
                                    compatMediaExtension(mediaUrl).uppercase()
                                mediaUrl?.substringBefore('?')?.substringBefore('#')
                                    ?.endsWith(".gif", ignoreCase = true) == true -> "GIF"
                                else -> null
                            }
                            if (mediaBadge != null) {
                                Text(
                                    mediaBadge,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.BottomStart)
                                        .background(palette.chrome).padding(horizontal = 2.dp)
                                )
                            }
                            if (saveMode) {
                                Checkbox(
                                    checked = mediaIdentity in selectedMediaKeys,
                                    onCheckedChange = null,
                                    modifier = Modifier.align(Alignment.TopStart)
                                        .testTag("compat-gallery-selection-${post.postNo}"),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = palette.chrome,
                                        uncheckedColor = Color.White
                                    )
                                )
                            }
                            if (savingMediaKey == mediaIdentity) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                                    color = palette.chrome,
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                        Text(
                            post.messageHtml.toCompatPlainText().lineSequence().firstOrNull().orEmpty(),
                            color = palette.text,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().background(palette.background)
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
                }
            }
        }
    }
    if (batchSaveFormatDialog) {
        AlertDialog(
            onDismissRequest = { batchSaveFormatDialog = false },
            title = { Text("一括保存") },
            text = { Text("選択した${selectedMediaKeys.size}件の画像・動画を保存します") },
            confirmButton = {
                TextButton(onClick = {
                    startBatchSave(
                        posts.filter { compatMediaIdentity(it) in selectedMediaKeys },
                        CompatGalleryBatchSaveFormat.ZIP
                    )
                }) { Text("ZIP") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        startBatchSave(
                            posts.filter { compatMediaIdentity(it) in selectedMediaKeys },
                            CompatGalleryBatchSaveFormat.FOLDER
                        )
                    }) { Text("フォルダ") }
                    TextButton(onClick = { batchSaveFormatDialog = false }) { Text("キャンセル") }
                }
            }
        )
    }
    batchSaveProgress?.let { progress ->
        CompatThreadSaveProgressDialog(
            progress = progress,
            cancelRequested = batchSaveCancelRequested,
            onCancel = {
                batchSaveCancelRequested = true
                batchSaveJob?.cancel()
            }
        )
    }
    contextPost?.let { post ->
        val mediaUrl = resolveCompatViewerMediaUrl(post)
        val entries = compatGalleryContextBaseLabels() + imageSearchTargets.map { it.label }
        CompatLegacyChoiceDialog(
            onDismiss = { contextPost = null },
            choices = entries,
            enabled = { mediaUrl != null || it == "元レスに移動する" },
            testTag = "compat-gallery-context-menu",
            onChoice = { label ->
                when (label) {
                    "元レスに移動する" -> scope.launch {
                        store.updateScrollAnchor(
                            tab.key,
                            ScrollAnchor(
                                postNo = post.postNo,
                                fallbackIndex = post.position.coerceAtLeast(0),
                                snapshotRevision = snapshotRevision
                            )
                        )
                        onBack()
                    }
                    "画像を保存する" -> savePost(post)
                    "サムネイルを再読み込みする" -> {
                        thumbnailReloadTokens = thumbnailReloadTokens +
                            (compatMediaIdentity(post) to Clock.System.now().toEpochMilliseconds())
                    }
                    "NG画像に登録" -> imageNgRegistrationPost = post
                    "リンクURLをコピー" -> mediaUrl?.let {
                        clipboard.setText(AnnotatedString(it))
                        message = "URLをコピーしました"
                    }
                    "ブラウザーで開く" -> mediaUrl?.let(openUrl)
                    "URLを共有" -> mediaUrl?.let { share(it, "text/plain", null) }
                    "画像を共有" -> sharePost(post)
                    else -> imageSearchTargets.firstOrNull { it.label == label }
                        ?.let { searchConfiguredTarget(post, it) }
                }
            }
        )
    }
    imageNgRegistrationPost?.let { post ->
        val mediaUrl = resolveCompatViewerMediaUrl(post).orEmpty()
        CompatImageNgRegistrationDialog(
            imageUrl = mediaUrl,
            initialMemo = post.messageHtml.toCompatPlainText().take(MAX_COMPAT_NG_MEMO_CHARS),
            onDismiss = { imageNgRegistrationPost = null },
            onRegister = { memo, localOnly ->
                imageNgRegistrationPost = null
                val scopeKey = compatThreadImageNgScopeKey(tab.boardKey, localOnly)
                val client = httpClient
                if (client == null) {
                    message = "通信機能を初期化できませんでした"
                } else {
                    message = "NG画像登録中"
                    scope.launch {
                        fetchCompatImagePhash(client, mediaUrl)
                            .onSuccess { phash ->
                                store.upsertNgRule(
                                    CompatNgRule(
                                        id = compatNgRuleId(CompatNgKind.THREAD_IMAGE_PHASH, scopeKey, phash),
                                        kind = CompatNgKind.THREAD_IMAGE_PHASH,
                                        scopeKey = scopeKey,
                                        normalizedValue = phash,
                                        imageUrl = mediaUrl,
                                        memo = memo,
                                        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds()
                                    )
                                )
                                message = "画像pHash NGに登録しました"
                            }
                            .onFailure { failure ->
                                message = failure.toCompatUserMessage("画像pHashを作成できませんでした")
                            }
                    }
                }
            }
        )
    }
    ascii2dRegisterPost?.let {
        CompatAscii2dRegistrationDialog(
            initialEndpoint = ascii2dRegistrationUrl,
            onDismiss = { ascii2dRegisterPost = null },
            onRegister = { endpoint ->
                scope.launch {
                    store.savePreference(COMPAT_ASCII2D_ENDPOINT_KEY, endpoint)
                    store.savePreference(COMPAT_ASCII2D_ENABLED_KEY, "ON")
                }
                ascii2dRegisterPost = null
                message = "登録しました"
            },
            onInvalid = { message = "アドレスが間違っています" }
        )
    }
    message?.let { current ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(current) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            dismissButton = {
                if (failedBatchMediaKeys.isNotEmpty() && lastBatchSaveFormat != null) {
                    TextButton(onClick = {
                        val retryTargets = posts.filter { compatMediaIdentity(it) in failedBatchMediaKeys }
                        message = null
                        lastBatchSaveFormat?.let { startBatchSave(retryTargets, it, isRetry = true) }
                    }) { Text("失敗分を再試行") }
                }
            }
        )
    }
    reverseSearchResult?.let { result ->
        CompatReverseImageSearchScreen(
            result = result,
            cookieRepository = cookieRepository,
            onClose = { reverseSearchResult = null },
            onOpenExternal = openUrl
        )
    }
}

@Composable
fun CompatAscii2dRegistrationDialog(
    initialEndpoint: String,
    onDismiss: () -> Unit,
    onRegister: (String) -> Unit,
    onInvalid: () -> Unit = {}
) {
    var endpoint by remember(initialEndpoint) { mutableStateOf(initialEndpoint) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("詳細画像検索の設定") },
        text = {
            Column {
                Text("アドレス ※わかる人向け")
                TextField(
                    value = endpoint,
                    onValueChange = { endpoint = it.take(512) },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("compat-ascii2d-address")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = endpoint.trim()
                if (isValidCompatAscii2dEndpoint(normalized)) {
                    onRegister(normalized)
                } else {
                    // AlertDialog#setPositiveButton in both reference APKs closes even
                    // when validation fails, then reports the exact toast text.
                    onDismiss()
                    onInvalid()
                }
            }) {
                Text("登録する")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
internal fun CompatViewerScreen(
    tab: CompatTab,
    initialIndex: Int,
    initialPostNo: String? = null,
    store: CompatibilityStore,
    toolbarRefreshToken: Long = 0L,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    cookieRepository: CookieRepository? = null,
    onToolbarEdit: () -> Unit,
    onShowSourcePost: (ScrollAnchor) -> Unit,
    onOpenGallery: (Int, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCommonSettings: () -> Unit,
    onOpenHelp: () -> Unit = {},
    onBack: () -> Unit
) {
    val tabKey = tab.key
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlLauncher()
    val share = rememberCompatShareLauncher()
    val clipboard = LocalClipboardManager.current
    val imageLoader = LocalFutachaImageLoader.current
    var posts by remember(tabKey) { mutableStateOf<List<CompatPostSnapshot>>(emptyList()) }
    var snapshotRevision by remember(tabKey) { mutableStateOf(tab.snapshotRevision) }
    var chromeVisible by remember { mutableStateOf(true) }
    var quickMenu by remember { mutableStateOf(false) }
    var imageSearchSelectorOpen by remember { mutableStateOf(false) }
    var ascii2dRegistrationOpen by remember { mutableStateOf(false) }
    var ascii2dRegistrationUrl by remember { mutableStateOf("") }
    var reverseSearchResult by remember { mutableStateOf<CompatImageSearchResult?>(null) }
    var topOverflowOpen by remember { mutableStateOf(false) }
    var toolbarOverflowOpen by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var infoOpen by remember { mutableStateOf(false) }
    var infoLoading by remember { mutableStateOf(false) }
    var remoteMediaInfo by remember { mutableStateOf<Map<String, CompatRemoteMediaInfo>>(emptyMap()) }
    var remoteExifInfo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Dimensions are only metadata for the optional information dialog. They
    // are not rendering state, so keeping them as a plain screen-local cache
    // prevents a Pager child from invalidating/recomposing the parent while
    // HorizontalPager is subcomposing its pages.
    val mediaDimensions = remember(tabKey) { mutableMapOf<String, Pair<Int, Int>>() }
    val videoMediaInfo = remember(tabKey) { mutableStateMapOf<String, VideoMediaInfo>() }
    var isSaving by remember { mutableStateOf(false) }
    var toolbarItems by remember { mutableStateOf(reconcileCompatToolbar(CompatToolbarSurface.VIEWER, emptyList())) }
    // Telephoto's FlickToDismiss keeps the unmodified drag offset as state. The
    // rubber-banded value is only used for rendering; using the rendered value
    // for the threshold makes the compat mode require roughly twice the APK
    // distance before dismissing.
    var verticalRawOffset by remember { mutableFloatStateOf(0f) }
    var verticalDismissAnimating by remember { mutableStateOf(false) }
    // Keep one transform per media URL at the viewer level. Pager children may
    // be disposed/recreated while the source image changes; page-local zoom
    // state then looks like a snap back to the centre.
    val viewerTransforms = remember(tabKey) { mutableStateMapOf<String, CompatViewerTransform>() }
    var isZoomed by remember { mutableStateOf(false) }
    var viewerReloadTokens by remember(tabKey) { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var verticalResetJob by remember { mutableStateOf<Job?>(null) }
    val verticalVelocityTracker = remember { VelocityTracker() }
    val mediaSaver = remember(httpClient, fileSystem) {
        if (httpClient != null && fileSystem != null) SingleMediaSaveService(httpClient, fileSystem) else null
    }
    val manualSaveLocation = parseCompatSaveLocation(
        preferences.compatPreferenceValue("storage", "dummyDownloadDir", "保存ファイルの保存先")
    )
    val verticalSwipeCloseEnabled = preferences.compatPreferenceValue(
        "control", "controlViewerSwipeClose", "下にスワイプして閉じる", "縦にスワイプして閉じる"
    ) != "OFF"
    val upsThumbnailMethod = preferences.compatPreferenceValue(
        "thread", "threadUpsThumbMethod", "あぷ小のサムネイルの読み込み", "あぷ小の読み込み"
    ) ?: "利用しない"
    val showDeletedContent = preferences.compatPreferenceValue(
        "thread", "threadAdminDeleteShow", "削除されたレスを表示"
    ) == "ON"
    val wifiConnected = isCompatWifiConnected(LocalPlatformContext.current)
    val threadPrivacyEnabled = preferences.compatPrivacyEnabled()
    val threadPrivacyAlpha = parseCompatPercent(
        preferences.compatPreferenceValue("thread", "commonPrivacyAlpha", "プライバシー透明度")
    )
    val viewerPreloadMode = preferences.compatPreferenceValue(
        "viewer", "viewerPreloadMode", "前後の画像を先読みする"
    ) ?: "常に利用する"
    val switchWebmToMp4 = preferences.compatPreferenceValue(
        "viewer", "viewerWebMSwitchMp4", "WebMをMP4で再生する"
    ) == "ON"
    val platformContext = LocalPlatformContext.current
    fun openSearchResult(url: String, title: String) {
        if (isCompatReverseSearchBrowserUrl(url)) {
            reverseSearchResult = CompatImageSearchResult.RemoteUrl(title, url)
        } else {
            message = "画像検索結果のURLが不正です"
        }
    }
    val imageNgPhashThreshold = preferences.compatPreferenceValue(
        "thread", "threadImageNgPhashThreshold", "画像NG類似度閾値"
    )?.filter(Char::isDigit)?.toIntOrNull()
        ?.coerceIn(CompatImagePhash.MIN_THRESHOLD, CompatImagePhash.MAX_THRESHOLD)
        ?: CompatImagePhash.DEFAULT_THRESHOLD
    val imageNgPhashRules = remember(ngRules, tabKey) {
        ngRules.filter { it.kind == CompatNgKind.THREAD_IMAGE_PHASH && it.appliesToThreadImage(tab.boardKey, tabKey) }
    }
    LaunchedEffect(
        tabKey,
        ngRules,
        upsThumbnailMethod,
        wifiConnected,
        imageNgPhashThreshold,
        imageNgPhashRules,
        httpClient,
        showDeletedContent
    ) {
        val hiddenImages = ngRules.asSequence()
            .filter { it.kind == CompatNgKind.THREAD_IMAGE && it.appliesToThreadImage(tab.boardKey, tabKey) }
            .mapTo(mutableSetOf(), CompatNgRule::normalizedValue)
        val snapshot = store.loadThreadSnapshot(tabKey)
        snapshotRevision = snapshot?.revision ?: tab.snapshotRevision
        val rawPosts = snapshot
            ?.let { loadedSnapshot ->
                withContext(AppDispatchers.parsing) { normalizeCompatThreadSnapshot(loadedSnapshot) }
            }
            ?.posts
            .orEmpty()
            .let { posts ->
                presentCompatPostsForDeletedVisibility(posts, showDeletedContent)
            }
        val hiddenPostNos = compatImagePhashHiddenPostNos(
            httpClient = httpClient,
            posts = rawPosts,
            rules = imageNgPhashRules,
            threshold = imageNgPhashThreshold
        )
        posts = withContext(AppDispatchers.parsing) {
            compatViewerMediaPosts(
                posts = rawPosts,
                hiddenImages = hiddenImages,
                hiddenPostNos = hiddenPostNos,
                upsThumbnailMethod = upsThumbnailMethod,
                wifiConnected = wifiConnected
            )
        }
    }
    LaunchedEffect(toolbarRefreshToken) { toolbarItems = store.loadToolbar(CompatToolbarSurface.VIEWER) }
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceAtLeast(0)) { posts.size.coerceAtLeast(1) }
    LaunchedEffect(posts.size, initialIndex, initialPostNo) {
        if (posts.isNotEmpty()) {
            val targetPage = compatViewerInitialPage(posts, initialPostNo, initialIndex)
            // The snapshot is loaded from a LaunchedEffect while the pager is
            // still being subcomposed for its new page count.  Scrolling the
            // PagerState in that same frame can race PagerMeasure and trigger
            // Compose's "Unsupported concurrent change during composition".
            // Wait for the next frame so the page-count update and its measure
            // pass are complete before changing the scroll state.
            withFrameNanos { }
            if (pagerState.currentPage != targetPage) {
                pagerState.scrollToPage(targetPage)
            }
        }
    }
    val canDismissVertically = verticalSwipeCloseEnabled && !isZoomed
    val renderedVerticalOffset = renderCompatViewerVerticalOffset(
        rawOffsetPx = verticalRawOffset,
        dismissalAnimating = verticalDismissAnimating
    )
    ApplyCompatViewerSystemBars(hidden = !chromeVisible)
    PlatformBackHandler(enabled = !chromeVisible) { chromeVisible = true }
    LaunchedEffect(pagerState.currentPage) {
        val currentMediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl)
        isZoomed = currentMediaUrl?.let { viewerTransforms[it]?.scale ?: 1f }
            ?.let { it > COMPAT_VIEWER_ZOOM_GESTURE_THRESHOLD }
            ?: false
        verticalRawOffset = 0f
        verticalDismissAnimating = false
    }
    LaunchedEffect(pagerState.currentPage, viewerPreloadMode, wifiConnected, posts) {
        val preloadEnabled = shouldPreloadCompatViewer(viewerPreloadMode, wifiConnected)
        if (!preloadEnabled || posts.isEmpty()) return@LaunchedEffect
        listOf(pagerState.currentPage - 1, pagerState.currentPage + 1)
            .mapNotNull(posts::getOrNull)
            .mapNotNull { post ->
                val url = resolveCompatPostPreviewUrl(post, upsThumbnailMethod, wifiConnected)
                    ?: return@mapNotNull null
                if (isCompatVideoMediaUrl(url)) return@mapNotNull null
                ImageRequest.Builder(platformContext)
                    .data(url)
                    .compatImageFallbackPolicy()
                    // Preloading is only a latency optimization. Do not let
                    // an attachment without a server thumbnail decode its
                    // full camera resolution before the user opens it.
                    .size(1024, 1024)
                    .build()
            }
            .forEach { request -> imageLoader.enqueue(request) }
    }
    fun saveCurrent(shareAfterSave: Boolean) {
        val mediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl)
        if (mediaUrl == null || isSaving) return
        scope.launch {
            isSaving = true
            val saver = mediaSaver
            val fs = fileSystem
            if (saver == null || fs == null) {
                message = if (shareAfterSave) "画像共有を初期化できませんでした" else "保存機能を初期化できませんでした"
            } else {
                saver.saveMedia(
                    mediaUrl,
                    tab.boardKey,
                    tab.threadNo,
                    baseSaveLocation = manualSaveLocation,
                    storageDirectoryOverride = if (shareAfterSave) null else "",
                    useTypeSubdirectory = shareAfterSave
                )
                    .onSuccess { saved ->
                        if (shareAfterSave) {
                            val mime = if (saved.mediaType.name == "VIDEO") "video/*" else "image/*"
                            share(
                                mediaUrl,
                                mime,
                                if (manualSaveLocation == null) {
                                    fs.resolveAbsolutePath("$MANUAL_SAVE_DIRECTORY/${saved.relativePath}")
                                } else null
                            )
                        } else {
                            message = "${saved.fileName}を保存しました"
                        }
                    }
                    .onFailure {
                        message = it.toCompatUserMessage(
                            if (shareAfterSave) "画像を共有できませんでした" else "画像を保存できませんでした"
                        )
                    }
            }
            isSaving = false
        }
    }
    fun searchAscii2dCurrent() {
        val mediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl, allowGif = false)) {
            message = "GIF・WebM・MP4は検索できません"
            return
        }
        val client = httpClient
        if (client == null) {
            message = "二次元画像検索を初期化できませんでした"
            return
        }
        if (!isCompatAscii2dRegistered(preferences)) {
            ascii2dRegistrationUrl = preferences[COMPAT_ASCII2D_ENDPOINT_KEY]
                ?.trim()
                .orEmpty()
            ascii2dRegistrationOpen = true
            return
        }
        val endpoint = compatAscii2dEndpoint(preferences)
        message = "二次元画像検索中…"
        scope.launch {
            searchCompatAscii2d(client, endpoint, mediaUrl)
                .onSuccess { resultUrl ->
                    message = null
                    openSearchResult(resultUrl, "二次元画像類似検索")
                }
                .onFailure { failure ->
                    message = failure.toCompatUserMessage("二次元画像検索に失敗しました")
                }
        }
    }
    fun searchGoogleCurrent(
        mode: CompatGoogleImageSearchMode = CompatGoogleImageSearchMode.LEGACY
    ) {
        val mediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl)) {
            message = "WebM・MP4は検索できません"
            return
        }
        when (mode) {
            CompatGoogleImageSearchMode.LEGACY -> {
                val resultUrl = buildCompatGoogleImageSearchUrl(mediaUrl)
                if (resultUrl == null) message = "検索する画像がありません"
                else openSearchResult(resultUrl, mode.label)
            }
            CompatGoogleImageSearchMode.GOOGLE_FILE -> {
                val client = httpClient
                if (client == null) {
                    message = "Google画像検索の通信機能を初期化できませんでした"
                    return
                }
                message = "Google画像検索に画像を送信中…"
                scope.launch {
                    searchCompatGoogleClassicFile(client, mediaUrl)
                        .onSuccess { resultUrl -> message = null; openSearchResult(resultUrl, mode.label) }
                        .onFailure { failure ->
                            message = failure.toCompatUserMessage("Google画像検索に失敗しました")
                        }
                }
            }
            CompatGoogleImageSearchMode.LENS_URL -> {
                val resultUrl = buildCompatGoogleLensUrl(mediaUrl)
                if (resultUrl == null) message = "検索する画像がありません"
                else openSearchResult(resultUrl, mode.label)
            }
            CompatGoogleImageSearchMode.LENS_FILE -> {
                val client = httpClient
                if (client == null) {
                    message = "Google Lensの通信機能を初期化できませんでした"
                    return
                }
                message = "Google Lensに画像を送信中…"
                scope.launch {
                    searchCompatGoogleLensFile(client, mediaUrl)
                        .onSuccess { resultUrl ->
                            message = null
                            openSearchResult(resultUrl, mode.label)
                        }
                        .onFailure { failure ->
                            message = failure.toCompatUserMessage("Google Lens検索に失敗しました")
                        }
                }
            }
        }
    }
    val imageSearchTargets = compatImageSearchActionTargets(preferences[COMPAT_CUSTOM_IMAGE_SEARCH_KEY])
    fun searchUrlTargetCurrent(target: CompatImageSearchTarget) {
        val mediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl)) {
            message = "WebM・MP4は検索できません"
            return
        }
        val resultUrl = buildCompatImageSearchTargetUrl(target, mediaUrl)
        if (resultUrl == null) message = "検索する画像がありません"
        else openSearchResult(resultUrl, target.label)
    }
    fun searchFileTargetCurrent(target: CompatImageSearchTarget) {
        val mediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl)
        if (mediaUrl == null || !isCompatImageSearchableMediaUrl(mediaUrl)) {
            message = "WebM・MP4は検索できません"
            return
        }
        val client = httpClient
        if (client == null) {
            message = "画像検索の通信機能を初期化できませんでした"
            return
        }
        message = "${target.label}に画像を送信中…"
        scope.launch {
            searchCompatImageFileTarget(client, target, mediaUrl)
                .onSuccess { result -> message = null; reverseSearchResult = result }
                .onFailure { failure ->
                    message = failure.toCompatUserMessage("${target.label}に失敗しました")
                }
        }
    }
    fun searchConfiguredTargetCurrent(target: CompatImageSearchTarget) {
        when (target) {
            CompatImageSearchTarget.GOOGLE_FILE ->
                searchGoogleCurrent(CompatGoogleImageSearchMode.GOOGLE_FILE)
            CompatImageSearchTarget.GOOGLE_URL ->
                searchGoogleCurrent(CompatGoogleImageSearchMode.LEGACY)
            CompatImageSearchTarget.LENS_FILE ->
                searchGoogleCurrent(CompatGoogleImageSearchMode.LENS_FILE)
            CompatImageSearchTarget.LENS_URL ->
                searchGoogleCurrent(CompatGoogleImageSearchMode.LENS_URL)
            CompatImageSearchTarget.ASCII2D_URL -> searchAscii2dCurrent()
            else -> if (target.method == CompatImageSearchMethod.FILE) {
                searchFileTargetCurrent(target)
            } else {
                searchUrlTargetCurrent(target)
            }
        }
    }
    fun searchCurrent() {
        if (imageSearchTargets.isEmpty()) {
            message = "画像検索が設定されていません"
        } else {
            imageSearchSelectorOpen = true
        }
    }
    fun showCurrentInfo() {
        infoOpen = true
        val mediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl) ?: return
        val client = httpClient ?: return
        if (mediaUrl in remoteMediaInfo && mediaUrl in remoteExifInfo || infoLoading) return
        scope.launch {
            infoLoading = true
            fetchCompatRemoteMediaInfo(client, mediaUrl)
                .onSuccess { info -> remoteMediaInfo = remoteMediaInfo + (mediaUrl to info) }
            if (!isCompatVideoMediaUrl(mediaUrl)) {
                fetchCompatExifSummary(client, mediaUrl)
                    .onSuccess { info -> remoteExifInfo = remoteExifInfo + (mediaUrl to info) }
                    .onFailure { remoteExifInfo = remoteExifInfo + (mediaUrl to "取得できません") }
            } else {
                remoteExifInfo = remoteExifInfo + (mediaUrl to "動画のため対象外")
            }
            infoLoading = false
        }
    }
    if (imageSearchSelectorOpen) {
        CompatLegacyChoiceDialog(
            onDismiss = { imageSearchSelectorOpen = false },
            choices = imageSearchTargets.map { it.label },
            testTag = "compat-viewer-image-search-menu",
            onChoice = { label ->
                imageSearchTargets.firstOrNull { it.label == label }
                    ?.let(::searchConfiguredTargetCurrent)
            }
        )
    }
    if (ascii2dRegistrationOpen) {
        CompatAscii2dRegistrationDialog(
            initialEndpoint = ascii2dRegistrationUrl,
            onDismiss = { ascii2dRegistrationOpen = false },
            onRegister = { endpoint ->
                scope.launch {
                    store.savePreference(COMPAT_ASCII2D_ENDPOINT_KEY, endpoint)
                    store.savePreference(COMPAT_ASCII2D_ENABLED_KEY, "ON")
                }
                ascii2dRegistrationOpen = false
                message = "登録しました"
            },
            onInvalid = { message = "アドレスが間違っています" }
        )
    }
    val viewerActions: Map<String, () -> Unit> = buildMap {
        if (pagerState.currentPage > 0) {
            put("left") {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
        }
        if (pagerState.currentPage < posts.lastIndex) {
            put("right") {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }
        putAll(mapOf(
        "download" to { saveCurrent(shareAfterSave = false) },
        "search" to { searchCurrent() },
        "back" to {
            when (val target = compatViewerNavigationTarget("back", posts, pagerState.currentPage, snapshotRevision)) {
                is CompatViewerNavigationTarget.SourcePost -> onShowSourcePost(target.anchor)
                else -> Unit
            }
        },
        "gallery" to {
            when (val target = compatViewerNavigationTarget("gallery", posts, pagerState.currentPage, snapshotRevision)) {
                is CompatViewerNavigationTarget.Gallery -> onOpenGallery(target.index, target.mediaIdentity)
                else -> Unit
            }
        },
        "share" to { saveCurrent(shareAfterSave = true) },
        "info" to { showCurrentInfo() },
        "screen" to { chromeVisible = !chromeVisible },
        "privacy" to {
            val enabled = threadPrivacyEnabled
            scope.launch {
                store.savePreference(
                    COMPAT_COMMON_PRIVACY_STORAGE_KEY,
                    if (enabled) "OFF" else "ON"
                )
            }
        },
        "reload" to {
            posts.getOrNull(pagerState.currentPage)
                ?.let(::resolveCompatViewerMediaUrl)
                ?.let { mediaUrl ->
                    viewerReloadTokens = viewerReloadTokens +
                        (mediaUrl to Clock.System.now().toEpochMilliseconds())
                    message = "再読み込みしました"
                }
        }
        ))
    }
    val currentViewerMediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl)
    val viewerTopOverflowLabels = remember { compatViewerTopOverflowLabels() }
    val currentViewerFileName = currentViewerMediaUrl
        ?.substringAfterLast('/')
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() }
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            if (chromeVisible) {
                TopAppBar(
                    expandedHeight = 56.dp,
                    title = {
                        if (posts.isEmpty()) {
                            Text("画像", modifier = Modifier.padding(start = 16.dp))
                        } else {
                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                Text(tab.title, maxLines = 1, fontSize = 16.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        currentViewerFileName ?: "画像",
                                        maxLines = 1,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(" (", fontSize = 13.sp)
                                    // Keep the counter as its own semantics node;
                                    // the reference UI renders the same value
                                    // inside parentheses, while TalkBack/tests
                                    // must be able to address the exact `1/N`.
                                    Text(
                                        "${pagerState.currentPage + 1}/${posts.size.coerceAtLeast(1)}",
                                        maxLines = 1,
                                        fontSize = 13.sp
                                    )
                                    Text(")", fontSize = 13.sp)
                                }
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") } },
                    actions = {
                        Box {
                            IconButton(onClick = { topOverflowOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "その他") }
                            DropdownMenu(
                                expanded = topOverflowOpen,
                                onDismissRequest = { topOverflowOpen = false },
                                shape = RoundedCornerShape(2.dp),
                                containerColor = compatibilityPopupSurface(LocalCompatibilityPalette.current),
                                tonalElevation = 0.dp,
                                shadowElevation = 8.dp
                            ) {
                                DropdownMenuItem(text = { Text(viewerTopOverflowLabels[0]) }, colors = compatibilityMenuItemColors(), onClick = {
                                    topOverflowOpen = false
                                    onOpenSettings()
                                })
                                DropdownMenuItem(
                                    text = { Text(viewerTopOverflowLabels[1]) },
                                    colors = compatibilityMenuItemColors(),
                                    onClick = { topOverflowOpen = false; onToolbarEdit() }
                                )
                                DropdownMenuItem(
                                    text = { Text(viewerTopOverflowLabels[2]) },
                                    colors = compatibilityMenuItemColors(),
                                    onClick = { topOverflowOpen = false; onOpenCommonSettings() }
                                )
                                DropdownMenuItem(
                                    text = { Text(viewerTopOverflowLabels[3]) },
                                    colors = compatibilityMenuItemColors(),
                                    onClick = { topOverflowOpen = false; onOpenHelp() }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            if (chromeVisible) {
                Column(
                    Modifier.fillMaxWidth().background(Color.Black).navigationBarsPadding()
                        .testTag("compat-viewer-bottom-bar")
                ) {
                    Row(Modifier.fillMaxWidth().height(40.dp)) {
                        toolbarItems.filter(CompatToolbarItem::active).sortedBy(CompatToolbarItem::position).forEach { item ->
                            ViewerButton(
                                icon = secondaryToolbarIcon(CompatToolbarSurface.VIEWER, item.key),
                                label = compatToolbarLabel(CompatToolbarSurface.VIEWER, item.key),
                                testTag = "compat-viewer-toolbar-icon-${item.key}",
                                enabled = viewerActions[item.key] != null,
                                onClick = { viewerActions[item.key]?.invoke() }
                            )
                        }
                        ViewerButton(
                            secondaryToolbarIcon(CompatToolbarSurface.VIEWER, "other"),
                            "その他",
                            "compat-viewer-toolbar-icon-other",
                            true
                        ) { toolbarOverflowOpen = true }
                    }
                }
            }
        }
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            // Horizontal navigation is owned by the viewer surface below.
            // The stock pager recognizer can lose the reverse (right) swipe
            // when the current page replaces its thumbnail with the full
            // image.  Keeping the pager programmatic here makes both
            // directions deterministic while retaining pager animation.
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize().padding(padding).graphicsLayer {
                translationY = renderedVerticalOffset
                alpha = (1f - abs(renderedVerticalOffset) / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
            }.pointerInput(posts, canDismissVertically) {
                if (canDismissVertically) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            verticalResetJob?.cancel()
                            verticalDismissAnimating = false
                            verticalVelocityTracker.resetTracking()
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            verticalVelocityTracker.addPosition(change.uptimeMillis, change.position)
                            verticalRawOffset += amount
                        },
                        onDragEnd = {
                            val velocity = verticalVelocityTracker.calculateVelocity().y
                            val viewportHeight = size.height.toFloat()
                            if (shouldDismissCompatViewer(verticalRawOffset, velocity, viewportHeight)) {
                                val direction = if (verticalRawOffset < 0f) -1f else 1f
                                val startOffset = verticalRawOffset
                                verticalDismissAnimating = true
                                verticalResetJob = scope.launch {
                                    Animatable(startOffset).animateTo(
                                        targetValue = direction * viewportHeight,
                                        animationSpec = spring(stiffness = COMPAT_VIEWER_RESET_SPRING_STIFFNESS)
                                    ) { verticalRawOffset = value }
                                    // The APK's dismiss callback runs after the
                                    // dismissal animation reaches the outside.
                                    onBack()
                                }
                            } else {
                                val startOffset = verticalRawOffset
                                verticalResetJob = scope.launch {
                                    Animatable(startOffset).animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(stiffness = COMPAT_VIEWER_RESET_SPRING_STIFFNESS)
                                    ) { verticalRawOffset = value }
                                    verticalDismissAnimating = false
                                }
                            }
                        },
                        onDragCancel = {
                            val startOffset = verticalRawOffset
                            verticalResetJob = scope.launch {
                                Animatable(startOffset).animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(stiffness = COMPAT_VIEWER_RESET_SPRING_STIFFNESS)
                                ) { verticalRawOffset = value }
                                verticalDismissAnimating = false
                            }
                        }
                    )
                }
            }
        ) { page ->
            val post = posts.getOrNull(page)
            val mediaUrl = post?.let(::resolveCompatViewerMediaUrl)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // PlatformVideoPlayer is backed by a native view (PlayerView on
                    // Android and WKWebView on iOS), so it does not inherit the
                    // image page's Compose gesture handler.  Observe the gesture
                    // from this parent at the Initial pass without consuming it:
                    // the player retains its own controls while horizontal drags
                    // still page through mixed image/video attachments.
                    // Restart the recognizer when Pager changes page. Nearby
                    // video pages are composed ahead of time, and otherwise a
                    // page that was initially off-screen would never start its
                    // recognizer after becoming current.
                    .pointerInput(mediaUrl, page, posts.size, pagerState.currentPage) {
                        if (mediaUrl == null || !isCompatVideoMediaUrl(mediaUrl) ||
                            page != pagerState.currentPage
                        ) {
                            return@pointerInput
                        }
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                            )
                            var horizontalDragDistance = 0f
                            var verticalDragDistance = 0f
                            var cancelled = false
                            do {
                                val event = awaitPointerEvent(
                                    pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                )
                                val pointerCount = event.changes.count { it.pressed }
                                if (pointerCount == 0) break
                                if (pointerCount >= 2) {
                                    cancelled = true
                                    break
                                }
                                val pan = event.calculatePan()
                                horizontalDragDistance += pan.x
                                verticalDragDistance += pan.y
                            } while (event.changes.any { it.pressed })

                            if (!cancelled &&
                                kotlin.math.abs(horizontalDragDistance) > kotlin.math.abs(verticalDragDistance)
                            ) {
                                val target = compatViewerSwipeTarget(
                                    currentPage = pagerState.currentPage,
                                    dragDistancePx = horizontalDragDistance,
                                    viewportWidthPx = size.width.toFloat(),
                                    pageCount = posts.size
                                ) ?: return@awaitEachGesture
                                scope.launch { pagerState.animateScrollToPage(target) }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val reloadToken = mediaUrl?.let(viewerReloadTokens::get)
                val reloadSuffix = reloadToken?.let { "#compat-reload=$it" }.orEmpty()
                if (mediaUrl != null && isCompatVideoMediaUrl(mediaUrl)) {
                    val videoCandidates = remember(mediaUrl, switchWebmToMp4) {
                        compatVideoPlaybackCandidates(mediaUrl, switchWebmToMp4)
                    }
                    var videoCandidateIndex by remember(mediaUrl, switchWebmToMp4) { mutableStateOf(0) }
                    var playbackState by remember(mediaUrl, switchWebmToMp4) { mutableStateOf(VideoPlayerState.Buffering) }
                    var playbackError by remember(mediaUrl, switchWebmToMp4) { mutableStateOf<VideoPlaybackError?>(null) }
                    val posterUrl = post?.thumbnailUrl
                        ?.takeIf { it.isNotBlank() && it != mediaUrl }
                    val privacyOverlayAlpha = compatPrivacyOverlayAlpha(
                        enabled = threadPrivacyEnabled,
                        transparency = threadPrivacyAlpha
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlatformVideoPlayer(
                            videoUrl = videoCandidates[videoCandidateIndex.coerceIn(0, videoCandidates.lastIndex)] + reloadSuffix,
                            modifier = Modifier.fillMaxSize(),
                            onStateChanged = { state ->
                                playbackState = state
                                if (state != VideoPlayerState.Error) playbackError = null
                                if (state == VideoPlayerState.Error && videoCandidateIndex < videoCandidates.lastIndex) {
                                    videoCandidateIndex++
                                }
                            },
                            onVideoSizeKnown = { width, height ->
                                if (width > 0 && height > 0) mediaDimensions[mediaUrl] = width to height
                            },
                            onMediaInfoKnown = { info -> videoMediaInfo[mediaUrl] = info },
                            onPlaybackError = { playbackError = it },
                            areControlsVisible = chromeVisible,
                            onControlsVisibilityChanged = { visible -> chromeVisible = visible }
                        )
                        if (posterUrl != null && playbackState == VideoPlayerState.Buffering) {
                            val posterPainter = rememberAsyncImagePainter(
                                model = ImageRequest.Builder(platformContext)
                                    .data(posterUrl)
                                    .crossfade(false)
                                    .build(),
                                imageLoader = imageLoader
                            )
                            Image(
                                painter = posterPainter,
                                contentDescription = post.let { "No.${it.postNo}の動画プレビュー" },
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (privacyOverlayAlpha > 0f) {
                            // PlayerView/AVPlayer/WKWebView are native interop
                            // surfaces and do not consistently inherit a
                            // Compose blur/alpha layer. Draw the legacy
                            // viewer's inverse-alpha privacy veil above the
                            // video (and its buffering poster) instead.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = privacyOverlayAlpha))
                            )
                        }
                    }
                    if (playbackState == VideoPlayerState.Error) {
                        Column(
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.72f)).padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("動画を再生できませんでした", color = Color.White)
                            formatVideoPlaybackError(playbackError)?.let { detail ->
                                Text(
                                    text = detail,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            TextButton(onClick = { openUrl(mediaUrl) }) {
                                Text("外部で開く", color = Color.White)
                            }
                        }
                    }
                } else {
                    CompatViewerImagePage(
                        post = post,
                        mediaUrl = mediaUrl,
                        reloadSuffix = reloadSuffix,
                        viewerTransform = mediaUrl?.let(viewerTransforms::get)
                            ?: CompatViewerTransform(),
                        isCurrentPage = page == pagerState.currentPage,
                        resetKey = if (page == pagerState.currentPage) pagerState.currentPage else -1,
                        privacyEnabled = threadPrivacyEnabled,
                        privacyAlpha = threadPrivacyAlpha,
                        onZoomedChanged = { zoomed ->
                            if (page == pagerState.currentPage) isZoomed = zoomed
                        },
                        onTransformChanged = { scale, translation ->
                            mediaUrl?.let { viewerTransforms[it] = CompatViewerTransform(scale, translation) }
                        },
                        onHorizontalSwipe = { dragDistancePx, viewportWidthPx ->
                            val target = compatViewerSwipeTarget(
                                currentPage = pagerState.currentPage,
                                dragDistancePx = dragDistancePx,
                                viewportWidthPx = viewportWidthPx,
                                pageCount = posts.size
                            ) ?: return@CompatViewerImagePage
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                        onDimensionsKnown = { width, height ->
                            if (mediaUrl != null) {
                                val dimensions = width to height
                                mediaDimensions[mediaUrl] = dimensions
                            }
                        },
                        onClick = { chromeVisible = !chromeVisible },
                        onLongClick = { quickMenu = true }
                    )
                }
            }
        }
    }
    if (quickMenu) {
        CompatLegacyChoiceDialog(
            onDismiss = { quickMenu = false },
            choices = compatViewerQuickMenuLabels(),
            enabled = { it == "検索" || !isSaving },
            testTag = "compat-viewer-quick-menu",
            onChoice = { choice ->
                when (choice) {
                    "保存" -> saveCurrent(shareAfterSave = false)
                    "共有" -> saveCurrent(shareAfterSave = true)
                    "検索" -> searchCurrent()
                }
            }
        )
    }
    if (toolbarOverflowOpen) {
        ViewerToolbarOverflowDialog(
            items = toolbarItems,
            actions = viewerActions,
            currentUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl),
            onCopyUrl = { url -> clipboard.setText(AnnotatedString(url)); message = "URLをコピーしました" },
            onOpenUrl = openUrl,
            onShareUrl = { url -> share(url, "text/plain", null) },
            onShareImage = { saveCurrent(shareAfterSave = true) },
            onDismiss = { toolbarOverflowOpen = false }
        )
    }
    if (infoOpen) {
        val current = posts.getOrNull(pagerState.currentPage)
        val mediaUrl = current?.let(::resolveCompatViewerMediaUrl)
        val mediaInfo = mediaUrl?.let(remoteMediaInfo::get)
        val exifInfo = mediaUrl?.let(remoteExifInfo::get)
        val dimensions = mediaUrl?.let(mediaDimensions::get)
        val videoInfo = mediaUrl?.let(videoMediaInfo::get)
        val isVideo = mediaUrl?.let(::isCompatVideoMediaUrl) == true
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            title = { Text(if (isVideo) "動画情報" else "画像情報") },
            text = {
                Column {
                    Text("${pagerState.currentPage + 1} / ${posts.size}")
                    current?.let { post ->
                        Text("No.${post.postNo}")
                        Text("ファイル: ${mediaUrl?.substringAfterLast('/')?.substringBefore('?').orEmpty()}")
                        Text("形式: ${mediaInfo?.contentType ?: mediaUrl?.substringAfterLast('.', "不明")?.substringBefore('?')?.uppercase()}")
                        Text("サイズ: ${formatCompatMediaByteSize(mediaInfo?.contentLengthBytes)}")
                        if (isVideo) {
                            val resolvedVideoInfo = videoInfo ?: dimensions?.let { (width, height) ->
                                VideoMediaInfo(width = width, height = height)
                            }
                            val technicalLines = resolvedVideoInfo?.let(::formatVideoMediaInfoLines).orEmpty()
                            if (technicalLines.none { it.startsWith("解像度:") }) {
                                Text("解像度: 不明")
                            }
                            technicalLines.forEach { Text(it) }
                        } else {
                            Text("解像度: ${dimensions?.let { "${it.first} × ${it.second}" } ?: "不明"}")
                            Text("Exif:\n${exifInfo ?: if (infoLoading) "取得中…" else "未取得"}")
                        }
                        Text(mediaUrl.orEmpty())
                        if (infoLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { infoOpen = false }) { Text("閉じる") } }
        )
    }
    message?.let { current ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(current) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
    reverseSearchResult?.let { result ->
        CompatReverseImageSearchScreen(
            result = result,
            cookieRepository = cookieRepository,
            onClose = { reverseSearchResult = null },
            onOpenExternal = openUrl
        )
    }
}

/**
 * Image content for one viewer page.  Zoom state intentionally lives here,
 * instead of in CompatViewerScreen: a pointer event must update only the
 * image layer, not the whole Scaffold and HorizontalPager.
 */
@Composable
private fun CompatViewerImagePage(
    post: CompatPostSnapshot?,
    mediaUrl: String?,
    reloadSuffix: String,
    viewerTransform: CompatViewerTransform,
    isCurrentPage: Boolean,
    resetKey: Int,
    privacyEnabled: Boolean,
    privacyAlpha: Float,
    onZoomedChanged: (Boolean) -> Unit,
    onTransformChanged: (Float, Offset) -> Unit,
    onHorizontalSwipe: (Float, Float) -> Unit,
    onDimensionsKnown: (Int, Int) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val platformContext = LocalPlatformContext.current
    val imageLoader = LocalFutachaImageLoader.current
    var localTransform by remember(mediaUrl) { mutableStateOf(viewerTransform) }
    var gestureActive by remember(mediaUrl) { mutableStateOf(false) }
    val zoomScale = localTransform.scale
    val zoomTranslation = localTransform.translation
    val latestViewerTransform by rememberUpdatedState(localTransform)
    var displayMode by remember(mediaUrl) { mutableStateOf(0) }
    val thumbnailUrl = post?.thumbnailUrl
        ?.takeIf { it.isNotBlank() && it != mediaUrl }
    LaunchedEffect(resetKey, mediaUrl) {
        displayMode = 0
    }
    LaunchedEffect(viewerTransform, mediaUrl) {
        if (!gestureActive) {
            localTransform = viewerTransform
        }
    }

    // Use the layout constraints as the decode bound. Updating a State from
    // onSizeChanged during HorizontalPager measurement can race Pager's own
    // snapshot and crash with "Unsupported concurrent change during
    // composition" on real images. BoxWithConstraints provides the same
    // viewport size without a write from the layout phase.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportSize = with(density) {
            IntSize(
                maxWidth.roundToPx().coerceAtLeast(1),
                maxHeight.roundToPx().coerceAtLeast(1)
            )
        }
        val requestSize = viewportSize
        val thumbnailRequest = remember(platformContext, mediaUrl, thumbnailUrl, requestSize, reloadSuffix) {
            val url = (thumbnailUrl ?: mediaUrl)?.plus(reloadSuffix)
            url?.let {
                ImageRequest.Builder(platformContext)
                    .data(it)
                    .compatImageFallbackPolicy()
                    .crossfade(false)
                    .size(requestSize.width, requestSize.height)
                .build()
            }
        }
        // The reference APK passes the source URL to its viewer and lets the
        // image loader decode that source without a thumbnail-sized request.
        // Do the same here.  A thumbnail is retained only as an error
        // fallback; it must never be the normal first frame of the viewer.
        val sourceRequest = remember(
            platformContext,
            mediaUrl,
            reloadSuffix
        ) {
            if (mediaUrl == null) {
                null
            } else {
                ImageRequest.Builder(platformContext)
                    .data(mediaUrl + reloadSuffix)
                    .compatImageFallbackPolicy()
                    .crossfade(false)
                    // Size.ORIGINAL is intentional.  A viewport-sized decode
                    // is visibly soft after the first zoom and was the reason
                    // the old implementation appeared to become sharp only
                    // after a gesture caused another request.
                    .size(Size.ORIGINAL)
                    .build()
            }
        }
        val thumbnailPainter = rememberAsyncImagePainter(
            model = thumbnailRequest,
            imageLoader = imageLoader
        )
        val sourcePainter = rememberAsyncImagePainter(
            model = sourceRequest,
            imageLoader = imageLoader
        )
        val thumbnailState by thumbnailPainter.state.collectAsState()
        val sourceState by sourcePainter.state.collectAsState()
        val sourceReady = sourceState is coil3.compose.AsyncImagePainter.State.Success
        val sourceFailed = sourceState is coil3.compose.AsyncImagePainter.State.Error
        val sourceFailureDetail = formatMediaLoadFailure(
            (sourceState as? coil3.compose.AsyncImagePainter.State.Error)?.result?.throwable
        )
        val thumbnailFallbackReady = thumbnailState is coil3.compose.AsyncImagePainter.State.Success
        val loadPresentation = resolveCompatViewerLoadPresentation(
            hasSource = sourceRequest != null,
            sourceReady = sourceReady,
            sourceFailed = sourceFailed,
            hasThumbnailFallback = thumbnailUrl != null,
            thumbnailReady = thumbnailFallbackReady,
            thumbnailFailed = thumbnailState is coil3.compose.AsyncImagePainter.State.Error
        )
        val showingThumbnailFallback =
            loadPresentation == CompatViewerLoadPresentation.THUMBNAIL_FALLBACK
        val isLoading = loadPresentation == CompatViewerLoadPresentation.LOADING

        LaunchedEffect(sourceState, thumbnailState, mediaUrl) {
            val painter = if (sourceReady) sourcePainter else thumbnailPainter
            val size = painter.intrinsicSize
            if (mediaUrl != null && size.width.isFinite() && size.height.isFinite() &&
                size.width > 0f && size.height > 0f
            ) {
                // Parent metadata is a non-rendering cache. Keep the callback
                // outside the image draw path so the Pager can continue to own
                // gesture and page composition.
                onDimensionsKnown(size.width.toInt(), size.height.toInt())
            }
        }

        // Render the active bitmap through exactly one transform. Keeping one
        // Image node avoids replacing a transformed thumbnail with a second
        // transformed node when the original finishes loading.
        fun imageRenderModifier(alpha: Float = 1f): Modifier = Modifier
            .fillMaxSize()
            .compatPrivacyImageEffect(compatPrivacyRenderAlpha(privacyEnabled, privacyAlpha))
            .graphicsLayer {
                // graphicsLayer's lambda is snapshot-aware and updates the
                // render layer without recomposing the pager for every move.
                if (isCurrentPage) {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = zoomTranslation.x
                    translationY = zoomTranslation.y
                }
                this.alpha = alpha
            }

        val imageInteractionModifier = Modifier
            .fillMaxSize()
            .then(
                if (isCurrentPage) Modifier.testTag("compat-viewer-image-page") else Modifier
            )
            .pointerInput(mediaUrl, isCurrentPage, resetKey) {
                if (isCurrentPage) {
                    awaitEachGesture {
                        awaitFirstDown(
                            requireUnconsumed = false,
                            pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                        )
                        // Keep a gesture-local copy as well as the screen-owned
                        // state. The parent callback may recompose the Pager
                        // only after the current pointer batch has completed;
                        // rereading the captured value for every event would
                        // otherwise apply every pinch delta to scale=1 and
                        // lose the transform before the next frame.
                        var gestureTransform = latestViewerTransform
                        gestureActive = true
                        var ownsGesture = gestureTransform.scale > COMPAT_VIEWER_ZOOM_GESTURE_THRESHOLD
                        var lastWasZoomed = gestureTransform.scale > COMPAT_VIEWER_ZOOM_GESTURE_THRESHOLD
                        var horizontalDragDistance = 0f
                        var verticalDragDistance = 0f
                        do {
                            val event = awaitPointerEvent(
                                pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                            )
                            val pointerCount = event.changes.count { it.pressed }
                            // The final up event has no valid centroid. Do
                            // not feed it through the transform calculation;
                            // doing so turns an otherwise valid translation
                            // into Offset.Zero just before the gesture ends.
                            if (pointerCount == 0) break
                            if (pointerCount >= 2) {
                                ownsGesture = true
                            }
                            if (ownsGesture) {
                                val gestureZoom = if (pointerCount >= 2) event.calculateZoom() else 1f
                                val pan = if (pointerCount >= 2) {
                                    event.calculatePan()
                                } else {
                                    event.changes
                                        .firstOrNull { it.pressed }
                                        ?.positionChangeIgnoreConsumed()
                                        ?: Offset.Zero
                                }
                                val currentTransform = gestureTransform
                                val oldScale = currentTransform.scale
                                val updatedScale =
                                    (oldScale * gestureZoom).coerceIn(1f, COMPAT_VIEWER_MAX_ZOOM)
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val updatedTranslation = if (updatedScale <= COMPAT_VIEWER_ZOOM_GESTURE_THRESHOLD) {
                                    Offset.Zero
                                } else {
                                    val translation = compatViewerZoomTranslation(
                                        currentX = currentTransform.translation.x,
                                        currentY = currentTransform.translation.y,
                                        panX = pan.x,
                                        panY = pan.y,
                                        centroidX = centroid.x,
                                        centroidY = centroid.y,
                                        viewportWidthPx = viewportSize.width.toFloat(),
                                        viewportHeightPx = viewportSize.height.toFloat(),
                                        oldScale = oldScale,
                                        newScale = updatedScale
                                    )
                                    Offset(translation.first, translation.second)
                                }
                                gestureTransform = CompatViewerTransform(updatedScale, updatedTranslation)
                                // Keep the rendered transform local to the
                                // page for the duration of the gesture. A
                                // parent Pager recomposition for every move
                                // can otherwise race measure/layout and paint
                                // the old centred transform for a frame.
                                localTransform = gestureTransform
                                val nowZoomed = updatedScale > COMPAT_VIEWER_ZOOM_GESTURE_THRESHOLD
                                if (nowZoomed != lastWasZoomed) {
                                    lastWasZoomed = nowZoomed
                                    onZoomedChanged(nowZoomed)
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                val pan = event.calculatePan()
                                horizontalDragDistance += pan.x
                                verticalDragDistance += pan.y
                            }
                        } while (event.changes.any { it.pressed })
                        gestureActive = false
                        if (ownsGesture) {
                            onTransformChanged(gestureTransform.scale, gestureTransform.translation)
                        }
                        if (!ownsGesture &&
                            kotlin.math.abs(horizontalDragDistance) > kotlin.math.abs(verticalDragDistance) &&
                            viewportSize.width > 0
                        ) {
                            onHorizontalSwipe(horizontalDragDistance, viewportSize.width.toFloat())
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = {
                    displayMode = (displayMode + 1) % 3
                    // Double-tap changes the APK-compatible display mode.
                    // Do not reset the independent pinch transform here: the
                    // old reset sent a panned image back to the centre every
                    // time the user switched between fit/width/original.
                },
                onLongClick = onLongClick
            )
            .semantics {
                if (isCurrentPage) {
                    stateDescription = "拡大率 ${(zoomScale * 100f).toInt()}% " +
                        "位置 ${(zoomTranslation.x).toInt()},${(zoomTranslation.y).toInt()}"
                }
            }

        // Keep pointer handling on one transparent parent. The active painter
        // below changes from preview to original without changing this node,
        // so gestures and the translation survive image replacement.
        Box(modifier = imageInteractionModifier) {
            val contentScale = when (displayMode) {
                1 -> ContentScale.FillWidth
                2 -> ContentScale.None
                else -> ContentScale.Fit
            }
            // Do not paint the thumbnail while the source request is merely
            // loading.  It is the exact visual failure reported on AQUOS:
            // opening the viewer showed a small bitmap, then a pinch caused
            // the source to be decoded and suddenly looked sharp.  The source
            // is the only normal painter; the thumbnail is an error fallback.
            val activePainter = if (showingThumbnailFallback) thumbnailPainter else sourcePainter
            Image(
                painter = activePainter,
                contentDescription = post?.let { "No.${it.postNo}の画像" },
                contentScale = contentScale,
                modifier = imageRenderModifier().testTag(
                    when {
                        sourceReady -> "compat-viewer-source-ready"
                        showingThumbnailFallback -> "compat-viewer-thumbnail-fallback"
                        else -> "compat-viewer-source-loading"
                    }
                )
            )
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (loadPresentation == CompatViewerLoadPresentation.ERROR) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "画像を読み込めませんでした",
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    sourceFailureDetail?.let { detail ->
                        Text(
                            text = detail,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private fun compatToolbarLabel(surface: CompatToolbarSurface, key: String): String =
    compatToolbarMaster(surface).firstOrNull { it.key == key }?.label ?: key

private fun secondaryToolbarIcon(
    surface: CompatToolbarSurface,
    key: String
): CompatToolbarArtwork = compatToolbarArtwork(surface, key)

@Composable
private fun SecondaryToolbarOverflowPopup(
    surface: CompatToolbarSurface,
    items: List<CompatToolbarItem>,
    actions: Map<String, () -> Unit>,
    onDismiss: () -> Unit
) {
    val inactive = items.sortedBy(CompatToolbarItem::position).filterNot(CompatToolbarItem::active)
    CompatBottomPopup(
        alignment = Alignment.BottomEnd,
        testTag = "compat-post-toolbar-overflow-popup",
        onDismiss = onDismiss
    ) {
        if (inactive.isEmpty()) Text("ツールバー外の操作はありません")
        inactive.forEach { item ->
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                enabled = actions[item.key] != null,
                onClick = {
                    onDismiss()
                    actions[item.key]?.invoke()
                }
            ) {
                // sample/1.apk inflates a native PopupMenu here.  Its rows are
                // text-only; placing the 192px toolbar artwork in this menu
                // made every row and the whole popup several times too large.
                Text(
                    text = compatToolbarLabel(surface, item.key),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun ViewerToolbarOverflowDialog(
    items: List<CompatToolbarItem>,
    actions: Map<String, () -> Unit>,
    currentUrl: String?,
    onCopyUrl: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onShareImage: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    // viewer_toolbar_overflow.xml is a fixed menu.  It contains actions even
    // when the same action is currently present in the bottom toolbar.
    val candidates = listOf(
        "copy_url", "browser", "share_url", "share", "privacy", "reload",
        "download", "search", "back", "gallery", "info", "screen"
    )
    val labels = mapOf(
        "copy_url" to "URLコピー",
        "browser" to "ブラウザーで開く",
        "share_url" to "URLを共有",
        "share" to "画像を共有",
        "privacy" to "プライバシー",
        "reload" to "再読み込み"
    )
    // Keep the viewer overflow above its 40dp bottom toolbar and the system
    // navigation inset; the old 60dp value was clipped/overlapped on Android
    // 11 devices (#33).
    val bottomInset = with(LocalDensity.current) { 96.dp.roundToPx() }
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, -bottomInset),
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismiss
    ) {
        Surface(
            color = compatibilityPopupSurface(palette),
            contentColor = compatibilityPopupContent(palette),
            tonalElevation = 0.dp,
            shadowElevation = 8.dp
        ) {
            LazyColumn(
                modifier = Modifier.width(200.dp).heightIn(max = 560.dp)
            ) {
                items(candidates) { key ->
                    val enabled = when (key) {
                        "copy_url", "browser", "share_url", "share" -> currentUrl != null
                        "reload" -> currentUrl != null && actions["reload"] != null
                        else -> actions[key] != null
                    }
                    TextButton(
                        enabled = enabled,
                        onClick = {
                            onDismiss()
                            when (key) {
                                "copy_url" -> currentUrl?.let(onCopyUrl)
                                "browser" -> currentUrl?.let(onOpenUrl)
                                "share_url" -> currentUrl?.let(onShareUrl)
                                "share" -> onShareImage()
                                else -> actions[key]?.invoke()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        CompatToolbarArtworkIcon(
                            artwork = secondaryToolbarIcon(CompatToolbarSurface.VIEWER, key),
                            contentDescription = null,
                            tint = LocalContentColor.current
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            labels[key] ?: compatToolbarLabel(CompatToolbarSurface.VIEWER, key),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.ViewerButton(
    icon: CompatToolbarArtwork,
    label: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick, enabled = enabled) {
            CompatToolbarArtworkIcon(
                artwork = icon,
                contentDescription = label,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(24.dp).testTag(testTag)
            )
        }
    }
}
