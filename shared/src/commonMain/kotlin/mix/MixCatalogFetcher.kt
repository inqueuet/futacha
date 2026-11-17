package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.compat.storeCanonicalBoardUrlOf
import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.model.CatalogMode
import com.valoser.futacha.shared.model.CatalogPageContent
import com.valoser.futacha.shared.ui.board.statusCodeOrNull
import com.valoser.futacha.shared.parser.ParserException
import kotlinx.serialization.SerializationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

class MixCatalogFetcher(
    private val fetchPage: suspend (BoardSummary, CatalogMode) -> CatalogPageContent,
    private val perBoardLimit: Int = 80,
    private val concurrency: Int = 3,
    private val perBoardTimeoutMs: Long = 20_000,
    private val loadPause: suspend () -> Unit,
    private val nowMillis: () -> Long,
    /** 失敗の理由を決める。既定は例外の文字から。呼び出し側が statusCodeOrNull を使う形に差し替えられる。 */
    private val classify: (Throwable) -> MixFetchFailure = ::classifyMixFailure,
    /** 作られた時刻の表（あいもげ）を取り出す。 */
    private val createdAtOf: (CatalogPageContent) -> Map<String, Long> = { emptyMap() }
) {
    private class Fetched(val board: BoardSummary, val page: CatalogPageContent?, val failure: MixFetchFailure?)

    suspend fun fetch(mix: ResolvedMix, generation: Long): MixFetchResult {
        loadPause()
        val mode = mix.definition.order.fetchMode()
        val gate = Semaphore(concurrency)
        val fetched = coroutineScope {
            mix.boards.map { board ->
                async {
                    gate.withPermit {
                        try {
                            val page = withTimeout(perBoardTimeoutMs) { fetchPage(board, mode) }
                            Fetched(board, page, null)
                        } catch (e: TimeoutCancellationException) {
                            Fetched(board, null, MixFetchFailure.TIMEOUT)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Fetched(board, null, classify(e))
                        }
                    }
                }
            }.awaitAll()
        }
        val now = nowMillis()
        val failures = LinkedHashMap<String, MixFetchFailure>()
        val cut = LinkedHashSet<String>()
        val seen = HashSet<String>()
        val entries = ArrayList<MixEntry>()
        for (f in fetched) {
            val boardKey = storeCanonicalBoardUrlOf(f.board.url) ?: f.board.url
            val page = f.page ?: run { failures[boardKey] = f.failure ?: MixFetchFailure.UNKNOWN; continue }
            val items = if (page.items.size > perBoardLimit) { cut += boardKey; page.items.take(perBoardLimit) } else page.items
            val isAimoge = boardKey.contains("nijiurachan.net", ignoreCase = true)
            val kind = if (mode == CatalogMode.So && !isAimoge) MixCountKind.SOUDANE else MixCountKind.REPLIES
            val aimogeTimes = createdAtOf(page)
            val times: List<Pair<Long?, Boolean>> = if (isAimoge) items.map { aimogeTimes[it.id] to false }
            else mixCreatedTimesByThreadNumber(items, now)
            items.forEachIndexed { i, item ->
                val key = MixEntryKey(boardKey, item.id)
                if (seen.add(key.asString())) {
                    entries += MixEntry(f.board, item, key, times[i].first, times[i].second, i, items.size, kind)
                }
            }
        }
        return MixFetchResult(generation, MixOrder.order(entries, mix.definition.order), cut, failures, mix.skipped, now)
    }
}

internal fun classifyMixFailure(e: Throwable): MixFetchFailure {
    val code = e.statusCodeOrNull()
    val causes = generateSequence(e) { it.cause }.take(16).toList()
    val names = causes.map { it::class.simpleName.orEmpty() }
    return when {
        code == 429 -> MixFetchFailure.RATE_LIMITED
        code == 404 || code == 410 -> MixFetchFailure.NOT_FOUND
        code != null && code >= 500 -> MixFetchFailure.SERVER_ERROR
        causes.any { it is SerializationException || it is ParserException } ||
            names.any { it.contains("Parse", ignoreCase = true) } -> MixFetchFailure.PARSE
        names.any { it.contains("Network", ignoreCase = true) || it.contains("IOException") } -> MixFetchFailure.NETWORK
        else -> MixFetchFailure.UNKNOWN
    }
}
