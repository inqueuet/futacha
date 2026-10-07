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
        val lease = AndroidSpeechPlaybackService.acquire(context) { stopped.complete(Unit) }
        try {
            shell("input keyevent 223")
            // Keep the app in the actual stopped/screen-off lifecycle, beyond startup.
            kotlinx.coroutines.delay(2_000)
            assertFalse(context.getSystemService(PowerManager::class.java).isInteractive)
            assertFalse(stopped.isCompleted)
            val service = context.getSystemService(ActivityManager::class.java).getRunningServices(100)
                .single { it.service.className == AndroidSpeechPlaybackService::class.java.name }
            assertTrue(service.foreground)
            val notification = context.getSystemService(NotificationManager::class.java).activeNotifications
                .single { it.notification.channelId == "read_aloud" }.notification
            assertEquals("停止", notification.actions.single().title.toString())
            notification.actions.single().actionIntent.send()
            withTimeout(5_000) { stopped.await() }
            rule.waitUntil(5_000) {
                context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.notification.channelId == "read_aloud" }
            }
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
