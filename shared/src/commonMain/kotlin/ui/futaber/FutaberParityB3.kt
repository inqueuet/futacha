package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.valoser.futacha.shared.compat.stableCompatHash
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.network.BoardPostingCapabilities
import com.valoser.futacha.shared.ui.compat.COMPAT_POST_ATTACHMENT_DIRECTORY
import com.valoser.futacha.shared.ui.compat.compatPostAttachmentContainer
import com.valoser.futacha.shared.ui.compat.deleteCompatPostAttachment
import com.valoser.futacha.shared.ui.compat.loadCompatPostAttachment
import com.valoser.futacha.shared.ui.compat.persistCompatPostAttachment
import com.valoser.futacha.shared.util.FileSystem
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

/*
 * The B3 batch of the mode parity work (docs/mode-feature-parity-plan-2026-10-12.md §5.3, docs/mode-data-contract-2026-10-12.md
 * D3 and §4.3):
 *  - M3: this mode's own lists (tabs, favourites, posts seen, drafts) name an official board by the key the other modes use
 *    (a hash of its canonical address) instead of the board's id in the board list, which depends on the order the boards
 *    were added and so differs after a restore or on another device. Both are read; only the new one is written, so an older
 *    value moves over with the next change. A board that is not an official one (the tutorial board) keeps its id.
 *    The screens keep working with the board ids; the translation is done where the lists are read and written.
 *  - P07: with the extension "下書きに添付も残す" on (off by default) the draft also keeps its attachment: the bytes go to a
 *    private file (the same place and form としあき(仮) keeps its draft attachments in), the draft notes where.
 */

private const val PARITY_B3_TAG = "FutaberParityB3"

// ---- M3: the board part of the keys ----

/** The board id written into this mode's lists: the shared key of an official board, else the board's own id. */
internal fun futaberStoredBoardId(board: BoardSummary): String = futaberNgScopeKey(board) ?: board.id

/**
 * Stored board id -> the id of the registered board it names. A board's own id wins over a shared key (they cannot look
 * alike: the shared keys start with `compat_board_`).
 */
internal fun futaberStoredBoardIndex(boards: List<BoardSummary>): Map<String, String> {
    val index = HashMap<String, String>()
    boards.forEach { board -> futaberNgScopeKey(board)?.let { key -> if (key !in index) index[key] = board.id } }
    boards.forEach { board -> index[board.id] = board.id }
    return index
}

private fun storedIdsOf(boards: List<BoardSummary>): Map<String, String> =
    boards.associate { it.id to futaberStoredBoardId(it) }

/**
 * The tabs as the screens use them (board ids of the board list). A stored key whose board is not registered stays as it
 * was stored: the automatic clean-up ([futaberPruneTabsOfRemovedBoards]) then drops it as the tab of a removed board, as
 * it always did; one of a registered board, in the old form or the new, is kept.
 */
internal fun futaberTabsFromStore(stored: String?, boards: List<BoardSummary>): List<FutaberTabKey> {
    val index = futaberStoredBoardIndex(boards)
    return decodeFutaberTabs(stored).map { key -> FutaberTabKey(index[key.boardId] ?: key.boardId, key.threadId) }.distinct()
}

/** The stored text for [keys]: official boards by their shared key. */
internal fun futaberTabsToStore(keys: List<FutaberTabKey>, boards: List<BoardSummary>): String {
    val stored = storedIdsOf(boards)
    return encodeFutaberTabs(keys.map { key -> FutaberTabKey(stored[key.boardId] ?: key.boardId, key.threadId) }.distinct())
}

internal fun futaberFavoritesFromStore(stored: String?, boards: List<BoardSummary>): List<FutaberFavorite> {
    val index = futaberStoredBoardIndex(boards)
    return decodeFutaberFavorites(stored).map { favorite ->
        index[favorite.boardId]?.let { favorite.copy(boardId = it) } ?: favorite
    }.distinctBy { it.key }
}

