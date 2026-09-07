package com.valoser.futacha.shared.ui.image

import coil3.Extras
import coil3.getExtra
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val RefreshOperation = Extras.Key(0L)
internal fun ImageRequest.Builder.refreshImageOnce(operation: Long): ImageRequest.Builder = apply {
    extras[RefreshOperation] = operation
}

/** Bypass old data once per explicit operation, but write the successful replacement. */
internal class ImageRefreshInterceptor : Interceptor {
    private val mutex = Mutex()
    private val completed = LinkedHashMap<Pair<String, Long>, Unit>()
    private val active = mutableMapOf<Pair<String, Long>, CompletableDeferred<Boolean>>()

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val operation = chain.request.getExtra(RefreshOperation)
        if (operation == 0L) return chain.proceed()
        val key = chain.request.data.toString() to operation
        while (true) {
            var leader = false
            val waiter = mutex.withLock {
                if (key in completed) null else active[key] ?: CompletableDeferred<Boolean>().also {
                    active[key] = it
                    leader = true
                }
            }
            if (waiter == null) return chain.proceed()
            if (!leader) {
                waiter.await()
                continue
            }
            var success = false
            try {
                val request = chain.request.newBuilder()
                    .memoryCachePolicy(CachePolicy.WRITE_ONLY)
                    .diskCachePolicy(CachePolicy.WRITE_ONLY)
                    .build()
                return chain.withRequest(request).proceed().also { success = it is SuccessResult }
            } finally {
                withContext(NonCancellable) {
                    mutex.withLock {
                        active.remove(key)
                        if (success) {
                            completed[key] = Unit
                            while (completed.size > 4096) completed.remove(completed.keys.first())
                        }
                        waiter.complete(success)
                    }
                }
            }
        }
    }
}
