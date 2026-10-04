package com.valoser.futacha

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.valoser.futacha.shared.compat.ExperienceProfile
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Exercise the production host, including app-level overlays and profile switching. */
class MainActivitySystemBarsInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var app: FutachaApplication
    private lateinit var originalProfile: ExperienceProfile
    private var originalTheme: String? = null
    private var originalNavigation: String? = null

    @Before fun prepare() {
        app = ApplicationProvider.getApplicationContext()
        originalProfile = app.experienceProfileStore.readActiveProfile()
        runBlocking {
            app.compatibilityStore.ensureInitialized()
            originalTheme = app.compatibilityStore.loadPreference("compat.design.designTheme")
            originalNavigation = app.compatibilityStore.loadPreference("compat.design.designNavigationBar")
            app.compatibilityStore.savePreference("compat.design.designTheme", "default")
            app.compatibilityStore.savePreference("compat.design.designNavigationBar", "OFF")
        }
    }

    @After fun restore() {
        if (!::app.isInitialized) return
        runBlocking {
            app.compatibilityStore.savePreference("compat.design.designTheme", originalTheme ?: "default")
            app.compatibilityStore.savePreference("compat.design.designNavigationBar", originalNavigation ?: "OFF")
        }
        switchProfile(originalProfile)
    }

    private fun switchProfile(target: ExperienceProfile) {
        val store = app.experienceProfileStore
        val current = store.readActiveProfile()
        if (current == target) return
        store.readJournal()?.let(store::completeSwitch)
        val journal = store.beginSwitch(current, target)
        store.completeSwitch(store.persistRequestedProfile(journal))
    }

    @Test fun productionHostKeepsCompatibilitySystemBarsAfterModeSwitchAndRestart() {
        switchProfile(ExperienceProfile.FUTACHA)
        rule.waitForIdle()
        switchProfile(ExperienceProfile.TOSHIAKI_COMPAT)
        assertBars("mode-switch", 0xFF009688.toInt(), 0xFF009688.toInt())
        rule.activityRule.scenario.recreate()
        assertBars("cold-recreate", 0xFF009688.toInt(), 0xFF009688.toInt())
        rule.onNodeWithContentDescription("その他").performClick()
        androidx.test.espresso.Espresso.pressBack()
        assertBars("menu-closed", 0xFF009688.toInt(), 0xFF009688.toInt())
    }

    @Test fun productionHostUsesPersistedCompatibilityThemeAndNavigationPreference() {
        switchProfile(ExperienceProfile.TOSHIAKI_COMPAT)
        for ((theme, chrome, background) in listOf(
            Triple("default", 0xFF009688.toInt(), 0xFFE6E6E6.toInt()),
            Triple("mono", 0xFF222222.toInt(), 0xFFE6E6E6.toInt()),
            Triple("blue", 0xFF03A9F4.toInt(), 0xFFFBFBFB.toInt()),
            Triple("pink", 0xFFE91E63.toInt(), 0xFFFAFAFA.toInt()),
            Triple("futaba", 0xFF542D24.toInt(), 0xFFFFFFEE.toInt()),
            Triple("black", 0xFF000000.toInt(), 0xFF000000.toInt())
        )) {
            for (useBackground in listOf(false, true)) {
                runBlocking {
                    app.compatibilityStore.savePreference("compat.design.designTheme", theme)
                    app.compatibilityStore.savePreference("compat.design.designNavigationBar", if (useBackground) "ON" else "OFF")
                }
                assertBars("$theme-$useBackground", chrome, if (useBackground) background else chrome)
            }
        }
    }

    private fun assertBars(label: String, status: Int, navigation: Int) {
        var actualStatus = 0
        var actualNavigation = 0
        var toolbar = 0
        try {
            rule.waitUntil(15_000) {
                val insets = ViewCompat.getRootWindowInsets(rule.activity.window.decorView)
                    ?: return@waitUntil false
                val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                if (top == 0 || bottom == 0) return@waitUntil false
                val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    ?: return@waitUntil false
                try {
                    actualStatus = screen.getPixel(screen.width / 4, top - 2)
                    actualNavigation = screen.getPixel(screen.width / 4, screen.height - bottom + 2)
                    toolbar = screen.getPixel(screen.width - 2, top + 2)
                    val matches = actualStatus == status && actualNavigation == navigation && toolbar == status
                    if (matches) {
                        File(app.getExternalFilesDir(null), "main-activity-bars-$label.png").outputStream().use {
                            screen.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    }
                    matches
                } finally { screen.recycle() }
            }
        } catch (failure: Throwable) {
            val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (screen != null) {
                File(app.getExternalFilesDir(null), "main-activity-bars-$label-failed.png").outputStream().use {
                    screen.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screen.recycle()
            }
            throw AssertionError("$label status=${actualStatus.toUInt().toString(16)} navigation=${actualNavigation.toUInt().toString(16)} toolbar=${toolbar.toUInt().toString(16)}", failure)
        }
        assertEquals("$label actual OS clock region", status, actualStatus)
        assertEquals("$label actual OS navigation region", navigation, actualNavigation)
        assertEquals("$label toolbar below OS status region", status, toolbar)
    }
}
