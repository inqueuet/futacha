import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every read of a Ktor response body goes through readResponseBody (Ktor 3.6.1, see ResponseBodyReadSupport.kt). */
class ResponseBodyReadWrappingContractTest {
    @Test fun everyResponseBodyReadIsWrapped() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }
        val pattern = Regex("""\.(readAvailable|readRemaining|readUTF8Line|bodyAsText|bodyAsBytes)\(""")
        val bad = listOf("shared/src/commonMain", "shared/src/androidMain", "shared/src/iosMain", "shared/src/jvmMain", "app-android/src/main", "app-desktop/src")
            .map { File(root, it) }.filter { it.exists() }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line ->
                if (pattern.containsMatchIn(line) && !line.contains("readResponseBody")) "${f.relativeTo(root)}:${i + 1}: ${line.trim()}" else null
            } }
        assertTrue(bad.isEmpty(), "Unwrapped response body reads:\n" + bad.joinToString("\n"))
    }
}
