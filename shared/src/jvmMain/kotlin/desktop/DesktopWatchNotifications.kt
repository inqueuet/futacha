package com.valoser.futacha.shared.desktop

import com.valoser.futacha.shared.network.BoardUrlResolver
import com.valoser.futacha.shared.service.CatalogWatchAlertMatch
import com.valoser.futacha.shared.service.WatchAlertNotificationLedger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Persist only notifications accepted by the OS, so failed delivery can be retried. */
internal class DesktopWatchNotifications(
    private val ledger: File,
    private val deliver: suspend (String, String, String, String) -> Unit = DesktopOsIntegration::notify
) {
    private val mutex = Mutex()

    suspend fun notify(
        matches: List<CatalogWatchAlertMatch>,
        sessionMutex: Mutex = Mutex(),
        isCurrent: suspend () -> Boolean
    ) = mutex.withLock {
        withContext(Dispatchers.IO) {
            var entries = if (ledger.isFile) ledger.inputStream().bufferedReader().use { it.readTextBounded() } else ""
            val pending = WatchAlertNotificationLedger.filterNewMatches(entries, matches = matches).distinctBy { it.identityKey }
            for (match in pending) {
                ensureActive()
                var accepted = false
                // Release the profile lock between notifications so a large batch cannot block switching modes.
                sessionMutex.withLock {
                    if (isCurrent()) {
                        val url = BoardUrlResolver.resolveThreadUrl(match.boardUrl, match.threadId)
                        val identifier = UUID.nameUUIDFromBytes(url.toByteArray(Charsets.UTF_8)).toString()
                        withTimeout(10_000) { deliver(identifier, "監視ワード: ${match.boardName}", match.title, url) }
                        // Once the OS accepts it, finish the ledger even if the screen/profile closes.
                        withContext(NonCancellable) {
                            entries = WatchAlertNotificationLedger.markMatches(entries, matches = listOf(match), nowMillis = System.currentTimeMillis())
                            val temporary = File(ledger.parentFile, "${ledger.name}.pending")
                            temporary.outputStream().use { output -> output.write(entries.toByteArray(Charsets.UTF_8)); output.fd.sync() }
                            Files.move(temporary.toPath(), ledger.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                        }
                        accepted = true
                    }
                }
                if (!accepted) return@withContext
            }
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
