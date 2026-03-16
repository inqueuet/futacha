@file:Suppress("DEPRECATION")

package com.valoser.futacha.shared.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.model.EmbeddedHtmlContent
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformEmbeddedHtmlSection(
    snippets: List<EmbeddedHtmlContent>,
    modifier: Modifier
) {
    if (snippets.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        snippets.forEach { snippet ->
            val baseUrl = remember(snippet.id) { NSURL.URLWithString("about:blank") }
            UIKitView(
                factory = {
                    val configuration = WKWebViewConfiguration().apply {
                        allowsInlineMediaPlayback = true
                    }
                    WKWebView(
                        frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                        configuration = configuration
                    ).apply {
                        scrollView.scrollEnabled = false
                        opaque = false
                        backgroundColor = platform.UIKit.UIColor.clearColor
                        loadHTMLString(snippet.html, baseURL = baseUrl)
                        tag = snippet.html.hashCode().toLong()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(snippet.estimatedHeightDp.dp),
                update = { view ->
                    val nextTag = snippet.html.hashCode().toLong()
                    if (view.tag != nextTag) {
                        view.tag = nextTag
                        view.loadHTMLString(snippet.html, baseURL = baseUrl)
                    }
                },
                onRelease = { view ->
                    view.stopLoading()
                    view.loadHTMLString("", baseURL = null)
                    view.navigationDelegate = null
                }
            )
        }
    }
}
