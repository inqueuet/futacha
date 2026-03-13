package com.valoser.futacha

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.ui.board.SavedThreadsScreen
import com.valoser.futacha.shared.util.createFileSystem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SavedThreadsScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repository: SavedThreadRepository
    private lateinit var baseDirectory: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        baseDirectory = "android_test_saved_threads_${System.currentTimeMillis()}"
        repository = SavedThreadRepository(
            fileSystem = createFileSystem(context),
            baseDirectory = baseDirectory
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            repository.deleteAllThreads()
        }
    }

    @Test
    fun savedThreadsScreen_showsSavedThreadAndDeletesIt() {
        runBlocking {
            repository.addThreadToIndex(
                SavedThread(
                    threadId = "123",
                    boardId = "img",
                    boardName = "img",
                    title = "保存テスト",
                    thumbnailPath = null,
                    savedAt = 1L,
                    postCount = 10,
                    imageCount = 1,
                    videoCount = 0,
                    totalSize = 2048L,
                    status = SaveStatus.COMPLETED
                )
            ).getOrThrow()
        }

        rule.setContent {
            MaterialTheme {
                SavedThreadsScreen(
                    repository = repository,
                    onThreadClick = {},
                    onBack = {}
                )
            }
        }

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("保存テスト").fetchSemanticsNodes().isNotEmpty()
        }

        rule.onNodeWithText("保存済みスレッド").assertIsDisplayed()
        rule.onNodeWithText("保存テスト").assertIsDisplayed()
        rule.onNodeWithText("img").assertIsDisplayed()
        rule.onNodeWithText("1 件 / 2 KB").assertIsDisplayed()

        rule.onNodeWithContentDescription("削除").performClick()
        rule.onNodeWithText("スレッドを削除").assertIsDisplayed()
        rule.onNodeWithText("削除").performClick()

        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("保存済みスレッドがありません").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("保存済みスレッドがありません").assertIsDisplayed()
    }
}
