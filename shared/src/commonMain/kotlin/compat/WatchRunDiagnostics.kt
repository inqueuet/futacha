package com.valoser.futacha.shared.compat

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val WATCH_RUN_CATALOG_KEY = "compat.watcher.diagnostics.catalog"
const val WATCH_RUN_CRAWL_KEY = "compat.watcher.diagnostics.crawl"

@Serializable
data class WatchRunDiagnostics(
    val checkedAt: Long,
    val outcome: String,
    val targets: List<String> = emptyList(),
    val words: List<String> = emptyList(),
    val matchCount: Int = 0,
    val failures: List<String> = emptyList(),
    val matchedTitles: List<String> = emptyList()
)

private val watchDiagnosticsJson = Json { ignoreUnknownKeys = true }
fun decodeWatchRunDiagnostics(raw: String?): WatchRunDiagnostics? =
    raw?.takeIf { it.length <= 18_000 }?.let { runCatching { watchDiagnosticsJson.decodeFromString<WatchRunDiagnostics>(it) }.getOrNull() }

/** Diagnostics must never delay notifications or turn a successful check into a failed run. */
suspend fun recordWatchRunDiagnostics(store: CompatibilityStore?, key: String, value: WatchRunDiagnostics) {
    if (store == null) return
    withContext(NonCancellable) {
        withTimeoutOrNull(1_000) {
            runCatching {
                store.savePreference(key, watchDiagnosticsJson.encodeToString(value.copy(
                    targets = value.targets.take(20).map { it.take(80) },
                    words = value.words.take(30).map { it.take(100) },
                    failures = value.failures.take(10).map { it.take(160) },
                    matchedTitles = value.matchedTitles.take(10).map { it.take(100) }
                )))
            }
        }
    }
}
