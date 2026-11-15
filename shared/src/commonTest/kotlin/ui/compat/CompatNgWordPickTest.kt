package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatNgWordPickTest {
    @Test fun theChosenPartOfTheThreadTextIsTheWord() {
        assertEquals("ねこ", compatPickedNgWord("今日の ねこ スレ", TextRange(3, 6)))
        assertEquals("今日の ねこ スレ", compatPickedNgWord(" 今日の ねこ スレ ", TextRange(0)))
        assertNull(compatPickedNgWord("", TextRange(0)))
        assertEquals(100, compatPickedNgWord("あ".repeat(150), TextRange(0))!!.length)
        // The menu keeps the original's seven items first.
        assertEquals(compatCatalogContextLabels(), (compatCatalogContextLabels() + COMPAT_CATALOG_PICK_NG_WORD).take(7))
    }
}
