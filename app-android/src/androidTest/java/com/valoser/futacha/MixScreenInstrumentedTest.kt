@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.mix.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.MixScreenHost
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import coil3.ImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaCatalogImageLoader
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import org.junit.*
import org.junit.Assert.*

/** まとめカタログの画面：二つの板を束ね、スレが並び、押すと開く操作が呼ばれ、切替と編集の入口が出る（通信は差し替え）。 */
class MixScreenInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val database = "mix_screen_test.db"
    private lateinit var store: AndroidCompatibilityStore
    private val may = "https://may.2chan.net/b/"
    private val img = "https://img.2chan.net/b/"

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    @Test fun theMixShowsThreadsOpensOneAndOffersSwitchAndEdit() {
        val id = runBlocking {
            MixStore(store).create("テスト", listOf(may, img), MixOrderKind.ALTERNATE, 1_700_000_000_000L)
            (MixStore(store).load() as MixLoadResult.Ok).mixes.first().id
        }
        val boards = listOf(BoardSummary("may", "may", "", may, ""), BoardSummary("img", "img", "", img, ""))
        val opened = mutableListOf<MixEntry>()
        rule.setContent {
            val loader = ImageLoader.Builder(rule.activity).build()
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                MaterialTheme {
                    MixScreenHost(id, store, FakeBoardRepository(), boards, onExit = {}, onOpen = { opened += it }, onSwitch = {}, stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder())
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("表示できるスレッドがありません").fetchSemanticsNodes().isEmpty() &&
            rule.onAllNodesWithText("テスト").fetchSemanticsNodes().isNotEmpty() }
        val first = runBlocking { com.valoser.futacha.shared.repo.mock.MockBoardData.catalogItems() }.first()
        rule.waitUntil(10_000) { rule.onAllNodesWithText(first.title.orEmpty().take(8), substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodesWithText(first.title.orEmpty().take(8), substring = true)[0].performClick()
        rule.waitUntil(5_000) { opened.isNotEmpty() }
        assertEquals(first.id, opened.first().item.id)
        rule.onNodeWithText("切替").assertIsDisplayed()
        rule.onNodeWithText("編集").assertIsDisplayed()
        rule.onNodeWithText("切替").performClick()
        rule.onNodeWithText("新しいまとめを作る").assertIsDisplayed()
    }
    @Test fun switchAndScrollSurviveDisposingTheCatalogForAThread() {
        val ids = runBlocking {
            MixStore(store).create("Mix A", listOf(may), MixOrderKind.ALTERNATE, 3)
            MixStore(store).create("Mix B", listOf(img), MixOrderKind.ALTERNATE, 4)
            (MixStore(store).load() as MixLoadResult.Ok).mixes.map { it.id }
        }
        val boards = listOf(BoardSummary("may", "may", "", may, ""), BoardSummary("img", "img", "", img, ""))
        val repository = object : com.valoser.futacha.shared.repo.BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogPage(board: String, mode: com.valoser.futacha.shared.model.CatalogMode) =
                com.valoser.futacha.shared.model.CatalogPageContent((1..80).map { n ->
                    com.valoser.futacha.shared.model.CatalogItem("$n", "${board}res/$n.htm", "Row $n", null, null, replyCount = n)
                })
            override suspend fun getCatalogPageWithSettings(board: String, mode: com.valoser.futacha.shared.model.CatalogMode,
                settings: com.valoser.futacha.shared.model.CatalogFetchSettings) = getCatalogPage(board, mode)
        }
        val loader = ImageLoader.Builder(rule.activity).build()
        rule.setContent {
            var selected by remember { mutableStateOf(ids.first()) }
            var thread by remember { mutableStateOf(false) }
            val holder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                MaterialTheme {
                    if (thread) TextButton(onClick = { thread = false }) { Text("Return to MIX") }
                    else MixScreenHost(selected, store, repository, boards, onExit = {}, onOpen = { thread = true },
                        onSwitch = { selected = it }, stateHolder = holder)
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Row 1").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("切替").performClick()
        rule.onNodeWithText("Mix B").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Mix B").fetchSemanticsNodes().isNotEmpty() &&
            rule.onAllNodesWithText("Row 1").fetchSemanticsNodes().isNotEmpty() }
        fun grid() = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))[0]
        grid().performScrollToIndex(40)
        rule.waitForIdle()
        val before = grid().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0)
        rule.onNodeWithText("Row 41").performClick()
        rule.onNodeWithText("Return to MIX").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Row 41").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Mix B").assertIsDisplayed()
        assertEquals(before, grid().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0.01f)
    }
    @Test fun r18MembersRequireConsentAndCancelLeavesTheCreationUnfinished() {
        rule.setContent {
            MaterialTheme {
                val confirm = com.valoser.futacha.shared.ui.board.rememberMixR18Confirmation(store, loadBoards = {
                    listOf(com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo("adult", "Adult board", "https://nijiurachan.net/b/adult/", r18 = true))
                })
                val scope = rememberCoroutineScope()
                var saved by remember { mutableStateOf(false) }
                if (saved) Text("Creation completed")
                else TextButton(onClick = { scope.launch { saved = confirm(listOf("https://nijiurachan.net/b/adult/")) } }) { Text("Create with R18") }
            }
        }
        rule.onNodeWithText("Create with R18").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("R18の板").assertIsDisplayed() }.isSuccess }
        rule.onNodeWithText("キャンセル").performClick()
        rule.onNodeWithText("Creation completed").assertDoesNotExist()
        rule.onNodeWithText("Create with R18").performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("追加").assertIsDisplayed() }.isSuccess }
        rule.onNodeWithText("追加").performClick()
        rule.onNodeWithText("Creation completed").assertIsDisplayed()
    }
    @Test fun headTitlesContinuePastTwelveVisibleRowsWithoutScrolling() {
        val board = BoardSummary("head-img", "img", "", img, "")
        val entries = (1..25).map { n -> MixEntry(board,
            com.valoser.futacha.shared.model.CatalogItem("$n", "${img}res/$n.htm", null, null, null, replyCount = 0),
            MixEntryKey(img, "$n"), null, false, n - 1, 25) }
        val repository = object : com.valoser.futacha.shared.repo.BoardRepository by FakeBoardRepository() {
            override suspend fun resolveCatalogDisplayTitle(board: String, item: com.valoser.futacha.shared.model.CatalogItem,
                allowFallbackHeadScan: Boolean): String {
                kotlinx.coroutines.delay(10)
                return "Completed ${item.id}"
            }
        }
        rule.setContent {
            MaterialTheme {
                val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                val list = androidx.compose.foundation.lazy.rememberLazyListState()
                val titles = com.valoser.futacha.shared.ui.board.rememberMixHeadMetadataTitles(entries, repository,
                    com.valoser.futacha.shared.model.CatalogDisplayStyle.Grid, grid, list, true)
                androidx.compose.foundation.layout.Column {
                    Text("Resolved ${titles.size}")
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(1), state = grid,
                        modifier = androidx.compose.ui.Modifier.height(400.dp)) {
                        items(25) { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.height(8.dp)) }
                    }
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Resolved 25").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun missingMemberCanBeRemovedToMakeRoomAtTheLimit() {
        val missing = (1..10).map { "https://may.2chan.net/board$it/" }
        val def = runBlocking {
            MixStore(store).create("Missing members", missing, MixOrderKind.ALTERNATE, 99)
            (MixStore(store).load() as MixLoadResult.Ok).mixes.single()
        }
        val replacement = BoardSummary("new-img", "Replacement board", "", img, "")
        rule.setContent {
            var closed by remember { mutableStateOf(false) }
            MaterialTheme {
                if (closed) Text("Editor saved")
                else com.valoser.futacha.shared.ui.board.MixEditDialog(def, listOf(replacement), store,
                    onDismiss = { closed = true }, onDeleted = {})
            }
        }
        rule.onNodeWithText("${missing.first()}（未登録）").assertIsDisplayed()
        rule.onNodeWithTag("mix-member-${missing.first()}").assertIsOn().performClick().assertIsOff()
        rule.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasTestTag("mix-member-$img"))
        rule.onNodeWithTag("mix-member-$img").assertIsOff().performClick().assertIsOn()
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Editor saved").fetchSemanticsNodes().isNotEmpty() }
        val saved = runBlocking { (MixStore(store).load() as MixLoadResult.Ok).mixes.single() }
        assertEquals("Saved members: ${saved.members}", 10, saved.members.size)
        assertFalse(missing.first() in saved.members)
        assertTrue(img in saved.members)
    }
    @Test fun completedTitlesAreHiddenAndNgOffRestoresThemWithoutRefetchingHeads() {
        val board = BoardSummary("ng-img", "img", "", img, "")
        val id = runBlocking {
            MixStore(store).create("Head NG", listOf(img), MixOrderKind.ALTERNATE, 991)
            (MixStore(store).load() as MixLoadResult.Ok).mixes.single().id
        }
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val repository = object : com.valoser.futacha.shared.repo.BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogPage(board: String, mode: com.valoser.futacha.shared.model.CatalogMode) =
                com.valoser.futacha.shared.model.CatalogPageContent(listOf(
                    com.valoser.futacha.shared.model.CatalogItem("501", "${img}res/501.htm", "No.501", null, null, replyCount = 1)))
            override suspend fun getCatalogPageWithSettings(board: String, mode: com.valoser.futacha.shared.model.CatalogMode,
                settings: com.valoser.futacha.shared.model.CatalogFetchSettings) = getCatalogPage(board, mode)
            override suspend fun resolveCatalogDisplayTitle(board: String, item: com.valoser.futacha.shared.model.CatalogItem,
                allowFallbackHeadScan: Boolean): String { calls.incrementAndGet(); return "Hidden completed title" }
        }
        val loader = ImageLoader.Builder(rule.activity).build()
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                    MixScreenHost(id, store, repository, listOf(board), onExit = {}, onOpen = {}, onSwitch = {},
                        stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder(),
                        modePolicy = com.valoser.futacha.shared.ui.board.MixModePolicy.COMPAT)
                }
            }
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Hidden completed title").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { store.upsertNgRule(com.valoser.futacha.shared.compat.CompatNgRule("head-ng",
            com.valoser.futacha.shared.compat.CompatNgKind.CATALOG_WORD, "*", "Hidden", 0L)) }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("表示できるスレッドがありません").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Hidden completed title").assertDoesNotExist()
        runBlocking { store.savePreference("compat.catalog.NG機能", "OFF") }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Hidden completed title").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { store.savePreference("compat.catalog.NG機能", "ON") }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("表示できるスレッドがありません").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        assertEquals("Filtering must retain the completed head title", 1, calls.get())
    }

    @Test fun disabledHostExitsWithoutFetchingSavedMix() {
        val old = com.valoser.futacha.shared.compat.MixAvailability.enabled
        val id = runBlocking {
            MixStore(store).create("Disabled MIX", listOf(may), MixOrderKind.ALTERNATE, 992)
            (MixStore(store).load() as MixLoadResult.Ok).mixes.single().id
        }
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val repository = object : com.valoser.futacha.shared.repo.BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogPage(board: String, mode: com.valoser.futacha.shared.model.CatalogMode): com.valoser.futacha.shared.model.CatalogPageContent {
                calls.incrementAndGet(); return com.valoser.futacha.shared.model.CatalogPageContent(emptyList())
            }
        }
        try {
            com.valoser.futacha.shared.compat.MixAvailability.enabled = false
            rule.setContent {
                var exited by remember { mutableStateOf(false) }
                MaterialTheme {
                    if (exited) Text("Disabled MIX exited")
                    else MixScreenHost(id, store, repository, listOf(BoardSummary("may", "may", "", may, "")),
                        onExit = { exited = true }, onOpen = {}, onSwitch = {},
                        stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder())
                }
            }
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Disabled MIX exited").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(0, calls.get())
        } finally { com.valoser.futacha.shared.compat.MixAvailability.enabled = old }
    }

    @Test fun failedEditorSaveShowsTheReasonAndKeepsTheEditedName() {
        val definition = runBlocking {
            MixStore(store).create("Original name", listOf(may), MixOrderKind.ALTERNATE, 993)
            (MixStore(store).load() as MixLoadResult.Ok).mixes.single()
        }
        val failing = object : com.valoser.futacha.shared.compat.CompatibilityStore by store {
            override suspend fun savePreference(key: String, value: String) { throw java.io.IOException("test write failure") }
        }
        rule.setContent {
            var closed by remember { mutableStateOf(false) }
            MaterialTheme {
                if (closed) Text("Editor closed")
                else com.valoser.futacha.shared.ui.board.MixEditDialog(definition,
                    listOf(BoardSummary("may", "may", "", may, "")), failing,
                    onDismiss = { closed = true }, onDeleted = { closed = true })
            }
        }
        rule.onNode(hasSetTextAction()).performTextReplacement("Edited name")
        rule.onNodeWithText("保存").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("まとめカタログを保存できませんでした").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Edited name").assertIsDisplayed()
        rule.onNodeWithText("Editor closed").assertDoesNotExist()
        assertEquals("Original name", runBlocking { (MixStore(store).load() as MixLoadResult.Ok).mixes.single().name })
    }

    @Test fun orderMenuQueuesOnlyTheLastSelectionUntilTheFirstFetchFinishes() {
        val id = runBlocking {
            MixStore(store).create("Queued order", listOf(may), MixOrderKind.ALTERNATE, 994)
            (MixStore(store).load() as MixLoadResult.Ok).mixes.single().id
        }
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val orders = java.util.concurrent.CopyOnWriteArrayList<com.valoser.futacha.shared.model.CatalogMode>()
        val cancelled = java.util.concurrent.atomic.AtomicInteger()
        val repository = object : com.valoser.futacha.shared.repo.BoardRepository by FakeBoardRepository() {
            override suspend fun getCatalogPage(board: String, mode: com.valoser.futacha.shared.model.CatalogMode): com.valoser.futacha.shared.model.CatalogPageContent {
                orders += mode
                try { gate.await() } catch (error: kotlinx.coroutines.CancellationException) { cancelled.incrementAndGet(); throw error }
                return com.valoser.futacha.shared.model.CatalogPageContent(listOf(
                    com.valoser.futacha.shared.model.CatalogItem("1", "${may}res/1.htm", "Order $mode", null, null, replyCount = 1)))
            }
            override suspend fun getCatalogPageWithSettings(board: String, mode: com.valoser.futacha.shared.model.CatalogMode,
                settings: com.valoser.futacha.shared.model.CatalogFetchSettings) = getCatalogPage(board, mode)
        }
        val loader = ImageLoader.Builder(rule.activity).build()
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader, LocalFutachaCatalogImageLoader provides loader) {
                    MixScreenHost(id, store, repository, listOf(BoardSummary("may", "may", "", may, "")),
                        onExit = {}, onOpen = {}, onSwitch = {}, stateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder(),
                        modePolicy = com.valoser.futacha.shared.ui.board.MixModePolicy.FUTABER)
                }
            }
        }
        rule.waitUntil(5_000) { orders.size == 1 }
        rule.onNodeWithContentDescription("並び順：交互").performClick()
        rule.onNodeWithText("新しい順").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("並び順：新しい順").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("並び順：新しい順").performClick()
        rule.onNodeWithText("古い順").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithContentDescription("並び順：古い順").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        assertEquals(listOf(com.valoser.futacha.shared.model.CatalogMode.Catalog), orders.toList())
        assertEquals(0, cancelled.get())
        gate.complete(Unit)
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Order Old").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(listOf(com.valoser.futacha.shared.model.CatalogMode.Catalog, com.valoser.futacha.shared.model.CatalogMode.Old), orders.toList())
    }

}
