@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATION")
package com.valoser.futacha

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import com.valoser.futacha.shared.audio.AndroidSpeechPlaybackService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SpeechBackgroundInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun playbackServiceSurvivesScreenOffAndNotificationStopsItsOwner() = runBlocking {
        val context = rule.activity
        fun shell(command: String) {
            ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand(command)).use { it.readBytes() }
        }
        val stopped = CompletableDeferred<Unit>()
        val notifications = context.getSystemService(NotificationManager::class.java)
        // Earlier tests in the same run leave the app's own notifications behind (and one of them
        // grants POST_NOTIFICATIONS). With another one present, Android groups ours under a system
        // summary ("ranker_group") that copies the triggering notification's channel, so a second
        // "read_aloud" notification appears. Start without leftovers, and only ever look at real
        // (non-summary) notifications.
        notifications.cancelAll()
        fun readAloudNotifications() = notifications.activeNotifications.filter {
            it.notification.channelId == "read_aloud" &&
                it.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY == 0
        }
        val lease = AndroidSpeechPlaybackService.acquire(context) { stopped.complete(Unit) }
        try {
            shell("input keyevent 223")
            // Keep the app in the actual stopped/screen-off lifecycle, beyond startup.
            kotlinx.coroutines.delay(2_000)
            assertFalse(context.getSystemService(PowerManager::class.java).isInteractive)
            assertFalse(stopped.isCompleted)
            val services = context.getSystemService(ActivityManager::class.java).getRunningServices(100)
                .filter { it.service.className == AndroidSpeechPlaybackService::class.java.name }
            assertEquals("Speech services: ${services.map { "pid=${it.pid} process=${it.process} started=${it.started} fg=${it.foreground} clients=${it.clientCount}" }}",
                1, services.size)
            assertTrue(services.single().foreground)
            val readAloud = readAloudNotifications()
            assertEquals("Read-aloud notifications: ${readAloud.map { "id=${it.id} tag=${it.tag} title=${it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)}" }}",
                1, readAloud.size)
            val notification = readAloud.single().notification
            assertEquals("停止", notification.actions.single().title.toString())
            notification.actions.single().actionIntent.send()
            withTimeout(5_000) { stopped.await() }
            rule.waitUntil(5_000) { readAloudNotifications().isEmpty() }
        } finally {
            lease.close()
            shell("input keyevent 224")
        }
    }

    @Test fun replacingAnUtteranceDoesNotReleaseAnotherPlaybackOwner() = runBlocking {
        val firstStopped = CompletableDeferred<Unit>()
        val secondStopped = CompletableDeferred<Unit>()
        val first = AndroidSpeechPlaybackService.acquire(rule.activity) { firstStopped.complete(Unit) }
        val second = AndroidSpeechPlaybackService.acquire(rule.activity) { secondStopped.complete(Unit) }
        try {
            first.close()
            kotlinx.coroutines.delay(100)
            assertFalse(secondStopped.isCompleted)
            rule.activity.startService(Intent(rule.activity, AndroidSpeechPlaybackService::class.java)
                .setAction(AndroidSpeechPlaybackService.ACTION_STOP))
            withTimeout(5_000) { secondStopped.await() }
            assertFalse(firstStopped.isCompleted)
        } finally {
            first.close()
            second.close()
        }
    }
}
