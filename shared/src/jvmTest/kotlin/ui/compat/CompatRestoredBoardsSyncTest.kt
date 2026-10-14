package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatSettingsBackup
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.encodeCompatSettingsBackup
import com.valoser.futacha.shared.compat.modernBoardsToCompatibility
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.state.FakePlatformStateStorage
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M2: a restored backup's boards live only in the compatibility store, and in ふたちゃ / ふたばー風 the board mirror drops
 * what the AppStateStore does not have at its next change. The restore adds them to the AppStateStore first.
 */
class CompatRestoredBoardsSyncTest {
    private val may = BoardSummary("may", "虹裏", "", "https://may.2chan.net/b/", "")
    private val img = CompatBoard(
        key = com.valoser.futacha.shared.compat.compatBoardKey("https://img.2chan.net/b/"),
        name = "img",
        canonicalUrl = "https://img.2chan.net/b/",
        originalUrl = "https://img.2chan.net/b/",
        sortOrder = 1
    )

    private fun <T> withStores(block: suspend (DesktopCompatibilityStore, AppStateStore) -> T): T = runBlocking {
        val directory = Files.createTempDirectory("restored-boards-sync").toFile()
        val compat = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            compat.initialize()
            val appState = AppStateStore(FakePlatformStateStorage())
            appState.setBoards(listOf(may))
            compat.upsertBoards(modernBoardsToCompatibility(listOf(may)))
            block(compat, appState)
        } finally {
            compat.close()
            directory.deleteRecursively()
        }
    }

    /** What the AppState-side board mirror keeps: every compatibility board the AppStateStore does not have is dropped. */
    private fun mirrorKeeps(appState: List<BoardSummary>): Set<String> =
        modernBoardsToCompatibility(appState).mapTo(HashSet()) { it.key }

    private suspend fun restoreBackupWithImg(compat: DesktopCompatibilityStore) {
        val mayCompat = modernBoardsToCompatibility(listOf(may)).single()
        val backup = CompatSettingsBackup(exportedAtEpochMillis = 1L, boards = listOf(mayCompat, img))
        compat.importSettingsBackup(encodeCompatSettingsBackup(backup), restoreUserSettings = true, restoreNgRules = false)
    }

    @Test
    fun aRestoredBoardIsAddedToTheAppStateBoardsSoTheMirrorKeepsIt() = withStores { compat, appState ->
        restoreBackupWithImg(compat)
        assertTrue(compat.boards.first().any { it.key == img.key }, "the restore must have brought the board")
        // Without the merge the mirror would delete it (the defect).
        assertFalse(img.key in mirrorKeeps(appState.boards.first()))

        mergeRestoredBoardsIntoAppState(compat, appState)

        val boards = appState.boards.first()
        assertEquals(listOf("may"), boards.map { it.id }.take(1), "the boards already there keep their place")
        assertTrue(boards.any { it.url == img.originalUrl }, "the restored board is in the AppStateStore")
        assertTrue(img.key in mirrorKeeps(boards), "the mirror keeps the restored board")
    }

    @Test
    fun nothingIsRemovedFromTheAppStateBoards() = withStores { compat, appState ->
        // A board the compatibility store does not have (the restore replaced its list) is not dropped by the merge.
        val local = BoardSummary("loc", "ローカル", "", "https://dat.2chan.net/b/", "")
        appState.setBoards(listOf(may, local))
        restoreBackupWithImg(compat)

        mergeRestoredBoardsIntoAppState(compat, appState)

        val urls = appState.boards.first().map { it.url }
        assertTrue(may.url in urls && local.url in urls && img.originalUrl in urls)
        assertEquals(urls.size, urls.toSet().size, "no board is added twice")
    }

    @Test
    fun mergingTwiceChangesNothingTheSecondTime() = withStores { compat, appState ->
        restoreBackupWithImg(compat)
        mergeRestoredBoardsIntoAppState(compat, appState)
        val once = appState.boards.first()
        mergeRestoredBoardsIntoAppState(compat, appState)
        assertEquals(once, appState.boards.first())
    }

    @Test
    fun withoutAnAppStateStoreAsInToshiakiNothingIsDone() = withStores { compat, appState ->
        restoreBackupWithImg(compat)
        val before = appState.boards.first()
        mergeRestoredBoardsIntoAppState(compat, null)
        assertEquals(before, appState.boards.first())
    }
}
