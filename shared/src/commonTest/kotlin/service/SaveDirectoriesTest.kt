package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SaveDirectoriesTest {
    @Test
    fun buildThreadStorageId_isStable_andIncludesSanitizedBoardAndThreadSegments() {
        val first = buildThreadStorageId("img/b", " 12/34 ")
        val second = buildThreadStorageId("img/b", "12/34")

        assertEquals(first, second)
        assertTrue(first.startsWith("img_b__12_34_"))
        assertEquals(8, first.substringAfterLast('_').length)
    }

    @Test
    fun buildThreadStorageId_truncatesLongSegments_butKeepsStableHashSuffix() {
        val boardId = "board-" + "x".repeat(120)
        val threadId = "thread-" + "y".repeat(120)

        val storageId = buildThreadStorageId(boardId, threadId)

        assertTrue(storageId.length > 16)
        assertTrue(storageId.contains("__"))
        assertEquals(8, storageId.substringAfterLast('_').length)
    }

    @Test
    fun buildLegacyThreadStorageId_dropsHashButKeepsLegacySanitization() {
        val legacy = buildLegacyThreadStorageId("img/b", "12/34")

        assertEquals("img_b__12_34", legacy)
    }

    @Test
    fun buildThreadStorageLockKey_differsByBaseLocationKindAndValue() {
        val pathKey = buildThreadStorageLockKey(
            storageId = "b__123_hash",
            baseDirectory = "Documents"
        )
        val explicitPathKey = buildThreadStorageLockKey(
            storageId = "b__123_hash",
            baseDirectory = "ignored",
            baseSaveLocation = SaveLocation.Path("/storage/emulated/0/Documents/futacha")
        )
        val treeKey = buildThreadStorageLockKey(
            storageId = "b__123_hash",
            baseDirectory = "ignored",
            baseSaveLocation = SaveLocation.TreeUri("content://tree/root")
        )
        val bookmarkKey = buildThreadStorageLockKey(
            storageId = "b__123_hash",
            baseDirectory = "ignored",
            baseSaveLocation = SaveLocation.Bookmark("bookmark-data")
        )

        assertTrue(pathKey.startsWith("root_"))
        assertTrue(explicitPathKey.startsWith("root_"))
        assertTrue(treeKey.startsWith("root_"))
        assertTrue(bookmarkKey.startsWith("root_"))
        assertTrue(pathKey.endsWith("__b__123_hash"))
        assertNotEquals(pathKey, explicitPathKey)
        assertNotEquals(explicitPathKey, treeKey)
        assertNotEquals(treeKey, bookmarkKey)
    }

    @Test
    fun buildThreadStorageLockKey_normalizesBlankStorageIdToThread() {
        val key = buildThreadStorageLockKey(
            storageId = "   ",
            baseDirectory = "Documents"
        )

        assertTrue(key.endsWith("__thread"))
    }
}
