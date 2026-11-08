package com.valoser.futacha.shared.ui.futaber

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowDown
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Tab
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.ui.board.messageHtmlToLines

// ---- L-B6 (M20): the operation menu's eleven slots of the original, then this app's own items and the extensions ----

/**
 * The original's eleven items in its order (M20, 4 columns: URLをコピーする／画像を小さくする／返信が多いレスを表示／
 * 読み上げを開始, URLスキームアクション／お気に入りに追加／MHTで保存／NG編集, NGを無効にする／新着へスクロール／
 * プレイヤーを表示). The ids are the items' tags (`futaber-menu-<id>`).
 */
internal val FUTABER_MENU_SLOT_IDS: List<String> = listOf(
    "copy-url", "small-images", "many-replies", "read-aloud",
    "url-scheme", "favorite", "mht-save", "ng-edit",
    "ng-toggle", "new", FUTABER_MENU_PLAYER_ID
)

/** The 11th slot, 「プレイヤーを表示」: the player (M19, L-B12). */
internal const val FUTABER_MENU_PLAYER_ID = "player"

/** The menu's columns and the rows the grid shows before it scrolls (the original's 320×300 grid of 80×100 cells). */
internal const val FUTABER_MENU_COLUMNS = 4
internal const val FUTABER_MENU_VISIBLE_ROWS = 3

/** The items that draw a word for an icon (+NG, NG OFF／NG ON, ↓New), drawn a little larger as the original's are. */
internal val FUTABER_MENU_TEXT_ICON_IDS: Set<String> = setOf("ng-edit", "ng-toggle", "new")

/**
 * The cells of the menu: the original's eleven slots in its order, each from [slots] by its id (a slot with no item,
 * as 「プレイヤーを表示」 since L-B12, may be null for an empty cell), then [extras] from the 12th cell on. An item of [slots]
 * whose id is not a slot of the original is not shown (it belongs to [extras]).
 */
internal fun futaberOperationMenuItems(slots: List<FutaberMenuItem>, extras: List<FutaberMenuItem>): List<FutaberMenuItem?> {
    val byId = slots.associateBy { it.id }
    return FUTABER_MENU_SLOT_IDS.map { byId[it] } + extras
}

