package com.valoser.futacha.shared.media.analysis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Closing the owner prevents new borrowers; the final borrower releases the native value.
 * Cancellation callbacks borrow independently, so they cannot touch an already freed RunOptions. */
internal class InferenceResource<T : Any>(value: T, private val dispose: (T) -> Unit) : AutoCloseable {
    private data class State<T>(val value: T?, val closed: Boolean = false, val readers: Int = 0)
    private val state = MutableStateFlow(State(value))
    val isClosed get() = state.value.closed

    fun tryRetain(): InferenceLease<T>? {
        while (true) {
            val before = state.value
            if (before.closed) return null
            check(before.readers < Int.MAX_VALUE)
            if (state.compareAndSet(before, before.copy(readers = before.readers + 1))) {
                return InferenceLease(requireNotNull(before.value), ::release)
            }
        }
    }
    fun retain(): InferenceLease<T> = checkNotNull(tryRetain()) { "推論リソースは解放済みです" }

    private fun release() {
        while (true) {
            val before = state.value
            check(before.readers > 0)
            val disposeNow = before.closed && before.readers == 1
            val after = before.copy(readers = before.readers - 1, value = if (disposeNow) null else before.value)
            if (state.compareAndSet(before, after)) {
                if (disposeNow) dispose(requireNotNull(before.value))
                return
            }
        }
    }
    override fun close() {
        while (true) {
            val before = state.value
            if (before.closed) return
            val after = before.copy(closed = true, value = if (before.readers == 0) null else before.value)
            if (state.compareAndSet(before, after)) {
                if (before.readers == 0) dispose(requireNotNull(before.value))
                return
            }
        }
    }
}

internal class InferenceLease<T : Any>(value: T, private val release: () -> Unit) : AutoCloseable {
    private val held = MutableStateFlow<T?>(value)
    val value get() = checkNotNull(held.value) { "推論リソースの参照は解放済みです" }
    override fun close() { if (held.getAndUpdate { null } != null) release() }
}
