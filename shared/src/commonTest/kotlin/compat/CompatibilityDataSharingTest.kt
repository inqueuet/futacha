package com.valoser.futacha.shared.compat

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.model.ThreadPage
import com.valoser.futacha.shared.model.QuoteReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CompatibilityDataSharingTest {
    @Test
    fun historyRoundTripsUsingTheCanonicalThreadIdentity() {
        val modern = ThreadHistoryEntry(
            threadId = "123",
            boardId = "modern-board",
            title = "title",
            titleImageUrl = "https://may.2chan.net/b/src/123.jpg",
            boardName = "mayb",
            boardUrl = "https://may.2chan.net/b/",
            lastVisitedEpochMillis = 200L,
            replyCount = 12,
            lastReadItemIndex = 4,
            lastReadItemOffset = 8
        )
        val compat = assertNotNull(modern.toCompatHistoryEntry())
        val restored = assertNotNull(compat.toModernThreadHistoryEntry())
        assertEquals("https://may.2chan.net/b/res/123.htm", compat.canonicalUrl)
        assertEquals(modern.threadId, restored.threadId)
        assertEquals(modern.title, restored.title)
        assertEquals(modern.boardUrl, restored.boardUrl)
        assertEquals(modern.replyCount, restored.replyCount)
        assertEquals(modern.lastReadItemIndex, restored.lastReadItemIndex)
        assertEquals(modern.lastReadItemOffset, restored.lastReadItemOffset)
    }

    @Test
    fun mergeKeepsModernBoardsAndAddsCompatibilityOnlyBoards() {
        val modern = listOf(
            BoardSummary("may", "mayb", "", "https://may.2chan.net/b/", "")
        )
        val compat = listOf(
            CompatBoard(
                key = compatBoardKey("https://img.2chan.net/b/"),
                name = "img",
                canonicalUrl = "https://img.2chan.net/b/",
                originalUrl = "https://img.2chan.net/b/",
                sortOrder = 0
            )
        )
        assertEquals(2, mergeCompatibilityBoards(modern, compat).size)
    }

    @Test
    fun compatibilitySynchronizationKeepsDeletionAndDoesNotReseedTutorial() {
        val modern = listOf(
            BoardSummary("t", "チュートリアル", "", "https://www.example.com/t/futaba.php", ""),
            BoardSummary("may", "mayb", "", "https://may.2chan.net/b/", ""),
            BoardSummary("other", "other", "", "https://example.org/custom", "")
        )

        assertEquals(
            listOf("other"),
            synchronizeModernBoardsFromCompatibility(modern, emptyList()).map(BoardSummary::id)
        )

        val compat = CompatBoard(
            key = compatBoardKey("https://may.2chan.net/b/"),
            name = "mayb",
            canonicalUrl = "https://may.2chan.net/b/",
            originalUrl = "https://may.2chan.net/b/",
            sortOrder = 0
        )
        val synchronized = synchronizeModernBoardsFromCompatibility(modern, listOf(compat))
        assertEquals(listOf("other", "may"), synchronized.map(BoardSummary::id))
    }

    @Test
    fun modernBoardConversionProvidesAuthoritativeKeysAndOrderForDeletionSync() {
        val converted = modernBoardsToCompatibility(
            listOf(
                BoardSummary("custom", "custom", "", "https://example.org/custom", ""),
                BoardSummary("may", "may", "", "https://may.2chan.net/b/futaba.php", ""),
                BoardSummary("img", "img", "", "https://img.2chan.net/b/", ""),
                BoardSummary("may-copy", "duplicate", "", "https://may.2chan.net/b/", "")
            )
        )

        assertEquals(listOf("https://may.2chan.net/b/", "https://img.2chan.net/b/"), converted.map { it.canonicalUrl })
        assertEquals(listOf(1, 2), converted.map { it.sortOrder })
        assertEquals(converted.map { compatBoardKey(it.canonicalUrl) }, converted.map { it.key })
    }

    @Test
    fun mergeRestoresTheModernBoardIdForCompatibilityHistory() {
        val boardUrl = "https://may.2chan.net/b/"
        val modernBoard = BoardSummary("may", "mayb", "", boardUrl, "")
        val compat = CompatHistoryEntry(
            canonicalUrl = "${boardUrl}res/123.htm",
            originalUrl = "${boardUrl}res/123.htm",
            boardKey = compatBoardKey(boardUrl),
            boardName = "mayb",
            threadNo = "123",
            title = "title",
            contentUpdatedAtEpochMillis = 10L
        )
        val merged = mergeCompatibilityHistory(emptyList(), listOf(compat), listOf(modernBoard))
        assertEquals("may", merged.single().boardId)
    }

    @Test
    fun mergeDeduplicatesModernThreadUrlAndCompatibilityBoardUrl() {
        val boardUrl = "https://may.2chan.net/b/"
        val modern = ThreadHistoryEntry(
            threadId = "123",
            boardId = "modern-board",
            title = "subject",
            titleImageUrl = "",
            boardName = "mayb",
            boardUrl = "${boardUrl}res/123.htm",
            lastVisitedEpochMillis = 20L,
            replyCount = 8,
            lastReadItemIndex = 9,
            lastReadItemOffset = 17
        )
        val compat = CompatHistoryEntry(
            canonicalUrl = "${boardUrl}res/123.htm",
            originalUrl = "${boardUrl}res/123.htm",
            boardKey = compatBoardKey(boardUrl),
            boardName = "mayb",
            threadNo = "123",
            title = "subject",
            replyCount = 12,
            contentUpdatedAtEpochMillis = 30L
        )

        val merged = mergeCompatibilityHistory(listOf(modern), listOf(compat))

        assertEquals(1, merged.size)
        assertEquals(12, merged.single().replyCount)
        assertEquals(9, merged.single().lastReadItemIndex)
        assertEquals(17, merged.single().lastReadItemOffset)
    }

    @Test
    fun sharedHistoryMetadataIgnoresCompatibilityOnlyScrollAnchor() {
        val entry = CompatHistoryEntry(
            canonicalUrl = "https://may.2chan.net/b/res/123.htm",
            originalUrl = "https://may.2chan.net/b/res/123.htm",
            boardKey = "may-b",
            boardName = "mayb",
            threadNo = "123",
            title = "subject",
            replyCount = 10,
            contentUpdatedAtEpochMillis = 20L
        )

        assertEquals(
            compatibilityHistorySharedMetadata(listOf(entry)),
            compatibilityHistorySharedMetadata(
                listOf(entry.copy(scrollAnchor = ScrollAnchor(postNo = "9", offsetPx = 40, fallbackIndex = 8)))
            )
        )
    }

    @Test
    fun threadSnapshotRoundTripsIntoModernPageWithoutDroppingMediaOrTruncation() {
        val snapshot = CompatThreadSnapshot(
            tabKey = compatTabKey("https://may.2chan.net/b/res/123.htm"),
            revision = 20L,
            fetchedAtEpochMillis = 20L,
            boardTitle = "mayb",
            expiresAtLabel = "02:00頃消えます",
            deletedNotice = "削除された記事が1件あります",
            isTruncated = true,
            truncationReason = "test fixture",
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = "123",
                    author = "としあき",
                    timestamp = "2026/08/14(金) 12:00:00",
                    posterId = "abc",
                    messageHtml = "本文",
                    imageUrl = "https://may.2chan.net/b/src/123.png",
                    thumbnailUrl = "https://may.2chan.net/b/thumb/123.jpg",
                    thumbnailWidth = 640,
                    thumbnailHeight = 480,
                    quoteReferences = listOf(QuoteReference(
                        text = ">>122",
                        targetPostIds = listOf("122")
                    ))
                )
            )
        )

        val page: ThreadPage = snapshot.toThreadPage("123")
        assertEquals("123", page.threadId)
        assertEquals(snapshot.boardTitle, page.boardTitle)
        assertEquals(snapshot.expiresAtLabel, page.expiresAtLabel)
        assertEquals(snapshot.deletedNotice, page.deletedNotice)
        assertEquals(snapshot.isTruncated, page.isTruncated)
        assertEquals(snapshot.truncationReason, page.truncationReason)
        assertEquals("https://may.2chan.net/b/src/123.png", page.posts.single().imageUrl)
        assertEquals("https://may.2chan.net/b/thumb/123.jpg", page.posts.single().thumbnailUrl)
        assertEquals(640, page.posts.single().thumbnailWidth)
        assertEquals(480, page.posts.single().thumbnailHeight)
        assertEquals(listOf("122"), page.posts.single().quoteReferences.single().targetPostIds)
    }

    @Test
    fun legacySharedSnapshotDropsFtbucketPreviewControlInModernPage() {
        val snapshot = CompatThreadSnapshot(
            tabKey = compatTabKey("https://img.2chan.net/b/res/123.htm"),
            revision = 1L,
            fetchedAtEpochMillis = 1L,
            posts = listOf(
                CompatPostSnapshot(
                    position = 0,
                    postNo = "123",
                    timestamp = "",
                    messageHtml =
                        "<a href=\"other/fu7199371.png\">fu7199371.png</a>" +
                            "<span onclick=\"previewImg('id','other/fu7199371.png')\">[見る]</span><br>本文"
                )
            )
        )

        assertEquals(
            "<a href=\"other/fu7199371.png\">fu7199371.png</a><br>本文",
            snapshot.toThreadPage("123").posts.single().messageHtml
        )
    }
}
