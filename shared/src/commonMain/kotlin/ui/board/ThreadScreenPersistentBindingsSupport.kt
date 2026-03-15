package com.valoser.futacha.shared.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.state.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class ThreadScreenPersistentBindings(
    val lastUsedDeleteKey: String,
    val updateLastUsedDeleteKey: (String) -> Unit,
    val persistedSelfPostIdentifiers: List<String>,
    val selfPostIdentifierSet: Set<String>,
    val isSelfPost: (Post) -> Boolean,
    val ngHeaders: List<String>,
    val ngWords: List<String>,
    val onFallbackHeadersChanged: (List<String>) -> Unit,
    val onFallbackWordsChanged: (List<String>) -> Unit
)

@Composable
internal fun rememberThreadScreenPersistentBindings(
    stateStore: AppStateStore?,
    coroutineScope: CoroutineScope,
    boardId: String,
    threadId: String
): ThreadScreenPersistentBindings {
    val lastUsedDeleteKeyState = stateStore?.lastUsedDeleteKey?.collectAsState(initial = "")
    var fallbackDeleteKey by rememberSaveable { mutableStateOf("") }
    val lastUsedDeleteKey = lastUsedDeleteKeyState?.value ?: fallbackDeleteKey
    val updateLastUsedDeleteKey: (String) -> Unit = remember(stateStore, coroutineScope) {
        { value: String ->
            val sanitized = sanitizeStoredDeleteKey(value)
            val store = stateStore
            if (store != null) {
                coroutineScope.launch { store.setLastUsedDeleteKey(sanitized) }
                Unit
            } else {
                fallbackDeleteKey = sanitized
            }
        }
    }

    val persistedSelfPostMapState =
        stateStore?.selfPostIdentifiersByThread?.collectAsState(initial = emptyMap())
    val persistedSelfPostMap = persistedSelfPostMapState?.value ?: emptyMap()
    val scopedSelfPostKey = remember(boardId, threadId) {
        buildThreadScopedSelfPostKey(boardId, threadId)
    }
    val persistedSelfPostIdentifiers = remember(persistedSelfPostMap, scopedSelfPostKey, threadId) {
        buildPersistedSelfPostIdentifiers(
            persistedSelfPostMap = persistedSelfPostMap,
            scopedSelfPostKey = scopedSelfPostKey,
            threadId = threadId
        )
    }
    val selfPostIdentifierSet = remember(persistedSelfPostIdentifiers) {
        buildSelfPostIdentifierSet(persistedSelfPostIdentifiers)
    }
    val isSelfPost = remember(selfPostIdentifierSet) {
        { post: Post -> isThreadSelfPost(selfPostIdentifierSet, post.id) }
    }

    val fallbackNgHeadersState = rememberSaveable(boardId, threadId) {
        mutableStateOf<List<String>>(emptyList())
    }
    val fallbackNgWordsState = rememberSaveable(boardId, threadId) {
        mutableStateOf<List<String>>(emptyList())
    }
    val ngHeadersState = stateStore?.ngHeaders?.collectAsState(initial = fallbackNgHeadersState.value)
    val ngWordsState = stateStore?.ngWords?.collectAsState(initial = fallbackNgWordsState.value)
    val ngHeaders = ngHeadersState?.value ?: fallbackNgHeadersState.value
    val ngWords = ngWordsState?.value ?: fallbackNgWordsState.value

    return remember(
        lastUsedDeleteKey,
        updateLastUsedDeleteKey,
        persistedSelfPostIdentifiers,
        selfPostIdentifierSet,
        isSelfPost,
        ngHeaders,
        ngWords
    ) {
        ThreadScreenPersistentBindings(
            lastUsedDeleteKey = lastUsedDeleteKey,
            updateLastUsedDeleteKey = updateLastUsedDeleteKey,
            persistedSelfPostIdentifiers = persistedSelfPostIdentifiers,
            selfPostIdentifierSet = selfPostIdentifierSet,
            isSelfPost = isSelfPost,
            ngHeaders = ngHeaders,
            ngWords = ngWords,
            onFallbackHeadersChanged = { fallbackNgHeadersState.value = it },
            onFallbackWordsChanged = { fallbackNgWordsState.value = it }
        )
    }
}

internal fun buildThreadScopedSelfPostKey(
    boardId: String,
    threadId: String
): String {
    return if (boardId.isBlank()) threadId else "$boardId::$threadId"
}

internal fun buildPersistedSelfPostIdentifiers(
    persistedSelfPostMap: Map<String, List<String>>,
    scopedSelfPostKey: String,
    threadId: String
): List<String> {
    return buildList {
        addAll(persistedSelfPostMap[scopedSelfPostKey].orEmpty())
        if (scopedSelfPostKey != threadId) {
            addAll(persistedSelfPostMap[threadId].orEmpty())
        }
    }
}

internal fun buildSelfPostIdentifierSet(
    persistedSelfPostIdentifiers: List<String>
): Set<String> {
    return persistedSelfPostIdentifiers
        .mapNotNull { it.trim().takeIf { trimmed -> trimmed.isNotBlank() } }
        .toSet()
}

internal fun isThreadSelfPost(
    selfPostIdentifierSet: Set<String>,
    postId: String
): Boolean {
    return selfPostIdentifierSet.contains(postId.trim())
}
