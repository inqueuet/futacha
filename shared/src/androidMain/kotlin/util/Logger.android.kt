@file:kotlin.OptIn(kotlin.ExperimentalMultiplatform::class)

package com.valoser.futacha.shared.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val persistentLogLock = Any()
@Volatile private var persistentLogContext: Context? = null
@Volatile private var persistentLogDebuggable = false
@Volatile private var persistentLogFile: File? = null
@Volatile private var persistentLogcatStarted = false
private val loggerLogcatEchoes = LogcatEchoFilter()
private val persistentLogExecutor = ThreadPoolExecutor(
    1,
    1,
    0L,
    TimeUnit.MILLISECONDS,
    ArrayBlockingQueue(2_048),
    { task -> Thread(task, "futacha-persistent-log").apply { isDaemon = true } },
    ThreadPoolExecutor.DiscardOldestPolicy()
)

/**
 * Enables the persistent log. Only in-memory state is set here; resolving the
 * storage directory, rotating the file and starting the logcat capture run on
 * the log thread (G-11), in order before any record queued after this call,
 * so early records are kept.
 */
fun initializeAndroidPersistentLogging(context: Context) {
    val appContext = context.applicationContext ?: context
    persistentLogDebuggable = appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    synchronized(persistentLogLock) {
        persistentLogContext = appContext
        persistentLogFile = null
    }
    persistentLogExecutor.execute { resolvePersistentLogFile() }
}

/**
 * Runs on the log thread. Debuggable builds keep the developer-retrievable
 * external files location; release builds store the log in app-private
 * no-backup storage, which other apps cannot read on API 26-29 (S-4), and
 * remove the log an older version left in external storage.
 */
private fun resolvePersistentLogFile(): File? {
    persistentLogFile?.let { return it }
    val context = persistentLogContext ?: return null
    return synchronized(persistentLogLock) {
        persistentLogFile ?: runCatching {
            val debuggable = persistentLogDebuggable
            val directory = if (debuggable) {
                context.getExternalFilesDir(null) ?: context.filesDir
            } else {
                context.noBackupFilesDir
            }
            if (!debuggable) {
                context.getExternalFilesDir(null)
                    ?.let { File(it, PERSISTENT_ERROR_LOG_FILE_NAME) }
                    ?.takeIf { it.exists() }
                    ?.delete()
            }
            File(directory, PERSISTENT_ERROR_LOG_FILE_NAME).also { file ->
                if (shouldResetPersistentLog(file.length())) file.delete()
                persistentLogFile = file
                if (!persistentLogcatStarted) {
                    persistentLogcatStarted = true
                    startErrorLogcatCapture(context)
                }
            }
        }.getOrNull()
    }
}

/** Extracts the tag of a `-v time` logcat line ("MM-DD HH:MM:SS.mmm E/Tag( pid): message"). */
internal fun logcatLineTag(line: String): String? {
    val levelIndex = line.indexOf(" E/")
    if (levelIndex < 0) return null
    val start = levelIndex + 3
    val end = line.indexOf('(', start).takeIf { it > start } ?: return null
    return line.substring(start, end).trim().takeIf { it.isNotEmpty() }
}

private fun startErrorLogcatCapture(context: Context) {
    Thread({
        runCatching {
            // Recover this UID's preceding crash before following the live process.
            val retained = readRetainedCrashLog(android.os.Process.myUid(), ::runLogcatCommand)
            if (retained.isNotBlank()) persistUnseenRetainedCrash(context, retained)
            // The crash buffer is left out: a crash of this process is read
            // from it on the next start, so following it here stored it twice.
            val process = Runtime.getRuntime().exec(
                arrayOf(
                    "logcat", "-b", "main", "-b", "system", "-v", "time", "-T", "1",
                    "--pid=${android.os.Process.myPid()}", "*:E"
                )
            )
            BufferedReader(InputStreamReader(process.inputStream), 1_024).useLines { lines ->
                lines.forEach { line ->
                    if (!loggerLogcatEchoes.consumeIfEcho(line)) {
                        appendPersistentLog("LOGCAT", "Android", line)
                    }
                }
            }
        }
        synchronized(persistentLogLock) { persistentLogcatStarted = false }
    }, "futacha-error-logcat").apply {
        isDaemon = true
        start()
    }
}

/**
 * Stores only the crash records not stored before. The main and `:ai`
 * processes both read the same UID crash buffer, so the check and the append
 * run under a file lock shared by the processes (F-7).
 */
