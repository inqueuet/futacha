@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.valoser.futacha

import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.valoser.futacha.shared.ui.compat.CompatHelpScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompatStoreLinkInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun helpStoreButtonLaunchesAndroidListing() {
        val launched = mutableListOf<Intent>()
        val context = object : ContextWrapper(rule.activity) {
            override fun startActivity(intent: Intent) { launched += intent }
        }
        rule.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme { CompatHelpScreen(onBack = {}) }
            }
        }
        rule.onNodeWithContentDescription("ストア").performClick()
        rule.runOnIdle {
            assertEquals(1, launched.size)
            assertEquals(Intent.ACTION_VIEW, launched.single().action)
            assertEquals("https://play.google.com/store/apps/details?id=com.valoser.futacha", launched.single().dataString)
        }
    }
}
