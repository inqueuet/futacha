package com.valoser.futacha.shared.compat

/** Volume-key actions are dispatched by the Android host to the active compat screen. */
enum class CompatVolumeKey {
    UP,
    DOWN
}

/**
 * The original APK handled volume keys at Activity level.  Keep the host hook
 * tiny and platform-neutral so the Compose screens can own the actual scroll
 * behavior without leaking Android KeyEvent into commonMain.
 */
object CompatVolumeKeyBus {
    private var owner: Any? = null
    private var handler: ((CompatVolumeKey) -> Boolean)? = null

    fun register(owner: Any, handler: (CompatVolumeKey) -> Boolean) {
        this.owner = owner
        this.handler = handler
    }

    fun unregister(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            this.handler = null
        }
    }

    fun dispatch(key: CompatVolumeKey): Boolean = handler?.invoke(key) == true
}
