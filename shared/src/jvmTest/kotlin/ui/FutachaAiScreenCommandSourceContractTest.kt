package ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * C4-1/E4-1/C4-2/C4-4: wiring of the platform AI command paths that composition
 * tests cannot reach (Android lifecycle pauses, startup loading).
 */
class FutachaAiScreenCommandSourceContractTest {
    private fun source(path: String): String {
        val file = File(path)
        assertTrue(file.isFile, "$path not found from ${File(".").absolutePath}")
        return file.readText()
    }

    private val app by lazy { source("src/commonMain/kotlin/ui/FutachaApp.kt") }
    private val compat by lazy { source("src/commonMain/kotlin/ui/compat/CompatibilityApp.kt") }

    @Test
    fun modernScreensReceiveOnlyTheCheckedCommand() {
        assertFalse(app.contains("aiCommand = pendingAiScreenCommand"), "a screen gets the command before its age is checked")
        assertEquals(3, Regex("""aiCommand = releasedAiScreenCommand,""").findAll(app).count())
        assertTrue(app.contains("superviseAiScreenCommand("))
        assertTrue(app.contains("pendingAiScreenForwardedAt[AiCommandEffectKey(command)] = TimeSource.Monotonic.markNow()"))
    }

    @Test
    fun modernLinkWaitsForTheStoredListsBeforeItIsConsumed() {
        val start = app.indexOf("LaunchedEffect(platformAiDeepLink)")
        assertTrue(start >= 0)
        val body = app.substring(start, app.indexOf("LaunchedEffect(AiCommandEffectKey(platformAiCommand))", start))
        val wait = body.indexOf("awaitAiCommandInputsLoaded()")
        val consume = body.indexOf("onPlatformAiDeepLinkConsumed(rawDeepLink)")
        assertTrue(wait in 0 until consume, "the link is consumed before the stored lists are loaded")
        // The command runs outside the effect that its consumption restarts.
        assertTrue(body.indexOf("coroutineScope.launch") > consume)
        assertTrue(app.contains("readPersistedAiCommandEnabled(stateStore, inputs.isAiCommandEnabled)"))
    }

    @Test
    fun compatibilityScreensReceiveOnlyTheCheckedCommandAndCheckTheLock() {
        assertTrue(compat.contains("platformAiCommand = threadAiSlot.deliverable(isAppUnlocked)"))
        assertTrue(compat.contains("platformAiCommand = catalogAiSlot.deliverable(isAppUnlocked)"))
        assertEquals(2, Regex("""if \(platformAiLock\?\.isUnlocked == false\) return@LaunchedEffect""").findAll(compat).count())
    }

    @Test
    fun compatibilityCommandsWaitForTheStoreAndFollowTheSetting() {
        val start = compat.indexOf("suspend fun deliverPlatformAiCommand(")
        assertTrue(start >= 0)
        val body = compat.substring(start, compat.indexOf("val platformAiArrivals", start))
        assertTrue(body.contains("boardsLoaded && historiesLoaded && workspaceLoaded && preferencesLoaded"))
        assertTrue(body.contains("compatPlatformAiDisabledRejection(command, enabled)"))
        assertTrue(compat.contains("LaunchedEffect(platformAiDeepLink)"), "Android links are not handled")
    }
}
