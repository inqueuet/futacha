package com.valoser.futacha.shared.desktop

object DesktopBackDispatcher {
    private val handlers = LinkedHashMap<Any, () -> Unit>()
    internal fun add(key: Any, action: () -> Unit) { handlers[key] = action }
    internal fun remove(key: Any) { handlers.remove(key) }
    fun dispatch(): Boolean {
        val action = handlers.values.lastOrNull() ?: return false
        action(); return true
    }
}
