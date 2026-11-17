package com.valoser.futacha

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityDeepLinkLifecycleInstrumentedTest {
    @Test
    fun pendingThreadIntentSurvivesActivityStateRecreation() {
        val deepLink = "https://deep-link-recreate.2chan.net/test/res/100.htm"
        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            setClassName("com.valoser.futacha", "com.valoser.futacha.MainActivity")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(deepLink, activity.pendingDeepLinksForTest().thread)
            }

            scenario.recreate()
            scenario.onActivity { activity ->
                assertEquals(deepLink, activity.pendingDeepLinksForTest().thread)
            }
        }
    }
}
