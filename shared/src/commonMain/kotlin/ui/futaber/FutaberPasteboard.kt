package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.util.ImageData
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The writing screen's "ペーストボード" (as in the original, the paper icon): texts and pictures kept on this device and put
 * into the writing with one tap ("よく使うテキストや画像を保存して素早く貼り付けられます"). Nothing is ever sent by itself.
 * The list is a preference ([FutaberPreferenceKeys.PASTEBOARD], left out of the settings backups: it names files of this
 * device), the pictures are files under [FUTABER_PASTEBOARD_DIRECTORY], apart from the draft files that
 * "添付・一時ファイルのクリア" removes.
 */
@Serializable
internal data class FutaberPasteItem(
    val id: String,
    val text: String = "",
    /** The picture's file under [FUTABER_PASTEBOARD_DIRECTORY]; null for a text. */
    val imageLocator: String? = null,
    val imageName: String = "",
    /**
     * L-B8 (M39): the id of the folder the row is in; absent (null) = no folder. A version before L-B8 ignores the field
     * (the list is read with `ignoreUnknownKeys`) and shows the row with the others.
     */
    val folder: String? = null,
    /**
     * L-B8 (M39): set only on a folder's own row (its name). Such a row has no text and no picture, so a version before
     * L-B8 drops it when it reads the list (its rows then show without a folder).
     */
    val folderName: String? = null
) {
    val isImage: Boolean get() = imageLocator != null
    val isFolder: Boolean get() = folderName != null
}

internal const val FUTABER_PASTEBOARD_DIRECTORY = "private/futaber_pasteboard"
internal const val FUTABER_PASTEBOARD_MAX_ITEMS = 40
internal const val FUTABER_PASTEBOARD_MAX_TEXT_CHARS = 2_000
private const val FUTABER_PASTEBOARD_STORE_MAX_CHARS = 18_000
private val pasteboardJson = Json { ignoreUnknownKeys = true }

internal fun decodeFutaberPasteboard(stored: String?): List<FutaberPasteItem> {
    if (stored.isNullOrBlank()) return emptyList()
    return runCatching { pasteboardJson.decodeFromString(ListSerializer(FutaberPasteItem.serializer()), stored) }
        .getOrDefault(emptyList())
        .filter { item ->
            item.id.isNotBlank() &&
                (!item.folderName.isNullOrBlank() || item.text.isNotEmpty() || futaberOwnsPasteboardFile(item.imageLocator))
        }
        .distinctBy { it.id }
        .take(FUTABER_PASTEBOARD_MAX_ITEMS)
        .let { kept ->
            // A row whose folder is gone (or that names a row which is not a folder) shows without one.
            val folders = kept.filter { it.isFolder }.map { it.id }.toSet()
            kept.map { item -> if (item.folder != null && (item.isFolder || item.folder !in folders)) item.copy(folder = null) else item }
        }
}

/** The newest first; the oldest go when the list would not fit the stored value (the files they name are returned). */
internal fun encodeFutaberPasteboard(items: List<FutaberPasteItem>): Pair<String, List<FutaberPasteItem>> {
    var kept = items.take(FUTABER_PASTEBOARD_MAX_ITEMS)
    while (true) {
        val encoded = pasteboardJson.encodeToString(ListSerializer(FutaberPasteItem.serializer()), kept)
        if (encoded.length <= FUTABER_PASTEBOARD_STORE_MAX_CHARS || kept.size <= 1) {
            return encoded to items.filterNot { item -> kept.any { it.id == item.id } }
        }
        kept = kept.dropLast(1)
    }
}

/** [text] kept at the top (one already kept moves up instead of being kept twice). Blank text changes nothing. */
internal fun futaberPasteboardWithText(items: List<FutaberPasteItem>, text: String, id: String, folder: String? = null): List<FutaberPasteItem> {
    val kept = text.take(FUTABER_PASTEBOARD_MAX_TEXT_CHARS)
    if (kept.isBlank()) return items
    val existing = items.firstOrNull { !it.isImage && !it.isFolder && it.text == kept }
    return listOf((existing ?: FutaberPasteItem(id = id, text = kept)).copy(folder = folder)) + items.filterNot { it === existing }
}

internal fun futaberPasteboardWithImage(items: List<FutaberPasteItem>, locator: String, name: String, id: String, folder: String? = null): List<FutaberPasteItem> =
    listOf(FutaberPasteItem(id = id, imageLocator = locator, imageName = name, folder = folder)) + items

/** Removes the row; a folder's rows go with it (as the original's 削除 of a folder). */
internal fun futaberPasteboardWithout(items: List<FutaberPasteItem>, id: String): List<FutaberPasteItem> =
    futaberPasteboardWithoutAll(items, setOf(id))

internal fun futaberPasteboardWithoutAll(items: List<FutaberPasteItem>, ids: Set<String>): List<FutaberPasteItem> =
    items.filterNot { it.id in ids || it.folder in ids }

// ---- L-B8 (M39): folders and the edit mode's 移動・並べ替え ----

/** The rows of one level: a folder's, or (null) the top's — the folders first, as the original's table lists them. */
internal fun futaberPasteboardLevel(items: List<FutaberPasteItem>, folder: String?): List<FutaberPasteItem> =
    if (folder == null) items.filter { it.folder == null }.sortedBy { if (it.isFolder) 0 else 1 }
    else items.filter { it.folder == folder && !it.isFolder }

