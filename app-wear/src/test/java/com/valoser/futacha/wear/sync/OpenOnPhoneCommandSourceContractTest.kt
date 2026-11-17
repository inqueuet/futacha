package com.valoser.futacha.wear.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * C4-3: "open board/thread on phone" sends the command over the Data Layer
 * (a watch command, allowed while "AIアプリ操作" is OFF) and uses the
 * `futacha://ai` link, marked as a watch relay, only to bring the phone app to
 * the front. Before, the command went only as the link when the phone accepted
 * it, ran as a web link and failed with the setting OFF. (RemoteActivityHelper
 * and the Data Layer need the Android runtime, so the source is checked.)
 */
class OpenOnPhoneCommandSourceContractTest {
    private val source: String by lazy {
        val file = File("src/main/java/com/valoser/futacha/wear/sync/PhoneCommandClient.kt")
        assertTrue("source not found from ${File(".").absolutePath}", file.isFile)
        file.readText()
    }

    private fun body(name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("$name not found", start >= 0)
        return source.substring(start, source.indexOf("\n    }\n", start))
    }

    @Test
    fun openCommandsGoOverTheDataLayerAndMarkTheLink() {
        listOf("openBoardOnPhone", "openThreadOnPhone").forEach { name ->
            val body = body(name)
            assertTrue("$name does not send the Data Layer command", body.contains("sendOpenCommandAndBringPhoneAppToFront("))
            assertTrue("$name link is not marked", body.contains("FUTACHA_AI_WATCH_RELAY_PARAMETER to FUTACHA_AI_WATCH_RELAY_WEAR_OS"))
            assertTrue("$name link and command differ", body.contains("\"commandId\" to commandId"))
        }
        val relay = body("sendOpenCommandAndBringPhoneAppToFront")
        assertTrue(relay.contains("sendCommand(command, onNotConnected)"))
        assertTrue(relay.contains("bringPhoneAppToFront(deepLink)"))
        assertFalse("the command is sent only as a fallback", source.contains("openDeepLinkOnPhoneOrFallback"))
        assertEquals(1, Regex("""sendCommand\(command, onNotConnected\)""").findAll(source).count())
    }
}
