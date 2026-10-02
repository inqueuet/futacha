package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidPersistentLogSupportTest {
    @Test
    fun releaseBuildsKeepOnlyWarningsErrorsAndCrashRecords() {
        assertFalse(shouldPersistAndroidLogLevel("DEBUG", debuggable = false))
        assertFalse(shouldPersistAndroidLogLevel("INFO", debuggable = false))
        listOf("WARN", "ERROR", "CRASH", "LOGCAT").forEach { level ->
            assertTrue(shouldPersistAndroidLogLevel(level, debuggable = false), level)
        }
        listOf("DEBUG", "INFO", "WARN", "ERROR", "CRASH", "LOGCAT").forEach { level ->
            assertTrue(shouldPersistAndroidLogLevel(level, debuggable = true), level)
        }
    }

    @Test
    fun urlsKeepOnlySchemeAndHost() {
        assertEquals(
            "fetch failed https://may.2chan.net/… (timeout)",
            redactLogUrls("fetch failed https://may.2chan.net/b/res/123456.htm?q=secret (timeout)")
        )
        assertEquals(
            "open content://com.android.externalstorage.documents/…",
            redactLogUrls("open content://com.android.externalstorage.documents/tree/primary%3AMy%20Folder")
        )
        assertEquals("login https://example.com/…", redactLogUrls("login https://user:pass@example.com/path"))
        assertEquals("host https://example.com", redactLogUrls("host https://example.com"))
        assertEquals("posted res/1.htm?…", redactLogUrls("posted res/1.htm?pwd=abc&mode=x"))
        assertEquals("no url here", redactLogUrls("no url here"))
    }

    @Test
    fun loggerErrorIsStoredOnceWhenItsLogcatCopyArrives() {
        val filter = LogcatEchoFilter()
        filter.expect("Repo", "Load failed\njava.io.IOException: boom\n\tat a.b.C.d(C.kt:1)")
        // Before the fix each of these lines was appended a second time.
        assertTrue(filter.consumeIfEcho("10-02 10:00:00.000 E/Repo    ( 123): Load failed"))
        assertTrue(filter.consumeIfEcho("10-02 10:00:00.000 E/Repo    ( 123): java.io.IOException: boom"))
        assertTrue(filter.consumeIfEcho("10-02 10:00:00.000 E/Repo    ( 123): \tat a.b.C.d(C.kt:1)"))
        // A second identical platform error is not an echo any more.
        assertFalse(filter.consumeIfEcho("10-02 10:00:01.000 E/Repo    ( 123): Load failed"))
        // Errors not written by Logger (platform logs) are kept.
        assertFalse(filter.consumeIfEcho("10-02 10:00:00.000 E/AndroidRuntime( 123): FATAL EXCEPTION: main"))
    }

    @Test
    fun echoExpectationsAreBounded() {
        val filter = LogcatEchoFilter(capacity = 2)
        filter.expect("T", "a")
        filter.expect("T", "b")
        filter.expect("T", "c")
        assertFalse(filter.consumeIfEcho("10-02 10:00:00.000 E/T( 1): a"))
        assertTrue(filter.consumeIfEcho("10-02 10:00:00.000 E/T( 1): b"))
        assertTrue(filter.consumeIfEcho("10-02 10:00:00.000 E/T( 1): c"))
    }

    @Test
    fun onlyCrashRecordsAfterTheLastStoredLineAreAppended() {
        val first = "10-02 10:00:00.000 E/AndroidRuntime( 100): FATAL EXCEPTION: main"
        val firstTrace = "10-02 10:00:00.000 E/AndroidRuntime( 100): \tat a.B.c(B.kt:1)"
        val second = "10-02 11:00:00.000 E/AndroidRuntime( 200): FATAL EXCEPTION: worker"
        val buffer = listOf(first, firstTrace, second).joinToString("\n")
        // Before the fix the whole buffer, including the first crash, was stored again.
        assertEquals(second, unseenRetainedCrashLines(buffer, lastStoredLine = firstTrace))
        assertEquals("", unseenRetainedCrashLines(buffer, lastStoredLine = second))
        assertEquals(buffer, unseenRetainedCrashLines(buffer, lastStoredLine = null))
        // A rotated buffer that no longer holds the marker is all new.
        assertEquals(second, unseenRetainedCrashLines(second, lastStoredLine = firstTrace))
    }
}
