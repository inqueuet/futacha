package com.valoser.futacha.shared.compat

enum class CompatOtherMenuRoute {
    CATALOG_ROOT,
    CATALOG_NG,
    CATALOG_EXTRACT,
    THREAD_ROOT,
    THREAD_SAVE,
    THREAD_NG,
    THREAD_URL,
    THREAD_EXTRACT
}

data class CompatOtherMenuItem(
    val key: String,
    val label: String,
    val childRoute: CompatOtherMenuRoute? = null,
    val enabled: Boolean = true,
    val deferredExternalBackend: Boolean = false
)

fun compatCatalogOtherMenu(
    route: CompatOtherMenuRoute,
    ngEnabled: Boolean,
    replyPriorityEnabled: Boolean,
    showNonPriority: Boolean,
    canUndoClose: Boolean,
    cacheEnabled: Boolean? = null,
    activeToolbarKeys: Set<String> = emptySet()
): List<CompatOtherMenuItem> = when (route) {
    CompatOtherMenuRoute.CATALOG_ROOT -> listOf(
        CompatOtherMenuItem("extract", "監視ワード"),
        CompatOtherMenuItem("ng", "NG管理", CompatOtherMenuRoute.CATALOG_NG),
        CompatOtherMenuItem("watcher", "巡回検索"),
        CompatOtherMenuItem("cache", "過去スレ検索"),
        CompatOtherMenuItem("external", "外部アプリ"),
        CompatOtherMenuItem("display", "表示の切り替え"),
        CompatOtherMenuItem("top", "一番上に行く"),
        CompatOtherMenuItem("privacy", "プライバシー"),
        CompatOtherMenuItem("bypass", "通信の軽量化", deferredExternalBackend = true),
        CompatOtherMenuItem("check", "更新の確認"),
        CompatOtherMenuItem("undo", "閉じたスレを元に戻す", enabled = canUndoClose)
    )

    CompatOtherMenuRoute.CATALOG_NG -> listOf(
        CompatOtherMenuItem("ng_refuse", "NGスレッド"),
        CompatOtherMenuItem("ng_ignore", "NGワード"),
        CompatOtherMenuItem("ng_image", "NG画像"),
        CompatOtherMenuItem("ng_toggle", if (ngEnabled) "無効にする" else "有効にする"),
        CompatOtherMenuItem(
            "reply_priority_toggle",
            if (replyPriorityEnabled) "レス数優先を無効にする" else "レス数優先を有効にする"
        ),
        CompatOtherMenuItem(
            "non_priority_toggle",
            if (showNonPriority) "レス数非優先を隠す" else "レス数非優先を表示する"
        )
    )

    // sample/1.apk's catalog_extract_menu is a separate screen with its own
    // search/add/clear actions.  The Compose implementation reuses the same
    // rule editor, but keeps this route so the entry point and Back behavior
    // match the reference APK.
    CompatOtherMenuRoute.CATALOG_EXTRACT -> listOf(
        CompatOtherMenuItem("extract_search", "検索"),
        CompatOtherMenuItem("extract_add", "新規追加"),
        CompatOtherMenuItem("extract_clear", "全て削除")
    )

    else -> emptyList()
}.filterNot { item ->
    route == CompatOtherMenuRoute.CATALOG_ROOT && item.key in activeToolbarKeys
}

