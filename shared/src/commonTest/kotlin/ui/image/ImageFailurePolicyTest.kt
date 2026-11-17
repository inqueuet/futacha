@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)
package com.valoser.futacha.shared.ui.image

import coil3.network.HttpException
import coil3.network.NetworkResponse
import kotlinx.coroutines.CancellationException
import kotlin.test.*

class ImageFailurePolicyTest {
    @Test fun onlyMissingResourcesPermitAlternativeUrls() {
        for (code in listOf(404, 410)) assertTrue(isMissingImage(HttpException(NetworkResponse(code = code))))
        for (code in listOf(400, 401, 403, 429, 500, 501, 502, 503, 504)) {
            assertFalse(isMissingImage(HttpException(NetworkResponse(code = code))), "code=$code")
        }
        assertFalse(isMissingImage(IllegalStateException("decode failed")))
        assertFalse(isMissingImage(CancellationException("left viewport")))
        assertTrue(isMissingImage(IllegalStateException("wrapped", HttpException(NetworkResponse(code = 404)))))
    }
}
