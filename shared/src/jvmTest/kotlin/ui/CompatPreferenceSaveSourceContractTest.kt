package ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * L42: in としあき(仮)'s screens a preference is saved through the screen's safe launcher, which turns a failed save
 * into a message. A bare `scope.launch { store.savePreference(...) }` let the failure escape into the screen's scope
 * (a crash).
 */
class CompatPreferenceSaveSourceContractTest {
    private val bareSave = Regex("""scope\.launch\s*\{\s*store\.savePreference""")

    @Test
    fun noBarePreferenceSaveInToshiakisScreens() {
        val file = File("src/commonMain/kotlin/ui/compat/CompatibilityApp.kt")
        assertTrue(file.isFile, "source not found from ${File(".").absolutePath}")
        // Joined, so a launch and the save on the next line are seen together.
        val text = file.readLines().joinToString(" ") { it.trim() }
        val found = bareSave.findAll(text).map { it.value }.toList()
        assertTrue(found.isEmpty(), "Bare preference saves: $found")
    }
}
