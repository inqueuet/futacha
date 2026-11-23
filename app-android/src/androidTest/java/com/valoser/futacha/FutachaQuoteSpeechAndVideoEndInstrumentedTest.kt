@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.ui.board.FUTACHA_SPEECH_READ_QUOTES_KEY
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** may 1449267646 K/L: ふたちゃ's quote read-aloud switches and the video-end choice are shown and saved. */
class FutachaQuoteSpeechAndVideoEndInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "kl-${System.nanoTime()}.db"
    private lateinit var storage: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { storage.initialize() }
        loader = ImageLoader.Builder(rule.activity).build()
    }

    @After fun cleanup() {
        loader.shutdown()
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    @Test fun theQuoteSwitchSavesOn() {
        rule.setContent { MaterialTheme { CompatSpeechSettingsScreen(storage, emptyMap(), onBack = {}, futachaQuoteOptions = true) } }
        rule.onNodeWithTag("futacha-speech-quote-low-voice").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("futacha-speech-read-quotes").performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { storage.loadPreference(FUTACHA_SPEECH_READ_QUOTES_KEY) } == "ON" }
        assertEquals("ON", runBlocking { storage.loadPreference(FUTACHA_SPEECH_READ_QUOTES_KEY) })
    }

    @Test fun theViewerPageOffersTheVideoEnd() {
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
            CompatSettingsScreen("viewer", storage, emptyMap(), null, onNavigate = {}, onBack = {})
        } } }
        rule.onNode(hasText("動画を最後まで再生したら")).performScrollTo().assertIsDisplayed()
    }
}
