package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/** L100: としあき(仮)'s boards keep 2chan addresses on https, and the bridge does not bring http back. */
class CompatBoardHttpsTest {
    private val mayKey = compatBoardKey("https://may.2chan.net/b/")

    @Test fun boardsWrittenWithHttpAreKeptOnHttps() = runBlocking {
        val root = Files.createTempDirectory("l100-store").toFile()
        try {
            val store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()
            store.upsertBoard(CompatBoard(mayKey, "may", "https://may.2chan.net/b/", "http://may.2chan.net/b/", 0))
            store.importModernBoards(listOf(BoardSummary("img", "img", "", "http://img.2chan.net/b/", "")))
            assertEquals(setOf("https://may.2chan.net/b/", "https://img.2chan.net/b/"),
                store.boards.first().map { it.originalUrl }.toSet())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun theBridgeFromToshiakiDoesNotBringHttpBackToTheAppStateBoards() = runBlocking {
        val root = Files.createTempDirectory("l100-bridge").toFile()
        val stateStore = AppStateStore(FakePlatformStateStorage())
        try {
            val store = DesktopCompatibilityStore(JvmFileSystem(root))
            store.initialize()
            stateStore.setBoards(listOf(BoardSummary("may", "may", "", "https://may.2chan.net/b/", "")))
            store.upsertBoard(CompatBoard(mayKey, "may", "https://may.2chan.net/b/", "http://may.2chan.net/b/", 0))
            val bridge = launch(Dispatchers.Default) {
                bridgeProfileBoardsAndHistory(stateStore, store, usesAppStateData = false) { _, _ -> }
            }
            delay(500)
            withTimeout(5_000) { while (stateStore.boards.first().none { it.url.contains("may.2chan.net") }) delay(20) }
            assertEquals(listOf("https://may.2chan.net/b/"),
                stateStore.boards.first().filter { it.url.contains("may.2chan.net") }.map { it.url })
            bridge.cancel()
        } finally {
            stateStore.close()
            root.deleteRecursively()
        }
    }
}
