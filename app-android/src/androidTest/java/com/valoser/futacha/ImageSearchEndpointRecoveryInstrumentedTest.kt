@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import coil3.ImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.board.searchThreadImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ImageSearchEndpointRecoveryInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val database = "image_search_endpoint_recovery.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var images: ImageLoader
    private val mistaken = "https://ascii2d.net/wrong-path"

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        images = ImageLoader(rule.activity)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking {
            store.initialize()
            store.savePreference(COMPAT_ASCII2D_ENDPOINT_KEY, mistaken)
            store.savePreference(COMPAT_ASCII2D_ENABLED_KEY, "ON")
        }
    }
    @After fun close() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store.closeForTest() }
        images.shutdown()
        rule.activity.deleteDatabase(database)
    }
    private fun show(modern: Boolean) {
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFutachaImageLoader provides images,
                    LocalFutachaCatalogImageLoader provides images) {
                CompatSettingsScreen(path = "image_search", store = store,
                    preferences = store.preferences.collectAsState(emptyMap()).value,
                    fileSystem = null, onNavigate = {}, onBack = {}, modernPresentation = modern)
                }
            }
        }
        rule.onNodeWithTag("compat-ascii2d-endpoint-setting").performClick()
        rule.onNodeWithTag("compat-ascii2d-address").assertTextEquals(mistaken)
    }
    @Test fun futachaSettingsCanResetInvalidInputAndPersistTheDefault() {
        show(true)
        rule.onNodeWithTag("compat-ascii2d-address").performTextReplacement("https://wrong.invalid/search")
        rule.onNodeWithText("保存").performClick()
        rule.onNodeWithText("ascii2d.net の検索URLを入力してください。").assertIsDisplayed()
        assertEquals(mistaken, runBlocking { store.preferences.first() }[COMPAT_ASCII2D_ENDPOINT_KEY])
        rule.onNodeWithText("初期値に戻す").performClick()
        rule.onNodeWithTag("compat-ascii2d-address").assertTextEquals(DEFAULT_COMPAT_ASCII2D_ENDPOINT)
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil { runBlocking { store.preferences.first() }[COMPAT_ASCII2D_ENDPOINT_KEY] == DEFAULT_COMPAT_ASCII2D_ENDPOINT }
        rule.onNodeWithTag("compat-ascii2d-endpoint-setting").performClick()
        rule.onNodeWithTag("compat-ascii2d-address").assertTextEquals(DEFAULT_COMPAT_ASCII2D_ENDPOINT)
        rule.onNodeWithText("キャンセル").performClick()
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking {
            store.closeForTest()
            store = AndroidCompatibilityStore(rule.activity, databaseName = database)
            store.initialize()
            assertEquals(DEFAULT_COMPAT_ASCII2D_ENDPOINT, store.preferences.first()[COMPAT_ASCII2D_ENDPOINT_KEY])
        }
    }
    @Test fun toshiakiSettingsCanCorrectTheUrlAndSearchUsingTheSavedValue() {
        show(false)
        val corrected = "https://ascii2d.net/imagesearch/search"
        rule.onNodeWithTag("compat-ascii2d-address").performTextReplacement(corrected)
        rule.onNodeWithText("キャンセル").performClick()
        assertEquals(mistaken, runBlocking { store.preferences.first() }[COMPAT_ASCII2D_ENDPOINT_KEY])
        rule.onNodeWithTag("compat-ascii2d-endpoint-setting").performClick()
        rule.onNodeWithTag("compat-ascii2d-address").assertTextEquals(mistaken)
        rule.onNodeWithTag("compat-ascii2d-address").performTextReplacement("  $corrected  ")
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil { runBlocking { store.preferences.first() }[COMPAT_ASCII2D_ENDPOINT_KEY] == corrected }
        var requested: String? = null
        val client = HttpClient(MockEngine { request ->
            requested = request.url.toString()
            respond("<a href=\"https://ascii2d.net/search/color/fixture\">result</a>")
        })
        try {
            val preferences = runBlocking { store.preferences.first() }
            assertTrue(isCompatAscii2dRegistered(preferences))
            val result = runBlocking { searchThreadImage(client, "https://may.2chan.net/b/src/fixture.png",
                CompatImageSearchTarget.ASCII2D_URL, compatAscii2dEndpoint(preferences)) }
            assertTrue(result.toString(), result.isSuccess)
            assertEquals(corrected, requested)
        } finally { client.close() }
    }
}
