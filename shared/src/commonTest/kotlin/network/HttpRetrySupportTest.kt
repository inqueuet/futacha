package com.valoser.futacha.shared.network

import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HttpRetrySupportTest {
    @Test
    fun automaticRetry_isLimitedToReadOnlyMethods() {
        assertTrue(isSafeAutomaticRetryMethod(HttpMethod.Get))
        assertTrue(isSafeAutomaticRetryMethod(HttpMethod.Head))
        assertTrue(isSafeAutomaticRetryMethod(HttpMethod.Options))
        assertFalse(isSafeAutomaticRetryMethod(HttpMethod.Post))
        assertFalse(isSafeAutomaticRetryMethod(HttpMethod.Put))
        assertFalse(isSafeAutomaticRetryMethod(HttpMethod.Delete))
        assertFalse(isSafeAutomaticRetryMethod(HttpMethod.Patch))
    }

    @Test
    fun automaticRetry_isSuppressedWhenHigherLayerOwnsRetryLoop() {
        assertTrue(shouldUseClientAutomaticRetry(HttpMethod.Get, higherLayerRetryManaged = false))
        assertFalse(shouldUseClientAutomaticRetry(HttpMethod.Get, higherLayerRetryManaged = true))
        assertFalse(shouldUseClientAutomaticRetry(HttpMethod.Post, higherLayerRetryManaged = false))
    }
}
