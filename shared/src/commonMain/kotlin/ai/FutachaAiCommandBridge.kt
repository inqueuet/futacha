package com.valoser.futacha.shared.ai

import com.valoser.futacha.shared.util.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

object FutachaAiCommandBridge {
    private const val TAG = "FutachaAiCommandBridge"
    private const val COMMAND_BUFFER_CAPACITY = 128
    private val commandChannel = Channel<FutachaAiCommand>(capacity = COMMAND_BUFFER_CAPACITY)

    // receiveAsFlow distributes elements across collectors; keep this flow single-consumer.
    // FutachaApp is the intended collector, and extra collectors would steal commands.
    val commands: Flow<FutachaAiCommand> = commandChannel.receiveAsFlow()

    fun enqueue(command: FutachaAiCommand): Boolean {
        val result = commandChannel.trySend(command)
        if (result.isFailure) {
            Logger.w(
                TAG,
                "Dropped AI command because buffer is full or closed: action=${command.action.id}, source=${command.source}"
            )
        }
        return result.isSuccess
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
