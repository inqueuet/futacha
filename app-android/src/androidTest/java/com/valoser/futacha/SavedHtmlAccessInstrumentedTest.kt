package com.valoser.futacha

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SavedHtmlAccessInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun uri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    private fun viewIntent(source: Uri) = Intent(context, SavedHtmlViewerActivity::class.java)
        .setAction(Intent.ACTION_VIEW).setDataAndType(source, "text/html")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Test fun ownProviderCannotExposeAnInternalHtmlDocument() {
        val privateHtml = File(context.filesDir, "futacha/round2-private-${System.nanoTime()}.html")
        privateHtml.parentFile!!.mkdirs()
        privateHtml.writeText("<html><body>private data</body></html>")
        try {
            ActivityScenario.launch<SavedHtmlViewerActivity>(viewIntent(uri(privateHtml))).use { scenario ->
                assertEquals(Lifecycle.State.DESTROYED, scenario.state)
            }
        } finally { privateHtml.delete() }
    }

    /** The real entry point: an external save shared through this app's FileProvider. */
    @Test fun externalSaveOpensThroughOwnContentUriAndKeepsPrivateFilesBlocked() {
        val external = File(requireNotNull(context.getExternalFilesDir(null)), "Documents/futacha/round3-html-${System.nanoTime()}").apply { mkdirs() }
        val savedHtml = File(external, "thread.html").apply { writeText("<html><body>saved thread</body></html>") }
        val image = File(external, "image.png").apply { writeBytes(byteArrayOf(1)) }
        val privateHtml = File(context.cacheDir, "compat_post_preview/round3-secret-${System.nanoTime()}.html").apply {
            parentFile!!.mkdirs()
            writeText("secret")
        }
        try {
            ActivityScenario.launch<SavedHtmlViewerActivity>(viewIntent(uri(savedHtml))).use { scenario ->
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                scenario.onActivity { activity ->
                    val web = (activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as WebView)
                    val client = web.webViewClient
                    assertFalse(web.settings.javaScriptEnabled)
                    assertTrue(web.settings.blockNetworkLoads)
                    assertNull(client.shouldInterceptRequest(web, Request(uri(image))))
                    assertNotNull(client.shouldInterceptRequest(web, Request(uri(privateHtml))))
                    assertNotNull(client.shouldInterceptRequest(web, Request(Uri.fromFile(privateHtml))))
                    assertNotNull(client.shouldInterceptRequest(web, Request(Uri.parse("content://other.provider/image.png"))))
                    assertNotNull(client.shouldInterceptRequest(web, Request(Uri.parse("https://example.test/image.png"))))
                    assertTrue(client.shouldOverrideUrlLoading(web, Request(uri(privateHtml), true)))
                }
            }
        } finally { privateHtml.delete(); external.deleteRecursively() }
    }

    @Test fun externalSaveOpensButItsPrivateIframeAndOtherAuthoritiesAreBlocked() {
        val external = File(requireNotNull(context.getExternalFilesDir(null)), "Documents/futacha/round2-html-${System.nanoTime()}").apply { mkdirs() }
        val savedHtml = File(external, "thread.html").apply { writeText("<html><body>saved thread</body></html>") }
        val image = File(external, "image.png").apply { writeBytes(byteArrayOf(1)) }
        val privateHtml = File(context.cacheDir, "compat_post_preview/round2-secret-${System.nanoTime()}.html").apply {
            parentFile!!.mkdirs()
            writeText("secret")
        }
        val intercepted = CountDownLatch(1)
        try {
            ActivityScenario.launch<SavedHtmlViewerActivity>(viewIntent(Uri.fromFile(savedHtml))).use { scenario ->
                scenario.onActivity { activity ->
                    val web = (activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as WebView)
                    val client = web.webViewClient
                    assertFalse(web.settings.javaScriptEnabled)
                    assertTrue(web.settings.blockNetworkLoads)
                    assertNull(client.shouldInterceptRequest(web, Request(Uri.fromFile(image))))
                    assertNotNull(client.shouldInterceptRequest(web, Request(uri(privateHtml))))
                    assertNotNull(client.shouldInterceptRequest(web, Request(Uri.fromFile(privateHtml))))
                    assertNotNull(client.shouldInterceptRequest(web, Request(Uri.parse("content://other.provider/image.png"))))
                    assertNotNull(client.shouldInterceptRequest(web, Request(Uri.parse("https://example.test/image.png"))))
                    assertTrue(client.shouldOverrideUrlLoading(web, Request(uri(privateHtml), true)))
                    web.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val response = client.shouldInterceptRequest(view, request)
                            if (request?.url == Uri.fromFile(privateHtml) && response != null) intercepted.countDown()
                            return response
                        }
                    }
                    web.loadDataWithBaseURL(savedHtmlDocumentBaseUrl(Uri.fromFile(savedHtml)),
                        "<html><body>saved<iframe src=\"${Uri.fromFile(privateHtml)}\"></iframe></body></html>", "text/html", "UTF-8", null)
                }
                assertTrue("WebView must reject the actual private iframe request", intercepted.await(10, TimeUnit.SECONDS))
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        } finally { privateHtml.delete(); external.deleteRecursively() }
    }

    private class Request(private val target: Uri, private val main: Boolean = false) : WebResourceRequest {
        override fun getUrl() = target
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = false
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String, String>()
    }
}
