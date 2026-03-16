package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.service.DEFAULT_MANUAL_SAVE_ROOT
import com.valoser.futacha.shared.util.AttachmentPickerPreference
import com.valoser.futacha.shared.util.PreferredFileManager
import com.valoser.futacha.shared.util.SaveDirectorySelection
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
            AppStateSeedDefaults(
                boards = listOf(
                    BoardSummary(
                        id = "b",
                        name = "Board",
                        category = "cat",
                        url = "https://may.2chan.net/b/",
                        description = "desc"
                    )
                ),
                history = listOf(historyEntry(threadId = "123")),
                lastUsedDeleteKey = "123456789"
            )
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
    fun updateBoards_recordsStorageErrorWhenPersistenceFails() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)
        val initialBoards = listOf(
            BoardSummary(
                id = "b",
                name = "Board",
                category = "cat",
                url = "https://may.2chan.net/b/",
                description = "desc"
            )
        )

        store.setBoards(initialBoards)
        storage.failBoardsUpdate = true
        store.updateBoards { boards ->
            boards + BoardSummary(
                id = "c",
                name = "Board C",
                category = "cat",
                url = "https://may.2chan.net/c/",
                description = "desc"
            )
        }

        assertEquals(initialBoards, store.boards.first())
        assertEquals("updateBoards", store.lastStorageError.value?.operation)
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
    fun preferenceSetters_preservePreviousValuesWhenPersistenceFails() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)

        store.setBackgroundRefreshEnabled(true)
        store.setHasShownPostingNotice(true)

        storage.failBackgroundRefreshUpdate = true
        storage.failPostingNoticeUpdate = true

        store.setBackgroundRefreshEnabled(false)
        store.setHasShownPostingNotice(false)

        assertEquals(true, store.isBackgroundRefreshEnabled.first())
        assertEquals(true, store.hasShownPostingNotice.first())
    }

    @Test
    fun setters_normalizeDeleteKeyDirectoryGridColumnsAndEnums() = runBlocking {
        val storage = FakePlatformStateStorage()
        val store = AppStateStore(storage)

        store.setLastUsedDeleteKey(" 1234567890 ")
        store.setAdsEnabled(false)
        store.setManualSaveDirectory("./saved_threads")
        store.setCatalogGridColumns(99)
        store.setAttachmentPickerPreference(AttachmentPickerPreference.ALWAYS_ASK)
        store.setSaveDirectorySelection(SaveDirectorySelection.PICKER)

        assertEquals("12345678", store.lastUsedDeleteKey.first())
        assertEquals(false, store.isAdsEnabled.first())
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
    fun historyMutations_preserveOrderAndApplyEntryUpdates() = runBlocking {
        val store = AppStateStore(FakePlatformStateStorage())
        val first = historyEntry("111")
        val second = historyEntry("222")
        store.setHistory(listOf(first, second))

        store.upsertHistoryEntry(second.copy(title = "updated-222", replyCount = 22))
        assertEquals(
            listOf("111", "222"),
            store.history.first().map { it.threadId }
        )
        assertEquals("updated-222", store.history.first()[1].title)
        assertEquals(22, store.history.first()[1].replyCount)

        val prepended = historyEntry("333")
        store.prependOrReplaceHistoryEntry(prepended)
        assertEquals(
            listOf("333", "111", "222"),
            store.history.first().map { it.threadId }
        )

        store.updateHistoryScrollPosition(
            AppStateHistoryScrollUpdateRequest(
                threadId = "222",
                index = 7,
                offset = 14,
                boardId = "b",
                title = "updated-222",
                titleImageUrl = "thumb-222",
                boardName = "board",
                boardUrl = "https://may.2chan.net/b/futaba.php",
                replyCount = 22
            )
        )
        val updatedEntry = store.history.first().first { it.threadId == "222" }
        assertEquals(7, updatedEntry.lastReadItemIndex)
        assertEquals(14, updatedEntry.lastReadItemOffset)

        store.removeHistoryEntry(first)
        assertEquals(
            listOf("333", "222"),
            store.history.first().map { it.threadId }
        )
    }

    fun preferenceFacade_forwardsPreferenceAndSelfPostOperations() = runBlocking {
        val calls = mutableListOf<String>()
        val facade = buildAppStatePreferenceFacade(
            setBackgroundRefreshEnabledImpl = { calls += "background:$it" },
            setHasShownPostingNoticeImpl = { calls += "postingNotice:$it" },
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
        facade.setHasShownPostingNotice(true)
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
                "postingNotice:true",
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
    fun boardsFlow_decodesPersistedBoards() = runBlocking {
        val storage = FakePlatformStateStorage().apply {
            boardsState.value = """
                [{"id":"img","name":"img","category":"","url":"https://may.2chan.net/img/futaba.php","description":"","pinned":false}]
            """.trimIndent()
        }
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

internal class FakePlatformStateStorage : BaseInMemoryPlatformStateStorage() {

    var failBoardsUpdate = false
    var failHistoryUpdate = false
    var failBackgroundRefreshUpdate = false
    var failPostingNoticeUpdate = false
    var failCatalogModeUpdate = false
    var failSelfPostIdentifiersUpdate = false
    var failPreferredFileManagerUpdate = false

    override suspend fun updateBoardsJson(value: String) {
        if (failBoardsUpdate) error("boards write failed")
        super.updateBoardsJson(value)
    }

    override suspend fun updateHistoryJson(value: String) {
        if (failHistoryUpdate) error("history write failed")
        super.updateHistoryJson(value)
    }

    override suspend fun updateBackgroundRefreshEnabled(enabled: Boolean) {
        if (failBackgroundRefreshUpdate) error("background refresh write failed")
        super.updateBackgroundRefreshEnabled(enabled)
    }

    override suspend fun updateHasShownPostingNotice(shown: Boolean) {
        if (failPostingNoticeUpdate) error("posting notice write failed")
        super.updateHasShownPostingNotice(shown)
    }

    override suspend fun updateCatalogModeMapJson(value: String) {
        if (failCatalogModeUpdate) error("catalog mode write failed")
        super.updateCatalogModeMapJson(value)
    }

    override suspend fun updateSelfPostIdentifiersJson(value: String) {
        if (failSelfPostIdentifiersUpdate) error("self post identifiers write failed")
        super.updateSelfPostIdentifiersJson(value)
    }

    override suspend fun updatePreferredFileManager(packageName: String, label: String) {
        if (failPreferredFileManagerUpdate) error("preferred file manager write failed")
        super.updatePreferredFileManager(packageName, label)
    }
}
