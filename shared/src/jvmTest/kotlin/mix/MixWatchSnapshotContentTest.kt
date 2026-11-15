package com.valoser.futacha.shared.mix

import com.valoser.futacha.shared.model.BoardSummary
import com.valoser.futacha.shared.watch.WatchSnapshotBuilder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The encoded watch snapshot is measured: boards of two sites are present, nothing MIX-shaped is. */
class MixWatchSnapshotContentTest {
    @Test
    fun theEncodedSnapshotHasBoardsButNoMixKeysOrNames() {
        val boards = listOf(
            BoardSummary("mixtest-may", "may", "", "https://may.2chan.net/b/", ""),
            BoardSummary("mixtest-img", "img", "", "https://img.2chan.net/b/", "")
        )
        val snapshot = WatchSnapshotBuilder(nowMillis = { 1L }).build(boards = boards, history = emptyList(), watchWords = listOf("猫"))
        val encoded = Json.encodeToString(snapshot)
        assertTrue(encoded.contains("may.2chan.net") || encoded.contains("mixtest-may"))
        assertFalse(encoded.contains("mix", ignoreCase = true).also { if (it) println(encoded) } && !encoded.contains("mixtest-"))
        assertFalse(encoded.replace("mixtest-", "").contains("mix", ignoreCase = true))
        assertFalse(encoded.contains("まとめ"))
    }
}
