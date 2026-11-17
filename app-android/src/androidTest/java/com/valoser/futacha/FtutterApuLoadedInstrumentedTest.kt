@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L24: a manually loaded あぷ小 stays loaded after its row scrolls away and back. No network (file URLs fail quietly). */
class FtutterApuLoadedInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var loader: ImageLoader
    @Before fun prepare() { loader = ImageLoader.Builder(rule.activity).build() }
    @After fun cleanup() { rule.runOnUiThread { rule.activity.setContent {} }; rule.waitForIdle(); loader.shutdown() }

    @Test fun aLoadedApuStaysLoadedAfterItsRowScrollsAway() {
        // Manual loading only (0 = never automatic), like a mobile connection with the default policy.
        val settings = FtutterThreadSettings(apuAutoLoad = 0)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                FtutterTheme(null, FtutterBrightness.LIGHT) {
                    // Held by the thread screen, outside the rows (as FtutterThreadScreen does).
                    val loaded = remember { mutableStateSetOf<String>() }
                    LazyColumn(Modifier.fillMaxSize().testTag("rows")) {
                        item { FtutterApuPreviews("あぷ小 https://dec.2chan.net/up2/src/fu7099124.jpg", settings, 1f, loaded) {} }
                        items(80) { Text("レス$it", Modifier.height(48.dp)) }
                    }
                }
            }
        }
        rule.onNodeWithTag("ftutter-apu-load").performClick()
        rule.onNodeWithTag("ftutter-apu-preview").assertExists()
        rule.onNodeWithTag("rows").performScrollToIndex(80)
        rule.onAllNodesWithTag("ftutter-apu-preview").assertCountEquals(0)
        rule.onNodeWithTag("rows").performScrollToIndex(0)
        rule.onNodeWithTag("ftutter-apu-preview").assertExists()
        rule.onAllNodesWithTag("ftutter-apu-load").assertCountEquals(0)
    }
}
