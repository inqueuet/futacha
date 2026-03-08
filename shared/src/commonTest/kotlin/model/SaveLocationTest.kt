package com.valoser.futacha.shared.model

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalEncodingApi::class)
class SaveLocationTest {
    @Test
    fun fromString_resolvesExplicitPrefixes() {
        assertEquals(
            SaveLocation.Path("/tmp/futacha"),
            SaveLocation.fromString("path:/tmp/futacha")
        )
        assertEquals(
            SaveLocation.TreeUri("content://tree/root"),
            SaveLocation.fromString("tree:content://tree/root")
        )
        assertEquals(
            SaveLocation.Bookmark("bookmark-data"),
            SaveLocation.fromString("bookmark:bookmark-data")
        )
    }

    @Test
    fun fromString_detectsLegacyTreeUriAndBookmark() {
        assertEquals(
            SaveLocation.TreeUri("content://com.android.externalstorage.documents/tree/primary%3ADocuments"),
            SaveLocation.fromString("content://com.android.externalstorage.documents/tree/primary%3ADocuments")
        )

        val bookmarkPayload = Base64.encode("bplist00demo".encodeToByteArray())
        val bookmark = SaveLocation.fromString(bookmarkPayload)
        assertIs<SaveLocation.Bookmark>(bookmark)
        assertEquals(bookmarkPayload, bookmark.bookmarkData)
    }

    @Test
    fun toRawString_roundTripsTreeUriAndBookmark() {
        val treeUri = SaveLocation.TreeUri("content://tree/root")
        val bookmark = SaveLocation.Bookmark("bookmark-data")

        val treeRaw = with(SaveLocation.Companion) { treeUri.toRawString() }
        val bookmarkRaw = with(SaveLocation.Companion) { bookmark.toRawString() }

        assertEquals("tree:content://tree/root", treeRaw)
        assertEquals("bookmark:bookmark-data", bookmarkRaw)
    }
}
