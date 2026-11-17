package com.valoser.futacha.wear.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.valoser.futacha.shared.ai.FUTACHA_AI_WATCH_RELAY_PARAMETER
import com.valoser.futacha.shared.ai.FUTACHA_AI_WATCH_RELAY_WEAR_OS
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.buildFutachaAiDeepLink
import com.valoser.futacha.shared.watch.WATCH_COMMAND_KEY
import com.valoser.futacha.shared.watch.WATCH_COMMAND_PATH
import com.valoser.futacha.shared.watch.WATCH_REQUEST_SNAPSHOT_PATH
import com.valoser.futacha.shared.watch.WATCH_UPDATED_AT_KEY
import com.valoser.futacha.shared.watch.WatchCommand
import com.valoser.futacha.shared.watch.WatchCommandType
import com.valoser.futacha.shared.watch.WatchThreadSummary
import com.valoser.futacha.shared.watch.encodeWatchClockRequestPayload
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PhoneCommandClient(
    private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val remoteActivityHelper = RemoteActivityHelper(appContext, mainExecutor)
    private val commandSequence = AtomicInteger(0)

    fun requestSnapshot() {
        sendMessageOrFallback(
            path = WATCH_REQUEST_SNAPSHOT_PATH,
            // The watch clock: the phone answers with its own so the watch can learn the offset
            // (an older phone build ignores the payload).
            payload = encodeWatchClockRequestPayload(System.currentTimeMillis()),
            fallback = { putRequestDataItem(WATCH_REQUEST_SNAPSHOT_PATH) }
        )
    }

    fun requestRefresh() {
        sendCommand(WatchCommand(type = WatchCommandType.Refresh))
    }

    /**
     * [onNotConnected] runs (on the main thread) when no phone is connected.
     * Open and read-aloud commands are then dropped instead of being queued
     * as a DataItem, which the Data Layer would deliver whenever the devices
     * reconnect, possibly hours later.
     */
    fun openBoardOnPhone(
        boardId: String,
        boardUrl: String,
        onNotConnected: () -> Unit = {}
    ) {
        val commandId = nextCommandId()
        val command = WatchCommand(
            type = WatchCommandType.SelectBoard,
            boardId = boardId,
            boardUrl = boardUrl,
            commandId = commandId
        )
        sendOpenCommandAndBringPhoneAppToFront(
            command = command,
            deepLink = buildFutachaAiDeepLink(
                action = FutachaAiAction.OpenBoard,
                parameters = mapOf(
                    "boardId" to boardId,
                    "boardUrl" to boardUrl,
                    "commandId" to commandId,
                    FUTACHA_AI_WATCH_RELAY_PARAMETER to FUTACHA_AI_WATCH_RELAY_WEAR_OS
                )
            ),
            onNotConnected = onNotConnected
        )
    }

    fun openThreadOnPhone(
        boardId: String,
        boardUrl: String,
        threadId: String,
        onNotConnected: () -> Unit = {}
    ) {
        val commandId = nextCommandId()
        val command = WatchCommand(
            type = WatchCommandType.OpenThreadOnPhone,
            boardId = boardId,
            boardUrl = boardUrl,
            threadId = threadId,
            commandId = commandId
        )
        sendOpenCommandAndBringPhoneAppToFront(
            command = command,
            deepLink = buildFutachaAiDeepLink(
                action = FutachaAiAction.OpenThread,
                parameters = mapOf(
                    "boardId" to boardId,
                    "boardUrl" to boardUrl,
                    "threadId" to threadId,
                    "commandId" to commandId,
                    FUTACHA_AI_WATCH_RELAY_PARAMETER to FUTACHA_AI_WATCH_RELAY_WEAR_OS
                )
            ),
            onNotConnected = onNotConnected
        )
    }

    fun startReadAloudOnPhone(thread: WatchThreadSummary, onNotConnected: () -> Unit = {}) {
        sendThreadCommand(WatchCommandType.StartReadAloudOnPhone, thread, onNotConnected)
    }

    fun pauseReadAloudOnPhone(thread: WatchThreadSummary, onNotConnected: () -> Unit = {}) {
        sendThreadCommand(WatchCommandType.PauseReadAloudOnPhone, thread, onNotConnected)
    }

    fun stopReadAloudOnPhone(thread: WatchThreadSummary, onNotConnected: () -> Unit = {}) {
        sendThreadCommand(WatchCommandType.StopReadAloudOnPhone, thread, onNotConnected)
    }

    fun nextReadAloudOnPhone(thread: WatchThreadSummary, onNotConnected: () -> Unit = {}) {
        sendThreadCommand(WatchCommandType.NextReadAloudOnPhone, thread, onNotConnected)
    }

    fun previousReadAloudOnPhone(thread: WatchThreadSummary, onNotConnected: () -> Unit = {}) {
        sendThreadCommand(WatchCommandType.PreviousReadAloudOnPhone, thread, onNotConnected)
    }

    private fun sendThreadCommand(
        type: WatchCommandType,
        thread: WatchThreadSummary,
        onNotConnected: () -> Unit
    ) {
        sendCommand(
            WatchCommand(
                type = type,
                boardId = thread.boardId,
                boardUrl = thread.boardUrl,
                threadId = thread.threadId
            ),
            onNotConnected
        )
    }

    /** A null [onNotConnected] queues the command as a DataItem while disconnected. */
    private fun sendCommand(command: WatchCommand, onNotConnected: (() -> Unit)? = null) {
        val commandWithId = command.takeIf { !it.commandId.isNullOrBlank() }
            ?: command.copy(commandId = nextCommandId())
        val encoded = json.encodeToString(WatchCommand.serializer(), commandWithId)
        val payload = encoded.encodeToByteArray()
        if (payload.size > WATCH_COMMAND_PAYLOAD_MAX_BYTES) {
            Log.w(TAG, "Dropped watch command because payload is too large: ${payload.size} bytes")
            return
        }
        sendMessageOrFallback(
            path = WATCH_COMMAND_PATH,
            payload = payload,
            fallback = { putCommandDataItem(encoded) },
            onNotConnected = onNotConnected
        )
    }

    /**
     * C4-3: the command itself always goes over the Data Layer, which only this
     * watch app can send and the phone runs as a watch command (allowed while
     * "AIアプリ操作" is OFF). The `futacha://ai` link (same commandId, marked as
     * a watch relay) only brings the phone app to the front so the command is
     * picked up; any web page can open such a link, so the phone does not let
     * the link itself bypass the setting. When the link is allowed, both
     * arrive and the phone runs the commandId once.
     */
    private fun sendOpenCommandAndBringPhoneAppToFront(
        command: WatchCommand,
        deepLink: String,
        onNotConnected: () -> Unit
    ) {
        sendCommand(command, onNotConnected)
        bringPhoneAppToFront(deepLink)
    }

    /** Best effort: the command itself was already sent over the Data Layer. */
    private fun bringPhoneAppToFront(deepLink: String) {
        val completed = AtomicBoolean(false)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = Uri.parse(deepLink)
        }
        val future = runCatching {
            remoteActivityHelper.startRemoteActivity(intent, null)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to request remote activity (${error.javaClass.simpleName})")
            return
        }
        future.addListener(
            {
                completed.set(true)
                runCatching { future.get() }.onFailure { error ->
                    Log.w(TAG, "Remote activity request failed (${error.javaClass.simpleName})")
                }
            },
            mainExecutor
        )
        mainHandler.postDelayed(
            {
                if (completed.compareAndSet(false, true)) {
                    Log.w(TAG, "Remote activity request timed out")
                    future.cancel(true)
                }
            },
            WATCH_REMOTE_ACTIVITY_TIMEOUT_MILLIS
        )
    }

    private fun sendMessageOrFallback(
        path: String,
        payload: ByteArray,
        fallback: () -> Unit,
        onNotConnected: (() -> Unit)? = null
    ) {
        Wearable.getNodeClient(context.applicationContext).connectedNodes
            .addOnSuccessListener { nodes ->
                val targetNodes = nodes.filter { it.isNearby }.ifEmpty { nodes }
                if (targetNodes.isEmpty()) {
                    if (onNotConnected != null) {
                        Log.i(TAG, "No connected phone; dropped $path instead of queueing it")
                        onNotConnected()
                    } else {
                        fallback()
                    }
                    return@addOnSuccessListener
                }
                val remaining = AtomicInteger(targetNodes.size)
                val delivered = AtomicBoolean(false)
                val fallbackStarted = AtomicBoolean(false)
                val fallbackOnce = {
                    if (fallbackStarted.compareAndSet(false, true)) {
                        fallback()
                    }
                }
                mainHandler.postDelayed(
                    {
                        if (!delivered.get() && remaining.get() > 0) {
                            Log.w(TAG, "sendMessage timed out for $path; falling back to DataItem")
                            fallbackOnce()
                        }
                    },
                    WATCH_MESSAGE_FALLBACK_TIMEOUT_MILLIS
                )
                targetNodes.forEach { node ->
                    Wearable.getMessageClient(context.applicationContext)
                        .sendMessage(node.id, path, payload)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                delivered.set(true)
                            } else {
                                Log.w(TAG, "sendMessage failed for $path to ${node.displayName}", task.exception)
                            }
                            if (remaining.decrementAndGet() == 0 && !delivered.get()) {
                                fallbackOnce()
                            }
                        }
                }
            }
            .addOnFailureListener {
                if (onNotConnected != null) {
                    Log.w(TAG, "Failed to resolve connected nodes for $path; dropped", it)
                    onNotConnected()
                } else {
                    Log.w(TAG, "Failed to resolve connected nodes for $path; falling back to DataItem", it)
                    fallback()
                }
            }
    }

    private fun putRequestDataItem(path: String) {
        val request = PutDataMapRequest.create(path).apply {
            dataMap.putLong(WATCH_UPDATED_AT_KEY, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context.applicationContext)
            .putDataItem(request)
            .addOnFailureListener {
                Log.w(TAG, "Failed to put snapshot request DataItem", it)
            }
    }

    private fun putCommandDataItem(encodedCommand: String) {
        val request = PutDataMapRequest.create(WATCH_COMMAND_PATH).apply {
            dataMap.putString(WATCH_COMMAND_KEY, encodedCommand)
            // The phone drops a command item older than a couple of minutes by its own clock, so
            // stamp it with the phone's time as far as the watch knows it.
            dataMap.putLong(WATCH_UPDATED_AT_KEY, WatchClockOffsetStore.phoneNowMillis(context.applicationContext))
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context.applicationContext)
            .putDataItem(request)
            .addOnFailureListener {
                Log.w(TAG, "Failed to put command DataItem", it)
            }
    }

    private fun nextCommandId(): String {
        val sequence = commandSequence.updateAndGet { current ->
            if (current == Int.MAX_VALUE) 1 else current + 1
        }
        return "wear-${System.currentTimeMillis()}-$sequence"
    }

    private companion object {
        private const val TAG = "PhoneCommandClient"
        private const val WATCH_COMMAND_PAYLOAD_MAX_BYTES = 4 * 1024
        private const val WATCH_MESSAGE_FALLBACK_TIMEOUT_MILLIS = 10_000L
        private const val WATCH_REMOTE_ACTIVITY_TIMEOUT_MILLIS = 10_000L
    }
}
