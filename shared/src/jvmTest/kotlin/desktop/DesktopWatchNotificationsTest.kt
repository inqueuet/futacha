package com.valoser.futacha.shared.desktop

import com.valoser.futacha.shared.service.CatalogWatchAlertMatch
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DesktopWatchNotificationsTest {
    private fun match(id: String) = CatalogWatchAlertMatch(id, "b", "二次元裏", "https://may.2chan.net/b/",
        "通知のテスト", "", 0, 1)

    @Test fun acceptedDeliveryIsPersistedAcrossRestartAndDuplicateMatchesAreSuppressed() = runBlocking {
        val directory = Files.createTempDirectory("futacha-notifications").toFile()
        try {
            val ledger = File(directory, "ledger.tsv")
            val delivered = mutableListOf<String>()
            val first = DesktopWatchNotifications(ledger) { _, title, body, url ->
                assertTrue(title.contains("二次元裏")); assertEquals("通知のテスト", body); delivered += url
            }
            first.notify(listOf(match("123"), match("123"))) { true }
            val restarted = DesktopWatchNotifications(ledger) { _, _, _, url -> delivered += url }
            restarted.notify(listOf(match("123"), match("456"))) { true }
            assertEquals(listOf("https://may.2chan.net/b/res/123.htm", "https://may.2chan.net/b/res/456.htm"), delivered)
        } finally { directory.deleteRecursively() }
    }

    @Test fun rejectedDeliveryRemainsRetryableAndEarlierSuccessIsPreserved() = runBlocking {
        val directory = Files.createTempDirectory("futacha-notification-failure").toFile()
        try {
            val ledger = File(directory, "ledger.tsv")
            var requests = 0
            val notifier = DesktopWatchNotifications(ledger) { _, _, _, _ ->
                if (++requests == 2) error("Notifications denied")
            }
            assertFailsWith<IllegalStateException> { notifier.notify(listOf(match("123"), match("456"))) { true } }
            val retried = mutableListOf<String>()
            DesktopWatchNotifications(ledger) { _, _, _, url -> retried += url }
                .notify(listOf(match("123"), match("456"))) { true }
            assertEquals(listOf("https://may.2chan.net/b/res/456.htm"), retried)
        } finally { directory.deleteRecursively() }
    }

    @Test fun profileChangeStopsRemainingNotifications() = runBlocking {
        val directory = Files.createTempDirectory("futacha-notification-profile").toFile()
        try {
            var active = true
            var requests = 0
            DesktopWatchNotifications(File(directory, "ledger.tsv")) { _, _, _, _ -> requests++; active = false }
                .notify(listOf(match("123"), match("456"))) { active }
            assertEquals(1, requests)
        } finally { directory.deleteRecursively() }
    }

    @Test fun modeSwitchAcquiresLockBetweenDeliveriesAndPreventsTheRest() = runBlocking {
        val directory = Files.createTempDirectory("futacha-notification-switch").toFile()
        try {
            val mutex = Mutex()
            var active = true
            var requests = 0
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val notifier = DesktopWatchNotifications(File(directory, "ledger.tsv")) { _, _, _, _ ->
                requests++; entered.complete(Unit); release.await()
            }
            val job = launch {
                notifier.notify(listOf(match("123"), match("456")), sessionMutex = mutex) { active }
            }
            entered.await()
            val switch = launch(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { active = false } }
            release.complete(Unit)
            withTimeout(2000) { switch.join(); job.join() }
            assertEquals(1, requests)
        } finally { directory.deleteRecursively() }
    }

    @Test fun cancellationDoesNotMarkUnacceptedNotification() = runBlocking {
        val directory = Files.createTempDirectory("futacha-notification-cancel").toFile()
        try {
            val ledger = File(directory, "ledger.tsv")
            val entered = CompletableDeferred<Unit>()
            val notifier = DesktopWatchNotifications(ledger) { _, _, _, _ -> entered.complete(Unit); awaitCancellation() }
            val job = launch { notifier.notify(listOf(match("123"))) { true } }
            entered.await(); job.cancelAndJoin()
            assertFalse(ledger.exists())
        } finally { directory.deleteRecursively() }
    }

    @Test fun invalidThreadCannotBeDeliveredOrRecorded() = runBlocking {
        val directory = Files.createTempDirectory("futacha-notification-invalid").toFile()
        try {
            val ledger = File(directory, "ledger.tsv")
            val notifier = DesktopWatchNotifications(ledger) { _, _, _, _ -> fail("Invalid thread reached OS") }
            assertFailsWith<IllegalArgumentException> { notifier.notify(listOf(match("../123"))) { true } }
            assertFalse(ledger.exists())
        } finally { directory.deleteRecursively() }
    }
}
