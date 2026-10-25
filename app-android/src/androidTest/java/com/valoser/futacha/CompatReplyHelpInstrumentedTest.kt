@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import coil3.ColorImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.ImageData
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Full CompatibilityApp reply/build navigation, isolated drafts and fake images/repository; no POST or external opens. */
class CompatReplyHelpInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val database = "compat_reply_help_test.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val threadUrl = "${boardUrl}res/123.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(threadUrl)
    private val fs by lazy { createFileSystem(rule.activity) }
    private var locator: String? = null
    @Volatile private var blockDraftWrites = false
    private val releaseDraftWrites = CompletableDeferred<Unit>()
    private val body = "ヘルプを開く直前に変えた本文"
    private val png = android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jN1sAAAAASUVORK5CYII=", 0)

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking {
            store.initialize()
            store.savePreference("compat.commonUsedVersion", "999.0")
            store.upsertBoard(CompatBoard(boardKey, "ヘルプ検証板", boardUrl, boardUrl, 0))
            store.openTab(CompatTab(tabKey, threadUrl, threadUrl, boardKey, "ヘルプ検証板", "123", "返信ヘルプ",
                insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1))
            store.saveToolbar(CompatToolbarSurface.THREAD,
                compatToolbarMaster(CompatToolbarSurface.THREAD).mapIndexed { index, item ->
                    CompatToolbarItem(item.key, index, item.key == "post")
                })
            locator = persistCompatPostAttachment(fs, "reply-help-fixture", ImageData(png, "retained_help.png")).getOrThrow()
            store.saveDraft(CompatReplyDraft(tabKey, comment = "元の本文", attachmentUri = locator,
                deleteKey = "KEEP1234", updatedAtEpochMillis = 1))
            store.saveBuildDraft(CompatBuildDraft(boardKey, comment = "スレ立ての本文", attachmentUri = locator,
                deleteKey = "KEEP1234", updatedAtEpochMillis = 1))
        }
        loader = ImageLoader.Builder(rule.activity).components {
            add(Interceptor { chain -> SuccessResult(ColorImage(android.graphics.Color.GREEN, 16, 16), chain.request) })
        }.build()
    }

    @After fun cleanup() {
        releaseDraftWrites.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        if (::loader.isInitialized) loader.shutdown()
        if (::store.isInitialized) runBlocking {
            locator?.let { deleteCompatPostAttachment(fs, it, deleteContainer = true) }
            store.closeForTest()
        }
        rule.activity.deleteDatabase(database)
    }

    private fun openApp(reply: Boolean) {
        val repository = object : BoardRepository by FakeBoardRepository() {
            private fun content() = ThreadPageContent(ThreadPage("123", "返信ヘルプ", null, null,
                listOf(Post("123", 0, null, null, "", messageHtml = "REPLY HELP FIXTURE", imageUrl = null, thumbnailUrl = null))))
            // CompatibilityApp's thread loader calls getThreadByUrl (not the modern content API).
            override suspend fun getThread(board: String, threadId: String) = content().page
            override suspend fun getThreadByUrl(threadUrl: String) = content().page
            override suspend fun getThreadContent(board: String, threadId: String) = content()
            override suspend fun getThreadContentByUrl(threadUrl: String) = content()
        }
        val observedStore = object : CompatibilityStore by store {
            override suspend fun saveDraft(draft: CompatReplyDraft) {
                if (blockDraftWrites) releaseDraftWrites.await()
                store.saveDraft(draft)
            }
        }
        rule.setContent { MaterialTheme {
            CompositionLocalProvider(LocalExperienceProfileUiController provides
                ExperienceProfileUiController(activeProfile = ExperienceProfile.TOSHIAKI_COMPAT)) {
                CompatibilityApp(store = observedStore, repository = repository, fileSystem = fs,
                    imageLoader = loader, catalogImageLoader = loader, appVersion = "13.1",
                    initialThreadDeepLink = threadUrl.takeIf { reply }, onExitApplication = {})
            }
        } }
        if (reply) {
            waitText("REPLY HELP FIXTURE")
            rule.onNodeWithContentDescription("書き込み").performClick()
        } else {
            rule.waitUntil(10_000) { rule.onAllNodesWithText(boardUrl).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText(boardUrl).performClick()
            rule.onNodeWithContentDescription("スレ立て").performClick()
        }
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-post-attachment-file-name").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitText(text: String) = rule.waitUntil(10_000) {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openHelpMenu() {
        val options = rule.onAllNodesWithContentDescription("その他").fetchSemanticsNodes()
        val index = options.indices.minBy { options[it].boundsInRoot.top }
        rule.onAllNodesWithContentDescription("その他")[index].performClick()
        rule.onNodeWithText("ヘルプ").performClick()
    }

    @Test fun replyHelpWaitsForLatestDraftThenGuideAndReportReturnToTheReplyWithAttachment() {
        openApp(reply = true)
        blockDraftWrites = true
        rule.onNodeWithTag("compat-post-comment-field").performTextReplacement(body)
        openHelpMenu()
        rule.onNodeWithTag("compat-post-waiting-dialog").assertIsDisplayed()
        rule.onNodeWithTag("help-operation-guide").assertDoesNotExist()
        Espresso.pressBack()
        rule.onNodeWithTag("compat-post-waiting-dialog").assertIsDisplayed()
        releaseDraftWrites.complete(Unit)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("help-operation-guide").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(body, runBlocking { store.loadDraft(tabKey) }?.comment)
        rule.onNodeWithTag("help-operation-guide").performClick()
        rule.onNodeWithText("操作ガイド：${ExperienceProfile.TOSHIAKI_COMPAT.displayName}").assertIsDisplayed()
        rule.onNodeWithTag("operation-guide-attach").performScrollTo().assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithTag("help-report-bug").performClick()
        rule.onNodeWithTag("bug-report-steps").assertIsDisplayed()
        // Inspect the report entry only; never invoke mail, external URLs or send a post.
        Espresso.pressBack()
        Espresso.pressBack()
        assertRestored(body)
        runBlocking {
            val draft = checkNotNull(store.loadDraft(tabKey))
            assertEquals(locator, draft.attachmentUri)
            assertEquals("KEEP1234", draft.deleteKey)
            assertArrayEquals(png, loadCompatPostAttachment(fs, checkNotNull(draft.attachmentUri)).getOrThrow().bytes)
        }
    }

    @Test fun buildHelpStillReturnsToTheExistingDraftAndAttachment() {
        openApp(reply = false)
        openHelpMenu()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("help-operation-guide").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("help-operation-guide").performClick()
        rule.onNodeWithTag("operation-guide-write").assertIsDisplayed()
        Espresso.pressBack()
        Espresso.pressBack()
        assertRestored("スレ立ての本文")
    }

    private fun assertRestored(comment: String) {
        rule.waitUntil(5_000) {
            rule.onAllNodesWithTag("compat-post-comment-field").fetchSemanticsNodes().any {
                it.config[SemanticsProperties.EditableText].text == comment
            }
        }
        rule.onNodeWithTag("compat-post-comment-field").assertTextContains(comment)
        rule.onNodeWithTag("compat-post-attachment-file-name").performScrollTo().assertTextContains("retained_help.png")
    }
}
