package com.valoser.futacha.shared.compat

/**
 * Main-thread bridge from Android's left-edge Back gesture to the visible
 * thread. Registration and gesture completion are synchronous: a gesture
 * cannot be buffered and later open a drawer on a different screen.
 */
object CompatBackGestureBus {
    private class Registration(val owner: Any, val openDrawer: () -> Boolean)
    private var registration: Registration? = null

    fun register(owner: Any, openDrawer: () -> Boolean) {
        registration = Registration(owner, openDrawer)
    }

    fun unregister(owner: Any) {
        if (registration?.owner === owner) registration = null
    }

    /** Capture at gesture start, invoke only on commit, discard on cancellation. */
    fun captureDrawerRequest(): (() -> Boolean)? {
        val captured = registration ?: return null
        var completed = false
        return {
            if (completed || registration !== captured) {
                false
            } else {
                completed = true
                captured.openDrawer()
            }
        }
    }
}