private fun persistUnseenRetainedCrash(context: Context, retained: String) {
    val directory = context.noBackupFilesDir
    val markerFile = File(directory, CRASH_CAPTURE_MARKER_FILE_NAME)
    RandomAccessFile(File(directory, CRASH_CAPTURE_LOCK_FILE_NAME), "rw").use { lockFile ->
        val lock = lockFile.channel.lock()
        try {
            val lastStored = markerFile.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
                ?: legacyStoredCrashLine(context, retained)
            val unseen = unseenRetainedCrashLines(retained, lastStored)
            if (unseen.isEmpty() || appendPersistentLogNow("CRASH", "Android", unseen)) {
                retained.lines().lastOrNull { it.isNotBlank() }?.let { markerFile.writeText(it) }
            }
        } finally {
            lock.release()
        }
    }
}

/** Versions before the marker file stored a digest of the whole buffer. */
private fun legacyStoredCrashLine(context: Context, retained: String): String? {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(retained.toByteArray()).joinToString("") { "%02x".format(it) }
    val prefs = context.getSharedPreferences("persistent_crash_capture", Context.MODE_PRIVATE)
    return if (prefs.getString("last_digest", null) == digest) {
        retained.lines().lastOrNull { it.isNotBlank() }
    } else {
        null
    }
}

private const val CRASH_CAPTURE_MARKER_FILE_NAME = "persistent_crash_capture_last_line"
private const val CRASH_CAPTURE_LOCK_FILE_NAME = "persistent_crash_capture.lock"

internal class LogcatRun(val exitCode: Int, val output: String)

/**
 * Reads the crash buffer, falling back to the unfiltered command when `--uid`
 * is rejected (G17): older logcat versions do not know the option and print
 * only an error. Without READ_LOGS logd returns only this app's own UID, which
 * is what `--uid` selected, so the fallback reads the same records.
 */
internal fun readRetainedCrashLog(uid: Int, run: (List<String>) -> LogcatRun?): String {
    val base = listOf("logcat", "-b", "crash", "-d", "-v", "time", "-t", "500")
    run(base + "--uid=$uid" + "*:E")?.takeIf(::isAcceptedLogcatRun)?.let { return it.output }
    return run(base + "*:E")?.takeIf(::isAcceptedLogcatRun)?.output.orEmpty()
}

private fun isAcceptedLogcatRun(run: LogcatRun): Boolean =
    run.exitCode == 0 &&
        !run.output.trimStart().startsWith("Usage:") &&
        !run.output.contains("nrecognized option", ignoreCase = true)

private fun runLogcatCommand(command: List<String>): LogcatRun? = runCatching {
    val process = ProcessBuilder(command).start()
    try {
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lineSequence().take(500).joinToString("\n")
        }
        if (!process.waitFor(LOGCAT_EXIT_WAIT_SECONDS, TimeUnit.SECONDS)) {
            process.destroy()
            null
        } else {
            LogcatRun(process.exitValue(), output)
        }
    } finally {
        runCatching { process.errorStream.close() }
    }
}.getOrNull()

private const val LOGCAT_EXIT_WAIT_SECONDS = 5L

private fun appendPersistentLog(level: String, tag: String, message: String, throwable: Throwable? = null) {
    if (persistentLogContext == null) return
    if (!shouldPersistAndroidLogLevel(level, persistentLogDebuggable)) return
    persistentLogExecutor.execute { appendPersistentLogNow(level, tag, message, throwable) }
}

private fun appendPersistentLogNow(
    level: String,
    tag: String,
    message: String,
    throwable: Throwable? = null
): Boolean {
    val file = resolvePersistentLogFile() ?: return false
    val text = formatPersistentLogLine(level, tag, message) +
        (throwable?.stackTraceToString()?.plus("\n") ?: "")
    return synchronized(persistentLogLock) {
        runCatching {
            if (shouldResetPersistentLog(file.length())) file.delete()
            file.parentFile?.mkdirs()
            file.appendText(if (persistentLogDebuggable) text else redactLogUrls(text))
        }.isSuccess
    }
}

actual object Logger {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
        appendPersistentLog("DEBUG", tag, message)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        if (persistentLogContext != null) {
            // android.util.Log prints exactly this text. Expect it before
            // printing so the logcat capture cannot see the copy first.
            loggerLogcatEchoes.expect(
                tag,
                if (throwable == null) message else message + "\n" + Log.getStackTraceString(throwable)
            )
        }
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
