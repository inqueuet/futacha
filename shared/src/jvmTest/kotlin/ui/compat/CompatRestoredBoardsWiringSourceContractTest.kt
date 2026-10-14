package ui.compat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * M2 wiring: both restore paths of the shared settings screen add the restored boards to the AppStateStore (ふたちゃ,
 * ふたばー風), and としあき(仮), whose compatibility store owns the boards, is not given one.
 */
class CompatRestoredBoardsWiringSourceContractTest {
    private fun source(path: String): String {
        val file = File("src/commonMain/kotlin/ui/$path")
        assertTrue(file.isFile, "$path not found from ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun everyRestorePathMergesTheBoardsRightAfterRecordingTheBackupDate() {
        val text = source("compat/CompatSettingsScreen.kt")
        val merges = Regex("""mergeRestoredBoardsIntoAppState\(store, restoreBoardsInto\.takeIf \{ modernPresentation \}\)""").findAll(text).toList()
        assertEquals(2, merges.size, "the file picker and the folder picker both restore")
        merges.forEach { match ->
            val before = text.substring(maxOf(0, match.range.first - 220), match.range.first)
            assertTrue(before.contains("recordCompatBackupDate("), "the merge follows a successful restore: $before")
        }
        // Saving a backup restores nothing, so it must not merge.
        assertTrue(text.contains("""if (!kind.startsWith("save_")) mergeRestoredBoardsIntoAppState("""))
    }

    @Test
    fun theModernModesPassTheirStoreAndToshiakiDoesNot() {
        assertTrue(source("futaber/FutaberSettingsScreen.kt").contains("restoreBoardsInto = stateStore"))
        assertTrue(source("board/FutachaSharedFeatures.kt").contains("restoreBoardsInto = appStateStore"))
        assertFalse(source("compat/CompatibilityApp.kt").contains("restoreBoardsInto"))
    }
}
