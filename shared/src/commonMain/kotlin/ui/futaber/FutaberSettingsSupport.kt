package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

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
    /** Shared with としあき(仮) and ふたちゃ (スレッド→オートスクロール量／速度), so the amount and speed are the same everywhere. */
    val AUTO_SCROLL_PIXEL = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("thread", "autoScrollPixel")
    val AUTO_SCROLL_SPEED = com.valoser.futacha.shared.ui.compat.compatPreferenceStorageKey("thread", "autoScrollSpeed")
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
    /** Pixels per step and milliseconds between steps of the auto-scroll. */
    val autoScrollPixel: Int = FUTABER_AUTO_SCROLL_PIXEL_DEFAULT,
    val autoScrollSpeedMillis: Int = FUTABER_AUTO_SCROLL_SPEED_DEFAULT
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
            autoScrollPixel = preferences[FutaberSettingKeys.AUTO_SCROLL_PIXEL]?.filter(Char::isDigit)?.toIntOrNull()
                ?.takeIf { it in FUTABER_AUTO_SCROLL_PIXEL_MIN..FUTABER_AUTO_SCROLL_PIXEL_MAX } ?: FUTABER_AUTO_SCROLL_PIXEL_DEFAULT,
            autoScrollSpeedMillis = preferences[FutaberSettingKeys.AUTO_SCROLL_SPEED]?.filter(Char::isDigit)?.toIntOrNull()
                ?.takeIf { it in FUTABER_AUTO_SCROLL_SPEED_MIN..FUTABER_AUTO_SCROLL_SPEED_MAX } ?: FUTABER_AUTO_SCROLL_SPEED_DEFAULT
        )
    }
}

/** The value after a "+" or "−" press, kept inside [min]..[max]. */
internal fun futaberStep(value: Int, delta: Int, min: Int, max: Int): Int = (value + delta).coerceIn(min, max)

internal val LocalFutaberDisplaySettings = staticCompositionLocalOf { FutaberDisplaySettings() }

/** What the person is told when something they changed could not be written (a full or unavailable storage). */
internal const val FUTABER_STORE_FAILURE_NOTICE = "保存できませんでした。端末の空き容量を確認してください"
