package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.normalizeBoardUrl

internal data class AddBoardValidationState(
    val trimmedName: String,
    val trimmedUrl: String,
    val normalizedInputUrl: String,
    val hasName: Boolean,
    val hasUrl: Boolean,
    val hasScheme: Boolean,
    val isValidUrl: Boolean,
    val isDuplicateUrl: Boolean,
    val canSubmit: Boolean,
    val helperText: String?
)

internal data class BoardManagementChromeState(
    val title: String,
    val showsBackButton: Boolean
)

internal data class BoardManagementEditModeState(
    val isDeleteMode: Boolean,
    val isReorderMode: Boolean
)

internal data class BoardManagementMenuActionState(
    val editModeState: BoardManagementEditModeState,
    val shouldShowAddDialog: Boolean,
    val shouldShowGlobalSettings: Boolean,
    val shouldHandleInternally: Boolean
)

internal fun buildAddBoardValidationState(
    name: String,
    url: String,
    existingBoards: List<BoardSummary>
): AddBoardValidationState {
    val trimmedName = name.trim()
    val trimmedUrl = url.trim()
    val hasName = trimmedName.isNotEmpty()
    val hasUrl = trimmedUrl.isNotEmpty()
    val ipv4Regex = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")
    val hasScheme = trimmedUrl.startsWith("http://", ignoreCase = true) ||
        trimmedUrl.startsWith("https://", ignoreCase = true)
    val normalizedInputUrl = runCatching { normalizeBoardUrl(trimmedUrl) }.getOrDefault(trimmedUrl)
    val normalizedExistingUrls = existingBoards.map { board ->
        runCatching { normalizeBoardUrl(board.url.trim()) }.getOrDefault(board.url.trim())
    }
    val isValidUrl = hasUrl && hasScheme && run {
        val urlWithoutScheme = trimmedUrl.removePrefix("https://").removePrefix("http://")
        val hostPart = urlWithoutScheme.substringBefore("/").substringBefore("?").substringBefore("#")
        hostPart.isNotEmpty() &&
            !hostPart.startsWith(".") &&
            !hostPart.endsWith(".") &&
            !hostPart.contains("..") &&
            !hostPart.contains(" ") &&
            (hostPart.contains(".") ||
                hostPart.equals("localhost", ignoreCase = true) ||
                hostPart.matches(ipv4Regex))
    }
    val isDuplicateUrl = hasUrl && isValidUrl &&
        normalizedExistingUrls.any { it.equals(normalizedInputUrl, ignoreCase = true) }
    val helperText = when {
        isDuplicateUrl -> "同じURLの板が既に登録されています"
        hasUrl && !hasScheme -> "http:// もしくは https:// から始まるURLを入力してください"
        hasUrl && !isValidUrl -> "有効なURLを入力してください（例: https://example.com/board）"
        else -> null
    }
    return AddBoardValidationState(
        trimmedName = trimmedName,
        trimmedUrl = trimmedUrl,
        normalizedInputUrl = normalizedInputUrl,
        hasName = hasName,
        hasUrl = hasUrl,
        hasScheme = hasScheme,
        isValidUrl = isValidUrl,
        isDuplicateUrl = isDuplicateUrl,
        canSubmit = hasName && isValidUrl && !isDuplicateUrl,
        helperText = helperText
    )
}

internal fun resolveBoardManagementChromeState(
    isDeleteMode: Boolean,
    isReorderMode: Boolean
): BoardManagementChromeState {
    return when {
        isDeleteMode -> BoardManagementChromeState(
            title = "削除する板を選択",
            showsBackButton = true
        )
        isReorderMode -> BoardManagementChromeState(
            title = "板の順序を変更",
            showsBackButton = true
        )
        else -> BoardManagementChromeState(
            title = "ふたば",
            showsBackButton = false
        )
    }
}

internal fun clearBoardManagementEditModes(): BoardManagementEditModeState {
    return BoardManagementEditModeState(
        isDeleteMode = false,
        isReorderMode = false
    )
}

