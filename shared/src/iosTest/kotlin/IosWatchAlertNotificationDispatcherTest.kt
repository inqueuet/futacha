package com.valoser.futacha.shared

import com.valoser.futacha.shared.compat.COMPAT_WATCH_ENABLED_KEY
import com.valoser.futacha.shared.compat.COMPAT_WATCH_NOTIFY_KEY
import com.valoser.futacha.shared.compat.COMPAT_WATCH_RULES_KEY
import com.valoser.futacha.shared.compat.CompatWatchRule
import com.valoser.futacha.shared.compat.encodeValidCompatWatchRules
import com.valoser.futacha.shared.service.CatalogWatchAlertMatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** G-10: background notification posting never prompts, never holds the ledger across it, records only posted matches. */
class IosWatchAlertNotificationDispatcherTest {
    private val match = CatalogWatchAlertMatch("1", "b", "二次元裏", "https://img.2chan.net/b/", "タイトル", "", 3, 10L)

    private class Ledger {
        val events = mutableListOf<String>()
        val notified = mutableSetOf<String>()
        fun filter(matches: List<CatalogWatchAlertMatch>) = matches.filterNot { it.identityKey in notified }
        fun mark(matches: List<CatalogWatchAlertMatch>) {
            events += "mark"
            matches.forEach { notified += it.identityKey }
        }
    }

    @Test
    fun withoutPermissionNothingIsRecordedSoTheMatchIsNotifiedLater() = runBlocking {
        val ledger = Ledger()
        var authorized = false
        val dispatcher = IosWatchAlertNotificationDispatcher(
            isAuthorized = { authorized },
            filterNew = ledger::filter,
            markNotified = ledger::mark,
            post = { ledger.events += "post"; true }
        )
        assertEquals(emptyList(), dispatcher.notifyNew(listOf(match)))
        assertEquals(emptyList<String>(), ledger.events)

        authorized = true
        assertEquals(listOf(match), dispatcher.notifyNew(listOf(match)))
        assertEquals(listOf("post", "mark"), ledger.events)
        assertEquals(emptyList(), dispatcher.notifyNew(listOf(match)), "a posted match is not repeated")
    }

    @Test
    fun aFailedPostIsNotRecorded() = runBlocking {
        val ledger = Ledger()
        var succeed = false
        val dispatcher = IosWatchAlertNotificationDispatcher(
            isAuthorized = { true },
            filterNew = ledger::filter,
            markNotified = ledger::mark,
            post = { succeed }
        )
        assertEquals(emptyList(), dispatcher.notifyNew(listOf(match)))
        assertTrue(ledger.notified.isEmpty())
        succeed = true
        assertEquals(listOf(match), dispatcher.notifyNew(listOf(match)))
    }

    @Test
    fun aPendingAuthorizationCheckDoesNotHoldTheLedgerLock() = runBlocking {
        val ledger = Ledger()
        val firstCheck = CompletableDeferred<Boolean>()
        var checks = 0
        val dispatcher = IosWatchAlertNotificationDispatcher(
            isAuthorized = { if (checks++ == 0) firstCheck.await() else true },
            filterNew = ledger::filter,
            markNotified = ledger::mark,
            post = { true }
        )
        val stalled = async(Dispatchers.Default) { dispatcher.notifyNew(listOf(match)) }
        while (checks == 0) kotlinx.coroutines.yield()
        val other = match.copy(threadId = "2")
        val posted = withTimeout(5_000) { dispatcher.notifyNew(listOf(other)) }
        assertEquals(listOf(other), posted)
        firstCheck.complete(true)
        assertEquals(listOf(match), stalled.await())
    }

    @Test
    fun cancellationWhilePostingStillRecordsTheScheduledNotification() = runBlocking {
        val ledger = Ledger()
        val postStarted = CompletableDeferred<Unit>()
        val postGate = CompletableDeferred<Unit>()
        val dispatcher = IosWatchAlertNotificationDispatcher(
            isAuthorized = { true },
            filterNew = ledger::filter,
            markNotified = ledger::mark,
            post = { postStarted.complete(Unit); postGate.await(); true }
        )
        val job = launch(Dispatchers.Default) { dispatcher.notifyNew(listOf(match)) }
        postStarted.await()
        job.cancel()
        postGate.complete(Unit)
        job.join()
        assertEquals(setOf(match.identityKey), ledger.notified, "the expiring task must not repeat it next run")
    }

    @Test
    fun onlyGrantedStatusesAllowBackgroundPosting() {
        assertTrue(isIosNotificationAuthorizationUsable(UNAuthorizationStatusAuthorized))
        assertTrue(isIosNotificationAuthorizationUsable(UNAuthorizationStatusProvisional))
        assertTrue(isIosNotificationAuthorizationUsable(UNAuthorizationStatusEphemeral))
        assertFalse(isIosNotificationAuthorizationUsable(UNAuthorizationStatusNotDetermined))
        assertFalse(isIosNotificationAuthorizationUsable(UNAuthorizationStatusDenied))
    }

    @Test
    fun foregroundPromptIsWantedOnlyForAnEnabledWatchNotification() {
        assertTrue(iosWatchNotificationsWanted(futachaWatchAlertEnabled = true, compatPreferences = emptyMap()))
        assertFalse(iosWatchNotificationsWanted(futachaWatchAlertEnabled = false, compatPreferences = emptyMap()))
        val rules = encodeValidCompatWatchRules(listOf(CompatWatchRule("猫")))
        val enabled = mapOf(COMPAT_WATCH_RULES_KEY to rules)
        assertTrue(iosWatchNotificationsWanted(false, enabled))
        assertFalse(iosWatchNotificationsWanted(false, enabled + (COMPAT_WATCH_NOTIFY_KEY to "OFF")))
        assertFalse(iosWatchNotificationsWanted(false, enabled + (COMPAT_WATCH_ENABLED_KEY to "OFF")))
    }
}
