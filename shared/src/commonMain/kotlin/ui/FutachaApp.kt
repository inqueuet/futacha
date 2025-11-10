package com.valoser.futacha.shared.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.ThreadHistoryEntry
import com.valoser.futacha.shared.repo.createRemoteBoardRepository
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.BoardManagementScreen
import com.valoser.futacha.shared.ui.board.CatalogScreen
import com.valoser.futacha.shared.ui.board.ThreadScreen
import com.valoser.futacha.shared.ui.board.mockBoardSummaries
import com.valoser.futacha.shared.ui.board.mockThreadHistory
import com.valoser.futacha.shared.ui.theme.FutachaTheme
import com.valoser.futacha.shared.network.BoardUrlResolver
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun FutachaApp(
    stateStore: AppStateStore,
    boardList: List<BoardSummary> = mockBoardSummaries,
    history: List<ThreadHistoryEntry> = mockThreadHistory
) {
    FutachaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val coroutineScope = rememberCoroutineScope()
            // Use remember with Unit key to ensure single instance per composition lifecycle
            val remoteBoardRepository = remember(Unit) {
                createRemoteBoardRepository()
            }

            // Clean up repository when composable leaves composition
            // Add Unit key to ensure DisposableEffect runs only once per composition
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    remoteBoardRepository.close()
                }
            }

            LaunchedEffect(stateStore, boardList, history) {
                stateStore.seedIfEmpty(boardList, history)
            }

            val persistedBoards by stateStore.boards.collectAsState(initial = boardList)
            val persistedHistory by stateStore.history.collectAsState(initial = history)

            var selectedBoardId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedThreadId by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedThreadTitle by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedThreadReplies by rememberSaveable { mutableStateOf<Int?>(null) }
            var selectedThreadThumbnailUrl by rememberSaveable { mutableStateOf<String?>(null) }
            var selectedThreadUrl by rememberSaveable { mutableStateOf<String?>(null) }

            val selectedBoard = persistedBoards.firstOrNull { it.id == selectedBoardId }
            val dismissHistoryEntry: (ThreadHistoryEntry) -> Unit = { entry ->
                val updatedHistory = persistedHistory.filterNot { it.threadId == entry.threadId }
                coroutineScope.launch {
                    stateStore.setHistory(updatedHistory)
                }
            }
            val updateHistoryEntry: (ThreadHistoryEntry) -> Unit = { entry ->
                coroutineScope.launch {
                    stateStore.upsertHistoryEntry(entry)
                }
            }
            val clearHistory: () -> Unit = {
                coroutineScope.launch {
                    stateStore.setHistory(emptyList())
                }
            }
            val refreshHistoryEntries: suspend () -> Unit = refreshHistoryEntries@{
                val snapshot = persistedHistory.toList()
                if (snapshot.isEmpty()) return@refreshHistoryEntries
                var firstError: Throwable? = null
                snapshot.forEach { entry ->
                    if (entry.boardUrl.isBlank() || entry.boardUrl.contains("example.com", ignoreCase = true)) {
                        return@forEach
                    }
                    val boardBaseUrl = persistedBoards.firstOrNull { it.id == entry.boardId }?.url
                        ?: runCatching {
                            if (entry.boardUrl.isNotBlank()) {
                                BoardUrlResolver.resolveBoardBaseUrl(entry.boardUrl)
                            } else {
                                null
                            }
                        }.getOrNull()
                    if (boardBaseUrl.isNullOrBlank()) {
                        return@forEach
                    }
                    try {
                        val page = remoteBoardRepository.getThread(boardBaseUrl, entry.threadId)
                        val opPost = page.posts.firstOrNull()
                        val updatedEntry = entry.copy(
                            title = opPost?.subject?.takeIf { it.isNotBlank() } ?: entry.title,
                            titleImageUrl = opPost?.thumbnailUrl ?: entry.titleImageUrl,
                            boardName = page.boardTitle ?: entry.boardName,
                            replyCount = page.posts.size
                        )
                        updateHistoryEntry(updatedEntry)
                    } catch (e: Exception) {
                        println("FutachaApp: Failed to refresh history entry ${entry.threadId}: ${e.message}")
                        if (firstError == null) {
                            firstError = e
                        }
                    }
                }
                firstError?.let { throw it }
            }
            val openHistoryEntry: (ThreadHistoryEntry) -> Unit = { entry ->
                val targetBoard = persistedBoards.firstOrNull { entry.boardId.isNotBlank() && it.id == entry.boardId }
                    ?: persistedBoards.firstOrNull { it.name == entry.boardName }
                targetBoard?.let { board ->
                    selectedBoardId = board.id
                    selectedThreadId = entry.threadId
                    selectedThreadTitle = entry.title
                    selectedThreadReplies = entry.replyCount
                    selectedThreadThumbnailUrl = entry.titleImageUrl
                    selectedThreadUrl = entry.boardUrl
                }
            }

            when {
                selectedBoard == null -> {
                    selectedThreadId = null
                    selectedThreadTitle = null
                    selectedThreadReplies = null
                    selectedThreadThumbnailUrl = null
                    selectedThreadUrl = null
                    BoardManagementScreen(
                        boards = persistedBoards,
                        history = persistedHistory,
                        onBoardSelected = { board -> selectedBoardId = board.id },
                        onAddBoard = { name, url ->
                            val normalizedUrl = normalizeBoardUrl(url)
                            if (persistedBoards.none { it.url.equals(normalizedUrl, ignoreCase = true) }) {
                                val newBoard = createCustomBoard(
                                    name = name,
                                    url = normalizedUrl,
                                    existingBoards = persistedBoards
                                )
                                coroutineScope.launch {
                                    stateStore.setBoards(persistedBoards + newBoard)
                                }
                            }
                        },
                        onMenuAction = { },
                        onHistoryEntrySelected = openHistoryEntry,
                        onHistoryEntryDismissed = dismissHistoryEntry,
                        onHistoryCleared = clearHistory,
                        onHistoryRefresh = refreshHistoryEntries,
                        onBoardDeleted = { board ->
                            coroutineScope.launch {
                                stateStore.setBoards(persistedBoards.filter { it.id != board.id })
                            }
                        },
                        onBoardsReordered = { reorderedBoards ->
                            coroutineScope.launch {
                                stateStore.setBoards(reorderedBoards)
                            }
                        }
                    )
                }

                selectedThreadId == null -> {
                    val boardRepository = selectedBoard.takeUnless { it.isMockBoard() }?.let { remoteBoardRepository }
                    CatalogScreen(
                        board = selectedBoard,
                        history = persistedHistory,
                        onBack = {
                            selectedThreadId = null
                            selectedThreadTitle = null
                            selectedThreadReplies = null
                            selectedThreadThumbnailUrl = null
                            selectedThreadUrl = null
                            selectedBoardId = null
                        },
                        onThreadSelected = { item ->
                            selectedThreadId = item.id
                            selectedThreadTitle = item.title
                            selectedThreadReplies = item.replyCount
                            selectedThreadThumbnailUrl = item.thumbnailUrl
                            selectedThreadUrl = item.threadUrl
                        },
                        onHistoryEntrySelected = openHistoryEntry,
                        onHistoryEntryDismissed = dismissHistoryEntry,
                        onHistoryEntryUpdated = updateHistoryEntry,
                        onHistoryRefresh = refreshHistoryEntries,
                        onHistoryCleared = clearHistory,
                        repository = boardRepository
                    )
                }

                else -> {
                    val activeThreadId = selectedThreadId ?: return@Surface
                    // selectedBoard should be non-null at this point due to when block structure
                    // but add explicit check for safety
                    val currentBoard = selectedBoard ?: return@Surface
                    val historyTitle = selectedThreadTitle ?: "無題"
                    val historyThreadUrl = selectedThreadUrl ?: currentBoard.url
                    val historyReplies = selectedThreadReplies ?: 0
                    val historyThumbnail = selectedThreadThumbnailUrl.orEmpty()
                    val existingHistoryEntry = persistedHistory.firstOrNull { it.threadId == activeThreadId }

                    // Reduce LaunchedEffect dependencies to only essential keys to prevent excessive coroutine creation
                    // Use a key that only changes when navigating to a different thread
                    LaunchedEffect(activeThreadId) {
                        val entry = ThreadHistoryEntry(
                            threadId = activeThreadId,
                            boardId = currentBoard.id,
                            title = historyTitle,
                            titleImageUrl = historyThumbnail,
                            boardName = currentBoard.name,
                            boardUrl = historyThreadUrl,
                            lastVisitedEpochMillis = Clock.System.now().toEpochMilliseconds(),
                            replyCount = historyReplies,
                            lastReadItemIndex = existingHistoryEntry?.lastReadItemIndex ?: 0,
                            lastReadItemOffset = existingHistoryEntry?.lastReadItemOffset ?: 0
                        )
                        val updatedHistory = buildList {
                            add(entry)
                            addAll(persistedHistory.filterNot { it.threadId == activeThreadId })
                        }
                        stateStore.setHistory(updatedHistory)
                    }

                    val persistScrollPosition: (String, Int, Int) -> Unit = { targetThreadId, index, offset ->
                        coroutineScope.launch {
                            // Use a synchronized update by reading current state within the same coroutine
                            stateStore.updateHistoryScrollPosition(
                                threadId = targetThreadId,
                                index = index,
                                offset = offset,
                                boardId = currentBoard.id,
                                title = historyTitle,
                                titleImageUrl = historyThumbnail,
                                boardName = currentBoard.name,
                                boardUrl = historyThreadUrl,
                                replyCount = historyReplies
                            )
                        }
                    }

                    val boardRepository = currentBoard.takeUnless { it.isMockBoard() }?.let { remoteBoardRepository }
                        ThreadScreen(
                            board = currentBoard,
                            history = persistedHistory,
                            threadId = activeThreadId,
                            threadTitle = selectedThreadTitle,
                            initialReplyCount = selectedThreadReplies,
                            onBack = {
                            selectedThreadId = null
                            selectedThreadTitle = null
                            selectedThreadReplies = null
                            selectedThreadThumbnailUrl = null
                            selectedThreadUrl = null
                        },
                        onHistoryEntrySelected = openHistoryEntry,
                        onHistoryEntryDismissed = dismissHistoryEntry,
                            onHistoryEntryUpdated = updateHistoryEntry,
                            onHistoryRefresh = refreshHistoryEntries,
                            onScrollPositionPersist = persistScrollPosition,
                            repository = boardRepository
                        )
                }
            }
        }
    }
}

