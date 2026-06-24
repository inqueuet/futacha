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

internal fun resolvePendingAiScreenCommand(
    current: FutachaAiCommand?,
    incoming: FutachaAiCommand
): FutachaAiCommand {
    return current ?: incoming
}

internal fun shouldReplacePendingAiConfirmation(
    current: FutachaAiConfirmationRequest?,
    outcome: FutachaAiCommandOutcome
): Boolean {
    return current == null && outcome is FutachaAiCommandOutcome.NeedsConfirmation
}
