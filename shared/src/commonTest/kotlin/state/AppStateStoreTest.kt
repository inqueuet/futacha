package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppStateStoreTest {
    @Test
    fun seedIfEmpty_populatesDefaultsWithoutOverwritingExistingValues() = runBlocking {
        val storage = FakePlatformStateStorage().apply {
            boardsState.value = """[{"id":"existing","name":"Existing","category":"cat","url":"https://may.2chan.net/existing/","description":"desc","pinned":false}]"""
            manualSaveDirectoryState.value = "/custom/save"
        }
        val store = AppStateStore(storage)

        store.seedIfEmpty(
            defaultBoards = listOf(
                BoardSummary(
                    id = "b",
                    name = "Board",
                    category = "cat",
                    url = "https://may.2chan.net/b/",
                    description = "desc"
                )
            ),
            defaultHistory = listOf(historyEntry(threadId = "123")),
            defaultLastUsedDeleteKey = "123456789"
        )

        assertEquals("existing", store.boards.first().single().id)
        assertEquals("/custom/save", store.manualSaveDirectory.first())
        assertEquals("12345678", store.lastUsedDeleteKey.first())
        assertEquals(1, store.history.first().size)
    }

    @Test
    fun setBoards_recordsStorageErrorWhenPersistenceFails() = runBlocking {
        val storage = FakePlatformStateStorage().apply {
            failBoardsUpdate = true
        }
        val store = AppStateStore(storage)

        store.setBoards(
            listOf(
                BoardSummary(
                    id = "b",
                    name = "Board",
                    category = "cat",
                    url = "https://may.2chan.net/b/",
                    description = "desc"
                )
            )
        )

        assertEquals(emptyList(), store.boards.first())
        assertEquals("setBoards", store.lastStorageError.value?.operation)
    }

    @Test
    fun setHistory_rollsBackCachedStateWhenPersistenceFails() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)
        val initial = listOf(historyEntry(threadId = "111"))
        val updated = listOf(historyEntry(threadId = "222"))

        store.setHistory(initial)
        storage.failHistoryUpdate = true

        assertFailsWith<IllegalStateException> {
            store.setHistory(updated)
        }

        assertEquals(initial, store.history.first())
        assertNull(store.lastStorageError.value?.takeIf { it.operation != "setHistory" })
        assertEquals("setHistory", store.lastStorageError.value?.operation)
    }
}

private fun historyEntry(threadId: String): ThreadHistoryEntry {
    return ThreadHistoryEntry(
        threadId = threadId,
        boardId = "b",
        title = "title-$threadId",
        titleImageUrl = "thumb-$threadId",
        boardName = "board",
        boardUrl = "https://may.2chan.net/b/futaba.php",
        lastVisitedEpochMillis = 100L,
        replyCount = 1
    )
}

internal class FakePlatformStateStorage : PlatformStateStorage {
    val boardsState = MutableStateFlow<String?>(null)
    val historyState = MutableStateFlow<String?>(null)
    val privacyFilterState = MutableStateFlow(false)
    val backgroundRefreshState = MutableStateFlow(false)
    val lightweightModeState = MutableStateFlow(false)
    val manualSaveDirectoryState = MutableStateFlow(DEFAULT_MANUAL_SAVE_ROOT)
    val attachmentPickerPreferenceState = MutableStateFlow<String?>(null)
    val saveDirectorySelectionState = MutableStateFlow<String?>(null)
    val lastUsedDeleteKeyState = MutableStateFlow<String?>(null)
    val catalogModeMapState = MutableStateFlow<String?>(null)
    val catalogDisplayStyleState = MutableStateFlow<String?>(null)
    val catalogGridColumnsState = MutableStateFlow<String?>(null)
    val ngHeadersState = MutableStateFlow<String?>(null)
    val ngWordsState = MutableStateFlow<String?>(null)
    val catalogNgWordsState = MutableStateFlow<String?>(null)
    val watchWordsState = MutableStateFlow<String?>(null)
    val selfPostIdentifiersState = MutableStateFlow<String?>(null)
    val preferredFileManagerPackageState = MutableStateFlow("")
    val preferredFileManagerLabelState = MutableStateFlow("")
    val threadMenuConfigState = MutableStateFlow<String?>(null)
    val threadSettingsMenuConfigState = MutableStateFlow<String?>(null)
    val threadMenuEntriesConfigState = MutableStateFlow<String?>(null)
    val catalogNavEntriesConfigState = MutableStateFlow<String?>(null)

