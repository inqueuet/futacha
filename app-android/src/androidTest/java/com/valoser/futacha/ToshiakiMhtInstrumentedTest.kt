package com.valoser.futacha

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.util.AndroidFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * MHT in としあき(仮): the save menu offers it, the file is written and listed in the saved-thread list, and
 * opening it makes a tab with the saved posts. No server is contacted (the mock engine answers 404 for pictures).
 */
class ToshiakiMhtInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "toshiaki_mht_${System.currentTimeMillis()}.db"
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var imageLoader: ImageLoader
    private var client: HttpClient? = null

    @Before
    fun prepare() {
        runBlocking {
            store = AndroidCompatibilityStore(context, databaseName = databaseName)
            store.initialize()
            store.savePreference("compat.commonUsedVersion", "1.0")
        }
        imageLoader = ImageLoader.Builder(context).build()
        // A previous run's files must not make the list look right by accident.
        java.io.File(context.filesDir, "futaber_mht").deleteRecursively()
    }

    @After
    fun cleanUp() {
        client?.close()
        context.deleteDatabase(databaseName)
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 10_000) {
        rule.waitUntil(timeoutMillis) {
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    @Test
    fun aThreadIsSavedAsMhtListedInTheSavedThreadsAndOpenedAsATab() {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        val threadUrl = "${boardUrl}res/9101.htm"
        val tabKey = compatTabKey(threadUrl)
        runBlocking {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
            store.openTab(
                CompatTab(
                    key = tabKey, canonicalUrl = threadUrl, originalUrl = threadUrl, boardKey = boardKey,
                    boardName = "mayb", threadNo = "9101", title = "MHT保存の確認", replyCount = 1,
                    insertedAtEpochMillis = 1L, contentUpdatedAtEpochMillis = 1L, snapshotRevision = 1L
                )
            )
            store.saveThreadSnapshot(
                CompatThreadSnapshot(
                    tabKey = tabKey, revision = 1L, fetchedAtEpochMillis = 1L,
                    posts = listOf(
                        CompatPostSnapshot(
                            position = 0, postNo = "9101", timestamp = "08/27 11:30",
                            messageHtml = "MHTに保存する本文その一",
                            imageUrl = "https://may.2chan.net/b/src/9101.jpg",
                            thumbnailUrl = "https://may.2chan.net/b/thumb/9101s.jpg"
                        ),
                        CompatPostSnapshot(position = 1, postNo = "9102", timestamp = "08/27 11:31", messageHtml = "本文その二")
                    )
                )
            )
        }
        val http = HttpClient(MockEngine { respond("", HttpStatusCode.NotFound) }).also { client = it }
        val files = AndroidFileSystem(context)
        rule.setContent {
            CompositionLocalProvider(LocalFutachaImageLoader provides imageLoader) {
                MaterialTheme {
                    CompatibilityApp(
                        store = store, repository = null, httpClient = http,
                        fileSystem = files, savedThreadRepository = SavedThreadRepository(files),
                        initialThreadDeepLink = threadUrl,
                        onExitApplication = {}
                    )
                }
            }
        }

        waitForTag("compat-thread-pager")
        waitForTag("compat-thread-post-9101")
        rule.onNodeWithTag("compat-toolbar-command-other").performSemanticsAction(SemanticsActions.OnClick)
        waitForText("ページを保存")
        rule.onNodeWithText("ページを保存", substring = true).performClick()
        rule.onNodeWithText("MHT(1ファイル・サムネイル)").assertIsDisplayed()
        rule.onNodeWithText("MHT(1ファイル・全画像)").assertIsDisplayed()
        rule.onNodeWithText("MHT(1ファイル・サムネイル)").performClick()
        waitForText("MHTの保存が完了しました", 20_000)
        rule.onNodeWithTag("futacha-mht-close").performClick()

        // Back to the board list; then the tab and its cached posts go away, so that what the file opens is
        // the only copy of the thread.
        androidx.test.espresso.Espresso.pressBack()
        waitForTag("compat-board-list")
        runBlocking {
            store.closeTabs(setOf(tabKey), 2L)
            store.clearThreadSnapshotCache()
        }
        org.junit.Assert.assertNull(runBlocking { store.loadThreadSnapshot(tabKey) })

        rule.onNodeWithContentDescription("その他").performClick()
        rule.onNodeWithText("保存済みスレッド").performClick()
        waitForTag("futacha-mht-row")
        rule.onNodeWithTag("futacha-mht-row").assertIsDisplayed()
        rule.onAllNodesWithText("MHT保存の確認", substring = true).onFirst().assertIsDisplayed()
        rule.onNodeWithTag("futacha-mht-row").performClick()

        waitForTag("compat-thread-post-9101")
        waitForText("MHTに保存する本文その一")
        val opened = runBlocking { store.loadThreadSnapshot(tabKey) }
        org.junit.Assert.assertNotNull(opened)
        org.junit.Assert.assertEquals(listOf("9101", "9102"), opened!!.posts.map { it.postNo })
    }
}
