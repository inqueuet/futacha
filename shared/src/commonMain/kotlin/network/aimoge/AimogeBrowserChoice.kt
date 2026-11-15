package com.valoser.futacha.shared.network.aimoge

/**
 * The browser to open the approval page with: the default browser unless it is this app (or the chooser), else the
 * first other browser. Null when there is none.
 */
fun aimogeBrowserPackage(browsers: List<String>, defaultPackage: String?, ownPackage: String): String? {
    val others = browsers.filter { it != ownPackage }
    return defaultPackage?.takeIf { it in others } ?: others.firstOrNull()
}
