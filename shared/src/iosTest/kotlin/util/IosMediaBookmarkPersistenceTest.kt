package com.valoser.futacha.shared.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** B4-3: folders resolved at the same time must all be remembered, not overwrite each other. */
class IosMediaBookmarkPersistenceTest {
    @Test
    fun concurrentlyRememberedFoldersAreAllKept() = runBlocking {
        val original = persistedMediaBookmarksForTest()
        try {
            repeat(5) { round ->
                replacePersistedMediaBookmarksForTest(emptyMap())
                val folders = (0 until 16).map { "/private/var/test-bookmarks/$round/$it" }
                folders.map { folder ->
                    async(Dispatchers.Default) { rememberMediaBookmarkForTest(folder, "bookmark-$folder") }
                }.awaitAll()
                assertEquals(folders.associateWith { "bookmark-$it" }, persistedMediaBookmarksForTest())
            }
        } finally {
            replacePersistedMediaBookmarksForTest(original)
        }
    }
}
