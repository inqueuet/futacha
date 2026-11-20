package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.repo.mock.FakeBoardRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** may 1448736916: ふたばー風 can choose how many threads its catalog asks for. */
class FutaberCatalogThreadCountTest {
    @Test
    fun unsetKeepsTheAppLayoutAndASetCountAsksForIt() = runTest {
        val repository = FakeBoardRepository()
        val plain = futaberCatalogFetchSettings(repository)
        assertEquals(repository.getCatalogFetchSettings().copy(titleLines = FUTABER_CATALOG_TITLE_CHARS), plain)
        val thousand = futaberCatalogFetchSettings(repository, 1_000)
        assertEquals(1_000, thousand.approximateThreadCount)
        assertEquals(FUTABER_CATALOG_TITLE_CHARS, thousand.titleLines)
    }

    @Test
    fun theSettingReadsOnlyAValueInRange() {
        fun read(value: String?) = FutaberDisplaySettings.from(
            value?.let { mapOf(FutaberSettingKeys.CATALOG_THREAD_COUNT to it) } ?: emptyMap()
        ).catalogThreadCount
        assertNull(read(null))
        assertEquals(500, read("500"))
        assertNull(read("10"))
        assertNull(read("x"))
    }
}
