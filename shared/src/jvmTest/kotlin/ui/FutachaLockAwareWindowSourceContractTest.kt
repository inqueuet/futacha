package ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * C-2: every window the futacha (modern) UI opens must go through
 * FutachaAppLockAwareWindow, or a result/notice opened while the app is
 * locked is stacked above the lock overlay. The toshiaki UI (ui/compat) has
 * its own contract. DropdownMenu is not covered here.
 */
class FutachaLockAwareWindowSourceContractTest {
    private val windowCall = Regex("""(?<![A-Za-z0-9_.])(AlertDialog|Dialog|Popup|ModalBottomSheet)\(""")
    private val wrapper = "FutachaAppLockAwareWindow {"

    /** Not app-screen windows: the lock overlay itself, and FutachaApp's windows gated on isAppUnlocked. */
    private val excludedFiles = setOf("FutachaAppLockScreen.kt", "FutachaApp.kt", "UpdateNotificationDialog.kt")

    @Test
    fun everyFutachaWindowIsLockAware() {
        val root = File("src/commonMain/kotlin/ui")
        assertTrue(root.isDirectory, "ui sources not found from ${File(".").absolutePath}")
        val compat = File(root, "compat")
        val unwrapped = mutableListOf<String>()
        var windows = 0
        root.walkTopDown()
            .onEnter { it != compat }
            .filter { it.isFile && it.extension == "kt" && it.name !in excludedFiles }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trimStart()
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    windowCall.findAll(line).forEach { match ->
                        val before = line.substring(0, match.range.first)
                        if (before.trimEnd().endsWith("fun")) return@forEach
                        windows += 1
                        // `FutachaAppLockAwareWindow { AlertDialog(` or the wrapper ending the previous line.
                        val wrapped = before.endsWith("$wrapper ") ||
                            (before.isBlank() && lines.getOrNull(index - 1)?.trimEnd()?.endsWith(wrapper) == true)
                        if (!wrapped) unwrapped += "${file.name}:${index + 1}"
                    }
                }
            }
        assertTrue(windows > 0, "no futacha windows found")
        assertTrue(unwrapped.isEmpty(), "Windows without FutachaAppLockAwareWindow: $unwrapped")
    }
}
