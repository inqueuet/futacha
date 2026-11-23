package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.ui.compat.compatMixGridColumns
import com.valoser.futacha.shared.ui.ftutter.FtutterKeys
import com.valoser.futacha.shared.ui.ftutter.ftutterMixGridColumns
import com.valoser.futacha.shared.ui.futaber.FutaberCatalogDisplayStyle
import com.valoser.futacha.shared.ui.futaber.FutaberCatalogFineTune
import com.valoser.futacha.shared.ui.futaber.futaberCatalogColumns
import com.valoser.futacha.shared.ui.futaber.futaberMixGridColumns
import kotlin.test.Test
import kotlin.test.assertEquals

/** The まとめカタログ draws as many columns as the mode's own catalog. */
class MixGridColumnsTest {
    @Test
    fun eachOrientationTakesItsOwnCount() {
        val columns = MixGridColumns(5, 7)
        assertEquals(5, columns.columnsFor(landscape = false))
        assertEquals(7, columns.columnsFor(landscape = true))
        assertEquals(3, MixGridColumns.DEFAULT.columnsFor(landscape = true))
    }

    @Test
    fun toshiakiReadsItsPortraitAndLandscapeColumns() {
        assertEquals(MixGridColumns(5, 7), compatMixGridColumns(emptyMap()))
        val set = compatMixGridColumns(mapOf("compat.catalog.catalogGridViewPortraitClmNum" to "4", "compat.catalog.catalogGridViewLandscapeClmNum" to "12"))
        assertEquals(MixGridColumns(4, 12), set)
    }

    @Test
    fun ftutterReadsItsCatalogColumns() {
        assertEquals(MixGridColumns(5, 8), ftutterMixGridColumns(emptyMap()))
        assertEquals(MixGridColumns(2, 10), ftutterMixGridColumns(mapOf(FtutterKeys.CATALOG_ROW to "2", FtutterKeys.CATALOG_ROW_LANDSCAPE to "10")))
    }

    @Test
    fun futaberUsesTheGridStylesColumnsAndThreeForARowStyle() {
        val fine = FutaberCatalogFineTune()
        FutaberCatalogDisplayStyle.entries.forEach { style ->
            val expected = futaberCatalogColumns(style, fine)?.let { MixGridColumns(it) } ?: MixGridColumns.DEFAULT
            assertEquals(expected, futaberMixGridColumns(style, fine))
        }
    }

    @Test
    fun theMixKeepsTheModesWiderRangeWhileTheCatalogKeepsItsOwn() {
        assertEquals(16, catalogGridColumnsShown(16, mix = true))
        assertEquals(1, catalogGridColumnsShown(1, mix = true))
        assertEquals(20, catalogGridColumnsShown(99, mix = true))
        assertEquals(MAX_CATALOG_GRID_COLUMNS, catalogGridColumnsShown(16, mix = false))
        // may 1449249741: ふたちゃ goes up to 12 columns on a tablet.
        assertEquals(12, MAX_CATALOG_GRID_COLUMNS)
        assertEquals(10, catalogGridColumnsShown(10, mix = false))
    }
}
