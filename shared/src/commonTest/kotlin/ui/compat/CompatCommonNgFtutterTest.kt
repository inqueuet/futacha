package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.compat.CompatNgKind
import com.valoser.futacha.shared.ui.ftutter.FtutterNgBoard
import com.valoser.futacha.shared.ui.ftutter.FtutterNgRule
import com.valoser.futacha.shared.ui.ftutter.encodeFtutterNgList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-B7: the plain NG ふたったー風 keeps reach ふたちゃ and としあき(仮) as common rules. */
class CompatCommonNgFtutterTest {
    @Test
    fun plainWordsTitlesAndIdsBecomeRulesAndTheRestIsLeftOut() {
        val raw = encodeFtutterNgList(
            listOf(
                FtutterNgRule("荒らし", 1),
                FtutterNgRule("釣り", 0),
                FtutterNgRule("ID:abc", 2),
                FtutterNgRule("a+", 1, isReg = true),
                FtutterNgRule("止めた", 1, isActive = false),
                FtutterNgRule("板だけ", 1, ita = FtutterNgBoard("img", "https://img.2chan.net/b/")),
                FtutterNgRule("ピックアップ", 3)
            )
        )
        val rules = compatCommonNgFtutterRules(raw)
        assertEquals(
            setOf(CompatNgKind.THREAD_IGNORE to "荒らし", CompatNgKind.CATALOG_IGNORE to "釣り", CompatNgKind.THREAD_POSTER_ID to "ID:abc"),
            rules.map { it.kind to it.normalizedValue }.toSet()
        )
        assertTrue(rules.all { it.scopeKey == "*" && isCompatCommonNgRule(it) })
        assertTrue(compatCommonNgFtutterRules(null).isEmpty())
    }
}
