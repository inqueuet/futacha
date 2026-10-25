package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.toCompatPostSnapshot
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.compat.CompatibilityPalette
import com.valoser.futacha.shared.ui.compat.compatViewerMediaPosts

/**
 * 画像一覧・ビューアの設定（原作 Setting_ImgMovieViewer と、画面側で保存する値）。
 */
internal data class FtutterMediaSettings(
    /** スレの画像をタップした時、スレ内の全画像をつなげる（OFFならそのレスの画像だけ）。 */
    val connectAll: Boolean = true,
    /** 削除されたレスの画像を一覧・ビューアに出さない（ビューアでは、開いた画像そのものは残す）。 */
    val excludeDeleted: Boolean = true,
    val galleryColumns: Int = 4,
    /** ビューアの下のサムネイル帯。 */
    val showStrip: Boolean = true,
    val stripSize: Int = 80,
    val videoAutoPlay: Boolean = false,
    val videoLoop: Boolean = false,
    val reduceThumbnail: Boolean = true,
    val thumbnailPixels: Int = 200
)

internal fun parseFtutterMediaSettings(p: Map<String, String>) = FtutterMediaSettings(
    connectAll = p.ftutterBool(FtutterKeys.CONNECT_ALL_IMAGES, true),
    excludeDeleted = p.ftutterBool(FtutterKeys.EXCLUDE_DELETED_IMAGES, true),
    // 原作は−が下限1、＋は上限なし。表示が壊れないよう20までにする。
    galleryColumns = p.ftutterInt(FtutterKeys.GALLERY_COLUMNS, 4, 1..20),
    showStrip = p.ftutterBool(FtutterKeys.SHOW_GALLERY_TAB, true),
    videoAutoPlay = p.ftutterBool(FtutterKeys.VIDEO_AUTO_PLAY, false),
    videoLoop = p.ftutterBool(FtutterKeys.VIDEO_LOOP, false),
    reduceThumbnail = p.ftutterBool(FtutterKeys.USE_MEM_SIZE, true),
    thumbnailPixels = p.ftutterInt(FtutterKeys.MEM_SIZE, 200, 1..4096),
    stripSize = p.ftutterInt(FtutterKeys.GALLERY_TAB_SIZE, 80, 20..300)
)

/**
 * 一覧・ビューアに並べる画像（本体ファイルと、本文中の「あぷ・あぷ小」のリンク。共有の`compatViewerMediaPosts`）。
 * [position]は0始まりの通し番号（OPが0）で、番号カードに出る。
 * - [onlyPostId]：そのレスの画像だけ（「スレ内の画像を全て繋げて表示する」がOFFの時）。
 * - [keepPostId]：削除レスを除く時も、開いた画像のレスは残す（原作のビューア）。NGの非表示には適用しない。
 */
internal fun ftutterMediaPosts(
    posts: List<Post>,
    excludeDeleted: Boolean,
    keepPostId: String? = null,
    onlyPostId: String? = null,
    hiddenPostIds: Set<String> = emptySet()
): List<CompatPostSnapshot> {
    val snapshots = posts.mapIndexed { i, post -> post.toCompatPostSnapshot(i) }
        .filter { onlyPostId == null || it.postNo == onlyPostId }
    val deleted = if (excludeDeleted) posts.filter { it.isDeleted && it.id != keepPostId }.map { it.id }.toSet() else emptySet()
    return compatViewerMediaPosts(snapshots, hiddenPostNos = deleted + hiddenPostIds)
}

/** A late NG/AI/image-hash result must also hide an already-held gallery/viewer snapshot. */
internal fun ftutterVisibleMedia(
    media: List<CompatPostSnapshot>, hiddenPostIds: Set<String>
): List<CompatPostSnapshot> = if (hiddenPostIds.isEmpty()) media else media.filterNot { it.postNo in hiddenPostIds }

/** 共有の画面（画像検索など）の色を、このモードの色に合わせる。 */
internal fun ftutterMediaPalette(colors: FtutterColors): CompatibilityPalette = CompatibilityPalette(
    chrome = colors.toolBar,
    chromeContent = colors.onToolBar,
    statusBarChrome = colors.toolBar,
    background = colors.background,
    divider = colors.outline,
    text = colors.text,
    headerSubject = colors.headerText,
    headerAuthor = colors.headerText,
    headerEmail = colors.link,
    headerSelfPost = colors.kakikomi,
    headerSelfQuote = colors.quote,
    headerQuoteAdd = colors.quote,
    headerSubtext = colors.headerText,
    identityTotal = colors.headerText,
    saidane = colors.deleted,
    saidaneMax = colors.deleted,
    fileName = colors.link,
    bodyLink = colors.link,
    bodyQuote = colors.quote,
    bodyDropSoon = colors.deleted,
    bodyIp = colors.link,
    bodyErased = colors.headerText,
    menuSurface = colors.popUp,
    dialogSurface = colors.popUp,
    uiPrimaryText = colors.text,
    uiSecondaryText = colors.headerText,
    searchResultBackground = colors.popUp,
    searchTextHighlight = colors.highlight,
    accent = colors.onToolBarActive,
    inputCursor = colors.onToolBarActive,
    closedThreadUndoAction = colors.onToolBarActive,
    loadingIcon = colors.onToolBarActive,
    loadingProgress = colors.onToolBarActive
)

/** 選択保存の確認の文（原作「N個のファイルを保存しますか？」）。 */
internal fun ftutterSelectSaveQuestion(count: Int): String = "${count}個のファイルを保存しますか？"

/** ビューアを縦のスワイプで閉じる：画面の高さの4割を超えたか、速く払った時（原作 Dismissible。実機で確かめた閾値）。 */
internal fun ftutterViewerShouldClose(dy: Float, dx: Float, heightPx: Float, millis: Long): Boolean {
    if (kotlin.math.abs(dy) <= kotlin.math.abs(dx) * 1.5f) return false
    val distance = kotlin.math.abs(dy)
    return distance > heightPx * 0.4f || (distance > heightPx * 0.15f && millis in 1..250)
}
