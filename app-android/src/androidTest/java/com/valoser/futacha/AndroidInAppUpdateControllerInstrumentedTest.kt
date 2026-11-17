package com.valoser.futacha

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class AndroidInAppUpdateControllerInstrumentedTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var fakeUpdateManager: FakeAppUpdateManager
    private lateinit var controller: AndroidInAppUpdateController
    private val flexibleDownloadObserved = AtomicBoolean(false)

    @Before
    fun setUp() {
        fakeUpdateManager = FakeAppUpdateManager(rule.activity)
        controller = AndroidInAppUpdateController(
            activity = rule.activity,
            onFlexibleUpdateDownloaded = { flexibleDownloadObserved.set(true) },
            onFlexibleUpdateCompletionFailed = {},
            appUpdateManager = fakeUpdateManager
        )
        controller.register()
    }

    @After
    fun tearDown() {
        controller.unregister()
    }

    @Test
    fun newlyAvailableUpdateStartsFlexibleAndReportsCompletedDownload() {
        fakeUpdateManager.setUpdateAvailable(165)
        fakeUpdateManager.setClientVersionStalenessDays(0)

        controller.checkForNewUpdate()
        waitUntil { fakeUpdateManager.isConfirmationDialogVisible }
        assertTrue("The Flexible Play confirmation was not started.", fakeUpdateManager.isConfirmationDialogVisible)

        fakeUpdateManager.userAcceptsUpdate()
        fakeUpdateManager.downloadStarts()
        fakeUpdateManager.downloadCompletes()

        waitUntil { flexibleDownloadObserved.get() }
        assertTrue("The completed Flexible download was not delivered to the UI.", flexibleDownloadObserved.get())
    }

    @Test
    fun sevenDayOldUpdateStartsImmediateFlow() {
        fakeUpdateManager.setUpdateAvailable(165)
        fakeUpdateManager.setClientVersionStalenessDays(IMMEDIATE_UPDATE_STALENESS_DAYS)

        controller.checkForNewUpdate()
        waitUntil { fakeUpdateManager.isImmediateFlowVisible }

        assertTrue("The Immediate Play flow was not started at day seven.", fakeUpdateManager.isImmediateFlowVisible)
    }

    @Test
    fun optionalUpdateIsSuppressedWhenSettingIsOff() {
        fakeUpdateManager.setUpdateAvailable(165)
        fakeUpdateManager.setClientVersionStalenessDays(0)

        controller.checkForNewUpdate(allowOptionalUpdate = false)
        waitUntil(timeoutMillis = 500) {
            fakeUpdateManager.isConfirmationDialogVisible || fakeUpdateManager.isImmediateFlowVisible
        }

        assertFalse(fakeUpdateManager.isConfirmationDialogVisible)
        assertFalse(fakeUpdateManager.isImmediateFlowVisible)
    }

    @Test
    fun emergencyUpdateStartsWhenSettingIsOff() {
        fakeUpdateManager.setUpdateAvailable(165)
        fakeUpdateManager.setClientVersionStalenessDays(IMMEDIATE_UPDATE_STALENESS_DAYS)

        controller.checkForNewUpdate(allowOptionalUpdate = false)
        waitUntil { fakeUpdateManager.isImmediateFlowVisible }

        assertTrue(
            "The emergency Play flow was suppressed by the disabled setting.",
            fakeUpdateManager.isImmediateFlowVisible
        )
    }

    @Test
    fun realGooglePlayBackendCompletesUpdateInfoRequest() {
        val completed = CountDownLatch(1)
        val task = AppUpdateManagerFactory.create(rule.activity).appUpdateInfo
        task.addOnCompleteListener { result ->
            Log.i(
                "AndroidInAppUpdateTest",
                "Live Play appUpdateInfo completed: successful=${result.isSuccessful}, " +
                    "error=${result.exception?.javaClass?.simpleName ?: "none"}"
            )
            completed.countDown()
        }

        assertTrue(
            "The live Google Play appUpdateInfo request did not complete.",
            completed.await(15, TimeUnit.SECONDS)
        )
    }

    private fun waitUntil(timeoutMillis: Long = 5_000L, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
    }
}
