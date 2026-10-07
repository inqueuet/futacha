package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

internal const val FUTABER_MAX_FAVORITES = 60
private const val FAVORITE_TITLE_LIMIT = 60
private const val FAVORITE_BOARD_NAME_LIMIT = 30
private const val FAVORITE_URL_LIMIT = 200

/** A stored preference value must stay under the 20,000 characters the settings store accepts. */
private const val FAVORITE_STORED_LIMIT = 18_000
private const val FIELD_SEPARATOR = '\n'

/**
 * A favourite the user starred. Unlike a tab it keeps its own title, thumbnail and board
 * name: it has to survive the history being cleared. It is kept in the ふたばー mode's own
 * setting, so starring a thread adds nothing to the other modes' tabs or favourites.
 */
internal data class FutaberFavorite(
    val boardId: String,
    val threadId: String,
    val title: String,
    val thumbnailUrl: String,
    val boardName: String,
    val replyCount: Int,
    val addedAtEpochMillis: Long
) {
    val key: FutaberTabKey get() = FutaberTabKey(boardId, threadId)
}

internal fun futaberFavoriteFor(
    board: BoardSummary,
    ref: FutaberThreadRef,
    nowEpochMillis: Long
): FutaberFavorite = FutaberFavorite(
    boardId = board.id,
    threadId = ref.threadId,
    title = futaberSafeTake(ref.title.ifBlank { "(無題)" }.singleLine(), FAVORITE_TITLE_LIMIT),
    thumbnailUrl = futaberSafeTake(ref.thumbnailUrl.singleLine(), FAVORITE_URL_LIMIT),
    boardName = futaberSafeTake(board.name.singleLine(), FAVORITE_BOARD_NAME_LIMIT),
    replyCount = ref.replyCount.coerceAtLeast(0),
    addedAtEpochMillis = nowEpochMillis
)

/** The separator must not appear inside a field. */
private fun String.singleLine(): String = replace('\n', ' ').replace('\r', ' ')

private fun FutaberFavorite.encoded(): String = listOf(
    boardId, threadId, title, thumbnailUrl, boardName, replyCount.toString(), addedAtEpochMillis.toString()
).joinToString(FIELD_SEPARATOR.toString())

/**
 * Newest first. When the text would not fit the stored limit, the oldest favourites are
 * dropped rather than failing to save the newest one.
 */
internal fun encodeFutaberFavorites(favorites: List<FutaberFavorite>): String {
    var kept = favorites.take(FUTABER_MAX_FAVORITES)
    while (true) {
        val text = Json.encodeToString(ListSerializer(String.serializer()), kept.map { it.encoded() })
        if (text.length <= FAVORITE_STORED_LIMIT || kept.isEmpty()) return text
        kept = kept.dropLast(1)
    }
}

/** A damaged or unknown stored value reads as no favourites rather than failing. */
internal fun decodeFutaberFavorites(stored: String?): List<FutaberFavorite> {
    if (stored.isNullOrBlank()) return emptyList()
    val raw = runCatching { Json.decodeFromString(ListSerializer(String.serializer()), stored) }
        .getOrNull() ?: return emptyList()
    return raw.mapNotNull { entry ->
        val parts = entry.split(FIELD_SEPARATOR)
        if (parts.size != 7 || parts[0].isBlank() || parts[1].isBlank()) null
        else FutaberFavorite(
            boardId = parts[0], threadId = parts[1], title = parts[2], thumbnailUrl = parts[3],
            boardName = parts[4], replyCount = parts[5].toIntOrNull() ?: 0,
            addedAtEpochMillis = parts[6].toLongOrNull() ?: 0L
        )
    }.distinctBy { it.key }.take(FUTABER_MAX_FAVORITES)
}

internal fun futaberIsFavorite(favorites: List<FutaberFavorite>, key: FutaberTabKey): Boolean =
    favorites.any { it.key == key }

/** Stars the thread at the top, or removes the star when it is already there. */
internal fun futaberToggleFavorite(favorites: List<FutaberFavorite>, favorite: FutaberFavorite): List<FutaberFavorite> =
    if (futaberIsFavorite(favorites, favorite.key)) favorites.filterNot { it.key == favorite.key }
    else (listOf(favorite) + favorites).take(FUTABER_MAX_FAVORITES)

internal fun futaberRemoveFavorite(favorites: List<FutaberFavorite>, key: FutaberTabKey): List<FutaberFavorite> =
    favorites.filterNot { it.key == key }

/** A removed board takes its favourites with it; deleting a thread from the history does not. */
internal fun futaberPruneFavorites(favorites: List<FutaberFavorite>, boards: List<BoardSummary>): List<FutaberFavorite> {
    val boardIds = boards.mapTo(HashSet()) { it.id }
    return favorites.filter { it.boardId in boardIds }
}

/** What one favourite row shows; the count follows the history when the thread was read since. */
internal data class FutaberFavoriteView(
    val favorite: FutaberFavorite,
    val replyCount: Int
)

internal fun futaberFavoriteViews(
    favorites: List<FutaberFavorite>,
    history: List<ThreadHistoryEntry>
): List<FutaberFavoriteView> {
    val byKey = history.associateBy { FutaberTabKey(it.boardId, it.threadId) }
    return favorites.map { favorite ->
        FutaberFavoriteView(favorite, byKey[favorite.key]?.replyCount ?: favorite.replyCount)
    }
}

internal fun futaberRefForFavorite(view: FutaberFavoriteView, boards: List<BoardSummary>): FutaberThreadRef? {
    val board = boards.firstOrNull { it.id == view.favorite.boardId } ?: return null
    return FutaberThreadRef(board.id, view.favorite.threadId, view.favorite.title, view.favorite.thumbnailUrl, view.replyCount)
}
