package com.valoser.futacha.shared.util

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TreeChildIndexTest {
    @Test
    fun eachFolderIsListedOnceWhileManyNamesAreResolved() = runBlocking {
        val index = TreeChildIndex<String>()
        val listings = mutableMapOf<String, Int>()
        suspend fun lookup(folder: String, name: String) = index.child(folder, name) {
            listings[folder] = (listings[folder] ?: 0) + 1
            mapOf("existing.jpg" to "$folder/existing.jpg")
        }

        repeat(100) { assertNull(lookup("src", "new-$it.jpg")) }
        assertEquals("src/existing.jpg", lookup("src", "existing.jpg"))
        lookup("thumb", "a.jpg")

        assertEquals(mapOf("src" to 1, "thumb" to 1), listings)
    }

    @Test
    fun createdAndDeletedEntriesAreVisibleWithoutListingAgain() = runBlocking {
        val index = TreeChildIndex<String>()
        var listed = 0
        suspend fun lookup(name: String) = index.child("dir", name) { listed++; mapOf("old.zip" to "old") }

        assertNull(lookup("new.zip"))
        index.record("dir", "new.zip", "new")
        assertEquals("new", lookup("new.zip"))
        index.forget("dir", "old.zip")
        assertNull(lookup("old.zip"))
        assertEquals(1, listed)
    }
}
