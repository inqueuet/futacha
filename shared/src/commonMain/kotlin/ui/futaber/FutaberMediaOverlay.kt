package com.valoser.futacha.shared.ui.futaber

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.appliesToThreadImage
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatToolbarSurface
import com.valoser.futacha.shared.compat.toCompatThreadSnapshot
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.compatViewerMediaPosts
import com.valoser.futacha.shared.ui.compat.CompatHelpScreen
import com.valoser.futacha.shared.ui.compat.CompatSettingsScreen
import com.valoser.futacha.shared.ui.compat.CompatToolbarEditorScreen
import com.valoser.futacha.shared.ui.compat.CompatViewerScreen
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.compat.normalizeCompatThreadSnapshot
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.util.AppDispatchers
import kotlinx.coroutines.withContext

/**
 * The image gallery (square thumbnails) and the viewer (zoom, save, share) of the thread,
 * shown full screen over it. These are the shared screens ふたちゃ and としあき also use:
 * they read the thread's posts from a snapshot built from the page on screen and only write
 * the shared viewer settings and NG rules, never tabs or history.
 */
@Composable
internal fun FutaberMediaOverlay(
    board: BoardSummary,
    ref: FutaberThreadRef,
    page: ThreadPage,
    services: FutaberMediaServices,
    /** Post numbers NG hides in the thread (words, IDs, rules, look-alike pictures); they are left out of the gallery and the viewer. */
    hiddenPostNos: Set<String> = emptySet(),
    onShowPost: (postId: String) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalFutaberColors.current
    val palette = remember(colors) { futaberMediaPalette(colors) }
    val scope = rememberCoroutineScope()
    val preferences by services.store.preferences.collectAsState(emptyMap())
    val ngRules by services.store.ngRules.collectAsState(emptyList<CompatNgRule>())
    val galleryGridState = rememberLazyGridState()
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var viewerPostNo by rememberSaveable { mutableStateOf<String?>(null) }
    var toolbarEditorOpen by rememberSaveable { mutableStateOf(false) }
    var toolbarRevision by rememberSaveable { mutableStateOf(0L) }
    // Shared settings pages opened from the overflow menus ("viewer", pages they lead to, "help").
    var settingsPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    val tab = remember(board.url, ref) { futaberMediaTab(board, ref, snapshotRevision = 0L) }
    val snapshot by produceState<CompatThreadSnapshot?>(null, page, tab.key) {
        value = withContext(AppDispatchers.parsing) {
            normalizeCompatThreadSnapshot(page.toCompatThreadSnapshot(tab.key, tab.snapshotRevision))
        }
    }
    val hiddenImages = remember(ngRules, tab) {
        ngRules.filter { it.kind == CompatNgKind.THREAD_IMAGE && it.appliesToThreadImage(tab.boardKey, tab.key) }
            .mapTo(mutableSetOf(), CompatNgRule::normalizedValue)
    }
    val mediaPosts = remember(snapshot, hiddenImages, hiddenPostNos) {
        snapshot?.let {
            compatViewerMediaPosts(posts = it.posts, hiddenImages = hiddenImages, hiddenPostNos = hiddenPostNos)
        }.orEmpty()
    }
    FutachaAppLockAwareWindow {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            CompositionLocalProvider(LocalCompatibilityPalette provides palette) {
                Surface(Modifier.fillMaxSize().testTag("futaber-media"), color = colors.background) {
                    val settingsPath = settingsPaths.lastOrNull()
                    val closeSettings = { settingsPaths = settingsPaths.dropLast(1) }
                    when {
                        snapshot == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.accent)
                        }
                        settingsPath == "help" -> {
                            PlatformBackHandler(onBack = closeSettings)
                            CompatHelpScreen(onBack = closeSettings)
                        }
                        settingsPath != null -> {
                            PlatformBackHandler(onBack = closeSettings)
                            CompatSettingsScreen(
                                path = settingsPath,
                                store = services.store,
                                preferences = preferences,
                                fileSystem = services.fileSystem,
                                httpClient = services.httpClient,
                                cookieRepository = services.cookieRepository,
                                modernPresentation = true,
                                onOpenHelp = { settingsPaths = settingsPaths + "help" },
                                onNavigate = { settingsPaths = settingsPaths + it },
                                onBack = closeSettings
                            )
                        }
                        toolbarEditorOpen -> {
                            PlatformBackHandler { toolbarEditorOpen = false }
                            CompatToolbarEditorScreen(CompatToolbarSurface.VIEWER, services.store) {
                                toolbarEditorOpen = false
                                toolbarRevision += 1
                            }
                        }
                        viewerIndex != null -> {
                            PlatformBackHandler { viewerIndex = null }
                            FutaberViewerScreen(
                                mediaPosts = mediaPosts,
                                initialIndex = viewerIndex ?: 0,
                                boardKey = tab.boardKey,
                                threadKey = tab.threadNo,
                                store = services.store,
                                preferences = preferences,
                                httpClient = services.httpClient,
                                fileSystem = services.fileSystem,
                                onShowPost = { postNo ->
                                    viewerIndex = null
                                    onShowPost(postNo)
                                    onClose()
                                },
                                onOpenGallery = { viewerIndex = null },
                                onOpenSettings = { settingsPaths = listOf("viewer") },
                                swipeDownToClose = FutaberDisplaySettings.from(preferences).viewerSwipeClose,
                                playVideoInApp = FutaberDisplaySettings.from(preferences).extVideo,
                                onClose = onClose
                            )
                        }
                        else -> FutaberGalleryScreen(
                            mediaPosts = mediaPosts,
                            gridState = galleryGridState,
                            onOpenViewer = { index, postNo ->
                                viewerIndex = index
                                viewerPostNo = postNo
                            },
                            onOpenSettings = { settingsPaths = listOf("viewer") },
                            onClose = onClose
                        )
                    }
                }
            }
        }
    }
}
