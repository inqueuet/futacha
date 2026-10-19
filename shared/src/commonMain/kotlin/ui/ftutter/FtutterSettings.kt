package com.valoser.futacha.shared.ui.ftutter

/**
 * ふたったー風モード専用の設定。保存先は CompatibilityStore の preferences で、キーは `compat.ftutter.<原作のキー>`。
 * 既定値と範囲は原作（ふたったー 3.1.1）の起動時の読み込みと設定画面のスライダーに合わせる
 * （docs/ftutter-mode-research-round6-2026-10-13.md §6）。壊れた値・範囲外の値は既定値に戻す。
 */
internal object FtutterKeys {
    const val PREFIX = "compat.ftutter."
    private fun k(name: String) = PREFIX + name

    // デザイン
    val MAIN_COLOR = k("mainColor")
    val THEME_MODE = k("themeMode")
    val CATALOG_FONT_SIZE = k("catalogFontSize")
    val CATALOG_NUMBER_FONT_SIZE = k("catalogSuujiFontSize")
    val RESU_HEADER_FONT_SIZE = k("resuHeaderFontSize")
    val RESU_CONTENT_FONT_SIZE = k("resuContentFontSize")
    val PRIVACY_CATALOG = k("privacyCatalog")
    val PRIVACY_CATALOG_ZOOM = k("privacyKakudaiCatalog")
    val PRIVACY_THREAD = k("privacySure")
    val PRIVACY_THREAD_ZOOM = k("privacyKakudaiSure")
    val PRIVACY_TAB_DRAWER = k("privacyTabDrawerETC")
    val DISABLE_STRETCH_ANIMATION = k("disableStretchAnimation")
    fun colorOverride(name: String) = k("color.$name")

    // カタログ
    val CATALOG_ROW = k("catalogRow")
    val CATALOG_ROW_LANDSCAPE = k("catalogRowLandscape")
    val MAX_CATALOG_THREAD_COUNT = k("maxCatalogThreadCount")
    val SAME_HEIGHT_GRID = k("useSameHeightGridView")
    val DEFAULT_ORDER_MODE = k("defaultOrderMode")
    val PREVIOUS_ORDER_MODE = k("previousOrderMode")
    val CATALOG_HIGH_RES = k("catalogHighRes")
    val MAX_THUMB_HEIGHT = k("maxThumbHeight")
    val MIN_THUMB_HEIGHT = k("minThumbHeight")
    val USE_ZOOM = k("useKakudai")
    val NUMBER_ON_THUMB = k("numOnThumb")
    val KITA_SHORTEN = k("kitaSyouryaku")
    val MAX_TITLE_LINES = k("maxThreadTitleLine")
    val LIMIT_TITLE_LETTERS = k("limitMaxThreadTitleLetter")
    val MAX_TITLE_LETTERS = k("maxThreadTitleLetter")
    val CATALOG_SCROLLBAR = k("useCatalogScrollbar")
    val CATALOG_SCROLLBAR_THICKNESS = k("catalogScrollbarThickness")
    val CATALOG_SCROLLBAR_ALWAYS = k("alwaysShowCatalogScrollbar")
    val CATALOG_SCROLLBAR_LEFT = k("putCatalogScrollbarLeft")
    val CATALOG_PULL_TOP = k("catPullToRefreshTop")
    val CATALOG_PULL_BOTTOM = k("catPullToRefreshBottom")
    val CATALOG_TOOLBAR_ORDER = k("catTBOrderName")
    val CATALOG_TOOLBAR_ENABLED = k("catTBOrderBool")
    val CATALOG_ICON_WIDTH = k("catIconWidth")
    val CATALOG_ICON_HEIGHT = k("catIconHeight")
    val CATALOG_TOOLBAR_LEFT = k("catTBLeftSpace")
    val CATALOG_TOOLBAR_RIGHT = k("catTBRightSpace")

