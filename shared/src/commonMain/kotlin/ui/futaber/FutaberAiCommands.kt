package com.valoser.futacha.shared.ui.futaber

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.ai.FutachaAiCommandArrivals
import com.valoser.futacha.shared.ai.FutachaAiCommandBridge
import com.valoser.futacha.shared.ai.parseFutachaAiDeepLink
import com.valoser.futacha.shared.ui.AiCommandEffectKey
import com.valoser.futacha.shared.ui.AiCommandHoldDecision
import com.valoser.futacha.shared.ui.FutachaAppLockHolder
import com.valoser.futacha.shared.ui.buildAiCommandHeldByLockMessage
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * AI commands (Siri / Shortcuts / Wear OS / `futacha://ai` links) arrive at the app root. They must be taken off the
 * queue whatever happens: on Android the bridge holds 128 of them and nobody else reads it while this mode is active (a
 * later switch to ふたちゃ / としあき(仮) replayed everything that had piled up), and on iOS an unconsumed head blocks
 * the queue until further Shortcuts are refused.
 *
 * The mode runs a subset of them (FutaberAiRouter.kt); a command outside it is answered with this notice and consumed.
 */
internal fun futaberAiUnsupportedNotice(command: FutachaAiCommand): String =
    "「${command.action.label}」はふたばー風モードでは使えません。ふたちゃモードで実行してください"

/** What the person is told when the link of an AI command cannot be read. */
internal const val FUTABER_AI_LINK_UNREADABLE_NOTICE = "AI操作のURLを解釈できませんでした"

/**
 * The notice for a command that reached this mode, or null for one that is dropped without a word (it was too old
 * for its sender). A command that waited behind the app lock for too long is dropped with the same message the other
 * modes give, never run (and never answered as if it had just arrived).
 */
internal fun futaberAiCommandNotice(command: FutachaAiCommand, decision: AiCommandHoldDecision): String? = when (decision) {
    AiCommandHoldDecision.Run -> futaberAiUnsupportedNotice(command)
    AiCommandHoldDecision.DropExpired -> null
    AiCommandHoldDecision.DropHeldByLock -> buildAiCommandHeldByLockMessage(command)
}

/** The notice for an AI link: the command it holds is not supported here, or the link says nothing readable. */
internal fun futaberAiDeepLinkNotice(raw: String): String {
    val command = parseFutachaAiDeepLink(raw, source = "platform") ?: return FUTABER_AI_LINK_UNREADABLE_NOTICE
    return futaberAiUnsupportedNotice(command)
}

/**
 * Takes every command off the Android bridge (it holds 128; whoever does not read it lets them pile up) and hands it to
 * [inbox]. Reads only while the app is unlocked, so commands sent behind the lock wait in the bridge, as in the other
 * modes. Runs until cancelled.
 */
internal suspend fun futaberReceiveAiBridgeCommands(inbox: FutaberAiInbox, appLock: FutachaAppLockHolder, onNotice: (String) -> Unit) {
    while (true) {
        appLock.awaitUnlocked()
        val queued = FutachaAiCommandBridge.receiveQueued()
        inbox.deliver(queued.command, queued.enqueuedAt, queued.maxAgeMillis, appLock, onNotice = onNotice)
    }
}

/**
 * Takes the AI commands this mode receives and hands each one to the mode's screen through the returned inbox once the
 * app is unlocked (X10-1: the screen runs the commands it supports and answers the others with
 * [futaberAiUnsupportedNotice]). [consumeAiCommandBridge] is false where the host owns the bridge itself and passes the
 * commands in as [platformAiCommand] (iOS). Draws nothing.
 *
 * The effects wait for the unlock outside composition (the lock does not restart them). A platform command is consumed
 * only after it was handed over: consuming restarts the effect that holds it. A link is handed over from a coroutine of
 * its own, since consuming it restarts its effect.
 */
@Composable
internal fun FutaberAiCommandSink(
    appLock: FutachaAppLockHolder,
    platformAiDeepLink: String?,
    onPlatformAiDeepLinkConsumed: (String) -> Unit,
    platformAiCommand: FutachaAiCommand?,
    onPlatformAiCommandConsumed: (FutachaAiCommand) -> Unit,
    consumeAiCommandBridge: Boolean,
    onNotice: (String) -> Unit
): FutaberAiInbox {
    val inbox = remember { FutaberAiInbox() }
    val scope = rememberCoroutineScope()
    val notice = rememberUpdatedState(onNotice)
    val deepLinkConsumed = rememberUpdatedState(onPlatformAiDeepLinkConsumed)
    val commandConsumed = rememberUpdatedState(onPlatformAiCommandConsumed)

    LaunchedEffect(platformAiDeepLink) {
        val raw = platformAiDeepLink?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val arrivedAt = TimeSource.Monotonic.markNow()
        appLock.awaitUnlocked()
        val command = parseFutachaAiDeepLink(raw, source = "platform")
        if (command == null) {
            notice.value(FUTABER_AI_LINK_UNREADABLE_NOTICE)
        } else {
            scope.launch { inbox.deliver(command, arrivedAt, null, appLock) { notice.value(it) } }
        }
        deepLinkConsumed.value(raw)
    }

    LaunchedEffect(AiCommandEffectKey(platformAiCommand)) {
        val command = platformAiCommand ?: return@LaunchedEffect
        val arrivedAt = FutachaAiCommandArrivals.arrivalOf(command) ?: TimeSource.Monotonic.markNow()
        inbox.deliver(command, arrivedAt, null, appLock) { notice.value(it) }
        commandConsumed.value(command)
    }

    if (consumeAiCommandBridge) {
        LaunchedEffect(Unit) { futaberReceiveAiBridgeCommands(inbox, appLock) { notice.value(it) } }
    }
    return inbox
}
