package com.valoser.futacha.shared.ui.compat

import kotlin.test.*

class CompatFv1820SettingsTest {
    private fun prefs(vararg pairs: Pair<String, String>) =
        pairs.associate { (key, value) -> compatPreferenceStorageKey("viewer", key) to value }

    @Test fun theViewerDefaultsAreFutabaViewer1820sAndAutoplayStaysOff() {
        val behaviour = compatViewerBehaviour(emptyMap(), autoPlayByDefault = false)
        assertTrue(behaviour.tapTogglesToolbar)
        assertTrue(behaviour.videoOptions.controlsFollowChrome)
        // 「自動で隠す」 OFF: the panel stays until a tap.
        assertEquals(0, behaviour.videoOptions.controlsAutoHideMillis)
        assertFalse(behaviour.videoOptions.autoPlay)
        // FutabaViewer plays a video over and over.
        assertTrue(behaviour.videoOptions.loop)
    }

    @Test fun futachasSharedViewerKeepsItsOwnPlayerUntilASwitchIsSaved() {
        val unsaved = compatViewerBehaviour(emptyMap(), reference = false, autoPlayByDefault = false)
        assertTrue(unsaved.tapTogglesToolbar)
        assertFalse(unsaved.videoOptions.loop)
        assertNull(unsaved.videoOptions.controlsAutoHideMillis)
        assertFalse(unsaved.videoOptions.controlsFollowChrome)
        assertFalse(unsaved.videoOptions.autoPlay)
        assertEquals(3_500, compatViewerBehaviour(prefs(COMPAT_VIEWER_VIDEO_AUTO_HIDE_KEY to "ON"), reference = false).videoOptions.controlsAutoHideMillis)
        assertEquals(0, compatViewerBehaviour(prefs(COMPAT_VIEWER_VIDEO_AUTO_HIDE_KEY to "OFF"), reference = false).videoOptions.controlsAutoHideMillis)
        assertTrue(compatViewerBehaviour(prefs(COMPAT_VIEWER_TAP_TOOLBAR_KEY to "ON"), reference = false).videoOptions.controlsFollowChrome)
        assertFalse(compatViewerBehaviour(prefs(COMPAT_VIEWER_TAP_TOOLBAR_KEY to "OFF"), reference = false).tapTogglesToolbar)
    }

    @Test fun anUnsavedAutoplayKeepsThePlatformsOwnAndASavedOneWins() {
        // The desktop's player starts by itself when nothing is saved, as it did before.
        assertTrue(compatViewerBehaviour(emptyMap(), autoPlayByDefault = true).videoOptions.autoPlay)
        assertFalse(compatViewerBehaviour(prefs(COMPAT_VIEWER_VIDEO_AUTO_PLAY_KEY to "OFF"), autoPlayByDefault = true).videoOptions.autoPlay)
        assertFalse(compatViewerBehaviour(emptyMap(), autoPlayByDefault = false).videoOptions.autoPlay)
    }

    @Test fun eachViewerSwitchChangesOnlyItsOwnBehaviour() {
        val off = compatViewerBehaviour(prefs(COMPAT_VIEWER_TAP_TOOLBAR_KEY to "OFF"))
        assertFalse(off.tapTogglesToolbar)
        assertFalse(off.videoOptions.controlsFollowChrome)
        val hide = compatViewerBehaviour(prefs(COMPAT_VIEWER_VIDEO_AUTO_HIDE_KEY to "ON"))
        assertEquals(3_500, hide.videoOptions.controlsAutoHideMillis)
        assertTrue(compatViewerBehaviour(prefs(COMPAT_VIEWER_VIDEO_AUTO_PLAY_KEY to "ON")).videoOptions.autoPlay)
    }

    @Test fun theViewerSettingsSitUnderBrowsingAfterThePreloadAsSwitches() {
        val browsing = compatSettingsGroups("viewer").first { it.first == "閲覧" }.second
        assertEquals(
            listOf("viewerPreloadMode", COMPAT_VIEWER_TAP_TOOLBAR_KEY, COMPAT_VIEWER_VIDEO_AUTO_HIDE_KEY, COMPAT_VIEWER_VIDEO_AUTO_PLAY_KEY),
            browsing.map { it.preferenceKey }
        )
        browsing.drop(1).forEach { assertTrue(compatIsBooleanPreference("viewer", it), it.title) }
        assertEquals(listOf("ON", "OFF", "OFF"), browsing.drop(1).map { it.summary })
    }

    @Test fun theReplyMenuSizeFollows1820AndNeverLeavesTheScreen() {
        assertEquals(CompatPostMenuSize(300f, 50f, 1f), compatPostMenuSize(100, 400f))
        val big = compatPostMenuSize(200, 800f)
        assertEquals(450f, big.widthDp)
        assertEquals(100f, big.cellHeightDp)
        assertTrue(big.fontScale > 1f)
        // A 360dp phone: 16dp each side.
        assertEquals(328f, compatPostMenuSize(200, 360f).widthDp)
        assertEquals(87.5f, compatPostMenuSize(175, 360f).cellHeightDp)
    }

    @Test fun theReplyMenuSizeIsStoredLike1820AndShownAsItsLabel() {
        val entry = compatSettingsGroups("thread").flatMap { it.second }.first { it.preferenceKey == COMPAT_THREAD_CONTEXT_MENU_SCALE_KEY }
        assertEquals(COMPAT_THREAD_CONTEXT_MENU_SCALE_OPTIONS, compatPreferenceOptions("thread", entry))
        assertEquals("175", compatPreferenceStoredValue(COMPAT_THREAD_CONTEXT_MENU_SCALE_KEY, "175%"))
        assertEquals("100", compatPreferenceStoredValue(COMPAT_THREAD_CONTEXT_MENU_SCALE_KEY, "標準"))
        assertEquals("150%", compatPreferenceDisplayValue(COMPAT_THREAD_CONTEXT_MENU_SCALE_KEY, "150"))
        assertEquals("標準", compatPreferenceDisplayValue(COMPAT_THREAD_CONTEXT_MENU_SCALE_KEY, "junk"))
        val display = compatSettingsGroups("thread").first { it.first == "画面表示" }.second.map { it.preferenceKey }
        assertEquals(COMPAT_THREAD_CONTEXT_MENU_SCALE_KEY, display.last())
    }
}
