package com.valoser.futacha.shared.compat

enum class CompatToolbarSurface { CATALOG, THREAD, VIEWER, POST }

const val COMPAT_REFERENCE_HELP_TITLE = "ヘルプ"

/** Semantic glyphs used by the reference toolbars. */
enum class CompatToolbarGlyph {
    EDIT, SEND, REFRESH, SEARCH, SORT, BOARD, TABS, PRIVACY, NETWORK,
    CHECK_UPDATES, UNDO, HISTORY, FILTER, NG_TOGGLE, DRAWER, TOP,
    PAGE_UP, PAGE_DOWN, BOTTOM, GALLERY, BACK_TO_POST, SCROLL, CLOSE, AUTO_SCROLL,
    PREVIOUS, NEXT, SHARE, DOWNLOAD, ATTACH, UPLOAD, INFO, VOICE_INPUT,
    DEVICE_INFO, SCREEN, MORE
}

/**
 * Keep reference semantics separate from Compose icons so unrelated adjacent
 * actions cannot silently fall back to the same placeholder artwork.
 */
fun compatToolbarGlyph(key: String): CompatToolbarGlyph = when (key) {
    "post", "pallete" -> CompatToolbarGlyph.EDIT
    "send" -> CompatToolbarGlyph.SEND
    "reload", "reset" -> CompatToolbarGlyph.REFRESH
    "search" -> CompatToolbarGlyph.SEARCH
    "sort" -> CompatToolbarGlyph.SORT
    "board" -> CompatToolbarGlyph.BOARD
    "tab" -> CompatToolbarGlyph.TABS
    "privacy" -> CompatToolbarGlyph.PRIVACY
    "bypass", "network_info" -> CompatToolbarGlyph.NETWORK
    "check" -> CompatToolbarGlyph.CHECK_UPDATES
    "undo" -> CompatToolbarGlyph.UNDO
    "dropped" -> CompatToolbarGlyph.HISTORY
    "extract" -> CompatToolbarGlyph.FILTER
    "quickng" -> CompatToolbarGlyph.NG_TOGGLE
    "drawer" -> CompatToolbarGlyph.DRAWER
    "top" -> CompatToolbarGlyph.TOP
    "page_up" -> CompatToolbarGlyph.PAGE_UP
    "page_down" -> CompatToolbarGlyph.PAGE_DOWN
    "bottom" -> CompatToolbarGlyph.BOTTOM
    "gallery" -> CompatToolbarGlyph.GALLERY
    "back" -> CompatToolbarGlyph.BACK_TO_POST
    "scroll" -> CompatToolbarGlyph.SCROLL
    "close", "discard" -> CompatToolbarGlyph.CLOSE
    "autoscroll" -> CompatToolbarGlyph.AUTO_SCROLL
    "left", "previous" -> CompatToolbarGlyph.PREVIOUS
    "right", "next" -> CompatToolbarGlyph.NEXT
    "share" -> CompatToolbarGlyph.SHARE
    "download" -> CompatToolbarGlyph.DOWNLOAD
    "attach" -> CompatToolbarGlyph.ATTACH
    "sio" -> CompatToolbarGlyph.UPLOAD
    "info" -> CompatToolbarGlyph.INFO
    "voice_input" -> CompatToolbarGlyph.VOICE_INPUT
    "model_info" -> CompatToolbarGlyph.DEVICE_INFO
    "screen" -> CompatToolbarGlyph.SCREEN
    else -> CompatToolbarGlyph.MORE
}

enum class CompatToolbarRefreshTarget { CURRENT_CONTENT, OPEN_THREADS }

fun compatToolbarRefreshTarget(surface: CompatToolbarSurface, key: String): CompatToolbarRefreshTarget? = when {
    key == "reload" -> CompatToolbarRefreshTarget.CURRENT_CONTENT
    key == "check" && surface in setOf(CompatToolbarSurface.CATALOG, CompatToolbarSurface.THREAD) ->
        CompatToolbarRefreshTarget.OPEN_THREADS
    else -> null
}

data class CompatToolbarMasterItem(
    val key: String,
    val label: String,
    val defaultActive: Boolean
)

data class CompatToolbarItem(
    val key: String,
    val position: Int,
    val active: Boolean
)

