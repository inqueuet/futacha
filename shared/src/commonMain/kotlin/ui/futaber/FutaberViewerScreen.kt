package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.ui.util.ScrollableToolbarRow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.ui.board.messageHtmlToPlainText
import com.valoser.futacha.shared.ui.compat.CompatViewerImagePage
import com.valoser.futacha.shared.ui.compat.CompatViewerTransform
import com.valoser.futacha.shared.ui.compat.CompatViewerReturnPosition
import com.valoser.futacha.shared.ui.compat.compatMediaIdentity
import com.valoser.futacha.shared.ui.compat.compatUniqueMediaKeys
import com.valoser.futacha.shared.ui.compat.isCompatVideoMediaUrl
import com.valoser.futacha.shared.ui.compat.resolveCompatViewerMediaUrl
import com.valoser.futacha.shared.ui.privacy.PrivacyVideoOverlay
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.rememberUrlLauncher
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch

private val ViewerBackground = Color(0xFF000000)
private val ViewerBar = Color(0xFF111111)
private val ViewerInfo = Color(0xE6222222)
private val ViewerText = Color(0xFFFFFFFF)

/** The line over the image: "16 26/08/31(月)07:49:43 IP:… No.325019" of the shown post. */
internal fun futaberViewerHeader(post: CompatPostSnapshot): String = buildString {
    append(post.position).append(' ').append(post.timestamp)
    // The timestamp text of some boards already carries the ID, so it is added only when missing.
    post.posterId?.takeIf { it.isNotBlank() && !post.timestamp.contains(it) }?.let { append(' ').append(it) }
    append(" No.").append(post.postNo)
}

/** The counter in the top bar, as the original app writes it ("9 of 9"). */
internal fun futaberViewerCounter(index: Int, count: Int): String = "${index + 1} of $count"

/**
 * The viewer as the original app lays it out: a dark bar with the settings gear, "N of M" and a
 * close button; the post header and the start of its text over the image; and a bottom bar with
 * the image list, the post, share and save. Zoom and image loading are the shared image page, and
 * saving and sharing use the shared save service. A tap on the image hides or shows the bars.
 */
