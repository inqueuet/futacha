package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

private fun board(url: String) = BoardSummary(url, url, "c", url, "")
private fun item(id: String, thumb: String? = null, replies: Int = 0) =
    CatalogItem(id, "$id.htm", "t$id", thumb, null, replyCount = replies)
private const val MAY = "https://may.2chan.net/b/"
private const val IMG = "https://img.2chan.net/b/"

class MixCoreTest {
    @Test fun decodeBrokenAndNewVersionAreReadOnly() {
        assertIs<MixLoadResult.ReadOnly>(decodeMixDefinitions("{bad"))
        assertIs<MixLoadResult.ReadOnly>(decodeMixDefinitions("""{"version":2,"mixes":[]}"""))
        assertIs<MixLoadResult.Ok>(decodeMixDefinitions(null))
    }

    @Test fun membersAreNormalizedOnRead() {
        val r = decodeMixDefinitions("""{"version":1,"mixes":[{"id":"a","name":"n","members":["http://may.2chan.net/b/futaba.php"],"createdAt":1}]}""")
        assertEquals(listOf(MAY), (r as MixLoadResult.Ok).mixes[0].members)
    }

    @Test fun resolveSkipsWithReasons() {
        val def = MixDefinition("m", "n", listOf(MAY, IMG, MAY, "https://nijiurachan.net/b/aimg/", "https://example.com/b/", "https://foo.test/"), createdAt = 1)
        val r = resolveMixMembers(def, listOf(board(MAY)), aimogeEnabled = false, sitePaused = { false }, boardPaused = { false })
        assertEquals(listOf(MAY), r.boards.map { it.url })
        assertEquals(MixSkipReason.NOT_REGISTERED, r.skipped[IMG])
        assertEquals(MixSkipReason.AIMOGE_DISABLED, r.skipped["https://nijiurachan.net/b/aimg/"])
        assertEquals(MixSkipReason.TUTORIAL_BOARD, r.skipped["https://example.com/b/"])
        assertEquals(MixSkipReason.UNSUPPORTED_SITE, r.skipped["https://foo.test/"])
    }

    @Test fun refreshPolicy() {
        val d = ::mixRefreshDecision
        assertEquals(MixRefreshDecision.REFUSE_RUNNING, d(MixRefreshTrigger.MANUAL, true, null, null, 0))
        assertEquals(MixRefreshDecision.QUEUE_AFTER_RUNNING, d(MixRefreshTrigger.ORDER_CHANGED, true, null, null, 0))
        assertEquals(MixRefreshDecision.REFUSE_TOO_SOON, d(MixRefreshTrigger.MANUAL, false, null, 1_000, 5_000))
        assertEquals(MixRefreshDecision.FETCH, d(MixRefreshTrigger.MANUAL, false, null, 1_000, 11_000))
        assertEquals(MixRefreshDecision.FETCH, d(MixRefreshTrigger.MANUAL, false, null, 9_000, 1_000)) // clock went back
        assertEquals(MixRefreshDecision.USE_CACHED, d(MixRefreshTrigger.OPEN, false, 0, null, 299_000))
        assertEquals(MixRefreshDecision.FETCH, d(MixRefreshTrigger.OPEN, false, 0, null, 300_000))
        assertEquals(MixRefreshDecision.FETCH, d(MixRefreshTrigger.ORDER_CHANGED, false, 0, 1, 2))
    }

    @Test fun cacheEvictsAndRemoves() {
        val c = MixCatalogSessionCache(maxEntries = 2)
        fun r() = MixFetchResult(1, emptyList(), emptySet(), emptyMap(), emptyMap(), 0)
        val k = { id: String -> MixCacheKey(id, MixOrderKind.NEW, 1) }
        c.put(k("a"), r()); c.put(k("b"), r()); c.put(k("c"), r())
        assertNull(c.get(k("a"))); assertNotNull(c.get(k("b")))
        c.markFinished("b", 5); c.remove("b")
        assertNull(c.get(k("b"))); assertNull(c.lastFinishedAt("b"))
        assertNotNull(c.get(k("c")))
    }