val COMPAT_TOOLBAR_MASTERS: Map<CompatToolbarSurface, List<CompatToolbarMasterItem>> = mapOf(
    CompatToolbarSurface.CATALOG to listOf(
        CompatToolbarMasterItem("post", "スレ立て", true),
        CompatToolbarMasterItem("reload", "リロード", true),
        CompatToolbarMasterItem("search", "スレッド検索", true),
        CompatToolbarMasterItem("sort", "表示順", true),
        CompatToolbarMasterItem("board", "板一覧", true),
        CompatToolbarMasterItem("tab", "タブ一覧", true),
        CompatToolbarMasterItem("privacy", "プライバシー", false),
        CompatToolbarMasterItem("bypass", "通信の軽量化", false),
        CompatToolbarMasterItem("check", "更新の確認", false),
        // This is CatalogActivity#getPrevCatalogData in sample/1.apk. Closed
        // thread restoration is a separate overflow-menu command.
        CompatToolbarMasterItem("undo", "リロード前に戻す", true),
        // The dropped-thread list is part of the catalog's normal command
        // surface in sample/1.apk.  It must be reachable on a fresh install;
        // hiding it behind toolbar editing makes the feature appear absent.
        CompatToolbarMasterItem("dropped", "消えたスレ", true),
        CompatToolbarMasterItem("quickng", "NG切り替え", true),
        CompatToolbarMasterItem("drawer", "ドロワーを開く", true)
    ),
    CompatToolbarSurface.THREAD to listOf(
        CompatToolbarMasterItem("post", "書き込み", true),
        CompatToolbarMasterItem("reload", "リロード", true),
        CompatToolbarMasterItem("undo", "リロード前に戻す", true),
        CompatToolbarMasterItem("search", "レス検索", true),
        CompatToolbarMasterItem("top", "ページ最上部へ", true),
        CompatToolbarMasterItem("page_up", "1ページ上へ", true),
        CompatToolbarMasterItem("page_down", "1ページ下へ", true),
        CompatToolbarMasterItem("bottom", "ページ最下部へ", true),
        CompatToolbarMasterItem("gallery", "画像一覧", true),
        CompatToolbarMasterItem("tab", "タブ一覧", true),
        CompatToolbarMasterItem("privacy", "プライバシー", false),
        CompatToolbarMasterItem("extract", "レス抽出", true),
        CompatToolbarMasterItem("bypass", "通信の軽量化", false),
        CompatToolbarMasterItem("scroll", "スクロールバー", false),
        CompatToolbarMasterItem("check", "更新の確認", false),
        CompatToolbarMasterItem("close", "スレを閉じる", false),
        CompatToolbarMasterItem("quickng", "NG切り替え", true),
        CompatToolbarMasterItem("drawer", "ドロワーを開く", true),
        CompatToolbarMasterItem("autoscroll", "オートスクロール", true)
    ),
    CompatToolbarSurface.VIEWER to listOf(
        CompatToolbarMasterItem("download", "保存する", true),
        CompatToolbarMasterItem("search", "類似検索", true),
        CompatToolbarMasterItem("back", "レスに戻る", true),
        CompatToolbarMasterItem("gallery", "画像一覧", true),
        CompatToolbarMasterItem("left", "前の画像", true),
        CompatToolbarMasterItem("right", "次の画像", true),
        CompatToolbarMasterItem("share", "画像を共有", false),
        CompatToolbarMasterItem("info", "詳細情報", true),
        CompatToolbarMasterItem("screen", "画面モード", false),
        CompatToolbarMasterItem("privacy", "プライバシー", true)
    ),
    CompatToolbarSurface.POST to listOf(
        CompatToolbarMasterItem("send", "送信する", true),
        CompatToolbarMasterItem("attach", "添付画像", true),
        CompatToolbarMasterItem("pallete", "手書き", true),
        // sample/1.apk seeds the あぷ小 item as inactive.  It remains
        // available in the toolbar editor, but must not appear on a fresh
        // install until the user enables it.
        CompatToolbarMasterItem("sio", "あぷ小", false),
        CompatToolbarMasterItem("voice_input", "音声入力", false),
        CompatToolbarMasterItem("network_info", "回線情報", false),
        CompatToolbarMasterItem("model_info", "機種情報", false),
        CompatToolbarMasterItem("reset", "リセット", false),
        CompatToolbarMasterItem("discard", "内容の破棄", true)
    )
)

fun compatToolbarMaster(surface: CompatToolbarSurface): List<CompatToolbarMasterItem> =
    COMPAT_TOOLBAR_MASTERS.getValue(surface)

/**
 * Catalog, thread and viewer keep their fixed overflow button even when every
 * command is visible.  The reference post form is the exception: its overflow
 * button exists only while at least one command is outside the toolbar.
 */
fun compatToolbarShowsOverflow(
    surface: CompatToolbarSurface,
    items: List<CompatToolbarItem>
): Boolean = surface != CompatToolbarSurface.POST || items.any { !it.active }

fun reconcileCompatToolbar(
    surface: CompatToolbarSurface,
    persisted: List<CompatToolbarItem>
): List<CompatToolbarItem> {
    val master = compatToolbarMaster(surface)
    val masterByKey = master.associateBy(CompatToolbarMasterItem::key)
    val canonicalPersisted = if (surface == CompatToolbarSurface.VIEWER) {
        persisted.map { item ->
            when (item.key) {
                "previous" -> item.copy(key = "left")
                "next" -> item.copy(key = "right")
                else -> item
            }
        }
    } else persisted
    val existing = canonicalPersisted
        .asSequence()
        .filter { it.key in masterByKey }
        .distinctBy(CompatToolbarItem::key)
        .sortedWith(compareBy<CompatToolbarItem> { it.position }.thenBy { it.key })
        .toMutableList()
    if (existing.isEmpty()) {
        return master.mapIndexed { index, item -> CompatToolbarItem(item.key, index, item.defaultActive) }
    }
    master.forEachIndexed { defaultIndex, item ->
        if (existing.none { it.key == item.key }) {
            existing.add(defaultIndex.coerceAtMost(existing.size), CompatToolbarItem(item.key, defaultIndex, item.defaultActive))
        }
    }
    return existing.mapIndexed { index, item -> item.copy(position = index) }
}

fun validateCompatToolbar(
    surface: CompatToolbarSurface,
    items: List<CompatToolbarItem>
): Boolean {
    val keys = items.map(CompatToolbarItem::key)
    return keys.size == keys.toSet().size &&
        keys.toSet() == compatToolbarMaster(surface).mapTo(mutableSetOf(), CompatToolbarMasterItem::key) &&
        items.map(CompatToolbarItem::position).toSet() == items.indices.toSet()
}
