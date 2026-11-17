package com.valoser.futacha.shared.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.newFixedThreadPoolContext

@OptIn(DelicateCoroutinesApi::class)
actual val platformIoDispatcher: CoroutineDispatcher =
    newFixedThreadPoolContext(8, "FutachaIo")
