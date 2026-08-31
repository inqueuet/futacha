package com.valoser.futacha

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.CompatHistoryEntry
import com.valoser.futacha.shared.compat.CompatCatalogSnapshot
import com.valoser.futacha.shared.compat.CompatCatalogSort
import com.valoser.futacha.shared.compat.CompatCatalogDroppedClass
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatReplyDraft
import com.valoser.futacha.shared.compat.ScrollAnchor
import com.valoser.futacha.shared.compat.CompatTab
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.MAX_COMPAT_THREAD_SNAPSHOT_POSTS
import com.valoser.futacha.shared.compat.compatBoardKey
import com.valoser.futacha.shared.compat.compatTabKey
import com.valoser.futacha.shared.model.CatalogItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompatibilitySnapshotCacheInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseName: String
    private var openStore: AndroidCompatibilityStore? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        databaseName = "compat_snapshot_${System.currentTimeMillis()}.db"
    }

    @After
    fun tearDown() {
        openStore?.let { runBlocking { it.closeForTest() } }
        openStore = null
        context.deleteDatabase(databaseName)
    }

    @Test
    fun catalogSnapshotIsAtomicNewestWinsAndKeepsSixGenerationsPerSort() = runBlocking {
        val store = newStore()
        val tab = createBoardAndTab(store, 90)
        repeat(7) { offset ->
            val revision = (offset + 1).toLong()
            assertTrue(
                store.saveCatalogSnapshot(
                    CompatCatalogSnapshot(
                        boardKey = tab.boardKey,
                        sort = CompatCatalogSort.CATALOG,
                        revision = revision,
                        fetchedAtEpochMillis = 10_000L + revision,
                        items = listOf(catalogItem("${revision}01"), catalogItem("${revision}02"))
                    )
                )
            )
        }
        assertFalse(
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    tab.boardKey,
                    CompatCatalogSort.CATALOG,
                    revision = 7L,
                    fetchedAtEpochMillis = 99_999L,
                    items = listOf(catalogItem("stale"))
                )
            )
        )
        val latest = store.loadCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG)
        assertEquals(7L, latest?.revision)
        assertEquals(10_007L, latest?.fetchedAtEpochMillis)
        assertEquals(listOf("701", "702"), latest?.items?.map { it.id })
        assertEquals(6L, store.loadCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG, 1)?.revision)
        assertEquals(3L, store.loadCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG, 4)?.revision)
        assertEquals(2L, store.loadCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG, 5)?.revision)
        assertNull(store.loadCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG, -1))
        assertNull(store.loadCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG, 6))

        assertTrue(
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    tab.boardKey,
                    CompatCatalogSort.NEW,
                    revision = 1L,
                    fetchedAtEpochMillis = 20_001L,
                    items = listOf(catalogItem("new-sort"))
                )
            )
        )
        assertEquals("new-sort", store.loadCatalogSnapshot(tab.boardKey, CompatCatalogSort.NEW)?.items?.single()?.id)
        writableDatabase(readOnly = true).use { db ->
            assertEquals(
                6,
                db.rawQuery(
                    "SELECT COUNT(*) FROM compat_catalog_snapshot WHERE board_key=? AND mode='CATALOG'",
                    arrayOf(tab.boardKey)
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            )
            assertEquals(
                0,
                db.rawQuery(
                    "SELECT COUNT(*) FROM compat_catalog_snapshot WHERE board_key=? AND mode='CATALOG' AND revision=1",
                    arrayOf(tab.boardKey)
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            )
        }

        writableDatabase().use { db ->
            db.execSQL(
                """CREATE TRIGGER fail_catalog_item_insert
                    BEFORE INSERT ON compat_catalog_item WHEN NEW.position=1
                    BEGIN SELECT RAISE(ABORT, 'injected catalog write failure'); END""".trimIndent()
            )
        }
        assertNotNull(
            runCatching {
                store.saveCatalogSnapshot(
                    CompatCatalogSnapshot(
                        tab.boardKey,
                        CompatCatalogSort.CATALOG,
                        revision = 8L,
                        fetchedAtEpochMillis = 10_008L,
                        items = listOf(catalogItem("801"), catalogItem("802"))
                    )
                )
            }.exceptionOrNull()
        )
        assertEquals(7L, store.loadCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG)?.revision)
    }

    @Test
    fun catalogDroppedTrackingKeepsLatestTwoHundredAndRemovesReappearingThreads() = runBlocking {
        val store = newStore()
        val tab = createBoardAndTab(store, 91)
        val original = (1..250).map { catalogItem(it.toString().padStart(3, '0')) }
        assertTrue(
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG, 1L, 1_000L, original),
                trackDropped = true
            )
        )
        assertTrue(
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    tab.boardKey,
                    CompatCatalogSort.CATALOG,
                    2L,
                    2_000L,
                    listOf(catalogItem("999"))
                ),
                trackDropped = true,
                requestedThreadCount = 300
            )
        )
        val dropped = store.loadDroppedCatalogItems(tab.boardKey)
        assertEquals(200, dropped.size)
        assertEquals("250", dropped.first().item.id)
        assertEquals("051", dropped.last().item.id)

        assertTrue(
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    tab.boardKey,
                    CompatCatalogSort.CATALOG,
                    3L,
                    3_000L,
                    listOf(catalogItem("250"))
                ),
                trackDropped = true,
                requestedThreadCount = 300
            )
        )
        val afterReappearance = store.loadDroppedCatalogItems(tab.boardKey)
        assertEquals(200, afterReappearance.size)
        assertTrue(afterReappearance.any { it.item.id == "999" })
        assertFalse(afterReappearance.any { it.item.id == "250" })

        assertTrue(
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(
                    tab.boardKey,
                    CompatCatalogSort.NEW,
                    1L,
                    4_000L,
                    listOf(catalogItem("untracked"))
                ),
                trackDropped = false
            )
        )
        assertEquals(afterReappearance, store.loadDroppedCatalogItems(tab.boardKey))
    }

    @Test
    fun catalogDroppedTrackingClassifiesHeadResultsAndDeletesOnlyDieRows() = runBlocking {
        val store = newStore()
        val tab = createBoardAndTab(store, 92)
        val previous = listOf(catalogItem("950"), catalogItem("850"), catalogItem("150"))
        val current = (100..1_000 step 100).map { catalogItem(it.toString()) }.reversed()
        assertTrue(
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG, 1L, 10_000L, previous),
                trackDropped = true,
                requestedThreadCount = 10
            )
        )
        assertTrue(
            store.saveCatalogSnapshot(
                CompatCatalogSnapshot(tab.boardKey, CompatCatalogSort.CATALOG, 2L, 20_000L, current),
                trackDropped = true,
                requestedThreadCount = 10,
                activeDroppedThreadIds = setOf("950")
            )
        )

        val dropped = store.loadDroppedCatalogItems(tab.boardKey).associateBy { it.item.id }
        assertEquals(CompatCatalogDroppedClass.ISOLATED, dropped.getValue("950").classification)
        assertEquals(CompatCatalogDroppedClass.DELETED, dropped.getValue("850").classification)
        assertEquals(CompatCatalogDroppedClass.DIE, dropped.getValue("150").classification)
        assertEquals(10_000L, dropped.getValue("950").lastSeenAtEpochMillis)

        assertEquals(1, store.deleteDroppedCatalogItems(tab.boardKey, CompatCatalogDroppedClass.DIE))
        val remaining = store.loadDroppedCatalogItems(tab.boardKey)
        assertEquals(setOf("950", "850"), remaining.mapTo(mutableSetOf()) { it.item.id })
    }

    @Test
    fun newestRevisionWinsAndExactly2050PostsCommitAtomicallyWithTabMetadata() = runBlocking {
        val store = newStore()
        val tab = createBoardAndTab(store, 1)
        val maximum = snapshot(tab, revision = 20L, postCount = MAX_COMPAT_THREAD_SNAPSHOT_POSTS)

        assertTrue(store.saveThreadSnapshot(maximum))
        assertFalse(store.saveThreadSnapshot(snapshot(tab, revision = 19L, postCount = 1)))
        assertFalse(store.saveThreadSnapshot(snapshot(tab, revision = 20L, postCount = 1)))

        val loaded = store.loadThreadSnapshot(tab.key)
        assertEquals(20L, loaded?.revision)
        assertEquals(MAX_COMPAT_THREAD_SNAPSHOT_POSTS, loaded?.posts?.size)
        val storedTab = store.tabs.first().single()
        assertEquals(20L, storedTab.snapshotRevision)
        assertEquals(MAX_COMPAT_THREAD_SNAPSHOT_POSTS, storedTab.replyCount)
        assertEquals(maximum.fetchedAtEpochMillis, storedTab.contentUpdatedAtEpochMillis)

        val oversized = snapshot(tab, revision = 21L, postCount = MAX_COMPAT_THREAD_SNAPSHOT_POSTS + 1)
        assertNotNull(runCatching { store.saveThreadSnapshot(oversized) }.exceptionOrNull())
        assertEquals(20L, store.loadThreadSnapshot(tab.key)?.revision)
    }

    @Test
    fun injectedMidWriteFailureRollsBackAndRetryAfterProcessRestartSucceeds() = runBlocking {
        var store = newStore()
        val tab = createBoardAndTab(store, 2)
        assertTrue(store.saveThreadSnapshot(snapshot(tab, revision = 1L, postCount = 2)))
        closeStore()

        writableDatabase().use { db ->
            db.execSQL(
                """CREATE TRIGGER fail_compat_post_insert
                    BEFORE INSERT ON compat_post WHEN NEW.position=1
                    BEGIN SELECT RAISE(ABORT, 'injected snapshot write failure'); END""".trimIndent()
            )
        }

        store = newStore()
        assertNotNull(
            runCatching { store.saveThreadSnapshot(snapshot(tab, revision = 2L, postCount = 3)) }
                .exceptionOrNull()
        )
        assertEquals(1L, store.loadThreadSnapshot(tab.key)?.revision)
        assertEquals(2, store.loadThreadSnapshot(tab.key)?.posts?.size)
        closeStore()

        writableDatabase().use { it.execSQL("DROP TRIGGER fail_compat_post_insert") }
        store = newStore()
        assertEquals(1L, store.loadThreadSnapshot(tab.key)?.revision)
        assertTrue(store.saveThreadSnapshot(snapshot(tab, revision = 2L, postCount = 3)))
        assertEquals(2L, store.loadThreadSnapshot(tab.key)?.revision)
        assertEquals(3, store.loadThreadSnapshot(tab.key)?.posts?.size)
    }

    @Test(timeout = 30_000L)
    fun realSeparateProcessDeathRollsBackOpenSnapshotTransaction() = runBlocking {
        var store = newStore()
        val tab = createBoardAndTab(store, 22)
        val original = snapshot(tab, revision = 4L, postCount = 2)
        assertTrue(store.saveThreadSnapshot(original))
        closeStore()

        val markerName = "compat_crash_${System.currentTimeMillis()}.marker"
        val marker = context.noBackupFilesDir.resolve(markerName)
        marker.delete()
        val serviceIntent = Intent().setClassName(
            context.packageName,
            "com.valoser.futacha.CompatibilitySnapshotCrashTestService"
        ).putExtra(CompatibilitySnapshotCrashTestService.EXTRA_DATABASE_NAME, databaseName)
            .putExtra(CompatibilitySnapshotCrashTestService.EXTRA_TAB_KEY, tab.key)
            .putExtra(CompatibilitySnapshotCrashTestService.EXTRA_MARKER_NAME, markerName)
        assertNotNull(context.startService(serviceIntent))

        val markerDeadline = SystemClock.elapsedRealtime() + 8_000L
        var markerState = ""
        while (SystemClock.elapsedRealtime() < markerDeadline) {
            markerState = runCatching {
                if (marker.exists()) marker.readText() else ""
            }.getOrDefault("")
            if (
                markerState.startsWith("transaction-open") ||
                markerState.startsWith("error=")
            ) {
                break
            }
            kotlinx.coroutines.delay(25L)
        }
        assertTrue(
            "Crash worker never reached its open transaction: $markerState",
            markerState.startsWith("transaction-open")
        )
        // The worker kills its own Linux process 750 ms after fsyncing the marker.
        kotlinx.coroutines.delay(1_500L)

        store = newStore()
        val recovered = store.loadThreadSnapshot(tab.key)
        assertEquals(4L, recovered?.revision)
        assertEquals(listOf("1", "2"), recovered?.posts?.map { it.postNo })
        val recoveredTab = store.tabs.first().single()
        assertEquals(4L, recoveredTab.snapshotRevision)
        assertEquals(2, recoveredTab.replyCount)
        assertEquals(original.fetchedAtEpochMillis, recoveredTab.contentUpdatedAtEpochMillis)
        marker.delete()
        Unit
    }

    @Test
    fun corruptAndWrongRevisionRowsDoNotCrashColdOfflineLoad() = runBlocking {
        var store = newStore()
        val tab = createBoardAndTab(store, 3)
        assertTrue(store.saveThreadSnapshot(snapshot(tab, revision = 5L, postCount = 2)))
        closeStore()

        writableDatabase().use { db ->
            db.execSQL(
                "UPDATE compat_post SET post_json=? WHERE tab_key=? AND revision=5 AND position=1",
                arrayOf("{broken-json", tab.key)
            )
            val stale = Json.encodeToString(
                CompatPostSnapshot.serializer(),
                post(position = 99, suffix = "stale")
            )
            db.execSQL(
                "INSERT INTO compat_post(tab_key,revision,position,post_json) VALUES(?,?,?,?)",
                arrayOf<Any>(tab.key, 4L, 99, stale)
            )
        }

        store = newStore()
        val degraded = store.loadThreadSnapshot(tab.key)
        assertEquals(5L, degraded?.revision)
        assertEquals(listOf("1"), degraded?.posts?.map { it.postNo })
        closeStore()

        writableDatabase(readOnly = true).use { db ->
            assertEquals(
                0,
                db.rawQuery(
                    "SELECT COUNT(*) FROM compat_post WHERE tab_key=? AND revision<>5",
                    arrayOf(tab.key)
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            )
            assertEquals(
                0,
                db.rawQuery(
                    "SELECT COUNT(*) FROM compat_post WHERE tab_key=? AND revision=5 AND position=1",
                    arrayOf(tab.key)
                ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
            )
        }
    }

    @Test
    fun threadCacheCleanServiceContractEvictsOnlyUnprotectedLruBodiesAndKeepsMetadata() = runBlocking {
        var clock = 1_000L
        val store = newStore(
            currentTimeMillis = { clock++ },
            quotaBytes = 8_000L
        )
        val tabs = (1..4).map { createBoardAndTab(store, it) }
        store.updateTab(tabs[1].copy(favorite = true))
        store.selectTab(tabs[3].key)
        store.saveDraft(CompatReplyDraft(tabKey = tabs[0].key, comment = "draft", updatedAtEpochMillis = 1L))

        tabs.forEachIndexed { index, tab ->
            assertTrue(store.saveThreadSnapshot(snapshot(tab, revision = (index + 1).toLong(), postCount = 1, messageSize = 3_500)))
        }

        assertNull(store.loadThreadSnapshot(tabs[0].key))
        assertNotNull(store.loadThreadSnapshot(tabs[1].key))
        assertNull(store.loadThreadSnapshot(tabs[2].key))
        assertNotNull(store.loadThreadSnapshot(tabs[3].key))
        assertEquals(4, store.tabs.first().size)
        assertEquals(4, store.history.first().size)
        assertEquals("draft", store.loadDraft(tabs[0].key)?.comment)
        assertEquals(0L, store.tabs.first().first { it.key == tabs[0].key }.snapshotRevision)
        assertEquals(0L, store.tabs.first().first { it.key == tabs[2].key }.snapshotRevision)
    }

    @Test
    fun loadingSnapshotRefreshesLruBeforeNextQuotaEviction() = runBlocking {
        var clock = 1_000L
        val store = newStore(
            currentTimeMillis = { clock++ },
            quotaBytes = 8_000L
        )
        val first = createBoardAndTab(store, 11)
        val second = createBoardAndTab(store, 12)
        assertTrue(store.saveThreadSnapshot(snapshot(first, revision = 1L, postCount = 1, messageSize = 3_500)))
        assertTrue(store.saveThreadSnapshot(snapshot(second, revision = 2L, postCount = 1, messageSize = 3_500)))

        // Neither of these tabs may remain active, otherwise active-tab protection rather
        // than access order would decide the result.
        store.selectTab(null)
        assertNotNull(store.loadThreadSnapshot(first.key))
        val third = createBoardAndTab(store, 13)
        assertTrue(store.saveThreadSnapshot(snapshot(third, revision = 3L, postCount = 1, messageSize = 3_500)))

        assertNotNull(store.loadThreadSnapshot(first.key))
        assertNull(store.loadThreadSnapshot(second.key))
        assertNotNull(store.loadThreadSnapshot(third.key))
    }

    @Test
    fun threadCacheCleanServiceForceClearRemovesOnlyBodiesAndReportsLogicalBytes() = runBlocking {
        val store = newStore(quotaProvider = { null })
        val tab = createBoardAndTab(store, 21)
        store.saveDraft(CompatReplyDraft(tabKey = tab.key, comment = "keep", updatedAtEpochMillis = 1L))
        assertTrue(store.saveThreadSnapshot(snapshot(tab, revision = 7L, postCount = 3, messageSize = 50)))
        val usage = store.threadSnapshotCacheUsageBytes()
        assertTrue(usage > 0L)

        assertEquals(usage, store.clearThreadSnapshotCache())
        assertEquals(0L, store.threadSnapshotCacheUsageBytes())
        assertNull(store.loadThreadSnapshot(tab.key))
        assertEquals(1, store.tabs.first().size)
        assertEquals(1, store.history.first().size)
        assertEquals("keep", store.loadDraft(tab.key)?.comment)
        assertEquals(0L, store.tabs.first().single().snapshotRevision)
    }

    @Test(timeout = 300_000L)
    fun oneHundredTabsByMaximumPostsWorkloadTrimsMetadataAndBoundsBodies() = runBlocking {
        val store = newStore()
        val startedAt = SystemClock.elapsedRealtime()
        val payloadReferences = mutableListOf<WeakReference<CompatThreadSnapshot>>()
        repeat(100) { index ->
            val tab = createBoardAndTab(store, 1_000 + index)
            val payload = snapshot(
                tab = tab,
                revision = (index + 1).toLong(),
                postCount = MAX_COMPAT_THREAD_SNAPSHOT_POSTS,
                messageSize = 128
            )
            payloadReferences += WeakReference(payload)
            assertTrue(
                store.saveThreadSnapshot(payload)
            )
        }
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        repeat(5) {
            System.gc()
            System.runFinalization()
            kotlinx.coroutines.delay(100L)
        }
        val retainedPayloads = payloadReferences.count { it.get() != null }
        val runtime = Runtime.getRuntime()
        val usedJavaHeap = runtime.totalMemory() - runtime.freeMemory()
        Log.i(
            "CompatSnapshotStress",
            "100x2050 elapsed=${elapsed}ms usage=${store.threadSnapshotCacheUsageBytes()} " +
                "retainedPayloads=$retainedPayloads javaHeap=$usedJavaHeap pssKb=${Debug.getPss()}"
        )
        assertTrue("Store retained $retainedPayloads complete thread payloads", retainedPayloads <= 1)

        if (InstrumentationRegistry.getArguments().getString("dumpCompatHeap") == "true") {
            val appHeap = checkNotNull(context.getExternalFilesDir(null))
                .resolve("futacha_compat_100x2050.hprof")
            Debug.dumpHprofData(appHeap.absolutePath)
            assertTrue(appHeap.length() > 0L)
            Log.i("CompatSnapshotStress", "heapDumpBytes=${appHeap.length()}")
        }

        assertEquals(100, store.tabs.first().size)
        assertEquals(100, store.history.first().size)
        assertEquals(MAX_COMPAT_THREAD_SNAPSHOT_POSTS, store.loadThreadSnapshot(store.workspace.first().activeTabKey!!)?.posts?.size)
        assertTrue(store.threadSnapshotCacheUsageBytes() <= 32L * 1024L * 1024L)
        assertTrue(elapsed < 300_000L)
        writableDatabase(readOnly = true).use { db ->
            val bodyCount = db.rawQuery("SELECT COUNT(*) FROM compat_thread_snapshot", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertTrue(bodyCount in 1 until 90)
        }
    }

    @Test
    fun threadTabAndHistoryCleanServicesUseReferenceTriggerAndTrimBoundaries() = runBlocking {
        val store = newStore(quotaProvider = { null })
        repeat(100) { index -> createBoardAndTab(store, 20_000 + index) }
        assertEquals(100, store.tabs.first().size)
        assertEquals(100, store.history.first().size)

        createBoardAndTab(store, 20_100)
        assertEquals(90, store.tabs.first().size)
        assertEquals(101, store.history.first().size)

        repeat(100) { index -> createBoardAndTab(store, 20_101 + index) }
        assertEquals(91, store.tabs.first().size)
        assertEquals(190, store.history.first().size)
    }

    @Test
    fun deleteHistoryKeepsOpenTabAndSnapshot() = runBlocking {
        val store = newStore()
        val tab = createBoardAndTab(store, 6)
        assertTrue(store.saveThreadSnapshot(snapshot(tab, revision = 3L, postCount = 2)))

        store.deleteHistory(tab.canonicalUrl)

        assertEquals(emptyList<CompatHistoryEntry>(), store.history.first())
        assertEquals(listOf(tab.key), store.tabs.first().map { it.key })
        assertNotNull(store.loadThreadSnapshot(tab.key))
        writableDatabase(readOnly = true).use { db ->
            assertEquals(
                1,
                db.scalarInt("SELECT COUNT(*) FROM compat_thread_snapshot WHERE tab_key=?", arrayOf(tab.key))
            )
            assertEquals(
                2,
                db.scalarInt("SELECT COUNT(*) FROM compat_post WHERE tab_key=?", arrayOf(tab.key))
            )
        }
    }

    @Test
    fun scrollAnchorUpdatesOpenTabAndHistoryForCloseAndReopen() = runBlocking {
        val store = newStore()
        val tab = createBoardAndTab(store, 42)
        val anchor = ScrollAnchor(
            postNo = "42-15",
            offsetPx = 27,
            fallbackIndex = 15,
            snapshotRevision = 99L
        )

        store.updateScrollAnchor(tab.key, anchor)

        assertEquals(anchor, store.tabs.first().single().scrollAnchor)
        assertEquals(anchor, store.history.first().single().scrollAnchor)
    }

    @Test
    fun staleMetadataWritesDoNotRollBackTheLatestScrollAnchor() = runBlocking {
        val store = newStore()
        val tab = createBoardAndTab(store, 43)
        val anchor = ScrollAnchor(
            postNo = "43-18",
            offsetPx = 31,
            fallbackIndex = 18,
            snapshotRevision = 100L
        )

        store.updateScrollAnchor(tab.key, anchor)
        store.updateTab(tab.copy(replyCount = tab.replyCount + 1, scrollAnchor = ScrollAnchor()))
        store.upsertHistory(
            store.history.first().single().copy(
                replyCount = tab.replyCount + 1,
                scrollAnchor = ScrollAnchor()
            )
        )

        assertEquals(anchor, store.tabs.first().single().scrollAnchor)
        assertEquals(anchor, store.history.first().single().scrollAnchor)
    }

    @Test
    fun closedTabKeepsSnapshotDeletesDraftAndUndoRestoresTabOnlyAcrossStoreRestart() = runBlocking {
        var store = newStore()
        val tab = createBoardAndTab(store, 5)
        val original = snapshot(tab, revision = 9L, postCount = 3)
        store.saveDraft(CompatReplyDraft(tabKey = tab.key, comment = "discard", updatedAtEpochMillis = 1L))
        assertTrue(store.saveThreadSnapshot(original))
        val closeTime = System.currentTimeMillis() + 60_000L
        val finalAnchor = ScrollAnchor(
            postNo = "5-2",
            offsetPx = 37,
            fallbackIndex = 2,
            snapshotRevision = 9L
        )
        val batch = store.closeTabs(
            setOf(tab.key),
            closeTime,
            mapOf(tab.key to finalAnchor)
        )
        assertEquals(finalAnchor, batch?.tabs?.single()?.tab?.scrollAnchor)
        assertEquals(finalAnchor, store.history.first().single().scrollAnchor)
        assertNull(batch?.tabs?.single()?.snapshot)
        assertNull(batch?.tabs?.single()?.draft)
        assertEquals(emptyList<CompatTab>(), store.tabs.first())
        assertEquals(listOf(tab.canonicalUrl), store.history.first().map { it.canonicalUrl })
        assertEquals(original, store.loadThreadSnapshot(tab.key))
        assertNull(store.loadDraft(tab.key))
        writableDatabase(readOnly = true).use { db ->
            assertEquals(
                1,
                db.scalarInt("SELECT COUNT(*) FROM compat_thread_snapshot WHERE tab_key=?", arrayOf(tab.key))
            )
            assertEquals(
                3,
                db.scalarInt("SELECT COUNT(*) FROM compat_post WHERE tab_key=?", arrayOf(tab.key))
            )
            assertEquals(
                0,
                db.scalarInt("SELECT COUNT(*) FROM compat_reply_draft WHERE tab_key=?", arrayOf(tab.key))
            )
        }
        closeStore()

        store = newStore()
        val durable = store.loadPendingClosedTabs(closeTime + 1L)
        assertNull(durable?.tabs?.single()?.snapshot)
        assertNull(durable?.tabs?.single()?.draft)
        store.restoreClosedTabs(checkNotNull(durable))
        assertEquals(listOf(tab.key), store.tabs.first().map { it.key })
        assertEquals(original, store.loadThreadSnapshot(tab.key))
        assertNull(store.loadDraft(tab.key))
    }

    @Test
    fun sharedThreadSnapshotCanBeLoadedByCanonicalUrlAcrossModes() = runBlocking {
        val store = newStore()
        val url = "https://may.2chan.net/b/res/777.htm"
        val snapshot = CompatThreadSnapshot(
            tabKey = compatTabKey(url),
            revision = 12L,
            fetchedAtEpochMillis = 120L,
            boardTitle = "mayb",
            posts = listOf(post(position = 0, suffix = "shared"))
        )

        assertTrue(
            store.saveSharedThreadSnapshot(
                canonicalUrl = url,
                originalUrl = "https://may.2chan.net/b/res/777.htm?mode=cat",
                boardName = "mayb",
                title = "shared thread",
                thumbnailUrl = "https://may.2chan.net/b/thumb/777.jpg",
                snapshot = snapshot
            )
        )

        val loaded = store.loadThreadSnapshotByCanonicalUrl(
            "https://MAY.2CHAN.NET//b/res/777.htm#post"
        )
        assertNotNull(loaded)
        assertEquals(snapshot.posts, loaded?.posts)
        assertEquals(snapshot.fetchedAtEpochMillis, loaded?.fetchedAtEpochMillis)
        assertEquals(0, store.tabs.first().size)
        assertEquals(0, store.history.first().size)
    }

    @Test
    fun deletedHistoryIsNotRecreatedByStaleMetadataButExplicitOpenRestoresIt() = runBlocking {
        val store = newStore()
        val tab = createBoardAndTab(store, 778)
        val stale = store.history.first().single()

        store.deleteHistory(tab.canonicalUrl)
        store.upsertHistory(stale.copy(replyCount = 999))

        assertEquals(emptyList<CompatHistoryEntry>(), store.history.first())
        assertEquals(listOf(tab.key), store.tabs.first().map { it.key })

        store.openTab(tab, stale)
        assertEquals(listOf(tab.canonicalUrl), store.history.first().map { it.canonicalUrl })
    }

    private suspend fun createBoardAndTab(store: AndroidCompatibilityStore, number: Int): CompatTab {
        val boardUrl = "https://may.2chan.net/b/"
        val boardKey = compatBoardKey(boardUrl)
        if (store.boards.first().none { it.key == boardKey }) {
            store.upsertBoard(CompatBoard(boardKey, "mayb", boardUrl, boardUrl, 0))
        }
        val url = "${boardUrl}res/$number.htm"
        val tab = CompatTab(
            key = compatTabKey(url),
            canonicalUrl = url,
            originalUrl = url,
            boardKey = boardKey,
            boardName = "mayb",
            threadNo = number.toString(),
            title = "thread-$number",
            insertedAtEpochMillis = number.toLong(),
            contentUpdatedAtEpochMillis = number.toLong()
        )
        store.openTab(
            tab,
            CompatHistoryEntry(
                canonicalUrl = url,
                originalUrl = url,
                boardKey = boardKey,
                boardName = "mayb",
                threadNo = number.toString(),
                title = tab.title,
                contentUpdatedAtEpochMillis = number.toLong()
            )
        )
        return tab
    }

    private fun snapshot(
        tab: CompatTab,
        revision: Long,
        postCount: Int,
        messageSize: Int = 8
    ): CompatThreadSnapshot = CompatThreadSnapshot(
        tabKey = tab.key,
        revision = revision,
        fetchedAtEpochMillis = 10_000L + revision,
        boardTitle = "mayb",
        posts = (0 until postCount).map { index ->
            post(index, suffix = "x".repeat(messageSize))
        }
    )

    private fun post(position: Int, suffix: String): CompatPostSnapshot = CompatPostSnapshot(
        position = position,
        postNo = (position + 1).toString(),
        timestamp = "now",
        messageHtml = "message-$suffix"
    )

    private fun catalogItem(id: String): CatalogItem = CatalogItem(
        id = id,
        threadUrl = "https://may.2chan.net/b/res/$id.htm",
        title = "catalog-$id",
        thumbnailUrl = "https://may.2chan.net/b/thumb/${id}s.jpg",
        fullImageUrl = "https://may.2chan.net/b/src/$id.jpg",
        replyCount = id.filter(Char::isDigit).takeLast(2).toIntOrNull() ?: 0
    )

    private suspend fun newStore(
        currentTimeMillis: () -> Long = System::currentTimeMillis,
        quotaBytes: Long? = null,
        quotaProvider: (() -> Long?)? = quotaBytes?.let { value -> { value } }
    ): AndroidCompatibilityStore {
        val store = AndroidCompatibilityStore(
            context = context,
            databaseName = databaseName,
            currentTimeMillis = currentTimeMillis,
            threadSnapshotQuotaOverrideBytes = quotaProvider
        )
        openStore = store
        store.initialize()
        return store
    }

    private fun closeStore() {
        openStore?.let { runBlocking { it.closeForTest() } }
        openStore = null
    }

    private fun writableDatabase(readOnly: Boolean = false): SQLiteDatabase =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(databaseName).absolutePath,
            null,
            if (readOnly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE
        )

    private fun SQLiteDatabase.scalarInt(sql: String, args: Array<String> = emptyArray()): Int =
        rawQuery(sql, args).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
