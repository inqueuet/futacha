package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue

/** Preference keys of the settings screen (all under the mode's own `compat.futaber.` prefix). */
internal object FutaberSettingKeys {
    const val FONT_SIZE = "compat.futaber.fontSize"
    const val MANY_REPLIES = "compat.futaber.manyReplies"
    /**
     * C21 (L-B9): 「画像のサイズ比率を指定」 is chosen (futaber's 画像表示サイズ). Before L-B9 the same key was 「小さく表示」,
     * which is read as the ratio [FUTABER_IMAGE_RATIO_LEGACY_SMALL] while no ratio is stored.
     */
    const val SMALL_IMAGES = "compat.futaber.smallImages"
    /** C21 (L-B9): futaber's 「比率 = N%」 (10〜100, 70 at first). */
    const val IMAGE_RATIO = "compat.futaber.imageRatio"
    const val CATALOG_SCROLL_TOP = "compat.futaber.catalog.scrollTop"
    const val CATALOG_ROW_WIDE = "compat.futaber.catalog.rowWide"
    /** Shared with ふたちゃ and としあき: [com.valoser.futacha.shared.compat.COMPAT_WATCH_ENABLED_KEY]. */
    const val PATROL_ENABLED = "compat.watcher.enabled"
    const val VIEWER_SWIPE_CLOSE = "compat.futaber.viewerSwipeClose"
    /** L-B11 (C27・F15): which control of the viewer saves to a storage provider (none / save / action / long press). */
    const val VIEWER_STORAGE_BUTTON = "compat.futaber.viewerStorageButton"
    /** L-B11 (C28): the picture list and the viewer stay upright while they are up (off at first). */
    const val VIEWER_PORTRAIT_LOCK = "compat.futaber.viewerPortraitLock"
    /** L-B11 (C29): a video begins to play when the viewer opens it (off at first: it opens stopped). */
    const val VIEWER_AUTOPLAY = "compat.futaber.viewerAutoplay"
    /** L-B12 (C05): the browser's URL scheme (「[url]」) and the URL scheme action's (「[text]」); empty at first. */
    /** L-B12 (C24): the voice pitch of the quoted lines, in tenths (5〜20; 5 = 0.5 at first, as in futaber). */
    const val QUOTE_PITCH = "compat.futaber.quotePitch"
    const val URL_SCHEME_BROWSER = "compat.futaber.urlSchemeBrowser"
    const val URL_SCHEME_ACTION = "compat.futaber.urlSchemeAction"
    const val TITLE_NAME_ALWAYS = "compat.futaber.titleNameAlways"
    /**
     * "拡張機能": things the original app does not have but ふたちゃ and としあき(仮) do. Each is off by default,
     * so the screens stay as the original app has them until a person turns one on in the settings.
     */
    const val EXT_TREE = "compat.futaber.ext.tree"
    const val EXT_EXTRACT = "compat.futaber.ext.extract"
    const val EXT_ID_TAP = "compat.futaber.ext.idTap"
    const val EXT_QUICK_SAIDANE = "compat.futaber.ext.quickSaidane"
    const val EXT_QUICK_NG = "compat.futaber.ext.quickNg"
    const val EXT_AUTO_SCROLL = "compat.futaber.ext.autoScroll"
    const val EXT_HISTORY = "compat.futaber.ext.history"
    const val EXT_MAIL_PRESETS = "compat.futaber.ext.mailPresets"
    const val EXT_VOLUME_KEYS = "compat.futaber.ext.volumeKeys"
    const val EXT_TABS = "compat.futaber.ext.tabs"
    const val EXT_VIDEO = "compat.futaber.ext.video"
    /** F-B3 E12 / E14 (off by default, as in ふたったー風): the replies to a post NG hides go too; open a thread at its top. */
    const val EXT_NG_CHAIN = "compat.futaber.ext.ngChain"
    const val EXT_OPEN_AT_TOP = "compat.futaber.ext.openAtTop"
    /** E7: the count of そうだね from which a post counts as many (shared with the other modes: 「そうだねが多いレス」). */
    val SAIDANE_THRESHOLD = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("thread", "threadExtractSoudaneNum")
    /** F-B2: this app's extra items sit behind one 「その他の機能」 (default); OFF lists them after the original's items as before. */
    const val FOLD_EXTRAS = "compat.futaber.foldExtras"
    const val EXT_TABS_MANAGE = "compat.futaber.ext.tabsManage"
    /** F-B1: 「全タブを確認」, split off [EXT_TABS_MANAGE] (which is now only 「お気に入りのタブを残す」); on unless turned off. */
    const val EXT_CATALOG_VOLUME_KEY = "compat.futaber.ext.catalogVolumeKey"
    const val EXT_OPEN_URL = "compat.futaber.ext.openUrl"
    const val THREAD_FAST_SCROLL = "compat.thread.threadFastScroll"
    const val CATALOG_FAST_SCROLL = "compat.catalog.catalogFastScroll"
    const val BOARD_PIN = "compat.futaber.ext.boardPin"
    const val EXT_TABS_CHECK = "compat.futaber.ext.tabsCheck"
    const val EXT_TABS_CLOSE_FALLEN = "compat.futaber.ext.tabsCloseFallen"
    const val EXT_OPEN_SUMMARY = "compat.futaber.ext.openSummary"
    const val EXT_TAB_STATUS = "compat.futaber.ext.tabStatus"
    const val EXT_REPORT_NG = "compat.futaber.ext.reportNg"
    const val EXT_ARCHIVE_MENU = "compat.futaber.ext.archiveMenu"
    const val EXT_SAVED_DETAIL = "compat.futaber.ext.savedDetail"
    const val EXT_OTHER_FAVORITES = "compat.futaber.ext.otherFavorites"
    const val EXT_SCROLL_MENU = "compat.futaber.ext.scrollMenu"
    const val EXT_SEARCH_NAV = "compat.futaber.ext.searchNav"
    /** What the volume keys do while [EXT_VOLUME_KEYS] is on: "screen" (the default, as before), "response" or "thread". */
    const val EXT_VOLUME_KEY_MODE = "compat.futaber.ext.volumeKeyMode"
    const val EXT_SORT_BY_BOARD = "compat.futaber.ext.sortByBoard"
    const val EXT_CATALOG_LONG_PRESS = "compat.futaber.ext.catalogLongPress"
    const val EXT_POST_DETAIL_OPS = "compat.futaber.ext.postDetailOps"
    const val EXT_GALLERY_COLUMNS = "compat.futaber.ext.galleryColumns"
    const val EXT_IMAGE_MENU = "compat.futaber.ext.imageMenu"
    /** The write screen's tools (a long press on the attach button) and the Cookie guidance after a failed post. */
    const val EXT_POST_TOOLS = "compat.futaber.ext.postTools"
    /** The draft also keeps its attachment (a private file; the draft notes where). */
    const val EXT_DRAFT_ATTACHMENT = "compat.futaber.ext.draftAttachment"
    /** The history band gets "アーカイブ" (the history archive ふたちゃ writes and reads). */
    const val EXT_HISTORY_ARCHIVE = "compat.futaber.ext.historyArchive"
    /** The patrol and the tab checks of the other modes also run while this mode is on screen. */
    const val EXT_FOREGROUND_PATROL = "compat.futaber.ext.foregroundPatrol"
    /** Android: the patrol manager offers the external にじろぐ's results, listed under "通知". */
    const val EXT_EXTERNAL_WATCHER = "compat.futaber.ext.externalWatcher"
    /** The save dialog also offers "メディアのみ（ZIP）／（フォルダ）" (ふたちゃ's media-only save). */
    const val EXT_MEDIA_ONLY_SAVE = "compat.futaber.ext.mediaOnlySave"
    /** The letters take the custom font every mode shares (compat.design.dummyCustomFont). */
    const val EXT_CUSTOM_FONT = "compat.futaber.ext.customFont"
    /** B7a: the sort menu also offers "そ順" and "監視" (the words of the patrol and the catalog extraction rules). */
    const val EXT_CATALOG_MODES = "compat.futaber.ext.catalogModes"
    /** B7a: catalog threads whose title holds a watch word get a badge and come first. */
    const val EXT_CATALOG_WATCH_HIGHLIGHT = "compat.futaber.ext.catalogWatchHighlight"
    /** B7a: the shared "レス数による優先表示" (threads with few replies later / hidden), per board as in the other modes. */
    const val EXT_REPLY_PRIORITY = "compat.futaber.ext.replyPriority"
    /** B7a: the refresh button's sheet offers each catalog kept before the last reloads (1〜4回前). */
    const val EXT_CATALOG_HISTORY = "compat.futaber.ext.catalogHistory"
    /** B7a: the cache server search and opening the catalog in the browser. */
    const val EXT_CATALOG_TOOLS = "compat.futaber.ext.catalogTools"
    /** B7b: "NGの詳細管理" (the shared rule lists of the other modes) in the thread menu and the catalog's refresh sheet. */
    const val EXT_NG_DETAIL = "compat.futaber.ext.ngDetail"
    /** B7b: the picture list is the shared one with the selection save (ZIP / folder), its long-press menu and retries. */
    const val EXT_GALLERY_SELECT_SAVE = "compat.futaber.ext.gallerySelectSave"
    /** B7b: tapping "読み上げ中" opens a small panel with the position slider and pause / resume. */
    const val EXT_READ_ALOUD_PANEL = "compat.futaber.ext.readAloudPanel"
    /** B9 (C06): 設定→カタログ gets the grid's column counts and title lines / letters (FutaberParityB9.kt). */
    const val EXT_CATALOG_FINE_TUNE = "compat.futaber.ext.catalogFineTune"
    /** B9: a live thread on screen is auto-saved as ふたちゃ does (pictures and videos are kept on the device). */
    const val EXT_AUTO_SAVE_VIEW = "compat.futaber.ext.autoSaveView"
    /**
     * Shared with ふたちゃ and としあき(仮) ("閲覧スレ通知"): a stored "OFF" stops it, anything else (nothing stored) is on.
     * [com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY].
     */
    const val ARCHIVE_REPORT = com.valoser.futacha.shared.compat.ARCHIVE_REPORT_ENABLED_PREFERENCE_KEY
    /** The thread scrolls a page when its upper or lower third is tapped (設定→ジェスチャー; off by default). */
    const val TAP_SCROLL = "compat.futaber.tapScroll"
    /**
     * L-B5 (C26): 設定→スレッド「その他」の「GoogleLens検索を表示」. On, a picture's long-press sheet offers Google Lens
     * after the original's six items. Off by default, as in the original.
     */
    const val GOOGLE_LENS = "compat.futaber.googleLens"
    /** The gap above and below a post, in dp (this app's own; futaber has none). */
    const val POST_GAP = "compat.futaber.postGap"
    /** The header on one line, cut with 「…」 where it does not fit (off by default). */
    const val HEADER_ONE_LINE = "compat.futaber.headerOneLine"
    /**
     * L-B5 (M23, plan §4.1 item 3): the post sheet's 「コピー」 copies the post's text at once, as before, instead of
     * opening the original's copy panel. Off by default (the original's panel); 設定→スレッドの末尾.
     */
    const val INSTANT_COPY = "compat.futaber.instantCopy"
    /**
     * L-B6 (M08, plan §4.1 item 3): the operation menu's search word narrows the thread as before (with the chip and,
     * with the extension, ▲▼), instead of the original's list and bubble. Off by default; 設定→スレッドの末尾.
     */
    const val LEGACY_SEARCH_FILTER = "compat.futaber.legacySearchFilter"
    /**
     * L-B6 (M11, plan §4.1 item 3): 「返信が多いレスを表示」 narrows the thread as before, instead of the original's
     * bubble. Off by default; 設定→スレッドの末尾.
     */
    const val LEGACY_MANY_REPLIES_FILTER = "compat.futaber.legacyManyRepliesFilter"
    /**
     * L-B8 (F17, plan §4.1 item 3): a new thread's writing screen shows the subject field as before. Off by default (the
     * original has none: a new thread is its body only); 設定→スレッドの末尾「従来の動き」.
     */
    const val LEGACY_SUBJECT_FIELD = "compat.futaber.legacySubjectField"
    /**
     * Shared with としあき(仮) and ふたちゃ ("板名の誤投稿確認"): one value for every mode. Read with the legacy title too
     * ([com.valoser.futacha.shared.ui.compat.compatPreferenceValue]).
     */
    val POST_DESTINATION_CONFIRM = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("control", "controlPostDestinationConfirm")
    /** Shared with としあき(仮) and ふたちゃ (スレッド→オートスクロール量／速度), so the amount and speed are the same everywhere. */
    val AUTO_SCROLL_PIXEL = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("thread", "autoScrollPixel")
    val AUTO_SCROLL_SPEED = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("thread", "autoScrollSpeed")
    /** Shared with としあき(仮) and ふたちゃ (カタログ→レス数で優先ソート): the reply count below which a thread is "few" (0〜30). */
    val REPLY_PRIORITY_THRESHOLD = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("catalog", "delayFewReplies")
}

