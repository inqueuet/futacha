package com.valoser.futacha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AM-B0 (docs/aimoge-support-plan-2026-11-10.md): the Android-only Futaba address code with an あいもげ address today.
 * AM-B7 builds あいもげ's notification link in a separate branch; these Futaba results must stay as they are.
 */
class AimogeAndroidUrlTodayTest {
    @Test
    fun aWatchAlertLinkIsBuiltOnlyForAFutabaBoard() {
        assertEquals("https://may.2chan.net/b/res/1439323740.htm", watchAlertThreadUrl("https://may.2chan.net/b/futaba.php", "1439323740"))
        assertNull(watchAlertThreadUrl("https://nijiurachan.net/b/aimoge/", "1"))
        assertNull(watchAlertThreadUrl("https://nijiurachan.net/b/aimoge/", "0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"))
        assertNull(watchAlertThreadUrl("https://api.nijiurachan.net/b/aimoge/", "1"))
    }

    @Test
    fun aDeepLinkIsTrustedOnlyOnA2chanHost() {
        assertTrue(isTrustedFutabaDeepLinkHost("may.2chan.net"))
        assertTrue(isTrustedFutabaDeepLinkHost("2chan.net"))
        assertFalse(isTrustedFutabaDeepLinkHost("nijiurachan.net"))
        assertFalse(isTrustedFutabaDeepLinkHost("api.nijiurachan.net"))
    }

    /** MainActivity's thread-link pattern (its private `futabaThreadDeepLinkOrNull`), copied to hold it. */
    @Test
    fun theThreadLinkPatternMatchesOnlyAFutabaThreadPath() {
        val pattern = Regex("/.+/res/[0-9]+\\.htm/?", RegexOption.IGNORE_CASE)
        assertTrue(pattern.matches("/b/res/1439323740.htm"))
        assertTrue(pattern.matches("/img2/res/1.htm/"))
        assertFalse(pattern.matches("/b/aimoge/thread/0193b2c4-1a2b-7c3d-8e4f-5a6b7c8d9e0f"))
        assertFalse(pattern.matches("/b/aimoge/"))
        val source = java.io.File("src/main/java/com/valoser/futacha/MainActivity.kt").readText()
        assertTrue("MainActivity still uses the same pattern", source.contains("Regex(\"/.+/res/[0-9]+\\\\.htm/?\", RegexOption.IGNORE_CASE)"))
    }

    /** HistoryRefreshWorker's board address of a watch match: the thread address before `/res/`. */
    @Test
    fun theWorkersBoardAddressCutsAtRes() {
        val source = java.io.File("src/main/java/com/valoser/futacha/HistoryRefreshWorker.kt").readText()
        // AM-B7: the worker reads the board through watchMatchBoardUrlOf (Futaba still cuts at /res/).
        assertTrue(source.contains("boardUrl = com.valoser.futacha.shared.compat.watchMatchBoardUrlOf(history.originalUrl)"))
        assertEquals("https://may.2chan.net/b", com.valoser.futacha.shared.compat.watchMatchBoardUrlOf("https://may.2chan.net/b/res/1.htm"))
    }
}
