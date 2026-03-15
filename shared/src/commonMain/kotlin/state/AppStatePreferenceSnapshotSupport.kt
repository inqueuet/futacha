package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal class AppStatePreferenceSnapshotCoordinator(
    private val storage: PlatformStateStorage,
    private val json: Json,
    private val catalogModeMutex: Mutex,
    private val selfPostIdentifiersMutex: Mutex,
    private val selfPostIdentifierMapSerializer: KSerializer<Map<String, List<String>>>,
    private val tag: String,
    private val rethrowIfCancellation: (Throwable) -> Unit
) {
    private var cachedCatalogModeMap: Map<String, CatalogMode>? = null
    private var cachedSelfPostIdentifierMap: Map<String, List<String>>? = null

    suspend fun mutateCatalogModeMap(
        onReadFailure: (Throwable) -> Unit,
        onWriteFailure: (Throwable) -> Unit,
        transform: (Map<String, CatalogMode>) -> Map<String, CatalogMode>
    ) {
        val loadedSnapshot = runCatching {
            readCatalogModeSnapshot()
        }.getOrElse { error ->
            onReadFailure(error)
            return
        }
        mutateAppStateCachedSnapshot(
            mutex = catalogModeMutex,
            loadedSnapshot = loadedSnapshot,
            currentCachedValue = { cachedCatalogModeMap },
            setCachedValue = { cachedCatalogModeMap = it },
            transform = transform,
            persistUpdatedValue = ::persistCatalogModeMap,
            onWriteFailure = onWriteFailure,
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    suspend fun mutateSelfPostIdentifierMap(
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) {
        val loadedSnapshot = readSelfPostIdentifierMapSnapshot()
        mutateAppStateCachedSnapshot(
            mutex = selfPostIdentifiersMutex,
            loadedSnapshot = loadedSnapshot,
            currentCachedValue = { cachedSelfPostIdentifierMap },
            setCachedValue = { cachedSelfPostIdentifierMap = it },
            transform = transform,
            persistUpdatedValue = ::persistSelfPostIdentifierMap,
            onWriteFailure = { error -> throw error },
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    private suspend fun readSelfPostIdentifierMapSnapshot(): Map<String, List<String>> {
        return readAppStateCachedSnapshot(
            mutex = selfPostIdentifiersMutex,
            currentCachedValue = { cachedSelfPostIdentifierMap },
            readStorageSnapshot = {
                val raw = storage.selfPostIdentifiersJson.first()
                withContext(AppDispatchers.parsing) {
                    decodeSelfPostIdentifierMapValue(raw, json, selfPostIdentifierMapSerializer)
                }
            },
            setCachedValue = { cachedSelfPostIdentifierMap = it },
            onReadFailure = { error ->
                Logger.e(tag, "Failed to read self post identifier map", error)
                emptyMap()
            },
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    private suspend fun persistSelfPostIdentifierMap(map: Map<String, List<String>>) {
        persistAppStateSelfPostIdentifierMap(
            map = map,
            json = json,
            update = storage::updateSelfPostIdentifiersJson
        )
    }

    private suspend fun readCatalogModeSnapshot(): Map<String, CatalogMode> {
        return readAppStateCachedSnapshot(
            mutex = catalogModeMutex,
            currentCachedValue = { cachedCatalogModeMap },
            readStorageSnapshot = {
                val raw = storage.catalogModeMapJson.first()
                withContext(AppDispatchers.parsing) {
                    decodeCatalogModeMapValue(raw)
                }
            },
            setCachedValue = { cachedCatalogModeMap = it },
            onReadFailure = { error ->
                throw error
            },
            rethrowIfCancellation = rethrowIfCancellation
        )
    }

    private suspend fun persistCatalogModeMap(map: Map<String, CatalogMode>) {
        persistAppStateCatalogModeMap(
            map = map,
            json = json,
            update = storage::updateCatalogModeMapJson
        )
    }
}