internal fun futaberFavoritesToStore(favorites: List<FutaberFavorite>, boards: List<BoardSummary>): String {
    val stored = storedIdsOf(boards)
    return encodeFutaberFavorites(
        favorites.map { favorite -> stored[favorite.boardId]?.let { favorite.copy(boardId = it) } ?: favorite }.distinctBy { it.key }
    )
}

/** The ids a board's records may be stored under: its own id (older values) and its shared key. */
private fun storedIdsFor(board: BoardSummary): Set<String> = setOf(board.id, futaberStoredBoardId(board))

/** The posts seen the last time the thread was left, whichever form of the board id the record has. */
internal fun futaberSeenBoundaryForBoard(stored: String?, board: BoardSummary, threadId: String): FutaberSeenCount? {
    val ids = storedIdsFor(board)
    return decodeFutaberSeenCounts(stored).lastOrNull { it.boardId in ids && it.threadId == threadId }
}

internal fun futaberSeenCountForBoard(stored: String?, board: BoardSummary, threadId: String): Int? {
    val ids = storedIdsFor(board)
    return decodeFutaberSeenCounts(stored).lastOrNull { it.boardId in ids && it.threadId == threadId }?.seen
}

/**
 * The stored value after the thread was left with [seen] posts on screen, written under the board's stored id (an older
 * record of the thread under the board's own id goes); null when nothing needs writing.
 */
internal fun futaberSeenCountsAfterForBoard(stored: String?, board: BoardSummary, threadId: String, seen: Int, lastPostId: String? = null): String? {
    if (seen <= 0 || threadId.isBlank()) return null
    val storedId = futaberStoredBoardId(board)
    val ids = storedIdsFor(board)
    val current = decodeFutaberSeenCounts(stored)
    val matching = current.filter { it.boardId in ids && it.threadId == threadId }
    val last = current.lastOrNull()
    if (matching.size == 1 && last == matching[0] && last.boardId == storedId && last.seen == seen && (lastPostId == null || last.lastPostId == lastPostId)) return null
    return encodeFutaberSeenCounts(current.filterNot { it.boardId in ids && it.threadId == threadId } + FutaberSeenCount(storedId, threadId, seen, lastPostId ?: matching.lastOrNull()?.takeIf { it.seen == seen }?.lastPostId))
}

/** The key the draft of [target] is written under. */
internal fun futaberDraftStoreKey(target: FutaberPostTarget, boards: List<BoardSummary>): String {
    val boardId = boards.firstOrNull { it.id == target.boardId }?.let(::futaberStoredBoardId) ?: target.boardId
    return when (target) {
        is FutaberPostTarget.Reply -> "r:$boardId:${target.threadId}"
        is FutaberPostTarget.CreateThread -> "c:$boardId"
    }
}

/** The keys the draft of [target] may be stored under: the new one first, then the older form. */
internal fun futaberDraftStoreKeys(target: FutaberPostTarget, boards: List<BoardSummary>): List<String> =
    listOf(futaberDraftStoreKey(target, boards), target.draftKey).distinct()

/** The draft of [target] under either key (the latest one when both are there). */
internal fun futaberDraftForTarget(drafts: List<FutaberDraft>, target: FutaberPostTarget, boards: List<BoardSummary>): FutaberDraft? {
    val keys = futaberDraftStoreKeys(target, boards)
    return drafts.filter { it.key in keys }.maxByOrNull { it.updatedAt }
}

/** [drafts] without any draft of [target] (the post went out, or it was thrown away). */
internal fun futaberDiscardDraft(drafts: List<FutaberDraft>, target: FutaberPostTarget, boards: List<BoardSummary>): List<FutaberDraft> {
    val keys = futaberDraftStoreKeys(target, boards)
    return drafts.filterNot { it.key in keys }
}

/** [drafts] after the text of [target] changed; the attachment noted for it stays, and an older-key entry moves over. */
internal fun futaberDraftsWithText(
    drafts: List<FutaberDraft>,
    target: FutaberPostTarget,
    boards: List<BoardSummary>,
    subject: String,
    comment: String,
    nowEpochMillis: Long
): List<FutaberDraft> {
    val existing = futaberDraftForTarget(drafts, target, boards)
    val draft = FutaberDraft(
        key = futaberDraftStoreKey(target, boards),
        subject = subject,
        comment = comment,
        updatedAt = nowEpochMillis,
        attachmentLocator = existing?.attachmentLocator,
        attachmentIsHandwriting = existing?.attachmentIsHandwriting ?: false
    )
    return futaberUpsertDraft(futaberDiscardDraft(drafts, target, boards), draft)
}