fun compatThreadOtherMenu(
    route: CompatOtherMenuRoute,
    ngEnabled: Boolean,
    canUndoClose: Boolean,
    ngCount: Int,
    cacheEnabled: Boolean? = null,
    activeToolbarKeys: Set<String> = emptySet()
): List<CompatOtherMenuItem> = when (route) {
    CompatOtherMenuRoute.THREAD_ROOT -> listOf(
        CompatOtherMenuItem("save", "ページを保存", CompatOtherMenuRoute.THREAD_SAVE),
        CompatOtherMenuItem("top", "ページ最上部へ"),
        CompatOtherMenuItem("page_up", "1ページ上へ"),
        CompatOtherMenuItem("page_down", "1ページ下へ"),
        CompatOtherMenuItem("bottom", "ページ最下部へ"),
        CompatOtherMenuItem("ng", "NG管理", CompatOtherMenuRoute.THREAD_NG),
        CompatOtherMenuItem("url", "URL", CompatOtherMenuRoute.THREAD_URL),
        CompatOtherMenuItem("read_aloud", "読み上げ"),
        CompatOtherMenuItem("autoscroll", "オートスクロール"),
        CompatOtherMenuItem("privacy", "プライバシー"),
        CompatOtherMenuItem("search", "レス検索"),
        CompatOtherMenuItem("extract", "レス抽出", CompatOtherMenuRoute.THREAD_EXTRACT),
        CompatOtherMenuItem("bypass", "通信の軽量化", deferredExternalBackend = true),
        CompatOtherMenuItem("cache", "過去ログ検索"),
        CompatOtherMenuItem("check", "更新の確認"),
        CompatOtherMenuItem("close", "スレを閉じる"),
        CompatOtherMenuItem("undo", "閉じたスレを元に戻す", enabled = canUndoClose)
    )

    CompatOtherMenuRoute.THREAD_SAVE -> listOf(
        CompatOtherMenuItem("save_html", "HTML"),
        CompatOtherMenuItem("save_thumb", "HTMLとサムネイル"),
        CompatOtherMenuItem("save_all", "HTMLと全ての画像"),
        CompatOtherMenuItem("save_images_zip", "メディアのみ(ZIP)"),
        CompatOtherMenuItem("save_images_folder", "メディアのみ(フォルダ)")
    )

    CompatOtherMenuRoute.THREAD_NG -> listOf(
        CompatOtherMenuItem("ng_refuse", "NGヘッダー"),
        CompatOtherMenuItem("ng_ignore", "NGワード"),
        CompatOtherMenuItem("ng_image", "NG画像"),
        CompatOtherMenuItem("ng_toggle", if (ngEnabled) "無効にする" else "有効にする")
    )

    CompatOtherMenuRoute.THREAD_URL -> listOf(
        CompatOtherMenuItem("url_browser", "ブラウザ"),
        CompatOtherMenuItem("url_copy", "コピー"),
        CompatOtherMenuItem("url_share", "共有"),
        CompatOtherMenuItem("url_ftbucket", "FTBucketに登録"),
        CompatOtherMenuItem("url_tsumamne", "つまんね。に登録"),
        CompatOtherMenuItem("url_forest", "ふたばフォレストで開く"),
        CompatOtherMenuItem("url_futapo", "ふたポで開く")
    )

    CompatOtherMenuRoute.THREAD_EXTRACT -> listOf(
        CompatOtherMenuItem("extract_own", "自分の書き込み"),
        CompatOtherMenuItem("extract_saidane", "そうだねが多い"),
        CompatOtherMenuItem("extract_replies", "返信が多い"),
        CompatOtherMenuItem("extract_deleted", "削除されたレス"),
        CompatOtherMenuItem("extract_url", "URLを含むレス"),
        CompatOtherMenuItem("extract_image", "画像レス"),
        CompatOtherMenuItem("extract_keyword", "キーワード"),
        CompatOtherMenuItem("extract_ng", "NG非表示のレス")
    )

    else -> emptyList()
}.filterNot { item ->
    route == CompatOtherMenuRoute.THREAD_ROOT && item.key in activeToolbarKeys
}

fun compatOtherMenuTitle(route: CompatOtherMenuRoute): String = when (route) {
    CompatOtherMenuRoute.CATALOG_ROOT,
    CompatOtherMenuRoute.THREAD_ROOT -> "その他"
    CompatOtherMenuRoute.CATALOG_NG,
    CompatOtherMenuRoute.THREAD_NG -> "NG管理"
    CompatOtherMenuRoute.CATALOG_EXTRACT -> "抽出"
    CompatOtherMenuRoute.THREAD_SAVE -> "ページを保存"
    CompatOtherMenuRoute.THREAD_URL -> "URL"
    CompatOtherMenuRoute.THREAD_EXTRACT -> "抽出"
}
