package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.COMPAT_SHARED_DATA_KEY_PREFIX
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.decodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.compat.shared.SingleMode.img
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.compat.shared.SingleMode.tutorial
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.BoardSettingKeys
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.state.boardSettingKeys
import com.valoser.futacha.shared.state.resolveEffectiveWatchWordsForBoard
import com.valoser.futacha.shared.ui.board.resolveCatalogModeSyncValue
import com.valoser.futacha.shared.ui.board.resolveInitialCatalogMode
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-B1 (docs/futaber-evolution-data-2026-10-31.md §2.1, §3.2): AppState's catalog modes and board watch words move from
 * the board's id (a slug that differs between devices and after a restore) to its shared key, read both, write one.
 */
class BoardKeyedSettingsTest {
    private val mayKey = compatBoardKey("https://may.2chan.net/b/")
    private val imgKey = compatBoardKey("https://img.2chan.net/b/")

    private fun withState(block: suspend (AppStateStore) -> Unit) = runBlocking {
        val state = AppStateStore(FakePlatformStateStorage())
        try { block(state) } finally { state.close() }
    }

    @Test fun anOfficialBoardsKeysAreItsSharedKeyAndItsIdAndAnotherBoardKeepsItsId() {
        assertEquals(BoardSettingKeys(mayKey, "b"), boardSettingKeys(may))
        assertEquals(BoardSettingKeys("t"), boardSettingKeys(tutorial))
        // A board that came from としあき(仮) already has the shared key as its id.
        assertEquals(BoardSettingKeys(mayKey), boardSettingKeys(may.copy(id = mayKey)))
        assertEquals(BoardSettingKeys(mayKey, may.url), boardSettingKeys("", may.url))
        assertNull(boardSettingKeys(" ", null))
    }

    @Test fun aValueUnderTheEarlierKeyIsReadAndAWriteMovesItToTheSharedKey() = withState { state ->
        // What an earlier version wrote.
        state.setCatalogMode("b", CatalogMode.New)
        state.setBoardWatchWords("b", listOf("古い"))
        val keys = boardSettingKeys(may)!!
        assertEquals(CatalogMode.New, resolveInitialCatalogMode(may.id, state.catalogModes.first(), may.url))
        assertEquals(CatalogMode.New, resolveCatalogModeSyncValue(may.id, state.catalogModes.first(), may.url))
        assertEquals(listOf("古い"), resolveEffectiveWatchWordsForBoard(listOf("共通"), state.boardWatchWords.first(), keys))
        // The screens write under the shared key and the earlier copy goes.
        state.setCatalogMode(keys, CatalogMode.Many)
        state.updateBoardWatchWords(keys) { it + "新しい" }
        assertEquals(mapOf(mayKey to CatalogMode.Many), state.catalogModes.first())
        assertEquals(mapOf(mayKey to listOf("古い", "新しい")), state.boardWatchWords.first())
        assertEquals(CatalogMode.Many, resolveInitialCatalogMode(may.id, state.catalogModes.first(), may.url))
        // The same board under another id (restored, or added again on another device) finds them.
        assertEquals(CatalogMode.Many, resolveInitialCatalogMode("b1", state.catalogModes.first(), may.url))
        state.clearBoardWatchWordsOverride(keys)
        assertEquals(listOf("共通"), resolveEffectiveWatchWordsForBoard(listOf("共通"), state.boardWatchWords.first(), keys))
        // A board that is not an official one keeps its id, as before.
        state.setCatalogMode(boardSettingKeys(tutorial)!!, CatalogMode.Old)
        assertEquals(CatalogMode.Old, state.catalogModes.first()["t"])
    }

    @Test fun theMigrationMovesTheEarlierKeysOnceAndAgainAfterAnOlderVersionWroteThem() = withState { state ->
        val fromToshiaki = img.copy(id = imgKey)
        state.setBoards(listOf(may, fromToshiaki, tutorial))
        state.setCatalogMode("b", CatalogMode.New)
        state.setCatalogMode("t", CatalogMode.Old)
        state.setCatalogMode("gone", CatalogMode.Many)
        state.setCatalogMode(imgKey, CatalogMode.Many)
        state.setBoardWatchWords("b", listOf("may"))
        val marks = object : SharedMigrationMarkStore {
            val rows = HashMap<String, SharedMigrationMark>()
            override suspend fun loadMarks() = rows.toMap()
            override suspend fun saveMark(mark: SharedMigrationMark) { rows[mark.stageId] = mark }
        }
        var clock = 1L
        val runner = SharedDataMigrationRunner(marks) { clock }
        assertEquals(listOf("boardkeyed-maps-v1"), runner.run(SharedDataMigrations.stagesFor(state)).ran)
        assertEquals(
            mapOf(mayKey to CatalogMode.New, "t" to CatalogMode.Old, "gone" to CatalogMode.Many, imgKey to CatalogMode.Many),
            state.catalogModes.first(),
            "a listed official board's value moves; another board's, and a key no board has, stay"
        )
        assertEquals(mapOf(mayKey to listOf("may")), state.boardWatchWords.first())
        assertEquals(listOf("boardkeyed-maps-v1"), runner.run(SharedDataMigrations.stagesFor(state)).skipped)
        // An older version of the app (after a downgrade) wrote the board's mode under its id again: that one is newer.
        state.setCatalogMode("b", CatalogMode.Old)
        clock = 2L
        assertEquals(listOf("boardkeyed-maps-v1"), runner.run(SharedDataMigrations.stagesFor(state)).ran)
        assertEquals(CatalogMode.Old, state.catalogModes.first()[mayKey])
        assertFalse("b" in state.catalogModes.first())
    }

