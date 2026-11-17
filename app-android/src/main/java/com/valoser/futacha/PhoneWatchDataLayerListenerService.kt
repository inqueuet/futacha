package com.valoser.futacha

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.valoser.futacha.shared.compat.ExperienceProfile
import com.valoser.futacha.shared.watch.WATCH_COMMAND_KEY
import com.valoser.futacha.shared.watch.WATCH_COMMAND_PATH
import com.valoser.futacha.shared.watch.WATCH_PHONE_STATUS_PATH
import com.valoser.futacha.shared.watch.WATCH_REQUEST_SNAPSHOT_PATH
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_PATH
import com.valoser.futacha.shared.watch.WATCH_UPDATED_AT_KEY
import com.valoser.futacha.shared.watch.decodeWatchClockRequestPayload
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class PhoneWatchDataLayerListenerService : WearableListenerService() {
    override fun onPeerConnected(peer: Node) {
        val manager = activeManagerOrNull() ?: return
        manager.start()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        if (!isFutachaMode()) {
            // Only ふたちゃ serves the watch. Tell the watch instead of dropping its request
            // while it keeps showing "要求しました".
            dataEvents.forEach { event ->
                if (event.type != DataEvent.TYPE_CHANGED) return@forEach
                val path = event.dataItem.uri.path
                if (path == WATCH_REQUEST_SNAPSHOT_PATH || path == WATCH_COMMAND_PATH) {
                    replyPhoneStatus(event.dataItem.uri.host, watchSentAtMillis = 0L, isSupported = false)
                }
            }
            return
        }
        val manager = activeManagerOrNull() ?: return
        manager.start()
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            when (event.dataItem.uri.path) {
                WATCH_REQUEST_SNAPSHOT_PATH -> {
                    manager.requestSnapshot()
                    val watchSentAt = runCatching {
                        DataMapItem.fromDataItem(event.dataItem).dataMap.getLong(WATCH_UPDATED_AT_KEY, 0L)
                    }.getOrDefault(0L)
                    replyPhoneStatus(event.dataItem.uri.host, watchSentAt, isSupported = true)
                }
                WATCH_SNAPSHOT_ACK_PATH -> {
                    val rawDataSize = event.dataItem.data?.size ?: 0
                    if (rawDataSize <= 0 || rawDataSize > WATCH_SNAPSHOT_ACK_DATA_ITEM_MAX_BYTES) {
                        return@forEach
                    }
                    val dataMap = runCatching { DataMapItem.fromDataItem(event.dataItem).dataMap }
                        .getOrNull()
                        ?: return@forEach
                    val ackId = dataMap
                        .getString(WATCH_SNAPSHOT_ACK_KEY)
                        ?: return@forEach
                    manager.handleSnapshotAckPayload(ackId.encodeToByteArray())
                }
                WATCH_COMMAND_PATH -> {
                    val rawDataSize = event.dataItem.data?.size ?: 0
                    if (rawDataSize <= 0 || rawDataSize > WATCH_COMMAND_PAYLOAD_MAX_BYTES) {
                        return@forEach
                    }
                    val dataMap = runCatching { DataMapItem.fromDataItem(event.dataItem).dataMap }
                        .getOrNull()
                        ?: return@forEach
                    val command = dataMap
                        .getString(WATCH_COMMAND_KEY)
                        ?: return@forEach
                    if (command.encodeToByteArray().size > WATCH_COMMAND_PAYLOAD_MAX_BYTES) {
                        return@forEach
                    }
                    if (
                        isStaleWatchCommandDataItem(
                            updatedAtMillis = dataMap.getLong(WATCH_UPDATED_AT_KEY, 0L),
                            nowMillis = System.currentTimeMillis()
                        )
                    ) {
                        com.valoser.futacha.shared.util.Logger.i(
                            "PhoneWatchDataLayer",
                            "Dropped a watch command queued while disconnected"
                        )
                        return@forEach
                    }
                    manager.handleCommandPayload(command.encodeToByteArray())
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (!isFutachaMode()) {
            if (messageEvent.path == WATCH_REQUEST_SNAPSHOT_PATH || messageEvent.path == WATCH_COMMAND_PATH) {
                replyPhoneStatus(messageEvent.sourceNodeId, watchSentAtMillis = 0L, isSupported = false)
            }
            return
        }
        val manager = activeManagerOrNull() ?: return
        manager.start()
        when (messageEvent.path) {
            WATCH_REQUEST_SNAPSHOT_PATH -> {
                manager.requestSnapshot()
                // The request carries the watch clock; echo it with the phone clock so the watch
                // can learn how far its own clock is off.
                replyPhoneStatus(
                    messageEvent.sourceNodeId,
                    decodeWatchClockRequestPayload(messageEvent.data),
                    isSupported = true
                )
            }
            WATCH_SNAPSHOT_ACK_PATH -> {
                if (messageEvent.data.size <= WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES) {
                    manager.handleSnapshotAckPayload(messageEvent.data)
                }
            }
            WATCH_COMMAND_PATH -> {
                if (messageEvent.data.size <= WATCH_COMMAND_PAYLOAD_MAX_BYTES) {
                    manager.handleCommandPayload(messageEvent.data)
                }
            }
        }
    }

    private fun isFutachaMode(): Boolean {
        val app = application as? FutachaApplication ?: return false
        return app.experienceProfileStore.readActiveProfile() == ExperienceProfile.FUTACHA
    }

    private fun replyPhoneStatus(nodeId: String?, watchSentAtMillis: Long, isSupported: Boolean) {
        if (nodeId.isNullOrBlank()) return
        val payload = buildWatchPhoneStatusPayload(
            phoneNowMillis = System.currentTimeMillis(),
            watchSentAtMillis = watchSentAtMillis,
            isSupported = isSupported
        )
        runCatching {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, WATCH_PHONE_STATUS_PATH, payload)
                .addOnFailureListener {
                    com.valoser.futacha.shared.util.Logger.w("PhoneWatchDataLayer", "Failed to send the phone status to the watch")
                }
        }
    }

    private fun activeManagerOrNull(): WatchSyncManager? {
        val app = application as? FutachaApplication ?: return null
        if (app.experienceProfileStore.readActiveProfile() != ExperienceProfile.FUTACHA) return null
        app.watchSyncManagerOrNull()?.let { return it }
        // A watch message can cold-start the process before the Application's
        // asynchronous network setup creates the manager. Listener callbacks
        // run on a background thread and their DataEventBuffer is released
        // on return, so wait here (bounded) instead of dropping the command.
        return runCatching {
            runBlocking {
                withTimeoutOrNull(WATCH_MANAGER_INIT_WAIT_MILLIS) { app.awaitWatchSyncManagerOrNull() }
            }
        }.getOrNull()
    }

    private companion object {
        private const val WATCH_COMMAND_PAYLOAD_MAX_BYTES = 4 * 1024
        private const val WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES = 128
        private const val WATCH_SNAPSHOT_ACK_DATA_ITEM_MAX_BYTES = 4 * 1024
        private const val WATCH_MANAGER_INIT_WAIT_MILLIS = 5_000L
    }
}
