@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.board

import coil3.network.HttpException
import coil3.network.NetworkResponse
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogPreviewCandidateTest {
    private val cat = "https://may.2chan.net/b/cat/1s.jpg"
    private val thumb = "https://may.2chan.net/b/thumb/1s.jpg"
    private val full = "https://may.2chan.net/b/src/1.jpg"

    @Test
    fun originalIsTriedOnlyWhenTheThumbnailIsMissing() {
        val candidates = listOf(thumb, full)
        for (code in listOf(404, 410)) {
            assertTrue(shouldAdvanceCatalogPreviewCandidate(0, candidates, full, HttpException(NetworkResponse(code = code))))
        }
        for (code in listOf(500, 503)) {
            assertFalse(shouldAdvanceCatalogPreviewCandidate(0, candidates, full, HttpException(NetworkResponse(code = code))))
        }
        assertFalse(shouldAdvanceCatalogPreviewCandidate(0, candidates, full, IllegalStateException("timeout")))
        assertFalse(shouldAdvanceCatalogPreviewCandidate(0, candidates, full, null))
    }

    @Test
    fun ecoThumbnailFallsBackToTheNormalThumbnailOnAnyFailure() {
        val candidates = listOf(cat, thumb, full)
        assertTrue(shouldAdvanceCatalogPreviewCandidate(0, candidates, full, IllegalStateException("timeout")))
        assertFalse(shouldAdvanceCatalogPreviewCandidate(1, candidates, full, IllegalStateException("timeout")))
        assertFalse(shouldAdvanceCatalogPreviewCandidate(2, candidates, full, HttpException(NetworkResponse(code = 404))))
    }
}
