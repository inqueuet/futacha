package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityImageNgManagementTest {
    @Test
    fun referenceManagerUnifiesUrlAndPhashRowsForTheCurrentSourceAndBoard() {
        val rules = listOf(
            rule("url", CompatNgKind.CATALOG_IMAGE, "board-a", "https://img.example/src/a.jpg", 10),
            rule("phash", CompatNgKind.CATALOG_IMAGE_PHASH, "*", "0123456789abcdef", 30, "https://img.example/src/b.jpg"),
            rule("other-board", CompatNgKind.CATALOG_IMAGE_PHASH, "board-b", "fedcba9876543210", 40),
            rule("thread", CompatNgKind.THREAD_IMAGE_PHASH, "board-a", "aaaaaaaaaaaaaaaa", 50),
            rule("legacy-thread", CompatNgKind.THREAD_IMAGE_PHASH, "thread-a", "bbbbbbbbbbbbbbbb", 60)
        )

        assertEquals(
            listOf("phash", "url"),
            compatImageNgManagementRules(rules, "board-a", CompatImageNgSource.CATALOG)
                .map(CompatNgRule::id)
        )
        assertEquals(
            listOf("legacy-thread", "thread"),
            compatImageNgManagementRules(
                rules,
                "board-a",
                CompatImageNgSource.THREAD,
                legacyThreadKey = "thread-a"
            ).map(CompatNgRule::id)
        )
    }

    @Test
    fun referenceRowsUseMemoThenFilenameThenFallbackAndBoardLabels() {
        val memo = rule(
            "memo",
            CompatNgKind.THREAD_IMAGE_PHASH,
            "*",
            "0123456789abcdef",
            1,
            "https://img.example/src/a.jpg?x=1",
            "メモ"
        )
        val filename = memo.copy(id = "filename", memo = "")
        val fallback = memo.copy(id = "fallback", imageUrl = null, memo = "")

        assertEquals("メモ", compatImageNgDisplayTitle(memo))
        assertEquals("a.jpg", compatImageNgDisplayTitle(filename))
        assertEquals("NG画像", compatImageNgDisplayTitle(fallback))
        assertEquals("全ての板", compatImageNgBoardLabel(memo, "may"))
        assertEquals("may", compatImageNgBoardLabel(memo.copy(scopeKey = "board-a"), "may"))
    }

    @Test
    fun referenceSearchUsesDisplayedTitleAndFirstUrlButNotOpaqueHash() {
        val rule = rule(
            "rule",
            CompatNgKind.CATALOG_IMAGE_PHASH,
            "*",
            "0123456789abcdef",
            1,
            "https://img.example/src/sample.jpg",
            "ＴＥＳＴ"
        )

        assertTrue(compatImageNgMatchesSearch(rule, "test"))
        assertTrue(compatImageNgMatchesSearch(rule.copy(memo = ""), "SAMPLE.JPG"))
        assertFalse(compatImageNgMatchesSearch(rule, "0123456789abcdef"))
    }

    private fun rule(
        id: String,
        kind: CompatNgKind,
        scope: String,
        value: String,
        createdAt: Long,
        imageUrl: String? = null,
        memo: String = ""
    ) = CompatNgRule(
        id = id,
        kind = kind,
        scopeKey = scope,
        normalizedValue = value,
        createdAtEpochMillis = createdAt,
        imageUrl = imageUrl,
        memo = memo
    )
}
