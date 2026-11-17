package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.mergeCompatibilityHistory
import com.valoser.futacha.shared.compat.toCompatHistoryEntry
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 2026-10-14の監査A8：ふたったー風の履歴の削除は、共有の履歴だけを消していたため、他モードの履歴（共有ストア）から
 * 起動時に戻り、自分の書き込みの記録と自動保存も残った。ふたちゃの`dismissHistoryEntry`と同じ後片付けを、まとめて行う。
 */
class DismissHistoryEntriesTest {
    private fun entry(n: Int) = ThreadHistoryEntry(
        threadId = "$n", boardId = "img", title = "スレ$n", titleImageUrl = "", boardName = "img",
        boardUrl = "https://may.2chan.net/img/res/$n.htm", lastVisitedEpochMillis = 1L, replyCount = 3
    )

    private fun saved(n: Int) = SavedThread(
        threadId = "$n", boardId = "img", boardName = "img", title = "保存$n", thumbnailPath = null, savedAt = 1L,
        postCount = 3, imageCount = 0, videoCount = 0, totalSize = 10L, status = SaveStatus.COMPLETED
    )

    @Test
    fun deletingSeveralRowsCleansUpEverywhereSoTheyDoNotComeBackAtTheNextStart() = runBlocking {
        val root = Files.createTempDirectory("futacha-dismiss-many").toFile()
        val desktop = DesktopCompatibilityStore(JvmFileSystem(root))
        try {
            desktop.initialize()
            val deleted = mutableListOf<String>()
            var writes = 0
            val compat = object : CompatibilityStore by desktop {
                override suspend fun deleteHistory(canonicalUrl: String) {
                    writes++
                    deleted += canonicalUrl
                    desktop.deleteHistory(canonicalUrl)
                }
                override suspend fun deleteHistories(canonicalUrls: Collection<String>) {
                    writes++
                    deleted += canonicalUrls
                    desktop.deleteHistories(canonicalUrls)
                }
            }
            val store = AppStateStore(FakePlatformStateStorage())
            val repository = SavedThreadRepository(InMemoryFileSystem(), baseDirectory = "saved_threads")
            val rows = (1..3).map(::entry)
            store.setHistory(rows)
            rows.forEach { compat.recordHistoryVisit(it.toCompatHistoryEntry()!!) }
            store.addSelfPostIdentifier(threadId = "1", identifier = "10", boardId = "img")
            store.addSelfPostIdentifier(threadId = "3", identifier = "30", boardId = "img")
            repository.addThreadToIndex(saved(1)).getOrThrow()
            repository.addThreadToIndex(saved(3)).getOrThrow()

            dismissHistoryEntries(
                stateStore = store,
                autoSavedThreadRepository = repository,
                compatibilityStore = compat,
                entries = listOf(entry(1), entry(2))
            )

            assertEquals(listOf("3"), store.history.first().map { it.threadId })
            // 他モードの履歴の行も消えるので、次の起動で共有の履歴へ戻らない（MainActivity・MainViewControllerの統合）。
            val merged = mergeCompatibilityHistory(store.history.first(), compat.history.first())
            assertEquals(listOf("3"), merged.map { it.threadId })
            // 共有ストアに無い行のためだけに書かない。
            assertEquals(2, deleted.size)
            // In one write (re-audit M27: one per row, each re-reading and publishing the whole history).
            assertEquals(1, writes)
            assertEquals(listOf("30"), store.selfPostIdentifiersByThread.first().values.flatten())
            assertEquals(listOf("3"), repository.getAllThreads().map { it.threadId })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nothingToDeleteWritesNothing() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        store.setHistory(listOf(entry(1)))
        dismissHistoryEntries(stateStore = store, autoSavedThreadRepository = null, compatibilityStore = null, entries = emptyList())
        assertEquals(listOf("1"), store.history.first().map { it.threadId })
    }
}
