@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.valoser.futacha.shared.network

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import platform.Foundation.*
import kotlin.test.*
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.seconds

class IosPostingBrowserTest {
    @Test fun browserUsesDeviceUserAgentAndKeepsPreparationStoragePerOrigin() = nativeTest {
        val browser=IosPostingBrowser()
        assertTrue(browser.userAgent().contains("AppleWebKit"))
        val html="<html><body></body></html>"
        val key="futacha-posting-storage-test"
        try {
            assertEquals("one",browser.evaluate("https://img.2chan.net/b/",html,"localStorage.setItem('$key','one'); localStorage.getItem('$key')"))
            assertEquals("one",browser.evaluate("https://img.2chan.net/b/",html,"localStorage.getItem('$key')"))
            assertEquals("missing",browser.evaluate("https://may.2chan.net/b/",html,"localStorage.getItem('$key') || 'missing'"))
        } finally {
            browser.evaluate("https://img.2chan.net/b/",html,"localStorage.removeItem('$key'); 'done'")
        }
    }
    private fun nativeTest(block:suspend ()->Unit) {
        val result=MutableStateFlow<Result<Unit>?>(null)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
        val job=scope.launch { result.value=runCatching { withTimeout(60_000) { block() } } }
        val start=TimeSource.Monotonic.markNow()
        try {
            while(!job.isCompleted && start.elapsedNow()<70.seconds) NSRunLoop.mainRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(0.01))
            assertTrue(job.isCompleted,"Posting browser timed out")
            requireNotNull(result.value).getOrThrow()
        } finally {scope.cancel()}
    }
}
