package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandArrivals
import com.valoser.futacha.shared.ai.boardUrlParameter
import com.valoser.futacha.shared.ai.buildFutachaAiConfirmationMessage
import com.valoser.futacha.shared.ai.threadUrlParameter
import com.valoser.futacha.shared.ai.toConfirmationValue
import com.valoser.futacha.shared.compat.CompatBoard
import com.valoser.futacha.shared.compat.canonicalizeBoardUrl
import com.valoser.futacha.shared.ui.AI_UNTRUSTED_THREAD_URL_MESSAGE
import com.valoser.futacha.shared.ui.AiCommandHoldDecision
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.buildAiScreenCommandDropMessage
import com.valoser.futacha.shared.ui.isAiThreadUrlAllowed
import com.valoser.futacha.shared.ui.isAllowedWhenAiCommandsDisabled
import com.valoser.futacha.shared.ui.resolveAiCommandHoldDecision
import com.valoser.futacha.shared.ui.superviseAiScreenCommand
import kotlin.time.TimeSource

// Gates for platform AI commands in the compatibility workspace, matching the
// modern mode's router (C-1 lock hold, S-1 thread URL host).

/** Same wording as the modern mode's router (S-1). */
internal const val COMPAT_AI_UNTRUSTED_THREAD_URL_MESSAGE = AI_UNTRUSTED_THREAD_URL_MESSAGE

/**
 * S-1: the message to show when [command] names a thread URL on a host other
 * than Futaba. Compatibility boards are always on 2chan.net
 * ([com.valoser.futacha.shared.compat.canonicalizeBoardUrl]), so no registered
 * board widens the allowed hosts. Null when the command may proceed.
 */
internal fun compatPlatformAiThreadUrlRejection(command: FutachaAiCommand): String? {
    val url = command.threadUrlParameter() ?: return null
    return if (isAiThreadUrlAllowed(url, emptyList())) null else COMPAT_AI_UNTRUSTED_THREAD_URL_MESSAGE
}

/**
 * Remembers when the platform AI command now delivered first reached the
 * workspace. The delivery effect restarts (and the host may hide the command
 * while the lock screen is shown); keeping the first arrival lets a command
 * that waited behind the lock expire like in the modern mode (C-1).
 */
internal class CompatPlatformAiArrivalTracker(
    private val now: () -> TimeSource.Monotonic.ValueTimeMark = { TimeSource.Monotonic.markNow() }
) {
    private var command: FutachaAiCommand? = null
    private var arrivedAt: TimeSource.Monotonic.ValueTimeMark? = null

    fun arrivalOf(command: FutachaAiCommand): TimeSource.Monotonic.ValueTimeMark {
        val known = arrivedAt
        if (this.command === command && known != null) return known
        return (FutachaAiCommandArrivals.arrivalOf(command) ?: now()).also { mark ->
            this.command = command
            arrivedAt = mark
        }
    }
}

/**
 * Holds a command (unconsumed, not run) until the app is unlocked, read
 * outside composition, then decides whether it may still run.
 */
internal suspend fun holdCompatPlatformAiCommand(
    arrivedAt: TimeSource.Monotonic.ValueTimeMark,
    appLock: FutachaAppLockHolder
): AiCommandHoldDecision {
    appLock.awaitUnlocked()
    return resolveAiCommandHoldDecision(
        ageMillis = arrivedAt.elapsedNow().inWholeMilliseconds,
        maxAgeMillis = null,
        wasHeldByLock = appLock.wasHeldByLock(arrivedAt)
    )
}

/**
 * C4-4: the compatibility workspace follows the same "AIアプリ操作" setting as
 * the modern mode, with the same exceptions (settings screens, watch open and
 * read-aloud stop). The setting has no switch in this mode's own settings, so
 * the message names where it is. Null when the command may proceed.
 */
internal fun compatPlatformAiDisabledRejection(command: FutachaAiCommand, isAiCommandEnabled: Boolean): String? =
    if (isAiCommandEnabled || command.isAllowedWhenAiCommandsDisabled()) null else COMPAT_AI_COMMANDS_DISABLED_MESSAGE

