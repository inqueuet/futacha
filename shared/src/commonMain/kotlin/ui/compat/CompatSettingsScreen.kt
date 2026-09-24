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

private val SecondaryAccent: Color @Composable get() =
    compatibilitySettingsCategoryColor(LocalCompatibilityPalette.current)

internal data class CompatSettingEntry(
    val title: String,
    val summary: String = "タップして設定",
    val route: String? = null,
    val enabled: Boolean = true,
    val preferenceKey: String = title
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
    onScrollPositionChanged: (Pair<Int, Int>) -> Unit = {},
    modernPresentation: Boolean = false
) {
    val profileController = LocalExperienceProfileUiController.current
    val scope = rememberCoroutineScope()
    val groups = remember(path, appVersion, modernPresentation) {
        if (path == "backup" && modernPresentation) {
            listOf("バックアップ" to (compatRootSettingsGroups(appVersion).first { it.first == "バックアップ" }.second +
                CompatSettingEntry("旧版設定・NGの復元", "keyword.cfg / setting.cfg を読み込む")))
        } else if (path == "ptmt" && modernPresentation) {
            listOf("Cookie" to listOf(CompatSettingEntry("ptmtクッキーの編集", "", preferenceKey = "ptmtEditor")))
        } else if (path == "root") compatRootSettingsGroups(appVersion) else compatSettingsGroups(path, modernPresentation)
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
    fun launchSettingsSafely(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                Logger.e("CompatSettings", "Settings operation failed", failure)
                transientNotice = "操作に失敗しました: ${failure.message.orEmpty()}"
            }
        }
    }
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
            launchSettingsSafely {
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
            launchSettingsSafely {
                store.savePreference(compatPreferenceStorageKey("storage", "dummyDownloadDir"), value)
            }
        }
    )
    val drawingDirectoryPicker = rememberDirectoryPickerLauncher(
        onDirectorySelected = { location ->
            val value = location.toRawString()
            savedValues = savedValues + ("dummyDrawingDir" to value)
            launchSettingsSafely {
                store.savePreference(compatPreferenceStorageKey("storage", "dummyDrawingDir"), value)
            }
        }
    )
    val backupDirectoryPicker = rememberDirectoryPickerLauncher(
        onDirectorySelected = { location ->
            if (backupInProgress) return@rememberDirectoryPickerLauncher
            backupInProgress = true
            launchSettingsSafely {
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
                launchSettingsSafely {
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
        if (path in setOf("root", "backup", "ptmt")) {
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
                    val normalBytes = imageLoader.originalMediaCacheSizeBytes() + (imageLoader.diskCache?.size ?: 0L) +
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
                title = { Text(if (path == "root") "設定" else if (path == "backup") "バックアップ・復元" else if (path == "design" && modernPresentation) "フォント・タブ一覧" else path.compatSettingsTitle(), modifier = Modifier.padding(start = 16.dp)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "戻る") } },
                actions = {
                    if (path == "root") {
                        Row {
                            Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                IconButton(onClick = onOpenChangeLog) {
                                    Icon(Icons.Filled.History, contentDescription = "更新情報", tint = SecondaryChromeContent)
                                }
                            }
                            Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                IconButton(onClick = {
                                    openUrl(compatCurrentStoreUrl())
                                }) {
                                    Icon(Icons.Filled.BusinessCenter, contentDescription = "ストア", tint = SecondaryChromeContent)
                                }
                            }
                            Box(Modifier.width(56.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                IconButton(onClick = onOpenHelp) {
                                    Icon(Icons.Filled.HelpOutline, contentDescription = "ヘルプ", tint = SecondaryChromeContent)
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryTeal,
                    titleContentColor = SecondaryChromeContent,
                    navigationIconContentColor = SecondaryChromeContent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            state = settingsListState,
            modifier = Modifier.fillMaxSize().padding(padding).testTag("compat-settings-list-$path")
        ) {
            if (path == "media") item(key = "media-settings") {
                com.valoser.futacha.shared.ui.media.DeviceImageEditorSettings()
                HorizontalDivider()
                com.valoser.futacha.shared.ui.media.DeviceVideoEditorSettings()
                HorizontalDivider()
                com.valoser.futacha.shared.ui.media.MediaHelpButton()
            }
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
                    val isSettingsRestore = path in setOf("root", "backup") && entry.title == "基本的な設定の復元"
                    val isSettingsSave = path in setOf("root", "backup") && entry.title == "基本的な設定の保存"
                    val isNgRestore = path in setOf("root", "backup") && entry.title == "監視･ＮＧワードの復元"
                    val isNgSave = path in setOf("root", "backup") && entry.title == "監視･ＮＧワードの保存"
                    val backupDateKey = when {
                        isSettingsRestore -> COMPAT_BACKUP_SETTING_IMPORT_DATE_KEY
                        isSettingsSave -> COMPAT_BACKUP_SETTING_EXPORT_DATE_KEY
                        isNgRestore -> COMPAT_BACKUP_KEYWORD_IMPORT_DATE_KEY
                        isNgSave -> COMPAT_BACKUP_KEYWORD_EXPORT_DATE_KEY
                        else -> null
                    }
                    val isLegacyRestore = path in setOf("root", "backup") && entry.title == "旧版設定・NGの復元"
                    val isPtmtEditor = path in setOf("root", "backup", "ptmt") && entry.preferenceKey == "ptmtEditor"
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
                            launchSettingsSafely {
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
                                launchSettingsSafely {
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
                                    launchSettingsSafely { store.savePreference(COMPAT_CACHE_ENABLED_KEY, "OFF") }
                                }
                            }
                            else -> {
                                savedValues = savedValues + (entry.preferenceKey to if (next) "ON" else "OFF")
                                if (path == "design" && entry.preferenceKey == "designNavigationBar") {
                                    transientNotice = "画面の再描画時に反映されます"
                                }
                                launchSettingsSafely {
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
                                "OFF・通常通知を停止（緊急更新は表示）"
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
                                launchSettingsSafely {
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
                                    launchSettingsSafely {
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
                        launchSettingsSafely {
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
                        launchSettingsSafely {
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
                        launchSettingsSafely {
                            val usage = withContext(AppDispatchers.io) {
                                runCatching {
                                    clearFutachaImageCaches(imageLoader, catalogImageLoader)
                                }
                                runCatching {
                                    val normalBytes = imageLoader.originalMediaCacheSizeBytes() + (imageLoader.diskCache?.size ?: 0L) +
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
                        launchSettingsSafely {
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
                    launchSettingsSafely {
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
                        launchSettingsSafely {
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
                launchSettingsSafely { store.savePreference(COMPAT_CACHE_ENABLED_KEY, "ON") }
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
                                        launchSettingsSafely {
                                            if (cacheLocationEntry) cacheLocationChangeInProgress = true
                                            runSuspendCatchingPreservingCancellation {
                                                applyCompatCacheLocationChange(
                                                    preferenceKey = entry.preferenceKey,
                                                    storedValue = storedValue,
                                                    clearOrdinaryImageCache = {
                                                        withContext(AppDispatchers.io) {
                                                            imageLoader.memoryCache?.clear()
                                                            clearFutachaImageCaches(imageLoader, clearMemory = false)
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
                                        color = LocalCompatibilityPalette.current.uiSecondaryText
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
                            launchSettingsSafely {
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
                                launchSettingsSafely {
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
                        launchSettingsSafely {
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
                        openUrl(compatCurrentStoreUrl())
                    }) {
                        Icon(Icons.Filled.BusinessCenter, contentDescription = "ストア")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecondaryTeal,
                    titleContentColor = SecondaryChromeContent,
                    navigationIconContentColor = SecondaryChromeContent,
                    actionIconContentColor = SecondaryChromeContent
                )
            )
        }
    ) { padding ->
        SearchableHelpContent(
            html = helpHtml,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("help-screen"),
            onLinkClicked = openUrl
        )
    }
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
            Text(title, fontSize = 16.sp, color = if (enabled) palette.uiPrimaryText else palette.uiSecondaryText)
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    fontSize = 12.sp,
                    color = palette.uiSecondaryText
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
                    uncheckedColor = palette.uiPrimaryText,
                    checkmarkColor = SecondaryChromeContent
                )
            )
        }
    }
}