internal const val FUTABER_AUTO_SCROLL_PIXEL_DEFAULT = 5
internal const val FUTABER_AUTO_SCROLL_PIXEL_MIN = 1
internal const val FUTABER_AUTO_SCROLL_PIXEL_MAX = 30
internal const val FUTABER_AUTO_SCROLL_SPEED_DEFAULT = 50
internal const val FUTABER_AUTO_SCROLL_SPEED_MIN = 10
internal const val FUTABER_AUTO_SCROLL_SPEED_MAX = 200

internal const val FUTABER_FONT_SIZE_DEFAULT = 14
internal const val FUTABER_FONT_SIZE_MIN = 10
internal const val FUTABER_FONT_SIZE_MAX = 24
internal const val FUTABER_SAIDANE_THRESHOLD_MIN = 1
internal const val FUTABER_SAIDANE_THRESHOLD_MAX = 10
internal const val FUTABER_POST_GAP_MIN = 0
internal const val FUTABER_POST_GAP_MAX = 20
internal const val FUTABER_POST_GAP_DEFAULT = 10
internal const val FUTABER_MANY_REPLIES_MIN = 2
internal const val FUTABER_MANY_REPLIES_MAX = 30

/**
 * What the settings screen changes in the screens. [fontSize] is the body text size in sp; the
 * header line and the title line keep their proportion to it (12 and 13 at the default 14).
 */
