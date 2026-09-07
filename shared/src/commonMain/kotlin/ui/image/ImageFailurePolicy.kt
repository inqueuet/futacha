package com.valoser.futacha.shared.ui.image

import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.HttpException
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.request.Options
import kotlinx.coroutines.CancellationException

/** Only a missing resource is evidence that another URL/extension may exist. */
internal fun isMissingImage(error: Throwable?): Boolean {
    var cause = error
    repeat(16) {
        if (cause is CancellationException) return false
        val current = cause ?: return false
        if (current is HttpException) return current.response.code == 404 || current.response.code == 410
        cause = current.cause.takeUnless { it === current }
    }
    return false
}

/** Revalidate old negative entries too; disabling new writes alone cannot repair them. */
@OptIn(ExperimentalCoilApi::class)
internal object ImageCacheStrategy : CacheStrategy {
    override suspend fun read(cacheResponse: NetworkResponse, networkRequest: NetworkRequest, options: Options): CacheStrategy.ReadResult =
        if (cacheResponse.code in 400..599) CacheStrategy.ReadResult(networkRequest)
        else CacheStrategy.DEFAULT.read(cacheResponse, networkRequest, options)

    override suspend fun write(cacheResponse: NetworkResponse?, networkRequest: NetworkRequest, networkResponse: NetworkResponse, options: Options): CacheStrategy.WriteResult =
        if (networkResponse.code in 400..599) CacheStrategy.WriteResult.DISABLED
        else CacheStrategy.DEFAULT.write(cacheResponse, networkRequest, networkResponse, options)
}
