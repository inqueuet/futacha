package com.valoser.futacha.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
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
        sendCommand(
            WatchCommand(
                type = WatchCommandType.SelectBoard,
                boardId = boardId,
                boardUrl = boardUrl
            )
        )
    }

    fun openThreadOnPhone(
        boardId: String,
        boardUrl: String,
        threadId: String
    ) {
        sendCommand(
            WatchCommand(
                type = WatchCommandType.OpenThreadOnPhone,
                boardId = boardId,
                boardUrl = boardUrl,
                threadId = threadId
            )
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
        val encoded = json.encodeToString(WatchCommand.serializer(), command)
        val payload = encoded.encodeToByteArray()
        sendMessageOrFallback(
            path = WATCH_COMMAND_PATH,
            payload = payload,
            fallback = { putCommandDataItem(encoded) }
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
                targetNodes.forEach { node ->
                    Wearable.getMessageClient(context.applicationContext)
                        .sendMessage(node.id, path, payload)
                        .addOnSuccessListener {
                            delivered.set(true)
                        }
                        .addOnFailureListener {
                            Log.w(TAG, "sendMessage failed for $path to ${node.displayName}", it)
                            if (remaining.decrementAndGet() == 0 && !delivered.get()) {
                                fallback()
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

    private companion object {
        private const val TAG = "PhoneCommandClient"
    }
}
