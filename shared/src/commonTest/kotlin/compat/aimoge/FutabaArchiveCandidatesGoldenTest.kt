package com.valoser.futacha.shared.compat.aimoge

import com.valoser.futacha.shared.ui.compat.buildCompatArchiveThreadCandidates
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * AM-B0 (docs/aimoge-support-plan-2026-11-10.md): `buildCompatArchiveThreadCandidates`
 * (ui/compat/CompatArchiveThreadFetcher.kt:51) for Futaba thread URLs. The order and the URLs must stay byte for byte
 * after あいもげ support.
 */
class FutabaArchiveCandidatesGoldenTest {
    internal val inputs = listOf(
        "https://may.2chan.net/b/res/1439323740.htm",
        "http://may.2chan.net/b/res/1439323740.htm",
        "https://img.2chan.net/b/res/1474754356.htm",
        "https://dat.2chan.net/b/res/12345.htm",
        "https://dec.2chan.net/up2/res/123.htm",
        "https://www.2chan.net/b/res/123.htm",
        "https://may.2chan.net/27/res/324989.htm",
        "https://jun.2chan.net/jun/res/55.htm",
        "https://may.2chan.net/b/res/1439323740.htm?x=1#r1",
        "https://may.2chan.net/b/futaba.php?res=1439323740",
    )

    internal fun actual(): List<String> = inputs.map { url ->
        "$url -> ${buildCompatArchiveThreadCandidates(url).joinToString(" | ")}"
    }

    @Test
    fun candidatesStayTheGolden() {
        val actual = actual()
        assertEquals(FUTABA_ARCHIVE_CANDIDATES_GOLDEN.size, actual.size, actual.joinToString("\n\n"))
        FUTABA_ARCHIVE_CANDIDATES_GOLDEN.zip(actual).forEach { (golden, got) -> assertEquals(golden, got) }
    }
}
