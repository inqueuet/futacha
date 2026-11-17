package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.time.TimeSource

/**
 * A bridge command with the time it was enqueued. [maxAgeMillis] lets a sender
 * whose command is only useful right away (a watch tap) have it dropped when
 * the app UI picks it up much later instead of replaying it (C-4).
 */
internal class FutachaAiQueuedCommand(
    val command: FutachaAiCommand,
    internal val enqueuedAt: TimeSource.Monotonic.ValueTimeMark,
    internal val maxAgeMillis: Long?
) {
    internal fun ageMillis(): Long = enqueuedAt.elapsedNow().inWholeMilliseconds

    internal fun isExpired(): Boolean = maxAgeMillis != null && ageMillis() > maxAgeMillis
}

object FutachaAiCommandBridge {
    private const val TAG = "FutachaAiCommandBridge"
    private const val COMMAND_BUFFER_CAPACITY = 128
    private val commandChannel = Channel<FutachaAiQueuedCommand>(capacity = COMMAND_BUFFER_CAPACITY)

    // receiveAsFlow distributes elements across collectors; keep this flow single-consumer.
    // FutachaApp is the intended collector, and extra collectors would steal commands.
    val commands: Flow<FutachaAiCommand> = commandChannel.receiveAsFlow().mapNotNull { queued ->
        if (queued.isExpired()) {
            logExpired(queued)
            null
        } else {
            queued.command
        }
    }

    /**
     * Receives the next command with its enqueue time. Single consumer like
     * [commands]: the caller decides itself when to receive, so a locked app
     * can leave commands in the channel instead of taking them out (C-1).
     */
    internal suspend fun receiveQueued(): FutachaAiQueuedCommand {
        while (true) {
            val queued = commandChannel.receive()
            if (!queued.isExpired()) return queued
            logExpired(queued)
        }
    }

    fun enqueue(command: FutachaAiCommand): Boolean = enqueueBounded(command, maxAgeMillis = null)

    /** Enqueues a command that is dropped if it is not received within [maxAgeMillis]. */
    fun enqueue(command: FutachaAiCommand, maxAgeMillis: Long): Boolean =
        enqueueBounded(command, maxAgeMillis = maxAgeMillis.coerceAtLeast(0L))

    private fun enqueueBounded(command: FutachaAiCommand, maxAgeMillis: Long?): Boolean {
        val boundedCommand = command.copy(
            parameters = sanitizeFutachaAiCommandParameters(command.parameters),
            source = command.source.filterNot { it == '\u0000' }.trim().take(64).ifBlank { "unknown" }
        )
        val result = commandChannel.trySend(
            FutachaAiQueuedCommand(boundedCommand, TimeSource.Monotonic.markNow(), maxAgeMillis)
        )
        if (result.isFailure) {
            Logger.w(
                TAG,
                "Dropped AI command because buffer is full or closed: action=${boundedCommand.action.id}, source=${boundedCommand.source}"
            )
        }
        return result.isSuccess
    }

    private fun logExpired(queued: FutachaAiQueuedCommand) {
        Logger.w(
            TAG,
            "Dropped expired AI command: action=${queued.command.action.id}, source=${queued.command.source}, ageMillis=${queued.ageMillis()}"
        )
    }

    fun enqueueIntentCommand(
        actionId: String,
        board: String,
        thread: String,
        query: String,
        url: String,
        value: String,
        name: String,
        email: String,
        subject: String,
        comment: String,
        password: String,
        source: String
    ): Boolean {
        val action = FutachaAiAction.fromId(actionId) ?: return false
        val parameters = sanitizeFutachaAiCommandParameters(
            mapOf(
                "board" to board,
                "thread" to thread,
                "query" to query,
                "url" to url,
                "value" to value,
                "name" to name,
                "email" to email,
                "subject" to subject,
                "comment" to comment,
                "password" to password
            )
        )
        return enqueue(
            FutachaAiCommand(
                action = action,
                parameters = parameters,
                source = source
            )
        )
    }

    fun enqueueDeepLink(raw: String, source: String = "bridge"): Boolean {
        val command = parseFutachaAiDeepLink(raw, source) ?: return false
        return enqueue(command)
    }

    fun supportedActionIds(): List<String> = FutachaAiAction.supportedActions.map { it.id }

    internal fun drainBufferedCommandsForTest() {
        while (commandChannel.tryReceive().isSuccess) {
            // Drain commands enqueued by earlier tests.
        }
    }
}

fun enqueueFutachaAiDeepLink(raw: String): Boolean {
    return FutachaAiCommandBridge.enqueueDeepLink(raw, source = "platform")
}