/** [drafts] after the attachment of [target] changed ([locator] null = none). A missing draft with no file stays missing. */
internal fun futaberDraftsWithAttachment(
    drafts: List<FutaberDraft>,
    target: FutaberPostTarget,
    boards: List<BoardSummary>,
    locator: String?,
    isHandwriting: Boolean,
    nowEpochMillis: Long
): List<FutaberDraft> {
    val existing = futaberDraftForTarget(drafts, target, boards)
    if (existing == null && locator == null) return drafts
    if (existing != null && existing.attachmentLocator == locator && existing.attachmentIsHandwriting == (isHandwriting && locator != null)) {
        return drafts
    }
    val draft = (existing ?: FutaberDraft(key = futaberDraftStoreKey(target, boards))).copy(
        key = futaberDraftStoreKey(target, boards),
        updatedAt = nowEpochMillis,
        attachmentLocator = locator,
        attachmentIsHandwriting = isHandwriting && locator != null
    )
    return futaberUpsertDraft(futaberDiscardDraft(drafts, target, boards), draft)
}

/** Every draft without its attachment (the extension was turned off); a draft that kept only a file goes. */
internal fun futaberDraftsWithoutAttachments(drafts: List<FutaberDraft>): List<FutaberDraft> =
    drafts.map { it.copy(attachmentLocator = null, attachmentIsHandwriting = false) }.filterNot { it.isEmpty }

/** The owner name the draft files are made under: their folders all begin with its hash, so only those are ever deleted. */
internal const val FUTABER_DRAFT_ATTACHMENT_OWNER = "futaber-draft"

/** Whether [locator] is a draft file this mode made (a file of としあき(仮)'s drafts is never touched). */
internal fun futaberOwnsDraftAttachment(locator: String): Boolean {
    val container = compatPostAttachmentContainer(locator) ?: return false
    return container.substringAfterLast('/').startsWith(stableCompatHash(FUTABER_DRAFT_ATTACHMENT_OWNER) + "-")
}

/** The files [before] noted that [after] no longer does: a draft was sent, cleared, pushed out or got another file. */
internal fun futaberDroppedDraftLocators(before: List<FutaberDraft>, after: List<FutaberDraft>): Set<String> {
    val kept = after.mapNotNullTo(HashSet()) { it.attachmentLocator }
    return before.mapNotNullTo(LinkedHashSet()) { it.attachmentLocator }
        .filterTo(LinkedHashSet()) { it !in kept && futaberOwnsDraftAttachment(it) }
}

/**
 * Changes the stored drafts by [transform] (applied to the value stored at that moment) and deletes the files of the
 * drafts that no longer note them. Nothing is written when nothing changed. The files go only once the drafts are saved
 * ([editPreference] calls its last argument then): if the save fails, the drafts still note them (audit M14; they used to
 * be deleted before the save, and a failed save left drafts pointing at files that were gone).
 */
internal fun futaberEditDrafts(
    editPreference: (key: String, transform: (String?) -> String?, afterSaved: () -> Unit) -> Unit,
    onDropped: (Set<String>) -> Unit,
    transform: (List<FutaberDraft>) -> List<FutaberDraft>
) {
    var dropped: Set<String> = emptySet()
    editPreference(FutaberPreferenceKeys.DRAFTS, { stored ->
        val before = decodeFutaberDrafts(stored)
        val after = transform(before)
        if (after == before) return@editPreference null
        dropped = futaberDroppedDraftLocators(before, after)
        encodeFutaberDrafts(after)
    }) { if (dropped.isNotEmpty()) onDropped(dropped) }
}