/** A toggling extra item: on or off, and whether it can be pressed now. */
internal data class FutaberMenuToggle(val active: Boolean, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * Every item the menu has after the original's eleven (plan §4.1-1, L §2.2): this app's own items in their old order
 * (新着を待って読み上げ／スレッドを保存／URLを共有／ブラウザで開く／更新前に戻す／タブに追加／書き込む), then the
 * extensions in the order of the extensions page (レスを抽出・オートスクロール・URL・アーカイブ・キャッシュ検索・
 * NGの詳細管理・1ページ上下・先頭へ・画像編集・動画編集). F-B2 turns this list into the second level behind
 * 「その他の機能」 in the 12th cell. Each item closes the menu ([close]) before it acts.
 */
internal fun futaberOperationMenuExtras(
    close: () -> Unit,
    liveReadAloudEnabled: Boolean,
    onLiveReadAloud: () -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onShareUrl: () -> Unit,
    onOpenBrowser: () -> Unit,
    undoReloadEnabled: Boolean,
    onUndoReload: () -> Unit,
    isTabbed: Boolean,
    tabEnabled: Boolean,
    onToggleTab: () -> Unit,
    onWrite: () -> Unit,
    extract: FutaberMenuToggle? = null,
    autoScroll: FutaberMenuToggle? = null,
    onArchive: (() -> Unit)? = null,
    onCacheSearch: (() -> Unit)? = null,
    cacheSearchEnabled: Boolean = true,
    ngDetail: FutaberNgDetailEntry? = null,
    scrollItems: List<FutaberMenuItem> = emptyList(),
    onImageEdit: (() -> Unit)? = null,
    onVideoEdit: (() -> Unit)? = null,
    evolution: List<FutaberMenuItem> = emptyList()
): List<FutaberMenuItem> {
    fun closing(action: () -> Unit): () -> Unit = { close(); action() }
    return listOf(
        FutaberMenuItem("live-read-aloud", "新着を待って読み上げ", Icons.Outlined.RecordVoiceOver,
            enabled = liveReadAloudEnabled, onClick = closing(onLiveReadAloud)),
        FutaberMenuItem("save", "スレッドを保存", Icons.Outlined.SaveAlt, enabled = saveEnabled, onClick = closing(onSave)),
        FutaberMenuItem("share-url", "URLを共有", Icons.Outlined.IosShare, onClick = closing(onShareUrl)),
        FutaberMenuItem("open-browser", "ブラウザで開く", Icons.Outlined.OpenInBrowser, onClick = closing(onOpenBrowser)),
        FutaberMenuItem("undo-reload", "更新前に戻す", Icons.AutoMirrored.Outlined.Undo, enabled = undoReloadEnabled,
            onClick = closing(onUndoReload)),
        FutaberMenuItem("tab", if (isTabbed) "タブから外す" else "タブに追加", Icons.Outlined.Tab,
            enabled = tabEnabled, active = isTabbed, onClick = closing(onToggleTab)),
        FutaberMenuItem("write", "書き込む", Icons.Outlined.Edit, onClick = closing(onWrite))
    ) + listOfNotNull(
        extract?.let {
            FutaberMenuItem("extract", "レスを抽出", Icons.Outlined.FilterList, enabled = it.enabled, active = it.active,
                onClick = closing(it.onClick))
        },
        autoScroll?.let {
            FutaberMenuItem("auto-scroll", if (it.active) "オートスクロールを停止" else "オートスクロール",
                Icons.Outlined.KeyboardDoubleArrowDown, enabled = it.enabled, active = it.active, onClick = closing(it.onClick))
        },
        onArchive?.let { FutaberMenuItem("archive", "URL・アーカイブ", Icons.Outlined.Archive, onClick = closing(it)) },
        futaberCacheSearchMenuItem(onCacheSearch, cacheSearchEnabled, close),
        futaberNgDetailMenuItem(ngDetail, close)
    ) + scrollItems + futaberMediaEditMenuItems(onImageEdit, onVideoEdit, close) + evolution
}

// ---- M10: 「画像を小さくする」 ----

/** futaber's small pictures: 0.3 of the usual size (175×132 → 53×40), so 0.21 of the thumbnail. */
internal const val FUTABER_SMALL_IMAGE_SCALE = 0.3f

/**
 * The ratio a post's picture is drawn at ([futaberAttachmentSize]): [base] (the original's 0.7, or the settings' ratio
 * of it, C21: [futaberSettingsImageRatio]), or 0.3 of that when small.
 */
internal fun futaberAttachmentRatio(small: Boolean, base: Float = FUTABER_ATTACHMENT_RATIO): Float =
    if (small) base * FUTABER_SMALL_IMAGE_SCALE else base

/** The item's label, as futaber's turns after it is pressed (42-02). */
internal fun futaberSmallImagesLabel(small: Boolean): String = if (small) "画像を大きくする" else "画像を小さくする"

// ---- M17: 「NGを無効にする」 ----

/** The item's label, as futaber's turns after it is pressed (49-02). */
internal fun futaberNgToggleLabel(ngDisabled: Boolean): String = if (ngDisabled) "NGを有効にする" else "NGを無効にする"

// ---- M08・M11: what the search word and 「返信が多いレスを表示」 show ----

/** The one bubble's text when nothing is found (futaber's 43-01 and 40-03). */
internal const val FUTABER_NOTHING_FOUND = "レスが見つかりませんでした"

/** The line over the found posts while the search word is typed (futaber's 40-02: 「8レスが見つかりました」). */
internal fun futaberSearchFoundLabel(count: Int): String = "${count}レスが見つかりました"

/**
 * The posts the menu's search word finds (M08): the posts NG does not hide whose text (body, subject, name, ID, number)
 * holds [query], compared the way the thread's search always compared (full / half width and kana folded alike).
 * [texts] (of these [posts]) saves working the texts out again. A blank word finds nothing.
 */
internal fun futaberSearchMatches(
    posts: List<Post>,
    query: String,
    hidden: Set<String> = emptySet(),
    texts: FutaberPostTexts? = null
): List<Post> {
    if (query.isBlank()) return emptyList()
    return futaberVisibleRows(posts, FutaberViewFilter(query = query), emptyMap(), hidden, texts = texts, ngHidden = hidden)
        .map { it.post }
}

/** The posts 「返信が多いレスを表示」 shows (M11): those NG does not hide with at least [threshold] replies, in thread order. */
internal fun futaberManyRepliesPosts(
    posts: List<Post>,
    replyIndex: Map<String, List<Post>>,
    hidden: Set<String> = emptySet(),
    threshold: Int = FUTABER_MANY_REPLIES_THRESHOLD
): List<Post> = futaberVisibleRows(posts, FutaberViewFilter(repliesOnly = true), replyIndex, hidden, threshold, ngHidden = hidden)
    .map { it.post }

/** A found post's line in the search list: the first line of its body with text (futaber lists the first lines, 40-02). */
internal fun futaberSearchMatchLine(post: Post): String =
    messageHtmlToLines(post.messageHtml).map { it.trim() }.firstOrNull { it.isNotEmpty() }
        ?: post.subject?.takeIf { it.isNotBlank() } ?: "No.${post.id}"

/**
 * F-B3 E8/E9/E16: the other modes' entries ふたばー風 now offers — the privacy filter, the previous/next tab and the tree view.
 * Each is null/absent when the host cannot do it.
 */
internal fun futaberEvolutionMenuItems(
    close: () -> Unit,
    privacyEnabled: Boolean?,
    onTogglePrivacy: () -> Unit,
    onTabStep: ((forward: Boolean) -> Unit)?,
    treeOn: Boolean,
    onToggleTree: () -> Unit
): List<FutaberMenuItem> = buildList {
    if (privacyEnabled != null) add(FutaberMenuItem("privacy", if (privacyEnabled) "プライバシー表示を解除" else "プライバシー表示",
        Icons.Outlined.VisibilityOff, active = privacyEnabled) { close(); onTogglePrivacy() })
    if (onTabStep != null) {
        add(FutaberMenuItem("prev-tab", "前のタブ", Icons.AutoMirrored.Outlined.ArrowBack) { close(); onTabStep(false) })
        add(FutaberMenuItem("next-tab", "次のタブ", Icons.AutoMirrored.Outlined.ArrowForward) { close(); onTabStep(true) })
    }
    add(FutaberMenuItem("tree", if (treeOn) "通常表示" else "ツリー表示", Icons.Outlined.AccountTree, active = treeOn) { close(); onToggleTree() })
}
