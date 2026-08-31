package com.valoser.futacha.shared.compat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * One-shot bridge for the Android predictive-back edge gesture.
 *
 * The platform owns the gesture stream, while the compatibility UI owns the
 * drawer state. Keeping this as an in-memory event avoids coupling common UI
 * code to an Android Activity or to persisted compatibility data.
 */
object CompatBackGestureBus {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val events: SharedFlow<Unit> = requests.asSharedFlow()

    fun requestDrawer() {
        requests.tryEmit(Unit)
    }
}
