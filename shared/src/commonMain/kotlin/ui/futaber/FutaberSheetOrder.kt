package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.compat.CompatImageSearchResult
import com.valoser.futacha.shared.ui.compat.CompatImageSearchTarget
import com.valoser.futacha.shared.ui.compat.compatAscii2dEndpoint
import com.valoser.futacha.shared.ui.compat.compatAscii2dFailureMessage
import com.valoser.futacha.shared.ui.compat.isCompatImageSearchableMediaUrl
import io.ktor.client.HttpClient

// L-B5「シートの並び」(docs/futaber-evolution-look-2026-10-31.md §2.3〜§2.7, §1.2 M21・M25, §1.3 M05・M23・M24・G28・C26):
// every sheet lists the original's items first, in the original's order, and then this app's own items and the
// extensions, all marked `extra` and made by ONE function per sheet (`futaber…SheetExtras`). F-B2 turns that list into
// the second level of 「その他の機能」 (plan §4.1 item 1) without touching the original's part.

/** [extras] marked as coming after the original's last item (the sheet stops there; they scroll inside it). */
internal fun futaberAsExtras(extras: List<FutaberPostAction>): List<FutaberPostAction> =
    extras.map { if (it.extra) it else it.copy(extra = true) }

// ---- M21: the post's long-press sheet ----

/** The original's order of the sending items: レス削除, 不適切な投稿を通報, そうだね (after the quotes and コピー). */
internal val FUTABER_POST_SHEET_REMOTE_ORDER: List<FutaberRemoteAction> =
    listOf(FutaberRemoteAction.DeleteOwn, FutaberRemoteAction.Report, FutaberRemoteAction.Saidane)

/** 「レス:N」, N counted from 0 as in the original (the post's place in the thread). */
internal fun futaberPostSheetTitle(posts: List<Post>, post: Post): String =
    "レス:${posts.indexOfFirst { it.id == post.id }.coerceAtLeast(0)}"

/**
 * The post sheet (M21): 引用／No.引用／最終行引用／コピー／レス削除／不適切な投稿を通報／そうだね, then [extras]
 * ([futaberPostSheetExtras]). [onCopy] opens the copy panel (M23), or copies at once with the old switch on.
 */
internal fun futaberPostSheetActions(
    post: Post,
    onQuote: (String) -> Unit,
    onCopy: () -> Unit,
    onRemote: (FutaberRemoteAction) -> Unit,
    extras: List<FutaberPostAction>
): List<FutaberPostAction> {
    val body = futaberQuoteBody(post)
    val lastLine = futaberQuoteLastLine(post)
    return listOf(
        FutaberPostAction("quote", "引用", enabled = body.isNotEmpty()) { onQuote(body) },
        FutaberPostAction("quote-number", "No.引用") { onQuote(futaberQuoteByNumber(post)) },
        FutaberPostAction("quote-last", "最終行引用", enabled = lastLine.isNotEmpty()) { onQuote(lastLine) },
        FutaberPostAction("copy", "コピー", onClick = onCopy)
    ) + FUTABER_POST_SHEET_REMOTE_ORDER.map { action -> FutaberPostAction(action.id, action.sheetLabel) { onRemote(action) } } +
        futaberAsExtras(extras)
}

/** What the post sheet has after the original's そうだね: NG登録, レスをマーク／一覧 and the post detail extension ([detail]). */
internal fun futaberPostSheetExtras(
    onNgRegister: () -> Unit,
    markEnabled: Boolean,
    onMark: () -> Unit,
    detail: List<FutaberPostAction>
): List<FutaberPostAction> = futaberAsExtras(
    listOf(
        FutaberPostAction("ng-register", "NG登録", onClick = onNgRegister),
        FutaberPostAction("mark", "レスをマーク／一覧", enabled = markEnabled, onClick = onMark)
    ) + detail
)

// ---- M24: the report's confirmation ----

internal const val FUTABER_REPORT_SEND_LABEL = "delを送信"

/**
 * The confirmation of 「不適切な投稿を通報」 as the original asks it (`F2:post-sheet-024`): a sheet titled 「レス:N」
 * with 「delを送信」 and キャンセル; [extras] ([futaberReportConfirmExtras]) after it.
 */
