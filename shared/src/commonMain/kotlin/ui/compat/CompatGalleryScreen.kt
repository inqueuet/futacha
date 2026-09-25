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
import com.valoser.futacha.shared.compat.toCompatPlainTextCached
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

@Composable
internal fun CompatGalleryScreen(
    tab: CompatTab,
    initialIndex: Int = 0,
    initialPostNo: String? = null,
    preparedSnapshot: com.valoser.futacha.shared.compat.CompatThreadSnapshot? = null,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState = rememberLazyGridState(),
    restoreInitialPosition: Boolean = true,
    store: CompatibilityStore,
    preferences: Map<String, String>,
    ngRules: List<CompatNgRule>,
    httpClient: HttpClient?,
    apngMarkerCache: CompatApngMarkerCache,
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
    var posts by remember(tabKey) { mutableStateOf<List<CompatPostSnapshot>>(emptyList()) }
    var snapshotRevision by remember(tabKey) { mutableStateOf(tab.snapshotRevision) }
    var saveMode by remember { mutableStateOf(false) }
    var selectedMediaKeys by remember(tabKey) { mutableStateOf<Set<String>>(emptySet()) }
    var batchSaveFormatDialog by remember { mutableStateOf(false) }
    val batchProtectionProgress = remember { kotlinx.coroutines.flow.MutableStateFlow<SaveProgress?>(null) }
    val batchSaveProgress by batchProtectionProgress.collectAsState()
    var batchSaveCancelRequested by remember { mutableStateOf(false) }
    var batchSaveJob by remember { mutableStateOf<Job?>(null) }
    var savingMediaKey by remember { mutableStateOf<String?>(null) }
    var failedBatchMediaKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastBatchSaveFormat by remember { mutableStateOf<CompatGalleryBatchSaveFormat?>(null) }
    var batchRetryAttempt by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    fun launchScreenAction(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job =
        scope.launchCompatScreenAction("CompatGallery", { failure ->
            message = failure.toCompatUserMessage("操作に失敗しました")
        }, block)
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
    val withSaveDestination = rememberCompatManualSaveDestinationLauncher(store, preferences) {
        message = it.toCompatUserMessage("保存先の設定を記録できませんでした")
    }
    var lastSavedFile by remember { mutableStateOf<Pair<com.valoser.futacha.shared.service.SavedMediaFile, SaveLocation?>?>(null) }
    val manualSaveLocation = parseCompatSaveLocation(
        preferences.compatPreferenceValue("storage", "dummyDownloadDir", "保存ファイルの保存先")
    )
    val upsThumbnailMethod = preferences.compatPreferenceValue(
        "thread", "threadUpsThumbMethod", "あぷ小のサムネイルの読み込み", "あぷ小の読み込み"
    ) ?: COMPAT_DEFAULT_APU_SMALL_THUMB_METHOD
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
        preparedSnapshot,
        ngRules,
        upsThumbnailMethod,
        wifiConnected,
        imageNgPhashThreshold,
        imageNgPhashRules,
        httpClient,
        showDeletedContent
    ) {
        try {
            val hiddenImages = ngRules.asSequence()
                .filter { it.kind == CompatNgKind.THREAD_IMAGE && it.appliesToThreadImage(tab.boardKey, tabKey) }
                .mapTo(mutableSetOf(), CompatNgRule::normalizedValue)
            val snapshot = preparedSnapshot ?: store.loadThreadSnapshot(tabKey)?.let {
                withContext(AppDispatchers.parsing) { normalizeCompatThreadSnapshot(it) }
            }
            snapshotRevision = snapshot?.revision ?: tab.snapshotRevision
            val rawPosts = presentCompatPostsForDeletedVisibilityOffMain(
                posts = snapshot?.posts.orEmpty(),
                showDeletedContent = showDeletedContent
            )
            // Open with the hashes that are already known and hide further
            // pHash matches as their originals are hashed, instead of keeping
            // the spinner up until up to 256 originals have been fetched.
            collectCompatImagePhashHiddenPostNos(
                httpClient = httpClient,
                store = store,
                posts = rawPosts,
                rules = imageNgPhashRules,
                threshold = imageNgPhashThreshold
            ) { hiddenPostNos ->
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatGallery", "Media list load failed", failure)
            message = "画像一覧を読み込めませんでした"
        }
    }
    // pHash matches are hidden progressively after the list first appears;
    // restore the launch position once instead of jumping back on each update.
    var initialPositionRestored by remember(tabKey, initialIndex, initialPostNo) { mutableStateOf(false) }
    LaunchedEffect(posts.size, initialIndex, initialPostNo) {
        if (!restoreInitialPosition || initialPositionRestored) return@LaunchedEffect
        if (posts.isNotEmpty()) {
            val target = compatViewerInitialPage(posts, initialPostNo, initialIndex)
            withFrameNanos { }
            gridState.scrollToItem(target)
            initialPositionRestored = true
        }
    }
    LaunchedEffect(posts) {
        val availableKeys = posts.mapTo(mutableSetOf(), ::compatMediaIdentity)
        selectedMediaKeys = selectedMediaKeys.intersect(availableKeys)
    }
    fun savePostNow(post: CompatPostSnapshot, selectedLocation: SaveLocation?) {
        if (savingMediaKey != null || batchSaveJob != null) return
        launchScreenAction {
            val saver = mediaSaver
            if (saver == null) {
                message = "保存機能を初期化できませんでした"
                return@launchScreenAction
            }
            val key = compatMediaIdentity(post)
            val mediaUrl = resolveCompatViewerMediaUrl(post)
            if (mediaUrl == null) {
                message = "保存するメディアがありません"
                return@launchScreenAction
            }
            savingMediaKey = key
            try {
                message = saver.saveMedia(
                    mediaUrl,
                    tab.boardKey,
                    tab.threadNo,
                    baseSaveLocation = selectedLocation,
                    storageDirectoryOverride = "",
                    useTypeSubdirectory = false
                ).fold(
                    onSuccess = {
                        lastSavedFile = it to selectedLocation
                        compatMediaSaveCompletionMessage(it, requireNotNull(fileSystem), selectedLocation)
                    },
                    onFailure = { it.toCompatUserMessage("メディアを保存できませんでした") }
                )
            } finally {
                savingMediaKey = null
            }
        }
    }
    fun savePost(post: CompatPostSnapshot) {
        withSaveDestination { savePostNow(post, it) }
    }
    fun startBatchSaveNow(
        targets: List<CompatPostSnapshot>,
        format: CompatGalleryBatchSaveFormat,
        isRetry: Boolean,
        selectedLocation: SaveLocation?
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
        batchSaveJob = launchScreenAction {
            val failedUrls = mutableSetOf<String>()
            var succeeded = 0
            try {
                com.valoser.futacha.shared.service.runProtectedThreadSave(tab.title, batchProtectionProgress) {
                    when (format) {
                        CompatGalleryBatchSaveFormat.ZIP -> {
                            val saver = mediaZipSaver ?: error("ZIP保存機能を初期化できませんでした")
                            val result = saver.save(
                                mediaUrls = targetByUrl.map { it.first },
                                boardId = tab.boardKey,
                                threadId = tab.threadNo,
                                baseSaveLocation = selectedLocation,
                                baseDirectory = MANUAL_SAVE_DIRECTORY,
                                fileNameSuffix = batchRetryAttempt.takeIf { isRetry }?.let { "retry$it" },
                                onProgress = { current, total, item, itemBytes, itemTotalBytes ->
                                    batchProtectionProgress.value = SaveProgress(
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
                                batchProtectionProgress.value = SaveProgress(SavePhase.DOWNLOADING, index, targetByUrl.size, item)
                                saver.saveMedia(
                                    url,
                                    tab.boardKey,
                                    tab.threadNo,
                                    baseSaveLocation = selectedLocation,
                                    baseDirectory = MANUAL_SAVE_DIRECTORY,
                                    storageDirectoryOverride = folder,
                                    useTypeSubdirectory = false,
                                    outputFileNameOverride = outputNames[url],
                                    onProgress = { itemBytes, itemTotalBytes ->
                                        batchProtectionProgress.value = SaveProgress(
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
                                batchProtectionProgress.value = SaveProgress(SavePhase.DOWNLOADING, index + 1, targetByUrl.size, item)
                            }
                            message = buildCompatGalleryBatchSaveMessage(format, succeeded, failedUrls.size)
                        }
                    }
                }
                message = message.orEmpty() + "\n保存先: " + manualSaveDestinationLabel(requireNotNull(fileSystem), selectedLocation)
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
                throw cancelled
            } catch (failure: Throwable) {
                failedBatchMediaKeys = targetByUrl.mapTo(mutableSetOf()) { compatMediaIdentity(it.second) }
                message = failure.toCompatUserMessage("一括保存できませんでした")
            } finally {
                batchProtectionProgress.value = null
                batchSaveCancelRequested = false
                batchSaveJob = null
            }
        }
    }
    fun startBatchSave(targets: List<CompatPostSnapshot>, format: CompatGalleryBatchSaveFormat, isRetry: Boolean = false) {
        withSaveDestination { startBatchSaveNow(targets, format, isRetry, it) }
    }
    fun sharePost(post: CompatPostSnapshot) {
        val mediaUrl = resolveCompatViewerMediaUrl(post) ?: return
        launchScreenAction {
            val saver = mediaSaver
            val fs = fileSystem
            if (saver == null || fs == null) {
                message = "画像共有を初期化できませんでした"
                return@launchScreenAction
            }
            saver.saveMedia(
                mediaUrl,
                tab.boardKey,
                tab.threadNo,
                baseSaveLocation = manualSaveLocation
            )
                .onSuccess { saved ->
                    val mime = if (saved.mediaType.name == "VIDEO") "video/*" else "image/*"
                    val localPath = fs.resolveSavedFile(manualSaveLocation ?: SaveLocation.Path(MANUAL_SAVE_DIRECTORY), saved.relativePath).getOrThrow()
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
        launchScreenAction {
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
                launchScreenAction {
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
                launchScreenAction {
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
        launchScreenAction {
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
                                color = SecondaryChromeContent
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
                                tint = SecondaryChromeContent.copy(alpha = 0.62f)
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
                    titleContentColor = SecondaryChromeContent,
                    navigationIconContentColor = SecondaryChromeContent,
                    actionIconContentColor = SecondaryChromeContent
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
                    // Per-item values only: the cell skips unless its own
                    // marker, selection, fallback or reload token changes.
                    CompatGalleryCell(
                        post = post,
                        mediaIdentity = mediaIdentity,
                        saveMode = saveMode,
                        selected = mediaIdentity in selectedMediaKeys,
                        apngMarker = apngMarkers[mediaIdentity],
                        useOriginalPreview = mediaIdentity in thumbnailFallbackPostNos,
                        reloadToken = thumbnailReloadTokens[mediaIdentity],
                        saving = savingMediaKey == mediaIdentity,
                        httpClient = httpClient,
                        apngMarkerCache = apngMarkerCache,
                        imageLoader = imageLoader,
                        privacyEnabled = threadPrivacyEnabled,
                        privacyAlpha = threadPrivacyAlpha,
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
                        onLongClick = { contextPost = post },
                        onApngResolved = { apngMarkers[mediaIdentity] = it },
                        onPreviewFailed = {
                            thumbnailFallbackPostNos = thumbnailFallbackPostNos + mediaIdentity
                        }
                    )
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
                    "元レスに移動する" -> launchScreenAction {
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
                    launchScreenAction {
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
                launchScreenAction {
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
                lastSavedFile?.let { (saved, location) ->
                    TextButton(onClick = {
                        launchScreenAction {
                            val path = requireNotNull(fileSystem).resolveSavedFile(location ?: SaveLocation.Path(MANUAL_SAVE_DIRECTORY), saved.relativePath).getOrThrow()
                            share("", if (saved.mediaType == com.valoser.futacha.shared.service.SavedMediaType.VIDEO) "video/*" else "image/*", path)
                        }
                    }) { Text("共有") }
                }
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
private fun CompatGalleryCell(
    post: CompatPostSnapshot,
    mediaIdentity: String,
    saveMode: Boolean,
    selected: Boolean,
    apngMarker: Boolean?,
    useOriginalPreview: Boolean,
    reloadToken: Long?,
    saving: Boolean,
    httpClient: HttpClient?,
    apngMarkerCache: CompatApngMarkerCache,
    imageLoader: coil3.ImageLoader,
    privacyEnabled: Boolean,
    privacyAlpha: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onApngResolved: (Boolean) -> Unit,
    onPreviewFailed: () -> Unit
) {
    val palette = LocalCompatibilityPalette.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 1.dp, vertical = 2.dp).combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick
        ).testTag("compat-gallery-item-${post.postNo}")
    ) {
        val requestedPreviewUrl = resolveCompatPostPreviewUrl(post)
        val originalMediaUrl = resolveCompatViewerMediaUrl(post)
        val isPng = originalMediaUrl
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.endsWith(".png", ignoreCase = true) == true
        val apngMarkerKnown = apngMarker != null
        LaunchedEffect(mediaIdentity, originalMediaUrl, httpClient, apngMarkerCache) {
            if (isPng && !apngMarkerKnown) {
                onApngResolved(
                    httpClient
                        ?.let { client ->
                            apngMarkerCache.getOrLoad(originalMediaUrl) {
                                fetchCompatApngMarker(client, originalMediaUrl)
                            }.getOrDefault(false)
                        }
                        ?: false
                )
            }
        }
        val previewUrl = if (
            useOriginalPreview &&
            requestedPreviewUrl != originalMediaUrl
        ) originalMediaUrl else requestedPreviewUrl
        var promptImageState by remember(previewUrl, reloadToken) {
            mutableStateOf<coil3.compose.AsyncImagePainter.State?>(null)
        }
        val promptMetadata = rememberGenerationMetadata(originalMediaUrl, promptImageState, visible = !privacyEnabled)
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
                onSuccess = { promptImageState = it },
                onError = {
                    if (requestedPreviewUrl != originalMediaUrl) {
                        onPreviewFailed()
                    }
                },
                // sample/1.apk fixes the image itself to a
                // column-width square and uses fitCenter.
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().compatPrivacyImageEffect(
                    if (privacyEnabled) compatPrivacyContentAlpha(privacyAlpha) else 1f
                )
            )
            Text(
                post.position.toString(),
                color = palette.text,
                fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopEnd)
                    .background(palette.background).padding(horizontal = 2.dp)
            )
            PromptAiBadge(promptMetadata, Modifier.align(Alignment.BottomEnd))
            val mediaUrl = originalMediaUrl
            val mediaBadge = when {
                apngMarker == true -> "APNG"
                mediaUrl != null && isCompatVideoMediaUrl(mediaUrl) ->
                    compatMediaExtension(mediaUrl).uppercase()
                mediaUrl?.substringBefore('?')?.substringBefore('#')
                    ?.endsWith(".gif", ignoreCase = true) == true -> "GIF"
                else -> null
            }
            if (mediaBadge != null) {
                Text(
                    mediaBadge,
                    color = palette.chromeContent,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .background(palette.chrome).padding(horizontal = 2.dp)
                )
            }
            if (saveMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.align(Alignment.TopStart)
                        .testTag("compat-gallery-selection-${post.postNo}"),
                    colors = CheckboxDefaults.colors(
                        checkedColor = palette.chrome,
                        checkmarkColor = palette.chromeContent,
                        uncheckedColor = Color.White
                    )
                )
            }
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    color = palette.loadingProgress,
                    strokeWidth = 3.dp
                )
            }
        }
        val firstLine = remember(post.messageHtml) {
            post.messageHtml.toCompatPlainTextCached().lineSequence().firstOrNull().orEmpty()
        }
        Text(
            firstLine,
            color = palette.text,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().background(palette.background)
                .padding(horizontal = 2.dp)
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
