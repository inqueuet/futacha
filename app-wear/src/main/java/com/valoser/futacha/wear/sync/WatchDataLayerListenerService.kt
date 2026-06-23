package com.valoser.futacha.wear.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_KEY
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_PATH
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_PATH
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_PATH
import com.valoser.futacha.shared.watch.WATCH_UPDATED_AT_KEY

class WatchDataLayerListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            when (item.uri.path) {
                WATCH_SNAPSHOT_PATH -> {
                    val rawDataSize = item.data?.size ?: 0
                    if (rawDataSize <= 0 || rawDataSize > WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) {
                        return@forEach
                    }
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val encoded = dataMap.getString(WATCH_SNAPSHOT_KEY) ?: return@forEach
                    val ackId = dataMap
                        .getString(WATCH_SNAPSHOT_ACK_KEY)
                        ?.takeIf { it.encodeToByteArray().size <= WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES }
                    decodeAndSaveSnapshot(encoded, ackId)
                }
                WATCH_READ_ALOUD_STATUS_PATH -> {
                    val rawDataSize = item.data?.size ?: 0
                    if (rawDataSize <= 0 || rawDataSize > WATCH_READ_ALOUD_STATUS_PAYLOAD_MAX_BYTES) {
                        return@forEach
                    }
                    val encoded = DataMapItem.fromDataItem(item)
                        .dataMap
                        .getString(WATCH_READ_ALOUD_STATUS_KEY)
                        ?: return@forEach
                    decodeAndSaveReadAloudStatusUpdate(encoded)
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WATCH_SNAPSHOT_PATH -> {
                if (messageEvent.data.isEmpty() || messageEvent.data.size > WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) return
                val dataMap = runCatching { DataMap.fromByteArray(messageEvent.data) }.getOrNull()
                if (dataMap != null) {
                    val encoded = dataMap.getString(WATCH_SNAPSHOT_KEY) ?: return
                    val ackId = dataMap.getString(WATCH_SNAPSHOT_ACK_KEY)
                        ?.takeIf { it.encodeToByteArray().size <= WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES }
                    decodeAndSaveSnapshot(encoded, ackId)
                } else {
                    val encoded = messageEvent.data.decodeToString()
                    decodeAndSaveSnapshot(encoded, ackId = null)
                }
            }
            WATCH_READ_ALOUD_STATUS_PATH -> {
                if (
                    messageEvent.data.isEmpty() ||
                    messageEvent.data.size > WATCH_READ_ALOUD_STATUS_PAYLOAD_MAX_BYTES
                ) {
                    return
                }
                val dataMap = runCatching { DataMap.fromByteArray(messageEvent.data) }.getOrNull()
                val encoded = dataMap
                    ?.getString(WATCH_READ_ALOUD_STATUS_KEY)
                    ?: messageEvent.data.decodeToString()
                decodeAndSaveReadAloudStatusUpdate(encoded)
            }
        }
    }

    private fun decodeAndSaveSnapshot(encoded: String, ackId: String?) {
        if (encoded.isBlank() || encoded.encodeToByteArray().size > WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) {
            return
        }
        WatchSnapshotStore.saveEncodedAsync(
            context = applicationContext,
            encoded = encoded,
            onSaved = {
                ackId?.let { sendSnapshotAck(it) }
            }
        )
    }

    private fun decodeAndSaveReadAloudStatusUpdate(encoded: String) {
        if (
            encoded.isBlank() ||
            encoded.encodeToByteArray().size > WATCH_READ_ALOUD_STATUS_PAYLOAD_MAX_BYTES
        ) {
            return
        }
        WatchSnapshotStore.saveReadAloudStatusUpdateEncodedAsync(
            context = applicationContext,
            encoded = encoded
        )
    }

    private fun sendSnapshotAck(ackId: String) {
        val request = PutDataMapRequest.create(WATCH_SNAPSHOT_ACK_PATH).apply {
            dataMap.putString(WATCH_SNAPSHOT_ACK_KEY, ackId)
            dataMap.putLong(WATCH_UPDATED_AT_KEY, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(applicationContext)
            .putDataItem(request)
            .addOnFailureListener {
                Log.w(TAG, "Failed to send watch snapshot ack", it)
            }
    }

    private companion object {
        private const val TAG = "WatchDataLayerListener"
        private const val WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES = 128 * 1024
        private const val WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES = 128
        private const val WATCH_READ_ALOUD_STATUS_PAYLOAD_MAX_BYTES = 4 * 1024
    }
}
