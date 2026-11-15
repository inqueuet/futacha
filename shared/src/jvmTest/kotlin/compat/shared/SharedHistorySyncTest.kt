package com.valoser.futacha.shared.compat.shared

import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatibilityHistorySharedMetadata
import com.valoser.futacha.shared.compat.mergeCompatibilityHistory
import com.valoser.futacha.shared.compat.planModernHistoryImport
import com.valoser.futacha.shared.compat.shared.SingleMode.may
import com.valoser.futacha.shared.compat.shared.SingleMode.withStores
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.FutachaThreadHistoryContext
import com.valoser.futacha.shared.ui.dismissHistoryEntry
import com.valoser.futacha.shared.ui.recordFutachaVisitedThread
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The history of the four modes as one list (plan B3, compat/shared/SharedHistory.kt): copied both ways whatever mode is
 * shown, never deleted by a difference, and a deletion does not come back.
 */
class SharedHistorySyncTest {
    private val now get() = Clock.System.now().toEpochMilliseconds()
    private fun mayUrl(threadId: String) = SingleMode.threadUrl(may, threadId)

    private fun visit(threadId: String, at: Long) = CompatHistoryEntry(
        canonicalUrl = mayUrl(threadId), originalUrl = mayUrl(threadId),
        boardKey = compatBoardKey("https://may.2chan.net/b/"), boardName = "may", threadNo = threadId,
        title = "スレ$threadId", replyCount = 3, contentUpdatedAtEpochMillis = at, lastVisitedEpochMillis = at,
        scrollAnchor = ScrollAnchor()
    )

    private suspend fun openInFutacha(state: AppStateStore, threadId: String, at: Long) {
        val context = FutachaThreadHistoryContext("スレ$threadId", mayUrl(threadId), 4, "")
        recordFutachaVisitedThread(state, state.history.first(), threadId, may, context, currentTimeMillis = at)
    }

    /** Runs [block] with the shared history copy running, as the screen of any mode does. */
    private fun withSync(block: suspend CoroutineScope.(DesktopCompatibilityStore, AppStateStore, List<Throwable>) -> Unit) =
        withStores { compat, state -> coroutineScope {
            val failures = CopyOnWriteArrayList<Throwable>()
            val sync = launch(Dispatchers.Default) {
                synchronizeSharedHistory(state, compat, importDebounceMillis = 50) { _, failure -> failures += failure }
            }
            try { block(compat, state, failures) } finally { sync.cancel() }
        } }

    private suspend fun waitUntil(what: String, condition: suspend () -> Boolean) {
        try { withTimeout(10_000) { while (!condition()) delay(20) } }
        catch (timeout: kotlinx.coroutines.TimeoutCancellationException) { throw AssertionError("timed out: $what", timeout) }
    }

    private suspend fun AppStateStore.threads() = history.first().map { it.threadId }.toSet()
    private suspend fun DesktopCompatibilityStore.threads() = history.first().map { it.threadNo }.toSet()

    @Test fun aThreadReadInToshiakiShowsInTheAppStateModesWithoutASwitch() = withSync { compat, state, failures ->
        compat.recordHistoryVisit(visit("100", now))
        waitUntil("the AppState history gets the row") { "100" in state.threads() }
        assertTrue(failures.isEmpty(), failures.toString())
    }

    @Test fun aThreadOpenedInAnAppStateModeShowsInToshiakiWithoutASwitch() = withSync { compat, state, failures ->
        openInFutacha(state, "200", now)
        waitUntil("the compatibility history gets the row") { "200" in compat.threads() }
        assertTrue(failures.isEmpty(), failures.toString())
    }

    @Test fun aDifferenceNeverDeletes() = withSync { compat, state, _ ->
        openInFutacha(state, "300", now)
        compat.recordHistoryVisit(visit("301", now))
        waitUntil("both rows in both stores") { state.threads() == setOf("300", "301") && compat.threads() == setOf("300", "301") }
        // A row missing on one side (here taken away there alone) is not a deletion of the other side's.
        compat.deleteHistory(mayUrl("301"))
        state.removeHistoryEntry(state.history.first().first { it.threadId == "300" })
        delay(400)
        assertTrue("301" in state.threads(), "the AppState row stays")
        assertTrue("300" in compat.threads(), "the compatibility row stays")
    }

