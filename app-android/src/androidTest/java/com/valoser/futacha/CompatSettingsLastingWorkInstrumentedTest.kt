@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** L58: work started on the shared settings screen is not cancelled by leaving it, and its result is still told. */
class CompatSettingsLastingWorkInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "l58-${System.nanoTime()}.db"
    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private val shown = mutableStateOf(true)
    private val notices = CopyOnWriteArrayList<String>()
    private lateinit var storage: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader

    @Before fun prepare() {
        storage = AndroidCompatibilityStore(rule.activity, databaseName = db)
        runBlocking { storage.initialize() }
        loader = ImageLoader.Builder(rule.activity).build()
    }

    @After fun cleanup() {
        release.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { storage.closeForTest() }
        rule.activity.deleteDatabase(db)
    }

    private fun show(store: CompatibilityStore) {
        rule.setContent {
            val lasting = rememberCoroutineScope()
            MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                if (shown.value) CompatSettingsScreen("storage", store, emptyMap(), null, onNavigate = {}, onBack = {},
                    lastingScope = lasting, onLastingNotice = { notices += it })
            } }
        }
    }

    private fun openEntry(key: String) {
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("compat-settings-list-storage").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-settings-list-storage").performScrollToNode(hasTestTag("compat-setting-$key"))
        rule.onNodeWithTag("compat-setting-$key").performClick()
    }

    @Test fun aCacheClearGoesOnAfterTheScreenIsLeft() {
        val cleared = AtomicBoolean(false)
        show(object : CompatibilityStore by storage {
            override suspend fun clearThreadSnapshotCache(): Long {
                started.complete(Unit); release.await()
                return storage.clearThreadSnapshotCache().also { cleared.set(true) }
            }
        })
        openEntry("dummyThreadCacheClear")
        rule.onNodeWithText("削除する").performClick()
        rule.waitUntil(5_000) { started.isCompleted }
        rule.runOnUiThread { shown.value = false }
        rule.waitForIdle()
        release.complete(Unit)
        rule.waitUntil(5_000) { cleared.get() }
    }

    @Test fun aFailureAfterTheScreenIsLeftIsToldAsTheAppsNotice() {
        val directoryKey = compatPreferenceStorageKey("storage", "dummyDownloadDir")
        show(object : CompatibilityStore by storage {
            override suspend fun savePreference(key: String, value: String) {
                if (key == directoryKey) { started.complete(Unit); release.await(); throw IOException("L58 test: disk full") }
                storage.savePreference(key, value)
            }
        })
        openEntry("dummyDownloadDir")
        rule.onNodeWithText("リセット").performClick()
        rule.waitUntil(5_000) { started.isCompleted }
        rule.runOnUiThread { shown.value = false }
        rule.waitForIdle()
        release.complete(Unit)
        rule.waitUntil(5_000) { notices.any { it.startsWith("操作に失敗しました") } }
        assertTrue(notices.any { "disk full" in it })
    }

    /** L165: the settings screen opened again knows its cache clear is still running and does not start another. */
    @Test fun aCacheClearStillRunningIsShownAsSuchWhenTheScreenIsOpenedAgain() {
        val clears = java.util.concurrent.atomic.AtomicInteger()
        show(object : CompatibilityStore by storage {
            override suspend fun clearThreadSnapshotCache(): Long {
                clears.incrementAndGet(); started.complete(Unit); release.await()
                return storage.clearThreadSnapshotCache()
            }
        })
        openEntry("dummyThreadCacheClear")
        rule.onNodeWithText("削除する").performClick()
        rule.waitUntil(5_000) { started.isCompleted }
        rule.runOnUiThread { shown.value = false }
        rule.waitForIdle()
        rule.runOnUiThread { shown.value = true }
        openEntry("dummyThreadCacheClear")
        // The list row and the dialog's button both say it; the button is disabled.
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("削除中…") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("削除中…") and isNotEnabled()).performClick()
        rule.waitForIdle()
        assertTrue("one clear only, not ${clears.get()}", clears.get() == 1)
        release.complete(Unit)
    }
}
