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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CompatibilityDrawerReferenceTest {
    @Test
    fun drawerToolbarUsesFiveDistinctOldAndFinalApkResourcesInReferenceOrder() {
        assertEquals(
            listOf("tabs", "history", "watcher", "check_all", "settings"),
            compatReferenceDrawerToolbarKeys
        )
        val expected = listOf(
            Res.drawable.menu_ico_drawer_toolbar_tab,
            Res.drawable.menu_ico_drawer_toolbar_history,
            Res.drawable.menu_ico_drawer_toolbar_watcher,
            Res.drawable.menu_ico_drawer_toolbar_check,
            Res.drawable.menu_ico_drawer_toolbar_setting
        )
        val actual = compatReferenceDrawerToolbarKeys.map { key ->
            assertIs<CompatToolbarArtwork.Resource>(compatDrawerToolbarArtwork(key)).drawable
        }
        assertEquals(expected, actual)
        assertEquals(expected.size, actual.distinct().size)
        assertEquals(30, COMPAT_REFERENCE_DRAWER_TOOLBAR_ICON_DP)
        assertEquals(50, COMPAT_REFERENCE_DRAWER_THREAD_ROW_DP)
        assertEquals(40, COMPAT_REFERENCE_DRAWER_THREAD_THUMBNAIL_DP)
        assertEquals(60, COMPAT_REFERENCE_DRAWER_WATCHER_ROW_DP)
        assertEquals(50, COMPAT_REFERENCE_DRAWER_WATCHER_THUMBNAIL_DP)
    }

    @Test
    fun drawerReplyRowsShowReadCountAndOnlyPositiveProbeDelta() {
        assertEquals(
            CompatDrawerReplyPresentation(readCount = "100", increase = "+5"),
            compatDrawerReplyPresentation(readReplyCount = 100, latestReplyCount = 105)
        )
        assertEquals(
            CompatDrawerReplyPresentation(readCount = "100", increase = ""),
            compatDrawerReplyPresentation(readReplyCount = 100, latestReplyCount = 100)
        )
        assertEquals(
            CompatDrawerReplyPresentation(readCount = "", increase = "+5"),
            compatDrawerReplyPresentation(readReplyCount = 0, latestReplyCount = 5)
        )
    }

    @Test
    fun drawerHeadersPutWatcherProviderFailuresInTheHeaderOnly() {
        assertEquals("閲覧中のスレッド", compatDrawerHeaderTitle(CompatDrawerPage.TABS, CompatExternalWatcherSnapshot()))
        assertEquals("履歴", compatDrawerHeaderTitle(CompatDrawerPage.HISTORY, CompatExternalWatcherSnapshot()))
        assertEquals("巡回結果", compatDrawerHeaderTitle(CompatDrawerPage.WATCHER, CompatExternalWatcherSnapshot()))
        assertEquals(
            "にじろぐ(仮) 未インストール",
            compatDrawerHeaderTitle(
                CompatDrawerPage.WATCHER,
                CompatExternalWatcherSnapshot(message = "にじろぐ(仮) 未インストール")
            )
        )
        assertEquals(
            "巡回結果",
            compatDrawerHeaderTitle(
                CompatDrawerPage.WATCHER,
                CompatExternalWatcherSnapshot(
                    installed = true,
                    available = true,
                    message = "アプリ内バックグラウンド巡回の結果"
                )
            )
        )
    }

    @Test
    fun drawerRowsUseTheTwoReferenceJapaneseTimestampPatterns() {
        val utc = TimeZone.UTC
        val epoch = 1_720_368_960_000L // 2024-07-07 16:16 UTC
        assertEquals("7/7 16:16 虹裏", compatDrawerThreadSubtitle(epoch, "虹裏", utc))
        assertEquals("7月7日 16:16", formatCompatDrawerWatcherTimestamp(epoch, utc))
    }

    @Test
    fun everyDrawerDeleteCommandHonorsTheDialogLocalFavoriteProtection() {
        fun tab(
            key: String,
            favorite: Boolean = false,
            dead: Boolean = false
        ) = CompatTab(
            key = key,
            canonicalUrl = "https://may.2chan.net/b/res/$key.htm",
            originalUrl = "https://may.2chan.net/b/res/$key.htm",
            boardKey = "may-b",
            boardName = "may",
            threadNo = key,
            title = key,
            isDead = dead,
            favorite = favorite,
            insertedAtEpochMillis = key.toLong(),
            contentUpdatedAtEpochMillis = key.toLong()
        )

        val tabs = listOf(
            tab("400"),
            tab("300", favorite = true),
            tab("200", dead = true),
            tab("100", favorite = true, dead = true)
        )

        assertEquals(
            emptySet(),
            compatDrawerTabCloseKeys(tabs, "300", CompatDrawerTabCloseAction.SELECTED)
        )
        assertEquals(
            setOf("300"),
            compatDrawerTabCloseKeys(
                tabs,
                "300",
                CompatDrawerTabCloseAction.SELECTED,
                protectFavorites = false
            )
        )
        assertEquals(
            setOf("200"),
            compatDrawerTabCloseKeys(tabs, "300", CompatDrawerTabCloseAction.BELOW)
        )
        assertEquals(
            setOf("400", "200"),
            compatDrawerTabCloseKeys(tabs, "300", CompatDrawerTabCloseAction.OTHERS)
        )
        assertEquals(
            setOf("200"),
            compatDrawerTabCloseKeys(tabs, "300", CompatDrawerTabCloseAction.DEAD)
        )
        assertEquals(
            setOf("400", "200"),
            compatDrawerTabCloseKeys(tabs, "300", CompatDrawerTabCloseAction.ALL)
        )
        assertEquals(
            emptySet(),
            compatDrawerTabCloseKeys(tabs, "missing", CompatDrawerTabCloseAction.BELOW)
        )
        assertEquals(true, COMPAT_REFERENCE_DRAWER_PROTECT_FAVORITES_DEFAULT)
    }
}