    @Test fun theMarksAreInTheStoresSharedRowsNotInTheSettingsOrTheBackup() = runBlocking {
        val root = Files.createTempDirectory("futacha-shared-rows").toFile()
        val state = AppStateStore(FakePlatformStateStorage())
        try {
            val compat = DesktopCompatibilityStore(JvmFileSystem(root))
            compat.initialize()
            state.setBoards(listOf(may))
            state.setCatalogMode("b", CatalogMode.New)
            val run = assertNotNull(runSharedDataMigrations(state, compat))
            assertEquals(listOf("boardkeyed-maps-v1"), run.ran)
            assertTrue(compat.loadSharedData().keys.any { it == SharedDataMigrations.markKey("boardkeyed-maps-v1") })
            assertTrue(compat.preferences.first().keys.none { it.startsWith(COMPAT_SHARED_DATA_KEY_PREFIX) })
            assertFalse(COMPAT_SHARED_DATA_KEY_PREFIX in compat.exportSettingsBackup())
            compat.close()
            // Kept across a restart.
            val reopened = DesktopCompatibilityStore(JvmFileSystem(root))
            reopened.initialize()
            assertEquals(listOf("boardkeyed-maps-v1"), assertNotNull(runSharedDataMigrations(state, reopened)).skipped)
            reopened.close()
        } finally {
            state.close()
            root.deleteRecursively()
        }
    }

    @Test fun aBoardDeletedEarlierAndRestoredFromABackupStaysWithWhatWasRestored() = runBlocking {
        val root = Files.createTempDirectory("futacha-restore-deleted").toFile()
        val state = AppStateStore(FakePlatformStateStorage())
        try {
            val compat = DesktopCompatibilityStore(JvmFileSystem(root))
            compat.initialize()
            state.setBoards(listOf(may, img))
            reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE)
            // Deleted in ふたちゃ: tombstone, gone from both.
            state.updateBoards { boards -> boards.filterNot { it.url == img.url } }
            reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE)
            assertTrue(compat.boards.first().none { it.key == imgKey })
            // Restored from a backup that has it, with a tab.
            val tab = SingleMode.tab(img, "200", insertedAt = 5)
            val payload = encodeCompatSettingsBackup(
                CompatSettingsBackup(exportedAtEpochMillis = 1, boards = modernBoardsToCompatibility(listOf(img)), tabs = listOf(tab), workspace = null)
            )
            withSharedBoardsRestore(compat, state, keep = { true }) { compat.importSettingsBackup(payload) }
            reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE)
            assertTrue(state.boards.first().any { it.url == img.url || it.id == imgKey }, "the restored board is in the list")
            assertTrue(compat.boards.first().any { it.key == imgKey }, "and in the store")
            assertTrue(compat.tabs.first().any { it.key == tab.key }, "with its tab")
            // The same in としあき(仮), where the store is the mode's own list.
            compat.deleteBoard(imgKey)
            reconcileSharedBoards(state, compat, SharedBoardSide.COMPAT)
            assertTrue(state.boards.first().none { it.url == img.url || it.id == imgKey })
            withSharedBoardsRestore(compat, null, keep = { true }) { compat.importSettingsBackup(payload) }
            reconcileSharedBoards(state, compat, SharedBoardSide.COMPAT)
            assertTrue(compat.tabs.first().any { it.key == tab.key })
            assertTrue(state.boards.first().any { it.id == imgKey || it.url.contains("img.2chan.net/b") })
            assertEquals(1, decodeCompatSettingsBackup(compat.exportSettingsBackup()).tabs.size)
            compat.close()
        } finally {
            state.close()
            root.deleteRecursively()
        }
    }

    @Test fun boardsFromEitherListReachTheOtherAndADeletionInEitherSticks() = runBlocking {
        val root = Files.createTempDirectory("futacha-either-list").toFile()
        val state = AppStateStore(FakePlatformStateStorage())
        val third = BoardSummary("dat", "dat", "", "https://dat.2chan.net/b/futaba.php", "")
        try {
            val compat = DesktopCompatibilityStore(JvmFileSystem(root))
            compat.initialize()
            state.setBoards(listOf(may))
            reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE)
            // Added in としあき(仮): reaches the AppState list.
            compat.upsertBoards(modernBoardsToCompatibility(listOf(may, img)))
            reconcileSharedBoards(state, compat, SharedBoardSide.COMPAT)
            assertEquals(setOf(may.url, img.url), state.boards.first().map { it.url }.toSet())
            // Added in ふたちゃ: reaches the store.
            state.updateBoards { it + third }
            reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE)
            assertTrue(compat.boards.first().any { it.key == compatBoardKey("https://dat.2chan.net/b/") })
            // In としあき(仮) (its pass ran when it opened), deleted there, and the app goes to ふたちゃ before
            // としあき(仮)'s next pass: the record's side still takes the deletion from the store.
            reconcileSharedBoards(state, compat, SharedBoardSide.COMPAT)
            compat.deleteBoard(imgKey)
            reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE)
            assertTrue(state.boards.first().none { it.url == img.url })
            // A slow screen writes it back into the store: it goes again and does not reach the list.
            compat.importModernBoards(listOf(img))
            reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE)
            assertTrue(compat.boards.first().none { it.key == imgKey })
            assertTrue(state.boards.first().none { it.url == img.url })
            // Added again by the person: back in both.
            state.updateBoards { it + img }
            reconcileSharedBoards(state, compat, SharedBoardSide.APP_STATE)
            assertTrue(compat.boards.first().any { it.key == imgKey })
            assertTrue(state.boards.first().any { it.url == img.url })
            compat.close()
        } finally {
            state.close()
            root.deleteRecursively()
        }
    }
}
