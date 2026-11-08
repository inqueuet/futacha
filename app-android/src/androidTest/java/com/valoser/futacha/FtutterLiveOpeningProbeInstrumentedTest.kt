package com.valoser.futacha

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたったー風で、実在の may/b のスレッドを開いた時の位置を確かめる（「スレを開くと勝手に最下部まで行く」の報告）。
 * 読むだけで、投稿・そうだね・del は送らない。通信が要るので、`-e ftutterLive true` を付けた時だけ動く。
 * 結果は logcat の `FtutterLiveProbe` と、`Android/data/<id>/files/ftutter-live/` の画面に残す。
 */
class FtutterLiveOpeningProbeInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private val may = BoardSummary("ftutter-live-may", "may/b", "", "https://may.2chan.net/b/futaba.php", "")

    @Before fun prepare() {
        assumeTrue("Pass -e ftutterLive true (reads may.2chan.net)",
            InstrumentationRegistry.getArguments().getString("ftutterLive") == "true")
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            if (app.appStateStore.boards.first().none { it.url == may.url }) {
                app.appStateStore.updateBoards { boards -> boards + may }
                app.compatibilityStore.importModernBoards(listOf(may))
            }
        }
        switchProfile(ExperienceProfile.FTUTTER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        switchProfile(originalProfile)
    }

    private fun switchProfile(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    private fun tagged(tag: String): SemanticsNodeInteractionCollection = rule.onAllNodesWithTag(tag)
    private fun present(tag: String) = tagged(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTag(tag: String, timeoutMillis: Long = 30_000) {
        rule.waitUntil(timeoutMillis) { present(tag) }
    }

    private fun pause(millis: Long) {
        SystemClock.sleep(millis)
        rule.waitForIdle()
    }

    private fun pressBack() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_BACK,
                0, 0, -1, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD), true)
        }
        pause(800)
    }

    private fun shot(name: String) {
        val dir = java.io.File(app.getExternalFilesDir(null), "ftutter-live").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        java.io.File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Where the thread's list is: its scroll value and range (0 is the top), whether the end band is on screen, the top post. */
    private data class Where(val value: Float, val max: Float, val endBandShown: Boolean, val topPost: String) {
        val atTop get() = value <= 1f
        val atBottom get() = max > 0f && value >= max - 1f
    }

    private fun where(): Where {
        val list = tagged("ftutter-thread-list").fetchSemanticsNodes().firstOrNull()
        val range = list?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        var height = 0f
        rule.runOnUiThread { height = rule.activity.window.decorView.height.toFloat() }
        val endShown = tagged("ftutter-end-band").fetchSemanticsNodes().any { it.boundsInRoot.top < height && it.boundsInRoot.bottom > 0f }
        val top = tagged("ftutter-post").fetchSemanticsNodes(atLeastOneRootRequired = false)
            .filter { it.boundsInRoot.bottom > 0f }
            .minByOrNull { it.boundsInRoot.top }
        val text = top?.let { node ->
            (node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
                ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")).orEmpty()
        }.orEmpty().replace('\n', ' ').take(60)
        return Where(range?.value?.invoke() ?: -1f, range?.maxValue?.invoke() ?: -1f, endShown, text)
    }

    private fun openCatalog() {
        rule.activityRule.scenario.recreate()
        waitForTag("ftutter-board-row")
        rule.onAllNodes(hasTestTag("ftutter-board-row") and hasText("may/b", substring = true))[0].performClick()
        waitForTag("ftutter-catalog-item", 60_000)
        pause(1500)
    }

    private fun openItem(index: Int): String {
        val items = tagged("ftutter-catalog-item")
        val count = items.fetchSemanticsNodes().size
        items[index.coerceAtMost(count - 1)].performClick()
        waitForTag("ftutter-post", 60_000)
        return runCatching { tagged("ftutter-thread").fetchSemanticsNodes().size.toString() }.getOrDefault("")
    }

    @Test fun liveMayThreadsOpenWhereTheyShould() {
        openCatalog()
        shot("00-catalog")
        val findings = mutableListOf<String>()
        val firstVisits = mutableListOf<Pair<Where, Where>>()
        for (index in 0 until 6) {
            openItem(index)
            pause(1000)
            val soon = where()
            shot("${index + 1}a-opened")
            pause(5000)
            val later = where()
            shot("${index + 1}b-5s-later")
            firstVisits += soon to later
            val line = "first visit #$index: soon=$soon later=$later"
            Log.i("FtutterLiveProbe", line)
            findings += line
            pressBack()
            waitForTag("ftutter-catalog-item")
        }
        // A second visit of the first thread after reading into its middle comes back there.
        openItem(0)
        pause(2000)
        tagged("ftutter-thread-list")[0].performTouchInput { swipeUp(durationMillis = 400) }
        pause(1500)
        val left = where()
        shot("20-left-in-the-middle")
        pressBack()
        waitForTag("ftutter-catalog-item")
        openItem(0)
        pause(3000)
        val back = where()
        shot("21-reopened")
        Log.i("FtutterLiveProbe", "reopen: left=$left back=$back")
        findings += "reopen: left=$left back=$back"
        pressBack()
        waitForTag("ftutter-catalog-item")
        // The extension 「スレッドを先頭から開く」: the same thread, left in its middle, now opens at its first post.
        runBlocking { app.compatibilityStore.savePreferences(mapOf("compat.ftutter.ext.openAtTop" to "true")) }
        pause(800)
        openItem(0)
        pause(3000)
        val withExtension = where()
        shot("22-reopened-with-open-at-top")
        Log.i("FtutterLiveProbe", "open at top: $withExtension")
        findings += "open at top: $withExtension"
        pressBack()
        runBlocking { app.compatibilityStore.savePreferences(mapOf("compat.ftutter.ext.openAtTop" to null)) }
        Log.i("FtutterLiveProbe", findings.joinToString("\n"))

        val wrong = firstVisits.withIndex().filter { (_, pair) -> !pair.first.atTop || !pair.second.atTop }
        assertTrue("first visits that did not stay at the top:\n" + wrong.joinToString("\n") { (i, p) -> "#$i soon=${p.first} later=${p.second}" },
            wrong.isEmpty())
        assertTrue("the reopened thread did not come back to where it was left ($left -> $back)", back.topPost == left.topPost)
        assertTrue("with 「スレッドを先頭から開く」 the thread did not open at its top: $withExtension", withExtension.atTop)
    }
}
