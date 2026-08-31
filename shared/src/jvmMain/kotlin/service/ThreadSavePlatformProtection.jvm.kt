package com.valoser.futacha.shared.service

import com.valoser.futacha.shared.model.SaveProgress
import kotlinx.coroutines.flow.StateFlow

actual suspend fun <T> withThreadSavePlatformProtection(
    title: String,
    progress: StateFlow<SaveProgress?>,
    block: suspend () -> T
): T = block()