    var failBoardsUpdate = false
    var failHistoryUpdate = false

    override val boardsJson: Flow<String?> = boardsState
    override val historyJson: Flow<String?> = historyState
    override val privacyFilterEnabled: Flow<Boolean> = privacyFilterState
    override val backgroundRefreshEnabled: Flow<Boolean> = backgroundRefreshState
    override val lightweightModeEnabled: Flow<Boolean> = lightweightModeState
    override val manualSaveDirectory: Flow<String> = manualSaveDirectoryState
    override val attachmentPickerPreference: Flow<String?> = attachmentPickerPreferenceState
    override val saveDirectorySelection: Flow<String?> = saveDirectorySelectionState
    override val lastUsedDeleteKey: Flow<String?> = lastUsedDeleteKeyState
    override val catalogModeMapJson: Flow<String?> = catalogModeMapState
    override val catalogDisplayStyle: Flow<String?> = catalogDisplayStyleState
    override val catalogGridColumns: Flow<String?> = catalogGridColumnsState
    override val ngHeadersJson: Flow<String?> = ngHeadersState
    override val ngWordsJson: Flow<String?> = ngWordsState
    override val catalogNgWordsJson: Flow<String?> = catalogNgWordsState
    override val watchWordsJson: Flow<String?> = watchWordsState
    override val selfPostIdentifiersJson: Flow<String?> = selfPostIdentifiersState
    override val preferredFileManagerPackage: Flow<String> = preferredFileManagerPackageState
    override val preferredFileManagerLabel: Flow<String> = preferredFileManagerLabelState
    override val threadMenuConfigJson: Flow<String?> = threadMenuConfigState
    override val threadSettingsMenuConfigJson: Flow<String?> = threadSettingsMenuConfigState
    override val threadMenuEntriesConfigJson: Flow<String?> = threadMenuEntriesConfigState
    override val catalogNavEntriesConfigJson: Flow<String?> = catalogNavEntriesConfigState

    override suspend fun updateBoardsJson(value: String) {
        if (failBoardsUpdate) error("boards write failed")
        boardsState.value = value
    }

    override suspend fun updateHistoryJson(value: String) {
        if (failHistoryUpdate) error("history write failed")
        historyState.value = value
    }

    override suspend fun updatePrivacyFilterEnabled(enabled: Boolean) {
        privacyFilterState.value = enabled
    }

    override suspend fun updateBackgroundRefreshEnabled(enabled: Boolean) {
        backgroundRefreshState.value = enabled
    }

    override suspend fun updateLightweightModeEnabled(enabled: Boolean) {
        lightweightModeState.value = enabled
    }

    override suspend fun updateManualSaveDirectory(directory: String) {
        manualSaveDirectoryState.value = directory
    }

    override suspend fun updateAttachmentPickerPreference(preference: String) {
        attachmentPickerPreferenceState.value = preference
    }

    override suspend fun updateSaveDirectorySelection(selection: String) {
        saveDirectorySelectionState.value = selection
    }

    override suspend fun updateLastUsedDeleteKey(value: String) {
        lastUsedDeleteKeyState.value = value
    }

    override suspend fun updateCatalogModeMapJson(value: String) {
        catalogModeMapState.value = value
    }

    override suspend fun updateCatalogDisplayStyle(style: String) {
        catalogDisplayStyleState.value = style
    }

    override suspend fun updateCatalogGridColumns(columns: String) {
        catalogGridColumnsState.value = columns
    }

    override suspend fun updateNgHeadersJson(value: String) {
        ngHeadersState.value = value
    }