    @Test fun aDeletionDoesNotComeBackThroughTheCopy() = withSync { compat, state, failures ->
        openInFutacha(state, "400", now)
        waitUntil("copied") { "400" in compat.threads() }
        dismissHistoryEntry(state, autoSavedThreadRepository = null, compatibilityStore = compat, entry = state.history.first().single())
        delay(400)
        assertEquals(emptySet(), state.threads())
        assertEquals(emptySet(), compat.threads())
        assertTrue(failures.isEmpty(), failures.toString())
    }

    @Test fun aThreadOpenedAgainComesBackInBothEvenWithAClockSetBack() = withSync { compat, state, _ ->
        openInFutacha(state, "500", now)
        waitUntil("copied") { "500" in compat.threads() }
        dismissHistoryEntry(state, autoSavedThreadRepository = null, compatibilityStore = compat, entry = state.history.first().single())
        // Opened again with a visit time before the deletion (a clock set back): the open forgets both tombstones.
        openInFutacha(state, "500", now - 60_000)
        waitUntil("back in the AppState history") { "500" in state.threads() }
        waitUntil("back in the compatibility history") { "500" in compat.threads() }
    }

    @Test fun aThreadOpenedAgainInToshiakiComesBackInTheAppStateModesEvenWithAClockSetBack() = withSync { compat, state, _ ->
        openInFutacha(state, "600", now)
        waitUntil("copied") { "600" in compat.threads() }
        dismissHistoryEntry(state, autoSavedThreadRepository = null, compatibilityStore = compat, entry = state.history.first().single())
        val old = visit("600", now - 60_000)
        // What CompatibilityApp does before recordHistoryVisit.
        state.forgetHistoryTombstones(mergeCompatibilityHistory(emptyList(), listOf(old), state.boards.first()))
        compat.recordHistoryVisit(old)
        waitUntil("back in the AppState history") { "600" in state.threads() }
    }

    @Test fun restoredHistoryShowsInEveryModeEvenIfDeletedBefore() = withSync { compat, state, _ ->
        openInFutacha(state, "700", now)
        waitUntil("copied") { "700" in compat.threads() }
        val backup = compat.exportSettingsBackup()
        dismissHistoryEntry(state, autoSavedThreadRepository = null, compatibilityStore = compat, entry = state.history.first().single())
        assertEquals(emptySet(), state.threads())
        // In としあき(仮) (no boards merged into AppState) the history still reaches it.
        withSharedBoardsRestore(compat, appStateStore = null, keep = { true }, historyStore = state) {
            compat.importSettingsBackup(backup, restoreUserSettings = true, restoreNgRules = false)
        }
        assertTrue("700" in compat.threads(), "restored in としあき(仮)")
        assertTrue("700" in state.threads(), "and at once in the AppState modes")
    }

    @Test fun theCopyStopsWritingOnceBothSidesAgree() = withSync { compat, state, failures ->
        openInFutacha(state, "800", now)
        compat.recordHistoryVisit(visit("801", now + 1))
        openInFutacha(state, "802", now + 2)
        waitUntil("settled") { state.threads() == setOf("800", "801", "802") && compat.threads() == setOf("800", "801", "802") }
        delay(500)
        val appStateWrites = AtomicInteger()
        val compatWrites = AtomicInteger()
        coroutineScope {
            val a = launch { state.history.drop(1).collect { appStateWrites.incrementAndGet() } }
            val c = launch { compat.history.drop(1).collect { compatWrites.incrementAndGet() } }
            delay(1_000)
            a.cancel(); c.cancel()
        }
        assertEquals(0, appStateWrites.get(), "no AppState write once agreed")
        assertEquals(0, compatWrites.get(), "no compatibility write once agreed")
        assertTrue(failures.isEmpty(), failures.toString())
    }

