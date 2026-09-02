package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CatalogLazyKeySupportTest {
    @Test
    fun catalogKeysFollowThreadIdentityAcrossSortFilterAndRefresh() {
        val original = listOf(item("100"), item("200"), item("300"))
        val originalById = original.zip(buildCatalogItemLazyKeys("may-b", original))
            .associate { (item, key) -> item.id to key }

        val sorted = original.reversed()
        val filtered = listOf(original[2], original[0])
        val refreshed = original.map { it.copy(replyCount = it.replyCount + 10) }

        assertEquals(
            sorted.map { originalById.getValue(it.id) },
            buildCatalogItemLazyKeys("may-b", sorted)
        )
        assertEquals(
            filtered.map { originalById.getValue(it.id) },
            buildCatalogItemLazyKeys("may-b", filtered)
        )
        assertEquals(
            refreshed.map { originalById.getValue(it.id) },
            buildCatalogItemLazyKeys("may-b", refreshed)
        )
    }

    @Test
    fun catalogKeysIncludeBoardIdentity() {
        val items = listOf(item("100"))

        assertNotEquals(
            buildCatalogItemLazyKeys("may-b", items),
            buildCatalogItemLazyKeys("img-b", items)
        )
    }

    @Test
    fun duplicateThreadIdsUseStableUrlFallbackWithoutGlobalIndex() {
        val first = item("100", "https://may.2chan.net/b/res/100.htm")
        val duplicate = item("100", "https://may.2chan.net/b/res/100-old.htm")
        val original = listOf(first, item("200"), duplicate)
        val reordered = listOf(duplicate, item("200"), first)

        val originalKeys = buildCatalogItemLazyKeys("may-b", original)
        val reorderedKeys = buildCatalogItemLazyKeys("may-b", reordered)

        assertEquals(originalKeys[0], reorderedKeys[2])
        assertEquals(originalKeys[1], reorderedKeys[1])
        assertEquals(originalKeys[2], reorderedKeys[0])
        assertEquals(originalKeys.size, originalKeys.distinct().size)
    }

    @Test
    fun exactDuplicateRowsReceiveExplicitOccurrenceFallback() {
        val duplicate = item("100")
        val keys = buildCatalogItemLazyKeys("may-b", listOf(duplicate, duplicate, duplicate))

        assertEquals(keys.size, keys.distinct().size)
        assertEquals(
            listOf(
                "catalog-item:may-b:100:url:https://may.2chan.net/b/res/100.htm:occurrence:0",
                "catalog-item:may-b:100:url:https://may.2chan.net/b/res/100.htm:occurrence:1",
                "catalog-item:may-b:100:url:https://may.2chan.net/b/res/100.htm:occurrence:2"
            ),
            keys
        )
    }

    @Test
    fun blankThreadIdsUseThreadUrlAsStableIdentity() {
        val first = item("", "https://may.2chan.net/b/res/100.htm")
        val second = item("", "https://may.2chan.net/b/res/200.htm")

        assertEquals(
            buildCatalogItemLazyKeys("may-b", listOf(first, second)).reversed(),
            buildCatalogItemLazyKeys("may-b", listOf(second, first))
        )
    }

    private fun item(
        id: String,
        threadUrl: String = "https://may.2chan.net/b/res/$id.htm"
    ) = CatalogItem(
        id = id,
        threadUrl = threadUrl,
        title = "thread-$id",
        thumbnailUrl = "https://may.2chan.net/b/thumb/${id}s.jpg",
        fullImageUrl = null,
        replyCount = id.toIntOrNull() ?: 0
    )
}
