package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun CompatReferenceChangeLogView(
    html: String,
    modifier: Modifier,
    onLinkClicked: (String) -> Unit
) {
    val delegate = remember { CompatChangeLogNavigationDelegate() }
    SideEffect { delegate.onLinkClicked = onLinkClicked }
    UIKitView(
        modifier = modifier,
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                defaultWebpagePreferences.allowsContentJavaScript = false
            }
            WKWebView(
                frame = CGRectMake(0.0, 0.0, 0.0, 0.0),
                configuration = configuration
            ).apply {
                navigationDelegate = delegate
                scrollView.showsVerticalScrollIndicator = true
                loadHTMLString(html, NSURL.URLWithString("file:///"))
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.navigationDelegate = null
        }
    )
}

@OptIn(ExperimentalForeignApi::class)
private class CompatChangeLogNavigationDelegate : NSObject(), WKNavigationDelegateProtocol {
    var onLinkClicked: (String) -> Unit = {}

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit
    ) {
        val url = decidePolicyForNavigationAction.request.URL?.absoluteString
        if (url?.startsWith("http://", ignoreCase = true) == true ||
            url?.startsWith("https://", ignoreCase = true) == true
        ) {
            onLinkClicked(url)
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        }
    }
}
