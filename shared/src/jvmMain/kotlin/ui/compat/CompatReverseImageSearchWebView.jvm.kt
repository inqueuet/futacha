package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
internal actual fun CompatReverseImageSearchWebView(
    initialUrl: String?,
    initialHtml: String?,
    baseUrl: String?,
    initialCookies: List<CompatBrowserCookie>,
    navigationCommand: CompatBrowserNavigationCommand?,
    modifier: Modifier,
    onStateChanged: (CompatBrowserState) -> Unit,
    onLinkLongPressed: (String) -> Unit,
    onCookiesChanged: (url: String, cookieHeader: String?) -> Unit
) {
    val displayedUrl = initialUrl ?: baseUrl.orEmpty()
    LaunchedEffect(initialUrl, initialHtml, baseUrl) {
        onStateChanged(CompatBrowserState(currentUrl = displayedUrl, loading = false))
    }
    Box(modifier) { Text(displayedUrl) }
}
