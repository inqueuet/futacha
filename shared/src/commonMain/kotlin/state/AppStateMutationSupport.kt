package com.valoser.futacha.shared.state

import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.util.Logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

internal suspend fun <T> setAppStateSimplePreference(
    value: T,
    update: suspend (T) -> Unit,
    label: String,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit,
    rethrowOnFailure: Boolean = false
) {
    persistAppStatePreference(
        value = value,
        update = update,
        onFailure = { error -> Logger.e(tag, "Failed to save $label: $value", error) },
        rethrowIfCancellation = rethrowIfCancellation,
        rethrowOnFailure = rethrowOnFailure
    )
}

internal suspend fun <T> setAppStateListPreference(
    values: List<T>,
    serializer: KSerializer<List<T>>,
    json: Json,
    update: suspend (String) -> Unit,
    label: String,
    tag: String,
    rethrowIfCancellation: (Throwable) -> Unit
) {
    persistAppStateListPreference(
        values = values,
        serializer = serializer,
        json = json,
        update = update,
        onFailure = { error -> Logger.e(tag, "Failed to save $label (${values.size})", error) },
        rethrowIfCancellation = rethrowIfCancellation
    )
}

internal suspend fun setAppStateCatalogMode(
    boardId: String,
    mode: CatalogMode,
    tag: String,
    mutateCatalogModeMap: suspend (
        onReadFailure: (Throwable) -> Unit,
        onWriteFailure: (Throwable) -> Unit,
        transform: (Map<String, CatalogMode>) -> Map<String, CatalogMode>
    ) -> Unit
) {
    val normalizedBoardId = boardId.trim()
    if (normalizedBoardId.isBlank()) {
        Logger.w(tag, "Ignoring catalog mode update with blank boardId")
        return
    }
    mutateCatalogModeMap(
        { error ->
            Logger.e(tag, "Failed to read catalog mode map snapshot", error)
        },
        { error ->
            Logger.e(tag, "Failed to save catalog mode for $normalizedBoardId: ${mode.name}", error)
        }
    ) { current ->
        if (current[normalizedBoardId] == mode) {
            current
        } else {
            current + (normalizedBoardId to mode)
        }
    }
}

internal suspend fun addAppStateSelfPostIdentifier(
    threadId: String,
    identifier: String,
    boardId: String? = null,
    maxEntries: Int = SELF_POST_IDENTIFIER_MAX_ENTRIES_VALUE,
    mutateSelfPostIdentifierMap: suspend (
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) -> Unit
) {
    val trimmed = identifier.trim().takeIf { it.isNotBlank() } ?: return
    mutateSelfPostIdentifierMap { currentMap ->
        mergeSelfPostIdentifierMap(
            currentMap = currentMap,
            threadId = threadId,
            identifier = trimmed,
            boardId = boardId,
            maxEntries = maxEntries
        )
    }
}

internal suspend fun removeAppStateSelfPostIdentifiersForThread(
    threadId: String,
    boardId: String? = null,
    mutateSelfPostIdentifierMap: suspend (
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) -> Unit
) {
    mutateSelfPostIdentifierMap { currentMap ->
        removeSelfPostIdentifiersFromMap(currentMap, threadId, boardId)
    }
}

internal suspend fun clearAppStateSelfPostIdentifiers(
    mutateSelfPostIdentifierMap: suspend (
        transform: (Map<String, List<String>>) -> Map<String, List<String>>
    ) -> Unit
) {
    mutateSelfPostIdentifierMap { currentMap ->
        if (currentMap.isEmpty()) currentMap else emptyMap()
    }
}