/** A new folder at the top of the list ([name] blank = nothing). Folders are not put in folders. */
internal fun futaberPasteboardWithFolder(items: List<FutaberPasteItem>, name: String, id: String): List<FutaberPasteItem> {
    val trimmed = name.trim().take(FUTABER_PASTEBOARD_FOLDER_NAME_MAX_CHARS)
    if (trimmed.isEmpty()) return items
    return listOf(FutaberPasteItem(id = id, folderName = trimmed)) + items
}

/** The rows [ids] (not folders) put in [folder] (null = no folder). */
internal fun futaberPasteboardMoved(items: List<FutaberPasteItem>, ids: Set<String>, folder: String?): List<FutaberPasteItem> {
    val target = folder?.takeIf { wanted -> items.any { it.id == wanted && it.isFolder } }
    return items.map { item -> if (item.id in ids && !item.isFolder) item.copy(folder = target) else item }
}

/**
 * The rows of one level in the order [orderedIds] (the edit mode's grips); the rows of the other levels keep their
 * places. Ids not on the level are ignored.
 */
internal fun futaberPasteboardReordered(items: List<FutaberPasteItem>, orderedIds: List<String>): List<FutaberPasteItem> {
    val byId = items.associateBy { it.id }
    val moving = orderedIds.mapNotNull(byId::get).distinctBy { it.id }
    if (moving.size < 2) return items
    val slots = items.withIndex().filter { (_, item) -> moving.any { it.id == item.id } }.map { it.index }
    val result = items.toMutableList()
    slots.forEachIndexed { slot, index -> result[index] = moving[slot] }
    return result
}

/**
 * The rows a grip drag of [item] moves among: on the top level the folders stay above the other rows (they are
 * shown first), so a folder moves among the folders and a row among the rows; it no longer writes a move that the
 * sort puts back (audit B5).
 */
internal fun futaberPasteboardDragLevel(rows: List<FutaberPasteItem>, item: FutaberPasteItem): List<String> =
    rows.filter { it.isFolder == item.isFolder }.map { it.id }

/** One look at a grip drag: the order after it and the step taken (0 = not past half a row, or at an end). */
internal fun futaberPasteboardGripStep(ids: List<String>, id: String, dragY: Float, rowHeight: Float): Pair<List<String>, Int> {
    val from = ids.indexOf(id)
    val step = when {
        from < 0 -> 0
        dragY > rowHeight / 2 && from < ids.lastIndex -> 1
        dragY < -rowHeight / 2 && from > 0 -> -1
        else -> 0
    }
    return if (step == 0) ids to 0 else futaberPasteboardMovedWithin(ids, from, from + step) to step
}

/** The row at [from] of a level moved to [to] (a drag on the grip, one place at a time). */
internal fun futaberPasteboardMovedWithin(levelIds: List<String>, from: Int, to: Int): List<String> {
    if (from !in levelIds.indices || to !in levelIds.indices || from == to) return levelIds
    return levelIds.toMutableList().apply { add(to, removeAt(from)) }
}

internal const val FUTABER_PASTEBOARD_FOLDER_NAME_MAX_CHARS = 40

/** Where a kept picture goes: one file per item, named after the item so two pictures never share a file. */
internal fun futaberPasteboardImageLocator(id: String, fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase().filter { it in 'a'..'z' || it in '0'..'9' }.take(5)
    val safeId = id.filter { it in 'a'..'z' || it in '0'..'9' || it == '-' }.ifEmpty { "item" }
    return "$FUTABER_PASTEBOARD_DIRECTORY/$safeId" + if (extension.isNotEmpty()) ".$extension" else ""
}

/** Only files in this mode's own folder are ever read or deleted through the list. */
internal fun futaberOwnsPasteboardFile(locator: String?): Boolean {
    if (locator == null) return false
    val parts = locator.replace('\\', '/').split('/')
    return parts.size == 3 && "${parts[0]}/${parts[1]}" == FUTABER_PASTEBOARD_DIRECTORY &&
        parts[2].isNotBlank() && parts[2] != "." && parts[2] != ".."
}

internal const val FUTABER_PASTEBOARD_IMAGE_LOST = "ペーストボードの画像を読み込めませんでした。編集から削除して、もう一度保存してください"
internal const val FUTABER_PASTEBOARD_SAVE_FAILED = "ペーストボードに保存できませんでした"

/** A new row's id: the time it was kept and a few random letters, so two rows kept in the same instant still differ. */
internal fun futaberNewPasteId(): String =
    kotlin.time.Clock.System.now().toEpochMilliseconds().toString(36) + "-" + kotlin.random.Random.nextInt(0, 46_656).toString(36)

/** A kept text put into the writing: after what is there, on its own line. */
internal fun futaberInsertPaste(comment: String, text: String): String = when {
    comment.isEmpty() -> text
    comment.endsWith("\n") -> comment + text
    else -> comment + "\n" + text
}

/** What the writing screen needs to offer the board; made by the app (FutaberApp), null where nothing can be kept. */
internal class FutaberPasteboardLink(
    val items: List<FutaberPasteItem>,
    /** Keeps the text in the folder shown (null = the top). */
    val onAddText: (text: String, folder: String?) -> Unit,
    val onAddImage: (image: ImageData, folder: String?) -> Unit,
    val onRemove: (FutaberPasteItem) -> Unit,
    /** The edit mode's 削除・移動・新規フォルダ・並べ替え: a change to the stored list (the pictures of the rows that go are deleted). */
    val onEdit: ((List<FutaberPasteItem>) -> List<FutaberPasteItem>) -> Unit,
    /** Reads a kept picture (for the thumbnail of a row on screen, and to attach it). */
    val loadImage: suspend (FutaberPasteItem) -> Result<ImageData>
)
