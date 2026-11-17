package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.model.CatalogItem

private val FUTABA_IMAGE_TIME = Regex("/(?:thumb|cat|src)/(\\d{13})s?\\.\\w+$")
private const val MIN_VALID_MILLIS = 1_104_537_600_000L // 2005-01-01
private const val ONE_DAY_MILLIS = 86_400_000L

/** ふたばのスレの作られた時刻。画像のファイル名（13桁のミリ秒）から読む。範囲の外・画像なしは null。 */
fun futabaThreadCreatedAtMillis(item: CatalogItem, nowMillis: Long): Long? {
    val url = item.thumbnailUrl?.takeIf { it.isNotBlank() } ?: item.fullImageUrl ?: return null
    val millis = FUTABA_IMAGE_TIME.find(url.substringBefore('?'))?.groupValues?.get(1)?.toLongOrNull() ?: return null
    if (millis < MIN_VALID_MILLIS || millis > nowMillis + ONE_DAY_MILLIS) return null
    return millis
}

/** Interpolate by thread number, independently of the catalog's server order. Never extrapolate. */
internal fun mixCreatedTimesByThreadNumber(items: List<CatalogItem>, nowMillis: Long): List<Pair<Long?, Boolean>> {
    val times = items.map { futabaThreadCreatedAtMillis(it, nowMillis) }
    val anchors = items.indices.mapNotNull { i ->
        val number = items[i].id.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
        times[i]?.let { number to it }
    }.distinctBy { it.first }.sortedBy { it.first }
    return items.mapIndexed { i, item ->
        val known = times[i]
        if (known != null) return@mapIndexed known to false
        val number = item.id.toLongOrNull()?.takeIf { it > 0 } ?: return@mapIndexed null to false
        val found = anchors.binarySearchBy(number) { it.first }
        if (found >= 0) return@mapIndexed anchors[found].second to true
        val next = -found - 1
        if (next == 0 || next == anchors.size) return@mapIndexed null to false
        val before = anchors[next - 1]
        val after = anchors[next]
        if (after.second < before.second) return@mapIndexed null to false
        val fraction = (number - before.first).toDouble() / (after.first - before.first).toDouble()
        (before.second + ((after.second - before.second) * fraction).toLong()) to true
    }
}

/** 同じ板の中で、時刻のある前後の行から線で補う。両側に有る時だけ。戻りは (時刻, 推定か)。 */
fun fillMissingCreatedAt(times: List<Long?>): List<Pair<Long?, Boolean>> {
    val out = times.map { it to false }.toMutableList()
    var i = 0
    while (i < times.size) {
        if (times[i] != null) { i++; continue }
        var j = i
        while (j < times.size && times[j] == null) j++
        val before = (i - 1 downTo 0).firstOrNull { times[it] != null }
        val after = if (j < times.size) j else null
        if (before != null && after != null) {
            val a = times[before]!!; val b = times[after]!!
            for (k in i until j) {
                val t = a + (b - a) * (k - before) / (after - before)
                out[k] = t to true
            }
        }
        i = j
    }
    return out
}
