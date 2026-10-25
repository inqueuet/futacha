package com.valoser.futacha.shared.ui.ftutter

/** Private metadata; the original .ftt read-count format remains unchanged. */
internal const val FTUTTER_READ_BOUNDARIES_KEY = "compat.ftutter.readBoundaries"
internal data class FtutterReadBoundary(val url: String, val count: Int, val postId: String)

internal fun decodeFtutterReadBoundaries(raw: String?): List<FtutterReadBoundary> =
    decodeFtutterList(raw).orEmpty().mapNotNull { line ->
        val parts = line.split('\t')
        val count = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size != 3 || count == null || count <= 0 || parts[0].isBlank() ||
            parts[2].isEmpty() || parts[2].length > 100 || !parts[2].all(Char::isDigit)) null
        else FtutterReadBoundary(ftutterCanonicalThreadUrl(parts[0]), count, parts[2])
    }

internal fun ftutterReadBoundaryId(raw: String?, url: String, count: Int?): String? =
    decodeFtutterReadBoundaries(raw).firstOrNull { it.url == url && it.count == count }?.postId

/** [keepFirst]（今のタブ・保護したスレ）の境界は、字数の上限で先に残す（L129）。 */
internal fun ftutterReadBoundariesAfter(raw: String?, url: String, count: Int, postId: String, keepFirst: Set<String> = emptySet()): String? {
    if (count <= 0 || postId.isEmpty() || postId.length > 100 || !postId.all(Char::isDigit)) return null
    val boundary = FtutterReadBoundary(url, count, postId)
    val old = decodeFtutterReadBoundaries(raw)
    if (old.firstOrNull() == boundary) return null
    fun line(it: FtutterReadBoundary) = "${it.url}\t${it.count}\t${it.postId}"
    val next = ftutterTrimReadRecords(listOf(boundary) + old.filterNot { it.url == url }, { it.url }, ::line, keepFirst + url)
    return encodeFtutterList(next.map(::line))
}