internal fun futaberReportConfirmActions(onSend: () -> Unit, extras: List<FutaberPostAction>): List<FutaberPostAction> =
    listOf(FutaberPostAction("del-send", FUTABER_REPORT_SEND_LABEL, onClick = onSend)) + futaberAsExtras(extras)

/** The extension 「delの確認でこのレスをNGにも登録」 (reportNg): a second way to send, which also hides the post. */
internal fun futaberReportConfirmExtras(offerNg: Boolean, onSendWithNg: () -> Unit): List<FutaberPostAction> =
    if (!offerNg) emptyList() else futaberAsExtras(
        listOf(FutaberPostAction("del-send-ng", "delを送信してこのレスをNGにも登録", onClick = onSendWithNg))
    )

// ---- M25・C26: a picture's long-press sheet ----

/** The picture sheet's heading: the file name (「1792153182260.jpg」), else the thumbnail's, else 「レス:Nの画像」. */
internal fun futaberImageSheetTitle(post: Post, posts: List<Post>): String =
    futaberImageFileName(post)
        ?: post.thumbnailUrl?.substringBefore('?')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: "${futaberPostSheetTitle(posts, post)}の画像"

/**
 * The picture sheet (M25): 画像を表示／ブラウザで開く／二次元画像詳細検索／URLスキームアクション／画像のURLをコピー／
 * 画像をNG, and GoogleLens検索 when the setting is on (C26: after the sixth until the device capture places it), then
 * [extras] ([futaberImageSheetExtras]). [url] is the picture (the original, else the thumbnail); without it nothing runs.
 * 「URLスキームアクション」 does nothing while no URL scheme action is registered (L-B12): the original does the same.
 */
internal fun futaberImageSheetActions(
    url: String?,
    viewerAvailable: Boolean,
    servicesAvailable: Boolean,
    lens: Boolean,
    onView: () -> Unit,
    onBrowser: (String) -> Unit,
    onAscii2d: (String) -> Unit,
    onUrlScheme: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onNg: (String) -> Unit,
    onLens: (String) -> Unit,
    extras: List<FutaberPostAction>
): List<FutaberPostAction> = listOfNotNull(
    FutaberPostAction("image-viewer", "画像を表示", enabled = url != null && viewerAvailable, onClick = onView),
    FutaberPostAction("image-external", "ブラウザで開く", enabled = url != null) { url?.let(onBrowser) },
    FutaberPostAction("image-ascii2d", "二次元画像詳細検索", enabled = url != null && servicesAvailable) { url?.let(onAscii2d) },
    FutaberPostAction("image-url-scheme", "URLスキームアクション", onClick = onUrlScheme),
    FutaberPostAction("image-copy-url", "画像のURLをコピー", enabled = url != null) { url?.let(onCopyUrl) },
    FutaberPostAction("image-ng", "画像をNG", enabled = url != null && servicesAvailable) { url?.let(onNg) },
    if (lens) FutaberPostAction("image-lens", "GoogleLens検索", enabled = url != null && servicesAvailable) { url?.let(onLens) } else null
) + futaberAsExtras(extras)

/**
 * What the picture sheet has after the original's items: 「画像検索」 (the shared search with its choice of services,
 * as before) and the picture menu extension ([menu], [futaberImageMenuExtraActions]: save and share; its three items
 * that the original's items now do are gone from it).
 */
internal fun futaberImageSheetExtras(
    url: String?,
    servicesAvailable: Boolean,
    onSearch: (String) -> Unit,
    menu: List<FutaberPostAction>
): List<FutaberPostAction> = futaberAsExtras(
    listOf(FutaberPostAction("image-search", "画像検索", enabled = url != null && servicesAvailable) { url?.let(onSearch) }) + menu
)

/** A search the picture sheet starts at once, without the choice of services (二次元画像詳細検索, GoogleLens検索). */
internal data class FutaberDirectImageSearch(val imageUrl: String, val target: CompatImageSearchTarget)

