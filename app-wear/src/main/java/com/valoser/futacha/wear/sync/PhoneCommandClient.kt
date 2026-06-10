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
import com.valoser.futacha.shared.ai.FutachaAiAction
import com.valoser.futacha.shared.ai.buildFutachaAiDeepLink
import com.valoser.futacha.shared.watch.WATCH_COMMAND_KEY
import com.valoser.futacha.shared.watch.WATCH_COMMAND_PATH
import com.valoser.futacha.shared.watch.WATCH_REQUEST_SNAPSHOT_PATH
import com.valoser.futacha.shared.watch.WATCH_UPDATED_AT_KEY
import com.valoser.futacha.shared.watch.WatchCommand
import com.valoser.futacha.shared.watch.WatchCommandType
import com.valoser.futacha.shared.watch.WatchThreadSummary
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
            payload = ByteArray(0),
            fallback = { putRequestDataItem(WATCH_REQUEST_SNAPSHOT_PATH) }
        )
    }

    fun requestRefresh() {
        sendCommand(WatchCommand(type = WatchCommandType.Refresh))
    }

    fun openBoardOnPhone(
        boardId: String,
        boardUrl: String
    ) {
        val commandId = nextCommandId()
        val command = WatchCommand(
            type = WatchCommandType.SelectBoard,
            boardId = boardId,
            boardUrl = boardUrl,
            commandId = commandId
        )
        openDeepLinkOnPhoneOrFallback(
            deepLink = buildFutachaAiDeepLink(
                action = FutachaAiAction.OpenBoard,
                parameters = mapOf(
                    "boardId" to boardId,
                    "boardUrl" to boardUrl,
                    "commandId" to commandId
                )
            ),
            fallback = { sendCommand(command) }
        )
    }

    fun openThreadOnPhone(
        boardId: String,
        boardUrl: String,
        threadId: String
    ) {
        val commandId = nextCommandId()
        val command = WatchCommand(
            type = WatchCommandType.OpenThreadOnPhone,
            boardId = boardId,
            boardUrl = boardUrl,
            threadId = threadId,
            commandId = commandId
        )
        openDeepLinkOnPhoneOrFallback(
            deepLink = buildFutachaAiDeepLink(
                action = FutachaAiAction.OpenThread,
                parameters = mapOf(
                    "boardId" to boardId,
                    "boardUrl" to boardUrl,
                    "threadId" to threadId,
                    "commandId" to commandId
                )
            ),
            fallback = { sendCommand(command) }
        )
    }

    fun startReadAloudOnPhone(thread: WatchThreadSummary) {
        sendThreadCommand(WatchCommandType.StartReadAloudOnPhone, thread)
    }

    fun pauseReadAloudOnPhone(thread: WatchThreadSummary) {
        sendThreadCommand(WatchCommandType.PauseReadAloudOnPhone, thread)
    }

    fun stopReadAloudOnPhone(thread: WatchThreadSummary) {
        sendThreadCommand(WatchCommandType.StopReadAloudOnPhone, thread)
    }

    fun nextReadAloudOnPhone(thread: WatchThreadSummary) {
        sendThreadCommand(WatchCommandType.NextReadAloudOnPhone, thread)
    }

    fun previousReadAloudOnPhone(thread: WatchThreadSummary) {
        sendThreadCommand(WatchCommandType.PreviousReadAloudOnPhone, thread)
    }

    private fun sendThreadCommand(
        type: WatchCommandType,
        thread: WatchThreadSummary
    ) {
        sendCommand(
            WatchCommand(
                type = type,
                boardId = thread.boardId,
                boardUrl = thread.boardUrl,
                threadId = thread.threadId
            )
        )
    }

    private fun sendCommand(command: WatchCommand) {
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
            fallback = { putCommandDataItem(encoded) }
        )
    }

    private fun openDeepLinkOnPhoneOrFallback(
        deepLink: String,
        fallback: () -> Unit
    ) {
        val completed = AtomicBoolean(false)
        val fallbackStarted = AtomicBoolean(false)
        val fallbackOnce = {
            if (fallbackStarted.compareAndSet(false, true)) {
                fallback()
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = Uri.parse(deepLink)
        }
        val future = runCatching {
            remoteActivityHelper.startRemoteActivity(intent, null)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to request remote activity for $deepLink; falling back to DataItem", error)
            fallback()
            return
        }
        future.addListener(
            {
                completed.set(true)
                runCatching { future.get() }.onFailure { error ->
                    Log.w(TAG, "Remote activity request failed for $deepLink; falling back to DataItem", error)
                    fallbackOnce()
                }
            },
            mainExecutor
        )
        mainHandler.postDelayed(
            {
                if (completed.compareAndSet(false, true)) {
                    Log.w(TAG, "Remote activity request timed out for $deepLink; falling back to DataItem")
                    future.cancel(true)
                    fallbackOnce()
                }
            },
            WATCH_REMOTE_ACTIVITY_FALLBACK_TIMEOUT_MILLIS
        )
    }

    private fun sendMessageOrFallback(
        path: String,
        payload: ByteArray,
        fallback: () -> Unit
    ) {
        Wearable.getNodeClient(context.applicationContext).connectedNodes
            .addOnSuccessListener { nodes ->
                val targetNodes = nodes.filter { it.isNearby }.ifEmpty { nodes }
                if (targetNodes.isEmpty()) {
                    fallback()
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
                Log.w(TAG, "Failed to resolve connected nodes for $path; falling back to DataItem", it)
                fallback()
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
            dataMap.putLong(WATCH_UPDATED_AT_KEY, System.currentTimeMillis())
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
        private const val WATCH_REMOTE_ACTIVITY_FALLBACK_TIMEOUT_MILLIS = 10_000L
    }
}
