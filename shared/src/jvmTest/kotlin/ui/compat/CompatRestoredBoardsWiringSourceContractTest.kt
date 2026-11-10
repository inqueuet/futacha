package ui.compat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M2 wiring: both restore paths of the shared settings screen add the restored boards to the AppStateStore (ふたちゃ,
 * ふたばー風, ふたったー風), and としあき(仮), whose compatibility store owns the boards, is not given one. Since D-B1 the
 * merge is done by withSharedBoardsRestore, which also holds the board synchronization during the restore.
 */
class CompatRestoredBoardsWiringSourceContractTest {
    private fun source(path: String): String {
        val file = File("src/commonMain/kotlin/ui/$path")
        assertTrue(file.isFile, "$path not found from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun everyRestorePathHoldsTheBoardSynchronizationAndMergesTheRestoredBoards() {
        val text = source("compat/CompatSettingsScreen.kt")
        // D-B1: the restore runs inside withSharedBoardsRestore, which lifts the restored boards' tombstones and merges
        // them into the AppStateStore (mergeRestoredBoardsIntoAppState) before any board synchronization can run.
        val restores = Regex("""withSharedBoardsRestore\(""").findAll(text).toList()
        assertEquals(2, restores.size, "the file picker and the folder picker both restore")
        assertTrue(text.contains("""withSharedBoardsRestore(store, restoreBoardsInto.takeIf { modernPresentation }, keep = { it.isSuccess }, historyStore = restoreBoardsInto ?: stateStore)"""))
        // Saving a backup restores nothing, so it neither merges nor keeps anything.
        assertTrue(text.contains("""val restoring = !kind.startsWith("save_")"""))
        assertTrue(text.contains("""restoreBoardsInto.takeIf { modernPresentation && restoring }, keep = { restoring && it.isSuccess }"""))
        assertFalse(text.contains("mergeRestoredBoardsIntoAppState("), "the merge is the wrapper's, not the screen's")
        assertTrue(source("../compat/shared/SharedBoards.kt").contains("mergeRestoredBoardsIntoAppState(compat, appStateStore)"))
        // Plan B3: the restored history reaches the AppState history in every mode (としあき(仮)'s own store included).
        assertEquals(2, Regex("""historyStore = restoreBoardsInto \?: stateStore""").findAll(text).count())
        assertTrue(source("../compat/shared/SharedBoards.kt").contains("mergeRestoredHistoryIntoAppState(compat, historyStore)"))
    }

    @Test
    fun theModernModesPassTheirStoreAndToshiakiDoesNot() {
        assertTrue(source("futaber/FutaberSettingsScreen.kt").contains("restoreBoardsInto = stateStore"))
        assertTrue(source("board/FutachaSharedFeatures.kt").contains("restoreBoardsInto = appStateStore"))
        assertFalse(source("compat/CompatibilityApp.kt").contains("restoreBoardsInto"))
    }
}
