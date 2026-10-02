package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiConfirmationRequest
import com.valoser.futacha.shared.ai.FutachaAiCommandOutcome

internal fun shouldStartAiBridgeCommand(
    command: FutachaAiCommand,
    isHistoryRefreshCommandRunning: Boolean
): Boolean {
    return !shouldLaunchAiCommandFromBridge(command) || !isHistoryRefreshCommandRunning
}

/**
 * The newest forwarded command replaces one the screen has not consumed yet: a
 * command that cannot run (no thread open, read-aloud with nothing to read)
 * must not block every later command (H1).
 */
@Suppress("UNUSED_PARAMETER")
internal fun resolvePendingAiScreenCommand(
    current: FutachaAiCommand?,
    incoming: FutachaAiCommand
): FutachaAiCommand {
    return incoming
}

internal fun shouldReplacePendingAiConfirmation(
    current: FutachaAiConfirmationRequest?,
    outcome: FutachaAiCommandOutcome
): Boolean {
    return current == null && outcome is FutachaAiCommandOutcome.NeedsConfirmation
}

private val AI_COMMAND_THREAD_URL_ID_REGEX = Regex("""/res/(\d+)\.html?""", RegexOption.IGNORE_CASE)

/**
 * Whether a screen command may run on the thread [threadId] of [boardId]. A
 * command that names its thread (watch commands carry boardId/threadId) runs only
 * there; one without a target runs on the thread on screen.
 */
internal fun isAiCommandForThread(
    command: FutachaAiCommand,
    boardId: String,
    threadId: String
): Boolean {
    val requestedThreadId = command.parameter("thread", "threadId", "thread_id", "threadNo", "thread_no")
        ?: command.parameter("threadUrl", "thread_url", "url", "link")
            ?.let { AI_COMMAND_THREAD_URL_ID_REGEX.find(it)?.groupValues?.getOrNull(1) }
    if (requestedThreadId != null && requestedThreadId != threadId) return false
    val requestedBoardId = command.parameter("boardId", "board_id")
    if (requestedThreadId != null && requestedBoardId != null && boardId.isNotBlank() &&
        !requestedBoardId.equals(boardId, ignoreCase = true)
    ) {
        return false
    }
    return true
}
