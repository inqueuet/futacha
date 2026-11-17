@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")

package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import coil3.ImageLoader
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.ThreadPageContent
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.ui.board.FutachaArchiveReportSettingRow
import com.valoser.futacha.shared.ui.board.ProvideFutachaSharedFeatures
import com.valoser.futacha.shared.ui.board.ScreenPreferencesState
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.model.ThemeMode
import com.valoser.futacha.shared.model.ThemePalette
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * ふたちゃの閲覧スレ通知（2026-10-12、既定ON）。テスト用データベースの送信待ち（outbox）に入るところまでを確かめる。
 * スレッドは模擬リポジトリで読み、ホストの送信処理（Worker）は呼ばず、実サーバー・外部サービスへは何も送らない。
 */
class FutachaArchiveReportInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: AndroidCompatibilityStore
    private lateinit var loader: ImageLoader
    private val database = "futacha_archive_report_ui_test.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val enqueued = java.util.Collections.synchronizedList(mutableListOf<Int>())
    private val switched = java.util.Collections.synchronizedList(mutableListOf<Boolean>())

    @Before fun prepare() {
        rule.activity.deleteDatabase(database)
        store = AndroidCompatibilityStore(rule.activity, databaseName = database)
        runBlocking { store.initialize() }
        loader = ImageLoader(rule.activity)
    }

    @After fun cleanup() {
        rule.runOnUiThread { rule.activity.setContent {} }
        loader.shutdown()
        runBlocking { store.closeForTest() }
        rule.activity.deleteDatabase(database)
    }

    private fun page() = ThreadPage("1001", "閲覧スレ通知", null, null, (1..2).map { index ->
        Post("${1000 + index}", index - 1, "としあき", null, "10/12 12:00",
            messageHtml = "REPORT-$index", imageUrl = null, thumbnailUrl = null)
    })

    private val pageRepository = object : BoardRepository by FakeBoardRepository() {
        override suspend fun getThread(board: String, threadId: String) = page()
        override suspend fun getThreadByUrl(threadUrl: String) = page()
        override suspend fun getThreadContent(board: String, threadId: String) = ThreadPageContent(page())
        override suspend fun getThreadContentByUrl(threadUrl: String) = ThreadPageContent(page())
    }

    private fun host(content: @Composable () -> Unit) {
        rule.setContent { FutachaTheme(ThemeMode.Light, ThemePalette.Current) {
            CompositionLocalProvider(LocalFutachaImageLoader provides loader) {
                ProvideFutachaSharedFeatures(
                    store, null, pageRepository, null, null, "test",
                    onArchiveReportEnqueued = { enqueued += it },
                    onArchiveReportEnabledChanged = { switched += it }
                ) { content() }
            }
        } }
    }

    private fun openThread() {
        host {
            ThreadScreen(board = BoardSummary("report", "may/b", "test", boardUrl, ""), history = emptyList(),
                threadId = "1001", threadTitle = "閲覧スレ通知", initialReplyCount = 2, repository = pageRepository,
                preferencesState = ScreenPreferencesState("test"), onBack = {})
        }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("REPORT-1", substring = true).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun aThreadReadFromTheBoardIsNotedWithNothingStored() {
        openThread()
        rule.waitUntil(10_000) { runBlocking { store.archiveReportOutboxStats() }.pendingOrRetry == 1 }
        rule.waitUntil(5_000) { enqueued.isNotEmpty() }
        assertEquals(1, enqueued.first())
    }

    @Test fun nothingIsNotedWithTheSharedSwitchOff() {
        runBlocking { store.savePreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY, "OFF") }
        openThread()
        Thread.sleep(2_000)
        rule.waitForIdle()
        assertEquals(0, runBlocking { store.archiveReportOutboxStats() }.total)
        assertTrue(enqueued.isEmpty())
    }

    @Test fun theSettingsRowWritesTheSharedKeyAndTellsTheHost() {
        host { FutachaArchiveReportSettingRow() }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("過去ログ保存サービスへ通知", substring = true).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("futacha-archive-report-switch").performClick()
        rule.waitUntil(5_000) { runBlocking { store.loadPreference(ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY) } == "OFF" }
        rule.waitUntil(5_000) { switched.isNotEmpty() }
        assertEquals(listOf(false), switched.toList())
    }
}
