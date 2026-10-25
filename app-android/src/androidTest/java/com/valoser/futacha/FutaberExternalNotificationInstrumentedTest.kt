@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.shared.compat.CompatWatchResult
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcher
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcherEntry
import com.valoser.futacha.shared.ui.compat.CompatExternalWatcherSnapshot
import com.valoser.futacha.shared.ui.futaber.FutaberExternalWatcher
import com.valoser.futacha.shared.ui.futaber.FutaberFavoriteView
import com.valoser.futacha.shared.ui.futaber.FutaberManagePanel
import com.valoser.futacha.shared.ui.futaber.FutaberSavedBoxState
import com.valoser.futacha.shared.ui.futaber.FutaberTabView
import com.valoser.futacha.shared.ui.futaber.FutaberTheme
import com.valoser.futacha.shared.ui.futaber.FutaberThemeMode
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real management LazyColumn plus production external load/delete boundary; old product APIs, no provider/network/audio. */
class FutaberExternalNotificationInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val opened = mutableListOf<String>()
    private val board = BoardSummary("may", "may", "", "https://may.2chan.net/b/futaba.php", "")
    private lateinit var provider: Provider
    private lateinit var external: FutaberExternalWatcher
    private var loader: ImageLoader? = null

    private fun entry(key: String, word: String = "猫", thread: String = "1234", url: String? = null) =
        CompatExternalWatcherEntry(key, true, "通知$thread", 12,
            url ?: "https://may.2chan.net/b/res/$thread.htm", extractedKeyword = word,
            boardName = "may", insertedAtEpochMillis = 100, updatedAtEpochMillis = 200)

    private class Provider(initial: List<CompatExternalWatcherEntry>) : CompatExternalWatcher {
        @Volatile var rows = initial
        val deleted = java.util.Collections.synchronizedList(mutableListOf<String>())
        var clears = 0
        override suspend fun load() = Result.success(CompatExternalWatcherSnapshot(true, true, entries = rows))
        override suspend fun delete(key: String): Result<Unit> {
            deleted += key
            rows = rows.filterNot { it.key == key }
            return Result.success(Unit)
        }
        override suspend fun deleteAll(): Result<Unit> { clears++; rows = emptyList(); return Result.success(Unit) }
        override fun openManager() = Result.success(Unit)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        scope.cancel()
        loader?.shutdown()
    }

    private fun host(entries: List<CompatExternalWatcherEntry>) {
        provider = Provider(entries)
        external = FutaberExternalWatcher(provider, scope)
        external.reload = { scope.launch { external.load(true) }; Unit }
        runBlocking { external.load(true) }
        val images = ImageLoader.Builder(rule.activity).memoryCache(null).diskCache(null).components {
            // Terminate every request in memory; the fixture can never reach a network fetcher.
            add(Interceptor { chain -> SuccessResult(ColorImage(android.graphics.Color.BLUE, 64, 64), chain.request) })
        }.build().also { loader = it }
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides images, LocalFutachaCatalogImageLoader provides images) {
            FutaberTheme(FutaberThemeMode.Light) {
                FutaberManagePanel(
                    history = emptyList<ThreadHistoryEntry>(), boards = listOf(board), tabs = emptyList<FutaberTabView>(),
                    savedBox = FutaberSavedBoxState(), savedBoxAvailable = false,
                    onOpenSaved = { _: SavedThread -> }, onDeleteSaved = { _: SavedThread -> },
                    notifications = external.results.orEmpty(), notificationsAvailable = true,
                    notificationsStatus = external.status.orEmpty(),
                    onOpenNotification = { result: CompatWatchResult -> opened.add(result.keyword); Unit },
                    onRemoveNotification = { result: CompatWatchResult -> external.remove(result) },
                    onClearNotifications = { external.clear() },
                    onOpenWatcherManager = {}, favorites = emptyList<FutaberFavoriteView>(),
                    onOpenFavorite = { _: FutaberFavoriteView -> }, onRemoveFavorite = { _: FutaberFavoriteView -> }, onClearFavorites = {},
                    onOpenHistory = { _: ThreadHistoryEntry -> }, onDeleteHistory = { _: ThreadHistoryEntry -> },
                    onOpenTab = { _: FutaberTabView -> }, onRemoveTab = { _: FutaberTabView -> },
                    onClearTabs = {}, onClose = {}
                )
            }
            }
        }
        rule.onNodeWithTag("futaber-manage-category-Notifications").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-notification-row").fetchSemanticsNodes().size == entries.size }
    }

    @Test fun twoKeywordsForOneNormalizedUrlOpenAndRemoveOnlyTheSelectedProviderRow() {
        host(listOf(entry("first", "猫"), entry("second", "犬", url = "http://may.2chan.net/b/res/1234.htm?x=1"),
            entry("unrelated", "別", "5678")))
        rule.onAllNodesWithTag("futaber-notification-row")[0].performClick()
        rule.onAllNodesWithTag("futaber-notification-row")[1].performClick()
        assertEquals(listOf("猫", "犬"), opened)
        rule.onNodeWithTag("futaber-manage-edit").performClick()
        rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
        rule.waitUntil(5_000) { provider.deleted.isNotEmpty() }
        assertEquals(listOf("first"), provider.deleted.toList())
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-notification-row").fetchSemanticsNodes().size == 2 }
        assertEquals(listOf("second", "unrelated"), provider.rows.map { it.key })
        rule.onNodeWithText("「犬」  may").assertIsDisplayed()
        rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
        rule.waitUntil(5_000) { provider.deleted.size == 2 }
        assertEquals(listOf("first", "second"), provider.deleted.toList())
        assertEquals(listOf("unrelated"), provider.rows.map { it.key })
        assertEquals(0, provider.clears)
    }

    @Test fun identicalVisibleRowsWithDifferentOpaqueIdsRemainSeparateAndDeleteTheFirstId() {
        host(listOf(entry("same:one"), entry("same:two")))
        rule.onNodeWithTag("futaber-manage-edit").performClick()
        rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
        rule.waitUntil(5_000) { provider.deleted.isNotEmpty() }
        assertEquals(listOf("same:one"), provider.deleted.toList())
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-notification-row").fetchSemanticsNodes().size == 1 }
        assertEquals(listOf("same:two"), provider.rows.map { it.key })
        assertEquals(0, provider.clears)
    }

    @Test fun missingAndNamespaceShapedProviderIdsDoNotCollideOrDeleteAnUnknownTarget() {
        host(listOf(entry(""), entry("", "犬"), entry("external:missing:0", "鳥"),
            entry("internal:url:https://may.2chan.net/b/res/1234.htm", "魚"),
            entry("external:missing:0", "重複ID")))
        rule.onNodeWithTag("futaber-manage-edit").performClick()
        rule.onAllNodesWithTag("futaber-manage-delete")[0].performClick()
        rule.waitForIdle()
        assertTrue(provider.deleted.isEmpty())
        assertEquals(5, provider.rows.size)
        assertEquals(0, provider.clears)
        rule.onAllNodesWithTag("futaber-notification-row").assertCountEquals(5)
    }
}
