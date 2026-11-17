package com.valoser.futacha.shared.model

/** Categories reported by Futaba itself; missing details must not be guessed. */
enum class PostDeletionKind(val label: String, val notice: String) {
    THREAD_OWNER("スレ主", "スレッドを立てた人によって削除されました"),
    AUTHOR("投稿者本人", "書き込みをした人によって削除されました"),
    ADMINISTRATOR("管理者", "管理者によって削除されました"),
    ISOLATED("隔離", "削除依頼によって隔離されました"),
    UNKNOWN("種類不明", "削除されました")
}

fun postDeletionKind(plainMessage: String, isDeleted: Boolean, isIsolated: Boolean): PostDeletionKind? {
    if (isIsolated) return PostDeletionKind.ISOLATED
    if (!isDeleted) return null
    return plainMessage.lineSequence().map(String::trim)
        .mapNotNull { line -> PostDeletionKind.entries.firstOrNull { it.notice == line } }
        .firstOrNull() ?: PostDeletionKind.UNKNOWN
}

/** Only whole server-notice lines are styled. Quoted notices remain quotes. */
fun postDeletionNoticeRanges(plainMessage: String): List<IntRange> = buildList {
    var offset = 0
    plainMessage.lineSequence().forEach { line ->
        val trimmed = line.trim()
        if (PostDeletionKind.entries.any { it.notice == trimmed }) {
            val start = offset + line.indexOf(trimmed)
            add(start until start + trimmed.length)
        }
        offset += line.length + 1
    }
}

private val deletedCountRegex = Regex("""削除された記事が\s*(\d+)\s*件あります[.。]?(?:\s*見る)?""")

fun threadNoticeWithoutDeletionCount(notice: String?): String? = notice
    ?.replace(deletedCountRegex, "")?.trim()?.takeIf(String::isNotEmpty)

fun threadDeletionSummary(notice: String?, kinds: List<PostDeletionKind>): String? {
    val serverCount = notice?.let { deletedCountRegex.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0
    val total = maxOf(serverCount, kinds.size)
    if (total == 0) return null
    val counts = kinds.groupingBy { it }.eachCount().toMutableMap()
    // A partial/cache response can retain the total without all deleted bodies.
    counts[PostDeletionKind.UNKNOWN] = (counts[PostDeletionKind.UNKNOWN] ?: 0) + total - kinds.size
    val details = PostDeletionKind.entries.mapNotNull { kind ->
        counts[kind]?.takeIf { it > 0 }?.let { "${kind.label}：${it}件" }
    }.joinToString("／")
    val label = if (PostDeletionKind.ISOLATED in kinds) "削除・隔離されたレス" else "削除されたレス"
    return "$label：${total}件（$details）"
}
