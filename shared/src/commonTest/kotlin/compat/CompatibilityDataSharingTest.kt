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
        // The tutorial fixture is dropped and the non-Futaba board keeps its
        // place after the Futaba board instead of being moved to the front.
        assertEquals(listOf("may", "other"), synchronized.map(BoardSummary::id))
    }

    @Test
    fun compatibilitySynchronizationKeepsModernPinsCategoriesAndInterleavedOrder() {
        val modern = listOf(
            BoardSummary("img", "img", "雑談", "https://img.2chan.net/b/", "画像", pinned = true),
            BoardSummary("custom", "custom", "外部", "https://example.org/custom", "外部板"),
            BoardSummary("may", "mayb", "二次元", "https://may.2chan.net/b/futaba.php", "may説明"),
            BoardSummary("other", "other", "外部", "https://example.net/other", "")
        )
        fun compat(url: String, name: String, order: Int) = CompatBoard(
            key = compatBoardKey(url),
            name = name,
            canonicalUrl = url,
            originalUrl = url,
            sortOrder = order
        )

        // Unchanged compatibility state (as produced from this modern list) is a no-op.
        assertEquals(
            modern,
            synchronizeModernBoardsFromCompatibility(modern, modernBoardsToCompatibility(modern))
        )

        // としあき(仮) reordered the Futaba boards, renamed one and added a new one.
        val synchronized = synchronizeModernBoardsFromCompatibility(
            modern,
            listOf(
                compat("https://may.2chan.net/b/", "may改名", 0),
                compat("https://img.2chan.net/b/", "img", 1),
                compat("https://dat.2chan.net/b/", "dat", 2)
            )
        )
        assertEquals(listOf("may", "custom", "img", "other", compatBoardKey("https://dat.2chan.net/b/")),
            synchronized.map(BoardSummary::id))
        val may = synchronized.first { it.id == "may" }
        assertEquals("may改名", may.name)
        assertEquals("二次元", may.category)
        assertEquals("may説明", may.description)
        val img = synchronized.first { it.id == "img" }
        assertEquals(true, img.pinned)
        assertEquals("雑談", img.category)
        assertEquals("画像", img.description)
        assertEquals(modern[1], synchronized[1])
        assertEquals(modern[3], synchronized[3])
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

    private val importBoardUrl = "https://may.2chan.net/b/"

    private fun importModern(id: Int, visited: Long, replies: Int = 1) = ThreadHistoryEntry(
        threadId = id.toString(),
        boardId = "may",
        title = "スレ$id",
        titleImageUrl = "",
        boardName = "虹裏",
        boardUrl = importBoardUrl,
        lastVisitedEpochMillis = visited,
        replyCount = replies
    )

    private fun importPlan(
        modern: List<ThreadHistoryEntry>,
        current: List<CompatHistoryEntry>,
        tombstones: Map<String, Long> = emptyMap()
    ) = planModernHistoryImport(
        modernHistory = modern,
        current = current,
        knownBoardKeys = setOf(compatBoardKey(importBoardUrl)),
        tombstoneAt = tombstones::get,
        historyLimit = 200,
        retain = { entries ->
            val sorted = entries.sortedByDescending(CompatHistoryEntry::lastVisitedEpochMillis)
            if (sorted.size > 200) sorted.take(190) else sorted
        }
    )

    @Test
    fun historyImportKeepsCompatibilityUpdateTimeSeparateFromViewTime() {
        val compat = assertNotNull(importModern(1, 100L).toCompatHistoryEntry()).copy(
            originalUrl = "http://may.2chan.net/b/res/1.htm",
            contentUpdatedAtEpochMillis = 150L,
            lastVisitedEpochMillis = 100L,
            scrollAnchor = ScrollAnchor(fallbackIndex = 7)
        )
        val written = importPlan(listOf(importModern(1, 500L, replies = 9)), listOf(compat)).single()
        // The view time and metadata are shared, the update time is not (11.4).
        assertEquals(500L, written.lastVisitedEpochMillis)
        assertEquals(150L, written.contentUpdatedAtEpochMillis)
        assertEquals(9, written.replyCount)
        assertEquals(compat.originalUrl, written.originalUrl)
        assertEquals(compat.scrollAnchor, written.scrollAnchor)
        // Importing the same history again writes nothing.
        assertEquals(emptyList<CompatHistoryEntry>(), importPlan(listOf(importModern(1, 500L, replies = 9)), listOf(written)))
    }

    @Test
    fun historyImportWritesOnlyChangedRowsThatSurviveTheLimit() {
        val modern = (0 until 5_000).map { index -> importModern(100_000 + index, 10_000L + index) }.shuffled()
        val first = importPlan(modern, emptyList())
        assertEquals(
            (104_999 downTo 104_800).map { "${importBoardUrl}res/$it.htm" },
            first.sortedByDescending(CompatHistoryEntry::lastVisitedEpochMillis).map(CompatHistoryEntry::canonicalUrl)
        )
        // Older entries the limit would trim are not written, so a repeated
        // import is a no-op instead of rewriting the history.
        assertEquals(emptyList<CompatHistoryEntry>(), importPlan(modern, first))
        val newer = importModern(200_000, 99_999L)
        assertEquals(listOf("200000"), importPlan(modern + newer, first).map(CompatHistoryEntry::threadNo))
    }

    @Test
    fun historyImportDoesNotResurrectDeletedEntries() {
        val url = "${importBoardUrl}res/1.htm"
        assertEquals(emptyList<CompatHistoryEntry>(), importPlan(listOf(importModern(1, 100L)), emptyList(), mapOf(url to 100L)))
        assertEquals(listOf(url), importPlan(listOf(importModern(1, 101L)), emptyList(), mapOf(url to 100L)).map { it.canonicalUrl })
    }
}
