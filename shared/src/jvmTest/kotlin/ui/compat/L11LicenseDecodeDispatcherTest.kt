package com.valoser.futacha.shared.ui.compat

import futacha.shared.generated.resources.Res
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class L11LicenseDecodeDispatcherTest {
    @Test fun theActualLargeBundledNoticeIsDecodedOffTheCallerUiThreadWithoutChangingText() = runBlocking {
        val path = requireNotNull(FUTACHA_LICENSE_ASSETS.first { it.id == "voicevox-core-notices" }.resourcePath)
        val bytes = Res.readBytes(path)
        assertTrue(bytes.size > 2_000_000, "This regression must cover the large bundled third-party notice")
        val expected = compatLicenseParagraphs(bytes)
        Executors.newSingleThreadExecutor { task -> Thread(task, "L11-test-ui") }.asCoroutineDispatcher().use { ui ->
            withContext(ui) {
                val caller = Thread.currentThread()
                val actual = loadCompatLicenseParagraphs(path, readBytes = { bytes }, decode = {
                    assertNotSame(caller, Thread.currentThread(), "The large decode/split must not occupy the caller UI thread")
                    compatLicenseParagraphs(it)
                })
                assertSame(caller, Thread.currentThread(), "The caller must resume on its UI dispatcher")
                assertEquals(expected, actual)
            }
        }
    }
}
