package com.valoser.futacha.shared.audio.voicevox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Readers retain a verified pack while native code may map it. Metadata reads and
 * other speakers can proceed; replacement/deletion waits for all native users.
 *
 * A replacement or deletion can take minutes, so it does not hold [mutex] while it runs (L8): it only marks
 * [writing]. Readers are refused at once while a mutation waits or runs (the speaker falls back to the system
 * voice instead of staying silent), status checks skip, and metadata reads wait for it to end. */
internal class VoiceAssetAccess {
    private val mutex = Mutex()
    private val readers = MutableStateFlow(0)
    private val writers = MutableStateFlow(0)
    private val writing = MutableStateFlow(false)

    suspend fun lease(load: suspend () -> VoicePack): VoicePackLease = mutex.withLock {
        // Refused before load: neither the invalid marker nor the shown state changes.
        if (writers.value > 0) throw VoicePackUnavailableException(VOICE_PACK_BUSY_MESSAGE)
        val pack = load()
        readers.update { it + 1 }
        VoicePackLease(pack) { readers.update { it - 1 } }
    }

    suspend fun <T> inspect(block: suspend () -> T): T {
        while (true) {
            mutex.lock()
            if (!writing.value) {
                try { return block() } finally { mutex.unlock() }
            }
            mutex.unlock()
            writing.first { !it }
        }
    }

    /** Null while the data is being replaced or deleted: a status refresh does not wait for it. */
    suspend fun <T> inspectUnlessMutating(block: suspend () -> T): T? = mutex.withLock {
        if (writing.value) null else block()
    }

    suspend fun <T> mutate(block: suspend () -> T): T {
        writers.update { it + 1 }
        try {
            while (true) {
                mutex.lock()
                val free = readers.value == 0 && !writing.value
                if (free) writing.value = true
                mutex.unlock()
                if (free) break
                combine(readers, writing) { count, busy -> count == 0 && !busy }.first { it }
            }
            try { return block() } finally { writing.value = false }
        } finally { writers.update { it - 1 } }
    }
}

internal const val VOICE_PACK_BUSY_MESSAGE = "音声データを取得・削除しています。終わるまで端末の音声で読み上げます"