    @Test fun onePassOverTwentyThousandRowsIsQuick() {
        val boards = SingleMode.boards
        val modern = (1..20_000).map { i ->
            ThreadHistoryEntry(
                threadId = "${1_000_000 + i}", boardId = "b", title = "スレ$i", titleImageUrl = "", boardName = "may",
                boardUrl = "https://may.2chan.net/b/res/${1_000_000 + i}.htm", lastVisitedEpochMillis = i.toLong(), replyCount = 5
            )
        }
        val compat = (1..200).map { i -> visit("${2_000_000 + i}", i.toLong()) }
        val keys = setOf(compatBoardKey("https://may.2chan.net/b/"))
        repeat(2) { // the first round warms the JIT
            val started = System.nanoTime()
            modernHistorySharedMetadata(modern)
            compatibilityHistorySharedMetadata(compat)
            val merged = mergeCompatibilityHistory(modern, compat, boards)
            planModernHistoryImport(merged, compat, keys, { null }, historyLimit = 200, retain = { it.sortedByDescending(CompatHistoryEntry::lastVisitedEpochMillis).take(200) })
            val millis = (System.nanoTime() - started) / 1_000_000
            assertEquals(20_200, merged.size)
            if (it == 1) assertTrue(millis < 3_000, "one pass took $millis ms")
        }
    }

    @Test fun everyOsRunsTheOneCopyAndTheOldOneWayImportsAreGone() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "shared/src").isDirectory }
        fun source(path: String) = File(root, path).readText()
        val bridge = source("shared/src/commonMain/kotlin/compat/ProfileDataBridge.kt")
        assertTrue("synchronizeSharedHistory" in bridge && "mergeCompatibilityHistory" !in bridge, "Android: the one copy")
        val ios = source("shared/src/iosMain/kotlin/IosProfileDataSynchronization.kt")
        assertTrue("synchronizeSharedHistory" in ios && "mergeCompatibilityHistory" !in ios, "iOS: the one copy")
        val desktop = source("shared/src/jvmMain/kotlin/desktop/DesktopApp.kt")
        assertTrue("synchronizeSharedHistory(graph.stateStore, graph.compatibility)" in desktop, "desktop: the one copy")
        val activity = source("app-android/src/main/java/com/valoser/futacha/MainActivity.kt")
        assertEquals(1, Regex("importModernHistory\\(").findAll(activity).count(), "Android: only the switch imports")
        val controller = source("shared/src/iosMain/kotlin/MainViewController.kt")
        assertEquals(1, Regex("importModernHistory\\(").findAll(controller).count(), "iOS: only the switch imports")
    }

    @Test fun toshiakisDeletionOfOneRowTakesEveryAppStateRowOfThatThread() = withStores { compat, state ->
        // The same thread under two board ids (the board's own and the address-derived one), as an old import left it.
        val url = mayUrl("900")
        state.importHistoryEntries(listOf(
            ThreadHistoryEntry("900", "b", "スレ900", "", "may", url, now, replyCount = 2),
            ThreadHistoryEntry("900", compatBoardKey("https://may.2chan.net/b/"), "スレ900", "", "may", url, now - 1, replyCount = 2)
        ))
        assertEquals(2, state.history.first().size)
        val rows = com.valoser.futacha.shared.ui.findModernHistoryEntriesFor(state.history.first(), url)
        assertEquals(2, rows.size)
        com.valoser.futacha.shared.ui.dismissHistoryEntries(state, autoSavedThreadRepository = null, compatibilityStore = compat, entries = rows)
        assertTrue(state.history.first().isEmpty(), "both rows go")
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "shared/src").isDirectory }
        val app = File(root, "shared/src/commonMain/kotlin/ui/compat/CompatibilityApp.kt").readText()
        // One deletion uses the helper; the other (audit 2026-11-11 P2-M1) groups every AppState row of each thread in one pass.
        assertEquals(1, Regex("findModernHistoryEntriesFor\\(").findAll(app).count(), "the single-row deletion of としあき(仮)")
        assertTrue(".groupBy({ it.first }, { it.second })" in app && "found[entry.canonicalUrl].orEmpty()" in app, "the batch keeps every row of the thread")
        assertTrue("findModernHistoryEntryFor(" !in app)
    }
}
