package com.valoser.futacha.shared.ui.aimoge

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.valoser.futacha.shared.compat.AimogeAvailability
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.aimogeBoardOnlyShapeOf
import com.valoser.futacha.shared.repo.aimoge.AimogeBoardInfo
import com.valoser.futacha.shared.ui.image.LocalFutachaImageLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The あいもげ board icons (`/boards` `presentation.imageUrl`, nijiurachan.net over https only), keyed by the
 * canonical board URL. Learned when the add-board list is read and kept in the shared store, so the board lists of
 * ふたちゃ and としあき(仮) can show them without reading `/boards` again. Futaba boards have none.
 */
object AimogeBoardIcons {
    const val PREFERENCE_KEY = "compat.aimoge.boardIcons"
    private val _icons = MutableStateFlow<Map<String, String>>(emptyMap())
    val icons: StateFlow<Map<String, String>> = _icons.asStateFlow()
    private val serializer = MapSerializer(String.serializer(), String.serializer())
    @kotlin.concurrent.Volatile private var loaded = false
    /** The store the host loaded from; learned icons are saved there. */
    @kotlin.concurrent.Volatile private var savedTo: CompatibilityStore? = null
    private val loadLock = kotlinx.coroutines.sync.Mutex()

    /** The icons of [boards] (others kept); returns the value to save. */
    fun learn(boards: List<AimogeBoardInfo>): String {
        val learned = boards.mapNotNull { b -> b.iconUrl?.takeIf(::isSafeIcon)?.let { b.url to it } }
        val next = _icons.updateAndGet { it + learned }
        return Json.encodeToString(serializer, next)
    }

    suspend fun learnAndSave(store: CompatibilityStore?, boards: List<AimogeBoardInfo>) {
        val text = learn(boards)
        com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation { (store ?: savedTo)?.savePreference(PREFERENCE_KEY, text) }
    }

    /** Reads the saved icons once per process; "loaded" is set only after a finished read (a cancel reads again). */
    suspend fun load(store: CompatibilityStore) {
        savedTo = store
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            val text = com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation { store.loadPreference(PREFERENCE_KEY) }.getOrNull()
            val saved = text?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }
            if (saved != null) _icons.update { saved.filterValues(::isSafeIcon) + it }
            loaded = true
        }
    }

    /** The icon of [boardUrl], or null (Futaba boards, unknown boards, あいもげ off). */
    fun iconOf(boardUrl: String, icons: Map<String, String>): String? {
        if (!AimogeAvailability.sitesEnabled) return null
        val canonical = aimogeBoardOnlyShapeOf(boardUrl) ?: return null
        return icons[canonical]
    }

    internal fun isSafeIcon(url: String): Boolean {
        val rest = url.removePrefix("https://").takeIf { it != url } ?: return false
        val host = rest.substringBefore('/').substringBefore('?')
        return (host == "nijiurachan.net" || host.endsWith(".nijiurachan.net")) && '@' !in host
    }

    internal fun resetForTest() { _icons.value = emptyMap(); loaded = false; savedTo = null }
}

/** The board's あいもげ icon in a circle; draws nothing (returns false) when the board has none. */
@Composable
fun AimogeBoardIcon(boardUrl: String, size: Dp, modifier: Modifier = Modifier): Boolean {
    val icons by AimogeBoardIcons.icons.collectAsState()
    val url = AimogeBoardIcons.iconOf(boardUrl, icons) ?: return false
    AsyncImage(
        model = url,
        imageLoader = LocalFutachaImageLoader.current,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(CircleShape).testTag("aimoge-board-icon")
    )
    return true
}
