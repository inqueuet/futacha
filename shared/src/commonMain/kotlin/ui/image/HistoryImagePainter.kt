package com.valoser.futacha.shared.ui.image

import androidx.compose.runtime.*
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.repository.IMPORTED_HISTORY_DIRECTORY
import com.valoser.futacha.shared.repository.historyThumbnailPath
import com.valoser.futacha.shared.service.AUTO_SAVE_DIRECTORY
import com.valoser.futacha.shared.ui.board.historyThumbnailFailureCache
import com.valoser.futacha.shared.ui.board.HistoryThumbnailFailureCache
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import kotlinx.coroutines.delay

internal val LocalHistoryImageRepositories = staticCompositionLocalOf<List<SavedThreadRepository>> { emptyList() }

@Composable
internal fun rememberHistoryImageRepositories(
    fileSystem: FileSystem?,
    autoSaveRepository: SavedThreadRepository?
): List<SavedThreadRepository> = remember(fileSystem, autoSaveRepository) {
    listOfNotNull(
        autoSaveRepository ?: fileSystem?.let { SavedThreadRepository(it, AUTO_SAVE_DIRECTORY) },
        fileSystem?.let { SavedThreadRepository(it, IMPORTED_HISTORY_DIRECTORY) }
    )
}

private data class HistoryImageCandidate(val url: String, val loader: ImageLoader, val network: Boolean = false)

/** Local saves and both image caches remain usable even while a remote URL is known missing. */
@Composable
internal fun rememberHistoryImagePainter(
    threadId: String,
    boardId: String,
    boardUrl: String,
    thumbnailUrl: String?,
    sizePx: Int,
    savedCopyAvailable: Boolean = false,
    failureCache: HistoryThumbnailFailureCache = historyThumbnailFailureCache,
    catalogImageLoader: ImageLoader? = null
): ViewerImagePainter {
    val repositories = LocalHistoryImageRepositories.current
    val imageLoader = LocalFutachaImageLoader.current
    val catalogLoader = catalogImageLoader ?: imageLoader
    val context = LocalPlatformContext.current
    return key(threadId, boardId, boardUrl, thumbnailUrl, savedCopyAvailable, repositories, imageLoader, catalogLoader) {
        var savedPaths by remember { mutableStateOf<List<String>?>(null) }
        LaunchedEffect(Unit) {
            savedPaths = repositories.mapNotNull { repository ->
                runSuspendCatchingPreservingCancellation {
                    repository.historyThumbnailPath(threadId, boardId, boardUrl)
                }.getOrNull()
            }.distinct()
        }
        val candidates = remember(savedPaths) {
            savedPaths?.let { paths -> buildList {
                paths.forEach { add(HistoryImageCandidate(it, imageLoader)) }
                thumbnailUrl?.takeIf(String::isNotBlank)?.let { url ->
                    add(HistoryImageCandidate(url, imageLoader))
                    if (catalogLoader !== imageLoader) {
                        add(HistoryImageCandidate(url, catalogLoader))
                        // Catalog eco mode stores the smaller /cat/ image under its own URL.
                        if ("/thumb/" in url) add(HistoryImageCandidate(url.replace("/thumb/", "/cat/"), catalogLoader))
                    }
                    add(HistoryImageCandidate(url, imageLoader, network = true))
                }
            } }.orEmpty()
        }
        var index by remember(candidates) { mutableIntStateOf(0) }
        var networkReady by remember { mutableStateOf(!failureCache.isKnownMissing(thumbnailUrl.orEmpty())) }
        LaunchedEffect(networkReady) {
            if (!networkReady) {
                delay(failureCache.retryAfterMillis(thumbnailUrl.orEmpty()))
                networkReady = true
            }
        }
        val candidate = candidates.getOrNull(index)
        var cacheReady by remember(candidate) {
            mutableStateOf(candidate == null || candidate.loader !is StableImageLoader || candidate.network ||
                !(candidate.url.startsWith("https://") || candidate.url.startsWith("http://")))
        }
        LaunchedEffect(candidate) {
            if (!cacheReady) {
                // Cold start opens the disk cache asynchronously. A premature miss would
                // otherwise bypass an existing image and request a dead remote URL.
                (candidate?.loader as? StableImageLoader)?.awaitDiskCacheInitialization()
                cacheReady = true
            }
        }
        val request = remember(candidate, sizePx, networkReady, cacheReady) {
            candidate?.takeIf { cacheReady && (!it.network || networkReady) }?.let {
                ImageRequest.Builder(context).data(it.url).size(sizePx, sizePx).crossfade(false)
                    .networkCachePolicy(if (it.network) CachePolicy.ENABLED else CachePolicy.DISABLED)
                    .build()
            }
        }
        val image = rememberViewerImagePainter(request, candidate?.loader ?: imageLoader)
        LaunchedEffect(image.state, candidate) {
            when (val state = image.state) {
                is AsyncImagePainter.State.Error -> if (candidate != null) {
                    if (!candidate.network) index += 1
                    else if (isMissingImage(state.result.throwable)) {
                        failureCache.recordMissing(candidate.url)
                        networkReady = false
                    }
                }
                is AsyncImagePainter.State.Success -> if (candidate?.network == true) {
                    failureCache.forget(candidate.url)
                }
                else -> Unit
            }
        }
        image
    }
}
