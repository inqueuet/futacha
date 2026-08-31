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
import com.valoser.futacha.shared.watch.WATCH_ALERT_KEY
import com.valoser.futacha.shared.watch.WATCH_ALERT_PATH
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_KEY
import com.valoser.futacha.shared.watch.WATCH_READ_ALOUD_STATUS_PATH
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_ACK_PATH
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_PATH
import com.valoser.futacha.shared.watch.WATCH_UPDATED_AT_KEY
import com.valoser.futacha.shared.watch.WatchAlert
import com.valoser.futacha.wear.live.WatchAlertNotifier
import kotlinx.serialization.json.Json

class WatchDataLayerListenerService : WearableListenerService() {
    private val json = Json { ignoreUnknownKeys = true }

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
                    val dataMap = runCatching { DataMapItem.fromDataItem(item).dataMap }
                        .getOrNull()
                        ?: return@forEach
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
                    val encoded = runCatching { DataMapItem.fromDataItem(item).dataMap }
                        .getOrNull()
                        ?: return@forEach
                    val status = encoded
                        .getString(WATCH_READ_ALOUD_STATUS_KEY)
                        ?: return@forEach
                    decodeAndSaveReadAloudStatusUpdate(status)
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
            WATCH_ALERT_PATH -> {
                if (messageEvent.data.isEmpty() || messageEvent.data.size > WATCH_ALERT_PAYLOAD_MAX_BYTES) return
                val dataMap = runCatching { DataMap.fromByteArray(messageEvent.data) }.getOrNull()
                val encoded = dataMap
                    ?.getString(WATCH_ALERT_KEY)
                    ?: messageEvent.data.decodeToString()
                decodeAndNotifyWatchAlert(encoded)
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

    private fun decodeAndNotifyWatchAlert(encoded: String) {
        if (encoded.isBlank() || encoded.encodeToByteArray().size > WATCH_ALERT_PAYLOAD_MAX_BYTES) {
            return
        }
        runCatching {
            json.decodeFromString(WatchAlert.serializer(), encoded)
        }.onSuccess { alert ->
            WatchAlertNotifier.notify(applicationContext, alert)
        }.onFailure {
            Log.w(TAG, "Failed to decode watch alert", it)
        }
    }

    private fun sendSnapshotAck(ackId: String) {
        val request = PutDataMapRequest.create(WATCH_SNAPSHOT_ACK_PATH).apply {
            dataMap.putString(WATCH_SNAPSHOT_ACK_KEY, ackId)
            dataMap.putLong(WATCH_UPDATED_AT_KEY, System.currentTimeMillis())
        }.asPutDataRequest()
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
        private const val WATCH_ALERT_PAYLOAD_MAX_BYTES = 8 * 1024
    }
}