    // スレッド
    val SHOW_DELETED = k("resuDeletedHyouji")
    val DELETED_CHAIN = k("resuDeletedChain")
    val QUOTE_SHORTEN = k("innyouSyouryaku")
    val PADDING_TOP = k("resuPaddingTop")
    val PADDING_LEFT = k("resuPaddingLeft")
    val PADDING_RIGHT = k("resuPaddingRight")
    val PADDING_BOTTOM = k("resuPaddingBottom")
    val PADDING_HEADER_TEXT = k("resuPaddingHeaderAndText")
    val PADDING_TEXT_FILE = k("resuPaddingTextAndFile")
    val OP_IMAGE_HIGH_RES = k("itiResuImgHighRes")
    val OP_IMAGE_MAX_HEIGHT = k("itiResuImgMaxHeight")
    val RES_IMAGE_HIGH_RES = k("resuImgHighRes")
    val RES_IMAGE_POSITION = k("resuImgIti")
    val RES_IMAGE_MAX_HEIGHT = k("resuImgMaxHeight")
    val RES_IMAGE_MAX_WIDTH = k("resuImgMaxWidth")
    val RES_IMAGE_FIXED_WIDTH = k("resuImgWidthKotei")
    val APU_AUTO_LOAD = k("apusyouAutoLoad")
    val APU_THUMB_SIZE = k("apusyouThumbSize")
    val THREAD_SCROLLBAR = k("useScrollbar")
    val THREAD_SCROLLBAR_THICKNESS = k("scrollbarThickness")
    val THREAD_SCROLLBAR_ALWAYS = k("alwaysShowScrollbar")
    val THREAD_SCROLLBAR_LEFT = k("putScrollbarLeft")
    val THREAD_PULL_TOP = k("surePullToRefreshTop")
    val THREAD_PULL_BOTTOM = k("surePullToRefreshBottom")
    val SHOW_NEW_COUNT = k("sintyakuCountHyouji")
    val SHOW_REPLY_COUNT = k("henshinCountHyouji")
    val POPULAR_REPLIES = k("ninkiHensin")
    val POPULAR_SOUDANE = k("ninkiSoudane")
    val HISTORY_LIMIT = k("rirekiLimit")
    val THREAD_TOOLBAR_ORDER = k("sureTBOrderName")
    val THREAD_TOOLBAR_ENABLED = k("sureTBOrderBool")
    val THREAD_ICON_WIDTH = k("sureIconWidth")
    val THREAD_ICON_HEIGHT = k("sureIconHeight")
    val THREAD_TOOLBAR_LEFT = k("sureTBLeftSpace")
    val THREAD_TOOLBAR_RIGHT = k("sureTBRightSpace")
    val POST_MENU_ORDER = k("resuDOrderName")
    val POST_MENU_ENABLED = k("resuDOrderBool")

    // タブ・ドロワー
    val TAB_ON_TOP = k("tabIsUp")
    val TAB_HEIGHT = k("tabHeight")
    val TAB_WIDTH = k("tabWidth")
    val MOVE_TAB_BY_SWIPE = k("moveTabBySwipe")
    val REFRESH_ON_TAB_MOVE_CATALOG = k("refreshTabByMoveInCatalog")
    val REFRESH_ON_TAB_MOVE_THREAD = k("refreshTabByMoveInSure")
    val DISABLE_TAB_DRAG = k("disableDragTab")
    val BACK_WHEN_NO_TAB = k("backWhenNoTab")
    val DRAWER_SIDE = k("drawerLeftOrRight")
    val DRAWER_OPEN_BY_DRAG = k("drawerOpenByDrag")
    val TAB_DISMISS_DIRECTION = k("drawerTabDismissDirection")
    val DRAWER_BUTTON_TOP_LEFT = k("drawerButtonOnTopLeft")
    val DRAWER_CONTENT = k("drawerContent")
    val SHOW_TAB = k("showTab")
    val IS_TREE = k("isTree")
    val IS_PRIVACY = k("isPrivacyMode")

    // 画像・動画
    val CONNECT_ALL_IMAGES = k("connectAllImgInThread")
    val EXCLUDE_DELETED_IMAGES = k("excludeDeletedImg")
    val VIDEO_AUTO_PLAY = k("videoAutoPlay")
    val VIDEO_LOOP = k("videoDefaultLoop")
    val GALLERY_TAB_SIZE = k("galleryTabSize")
    val USE_MEM_SIZE = k("useMemSize")
    val MEM_SIZE = k("memSize")
    val SHOW_GALLERY_TAB = k("showGalleryTab")
    val GALLERY_COLUMNS = k("gazouItiranRow")
    val SAVE_HTML_THUMB = k("saveHtmlWithThumb")
    val SAVE_HTML_FILE = k("saveHtmlWithFile")
    val SAVE_HTML_APU = k("saveHtmlWithApusyou")
    val SAVE_HTML_ZIP = k("saveHtmlZip")

    // ピックアップ・NG
    val NG_LIST = k("NGList")
    val NG_CHAIN = k("nGChain")
    val PICKUP_ORDER_MODES = k("applyPickupOrderMode")
    val HIDDEN_THREADS = k("hihyouji")

