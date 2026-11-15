package com.valoser.futacha.shared.mix

/** 経過時間の短い表示：「12分」「3時間」「2日」。時刻が無い行は null（出さない）。推定の時刻には「約」を付ける。 */
fun mixElapsedLabel(createdAtMillis: Long?, estimated: Boolean, nowMillis: Long): String? {
    val created = createdAtMillis ?: return null
    val minutes = ((nowMillis - created).coerceAtLeast(0L)) / 60_000L
    val text = when {
        minutes < 60 -> "${minutes}分"
        minutes < 60 * 24 -> "${minutes / 60}時間"
        else -> "${minutes / (60 * 24)}日"
    }
    return if (estimated) "約$text" else text
}

/** セルの札：短い板名と経過時間。 */
fun mixCellLabel(entry: MixEntry, nowMillis: Long): String {
    val elapsed = mixElapsedLabel(entry.createdAtMillis, entry.createdAtEstimated, nowMillis)
    val name = entry.board.name.trim().take(6)
    return if (elapsed == null) name else "$name $elapsed"
}

/** そ順の数字：ふたばの行は「そうだねN」。それ以外は null（今のレス数のまま）。 */
fun mixCountOverride(entry: MixEntry): String? =
    if (entry.countKind == MixCountKind.SOUDANE) "そうだね${entry.item.replyCount}" else null
