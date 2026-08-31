package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatibilityCatalogProjectionTest {
    private val source = listOf(
        item("a", 10),
        item("b", 2),
        item("c", 5),
        item("d", 8),
        item("e", 1)
    )

    @Test
    fun referencePriorityKeepsSelectedSortOrderAndAppendsFewReplies() {
        val projected = projectCompatCatalogItems(
            items = source,
            replyPriorityEnabled = true,
            replyThreshold = 5,
            showNonPriority = true,
            isExtracted = { it.id in setOf("b", "c") }
        )

        assertEquals(
            listOf("c", "a", "d", "b", "e"),
            projected.map(CatalogItem::id),
            "1.apk promotes extracted entries only inside the priority group and never reply-sorts that group"
        )
    }

    @Test
    fun hidingNonPriorityDropsOnlyEntriesBelowTheConfiguredThreshold() {
        val projected = projectCompatCatalogItems(
            items = source,
            replyPriorityEnabled = true,
            replyThreshold = 5,
            showNonPriority = false,
            isExtracted = { it.id in setOf("b", "c") }
        )

        assertEquals(listOf("c", "a", "d"), projected.map(CatalogItem::id))
    }

    @Test
    fun disablingReplyPriorityMakesThresholdZeroButKeepsExtractionPriority() {
        val projected = projectCompatCatalogItems(
            items = source,
            replyPriorityEnabled = false,
            replyThreshold = 5,
            showNonPriority = false,
            isExtracted = { it.id in setOf("b", "c") }
        )

        assertEquals(listOf("b", "c", "a", "d", "e"), projected.map(CatalogItem::id))
    }

    @Test
    fun freshCatalogMatchesFinalApkSortFlags() {
        val preference = CompatCatalogPreference(boardKey = "may-b")

        assertTrue(preference.replyPriorityEnabled)
        assertTrue(preference.showNonPriority)
        assertEquals(0, preference.fewRepliesDelay)
    }

    @Test
    fun sourceTitleLimitRunsBeforeIndependentGridOrListLimit() {
        val title = "1234567890abcdefghijABCDEFGHIJ"

        val sourceTen = truncateCompatCatalogSourceTitle(title, sourceLimit = 10)
        assertEquals("1234567890", sourceTen)
        assertEquals(
            "1234567890",
            sourceTen.orEmpty().take(30),
            "A longer grid limit must not recover text discarded by CatalogLoader"
        )

        val sourceThirty = truncateCompatCatalogSourceTitle(title, sourceLimit = 30)
        assertEquals("1234567890", sourceThirty.orEmpty().take(10))
    }

    @Test
    fun sourceTitleLimitMatchesReferenceBoundsAndPreservesMissingTitles() {
        val title = "1234567890abcdefghijABCDEFGHIJ-extra"

        assertEquals("1234567890", truncateCompatCatalogSourceTitle(title, sourceLimit = 0))
        assertEquals(title.take(30), truncateCompatCatalogSourceTitle(title, sourceLimit = 100))
        assertNull(truncateCompatCatalogSourceTitle(null, sourceLimit = 20))
    }

    private fun item(id: String, replies: Int) = CatalogItem(
        id = id,
        threadUrl = "https://may.2chan.net/b/res/$id.htm",
        title = id,
        thumbnailUrl = null,
        fullImageUrl = null,
        replyCount = replies
    )
}
