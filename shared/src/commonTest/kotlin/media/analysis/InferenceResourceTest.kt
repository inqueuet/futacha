package com.valoser.futacha.shared.media.analysis

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.test.*

class InferenceResourceTest {
    @Test fun ownerCloseDefersDisposalUntilTheLastBorrowerAndRejectsNewUsers() {
        val value = Any()
        var disposed = 0
        val resource = InferenceResource(value) { assertSame(value, it); disposed++ }
        val first = resource.retain()
        val second = resource.retain()
        resource.close(); resource.close()
        assertTrue(resource.isClosed); assertNull(resource.tryRetain())
        assertEquals(0, disposed); assertSame(value, second.value)
        first.close(); first.close()
        assertFailsWith<IllegalStateException> { first.value }
        assertEquals(0, disposed)
        second.close(); second.close(); resource.close()
        assertEquals(1, disposed)
    }

    @Test fun concurrentOwnerAndBorrowerCloseDisposeExactlyOnce() = runBlocking {
        repeat(100) {
            val disposed = MutableStateFlow(0)
            val resource = InferenceResource(Any()) { disposed.update { it + 1 } }
            val pins = List(8) { resource.retain() }
            val start = CompletableDeferred<Unit>()
            coroutineScope {
                repeat(4) { launch(Dispatchers.Default) { start.await(); resource.close() } }
                pins.forEach { pin -> repeat(2) { launch(Dispatchers.Default) { start.await(); pin.close() } } }
                launch(Dispatchers.Default) { start.await(); repeat(100) { resource.tryRetain()?.close() } }
                start.complete(Unit)
            }
            assertEquals(1, disposed.value); assertNull(resource.tryRetain())
        }
    }

    @Test fun tensorSizeRejectsOverflowDynamicAndExcessiveDimensionsBeforeAllocation() {
        assertEquals(3 * 640 * 640, inferenceTensorSize(listOf(1, 3, 640, 640)))
        assertEquals(16 * 1024 * 1024, inferenceTensorSize(listOf(16, 1024, 1024)))
        for (shape in listOf<List<Long>>(emptyList(), listOf(0), listOf(-1), listOf(Long.MAX_VALUE),
            listOf(1024, Long.MAX_VALUE), listOf(16, 1024, 1025), List(9) { 1L })) {
            assertFailsWith<IllegalArgumentException>(shape.toString()) { inferenceTensorSize(shape) }
        }
    }
}
