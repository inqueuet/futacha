package com.valoser.futacha.shared.ui.compat

import android.graphics.Color
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal actual fun CompatReferenceChangeLogView(
    html: String,
    modifier: Modifier,
    onLinkClicked: (String) -> Unit
) {
    val currentLinkCallback by rememberUpdatedState(onLinkClicked)
    var webView by remember { mutableStateOf<WebView?>(null) }
    AndroidView<View>(
        modifier = modifier,
        factory = { context ->
            runCatching {
                WebView(context).apply {
                    setInitialScale(100)
                    isVerticalScrollBarEnabled = true
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = false
                    settings.useWideViewPort = false
                    settings.builtInZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = intercept(request?.url?.toString())

                        @Deprecated("Deprecated in Android")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                            intercept(url)

                        private fun intercept(url: String?): Boolean {
                            if (url?.startsWith("http://", true) == true ||
                                url?.startsWith("https://", true) == true
                            ) {
                                currentLinkCallback(url)
                                return true
                            }
                            return false
                        }
                    }
                    loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                    webView = this
                }
            }.getOrElse {
                // Some valid API 26 devices and stripped emulator images have
                // no WebView provider.  Help, licence and change-log screens
                // must remain usable instead of crashing the whole process.
                TextView(context).apply {
                    setPadding(24, 16, 24, 16)
                    setTextColor(Color.BLACK)
                    setBackgroundColor(Color.WHITE)
                    textSize = 14f
                    isVerticalScrollBarEnabled = true
                    movementMethod = LinkMovementMethod.getInstance()
                    text = htmlFallbackText(html) { currentLinkCallback(it) }
                }
            }
        }
    )
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.webViewClient = WebViewClient()
            webView?.destroy()
            webView = null
        }
    }
}

private fun htmlFallbackText(
    html: String,
    onLinkClicked: (String) -> Unit
): CharSequence {
    val parsed = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
    val result = SpannableStringBuilder(parsed)
    result.getSpans(0, result.length, URLSpan::class.java).forEach { span ->
        val start = result.getSpanStart(span)
        val end = result.getSpanEnd(span)
        val flags = result.getSpanFlags(span)
        result.removeSpan(span)
        result.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = onLinkClicked(span.url)
            },
            start,
            end,
            flags
        )
    }
    return result
}
