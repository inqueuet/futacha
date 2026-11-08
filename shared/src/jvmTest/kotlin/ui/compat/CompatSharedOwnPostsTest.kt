package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.BoardSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** D-B8: としあき(仮) and AppState's modes see each other's own posts. */
class CompatSharedOwnPostsTest {
    private val may = BoardSummary("may-b", "二次元裏", "", "https://may.2chan.net/b/futaba.php", "")
    private val img = BoardSummary("img-b", "img", "", "https://img.2chan.net/b/futaba.php", "")

    @Test
    fun theBoardIsFoundByItsAddressWhateverTheForm() {
        assertEquals("may-b", compatSharedSelfPostBoard(listOf(img, may), "https://may.2chan.net/b/")?.id)
        assertEquals("may-b", compatSharedSelfPostBoard(listOf(img, may), "https://may.2chan.net/b/futaba.php")?.id)
        assertNull(compatSharedSelfPostBoard(listOf(img), "https://may.2chan.net/b/"))
    }

    @Test
    fun theSharedOwnPostsAreThoseOfThatBoardAndThread() {
        val map = mapOf("may-b::10" to listOf("11", "12"), "img-b::10" to listOf("99"), "may-b::20" to listOf("5"))
        assertEquals(setOf("11", "12"), compatSharedSelfPostNos(listOf(may, img), map, "https://may.2chan.net/b/", "10"))
        assertEquals(emptySet(), compatSharedSelfPostNos(listOf(img), map, "https://may.2chan.net/b/", "10"))
    }

    @Test
    fun onlyWhatAppStateLacksIsHandedOverInNumberOrder() {
        assertEquals(listOf("3", "20"), compatSelfPostNosToShare(setOf("20", "3", "7"), setOf("7", "99")))
        assertEquals(emptyList(), compatSelfPostNosToShare(setOf("7"), setOf("7")))
    }
}
