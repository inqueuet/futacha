@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked
import com.valoser.futacha.shared.ui.board.PlatformBackgroundLifecycleEffect
import com.valoser.futacha.shared.ui.futaber.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.ContinuationInterceptor
import org.junit.*
import org.junit.Assert.*

/** The real shared mode guard/controller and Activity ON_STOP, with a silent speech driver. */
class ReadAloudAppLockLifecycleInstrumentedTest {
    // No Compose test rule: LaunchedEffect and the reader must use the production Android
    // dispatcher while stopped, rather than a virtual dispatcher that only runs when pumped.
    @get:Rule val rule = ActivityScenarioRule(ComponentActivity::class.java)
    private val composed = CountDownLatch(1)
    private fun onUi(block: () -> Unit) { rule.scenario.onActivity { block() } }
    private fun waitForIdle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    private val holder = FutachaAppLockHolder().apply { openSessionWithoutLock(); setContentVisible(true) }
    private val driver = Driver()
    private val background = CountDownLatch(1)
    private var composedUnlocked by mutableStateOf(true)
    private lateinit var reader: FutaberReadAloud
    private val post = Post("63", author = null, subject = null, timestamp = "", messageHtml = "音を出さない試験の本文。", imageUrl = null, thumbnailUrl = null)

    private class Driver : FutaberSpeechEngine {
        val events = CopyOnWriteArrayList<String>()
        val speaking = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val starts = AtomicInteger()
        override suspend fun prepare() {
            events += "prepare"
            android.util.Log.i("AuditM63", "reader dispatcher=${currentCoroutineContext()[ContinuationInterceptor]}")
        }
        override suspend fun speak(text: String) {
            events += "speak"; starts.incrementAndGet(); speaking.countDown(); awaitCancellation()
        }
        override fun stop() { events += "stop"; stopped.countDown() }
        override fun close() { events += "close" }
    }
    private fun start(lockEnabled: Boolean, provideHolder: Boolean = true) {
        rule.scenario.onActivity { activity -> activity.setContent {
            val scope = rememberCoroutineScope()
            val current = remember { FutaberReadAloud(scope, { driver }) }
            SideEffect { reader = current; composed.countDown() }
            CompositionLocalProvider(LocalFutachaAppUnlocked provides composedUnlocked,
                LocalFutachaAppLockHolder provides if (provideHolder) holder else null) {
                // This is the same lifecycle/holder connection as FutachaApp. The composition value
                // stays true while stopped, reproducing Android/iOS's background frame suspension.
                PlatformBackgroundLifecycleEffect {
                    driver.events += "background"
                    if (lockEnabled) holder.lockSession()
                    background.countDown()
                }
                FutaberReadAloudAppLockEffect(current)
                DisposableEffect(current) { onDispose { current.dispose() } }
            }
        } }
        assertTrue("Real Android composition must mount", composed.await(5, TimeUnit.SECONDS))
        waitForIdle()
        onUi { reader.start(listOf(post), 0) {} }
        assertTrue("Real Android reader must start: ${driver.events}", driver.speaking.await(5, TimeUnit.SECONDS))
        waitForIdle()
    }
    @After fun cleanup() {
        // Compose idle waits are deliberately avoided until the Activity is visible again.
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.scenario.onActivity { it.setContent {} }; waitForIdle()
    }
    private fun background() {
        rule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue("Actual ON_STOP must run the product lifecycle callback", background.await(5, TimeUnit.SECONDS))
        assertTrue("The Compose snapshot must remain unlocked in this reproduction", composedUnlocked)
        android.util.Log.i("AuditM63", "stopped Activity, session=${holder.sessionUnlocked.value}, events=${driver.events}")
    }
    @Test fun aSessionLockStopsSpeakingBeforeAnyForegroundFrameAndNeverAutomaticallyResumes() {
        start(lockEnabled = true)
        background()
        assertFalse(holder.sessionUnlocked.value)
        assertTrue("Speech must stop while Activity is still CREATED: ${driver.events}", driver.stopped.await(5, TimeUnit.SECONDS))
        rule.scenario.onActivity { assertEquals(Lifecycle.State.CREATED, it.lifecycle.currentState) }
        assertEquals(1, driver.starts.get())
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForIdle()
        assertFalse(reader.isReading)
        onUi { holder.unlockSession() }
        waitForIdle()
        assertEquals("Unlocking does not restart the stopped sentence", 1, driver.starts.get())
    }
    @Test fun lockDisabledKeepsTheExistingBackgroundReadingAndTheSameSentenceOnReturn() {
        start(lockEnabled = false)
        background()
        assertTrue(holder.sessionUnlocked.value)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertFalse("Background alone must not interrupt unlocked speech: ${driver.events}", driver.stopped.await(250, TimeUnit.MILLISECONDS))
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForIdle()
        assertTrue(reader.isReading)
        assertEquals(1, driver.starts.get())
        onUi { reader.stop() }
        assertTrue(driver.stopped.await(5, TimeUnit.SECONDS))
    }
    @Test fun aLockThatArrivesWhilePausedClearsTheSessionAndCannotResumeAfterUnlock() {
        start(lockEnabled = true)
        onUi { reader.pause() }
        assertTrue(reader.isPaused)
        background()
        assertFalse(holder.sessionUnlocked.value)
        // Synchronize Android's main queue without requesting a Compose frame from a stopped Activity.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        rule.scenario.onActivity { assertFalse("Paused reading also belongs to the locked session", reader.isReading) }
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        onUi { holder.unlockSession(); reader.resume() }
        waitForIdle()
        assertFalse(reader.isReading)
        assertEquals(1, driver.starts.get())
    }
    @Test fun theCompositionGuardStillStopsSpeechWithoutAnAppLockHolder() {
        start(lockEnabled = false, provideHolder = false)
        onUi { composedUnlocked = false }
        waitForIdle()
        assertTrue(driver.stopped.await(5, TimeUnit.SECONDS))
        waitForIdle()
        assertFalse(reader.isReading)
    }
}
