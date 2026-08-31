@file:kotlin.OptIn(
    kotlin.ExperimentalMultiplatform::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class
)

package com.valoser.futacha.shared.util

import platform.Foundation.*

private val persistentLogPath: String? by lazy {
    val documents = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true
    ).firstOrNull() as? String
    documents?.let { "$it/$PERSISTENT_ERROR_LOG_FILE_NAME" }
}
private val persistentLogQueue = NSOperationQueue().apply {
    maxConcurrentOperationCount = 1
    name = "com.valoser.futacha.persistent-log"
}

private fun appendPersistentLog(level: String, tag: String, message: String) {
    val path = persistentLogPath ?: return
    persistentLogQueue.addOperationWithBlock {
        runCatching {
            val manager = NSFileManager.defaultManager
            val existingSize = (manager.attributesOfItemAtPath(path, error = null)
                ?.get(NSFileSize) as? NSNumber)?.longValue ?: 0L
            if (shouldResetPersistentLog(existingSize)) {
                manager.removeItemAtPath(path, null)
            }
            val data = NSString.create(string = formatPersistentLogLine(level, tag, message))
                .dataUsingEncoding(NSUTF8StringEncoding) ?: return@addOperationWithBlock
            if (!manager.fileExistsAtPath(path)) {
                manager.createFileAtPath(path, contents = data, attributes = null)
            } else {
                NSFileHandle.fileHandleForWritingAtPath(path)?.let { handle ->
                    handle.seekToEndOfFile()
                    handle.writeData(data, error = null)
                    handle.closeAndReturnError(null)
                }
            }
        }
    }
}

actual object Logger {
    actual fun d(tag: String, message: String) {
        NSLog("DEBUG [$tag]: $message")
        appendPersistentLog("DEBUG", tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            NSLog("ERROR [$tag]: $message - ${throwable.message}")
            throwable.printStackTrace()
        } else {
            NSLog("ERROR [$tag]: $message")
        }
        appendPersistentLog("ERROR", tag, message + (throwable?.message?.let { " - $it" } ?: ""))
    }

    actual fun w(tag: String, message: String) {
        NSLog("WARN [$tag]: $message")
        appendPersistentLog("WARN", tag, message)
    }

    actual fun i(tag: String, message: String) {
        NSLog("INFO [$tag]: $message")
        appendPersistentLog("INFO", tag, message)
    }
}