internal const val COMPAT_AI_COMMANDS_DISABLED_MESSAGE =
    "AIアプリ操作は設定でOFFです。ふたちゃモードの設定画面で「AIアプリ操作」をONにしてください（この設定は両モード共通です）。"

/**
 * S4-2: the confirmation text, with the values the command will store. A new
 * board is stored under its canonical URL and, without a name, the board path.
 */
internal fun compatPlatformAiConfirmationMessage(command: FutachaAiCommand): String {
    if (command.action != FutachaAiAction.AddBoard) return buildFutachaAiConfirmationMessage(command)
    val url = command.boardUrlParameter()
    val canonical = url?.let(::canonicalizeBoardUrl)
    val name = command.parameter("name", "board", "title", "label")?.takeIf { it.isNotBlank() }
        ?: canonical?.let(::compatPlatformAiDefaultBoardName)
    return buildFutachaAiConfirmationMessage(
        command,
        listOf(
            "板名: ${name?.toConfirmationValue() ?: "（指定なし）"}",
            "URL: ${(canonical ?: url)?.toConfirmationValue() ?: "（指定なし）"}"
        )
    )
}

/**
 * S4-3/C4-5: a command that opens a thread of a board not in the list asks
 * "未登録の板" first, like a thread link. This holds for every source (links,
 * Siri / Shortcuts, the watch): the modern mode never adds a board from such a
 * command, and through the command path the board used to be registered
 * silently.
 */
internal fun compatPlatformAiNeedsBoardConsent(
    board: CompatBoard,
    boards: List<CompatBoard>
): Boolean = boards.none { it.canonicalUrl.equals(board.canonicalUrl, ignoreCase = true) }

/** The name of a board added without one: its path ("b" for https://may.2chan.net/b/). */
internal fun compatPlatformAiDefaultBoardName(canonicalBoardUrl: String): String =
    canonicalBoardUrl.trimEnd('/').substringAfterLast('/')

/**
 * S4-5: a confirmation already on screen is kept, as in the modern mode; a
 * later command needing confirmation is dropped instead of replacing the text
 * (and the command "続行" runs) under the user's finger.
 */
internal fun shouldShowCompatPlatformAiConfirmation(current: FutachaAiCommand?): Boolean = current == null

/**
 * A command forwarded to a compatibility screen (thread or catalog) and not yet
 * consumed (C4-1/E4-1). [deliverable] is the command the screen may run: only
 * after [supervise] has checked it since the last unlock, so a command that is
 * too old or waited behind the lock never reaches the screen.
 */
internal class CompatAiScreenCommandSlot(
    private val now: () -> TimeSource.Monotonic.ValueTimeMark = { TimeSource.Monotonic.markNow() }
) {
    var pending: FutachaAiCommand? by mutableStateOf(null)
        private set
    private var released: FutachaAiCommand? by mutableStateOf(null)
    private var forwardedAt: TimeSource.Monotonic.ValueTimeMark? = null

    fun forward(command: FutachaAiCommand) {
        released = null
        forwardedAt = now()
        pending = command
    }

    fun consume(command: FutachaAiCommand) {
        if (pending === command) clear()
    }

    fun clear() {
        released = null
        pending = null
    }

    fun deliverable(isAppUnlocked: Boolean): FutachaAiCommand? =
        pending?.takeIf { isAppUnlocked && it === released }

    /**
     * Run from an effect keyed on [pending] (identity) and on the lock. Returns
     * the message for a command it dropped, or null.
     */
    suspend fun supervise(appLock: FutachaAppLockHolder): String? {
        released = null
        val command = pending ?: return null
        val decision = superviseAiScreenCommand(
            forwardedAt = forwardedAt ?: now(),
            appLock = appLock,
            onRelease = { if (pending === command) released = command }
        )
        if (pending !== command) return null
        clear()
        return buildAiScreenCommandDropMessage(command, decision)
    }
}
