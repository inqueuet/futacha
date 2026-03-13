package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.model.CatalogNavEntryId
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.CatalogNavEntryPlacement
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryId
import com.valoser.futacha.shared.model.ThreadMenuEntryPlacement
import com.valoser.futacha.shared.model.defaultCatalogNavEntries
import com.valoser.futacha.shared.model.defaultThreadMenuEntries
import com.valoser.futacha.shared.repository.SavedThreadRepository
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GlobalSettingsSupportTest {
    @Test
    fun resolvePreferredFileManagerSummaryState_formatsConfiguredAndDefaultStates() {
        assertEquals(
            PreferredFileManagerSummaryState(
                currentSettingText = "未設定(システムのデフォルト)",
                isConfigured = false
            ),
            resolvePreferredFileManagerSummaryState(null)
        )
        assertEquals(
            PreferredFileManagerSummaryState(
                currentSettingText = "現在の設定: Solid Explorer",
                isConfigured = true
            ),
            resolvePreferredFileManagerSummaryState(" Solid Explorer ")
        )
    }

    @Test
    fun resolveSaveDirectoryPickerState_reflectsPlatformAndLauncherAvailability() {
        assertEquals(
            SaveDirectoryPickerState(
                descriptionText = "AndroidではSAFで選ぶ方法を推奨します。手入力も利用できます。",
                warningText = "※ SAF のフォルダー選択 (OPEN_DOCUMENT_TREE) に非対応のファイラーでは選択できません。その場合は標準ファイラーを使うか手入力を選んでください。",
                isPickerButtonEnabled = true,
                showManualInputFallbackButton = false
            ),
            resolveSaveDirectoryPickerState(
                isAndroidPlatform = true,
                hasPickerLauncher = true
            )
        )
        assertEquals(
            SaveDirectoryPickerState(
                descriptionText = "ファイラーで選んだディレクトリを保存先に使います。パスが取得できない場合は手入力に切り替えてください。",
                warningText = "※ SAF のフォルダー選択 (OPEN_DOCUMENT_TREE) に非対応のファイラーでは選択できません。その場合は標準ファイラーを使うか手入力を選んでください。",
                isPickerButtonEnabled = false,
                showManualInputFallbackButton = true
            ),
            resolveSaveDirectoryPickerState(
                isAndroidPlatform = false,
                hasPickerLauncher = false
            )
        )
    }

    @Test
    fun cookieSettingsEntryVisibility_reflectsCookieManagerAvailability() {
        assertTrue(shouldShowCookieSettingsEntry(hasCookieManager = true))
        assertFalse(shouldShowCookieSettingsEntry(hasCookieManager = false))
    }

    @Test
    fun resolveGlobalSettingsEntrySelection_routesCookieManagerAndExternalLinks() {
        assertEquals(
            GlobalSettingsEntrySelectionState(
                shouldOpenCookieManager = true,
                externalUrl = null,
                shouldCloseScreen = true
            ),
            resolveGlobalSettingsEntrySelection(GlobalSettingsAction.Cookies)
        )
        assertEquals(
            GlobalSettingsEntrySelectionState(
                shouldOpenCookieManager = false,
                externalUrl = "https://github.com/inqueuet/futacha",
                shouldCloseScreen = true
            ),
            resolveGlobalSettingsEntrySelection(GlobalSettingsAction.Developer)
        )
    }

    @Test
    fun resolveGlobalSettingsStorageSummaryState_marksWarningAtThreshold() {
        val normal = resolveGlobalSettingsStorageSummaryState(
            historyCount = 49,
            autoSavedCount = 2,
            autoSavedSize = 1_572_864L
        )
        assertEquals("履歴: 49件", normal.historyText)
        assertEquals("自動保存: 2件 / 1.5 MB", normal.autoSavedText)
        assertFalse(normal.isHistoryWarning)
        assertNull(normal.warningText)

        val warning = resolveGlobalSettingsStorageSummaryState(
            historyCount = 50,
            autoSavedCount = null,
            autoSavedSize = null
        )
        assertTrue(warning.isHistoryWarning)
        assertEquals("履歴: 50件", warning.historyText)
        assertEquals("自動保存: 0件 / 不明", warning.autoSavedText)
        assertTrue(warning.warningText?.contains("更新に時間がかかることがあります") == true)
    }

    @Test
    fun buildGlobalSettingsCacheCleanupMessage_returnsTargetSpecificMessages() {
        assertEquals(
            "画像キャッシュを削除しました",
            buildGlobalSettingsCacheCleanupMessage(
                target = GlobalSettingsCacheCleanupTarget.IMAGE_CACHE,
                result = Result.success(Unit)
            )
        )
        assertEquals(
            "一時キャッシュを削除しました",
            buildGlobalSettingsCacheCleanupMessage(
                target = GlobalSettingsCacheCleanupTarget.TEMPORARY_CACHE,
                result = Result.success(Unit)
            )
        )
        assertEquals(
            "削除に失敗しました: permission denied",
            buildGlobalSettingsCacheCleanupMessage(
                target = GlobalSettingsCacheCleanupTarget.IMAGE_CACHE,
                result = Result.failure(IllegalStateException("permission denied"))
            )
        )
    }

    @Test
    fun moveCatalogMenuEntry_reordersOnlyBarEntries() {
        val moved = moveCatalogMenuEntry(
            entries = defaultCatalogNavEntries(),
            id = CatalogNavEntryId.RefreshCatalog,
            delta = -1
        )
        val state = resolveCatalogMenuConfigState(moved)
        assertEquals(
            listOf(
                CatalogNavEntryId.CreateThread,
                CatalogNavEntryId.RefreshCatalog,
                CatalogNavEntryId.ScrollToTop
            ),
            state.barEntries.take(3).map { it.id }
        )
        assertEquals(1, state.hiddenEntries.size)
        assertEquals(CatalogNavEntryId.PastThreadSearch, state.hiddenEntries.single().id)
    }

    @Test
    fun setCatalogMenuEntryPlacement_keepsAtLeastOneBarEntry() {
        val hiddenAll = defaultCatalogNavEntries().map {
            it.copy(placement = CatalogNavEntryPlacement.HIDDEN)
        }
        val state = resolveCatalogMenuConfigState(hiddenAll)
        assertTrue(state.barEntries.isNotEmpty())
        assertEquals(CatalogNavEntryId.Settings, state.barEntries.single().id)
    }

    @Test
    fun moveThreadMenuEntryWithinPlacement_reordersWithinBar() {
        val moved = moveThreadMenuEntryWithinPlacement(
            entries = defaultThreadMenuEntries(),
            id = ThreadMenuEntryId.Save,
            delta = -1,
            placement = ThreadMenuEntryPlacement.BAR
        )
        val state = resolveThreadMenuConfigState(moved)
        assertEquals(
            listOf(
                ThreadMenuEntryId.Refresh,
                ThreadMenuEntryId.Save,
                ThreadMenuEntryId.Gallery
            ),
            state.barEntries.drop(3).take(3).map { it.id }
        )
    }

    @Test
    fun setThreadMenuEntryPlacement_keepsSettingsInBarWhenSheetExists() {
        val hiddenSettings = setThreadMenuEntryPlacement(
            entries = defaultThreadMenuEntries(),
            id = ThreadMenuEntryId.Settings,
            placement = ThreadMenuEntryPlacement.HIDDEN
        )
        val state = resolveThreadMenuConfigState(hiddenSettings)
        assertTrue(state.sheetEntries.isNotEmpty())
        assertTrue(state.barEntries.any { it.id == ThreadMenuEntryId.Settings })
    }

    @Test
    fun globalSettingsBindingsSupport_catalogMenuCallbacks_updateEntries() {
        var localEntries: List<CatalogNavEntryConfig> = defaultCatalogNavEntries()
        var persistedEntries = emptyList<CatalogNavEntryConfig>()
        val callbacks = buildGlobalSettingsCatalogMenuCallbacks(
            currentEntries = { localEntries },
            setLocalEntries = { localEntries = it },
            onCatalogNavEntriesChanged = { persistedEntries = it }
        )

        callbacks.moveEntry(CatalogNavEntryId.RefreshCatalog, -1)
        assertEquals(localEntries, persistedEntries)
        assertEquals(
            listOf(
                CatalogNavEntryId.CreateThread,
                CatalogNavEntryId.RefreshCatalog,
                CatalogNavEntryId.ScrollToTop
            ),
            resolveCatalogMenuConfigState(localEntries).barEntries.take(3).map { it.id }
        )

        callbacks.setPlacement(CatalogNavEntryId.PastThreadSearch, CatalogNavEntryPlacement.BAR)
        assertTrue(resolveCatalogMenuConfigState(localEntries).hiddenEntries.any { it.id == CatalogNavEntryId.PastThreadSearch })

        callbacks.resetEntries()
        assertEquals(resolveCatalogMenuConfigState(defaultCatalogNavEntries()).allEntries, localEntries)
    }

    @Test
    fun globalSettingsBindingsSupport_threadMenuCallbacks_updateEntries() {
        var localEntries: List<ThreadMenuEntryConfig> = defaultThreadMenuEntries()
        var persistedEntries = emptyList<ThreadMenuEntryConfig>()
        val callbacks = buildGlobalSettingsThreadMenuCallbacks(
            currentEntries = { localEntries },
            setLocalEntries = { localEntries = it },
            onThreadMenuEntriesChanged = { persistedEntries = it }
        )

        callbacks.moveWithinPlacement(ThreadMenuEntryId.Save, -1, ThreadMenuEntryPlacement.BAR)
        assertEquals(localEntries, persistedEntries)
        assertEquals(
            listOf(
                ThreadMenuEntryId.Refresh,
                ThreadMenuEntryId.Save,
                ThreadMenuEntryId.Gallery
            ),
            resolveThreadMenuConfigState(localEntries).barEntries.drop(3).take(3).map { it.id }
        )

        callbacks.setPlacement(ThreadMenuEntryId.Settings, ThreadMenuEntryPlacement.SHEET)
        assertTrue(resolveThreadMenuConfigState(localEntries).barEntries.any { it.id == ThreadMenuEntryId.Settings })

        callbacks.resetEntries()
        assertEquals(resolveThreadMenuConfigState(defaultThreadMenuEntries()).allEntries, localEntries)
    }

    @Test
    fun globalSettingsBindingsSupport_linkCallbacks_routeCookieAndExternalActions() {
        var openedCookieManager = false
        var openedUrl: String? = null
        var dismissed = false
        val callbacks = buildGlobalSettingsLinkCallbacks(
            onOpenCookieManager = { openedCookieManager = true },
            urlLauncher = { openedUrl = it },
            onBack = { dismissed = true }
        )

        callbacks.onEntrySelected(GlobalSettingsAction.Cookies)
        assertTrue(openedCookieManager)
        assertNull(openedUrl)
        assertTrue(dismissed)

        openedCookieManager = false
        openedUrl = null
        dismissed = false
        callbacks.onEntrySelected(GlobalSettingsAction.Developer)
        assertFalse(openedCookieManager)
        assertEquals("https://github.com/inqueuet/futacha", openedUrl)
        assertTrue(dismissed)
    }

    @Test
    fun globalSettingsBindingsSupport_cacheCallbacks_runActionsAndReportMessages() = runBlocking {
        val messages = mutableListOf<String>()
        var imageCleared = false
        var tempCleared = false
        var refreshed = false
        val callbacks = buildGlobalSettingsCacheCallbacks(
            coroutineScope = this,
            showSnackbar = { messages += it },
            clearImageCache = { imageCleared = true },
            clearTemporaryCache = { tempCleared = true },
            refreshAutoSavedStats = { refreshed = true }
        )

        callbacks.clearImageCache()
        callbacks.clearTemporaryCache()
        callbacks.refreshStorageStats()
        yield()
        yield()

        assertTrue(imageCleared)
        assertTrue(tempCleared)
        assertTrue(refreshed)
        assertEquals(
            listOf("画像キャッシュを削除しました", "一時キャッシュを削除しました"),
            messages
        )
    }

    @Test
    fun globalSettingsBindingsSupport_saveCallbacks_updatePickerAndManualPath() {
        var manualSaveInput = "/tmp/raw"
        var isFileManagerPickerVisible = false
        var changedDirectory: String? = null
        var changedSelection: SaveDirectorySelection? = null
        var selectedFileManager: Pair<String, String>? = null
        val callbacks = buildGlobalSettingsSaveCallbacks(
            currentManualSaveInput = { manualSaveInput },
            setManualSaveInput = { manualSaveInput = it },
            setIsFileManagerPickerVisible = { isFileManagerPickerVisible = it },
            onManualSaveDirectoryChanged = { changedDirectory = it },
            onSaveDirectorySelectionChanged = { changedSelection = it },
            onFileManagerSelected = { packageName, label ->
                selectedFileManager = packageName to label
            }
        )

        callbacks.onOpenFileManagerPicker()
        assertTrue(isFileManagerPickerVisible)

        callbacks.onDismissFileManagerPicker()
        assertFalse(isFileManagerPickerVisible)

        callbacks.onFileManagerSelected("pkg", "Files")
        assertFalse(isFileManagerPickerVisible)
        assertEquals("pkg" to "Files", selectedFileManager)

        callbacks.onManualSaveInputChanged("./saved_threads")
        assertEquals("./saved_threads", manualSaveInput)

        callbacks.onUpdateManualSaveDirectory()
        assertEquals("./saved_threads", manualSaveInput)
        assertEquals("./saved_threads", changedDirectory)

        manualSaveInput = "/custom"
        callbacks.onResetManualSaveDirectory()
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, manualSaveInput)
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, changedDirectory)

        callbacks.onFallbackToManualInput()
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, manualSaveInput)
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, changedDirectory)
        assertEquals(SaveDirectorySelection.MANUAL_INPUT, changedSelection)
    }

    @Test
    fun globalSettingsBindingsSupport_interactionBundle_forwardsCallbacks() {
        val saveCallbacks = buildGlobalSettingsSaveCallbacks(
            currentManualSaveInput = { "" },
            setManualSaveInput = {},
            setIsFileManagerPickerVisible = {},
            onManualSaveDirectoryChanged = {},
            onSaveDirectorySelectionChanged = {},
            onFileManagerSelected = null
        )
        val catalogCallbacks = buildGlobalSettingsCatalogMenuCallbacks(
            currentEntries = { defaultCatalogNavEntries() },
            setLocalEntries = {},
            onCatalogNavEntriesChanged = {}
        )
        val threadCallbacks = buildGlobalSettingsThreadMenuCallbacks(
            currentEntries = { defaultThreadMenuEntries() },
            setLocalEntries = {},
            onThreadMenuEntriesChanged = {}
        )
        val linkCallbacks = buildGlobalSettingsLinkCallbacks(
            onOpenCookieManager = null,
            urlLauncher = {},
            onBack = {}
        )
        val cacheCallbacks = buildGlobalSettingsCacheCallbacks(
            coroutineScope = CoroutineScope(Dispatchers.Unconfined),
            showSnackbar = {},
            clearImageCache = {},
            clearTemporaryCache = {},
            refreshAutoSavedStats = {}
        )

        val bundle = buildGlobalSettingsInteractionBindingsBundle(
            saveCallbacks = saveCallbacks,
            catalogMenuCallbacks = catalogCallbacks,
            threadMenuCallbacks = threadCallbacks,
            linkCallbacks = linkCallbacks,
            cacheCallbacks = cacheCallbacks
        )

        assertTrue(bundle.saveCallbacks === saveCallbacks)
        assertTrue(bundle.catalogMenuCallbacks === catalogCallbacks)
        assertTrue(bundle.threadMenuCallbacks === threadCallbacks)
        assertTrue(bundle.linkCallbacks === linkCallbacks)
        assertTrue(bundle.cacheCallbacks === cacheCallbacks)
    }

    @Test
    fun globalSettingsDerivedSupport_buildsExpectedDerivedState() {
        val state = GlobalSettingsDerivedState(
            resolvedManualPath = resolveFallbackManualSavePathValue("Documents"),
            saveDestinationModeLabel = buildSaveDestinationModeLabelValue(
                SaveDirectorySelection.MANUAL_INPUT,
                isAndroidPlatform = true
            ),
            saveDestinationHint = buildSaveDestinationHintValue(
                SaveDirectorySelection.MANUAL_INPUT,
                isAndroidPlatform = true
            ),
            settingsEntries = buildList {
                if (shouldShowCookieSettingsEntry(true)) add(cookieSettingsEntry)
                addAll(globalSettingsEntries)
            },
            preferredFileManagerState = resolvePreferredFileManagerSummaryState("Files"),
            saveDirectoryPickerState = resolveSaveDirectoryPickerState(
                isAndroidPlatform = true,
                hasPickerLauncher = true
            ),
            storageSummaryState = resolveGlobalSettingsStorageSummaryState(
                historyCount = 3,
                autoSavedCount = 1,
                autoSavedSize = 1024L
            )
        )

        assertEquals("Documents/futacha/saved_threads", state.resolvedManualPath)
        assertEquals("手入力の保存先", state.saveDestinationModeLabel)
        assertTrue(state.settingsEntries.any { it.action == GlobalSettingsAction.Cookies })
        assertTrue(state.preferredFileManagerState.isConfigured)
        assertTrue(state.saveDirectoryPickerState.isPickerButtonEnabled)
        assertEquals("履歴: 3件", state.storageSummaryState.historyText)
    }

    @Test
    fun globalSettingsRuntimeSupport_resolvesAutoSavedStatsUpdate() {
        assertEquals(
            GlobalSettingsAutoSavedStatsUpdate(
                autoSavedCount = null,
                autoSavedSize = null,
                shouldApply = true
            ),
            resolveGlobalSettingsAutoSavedStatsUpdate(
                hasRepository = false,
                stats = null
            )
        )
        assertEquals(
            GlobalSettingsAutoSavedStatsUpdate(
                autoSavedCount = 2,
                autoSavedSize = 4096L,
                shouldApply = true
            ),
            resolveGlobalSettingsAutoSavedStatsUpdate(
                hasRepository = true,
                stats = SavedThreadRepository.SavedThreadStats(
                    threadCount = 2,
                    totalSize = 4096L
                )
            )
        )
        assertEquals(
            GlobalSettingsAutoSavedStatsUpdate(
                autoSavedCount = null,
                autoSavedSize = null,
                shouldApply = false
            ),
            resolveGlobalSettingsAutoSavedStatsUpdate(
                hasRepository = true,
                stats = null
            )
        )
    }
}