    // ネットワーク・書き込み
    val CONFIRM_BEFORE_POST = k("confirmBeforeKakikomi")

    // ジェスチャー
    val USE_GESTURE = k("useGesture")
    val GESTURE_SENSITIVITY = k("kando")
    val USE_HTTPS = k("useHttps")
    val USE_CUSTOM_TAB = k("useChromeCustomTab")
    val IMAGE_SEARCH_ASCII2D = k("nijigenGazouSyousaiKensaku")
    val ALBUM_NAME = k("albumName")
    val DISABLE_ALBUM_NAME = k("disableAlbumName")
    val BACK_BY_PREVIOUS_CATALOG = k("backByPreviousInCatalog")
    val BACK_BY_PREVIOUS_THREAD = k("backByPreviousInSure")
    fun gesture(code: String) = k("Gesture$code")

    // 二画面
    val USE_TABLET = k("useTabletMode")
    val TABLET_MIN_WIDTH = k("isTabletModeWhenWiderThan")
    val TABLET_ROW_PORTRAIT = k("catalogRowTabletPortrait")
    val TABLET_ROW_LANDSCAPE = k("catalogRowTabletLandscape")
    val THREAD_ON_LEFT = k("sureIsLeft")
    val DIVIDE_PERCENTAGE = k("dividePercentage")

    // モード専用の状態
    val TABS = k("tabs")
    /** 保護したスレッドのURL（原作はスレッドのデータ側の印`protected`）。 */
    val PROTECTED = k("protected")
    /** 下書き（返信はスレッド、スレ立ては板ごと。原作は閉じる時だけ保存）。 */
    val DRAFTS = k("drafts")
    /** 削除キー（原作は初回起動で英数字8文字を作る）。他モードの削除キーとは別。 */
    val DELETE_KEY = k("deleteKey")
    /** 統計（原作は板ごとの4つの回数の合計。端末の中だけ）。 */
    val STATS_OPENED = k("stats.opened")
    val STATS_CREATED = k("stats.created")
    val STATS_REPLIED = k("stats.replied")
    val STATS_SOUDANE = k("stats.soudane")
    val OPENING = k("opening")
    val LAST_BOARD = k("lastBoard")
    /** スレごとの「ここまで読んだ」の件数（[parseFtutterReadCounts]）。 */
    val READ_COUNTS = k("seen")
}

internal fun Map<String, String>.ftutterInt(key: String, default: Int, range: IntRange): Int =
    this[key]?.trim()?.toIntOrNull()?.takeIf { it in range } ?: default

/** 統計の数を1つ増やす。壊れた値は0から数え直し、上限で止める（監査M2：保存されている値に足す）。 */
internal fun ftutterIncrementedCount(stored: String?): String {
    val current = stored?.trim()?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
    return (if (current == Int.MAX_VALUE) current else current + 1).toString()
}

internal fun Map<String, String>.ftutterDouble(key: String, default: Double, range: ClosedFloatingPointRange<Double>): Double =
    this[key]?.trim()?.toDoubleOrNull()?.takeIf { !it.isNaN() && it in range } ?: default

internal fun Map<String, String>.ftutterBool(key: String, default: Boolean): Boolean =
    when (this[key]?.trim()?.lowercase()) {
        "true", "on", "1" -> true
        "false", "off", "0" -> false
        else -> default
    }

/** ARGB の保存値（10進または `#AARRGGBB`／`0x…`）。読めない値は null。 */
internal fun parseFtutterColor(raw: String?): Long? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val value = when {
        text.startsWith("#") -> text.drop(1).toLongOrNull(16)?.let { if (text.length == 7) it or 0xFF000000L else it }
        text.startsWith("0x", ignoreCase = true) -> text.drop(2).toLongOrNull(16)
        else -> text.toLongOrNull()
    } ?: return null
    return value and 0xFFFFFFFFL
}

/** デザインの設定。 */
internal data class FtutterDesignSettings(
    val mainColor: Long = FTUTTER_DEFAULT_MAIN_COLOR,
    val brightness: FtutterBrightness = FtutterBrightness.LIGHT,
    val catalogFontSize: Int = 15,
    val catalogNumberFontSize: Int = 14,
    val resuHeaderFontSize: Int = 13,
    val resuContentFontSize: Int = 15,
    val privacyCatalog: Double = 0.8,
    val privacyCatalogZoom: Double = 0.8,
    val privacyThread: Double = 0.8,
    val privacyThreadZoom: Double = 0.8,
    val privacyTabDrawer: Double = 0.8,
    val disableStretchAnimation: Boolean = true,
    val overrides: FtutterColorOverrides = FtutterColorOverrides()
)

