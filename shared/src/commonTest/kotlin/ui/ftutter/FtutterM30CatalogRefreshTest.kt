package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.CatalogItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class FtutterM30CatalogRefreshTest {
    @Test fun previouslyVisitedBoardRequiresARequestOutsideTabNavigation() {
        val holder = cachedHolder()
        holder.load = FtutterCatalogLoad("b|CATALOG")
        assertNull(holder.reusableLoad("a", "a|CATALOG", 0))
    }

    @Test fun previouslyUsedSortRequiresARequestEvenWhenTabRefreshIsOff() {
        val holder = cachedHolder()
        assertNull(holder.reusableLoad("a", "a|CATALOG", 0))
    }

    @Test fun anExplicitRevisitOverridesAStoredCatalog() {
        val holder = cachedHolder()
        holder.openBoard("a")
        assertNull(holder.reusableLoad("a", "a|CATALOG", holder.refreshTick))
    }

    @Test fun returningFromAChildReusesTheCurrentSuccessfulSnapshotButManualRefreshDoesNot() {
        val holder = cachedHolder()
        assertSame(holder.load, holder.reusableLoad("a", "a|NEW", 0))
        holder.refreshTick++
        assertNull(holder.reusableLoad("a", "a|NEW", holder.refreshTick))
    }
    @Test fun explicitBoardOpenNeverUsesTheTabPreference() {
        for (refresh in listOf(false, true)) {
            val holder = cachedHolder()
            holder.openBoard("a", refreshOnTabMove = refresh)
            assertEquals(1, holder.refreshTick)
            assertNull(holder.takeTabCache("a", "a|CATALOG"))
        }
    }

    @Test fun favoriteTabMovesReuseOnlyWhenTheSettingIsOff() {
        val holder = cachedHolder()
        val cached = holder.cached("a|CATALOG")
        holder.openBoard("a", tabMove = true)
        val reused = holder.reusableLoad("a", "a|CATALOG", holder.refreshTick)
        assertEquals(cached?.items, reused?.items)
        assertEquals(holder.refreshTick, reused?.tick)
        holder.openBoard("a", tabMove = true, refreshOnTabMove = true)
        assertNull(holder.reusableLoad("a", "a|CATALOG", holder.refreshTick))
        assertEquals(2, holder.refreshTick)
    }

    @Test fun tabReuseIsConsumedSoChangingOrderAlwaysFetches() {
        val holder = cachedHolder()
        holder.openBoard("a", tabMove = true)
        holder.takeTabCache("a", "a|CATALOG")
        assertNull(holder.takeTabCache("a", "a|NEW"))
    }

    @Test fun anUncachedTabStillConsumesItsOneTimeReusePermission() {
        val holder = cachedHolder()
        holder.openBoard("a", tabMove = true)
        assertNull(holder.takeTabCache("a", "a|OLD"))
        assertNull(holder.takeTabCache("a", "a|CATALOG"))
    }

    @Test fun explicitNavigationReplacesPendingTabReuse() {
        val holder = cachedHolder()
        holder.openBoard("a", tabMove = true)
        holder.openBoard("a")
        assertNull(holder.takeTabCache("a", "a|CATALOG"))
    }

    /** L144：取得中のまま残った記録は、戻った時に再利用しない（取り直す）。 */
    @Test fun aRecordStillMarkedLoadingIsNotReused() {
        val holder = cachedHolder()
        holder.load = holder.load!!.copy(loading = true)
        assertNull(holder.reusableLoad("a", "a|NEW", 0))
    }

    /** L144：離れて取り消された取得は、自分の「取得中」の記録を取得前の一覧へ戻す。新しい取得の記録は残す。 */
    @Test fun aCancelledFetchRestoresTheRowsItKeptUnlessANewerFetchReplacedThem() {
        val before = cachedHolder().load!!
        val started = before.copy(tick = 1, loading = true, error = null)
        assertSame(before, ftutterCatalogLoadAfterCancel(started, started, before))
        val newer = before.copy(tick = 2)
        assertSame(newer, ftutterCatalogLoadAfterCancel(newer, started, before))
        assertNull(ftutterCatalogLoadAfterCancel(null, started, before))
    }

    private fun cachedHolder() = FtutterCatalogHolder().apply {
        val items = listOf(CatalogItem(id = "123", title = "test", thumbnailUrl = null, fullImageUrl = null,
            threadUrl = "https://may.2chan.net/b/res/123.htm", replyCount = 1))
        load = FtutterCatalogLoad("a|CATALOG", items = items)
        load = FtutterCatalogLoad("a|NEW", items = items)
    }
}
