package com.valoser.futacha.shared.network.aimoge

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** The あいもげ secret stores are Android Keystore / iOS Keychain: never read on the caller's (main) thread. */
class AimogeStoreThreadTest {
    private class RecordingStore : AimogeSecretStorage {
        val threads = mutableListOf<Thread>()
        override fun read(): String? { synchronized(threads) { threads += Thread.currentThread() }; return null }
        override fun write(value: String) { synchronized(threads) { threads += Thread.currentThread() } }
    }

    @Test
    fun creatingTheAuthAndUsingTheLedgerStayOffTheCallersThread() = runBlocking {
        val caller = Thread.currentThread()
        val store = RecordingStore()
        val auth = AimogeAuth(store, AimogeAuthApi(HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })), persistPending = true)
        auth.refresh()
        auth.deleteKey()
        val ledgerStore = RecordingStore()
        AimogeRequestLedger(ledgerStore).keyOf("body|board")
        val all = synchronized(store.threads) { store.threads.toList() } + synchronized(ledgerStore.threads) { ledgerStore.threads.toList() }
        assertTrue(all.isNotEmpty())
        assertTrue(all.none { it === caller }, "a store was touched on the caller's thread")
    }
}
