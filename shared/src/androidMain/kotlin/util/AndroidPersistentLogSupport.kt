package com.valoser.futacha.shared.util

/**
 * Levels kept in the persistent Android log. Debuggable builds keep every
 * level for diagnosis; release builds keep only warnings, errors and the
 * crash/error logcat records (S-4).
 */
internal fun shouldPersistAndroidLogLevel(level: String, debuggable: Boolean): Boolean =
    debuggable || (level != "DEBUG" && level != "INFO")

private val logUrlPattern = Regex(
    """\b([A-Za-z][A-Za-z0-9+.\-]{1,15})://(?:[^\s/?#"'<>@]*@)?([^\s/?#"'<>]*)([^\s"'<>]*)"""
)
private val logQueryPattern = Regex("""\?[^\s"'<>?]*=[^\s"'<>]*""")

/**
 * Removes what a URL can reveal beyond its server: user info, path, query and
 * fragment (thread numbers, search words, SAF folder names, keys). The scheme
 * and host stay so a failing server can still be told apart. A bare query
 * string (`?key=value`) is removed as well.
 */
internal fun redactLogUrls(text: String): String =
    text.replace(logUrlPattern) { match ->
        val (scheme, host, rest) = match.destructured
        if (rest.isEmpty()) "$scheme://$host" else "$scheme://$host/…"
    }.replace(logQueryPattern, "?…")

/** The message part of a `-v time` logcat line ("... E/Tag( pid): message"). */
internal fun logcatLineMessage(line: String): String? {
    val levelIndex = line.indexOf(" E/")
    if (levelIndex < 0) return null
    val open = line.indexOf('(', levelIndex + 3).takeIf { it > levelIndex + 3 } ?: return null
    val close = line.indexOf("):", open).takeIf { it >= 0 } ?: return null
    return line.substring(close + 2).removePrefix(" ")
}

/**
 * Logger.e writes its record directly and through android.util.Log; the live
 * logcat capture then sees the same lines again. Each Logger.e line is
 * expected here once and its logcat copy is skipped, so the error is stored
 * once (F-7). Unmatched expectations are dropped oldest first.
 */
internal class LogcatEchoFilter(private val capacity: Int = 2_048) {
    private val pending = ArrayDeque<String>()
    private val counts = HashMap<String, Int>()

    @Synchronized
    fun expect(tag: String, text: String) {
        text.split('\n').forEach { line ->
            val key = key(tag, line)
            pending.addLast(key)
            counts[key] = (counts[key] ?: 0) + 1
            while (pending.size > capacity) release(pending.removeFirst())
        }
    }

    /** True when [line] is the logcat copy of an expected Logger.e line. */
    @Synchronized
    fun consumeIfEcho(line: String): Boolean {
        val tag = logcatLineTag(line) ?: return false
        val message = logcatLineMessage(line) ?: return false
        val key = key(tag, message)
        if ((counts[key] ?: 0) == 0) return false
        pending.remove(key)
        release(key)
        return true
    }

    private fun release(key: String) {
        val remaining = (counts[key] ?: return) - 1
        if (remaining <= 0) counts.remove(key) else counts[key] = remaining
    }

    private fun key(tag: String, line: String) = tag.trim() + '\u0000' + line.trimEnd()
}

/**
 * The part of the crash buffer that was not stored yet. The crash buffer keeps
 * earlier crashes; appending the whole buffer after each new crash stored the
 * old ones again (F-7). Lines carry a timestamp and pid, so the last stored
 * line marks where the new records begin. When it is gone (buffer rotated,
 * reboot) everything is new.
 */
internal fun unseenRetainedCrashLines(retained: String, lastStoredLine: String?): String {
    val lines = retained.lines().filter { it.isNotBlank() }
    if (lastStoredLine.isNullOrBlank()) return lines.joinToString("\n")
    val index = lines.lastIndexOf(lastStoredLine)
    return (if (index < 0) lines else lines.drop(index + 1)).joinToString("\n")
}
