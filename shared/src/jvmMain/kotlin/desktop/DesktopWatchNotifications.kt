package com.valoser.futacha.shared.desktop

import com.valoser.futacha.shared.compat.canonicalizeThreadUrl
import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.service.CatalogWatchAlertMatch
import com.valoser.futacha.shared.service.WatchAlertNotificationLedger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.moveReplacingWithRetry
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Persist only notifications accepted by the OS, so failed delivery can be retried.
 * The ledger is also kept in memory: a delivered notification whose ledger write still
 * fails after the replace retries is not shown again, and the write is retried next time (N4-3).
 */
internal class DesktopWatchNotifications(
    private val ledger: File,
    private val move: (Path, Path, Array<out CopyOption>) -> Unit = { source, target, options -> Files.move(source, target, *options) },
    private val deliver: suspend (String, String, String, String?) -> Unit = DesktopOsIntegration::notify
) {
    private val mutex = Mutex()
    /** Latest ledger including entries not yet on disk; null until something was recorded. */
    private var recorded: String? = null
    private var unsaved = false

    suspend fun notify(
        matches: List<CatalogWatchAlertMatch>,
        sessionMutex: Mutex = Mutex(),
        isCurrent: suspend () -> Boolean
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (unsaved) persist(requireNotNull(recorded))
            var entries = recorded ?: if (ledger.isFile) ledger.inputStream().bufferedReader().use { it.readTextBounded() } else ""
            val pending = WatchAlertNotificationLedger.filterNewMatches(entries, matches = matches).distinctBy { it.identityKey }
            for (match in pending) {
                ensureActive()
                // A match that cannot be delivered is skipped, not thrown: the exception ended the run
                // before the later matches, on every run (L126).
                val threadId = match.threadId.trim()
                if (threadId.isEmpty() || !threadId.all { it in '0'..'9' }) {
                    Logger.w("DesktopWatchNotifications", "Skipping a watch match without a thread number")
                    continue
                }
                // Every match is shown, as on Android and iOS; only an official 2chan thread is linked,
                // because a click opens it in the app. Elsewhere a click just brings the app forward.
                val url = runCatching { BoardUrlResolver.resolveThreadUrl(match.boardUrl, threadId) }.getOrNull()
                    ?.takeIf { canonicalizeThreadUrl(it) != null }
                var accepted = false
                // Release the profile lock between notifications so a large batch cannot block switching modes.
                sessionMutex.withLock {
                    if (isCurrent()) {
                        val identifier = UUID.nameUUIDFromBytes((url ?: match.identityKey).toByteArray(Charsets.UTF_8)).toString()
                        withTimeout(10_000) { deliver(identifier, "監視ワード: ${match.boardName}", match.title, url) }
                        // Once the OS accepts it, finish the ledger even if the screen/profile closes.
                        withContext(NonCancellable) {
                            entries = WatchAlertNotificationLedger.markMatches(entries, matches = listOf(match), nowMillis = System.currentTimeMillis())
                            recorded = entries
                            unsaved = true
                            persist(entries)
                        }
                        accepted = true
                    }
                }
                if (!accepted) return@withContext
            }
        }
    }

    private fun persist(entries: String) {
        val temporary = File(ledger.parentFile, "${ledger.name}.pending")
        try {
            temporary.outputStream().use { output -> output.write(entries.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            moveReplacingWithRetry(temporary.toPath(), ledger.toPath(), move)
            unsaved = false
        } catch (failure: Exception) {
            // The OS already showed it; the in-memory ledger suppresses a repeat until a later write succeeds.
            Logger.w("DesktopWatchNotifications", "Failed to record notification: ${failure.message}")
            temporary.delete()
        }
    }

    private fun java.io.Reader.readTextBounded(): String {
        val buffer = CharArray(2 * 1024 * 1024)
        var count = 0
        while (count < buffer.size) {
            val read = read(buffer, count, buffer.size - count)
            if (read < 0) break
            count += read
        }
        return String(buffer, 0, count)
    }
}
