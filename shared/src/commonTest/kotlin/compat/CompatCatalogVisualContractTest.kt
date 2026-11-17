package compat

import androidx.compose.ui.graphics.Color
import com.valoser.futacha.shared.ui.compat.CompatCatalogVisualContract
import com.valoser.futacha.shared.ui.compat.compatibilityCatalogSurface
import com.valoser.futacha.shared.ui.compat.compatibilityPaletteFor
import com.valoser.futacha.shared.ui.compat.CompatCatalogReplyCountPlacement
import com.valoser.futacha.shared.ui.compat.compatCatalogReplyCountPlacement
import com.valoser.futacha.shared.ui.compat.compatSettingsEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CompatCatalogVisualContractTest {
    @Test
    fun sampleCatalogGeometryAndCropDefaultsAreExplicit() {
        assertEquals(5, CompatCatalogVisualContract.itemTopSpacerDp)
        assertEquals(1f, CompatCatalogVisualContract.thumbnailAspectRatio)
        assertEquals(2, CompatCatalogVisualContract.titleHorizontalPaddingDp)
        assertFalse(CompatCatalogVisualContract.defaultThumbnailCrop)
    }

    @Test
    fun catalogPreferenceDefaultMatchesSampleAdapter() {
        val entry = "catalog".compatSettingsEntries()
            .single { it.preferenceKey == "catalogThumbCrop" }
        assertEquals("OFF", entry.summary)
        assertEquals(
            CompatCatalogReplyCountPlacement.BELOW_TITLE,
            compatCatalogReplyCountPlacement(showOnThumbnail = false)
        )
        assertEquals(
            CompatCatalogReplyCountPlacement.ON_THUMBNAIL,
            compatCatalogReplyCountPlacement(showOnThumbnail = true)
        )
    }

    @Test
    fun cardSurfacesAreWhiteForLightThemesAndBlackForBlackTheme() {
        assertEquals(Color.White, compatibilityCatalogSurface(compatibilityPaletteFor(null)))
        assertEquals(Color.White, compatibilityCatalogSurface(compatibilityPaletteFor("ふたば")))
        assertEquals(Color.Black, compatibilityCatalogSurface(compatibilityPaletteFor("ブラック")))
    }
}
