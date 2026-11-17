package com.valoser.futacha

import android.view.View
import android.webkit.WebView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal fun helpDocumentScript(script: String): String {
    val result = AtomicReference<String>()
    val ready = CountDownLatch(1)
    onView(isAssignableFrom(WebView::class.java)).perform(object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDisplayed()
        override fun getDescription() = "Inspect the rendered help document"
        override fun perform(uiController: UiController, view: View) {
            val web = view as WebView
            // JavaScript is enabled only for this test's DOM inspection.
            web.settings.javaScriptEnabled = true
            web.evaluateJavascript(script) {
                web.settings.javaScriptEnabled = false
                result.set(it)
                ready.countDown()
            }
        }
    })
    assertTrue("Help document inspection timed out", ready.await(5, TimeUnit.SECONDS))
    return result.get()
}

internal fun assertHelpSearchDocument(word: String) {
    val quoted = org.json.JSONObject.quote(word)
    assertEquals("true", helpDocumentScript("""
        (function() {
            const section = document.getElementById('watcher-help');
            const marks = Array.from(document.querySelectorAll('mark'));
            return section.checked && section.nextElementSibling.offsetHeight > 0 &&
                marks.some(mark => mark.textContent === $quoted) &&
                marks.every(mark => getComputedStyle(mark).color === 'rgb(34, 34, 34)' &&
                    getComputedStyle(mark).backgroundColor === 'rgb(255, 224, 130)');
        })()
    """.trimIndent()))
}
