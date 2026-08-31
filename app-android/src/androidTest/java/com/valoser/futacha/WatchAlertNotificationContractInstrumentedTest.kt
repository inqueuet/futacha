package com.valoser.futacha

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchAlertNotificationContractInstrumentedTest {
    @Test
    fun contentIntentResumesCurrentRootWithoutProfileOrThreadRoutingData() {
        val context = ApplicationProvider.getApplicationContext<FutachaApplication>()
        val intent = buildWatchAlertContentIntent(context)

        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertNull(intent.action)
        assertNull(intent.data)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }
}
