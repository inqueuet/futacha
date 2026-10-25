package com.valoser.futacha.shared.repo

import com.valoser.futacha.shared.repo.mock.MockBoardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tutorial board's mock data is read from any caller's dispatcher, release builds included (L104). */
class MockBoardDataConcurrencyTest {
    @Test
    fun keepsNoUnsynchronizedMapSharedByItsCallers() {
        // thread() filled a plain mutable map from whichever dispatcher the caller used. A race on it
        // is not reliably reproducible, so the shared state itself is checked.
        val sharedMaps = MockBoardData::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && Map::class.java.isAssignableFrom(it.type) }
            .mapNotNull { field -> field.apply { isAccessible = true }.get(null) }
            .filter { it is java.util.HashMap<*, *> }
        assertTrue(sharedMaps.isEmpty(), "Unsynchronized maps shared by every caller: $sharedMaps")
    }

    @Test
    fun concurrentReadsReturnTheTutorialThreadUnderEachId() = runBlocking {
        val base = MockBoardData.thread("")
        coroutineScope {
            (0 until 64).map { worker ->
                async(Dispatchers.Default) {
                    repeat(100) { index ->
                        val id = (worker * 1_000 + index).toString()
                        val page = MockBoardData.thread(id)
                        assertEquals(id, page.threadId)
                        assertNull(page.boardTitle)
                        assertEquals(base.posts, page.posts)
                    }
                }
            }.awaitAll()
        }
        // The tutorial thread's own id still returns it whole (with its board title), and
        // the same id keeps giving the same page.
        assertEquals(base, MockBoardData.thread(base.threadId))
        assertEquals(MockBoardData.thread("42"), MockBoardData.thread("42"))
    }
}
