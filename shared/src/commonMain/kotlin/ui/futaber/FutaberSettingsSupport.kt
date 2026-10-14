package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.valoser.futacha.shared.ui.compat.compatPreferenceValue

/** Preference keys of the settings screen (all under the mode's own `compat.futaber.` prefix). */
internal object FutaberSettingKeys {
    const val FONT_SIZE = "compat.futaber.fontSize"
    const val MANY_REPLIES = "compat.futaber.manyReplies"
    const val SMALL_IMAGES = "compat.futaber.smallImages"
    const val CATALOG_SCROLL_TOP = "compat.futaber.catalog.scrollTop"
    const val CATALOG_ROW_WIDE = "compat.futaber.catalog.rowWide"
    /** Shared with ふたちゃ and としあき: [com.valoser.futacha.shared.compat.COMPAT_WATCH_ENABLED_KEY]. */
    const val PATROL_ENABLED = "compat.watcher.enabled"
    const val VIEWER_SWIPE_CLOSE = "compat.futaber.viewerSwipeClose"
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
    const val EXT_TABS_MANAGE = "compat.futaber.ext.tabsManage"
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
internal const val FUTABER_MANY_REPLIES_MIN = 2
internal const val FUTABER_MANY_REPLIES_MAX = 30

/**
 * What the settings screen changes in the screens. [fontSize] is the body text size in sp; the
 * header line and the title line keep their proportion to it (12 and 13 at the default 14).
 */
internal data class FutaberDisplaySettings(
    val fontSize: Int = FUTABER_FONT_SIZE_DEFAULT,
    val manyRepliesThreshold: Int = FUTABER_MANY_REPLIES_THRESHOLD,
    /** Posts' images start at half size (the operation menu can still flip it per thread). */
    val smallImages: Boolean = false,
    val scrollCatalogToTopOnRefresh: Boolean = true,
    /** The list style's "やや広め" rows: taller thumbnails and room for two title lines. */
    val catalogRowWide: Boolean = false,
    /** Show a post's subject and name even when they are the board defaults. */
    val alwaysShowTitleAndName: Boolean = false,
    /** The image viewer closes on a downward swipe (off by default, as in the original). */
    val viewerSwipeClose: Boolean = false,
    /** The keyword patrol shared with the other modes (their own "OFF" turns it off everywhere). */
    val patrolEnabled: Boolean = true,
    /** Replies are listed under the post they quote, indented by depth (the tree view of the other modes). */
    val extTree: Boolean = false,
    /** The operation menu gets "レスを抽出" (own posts, many "そうだね", deleted, URL, image). */
    val extExtract: Boolean = false,
    /** Tapping a poster's ID lists the posts with the same ID. */
    val extIdTap: Boolean = false,
    /** The operation menu gets "オートスクロール". */
    val extAutoScroll: Boolean = false,
    val extQuickSaidane: Boolean = false,
    val extQuickNg: Boolean = false,
    /** The history list of the manage panel gets a search / sort / filter band and "一括更新". */
    val extHistory: Boolean = false,
    /** The write settings' mail field gets "ID表示" / "IP表示" / "sage" buttons. */
    val extMailPresets: Boolean = false,
    /** The volume keys scroll the thread by a screen (Android; the keys are not delivered on iOS). */
    val extVolumeKeys: Boolean = false,
    /** The tab long-press sheet gets "閉じたタブを元に戻す". */
    val extTabs: Boolean = false,
    /**
     * A video page of the picture viewer is the video viewer (play / pause / stop, volume) instead of only a button that
     * opens the video elsewhere. On from the start; a person can turn it off.
     */
    val extVideo: Boolean = true,
    /** Closing several tabs at once (the tab sheet, "すべて削除") keeps the tabs of starred threads. */
    val extTabsManage: Boolean = false,
    /** The operation menu gets "URL・アーカイブ" (FTBucket, つまんね。, ふたばフォレスト, ふたポ). */
    val extArchiveMenu: Boolean = false,
    /** A long press on a row of the saved box shows how complete the save is. */
    val extSavedDetail: Boolean = false,
    /** The favourites of the manage panel also list the favourites of the other modes (read only). */
    val extOtherFavorites: Boolean = false,
    /** The operation menu gets "1ページ上へ" / "1ページ下へ" / "先頭へ". */
    val extScrollMenu: Boolean = false,
    /** While a search narrows the thread, the chip moves between the found posts (▲ n/N ▼). */
    val extSearchNav: Boolean = false,
    /** What the volume keys do (with [extVolumeKeys]): a screen (as before), a post, or the next / previous tab's thread. */
    val volumeKeyMode: FutaberVolumeKeyMode = FutaberVolumeKeyMode.Screen,
    /** The catalog's sort order is remembered per board (a board without its own record uses the one shared value). */
    val extSortByBoard: Boolean = false,
    /** The catalog's long-press sheet gets "画像NGに登録". */
    val extCatalogLongPress: Boolean = false,
    /**
     * "レスの詳細操作": deleting an own post can delete only its picture ("画像だけ消す"), and the long-press sheet gets
     * "項目を選んでコピー" / "語句を選んでGoogle検索" / "クイック引用返信" and "関連レス" / "同じID" / "同じIP" (B7b).
     */
    val extPostDetailOps: Boolean = false,
    /** The picture list takes the column counts of the shared viewer settings (portrait / landscape) instead of 3. */
    val extGalleryColumns: Boolean = false,
    /** The long-press menu of a post's picture gets save, share, copy URL, open elsewhere and open in the viewer. */
    val extImageMenu: Boolean = false,
    /** A long press on the write screen's attach button opens the tools (drawing, あぷ小, voice, network / device lines). */
    val extPostTools: Boolean = false,
    /** The draft keeps its attachment as well as its text. */
    val extDraftAttachment: Boolean = false,
    /** The history band's "アーカイブ" (export / import of the history archive). */
    val extHistoryArchive: Boolean = false,
    /** The shared patrol / tab checks run every minute while this mode is in the foreground. */
    val extForegroundPatrol: Boolean = false,
    /** Android: the external にじろぐ can be chosen in the patrol manager, and its results are listed under "通知". */
    val extExternalWatcher: Boolean = false,
    /** The save dialog's media-only rows (ZIP / folder). */
    val extMediaOnlySave: Boolean = false,
    /** The shared custom font is applied to this mode's letters. */
    val extCustomFont: Boolean = false,
    /** The shared "閲覧スレ通知": on unless it was turned off (in any mode). */
    val archiveReportEnabled: Boolean = true,
    /** Tapping the upper or lower third of the thread scrolls a page (the gestures settings). */
    val tapScroll: Boolean = false,
    /** The shared "板名の誤投稿確認": a post that looks meant for another board asks first. */
    val postDestinationConfirm: Boolean = false,
    /** Pixels per step and milliseconds between steps of the auto-scroll. */
    val autoScrollPixel: Int = FUTABER_AUTO_SCROLL_PIXEL_DEFAULT,
    val autoScrollSpeedMillis: Int = FUTABER_AUTO_SCROLL_SPEED_DEFAULT,
    /** B7a extensions (all off by default). */
    val extCatalogModes: Boolean = false,
    val extCatalogWatchHighlight: Boolean = false,
    val extReplyPriority: Boolean = false,
    val extCatalogHistory: Boolean = false,
    val extCatalogTools: Boolean = false,
    /** B7b extensions (all off by default). */
    val extNgDetail: Boolean = false,
    val extGallerySelectSave: Boolean = false,
    val extReadAloudPanel: Boolean = false,
    /** B9 extensions (all off by default). */
    val extCatalogFineTune: Boolean = false,
    /** The grid's own column counts and title (only read with [extCatalogFineTune]; the default draws as before). */
    val catalogFine: FutaberCatalogFineTune = FutaberCatalogFineTune(),
    val extAutoSaveView: Boolean = false,
    /** The shared "レス数で優先ソート" value (0 = no thread counts as few). */
    val replyPriorityThreshold: Int = 0
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
            scrollCatalogToTopOnRefresh = preferences[FutaberSettingKeys.CATALOG_SCROLL_TOP] != "OFF",
            catalogRowWide = preferences[FutaberSettingKeys.CATALOG_ROW_WIDE] == "ON",
            alwaysShowTitleAndName = preferences[FutaberSettingKeys.TITLE_NAME_ALWAYS] == "ON",
            viewerSwipeClose = preferences[FutaberSettingKeys.VIEWER_SWIPE_CLOSE] == "ON",
            patrolEnabled = preferences[FutaberSettingKeys.PATROL_ENABLED] != "OFF",
            extTree = preferences[FutaberSettingKeys.EXT_TREE] == "ON",
            extExtract = preferences[FutaberSettingKeys.EXT_EXTRACT] == "ON",
            extIdTap = preferences[FutaberSettingKeys.EXT_ID_TAP] == "ON",
            extQuickSaidane = preferences[FutaberSettingKeys.EXT_QUICK_SAIDANE] == "ON",
            extQuickNg = preferences[FutaberSettingKeys.EXT_QUICK_NG] == "ON",
            extAutoScroll = preferences[FutaberSettingKeys.EXT_AUTO_SCROLL] == "ON",
            extHistory = preferences[FutaberSettingKeys.EXT_HISTORY] == "ON",
            extMailPresets = preferences[FutaberSettingKeys.EXT_MAIL_PRESETS] == "ON",
            extVolumeKeys = preferences[FutaberSettingKeys.EXT_VOLUME_KEYS] == "ON",
            extTabs = preferences[FutaberSettingKeys.EXT_TABS] == "ON",
            extVideo = preferences[FutaberSettingKeys.EXT_VIDEO] != "OFF",
            extTabsManage = preferences[FutaberSettingKeys.EXT_TABS_MANAGE] == "ON",
            extArchiveMenu = preferences[FutaberSettingKeys.EXT_ARCHIVE_MENU] == "ON",
            extSavedDetail = preferences[FutaberSettingKeys.EXT_SAVED_DETAIL] == "ON",
            extOtherFavorites = preferences[FutaberSettingKeys.EXT_OTHER_FAVORITES] == "ON",
            extScrollMenu = preferences[FutaberSettingKeys.EXT_SCROLL_MENU] == "ON",
            extSearchNav = preferences[FutaberSettingKeys.EXT_SEARCH_NAV] == "ON",
            volumeKeyMode = FutaberVolumeKeyMode.fromPersisted(preferences[FutaberSettingKeys.EXT_VOLUME_KEY_MODE]),
            extSortByBoard = preferences[FutaberSettingKeys.EXT_SORT_BY_BOARD] == "ON",
            extCatalogLongPress = preferences[FutaberSettingKeys.EXT_CATALOG_LONG_PRESS] == "ON",
            extPostDetailOps = preferences[FutaberSettingKeys.EXT_POST_DETAIL_OPS] == "ON",
            extGalleryColumns = preferences[FutaberSettingKeys.EXT_GALLERY_COLUMNS] == "ON",
            extImageMenu = preferences[FutaberSettingKeys.EXT_IMAGE_MENU] == "ON",
            extPostTools = preferences[FutaberSettingKeys.EXT_POST_TOOLS] == "ON",
            extDraftAttachment = preferences[FutaberSettingKeys.EXT_DRAFT_ATTACHMENT] == "ON",
            extHistoryArchive = preferences[FutaberSettingKeys.EXT_HISTORY_ARCHIVE] == "ON",
            extForegroundPatrol = preferences[FutaberSettingKeys.EXT_FOREGROUND_PATROL] == "ON",
            extExternalWatcher = preferences[FutaberSettingKeys.EXT_EXTERNAL_WATCHER] == "ON",
            extMediaOnlySave = preferences[FutaberSettingKeys.EXT_MEDIA_ONLY_SAVE] == "ON",
            extCustomFont = preferences[FutaberSettingKeys.EXT_CUSTOM_FONT] == "ON",
            archiveReportEnabled = preferences[FutaberSettingKeys.ARCHIVE_REPORT] != "OFF",
            tapScroll = preferences[FutaberSettingKeys.TAP_SCROLL] == "ON",
            postDestinationConfirm = preferences.compatPreferenceValue(
                "control", "controlPostDestinationConfirm", "板名の誤投稿確認"
            ) == "ON",
            autoScrollPixel = preferences[FutaberSettingKeys.AUTO_SCROLL_PIXEL]?.filter(Char::isDigit)?.toIntOrNull()
                ?.takeIf { it in FUTABER_AUTO_SCROLL_PIXEL_MIN..FUTABER_AUTO_SCROLL_PIXEL_MAX } ?: FUTABER_AUTO_SCROLL_PIXEL_DEFAULT,
            autoScrollSpeedMillis = preferences[FutaberSettingKeys.AUTO_SCROLL_SPEED]?.filter(Char::isDigit)?.toIntOrNull()
                ?.takeIf { it in FUTABER_AUTO_SCROLL_SPEED_MIN..FUTABER_AUTO_SCROLL_SPEED_MAX } ?: FUTABER_AUTO_SCROLL_SPEED_DEFAULT,
            extCatalogModes = preferences[FutaberSettingKeys.EXT_CATALOG_MODES] == "ON",
            extCatalogWatchHighlight = preferences[FutaberSettingKeys.EXT_CATALOG_WATCH_HIGHLIGHT] == "ON",
            extReplyPriority = preferences[FutaberSettingKeys.EXT_REPLY_PRIORITY] == "ON",
            extCatalogHistory = preferences[FutaberSettingKeys.EXT_CATALOG_HISTORY] == "ON",
            extCatalogTools = preferences[FutaberSettingKeys.EXT_CATALOG_TOOLS] == "ON",
            extNgDetail = preferences[FutaberSettingKeys.EXT_NG_DETAIL] == "ON",
            extGallerySelectSave = preferences[FutaberSettingKeys.EXT_GALLERY_SELECT_SAVE] == "ON",
            extReadAloudPanel = preferences[FutaberSettingKeys.EXT_READ_ALOUD_PANEL] == "ON",
            extCatalogFineTune = preferences[FutaberSettingKeys.EXT_CATALOG_FINE_TUNE] == "ON",
            catalogFine = FutaberCatalogFineTune.from(preferences),
            extAutoSaveView = preferences[FutaberSettingKeys.EXT_AUTO_SAVE_VIEW] == "ON",
            replyPriorityThreshold = futaberReplyPriorityThreshold(preferences)
        )
    }
}

/** The value after a "+" or "−" press, kept inside [min]..[max]. */
internal fun futaberStep(value: Int, delta: Int, min: Int, max: Int): Int = (value + delta).coerceIn(min, max)

internal val LocalFutaberDisplaySettings = staticCompositionLocalOf { FutaberDisplaySettings() }

/** What the person is told when something they changed could not be written (a full or unavailable storage). */
internal const val FUTABER_STORE_FAILURE_NOTICE = "保存できませんでした。端末の空き容量を確認してください"
