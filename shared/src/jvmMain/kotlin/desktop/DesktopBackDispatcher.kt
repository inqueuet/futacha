package com.valoser.futacha.shared.desktop

object DesktopBackDispatcher {
    private val handlers = LinkedHashMap<Any, () -> Unit>()
    // The app tree stays composed under the lock overlay, so its back handlers stay registered;
    // the window's Esc and the menu's "戻る" moved those hidden screens back and, at としあき(仮)'s
    // top level, closed the app (L124). Unlocked until the app says otherwise: only FutachaApp knows.
    @Volatile private var appUnlocked = true
    internal fun add(key: Any, action: () -> Unit) { handlers[key] = action }
    internal fun remove(key: Any) { handlers.remove(key) }
    /** Whether FutachaApp shows its content (not the lock screen); fed from its onAppUnlockedChanged. */
    fun setAppUnlocked(unlocked: Boolean) { appUnlocked = unlocked }
    fun dispatch(): Boolean {
        if (!appUnlocked) return false
        val action = handlers.values.lastOrNull() ?: return false
        action(); return true
    }
}