internal fun resolveBoardManagementMenuActionState(
    isDeleteMode: Boolean,
    isReorderMode: Boolean,
    action: BoardManagementMenuAction
): BoardManagementMenuActionState {
    return when (action) {
        BoardManagementMenuAction.ADD -> BoardManagementMenuActionState(
            editModeState = BoardManagementEditModeState(
                isDeleteMode = isDeleteMode,
                isReorderMode = isReorderMode
            ),
            shouldShowAddDialog = true,
            shouldShowGlobalSettings = false,
            shouldHandleInternally = true
        )
        BoardManagementMenuAction.DELETE -> BoardManagementMenuActionState(
            editModeState = BoardManagementEditModeState(
                isDeleteMode = !isDeleteMode,
                isReorderMode = false
            ),
            shouldShowAddDialog = false,
            shouldShowGlobalSettings = false,
            shouldHandleInternally = true
        )
        BoardManagementMenuAction.REORDER -> BoardManagementMenuActionState(
            editModeState = BoardManagementEditModeState(
                isDeleteMode = false,
                isReorderMode = !isReorderMode
            ),
            shouldShowAddDialog = false,
            shouldShowGlobalSettings = false,
            shouldHandleInternally = true
        )
        BoardManagementMenuAction.SAVED_THREADS -> BoardManagementMenuActionState(
            editModeState = BoardManagementEditModeState(
                isDeleteMode = isDeleteMode,
                isReorderMode = isReorderMode
            ),
            shouldShowAddDialog = false,
            shouldShowGlobalSettings = false,
            shouldHandleInternally = false
        )
        BoardManagementMenuAction.SETTINGS -> BoardManagementMenuActionState(
            editModeState = BoardManagementEditModeState(
                isDeleteMode = isDeleteMode,
                isReorderMode = isReorderMode
            ),
            shouldShowAddDialog = false,
            shouldShowGlobalSettings = true,
            shouldHandleInternally = true
        )
    }
}

internal fun createCustomBoardSummary(
    name: String,
    url: String,
    existingBoards: List<BoardSummary>
): BoardSummary {
    val trimmedName = name.trim().ifBlank { "新しい板" }
    val normalizedUrl = url.trim()
    val boardId = generateBoardId(normalizedUrl, trimmedName, existingBoards)
    return BoardSummary(
        id = boardId,
        name = trimmedName,
        category = "",
        url = normalizedUrl,
        description = "$trimmedName のユーザー追加板",
        pinned = false
    )
}

internal fun moveBoardSummary(
    boards: List<BoardSummary>,
    index: Int,
    moveUp: Boolean
): List<BoardSummary> {
    if (index !in boards.indices) return boards
    if (moveUp && index == 0) return boards
    if (!moveUp && index == boards.lastIndex) return boards
    val targetIndex = if (moveUp) index - 1 else index + 1
    val mutable = boards.toMutableList()
    val board = mutable.removeAt(index)
    mutable.add(targetIndex, board)
    return mutable
}

internal fun shouldShowBoardManagementScreenAction(
    action: BoardManagementMenuAction
): Boolean {
    return action != BoardManagementMenuAction.SAVED_THREADS
}

private fun generateBoardId(
    url: String,
    fallbackName: String,
    existingBoards: List<BoardSummary>
): String {
    val candidates = buildList {
        extractPathSegment(url)?.let { add(it) }
        extractSubdomain(url)?.let { add(it) }
        val nameSlug = slugify(fallbackName)
        if (nameSlug.isNotBlank()) add(nameSlug)
    }
    val base = candidates.firstOrNull { it.isNotBlank() } ?: "board"
    var candidate = base
    var suffix = 1
    while (existingBoards.any { it.id.equals(candidate, ignoreCase = true) }) {
        candidate = "$base$suffix"
        suffix += 1
    }
    return candidate
}

private fun extractPathSegment(url: String): String? {
    val withoutScheme = url.substringAfter("//", url)
    val slashIndex = withoutScheme.indexOf('/')
    if (slashIndex == -1) return null
    val path = withoutScheme.substring(slashIndex + 1)
        .substringBefore('?')
        .substringBefore('#')
    if (path.isBlank()) return null
    val firstSegment = path.split('/')
        .firstOrNull { it.isNotBlank() }
        ?: return null
    return slugify(firstSegment)
}

private fun extractSubdomain(url: String): String? {
    val withoutScheme = url.substringAfter("//", url)
    val host = withoutScheme.substringBefore('/')
    if (host.isBlank()) return null
    val parts = host.split('.')
    if (parts.isEmpty()) return null
    val first = parts.first()
    if (first.equals("example", ignoreCase = true)) return null
    return slugify(first)
}

private fun slugify(value: String): String {
    val normalized = value.lowercase()
    val builder = StringBuilder()
    normalized.forEach { ch ->
        when {
            ch.isLetterOrDigit() -> builder.append(ch)
            ch == '-' || ch == '_' -> builder.append(ch)
        }
    }
    return builder.toString()
}
