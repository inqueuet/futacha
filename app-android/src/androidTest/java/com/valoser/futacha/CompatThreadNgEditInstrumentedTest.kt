package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.compat.CompatibilityApp
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Exercises the production thread NG manager; all content is local. */
class CompatThreadNgEditInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val db = "audit_m15_ng_edit.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val url = "${boardUrl}res/100.htm"
    private val boardKey = compatBoardKey(boardUrl)
    private val tabKey = compatTabKey(url)
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    @Before fun prepare() {
        rule.activity.deleteDatabase(db)
        store = AndroidCompatibilityStore(rule.activity, databaseName = db)
        loader = ImageLoader.Builder(rule.activity).build()
        runBlocking {
            store.initialize(); store.savePreference("compat.commonUsedVersion", "1.0"); store.upsertBoard(CompatBoard(boardKey, "板", boardUrl, boardUrl, 0))
            store.openTab(CompatTab(tabKey, url, url, boardKey, "板", "100", "NG編集の検証", insertedAtEpochMillis = 1, contentUpdatedAtEpochMillis = 1))
        }
    }
    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown(); runBlocking { store.closeForTest() }; rule.activity.deleteDatabase(db)
    }
    private fun post(id: String) = Post(id, author = null, subject = null, timestamp = "", messageHtml = "本文$id", imageUrl = null, thumbnailUrl = null)
    private fun open(kind: CompatNgKind, scope: String, value: String, memo: String = "") {
        runBlocking { assertTrue(store.upsertNgRule(CompatNgRule("seed", kind, scope, value, 1, memo = memo))) }
        val page = ThreadPage("100", null, null, null, listOf(post("100"), post("101").copy(posterId = "AbCd"), post("102").copy(posterId = "XyZ")))
        val repo = object : BoardRepository by FakeBoardRepository() {
            override suspend fun getThreadByUrl(threadUrl: String) = page
        }
        rule.setContent { MaterialTheme { CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
            CompatibilityApp(store, repository = repo, initialThreadDeepLink = url, onExitApplication = {})
        } } }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-thread-pager").fetchSemanticsNodes().isNotEmpty() }
        rule.waitUntil(15_000) { rule.onAllNodesWithTag("compat-thread-post-100").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("compat-toolbar-command-other", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithText("NG管理", substring = true).performClick()
        rule.onNodeWithText("NGヘッダー").performClick()
        rule.onNodeWithTag("compat-thread-refuse-row-seed", useUnmergedTree = true).performClick()
    }
    private fun saved(): CompatNgRule {
        rule.waitUntil(5_000) { runBlocking { store.ngRules.first().let { it.size == 1 && it.single().id != "seed" } } }
        return runBlocking { store.ngRules.first().single() }
    }
    @Test fun changingOnlyTheScopeKeepsPostNumberKindValueAndMemo() {
        open(CompatNgKind.THREAD_POST_NO, "*", "101", "残すメモ")
        rule.onNode(isToggleable()).assertIsOff().performClick()
        rule.onNodeWithText("更新する").performClick()
        val updated = saved()
        assertEquals(CompatNgKind.THREAD_POST_NO, updated.kind)
        assertEquals("101", updated.normalizedValue)
        assertEquals("残すメモ", updated.memo)
        assertEquals(tabKey, updated.scopeKey)
    }
    @Test fun aReportedPostNumberKeepsItsUrlScopeAfterEditing() {
        open(CompatNgKind.THREAD_POST_NO, url, "101")
        rule.onNodeWithTag("compat-thread-refuse-edit-word", useUnmergedTree = true).performTextReplacement("No.102")
        rule.onNodeWithText("更新する").performClick()
        val updated = saved()
        assertEquals(CompatNgKind.THREAD_POST_NO, updated.kind)
        assertEquals("102", updated.normalizedValue)
        assertEquals(url, updated.scopeKey)
        assertEquals(setOf("102"), buildCompatThreadNgRuleIndex(listOf(updated), tabKey, boardKey).postNos)
    }
    @Test fun editingAnIdKeepsTheIdentityKindAndCase() {
        open(CompatNgKind.THREAD_POSTER_ID, "*", "ID:AbCd")
        rule.onNodeWithTag("compat-thread-refuse-edit-word", useUnmergedTree = true).performTextReplacement("ID:XyZ")
        rule.onNodeWithText("更新する").performClick()
        val updated = saved()
        assertEquals(CompatNgKind.THREAD_POSTER_ID, updated.kind)
        assertEquals("ID:XyZ", updated.normalizedValue)
        assertEquals(setOf("ID:XyZ"), buildCompatThreadNgRuleIndex(listOf(updated), tabKey, boardKey).posterIds)
    }
    @Test fun aLongIpv6IdentityIsNotCutToTwentyCharacters() {
        open(CompatNgKind.THREAD_POSTER_ID, "*", "IP:2001:db8:1234:5678::1")
        val replacement = "IP:2001:db8:1234:5678::2"
        rule.onNodeWithTag("compat-thread-refuse-edit-word", useUnmergedTree = true).performTextReplacement(replacement)
        rule.onNodeWithText("更新する").performClick()
        assertEquals(replacement, saved().normalizedValue)
    }
    @Test fun anInvalidPostNumberDoesNotReplaceOrRemoveTheExistingRule() {
        open(CompatNgKind.THREAD_POST_NO, url, "101")
        rule.onNodeWithTag("compat-thread-refuse-edit-word", useUnmergedTree = true).performTextReplacement("No.words")
        rule.onNodeWithText("更新する").performClick()
        rule.onNodeWithText("レス番号を入力してください").assertIsDisplayed()
        assertEquals("seed", runBlocking { store.ngRules.first().single().id })
        assertEquals("101", runBlocking { store.ngRules.first().single().normalizedValue })
    }
}
