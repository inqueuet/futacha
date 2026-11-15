@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.network.aimoge.*
import com.valoser.futacha.shared.ui.aimoge.AIMOGE_CONSENT_TITLE
import com.valoser.futacha.shared.ui.aimoge.AimogeWritePromptHost
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

/**
 * The あいもげ questions asked at the tap, on the shared host every mode draws inside PrivacyModeHost.
 * No request goes out: the prompts are answered by taps and the poll link is checked without a network.
 */
class AimogeWritePromptsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var shown by mutableStateOf(true)
    private val db = "aimoge-prompts-${System.nanoTime()}.db"

    @Before fun on() {
        AimogeAvailability.sitesEnabled = true
        val store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        rule.setContent { MaterialTheme { if (shown) AimogeWritePromptHost(store) } }
        rule.waitForIdle()
    }

    @After fun off() {
        scope.cancel()
        rule.runOnUiThread { shown = false }
        AimogeAvailability.sitesEnabled = false
        AimogeNotices.clear()
        rule.activity.deleteDatabase(db)
    }

    private fun <T> asking(block: suspend () -> T): Deferred<T> = scope.async { block() }

    private fun waitFor(tag: String) = rule.waitUntil(5_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    @Test fun theConsentIsAskedAndNoAnswersNo() {
        val answer = asking { AimogePrompts.ask(AimogePromptRequest.DeleteKeyConsent) }
        waitFor("aimoge-consent-no")
        rule.onNodeWithText(AIMOGE_CONSENT_TITLE).assertIsDisplayed()
        rule.onNodeWithTag("aimoge-consent-no").performClick()
        assertFalse(runBlocking { answer.await() })
        val yes = asking { AimogePrompts.ask(AimogePromptRequest.DeleteKeyConsent) }
        waitFor("aimoge-consent-yes")
        rule.onNodeWithTag("aimoge-consent-yes").performClick()
        assertTrue(runBlocking { yes.await() })
    }

    @Test fun theDelButtonOffersDelOrAReportWithAReason() {
        val del = asking { AimogePrompts.askDelOrReport() }
        waitFor("aimoge-del")
        rule.onNodeWithTag("aimoge-del").performClick()
        assertEquals(AimogeDelChoice.Del, runBlocking { del.await() })

        val report = asking { AimogePrompts.askDelOrReport() }
        waitFor("aimoge-report")
        rule.onNodeWithTag("aimoge-report").performClick()
        rule.onNodeWithTag("aimoge-report-${AimogeReportReason.ILLEGAL.wire}").performClick()
        rule.onNodeWithTag("aimoge-report-detail").performTextInput("詳細")
        rule.onNodeWithTag("aimoge-report-send").performClick()
        assertEquals(AimogeDelChoice.Report(AimogeReportReason.ILLEGAL, "詳細"), runBlocking { report.await() })
    }

    @Test fun theLifetimeChoiceAndTheCloseConfirmationAnswerByTap() {
        val limits = AimogeThreadLimits(daysInput = false, durationHours = 6.0, minimumMinutes = 30, allowR18ThreadCreation = false)
        val choices = aimogeDurationChoices(limits)
        val picked = asking {
            AimogePrompts.askChoice(AimogeChoiceRequest("aimoge-duration", "スレの保持時間", "", choices.map { it.label }, "スレを立てる"), whenNoHost = 0)
        }
        waitFor("aimoge-duration-1")
        rule.onNodeWithText("3時間").assertIsDisplayed()
        rule.onNodeWithTag("aimoge-duration-1").performClick()
        rule.onNodeWithTag("aimoge-duration-ok").performClick()
        assertEquals(1, runBlocking { picked.await() })

        val cancelled = asking {
            AimogePrompts.askChoice(AimogeChoiceRequest("aimoge-op-delete", "スレの1レス目", "", listOf("スレを閉じる", "1レス目を削除する"), "実行"), whenNoHost = 1)
        }
        waitFor("aimoge-op-delete-cancel")
        rule.onNodeWithTag("aimoge-op-delete-cancel").performClick()
        assertNull("cancelling sends nothing", runBlocking { cancelled.await() })
    }

    @Test fun aPollLinkIsAnsweredInTheAppAndTheNoticeShows() {
        var asked: Pair<String, String>? = null
        AimogePollLinks.handler = { post, option -> asked = post to option; AIMOGE_POLL_VOTED_MESSAGE_FOR_TEST }
        try {
            assertTrue(handleAimogeInternalLink("${AIMOGE_POLL_LINK_PREFIX}p1/o2"))
            assertFalse("other addresses still open outside", handleAimogeInternalLink("https://example.com/"))
            waitFor("aimoge-notice")
            assertEquals("p1" to "o2", asked)
            rule.onNodeWithText(AIMOGE_POLL_VOTED_MESSAGE_FOR_TEST).assertIsDisplayed()
        } finally {
            AimogePollLinks.handler = null
        }
    }

    private companion object { const val AIMOGE_POLL_VOTED_MESSAGE_FOR_TEST = "回答しました（試験）" }
}