internal data class FutaberDisplaySettings(
    val fontSize: Int = FUTABER_FONT_SIZE_DEFAULT,
    val manyRepliesThreshold: Int = FUTABER_MANY_REPLIES_THRESHOLD,
    /**
     * C21: futaber's 「画像のサイズ比率を指定」 (false: 「オリジナルサイズで表示」). The pictures are drawn at [imageRatioPercent]
     * of the usual size; the operation menu's 「画像を小さくする」 still shrinks them per thread.
     */
    val smallImages: Boolean = false,
    /** C21: futaber's 「比率 = N%」 (only read with [smallImages]). */
    val imageRatioPercent: Int = FUTABER_IMAGE_RATIO_DEFAULT,
    /** C23: the shared read-aloud pitch and rate (compat.speech.pitch / rate) in whole tenths (10 = normal). */
    val speechPitchTenths: Int = 10,
    val quotePitchTenths: Int = FUTABER_QUOTE_PITCH_DEFAULT_TENTHS,
    val speechRateTenths: Int = 10,
    val scrollCatalogToTopOnRefresh: Boolean = true,
    /** The list style's "やや広め" rows: taller thumbnails and room for two title lines. */
    val catalogRowWide: Boolean = false,
    /** Show a post's subject and name even when they are the board defaults. */
    val alwaysShowTitleAndName: Boolean = false,
    /** The image viewer closes on a downward swipe (off by default, as in the original). */
    val viewerSwipeClose: Boolean = false,
    /** C27・F15: the viewer's control that saves to a storage provider ([FutaberStorageButton.None] keeps the plain save and share). */
    val viewerStorageButton: FutaberStorageButton = FutaberStorageButton.None,
    /** C28: the picture list and the viewer are held upright while they are up. */
    val viewerPortraitLock: Boolean = false,
    /** C29: a video starts playing when the viewer opens it. */
    val viewerAutoplay: Boolean = false,
    val urlSchemes: FutaberUrlSchemes = FutaberUrlSchemes(),
    val wallpapers: FutaberWallpapers = FutaberWallpapers(),
    /** The keyword patrol shared with the other modes (their own "OFF" turns it off everywhere). */
    val patrolEnabled: Boolean = true,
    /** Replies are listed under the post they quote, indented by depth (the tree view of the other modes). */
    val extTree: Boolean = false,
    /** The operation menu gets "レスを抽出" (own posts, many "そうだね", deleted, URL, image). */
    val extExtract: Boolean = true,
    /** Tapping a poster's ID lists the posts with the same ID. */
    val extIdTap: Boolean = true,
    /** The operation menu gets "オートスクロール". */
    val extAutoScroll: Boolean = true,
    val extQuickSaidane: Boolean = false,
    val extQuickNg: Boolean = false,
    /** The history list of the manage panel gets a search / sort / filter band and "一括更新". */
    val extHistory: Boolean = true,
    /** The write settings' mail field gets "ID表示" / "IP表示" / "sage" buttons. */
    val extMailPresets: Boolean = true,
    /** The volume keys scroll the thread by a screen (Android; the keys are not delivered on iOS). */
    val extVolumeKeys: Boolean = false,
    /** The tab long-press sheet gets "閉じたタブを元に戻す". */
    val extTabs: Boolean = true,
    /**
     * A video page of the picture viewer is the video viewer (play / pause / stop, volume) instead of only a button that
     * opens the video elsewhere. On from the start; a person can turn it off.
     */
    val extVideo: Boolean = true,
    val extNgChain: Boolean = false,
    val extOpenAtTop: Boolean = false,
    val saidaneThreshold: Int = FUTABER_MANY_SAIDANE_THRESHOLD,
    val foldExtras: Boolean = true,
    /** Closing several tabs at once (the tab sheet, "すべて削除") keeps the tabs of starred threads. */
    val extTabsManage: Boolean = false,
    val extTabsCheck: Boolean = true,
    /** F-B4 E1: the star-like pin on the board list's edit rows. */
    val boardPin: Boolean = true,
    /** F-B5 E5: the shared fast scroll bars (the same values the other modes read). */
    val threadFastScroll: Boolean = false,
    val catalogFastScroll: Boolean = false,
    /** F-B4 E13: the refresh sheet's "URLから開く". */
    val extOpenUrl: Boolean = true,
    /** F-B4 E6: the volume keys page the catalog (Android). */
    val extCatalogVolumeKey: Boolean = false,
    /** F-B4 E2: the tab sheet's "落ちたタブを確認して閉じる". */
    val extTabsCloseFallen: Boolean = true,
    /** The operation menu gets "URL・アーカイブ" (FTBucket, つまんね。, ふたばフォレスト, ふたポ). */
    val extArchiveMenu: Boolean = true,
    /** A long press on a row of the saved box shows how complete the save is. */
    val extSavedDetail: Boolean = true,
    /** The favourites of the manage panel also list the favourites of the other modes (read only). */
    val extOtherFavorites: Boolean = true,
    /** The operation menu gets "1ページ上へ" / "1ページ下へ" / "先頭へ". */
    val extScrollMenu: Boolean = true,
    /** While a search narrows the thread, the chip moves between the found posts (▲ n/N ▼). */
    val extSearchNav: Boolean = true,
    /** What the volume keys do (with [extVolumeKeys]): a screen (as before), a post, or the next / previous tab's thread. */
    val volumeKeyMode: FutaberVolumeKeyMode = FutaberVolumeKeyMode.Screen,
    /** The catalog's sort order is remembered per board (a board without its own record uses the one shared value). */
    val extSortByBoard: Boolean = false,
    /** The catalog's long-press sheet gets "画像NGに登録". */
    val extCatalogLongPress: Boolean = true,
    /**
     * "レスの詳細操作": deleting an own post can delete only its picture ("画像だけ消す"), and the long-press sheet gets
     * "項目を選んでコピー" / "語句を選んでGoogle検索" / "クイック引用返信" and "関連レス" / "同じID" / "同じIP" (B7b).
     */
    val extPostDetailOps: Boolean = true,
    /** The picture list takes the column counts of the shared viewer settings (portrait / landscape) instead of 3. */
    val extGalleryColumns: Boolean = true,
    /** The long-press menu of a post's picture gets save, share, copy URL, open elsewhere and open in the viewer. */
    val extImageMenu: Boolean = true,
    /** A long press on the write screen's attach button opens the tools (drawing, あぷ小, voice, network / device lines). */
    val extPostTools: Boolean = true,
    /** The draft keeps its attachment as well as its text. */
    val extDraftAttachment: Boolean = true,
    /** The history band's "アーカイブ" (export / import of the history archive). */
    val extHistoryArchive: Boolean = true,
    /** The shared patrol / tab checks run every minute while this mode is in the foreground. */
    val extForegroundPatrol: Boolean = true,
    /** Android: the external にじろぐ can be chosen in the patrol manager, and its results are listed under "通知". */
    val extExternalWatcher: Boolean = true,
    /** The save dialog's media-only rows (ZIP / folder). */
    val extMediaOnlySave: Boolean = true,
    /** The shared custom font is applied to this mode's letters. */
    val extCustomFont: Boolean = true,
    /** The shared "閲覧スレ通知": on unless it was turned off (in any mode). */
    val archiveReportEnabled: Boolean = true,
    /**
     * Tapping the upper or lower third of the thread scrolls a page (the gestures settings). G20 (L-B7): on unless it was
     * turned off, as futaber's 「する」 (a person who never set it gets it on; an explicit OFF stays).
     */
    val tapScroll: Boolean = true,
    /** C26: the picture sheet offers Google Lens (設定→スレッド→その他; off by default). */
    val googleLens: Boolean = false,
    val postGapDp: Int = FUTABER_POST_GAP_DEFAULT,
    val headerOneLine: Boolean = false,
    /** M23: 「コピー」 copies at once as before (設定→スレッドの末尾; off by default: the original's copy panel). */
    val instantCopy: Boolean = false,
    /** M08: the menu's search word narrows the thread as before (設定→スレッドの末尾; off by default: the original's list and bubble). */
    val legacySearchFilter: Boolean = false,
    /** M11: 「返信が多いレスを表示」 narrows the thread as before (設定→スレッドの末尾; off by default: the original's bubble). */
    val legacyManyRepliesFilter: Boolean = false,
    /** F17: a new thread's writing screen has the subject field as before (設定→スレッドの末尾; off by default: the original's has none). */
    val legacySubjectField: Boolean = false,
    /** The shared "板名の誤投稿確認": a post that looks meant for another board asks first. */
    val postDestinationConfirm: Boolean = false,
    /** Pixels per step and milliseconds between steps of the auto-scroll. */
    val autoScrollPixel: Int = FUTABER_AUTO_SCROLL_PIXEL_DEFAULT,
    val autoScrollSpeedMillis: Int = FUTABER_AUTO_SCROLL_SPEED_DEFAULT,
    /** B7a extensions (all off by default). */
    val extCatalogModes: Boolean = true,
    val extCatalogWatchHighlight: Boolean = true,
    val extReplyPriority: Boolean = true,
    val extCatalogHistory: Boolean = true,
    val extCatalogTools: Boolean = true,
    /** B7b extensions (all off by default). */
    val extNgDetail: Boolean = true,
    val extGallerySelectSave: Boolean = false,
    val extReadAloudPanel: Boolean = true,
    /** B9 extensions (all off by default). */
    val extCatalogFineTune: Boolean = true,
    /** The grid's own column counts and title (only read with [extCatalogFineTune]; the default draws as before). */
    val catalogFine: FutaberCatalogFineTune = FutaberCatalogFineTune(),
    val extAutoSaveView: Boolean = true,
    /** The shared "レス数で優先ソート" value (0 = no thread counts as few). */
    val replyPriorityThreshold: Int = 0,
    val extOpenSummary: Boolean = true,
    val extTabStatus: Boolean = true,
    val extReportNg: Boolean = true
) {
    /** [base] is a size at the default font size; the result follows the chosen one. */
    fun sp(base: Float): TextUnit = (base * fontSize / FUTABER_FONT_SIZE_DEFAULT).sp

    companion object {
        /** A missing, damaged or out-of-range stored value reads as the default. */
        fun from(preferences: Map<String, String>): FutaberDisplaySettings = FutaberDisplaySettings(
            fontSize = preferences[FutaberSettingKeys.FONT_SIZE]?.toIntOrNull()
                ?.takeIf { it in FUTABER_FONT_SIZE_MIN..FUTABER_FONT_SIZE_MAX } ?: FUTABER_FONT_SIZE_DEFAULT,
            manyRepliesThreshold = preferences[FutaberSettingKeys.MANY_REPLIES]?.toIntOrNull()
                ?.takeIf { it in FUTABER_MANY_REPLIES_MIN..FUTABER_MANY_REPLIES_MAX } ?: FUTABER_MANY_REPLIES_THRESHOLD,
            smallImages = preferences[FutaberSettingKeys.SMALL_IMAGES] == "ON",
            imageRatioPercent = futaberImageRatioPercent(preferences),
            quotePitchTenths = futaberQuotePitchTenths(preferences[FutaberSettingKeys.QUOTE_PITCH]),
            speechPitchTenths = futaberSharedSpeechTenths(preferences[com.valoser.futacha.shared.audio.SPEECH_PITCH_KEY]),
            speechRateTenths = futaberSharedSpeechTenths(preferences[com.valoser.futacha.shared.audio.SPEECH_RATE_KEY]),
            scrollCatalogToTopOnRefresh = preferences[FutaberSettingKeys.CATALOG_SCROLL_TOP] != "OFF",
            catalogRowWide = preferences[FutaberSettingKeys.CATALOG_ROW_WIDE] == "ON",
            alwaysShowTitleAndName = preferences[FutaberSettingKeys.TITLE_NAME_ALWAYS] == "ON",
            viewerSwipeClose = preferences[FutaberSettingKeys.VIEWER_SWIPE_CLOSE] == "ON",
            viewerStorageButton = FutaberStorageButton.from(preferences[FutaberSettingKeys.VIEWER_STORAGE_BUTTON]),
            viewerPortraitLock = preferences[FutaberSettingKeys.VIEWER_PORTRAIT_LOCK] == "ON",
            viewerAutoplay = preferences[FutaberSettingKeys.VIEWER_AUTOPLAY] == "ON",
            urlSchemes = FutaberUrlSchemes.from(preferences),
            wallpapers = FutaberWallpapers.from(preferences),
            patrolEnabled = preferences[FutaberSettingKeys.PATROL_ENABLED] != "OFF",
            extTree = preferences[FutaberSettingKeys.EXT_TREE] == "ON",
            extExtract = preferences[FutaberSettingKeys.EXT_EXTRACT] != "OFF",
            extIdTap = preferences[FutaberSettingKeys.EXT_ID_TAP] != "OFF",
            extQuickSaidane = preferences[FutaberSettingKeys.EXT_QUICK_SAIDANE] == "ON",
            extQuickNg = preferences[FutaberSettingKeys.EXT_QUICK_NG] == "ON",
            extAutoScroll = preferences[FutaberSettingKeys.EXT_AUTO_SCROLL] != "OFF",
            extHistory = preferences[FutaberSettingKeys.EXT_HISTORY] != "OFF",
            extMailPresets = preferences[FutaberSettingKeys.EXT_MAIL_PRESETS] != "OFF",
            extVolumeKeys = preferences[FutaberSettingKeys.EXT_VOLUME_KEYS] == "ON",
            extTabs = preferences[FutaberSettingKeys.EXT_TABS] != "OFF",
            extVideo = preferences[FutaberSettingKeys.EXT_VIDEO] != "OFF",
            extNgChain = preferences[FutaberSettingKeys.EXT_NG_CHAIN] == "ON",
            extOpenAtTop = preferences[FutaberSettingKeys.EXT_OPEN_AT_TOP] == "ON",
            saidaneThreshold = preferences[FutaberSettingKeys.SAIDANE_THRESHOLD]?.filter(Char::isDigit)?.toIntOrNull()
                ?.takeIf { it in FUTABER_SAIDANE_THRESHOLD_MIN..FUTABER_SAIDANE_THRESHOLD_MAX } ?: FUTABER_MANY_SAIDANE_THRESHOLD,
            foldExtras = preferences[FutaberSettingKeys.FOLD_EXTRAS] != "OFF",
            extTabsManage = preferences[FutaberSettingKeys.EXT_TABS_MANAGE] == "ON",
            extCatalogVolumeKey = preferences[FutaberSettingKeys.EXT_CATALOG_VOLUME_KEY] == "ON",
            extOpenUrl = preferences[FutaberSettingKeys.EXT_OPEN_URL] != "OFF",
            threadFastScroll = preferences[FutaberSettingKeys.THREAD_FAST_SCROLL] == "ON",
            catalogFastScroll = preferences[FutaberSettingKeys.CATALOG_FAST_SCROLL] == "ON",
            boardPin = preferences[FutaberSettingKeys.BOARD_PIN] != "OFF",
            extTabsCheck = preferences[FutaberSettingKeys.EXT_TABS_CHECK] != "OFF",
            extTabsCloseFallen = preferences[FutaberSettingKeys.EXT_TABS_CLOSE_FALLEN] != "OFF",
            extArchiveMenu = preferences[FutaberSettingKeys.EXT_ARCHIVE_MENU] != "OFF",
            extSavedDetail = preferences[FutaberSettingKeys.EXT_SAVED_DETAIL] != "OFF",
            extOtherFavorites = preferences[FutaberSettingKeys.EXT_OTHER_FAVORITES] != "OFF",
            extScrollMenu = preferences[FutaberSettingKeys.EXT_SCROLL_MENU] != "OFF",
            extSearchNav = preferences[FutaberSettingKeys.EXT_SEARCH_NAV] != "OFF",
            volumeKeyMode = FutaberVolumeKeyMode.fromPersisted(preferences[FutaberSettingKeys.EXT_VOLUME_KEY_MODE]),
            extSortByBoard = preferences[FutaberSettingKeys.EXT_SORT_BY_BOARD] == "ON",
            extCatalogLongPress = preferences[FutaberSettingKeys.EXT_CATALOG_LONG_PRESS] != "OFF",
            extPostDetailOps = preferences[FutaberSettingKeys.EXT_POST_DETAIL_OPS] != "OFF",
            extGalleryColumns = preferences[FutaberSettingKeys.EXT_GALLERY_COLUMNS] != "OFF",
            extImageMenu = preferences[FutaberSettingKeys.EXT_IMAGE_MENU] != "OFF",
            extPostTools = preferences[FutaberSettingKeys.EXT_POST_TOOLS] != "OFF",
            extDraftAttachment = preferences[FutaberSettingKeys.EXT_DRAFT_ATTACHMENT] != "OFF",
            extHistoryArchive = preferences[FutaberSettingKeys.EXT_HISTORY_ARCHIVE] != "OFF",
            extForegroundPatrol = preferences[FutaberSettingKeys.EXT_FOREGROUND_PATROL] != "OFF",
            extExternalWatcher = preferences[FutaberSettingKeys.EXT_EXTERNAL_WATCHER] != "OFF",
            extMediaOnlySave = preferences[FutaberSettingKeys.EXT_MEDIA_ONLY_SAVE] != "OFF",
            extCustomFont = preferences[FutaberSettingKeys.EXT_CUSTOM_FONT] != "OFF",
            archiveReportEnabled = preferences[FutaberSettingKeys.ARCHIVE_REPORT] != "OFF",
            tapScroll = preferences[FutaberSettingKeys.TAP_SCROLL] != "OFF",
            googleLens = preferences[FutaberSettingKeys.GOOGLE_LENS] == "ON",
            postGapDp = preferences[FutaberSettingKeys.POST_GAP]?.toIntOrNull()
                ?.takeIf { it in FUTABER_POST_GAP_MIN..FUTABER_POST_GAP_MAX } ?: FUTABER_POST_GAP_DEFAULT,
            headerOneLine = preferences[FutaberSettingKeys.HEADER_ONE_LINE] == "ON",
            instantCopy = preferences[FutaberSettingKeys.INSTANT_COPY] == "ON",
            legacySearchFilter = preferences[FutaberSettingKeys.LEGACY_SEARCH_FILTER] == "ON",
            legacyManyRepliesFilter = preferences[FutaberSettingKeys.LEGACY_MANY_REPLIES_FILTER] == "ON",
            legacySubjectField = preferences[FutaberSettingKeys.LEGACY_SUBJECT_FIELD] == "ON",
            postDestinationConfirm = preferences.compatPreferenceValue(
                "control", "controlPostDestinationConfirm", "板名の誤投稿確認"
            ) == "ON",
            autoScrollPixel = preferences[FutaberSettingKeys.AUTO_SCROLL_PIXEL]?.filter(Char::isDigit)?.toIntOrNull()
                ?.takeIf { it in FUTABER_AUTO_SCROLL_PIXEL_MIN..FUTABER_AUTO_SCROLL_PIXEL_MAX } ?: FUTABER_AUTO_SCROLL_PIXEL_DEFAULT,
            autoScrollSpeedMillis = preferences[FutaberSettingKeys.AUTO_SCROLL_SPEED]?.filter(Char::isDigit)?.toIntOrNull()
                ?.takeIf { it in FUTABER_AUTO_SCROLL_SPEED_MIN..FUTABER_AUTO_SCROLL_SPEED_MAX } ?: FUTABER_AUTO_SCROLL_SPEED_DEFAULT,
            extOpenSummary = preferences[FutaberSettingKeys.EXT_OPEN_SUMMARY] != "OFF",
            extTabStatus = preferences[FutaberSettingKeys.EXT_TAB_STATUS] != "OFF",
            extReportNg = preferences[FutaberSettingKeys.EXT_REPORT_NG] != "OFF",
            extCatalogModes = preferences[FutaberSettingKeys.EXT_CATALOG_MODES] != "OFF",
            extCatalogWatchHighlight = preferences[FutaberSettingKeys.EXT_CATALOG_WATCH_HIGHLIGHT] != "OFF",
            extReplyPriority = preferences[FutaberSettingKeys.EXT_REPLY_PRIORITY] != "OFF",
            extCatalogHistory = preferences[FutaberSettingKeys.EXT_CATALOG_HISTORY] != "OFF",
            extCatalogTools = preferences[FutaberSettingKeys.EXT_CATALOG_TOOLS] != "OFF",
            extNgDetail = preferences[FutaberSettingKeys.EXT_NG_DETAIL] != "OFF",
            extGallerySelectSave = preferences[FutaberSettingKeys.EXT_GALLERY_SELECT_SAVE] == "ON",
            extReadAloudPanel = preferences[FutaberSettingKeys.EXT_READ_ALOUD_PANEL] != "OFF",
            extCatalogFineTune = preferences[FutaberSettingKeys.EXT_CATALOG_FINE_TUNE] != "OFF",
            catalogFine = FutaberCatalogFineTune.from(preferences),
            extAutoSaveView = preferences[FutaberSettingKeys.EXT_AUTO_SAVE_VIEW] != "OFF",
            replyPriorityThreshold = futaberReplyPriorityThreshold(preferences)
        )
    }
}

/** The value after a "+" or "−" press, kept inside [min]..[max]. */
internal fun futaberStep(value: Int, delta: Int, min: Int, max: Int): Int = (value + delta).coerceIn(min, max)

internal val LocalFutaberDisplaySettings = staticCompositionLocalOf { FutaberDisplaySettings() }

/** What the person is told when something they changed could not be written (a full or unavailable storage). */
internal const val FUTABER_STORE_FAILURE_NOTICE = "保存できませんでした。端末の空き容量を確認してください"
