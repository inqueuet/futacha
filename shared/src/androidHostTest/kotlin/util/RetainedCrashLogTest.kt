package com.valoser.futacha.shared.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RetainedCrashLogTest {
    private val crash = "10-02 10:00:00.000 E/AndroidRuntime( 123): FATAL EXCEPTION: main"

    @Test
    fun uidFilteredCommandIsUsedWhenSupported() {
        val commands = mutableListOf<List<String>>()
        val result = readRetainedCrashLog(10123) { command ->
            commands += command
            LogcatRun(0, crash)
        }
        assertEquals(crash, result)
        assertEquals(1, commands.size)
        assertTrue("--uid=10123" in commands.single())
    }

    @Test
    fun rejectedUidOptionFallsBackToTheUnfilteredCrashBuffer() {
        val commands = mutableListOf<List<String>>()
        val result = readRetainedCrashLog(10123) { command ->
            commands += command
            if (command.any { it.startsWith("--uid") }) {
                LogcatRun(1, "")
            } else {
                LogcatRun(0, crash)
            }
        }
        // Before the fallback the crash of an old Android version was never collected.
        assertEquals(crash, result)
        assertEquals(listOf("logcat", "-b", "crash", "-d", "-v", "time", "-t", "500", "*:E"), commands.last())
    }

    @Test
    fun usageTextPrintedForTheUnknownOptionIsNotStoredAsACrash() {
        val result = readRetainedCrashLog(10123) { command ->
            if (command.any { it.startsWith("--uid") }) {
                LogcatRun(0, "logcat: unrecognized option '--uid=10123'\nUsage: logcat [options] [filterspecs]")
            } else {
                LogcatRun(0, crash)
            }
        }
        assertEquals(crash, result)
    }

    @Test
    fun failedOrUnavailableLogcatYieldsNothing() {
        assertEquals("", readRetainedCrashLog(10123) { null })
        assertEquals("", readRetainedCrashLog(10123) { LogcatRun(1, "error") })
    }
}
