@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.PlatformContext
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.IosCompatibilityStore
import com.valoser.futacha.shared.ui.board.MediaPreviewEntry
import com.valoser.futacha.shared.ui.board.MediaType
import com.valoser.futacha.shared.ui.board.VideoPreviewDialog
import com.valoser.futacha.shared.ui.compat.CompatViewerScreen
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.futaber.*
import com.valoser.futacha.shared.ui.compat.CompatibilityProfileTheme
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.createFileSystem
import platform.Foundation.NSURL
import platform.UIKit.UIViewController

/**
 * DEBUG-only Swift launch route: the real compatibility viewer on a local two-page thread, the
 * video first. It exists so a UI test can swipe on the real native player. Nothing starts playback.
 */
fun CompatViewerVideoValidationController(filePath: String, mode: String = "compat"): UIViewController = ComposeUIViewController {
    val url = remember { requireNotNull(NSURL.fileURLWithPath(filePath).absoluteString) }
    val loader = remember { ImageLoader.Builder(PlatformContext.INSTANCE).build() }
    val store = remember { IosCompatibilityStore(createFileSystem()) }
    val ready = produceState(false) { store.initialize(); value = true }
    if (!ready.value) return@ComposeUIViewController
    val tab = remember {
        CompatTab(key = "m57", canonicalUrl = "https://may.2chan.net/b/res/57.htm",
            originalUrl = "https://may.2chan.net/b/res/57.htm", boardKey = "may/b", boardName = "may/b", threadNo = "57",
            title = "M57", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1, snapshotRevision = 1)
    }
    val snapshot = remember {
        CompatThreadSnapshot(tabKey = tab.key, revision = 1, fetchedAtEpochMillis = 1, posts = listOf(
            CompatPostSnapshot(position = 0, postNo = "57", timestamp = "now", messageHtml = "video", imageUrl = url),
            CompatPostSnapshot(position = 1, postNo = "58", timestamp = "now", messageHtml = "other", imageUrl = "file:///m57-unused.png")
        ))
    }
    CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
        when (mode) {
            "futaber" -> FutaberTheme(FutaberThemeMode.Light) { FutaberViewerScreen(snapshot.posts, 0, "may/b", tab.key, store, emptyMap(), null, null,
                onShowPost = {}, onOpenGallery = {}, onOpenSettings = {}, playVideoInApp = true, onClose = {}) }
            "ftutter" -> FtutterTheme(null, FtutterBrightness.DARK) {
                FtutterViewerScreen(snapshot.posts, 0, FtutterMediaSettings(), 1f,
                    FtutterMediaSaveTools("", {}, {}, { _, _ -> error("unused") }, false), emptyList(),
                    { emptyList() }, {}, {}, {}, {}, {})
            }
            "futacha" -> {
                var second by remember { mutableStateOf(false) }
                MaterialTheme {
                    if (second) Text("page-two") else VideoPreviewDialog(
                        MediaPreviewEntry(url, MediaType.Video, "57", "M57"), 0, 2,
                        onDismiss = {}, onNavigateNext = { second = true }, onNavigatePrevious = {})
                }
            }
            else -> CompatibilityProfileTheme("mono") {
                CompatViewerScreen(tab, 0, preparedSnapshot = snapshot, store = store,
                    preferences = mapOf("compat.viewer.viewerPreloadMode" to "利用しない"), ngRules = emptyList(),
                    httpClient = null, fileSystem = null, onToolbarEdit = {}, onShowSourcePost = {},
                    onOpenGallery = { _, _ -> }, onOpenSettings = {}, onOpenCommonSettings = {}, onBack = {})
            }
        }
    }
}