/**
 * Deletes this mode's draft folders that no stored draft notes (audit M14: a file written while the drafts could not be
 * saved, or left by a mode switch or the app ending in the middle, stayed for good). Only this mode's folders are looked
 * at (としあき(仮)'s are never touched), and only while [stillIdle] says nothing is attached on the write screen: a file
 * being kept right now would not be noted yet. Returns how many folders went.
 */
internal suspend fun futaberSweepDraftFiles(
    fileSystem: FileSystem,
    drafts: List<FutaberDraft>,
    stillIdle: () -> Boolean
): Int {
    val noted = drafts.mapNotNullTo(mutableSetOf()) { draft -> draft.attachmentLocator?.let(::compatPostAttachmentContainer)?.substringAfterLast('/') }
    val ownPrefix = stableCompatHash(FUTABER_DRAFT_ATTACHMENT_OWNER) + "-"
    val stale = fileSystem.listFiles(COMPAT_POST_ATTACHMENT_DIRECTORY).filter { it.startsWith(ownPrefix) && it !in noted }
    var removed = 0
    for (folder in stale) {
        if (!stillIdle()) break
        fileSystem.deleteRecursively("$COMPAT_POST_ATTACHMENT_DIRECTORY/$folder")
            .onSuccess { removed++ }
            .onFailure { Logger.e(PARITY_B3_TAG, "Failed to delete a stale draft attachment", it) }
    }
    return removed
}

/** Told when the file a draft kept could not be read back (deleted by "添付・一時ファイルのクリア", or damaged). */
internal const val FUTABER_DRAFT_ATTACHMENT_LOST_NOTICE = "下書きの添付ファイルを読み込めなかったため、添付が外れました。もう一度選んでください"
internal const val FUTABER_DRAFT_ATTACHMENT_SAVE_FAILED_NOTICE = "添付を下書きに残せませんでした（書き込みにはそのまま使えます）"

