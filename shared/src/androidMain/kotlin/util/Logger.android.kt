@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val persistentLogLock = Any()
@Volatile private var persistentLogFile: File? = null
@Volatile private var persistentLogcatStarted = false
private val persistentLogExecutor = ThreadPoolExecutor(
    1,
    1,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(2_048),
    { task -> Thread(task, "futacha-persistent-log").apply { isDaemon = true } },
    ThreadPoolExecutor.DiscardOldestPolicy()
)

fun initializeAndroidPersistentLogging(context: Context) {
    synchronized(persistentLogLock) {
        val directory = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(directory, PERSISTENT_ERROR_LOG_FILE_NAME)
        if (shouldResetPersistentLog(file.length())) {
            runCatching { file.delete() }
        }
        persistentLogFile = file
        if (!persistentLogcatStarted) {
            persistentLogcatStarted = true
            startErrorLogcatCapture()
        }
    }
}

private fun startErrorLogcatCapture() {
    Thread({
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "time", "*:E"))
            BufferedReader(InputStreamReader(process.inputStream), 1_024).useLines { lines ->
                lines.forEach { line -> appendPersistentLog("LOGCAT", "Android", line) }
            }
        }
        synchronized(persistentLogLock) { persistentLogcatStarted = false }
    }, "futacha-error-logcat").apply {
        isDaemon = true
        start()
    }
}

private fun appendPersistentLog(level: String, tag: String, message: String, throwable: Throwable? = null) {
    val file = persistentLogFile ?: return
    persistentLogExecutor.execute {
        synchronized(persistentLogLock) {
            runCatching {
                if (shouldResetPersistentLog(file.length())) file.delete()
                file.parentFile?.mkdirs()
                file.appendText(
                    formatPersistentLogLine(level, tag, message) +
                        (throwable?.stackTraceToString()?.plus("\n") ?: "")
                )
            }
        }
    }
}

actual object Logger {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
        appendPersistentLog("DEBUG", tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        appendPersistentLog("ERROR", tag, message, throwable)
    }

    actual fun w(tag: String, message: String) {
        Log.w(tag, message)
        appendPersistentLog("WARN", tag, message)
    }

    actual fun i(tag: String, message: String) {
        Log.i(tag, message)
        appendPersistentLog("INFO", tag, message)
    }
}
