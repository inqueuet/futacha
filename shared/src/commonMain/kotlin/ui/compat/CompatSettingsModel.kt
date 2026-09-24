@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    kotlin.time.ExperimentalTime::class
)

package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ui.image.rememberGenerationMetadata
import com.valoser.futacha.shared.ui.image.PromptAiBadge
import com.valoser.futacha.shared.ui.image.PromptInfoAction

import com.valoser.futacha.shared.ui.image.clearFutachaImageCaches
import com.valoser.futacha.shared.ui.image.originalMediaCacheSizeBytes

import com.valoser.futacha.shared.ui.image.rememberViewerImagePainter

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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
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
import com.valoser.futacha.shared.util.Logger
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

private const val COMPAT_REFERENCE_DATABASE_VERSION = 26

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

internal const val COMPAT_IMAGE_SEARCH_DESCRIPTION =
    "File方式は画像そのものを送り、結果をアプリ内蔵ブラウザで表示します。" +
        "サーバから画像が消えた落ちスレでも検索できます。\n" +
        "URL方式は画像のURLを外部ブラウザへ渡します。" +
        "落ちスレやZIPスレでは検索できません。"

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
        CompatSettingEntry("巡回管理", "キーワード・自動巡回・通知", "watcher"),
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
        CompatSettingEntry("メディア機能", "画像編集、動画編集", "media"),
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
        if (com.valoser.futacha.shared.util.isDesktop()) "未設定時：アプリのデータ保存先" else if (com.valoser.futacha.shared.util.isAndroid()) "未設定時：保存時にフォルダを選択" else "未設定時：ファイル > このiPhone内 > futacha"
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
    "media" -> "メディア機能"
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
        CompatSettingEntry("保存ファイル", if (com.valoser.futacha.shared.util.isDesktop()) "未設定時：アプリのデータ保存先" else if (com.valoser.futacha.shared.util.isAndroid()) "未設定時：保存時にフォルダを選択" else "未設定時：ファイル > このiPhone内 > futacha", preferenceKey = "dummyDownloadDir"),
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
        CompatSettingEntry(
            "あぷ小のサムネイルの読み込み",
            COMPAT_DEFAULT_APU_SMALL_THUMB_METHOD,
            preferenceKey = "threadUpsThumbMethod"
        ),
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
internal fun compatSettingsGroups(path: String, modernPresentation: Boolean = false): List<Pair<String, List<CompatSettingEntry>>> {
    val entries = path.compatSettingsEntries().filterNot {
        modernPresentation && path == "design" &&
            it.preferenceKey in setOf("designTheme", "designTextColor", "designNavigationBar")
    }
    return when (path) {
        "media" -> emptyList()
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
