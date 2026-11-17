package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class CompatTextCacheTest {
    @Test
    fun cachedDerivationsMatchUncachedResults() {
        val html = "&gt;No.123<br>本文 https://example.com/a?b=1 fu12345.jpg<br><a href=\"https://may.2chan.net/b/src/1.jpg\">1.jpg</a>"
        assertEquals(html.toCompatPlainText(), html.toCompatPlainTextCached())
        assertEquals(compatInlineLinks(html), compatInlineLinksCached(html))
        // A second lookup returns the cached instance instead of re-parsing.
        assertSame(compatInlineLinksCached(html), compatInlineLinksCached(html))
    }

    @Test
    fun cacheEvictsLeastRecentlyUsedEntriesWithinBounds() {
        var builds = 0
        val cache = CompatTextDerivationCache<String>(
            maxEntries = 2,
            maxChars = 100,
            sizeOf = { key, value -> (key.length + value.length).toLong() }
        )
        val build: (String) -> String = { builds += 1; it.uppercase() }
        assertEquals("A", cache.getOrBuild("a", build))
        assertEquals("B", cache.getOrBuild("b", build))
        cache.getOrBuild("a", build) // refresh "a"; "b" is now eldest
        cache.getOrBuild("c", build)
        assertEquals(3, builds)
        assertEquals(2, cache.size)
        cache.getOrBuild("a", build)
        assertEquals(3, builds)
        cache.getOrBuild("b", build)
        assertEquals(4, builds)
        // An entry larger than the whole budget is returned but not retained.
        val huge = "x".repeat(80)
        assertEquals(huge.uppercase(), cache.getOrBuild(huge, build))
        assertEquals(2, cache.size)
    }
}
