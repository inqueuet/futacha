@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import coil3.ImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.model.SaveLocation
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.state.createAppStateStore
import com.valoser.futacha.shared.ui.board.ProvideFutachaSharedFeatures
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class SaveLocationResetInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun resettingSharedPreferencesDoesNotRestoreThePreviousSaveFolder() {
        val folder = File(rule.activity.cacheDir, "save-reset-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(rule.activity.applicationContext) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = folder
        }
        val modern = createAppStateStore(context)
        val database = "save-reset-${System.nanoTime()}.db"
        val shared = AndroidCompatibilityStore(rule.activity, databaseName = database)
        val key = compatPreferenceStorageKey("storage", "dummyDownloadDir")
        val images = ImageLoader(rule.activity)
        val custom = SaveLocation.TreeUri("content://test/tree/previous")
        try {
            runBlocking {
                shared.initialize()
                shared.savePreference(key, "tree:content://test/tree/previous")
            }
            rule.setContent {
                FutachaTheme {
                    CompositionLocalProvider(LocalFutachaImageLoader provides images) {
                        ProvideFutachaSharedFeatures(shared, null, null, null, null, "test", appStateStore = modern) {}
                    }
                }
            }
            rule.waitUntil(10_000) { runBlocking { modern.manualSaveLocation.first() == custom } }
            runBlocking { shared.savePreference(key, "") }
            val default = SaveLocation.Path(DEFAULT_MANUAL_SAVE_ROOT)
            rule.waitUntil(10_000) { runBlocking { modern.manualSaveLocation.first() == default } }
            rule.waitForIdle()
            assertNotEquals("tree:content://test/tree/previous", runBlocking { shared.preferences.first()[key] })
            runBlocking { modern.setManualSaveLocation(SaveLocation.Path("new-folder")) }
            rule.waitUntil(10_000) { runBlocking { shared.preferences.first()[key] == "new-folder" } }
        } finally {
            rule.runOnUiThread { rule.activity.setContent {} }
            images.shutdown()
            modern.close()
            runBlocking { shared.closeForTest() }
            rule.activity.deleteDatabase(database)
        }
    }
}