@Composable
internal fun FutaberViewerScreen(
    mediaPosts: List<CompatPostSnapshot>,
    initialIndex: Int,
    boardKey: String,
    threadKey: String,
    store: CompatibilityStore,
    preferences: Map<String, String>,
    httpClient: HttpClient?,
    fileSystem: FileSystem?,
    onShowPost: (postNo: String) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    swipeDownToClose: Boolean = false,
    /** A video page is the video viewer, with play / pause / stop, volume and "ブラウザで開く" (an extension); otherwise it only offers to open the video elsewhere. */
    playVideoInApp: Boolean = false,
    /** Kept by the media window while settings/help temporarily replace this composable. */
    returnPosition: CompatViewerReturnPosition? = null,
    onClose: () -> Unit
) {
    PlatformBackHandler(onBack = onOpenGallery)
    val scope = rememberCoroutineScope()
    val openUrl = rememberUrlLauncher()
    val keys = remember(mediaPosts) { compatUniqueMediaKeys(mediaPosts) }
    val pagerState = rememberPagerState(initialPage = futaberViewerIndexFor(
        mediaPosts, returnPosition?.mediaIdentity, returnPosition?.index ?: initialIndex
    )) { mediaPosts.size }
    // currentPage follows a drag/fling before it settles; that is the image the person is currently leaving.
    val returnIndex = pagerState.currentPage
    val returnIdentity = mediaPosts.getOrNull(returnIndex)?.let { resolveCompatViewerMediaUrl(it) ?: compatMediaIdentity(it) }
    SideEffect { if (returnIdentity != null) returnPosition?.update(returnIndex, returnIdentity) }
    fun rememberCurrentPosition() {
        val index = pagerState.currentPage
        mediaPosts.getOrNull(index)?.let { returnPosition?.update(index, resolveCompatViewerMediaUrl(it) ?: compatMediaIdentity(it)) }
    }
    var chromeVisible by remember { mutableStateOf(true) }
    val longPressHaptic = rememberFutaberLongPressHaptic()
    var message by remember { mutableStateOf<String?>(null) }
    val transforms = remember { mutableStateMapOf<String, CompatViewerTransform>() }
    // Saving and sharing go through the saver the thread's image menu shares (one at a time, the same destinations).
    val mediaSaver = rememberFutaberMediaSaver(store, preferences, httpClient, fileSystem, boardKey, threadKey) { message = it }
    val isSaving = mediaSaver.isSaving
    val sharingInProgress = mediaSaver.sharingInProgress
    val current = mediaPosts.getOrNull(pagerState.currentPage)

    fun saveCurrent(shareAfterSave: Boolean) {
        val mediaUrl = current?.let(::resolveCompatViewerMediaUrl) ?: return
        mediaSaver.saveOrShare(mediaUrl, shareAfterSave)
    }

    var zoomed by remember { mutableStateOf(false) }
    val currentIsVideo = current?.let(::resolveCompatViewerMediaUrl)?.let(::isCompatVideoMediaUrl) == true
    LaunchedEffect(pagerState.currentPage, currentIsVideo) {
        if (currentIsVideo) zoomed = false
    }
    val closeOnSwipe = if (swipeDownToClose) {
        Modifier.pointerInput(zoomed) {
            if (zoomed) return@pointerInput
            val triggerPx = 120.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var dx = 0f
                var dy = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (!change.pressed || event.changes.size > 1) return@awaitEachGesture
                    dx += change.positionChange().x
                    dy += change.positionChange().y
                    if (kotlin.math.abs(dx) > 48.dp.toPx() && kotlin.math.abs(dx) > kotlin.math.abs(dy)) return@awaitEachGesture
                    if (dy > triggerPx && dy > kotlin.math.abs(dx) * 1.5f) {
                        onClose()
                        return@awaitEachGesture
                    }
                }
            }
        }
    } else Modifier
    Box(Modifier.fillMaxSize().background(ViewerBackground).then(closeOnSwipe).testTag("futaber-viewer")) {
        HorizontalPager(
            state = pagerState,
            key = { keys[it] },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val post = mediaPosts.getOrNull(page)
            val mediaUrl = post?.let(::resolveCompatViewerMediaUrl)
            Box(Modifier.fillMaxSize()) {
                if (mediaUrl != null && isCompatVideoMediaUrl(mediaUrl)) {
                    FutaberVideoPoster(post?.thumbnailUrl, Modifier.fillMaxSize().pointerInput(mediaUrl) {
                        detectTapGestures(
                            onTap = { chromeVisible = !chromeVisible },
                            onLongPress = { if (page == pagerState.currentPage) { longPressHaptic(); saveCurrent(true) } }
                        )
                    })
                } else {
                    CompatViewerImagePage(
                        post = post,
                        mediaUrl = mediaUrl,
                        reloadSuffix = "",
                        viewerTransform = mediaUrl?.let(transforms::get) ?: CompatViewerTransform(),
                        isCurrentPage = page == pagerState.currentPage,
                        resetKey = if (page == pagerState.currentPage) pagerState.currentPage else -1,
                        privacyEnabled = false,
                        privacyAlpha = 0f,
                        onZoomedChanged = { if (page == pagerState.currentPage) zoomed = it },
                        onTransformChanged = { scale, translation ->
                            mediaUrl?.let { transforms[it] = CompatViewerTransform(scale, translation) }
                        },
                        onHorizontalSwipe = { dragDistancePx, viewportWidthPx ->
                            val target = when {
                                dragDistancePx < -viewportWidthPx * 0.25f -> page + 1
                                dragDistancePx > viewportWidthPx * 0.25f -> page - 1
                                else -> page
                            }.coerceIn(0, mediaPosts.size - 1)
                            if (target != page) scope.launch { pagerState.animateScrollToPage(target) }
                        },
                        onDimensionsKnown = { _, _ -> },
                        onClick = { chromeVisible = !chromeVisible },
                        // A long press on the image opens the share sheet at once, as in the original.
                        onLongClick = { if (page == pagerState.currentPage) { longPressHaptic(); saveCurrent(true) } }
                    )
                }
                if (mediaUrl != null && isCompatVideoMediaUrl(mediaUrl)) {
                    // The video page is the video viewer itself (no play button). Like the other modes' viewers it opens stopped,
                    // ready for a deliberate play (the shared player never autoplays); "停止" leaves it for the picture list.
                    if (playVideoInApp) {
                        FutaberVideoPlayer(
                            videoUrl = mediaUrl,
                            posterUrl = post?.thumbnailUrl,
                            active = page == pagerState.currentPage,
                            onStop = onOpenGallery,
                            onOpenExternal = { openUrl(mediaUrl) },
                            bottomInset = 110.dp
                        )
                    } else {
                        PrivacyVideoOverlay()
                        TextButton(onClick = { openUrl(mediaUrl) }, modifier = Modifier.align(Alignment.Center)) {
                            Text("動画を開く", color = ViewerText, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
        if (chromeVisible) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().background(ViewerBar).statusBarsPadding().height(FUTABER_TOP_BAR_HEIGHT_DP.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { rememberCurrentPosition(); onOpenSettings() }, modifier = Modifier.testTag("futaber-viewer-gear")) {
                        FutaberIcon(Icons.Outlined.Settings, contentDescription = "画像ビューアの設定", tint = ViewerText)
                    }
                    Text(
                        futaberViewerCounter(pagerState.currentPage, mediaPosts.size),
                        color = ViewerText, fontSize = 20.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).testTag("futaber-viewer-counter")
                    )
                    IconButton(onClick = onClose, modifier = Modifier.testTag("futaber-viewer-close")) {
                        FutaberIcon(Icons.Outlined.Close, contentDescription = "閉じる", tint = ViewerText)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth())
                Column(Modifier.fillMaxWidth().background(ViewerBar).navigationBarsPadding()) {
                    if (isSaving) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), color = ViewerText, strokeWidth = 2.dp)
                            Text(if (sharingInProgress) "共有の準備中…" else "保存中…", color = ViewerText,
                                modifier = Modifier.testTag("futaber-viewer-save-progress"))
                        }
                    }
                    current?.let { post ->
                        Column(
                            Modifier.fillMaxWidth().background(ViewerInfo).padding(horizontal = 20.dp, vertical = 10.dp)
                                .testTag("futaber-viewer-info"),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(futaberViewerHeader(post), color = ViewerText, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                messageHtmlToPlainText(post.messageHtml).trim(),
                                color = ViewerText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    ScrollableToolbarRow(itemCount = 4, minItemWidth = 48.dp, modifier = Modifier.fillMaxWidth().height(FUTABER_BOTTOM_BAR_HEIGHT_DP.dp)) {
                        ViewerBarButton(Icons.Outlined.GridView, "画像一覧", "futaber-viewer-grid", Modifier.weight(1f), onClick = onOpenGallery)
                        ViewerBarButton(Icons.Outlined.ChatBubbleOutline, "レスに戻る", "futaber-viewer-post", Modifier.weight(1f)) {
                            current?.let { onShowPost(it.postNo) }
                        }
                        ViewerBarButton(Icons.Outlined.IosShare, "共有", "futaber-viewer-share", Modifier.weight(1f), enabled = !isSaving) { saveCurrent(true) }
                        ViewerBarButton(Icons.Outlined.SaveAlt, "保存", "futaber-viewer-save", Modifier.weight(1f), enabled = !isSaving) { saveCurrent(false) }
                    }
                }
            }
        }
        message?.let { text ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 120.dp)
                    .background(Color(0xCC000000)).padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(text, color = ViewerText, fontSize = 14.sp,
                    modifier = Modifier.weight(1f).testTag("futaber-viewer-message"))
                TextButton(onClick = { message = null }, modifier = Modifier.testTag("futaber-viewer-message-dismiss")) {
                    Text("閉じる", color = ViewerText)
                }
            }
        }
    }
}

@Composable
private fun ViewerBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.testTag(tag)) {
        FutaberIcon(icon, contentDescription = label, tint = ViewerText)
    }
}
