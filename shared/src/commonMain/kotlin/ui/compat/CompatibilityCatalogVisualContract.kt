package com.valoser.futacha.shared.ui.compat

import androidx.compose.ui.graphics.Color

/**
 * Visual facts measured from sample/1.apk's catalog_gridview_item.xml and
 * CatalogFragment.CatalogAdapter. Keep these values in one place so a visual
 * change cannot silently alter only one part of the catalog card.
 */
internal object CompatCatalogVisualContract {
    const val itemTopSpacerDp = 5
    const val thumbnailAspectRatio = 1f
    const val titleHorizontalPaddingDp = 2
    // AppInitialize, AppSettingData and CatalogActivity in sample/1.apk all seed/read false.
    // CatalogFragment's isolated true fallback is overwritten by that initialization path.
    const val defaultThumbnailCrop = false
}

internal enum class CompatCatalogReplyCountPlacement { ON_THUMBNAIL, BELOW_TITLE }

internal fun compatCatalogReplyCountPlacement(showOnThumbnail: Boolean): CompatCatalogReplyCountPlacement =
    if (showOnThumbnail) CompatCatalogReplyCountPlacement.ON_THUMBNAIL
    else CompatCatalogReplyCountPlacement.BELOW_TITLE

internal fun compatibilityCatalogSurface(palette: CompatibilityPalette): Color =
    if (palette.background == Color.Black) Color.Black else Color.White
