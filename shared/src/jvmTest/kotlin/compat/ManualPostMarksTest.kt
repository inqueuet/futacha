package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import kotlin.test.*

class ManualPostMarksTest {
    @Test fun marksSurviveReopeningAndKeepAutomaticOwnPostMarkersSeparate() = runBlocking {
        val root = Files.createTempDirectory("futacha-manual-marks").toFile()
        var store = DesktopCompatibilityStore(JvmFileSystem(root))
        val url = "https://may.2chan.net/b/res/123.htm"
        try {
            store.initialize()
            val ownKey = compatOwnPostPreferencePrefix(compatTabKey(url)) + "456"
            store.savePreference(ownKey, "1")
            setManualPostMark(store, "http://may.2chan.net/b/res/123.htm#456", "456", true)
            setManualPostMark(store, url, "456", true)
            store.close()
            store = DesktopCompatibilityStore(JvmFileSystem(root)).also { it.initialize() }
            assertEquals(listOf(ManualPostMark(url, "456")), decodeManualPostMarks(store.preferences.first()[MANUAL_POST_MARKS_KEY]))
            setManualPostMark(store, url, "456", false)
            assertTrue(decodeManualPostMarks(store.preferences.first()[MANUAL_POST_MARKS_KEY]).isEmpty())
            assertEquals("1", store.preferences.first()[ownKey])
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun concurrentScreensKeepEachOthersMarksAndBoundStorage() = runBlocking {
        val root = Files.createTempDirectory("futacha-mark-bound").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            store.initialize()
            (1..140).map { no -> async { setManualPostMark(store, "https://may.2chan.net/b/res/1.htm", "$no", true) } }.awaitAll()
            val raw = store.preferences.first()[MANUAL_POST_MARKS_KEY]!!
            assertEquals(120, decodeManualPostMarks(raw).size)
            assertTrue(raw.length <= 18_000)
            assertEquals(120, decodeManualPostMarks(raw).distinct().size)
            assertFailsWith<IllegalArgumentException> { setManualPostMark(store, "u", "invalid", true) }
            Unit
        } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun corruptOrOversizedStateCannotCrashTheReader() {
        assertEquals(emptyList(), decodeManualPostMarks("{broken"))
        assertEquals(emptyList(), decodeManualPostMarks(" ".repeat(18_001)))
    }
}
