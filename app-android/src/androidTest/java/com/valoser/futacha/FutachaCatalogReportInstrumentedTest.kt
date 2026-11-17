@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.IosReviewCompliance
import com.valoser.futacha.shared.ui.LocalIosReviewCompliance
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FutachaCatalogReportInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val database = "audit_catalog_report.db"
    private val board = BoardSummary("may", "試験用の板", "", "https://may.2chan.net/b/", "")
    private val item = CatalogItem("123456", "https://may.2chan.net/b/res/123456.htm", "確認対象のスレ", null, null, replyCount = 5)
    private val calls = ConcurrentLinkedQueue<List<String>>()
    private val release = CompletableDeferred<Unit>()
    private val fail = AtomicBoolean(false)
    private val longPress = AtomicReference<((CatalogItem) -> Unit)?>(null)
    private val observedMode = AtomicReference<String?>(null)
    private val repository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun requestDeletion(board: String, threadId: String, postId: String, reasonCode: String) {
            calls.add(listOf(board, threadId, postId, reasonCode))
            release.await()
            if (fail.get()) error("試験用の送信失敗")
        }
    }

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        release.complete(Unit)
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun host(review: Boolean = false, direct: Boolean = false) {
        if (direct) runBlocking { store.savePreference(compatPreferenceStorageKey("control", "controlCatalogLongTap"), "del") }
        rule.setContent {
            FutachaTheme(ThemeMode.Light, ThemePalette.Current) {
                CompositionLocalProvider(LocalFutachaImageLoader provides loader,
                    LocalIosReviewCompliance provides IosReviewCompliance(review)) {
                    ProvideFutachaSharedFeatures(store, null, repository, null, null, "test") {
                        FutachaCatalogFeatureHost(board, CatalogMode.Catalog,
                            CatalogUiState.Success(CatalogPageContent(listOf(item))), repository, {}, {}, {}, {}, {}, {}) {
                            longPress.set(LocalFutachaCatalogLongPress.current)
                            observedMode.set(LocalFutachaSharedFeatures.current?.value("control", "controlCatalogLongTap"))
                            TextButton(onClick = { longPress.get()?.invoke(item) }) { Text("長押しを開く") }
                        }
                    }
                }
            }
        }
        rule.waitUntil(10_000) { longPress.get() != null && (!direct || observedMode.get() == "del") }
    }

    private fun openMenu() { rule.onNodeWithText("長押しを開く").performClick() }
    private fun awaitCalls(count: Int) { rule.waitUntil(10_000) { calls.size >= count } }

    @Test fun menuRequiresConfirmationCancelSendsNothingAndDoubleConfirmSendsOnce() {
        host()
        openMenu()
        rule.onNodeWithText("DEL依頼を送信").performClick()
        rule.onNodeWithText("削除依頼 No.123456").assertIsDisplayed()
        assertTrue(calls.isEmpty())
        rule.onNodeWithText("キャンセル").performClick()
        assertTrue(calls.isEmpty())
        openMenu()
        rule.onNodeWithText("DEL依頼を送信").performClick()
        val confirm = rule.onNodeWithText("通報する").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        rule.runOnIdle { confirm(); confirm() }
        awaitCalls(1)
        rule.waitForIdle()
        assertEquals(1, calls.size)
        assertEquals(listOf(board.url, item.id, item.id, "110"), calls.single())
        rule.onNodeWithText("DEL依頼を送信").assertDoesNotExist()
        release.complete(Unit)
        rule.onNodeWithText("DEL依頼を送信しました").assertIsDisplayed()
    }

    @Test fun configuredDirectDelRemainsImmediateButDoesNotSendTwiceWhilePending() {
        host(direct = true)
        rule.runOnIdle { longPress.get()!!.invoke(item); longPress.get()!!.invoke(item) }
        awaitCalls(1)
        rule.waitForIdle()
        assertEquals(1, calls.size)
        rule.onNodeWithText("削除依頼 No.123456").assertDoesNotExist()
        release.complete(Unit)
        rule.onNodeWithText("DEL依頼を送信しました").assertIsDisplayed()
    }

    @Test fun reviewPolicyUsesReportWordingInTheMenuConfirmationAndSuccess() {
        host(review = true)
        openMenu()
        rule.onNodeWithText("不適切な投稿を通報").performClick()
        rule.onNodeWithText("不適切な投稿を通報").assertIsDisplayed()
        // L137: the body says what the button sends, not "削除依頼".
        rule.onNodeWithText("No.123456 を不適切な投稿として、ふたば☆ちゃんねるの掲示板管理者へ通報します。").assertIsDisplayed()
        rule.onNodeWithText("削除依頼します", substring = true).assertDoesNotExist()
        assertTrue(calls.isEmpty())
        rule.onNodeWithText("通報する").performClick()
        awaitCalls(1)
        release.complete(Unit)
        rule.onNodeWithText("通報しました").assertIsDisplayed()
    }

    @Test fun aFailedRequestDoesNotRetryAutomaticallyAndAllowsAnExplicitRetry() {
        fail.set(true)
        host()
        openMenu()
        rule.onNodeWithText("DEL依頼を送信").performClick()
        rule.onNodeWithText("通報する").performClick()
        awaitCalls(1)
        release.complete(Unit)
        rule.onNodeWithText("試験用の送信失敗").assertIsDisplayed()
        assertEquals(1, calls.size)
        rule.onNodeWithText("閉じる").performClick()
        fail.set(false)
        openMenu()
        rule.onNodeWithText("DEL依頼を送信").performClick()
        assertEquals(1, calls.size)
        rule.onNodeWithText("通報する").performClick()
        awaitCalls(2)
        rule.onNodeWithText("DEL依頼を送信しました").assertIsDisplayed()
    }
}
