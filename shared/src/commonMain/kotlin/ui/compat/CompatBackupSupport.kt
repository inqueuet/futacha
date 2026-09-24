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

internal const val COMPAT_BACKUP_SETTING_IMPORT_DATE_KEY = "compat.root.backupSettingImportDate"
internal const val COMPAT_BACKUP_SETTING_EXPORT_DATE_KEY = "compat.root.backupSettingExportDate"
internal const val COMPAT_BACKUP_KEYWORD_IMPORT_DATE_KEY = "compat.root.backupKeywordImportDate"
internal const val COMPAT_BACKUP_KEYWORD_EXPORT_DATE_KEY = "compat.root.backupKeywordExportDate"

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

/**
 * Imports the Base64(JSON) files written by the original toshiaki(仮) APK.
 * The selected location is a directory because the APK exported two files
 * with fixed names: keyword.cfg and setting.cfg. Missing one is fine; this
 * lets users restore just their NG/watch data.
 */
internal suspend fun importCompatLegacyBackup(
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

internal suspend fun importCompatLegacyBackupData(
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

internal suspend fun FileSystem.readCompatBackupTextWithLimit(
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
