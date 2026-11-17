@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package com.valoser.futacha

import com.valoser.futacha.shared.ui.futaber.FutaberDisplaySettings

/**
 * F-B1 turned the other modes' entries on from the start. A test about what an entry does when it is off (or about the
 * original's own screen) starts from this: every one of those switches saved as OFF, so what it turns on it turns on itself.
 */
val FUTABER_EXTENSIONS_OFF: Map<String, String> = listOf("extract", "idTap", "autoScroll", "history", "mailPresets", "tabs", "archiveMenu", "savedDetail", "otherFavorites", "scrollMenu", "searchNav", "catalogLongPress", "postDetailOps", "galleryColumns", "imageMenu", "postTools", "draftAttachment", "historyArchive", "foregroundPatrol", "externalWatcher", "mediaOnlySave", "customFont", "openSummary", "tabStatus", "reportNg", "catalogModes", "catalogWatchHighlight", "replyPriority", "catalogHistory", "catalogTools", "ngDetail", "readAloudPanel", "catalogFineTune", "autoSaveView", "tabsCheck", "video").associate { "compat.futaber.ext.$it" to "OFF" } + ("compat.futaber.foldExtras" to "OFF")

/** The same, for a test that builds the settings object itself. */
internal fun FutaberDisplaySettings.extensionsOff(): FutaberDisplaySettings = copy(
    extExtract = false,
    extIdTap = false,
    extAutoScroll = false,
    extHistory = false,
    extMailPresets = false,
    extTabs = false,
    extArchiveMenu = false,
    extSavedDetail = false,
    extOtherFavorites = false,
    extScrollMenu = false,
    extSearchNav = false,
    extCatalogLongPress = false,
    extPostDetailOps = false,
    extGalleryColumns = false,
    extImageMenu = false,
    extPostTools = false,
    extDraftAttachment = false,
    extHistoryArchive = false,
    extForegroundPatrol = false,
    extExternalWatcher = false,
    extMediaOnlySave = false,
    extCustomFont = false,
    extOpenSummary = false,
    extTabStatus = false,
    extReportNg = false,
    extCatalogModes = false,
    extCatalogWatchHighlight = false,
    extReplyPriority = false,
    extCatalogHistory = false,
    extCatalogTools = false,
    extNgDetail = false,
    extReadAloudPanel = false,
    extCatalogFineTune = false,
    extAutoSaveView = false,
    extTabsCheck = false,
    extVideo = false
)
