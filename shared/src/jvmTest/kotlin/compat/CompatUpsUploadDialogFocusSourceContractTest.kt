package compat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * E4-2: the あぷ小 dialog asks for focus and the keyboard from inside its
 * lock-aware window. An effect outside the window runs while a dialog opened
 * under the lock is still deferred, showing the keyboard over the lock screen
 * and leaving the field without focus after the unlock.
 */
class CompatUpsUploadDialogFocusSourceContractTest {
    @Test
    fun focusAndKeyboardRequestsRunInsideTheWindow() {
        val source = File("src/commonMain/kotlin/ui/compat/CompatPostScreen.kt").readText()
        val start = source.indexOf("fun CompatUpsUploadDialog(")
        assertTrue(start >= 0, "CompatUpsUploadDialog not found")
        val end = source.indexOf("\n}\n", start).takeIf { it > start } ?: source.length
        val body = source.substring(start, end)
        val window = body.indexOf("FutachaAppLockAwareWindow {")
        assertTrue(window >= 0, "the dialog is not lock-aware")
        listOf("LaunchedEffect(", "requestFocus()", "keyboard?.show()").forEach { call ->
            val at = body.indexOf(call)
            assertTrue(at > window, "$call must be inside FutachaAppLockAwareWindow")
        }
    }
}
