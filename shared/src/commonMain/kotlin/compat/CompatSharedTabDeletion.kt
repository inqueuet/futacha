package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

const val COMPAT_SHARED_BOARD_ALIASES = "compat.shared.boardAliases"
private val boardAliasSerializer = MapSerializer(String.serializer(), String.serializer())
private fun sharedBoardAliases(raw: String?): Map<String, String> =
    raw?.let { runCatching { Json.decodeFromString(boardAliasSerializer, it) }.getOrNull() }.orEmpty()

fun updatedCompatBoardAliases(raw: String?, boards: List<BoardSummary>): String {
    val aliases = sharedBoardAliases(raw).toMutableMap()
    boards.forEach { board -> canonicalizeBoardUrl(board.url)?.let { aliases[board.id] = compatBoardKey(it) } }
    val encoded = Json.encodeToString(boardAliasSerializer, aliases)
    requireValidCompatPreference(COMPAT_SHARED_BOARD_ALIASES, encoded)
    return encoded
}

/**
 * The shared tab keys a mode still lists in its own settings rows (plan B4, 1-3): ふたったー風's opening thread. The tab
 * trim leaves it alone. ふたったー風's old tab rows (plan B7b) and ふたばー風's old strip row (plan B8b) are no longer read
 * by the reconciliation: both strips are views of the shared tabs, so their tabs are trimmed like the others and the
 * stale rows keep nothing.
 */
fun compatModeListTabKeys(preferences: Map<String, String>, @Suppress("UNUSED_PARAMETER") boards: List<CompatBoard>): Set<String> =
    buildSet {
        preferences[FtutterKeys.OPENING]?.let { url -> canonicalizeThreadUrl(url)?.let { add(compatTabKey(it.canonicalUrl)) } }
    }

/**
 * The tabs a write removed, so the store drops their drafts and moves the active tab. Run inside the store transaction,
 * before publishing any observable state. It reads no mode's own rows (plans B5, B7b, B8b, B10): a tab closes only when
 * the shared tab goes, and writing a mode's old row closes nothing.
 */
fun compatRemovedTabKeys(beforeTabs: List<CompatTab>, afterTabs: List<CompatTab>): Set<String> =
    beforeTabs.mapTo(HashSet()) { it.key } - afterTabs.mapTo(HashSet()) { it.key }
