@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.valoser.futacha.shared.ui.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import com.valoser.futacha.shared.repository.CookieRepository
import io.ktor.http.Url
import kotlinx.coroutines.launch

internal enum class CompatBrowserNavigation { BACK, FORWARD }

internal data class CompatBrowserNavigationCommand(
    val serial: Long,
    val navigation: CompatBrowserNavigation
)

internal data class CompatBrowserState(
    val currentUrl: String? = null,
    val loading: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false
)

internal data class CompatBrowserCookie(
    val name: String,
    val value: String,
    val path: String = "/",
    val secure: Boolean = false
)

private val compatReverseSearchAbsoluteHttpUrl = Regex(
    "^https?://[^/?#\\s]+(?:[/?#]|$)",
    RegexOption.IGNORE_CASE
)

internal fun isCompatReverseSearchBrowserUrl(value: String?): Boolean {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty() || raw.length > 8_192 || !compatReverseSearchAbsoluteHttpUrl.containsMatchIn(raw)) {
        return false
    }
    return runCatching {
        val url = Url(raw)
        url.protocol.name.lowercase() in setOf("http", "https") && url.host.isNotBlank()
    }
        .getOrDefault(false)
}

internal fun normalizeCompatReverseSearchLongPressedLink(value: String?): String? =
    value?.trim()?.takeIf(::isCompatReverseSearchBrowserUrl)

internal enum class CompatReverseSearchLinkAction { OPEN_EXTERNAL, COPY_URL }

internal data class CompatReverseSearchLinkMenuItem(
    val label: String,
    val action: CompatReverseSearchLinkAction
)

internal fun compatReverseSearchLinkMenuItems(value: String?): List<CompatReverseSearchLinkMenuItem> =
    if (normalizeCompatReverseSearchLongPressedLink(value) == null) {
        emptyList()
    } else {
        listOf(
            CompatReverseSearchLinkMenuItem(
                "外部ブラウザで開く",
                CompatReverseSearchLinkAction.OPEN_EXTERNAL
            ),
            CompatReverseSearchLinkMenuItem("URLをコピー", CompatReverseSearchLinkAction.COPY_URL)
        )
    }

@Composable
internal fun CompatReverseImageSearchScreen(
    initialUrl: String,
    title: String = "画像検索",
    cookieRepository: CookieRepository? = null,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit
) = CompatReverseImageSearchScreenContent(
    initialUrl = initialUrl,
    initialHtml = null,
    baseUrl = null,
    title = title,
    cookieRepository = cookieRepository,
    onClose = onClose,
    onOpenExternal = onOpenExternal
)

@Composable
internal fun CompatReverseImageSearchScreen(
    result: CompatImageSearchResult,
    cookieRepository: CookieRepository? = null,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit
) = when (result) {
    is CompatImageSearchResult.RemoteUrl -> CompatReverseImageSearchScreenContent(
        initialUrl = result.url,
        initialHtml = null,
        baseUrl = null,
        title = result.title,
        cookieRepository = cookieRepository,
        onClose = onClose,
        onOpenExternal = onOpenExternal
    )
    is CompatImageSearchResult.InlineHtml -> CompatReverseImageSearchScreenContent(
        initialUrl = null,
        initialHtml = result.html,
        baseUrl = result.baseUrl,
        title = result.title,
        cookieRepository = cookieRepository,
        onClose = onClose,
        onOpenExternal = onOpenExternal
    )
}

