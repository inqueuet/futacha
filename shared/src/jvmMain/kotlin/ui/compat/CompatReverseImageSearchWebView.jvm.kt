package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.desktop.openDesktopUrl

@Composable
internal actual fun CompatReverseImageSearchWebView(initialUrl: String?, initialHtml: String?, baseUrl: String?,
    initialCookies: List<CompatBrowserCookie>, navigationCommand: CompatBrowserNavigationCommand?, modifier: Modifier,
    onStateChanged: (CompatBrowserState) -> Unit, onLinkLongPressed: (String) -> Unit,
    onCookiesChanged: (url: String, cookieHeader: String?) -> Unit) {
    val url = initialUrl ?: baseUrl.orEmpty()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url) { onStateChanged(CompatBrowserState(currentUrl = url, loading = false)) }
    Column(modifier.padding(16.dp)) {
        Text("画像検索の結果はブラウザで開きます")
        TextButton(onClick = { runCatching { openDesktopUrl(url) }.onFailure { error = it.message } }) { Text("ブラウザで開く") }
        error?.let { Text(it) }
    }
}
