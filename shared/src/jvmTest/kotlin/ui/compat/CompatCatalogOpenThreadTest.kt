package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatTab
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CompatCatalogOpenThreadTest {
    private fun tab(key: String, board: String, no: String) = CompatTab(
        key = key, canonicalUrl = "u$key", originalUrl = "u$key", boardKey = board, boardName = board,
        threadNo = no, title = "t", insertedAtEpochMillis = 0, contentUpdatedAtEpochMillis = 0
    )

    @Test
    fun onlyTheOpenTabOfThisBoardIsMarked() {
        val tabs = listOf(tab("a", "may/b", "1"), tab("b", "img/b", "2"))
        assertEquals("1", compatCatalogOpenThreadNo(tabs, "a", "may/b"))
        assertNull(compatCatalogOpenThreadNo(tabs, "b", "may/b"))
        assertNull(compatCatalogOpenThreadNo(tabs, null, "may/b"))
        assertNull(compatCatalogOpenThreadNo(tabs, "gone", "may/b"))
    }
}

class CompatTabUnreadBadgeTest {
    @Test
    fun theWhiteCountIsReadableOnTheBadge() {
        val badge = COMPAT_NEW_REPLIES_BADGE_COLOR.luminance()
        val ratio = (1.0f + 0.05f) / (badge + 0.05f)
        kotlin.test.assertTrue(ratio >= 4.5f, "contrast $ratio")
    }
}

class CompatThreadCatalogStatusTest {
    private val base = com.valoser.futacha.shared.compat.CompatTab(
        key = "k", canonicalUrl = "u", originalUrl = "u", boardKey = "may/b", boardName = "may",
        threadNo = "5", title = "t", insertedAtEpochMillis = 0, contentUpdatedAtEpochMillis = 0
    )
    private fun dropped(id: String, c: com.valoser.futacha.shared.compat.CompatCatalogDroppedClass) =
        com.valoser.futacha.shared.compat.CompatDroppedCatalogItem(
            "may/b", com.valoser.futacha.shared.model.CatalogItem(id = id, threadUrl = "x", title = null,
                thumbnailUrl = null, fullImageUrl = null, replyCount = 0), 0, classification = c)

    @Test
    fun theTabFlagsComeFirstThenTheCatalog() {
        val iso = com.valoser.futacha.shared.compat.CompatCatalogDroppedClass.ISOLATED
        val die = com.valoser.futacha.shared.compat.CompatCatalogDroppedClass.DIE
        assertNull(compatThreadCatalogStatus(base, emptyList()))
        assertEquals(iso, compatThreadCatalogStatus(base, listOf(dropped("5", iso), dropped("6", die))))
        assertNull(compatThreadCatalogStatus(base, listOf(dropped("6", die))))
        assertEquals(die, compatThreadCatalogStatus(base.copy(isDead = true), listOf(dropped("5", iso)).take(0)))
        assertEquals(iso, compatThreadCatalogStatus(base.copy(isIsolated = true, isDead = true), emptyList()))
    }
}
