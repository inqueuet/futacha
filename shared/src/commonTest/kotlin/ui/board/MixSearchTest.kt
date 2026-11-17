package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.mix.*
import com.valoser.futacha.shared.model.*
import kotlin.test.*

/** The まとめカタログ's thread search. */
class MixSearchTest {
    private fun entry(boardUrl: String, id: String, title: String?) = MixEntry(
        BoardSummary(boardUrl, boardUrl, "c", boardUrl, ""), CatalogItem(id, "https://x/$id", title, null, null, replyCount = 1),
        MixEntryKey(boardUrl, id), null, false, 0, 1)

    private val may = "https://may.2chan.net/b/"
    private val img = "https://img.2chan.net/b/"
    private val rows = listOf(entry(may, "1", "ウマ娘スレ"), entry(img, "2", "猫の画像"), entry(may, "3", null), entry(img, "4", "ｳﾏ娘 そうだね"))

    @Test fun aBlankQueryKeepsEveryRow() {
        MixModePolicy.entries.forEach { assertEquals(rows, filterMixEntriesByQuery(rows, "  　", it)) }
    }

    @Test fun findsRowsFromEveryBoardIgnoringHalfWidthKana() {
        MixModePolicy.entries.forEach { policy ->
            assertEquals(listOf("1", "4"), filterMixEntriesByQuery(rows, "ウマ", policy).map { it.item.id }, policy.name)
        }
    }

    @Test fun everyWordMustMatch() {
        assertEquals(listOf("4"), filterMixEntriesByQuery(rows, "ウマ　そうだね", MixModePolicy.FUTABER).map { it.item.id })
        assertTrue(filterMixEntriesByQuery(rows, "猫 ウマ", MixModePolicy.FUTACHA).isEmpty())
    }

    @Test fun usesTheTitleReadFromTheThreadHead() {
        val titles = mapOf(MixEntryKey(may, "3").asString() to "完全なタイトル")
        assertEquals(listOf("3"), filterMixEntriesByQuery(rows, "完全", MixModePolicy.COMPAT, titles).map { it.item.id })
    }
}
