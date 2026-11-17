package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valoser.futacha.shared.desktop.openDesktopInlineSearchHtml
import com.valoser.futacha.shared.desktop.openDesktopUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun CompatReverseImageSearchWebView(initialUrl: String?, initialHtml: String?, baseUrl: String?,
    initialCookies: List<CompatBrowserCookie>, navigationCommand: CompatBrowserNavigationCommand?, modifier: Modifier,
    onStateChanged: (CompatBrowserState) -> Unit, onLinkLongPressed: (String) -> Unit,
    onCookiesChanged: (url: String, cookieHeader: String?) -> Unit) {
    val url = initialUrl ?: baseUrl.orEmpty()
    // IQDB / SauceNAO by file answer with a page, not a URL (the screen passes it as initialHtml).
    val inlineHtml = initialHtml.takeIf { initialUrl == null && !it.isNullOrBlank() }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(url) { onStateChanged(CompatBrowserState(currentUrl = url, loading = false)) }
    Column(modifier.padding(16.dp)) {
        Text("画像検索の結果はブラウザで開きます")
        TextButton(onClick = {
            error = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (inlineHtml != null) openDesktopInlineSearchHtml(inlineHtml, baseUrl.orEmpty()) else openDesktopUrl(url)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    // Only our own (Japanese) validation messages are shown; the JDK's are English.
                    error = (failure as? IllegalArgumentException)?.message ?: "ブラウザで開けませんでした"
                }
            }
        }) { Text("ブラウザで開く") }
        error?.let { Text(it) }
    }
}