private fun BoardSummary.isMockBoard(): Boolean {
    return url.contains("example.com", ignoreCase = true)
}

private fun createCustomBoard(
    name: String,
    url: String,
    existingBoards: List<BoardSummary>
): BoardSummary {
    val trimmedName = name.trim().ifBlank { "新しい板" }
    val normalizedUrl = url.trim()
    val boardId = generateBoardId(normalizedUrl, trimmedName, existingBoards)
    return BoardSummary(
        id = boardId,
        name = trimmedName,
        category = "カスタム",
        url = normalizedUrl,
        description = "$trimmedName のユーザー追加板",
        pinned = false
    )
}

private fun generateBoardId(
    url: String,
    fallbackName: String,
    existingBoards: List<BoardSummary>
): String {
    val candidates = buildList {
        extractPathSegment(url)?.let { add(it) }
        extractSubdomain(url)?.let { add(it) }
        val nameSlug = slugify(fallbackName)
        if (nameSlug.isNotBlank()) add(nameSlug)
    }
    val base = candidates.firstOrNull { it.isNotBlank() } ?: "board"
    var candidate = base
    var suffix = 1
    while (existingBoards.any { it.id.equals(candidate, ignoreCase = true) }) {
        candidate = "$base$suffix"
        suffix += 1
    }
    return candidate
}