    override suspend fun updateNgWordsJson(value: String) {
        ngWordsState.value = value
    }

    override suspend fun updateCatalogNgWordsJson(value: String) {
        catalogNgWordsState.value = value
    }

    override suspend fun updateWatchWordsJson(value: String) {
        watchWordsState.value = value
    }

    override suspend fun updateSelfPostIdentifiersJson(value: String) {
        selfPostIdentifiersState.value = value
    }

    override suspend fun updatePreferredFileManager(packageName: String, label: String) {
        preferredFileManagerPackageState.value = packageName
        preferredFileManagerLabelState.value = label
    }

    override suspend fun updatePreferredFileManagerPackage(packageName: String) {
        preferredFileManagerPackageState.value = packageName
    }

    override suspend fun updatePreferredFileManagerLabel(label: String) {
        preferredFileManagerLabelState.value = label
    }

    override suspend fun updateThreadMenuConfigJson(value: String) {
        threadMenuConfigState.value = value
    }

    override suspend fun updateThreadSettingsMenuConfigJson(value: String) {
        threadSettingsMenuConfigState.value = value
    }

    override suspend fun updateThreadMenuEntriesConfigJson(value: String) {
        threadMenuEntriesConfigState.value = value
    }

    override suspend fun updateCatalogNavEntriesConfigJson(value: String) {
        catalogNavEntriesConfigState.value = value
    }

    override suspend fun seedIfEmpty(
        defaultBoardsJson: String,
        defaultHistoryJson: String,
        defaultNgHeadersJson: String?,
        defaultNgWordsJson: String?,
        defaultCatalogNgWordsJson: String?,
        defaultWatchWordsJson: String?,
        defaultSelfPostIdentifiersJson: String?,
        defaultCatalogModeMapJson: String?,
        defaultAttachmentPickerPreference: String?,
        defaultSaveDirectorySelection: String?,
        defaultLastUsedDeleteKey: String?,
        defaultThreadMenuConfigJson: String?,
        defaultThreadSettingsMenuConfigJson: String?,
        defaultThreadMenuEntriesConfigJson: String?,
        defaultCatalogNavEntriesJson: String?
    ) {
        if (boardsState.value == null) boardsState.value = defaultBoardsJson
        if (historyState.value == null) historyState.value = defaultHistoryJson
        if (manualSaveDirectoryState.value.isBlank()) manualSaveDirectoryState.value = DEFAULT_MANUAL_SAVE_ROOT
        if (ngHeadersState.value == null) ngHeadersState.value = defaultNgHeadersJson
        if (ngWordsState.value == null) ngWordsState.value = defaultNgWordsJson
        if (catalogNgWordsState.value == null) catalogNgWordsState.value = defaultCatalogNgWordsJson
        if (watchWordsState.value == null) watchWordsState.value = defaultWatchWordsJson
        if (selfPostIdentifiersState.value == null) selfPostIdentifiersState.value = defaultSelfPostIdentifiersJson
        if (catalogModeMapState.value == null) catalogModeMapState.value = defaultCatalogModeMapJson
        if (attachmentPickerPreferenceState.value == null) attachmentPickerPreferenceState.value = defaultAttachmentPickerPreference
        if (saveDirectorySelectionState.value == null) saveDirectorySelectionState.value = defaultSaveDirectorySelection
        if (lastUsedDeleteKeyState.value == null) lastUsedDeleteKeyState.value = defaultLastUsedDeleteKey
        if (threadMenuConfigState.value == null) threadMenuConfigState.value = defaultThreadMenuConfigJson
        if (threadSettingsMenuConfigState.value == null) threadSettingsMenuConfigState.value = defaultThreadSettingsMenuConfigJson
        if (threadMenuEntriesConfigState.value == null) threadMenuEntriesConfigState.value = defaultThreadMenuEntriesConfigJson
        if (catalogNavEntriesConfigState.value == null) catalogNavEntriesConfigState.value = defaultCatalogNavEntriesJson
    }
}
