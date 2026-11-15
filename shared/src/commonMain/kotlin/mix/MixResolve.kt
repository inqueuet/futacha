package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.siteCanonicalBoardUrlOf
import com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf
import com.valoser.futacha.shared.model.BoardSummary

data class ResolvedMix(
    val definition: MixDefinition,
    val boards: List<BoardSummary>,
    val skipped: Map<String, MixSkipReason>
)

private fun isAimogeUrl(url: String) = url.contains("nijiurachan.net", ignoreCase = true)
private fun isTutorialUrl(url: String) = url.contains("example.com", ignoreCase = true)

/** Pause data must be loaded before resolving, including on a manual retry after a pause expires. */
internal suspend fun resolveMixForFetch(
    definition: MixDefinition,
    boards: List<BoardSummary>,
    aimogeEnabled: Boolean,
    loadPause: suspend () -> Unit,
    sitePaused: () -> Boolean,
    boardPaused: (String) -> Boolean
): ResolvedMix {
    loadPause()
    return resolveMixMembers(definition, boards, aimogeEnabled, sitePaused, boardPaused)
}

/** 一員を板一覧に照らす。飛ばす板は理由つき。板の削除や付け替えには手を入れない。 */
fun resolveMixMembers(
    def: MixDefinition,
    boards: List<BoardSummary>,
    aimogeEnabled: Boolean,
    sitePaused: () -> Boolean,
    boardPaused: (String) -> Boolean
): ResolvedMix {
    val byCanonical = LinkedHashMap<String, BoardSummary>()
    boards.forEach { b -> (storeCanonicalBoardUrlOf(b.url) ?: if (isTutorialUrl(b.url)) b.url else null)?.let { if (it !in byCanonical) byCanonical[it] = b } }
    val resolved = ArrayList<BoardSummary>()
    val skipped = LinkedHashMap<String, MixSkipReason>()
    val seen = HashSet<String>()
    for (member in def.members) {
        val canonical = storeCanonicalBoardUrlOf(member)
        when {
            canonical == null && isTutorialUrl(member) -> skipped[member] = MixSkipReason.TUTORIAL_BOARD
            canonical == null -> {
                // 正規の形を持たない（移行前のhttpのあいもげなど）。移行が済めば照らせる
                skipped[member] = MixSkipReason.UNSUPPORTED_SITE
            }
            !seen.add(canonical) -> skipped[member] = MixSkipReason.DUPLICATE
            isAimogeUrl(canonical) && !aimogeEnabled -> skipped[member] = MixSkipReason.AIMOGE_DISABLED
            else -> {
                val board = byCanonical[canonical]
                when {
                    board == null -> skipped[member] = MixSkipReason.NOT_REGISTERED
                    isTutorialUrl(board.url) -> skipped[member] = MixSkipReason.TUTORIAL_BOARD
                    isAimogeUrl(canonical) && (sitePaused() || boardPaused(siteCanonicalBoardUrlOf(board.url) ?: canonical)) ->
                        skipped[member] = MixSkipReason.SITE_PAUSED
                    else -> resolved += board
                }
            }
        }
    }
    return ResolvedMix(def, resolved, skipped)
}
