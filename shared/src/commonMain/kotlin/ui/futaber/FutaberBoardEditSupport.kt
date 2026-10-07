package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.board.buildAddBoardValidationState
import com.valoser.futacha.shared.ui.board.createCustomBoardSummary
import com.valoser.futacha.shared.ui.board.moveBoardSummary

/** What the add-board dialog shows for the text typed so far. */
internal data class FutaberAddBoardState(
    /** Name that will be used: the typed one, else one derived from the address. */
    val resolvedName: String,
    val canSubmit: Boolean,
    val helperText: String?
)

/** Address-only entry is enough: the board is named after its address until renamed elsewhere. */
internal fun futaberDefaultBoardName(rawUrl: String): String {
    val address = futaberBoardAddress(rawUrl)
    return address.removeSuffix("/futaba.php").ifBlank { rawUrl.trim() }
}

internal fun futaberAddBoardState(
    nameInput: String,
    urlInput: String,
    existingBoards: List<BoardSummary>
): FutaberAddBoardState {
    val trimmedUrl = urlInput.trim()
    val resolvedName = nameInput.trim().ifEmpty { futaberDefaultBoardName(trimmedUrl) }.let { futaberSafeTake(it, FUTABER_BOARD_NAME_MAX_CHARS) }
    if (trimmedUrl.isEmpty()) return FutaberAddBoardState(resolvedName, canSubmit = false, helperText = null)
    val validation = buildAddBoardValidationState(resolvedName, trimmedUrl, existingBoards)
    return FutaberAddBoardState(resolvedName, validation.canSubmit, validation.helperText)
}

internal const val FUTABER_BOARD_NAME_MAX_CHARS = 40

/**
 * The list after adding the typed board, or [latest] unchanged when the address is
 * invalid or already registered (checked against the latest stored list).
 */
internal fun futaberAddBoard(
    latest: List<BoardSummary>,
    nameInput: String,
    urlInput: String
): List<BoardSummary> {
    val state = futaberAddBoardState(nameInput, urlInput, latest)
    if (!state.canSubmit) return latest
    val validation = buildAddBoardValidationState(state.resolvedName, urlInput.trim(), latest)
    return latest + createCustomBoardSummary(
        name = state.resolvedName,
        url = validation.normalizedInputUrl,
        existingBoards = latest
    )
}

internal fun futaberDeleteBoard(latest: List<BoardSummary>, boardId: String): List<BoardSummary> =
    latest.filter { it.id != boardId }

/** One step up or down; a board that was deleted meanwhile, or is already at the end, leaves the list as is. */
internal fun futaberMoveBoard(latest: List<BoardSummary>, boardId: String, up: Boolean): List<BoardSummary> {
    val index = latest.indexOfFirst { it.id == boardId }
    return if (index < 0) latest else moveBoardSummary(latest, index, up)
}

/** The list with the board [boardId] renamed; an empty name, an unknown board or an unchanged name leaves it as it is. */
internal fun futaberRenameBoard(latest: List<BoardSummary>, boardId: String, nameInput: String): List<BoardSummary> {
    val name = nameInput.trim().let { futaberSafeTake(it, FUTABER_BOARD_NAME_MAX_CHARS) }
    if (name.isEmpty()) return latest
    val index = latest.indexOfFirst { it.id == boardId }
    if (index < 0 || latest[index].name == name) return latest
    return latest.toMutableList().also { it[index] = it[index].copy(name = name) }
}

/**
 * The list with every board of [discovered] (name to address) that is not registered yet added, in the order found.
 * An address that is invalid or already registered is skipped, as adding it one by one would refuse it.
 */
internal fun futaberAddDiscoveredBoards(latest: List<BoardSummary>, discovered: List<Pair<String, String>>): List<BoardSummary> =
    discovered.fold(latest) { list, (name, url) -> futaberAddBoard(list, name, url) }
