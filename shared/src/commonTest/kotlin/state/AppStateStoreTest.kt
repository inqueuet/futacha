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
