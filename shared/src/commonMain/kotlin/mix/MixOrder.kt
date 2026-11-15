package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.model.CatalogMode

fun MixOrderKind.fetchMode(): CatalogMode = when (this) {
    MixOrderKind.ALTERNATE -> CatalogMode.Catalog
    MixOrderKind.NEW -> CatalogMode.New
    MixOrderKind.OLD -> CatalogMode.Old
    MixOrderKind.MANY -> CatalogMode.Many
    MixOrderKind.FEW -> CatalogMode.Few
    MixOrderKind.MOMENTUM -> CatalogMode.Momentum
    MixOrderKind.SO -> CatalogMode.So
}

object MixOrder {
    /** 各板のサーバーの並びを保ち、板の順に1件ずつ取った順を交互の順位とする。 */
    private fun alternateRanks(entries: List<MixEntry>): Map<String, Int> {
        val perBoard = LinkedHashMap<String, ArrayDeque<MixEntry>>()
        entries.forEach { perBoard.getOrPut(it.key.boardUrl) { ArrayDeque() }.addLast(it) }
        val ranks = HashMap<String, Int>()
        var n = 0
        while (perBoard.values.any { it.isNotEmpty() }) {
            for (q in perBoard.values) q.removeFirstOrNull()?.let { ranks[it.key.asString()] = n++ }
        }
        return ranks
    }

    fun order(entries: List<MixEntry>, kind: MixOrderKind): List<MixEntry> {
        if (entries.isEmpty()) return entries
        val ranks = alternateRanks(entries)
        fun alt(e: MixEntry) = ranks[e.key.asString()] ?: Int.MAX_VALUE
        val boardIndex = LinkedHashMap<String, Int>().also { m -> entries.forEach { if (it.key.boardUrl !in m) m[it.key.boardUrl] = m.size } }
        return when (kind) {
            MixOrderKind.ALTERNATE -> entries.sortedBy(::alt)
            MixOrderKind.NEW, MixOrderKind.OLD -> {
                val (withTime, without) = entries.partition { it.createdAtMillis != null }
                val sorted = if (kind == MixOrderKind.NEW)
                    withTime.sortedWith(compareByDescending<MixEntry> { it.createdAtMillis }.thenBy(::alt))
                else withTime.sortedWith(compareBy<MixEntry> { it.createdAtMillis }.thenBy(::alt))
                sorted + without.sortedBy(::alt)
            }
            MixOrderKind.MANY -> entries.sortedWith(compareByDescending<MixEntry> { it.item.replyCount }.thenBy(::alt))
            MixOrderKind.FEW -> entries.sortedWith(compareBy<MixEntry> { it.item.replyCount }.thenBy(::alt))
            MixOrderKind.MOMENTUM, MixOrderKind.SO -> entries.sortedWith(
                compareBy<MixEntry> { if (it.serverCount <= 1) 0.0 else it.serverRank / (it.serverCount - 1.0) }
                    .thenBy { boardIndex[it.key.boardUrl] ?: 0 }.thenBy { it.serverRank }
            )
        }
    }
}
