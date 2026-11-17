package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.*
import com.valoser.futacha.shared.ui.compat.CompatForegroundLifecycleEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlin.time.Clock

/** Display-only clock: no catalog request, and cancelled when the screen leaves the foreground. */
internal fun mixDisplayTimes(nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() }) = flow {
    while (true) {
        emit(nowMillis())
        delay(60_000L)
    }
}

@Composable
internal fun rememberMixDisplayTime(): Long {
    var foreground by remember { mutableStateOf(false) }
    CompatForegroundLifecycleEffect { foreground = it }
    val now by produceState(Clock.System.now().toEpochMilliseconds(), foreground) {
        if (foreground) mixDisplayTimes().collect { value = it }
    }
    return now
}
