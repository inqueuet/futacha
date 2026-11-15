package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.board.mixEntriesWithoutNg
import com.valoser.futacha.shared.ui.board.mixEntryWithHeadTitle
import com.valoser.futacha.shared.ui.board.mixEntriesWithoutPhashNg
import kotlin.test.Test
import kotlin.test.assertEquals

class MixNgFilterTest {
    private val may = "https://may.2chan.net/b/"
    private val img = "https://img.2chan.net/b/"
    private fun entry(boardUrl: String, id: String) = MixEntry(
        BoardSummary(boardUrl, boardUrl, "c", boardUrl, ""),
        CatalogItem(id, "$id.htm", "t$id", null, null, replyCount = 1),
        MixEntryKey(boardUrl, id), null, false, 0, 1
    )

    @Test fun aThreadNgHidesOnlyThatBoardsRow() {
        val rule = CompatNgRule(
            "r", CompatNgKind.CATALOG_REFUSE, compatBoardKey(storeCanonicalBoardUrlOf(may) ?: may), "1.htm", 0L
        )
        val rows = listOf(entry(may, "1"), entry(img, "1"), entry(may, "2"))
        val kept = mixEntriesWithoutNg(rows, listOf(rule)).map { it.key.asString() }
        assertEquals(listOf("${img}#1", "${may}#2"), kept)
    }

    @Test fun noRulesKeepsEverything() {
        val rows = listOf(entry(may, "1"))
        assertEquals(rows, mixEntriesWithoutNg(rows, emptyList()))
    }

    @Test fun aPictureNgHidesOnlyASimilarPictureOnThatBoard() {
        val hash = "0".repeat(16)
        val rule = CompatNgRule("p", CompatNgKind.CATALOG_IMAGE_PHASH, compatBoardKey(storeCanonicalBoardUrlOf(may) ?: may), hash, 0L)
        val rows = listOf(entry(may, "1"), entry(img, "1"), entry(may, "2"))
        val hashes = mapOf("${may}#1" to hash, "${img}#1" to hash, "${may}#2" to "f".repeat(16))
        val kept = mixEntriesWithoutPhashNg(rows, listOf(rule), hashes).map { it.key.asString() }
        assertEquals(listOf("${img}#1", "${may}#2"), kept)
    }

    @Test fun openingCarriesTheHeadTitleOnlyWhenThereIsOne() {
        val e = entry(may, "1")
        assertEquals("本当の題", mixEntryWithHeadTitle(e, mapOf("${may}#1" to "本当の題")).item.title)
        assertEquals(e, mixEntryWithHeadTitle(e, mapOf("${may}#1" to " ")))
        assertEquals(e, mixEntryWithHeadTitle(e, emptyMap()))
    }
}
