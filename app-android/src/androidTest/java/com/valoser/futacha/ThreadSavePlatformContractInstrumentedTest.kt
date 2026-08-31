package com.valoser.futacha

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.valoser.futacha.shared.service.AndroidThreadSaveForegroundService
import com.valoser.futacha.shared.service.THREAD_SAVE_NOTIFICATION_CHANNEL_DESCRIPTION
import com.valoser.futacha.shared.util.PERSISTENT_ERROR_LOG_FILE_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ThreadSavePlatformContractInstrumentedTest {
    @Test
    fun platformErrorLogcatIsPersistedForDiagnostics() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val marker = "futacha-logcat-probe-${System.nanoTime()}"
        Log.e("FutachaLogcatProbe", marker)

        val logFile = java.io.File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            PERSISTENT_ERROR_LOG_FILE_NAME
        )
        val deadline = System.currentTimeMillis() + 5_000L
        while (
            System.currentTimeMillis() < deadline &&
            (!logFile.exists() || !logFile.tailContains(marker))
        ) {
            Thread.sleep(50L)
        }
        assertTrue(logFile.exists() && logFile.tailContains(marker))
    }

    @Test
    fun foregroundSaveServiceAndRequiredPermissionsArePackaged() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val serviceInfo = packageManager.getServiceInfo(
            ComponentName(context, AndroidThreadSaveForegroundService::class.java),
            0
        )
        if (Build.VERSION.SDK_INT >= 29) {
            assertTrue(serviceInfo.foregroundServiceType and ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC != 0)
        }
        val requested = packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        ).requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in requested)
        assertTrue(Manifest.permission.WAKE_LOCK in requested)
    }

    @Test
    fun savedHtmlDocumentResolvesToTheInternalViewer() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://example.documents/thread/123.htm"), "text/html")
        }
        val candidates = context.packageManager.queryIntentActivities(intent, 0)
        assertTrue(candidates.any { it.activityInfo.name == SavedHtmlViewerActivity::class.java.name })
    }

    @Test
    fun savedHtmlViewerRemovesFtbucketPreviewControlOnDevice() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val htmlFile = java.io.File(context.cacheDir, "issue78-saved-thread.htm")
        htmlFile.writeText(
            """
                <html><head><meta charset="UTF-8"></head><body>
                <a target=_blank href="other/fu7199371.png">fu7199371.png</a><span
                  id="preview" onclick="previewImg('preview','other/fu7199371.png')">[見る]</span><br>保存本文
                </body></html>
            """.trimIndent()
        )
        val intent = Intent(context, SavedHtmlViewerActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.fromFile(htmlFile), "text/html")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<SavedHtmlViewerActivity>(intent).use { scenario ->
            val renderedBody = AtomicReference("")
            for (attempt in 0 until 20) {
                val evaluated = CountDownLatch(1)
                scenario.onActivity { activity ->
                    val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                    val webView = content.getChildAt(0) as WebView
                    webView.settings.javaScriptEnabled = true
                    webView.evaluateJavascript("document.body.innerText") { value ->
                        renderedBody.set(value.orEmpty())
                        webView.settings.javaScriptEnabled = false
                        evaluated.countDown()
                    }
                }
                assertTrue(evaluated.await(2, TimeUnit.SECONDS))
                if (renderedBody.get().contains("保存本文")) break
                SystemClock.sleep(100L)
            }
            assertTrue(renderedBody.get().contains("保存本文"))
            assertFalse(renderedBody.get().contains("[見る]"))
        }
    }

    @Test
    fun progressNotificationExposesADistinctCancelAction() {
        assertTrue(AndroidThreadSaveForegroundService.ACTION_START != AndroidThreadSaveForegroundService.ACTION_CANCEL)
        assertTrue(AndroidThreadSaveForegroundService.ACTION_FINISH != AndroidThreadSaveForegroundService.ACTION_CANCEL)
        assertEquals(
            "スレッドをzipに保存している間だけ表示されます",
            THREAD_SAVE_NOTIFICATION_CHANNEL_DESCRIPTION
        )
    }
}

/**
 * The production diagnostic file is deliberately allowed to grow to 10 MB.
 * Reading it into one String can exhaust the 48 MB heap of an API 26 process
 * after the full instrumentation suite has run. The probe was just emitted,
 * so only a bounded tail is relevant.
 */
private fun java.io.File.tailContains(marker: String, maxBytes: Int = 1024 * 1024): Boolean =
    runCatching {
        java.io.RandomAccessFile(this, "r").use { input ->
            val length = input.length()
            val start = (length - maxBytes).coerceAtLeast(0L)
            input.seek(start)
            val bytes = ByteArray((length - start).toInt())
            val count = input.read(bytes)
            count > 0 && String(bytes, 0, count, Charsets.UTF_8).contains(marker)
        }
    }.getOrDefault(false)
