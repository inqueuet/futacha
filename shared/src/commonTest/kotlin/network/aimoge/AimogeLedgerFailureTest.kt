package com.valoser.futacha.shared.network.aimoge

import com.valoser.futacha.shared.network.NetworkException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AimogeLedgerFailureTest {
    private class Store(var text: String? = null) : AimogeSecretStorage {
        var unreadable = false
        var unwritable = false
        var writes = 0
        override fun read(): String? { check(!unreadable); return text }
        override fun write(value: String) { check(!unwritable); writes++; text = value }
    }

    @Test fun unreadableLedgerNeverReplacesTheSecretOrPendingRequest() = runTest {
        val store = Store()
        val ledger = AimogeRequestLedger(store) { 1L }
        val key = ledger.keyOf("post")
        ledger.put(AimogeLedgerEntry(key, "request-id", "reply:uuid", 1, unconfirmed = true))
        val original = store.text
        val writes = store.writes
        store.unreadable = true
        assertFailsWith<NetworkException> { ledger.keyOf("post") }
        assertEquals(original, store.text)
        assertEquals(writes, store.writes)
        store.unreadable = false
        assertEquals(key, ledger.keyOf("post"))
        assertEquals("request-id", ledger.unconfirmedFor("reply:uuid")?.requestId)
    }

    @Test fun malformedLedgerIsNotTreatedAsANewInstall() = runTest {
        val store = Store("broken-json")
        assertFailsWith<NetworkException> { AimogeRequestLedger(store).keyOf("post") }
        assertEquals("broken-json", store.text)
        assertEquals(0, store.writes)
    }

    @Test fun failedWriteRetainsThePreviousDurableRequest() = runTest {
        val store = Store()
        val ledger = AimogeRequestLedger(store) { 1L }
        val key = ledger.keyOf("post")
        ledger.put(AimogeLedgerEntry(key, "request-id", "reply:uuid", 1))
        store.unwritable = true
        assertFailsWith<NetworkException> { ledger.remove(key) }
        assertEquals("request-id", ledger.find(key)?.requestId)
    }

    @Test fun byteBudgetNeverEvictsAnUnconfirmedPost() = runTest {
        val store = Store()
        val ledger = AimogeRequestLedger(store) { 1L }
        ledger.keyOf("secret")
        val first = AimogeLedgerEntry("first", "one", "reply:uuid", 1, unconfirmed = true, sent = "x".repeat(180_000))
        assertTrue(ledger.put(first))
        val original = store.text
        assertFalse(ledger.put(first.copy(key = "second", requestId = "two")))
        assertEquals(original, store.text)
        assertEquals("one", ledger.find("first")?.requestId)
    }
}
