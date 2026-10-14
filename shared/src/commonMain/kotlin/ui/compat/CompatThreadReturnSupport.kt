package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatHost

/**
 * Whether the thread screen of [tabKey] is shown again only because one of its own child screens closed: the image
 * viewer or gallery of that thread, or the settings, help or toolbar editor opened from it (also through the viewer).
 *
 * The child screens are separate hosts, so the thread screen is created again when they close. That is not opening
 * the thread, so "スレッドを開いた時に更新" must not fetch it again (2026-10-13 全体検証 7.6; the viewer return has not
 * fetched since 08-29). The post screen is not one of them: coming back from it keeps refreshing as before.
 *
 * [previousHost] is the host shown just before, and [lastThreadTabKey] the tab the thread workspace showed last: a
 * thread opened from such a child screen (a deep link) is another tab and is opened as usual.
 */
internal fun compatThreadReturnsFromOwnChild(
    previousHost: CompatHost?,
    lastThreadTabKey: String?,
    tabKey: String
): Boolean {
    if (lastThreadTabKey != tabKey) return false
    var host: CompatHost = previousHost ?: return false
    // Settings / help / editor pages can be stacked; the depth is bounded by the hosts the user opened.
    repeat(MAX_CHILD_HOST_DEPTH) {
        host = when (val current = host) {
            is CompatHost.Viewer -> return current.tabKey == tabKey
            is CompatHost.Gallery -> return current.tabKey == tabKey
            is CompatHost.Settings -> current.origin
            is CompatHost.Help -> current.origin
            is CompatHost.ToolbarEditor -> current.origin
            // Reached only through one of the pages above: the page was opened from this thread.
            is CompatHost.ThreadWorkspace -> return previousHost !is CompatHost.ThreadWorkspace
            else -> return false
        }
    }
    return false
}

private const val MAX_CHILD_HOST_DEPTH = 32

/**
 * The host shown last and the tab the thread workspace showed last, kept outside snapshot state: they are read once
 * when the thread screen is created and must not recompose the app when they change.
 */
internal class CompatThreadReturnTracker {
    var lastHost: CompatHost? = null
    var lastThreadTabKey: String? = null

    fun returnsFromOwnChild(tabKey: String): Boolean =
        compatThreadReturnsFromOwnChild(lastHost, lastThreadTabKey, tabKey)
}
