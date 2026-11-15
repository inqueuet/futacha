package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class MixCatalogState(
    val result: MixFetchResult? = null,
    val running: Boolean = false,
    val message: String? = null
)

/** Owns one visible catalog. Every request captures its inputs; obsolete responses cannot publish or cache. */
internal class MixCatalogSession(
    private val scope: CoroutineScope,
    private val cache: MixCatalogSessionCache,
    private val nowMillis: () -> Long,
    private val resolve: suspend (MixDefinition, List<BoardSummary>) -> ResolvedMix,
    private val fetch: suspend (ResolvedMix, Long) -> MixFetchResult,
    private val cacheNamespace: String = "default",
    private val isEnabled: () -> Boolean = { true }
) {
    private data class Request(val definition: MixDefinition, val boards: List<BoardSummary>)
    private var request: Request? = null
    private var activeRequest: Request? = null
    private var pendingOrder: Request? = null
    private var generation = 0L
    private var job: Job? = null
    private val mutableState = MutableStateFlow(MixCatalogState())
    val state = mutableState.asStateFlow()

    fun show(definition: MixDefinition, boards: List<BoardSummary>) {
        if (!isEnabled()) { close(); request = null; mutableState.value = MixCatalogState(); return }
        val next = Request(definition.copy(members = definition.members.toList()), boards.toList())
        if (next == request) return
        val trigger = if (request?.definition?.id == definition.id && request?.definition?.order != definition.order)
            MixRefreshTrigger.ORDER_CHANGED else MixRefreshTrigger.OPEN
        request = next
        val active = activeRequest
        if (state.value.running && active != null && active.boards == next.boards &&
            active.definition.copy(order = next.definition.order) == next.definition) {
            // Keep the current network work alive, but only the last requested order may run next.
            pendingOrder = next
            mutableState.value = MixCatalogState(running = true)
            return
        }
        pendingOrder = null
        start(next, trigger, keepResult = false)
    }

    fun refresh() {
        if (!isEnabled()) { close(); request = null; mutableState.value = MixCatalogState(); return }
        val current = request ?: return
        when (mixRefreshDecision(MixRefreshTrigger.MANUAL, state.value.running, null,
            cache.lastFinishedAt(current.definition.id), nowMillis())) {
            MixRefreshDecision.REFUSE_RUNNING -> return
            MixRefreshDecision.REFUSE_TOO_SOON -> {
                mutableState.value = state.value.copy(message = "少し待ってから更新してください")
                return
            }
            else -> start(current, MixRefreshTrigger.MANUAL, keepResult = true)
        }
    }

    private fun start(request: Request, trigger: MixRefreshTrigger, keepResult: Boolean) {
        val token = ++generation
        job?.cancel()
        activeRequest = request
        mutableState.value = MixCatalogState(result = state.value.result.takeIf { keepResult }, running = true)
        job = scope.launch {
            try {
                // The resolver loads persisted pauses first and checks their current expiry on EVERY request.
                val resolved = resolve(request.definition, request.boards)
                if (!isEnabled()) return@launch
                val key = MixCacheKey(request.definition.id, request.definition.order,
                    resolved.boards.joinToString("|") { it.url }.hashCode(), cacheNamespace)
                val cached = cache.get(key)
                val reuse = mixRefreshDecision(trigger, false, cached?.fetchedAt, null, nowMillis()) == MixRefreshDecision.USE_CACHED
                val result = if (reuse) {
                    // The process cache is shared by modes, whose IDs/names for the same URL can differ.
                    val boardsByUrl = resolved.boards.associateBy {
                        com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf(it.url) ?: it.url
                    }
                    cached!!.copy(skipped = resolved.skipped, entries = cached.entries.mapNotNull { entry ->
                        boardsByUrl[entry.key.boardUrl]?.let { entry.copy(board = it) }
                    })
                } else fetch(resolved, token)
                if (token != generation || pendingOrder != null || !isEnabled()) return@launch
                if (!reuse) {
                    cache.put(key, result)
                    cache.markFinished(request.definition.id, nowMillis())
                }
                mutableState.value = MixCatalogState(result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (token == generation && pendingOrder == null) mutableState.value = mutableState.value.copy(
                    running = false, message = "まとめカタログを読み込めませんでした")
            } finally {
                if (token == generation) {
                    activeRequest = null
                    job = null
                    val next = pendingOrder
                    pendingOrder = null
                    if (next != null && isEnabled()) start(next, MixRefreshTrigger.ORDER_CHANGED, keepResult = false)
                    else mutableState.value = mutableState.value.copy(running = false)
                }
            }
        }
    }

    fun close() {
        ++generation
        pendingOrder = null
        activeRequest = null
        job?.cancel()
    }
}