/** What 二次元画像詳細検索 asks: ascii2d with the picture's address (its address setting is the shared one). */
internal fun futaberAscii2dSearch(imageUrl: String) = FutaberDirectImageSearch(imageUrl, CompatImageSearchTarget.ASCII2D_URL)

/** What GoogleLens検索 asks: Google Lens with the picture's address (only an address is built; nothing is sent until shown). */
internal fun futaberLensSearch(imageUrl: String) = FutaberDirectImageSearch(imageUrl, CompatImageSearchTarget.LENS_URL)

/**
 * Runs [search] the way the shared image search does for that service (only when the item is pressed): ascii2d sends the
 * picture's address to the address in the shared settings and answers its result page; Lens only builds its address.
 * A GIF, a video or a failed search answers why, in words for the notice.
 */
internal suspend fun futaberRunDirectImageSearch(
    httpClient: HttpClient?,
    search: FutaberDirectImageSearch,
    preferences: Map<String, String>
): Result<CompatImageSearchResult> {
    val ascii2d = search.target == CompatImageSearchTarget.ASCII2D_URL
    if (!isCompatImageSearchableMediaUrl(search.imageUrl, allowGif = !ascii2d)) {
        return Result.failure(IllegalArgumentException(if (ascii2d) "GIF・WebM・MP4は検索できません" else "WebM・MP4は検索できません"))
    }
    val endpoint = compatAscii2dEndpoint(preferences)
    return com.valoser.futacha.shared.ui.board.searchThreadImage(httpClient, search.imageUrl, search.target, endpoint)
        .recoverCatching { error ->
            if (error is kotlin.coroutines.cancellation.CancellationException) throw error
            val message = error.message ?: "画像検索に失敗しました"
            throw IllegalStateException(if (ascii2d) compatAscii2dFailureMessage(message, endpoint) else message, error)
        }
}

// ---- M05: a catalog row's long-press sheet ----

/**
 * The catalog row's sheet (M05): URLスキームアクション／URLをコピー／スレッドをNG, and 履歴から削除 (red) only when the
 * thread is in the history (the original leaves it out otherwise), then [extras] ([futaberCatalogSheetExtras]).
 */
internal fun futaberCatalogSheetActions(
    inHistory: Boolean,
    ngThreadEnabled: Boolean,
    onUrlScheme: () -> Unit,
    onCopyUrl: () -> Unit,
    onNgThread: () -> Unit,
    onRemoveHistory: () -> Unit,
    extras: List<FutaberPostAction>
): List<FutaberPostAction> = listOfNotNull(
    FutaberPostAction("url-scheme", "URLスキームアクション", onClick = onUrlScheme),
    FutaberPostAction("copy-url", "URLをコピー", onClick = onCopyUrl),
    FutaberPostAction("ng-thread", "スレッドをNG", enabled = ngThreadEnabled, onClick = onNgThread),
    if (inHistory) FutaberPostAction("remove-history", "履歴から削除", destructive = true, onClick = onRemoveHistory) else null
) + futaberAsExtras(extras)

/** What the catalog row's sheet has after the original's items: ブラウザで開く, 不適切なスレッドを通報 (it asks first) and the extension's 画像NG ([imageNg]). */
internal fun futaberCatalogSheetExtras(
    onBrowser: () -> Unit,
    onReport: () -> Unit,
    imageNg: FutaberPostAction?
): List<FutaberPostAction> = futaberAsExtras(
    listOfNotNull(
        FutaberPostAction("browser", "ブラウザで開く", onClick = onBrowser),
        FutaberPostAction("report", "不適切なスレッドを通報", onClick = onReport),
        imageNg
    )
)

// ---- The tab's long-press sheet ----

/**
 * The tab sheet: タブを削除／右側のタブを削除／他のタブを削除／スレ落ちしたタブを削除／タブをすべて削除 (red), as the
 * original has them (one that cannot do anything now stays grey), then [extras] ([futaberTabSheetExtras]).
 */
