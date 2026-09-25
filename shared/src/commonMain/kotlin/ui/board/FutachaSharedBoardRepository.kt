package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.*
import com.valoser.futacha.shared.model.*
import com.valoser.futacha.shared.repo.BoardRepository
import com.valoser.futacha.shared.ui.compat.*
import com.valoser.futacha.shared.util.AppDispatchers
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
internal fun rememberFutachaSharedRepository(repository: BoardRepository): BoardRepository {
    val features = LocalFutachaSharedFeatures.current
    return remember(repository, features?.store, features?.httpClient) {
        if (features == null) repository else FutachaSharedBoardRepository(repository, features.store, features.httpClient, features.catalogCache)
    }
}

/** Reads common preferences at the start of each request; no profile-specific copy is stored. */
internal class FutachaSharedBoardRepository(
    private val delegate: BoardRepository,
    private val store: CompatibilityStore,
    private val httpClient: HttpClient?,
    private val catalogCache: FutachaCatalogMemoryCache = FutachaCatalogMemoryCache()
) : BoardRepository by delegate {
    private val catalogMutex = Mutex()
    private val openedCatalogs = mutableSetOf<String>()
    override suspend fun getThread(board: String, threadId: String): ThreadPage = getThreadContent(board, threadId).page
    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = getThreadContentByUrl(threadUrl).page
    override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
        val preferences = store.preferences.first()
        val cacheKey = "$board|$mode"
        val firstOpen = catalogMutex.withLock { openedCatalogs.add(cacheKey) }
        val reloadSetting = preferences.compatPreferenceValue("catalog", "catalogOpenWithReload")
        if (firstOpen && reloadSetting != "ON") {
            // Returning within this session reuses the list the user just saw.
            catalogCache.get(cacheKey)?.let { return it }
        }
        // A disk snapshot can be days old (e.g. after a restart); only an explicit
        // "OFF" opts into showing it without fetching.
        if (firstOpen && reloadSetting == "OFF") {
            mode.sharedCatalogSort()?.let { sort ->
                store.loadCatalogSnapshot(compatBoardKey(board), sort)?.let { snapshot ->
                    return CatalogPageContent(snapshot.items).also { catalogCache.put(cacheKey, it) }
                }
            }
        }
        val settings = compatCatalogFetchSettingsFromPreferences(preferences)
        val page = if (settings == null || canonicalizeBoardUrl(board) == null) delegate.getCatalogPage(board, mode)
            else delegate.getCatalogPageWithSettings(board, mode, settings)
        catalogCache.put(cacheKey, page)
        return page
    }

    // getCatalog (watcher, update checks) must use the same layout as the
    // catalog screen, or each call switches the board's catalog setup.
    override suspend fun getCatalog(board: String, mode: CatalogMode): List<CatalogItem> {
        val settings = compatCatalogFetchSettingsFromPreferences(store.preferences.first())
        if (settings == null || canonicalizeBoardUrl(board) == null) return delegate.getCatalog(board, mode)
        return delegate.getCatalogWithSettings(board, mode, settings)
    }

    override suspend fun getThreadContent(board: String, threadId: String): ThreadPageContent {
        val canonicalBoard = canonicalizeBoardUrl(board) ?: return delegate.getThreadContent(board, threadId)
        return load("${canonicalBoard}res/$threadId.htm") { delegate.getThreadContent(board, threadId) }
    }

    override suspend fun getThreadContentByUrl(threadUrl: String): ThreadPageContent {
        if (canonicalizeThreadUrl(threadUrl) == null || !threadUrl.contains(".2chan.net/")) return delegate.getThreadContentByUrl(threadUrl)
        return load(threadUrl) { delegate.getThreadContentByUrl(threadUrl) }
    }

    private suspend fun load(source: String, primary: suspend () -> ThreadPageContent): ThreadPageContent {
        val preferences = store.preferences.first()
        val expected = store.tabs.first().firstOrNull { it.key == compatTabKey(source) }?.replyCount
        val useCache = canUseCompatCacheServer(preferences[COMPAT_CACHE_ENABLED_KEY] == "ON",
            preferences[COMPAT_CACHE_AVAILABLE_KEY] == "ON", source, expected ?: 0,
            preferences[COMPAT_CACHE_RESPONSE_THRESHOLD_KEY]?.toIntOrNull() ?: DEFAULT_COMPAT_CACHE_RESPONSE_THRESHOLD)
        val cached = if (useCache) buildCompatCacheThreadUrl(source, preferences[COMPAT_CACHE_BASE_URL_KEY])?.let { url ->
            try { withTimeoutOrNull(4_000) { delegate.getThreadContentByUrl(url) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } else null
        val content = cached ?: primary()
        return content
    }

    suspend fun supplement(source: String, content: ThreadPageContent): ThreadPageContent {
        val expected = store.tabs.first().firstOrNull { it.key == compatTabKey(source) }?.replyCount
        val needsSupplement = content.page.isTruncated || expected?.let { content.page.posts.size < it + 1 } == true
        val client = httpClient
        if (!needsSupplement || client == null) return content
        val candidates = buildCompatArchiveThreadCandidates(source)
        val completed = arrayOfNulls<ThreadPage>(candidates.size)
        // At most two requests at once and four seconds for the entire
        // supplement. Keep successful results even if the remaining work times out.
        withTimeoutOrNull(4_000) {
            for (batch in candidates.withIndex().chunked(2)) {
                coroutineScope {
                    batch.map { (index, url) -> async {
                        try { completed[index] = fetchCompatArchiveThreadPage(client, url) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Another source can still fill the gap. */ }
                    } }.forEach { it.await() }
                }
                val merged = withContext(AppDispatchers.parsing) {
                    mergeCompatThreadPages(content.page, completed.filterNotNull())
                }
                if (!merged.isTruncated && merged.posts.size >= (expected ?: content.page.posts.lastIndex) + 1) break
            }
        }
        val pages = completed.filterNotNull()
        if (pages.isEmpty()) return content
        val merged = withContext(AppDispatchers.parsing) { mergeCompatThreadPages(content.page, pages) }
        return content.copy(page = merged)
    }
}

/** A small session cache also covers navigation before the disk snapshot effect completes. */
internal class FutachaCatalogMemoryCache {
    private val mutex = Mutex()
    private val entries = linkedMapOf<String, CatalogPageContent>()
    suspend fun get(key: String): CatalogPageContent? = mutex.withLock {
        entries.remove(key)?.also { entries[key] = it }
    }
    suspend fun put(key: String, page: CatalogPageContent) = mutex.withLock {
        entries.remove(key)
        entries[key] = page
        while (entries.size > 4) entries.remove(entries.keys.first())
    }
}
