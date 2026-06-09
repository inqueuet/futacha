package com.valoser.futacha.wear.sync

import android.content.Context
import com.valoser.futacha.shared.watch.WatchSnapshot
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

object WatchSnapshotStore {
    private const val PREFS_NAME = "futacha_watch_snapshot"
    private const val SNAPSHOT_JSON_KEY = "snapshot_json"
    private const val SNAPSHOT_PAYLOAD_MAX_BYTES = 128 * 1024

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val snapshotState = MutableStateFlow<WatchSnapshot?>(null)
    private val saveMutex = Mutex()

    fun observe(): StateFlow<WatchSnapshot?> {
        return snapshotState.asStateFlow()
    }

    suspend fun loadPersisted(context: Context) {
        if (snapshotState.value != null) return
        snapshotState.value = load(context)
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

    private fun isValidSnapshotPayload(encoded: String): Boolean {
        return encoded.isNotBlank() && encoded.encodeToByteArray().size <= SNAPSHOT_PAYLOAD_MAX_BYTES
    }
}
