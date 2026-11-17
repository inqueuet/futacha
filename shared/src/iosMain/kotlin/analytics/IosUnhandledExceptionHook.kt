@file:OptIn(
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

package com.valoser.futacha.shared.analytics

import platform.Foundation.NSLock
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile
import com.valoser.futacha.shared.util.iosDiagnosticPath
import kotlin.native.getUnhandledExceptionHook
import kotlin.native.setUnhandledExceptionHook
import kotlin.native.terminateWithUnhandledException

internal const val IOS_KOTLIN_CRASH_FILE_NAME = "last_kotlin_crash.txt"

internal class IosUnhandledExceptionHookInstaller {
    private val lock = NSLock()
    private var installed = false

    fun install() {
        lock.lock()
        try {
            if (installed) return
            // Resolve the path before a crash, including when Firebase is unavailable.
            val path = iosDiagnosticPath(IOS_KOTLIN_CRASH_FILE_NAME)
            val previous = getUnhandledExceptionHook()
            setUnhandledExceptionHook { error ->
                handleIosUnhandledException(
                    error = error,
                    saveLocally = { failure ->
                        if (path != null) writeIosKotlinCrashReport(path, failure)
                    },
                    report = { failure ->
                        CrashReporter.recordNonFatal(
                            failure,
                            keys = mapOf("kotlin_unhandled" to "true")
                        )
                    },
                    previousHook = previous,
                    terminate = ::terminateWithUnhandledException
                )
            }
            installed = true
        } finally {
            lock.unlock()
        }
    }
}

internal fun handleIosUnhandledException(
    error: Throwable,
    saveLocally: (Throwable) -> Unit,
    report: (Throwable) -> Unit,
    previousHook: ((Throwable) -> Unit)?,
    terminate: (Throwable) -> Nothing
): Nothing {
    // At the fatal boundary even a reporting Error must not replace the original
    // exception. Keep each diagnostic independent and always terminate with it.
    try { saveLocally(error) } catch (_: Throwable) { }
    try { report(error) } catch (_: Throwable) { }
    try { previousHook?.invoke(error) } catch (_: Throwable) { }
    return terminate(error)
}

internal fun writeIosKotlinCrashReport(path: String, error: Throwable): Boolean {
    // Logger and Crashlytics enqueue writes; abort can happen before they run.
    // Retain one bounded report synchronously. It is local only and never uploaded.
    return NSString.create(string = buildIosKotlinCrashReport(error))
        .writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
}

internal fun buildIosKotlinCrashReport(error: Throwable): String = buildString {
    appendLine("Unhandled Kotlin exception (local diagnostic)")
    var current: Throwable? = error
    val visited = mutableListOf<Throwable>()
    while (current != null && visited.size < 4 && visited.none { it === current }) {
        val failure = current
        if (visited.isNotEmpty()) append("Caused by: ")
        visited.add(failure)
        append(failure::class.simpleName ?: "Throwable")
        append(": ")
        // Original messages stay in this on-device file; Firebase receives only
        // buildSanitizedCrashExceptionMessage and native frame addresses.
        appendLine(failure.message.orEmpty().take(2_048))
        failure.getStackTrace().take(64).forEach { frame ->
            appendLine(frame.take(1_024))
        }
        current = failure.cause
    }
    if (current != null) appendLine("[cause chain truncated]")
}
