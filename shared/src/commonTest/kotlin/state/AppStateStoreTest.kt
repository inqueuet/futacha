package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.PreferredFileManager
import com.valoser.futacha.shared.util.SaveDirectorySelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AppStateStoreTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val selfPostSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

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

    @Test
    fun setCatalogMode_ignoresBlankBoardId_andRollsBackOnPersistenceFailure() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)

        store.setCatalogMode("board-a", CatalogMode.New)
        storage.failCatalogModeUpdate = true
        store.setCatalogMode("board-a", CatalogMode.WatchWords)
        store.setCatalogMode("   ", CatalogMode.Old)

        assertEquals(mapOf("board-a" to CatalogMode.New), store.catalogModes.first())
    }

    @Test
    fun setPreferredFileManager_preservesPreviousSelectionWhenPersistenceFails() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)

        store.setPreferredFileManager("com.example.files", "Files")
        storage.failPreferredFileManagerUpdate = true

        assertFailsWith<IllegalStateException> {
            store.setPreferredFileManager("com.example.other", "Other")
        }

        assertEquals(
            PreferredFileManager(
                packageName = "com.example.files",
                label = "Files"
            ),
            store.getPreferredFileManager().first()
        )
    }

    @Test
    fun setters_normalizeDeleteKeyDirectoryGridColumnsAndEnums() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)

        store.setLastUsedDeleteKey(" 1234567890 ")
        store.setManualSaveDirectory("./saved_threads")
        store.setCatalogGridColumns(99)
        store.setAttachmentPickerPreference(AttachmentPickerPreference.ALWAYS_ASK)
        store.setSaveDirectorySelection(SaveDirectorySelection.PICKER)

        assertEquals("12345678", store.lastUsedDeleteKey.first())
        assertEquals(DEFAULT_MANUAL_SAVE_ROOT, store.manualSaveDirectory.first())
        assertEquals(8, store.catalogGridColumns.first())
        assertEquals(AttachmentPickerPreference.ALWAYS_ASK, store.attachmentPickerPreference.first())
        assertEquals(SaveDirectorySelection.PICKER, store.saveDirectorySelection.first())
    }

    @Test
    fun selfPostIdentifierMutations_updateAndRollbackAsExpected() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)

        store.addSelfPostIdentifier(threadId = "123", identifier = "ID:abc", boardId = "b")
        assertEquals(
            mapOf("b::123" to listOf("ID:abc")),
            store.selfPostIdentifiersByThread.first()
        )

        storage.failSelfPostIdentifiersUpdate = true
        assertFailsWith<IllegalStateException> {
            store.addSelfPostIdentifier(threadId = "123", identifier = "ID:def", boardId = "b")
        }
        assertEquals(
            mapOf("b::123" to listOf("ID:abc")),
            store.selfPostIdentifiersByThread.first()
        )

        storage.failSelfPostIdentifiersUpdate = false
        store.removeSelfPostIdentifiersForThread(threadId = "123", boardId = "b")
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())

        store.addSelfPostIdentifier(threadId = "123", identifier = "ID:xyz", boardId = "b")
        store.clearSelfPostIdentifiers()
        assertEquals(emptyMap(), store.selfPostIdentifiersByThread.first())
        assertEquals(
            emptyMap(),
            json.decodeFromString(selfPostSerializer, storage.selfPostIdentifiersState.value ?: "{}")
        )
    }

    @Test
    fun historyFacade_forwardsPublicHistoryOperations() = runBlocking {
        val calls = mutableListOf<String>()
        val entry = historyEntry("123")
        val facade = buildAppStateHistoryFacade(
            setHistoryImpl = { calls += "setHistory:${it.size}" },
            upsertHistoryEntryImpl = { calls += "upsert:${it.threadId}" },
            prependOrReplaceHistoryEntryImpl = { calls += "prepend:${it.threadId}" },
            prependOrReplaceHistoryEntriesImpl = { calls += "prependBatch:${it.size}" },
            mergeHistoryEntriesImpl = { calls += "merge:${it.size}" },
            removeHistoryEntryImpl = { calls += "remove:${it.threadId}" },
            updateHistoryScrollPositionImpl = { threadId, _, _, _, _, _, _, _, _ ->
                calls += "scroll:$threadId"
            },
            setScrollDebounceScopeImpl = { calls += "scope" }
        )

        facade.setScrollDebounceScope(this)
        facade.setHistory(listOf(entry))
        facade.upsertHistoryEntry(entry)
        facade.prependOrReplaceHistoryEntry(entry)
        facade.prependOrReplaceHistoryEntries(listOf(entry))
        facade.mergeHistoryEntries(listOf(entry))
        facade.removeHistoryEntry(entry)
        facade.updateHistoryScrollPosition(
            threadId = "123",
            index = 1,
            offset = 2,
            boardId = "b",
            title = "title",
            titleImageUrl = "thumb",
            boardName = "board",
            boardUrl = "url",
            replyCount = 3
        )

        assertEquals(
            listOf(
                "scope",
                "setHistory:1",
                "upsert:123",
                "prepend:123",
                "prependBatch:1",
                "merge:1",
                "remove:123",
                "scroll:123"
            ),
            calls
        )
    }

    @Test
    fun preferenceFacade_forwardsPreferenceAndSelfPostOperations() = runBlocking {
        val calls = mutableListOf<String>()
        val facade = buildAppStatePreferenceFacade(
            setBackgroundRefreshEnabledImpl = { calls += "background:$it" },
            setLastUsedDeleteKeyImpl = { calls += "deleteKey:$it" },
            setLightweightModeEnabledImpl = { calls += "lightweight:$it" },
            setManualSaveDirectoryImpl = { calls += "directory:$it" },
            setManualSaveLocationImpl = {
                calls += when (it) {
                    is com.valoser.futacha.shared.model.SaveLocation.Path -> "location:path:${it.path}"
                    is com.valoser.futacha.shared.model.SaveLocation.TreeUri -> "location:tree:${it.uri}"
                    is com.valoser.futacha.shared.model.SaveLocation.Bookmark -> "location:bookmark:${it.bookmarkData}"
                }
            },
            setAttachmentPickerPreferenceImpl = { calls += "picker:${it.name}" },
            setSaveDirectorySelectionImpl = { calls += "selection:${it.name}" },
            setPreferredFileManagerImpl = { pkg, label -> calls += "fileManager:${pkg.orEmpty()}:${label.orEmpty()}" },
            setPrivacyFilterEnabledImpl = { calls += "privacy:$it" },
            setCatalogDisplayStyleImpl = { calls += "display:${it.name}" },
            setCatalogGridColumnsImpl = { calls += "columns:$it" },
            setCatalogModeImpl = { boardId, mode -> calls += "catalogMode:$boardId:${mode.name}" },
            setNgHeadersImpl = { calls += "ngHeaders:${it.size}" },
            setNgWordsImpl = { calls += "ngWords:${it.size}" },
            setCatalogNgWordsImpl = { calls += "catalogNgWords:${it.size}" },
            setWatchWordsImpl = { calls += "watchWords:${it.size}" },
            setThreadMenuConfigImpl = { calls += "threadMenuConfig:${it.size}" },
            setThreadSettingsMenuConfigImpl = { calls += "threadSettingsMenuConfig:${it.size}" },
            setThreadMenuEntriesImpl = { calls += "threadMenuEntries:${it.size}" },
            setCatalogNavEntriesImpl = { calls += "catalogNavEntries:${it.size}" },
            addSelfPostIdentifierImpl = { threadId, identifier, boardId ->
                calls += "addSelfPost:$threadId:$identifier:${boardId.orEmpty()}"
            },
            removeSelfPostIdentifiersForThreadImpl = { threadId, boardId ->
                calls += "removeSelfPost:$threadId:${boardId.orEmpty()}"
            },
            clearSelfPostIdentifiersImpl = { calls += "clearSelfPost" }
        )

        facade.setBackgroundRefreshEnabled(true)
        facade.setLastUsedDeleteKey("1234")
        facade.setLightweightModeEnabled(true)
        facade.setManualSaveDirectory("/tmp")
        facade.setManualSaveLocation(com.valoser.futacha.shared.model.SaveLocation.Path("/tmp"))
        facade.setAttachmentPickerPreference(AttachmentPickerPreference.ALWAYS_ASK)
        facade.setSaveDirectorySelection(SaveDirectorySelection.PICKER)
        facade.setPreferredFileManager("pkg", "Files")
        facade.setPrivacyFilterEnabled(true)
        facade.setCatalogDisplayStyle(com.valoser.futacha.shared.model.CatalogDisplayStyle.List)
        facade.setCatalogGridColumns(4)
        facade.setCatalogMode("b", CatalogMode.Old)
        facade.setNgHeaders(listOf("a"))
        facade.setNgWords(listOf("a", "b"))
        facade.setCatalogNgWords(listOf("a"))
        facade.setWatchWords(listOf("a"))
        facade.setThreadMenuConfig(emptyList())
        facade.setThreadSettingsMenuConfig(emptyList())
        facade.setThreadMenuEntries(emptyList())
        facade.setCatalogNavEntries(emptyList())
        facade.addSelfPostIdentifier("123", "ID:abc", "b")
        facade.removeSelfPostIdentifiersForThread("123", "b")
        facade.clearSelfPostIdentifiers()

        assertEquals(
            listOf(
                "background:true",
                "deleteKey:1234",
                "lightweight:true",
                "directory:/tmp",
                "location:path:/tmp",
                "picker:ALWAYS_ASK",
                "selection:PICKER",
                "fileManager:pkg:Files",
                "privacy:true",
                "display:List",
                "columns:4",
                "catalogMode:b:Old",
                "ngHeaders:1",
                "ngWords:2",
                "catalogNgWords:1",
                "watchWords:1",
                "threadMenuConfig:0",
                "threadSettingsMenuConfig:0",
                "threadMenuEntries:0",
                "catalogNavEntries:0",
                "addSelfPost:123:ID:abc:b",
                "removeSelfPost:123:b",
                "clearSelfPost"
            ),
            calls
        )
    }

    @Test
    fun boardsFacade_andFlow_forwardAndDecodeBoards() = runBlocking {
        val calls = mutableListOf<String>()
        val facade = buildAppStateBoardsFacade(
            setBoardsImpl = { calls += "boards:${it.size}" }
        )
        val storage = FakePlatformStateStorage().apply {
            boardsState.value = """
                [{"id":"img","name":"img","category":"","url":"https://may.2chan.net/img/futaba.php","description":"","pinned":false}]
            """.trimIndent()
        }

        facade.setBoards(
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

        assertEquals(listOf("boards:1"), calls)
        assertEquals(
            listOf("img"),
            buildAppStateBoardsFlow(storage, json, "AppStateStoreTest").first().map { it.id }
        )
    }

    @Test
    fun seedPayload_toSeedBundles_splitsBoardsHistoryAndPreferences() {
        val bundles = AppStateSeedPayload(
            defaultBoardsJson = "boards",
            defaultHistoryJson = "history",
            defaultNgHeadersJson = "ngHeaders",
            defaultNgWordsJson = "ngWords",
            defaultCatalogNgWordsJson = "catalogNgWords",
            defaultWatchWordsJson = "watchWords",
            defaultSelfPostIdentifiersJson = "selfPost",
            defaultCatalogModeMapJson = "catalogMode",
            defaultAttachmentPickerPreference = "MEDIA",
            defaultSaveDirectorySelection = "MANUAL_INPUT",
            defaultLastUsedDeleteKey = "12345678",
            defaultThreadMenuConfigJson = "threadMenu",
            defaultThreadSettingsMenuConfigJson = "threadSettings",
            defaultThreadMenuEntriesConfigJson = "threadEntries",
            defaultCatalogNavEntriesJson = "catalogEntries"
        ).toSeedBundles()

        assertEquals("boards", bundles.boards.boardsJson)
        assertEquals("history", bundles.history.historyJson)
        assertEquals("ngHeaders", bundles.preferences.ngHeadersJson)
        assertEquals("catalogEntries", bundles.preferences.catalogNavEntriesJson)
        assertEquals("12345678", bundles.preferences.lastUsedDeleteKey)
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
    var failCatalogModeUpdate = false
    var failSelfPostIdentifiersUpdate = false
    var failPreferredFileManagerUpdate = false

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
        if (failCatalogModeUpdate) error("catalog mode write failed")
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
        if (failSelfPostIdentifiersUpdate) error("self post identifiers write failed")
        selfPostIdentifiersState.value = value
    }

    override suspend fun updatePreferredFileManager(packageName: String, label: String) {
        if (failPreferredFileManagerUpdate) error("preferred file manager write failed")
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
