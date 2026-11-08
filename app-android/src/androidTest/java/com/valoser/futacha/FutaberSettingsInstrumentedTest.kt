package com.valoser.futacha

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * L-B9: futaber's settings in the real app (the tutorial board only; nothing is sent anywhere). The table's look (the
 * grey page, white rows across the width, about 50 high, the band under it grey too), futaber's rows first and this
 * app's after its EULA, 設定→スレッド (tap-scroll, the ratio, the read-aloud −＋ on the shared keys, the four 「従来の動き」),
 * the gestures page, an NG list's 「編集」・「すべて削除」 and the ID/IP page, 新規スレッド通知, the EULA, and the operation
 * menu's NG編集 into the NG settings with 「完了」 back to the thread.
 */
class FutaberSettingsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private val keys = listOf(
        "compat.futaber.theme", "compat.futaber.lastBoardId", "compat.futaber.seen", "compat.futaber.tabs", "compat.futaber.manageCategory",
        "compat.futaber.smallImages", "compat.futaber.imageRatio", "compat.futaber.tapScroll", "compat.futaber.googleLens",
        "compat.futaber.instantCopy", "compat.futaber.legacySearchFilter", "compat.futaber.legacyManyRepliesFilter",
        "compat.futaber.legacySubjectField", "compat.speech.pitch", "compat.speech.rate"
    )
    private var savedRules: String? = null
    private var savedWords: List<String> = emptyList()
    private var savedHeaders: List<String> = emptyList()

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            savedRules = app.compatibilityStore.loadPreference("compat.watcher.rules")
            savedWords = app.appStateStore.ngWords.first()
            savedHeaders = app.appStateStore.ngHeaders.first()
            app.appStateStore.setNgWords(emptyList())
            app.appStateStore.setNgHeaders(emptyList())
            app.compatibilityStore.savePreferences(keys.associateWith { null } + ("compat.futaber.theme" to "light"))
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.appStateStore.setNgWords(savedWords)
            app.appStateStore.setNgHeaders(savedHeaders)
            app.compatibilityStore.savePreferences(keys.associateWith { null } + ("compat.watcher.rules" to savedRules))
        }
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

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    /** Where a node is in the page, also when it is scrolled out of sight (its bounds are then clipped to nothing). */
    private fun top(tag: String): Float = rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    private fun dp(px: Float) = px / rule.activity.resources.displayMetrics.density

    private fun openCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
    }

    private fun openSettings() {
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
    }

    private fun closeSettings() {
        rule.onNodeWithTag("futaber-settings-done").performClick()
        rule.waitUntil(10_000) { absent("futaber-settings") }
    }

    private data class Rgb(val r: Int, val g: Int, val b: Int) {
        fun distance(other: Rgb) = abs(r - other.r) + abs(g - other.g) + abs(b - other.b)
        override fun toString() = "#%02X%02X%02X".format(r, g, b)
    }

    private fun PixelMap.at(x: Float, y: Float): Rgb {
        val c = this[x.toInt().coerceIn(0, width - 1), y.toInt().coerceIn(0, height - 1)]
        return Rgb((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
    }

    private fun assertColour(label: String, expected: Rgb, actual: Rgb, tolerance: Int = 9) {
        assertTrue("$label: expected $expected, was $actual", expected.distance(actual) <= tolerance)
    }

    private val page = Rgb(0xF2, 0xF2, 0xF2)
    private val white = Rgb(0xFF, 0xFF, 0xFF)

    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(1_000)
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-settings-$name.png")
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }

    @Test fun theSettingsAreFutabersTableWithItsRowsFirstAndThisAppsAfterTheEula() {
        openCatalog()
        openSettings()
        rule.waitForIdle()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        // futaber's rows in its order (C47), the EULA its last; this app's rows after it.
        val futabers = listOf(
            "row-theme", "row-catalog", "row-thread", "row-viewer", "row-font-size", "row-posting", "row-gesture", "row-ng",
            "switch-patrol-enabled", "row-new-thread-notify", "row-eula"
        )
        val tops = futabers.map { top("futaber-settings-$it") }
        assertEquals("futaber's order", tops.sorted(), tops)
        val ours = listOf("row-storage", "row-notifications", "switch-update-check", "row-extras", "row-mode", "switch-archive-report", "row-help", "row-version")
        val ourTops = ours.map { top("futaber-settings-$it") }
        assertEquals("this app's order", ourTops.sorted(), ourTops)
        assertTrue("this app's rows come after futaber's EULA", ourTops.first() > top("futaber-settings-row-eula"))
        // V21: rows across the whole width, about 50 high (futaber 49.5), 14sp text (V39).
        val theme = bounds("futaber-settings-row-theme")
        assertEquals(root.left, theme.left, 1f)
        assertEquals(root.right, theme.right, 1f)
        assertTrue("row height ${dp(theme.height)}", dp(theme.height) in 49f..53f)
        // The grey page above the first heading, the white row, the grey band under the page (L §8.2's handoff).
        val image = rule.onRoot().captureToImage().toPixelMap()
        val heading = bounds("futaber-settings-group-general")
        assertColour("page above 全般", page, image.at(root.width / 2, heading.top - 4))
        assertColour("the row", white, image.at(theme.right - 60, theme.center.y))
        val screenshot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val band = android.graphics.Color.valueOf(screenshot.getPixel(8, screenshot.height - 3))
        assertColour(
            "the band under the page", page,
            Rgb((band.red() * 255).toInt(), (band.green() * 255).toInt(), (band.blue() * 255).toInt())
        )
        capture("top-light")
        // 「完了」 at the top left closes them.
        assertTrue(bounds("futaber-settings-done").left < root.width / 3)
        closeSettings()
    }

    @Test fun theThreadPageHasTapScrollTheReadAloudStepsAndTheFourLegacySwitches() {
        openCatalog()
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-thread").performClick()
        waitForTag("futaber-settings-row-thread-tap-scroll-on")
        val order = listOf(
            "row-thread-title-default", "row-thread-image-original", "row-many-replies", "row-speech-pitch", "row-speech-rate",
            "row-thread-tap-scroll-on", "switch-thread-google-lens", "row-speech", "row-deleted-posts",
            "switch-thread-instant-copy", "switch-thread-legacy-search", "switch-thread-legacy-many-replies", "switch-thread-legacy-subject"
        ).map { top("futaber-settings-$it") }
        assertEquals(order.sorted(), order)
        capture("thread-light")
        // C25: futaber's two choices, on by default (G20); 「しない」 stores OFF.
        rule.onNodeWithTag("futaber-settings-row-thread-tap-scroll-on").performScrollTo().assertIsSelected()
        rule.onNodeWithTag("futaber-settings-row-thread-tap-scroll-off").performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.tapScroll") == "OFF" }
        rule.onNodeWithTag("futaber-settings-row-thread-tap-scroll-on").assertIsNotSelected()
        // C23: the −＋ write the shared values every mode reads (1.0 → 1.1; 1.0 → 0.9, shown as 0.45 on futaber's scale).
        rule.onNodeWithTag("futaber-settings-value-speech-pitch").performScrollTo().assertTextContains("声の高さ = 1.0", substring = true)
        rule.onNodeWithTag("futaber-settings-value-speech-rate").assertTextContains("スピード = 0.5", substring = true)
        rule.onNodeWithTag("futaber-settings-plus-speech-pitch").performClick()
        rule.waitUntil(5_000) { preference("compat.speech.pitch") == "1.1" }
        rule.onNodeWithTag("futaber-settings-minus-speech-rate").performClick()
        rule.waitUntil(5_000) { preference("compat.speech.rate") == "0.9" }
        rule.onNodeWithTag("futaber-settings-value-speech-rate").assertTextContains("スピード = 0.45", substring = true)
        val shared = com.valoser.futacha.shared.audio.speechSettingsFromPreferences(
            mapOf("compat.speech.pitch" to preference("compat.speech.pitch")!!, "compat.speech.rate" to preference("compat.speech.rate")!!)
        )
        assertEquals(1.1f, shared.pitch, 0.0001f)
        assertEquals(0.9f, shared.rate, 0.0001f)
        // C26: futaber's switch and note.
        rule.onNodeWithTag("futaber-settings-google-lens-note").performScrollTo().assertTextContains("GoogleLens", substring = true)
        // 「従来の動き」: the four switches keep their keys and their off default.
        mapOf(
            "thread-instant-copy" to "compat.futaber.instantCopy",
            "thread-legacy-search" to "compat.futaber.legacySearchFilter",
            "thread-legacy-many-replies" to "compat.futaber.legacyManyRepliesFilter",
            "thread-legacy-subject" to "compat.futaber.legacySubjectField"
        ).forEach { (id, key) ->
            assertEquals(null, preference(key))
            rule.onNodeWithTag("futaber-settings-switch-$id").performScrollTo().performClick()
            rule.waitUntil(5_000) { preference(key) == "ON" }
        }
        rule.onNodeWithTag("futaber-settings-back").performClick()
        // C46: the gestures page has futaber's switch and its four lines first; tap-scroll is not there any more.
        rule.onNodeWithTag("futaber-settings-row-gesture").performScrollTo().performClick()
        waitForTag("futaber-settings-gesture-note")
        assertTrue(absent("futaber-settings-switch-gesture-tap-scroll"))
        rule.onNodeWithTag("futaber-settings-gesture-note").assertTextContains("他\n・スレッド内で左端からスワイプで板一覧を表示", substring = true)
        capture("gesture-light")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        closeSettings()
    }

    /** The longest side of the pictures on screen: the thread reopens where it was read, so the first picture can differ, the box they fit does not. */
    private fun longestPictureSide(): Float =
        rule.onAllNodesWithTag("futaber-post-image").fetchSemanticsNodes().maxOf { maxOf(it.size.width, it.size.height) }.toFloat()

    @Test fun theRatioShrinksAPostsPicture() {
        openCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post-image")
        rule.waitForIdle()
        val original = longestPictureSide()
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("futaber-catalog-item")
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-thread").performClick()
        // C21: 「画像のサイズ比率を指定」 shows 「比率 = 70%」 (−＋ by 10); two − make it 50%.
        assertTrue(absent("futaber-settings-row-image-ratio"))
        rule.onNodeWithTag("futaber-settings-row-thread-image-small").performClick()
        waitForTag("futaber-settings-row-image-ratio")
        rule.onNodeWithTag("futaber-settings-value-image-ratio").assertTextContains("比率 = 70%")
        rule.onNodeWithTag("futaber-settings-minus-image-ratio").performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.imageRatio") == "60" }
        rule.onNodeWithTag("futaber-settings-minus-image-ratio").performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.imageRatio") == "50" }
        rule.onNodeWithTag("futaber-settings-back").performClick()
        closeSettings()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post-image")
        rule.waitForIdle()
        val shrunk = longestPictureSide()
        assertEquals("50% of the original size", original * 0.5f, shrunk, 2f)
    }

    @Test fun anNgListIsATableEditedUnderEditAndTheIdsHaveTheirOwnPage() {
        openCatalog()
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-ng").performClick()
        waitForTag("futaber-settings-row-ng-res-words")
        // futaber's rows first in each group, this app's after them.
        val catalog = listOf("ng-catalog-words", "ng-catalog-threads", "ng-catalog-word-rules", "ng-catalog-images").map { top("futaber-settings-row-$it") }
        assertEquals(catalog.sorted(), catalog)
        val res = listOf("ng-res-words", "ng-res-images", "ng-res-names", "ng-res-id-ip").map { top("futaber-settings-row-$it") }
        assertEquals(res.sorted(), res)
        assertTrue(res.first() > catalog.last())
        capture("ng-light")
        rule.onNodeWithTag("futaber-settings-row-ng-res-words").performClick()
        rule.onNodeWithTag("futaber-settings-title").assertTextEquals("レスNGワード")
        // 「新規」: the keyboard's enter adds, as futaber's; the entries have no ⊖ until 「編集」.
        rule.onNodeWithTag("futaber-ng-input").performTextInput("lb9いち")
        rule.onNodeWithTag("futaber-ng-input").performImeAction()
        rule.waitUntil(5_000) { runBlocking { app.appStateStore.ngWords.first() } == listOf("lb9いち") }
        rule.onNodeWithTag("futaber-ng-input").performTextInput("lb9に")
        rule.onNodeWithTag("futaber-ng-add").performClick()
        rule.waitUntil(5_000) { runBlocking { app.appStateStore.ngWords.first() } == listOf("lb9いち", "lb9に") }
        assertTrue(absent("futaber-ng-delete"))
        assertTrue(absent("futaber-ng-delete-all"))
        rule.onNodeWithTag("futaber-settings-edit").performClick()
        waitForTag("futaber-ng-delete")
        assertEquals(2, rule.onAllNodesWithTag("futaber-ng-delete").fetchSemanticsNodes().size)
        capture("ng-list-editing-light")
        // 「すべて削除」 asks first; キャンセル keeps the list.
        rule.onNodeWithTag("futaber-ng-delete-all").performScrollTo().performClick()
        waitForTag("futaber-settings-alert-confirm")
        rule.onNodeWithTag("futaber-settings-alert-cancel").performClick()
        assertEquals(2, runBlocking { app.appStateStore.ngWords.first() }.size)
        rule.onNodeWithTag("futaber-ng-delete-all").performClick()
        rule.onNodeWithTag("futaber-settings-alert-confirm").performClick()
        rule.waitUntil(5_000) { runBlocking { app.appStateStore.ngWords.first() }.isEmpty() }
        waitForTag("futaber-ng-empty")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        // C37: a word typed into NGID/IP is stored with 「ID:」 in the shared list and shown there, not under NGネーム.
        rule.onNodeWithTag("futaber-settings-row-ng-res-id-ip").performClick()
        rule.onNodeWithTag("futaber-ng-input").performTextInput("lb9abc")
        rule.onNodeWithTag("futaber-ng-input").performImeAction()
        rule.waitUntil(5_000) { runBlocking { app.appStateStore.ngHeaders.first() } == listOf("ID:lb9abc") }
        rule.onNodeWithText("ID:lb9abc").assertIsDisplayed()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-detail-ng-res-id-ip", useUnmergedTree = true).assertTextEquals("1")
        rule.onNodeWithTag("futaber-settings-detail-ng-res-names", useUnmergedTree = true).assertTextEquals("0")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        closeSettings()
    }

    @Test fun theNewThreadNotifyPageAddsAndRemovesAKeywordOfEveryBoard() {
        runBlocking { app.compatibilityStore.savePreference("compat.watcher.rules", "[]") }
        openCatalog()
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-new-thread-notify").performClick()
        waitForTag("futaber-watch-add-0")
        rule.onNodeWithTag("futaber-settings-title").assertTextEquals("新規スレッド通知")
        capture("notify-light")
        rule.onNodeWithTag("futaber-watch-add-0").performClick()
        waitForTag("futaber-settings-alert-confirm")
        rule.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("lb9ねこ")
        rule.onNodeWithTag("futaber-settings-alert-confirm").performClick()
        rule.waitUntil(5_000) { preference("compat.watcher.rules")?.contains("\"word\":\"lb9ねこ\"") == true }
        assertFalse("every board: no board key", preference("compat.watcher.rules")!!.contains("boardKey\":\"compat"))
        rule.waitUntil(5_000) { rule.onAllNodesWithText("lb9ねこ").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futaber-settings-edit").performClick()
        rule.onNodeWithContentDescription("「lb9ねこ」を削除").performClick()
        rule.waitUntil(5_000) { preference("compat.watcher.rules") == "[]" }
        // The shared patrol manager is still reachable from here.
        rule.onNodeWithTag("futaber-settings-row-patrol-detail").assertExists()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        closeSettings()
    }

    @Test fun theEulaShowsThisAppsTerms() {
        openCatalog()
        openSettings()
        rule.onNodeWithTag("futaber-settings-row-eula").performScrollTo().performClick()
        waitForTag("futaber-settings-eula-page")
        rule.onNodeWithTag("futaber-settings-title").assertTextEquals("EULA")
        rule.onNodeWithText("利用規約（EULA）").assertIsDisplayed()
        rule.onNodeWithText("通報とブロック").assertIsDisplayed()
        capture("eula-light")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        waitForTag("futaber-settings-row-eula")
        closeSettings()
    }

    @Test fun theMenusNgEditOpensTheNgSettingsAndDoneGoesBackToTheThread() {
        openCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        rule.onNodeWithTag("futaber-menu-ng-edit").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-title").assertTextEquals("NG設定")
        // An NG list comes back to 「‹ NG設定」, and 「完了」 there closes the settings back to the thread.
        rule.onNodeWithTag("futaber-settings-row-ng-res-names").performClick()
        rule.onNodeWithTag("futaber-settings-title").assertTextEquals("レスNGネーム")
        rule.onNodeWithContentDescription("‹ NG設定").performClick()
        rule.onNodeWithTag("futaber-settings-title").assertTextEquals("NG設定")
        closeSettings()
        waitForTag("futaber-post")
    }

    @Test fun captureTheDarkSettingsForReview() {
        runBlocking { app.compatibilityStore.savePreference("compat.futaber.theme", "dark") }
        openCatalog()
        openSettings()
        capture("top-dark")
        rule.onNodeWithTag("futaber-settings-row-thread").performClick()
        capture("thread-dark")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-row-ng").performClick()
        capture("ng-dark")
        rule.onNodeWithTag("futaber-settings-row-ng-res-words").performClick()
        capture("ng-list-dark")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-row-gesture").performClick()
        capture("gesture-dark")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-row-new-thread-notify").performClick()
        capture("notify-dark")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        rule.onNodeWithTag("futaber-settings-row-eula").performScrollTo().performClick()
        capture("eula-dark")
        rule.onNodeWithTag("futaber-settings-back").performClick()
        closeSettings()
    }

    /**
     * The same screens before and after L-B9 (only tags both builds have), light and dark, for the side-by-side review
     * (`build/l-b9/android-before|after`). Takes pictures only.
     */
    @Test fun captureTheSettingsForReviewInEitherBuild() {
        openCatalog()
        for (theme in listOf("light", "dark")) {
            runBlocking { app.compatibilityStore.savePreference("compat.futaber.theme", theme) }
            rule.waitForIdle()
            openSettings()
            capture("review-$theme-1-top")
            rule.onNodeWithTag("futaber-settings-row-mode").performScrollTo()
            capture("review-$theme-2-top-lower")
            rule.onNodeWithTag("futaber-settings-row-thread").performScrollTo().performClick()
            waitForTag("futaber-settings-row-thread-title-default")
            capture("review-$theme-3-thread")
            rule.onNodeWithTag("futaber-settings-row-thread-image-original").performScrollTo()
            rule.onNodeWithTag("futaber-settings-switch-thread-instant-copy").performScrollTo()
            capture("review-$theme-4-thread-lower")
            rule.onNodeWithTag("futaber-settings-back").performClick()
            rule.onNodeWithTag("futaber-settings-row-gesture").performScrollTo().performClick()
            waitForTag("futaber-settings-gesture-note")
            capture("review-$theme-5-gesture")
            rule.onNodeWithTag("futaber-settings-back").performClick()
            rule.onNodeWithTag("futaber-settings-row-ng").performScrollTo().performClick()
            waitForTag("futaber-settings-row-ng-res-words")
            capture("review-$theme-6-ng")
            rule.onNodeWithTag("futaber-settings-row-ng-res-words").performClick()
            waitForTag("futaber-ng-input")
            capture("review-$theme-7-ng-list")
            rule.onNodeWithTag("futaber-settings-back").performClick()
            rule.onNodeWithTag("futaber-settings-back").performClick()
            closeSettings()
        }
    }
}
