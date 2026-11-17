package com.valoser.futacha

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.compat.AndroidCompatibilityStore
import com.valoser.futacha.shared.compat.NormalizedArchiveThread
import com.valoser.futacha.shared.compat.buildArchiveReportPayload
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveReportOutboxInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val databaseName = "archive_report_outbox_${System.currentTimeMillis()}.db"
    private lateinit var store: AndroidCompatibilityStore

    @Before
    fun prepare() = runBlocking {
        store = AndroidCompatibilityStore(context, databaseName = databaseName)
        store.initialize()
    }

    @After
    fun cleanUp() {
        if (::store.isInitialized) runBlocking { store.closeForTest() }
        context.deleteDatabase(databaseName)
    }

    @Test
    fun enqueueClaimRetryAndAcceptPreserveBatchIdentity() = runBlocking {
        val now = 1_000_000L
        assertFalse(store.enqueueArchiveReport("https://example.com/res/1.htm", now).inserted)
        assertTrue(store.enqueueArchiveReport("https://may.2chan.net/b/res/2.htm#3", now).inserted)
        assertTrue(store.enqueueArchiveReport("https://may.2chan.net/b/res/1.htm", now + 1).inserted)
        assertFalse(store.enqueueArchiveReport("https://may.2chan.net/b/res/1.htm", now + 2).inserted)
        assertEquals(2, store.archiveReportOutboxStats().pendingOrRetry)
        assertNull(store.claimArchiveReportBatch(now + 14_999, "12345678-first"))

        val first = requireNotNull(store.claimArchiveReportBatch(now + 15_001, "12345678-first"))
        assertEquals(listOf("may/b/1", "may/b/2"), first.payload.threadIds)
        assertEquals("12345678-first", first.payload.requestId)
        store.markArchiveReportRetry(first.payload.requestId, now + 75_001, "network")
        assertNull(store.claimArchiveReportBatch(now + 75_000, "12345678-unused"))

        val retry = requireNotNull(store.claimArchiveReportBatch(now + 75_001, "12345678-unused"))
        assertEquals(first.payload.requestId, retry.payload.requestId)
        assertEquals(first.payload.sha256, retry.payload.sha256)
        assertEquals(1, retry.attemptCount)
        assertEquals(2, store.markArchiveReportAccepted(retry.payload.requestId, now + 76_000))
        assertEquals(0, store.archiveReportOutboxStats().pendingOrRetry)
        assertFalse(store.enqueueArchiveReport("https://may.2chan.net/b/res/1.htm", now + 77_000).inserted)
    }

    @Test
    fun staleSendingRecoversWithSameRequestId() = runBlocking {
        val now = 2_000_000L
        store.enqueueArchiveReport("https://img.2chan.net/b/res/9.htm", now)
        val first = requireNotNull(store.claimArchiveReportBatch(now + 15_000, "12345678-stale"))
        assertEquals(now + 15_000 + 10 * 60_000, store.archiveReportNextAttemptAt())
        assertEquals(1, store.recoverStaleArchiveReports(now + 15_000 + 10 * 60_000))
        val retry = requireNotNull(store.claimArchiveReportBatch(now + 15_000 + 10 * 60_000, "12345678-new"))
        assertEquals(first.payload.requestId, retry.payload.requestId)
        assertEquals(first.payload.sha256, retry.payload.sha256)
    }

    @Test
    fun splitReassignsBothHalvesAtomicallyAndPersistsNextAttempt() = runBlocking {
        val now = 3_000_000L
        (1..4).forEach { number ->
            assertTrue(store.enqueueArchiveReport("https://may.2chan.net/b/res/$number.htm", now).inserted)
        }
        val original = requireNotNull(store.claimArchiveReportBatch(now + 15_000, "12345678-original"))
        val rows = original.payload.threadIds.zip(original.payload.urls).map { (id, url) ->
            NormalizedArchiveThread(id, url)
        }
        val first = requireNotNull(buildArchiveReportPayload("12345678-split-a", rows.take(2)))
        val second = requireNotNull(buildArchiveReportPayload("12345678-split-b", rows.drop(2)))
        assertTrue(
            store.splitSendingArchiveReportBatch(
                original.payload.requestId,
                first,
                second,
                now + 15_001
            )
        )
        assertEquals(2, store.markArchiveReportAccepted(first.requestId, now + 15_002))
        assertEquals(2, store.markArchiveReportRetry(second.requestId, now + 90_000, "http_429:rate_limited"))
        assertEquals(now + 90_000, store.archiveReportNextAttemptAt())
        val retry = requireNotNull(store.claimArchiveReportBatch(now + 90_000, "12345678-unused"))
        assertEquals(second.requestId, retry.payload.requestId)
        assertEquals(second.sha256, retry.payload.sha256)
    }

    @Test
    fun boundedMaintenanceDrainsOutboxInBackgroundSizedSteps() = runBlocking {
        val now = 4_000_000L
        (1..4_501).forEach { number ->
            assertTrue(
                store.enqueueArchiveReport("https://may.2chan.net/b/res/$number.htm", now).inserted
            )
        }
        assertEquals(4_501, store.archiveReportOutboxStats().total)
        assertEquals(100, store.maintainArchiveReportOutbox(now + 1))
        assertEquals(4_401, store.archiveReportOutboxStats().total)
        repeat(4) { store.maintainArchiveReportOutbox(now + 2 + it) }
        assertEquals(4_001, store.archiveReportOutboxStats().total)
        assertEquals(1, store.maintainArchiveReportOutbox(now + 10))
        assertEquals(4_000, store.archiveReportOutboxStats().total)
    }
}
