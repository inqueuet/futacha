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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Composable
internal fun rememberFutachaSharedRepository(repository: BoardRepository): BoardRepository {
    val features = LocalFutachaSharedFeatures.current
    return remember(repository, features?.store, features?.httpClient) {
        if (features == null) repository else FutachaSharedBoardRepository(repository, features.store, features.httpClient)
    }
}

/** Reads common preferences at the start of each request; no profile-specific copy is stored. */
internal class FutachaSharedBoardRepository(
    private val delegate: BoardRepository,
    private val store: CompatibilityStore,
    private val httpClient: HttpClient?
) : BoardRepository by delegate {
    private val catalogMutex = Mutex()
    private val openedCatalogs = mutableSetOf<String>()
    override suspend fun getThread(board: String, threadId: String): ThreadPage = getThreadContent(board, threadId).page
    override suspend fun getThreadByUrl(threadUrl: String): ThreadPage = getThreadContentByUrl(threadUrl).page
    override suspend fun getCatalogPage(board: String, mode: CatalogMode): CatalogPageContent {
        val preferences = store.preferences.first()
        val firstOpen = catalogMutex.withLock { openedCatalogs.add("$board|$mode") }
        if (firstOpen && preferences.compatPreferenceValue("catalog", "catalogOpenWithReload") == "OFF") {
            mode.sharedCatalogSort()?.let { sort ->
                store.loadCatalogSnapshot(compatBoardKey(board), sort)?.let { return CatalogPageContent(it.items) }
            }
        }
        val settings = compatCatalogFetchSettingsFromPreferences(preferences)
        if (settings == null || canonicalizeBoardUrl(board) == null) return delegate.getCatalogPage(board, mode)
        return delegate.getCatalogPageWithSettings(board, mode, settings)
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
        val needsSupplement = content.page.isTruncated || expected?.let { content.page.posts.size < it + 1 } == true
        val client = httpClient
        if (!needsSupplement || client == null) return content
        val pages = buildList {
            buildCompatArchiveThreadCandidates(source).forEach { url ->
                try { withTimeoutOrNull(4_000) { fetchCompatArchiveThreadPage(client, url) }?.let(::add) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Another source can still fill the missing replies. */ }
            }
        }
        val merged = withContext(AppDispatchers.parsing) { mergeCompatThreadPages(content.page, pages) }
        return content.copy(page = merged)
    }
}
