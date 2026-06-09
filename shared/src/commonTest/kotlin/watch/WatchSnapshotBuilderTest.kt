package com.valoser.futacha.shared.watch

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WatchSnapshotBuilderTest {
    @Test
    fun build_sortsBoardsAndThreadsAndComputesCounts() {
        val boards = listOf(
            board(id = "b", name = "B", pinned = false),
            board(id = "a", name = "A", pinned = true)
        )
        val older = history(
            threadId = "100",
            boardId = "b",
            title = "雑談",
            replyCount = 10,
            lastVisited = 1_000L
        )
        val newer = history(
            threadId = "200",
            boardId = "a",
            title = "猫スレ",
            replyCount = 20,
            lastVisited = 2_000L
        )
        val previousReplyCounts = mapOf(
            newer.toWatchThreadKey() to 18,
            older.toWatchThreadKey() to 10
        )

        val snapshot = WatchSnapshotBuilder(nowMillis = { 9_999L }).build(
            boards = boards,
            history = listOf(older, newer),
            watchWords = listOf(" 猫 ", "猫"),
            previousReplyCounts = previousReplyCounts
        )

        assertEquals(9_999L, snapshot.generatedAtMillis)
        assertEquals(listOf("a", "b"), snapshot.boards.map { it.id })
        assertEquals(listOf("200", "100"), snapshot.threads.map { it.threadId })
        assertEquals(listOf("猫"), snapshot.watchWords)
        assertEquals(2, snapshot.unreadTotal)
        assertEquals(1, snapshot.watchMatchTotal)
        assertTrue(snapshot.threads.first().isWatchWordMatch)
        assertFalse(snapshot.threads.last().isWatchWordMatch)
    }

    @Test
    fun build_usesBoardSnapshotAsDisplaySourceWhenAvailable() {
        val board = board(id = "b", name = "最新板名", url = "https://may.2chan.net/b/")
        val entry = history(
            threadId = "100",
            boardId = "b",
            boardName = "古い板名",
            boardUrl = "https://old.example/b/",
            title = "本文",
            replyCount = 1
        )

        val thread = WatchSnapshotBuilder(nowMillis = { 1L }).build(
            boards = listOf(board),
            history = listOf(entry),
            watchWords = emptyList()
        ).threads.single()

        assertEquals("最新板名", thread.boardName)
        assertEquals("https://may.2chan.net/b/", thread.boardUrl)
    }

    @Test
    fun build_buildsLatestPlainTextPreviewPosts() {
        val entry = history(threadId = "100", boardId = "b", title = "title", replyCount = 4)
        val page = ThreadPage(
            threadId = "100",
            boardTitle = null,
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(
                post("1", "<b>古い</b>"),
                post("2", "A&amp;B<br>二行目"),
                post("3", "削除", isDeleted = true),
                post("4", "長い本文です".repeat(20))
            )
        )

        val snapshot = WatchSnapshotBuilder(
            maxPreviewPosts = 2,
            maxPreviewTextLength = 12,
            nowMillis = { 1L }
        ).build(
            boards = listOf(board(id = "b")),
            history = listOf(entry),
            watchWords = emptyList(),
            threadPages = mapOf(entry.toWatchThreadKey() to page)
        )

        val previews = snapshot.threads.single().previewPosts
        assertEquals(listOf("2", "4"), previews.map { it.postId })
        assertEquals("A&B 二行目", previews.first().text)
        assertTrue(previews.last().text.endsWith("..."))
        assertEquals(15, previews.last().text.length)
    }

    @Test
    fun build_limitsThreadsAndHandlesMissingPreviousCountsAsZeroNewReplies() {
        val entries = (1..3).map { index ->
            history(
                threadId = index.toString(),
                boardId = "b",
                title = "title-$index",
                replyCount = index,
                lastVisited = index.toLong()
            )
        }

        val snapshot = WatchSnapshotBuilder(
            maxThreads = 2,
            nowMillis = { 1L }
        ).build(
            boards = listOf(board(id = "b")),
            history = entries,
            watchWords = emptyList()
        )

        assertEquals(listOf("3", "2"), snapshot.threads.map { it.threadId })
        assertEquals(0, snapshot.unreadTotal)
        assertEquals(listOf(0, 0), snapshot.threads.map { it.newReplyCount })
    }

    @Test
    fun build_attachesReadAloudStatusOnlyToMatchingThread() {
        val active = history(
            threadId = "100",
            boardId = "b",
            title = "active",
            replyCount = 10,
            lastVisited = 2_000L
        )
        val inactive = history(
            threadId = "200",
            boardId = "b",
            title = "inactive",
            replyCount = 20,
            lastVisited = 1_000L
        )

        val snapshot = WatchSnapshotBuilder(nowMillis = { 1L }).build(
            boards = listOf(board(id = "b")),
            history = listOf(active, inactive),
            watchWords = emptyList(),
            readAloudStatus = WatchReadAloudStatus(
                boardId = "b",
                boardUrl = "https://may.2chan.net/b/",
                threadId = "100",
                state = WatchReadAloudPlaybackState.Speaking,
                postId = "42",
                currentIndex = 4,
                totalPosts = 10,
                updatedAtMillis = 9_999L
            )
        )

        val activeStatus = snapshot.threads.first { it.threadId == "100" }.readAloudStatus
        assertEquals(WatchReadAloudPlaybackState.Speaking, activeStatus?.state)
        assertEquals("42", activeStatus?.postId)
        assertEquals(null, snapshot.threads.first { it.threadId == "200" }.readAloudStatus)
    }

    @Test
    fun build_dropsStaleReadAloudStatus() {
        val entry = history(
            threadId = "100",
            boardId = "b",
            title = "active",
            replyCount = 10
        )

        val snapshot = WatchSnapshotBuilder(nowMillis = { 20 * 60 * 1000L }).build(
            boards = listOf(board(id = "b")),
            history = listOf(entry),
            watchWords = emptyList(),
            readAloudStatus = WatchReadAloudStatus(
                boardId = "b",
                boardUrl = "https://may.2chan.net/b/",
                threadId = "100",
                state = WatchReadAloudPlaybackState.Paused,
                postId = "42",
                currentIndex = 4,
                totalPosts = 10,
                updatedAtMillis = 1L
            )
        )

        assertEquals(null, snapshot.threads.single().readAloudStatus)
    }

    @Test
    fun build_capsBoardsWatchWordsAndSerializedPayloadSize() {
        val boards = (1..100).map { index ->
            board(id = "b$index", name = "板$index", pinned = index % 10 == 0)
        }
        val history = (1..100).map { index ->
            history(
                threadId = index.toString(),
                boardId = "b${(index % 100) + 1}",
                title = "監視対象の長いスレタイトル$index",
                replyCount = 100 + index,
                lastVisited = index.toLong()
            )
        }
        val pages = history.takeLast(20).associate { entry ->
            entry.toWatchThreadKey() to ThreadPage(
                threadId = entry.threadId,
                boardTitle = null,
                expiresAtLabel = null,
                deletedNotice = null,
                posts = (1..10).map { postIndex ->
                    post(
                        id = "${entry.threadId}-$postIndex",
                        messageHtml = "本文".repeat(200)
                    )
                }
            )
        }

        val snapshot = WatchSnapshotBuilder(nowMillis = { 1L }).build(
            boards = boards,
            history = history,
            watchWords = (1..100).map { " 監視ワード$it".repeat(4) },
            threadPages = pages
        )
        val encoded = Json.encodeToString(WatchSnapshot.serializer(), snapshot)

        assertEquals(80, snapshot.boards.size)
        assertEquals(20, snapshot.threads.size)
        assertEquals(50, snapshot.watchWords.size)
        assertTrue(snapshot.watchWords.all { it.length <= 40 })
        assertTrue(encoded.encodeToByteArray().size < 64 * 1024)
    }

    private fun board(
        id: String,
        name: String = id,
        category: String = "cat",
        url: String = "https://may.2chan.net/$id/",
        pinned: Boolean = false
    ): BoardSummary = BoardSummary(
        id = id,
        name = name,
        category = category,
        url = url,
        description = "",
        pinned = pinned
    )

    private fun history(
        threadId: String,
        boardId: String,
        title: String,
        replyCount: Int,
        lastVisited: Long = 1_000L,
        boardName: String = boardId,
        boardUrl: String = "https://may.2chan.net/$boardId/"
    ): ThreadHistoryEntry = ThreadHistoryEntry(
        threadId = threadId,
        boardId = boardId,
        title = title,
        titleImageUrl = "",
        boardName = boardName,
        boardUrl = boardUrl,
        lastVisitedEpochMillis = lastVisited,
        replyCount = replyCount
    )

    private fun post(
        id: String,
        messageHtml: String,
        isDeleted: Boolean = false
    ): Post = Post(
        id = id,
        author = null,
        subject = null,
        timestamp = "now",
        messageHtml = messageHtml,
        imageUrl = null,
        thumbnailUrl = null,
        isDeleted = isDeleted
    )
}
