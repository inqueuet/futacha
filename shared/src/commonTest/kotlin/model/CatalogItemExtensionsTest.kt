package com.valoser.futacha.shared.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogItemExtensionsTest {
    @Test
    fun numericIdSupportsDecoratedIdsAndUsesSafeFallback() {
        assertEquals(123456L, item(id = "No.123456").numericId())
        assertEquals(42L, item(id = "42").numericId())
        assertEquals(0L, item(id = "not-a-number").numericId())
        assertEquals(0L, item(id = "999999999999999999999999").numericId())
    }

    @Test
    fun hostLabelHandlesAbsoluteSchemeRelativeAndRelativeThreadUrls() {
        assertEquals("img.2chan.net", item(url = "https://img.2chan.net/b/res/1.htm").hostLabel())
        assertEquals("may.2chan.net", item(url = "//may.2chan.net/b/res/1.htm").hostLabel())
        assertEquals("b", item(url = "b/res/1.htm").hostLabel())
    }

    private fun item(
        id: String = "1",
        url: String = "https://img.2chan.net/b/res/1.htm"
    ) = CatalogItem(
        id = id,
        title = "title",
        replyCount = 0,
        thumbnailUrl = null,
        fullImageUrl = null,
        threadUrl = url
    )
}
