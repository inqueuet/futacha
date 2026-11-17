package com.valoser.futacha

import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風のスワイプと書き込み画面を、Androidのナビゲーション方式（ジェスチャー／3ボタン）のどちらでも確かめる。
 * 指の動きはシステムの入力経路から入れるので、ジェスチャーナビでは端のスワイプがSystemUIの戻る操作として届く
 * （Composeの試験用の入力ではこれを通らない）。Emulatorの方式を切り替えて、同じ試験を両方で実行する。
 * 通信はチュートリアル板の同梱データだけを使い、投稿は送らない。
 */
class FutaberSystemNavigationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private var width = 0f
    private var height = 0f

    private val futaberKeys = listOf(
        "compat.futaber.lastBoardId",
        "compat.futaber.manageCategory",
        "compat.futaber.tabs",
        "compat.futaber.drafts",
        "compat.futaber.seen",
        "compat.futaber.catalog.displayStyle",
        "compat.futaber.catalog.sort",
        "compat.futaber.ext.postTools"
    )

    /** 0 = 3ボタン, 2 = ジェスチャー. */
    private val navigationMode: Int
        get() = Settings.Secure.getInt(app.contentResolver, "navigation_mode", 0)
    private val gestureNavigation get() = navigationMode == 2
    private val modeName get() = if (gestureNavigation) "gesture" else "three-button"

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(futaberKeys.associateWith { null })
        }
        switchProfile(ExperienceProfile.FUTABER)
        rule.runOnUiThread {
            width = rule.activity.window.decorView.width.toFloat()
            height = rule.activity.window.decorView.height.toFloat()
        }
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking { app.compatibilityStore.savePreferences(futaberKeys.associateWith { null }) }
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

    private fun present(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { present(tag) }
    }

    private fun waitGone(tag: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) { !present(tag) }
    }

    private fun openTutorialCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-board-address")
        waitForTag("futaber-catalog-item")
        waitGone("futaber-drawer")
    }

    private fun openFirstThread() {
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-thread-title")
        waitForTag("futaber-post")
        // The swipe is read from the posts, which settle once their pictures are laid out.
        SystemClock.sleep(500)
        rule.waitForIdle()
    }

    /** A horizontal swipe through Android's input dispatcher (so SystemUI's edge recognizer sees it), across the middle. */
    private fun systemSwipe(fromX: Float, toX: Float, y: Float = 0.5f) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun inject(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x * width, y * height, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
            rule.mainClock.advanceTimeByFrame()
        }
        inject(MotionEvent.ACTION_DOWN, fromX)
        for (step in 1..16) {
            SystemClock.sleep(16)
            inject(MotionEvent.ACTION_MOVE, fromX + (toX - fromX) * step / 16f)
        }
        inject(MotionEvent.ACTION_UP, toX)
        SystemClock.sleep(600)
        rule.waitForIdle()
    }

    /** The back key through ViewRootImpl, as the 3-button bar sends it. */
    private fun pressBackKey() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            assertTrue(automation.injectInputEvent(KeyEvent(
                downTime, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_BACK,
                0, 0, -1, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD
            ), true))
        }
        SystemClock.sleep(400)
        rule.waitForIdle()
    }

    private fun insets(): WindowInsetsCompat? {
        var insets: WindowInsetsCompat? = null
        rule.runOnUiThread { insets = ViewCompat.getRootWindowInsets(rule.activity.window.decorView) }
        return insets
    }

    private fun imeVisible() = insets()?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test fun threadAndCatalogSwipesWorkWithThisNavigationMode() {
        openTutorialCatalog()
        openFirstThread()

        // From the middle to the right: back to the catalog.
        systemSwipe(0.3f, 0.9f)
        waitGone("futaber-thread-title")
        waitForTag("futaber-catalog-item")
        assertTrue("[$modeName] the board list opened instead of the catalog", !present("futaber-drawer"))

        // On the catalog, to the left: back into the thread last read.
        systemSwipe(0.85f, 0.15f)
        waitForTag("futaber-thread-title")

        // From the left edge to the right: the board list over the thread (gesture navigation: SystemUI's back from the
        // left edge, which the thread turns into the board list; 3 buttons: the screen's own edge swipe).
        systemSwipe(0.005f, 0.75f)
        waitForTag("futaber-drawer")
        assertTrue("[$modeName] the left edge left the thread", present("futaber-thread-title"))
        pressBackKey()
        waitGone("futaber-drawer")
        assertTrue("[$modeName] closing the board list left the thread", present("futaber-thread-title"))

        // From the middle to the left: the writing screen; closing it sends nothing.
        systemSwipe(0.8f, 0.2f)
        waitForTag("futaber-post-screen")
        rule.onNodeWithTag("futaber-post-close").performClick()
        waitGone("futaber-post-screen")
        assertTrue("[$modeName] closing the writing screen left the thread", present("futaber-thread-title"))

        // The back key (or the back gesture's key) goes back to the catalog, not to the board list.
        pressBackKey()
        waitGone("futaber-thread-title")
        waitForTag("futaber-catalog-item")
        assertTrue("[$modeName] Back opened the board list", !present("futaber-drawer"))

        // On the catalog, to the right: the board list.
        systemSwipe(0.3f, 0.9f)
        waitForTag("futaber-drawer")
    }

    @Test fun aSwipeFromTheRightEdgeFollowsTheNavigationMode() {
        openTutorialCatalog()
        openFirstThread()
        systemSwipe(0.995f, 0.3f)
        if (gestureNavigation) {
            // The right edge belongs to the system's back gesture: it goes back to the catalog, as Back does.
            waitGone("futaber-thread-title")
            waitForTag("futaber-catalog-item")
            assertTrue("[gesture] the right edge opened the writing screen", !present("futaber-post-screen"))
        } else {
            // With the 3 buttons nothing takes the edge: it is the screen's left swipe, the writing screen (as futaber).
            waitForTag("futaber-post-screen")
            rule.onNodeWithTag("futaber-post-close").performClick()
            waitGone("futaber-post-screen")
        }
    }

    @Test fun theWritingScreenOpensReadyToTypeWithEveryToolAboveTheKeyboardAndTheBar() {
        openTutorialCatalog()
        openFirstThread()
        systemSwipe(0.8f, 0.2f)
        waitForTag("futaber-post-screen")
        // The keyboard comes up with the screen, as in futaber.
        rule.waitUntil(5_000) { imeVisible() }
        SystemClock.sleep(600) // the keyboard's slide
        rule.waitForIdle()
        // L-B8 (M36): the original's six places (カメラ／ｶﾅ／用紙／紙飛行機の空き／×／歯車) and 送信 fit; this app's tools
        // (画像の貼り付け・引用モード) come after them and are reached by scrolling the tools sideways.
        val tools = listOf(
            "futaber-post-attach", "futaber-post-kana", "futaber-post-pasteboard", "futaber-post-plane",
            "futaber-post-clear", "futaber-post-settings", "futaber-post-send"
        )
        fun assertAllAbove(limit: Float, what: String) {
            tools.forEach { tag ->
                val b = bounds(tag)
                assertTrue("[$modeName] $tag (bottom ${b.bottom}) is under the $what ($limit)", b.bottom <= limit + 1f)
                assertTrue("[$modeName] $tag is off the screen ($b, width $width)", b.left >= 0f && b.right <= width + 1f)
            }
            // The six fit beside 送信 in the original's order: the gear ends before 送信 begins.
            val lefts = tools.dropLast(1).map { bounds(it).left }
            assertEquals("[$modeName] the tools are not in the original's order", lefts.sorted(), lefts)
            assertTrue("[$modeName] the settings tool runs under 送信",
                bounds("futaber-post-settings").right <= bounds("futaber-post-send").left + 1f)
            // 画像の貼り付け comes after the gear.
            assertTrue("[$modeName] 画像の貼り付け is not after the gear", bounds("futaber-post-paste").left >= bounds("futaber-post-settings").right - 1f)
        }
        val withKeyboard = insets()!!.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars())
        assertAllAbove(height - withKeyboard.bottom, "keyboard")

        // Back hides the keyboard first; the bar then sits above the navigation bar / gesture handle.
        pressBackKey()
        rule.waitUntil(5_000) { !imeVisible() }
        SystemClock.sleep(600)
        rule.waitForIdle()
        assertTrue("[$modeName] Back closed the writing screen instead of the keyboard", present("futaber-post-screen"))
        val navigationBar = insets()!!.getInsets(WindowInsetsCompat.Type.navigationBars())
        assertAllAbove(height - navigationBar.bottom, "navigation bar")

        rule.onNodeWithTag("futaber-post-close").performClick()
        waitGone("futaber-post-screen")
    }

    /**
     * L-B4 → L-B8 (M37): the camera's black menu opened while typing is over the keyboard on the screen itself (the
     * tools sheet before it was under it: on iOS 「手書き」 could not be pressed), the keyboard stays, and Back then puts the
     * keyboard away before it closes the menu.
     */
    @Test fun theCameraMenuOpensAboveTheKeyboard() {
        runBlocking { app.compatibilityStore.savePreference("compat.futaber.ext.postTools", "ON") }
        openTutorialCatalog()
        openFirstThread()
        systemSwipe(0.8f, 0.2f)
        waitForTag("futaber-post-screen")
        rule.waitUntil(5_000) { imeVisible() }
        SystemClock.sleep(600) // the keyboard's slide
        rule.onNodeWithTag("futaber-post-attach").performClick()
        waitForTag("futaber-callout-recent")
        SystemClock.sleep(600)
        rule.waitForIdle()
        assertTrue("[$modeName] the keyboard went away when the menu opened", imeVisible())
        val keyboardTop = height - insets()!!.getInsets(WindowInsetsCompat.Type.ime() or WindowInsetsCompat.Type.navigationBars()).bottom
        val menu = bounds("futaber-callout")
        val camera = bounds("futaber-post-attach")
        assertTrue("[$modeName] the menu $menu is under the keyboard ($keyboardTop)", menu.bottom <= keyboardTop + 1f)
        assertTrue("[$modeName] the menu $menu is not over the camera $camera", menu.bottom <= camera.top + 1f)
        // The original's three first (its 「カメラ」 is taken out), then 手書き (the extension is on) at the end.
        val ids = listOf("recent", "file", "library", "tool-drawing")
        val lefts = ids.map { bounds("futaber-callout-$it").left }
        assertEquals("[$modeName] the menu's order", lefts.sorted(), lefts)
        // On the screen itself (the keyboard is another window): the menu's black just inside its left edge.
        val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val recent = bounds("futaber-callout-recent")
        val pixel = screen.getPixel((recent.left + 4f).toInt(), recent.center.y.toInt())
        screen.recycle()
        assertEquals("[$modeName] the menu's black is ${Integer.toHexString(pixel)}", 0xFF333333.toInt(), pixel)
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/futaber-post-$modeName-camera-keyboard.png")
        java.io.FileInputStream(shot.fileDescriptor).use { it.readBytes() }
        shot.close()
        // Back: the keyboard first (Android's convention), then the menu.
        pressBackKey()
        rule.waitUntil(5_000) { !imeVisible() }
        assertTrue("[$modeName] Back closed the menu before the keyboard", present("futaber-callout"))
        pressBackKey()
        waitGone("futaber-callout")
        assertTrue(present("futaber-post-screen"))
        rule.onNodeWithTag("futaber-post-close").performClick()
        waitGone("futaber-post-screen")
    }

    /**
     * L-B8 (G29): a swipe to the right from the middle closes the writing and keeps the draft. From the left edge it closes
     * too with the 3-button bar; with gestures the system takes that edge as Back, whose first press only puts the
     * keyboard away (2026-10-28), and the next one closes.
     */
    @Test fun aRightSwipeClosesTheWritingAndKeepsTheDraft() {
        openTutorialCatalog()
        openFirstThread()
        systemSwipe(0.8f, 0.2f)
        waitForTag("futaber-post-comment")
        rule.onNodeWithTag("futaber-post-comment").performTextInput("右スワイプの下書き")
        rule.waitUntil(5_000) { imeVisible() }
        SystemClock.sleep(600)
        systemSwipe(0.3f, 0.85f, y = 0.3f)
        waitGone("futaber-post-screen")
        rule.waitUntil(10_000) {
            runBlocking { app.compatibilityStore.loadPreference("compat.futaber.drafts") }?.contains("右スワイプの下書き") == true
        }
        // Back into the writing: the draft is there.
        systemSwipe(0.8f, 0.2f)
        waitForTag("futaber-post-comment")
        rule.waitUntil(5_000) { rule.onAllNodesWithText("右スワイプの下書き").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(5_000) { imeVisible() }
        SystemClock.sleep(600)
        systemSwipe(0.0f, 0.7f, y = 0.3f)
        if (gestureNavigation) {
            rule.waitUntil(5_000) { !imeVisible() }
            assertTrue("[$modeName] the edge's Back closed the writing instead of the keyboard", present("futaber-post-screen"))
            systemSwipe(0.0f, 0.7f, y = 0.3f)
        }
        waitGone("futaber-post-screen")
        rule.onNodeWithTag("futaber-thread-menu").assertExists()
    }
}