private val FTUTTER_FONT_RANGE = 1..40
private val FTUTTER_OPACITY_RANGE = 0.0..1.0

internal fun parseFtutterDesign(preferences: Map<String, String>): FtutterDesignSettings {
    val p = preferences
    val defaults = FtutterColorOverrides()
    fun color(name: String) = parseFtutterColor(p[FtutterKeys.colorOverride(name)])
    val mainColor = parseFtutterColor(p[FtutterKeys.MAIN_COLOR])?.takeIf { it in FTUTTER_SEED_COLORS } ?: FTUTTER_DEFAULT_MAIN_COLOR
    return FtutterDesignSettings(
        mainColor = mainColor,
        brightness = FtutterBrightness.fromStored(p[FtutterKeys.THEME_MODE]?.trim()?.toIntOrNull()),
        catalogFontSize = p.ftutterInt(FtutterKeys.CATALOG_FONT_SIZE, 15, FTUTTER_FONT_RANGE),
        catalogNumberFontSize = p.ftutterInt(FtutterKeys.CATALOG_NUMBER_FONT_SIZE, 14, FTUTTER_FONT_RANGE),
        resuHeaderFontSize = p.ftutterInt(FtutterKeys.RESU_HEADER_FONT_SIZE, 13, FTUTTER_FONT_RANGE),
        resuContentFontSize = p.ftutterInt(FtutterKeys.RESU_CONTENT_FONT_SIZE, 15, FTUTTER_FONT_RANGE),
        privacyCatalog = p.ftutterDouble(FtutterKeys.PRIVACY_CATALOG, 0.8, FTUTTER_OPACITY_RANGE),
        privacyCatalogZoom = p.ftutterDouble(FtutterKeys.PRIVACY_CATALOG_ZOOM, 0.8, FTUTTER_OPACITY_RANGE),
        privacyThread = p.ftutterDouble(FtutterKeys.PRIVACY_THREAD, 0.8, FTUTTER_OPACITY_RANGE),
        privacyThreadZoom = p.ftutterDouble(FtutterKeys.PRIVACY_THREAD_ZOOM, 0.8, FTUTTER_OPACITY_RANGE),
        privacyTabDrawer = p.ftutterDouble(FtutterKeys.PRIVACY_TAB_DRAWER, 0.8, FTUTTER_OPACITY_RANGE),
        disableStretchAnimation = p.ftutterBool(FtutterKeys.DISABLE_STRETCH_ANIMATION, true),
        overrides = FtutterColorOverrides(
            background = color("background"), text = color("text"), toolBar = color("toolBar"),
            onToolBar = color("onToolBar"), onToolBarActive = color("onToolBarActive"), popUp = color("popUp"),
            highlight = color("highlight"), dividerCard = color("dividerCard"), onDividerCard = color("onDividerCard"),
            inTab = color("inTab"), gesture = color("gesture"), scrollBar = color("scrollBar"), indicator = color("indicator"), outline = color("outline"), kidoku = color("kidoku"), midoku = color("midoku"),
            onMidoku = color("onMidoku"), headerText = color("headerText"),
            resuSuu = color("resuSuu") ?: defaults.resuSuu, onResuSuu = color("onResuSuu") ?: defaults.onResuSuu,
            pickUp = color("pickUp") ?: defaults.pickUp, onPickUp = color("onPickUp") ?: defaults.onPickUp,
            kakikomi = color("kakikomi") ?: defaults.kakikomi, henshin = color("henshin") ?: defaults.henshin
        )
    )
}


/** 統計の4つの回数。 */
internal data class FtutterStats(val opened: Int = 0, val created: Int = 0, val replied: Int = 0, val soudane: Int = 0)

internal fun parseFtutterStats(p: Map<String, String>) = FtutterStats(
    opened = p.ftutterInt(FtutterKeys.STATS_OPENED, 0, 0..Int.MAX_VALUE),
    created = p.ftutterInt(FtutterKeys.STATS_CREATED, 0, 0..Int.MAX_VALUE),
    replied = p.ftutterInt(FtutterKeys.STATS_REPLIED, 0, 0..Int.MAX_VALUE),
    soudane = p.ftutterInt(FtutterKeys.STATS_SOUDANE, 0, 0..Int.MAX_VALUE)
)
