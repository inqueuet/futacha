package com.valoser.futacha.desktop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs the shutdown cleanup exactly once, whichever way the app ends.
 *
 * Cmd+Q, Dock → Quit and logout go through the macOS quit handler, whose default
 * calls `System.exit` and skips the `finally` after `application { }`. The handler
 * here instead asks Compose to exit (so the normal path runs the cleanup) and
 * confirms the OS quit afterwards. A watchdog confirms it anyway after
 * [quitWatchdogMillis] so a stuck cleanup never blocks quit or logout, and a JVM
 * shutdown hook (Windows logoff, SIGTERM, an exit from elsewhere) runs or briefly
 * waits for the same cleanup. Closing the window (or 終了 in the menu) has no OS
 * quit to confirm, so [finish] starts its own watchdog that ends the process
 * after [quitWatchdogMillis] if the cleanup is still running: a step blocked in
 * a synchronous call ignores the coroutine timeouts, and the window would be
 * gone while the process kept running.
 */
internal class DesktopShutdownCoordinator(
    private val cleanup: () -> Unit,
    private val quitWatchdogMillis: Long = 25_000L,
    private val hookWaitMillis: Long = 5_000L,
    private val startWatchdog: (Long, () -> Unit) -> Unit = ::startDaemonWatchdog,
    private val startExitWatchdog: (Long, () -> Unit) -> Unit = ::startDaemonWatchdog,
    private val exitProcess: () -> Unit = { Runtime.getRuntime().exit(0) }
) {
    private val started = AtomicBoolean(false)
    private val finished = CountDownLatch(1)
    private val pendingQuit = AtomicReference<(() -> Unit)?>(null)
    private val quitRequested = AtomicBoolean(false)
    @Volatile private var exitApplication: (() -> Unit)? = null

    /** The running Compose application; a quit requested before it started exits it at once. */
    fun attach(exit: () -> Unit) {
        exitApplication = exit
        if (quitRequested.get()) exit()
    }

    fun detach() { exitApplication = null }

    /** The OS asked to quit; [confirmQuit] lets the quit (or logout) continue. */
    fun onQuitRequested(confirmQuit: () -> Unit) {
        val once = AtomicBoolean(false)
        val confirm = { if (once.compareAndSet(false, true)) confirmQuit() }
        // A repeated Cmd+Q while one quit is pending: the first confirmation ends the process.
        if (!pendingQuit.compareAndSet(null, confirm)) return
        quitRequested.set(true)
        if (finished.count == 0L) { confirmPendingQuit(); return }
        exitApplication?.invoke()
        startWatchdog(quitWatchdogMillis) {
            if (pendingQuit.get() === confirm) confirmPendingQuit()
        }
    }

    /** Called when `application { }` has returned. */
    fun finish() {
        // An OS quit already has its watchdog; a cleanup started elsewhere is the shutdown hook's.
        if (pendingQuit.get() == null && !started.get()) {
            startExitWatchdog(quitWatchdogMillis) {
                if (finished.count != 0L) {
                    System.err.println("Shutdown cleanup did not finish within ${quitWatchdogMillis}ms; exiting")
                    exitProcess()
                }
            }
        }
        cleanupOnce(waitMillis = 0L)
        confirmPendingQuit()
    }

    /** JVM shutdown hook: run the cleanup if nobody did, or wait briefly for the running one. */
    fun onJvmShutdown() { cleanupOnce(waitMillis = hookWaitMillis) }

    private fun cleanupOnce(waitMillis: Long) {
        if (started.compareAndSet(false, true)) {
            try { cleanup() } finally { finished.countDown() }
        } else if (waitMillis > 0L) {
            try { finished.await(waitMillis, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
        }
    }

    private fun confirmPendingQuit() { pendingQuit.getAndSet(null)?.invoke() }
}

private fun startDaemonWatchdog(delayMillis: Long, action: () -> Unit) {
    Thread({
        try { Thread.sleep(delayMillis) } catch (_: InterruptedException) { return@Thread }
        action()
    }, "futacha-quit-watchdog").apply { isDaemon = true }.start()
}
