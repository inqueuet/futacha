package compat

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * C-2: every window the toshiaki (compat) UI opens must go through
 * FutachaAppLockAwareWindow, or a result/notice opened while the app is
 * locked is stacked above the lock overlay. DropdownMenu is not covered here.
 */
class CompatLockAwareWindowSourceContractTest {
    private val windowCall = Regex("""(?<![A-Za-z0-9_.])(AlertDialog|Dialog|Popup)\(""")
    private val wrapper = "FutachaAppLockAwareWindow { "

    @Test
    fun everyCompatWindowIsLockAware() {
        val root = File("src/commonMain/kotlin/ui/compat")
        assertTrue(root.isDirectory, "compat sources not found from ${File(".").absolutePath}")
        val unwrapped = mutableListOf<String>()
        var windows = 0
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                val trimmed = line.trimStart()
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                windowCall.findAll(line).forEach { match ->
                    if (line.substring(0, match.range.first).trimEnd().endsWith("fun")) return@forEach
                    windows += 1
                    if (!line.substring(0, match.range.first).endsWith(wrapper)) {
                        unwrapped += "${file.name}:${index + 1}"
                    }
                }
            }
        }
        assertTrue(windows > 0, "no compat windows found")
        assertTrue(unwrapped.isEmpty(), "Windows without FutachaAppLockAwareWindow: $unwrapped")
    }
}
