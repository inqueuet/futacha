@file:kotlin.OptIn(
    kotlin.ExperimentalMultiplatform::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

package com.valoser.futacha.shared.util

import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.*

/**
 * Logs [message] through NSLog without treating it as a format string.
 * URLs, file names and server responses may contain `%@` / `%s`, which would
 * otherwise make NSLog read nonexistent varargs and crash.
 */
internal fun logToSystem(message: String) {
    // NSLog treats its first argument as a format string. Kotlin/Native does not bridge a
    // String passed through C varargs to an NSString, so "%@" with a vararg would read a
    // garbage pointer; escaping '%' keeps the text literal instead.
    NSLog(message.replace("%", "%%"))
}

private const val PERSISTENT_LOG_MAX_PENDING_WRITES = 512

private val persistentLogPath: String? by lazy {
    iosDiagnosticPath(PERSISTENT_ERROR_LOG_FILE_NAME)
}
private val persistentLogQueue = NSOperationQueue().apply {
    maxConcurrentOperationCount = 1
    name = "com.valoser.futacha.persistent-log"
}
private val persistentLogPendingWrites = AtomicInt(0)

private fun appendPersistentLog(level: String, tag: String, message: String) {
    val path = persistentLogPath ?: return
    // Bound the queue so a log storm (or a stalled disk) cannot grow memory without limit.
    if (persistentLogPendingWrites.incrementAndGet() > PERSISTENT_LOG_MAX_PENDING_WRITES) {
        persistentLogPendingWrites.decrementAndGet()
        return
    }
    persistentLogQueue.addOperationWithBlock {
        try {
            appendPersistentLogLine(path, level, tag, message)
        } finally {
            persistentLogPendingWrites.decrementAndGet()
        }
    }
}

private fun appendPersistentLogLine(path: String, level: String, tag: String, message: String) {
    runCatching {
        val manager = NSFileManager.defaultManager
        val existingSize = (manager.attributesOfItemAtPath(path, error = null)
            ?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
        if (shouldResetPersistentLog(existingSize)) {
            manager.removeItemAtPath(path, null)
        }
        val bytes = formatPersistentLogLine(level, tag, message).encodeToByteArray()
        if (bytes.isEmpty()) return@runCatching
        // POSIX append instead of NSFileHandle.seekToEndOfFile(), which raises an
        // uncatchable Objective-C exception on I/O errors.
        val descriptor = platform.posix.open(
            path,
            platform.posix.O_WRONLY or platform.posix.O_CREAT or platform.posix.O_APPEND,
            0x1A4 // 0644
        )
        if (descriptor < 0) return@runCatching
        try {
            bytes.usePinned { pinned ->
                var written = 0
                while (written < bytes.size) {
                    val count = platform.posix.write(
                        descriptor,
                        pinned.addressOf(written),
                        (bytes.size - written).convert()
                    )
                    if (count > 0) {
                        written += count.toInt()
                    } else if (count < 0L && platform.posix.errno == platform.posix.EINTR) {
                        continue
                    } else {
                        break
                    }
                }
            }
        } finally {
            platform.posix.close(descriptor)
        }
    }
}

actual object Logger {
    actual fun d(tag: String, message: String) {
        logToSystem("DEBUG [$tag]: $message")
        appendPersistentLog("DEBUG", tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            logToSystem("ERROR [$tag]: $message - ${throwable.message}")
            throwable.printStackTrace()
        } else {
            logToSystem("ERROR [$tag]: $message")
        }
        appendPersistentLog("ERROR", tag, message + (throwable?.message?.let { " - $it" } ?: ""))
    }

    actual fun w(tag: String, message: String) {
        logToSystem("WARN [$tag]: $message")
        appendPersistentLog("WARN", tag, message)
    }

    actual fun i(tag: String, message: String) {
        logToSystem("INFO [$tag]: $message")
        appendPersistentLog("INFO", tag, message)
    }
}
