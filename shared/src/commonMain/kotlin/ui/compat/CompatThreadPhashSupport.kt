package com.valoser.futacha.shared.ui.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.compat.CompatNgRule
import com.valoser.futacha.shared.compat.CompatPostSnapshot
import com.valoser.futacha.shared.compat.CompatThreadSnapshot
import com.valoser.futacha.shared.compat.CompatibilityStore
import com.valoser.futacha.shared.compat.compatImagePhashCachePreferenceKey
import com.valoser.futacha.shared.compat.isValidCompatImagePhash
import com.valoser.futacha.shared.util.AppDispatchers
import com.valoser.futacha.shared.util.Logger
import com.valoser.futacha.shared.util.runSuspendCatchingPreservingCancellation
import io.ktor.client.HttpClient
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

private const val COMPAT_THREAD_PHASH_MAX_CANDIDATES = 256

/** (postNo, image URL) pairs whose hashes the thread image NG needs, at most 256 distinct images. */
internal fun compatThreadPhashCandidates(posts: List<CompatPostSnapshot>): List<Pair<String, String>> =
    posts.asSequence()
        .map(::normalizeCompatPostMedia)
        .mapNotNull { post -> (post.imageUrl ?: post.thumbnailUrl)?.let { url -> post.postNo to url } }
        .distinctBy { it.second }
        .take(COMPAT_THREAD_PHASH_MAX_CANDIDATES)
        .toList()

/**
 * Collects postNo -> pHash for the thread's image NG rules.
 *
 * Hashes already in the persisted cache (shared with the catalog) are published
 * first. The remaining images are fetched within the batch budget, and what was
 * computed before the budget ran out is kept, published and persisted rather
 * than discarded, so the next pass only fetches what is still missing.
 */
internal suspend fun collectCompatThreadImagePhashes(
    httpClient: HttpClient?,
    store: CompatibilityStore?,
    posts: List<CompatPostSnapshot>,
    concurrency: Int = COMPAT_PHASH_FETCH_CONCURRENCY,
    onUpdate: (Map<String, String>) -> Unit
) {
    val client = httpClient
    if (client == null) {
        onUpdate(emptyMap())
        return
    }
    val candidates = withContext(AppDispatchers.parsing) { compatThreadPhashCandidates(posts) }
    val stored = loadStoredCompatThreadImagePhashes(store, candidates)
    onUpdate(stored)
    val missing = candidates.filterNot { it.first in stored }
    if (missing.isEmpty()) return
    val computed = linkedMapOf<String, String>()
    try {
        collectCompatImagePhashes(
            client, missing,
            onComputed = { id, phash -> computed[id] = phash },
            onPartial = { partial -> onUpdate(stored + partial) },
            concurrency = concurrency
        )
    } finally {
        // Even fewer than a UI publication batch must survive tab changes and cancellation.
        if (store != null && computed.isNotEmpty()) {
            val urlByPostNo = missing.toMap()
            val entries = computed.mapNotNull { (postNo, phash) ->
                urlByPostNo[postNo]?.let { compatImagePhashCachePreferenceKey(it) to phash }
            }.toMap()
            withContext(NonCancellable) {
                runSuspendCatchingPreservingCancellation { store.saveImagePhashes(entries) }
                    .onFailure { Logger.e("CompatThreadPhash", "Failed to save image hashes", it) }
            }
        }
    }
}

internal suspend fun loadStoredCompatThreadImagePhashes(
    store: CompatibilityStore?,
    candidates: List<Pair<String, String>>
): Map<String, String> {
    if (store == null || candidates.isEmpty()) return emptyMap()
    val loaded = runSuspendCatchingPreservingCancellation {
        store.loadImagePhashes(candidates.map { compatImagePhashCachePreferenceKey(it.second) })
    }.onFailure { Logger.e("CompatThreadPhash", "Failed to load image hashes", it) }
        .getOrDefault(emptyMap())
    return candidates.mapNotNull { (postNo, url) ->
        loaded[compatImagePhashCachePreferenceKey(url)]?.takeIf(::isValidCompatImagePhash)?.let { postNo to it }
    }.toMap()
}

/**
 * postNo -> pHash for a swipe-neighbor thread preview, read from the persisted
 * hash cache only (no network). The active thread's map is keyed by its own
 * post numbers and must not be reused for another thread (E10).
 */
@Composable
internal fun rememberCompatNeighborStoredImagePhashes(
    store: CompatibilityStore?,
    snapshot: CompatThreadSnapshot?,
    ngRules: List<CompatNgRule>
): Map<String, String>? {
    val enabled = remember(ngRules) { ngRules.any { it.kind == CompatNgKind.THREAD_IMAGE_PHASH } }
    var hashes by remember(snapshot?.tabKey, snapshot?.revision, enabled) {
        mutableStateOf<Map<String, String>?>(if (enabled) null else emptyMap())
    }
    LaunchedEffect(snapshot?.tabKey, snapshot?.revision, enabled, store) {
        val posts = snapshot?.posts
        hashes = if (!enabled || posts == null) emptyMap() else {
            val candidates = withContext(AppDispatchers.parsing) { compatThreadPhashCandidates(posts) }
            loadStoredCompatThreadImagePhashes(store, candidates)
        }
    }
    return hashes
}
