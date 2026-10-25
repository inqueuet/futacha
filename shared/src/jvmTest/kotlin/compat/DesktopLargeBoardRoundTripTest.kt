package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.util.JvmFileSystem
import com.valoser.futacha.shared.model.MAX_REGISTERED_BOARDS
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopLargeBoardRoundTripTest {
    @Test fun bootstrapKeepsAll201BoardsAcrossModeSwitchingAndReopening(): Unit = runBlocking { check(true) }
    @Test fun upsertKeepsAll201BoardsAcrossModeSwitchingAndReopening(): Unit = runBlocking { check(false) }

    @Test fun aFullStoreRejectsANewBoardWithoutLosingExistingBoardsAndCanStillRenameOne(): Unit = runBlocking {
        val root = Files.createTempDirectory("futacha-board-capacity").toFile()
        val fs = JvmFileSystem(root)
        val boards = (0 until MAX_REGISTERED_BOARDS).map { index ->
            val url = "https://may.2chan.net/board$index/"
            CompatBoard(compatBoardKey(url), "板$index", url, url, index)
        }
        var store: DesktopCompatibilityStore? = null
        try {
            val seed = DesktopCompatibilityDatabase(fs)
            try {
                val payload = """{"boards":${Json.encodeToString(ListSerializer(CompatBoard.serializer()), boards)}}"""
                seed.writePayload(payload, 1)
            } finally { seed.close() }
            val active = DesktopCompatibilityStore(fs).also { store = it; it.initialize() }
            assertEquals(MAX_REGISTERED_BOARDS, active.boards.first().size)
            val extra = boards.last().copy(key = "new", canonicalUrl = "https://may.2chan.net/new/", originalUrl = "https://may.2chan.net/new/")
            assertFailsWith<IllegalArgumentException> { active.upsertBoard(extra) }
            assertEquals(boards, active.boards.first())
            active.upsertBoard(boards.first().copy(name = "改名"))
            active.close()
            val reopened = DesktopCompatibilityStore(fs).also { store = it; it.initialize() }
            assertEquals(MAX_REGISTERED_BOARDS, reopened.boards.first().size)
            assertEquals("改名", reopened.boards.first().first().name)
            assertEquals(boards.last(), reopened.boards.first().last())
        } finally { store?.close(); root.deleteRecursively() }
    }

    private suspend fun check(bootstrap: Boolean) {
        val root = Files.createTempDirectory("futacha-many-boards").toFile()
        val fs = JvmFileSystem(root)
        var store = DesktopCompatibilityStore(fs)
        try {
            store.initialize()
            verifyLargeBoardRoundTrip(store, bootstrap) {
                store.close()
                DesktopCompatibilityStore(fs).also { store = it; it.initialize() }
            }
        } finally { store.close(); root.deleteRecursively() }
    }
}
