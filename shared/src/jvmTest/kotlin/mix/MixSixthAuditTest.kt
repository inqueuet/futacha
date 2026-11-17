package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.network.NetworkException
import com.valoser.futacha.shared.util.JvmFileSystem
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

/** Regression tests for audit round six. */
@OptIn(ExperimentalCoroutinesApi::class)
class MixSixthAuditTest {
    private val board = BoardSummary("may", "may", "", "https://may.2chan.net/b/", "")
    private val def = MixDefinition("audit-order", "orders", listOf(board.url), createdAt = 1)
    private fun result(token: Long) = MixFetchResult(token, emptyList(), emptySet(), emptyMap(), emptyMap(), 1_000)

    @Test fun orderChangesWaitAndRunOnlyTheLastOrderWithoutCancellingTheFirst() = runTest {
        val started = mutableListOf<MixOrderKind>()
        val gate = CompletableDeferred<Unit>()
        var cancellations = 0
        val cache = MixCatalogSessionCache()
        val session = MixCatalogSession(backgroundScope, cache, { 1_000 },
            resolve = { definition, boards -> ResolvedMix(definition, boards, emptyMap()) },
            fetch = { mix, token ->
                started += mix.definition.order
                try { gate.await() } catch (e: CancellationException) { cancellations++; throw e }
                result(token)
            })
        session.show(def, listOf(board)); runCurrent()
        session.show(def.copy(order = MixOrderKind.NEW), listOf(board)); runCurrent()
        session.show(def.copy(order = MixOrderKind.OLD), listOf(board)); runCurrent()
        assertEquals(listOf(MixOrderKind.ALTERNATE), started)
        assertEquals(0, cancellations)
        assertTrue(session.state.value.running)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(MixOrderKind.ALTERNATE, MixOrderKind.OLD), started)
        val hash = board.url.hashCode()
        assertNull(cache.get(MixCacheKey(def.id, MixOrderKind.ALTERNATE, hash)))
        assertNull(cache.get(MixCacheKey(def.id, MixOrderKind.NEW, hash)))
        assertNotNull(cache.get(MixCacheKey(def.id, MixOrderKind.OLD, hash)))
        assertFalse(session.state.value.running)
        session.close()
    }

    @Test fun closingWhileOrderIsPendingNeverStartsIt() = runTest {
        var started = 0
        val gate = CompletableDeferred<Unit>()
        val session = MixCatalogSession(backgroundScope, MixCatalogSessionCache(), { 1_000 },
            resolve = { definition, boards -> ResolvedMix(definition, boards, emptyMap()) },
            fetch = { _, token -> started++; withContext(NonCancellable) { gate.await() }; result(token) })
        session.show(def, listOf(board)); runCurrent()
        session.show(def.copy(order = MixOrderKind.NEW), listOf(board)); runCurrent()
        session.close()
        gate.complete(Unit); runCurrent()
        assertEquals(1, started)
        assertNull(session.state.value.result)
    }

    @Test fun failedOldOrderStillStartsTheLastPendingOrder() = runTest {
        val gate = CompletableDeferred<Unit>()
        val orders = mutableListOf<MixOrderKind>()
        val session = MixCatalogSession(backgroundScope, MixCatalogSessionCache(), { 1_000 },
            resolve = { definition, boards -> ResolvedMix(definition, boards, emptyMap()) },
            fetch = { mix, token ->
                orders += mix.definition.order
                if (orders.size == 1) { gate.await(); throw IOException("old request failed") }
                result(token)
            })
        session.show(def, listOf(board)); runCurrent()
        session.show(def.copy(order = MixOrderKind.NEW), listOf(board)); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(MixOrderKind.ALTERNATE, MixOrderKind.NEW), orders)
        assertNotNull(session.state.value.result)
        assertNull(session.state.value.message)
        session.close()
    }

    private fun withStore(block: suspend (DesktopCompatibilityStore) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("mix-sixth-audit").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(root))
        try { store.initialize(); block(store) } finally { store.close(); root.deleteRecursively() }
    }

    @Test fun writeFailuresReturnRefusedAndLeaveStoredDefinitionsUntouched() = withStore { store ->
        MixStore(store).create("before", listOf(board.url), MixOrderKind.ALTERNATE, 1)
        val id = (MixStore(store).load() as MixLoadResult.Ok).mixes.single().id
        val raw = store.loadPreference(MIX_DEFINITIONS_KEY)
        val failingStore = object : CompatibilityStore by store {
            override suspend fun savePreference(key: String, value: String) { throw IOException("simulated persistence failure") }
        }
        val mix = MixStore(failingStore)
        assertIs<MixWriteResult.Refused>(mix.update(id) { it.copy(name = "after") })
        assertIs<MixWriteResult.Refused>(mix.delete(id))
        assertIs<MixWriteResult.Refused>(mix.move(id, 0))
        assertIs<MixWriteResult.Refused>(mix.create("new", listOf(board.url), MixOrderKind.NEW, 2))
        assertEquals(raw, store.loadPreference(MIX_DEFINITIONS_KEY))
    }

    @Test fun readFailuresReturnAReasonWithoutAttemptingToWrite() = withStore { store ->
        var writes = 0
        val failingStore = object : CompatibilityStore by store {
            override suspend fun loadPreference(key: String): String? { throw IOException("cannot read") }
            override suspend fun savePreference(key: String, value: String) { writes++ }
        }
        val mix = MixStore(failingStore)
        assertContains(assertIs<MixLoadResult.ReadOnly>(mix.load()).reason, "読み込めません")
        assertContains(assertIs<MixWriteResult.Refused>(mix.create("new", listOf(board.url), MixOrderKind.NEW, 2)).message, "読み込めません")
        assertEquals(0, writes)
    }

    @Test fun readAndWriteCancellationAreNeverConvertedToFailureMessages() = withStore { store ->
        val readCancelled = object : CompatibilityStore by store {
            override suspend fun loadPreference(key: String): String? { throw CancellationException("read cancelled") }
        }
        assertFailsWith<CancellationException> { MixStore(readCancelled).load() }
        assertFailsWith<CancellationException> { MixStore(readCancelled).create("new", listOf(board.url), MixOrderKind.NEW, 1) }
        val writeCancelled = object : CompatibilityStore by store {
            override suspend fun savePreference(key: String, value: String) { throw CancellationException("write cancelled") }
        }
        assertFailsWith<CancellationException> { MixStore(writeCancelled).create("new", listOf(board.url), MixOrderKind.NEW, 1) }
    }

    @Test fun wrappedDecodeFailureIsParseButHttpStatusKeepsItsPriority() {
        val cause = SerializationException("malformed JSON")
        assertEquals(MixFetchFailure.PARSE, classifyMixFailure(NetworkException("cannot decode", cause = cause)))
        assertEquals(MixFetchFailure.PARSE, classifyMixFailure(cause))
        for ((code, expected) in listOf(429 to MixFetchFailure.RATE_LIMITED, 404 to MixFetchFailure.NOT_FOUND, 503 to MixFetchFailure.SERVER_ERROR)) {
            assertEquals(expected, classifyMixFailure(NetworkException("status", statusCode = code, cause = cause)))
        }
        assertEquals(MixFetchFailure.NETWORK, classifyMixFailure(IOException("disconnected")))
    }
}
