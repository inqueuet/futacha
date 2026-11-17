package com.valoser.futacha

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.MAX_COMPAT_THREAD_SNAPSHOT_POSTS
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * L106 (measurement): how long deleting large thread caches takes with `PRAGMA secure_delete=ON`, which also
 * overwrites the freed pages. The decision is to split the deletion only if this is slow. Times go to logcat
 * (tag L106) and the instrumentation status.
 */
@RunWith(AndroidJUnit4::class)
class CompatSnapshotDeletionTimingInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "compat_l106_${System.nanoTime()}.db"
    private val boardUrl = "https://may.2chan.net/b/"
    private val boardKey = compatBoardKey(boardUrl)
    private lateinit var store: AndroidCompatibilityStore

    @Before fun prepare() = runBlocking {
        store = AndroidCompatibilityStore(context, databaseName = databaseName)
        store.initialize()
        store.upsertBoard(CompatBoard(boardKey, "may", boardUrl, boardUrl, 0))
    }

    @After fun cleanUp() {
        runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    private val body = "本文テキスト".repeat(40) + "<br>>引用行<br>" + "https://example.com/".repeat(4)

    private suspend fun fill(threads: Int): List<String> = List(threads) { index ->
        val url = "${boardUrl}res/${1_000_000 + index}.htm"
        val key = compatTabKey(url)
        store.openTab(CompatTab(key, url, url, boardKey, "may", "${1_000_000 + index}", "L106 $index",
            insertedAtEpochMillis = index.toLong(), contentUpdatedAtEpochMillis = index.toLong()))
        store.saveThreadSnapshot(CompatThreadSnapshot(key, 1, 1, posts = List(MAX_COMPAT_THREAD_SNAPSHOT_POSTS) { position ->
            CompatPostSnapshot(position, "${2_000_000 + position}", timestamp = "26/10/25(日)12:00:00", messageHtml = "$body$position",
                imageUrl = "${boardUrl}src/${3_000_000 + position}.jpg", thumbnailUrl = "${boardUrl}thumb/${3_000_000 + position}s.jpg")
        }))
        key
    }

    private fun report(what: String, value: Long, unit: String = "ms") {
        Log.i("L106", "$what: $value $unit")
        android.os.Bundle().apply { putString("stream", "L106 $what: $value $unit\n") }
            .let { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0, it) }
    }

    private inline fun timed(what: String, block: () -> Unit) {
        val start = SystemClock.elapsedRealtime()
        block()
        report(what, SystemClock.elapsedRealtime() - start)
    }

    @Test fun measureDeletingLargeThreadCaches() = runBlocking {
        val bytes = store.threadSnapshotCacheUsageBytes()
        val keys = fill(12)
        report("cache bytes after 12 threads of $MAX_COMPAT_THREAD_SNAPSHOT_POSTS posts", store.threadSnapshotCacheUsageBytes() - bytes, "bytes")
        timed("close one full thread (its cache stays)") { store.closeTabs(setOf(keys[0]), System.currentTimeMillis()) }
        timed("delete the board with 11 full threads") { store.deleteBoard(boardKey) }
        store.upsertBoard(CompatBoard(boardKey, "may", boardUrl, boardUrl, 0))
        fill(12)
        timed("clear the whole thread cache (12 full threads)") { store.clearThreadSnapshotCache() }
        fill(1)
        timed("clear the cache of one full thread") { store.clearThreadSnapshotCache() }
    }
}
