package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogNavEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuEntryConfig
import com.valoser.futacha.shared.model.ThreadMenuItemConfig
import com.valoser.futacha.shared.model.ThreadSettingsMenuItemConfig
import com.valoser.futacha.shared.util.PreferredFileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AppStatePreferenceOperationsSupportTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val stringListSerializer = ListSerializer(String.serializer())
    private val threadMenuConfigSerializer = ListSerializer(ThreadMenuItemConfig.serializer())
    private val threadSettingsMenuConfigSerializer = ListSerializer(ThreadSettingsMenuItemConfig.serializer())
    private val threadMenuEntriesSerializer = ListSerializer(ThreadMenuEntryConfig.serializer())
    private val catalogNavEntriesSerializer = ListSerializer(CatalogNavEntryConfig.serializer())

    @Test
    fun setNgWords_persistsEncodedWordsToStorage() = runBlocking {
        val storage = FakePlatformStateStorage()
        val operations = createOperations(storage)

        operations.setNgWords(listOf("spam", "ads"))

        assertEquals(
            listOf("spam", "ads"),
            json.decodeFromString(stringListSerializer, storage.ngWordsState.value ?: "[]")
        )
    }

    @Test
    fun catalogModeAndSelfPostOperations_useInjectedMutators() = runBlocking {
        var catalogModeMap = emptyMap<String, CatalogMode>()
        var selfPostIdentifierMap = emptyMap<String, List<String>>()
        val operations = createOperations(
            storage = FakePlatformStateStorage(),
            mutateCatalogModeMap = { _, _, transform ->
                catalogModeMap = transform(catalogModeMap)
            },
            mutateSelfPostIdentifierMap = { transform ->
                selfPostIdentifierMap = transform(selfPostIdentifierMap)
            }
        )

        operations.setCatalogMode("img", CatalogMode.Old)
        operations.addSelfPostIdentifier(threadId = "123", identifier = "ID:abc", boardId = "img")
        operations.removeSelfPostIdentifiersForThread(threadId = "123", boardId = "img")

        assertEquals(mapOf("img" to CatalogMode.Old), catalogModeMap)
        assertEquals(emptyMap(), selfPostIdentifierMap)
    }

    private fun createOperations(
        storage: PlatformStateStorage,
        preferredFileManagerFlow: Flow<PreferredFileManager?> = flowOf(null),
        mutateCatalogModeMap: suspend (
            onReadFailure: (Throwable) -> Unit,
            onWriteFailure: (Throwable) -> Unit,
            transform: (Map<String, CatalogMode>) -> Map<String, CatalogMode>
        ) -> Unit = { _, _, transform -> transform(emptyMap()) },
        mutateSelfPostIdentifierMap: suspend (
            transform: (Map<String, List<String>>) -> Map<String, List<String>>
        ) -> Unit = { transform -> transform(emptyMap()) }
    ): AppStatePreferenceOperations {
        return AppStatePreferenceOperations(
            storage = storage,
            json = json,
            stringListSerializer = stringListSerializer,
            threadMenuConfigSerializer = threadMenuConfigSerializer,
            threadSettingsMenuConfigSerializer = threadSettingsMenuConfigSerializer,
            threadMenuEntriesSerializer = threadMenuEntriesSerializer,
            catalogNavEntriesSerializer = catalogNavEntriesSerializer,
            preferredFileManagerFlow = preferredFileManagerFlow,
            selfIdentifierMaxEntries = 20,
            tag = "AppStatePreferenceOperationsSupportTest",
            rethrowIfCancellation = { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
            },
            mutateCatalogModeMap = mutateCatalogModeMap,
            mutateSelfPostIdentifierMap = mutateSelfPostIdentifierMap
        )
    }
}
