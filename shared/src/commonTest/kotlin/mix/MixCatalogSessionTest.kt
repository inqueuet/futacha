package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MixCatalogSessionTest {
    private val may = BoardSummary("may", "may", "", "https://may.2chan.net/b/", "")
    private val img = BoardSummary("img", "img", "", "https://img.2chan.net/b/", "")
    private fun definition(boards: List<BoardSummary>, order: MixOrderKind = MixOrderKind.ALTERNATE) =
        MixDefinition("mix-test", "test", boards.map { it.url }, order, 1)
    private fun result(mix: ResolvedMix, token: Long) = MixFetchResult(token, mix.boards.map {
        MixEntry(it, CatalogItem("1", "${it.url}res/1.htm", it.name, null, null, replyCount = 0),
            MixEntryKey(it.url, "1"), null, false, 0, 1)
    }, emptySet(), emptyMap(), mix.skipped, 1_000)
    private data class Pending(val mix: ResolvedMix, val token: Long, val complete: CompletableDeferred<Unit> = CompletableDeferred())

    @Test fun obsoleteOrderResponseCannotPolluteNewOrderCache() = runTest {
        val pending = Channel<Pending>(Channel.UNLIMITED)
        val cache = MixCatalogSessionCache()
        val boards = listOf(may, img)
        val session = MixCatalogSession(backgroundScope, cache, { 1_000 },
            resolve = { def, all -> resolveMixMembers(def, all, true, { false }, { false }) },
            fetch = { mix, token ->
                val response = Pending(mix, token)
                pending.send(response)
                // A superseded order may finish, but must not publish its obsolete answer.
                withContext(NonCancellable) { response.complete.await() }
                result(mix, token)
            })
        session.show(definition(boards), boards)
        runCurrent()
        val old = pending.receive()
        session.show(definition(boards, MixOrderKind.NEW), boards)
        runCurrent()
        assertTrue(pending.tryReceive().isFailure)
        old.complete.complete(Unit)
        runCurrent()
        val newest = pending.receive()
        assertEquals(MixOrderKind.NEW, newest.mix.definition.order)
        newest.complete.complete(Unit)
        runCurrent()
        old.complete.complete(Unit)
        runCurrent()
        assertEquals(newest.token, session.state.value.result?.generation)
        val hash = boards.joinToString("|") { it.url }.hashCode()
        assertNull(cache.get(MixCacheKey("mix-test", MixOrderKind.ALTERNATE, hash)))
        assertEquals(newest.token, cache.get(MixCacheKey("mix-test", MixOrderKind.NEW, hash))?.generation)
        assertFalse(session.state.value.running)
        session.close()
    }

    @Test fun replacingMembersWhileFetchingStartsNewRequestAndRejectsOldRows() = runTest {
        val pending = Channel<Pending>(Channel.UNLIMITED)
        val boards = listOf(may, img)
        val session = MixCatalogSession(backgroundScope, MixCatalogSessionCache(), { 1_000 },
            resolve = { def, all -> resolveMixMembers(def, all, true, { false }, { false }) },
            fetch = { mix, token ->
                val response = Pending(mix, token)
                pending.send(response)
                withContext(NonCancellable) { response.complete.await() }
                result(mix, token)
            })
        session.show(definition(boards), boards)
        runCurrent()
        val old = pending.receive()
        session.show(definition(listOf(img)), boards)
        runCurrent()
        val newest = pending.receive()
        assertEquals(listOf(img), newest.mix.boards)
        old.complete.complete(Unit)
        runCurrent()
        assertNull(session.state.value.result)
        assertTrue(session.state.value.running)
        newest.complete.complete(Unit)
        runCurrent()
        assertEquals(listOf(img), session.state.value.result?.entries?.map { it.board })
        session.close()
    }

    @Test fun pauseIsLoadedBeforeResolutionAndRecheckedAfterExpiry() = runTest {
        val aimoge = may.copy(id = "aimoge-aimg", url = "https://nijiurachan.net/b/aimg/")
        val def = definition(listOf(aimoge))
        var loaded = false
        var paused = false
        var now = 1_000L
        val fetched = mutableListOf<ResolvedMix>()
        val session = MixCatalogSession(backgroundScope, MixCatalogSessionCache(), { now },
            resolve = { definition, boards ->
                resolveMixForFetch(definition, boards, true, loadPause = {
                    loaded = true
                    paused = now < 10_000
                }, sitePaused = { assertTrue(loaded); paused }, boardPaused = { false })
            }, fetch = { mix, token -> fetched += mix; result(mix, token) })
        session.show(def, listOf(aimoge))
        runCurrent()
        assertTrue(fetched.single().boards.isEmpty())
        assertEquals(MixSkipReason.SITE_PAUSED, session.state.value.result?.skipped?.get(aimoge.url))
        now = 20_000
        loaded = false
        session.refresh()
        runCurrent()
        assertEquals(listOf(aimoge), fetched.last().boards)
        assertTrue(session.state.value.result!!.skipped.isEmpty())
        now = 40_000
        paused = true
        // A pause that starts while the screen is open must also be checked again.
        val refreshed = resolveMixForFetch(def, listOf(aimoge), true, {}, { paused }, { false })
        assertTrue(refreshed.boards.isEmpty())
        session.close()
    }

    @Test fun cachedPartialFailureSurvivesLeavingAndReturning() = runTest {
        val cache = MixCatalogSessionCache()
        var fetches = 0
        fun session() = MixCatalogSession(backgroundScope, cache, { 2_000 },
            resolve = { def, boards -> resolveMixMembers(def, boards, true, { false }, { false }) },
            fetch = { mix, token ->
                fetches++
                result(mix, token).copy(failures = mapOf(img.url to MixFetchFailure.TIMEOUT))
            })
        val first = session()
        first.show(definition(listOf(may, img)), listOf(may, img))
        runCurrent()
        first.close()
        val returned = session()
        val renamedMay = may.copy(id = "another-mode-may", name = "Renamed may")
        returned.show(definition(listOf(may, img)), listOf(renamedMay, img))
        runCurrent()
        assertEquals(1, fetches)
        assertEquals(renamedMay, returned.state.value.result?.entries?.first()?.board)
        assertEquals(MixFetchFailure.TIMEOUT, returned.state.value.result?.failures?.get(img.url))
        returned.close()
    }
    @Test fun modesWithDifferentFetchContractsDoNotShareCachedResults() = runTest {
        val cache = MixCatalogSessionCache()
        var fetches = 0
        fun session(namespace: String) = MixCatalogSession(backgroundScope, cache, { 1_000 },
            resolve = { def, boards -> resolveMixMembers(def, boards, true, { false }, { false }) },
            fetch = { mix, token -> fetches++; result(mix, token) }, cacheNamespace = namespace)
        val a = session("FUTABER")
        a.show(definition(listOf(may)), listOf(may)); runCurrent(); a.close()
        val b = session("FTUTTER")
        b.show(definition(listOf(may)), listOf(may)); runCurrent(); b.close()
        assertEquals(2, fetches)
        val returned = session("FUTABER")
        returned.show(definition(listOf(may)), listOf(may)); runCurrent(); returned.close()
        assertEquals(2, fetches)
    }
    @Test fun disabledSessionDoesNotResolveOrFetchEvenAfterRestoreOrRefresh() = runTest {
        var enabled = false
        var resolves = 0
        var fetches = 0
        val session = MixCatalogSession(backgroundScope, MixCatalogSessionCache(), { 1_000 },
            resolve = { def, boards -> resolves++; resolveMixMembers(def, boards, true, { false }, { false }) },
            fetch = { mix, token -> fetches++; result(mix, token) }, isEnabled = { enabled })
        session.show(definition(listOf(may)), listOf(may)); session.refresh(); runCurrent()
        assertEquals(0, resolves); assertEquals(0, fetches)
        enabled = true
        session.show(definition(listOf(may)), listOf(may)); runCurrent()
        assertEquals(1, fetches)
        enabled = false
        session.refresh(); runCurrent()
        assertNull(session.state.value.result)
        assertEquals(1, fetches)
        enabled = true
        session.show(definition(listOf(may)), listOf(may)); runCurrent()
        assertNotNull(session.state.value.result)
        session.close()
    }

}
