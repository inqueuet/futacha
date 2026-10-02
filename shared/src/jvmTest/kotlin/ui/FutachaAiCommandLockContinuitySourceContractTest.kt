package ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C-7: the effects that run AI commands in FutachaApp must not restart or
 * leave composition when the app lock engages. Keyed on the lock (or composed
 * only while unlocked), a command running at that moment was cancelled, a
 * platform command without an id ran again after the unlock and a command
 * already taken from the bridge was lost. They wait in
 * FutachaAppLockHolder.awaitUnlocked() instead.
 */
class FutachaAiCommandLockContinuitySourceContractTest {
    private val source: List<String> by lazy {
        val file = File("src/commonMain/kotlin/ui/FutachaApp.kt")
        assertTrue(file.isFile, "FutachaApp.kt not found from ${File(".").absolutePath}")
        file.readLines()
    }

    private fun codeLines(): List<IndexedValue<String>> = source.withIndex().filter { (_, line) ->
        val trimmed = line.trimStart()
        !trimmed.startsWith("//") && !trimmed.startsWith("*")
    }

    @Test
    fun platformCommandEffectsAreNotKeyedOnTheLock() {
        val effects = codeLines().filter { (_, line) ->
            line.contains("LaunchedEffect(platformAiDeepLink") ||
                line.contains("LaunchedEffect(AiCommandEffectKey(platformAiCommand)")
        }
        assertEquals(2, effects.size, "platform AI command effects not found: $effects")
        effects.forEach { (index, line) ->
            assertFalse(line.contains("isAppUnlocked"), "FutachaApp.kt:${index + 1} restarts on the lock: $line")
        }
    }

    @Test
    fun bridgeCollectorStaysComposedAndReceivesOnlyWhileUnlocked() {
        val lines = codeLines()
        lines.forEach { (index, line) ->
            assertFalse(
                Regex("""if \(consumeAiCommandBridge\s*&&\s*isAppUnlocked""").containsMatchIn(line),
                "FutachaApp.kt:${index + 1} removes the bridge collector while locked"
            )
            assertFalse(
                line.contains("FutachaAiCommandBridge.commands.collect"),
                "FutachaApp.kt:${index + 1} collects the bridge without waiting for the unlock"
            )
        }
        val receives = lines.filter { (_, line) -> line.contains("FutachaAiCommandBridge.receiveQueued()") }
        assertTrue(receives.isNotEmpty(), "bridge receive not found")
        receives.forEach { (index, _) ->
            val previous = source.subList(maxOf(0, index - 3), index).joinToString("\n")
            assertTrue(
                previous.contains("appLock.awaitUnlocked()"),
                "FutachaApp.kt:${index + 1} receives a bridge command without awaiting the unlock"
            )
        }
    }
}
