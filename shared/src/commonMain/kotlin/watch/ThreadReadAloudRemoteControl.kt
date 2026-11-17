package com.valoser.futacha.shared.watch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-level path for watch playback controls that only reduce activity
 * (pause / stop) (C-4, D7).
 *
 * Going through the AI command queue, a pause or stop waited for the app UI
 * (and the app lock), so it ran when the phone was next opened instead of
 * now. A thread screen that is composed — even while its activity is stopped
 * and recomposition is paused — registers here, and the watch handler calls
 * it directly. Starting or seeking playback is not offered: those still need
 * the foreground, unlocked app.
 *
 * [register] / [dispatch] are called on the main thread; the registry itself
 * is safe to read from any thread.
 */
object ThreadReadAloudRemoteControl {
    enum class Command { Pause, Stop }

    fun interface Registration {
        fun unregister()
    }

    private class Controller(
        val boardId: String,
        val boardUrl: String,
        val threadId: String,
        val onCommand: (Command) -> Unit
    )

    private val controllers = MutableStateFlow<List<Controller>>(emptyList())

    fun register(
        boardId: String,
        boardUrl: String,
        threadId: String,
        onCommand: (Command) -> Unit
    ): Registration {
        val controller = Controller(boardId, boardUrl, threadId, onCommand)
        controllers.update { it + controller }
        return Registration { controllers.update { current -> current.filterNot { it === controller } } }
    }

    /**
     * Runs [command] on every registered screen showing that thread. Returns
     * false when no such screen exists (nothing of that thread can be playing).
     */
    fun dispatch(
        command: Command,
        boardId: String,
        boardUrl: String?,
        threadId: String
    ): Boolean {
        val targets = controllers.value.filter { controller ->
            controller.threadId == threadId &&
                (controller.boardId.equals(boardId, ignoreCase = true) ||
                    (!boardUrl.isNullOrBlank() && controller.boardUrl.trimEnd('/')
                        .equals(boardUrl.trimEnd('/'), ignoreCase = true)))
        }
        targets.forEach { it.onCommand(command) }
        return targets.isNotEmpty()
    }

    internal fun clearForTest() {
        controllers.value = emptyList()
    }
}
