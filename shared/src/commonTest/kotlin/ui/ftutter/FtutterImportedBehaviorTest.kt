package com.valoser.futacha.shared.ui.ftutter

import androidx.compose.ui.graphics.Color
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class FtutterImportedBehaviorTest {
    private val post = Post("1", author = null, subject = null, timestamp = "", messageHtml = "fu1234567.jpg",
        imageUrl = "https://may.2chan.net/b/src/1.jpg", thumbnailUrl = "https://may.2chan.net/b/thumb/1s.jpg")
    private val page = ThreadPage("1", null, null, null, listOf(post))

    @Test fun individualColorsAndGestureDistanceAreUsedAfterImport() {
        val plan = ftutterBackupPlan(mapOf(
            "kando" to FtutterPrefValue.Number("45.5"),
            "gestureColorOverride" to FtutterPrefValue.Number("4279312947"),
            "scrollBarColorOverride" to FtutterPrefValue.Number("4282668390"),
            "indicatorColorOverride" to FtutterPrefValue.Number("4286023833")
        ), emptyList(), emptyList(), emptyMap(), 0)
        val design = parseFtutterDesign(plan.preferences)
        val colors = ftutterColors(design.mainColor, design.brightness, design.overrides)
        assertEquals(Color(0xFF112233), colors.gesture)
        assertEquals(Color(0xFF445566), colors.scrollBar)
        assertEquals(Color(0xFF778899), colors.indicator)
        val gesture = parseFtutterGestureSettings(plan.preferences)
        assertEquals(45.5f, gesture.sensitivity)
        val tracker = FtutterGestureTracker(gesture.sensitivity)
        tracker.update(1f, 0f); tracker.update(0f, -40f)
        assertEquals("1", tracker.code)
        tracker.update(0f, -6f)
        assertEquals("10", tracker.code)
        assertTrue(plan.limitations.isEmpty())
    }

    @Test fun resolutionAndApuChoicesRespectWifiAndExtractOnlyApuImages() {
        assertFalse(ftutterLoadHighResolution(0, true))
        assertFalse(ftutterLoadHighResolution(1, false))
        assertTrue(ftutterLoadHighResolution(1, true))
        assertTrue(ftutterLoadHighResolution(2, false))
        val settings = parseFtutterThreadSettings(mapOf(FtutterKeys.OP_IMAGE_HIGH_RES to "2",
            FtutterKeys.RES_IMAGE_HIGH_RES to "1", FtutterKeys.APU_AUTO_LOAD to "0", FtutterKeys.APU_THUMB_SIZE to "321"))
        assertEquals(2, settings.opImageQuality); assertEquals(1, settings.imageQuality)
        assertEquals(0, settings.apuAutoLoad); assertEquals(321, settings.apuThumbSize)
        assertEquals(listOf("https://dec.2chan.net/up2/src/fu1234567.jpg"),
            ftutterApuImages("fu1234567.jpg fu1234567.jpg https://example.com/no.jpg fu1234568.mp4"))
    }

    @Test fun mediaAndScrollbarPreferencesAreAppliedIndependently() {
        val prefs = mapOf(FtutterKeys.VIDEO_AUTO_PLAY to "true", FtutterKeys.VIDEO_LOOP to "true",
            FtutterKeys.USE_MEM_SIZE to "false", FtutterKeys.MEM_SIZE to "987",
            FtutterKeys.CATALOG_SCROLLBAR to "false", FtutterKeys.THREAD_SCROLLBAR_ALWAYS to "true",
            FtutterKeys.THREAD_SCROLLBAR_LEFT to "true", FtutterKeys.THREAD_SCROLLBAR_THICKNESS to "15")
        val media = parseFtutterMediaSettings(prefs)
        assertTrue(media.videoAutoPlay && media.videoLoop); assertFalse(media.reduceThumbnail)
        assertEquals(987, media.thumbnailPixels)
        assertFalse(parseFtutterCatalogSettings(prefs).scrollbar.enabled)
        assertEquals(FtutterScrollbarSettings(true, true, true, 15), parseFtutterThreadSettings(prefs).scrollbar)
    }

    @Test fun aCachedThreadAvoidsReloadUnlessRequestedAndRefreshIsConsumedOnce() {
        val memory = FtutterThreadMemory()
        memory.put("one", page)
        memory.open("one", false)
        assertEquals(page, memory.page("one")); assertFalse(memory.takeRefresh("one"))
        memory.open("one", true)
        assertTrue(memory.takeRefresh("one")); assertFalse(memory.takeRefresh("one"))
        repeat(12) { memory.put("thread-$it", page) }
        assertNull(memory.page("one"))
        memory.remove("thread-11"); assertNull(memory.page("thread-11"))
    }

    @Test fun historyLimitKeepsProtectedRowsAndDoesNotChangeSharedRecords() {
        val shared = listOf("new", "protected", "middle", "old")
        assertEquals(listOf("new", "protected"), ftutterLimitedHistoryUrls(shared, setOf("protected"), 1))
        assertEquals(listOf("protected"), ftutterLimitedHistoryUrls(shared, setOf("protected"), 0))
        assertEquals(4, shared.size)
    }

    @Test fun futabaAlwaysUsesHttpsAndHtmlChoicesKeepTheirIndependentFlags() {
        assertEquals("https://may.2chan.net/b/", ftutterNetworkUrl("https://may.2chan.net/b/"))
        assertEquals("https://may.2chan.net/b/", ftutterNetworkUrl("http://may.2chan.net/b/"))
        assertEquals("https://2chan.net.example/b/", ftutterNetworkUrl("https://2chan.net.example/b/"))
        assertEquals("https://may.2chan.net/b/res/123.htm?x=1#p2", ftutterNetworkUrl("http://may.2chan.net/b/res/123.htm?x=1#p2"))
        assertEquals("http://example.com/b/", ftutterNetworkUrl("http://example.com/b/"))
        assertEquals("http://2chan.net.example/b/", ftutterNetworkUrl("http://2chan.net.example/b/"))
        assertEquals(FtutterHtmlOptions(true, true, true, true), parseFtutterHtmlOptions(emptyMap()))
        val options = parseFtutterHtmlOptions(mapOf(FtutterKeys.SAVE_HTML_THUMB to "false",
            FtutterKeys.SAVE_HTML_FILE to "true", FtutterKeys.SAVE_HTML_APU to "true", FtutterKeys.SAVE_HTML_ZIP to "true"))
        assertEquals(FtutterHtmlOptions(false, true, true, true), options)
        val saved = ftutterHtmlPosts(page, options).single()
        assertNull(saved.thumbnailUrl); assertEquals(post.imageUrl, saved.imageUrl)
        assertEquals(post.messageHtml, saved.messageHtml)
    }

    @Test fun importedCookiesPersistAcrossReopenAndAreNeverSentOutsideFutaba() = runBlocking {
        val fs = InMemoryFileSystem()
        val storage = PersistentCookieStorage(fs)
        val repository = CookieRepository(storage)
        assertEquals(2, ftutterImportCookies(repository, "ptmt=migration-check; session=local-check"))
        assertEquals(setOf("ptmt", "session"), repository.getCookiesFor("https://img.2chan.net/b/").map { it.name }.toSet())
        assertTrue(repository.getCookiesFor("https://example.com/").isEmpty())
        storage.close()
        val reopened = PersistentCookieStorage(fs)
        try {
            assertEquals("migration-check", CookieRepository(reopened).getCookiesFor("http://may.2chan.net/b/").single { it.name == "ptmt" }.value)
        } finally { reopened.close() }
    }
}
