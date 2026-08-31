package com.valoser.futacha.shared.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AnalyticsContextTest {

    @Test
    fun sessionContextIdIsStableWithinTheProcessButDoesNotExposeTheSource() {
        val first = analyticsSessionContextId("thread", "https://example.com/b/res/123.htm", "123")
        val repeated = analyticsSessionContextId("thread", "https://example.com/b/res/123.htm", "123")
        val different = analyticsSessionContextId("thread", "https://example.com/b/res/124.htm", "124")

        assertEquals(first, repeated)
        assertNotEquals(first, different)
        assertNotEquals("123", first)
    }

    @Test
    fun textMetadataOnlyReportsCoarseStructure() {
        assertEquals("2_5", analyticsTextLengthBucket("abc"))
        assertEquals("2_5", analyticsTextLineCountBucket("first\nsecond"))
        assertEquals("yes", analyticsTextHasUrl("see https://example.com/path"))
        assertEquals("no", analyticsTextHasUrl("plain comment"))
    }

    @Test
    fun failureCategoryBoundsUntrustedMessagesBeforeClassification() {
        val hugeMessage = "x".repeat(2_000_000) + " timeout"

        assertEquals("unknown", analyticsFailureCategory(IllegalStateException(hugeMessage)))
        assertEquals("timeout", analyticsFailureCategory(IllegalStateException("request timeout")))
    }
}
