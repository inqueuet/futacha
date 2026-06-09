package com.valoser.futacha.wear.sync

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_PATH
import com.google.android.gms.wearable.WearableListenerService
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_PATH
import com.valoser.futacha.shared.watch.WATCH_UPDATED_AT_KEY

class WatchDataLayerListenerService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val item = event.dataItem
            if (item.uri.path != WATCH_SNAPSHOT_PATH) return@forEach
            val rawDataSize = item.data?.size ?: 0
            if (rawDataSize <= 0 || rawDataSize > WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) {
                return@forEach
            }
            val encoded = DataMapItem.fromDataItem(item)
                .dataMap
                .getString(WATCH_SNAPSHOT_KEY)
                ?: return@forEach
            val ackId = DataMapItem.fromDataItem(item)
                .dataMap
                .getString(WATCH_SNAPSHOT_ACK_KEY)
                ?.takeIf { it.encodeToByteArray().size <= WATCH_SNAPSHOT_ACK_PAYLOAD_MAX_BYTES }
            decodeAndSaveSnapshot(encoded, ackId)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WATCH_SNAPSHOT_PATH) return
        if (messageEvent.data.isEmpty() || messageEvent.data.size > WATCH_SNAPSHOT_PAYLOAD_MAX_BYTES) return
        val encoded = messageEvent.data.decodeToString()
        decodeAndSaveSnapshot(encoded, ackId = null)
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
    }
}
