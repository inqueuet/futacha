package com.valoser.futacha.shared.ui.board

import kotlin.test.Test
import kotlin.test.assertEquals

/** may 1449174385: "題名からNGワードを登録" saves the trimmed words and refuses an empty one. */
class FutachaCatalogTitleNgTest {
    @Test
    fun theTrimmedWordIsAdded() {
        assertEquals(FutachaTitleNgResult.Added(listOf("a", "ウマ娘")), futachaAddCatalogNgWord(listOf("a"), "  ウマ娘 "))
    }

    @Test
    fun emptyAndDuplicatesAreRefusedWithAReason() {
        val empty = futachaAddCatalogNgWord(listOf("a"), "   ")
        kotlin.test.assertTrue(empty is FutachaTitleNgResult.Rejected && empty.reason.contains("空"))
        val dup = futachaAddCatalogNgWord(listOf("ウマ娘"), "ウマ娘")
        kotlin.test.assertTrue(dup is FutachaTitleNgResult.Rejected && dup.reason.contains("すでに"))
    }
}
