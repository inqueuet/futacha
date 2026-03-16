package com.valoser.futacha.shared.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual val platformIoDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(8)
