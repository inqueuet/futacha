package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.ui.board.*
import com.valoser.futacha.shared.ui.compat.COMPAT_COMMON_NG_APPLY_STORAGE_KEY
import com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey
import com.valoser.futacha.shared.ui.ftutter.*
import com.valoser.futacha.shared.ui.futaber.futaberApplyCatalogRules
import com.valoser.futacha.shared.ui.futaber.futaberPhashHiddenIds
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import java.util.concurrent.Executors
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class MixFourthAuditTest {
    private val url = "https://may.2chan.net/b/"
    private val row = MixEntry(
        BoardSummary("may", "may", "", url, ""),
        CatalogItem("1", "${url}res/1.htm", "隠したい題名", null, null, replyCount = 1),
        MixEntryKey(url, "1"), null, false, 0, 1
    )

    @Test fun commonCatalogWordsMatchNormalCatalogAndRespectCompatSwitch() = runTest {
        assertTrue(futaberApplyCatalogRules(listOf(row.item), listOf("隠したい"), null).isEmpty())
        for (policy in listOf(MixModePolicy.FUTACHA, MixModePolicy.FUTABER, MixModePolicy.COMPAT)) {
            assertTrue(calculateMixCatalogProjection(listOf(row), emptyList(), listOf("隠したい"), emptyMap(), policy).isEmpty())
        }
        val off = mapOf(COMPAT_COMMON_NG_APPLY_STORAGE_KEY to "OFF")
        assertEquals(listOf(row), calculateMixCatalogProjection(listOf(row), emptyList(), listOf("隠したい"), off, MixModePolicy.COMPAT))
    }

    @Test fun commonWordsKeepEachModesKanaMatchingBehavior() = runTest {
        val kanaRow = row.copy(item = row.item.copy(title = "カタカナ"))
        assertEquals(listOf(kanaRow), calculateMixCatalogProjection(listOf(kanaRow), emptyList(), listOf("かたかな"), emptyMap(), MixModePolicy.FUTACHA))
        assertEquals(listOf(kanaRow), calculateMixCatalogProjection(listOf(kanaRow), emptyList(), listOf("かたかな"), emptyMap(), MixModePolicy.COMPAT))
        assertTrue(calculateMixCatalogProjection(listOf(kanaRow), emptyList(), listOf("かたかな"), emptyMap(), MixModePolicy.FUTABER).isEmpty())
    }

    @Test fun futaberSharedTitleRulesRespectBoardScopeSwitchAndMixOrder() = runTest {
        val rules = listOf(FtutterNgRule("隠したい", FtutterNgType.TITLE.stored, ita = FtutterNgBoard("may", url)))
        val imgUrl = "https://img.2chan.net/b/"
        val other = row.copy(board = row.board.copy(id = "img", url = imgUrl), key = MixEntryKey(imgUrl, "1"))
        val last = other.copy(item = other.item.copy(id = "2"), key = MixEntryKey(imgUrl, "2"))
        val rows = listOf(other, row, last)
        val prefs = mapOf(FtutterKeys.NG_LIST to encodeFtutterNgList(rules))
        assertEquals(listOf(other, last), calculateMixCatalogProjection(rows, emptyList(), emptyList(), prefs, MixModePolicy.FUTABER))
        assertEquals(rows, calculateMixCatalogProjection(rows, emptyList(), emptyList(), prefs + (COMPAT_COMMON_NG_APPLY_STORAGE_KEY to "OFF"), MixModePolicy.FUTABER))
    }

    @Test fun imageNgUsesSavedSensitivityInBothDirections() {
        val rule = CompatNgRule("image", CompatNgKind.CATALOG_IMAGE_PHASH, compatBoardKey(url), "0000000000000000", 0L)
        val key = compatPreferenceStorageKey("thread", "threadImageNgPhashThreshold")
        for ((threshold, hash) in listOf(0 to "000000000000000f", 16 to "0000000000000fff")) {
            val hidden = futaberPhashHiddenIds(mapOf("1" to hash), listOf(rule), threshold).isNotEmpty()
            val resolved = mixImageNgThreshold(mapOf(key to "$threshold"), MixModePolicy.FUTABER)
            assertEquals(hidden, mixEntriesWithoutPhashNg(listOf(row), listOf(rule), mapOf(row.key.asString() to hash), resolved).isEmpty())
        }
        assertEquals(16, mixImageNgThreshold(mapOf("compat.thread.画像NG類似度閾値" to "16"), MixModePolicy.COMPAT))
    }

    @Test fun projectionRunsOnWorkerAndCancellationStopsTheWholePass() = runBlocking {
        lateinit var workerThread: Thread
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "mix-projection-worker").also { workerThread = it } }.asCoroutineDispatcher().use { worker ->
            var checkpoints = 0
            val rule = CompatNgRule("regex", CompatNgKind.CATALOG_WORD, "*", "隠したい.*", 0L, isRegex = true)
            val filtered = calculateMixCatalogProjection(listOf(row), listOf(rule), emptyList(), emptyMap(), MixModePolicy.FUTABER,
                projectEntries = { entries ->
                    assertSame(workerThread, Thread.currentThread())
                    checkpoints++
                    entries
                }, dispatcher = worker)
            assertTrue(filtered.isEmpty())
            assertEquals(1, checkpoints)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var published = false
            val job = launch {
                calculateMixCatalogProjection(listOf(row), emptyList(), emptyList(), emptyMap(), MixModePolicy.FUTABER,
                    projectEntries = { entries ->
                        entered.complete(Unit)
                        withContext(NonCancellable) { release.await() }
                        entries
                    }, dispatcher = worker)
                published = true
            }
            entered.await()
            job.cancel()
            release.complete(Unit)
            job.join()
            assertFalse(published)
        }
    }

    @Test fun commonRulePassChecksCancellationBetweenRows() {
        val rule = CompatNgRule("regex", CompatNgKind.CATALOG_WORD, "*", "other", 0L, isRegex = true)
        var checked = 0
        assertFailsWith<CancellationException> {
            mixEntriesWithoutNg(listOf(row, row), listOf(rule)) {
                if (++checked == 2) throw CancellationException("superseded")
            }
        }
        assertEquals(2, checked)
    }

    @Test fun displayClockAdvancesLabelsWithoutAnyRepository() = runTest {
        val labels = mutableListOf<String?>()
        val collect = launch {
            mixDisplayTimes { 60_000L + testScheduler.currentTime }.take(3).toList().forEach {
                labels += mixElapsedLabel(0, false, it)
            }
        }
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        collect.join()
        assertEquals(listOf<String?>("1分", "2分", "3分"), labels)
    }
}
