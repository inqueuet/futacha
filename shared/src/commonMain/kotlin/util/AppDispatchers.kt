package com.valoser.futacha.shared.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

object AppDispatchers {
    val parsing: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)
    val mockData: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    fun imageFetch(parallelism: Int): CoroutineDispatcher {
        return Dispatchers.Default.limitedParallelism(parallelism.coerceAtLeast(1))
    }
}