internal fun futaberTabSheetActions(
    hasRight: Boolean,
    hasOthers: Boolean,
    hasFallen: Boolean,
    onRemove: () -> Unit,
    onRemoveRight: () -> Unit,
    onRemoveOthers: () -> Unit,
    onRemoveFallen: () -> Unit,
    onRemoveAll: () -> Unit,
    extras: List<FutaberPostAction>
): List<FutaberPostAction> = listOf(
    FutaberPostAction("tab-remove", "タブを削除", onClick = onRemove),
    FutaberPostAction("tab-remove-right", "右側のタブを削除", enabled = hasRight, onClick = onRemoveRight),
    FutaberPostAction("tab-remove-others", "他のタブを削除", enabled = hasOthers, onClick = onRemoveOthers),
    FutaberPostAction("tab-remove-fallen", "スレ落ちしたタブを削除", enabled = hasFallen, onClick = onRemoveFallen),
    FutaberPostAction("tab-remove-all", "タブをすべて削除", destructive = true, onClick = onRemoveAll)
) + futaberAsExtras(extras)

/** What the tab sheet has after the original's items: 閉じたタブを元に戻す and 全タブを確認 (extensions; null while off). */
internal fun futaberTabSheetExtras(
    restore: FutaberPostAction?,
    checkAll: FutaberPostAction?,
    closeFallen: FutaberPostAction? = null
): List<FutaberPostAction> = futaberAsExtras(listOfNotNull(restore, checkAll, closeFallen))

// ---- G28: the IP's long-press sheet ----

/** The IP sheet: 「IPをNG」 only (its heading is the IP as the header writes it, 「IP:14.9.*(enabler.ne.jp)」). */
internal fun futaberIpSheetActions(onNg: () -> Unit): List<FutaberPostAction> =
    listOf(FutaberPostAction("ip-ng", "IPをNG", onClick = onNg))

/**
 * The entry 「IPをNG」 adds to the shared 「NGネーム・ID等」 (AppState `ngHeaders`, the minimal place until D-B7): the IP as
 * the header writes it, with its 「IP:」. Every mode matches that list as a part of the header (the date line holds the
 * IP), and としあき(仮)'s common NG also reads 「IP:…」 as that poster, so the entry hides the poster's posts everywhere.
 */
internal fun futaberIpNgEntry(ip: String): String {
    val value = ip.trim()
    return if (value.startsWith("IP:", ignoreCase = true)) value else "IP:$value"
}

/** What is told after 「IPをNG」: the list is full ([futaberNgFullMessage]), or the entry was added. */
internal fun futaberIpNgNotice(headers: List<String>, entry: String): String =
    futaberNgFullMessage(headers) ?: "NGネーム・ID等に「$entry」を追加しました"

// ---- M23: the copy panel ----

/** What the copy panel shows: [header] (as the original writes it: 題名 名前 日時 IP/ID No. そうだねxN) over [body]. */
internal data class FutaberCopyPanelText(val header: String, val body: String) {
    val text: String get() = if (header.isEmpty()) body else "$header\n$body"

    /** Where the body starts in [text]: the panel opens with the body selected (from here to the end). */
    val bodyStart: Int get() = if (header.isEmpty()) 0 else header.length + 1
}

/**
 * The copy panel's text of [post] (`F2:post-sheet-023`): the header line 「無題 名無し 26/10/16(金)22:37:55
 * IP:130.62.*(transix.jp) No.325109 そうだねx28」 and the body, with the picture's file name as its last line (the
 * original's body has it).
 */
internal fun futaberCopyPanelText(post: Post): FutaberCopyPanelText {
    val header = listOfNotNull(
        post.subject?.trim()?.takeIf { it.isNotEmpty() },
        post.author?.trim()?.takeIf { it.isNotEmpty() },
        post.timestamp.trim().takeIf { it.isNotEmpty() },
        "No.${post.id}",
        futaberSaidaneCount(post.saidaneLabel)?.let { "そうだねx$it" }
    ).joinToString(" ")
    val body = listOfNotNull(
        futaberPostPlainText(post).trimEnd().takeIf { it.isNotEmpty() },
        futaberImageFileName(post)
    ).joinToString("\n")
    return FutaberCopyPanelText(header, body)
}

/** The copy panel's 「引用」: each line of [selected] that is not blank, after ">". */
internal fun futaberQuoteSelection(selected: String): String =
    selected.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n") { ">$it" }
