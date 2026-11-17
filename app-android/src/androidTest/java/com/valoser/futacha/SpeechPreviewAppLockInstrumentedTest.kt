@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.audio.SpeechBackend
import com.valoser.futacha.shared.audio.SpeechSettings
import com.valoser.futacha.shared.audio.SpeechVoice
import com.valoser.futacha.shared.audio.TextSpeaker
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppLockHolder
import com.valoser.futacha.shared.ui.LocalFutachaAppUnlocked
import com.valoser.futacha.shared.ui.board.PlatformBackgroundLifecycleEffect
import com.valoser.futacha.shared.ui.compat.CompatSpeechSettingsScreen
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/**
 * L148 (rest of M63): the settings sample follows the app-lock session like the mode readers. A real Activity
 * ON_STOP locks the session while the composition value stays true (paused background frames). Silent driver.
 */
class SpeechPreviewAppLockInstrumentedTest {
    // No Compose test rule: effects must run on the production Android dispatcher while stopped.
    @get:Rule val rule = ActivityScenarioRule(ComponentActivity::class.java)
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val holder = FutachaAppLockHolder().apply { openSessionWithoutLock(); setContentVisible(true) }
    private val background = CountDownLatch(1)
    private val database = "speech_preview_lock_test.db"
    private lateinit var store: AndroidCompatibilityStore
    private val driver = object : SpeechBackend {
        val events = CopyOnWriteArrayList<String>()
        val speaking = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        override val supportsPitch = true
        override suspend fun availableVoices() = emptyList<SpeechVoice>()
        override fun configure(settings: SpeechSettings) {}
        override suspend fun prepare() {}
        override suspend fun speak(text: String) { events += "speak"; speaking.countDown(); awaitCancellation() }
        override fun stop() { events += "stop"; stopped.countDown() }
        override fun close() { events += "close" }
    }

    @Before fun prepare() {
        rule.scenario.onActivity { it.deleteDatabase(database); store = AndroidCompatibilityStore(it, databaseName = database) }
        runBlocking { store.initialize() }
    }
    @After fun cleanup() {
        rule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.scenario.onActivity { it.setContent {} }
        instrumentation.waitForIdleSync()
        runBlocking { store.closeForTest() }
        rule.scenario.onActivity { it.deleteDatabase(database) }
    }

    private fun startSample(lockEnabled: Boolean) {
        rule.scenario.onActivity { activity -> activity.setContent {
            CompositionLocalProvider(LocalFutachaAppUnlocked provides true, LocalFutachaAppLockHolder provides holder) {
                PlatformBackgroundLifecycleEffect {
                    if (lockEnabled) holder.lockSession()
                    background.countDown()
                }
                CompatSpeechSettingsScreen(store, emptyMap(), onBack = {},
                    createSpeaker = { _, _ -> TextSpeaker(null, null, systemEngine = { driver }) })
            }
        } }
        val device = UiDevice.getInstance(instrumentation)
        val sample = device.wait(Until.findObject(By.text("試聴")), 10_000)
        assertNotNull("The real settings screen must show its sample button", sample)
        sample.click()
        assertTrue("The sample must start: ${driver.events}", driver.speaking.await(5, TimeUnit.SECONDS))
        driver.events.clear()
    }

    @Test fun aSessionLockInTheBackgroundStopsTheSampleBeforeAnyForegroundFrame() {
        startSample(lockEnabled = true)
        rule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(background.await(5, TimeUnit.SECONDS))
        assertFalse(holder.sessionUnlocked.value)
        assertTrue("The locked session must stop the sample while still CREATED: ${driver.events}",
            driver.stopped.await(5, TimeUnit.SECONDS))
        rule.scenario.onActivity { assertEquals(Lifecycle.State.CREATED, it.lifecycle.currentState) }
    }

    @Test fun withoutTheLockTheBackgroundAloneDoesNotStopTheSample() {
        startSample(lockEnabled = false)
        rule.scenario.moveToState(Lifecycle.State.CREATED)
        assertTrue(background.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        assertFalse("Unlocked background playback is kept: ${driver.events}", driver.stopped.await(300, TimeUnit.MILLISECONDS))
    }
}
