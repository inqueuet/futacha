package com.valoser.futacha

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import com.valoser.futacha.shared.compat.CompatVolumeKey
import com.valoser.futacha.shared.compat.CompatVolumeKeyBus
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたばー風モードの機能そろえ B1 後半（拡張機能のスイッチ・ボリュームキーのモード・NGで隠したレスの抽出）。
 * チュートリアル板の同梱データ（模擬リポジトリ）だけを使い、実サーバーへは送らない（画像の保存・共有・外部で開くは押さない）。
 */
private const val B1B_TUTORIAL_URL = "https://www.example.com/t/futaba.php"

class FutaberParityB1bInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private var originalDeleteKey = ""
    private var originalNgWords = emptyList<String>()
    private val viewerColumnKeys = listOf("compat.viewer.galleryGridViewPortraitClmNum", "compat.viewer.galleryGridViewLandscapeClmNum")
    private var originalViewerColumns = emptyMap<String, String?>()

    private val keys = listOf(
        "compat.futaber.ext.volumeKeys",
        "compat.futaber.ext.volumeKeyMode",
        "compat.futaber.ext.extract",
        "compat.futaber.ext.sortByBoard",
        "compat.futaber.ext.catalogLongPress",
        "compat.futaber.ext.postDetailOps",
        "compat.futaber.ext.galleryColumns",
        "compat.futaber.ext.imageMenu",
        "compat.futaber.catalog.sort",
        "compat.futaber.catalog.sortByBoard",
        "compat.futaber.lastBoardId",
        "compat.futaber.tabs",
        "compat.futaber.seen"
    )

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            app.compatibilityStore.savePreferences(keys.associateWith { null })
            originalDeleteKey = app.appStateStore.lastUsedDeleteKey.first()
            originalNgWords = app.appStateStore.ngWords.first()
            app.appStateStore.setNgWords(emptyList())
            originalViewerColumns = viewerColumnKeys.associateWith { app.compatibilityStore.loadPreference(it) }
            app.compatibilityStore.savePreferences(viewerColumnKeys.associateWith { null })
            // Earlier tests leave a read position on the tutorial thread, which would open it
            // scrolled down so that "the first picture" on screen is not the thread's first one.
            app.appStateStore.updateHistory { list ->
                list.map { entry ->
                    if (entry.boardId == "t") entry.copy(lastReadItemIndex = 0, lastReadItemOffset = 0, lastReadPostId = null)
                    else entry
                }
            }
            // The tests read the bundled tutorial board; put it back if an earlier run lost it.
            if (app.appStateStore.boards.first().none { it.url == B1B_TUTORIAL_URL }) {
                app.appStateStore.updateBoards { boards ->
                    listOf(
                        com.valoser.futacha.shared.model.BoardSummary(
                            "t", "チュートリアル＠ふたちゃ", "チュートリアル", B1B_TUTORIAL_URL, "チュートリアル"
                        )
                    ) + boards
                }
            }
        }
        switchProfile(ExperienceProfile.FUTABER)
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.appStateStore.updateHistory { list -> list.filterNot { it.threadId == "999011" } }
            app.appStateStore.setNgWords(originalNgWords)
            app.appStateStore.setLastUsedDeleteKey(originalDeleteKey)
            app.compatibilityStore.savePreferences(keys.associateWith { null })
            app.compatibilityStore.savePreferences(originalViewerColumns)
        }
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

    private fun waitForTag(tag: String, timeoutMillis: Long = 20_000) {
        rule.waitUntil(timeoutMillis) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun absent(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()

    private fun preference(key: String): String? = runBlocking { app.compatibilityStore.loadPreference(key) }

    private fun turnOn(vararg extensionKeys: String) = runBlocking {
        app.compatibilityStore.savePreferences(extensionKeys.associate { it to "ON" })
    }

    private fun openTutorialCatalog() {
        waitForTag("futaber-drawer")
        waitForTag("futaber-drawer-board")
        rule.onAllNodesWithTag("futaber-drawer-board")[0].performClick()
        waitForTag("futaber-catalog-item")
    }

    private fun openTutorialThread() {
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
    }

    private fun longPressText(text: String) {
        rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasText(text))
        rule.onNodeWithText(text).performTouchInput { longClick() }
        waitForTag("futaber-post-actions")
    }

    private fun longPressFirstPicture() {
        rule.onNodeWithTag("futaber-thread-list").performScrollToNode(hasTestTag("futaber-post-image"))
        rule.onAllNodesWithTag("futaber-post-image")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-image-search")
    }

    private fun clickMenu(id: String) {
        waitForTag("futaber-menu-$id")
        runCatching { rule.onNodeWithTag("futaber-menu-$id").performScrollTo() }
        rule.onNodeWithTag("futaber-menu-$id").performClick()
    }

    private fun closeSheet() {
        rule.onNodeWithTag("futaber-action-cancel").performClick()
        rule.waitUntil(5_000) { absent("futaber-post-actions") }
    }

    private fun listScroll(): Float {
        rule.waitForIdle()
        return rule.onNodeWithTag("futaber-thread-list").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f
    }

    private fun threadTitle(): String = rule.onNodeWithTag("futaber-thread-title").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()

    /** How many grid items fit across the picture list (its width over one item's). */
    private fun galleryColumns(): Int {
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("futaber-gallery-item").fetchSemanticsNodes().size >= 3 }
        val grid = rule.onNodeWithTag("futaber-gallery-grid").fetchSemanticsNode().size.width.toFloat()
        val item = rule.onAllNodesWithTag("futaber-gallery-item")[0].fetchSemanticsNode().size.width.toFloat()
        return Math.round(grid / item)
    }

    @Test fun byDefaultNoneOfTheNewEntriesIsThere() {
        runBlocking { app.appStateStore.setLastUsedDeleteKey("b1b-pass") }
        openTutorialCatalog()
        // The catalog sheet has no "画像NGに登録".
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-copy-url")
        assertTrue(absent("futaber-action-image-ng"))
        closeSheet()
        // The picture menu stays "画像検索 / 画像NGに登録".
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performClick()
        waitForTag("futaber-post")
        longPressFirstPicture()
        for (id in listOf("image-save", "image-share", "image-copy-url", "image-external", "image-viewer")) {
            assertTrue(id, absent("futaber-action-$id"))
        }
        closeSheet()
        // Deleting a post with a picture asks without "画像だけ消す".
        longPressText("テスト8")
        rule.onNodeWithTag("futaber-action-delete-own").performClick()
        waitForTag("futaber-remote-confirm")
        assertTrue(absent("futaber-delete-image-only"))
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(5_000) { absent("futaber-remote-confirm") }
        // The picture list keeps its three columns.
        rule.onNodeWithTag("futaber-thread-gallery").performClick()
        waitForTag("futaber-media")
        assertEquals(3, galleryColumns())
        Espresso.pressBack()
        rule.waitUntil(10_000) { absent("futaber-media") }
        // The sort is the one shared value.
        Espresso.pressBack()
        waitForTag("futaber-sort")
        rule.onNodeWithTag("futaber-sort").performClick()
        rule.onNodeWithTag("futaber-sort-New").performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.catalog.sort") == "New" }
        assertNull(preference("compat.futaber.catalog.sortByBoard"))
    }

    @Test fun theSettingsTurnTheNewSwitchesOnAndOfferTheVolumeKeyModes() {
        openTutorialCatalog()
        rule.onNodeWithTag("futaber-open-settings").performClick()
        waitForTag("futaber-settings")
        rule.onNodeWithTag("futaber-settings-row-extras").performScrollTo().performClick()
        waitForTag("futaber-settings-switch-ext-sort-by-board")
        val switches = mapOf(
            "ext-sort-by-board" to "compat.futaber.ext.sortByBoard",
            "ext-catalog-long-press" to "compat.futaber.ext.catalogLongPress",
            "ext-post-detail-ops" to "compat.futaber.ext.postDetailOps",
            "ext-image-menu" to "compat.futaber.ext.imageMenu",
            "ext-gallery-columns" to "compat.futaber.ext.galleryColumns"
        )
        for ((id, key) in switches) {
            assertNull(preference(key))
            rule.onNodeWithTag("futaber-settings-switch-$id").performScrollTo().performClick()
            rule.waitUntil(5_000) { preference(key) == "ON" }
        }
        // The modes appear under the volume keys' switch only while it is on; the first is what the keys did before.
        assertTrue(absent("futaber-settings-row-volume-key-thread"))
        rule.onNodeWithTag("futaber-settings-switch-ext-volume-keys").performScrollTo().performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.ext.volumeKeys") == "ON" }
        waitForTag("futaber-settings-row-volume-key-screen")
        rule.onNodeWithTag("futaber-settings-row-volume-key-thread").performScrollTo().performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.ext.volumeKeyMode") == "thread" }
        rule.onNodeWithTag("futaber-settings-row-volume-key-response").performScrollTo().performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.ext.volumeKeyMode") == "response" }
        rule.onNodeWithTag("futaber-settings-extras-note").performScrollTo()
            .assertTextContains("NGで隠したレス", substring = true)
    }

    @Test fun theVolumeKeysMoveAPostOrSwitchToTheNeighbouringTab() {
        runBlocking {
            app.compatibilityStore.savePreferences(
                mapOf("compat.futaber.ext.volumeKeys" to "ON", "compat.futaber.ext.volumeKeyMode" to "response")
            )
        }
        openTutorialThread()
        // An earlier visit may have left the reading position anywhere; start from the top.
        rule.onNodeWithTag("futaber-thread-list").performScrollToIndex(0)
        val start = listScroll()
        rule.runOnIdle { assertTrue(CompatVolumeKeyBus.dispatch(CompatVolumeKey.DOWN)) }
        rule.waitUntil(5_000) { listScroll() > start }
        rule.runOnIdle { assertTrue(CompatVolumeKeyBus.dispatch(CompatVolumeKey.UP)) }
        rule.waitUntil(5_000) { listScroll() <= start + 1f }

        // "タブのスレッドを切り替え": two tabs, the open thread first.
        val first = runBlocking { app.appStateStore.history.first().first() }
        val firstTitle = threadTitle()
        runBlocking {
            app.appStateStore.updateHistory { list ->
                list + first.copy(threadId = "999011", title = "となりのタブ", boardUrl = first.boardUrl.replace(first.threadId, "999011"))
            }
            app.compatibilityStore.savePreferences(
                mapOf(
                    "compat.futaber.ext.volumeKeyMode" to "thread",
                    "compat.futaber.tabs" to "[\"${first.boardId}\\n${first.threadId}\",\"${first.boardId}\\n999011\"]"
                )
            )
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-tab").fetchSemanticsNodes().size == 2 }
        // Up at the first tab: nothing to go back to, the key is still taken.
        rule.runOnIdle { assertTrue(CompatVolumeKeyBus.dispatch(CompatVolumeKey.UP)) }
        rule.waitForIdle()
        assertEquals(firstTitle, threadTitle())
        rule.runOnIdle { assertTrue(CompatVolumeKeyBus.dispatch(CompatVolumeKey.DOWN)) }
        rule.waitUntil(10_000) { threadTitle() == "となりのタブ" }
        rule.runOnIdle { assertTrue(CompatVolumeKeyBus.dispatch(CompatVolumeKey.UP)) }
        rule.waitUntil(10_000) { threadTitle() == firstTitle }
    }

    @Test fun theNgExtractListsOnlyThePostsNgHidesAlsoWithNgSwitchedOff() {
        turnOn("compat.futaber.ext.extract")
        runBlocking { app.appStateStore.setNgWords(listOf("テスト9")) }
        openTutorialThread()
        val list = rule.onNodeWithTag("futaber-thread-list")
        assertTrue(runCatching { list.performScrollToNode(hasText("テスト9")) }.isFailure)
        list.performScrollToIndex(0)
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        clickMenu("extract")
        waitForTag("futaber-action-extract-ng")
        rule.onNodeWithTag("futaber-action-extract-ng").performClick()
        waitForTag("futaber-filter-chip")
        rule.onNodeWithTag("futaber-filter-chip").assertTextContains("NGで隠したレス 1件", substring = true)
        rule.onNodeWithText("テスト9").assertIsDisplayed()
        assertEquals(1, rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().size)
        // NG switched off for the thread: the extract still lists the same post.
        rule.onNodeWithTag("futaber-thread-menu").performClick()
        waitForTag("futaber-operation-menu")
        clickMenu("ng-toggle")
        rule.waitUntil(5_000) { absent("futaber-operation-menu") }
        rule.onNodeWithTag("futaber-filter-chip").assertTextContains("NGで隠したレス 1件", substring = true)
        assertEquals(1, rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().size)
        // Clearing the chip shows the whole thread again (NG is still off here).
        rule.onNodeWithTag("futaber-filter-chip").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("futaber-post").fetchSemanticsNodes().size > 1 }
    }

    @Test fun deletingAPostWithAPictureCanDeleteOnlyThePicture() {
        turnOn("compat.futaber.ext.postDetailOps")
        runBlocking { app.appStateStore.setLastUsedDeleteKey("b1b-pass") }
        openTutorialThread()
        // A post without a picture: no check box.
        longPressText("テスト9")
        rule.onNodeWithTag("futaber-action-delete-own").performClick()
        waitForTag("futaber-remote-confirm")
        assertTrue(absent("futaber-delete-image-only"))
        rule.onNodeWithText("キャンセル").performClick()
        rule.waitUntil(5_000) { absent("futaber-remote-confirm") }
        // A post with a picture: "画像だけ消す" is offered and says what it did (the bundled board answers at once).
        longPressText("テスト8")
        rule.onNodeWithTag("futaber-action-delete-own").performClick()
        waitForTag("futaber-delete-image-only")
        rule.onNodeWithTag("futaber-delete-image-only").performClick()
        rule.onNodeWithTag("futaber-remote-confirm").performClick()
        waitForTag("futaber-remote-notice")
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("画像を削除しました").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun thePictureMenuGetsItsEntriesAndOpensTheViewer() {
        turnOn("compat.futaber.ext.imageMenu")
        openTutorialThread()
        longPressFirstPicture()
        rule.onNodeWithTag("futaber-action-image-save").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("futaber-action-image-share").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("futaber-action-image-external").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("futaber-action-image-copy-url").performScrollTo().performClick()
        rule.waitUntil(5_000) { absent("futaber-post-actions") }
        val copied = rule.runOnIdle {
            val clipboard = app.getSystemService(android.content.ClipboardManager::class.java)
            clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        }
        assertTrue(copied, copied.contains("/src/"))
        longPressFirstPicture()
        rule.onNodeWithTag("futaber-action-image-viewer").performScrollTo().performClick()
        waitForTag("futaber-viewer-info")
        rule.onNodeWithTag("futaber-viewer-counter").assertTextContains("1 of", substring = true)
        // Back goes to the picture list, then to the thread.
        Espresso.pressBack()
        rule.waitUntil(10_000) { rule.onAllNodesWithTag("futaber-gallery-item").fetchSemanticsNodes().isNotEmpty() }
        Espresso.pressBack()
        rule.waitUntil(10_000) { absent("futaber-media") }
        rule.onNodeWithTag("futaber-thread-list").assertIsDisplayed()
    }

    @Test fun theGalleryTakesTheSharedColumnCount() {
        turnOn("compat.futaber.ext.galleryColumns")
        runBlocking { app.compatibilityStore.savePreference("compat.viewer.galleryGridViewPortraitClmNum", "4") }
        openTutorialThread()
        rule.onNodeWithTag("futaber-thread-gallery").performClick()
        waitForTag("futaber-media")
        assertEquals(4, galleryColumns())
    }

    @Test fun theCatalogSheetOffersTheImageNgAndTheSortIsKeptPerBoard() {
        turnOn("compat.futaber.ext.catalogLongPress", "compat.futaber.ext.sortByBoard")
        openTutorialCatalog()
        rule.onAllNodesWithTag("futaber-catalog-item")[0].performTouchInput { longClick() }
        waitForTag("futaber-action-image-ng")
        // The tutorial board is not a board of the shared store, so the rule cannot be made for it.
        rule.onNodeWithTag("futaber-action-image-ng").performScrollTo().assertIsNotEnabled()
        closeSheet()
        val boardId = runBlocking { app.appStateStore.boards.first().first { it.url == B1B_TUTORIAL_URL }.id }
        rule.onNodeWithTag("futaber-sort").performClick()
        rule.onNodeWithTag("futaber-sort-Many").performClick()
        rule.waitUntil(5_000) { preference("compat.futaber.catalog.sortByBoard")?.contains("\"$boardId\":\"Many\"") == true }
        // The one shared value is left as it was.
        assertNull(preference("compat.futaber.catalog.sort"))
    }
}