@Composable
private fun CompatReverseImageSearchScreenContent(
    initialUrl: String?,
    initialHtml: String?,
    baseUrl: String?,
    title: String,
    cookieRepository: CookieRepository?,
    onClose: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val validInitialUrl = initialUrl?.let(::isCompatReverseSearchBrowserUrl) == true
    val validInlineHtml = initialHtml != null && initialHtml.length <= 2 * 1024 * 1024 &&
        isCompatReverseSearchBrowserUrl(baseUrl)
    val initialCurrentUrl = initialUrl ?: baseUrl
    var initialCookies by remember(initialCurrentUrl, cookieRepository) {
        mutableStateOf<List<CompatBrowserCookie>?>(if (cookieRepository == null) emptyList() else null)
    }
    var state by remember(initialUrl, initialHtml, baseUrl) {
        mutableStateOf(CompatBrowserState(currentUrl = initialCurrentUrl))
    }
    var serial by remember(initialUrl, initialHtml, baseUrl) { mutableLongStateOf(0L) }
    var command by remember(initialUrl, initialHtml, baseUrl) {
        mutableStateOf<CompatBrowserNavigationCommand?>(null)
    }
    var overflowOpen by remember { mutableStateOf(false) }
    var longPressedLink by remember(initialUrl, initialHtml, baseUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(initialCurrentUrl, cookieRepository) {
        initialCookies = if (cookieRepository == null || !isCompatReverseSearchBrowserUrl(initialCurrentUrl)) {
            emptyList()
        } else {
            runCatching { cookieRepository.getCookiesFor(requireNotNull(initialCurrentUrl)) }
                .getOrDefault(emptyList())
                .map { cookie ->
                    CompatBrowserCookie(
                        name = cookie.name,
                        value = cookie.value,
                        path = cookie.path ?: "/",
                        secure = cookie.secure
                    )
                }
        }
    }
    fun navigate(action: CompatBrowserNavigation) {
        serial += 1L
        command = CompatBrowserNavigationCommand(serial, action)
    }
    PlatformBackHandler {
        if (state.canGoBack) navigate(CompatBrowserNavigation.BACK) else onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(LocalCompatibilityPalette.current.background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(LocalCompatibilityPalette.current.chrome),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "閉じる")
                }
                Text(title, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(
                    enabled = state.canGoBack,
                    onClick = { navigate(CompatBrowserNavigation.BACK) }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "履歴を戻る")
                }
                IconButton(
                    enabled = state.canGoForward,
                    onClick = { navigate(CompatBrowserNavigation.FORWARD) }
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "履歴を進む")
                }
                Box {
                    IconButton(onClick = { overflowOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(
                        expanded = overflowOpen,
                        onDismissRequest = { overflowOpen = false },
                        containerColor = compatibilityPopupSurface(LocalCompatibilityPalette.current)
                    ) {
                        DropdownMenuItem(
                            text = { Text("外部ブラウザで開く") },
                            colors = compatibilityMenuItemColors(),
                            onClick = {
                                overflowOpen = false
                                state.currentUrl
                                    ?.takeIf(::isCompatReverseSearchBrowserUrl)
                                    ?.let(onOpenExternal)
                            }
                        )
                    }
                }
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
            } else {
                Spacer(Modifier.height(3.dp))
            }
            when {
                !validInitialUrl && !validInlineHtml -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("画像検索ページを開けません")
                    }
                }
                initialCookies == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> {
                    CompatReverseImageSearchWebView(
                        initialUrl = initialUrl,
                        initialHtml = initialHtml,
                        baseUrl = baseUrl,
                        initialCookies = initialCookies.orEmpty(),
                        navigationCommand = command,
                        modifier = Modifier.fillMaxSize(),
                        onStateChanged = { state = it },
                        onLinkLongPressed = { value ->
                            longPressedLink = normalizeCompatReverseSearchLongPressedLink(value)
                        },
                        onCookiesChanged = { url, header ->
                            if (cookieRepository != null && isCompatReverseSearchBrowserUrl(url)) {
                                scope.launch {
                                    runCatching { cookieRepository.importCookieHeader(url, header) }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    longPressedLink?.let { url ->
        val linkItems = compatReverseSearchLinkMenuItems(url)
        AlertDialog(
            onDismissRequest = { longPressedLink = null },
            title = { Text("リンク") },
            text = { Text(url, maxLines = 3) },
            confirmButton = {
                Row {
                    linkItems.forEach { item ->
                        TextButton(onClick = {
                            when (item.action) {
                                CompatReverseSearchLinkAction.OPEN_EXTERNAL -> onOpenExternal(url)
                                CompatReverseSearchLinkAction.COPY_URL ->
                                    clipboard.setText(AnnotatedString(url))
                            }
                            longPressedLink = null
                        }) {
                            Text(item.label)
                        }
                    }
                    TextButton(onClick = { longPressedLink = null }) { Text("キャンセル") }
                }
            },
            dismissButton = {}
        )
    }
}

@Composable
internal expect fun CompatReverseImageSearchWebView(
    initialUrl: String?,
    initialHtml: String?,
    baseUrl: String?,
    initialCookies: List<CompatBrowserCookie>,
    navigationCommand: CompatBrowserNavigationCommand?,
    modifier: Modifier,
    onStateChanged: (CompatBrowserState) -> Unit,
    onLinkLongPressed: (String) -> Unit,
    onCookiesChanged: (url: String, cookieHeader: String?) -> Unit
)
