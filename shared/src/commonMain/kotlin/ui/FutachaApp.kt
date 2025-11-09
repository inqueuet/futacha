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
            val remoteBoardRepository = remember {
                createRemoteBoardRepository()
            }

            // Clean up repository when composable leaves composition
            androidx.compose.runtime.DisposableEffect(remoteBoardRepository) {
                onDispose {
                    // Repository cleanup if needed
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
                        onHistoryEntryDismissed = dismissHistoryEntry
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
                        repository = boardRepository
                    )
                }

                else -> {
                    val activeThreadId = selectedThreadId ?: return@Surface
                    val historyTitle = selectedThreadTitle ?: "無題"
                    val historyThreadUrl = selectedThreadUrl ?: selectedBoard.url
                    val historyReplies = selectedThreadReplies ?: 0
                    val historyThumbnail = selectedThreadThumbnailUrl.orEmpty()
                    val existingHistoryEntry = persistedHistory.firstOrNull { it.threadId == activeThreadId }

                    // Reduce LaunchedEffect dependencies to only essential keys to prevent excessive coroutine creation
                    LaunchedEffect(activeThreadId, selectedBoard.id) {
                        val entry = ThreadHistoryEntry(
                            threadId = activeThreadId,
                            boardId = selectedBoard.id,
                            title = historyTitle,
                            titleImageUrl = historyThumbnail,
                            boardName = selectedBoard.name,
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
                                boardId = selectedBoard.id,
                                title = historyTitle,
                                titleImageUrl = historyThumbnail,
                                boardName = selectedBoard.name,
                                boardUrl = historyThreadUrl,
                                replyCount = historyReplies
                            )
                        }
                    }

                    val boardRepository = selectedBoard.takeUnless { it.isMockBoard() }?.let { remoteBoardRepository }
                    ThreadScreen(
                        board = selectedBoard,
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

    // Force HTTPS for security
    return when {
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("http://", ignoreCase = true) -> {
            println("FutachaApp: Converting HTTP to HTTPS for security: $trimmed")
            trimmed.replaceFirst("http://", "https://", ignoreCase = true)
        }
        else -> "https://$trimmed"
    }
}
