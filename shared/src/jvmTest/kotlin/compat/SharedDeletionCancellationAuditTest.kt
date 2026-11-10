package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.ui.clearHistory
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.nio.file.Files
import kotlin.test.*

class SharedDeletionCancellationAuditTest {
    @Test fun modeSwitchDuringClearMustNotLeaveOtherModesHistory() = runBlocking {
        val root = Files.createTempDirectory("history-clear-audit").toFile()
        val desktop = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            desktop.initialize()
            val modern = AppStateStore(FakePlatformStateStorage())
            val entry = ThreadHistoryEntry("123", "may", "題", "", "may", "https://may.2chan.net/b/res/123.htm", 1, 3)
            modern.setHistory(listOf(entry))
            desktop.recordHistoryVisit(entry.toCompatHistoryEntry()!!)
            lateinit var screenJob: Job
            val compat = object : CompatibilityStore by desktop {
                override suspend fun clearHistory() {
                    desktop.clearHistory()
                    // The screen's job is cancelled after the first store commits.
                    screenJob.cancel()
                    yield()
                }
            }
            screenJob = launch(start = CoroutineStart.LAZY) {
                clearHistory(modern, null, compatibilityStore = compat, onSkippedThreadsCleared = {})
            }
            screenJob.start()
            screenJob.join()
            assertTrue(desktop.history.first().isEmpty())
            assertTrue(modern.history.first().isEmpty(), "全モードの履歴が消えるべき")
        } finally { desktop.close(); root.deleteRecursively() }
    }
}

/** Audit F2 (2026-11-09): one row's deletion must not stop half way. */
class SharedDismissCancellationTest {
    @Test fun cancellingAfterTheSharedStoresDeleteStillRemovesTheAppStateRow() = runBlocking {
        val root = Files.createTempDirectory("history-dismiss-audit").toFile()
        val desktop = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            desktop.initialize()
            val modern = AppStateStore(FakePlatformStateStorage())
            val entry = ThreadHistoryEntry("123", "may", "題", "", "may", "https://may.2chan.net/b/res/123.htm", 1, 3)
            modern.setHistory(listOf(entry))
            desktop.recordHistoryVisit(entry.toCompatHistoryEntry()!!)
            lateinit var screenJob: Job
            val compat = object : CompatibilityStore by desktop {
                override suspend fun deleteHistory(canonicalUrl: String) {
                    desktop.deleteHistory(canonicalUrl)
                    screenJob.cancel()
                    yield()
                }
            }
            screenJob = launch(start = CoroutineStart.LAZY) {
                com.valoser.futacha.shared.ui.dismissHistoryEntry(modern, null, compatibilityStore = compat, entry = entry)
            }
            screenJob.start()
            screenJob.join()
            assertTrue(desktop.history.first().isEmpty())
            assertTrue(modern.history.first().isEmpty(), "AppStateの行も消えるべき")
        } finally { desktop.close(); root.deleteRecursively() }
    }
}
