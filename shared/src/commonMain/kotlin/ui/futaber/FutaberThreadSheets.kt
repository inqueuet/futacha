package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.FutachaAppLockAwareWindow
import com.valoser.futacha.shared.ui.compat.CompatImageSearchResult
import com.valoser.futacha.shared.ui.compat.CompatReverseImageSearchScreen
import com.valoser.futacha.shared.ui.compat.LocalCompatibilityPalette
import com.valoser.futacha.shared.ui.util.PlatformBackHandler
import kotlinx.coroutines.CancellationException

// L-B5: the thread's sheets and panels that came with the original's order (M23 the copy panel, M24 the report's sheet,
// G28 the IP's sheet, M25 the direct picture searches), kept in one holder and one host so the thread screen's own
// composable does not grow (it is near the JVM's method size).

/** What the L-B5 overlays of a thread show; null closes each. */
@Stable
internal class FutaberThreadSheetState {
    /** The post whose 「コピー」 opened the copy panel (M23). */
    var copyPost by mutableStateOf<Post?>(null)

    /** The post whose 「不適切な投稿を通報」 asks (M24). */
    var reportPost by mutableStateOf<Post?>(null)

    /** The IP whose long press opened its sheet (G28), as the header writes it. */
    var ip by mutableStateOf<String?>(null)

    /** The search 二次元画像詳細検索 or GoogleLens検索 started (M25・C26). */
    var directSearch by mutableStateOf<FutaberDirectImageSearch?>(null)

    /** The words the copy panel's 「NGに追加」 opened the NG dialog with. */
    var ngWord by mutableStateOf<String?>(null)

    /** Something of these is over the thread (the edge gestures and the volume keys leave the thread alone then). */
    val anyOpen: Boolean get() = copyPost != null || reportPost != null || ip != null || directSearch != null || ngWord != null
}

/**
 * The L-B5 overlays of the thread, over the whole screen (drawn in the thread's [FutaberScreenLayer] after its sheets):
 * the copy panel and the NG dialog it opens, the report's confirmation sheet (「レス:N」 / 「delを送信」), the IP sheet
 * (「IP:…」 / 「IPをNG」) and the direct picture search. Each closes itself before it acts.
 */
@Composable
internal fun FutaberThreadSheetsHost(
    sheets: FutaberThreadSheetState,
    posts: List<Post>,
    ngWords: List<String>,
    ngHeaders: List<String>,
    hiddenCount: Int,
    onChangeNgWords: ((List<String>) -> List<String>) -> Unit,
    onChangeNgHeaders: ((List<String>) -> List<String>) -> Unit,
    /** The extension 「delの確認でこのレスをNGにも登録」 offers a second way to send. */
    reportNgOffered: Boolean,
    onReport: (post: Post, alsoNg: Boolean) -> Unit,
    onOpenPost: (quote: String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onNotice: (String) -> Unit,
    mediaServices: FutaberMediaServices?,
    preferences: Map<String, String>,
    openUrl: (String) -> Unit
) {
    sheets.copyPost?.let { post ->
        FutaberPostCopyPanel(
            post = post,
            onDismiss = { sheets.copyPost = null },
            onCopy = onCopy,
            onShare = onShare,
            onQuote = { quote -> sheets.copyPost = null; onOpenPost(quote) },
            onNg = { words -> sheets.copyPost = null; sheets.ngWord = words }
        )
    }
    sheets.ngWord?.let { words ->
        FutaberNgDialog(
            words = ngWords, headers = ngHeaders, hiddenCount = hiddenCount,
            onChangeWords = onChangeNgWords, onChangeHeaders = onChangeNgHeaders,
            onDismiss = { sheets.ngWord = null }, initialWord = words
        )
    }
    sheets.reportPost?.let { post ->
        val close = { sheets.reportPost = null }
        FutaberActionSheet(
            title = futaberPostSheetTitle(posts, post),
            actions = futaberReportConfirmActions(
                onSend = { close(); onReport(post, false) },
                extras = futaberReportConfirmExtras(reportNgOffered) { close(); onReport(post, true) }
            ),
            onDismiss = close
        )
    }
    sheets.ip?.let { ip ->
        val close = { sheets.ip = null }
        FutaberActionSheet(
            title = ip,
            actions = futaberIpSheetActions {
                close()
                val entry = futaberIpNgEntry(ip)
                onNotice(futaberIpNgNotice(ngHeaders, entry))
                onChangeNgHeaders { futaberAddNgEntry(it, entry) }
            },
            onDismiss = close
        )
    }
    sheets.directSearch?.let { search ->
        FutaberDirectImageSearchHost(search, mediaServices, preferences, openUrl, onNotice) { sheets.directSearch = null }
    }
}

/**
 * 二次元画像詳細検索 / GoogleLens検索 (M25・C26): the search runs at once, with the HUD over the thread while it does (the
 * system's Back stops it), and its result opens in the shared search result screen; a failure is told and nothing
 * stays open. Nothing is sent before the item is pressed.
 */
@Composable
private fun FutaberDirectImageSearchHost(
    search: FutaberDirectImageSearch,
    mediaServices: FutaberMediaServices?,
    preferences: Map<String, String>,
    openUrl: (String) -> Unit,
    onNotice: (String) -> Unit,
    onDone: () -> Unit
) {
    val colors = LocalFutaberColors.current
    var result by remember(search) { mutableStateOf<CompatImageSearchResult?>(null) }
    // Leaving (Back, or the thread closing) cancels the search with this effect.
    LaunchedEffect(search) {
        futaberRunDirectImageSearch(mediaServices?.httpClient, search, preferences)
            .onSuccess { result = it }
            .onFailure { failure ->
                if (failure is CancellationException) throw failure
                onNotice(failure.message ?: "画像検索に失敗しました")
                onDone()
            }
    }
    val shown = result
    if (shown == null) {
        PlatformBackHandler { onDone() }
        FutaberHud()
    } else {
        FutachaAppLockAwareWindow {
            CompositionLocalProvider(LocalCompatibilityPalette provides futaberMediaPalette(colors)) {
                CompatReverseImageSearchScreen(
                    result = shown,
                    cookieRepository = mediaServices?.cookieRepository,
                    onClose = onDone,
                    onOpenExternal = openUrl
                )
            }
        }
    }
}
