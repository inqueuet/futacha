package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.boardSelectorParameter
import com.valoser.futacha.shared.ai.catalogModeParameter
import com.valoser.futacha.shared.ai.threadIdParameter
import com.valoser.futacha.shared.ai.threadUrlParameter
import com.valoser.futacha.shared.compat.CanonicalThreadUrl
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatHost
import com.valoser.futacha.shared.compat.CompatibilityWorkspaceState
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.compatBoardKey

// Resolves the board, thread and catalog order named by a platform AI command
// against the compatibility workspace. Without a board selector the active
// tab's board, then the catalog being shown, is used.

internal fun resolvePlatformAiBoard(
    command: FutachaAiCommand,
    state: CompatibilityWorkspaceState,
    boards: List<CompatBoard>
): CompatBoard? {
    val requested = command.boardSelectorParameter()
    if (requested.isNullOrBlank()) {
        val active = state.activeTabKey?.let { key -> state.tabs.firstOrNull { it.key == key } }
        return active?.let { tab -> boards.firstOrNull { it.key == tab.boardKey } }
            ?: (state.host as? CompatHost.Catalog)?.let { host -> boards.firstOrNull { it.key == host.boardKey } }
    }
    val normalized = requested.trim().lowercase()
    val canonical = canonicalizeBoardUrl(requested)
    return boards.firstOrNull { board ->
        board.key.equals(requested, ignoreCase = true) ||
            board.name.equals(requested, ignoreCase = true) ||
            board.originalUrl.equals(requested, ignoreCase = true) ||
            (canonical != null && board.canonicalUrl.equals(canonical, ignoreCase = true))
    } ?: boards.firstOrNull { board ->
        board.name.lowercase().contains(normalized) || board.key.lowercase().contains(normalized)
    }
}

internal fun resolvePlatformAiThread(
    command: FutachaAiCommand,
    state: CompatibilityWorkspaceState,
    boards: List<CompatBoard>
): Pair<CanonicalThreadUrl, CompatBoard>? {
    val targetUrl = command.threadUrlParameter()
        ?: run {
            val board = resolvePlatformAiBoard(command, state, boards) ?: return@run null
            val number = command.threadIdParameter()?.takeIf { it.all(Char::isDigit) } ?: return@run null
            "${board.canonicalUrl.trimEnd('/')}/res/$number.htm"
        }
        ?: return null
    val parsed = canonicalizeThreadUrl(targetUrl) ?: return null
    val board = boards.firstOrNull { it.canonicalUrl.equals(parsed.canonicalBoardUrl, ignoreCase = true) }
        ?: CompatBoard(
            key = compatBoardKey(parsed.canonicalBoardUrl),
            name = parsed.boardPath.substringAfterLast('/'),
            canonicalUrl = parsed.canonicalBoardUrl,
            originalUrl = parsed.canonicalBoardUrl,
            sortOrder = boards.size
        )
    return parsed to board
}

internal fun resolvePlatformAiCatalogSort(command: FutachaAiCommand): CompatCatalogSort? {
    val raw = command.catalogModeParameter()?.trim()?.lowercase() ?: return null
    return CompatCatalogSort.entries.firstOrNull { sort ->
        sort.name.lowercase() == raw || sort.displayLabel.lowercase() == raw
    } ?: when (raw) {
        "catalog", "cat" -> CompatCatalogSort.CATALOG
        "new", "newest", "newest_first" -> CompatCatalogSort.NEW
        "old", "oldest", "oldest_first" -> CompatCatalogSort.OLD
        "many", "most", "most_replies" -> CompatCatalogSort.MANY
        "few", "least", "fewest_replies" -> CompatCatalogSort.FEW
        "lively", "momentum", "speed" -> CompatCatalogSort.LIVELY
        else -> null
    }
}
