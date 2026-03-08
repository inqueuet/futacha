package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.SaveStatus
import com.valoser.futacha.shared.model.SavedThread
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SavedThreadsScreenTest {
    @Test
    fun formatSize_formatsByteRanges() {
        assertEquals("999 B", formatSize(999))
        assertEquals("2 KB", formatSize(2 * 1024L))
        assertEquals("3 MB", formatSize(3 * 1024L * 1024L))
        assertEquals("1.50 GB", formatSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatDecimal_padsFractionDigits() {
        assertEquals("1.50", formatDecimal(1.5, 2))
        assertEquals("2.00", formatDecimal(2.0, 2))
    }

    @Test
    fun savedThreadStatusLabel_returnsExpectedLabels() {
        assertEquals("ダウンロード中", savedThreadStatusLabel(SaveStatus.DOWNLOADING))
        assertEquals("完了", savedThreadStatusLabel(SaveStatus.COMPLETED))
        assertEquals("失敗", savedThreadStatusLabel(SaveStatus.FAILED))
        assertEquals("一部", savedThreadStatusLabel(SaveStatus.PARTIAL))
    }

    @Test
    fun formatDate_returnsPaddedTimestamp() {
        val value = formatDate(0L)

        assertTrue(value.matches(Regex("""\d{4}/\d{2}/\d{2} \d{2}:\d{2}""")))
    }

    @Test
    fun resolveSavedThreadsContentState_prioritizesLoadingThenErrorThenEmptyThenData() {
        assertIs<SavedThreadsContentState.Loading>(
            resolveSavedThreadsContentState(
                isLoading = true,
                loadError = "err",
                threads = listOf(savedThread())
            )
        )
        assertIs<SavedThreadsContentState.Error>(
            resolveSavedThreadsContentState(
                isLoading = false,
                loadError = "err",
                threads = listOf(savedThread())
            )
        )
        assertIs<SavedThreadsContentState.Empty>(
            resolveSavedThreadsContentState(
                isLoading = false,
                loadError = null,
                threads = emptyList()
            )
        )
        assertIs<SavedThreadsContentState.Data>(
            resolveSavedThreadsContentState(
                isLoading = false,
                loadError = null,
                threads = listOf(savedThread())
            )
        )
    }

    @Test
    fun buildSavedThreadsSummaryText_hidesWhileLoading() {
        assertEquals(null, buildSavedThreadsSummaryText(threadCount = 1, totalSize = 10L, isLoading = true))
        assertEquals("2 件 / 2 KB", buildSavedThreadsSummaryText(threadCount = 2, totalSize = 2048L, isLoading = false))
    }

    @Test
    fun buildSavedThreadsLoadErrorMessage_mapsTimeoutSeparately() {
        val timeoutError = runCatching {
            runBlocking {
                withTimeout(1) {
                    delay(10)
                }
            }
        }.exceptionOrNull() ?: error("timeout expected")

        assertEquals(
            "読み込みがタイムアウトしました",
            buildSavedThreadsLoadErrorMessage(timeoutError)
        )
        assertEquals(
            "読み込みエラー: boom",
            buildSavedThreadsLoadErrorMessage(IllegalStateException("boom"))
        )
    }

    @Test
    fun buildSavedThreadsDeleteMessage_mapsSuccessAndFailure() {
        assertEquals("削除しました", buildSavedThreadsDeleteMessage(Result.success(Unit)))
        assertEquals(
            "削除に失敗しました: boom",
            buildSavedThreadsDeleteMessage(Result.failure(IllegalStateException("boom")))
        )
    }

    private fun savedThread(): SavedThread {
        return SavedThread(
            threadId = "1",
            boardId = "b",
            boardName = "board",
            title = "title",
            thumbnailPath = null,
            savedAt = 0L,
            postCount = 1,
            imageCount = 0,
            videoCount = 0,
            totalSize = 1L,
            status = SaveStatus.COMPLETED
        )
    }
}