/** Deletes draft files this mode made; a failure is only logged (the file is unreferenced either way). */
internal fun futaberDeleteDraftFiles(scope: CoroutineScope, fileSystem: FileSystem?, locators: Set<String>) {
    if (fileSystem == null || locators.isEmpty()) return
    scope.launch {
        locators.filter(::futaberOwnsDraftAttachment).forEach { locator ->
            try {
                deleteCompatPostAttachment(fileSystem, locator, deleteContainer = true)
                    .onFailure { Logger.e(PARITY_B3_TAG, "Failed to delete a draft attachment", it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(PARITY_B3_TAG, "Failed to delete a draft attachment", error)
            }
        }
    }
}

/** What the sync remembers between its runs: the attachment that is already in the draft. */
@Stable
private class FutaberDraftAttachmentMemory {
    var savedImage: ImageData? = null
    var adoptNext: Boolean = false
}

/**
 * Keeps the attachment of the post being written in its draft while [enabled] (the extension "下書きに添付も残す"):
 *  - opening the write screen with no file attached brings the draft's file back, through [FutaberAttachment.offer] so the
 *    board's present limits are checked again;
 *  - a new file is written to a private file and noted in the draft; removing it (or another file) deletes the old one;
 *  - turning the extension off deletes every draft file and the notes of them.
 * The text of the draft is kept by the caller as before.
 */
@Composable
internal fun FutaberDraftAttachmentSync(
    enabled: Boolean,
    fileSystem: FileSystem?,
    scope: CoroutineScope,
    target: FutaberPostTarget?,
    attachment: FutaberAttachment,
    boards: List<BoardSummary>,
    /** The key of the post being written now (read when a file arrives: the screen may have moved on). */
    currentTargetKey: () -> String?,
    /** The limits to check a file brought back against; null when the board is gone (nothing is brought back). */
    capabilitiesFor: (FutaberPostTarget) -> BoardPostingCapabilities?,
    loadDrafts: suspend () -> List<FutaberDraft>,
    editDrafts: ((List<FutaberDraft>) -> List<FutaberDraft>) -> Unit,
    /** Notes [target] as the owner of the attached file (after the screen was rebuilt the owner is not known). */
    adoptOwner: (String) -> Unit,
    notify: (String) -> Unit
) {
    val memory = remember { FutaberDraftAttachmentMemory() }
    val latestBoards by rememberUpdatedState(boards)
    val latestEnabled by rememberUpdatedState(enabled)
    LaunchedEffect(enabled) {
        if (!enabled) {
            memory.savedImage = null
            editDrafts(::futaberDraftsWithoutAttachments)
        }
    }
    LaunchedEffect(target?.draftKey, enabled, fileSystem) {
        val writing = target ?: return@LaunchedEffect
        if (!enabled || fileSystem == null) return@LaunchedEffect
        adoptOwner(writing.draftKey)
        if (attachment.image == null && !attachment.processing) {
            val draft = try {
                futaberDraftForTarget(loadDrafts(), writing, latestBoards)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e(PARITY_B3_TAG, "Failed to read the drafts", error)
                null
            }
            val locator = draft?.attachmentLocator
            if (locator != null) {
                val loaded = try {
                    loadCompatPostAttachment(fileSystem, locator)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                val capabilities = capabilitiesFor(writing)
                if (capabilities != null) loaded.onSuccess { image ->
                    // Checked again against what the board allows now (size, formats, replies with files).
                    memory.adoptNext = true
                    attachment.offer(image.copy(isHandwriting = draft.attachmentIsHandwriting), capabilities, writing is FutaberPostTarget.Reply)
                }.onFailure { error ->
                    Logger.e(PARITY_B3_TAG, "A draft attachment could not be read back", error)
                    notify(FUTABER_DRAFT_ATTACHMENT_LOST_NOTICE)
                    editDrafts { futaberDraftsWithAttachment(it, writing, latestBoards, null, false, now()) }
                }
            }
        }
        snapshotFlow { attachment.image to attachment.processing }.collect { (image, processing) ->
            if (processing) return@collect
            // The screen moved on to another post (its file was just cleared): that is not a change of this draft's file.
            if (currentTargetKey() != writing.draftKey) return@collect
            if (image != null && image === memory.savedImage) return@collect
            if (image != null && memory.adoptNext) {
                // The file just brought back from the draft: it is the one the draft notes already.
                memory.adoptNext = false
                memory.savedImage = image
                return@collect
            }
            memory.adoptNext = false
            memory.savedImage = image
            if (image == null) {
                if (attachment.pendingCompression == null) {
                    editDrafts { futaberDraftsWithAttachment(it, writing, latestBoards, null, false, now()) }
                }
                return@collect
            }
            // Written outside this effect, so leaving the screen does not cut the file in half; whether it is still wanted
            // is checked when it is written.
            scope.launch {
                val locator = try {
                    persistCompatPostAttachment(fileSystem, FUTABER_DRAFT_ATTACHMENT_OWNER, image).getOrThrow()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e(PARITY_B3_TAG, "Failed to keep the attachment with the draft", error)
                    notify(FUTABER_DRAFT_ATTACHMENT_SAVE_FAILED_NOTICE)
                    return@launch
                }
                // Still the attached file (the screen may have been closed meanwhile: the file is kept for when it opens
                // again). Another post, a sent post or another file took its place: it is not needed.
                // The extension was turned off meanwhile (its drafts were just cleared of files): the file is not needed either
                // (audit M15: it used to be noted again after the clearing).
                if (attachment.image !== image || !latestEnabled) {
                    futaberDeleteDraftFiles(scope, fileSystem, setOf(locator))
                    return@launch
                }
                // Checked again inside the edit, which runs in order with the others (audit M15: a post sent between the check
                // above and this write discarded its draft, and the write then made a draft with the file again).
                editDrafts { drafts ->
                    if (attachment.image === image && latestEnabled) {
                        futaberDraftsWithAttachment(drafts, writing, latestBoards, locator, image.isHandwriting, now())
                    } else {
                        futaberDeleteDraftFiles(scope, fileSystem, setOf(locator))
                        drafts
                    }
                }
            }
        }
    }
}

@OptIn(kotlin.time.ExperimentalTime::class)
private fun now(): Long = Clock.System.now().toEpochMilliseconds()
