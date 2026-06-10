package com.valoser.futacha.wear.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.valoser.futacha.shared.watch.WatchSnapshot
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_KEY
import com.valoser.futacha.shared.watch.WATCH_SNAPSHOT_PATH
import com.valoser.futacha.wear.tile.FutachaTileService
import androidx.wear.tiles.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

object WatchSnapshotStore {
    private const val PREFS_NAME = "futacha_watch_snapshot"
    private const val SNAPSHOT_JSON_KEY = "snapshot_json"
    private const val SNAPSHOT_PAYLOAD_MAX_BYTES = 128 * 1024
    private const val DATA_LAYER_LOAD_TIMEOUT_MILLIS = 3_000L

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshotState = MutableStateFlow<WatchSnapshot?>(null)
    private val saveMutex = Mutex()

    fun observe(): StateFlow<WatchSnapshot?> {
        return snapshotState.asStateFlow()
    }

    suspend fun loadPersisted(context: Context) {
        if (snapshotState.value != null) return
        val loaded = load(context) ?: return
        saveMutex.withLock {
            // A fresher snapshot may have arrived via save() while load() was
            // reading disk; never overwrite it with older persisted data.
            val current = snapshotState.value
            if (current == null || loaded.generatedAtMillis > current.generatedAtMillis) {
                snapshotState.value = loaded
            }
        }
    }

    fun loadDataLayerSnapshotAsync(context: Context) {
        val appContext = context.applicationContext
        storeScope.launch {
            loadLatestDataLayerSnapshot(appContext)?.let { snapshot ->
                save(appContext, snapshot)
            }
        }
    }

    suspend fun getSnapshot(context: Context): WatchSnapshot? {
        loadPersisted(context)
        return snapshotState.value
    }

    fun saveEncodedAsync(
        context: Context,
        encoded: String,
        onSaved: (() -> Unit)? = null
    ) {
        if (!isValidSnapshotPayload(encoded)) return
        val appContext = context.applicationContext
        storeScope.launch {
            val snapshot = decodeSnapshot(encoded) ?: return@launch
            if (save(appContext, snapshot)) {
                onSaved?.invoke()
            }
        }
    }

    suspend fun save(context: Context, snapshot: WatchSnapshot): Boolean {
        val encoded = withContext(Dispatchers.Default) {
            json.encodeToString(WatchSnapshot.serializer(), snapshot)
        }
        saveMutex.withLock {
            val currentSnapshot = snapshotState.value
            if (currentSnapshot != null && snapshot.generatedAtMillis < currentSnapshot.generatedAtMillis) {
                return false
            }
            val committed = withContext(Dispatchers.IO) {
                context.applicationContext
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(SNAPSHOT_JSON_KEY, encoded)
                    .commit()
            }
            if (!committed) {
                return false
            }
            snapshotState.value = snapshot
            TileService.getUpdater(context.applicationContext)
                .requestUpdate(FutachaTileService::class.java)
            return true
        }
    }

    suspend fun decodeSnapshot(encoded: String): WatchSnapshot? {
        if (!isValidSnapshotPayload(encoded)) return null
        return withContext(Dispatchers.Default) {
            runCatching {
                json.decodeFromString(WatchSnapshot.serializer(), encoded)
            }.getOrNull()
        }
    }

    private suspend fun load(context: Context): WatchSnapshot? {
        val encoded = withContext(Dispatchers.IO) {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(SNAPSHOT_JSON_KEY, null)
        } ?: return null
        if (!isValidSnapshotPayload(encoded)) return null
        return decodeSnapshot(encoded)
    }

    private suspend fun loadLatestDataLayerSnapshot(context: Context): WatchSnapshot? {
        val uri = Uri.Builder()
            .scheme("wear")
            .path(WATCH_SNAPSHOT_PATH)
            .build()
        return withContext(Dispatchers.IO) {
            runCatching {
                val buffer = Tasks.await(
                    Wearable.getDataClient(context.applicationContext)
                        .getDataItems(uri, DataClient.FILTER_LITERAL),
                    DATA_LAYER_LOAD_TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                try {
                    var latestSnapshot: WatchSnapshot? = null
                    buffer.forEach { item ->
                        val rawDataSize = item.data?.size ?: 0
                        if (rawDataSize > 0 && rawDataSize <= SNAPSHOT_PAYLOAD_MAX_BYTES) {
                            val encoded = DataMapItem.fromDataItem(item)
                                .dataMap
                                .getString(WATCH_SNAPSHOT_KEY)
                                ?.takeIf(::isValidSnapshotPayload)
                            val snapshot = encoded?.let { decodeSnapshot(it) }
                            if (
                                snapshot != null &&
                                (latestSnapshot == null ||
                                    snapshot.generatedAtMillis > latestSnapshot.generatedAtMillis)
                            ) {
                                latestSnapshot = snapshot
                            }
                        }
                    }
                    latestSnapshot
                } finally {
                    buffer.release()
                }
            }.onFailure {
                Log.w(TAG, "Failed to load snapshot from Data Layer", it)
            }.getOrNull()
        }
    }

    private fun isValidSnapshotPayload(encoded: String): Boolean {
        return encoded.isNotBlank() && encoded.encodeToByteArray().size <= SNAPSHOT_PAYLOAD_MAX_BYTES
    }

    private const val TAG = "WatchSnapshotStore"
}
