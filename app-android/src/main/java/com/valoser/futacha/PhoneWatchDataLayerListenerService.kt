package com.valoser.futacha

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.valoser.futacha.shared.watch.WATCH_COMMAND_KEY
import com.valoser.futacha.shared.watch.WATCH_COMMAND_PATH
import com.valoser.futacha.shared.watch.WATCH_REQUEST_SNAPSHOT_PATH
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_PATH

class PhoneWatchDataLayerListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val app = application as? FutachaApplication ?: return
        val manager = runCatching { app.watchSyncManager }.getOrNull() ?: return
        manager.start()
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            when (event.dataItem.uri.path) {
                WATCH_REQUEST_SNAPSHOT_PATH -> manager.requestSnapshot()
                WATCH_SNAPSHOT_ACK_PATH -> {
                    val rawDataSize = event.dataItem.data?.size ?: 0
                    if (rawDataSize <= 0 || rawDataSize > WATCH_SNAPSHOT_ACK_DATA_ITEM_MAX_BYTES) {
                        return@forEach
                    }
                    val ackId = DataMapItem.fromDataItem(event.dataItem)
                        .dataMap
                        .getString(WATCH_SNAPSHOT_ACK_KEY)
                        ?: return@forEach
                    manager.handleSnapshotAckPayload(ackId.encodeToByteArray())
                }
                WATCH_COMMAND_PATH -> {
                    val rawDataSize = event.dataItem.data?.size ?: 0
                    if (rawDataSize <= 0 || rawDataSize > WATCH_COMMAND_PAYLOAD_MAX_BYTES) {
                        return@forEach
                    }
                    val command = DataMapItem.fromDataItem(event.dataItem)
                        .dataMap
                        .getString(WATCH_COMMAND_KEY)
                        ?: return@forEach
                    if (command.encodeToByteArray().size > WATCH_COMMAND_PAYLOAD_MAX_BYTES) {
                        return@forEach
                    }
                    manager.handleCommandPayload(command.encodeToByteArray())
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val app = application as? FutachaApplication ?: return
        val manager = runCatching { app.watchSyncManager }.getOrNull() ?: return
        manager.start()
        when (messageEvent.path) {
            WATCH_REQUEST_SNAPSHOT_PATH -> manager.requestSnapshot()
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

    private companion object {
        private const val WATCH_COMMAND_PAYLOAD_MAX_BYTES = 4 * 1024
        private const val WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES = 128
        private const val WATCH_SNAPSHOT_ACK_DATA_ITEM_MAX_BYTES = 4 * 1024
    }
}
