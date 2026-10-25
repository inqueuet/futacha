package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** L96: one failed update does not stop the bridge between the AppState modes and としあき(仮). */
class ProfileDataBridgeTest {
    private fun board(slug: String) = BoardSummary(slug, slug, "", "https://may.2chan.net/$slug/", "")

    @Test fun aFailedDeletionIsCompletedByTheNextChange() = runBlocking {
        val root = Files.createTempDirectory("l96-bridge").toFile()
        val desktop = DesktopCompatibilityStore(JvmFileSystem(root))
        val stateStore = AppStateStore(FakePlatformStateStorage())
        try {
            desktop.initialize()
            val deletions = AtomicInteger()
            val compat = object : CompatibilityStore by desktop {
                override suspend fun deleteBoard(boardKey: String) {
                    if (deletions.incrementAndGet() == 1) throw IOException("L96 test: disk full")
                    desktop.deleteBoard(boardKey)
                }
            }
            stateStore.setBoards(listOf(board("a"), board("b")))
            val failures = CopyOnWriteArrayList<String>()
            val bridge = launch(Dispatchers.Default) {
                bridgeProfileBoardsAndHistory(stateStore, compat, usesAppStateData = true) { operation, _ -> failures += operation }
            }
            suspend fun compatSlugs() = desktop.boards.first().map { it.canonicalUrl.removeSuffix("/").substringAfterLast('/') }.toSet()
            withTimeout(5_000) { while (compatSlugs() != setOf("a", "b")) delay(20) }

            stateStore.setBoards(listOf(board("a")))
            withTimeout(5_000) { while (failures.isEmpty()) delay(20) }
            assertEquals(listOf("boards"), failures.toList())

            stateStore.setBoards(listOf(board("a"), board("c")))
            withTimeout(5_000) { while (compatSlugs() != setOf("a", "c")) delay(20) }
            assertTrue(deletions.get() >= 2, "The deletion was tried again")
            bridge.cancel()
        } finally {
            stateStore.close()
            root.deleteRecursively()
        }
    }
}
