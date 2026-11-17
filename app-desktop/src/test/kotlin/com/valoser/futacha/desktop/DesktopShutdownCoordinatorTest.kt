package com.valoser.futacha.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopShutdownCoordinatorTest {
    @Test
    fun osQuitExitsComposeRunsCleanupOnceAndThenConfirmsTheQuit() {
        val events = mutableListOf<String>()
        var watchdog: (() -> Unit)? = null
        val coordinator = DesktopShutdownCoordinator(
            cleanup = { events += "cleanup" },
            startWatchdog = { _, action -> watchdog = action }
        )
        coordinator.attach { events += "exitApplication" }

        coordinator.onQuitRequested { events += "performQuit" }
        // Cmd+Q must not quit before the cleanup: only Compose is asked to exit.
        assertEquals(listOf("exitApplication"), events)

        coordinator.finish() // application { } returned
        coordinator.onJvmShutdown() // performQuit → System.exit → shutdown hook
        watchdog!!.invoke()

        assertEquals(listOf("exitApplication", "cleanup", "performQuit"), events)
    }

    @Test
    fun stuckCleanupNeverBlocksTheQuit() {
        val release = CountDownLatch(1)
        val cleanupStarted = CountDownLatch(1)
        var watchdog: (() -> Unit)? = null
        val confirmed = CountDownLatch(1)
        val coordinator = DesktopShutdownCoordinator(
            cleanup = { cleanupStarted.countDown(); release.await(10, TimeUnit.SECONDS) },
            hookWaitMillis = 50L,
            startWatchdog = { _, action -> watchdog = action }
        )
        coordinator.attach { Thread { coordinator.finish() }.start() }
        coordinator.onQuitRequested { confirmed.countDown() }
        assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS))

        watchdog!!.invoke()
        assertTrue(confirmed.await(1, TimeUnit.SECONDS))
        val hookStarted = System.nanoTime()
        coordinator.onJvmShutdown()
        assertTrue(System.nanoTime() - hookStarted < TimeUnit.SECONDS.toNanos(2))
        release.countDown()
    }

    @Test
    fun windowCloseWithAStuckCleanupStillEndsTheProcess() {
        val release = CountDownLatch(1)
        val cleanupStarted = CountDownLatch(1)
        var watchdogDelay = 0L
        var watchdog: (() -> Unit)? = null
        val exited = CountDownLatch(1)
        val coordinator = DesktopShutdownCoordinator(
            cleanup = { cleanupStarted.countDown(); release.await(10, TimeUnit.SECONDS) },
            startWatchdog = { _, _ -> error("no OS quit") },
            startExitWatchdog = { delay, action -> watchdogDelay = delay; watchdog = action },
            exitProcess = { exited.countDown() }
        )
        // Window close: application { } returns without an OS quit, so finish() runs the cleanup.
        val main = Thread { coordinator.finish() }.apply { isDaemon = true; start() }
        assertTrue(cleanupStarted.await(5, TimeUnit.SECONDS))

        assertEquals(25_000L, watchdogDelay)
        watchdog!!.invoke()
        assertTrue(exited.await(1, TimeUnit.SECONDS))
        release.countDown()
        main.join(5_000)
    }

    @Test
    fun windowCloseWhoseCleanupFinishesIsNotForcedToExit() {
        var watchdog: (() -> Unit)? = null
        var exits = 0
        val coordinator = DesktopShutdownCoordinator(
            cleanup = {},
            startWatchdog = { _, _ -> error("no OS quit") },
            startExitWatchdog = { _, action -> watchdog = action },
            exitProcess = { exits += 1 }
        )
        coordinator.finish()
        watchdog!!.invoke()

        assertEquals(0, exits)
    }

    @Test
    fun osQuitKeepsItsOwnWatchdogAndFinishStartsNoSecondOne() {
        val coordinator = DesktopShutdownCoordinator(
            cleanup = {},
            startWatchdog = { _, _ -> },
            startExitWatchdog = { _, _ -> error("the quit watchdog already covers this") },
            exitProcess = { error("no forced exit") }
        )
        coordinator.attach {}
        coordinator.onQuitRequested {}
        coordinator.finish()
    }

    @Test
    fun shutdownHookAloneRunsTheCleanupAndNormalExitDoesNotRepeatIt() {
        var cleanups = 0
        val coordinator = DesktopShutdownCoordinator(cleanup = { cleanups += 1 }, startWatchdog = { _, _ -> })

        coordinator.onJvmShutdown() // Windows logoff / SIGTERM
        coordinator.finish()
        coordinator.onJvmShutdown()

        assertEquals(1, cleanups)
    }

    @Test
    fun quitBeforeTheWindowExistsExitsAsSoonAsItAttaches() {
        var exits = 0
        var confirms = 0
        val coordinator = DesktopShutdownCoordinator(cleanup = {}, startWatchdog = { _, _ -> })

        coordinator.onQuitRequested { confirms += 1 }
        coordinator.onQuitRequested { confirms += 100 } // repeated Cmd+Q
        coordinator.attach { exits += 1 }
        coordinator.finish()

        assertEquals(1, exits)
        assertEquals(1, confirms)
    }

    @Test
    fun quitAfterTheCleanupFinishedIsConfirmedImmediately() {
        var confirms = 0
        val coordinator = DesktopShutdownCoordinator(cleanup = {}, startWatchdog = { _, _ -> error("no watchdog") })
        coordinator.finish()

        coordinator.onQuitRequested { confirms += 1 }

        assertEquals(1, confirms)
    }
}
