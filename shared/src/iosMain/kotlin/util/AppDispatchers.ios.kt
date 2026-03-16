package com.valoser.futacha.shared.util

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.newFixedThreadPoolContext

private val iosIoDispatcher: CloseableCoroutineDispatcher =
    newFixedThreadPoolContext(4, "futacha-ios-io")

actual val platformIoDispatcher: CoroutineDispatcher = iosIoDispatcher
