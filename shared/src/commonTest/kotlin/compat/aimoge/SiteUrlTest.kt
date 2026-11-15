package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.AimogeLinkTarget
import com.valoser.futacha.shared.compat.BoardSite
import com.valoser.futacha.shared.compat.aimogeBoardIdOf
import com.valoser.futacha.shared.compat.aimogeBoardShapeOf
import com.valoser.futacha.shared.compat.aimogeLinkTargetOf
import com.valoser.futacha.shared.compat.aimogeUnverifiedDelEnabled
import com.valoser.futacha.shared.compat.aimogeUnverifiedPostingEnabled
import com.valoser.futacha.shared.compat.boardSiteOf
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeSiteBoardUrl
import com.valoser.futacha.shared.compat.canonicalizeSiteThreadUrl
import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.compat.siteThreadUrlOf
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.ui.board.resolveEffectiveBoardUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

private const val UUID = "0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"
private const val BOARD = "https://nijiurachan.net/b/aimoge/"
private const val THREAD = "https://nijiurachan.net/b/aimoge/thread/$UUID"

internal inline fun <T> withAimoge(enabled: Boolean = true, block: () -> T): T {
    val before = AimogeAvailability.sitesEnabled
    AimogeAvailability.sitesEnabled = enabled
    try {
        return block()
    } finally {
        AimogeAvailability.sitesEnabled = before
    }
}

class SiteUrlTest {
    @Test
    fun everythingIsOffByDefault() {
        assertFalse(AimogeAvailability.sitesEnabled)
        assertFalse(aimogeUnverifiedPostingEnabled())
        assertFalse(aimogeUnverifiedDelEnabled())
        assertEquals(BoardSite.FUTABA, boardSiteOf(BOARD))
        assertNull(canonicalizeSiteBoardUrl(BOARD))
        assertNull(canonicalizeSiteThreadUrl(THREAD))
        assertNull(siteThreadUrlOf(BOARD, UUID))
        assertNull(aimogeLinkTargetOf(THREAD))
        assertNull(aimogeBoardIdOf(BOARD))
        // The shape check does not read the switch.
        assertEquals(BOARD, aimogeBoardShapeOf(THREAD))
    }

    @Test
    fun futabaGivesTheSameStringsAsTheOldFunctions() = withAimoge {
        for (url in CanonicalKeyGoldenInputs.boards) {
            val old = canonicalizeBoardUrl(url) ?: continue
            assertEquals(old, canonicalizeSiteBoardUrl(url)!!.canonicalUrl, url)
            assertEquals(BoardSite.FUTABA, canonicalizeSiteBoardUrl(url)!!.site)
        }
        for (url in CanonicalKeyGoldenInputs.threads) {
            val old = canonicalizeThreadUrl(url) ?: continue
            val site = canonicalizeSiteThreadUrl(url)!!
            assertEquals(old.canonicalUrl, site.canonicalUrl, url)
            assertEquals(old.canonicalBoardUrl, site.canonicalBoardUrl)
            assertEquals(old.threadNo, site.threadId)
        }
        // The old functions still refuse あいもげ.
        assertNull(canonicalizeBoardUrl(BOARD))
        assertNull(canonicalizeThreadUrl(THREAD))
    }

    @Test
    fun aimogeBoardAndThreadForms() = withAimoge {
        for (url in listOf(BOARD, "https://nijiurachan.net/b/aimoge", "https://NIJIURACHAN.net/b/AIMOGE/", "https://nijiurachan.net/b/aimoge/futaba.php")) {
            assertEquals(BOARD, canonicalizeSiteBoardUrl(url)?.canonicalUrl, url)
        }
        assertEquals("aimoge", canonicalizeSiteBoardUrl(BOARD)!!.boardPath)
        val thread = canonicalizeSiteThreadUrl("https://nijiurachan.net/b/aimoge/thread/${UUID.uppercase()}/#post-$UUID")!!
        assertEquals(BoardSite.AIMOGE, thread.site)
        assertEquals(THREAD, thread.canonicalUrl)
        assertEquals(BOARD, thread.canonicalBoardUrl)
        assertEquals(UUID, thread.threadId)
        assertEquals(UUID, thread.postId)
        assertEquals(BoardSite.AIMOGE, boardSiteOf(THREAD))
        assertEquals(AimogeLinkTarget.Board(BOARD), aimogeLinkTargetOf("https://nijiurachan.net/b/aimoge?x=1"))
        assertEquals(UUID, (aimogeLinkTargetOf("$THREAD#post-$UUID") as AimogeLinkTarget.Thread).thread.postId)
        assertEquals("aimoge-aimoge", aimogeBoardIdOf(THREAD))
    }

    @Test
    fun shapesThatAreRefused() = withAimoge {
        val refused = listOf(
            "http://nijiurachan.net/b/aimoge/",
            "https://nijiurachan.net:443/b/aimoge/",
            "https://user@nijiurachan.net/b/aimoge/",
            "https://nijiurachan.net.evil.com/b/aimoge/",
            "https://evil.com\\@nijiurachan.net/b/aimoge/",
            "https://api.nijiurachan.net/b/aimoge/",
            "https://nijiurachan.net/aimoge/",
            "https://nijiurachan.net/",
            "https://nijiurachan.net/b/",
            "https://nijiurachan.net/b/ai%6Doge/",
            "https://nijiurachan.net/b/../aimoge/",
            "https://nijiurachan.net/b/aimoge/res/123.htm",
            "https://nijiurachan.net/b/あいもげ/",
            "https://nijiurachan.net/b/ aimoge/"
        )
        for (url in refused) {
            assertNull(canonicalizeSiteBoardUrl(url), url)
            assertEquals(BoardSite.FUTABA, boardSiteOf(url), url)
        }
        val badThreads = listOf(
            "https://nijiurachan.net/b/aimoge/thread/123",
            "https://nijiurachan.net/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0",
            "https://nijiurachan.net/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0fa",
            "https://nijiurachan.net/b/aimoge/thread/g193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
            "https://api.nijiurachan.net/b/aimoge/thread/$UUID",
            "https://nijiurachan.net/aimoge/thread/$UUID"
        )
        for (url in badThreads) assertNull(canonicalizeSiteThreadUrl(url), url)
        assertNull(siteThreadUrlOf(BOARD, "123"))
        assertNull(siteThreadUrlOf("https://may.2chan.net/b/", UUID))
    }

    @Test
    fun theResolversFollowTheSite() {
        // Off: the Futaba rules, unchanged.
        assertEquals("https://nijiurachan.net/b/aimoge/res/123.htm", BoardUrlResolver.resolveThreadUrl(BOARD, "123"))
        withAimoge {
            assertEquals(THREAD, BoardUrlResolver.resolveThreadUrl(BOARD, UUID))
            assertFailsWith<IllegalArgumentException> { BoardUrlResolver.resolveThreadUrl(BOARD, "123") }
            assertEquals("https://nijiurachan.net/b/aimoge", BoardUrlResolver.resolveBoardBaseUrl(THREAD))
            assertEquals("aimoge", BoardUrlResolver.resolveBoardSlug(THREAD))
            assertEquals(BOARD, resolveEffectiveBoardUrl(THREAD, "x"))
            // Futaba unchanged while on.
            assertEquals("https://may.2chan.net/b/res/1.htm", BoardUrlResolver.resolveThreadUrl("https://may.2chan.net/b/futaba.php", "1"))
        }
    }
}
