package com.valoser.futacha.shared.ui

import com.valoser.futacha.shared.ai.FutachaAiCommand
import com.valoser.futacha.shared.state.AppStateStore
import com.valoser.futacha.shared.ui.board.ALPHA_AI_COMMAND_ENABLED
import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.TimeSource

/** Upper bound for platform AI commands waiting for the active profile. */
internal const val PLATFORM_AI_COMMAND_QUEUE_MAX = 32

/**
 * Appends [command] to the queue of platform AI commands.  Hosts that inject
 * commands one at a time hand the head to the active profile; keeping a
 * queue (instead of one slot) means a command arriving before the previous
 * one was consumed is no longer overwritten.  When the queue is full the
 * oldest waiting command after the head is dropped, never the head itself,
 * because the head may already be executing.
 */
internal fun <T : Any> enqueuePlatformAiCommand(
    queue: List<T>,
    command: T,
    maxSize: Int = PLATFORM_AI_COMMAND_QUEUE_MAX
): List<T> {
    val appended = queue + command
    if (appended.size <= maxSize.coerceAtLeast(2)) return appended
    return listOf(appended.first()) + appended.drop(appended.size - (maxSize.coerceAtLeast(2) - 1))
}

/**
 * Removes [consumed] when it is the head of the queue (identity match).
 * Each delivery is consumed separately, including repeated id-less shortcuts.
 */
internal fun <T : Any> consumePlatformAiCommand(
    queue: List<T>,
    consumed: T
): List<T> {
    if (queue.firstOrNull() !== consumed) return queue
    return queue.drop(1)
}

/** An effect follows delivery identity so two equal "next" commands both run. */
internal class AiCommandEffectKey(private val command: FutachaAiCommand?) {
    override fun equals(other: Any?): Boolean = other is AiCommandEffectKey && command === other.command
    override fun hashCode(): Int = 0
}

/**
 * How long a command that waited behind the app lock may still run after the
 * unlock. A command that arrived while the phone was in a pocket must not
 * open a thread or start reading minutes later (C-1).
 */
internal const val AI_COMMAND_LOCK_HOLD_TTL_MILLIS = 60_000L

/**
 * How long a command forwarded to a screen may wait to be consumed. It covers
 * a thread load (up to about 75 s) so a slow thread does not lose the command
 * (C-5).
 */
internal const val AI_SCREEN_COMMAND_EXPIRY_MILLIS = 90_000L

internal enum class AiCommandHoldDecision {
    Run,
    /** Too old for its sender ([FutachaAiQueuedCommand.maxAgeMillis]); dropped quietly. */
    DropExpired,
    /** Waited behind the app lock longer than [AI_COMMAND_LOCK_HOLD_TTL_MILLIS]. */
    DropHeldByLock
}

internal fun resolveAiCommandHoldDecision(
    ageMillis: Long,
    maxAgeMillis: Long?,
    wasHeldByLock: Boolean,
    lockHoldTtlMillis: Long = AI_COMMAND_LOCK_HOLD_TTL_MILLIS
): AiCommandHoldDecision = when {
    maxAgeMillis != null && ageMillis > maxAgeMillis -> AiCommandHoldDecision.DropExpired
    wasHeldByLock && ageMillis > lockHoldTtlMillis -> AiCommandHoldDecision.DropHeldByLock
    else -> AiCommandHoldDecision.Run
}

internal fun buildAiCommandHeldByLockMessage(command: FutachaAiCommand): String =
    "起動ロック中に届いた「${command.action.label}」は、時間が経ったため実行しませんでした"

internal fun buildAiScreenCommandExpiredMessage(command: FutachaAiCommand): String =
    "「${command.action.label}」は対象画面の準備が終わらなかったため実行しませんでした"

/**
 * C4-1/E4-1: the decision for a command forwarded to a screen [ageMillis] ago,
 * taken when the app is unlocked. [wasHeldByLock]: the password was entered
 * after it was forwarded, so the lock-hold rule of the reception applies.
 */
internal fun resolveAiScreenCommandHoldDecision(
    ageMillis: Long,
    wasHeldByLock: Boolean,
    expiryMillis: Long = AI_SCREEN_COMMAND_EXPIRY_MILLIS,
    lockHoldTtlMillis: Long = AI_COMMAND_LOCK_HOLD_TTL_MILLIS
): AiCommandHoldDecision = when {
    wasHeldByLock && ageMillis > lockHoldTtlMillis -> AiCommandHoldDecision.DropHeldByLock
    ageMillis > expiryMillis -> AiCommandHoldDecision.DropExpired
    else -> AiCommandHoldDecision.Run
}

/**
 * Supervises a command forwarded to a screen at [forwardedAt] (C4-1/E4-1).
 *
 * The age is a real monotonic mark, so time spent stopped (Android pauses
 * recomposition after ON_STOP, so an effect started then only began at the
 * next resume) or behind the lock counts. Waits for the unlock (read outside
 * composition), then drops the command when it is too old; otherwise calls
 * [onRelease] (the screen may run it from now on) and returns
 * [AiCommandHoldDecision.DropExpired] once the expiry has passed. Never returns
 * [AiCommandHoldDecision.Run]. Call it from an effect keyed on the command and
 * on the lock, so a relock re-checks the command after the next unlock.
 */
internal suspend fun superviseAiScreenCommand(
    forwardedAt: TimeSource.Monotonic.ValueTimeMark,
    appLock: FutachaAppLockHolder,
    onRelease: () -> Unit,
    expiryMillis: Long = AI_SCREEN_COMMAND_EXPIRY_MILLIS
): AiCommandHoldDecision {
    appLock.awaitUnlocked()
    val ageMillis = forwardedAt.elapsedNow().inWholeMilliseconds
    val decision = resolveAiScreenCommandHoldDecision(
        ageMillis = ageMillis,
        wasHeldByLock = appLock.wasHeldByLock(forwardedAt),
        expiryMillis = expiryMillis
    )
    if (decision != AiCommandHoldDecision.Run) return decision
    onRelease()
    delay((expiryMillis - ageMillis).coerceAtLeast(0L) + 1L)
    return AiCommandHoldDecision.DropExpired
}

/** What the user is told when a forwarded command is dropped. */
internal fun buildAiScreenCommandDropMessage(
    command: FutachaAiCommand,
    decision: AiCommandHoldDecision
): String? = when (decision) {
    AiCommandHoldDecision.Run -> null
    AiCommandHoldDecision.DropExpired -> buildAiScreenCommandExpiredMessage(command)
    AiCommandHoldDecision.DropHeldByLock -> buildAiCommandHeldByLockMessage(command)
}

/**
 * The stored "AIアプリ操作" setting, read when a command runs (C4-2). The value
 * observed in composition starts as OFF until the store has loaded, so a
 * command arriving at startup was rejected with "設定でOFFです" and lost.
 * [fallback] is used only when the store cannot be read.
 */
internal suspend fun readPersistedAiCommandEnabled(
    stateStore: AppStateStore?,
    fallback: Boolean
): Boolean {
    val store = stateStore ?: return fallback
    return try {
        store.isAiCommandEnabled.first() && ALPHA_AI_COMMAND_ENABLED
    } catch (e: CancellationException) {
        throw e
    } catch (error: Throwable) {
        Logger.e("FutachaAiCommand", "Failed to read the AI command setting", error)
        fallback
    }
}
