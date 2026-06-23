package com.valoser.futacha.shared.util

import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CatalogDisplayTitleResolverTest {
    @Test
    fun shouldResolveCatalogThreadTitleFromHead_allowsConfiguredHostForEmptyOrNumericTitles() {
        val boardUrl = "https://img.2chan.net/b/futaba.php"

        assertTrue(shouldResolveCatalogThreadTitleFromHead(boardUrl, "", replyCount = 10))
        assertTrue(shouldResolveCatalogThreadTitleFromHead(boardUrl, "10", replyCount = 10))
        assertTrue(shouldUseCatalogTitleFallbackHeadScan(boardUrl))
    }

    @Test
    fun shouldResolveCatalogThreadTitleFromHead_rejectsNonConfiguredHosts() {
        val boardUrl = "https://may.2chan.net/b/futaba.php"

        assertFalse(shouldResolveCatalogThreadTitleFromHead(boardUrl, "", replyCount = 10))
        assertFalse(shouldResolveCatalogThreadTitleFromHead(boardUrl, "10", replyCount = 10))
        assertFalse(shouldUseCatalogTitleFallbackHeadScan(boardUrl))
    }

    @Test
    fun shouldResolveCatalogThreadTitleFromHead_usesInferredPolicyForNonConfiguredHosts() {
        val boardUrl = "https://dat.2chan.net/t/futaba.php"
        val policy = CatalogTitleCompletionPolicy(enabled = true)

        assertTrue(
            shouldResolveCatalogThreadTitleFromHead(
                boardUrl = boardUrl,
                currentTitle = "10",
                replyCount = 10,
                inferredPolicy = policy
            )
        )
    }

    @Test
    fun shouldResolveCatalogThreadTitleFromHead_keepsNormalTitles() {
        val boardUrl = "https://img.2chan.net/b/futaba.php"

        assertFalse(shouldResolveCatalogThreadTitleFromHead(boardUrl, "通常タイトル", replyCount = 10))
    }

    @Test
    fun inferCatalogTitleCompletionPolicy_enablesWhenMostTitlesAreReplyCountPlaceholders() {
        val items = List(8) { index ->
            catalogItem(
                id = index.toString(),
                title = index.toString(),
                replyCount = index
            )
        }

        val policy = inferCatalogTitleCompletionPolicy(items)

        assertTrue(policy?.enabled == true)
    }

    @Test
    fun inferCatalogTitleCompletionPolicy_disablesWhenCatalogHasRealTitles() {
        val items = List(8) { index ->
            catalogItem(
                id = index.toString(),
                title = "タイトル$index",
                replyCount = index
            )
        }

        val policy = inferCatalogTitleCompletionPolicy(items)

        assertFalse(policy?.enabled == true)
    }

    @Test
    fun inferCatalogTitleCompletionPolicy_returnsNullForSmallSamples() {
        val items = List(4) { index ->
            catalogItem(
                id = index.toString(),
                title = index.toString(),
                replyCount = index
            )
        }

        assertNull(inferCatalogTitleCompletionPolicy(items))
    }

    private fun catalogItem(
        id: String,
        title: String?,
        replyCount: Int
    ): CatalogItem {
        return CatalogItem(
            id = id,
            threadUrl = "https://example.com/res/$id.htm",
            title = title,
            thumbnailUrl = null,
            fullImageUrl = null,
            replyCount = replyCount
        )
    }
}
