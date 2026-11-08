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
    val thumbnailPixels: Int = 200,
    /** 従来の動き：画像一覧のセルを正方形に切り抜く（M85。原作は画像全体を出す）。 */
    val legacyGallerySquare: Boolean = false
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
    stripSize = p.ftutterInt(FtutterKeys.GALLERY_TAB_SIZE, 80, 20..300),
    legacyGallerySquare = p.ftutterBool(FTUTTER_LEGACY_GALLERY_SQUARE_KEY, false)
)

/** 従来の動き：画像一覧のセルを正方形に切り抜く（M85）。 */
internal val FTUTTER_LEGACY_GALLERY_SQUARE_KEY: String get() = FtutterKeys.PREFIX + "legacyGallerySquare"
internal const val FTUTTER_LEGACY_GALLERY_SQUARE_LABEL = "画像一覧を正方形に切り抜く"
internal const val FTUTTER_LEGACY_GALLERY_SQUARE_NOTE =
    "原作の画像一覧は画像を切り抜かずに全体を出し、行の高さはその行で一番高い画像に合わせます。ONにすると今までの形（正方形に切り抜く）に戻します。"

/** 画像の縦横比（幅÷高さ）。大きさが分からない時は1（正方形）。 */
internal fun ftutterGalleryImageRatio(post: CompatPostSnapshot): Float {
    val w = post.thumbnailWidth?.takeIf { it > 0 } ?: return 1f
    val h = post.thumbnailHeight?.takeIf { it > 0 } ?: return 1f
    return w.toFloat() / h
}

/**
 * M85：原作の画像一覧のセルの高さ÷幅。各画像は列の幅に収めて全体を出す（高さ＝幅×min(高さ÷幅, 1)）。行の高さは
 * その行で一番高い画像に合わせ、画像は縦の中央に置く。[index]のセルが入る行の値を返す。
 */
internal fun ftutterGalleryRowHeightFraction(media: List<CompatPostSnapshot>, index: Int, columns: Int): Float {
    val cols = columns.coerceAtLeast(1)
    val start = (index / cols) * cols
    val end = minOf(start + cols, media.size)
    var max = 0f
    for (i in start until end) max = maxOf(max, 1f / maxOf(ftutterGalleryImageRatio(media[i]), 1f))
    return if (max > 0f) max else 1f
}

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
