package com.valoser.futacha.shared.ui.futaber

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F-B1: the other modes' entries start on (35 switches); the ones that change what futaber does stay off. Every switch can be turned off. */
class FutaberDefaultsTest {
    private val start = FutaberDisplaySettings.from(emptyMap())

    @Test fun theEntriesThatOnlyAddADoorStartOn() {
        val on = listOf(
            start.extExtract, start.extAutoScroll, start.extScrollMenu, start.extArchiveMenu, start.extNgDetail, start.extPostDetailOps,
            start.extImageMenu, start.extIdTap, start.extSearchNav, start.extReadAloudPanel, start.extOpenSummary, start.extReportNg,
            start.extCatalogLongPress, start.extCatalogModes, start.extCatalogWatchHighlight, start.extReplyPriority, start.extCatalogHistory,
            start.extCatalogTools, start.extCatalogFineTune, start.extMailPresets, start.extPostTools, start.extDraftAttachment,
            start.extHistory, start.extHistoryArchive, start.extSavedDetail, start.extTabStatus, start.extTabs, start.extTabsCheck,
            start.extOtherFavorites, start.extForegroundPatrol, start.extExternalWatcher, start.extMediaOnlySave, start.extAutoSaveView,
            start.extCustomFont, start.extGalleryColumns, start.extVideo
        )
        assertEquals(36, on.size)
        assertTrue(on.all { it })
    }

    @Test fun theOnesThatChangeWhatFutaberDoesStayOff() {
        assertFalse(start.extTree)
        assertFalse(start.extQuickSaidane)
        assertFalse(start.extQuickNg)
        assertFalse(start.extVolumeKeys)
        assertFalse(start.extSortByBoard)
        assertFalse(start.postDestinationConfirm)
        assertFalse(start.extGallerySelectSave)
        assertFalse(start.extTabsManage)
    }

    @Test fun anExplicitOffIsKeptAndEverySwitchTurnsOff() {
        val keys = listOf(
            "extract", "autoScroll", "scrollMenu", "archiveMenu", "ngDetail", "postDetailOps", "imageMenu", "idTap", "searchNav",
            "readAloudPanel", "openSummary", "reportNg", "catalogLongPress", "catalogModes", "catalogWatchHighlight", "replyPriority",
            "catalogHistory", "catalogTools", "catalogFineTune", "mailPresets", "postTools", "draftAttachment", "history", "historyArchive",
            "savedDetail", "tabStatus", "tabs", "tabsCheck", "otherFavorites", "foregroundPatrol", "externalWatcher", "mediaOnlySave",
            "autoSaveView", "customFont", "galleryColumns", "video"
        )
        val off = FutaberDisplaySettings.from(keys.associate { "compat.futaber.ext.$it" to "OFF" })
        assertFalse(off.extExtract || off.extAutoScroll || off.extScrollMenu || off.extArchiveMenu || off.extNgDetail || off.extPostDetailOps)
        assertFalse(off.extImageMenu || off.extIdTap || off.extSearchNav || off.extReadAloudPanel || off.extOpenSummary || off.extReportNg)
        assertFalse(off.extCatalogLongPress || off.extCatalogModes || off.extCatalogWatchHighlight || off.extReplyPriority)
        assertFalse(off.extCatalogHistory || off.extCatalogTools || off.extCatalogFineTune || off.extMailPresets || off.extPostTools)
        assertFalse(off.extDraftAttachment || off.extHistory || off.extHistoryArchive || off.extSavedDetail || off.extTabStatus || off.extTabs)
        assertFalse(off.extTabsCheck || off.extOtherFavorites || off.extForegroundPatrol || off.extExternalWatcher || off.extMediaOnlySave)
        assertFalse(off.extAutoSaveView || off.extCustomFont || off.extGalleryColumns || off.extVideo)
    }

    @Test fun theSharedColumnsApplyOnlyWhenSomeWereSaved() {
        assertEquals(null, futaberSavedSharedGalleryColumns(emptyMap()))
        val saved = futaberSavedSharedGalleryColumns(mapOf("compat.viewer.galleryGridViewPortraitClmNum" to "4"))
        assertEquals(4 to FUTABER_GALLERY_DEFAULT_COLUMNS, saved)
    }

    @Test fun tabsCheckIsSplitOffFromKeepingFavouriteTabs() {
        assertEquals("compat.futaber.ext.tabsCheck", FutaberSettingKeys.EXT_TABS_CHECK)
        val onlyKeep = FutaberDisplaySettings.from(mapOf(FutaberSettingKeys.EXT_TABS_MANAGE to "ON", FutaberSettingKeys.EXT_TABS_CHECK to "OFF"))
        assertTrue(onlyKeep.extTabsManage); assertFalse(onlyKeep.extTabsCheck)
    }
}