private fun extractPathSegment(url: String): String? {
    val withoutScheme = url.substringAfter("//", url)
    val slashIndex = withoutScheme.indexOf('/')
    if (slashIndex == -1) return null
    val path = withoutScheme.substring(slashIndex + 1)
        .substringBefore('?')
        .substringBefore('#')
    if (path.isBlank()) return null
    val firstSegment = path.split('/')
        .firstOrNull { it.isNotBlank() }
        ?: return null
    return slugify(firstSegment)
}

private fun extractSubdomain(url: String): String? {
    val withoutScheme = url.substringAfter("//", url)
    val host = withoutScheme.substringBefore('/')
    if (host.isBlank()) return null
    val parts = host.split('.')
    if (parts.isEmpty()) return null
    val candidate = when {
        parts.size >= 3 -> parts.first()
        else -> parts.first()
    }
    return slugify(candidate)
}

private fun slugify(value: String): String {
    val normalized = value.lowercase()
    val builder = StringBuilder()
    normalized.forEach { ch ->
        when {
            ch.isLetterOrDigit() -> builder.append(ch)
            ch == '-' || ch == '_' -> builder.append(ch)
        }
    }
    return builder.toString()
}

private fun normalizeBoardUrl(raw: String): String {
    val trimmed = raw.trim()

    // Keep user's protocol choice - don't force HTTPS conversion
    // The network security config will handle cleartext traffic restrictions
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> {
            // Log warning but keep HTTP if user explicitly specified it
            println("FutachaApp: Warning - HTTP URL detected. Connection may fail if cleartext traffic is disabled: $trimmed")
            trimmed
        }
        else -> "https://$trimmed"  // Default to HTTPS for security
    }
}
