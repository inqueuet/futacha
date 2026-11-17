package com.valoser.futacha.shared.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Audit 2026-11-11 L1: records from the foreground and the background at once must not drop each other. */
class SiteRefreshPauseConcurrencyTest {
    @AfterTest fun clean() = SiteRefreshPause.reset()

    @Test
    fun boardPausesRecordedAtOnceAreAllKept() {
        SiteRefreshPause.reset()
        val threads = 8
        val perThread = 400
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        repeat(threads) { t ->
            pool.execute {
                start.await()
                repeat(perThread) { i -> SiteRefreshPause.recordBoardGone("https://nijiurachan.net/b/t$t-$i/") }
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(60, TimeUnit.SECONDS)
        assertEquals(threads * perThread, SiteRefreshPause.snapshot().boards.size)
    }
}
