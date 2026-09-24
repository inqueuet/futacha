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

@Composable
internal fun CompatViewerScreen(
    tab: CompatTab,
    initialIndex: Int,
    initialPostNo: String? = null,
    directMediaUrl: String? = null,
    directSourcePosition: Int? = null,
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
    var loadedPosts by remember(tabKey, directMediaUrl, directSourcePosition, initialIndex, initialPostNo) {
        mutableStateOf<List<CompatPostSnapshot>?>(null)
    }
    val posts = loadedPosts.orEmpty()
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

    fun launchScreenAction(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): Job =
        scope.launchCompatScreenAction("CompatViewer", { failure ->
            message = failure.toCompatUserMessage("操作に失敗しました")
        }, block)
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
    val withSaveDestination = rememberCompatManualSaveDestinationLauncher(store, preferences) {
        message = it.toCompatUserMessage("保存先の設定を記録できませんでした")
    }
    var lastSavedFile by remember { mutableStateOf<Pair<com.valoser.futacha.shared.service.SavedMediaFile, SaveLocation?>?>(null) }
    val manualSaveLocation = parseCompatSaveLocation(
        preferences.compatPreferenceValue("storage", "dummyDownloadDir", "保存ファイルの保存先")
    )
    val verticalSwipeCloseEnabled = preferences.compatPreferenceValue(
        "control", "controlViewerSwipeClose", "下にスワイプして閉じる", "縦にスワイプして閉じる"
    ) != "OFF"
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
        showDeletedContent,
        directMediaUrl,
        directSourcePosition,
        initialIndex,
        initialPostNo
    ) {
        try {
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
            if (directMediaUrl != null) {
                val sourcePost = rawPosts.firstOrNull { it.postNo == initialPostNo }
                loadedPosts = listOf(
                    CompatPostSnapshot(
                        position = directSourcePosition ?: sourcePost?.position ?: 0,
                        postNo = initialPostNo ?: sourcePost?.postNo ?: tab.threadNo,
                        timestamp = sourcePost?.timestamp.orEmpty(),
                        messageHtml = sourcePost?.messageHtml.orEmpty(),
                        imageUrl = directMediaUrl,
                        thumbnailUrl = compatApuSmallThumbnailUrl(directMediaUrl)
                            .takeIf { isCompatVideoMediaUrl(directMediaUrl) },
                        mediaKey = "direct::$directMediaUrl"
                    )
                )
                return@LaunchedEffect
            }
            val hiddenPostNos = compatImagePhashHiddenPostNos(
                httpClient = httpClient,
                posts = rawPosts,
                rules = imageNgPhashRules,
                threshold = imageNgPhashThreshold
            )
            loadedPosts = withContext(AppDispatchers.parsing) {
                compatViewerMediaPosts(
                    posts = rawPosts,
                    hiddenImages = hiddenImages,
                    hiddenPostNos = hiddenPostNos,
                    upsThumbnailMethod = upsThumbnailMethod,
                    wifiConnected = wifiConnected
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            Logger.e("CompatViewer", "Media list load failed", failure)
            message = "画像一覧を読み込めませんでした"
            loadedPosts = emptyList()
        }
    }
    LaunchedEffect(toolbarRefreshToken) {
        runSuspendCatchingPreservingCancellation { store.loadToolbar(CompatToolbarSurface.VIEWER) }
            .onSuccess { toolbarItems = it }
            .onFailure { failure ->
                Logger.e("CompatViewer", "Toolbar load failed", failure)
                message = "ツールバー設定を読み込めませんでした"
            }
    }
    if (posts.isEmpty()) {
        // Never create a placeholder pager with pageCount=1: it can paint
        // page zero before the asynchronously loaded launch identity is applied.
        PlatformBackHandler(onBack = onBack)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            IconButton(onClick = onBack, modifier = Modifier.statusBarsPadding()) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
            }
            if (loadedPosts == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else {
                Text(message ?: "表示できる画像がありません", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
        return
    }
    // Resolve the actual media identity before the very first pager measure.
    // No corrective scroll (and no intervening wrong-image frame) is needed.
    val pagerState = rememberPagerState(
        initialPage = compatViewerInitialPage(posts, initialPostNo, initialIndex)
    ) { posts.size }
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
    val imagePrefetcher = remember(imageLoader) { com.valoser.futacha.shared.ui.image.ImagePrefetcher(imageLoader) }
    DisposableEffect(imagePrefetcher) { onDispose { imagePrefetcher.close() } }
    LaunchedEffect(pagerState.currentPage, viewerPreloadMode, wifiConnected, upsThumbnailMethod, posts) {
        val currentUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl)
        val requests = if (!shouldPreloadCompatViewer(viewerPreloadMode, wifiConnected)) emptyList() else {
            listOf(pagerState.currentPage - 1, pagerState.currentPage + 1)
                .mapNotNull(posts::getOrNull)
                .mapNotNull { post ->
                    val url = resolveCompatPostPreviewUrl(post, upsThumbnailMethod, wifiConnected)
                        ?: return@mapNotNull null
                    if (isCompatVideoMediaUrl(url)) return@mapNotNull null
                    ImageRequest.Builder(platformContext)
                        .data(url)
                        .compatImageFallbackPolicy()
                        .size(1024, 1024)
                        .build()
                }
        }
        imagePrefetcher.update(requests, currentUrl)
    }
    fun saveCurrentNow(mediaUrl: String, shareAfterSave: Boolean, selectedLocation: SaveLocation?) {
        if (isSaving) return
        launchScreenAction {
            isSaving = true
            try {
                val saver = mediaSaver
                val fs = fileSystem
                if (saver == null || fs == null) {
                    message = if (shareAfterSave) "画像共有を初期化できませんでした" else "保存機能を初期化できませんでした"
                } else {
                    saver.saveMedia(
                        mediaUrl,
                        tab.boardKey,
                        tab.threadNo,
                        baseSaveLocation = selectedLocation,
                        storageDirectoryOverride = if (shareAfterSave) null else "",
                        useTypeSubdirectory = shareAfterSave
                    )
                        .onSuccess { saved ->
                            if (shareAfterSave) {
                                val mime = if (saved.mediaType.name == "VIDEO") "video/*" else "image/*"
                                share(
                                    mediaUrl,
                                    mime,
                                    fs.resolveSavedFile(selectedLocation ?: SaveLocation.Path(MANUAL_SAVE_DIRECTORY), saved.relativePath).getOrThrow()
                                )
                            } else {
                                lastSavedFile = saved to selectedLocation
                                message = compatMediaSaveCompletionMessage(saved, fs, selectedLocation)
                            }
                        }
                        .onFailure {
                            message = it.toCompatUserMessage(
                                if (shareAfterSave) "画像を共有できませんでした" else "画像を保存できませんでした"
                            )
                        }
                }
            } finally { isSaving = false }
        }
    }
    fun saveCurrent(shareAfterSave: Boolean) {
        val mediaUrl = posts.getOrNull(pagerState.currentPage)?.let(::resolveCompatViewerMediaUrl) ?: return
        if (isSaving) return
        withSaveDestination { saveCurrentNow(mediaUrl, shareAfterSave, it) }
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
        launchScreenAction {
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
        launchScreenAction {
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
                launchScreenAction {
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
                launchScreenAction { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
        }
        if (pagerState.currentPage < posts.lastIndex) {
            put("right") {
                launchScreenAction { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }
        put("download") { saveCurrent(shareAfterSave = false) }
        put("search") { searchCurrent() }
        put("back") {
            when (val target = compatViewerNavigationTarget("back", posts, pagerState.currentPage, snapshotRevision)) {
                is CompatViewerNavigationTarget.SourcePost -> onShowSourcePost(target.anchor)
                else -> Unit
            }
        }
        if (directMediaUrl == null) {
            put("gallery") {
                when (val target = compatViewerNavigationTarget("gallery", posts, pagerState.currentPage, snapshotRevision)) {
                    is CompatViewerNavigationTarget.Gallery -> onOpenGallery(target.index, target.mediaIdentity)
                    else -> Unit
                }
            }
        }
        put("share") { saveCurrent(shareAfterSave = true) }
        put("info") { showCurrentInfo() }
        put("screen") { chromeVisible = !chromeVisible }
        put("privacy") {
            val enabled = threadPrivacyEnabled
            launchScreenAction {
                store.savePreference(
                    COMPAT_COMMON_PRIVACY_STORAGE_KEY,
                    if (enabled) "OFF" else "ON"
                )
            }
        }
        put("reload") {
            posts.getOrNull(pagerState.currentPage)
                ?.let(::resolveCompatViewerMediaUrl)
                ?.let { mediaUrl ->
                    viewerReloadTokens = viewerReloadTokens +
                        (mediaUrl to Clock.System.now().toEpochMilliseconds())
                    message = "再読み込みしました"
                }
        }
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
            key = { page -> compatMediaIdentity(posts[page]) },
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
                                verticalResetJob = launchScreenAction {
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
                                verticalResetJob = launchScreenAction {
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
                            verticalResetJob = launchScreenAction {
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
                                launchScreenAction { pagerState.animateScrollToPage(target) }
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
                    val playingUrl = videoCandidates[videoCandidateIndex.coerceIn(0, videoCandidates.lastIndex)]
                    val videoPromptMetadata = rememberGenerationMetadata(playingUrl,
                        visible = page == pagerState.currentPage && !threadPrivacyEnabled)
                    val posterUrl = post?.thumbnailUrl
                        ?.takeIf { it.isNotBlank() && it != mediaUrl }
                    val privacyOverlayAlpha = compatPrivacyOverlayAlpha(
                        enabled = threadPrivacyEnabled,
                        transparency = threadPrivacyAlpha
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlatformVideoPlayer(
                            videoUrl = playingUrl + reloadSuffix,
                            isActive = page == pagerState.currentPage,
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
                        if (page == pagerState.currentPage && !threadPrivacyEnabled) {
                            PromptInfoAction(videoPromptMetadata, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 72.dp))
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
                            launchScreenAction { pagerState.animateScrollToPage(target) }
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
            confirmButton = { TextButton(onClick = { message = null; lastSavedFile = null }) { Text("OK") } },
            dismissButton = {
                lastSavedFile?.let { (saved, location) ->
                    TextButton(onClick = {
                        launchScreenAction {
                            val path = requireNotNull(fileSystem).resolveSavedFile(location ?: SaveLocation.Path(MANUAL_SAVE_DIRECTORY), saved.relativePath).getOrThrow()
                            share("", if (saved.mediaType == com.valoser.futacha.shared.service.SavedMediaType.VIDEO) "video/*" else "image/*", path)
                        }
                    }) { Text("共有") }
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
        val thumbnailImage = rememberViewerImagePainter(
            request = thumbnailRequest,
            imageLoader = imageLoader
        )
        val sourceImage = rememberViewerImagePainter(
            request = sourceRequest,
            imageLoader = imageLoader
        )
        val thumbnailPainter = thumbnailImage.painter
        val sourcePainter = sourceImage.painter
        val thumbnailState = thumbnailImage.state
        val sourceState = sourceImage.state
        val promptMetadata = rememberGenerationMetadata(mediaUrl, sourceState, visible = !privacyEnabled)
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
        if (isCurrentPage && !privacyEnabled) {
            PromptInfoAction(promptMetadata, Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 72.dp))
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
