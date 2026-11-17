@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package com.valoser.futacha

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.valoser.futacha.shared.model.SaveProgress
import com.valoser.futacha.shared.service.AndroidProtectedThreadSave
import com.valoser.futacha.shared.service.AndroidProtectedThreadSaveRegistry
import com.valoser.futacha.shared.service.AndroidThreadSaveForegroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class ConcurrentThreadSaveInstrumentedTest {
    @Test fun finishingTheLatestSaveKeepsTheEarlierSaveProtected() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ActivityScenario.launch(MainActivity::class.java).use {
            val first = AndroidProtectedThreadSaveRegistry.register(AndroidProtectedThreadSave("audit-save-A", MutableStateFlow<SaveProgress?>(null), Job()))
            val second = AndroidProtectedThreadSaveRegistry.register(AndroidProtectedThreadSave("audit-save-B", MutableStateFlow<SaveProgress?>(null), Job()))
            fun start(id: String) = context.startForegroundService(Intent(context, AndroidThreadSaveForegroundService::class.java).apply {
                action = AndroidThreadSaveForegroundService.ACTION_START
                putExtra(AndroidThreadSaveForegroundService.EXTRA_SESSION_ID, id)
            })
            fun awaitNotification(title: String): Boolean {
                val deadline = SystemClock.elapsedRealtime() + 5_000
                while (SystemClock.elapsedRealtime() < deadline) {
                    if (manager.activeNotifications.any { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == title }) return true
                    SystemClock.sleep(30)
                }
                return false
            }
            try {
                start(first)
                assertTrue(awaitNotification("audit-save-A"))
                start(second)
                assertTrue(awaitNotification("audit-save-B"))
                AndroidProtectedThreadSaveRegistry.remove(second)
                context.startService(Intent(context, AndroidThreadSaveForegroundService::class.java).apply {
                    action = AndroidThreadSaveForegroundService.ACTION_FINISH
                })
                assertTrue("A must regain the foreground notification when B finishes", awaitNotification("audit-save-A"))
                assertNotNull(AndroidProtectedThreadSaveRegistry.get(first))
            } finally {
                AndroidProtectedThreadSaveRegistry.remove(first)
                AndroidProtectedThreadSaveRegistry.remove(second)
                context.stopService(Intent(context, AndroidThreadSaveForegroundService::class.java))
            }
        }
    }
}