    @Test fun threadTimeFromImageName() {
        val now = 1_731_000_000_000L
        assertEquals(1_730_000_000_000L, futabaThreadCreatedAtMillis(item("1", "https://may.2chan.net/b/thumb/1730000000000s.jpg"), now))
        assertEquals(1_730_000_000_000L, futabaThreadCreatedAtMillis(item("1", "https://may.2chan.net/b/cat/1730000000000s.jpg"), now))
        assertNull(futabaThreadCreatedAtMillis(item("1", null), now))
        assertNull(futabaThreadCreatedAtMillis(item("1", "https://x/thumb/0000000000001s.jpg"), now))
        assertNull(futabaThreadCreatedAtMillis(item("1", "https://x/thumb/9999999999999s.jpg"), now))
        val f = fillMissingCreatedAt(listOf(100L, null, 300L, null))
        assertEquals(200L to true, f[1]); assertEquals(null to false, f[3])
    }

    private fun entry(b: String, id: String, rank: Int, count: Int, replies: Int = 0, t: Long? = null) =
        MixEntry(board(b), item(id, replies = replies), MixEntryKey(b, id), t, false, rank, count)

    @Test fun soudaneCountIsLabelledAndKeptOutOfTheReplySeed() {
        val so = MixEntry(board(MAY), item("1", replies = 7), MixEntryKey(MAY, "1"), null, false, 0, 1, MixCountKind.SOUDANE)
        assertEquals("そうだね7", mixCountOverride(so))
        assertNull(so.replyCountOrNull)
        val plain = entry(MAY, "2", 0, 1, replies = 4)
        assertNull(mixCountOverride(plain))
        assertEquals(4, plain.replyCountOrNull)
    }

    @Test fun orderAlternateKeepsSameNumberOfDifferentBoards() {
        val e = listOf(entry(MAY, "1", 0, 2), entry(MAY, "2", 1, 2), entry(IMG, "1", 0, 1))
        val o = MixOrder.order(e, MixOrderKind.ALTERNATE).map { it.key.asString() }
        assertEquals(listOf("${MAY}#1", "${IMG}#1", "${MAY}#2"), o)
    }

    @Test fun orderNewPutsTimelessLast() {
        val e = listOf(entry(MAY, "1", 0, 3, t = 10), entry(MAY, "2", 1, 3), entry(IMG, "1", 0, 1, t = 20))
        assertEquals(listOf("${IMG}#1", "${MAY}#1", "${MAY}#2"), MixOrder.order(e, MixOrderKind.NEW).map { it.key.asString() })
    }

    @Test fun orderMomentumHandlesSingleItemBoard() {
        val e = listOf(entry(MAY, "1", 0, 1), entry(IMG, "1", 0, 1))
        assertEquals(2, MixOrder.order(e, MixOrderKind.MOMENTUM).size)
    }

    @Test fun fetcherSurvivesFailuresCutsAndDedups() = runTest {
        val modes = mutableListOf<CatalogMode>()
        var pause = 0
        val f = MixCatalogFetcher(
            fetchPage = { b, m ->
                modes += m
                if (b.url == IMG) error("boom")
                CatalogPageContent((1..100).map { item("$it") })
            },
            loadPause = { pause++ }, nowMillis = { 123L }
        )
        val def = MixDefinition("m", "n", listOf(MAY, IMG), MixOrderKind.SO, createdAt = 0)
        val r = f.fetch(ResolvedMix(def, listOf(board(MAY), board(IMG)), emptyMap()), 7)
        assertEquals(1, pause)
        assertEquals(80, r.entries.size)
        assertTrue(MAY in r.cutByLimit)
        assertEquals(MixFetchFailure.UNKNOWN, r.failures[IMG])
        assertTrue(r.entries.all { it.countKind == MixCountKind.SOUDANE && it.replyCountOrNull == null })
        assertTrue(modes.all { it == CatalogMode.So })
        assertEquals(7, r.generation)
    }

    @Test fun fetchModeMapping() {
        assertEquals(CatalogMode.Catalog, MixOrderKind.ALTERNATE.fetchMode())
        assertEquals(CatalogMode.So, MixOrderKind.SO.fetchMode())
        assertEquals(CatalogMode.Few, MixOrderKind.FEW.fetchMode())
    }
}

class MixCellLabelTest {
    @kotlin.test.Test fun elapsedLabelsAreShortAndEstimatedOnesAreMarked() {
        val now = 10L * 24 * 3_600_000
        kotlin.test.assertEquals("12分", mixElapsedLabel(now - 12 * 60_000, false, now))
        kotlin.test.assertEquals("3時間", mixElapsedLabel(now - 3 * 3_600_000L, false, now))
        kotlin.test.assertEquals("約2日", mixElapsedLabel(now - 2 * 24 * 3_600_000L, true, now))
        kotlin.test.assertNull(mixElapsedLabel(null, false, now))
        kotlin.test.assertEquals("0分", mixElapsedLabel(now + 5_000, false, now))
    }
}
