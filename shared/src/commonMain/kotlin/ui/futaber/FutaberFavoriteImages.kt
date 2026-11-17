package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.service.RawHtmlSaveOptions
import com.valoser.futacha.shared.service.ThreadSaveLimits
import com.valoser.futacha.shared.service.ThreadSaveService
import com.valoser.futacha.shared.service.ThreadSaveStorageOptions
import com.valoser.futacha.shared.service.buildThreadStorageId
import com.valoser.futacha.shared.util.FileSystem
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException

/*
 * L-B12 (M14): when a thread is added to the favourites futaber asks 「画像をダウンロードしますか？」 and keeps the pictures
 * until the favourite is taken away. They are kept with the thread-saving code the app already has, in a folder of their own
 * (not the person's save folder, not in the settings backup: it is under `private/`), one folder per thread.
 */

internal const val FUTABER_FAVORITE_IMAGES_DIRECTORY = "private/futaber_favorites"

/** What the person chose in the alert. */
internal enum class FutaberFavoriteImageChoice { None, Thumbnails, Full }

/** The folder of one thread's pictures (the same name the thread-saving code gives its folder). */
internal fun futaberFavoriteImagesFolder(boardId: String, threadId: String): String =
    "$FUTABER_FAVORITE_IMAGES_DIRECTORY/${buildThreadStorageId(boardId, threadId)}"

/** [posts] as they are saved for [choice]: thumbnails only drops the originals; nothing drops both. */
internal fun futaberFavoriteSavePosts(posts: List<Post>, choice: FutaberFavoriteImageChoice): List<Post> = when (choice) {
    FutaberFavoriteImageChoice.Full -> posts
    FutaberFavoriteImageChoice.Thumbnails -> posts.map { it.copy(imageUrl = null) }
    FutaberFavoriteImageChoice.None -> posts.map { it.copy(imageUrl = null, thumbnailUrl = null) }
}

/** Whether the alert is worth asking: some post has a picture to keep. */
internal fun futaberFavoriteHasImages(posts: List<Post>): Boolean = posts.any { !it.thumbnailUrl.isNullOrBlank() || !it.imageUrl.isNullOrBlank() }

/** Saves the pictures of the thread for [choice]; a failure is returned, not thrown (a favourite is kept either way). */
internal suspend fun futaberSaveFavoriteImages(
    httpClient: HttpClient,
    fileSystem: FileSystem,
    boardId: String,
    boardName: String,
    boardUrl: String,
    threadId: String,
    title: String,
    posts: List<Post>,
    choice: FutaberFavoriteImageChoice
): Result<Unit> {
    if (choice == FutaberFavoriteImageChoice.None) return Result.success(Unit)
    return try {
        ThreadSaveService(httpClient, fileSystem).saveThread(
            threadId = threadId, boardId = boardId, boardName = boardName, boardUrl = boardUrl, title = title,
            expiresAtLabel = null, posts = futaberFavoriteSavePosts(posts, choice),
            baseSaveLocation = null, baseDirectory = FUTABER_FAVORITE_IMAGES_DIRECTORY, writeMetadata = false,
            rawHtmlOptions = RawHtmlSaveOptions(enable = false, stripExternalResources = true),
            limits = ThreadSaveLimits(downloadFullMedia = choice == FutaberFavoriteImageChoice.Full),
            storageOptions = ThreadSaveStorageOptions(storageIdOverride = buildThreadStorageId(boardId, threadId))
        ).map { }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
}

/** Takes the thread's kept pictures away (the favourite was taken off). Nothing there is not a failure. */
internal suspend fun futaberDeleteFavoriteImages(fileSystem: FileSystem, boardId: String, threadId: String) {
    val folder = futaberFavoriteImagesFolder(boardId, threadId)
    try {
        if (fileSystem.exists(folder)) fileSystem.deleteRecursively(folder)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // Left behind; the next removal of the same thread tries again.
    }
}
