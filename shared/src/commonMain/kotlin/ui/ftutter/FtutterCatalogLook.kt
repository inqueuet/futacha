package com.valoser.futacha.shared.ui.ftutter

/**
 * T-B5：カタログを原作（Catalog）に合わせる決まり。
 *
 * - H5：セルの長押しは、画像の部分は押している間だけ拡大、題名の部分はメニュー。
 * - 今の動き（セルのどこでも同じ動き）は「従来の動き」のスイッチで残す。
 */
internal val FTUTTER_LEGACY_CATALOG_PRESS_KEY: String get() = FtutterKeys.PREFIX + "legacyCatalogPress"
internal const val FTUTTER_LEGACY_CATALOG_PRESS_LABEL = "カタログの長押しを前の形にする"
internal const val FTUTTER_LEGACY_CATALOG_PRESS_NOTE =
    "ONにすると、セルのどこを長押ししても「長押しで拡大」の設定に従います。OFFは原作と同じ、画像の部分は押している間だけ拡大、題名の部分はメニューです"

internal fun ftutterLegacyCatalogPress(preferences: Map<String, String>): Boolean =
    preferences.ftutterBool(FTUTTER_LEGACY_CATALOG_PRESS_KEY, false)

internal enum class FtutterCatalogPress { ZOOM, MENU }

/** 長押しした所で決まる動き（H5）。[imageBottom]は画像の下端（セルの中のy）。無ければ（題名の無いセル等）拡大。 */
internal fun ftutterCatalogPress(y: Float, imageBottom: Float?, useZoom: Boolean, legacy: Boolean): FtutterCatalogPress = when {
    // 「長押しで拡大」をOFFにした時は、どこでもメニュー（原作の設定と同じ）。
    !useZoom || legacy -> if (useZoom) FtutterCatalogPress.ZOOM else FtutterCatalogPress.MENU
    imageBottom == null || y <= imageBottom -> FtutterCatalogPress.ZOOM
    else -> FtutterCatalogPress.MENU
}
