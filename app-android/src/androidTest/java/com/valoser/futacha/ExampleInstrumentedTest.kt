package com.valoser.futacha

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.security.NetworkSecurityPolicy
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.shared.service.AndroidThreadSaveForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun appContext_usesExpectedPackageAndApplicationClass() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("com.valoser.futacha", appContext.packageName)
        assertTrue(appContext.applicationContext is FutachaApplication)
    }

    @Test
    fun networkPolicyAllowsOnlyReferenceHttpMediaHosts() {
        val policy = NetworkSecurityPolicy.getInstance()
        listOf(
            "may.2chan.net",
            "www.nijibox2.com",
            "www.nijibox5.com",
            "www.nijibox6.com",
            "www.siokarabin.com"
        ).forEach { host ->
            assertTrue(
                "Reference HTTP media host must remain reachable: $host",
                policy.isCleartextTrafficPermitted(host)
            )
        }
        assertFalse(
            "Cleartext must not be enabled globally",
            policy.isCleartextTrafficPermitted("example.com")
        )
    }

    @Test
    fun manifestRoutesBothReferenceFutabaThreadSchemesIntoMainActivityOnly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        listOf("http", "https").forEach { scheme ->
            val resolved = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("$scheme://may.2chan.net/b/res/123456.htm")
            ).setPackage(context.packageName).resolveActivity(context.packageManager)
            assertEquals(
                "Reference thread deep link must resolve for $scheme",
                MainActivity::class.java.name,
                resolved?.className
            )
        }

        val unrelated = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://example.com/b/res/123456.htm")
        ).setPackage(context.packageName).resolveActivity(context.packageManager)
        assertNull("Non-Futaba lookalike links must stay outside the app", unrelated)
    }

    @Suppress("DEPRECATION")
    @Test
    fun manifestRetainsReferenceCapabilitiesWithModernSecurityBoundaries() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val packageManager = context.packageManager
        val packageInfo = packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or
                PackageManager.GET_PERMISSIONS or
                PackageManager.GET_PROVIDERS or
                PackageManager.GET_SERVICES
        )
        val applicationInfo = packageInfo.applicationInfo!!

        assertEquals(26, applicationInfo.minSdkVersion)
        assertEquals(37, applicationInfo.targetSdkVersion)
        assertEquals(FutachaApplication::class.java.name, applicationInfo.className)
        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertEquals(0, applicationInfo.flags and ApplicationInfo.FLAG_LARGE_HEAP)
        assertTrue(applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0)

        val permissions = packageInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.INTERNET in permissions)
        assertTrue(Manifest.permission.ACCESS_NETWORK_STATE in permissions)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in permissions)
        assertTrue(Manifest.permission.WAKE_LOCK in permissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in permissions)
        assertFalse(Manifest.permission.WRITE_EXTERNAL_STORAGE in permissions)
        assertFalse(Manifest.permission.VIBRATE in permissions)
        assertFalse("Advertising ID permission must stay removed", "com.google.android.gms.permission.AD_ID" in permissions)
        assertFalse(
            "Reference ad-only Activity must not be restored",
            packageInfo.activities.orEmpty().any { it.name == "com.google.android.gms.ads.AdActivity" }
        )

        val mainActivity = packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0
        )
        val requiredConfigChanges = ActivityInfo.CONFIG_KEYBOARD or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN or
            ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            ActivityInfo.CONFIG_UI_MODE
        assertEquals(requiredConfigChanges, mainActivity.configChanges and requiredConfigChanges)
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, mainActivity.launchMode)
        assertTrue(mainActivity.exported)

        val saveService = packageManager.getServiceInfo(
            ComponentName(context, AndroidThreadSaveForegroundService::class.java),
            0
        )
        assertFalse(saveService.exported)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertTrue(
                saveService.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC != 0
            )
        }

        val fileProvider = packageInfo.providers.orEmpty().single {
            it.authority == "${context.packageName}.fileprovider"
        }
        assertFalse(fileProvider.exported)
        assertTrue(fileProvider.grantUriPermissions)
    }
}
