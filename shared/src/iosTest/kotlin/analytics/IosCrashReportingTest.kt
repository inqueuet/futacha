@file:OptIn(
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

package com.valoser.futacha.shared.analytics

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.stringWithContentsOfFile
import kotlin.native.getStackTraceAddresses
import kotlin.native.getUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IosCrashReportingTest {
    private class TerminationObserved : Error()

    @Test
    fun recordsOriginalNativeFramesAndRedactsMessageOnlyWhenEnabled() {
        val bridge = IosFirebaseTelemetryBridge
        val previous = bridge.recordCrashlyticsExceptionHandler
        var calls = 0
        val error = IllegalStateException("https://user:secret@example.com/private")
        try {
            bridge.recordCrashlyticsExceptionHandler = { name, message, addresses ->
                calls++
                assertEquals("IllegalStateException", name)
                assertFalse(message.contains("secret"))
                assertFalse(message.contains("example.com"))
                assertTrue(addresses.isNotEmpty())
                assertEquals(error.getStackTraceAddresses().take(64), addresses)
            }
            CrashReporter.setCollectionEnabled(false)
            CrashReporter.recordNonFatal(error)
            assertEquals(0, calls)
            CrashReporter.setCollectionEnabled(true)
            CrashReporter.recordNonFatal(error)
            assertEquals(1, calls)
            CrashReporter.setCollectionEnabled(false)
            CrashReporter.recordNonFatal(error)
            assertEquals(1, calls)
        } finally {
            CrashReporter.setCollectionEnabled(false)
            bridge.recordCrashlyticsExceptionHandler = previous
        }
    }

    @Test
    fun fatalBoundaryRecordsThenChainsThenTerminatesWithOriginalException() {
        val error = IllegalStateException("original")
        val calls = mutableListOf<String>()
        assertFailsWith<TerminationObserved> {
            handleIosUnhandledException(
                error,
                saveLocally = { assertSame(error, it); calls.add("local") },
                report = { assertSame(error, it); calls.add("report") },
                previousHook = { assertSame(error, it); calls.add("previous") },
                terminate = { assertSame(error, it); calls.add("terminate"); throw TerminationObserved() }
            )
        }
        assertEquals(listOf("local", "report", "previous", "terminate"), calls)
    }

    @Test
    fun diagnosticFailuresCannotReplaceOriginalFatalException() {
        val error = IllegalStateException("original")
        val calls = mutableListOf<String>()
        var terminatedWith: Throwable? = null
        assertFailsWith<TerminationObserved> {
            handleIosUnhandledException(
                error,
                saveLocally = { calls.add("local"); throw Error("disk failure") },
                report = { calls.add("report"); throw Error("SDK failure") },
                previousHook = { calls.add("previous"); throw Error("hook failure") },
                terminate = { terminatedWith = it; throw TerminationObserved() }
            )
        }
        assertEquals(listOf("local", "report", "previous"), calls)
        assertSame(error, terminatedWith)
    }

    @Test
    fun absentPreviousHookStillTerminates() {
        val error = IllegalStateException()
        var terminatedWith: Throwable? = null
        assertFailsWith<TerminationObserved> {
            handleIosUnhandledException(error, {}, {}, null) {
                terminatedWith = it
                throw TerminationObserved()
            }
        }
        assertSame(error, terminatedWith)
    }

    @Test
    fun installingTwiceDoesNotChainHookToItself() {
        val previous = getUnhandledExceptionHook()
        try {
            val installer = IosUnhandledExceptionHookInstaller()
            installer.install()
            val installed = assertNotNull(getUnhandledExceptionHook())
            installer.install()
            assertSame(installed, getUnhandledExceptionHook())
        } finally {
            setUnhandledExceptionHook(previous)
        }
    }

    @Test
    fun localReportPreservesOriginalMessageCauseAndKotlinFrames() {
        val error = IllegalStateException("outer detail", IllegalArgumentException("root detail"))
        val report = buildIosKotlinCrashReport(error)
        assertTrue(report.contains("IllegalStateException: outer detail"))
        assertTrue(report.contains("Caused by: IllegalArgumentException: root detail"))
        assertTrue(report.contains("localReportPreservesOriginalMessageCauseAndKotlinFrames"))
    }

    @Test
    fun localReportBoundsMessagesAndCyclicCauseChains() {
        val error = object : Exception("x".repeat(100_000)) {
            override val cause: Throwable get() = this
        }
        val report = buildIosKotlinCrashReport(error)
        assertTrue(report.contains("x".repeat(2_048)))
        assertFalse(report.contains("x".repeat(2_049)))
        assertTrue(report.contains("[cause chain truncated]"))
        assertTrue(report.length < 70_000)
    }

    @Test
    fun localReportIsReadableImmediatelyAndReplacesPreviousCrash() {
        val path = "${NSTemporaryDirectory()}/${NSUUID().UUIDString}-$IOS_KOTLIN_CRASH_FILE_NAME"
        try {
            assertTrue(writeIosKotlinCrashReport(path, IllegalStateException("first failure")))
            assertTrue(readReport(path).contains("first failure"))
            assertTrue(writeIosKotlinCrashReport(path, IllegalArgumentException("second failure")))
            val latest = readReport(path)
            assertTrue(latest.contains("second failure"))
            assertFalse(latest.contains("first failure"))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
    }

    private fun readReport(path: String): String = assertNotNull(
        NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)
    )
}
