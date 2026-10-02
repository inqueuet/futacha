package com.valoser.futacha.shared.watch

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WatchSnapshotPayloadFittingTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun encode(snapshot: WatchSnapshot) = json.encodeToString(WatchSnapshot.serializer(), snapshot)

    private fun thread(id: String, previewChars: Int) = WatchThreadSummary(
        threadId = id,
        boardId = "b",
        boardName = "B",
        boardUrl = "https://may.2chan.net/b/",
        title = "title $id",
        thumbnailUrl = null,
        replyCount = 10,
        previousReplyCount = 8,
        newReplyCount = 2,
        lastVisitedEpochMillis = 1L,
        isWatchWordMatch = false,
        previewPosts = if (previewChars == 0) emptyList() else List(4) { WatchPostPreview("$id-$it", "あ".repeat(previewChars), null) }
    )

    private fun snapshot(threadCount: Int, previewChars: Int) = WatchSnapshot(
        generatedAtMillis = 1L,
        boards = listOf(WatchBoard("b", "B", "", "https://may.2chan.net/b/", false)),
        threads = List(threadCount) { thread("$it", previewChars) },
        watchWords = emptyList(),
        unreadTotal = threadCount * 2,
        watchMatchTotal = 0
    )

    @Test
    fun keepsSnapshotThatAlreadyFits() {
        val source = snapshot(threadCount = 2, previewChars = 10)
        assertEquals(encode(source), encodeWatchSnapshotWithinPayload(source, 60 * 1024, ::encode))
    }

    @Test
    fun trimsPreviewsBeforeDroppingThreads() {
        val source = snapshot(threadCount = 20, previewChars = 300)
        val limit = 60 * 1024
        val encoded = assertNotNull(encodeWatchSnapshotWithinPayload(source, limit, ::encode))
        assertTrue(encoded.encodeToByteArray().size <= limit)
        val decoded = json.decodeFromString(WatchSnapshot.serializer(), encoded)
        assertEquals(20, decoded.threads.size)
        assertTrue(decoded.threads.first().previewPosts.isNotEmpty())
        assertTrue(decoded.threads.last().previewPosts.isEmpty())
        assertEquals(1, decoded.boards.size)
    }

    @Test
    fun dropsThreadsWhenPreviewsAloneAreNotEnough() {
        val source = snapshot(threadCount = 50, previewChars = 0)
        val limit = 4 * 1024
        val encoded = assertNotNull(encodeWatchSnapshotWithinPayload(source, limit, ::encode))
        val decoded = json.decodeFromString(WatchSnapshot.serializer(), encoded)
        assertTrue(decoded.threads.size in 1 until 50)
        assertEquals(decoded.threads.size * 2, decoded.unreadTotal)
    }
}
