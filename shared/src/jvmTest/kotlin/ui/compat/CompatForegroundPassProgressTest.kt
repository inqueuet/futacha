package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE
import com.valoser.futacha.shared.compat.COMPAT_WATCH_RULES_KEY
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatWatchRule
import com.valoser.futacha.shared.compat.DesktopCompatibilityStore
import com.valoser.futacha.shared.compat.encodeValidCompatWatchRules
import com.valoser.futacha.shared.model.CatalogItem
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogFetchSettings
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import com.valoser.futacha.shared.util.JvmFileSystem
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Audit M21: the foreground loop of としあき(仮) is cancelled whenever a child screen opens. A pass that was cut off goes on
 * from where it stopped when the loop starts again, instead of fetching the first boards again.
 */
class CompatForegroundPassProgressTest {
    private val boards = (1..3).map { CompatBoard("b$it", "板$it", "https://may.2chan.net/$it/", "https://may.2chan.net/$it/futaba.php", it) }
    private val preferences = mapOf(COMPAT_WATCH_RULES_KEY to encodeValidCompatWatchRules(listOf(CompatWatchRule("猫"))))

    @Test
    fun aPassCutOffByAChildScreenGoesOnFromWhereItStopped() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("futacha-foreground").toFile()
        val store = DesktopCompatibilityStore(JvmFileSystem(directory))
        try {
            store.initialize()
            val fetched = Collections.synchronizedList(mutableListOf<String>())
            var hold: CompletableDeferred<Unit>? = CompletableDeferred()
            val secondStarted = CompletableDeferred<Unit>()
            val repository = object : BoardRepository by FakeBoardRepository() {
                override suspend fun getCatalogWithSettings(board: String, mode: CatalogMode, settings: CatalogFetchSettings): List<CatalogItem> {
                    fetched += board
                    if (board == boards[1].originalUrl) {
                        secondStarted.complete(Unit)
                        hold?.await()
                    }
                    return emptyList()
                }
            }
            val progress = CompatForegroundPassProgress()
            fun loop() = launch {
                runCompatForegroundRefreshLoop(
                    store = store, repository = repository, platformContext = null,
                    preferences = { preferences }, tabs = { emptyList() }, boards = { boards }, histories = { emptyList() },
                    // Nothing is stored here, so the pass keeps the same identity across the restart.
                    persist = { _, _ -> }, progress = progress
                )
            }
            // A child screen opens while the second board is being fetched: the loop is cancelled.
            val first = loop()
            withTimeout(10_000) { secondStarted.await() }
            first.cancelAndJoin()
            hold = null
            // Back on the main screen: the pass goes on with the boards it had not finished.
            val second = loop()
            withTimeout(10_000) { while (fetched.size < 4) yield() }
            second.cancelAndJoin()
            assertEquals(listOf(boards[0], boards[1], boards[1], boards[2]).map { it.originalUrl }, fetched.toList())
        } finally {
            store.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun aNewPassStartsAfreshOnceTheCheckTimesChange() {
        val progress = CompatForegroundPassProgress()
        progress.begin(compatForegroundPassKey(emptyMap()))
        progress.markDone("watch:b1")
        progress.begin(compatForegroundPassKey(emptyMap()))
        assert(progress.isDone("watch:b1"))
        progress.begin(compatForegroundPassKey(mapOf(COMPAT_BACKGROUND_UPDATE_TIME_PREFERENCE to "1")))
        assert(!progress.isDone("watch:b1"))
    }

    /**
     * Re-audit L10: the watch time, written during the pass, does not start the pass again (a pass cut off in the
     * existence probes went back to the first board), and a finished pass starts the next afresh even when the times
     * could not be written.
     */
    @Test
    fun theWatchTimeDoesNotRestartThePassAndAFinishedPassStartsAfresh() {
        val progress = CompatForegroundPassProgress()
        progress.begin(compatForegroundPassKey(emptyMap()))
        progress.markDone("tabs:b1")
        progress.begin(compatForegroundPassKey(mapOf(COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE to "1")))
        assert(progress.isDone("tabs:b1"))
        progress.finish()
        progress.begin(compatForegroundPassKey(mapOf(COMPAT_BACKGROUND_WATCH_TIME_PREFERENCE to "1")))
        assert(!progress.isDone("tabs:b1"))
    }
}
