package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatDrawerPage
import com.valoser.futacha.shared.compat.CompatTab
import futacha.shared.generated.resources.Res
import futacha.shared.generated.resources.menu_ico_drawer_toolbar_check
import futacha.shared.generated.resources.menu_ico_drawer_toolbar_history
import futacha.shared.generated.resources.menu_ico_drawer_toolbar_setting
import futacha.shared.generated.resources.menu_ico_drawer_toolbar_tab
import futacha.shared.generated.resources.menu_ico_drawer_toolbar_watcher
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

internal val compatReferenceDrawerToolbarKeys = listOf(
    "tabs",
    "history",
    "watcher",
    "check_all",
    "settings"
)

internal const val COMPAT_REFERENCE_DRAWER_TOOLBAR_ICON_DP = 30
internal const val COMPAT_REFERENCE_DRAWER_THREAD_ROW_DP = 50
internal const val COMPAT_REFERENCE_DRAWER_THREAD_THUMBNAIL_DP = 40
internal const val COMPAT_REFERENCE_DRAWER_WATCHER_ROW_DP = 60
internal const val COMPAT_REFERENCE_DRAWER_WATCHER_THUMBNAIL_DP = 50
internal const val COMPAT_REFERENCE_DRAWER_PROTECT_FAVORITES_DEFAULT = true

internal enum class CompatDrawerTabCloseAction {
    SELECTED,
    BELOW,
    OTHERS,
    DEAD,
    ALL
}

/**
 * DrawerTabFragment applies the protect checkbox to every deletion command,
 * including the single selected row. The checkbox is dialog-local and starts
 * checked every time the reference dialog is created.
 */
internal fun compatDrawerTabCloseKeys(
    tabs: List<CompatTab>,
    selectedKey: String,
    action: CompatDrawerTabCloseAction,
    protectFavorites: Boolean = COMPAT_REFERENCE_DRAWER_PROTECT_FAVORITES_DEFAULT
): Set<String> {
    val uniqueTabs = tabs.distinctBy(CompatTab::key)
    val selectedIndex = uniqueTabs.indexOfFirst { it.key == selectedKey }
    val candidates = when (action) {
        CompatDrawerTabCloseAction.SELECTED -> uniqueTabs.getOrNull(selectedIndex)?.let(::listOf).orEmpty()
        CompatDrawerTabCloseAction.BELOW -> if (selectedIndex < 0) emptyList() else uniqueTabs.drop(selectedIndex + 1)
        CompatDrawerTabCloseAction.OTHERS -> uniqueTabs.filterNot { it.key == selectedKey }
        CompatDrawerTabCloseAction.DEAD -> uniqueTabs.filter(CompatTab::isDead)
        CompatDrawerTabCloseAction.ALL -> uniqueTabs
    }
    return candidates
        .filterNot { protectFavorites && it.favorite }
        .mapTo(linkedSetOf(), CompatTab::key)
}

/** Exact 1.apk/old.apk artwork for DrawerMultiFragment.setupToolbar(). */
internal fun compatDrawerToolbarArtwork(key: String): CompatToolbarArtwork =
    CompatToolbarArtwork.Resource(
        when (key) {
            "tabs" -> Res.drawable.menu_ico_drawer_toolbar_tab
            "history" -> Res.drawable.menu_ico_drawer_toolbar_history
            "watcher" -> Res.drawable.menu_ico_drawer_toolbar_watcher
            "check_all" -> Res.drawable.menu_ico_drawer_toolbar_check
            "settings" -> Res.drawable.menu_ico_drawer_toolbar_setting
            else -> error("Unknown reference drawer toolbar key: $key")
        }
    )

internal fun compatDrawerHeaderTitle(
    page: CompatDrawerPage,
    watcher: CompatExternalWatcherSnapshot
): String = when (page) {
    CompatDrawerPage.TABS -> "閲覧中のスレッド"
    CompatDrawerPage.HISTORY -> "履歴"
    CompatDrawerPage.WATCHER -> watcher.message
        ?.takeIf { !watcher.available && it.isNotBlank() }
        ?: "巡回結果"
}

internal data class CompatDrawerReplyPresentation(
    val readCount: String,
    val increase: String
)

/**
 * The reference stores the last body count in intRes and a newer catalog
 * probe in intResCheck.  Its rows show the former on top and only a positive
 * `+N` delta below it.
 */
internal fun compatDrawerReplyPresentation(
    readReplyCount: Int,
    latestReplyCount: Int
): CompatDrawerReplyPresentation {
    val read = readReplyCount.coerceAtLeast(0)
    val latest = latestReplyCount.coerceAtLeast(read)
    return CompatDrawerReplyPresentation(
        readCount = read.takeIf { it > 0 }?.toString().orEmpty(),
        increase = (latest - read).takeIf { it > 0 }?.let { "+$it" }.orEmpty()
    )
}

internal fun formatCompatDrawerThreadTimestamp(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val local = Instant.fromEpochMilliseconds(epochMillis.coerceAtLeast(0L))
        .toLocalDateTime(timeZone)
    return "${local.month.ordinal + 1}/${local.day} " +
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

internal fun formatCompatDrawerWatcherTimestamp(
    epochMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String {
    val local = Instant.fromEpochMilliseconds(epochMillis.coerceAtLeast(0L))
        .toLocalDateTime(timeZone)
    return "${local.month.ordinal + 1}月${local.day}日 " +
        "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

internal fun compatDrawerThreadSubtitle(
    epochMillis: Long,
    boardName: String,
    timeZone: TimeZone = TimeZone.currentSystemDefault()
): String = "${formatCompatDrawerThreadTimestamp(epochMillis, timeZone)} $boardName"
