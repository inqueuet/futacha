package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompatCatalogCacheSearchDialogTest {
    @Test
    fun modeLabelsMatchTheFinalApkRadioButtons() {
        assertEquals("OR検索", compatCatalogCacheSearchModeLabel(CompatCatalogCacheSearchMode.OR))
        assertEquals("AND検索", compatCatalogCacheSearchModeLabel(CompatCatalogCacheSearchMode.AND))
    }

    @Test
    fun legacyResponseUsesCatalogThumbnailFieldAndMapsMetadata() = runBlocking {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        """
                        {"l":[{"u":"http://may.2chan.net/b/res/123.htm","t":"猫 test","C":"37","c":"http://may.2chan.net/b/thumb/123s.jpg","e":"120","f":"80"}]}
                        """.trimIndent(),
                        HttpStatusCode.OK,
                        headersOf("Content-Type", "application/json")
                    )
                }
            }
        }
        try {
            val result = searchLegacyCompatCatalogCache(
                client,
                "https://may.2chan.net/b/",
                "猫"
            ).getOrThrow()

            assertEquals(1, result.size)
            assertEquals("123", result.single().id)
            assertEquals("猫 test", result.single().title)
            assertEquals("https://may.2chan.net/b/thumb/123s.jpg", result.single().thumbnailUrl)
            assertEquals(120, result.single().thumbnailWidth)
            assertEquals(80, result.single().thumbnailHeight)
            assertEquals(37, result.single().replyCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun cacheResultsSupportOrAndFilteringLikeLegacySearch() {
        val items = listOf(
            cacheItem("1", "猫 犬"),
            cacheItem("2", "猫 鳥"),
            cacheItem("3", "犬 鳥")
        )

        assertEquals(
            listOf("1", "2", "3"),
            filterLegacyCompatCatalogCache(
                items,
                "猫 鳥",
                CompatCatalogCacheSearchMode.OR
            ).map(CatalogItem::id)
        )
        assertEquals(
            listOf("2"),
            filterLegacyCompatCatalogCache(
                items,
                "猫 鳥",
                CompatCatalogCacheSearchMode.AND
            ).map(CatalogItem::id)
        )
        assertTrue(
            filterLegacyCompatCatalogCache(
                items,
                "999",
                CompatCatalogCacheSearchMode.OR
            ).isEmpty()
        )
        assertEquals(
            listOf("1"),
            filterLegacyCompatCatalogCache(
                listOf(cacheItem("1", "ガッツポーズ"), cacheItem("2", "別")),
                "ｶﾞｯﾂ",
                CompatCatalogCacheSearchMode.OR
            ).map(CatalogItem::id)
        )
    }

    @Test
    fun keywordHistoryMatchesReferenceNormalizationAndLimits() {
        assertEquals(
            listOf("猫 犬", "鳥"),
            normalizeCompatCacheSearchHistory(" 猫　犬\n猫 犬\r\n鳥\n")
        )
        assertEquals(
            listOf("鳥", "猫", "犬"),
            rememberCompatCacheSearchKeyword(listOf("猫", "鳥", "犬"), "鳥")
        )
        assertEquals(
            listOf("猫 犬", "猫"),
            compatCacheSearchSuggestions(listOf("猫 犬", "犬", "猫"), "猫")
        )
        assertEquals(
            50,
            rememberCompatCacheSearchKeyword((1..50).map { "old$it" }, "new").size
        )
    }

    @Test
    fun switchingFromAndBackToOrUsesStableBaseResults() {
        val base = listOf(
            cacheItem("1", "猫 犬"),
            cacheItem("2", "猫 鳥"),
            cacheItem("3", "犬 鳥")
        )
        val andResults = filterLegacyCompatCatalogCache(
            base,
            "猫 鳥",
            CompatCatalogCacheSearchMode.AND
        )

        assertEquals(listOf("2"), andResults.map(CatalogItem::id))
        assertEquals(
            listOf("1", "2", "3"),
            filterLegacyCompatCatalogCache(
                base,
                "猫 鳥",
                CompatCatalogCacheSearchMode.OR
            ).map(CatalogItem::id)
        )
    }

    @Test
    fun remoteCacheResultsMergeMatchingLocalHistoryForCurrentBoardOnly() {
        val remote = listOf(cacheItem("1", "猫 remote"))
        val local = listOf(
            historyItem("1", "猫 duplicate", "may-b"),
            historyItem("2", "猫 local", "may-b"),
            historyItem("3", "猫 other board", "img-b"),
            historyItem("4", "犬", "may-b")
        )

        val merged = mergeCompatCacheSearchResults(remote, local, "may-b", "猫")

        assertEquals(listOf("1", "2"), merged.map(CatalogItem::id))
        assertEquals("猫 remote", merged.first().title)
    }

    @Test
    fun cachedDroppedThreadBodyParticipatesInOrAndSearchWithoutChangingItsTitle() {
        val local = listOf(historyItem("2", "題名にはない", "may-b"))
        val body = compatCacheSearchBodyText(
            CompatThreadSnapshot(
                tabKey = "thread-2",
                revision = 1L,
                fetchedAtEpochMillis = 1L,
                posts = listOf(
                    CompatPostSnapshot(
                        position = 0,
                        postNo = "2",
                        subject = "被写体",
                        timestamp = "now",
                        messageHtml = "<b>猫</b> と 犬"
                    )
                )
            )
        )
        val merged = mergeCompatCacheSearchResults(
            remoteResults = emptyList(),
            localHistory = local,
            boardKey = "may-b",
            query = "猫 犬",
            bodyTextByThreadId = mapOf("2" to body)
        )

        assertEquals("題名にはない", merged.single().title)
        assertEquals(
            listOf("2"),
            filterLegacyCompatCatalogCache(
                merged,
                "猫 犬",
                CompatCatalogCacheSearchMode.AND,
                supplementalTextById = mapOf("2" to body)
            ).map(CatalogItem::id)
        )
    }

    @Test
    fun emptyQueryShowsNewestHistorySuggestionsLikeReferenceDialog() {
        assertEquals(
            listOf("猫", "犬"),
            compatCacheSearchSuggestions(listOf("猫", "犬"), "")
        )
    }

    private fun cacheItem(id: String, title: String) = CatalogItem(
        id = id,
        threadUrl = "https://may.2chan.net/b/res/$id.htm",
        title = title,
        thumbnailUrl = null,
        fullImageUrl = null,
        replyCount = 0
    )

    private fun historyItem(id: String, title: String, boardKey: String) = CompatHistoryEntry(
        canonicalUrl = "https://may.2chan.net/b/res/$id.htm",
        originalUrl = "https://may.2chan.net/b/res/$id.htm",
        boardKey = boardKey,
        boardName = boardKey,
        threadNo = id,
        title = title,
        replyCount = 1,
        contentUpdatedAtEpochMillis = 1L
    )
}
