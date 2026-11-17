package com.valoser.futacha.shared.network

import io.ktor.http.HttpMethod
import io.ktor.util.AttributeKey

/**
 * Marks requests whose retry loop is already owned by the caller.  Without
 * this marker, [HttpBoardApi] retries could be multiplied by the platform
 * client's [io.ktor.client.plugins.HttpRequestRetry] plugin.
 */
internal val HigherLayerRetryManaged = AttributeKey<Boolean>("HigherLayerRetryManaged")

internal fun isSafeAutomaticRetryMethod(method: HttpMethod): Boolean =
    method == HttpMethod.Get || method == HttpMethod.Head || method == HttpMethod.Options

internal fun shouldUseClientAutomaticRetry(
    method: HttpMethod,
    higherLayerRetryManaged: Boolean
): Boolean = isSafeAutomaticRetryMethod(method) && !higherLayerRetryManaged
