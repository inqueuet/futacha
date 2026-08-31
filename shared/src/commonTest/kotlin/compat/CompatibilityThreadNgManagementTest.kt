package com.valoser.futacha.shared.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatibilityThreadNgManagementTest {
    @Test
    fun headerManagerIncludesPreviouslyWrittenPostNumberAndPosterIdentityRules() {
        assertEquals(
            setOf(
                CompatNgKind.THREAD_REFUSE,
                CompatNgKind.THREAD_POST_NO,
                CompatNgKind.THREAD_POSTER_ID
            ),
            compatThreadReferenceKinds(CompatNgKind.THREAD_REFUSE)
        )
        assertEquals(
            setOf(CompatNgKind.THREAD_IGNORE),
            compatThreadReferenceKinds(CompatNgKind.THREAD_IGNORE)
        )
    }

    @Test
    fun referenceManagersShowGlobalAndCurrentThreadRulesInDisplayedWordOrder() {
        val rules = listOf(
            rule("z", CompatNgKind.THREAD_REFUSE, "thread-a", "zebra", "Ｚｅｂｒａ"),
            rule("a", CompatNgKind.THREAD_POST_NO, "*", "12"),
            rule("other", CompatNgKind.THREAD_REFUSE, "thread-b", "hidden"),
            rule("body", CompatNgKind.THREAD_IGNORE, "thread-a", "apple", "Apple")
        )

        assertEquals(
            listOf("a", "z"),
            compatThreadReferenceRules(rules, "thread-a", CompatNgKind.THREAD_REFUSE)
                .map(CompatNgRule::id)
        )
        assertEquals("No.12", compatThreadReferenceDisplayValue(rules[1]))
        assertEquals(
            listOf("body"),
            compatThreadReferenceRules(rules, "thread-a", CompatNgKind.THREAD_IGNORE)
                .map(CompatNgRule::id)
        )
    }

    @Test
    fun editorUsesReferenceTrimmingLengthForbiddenWordsAndDuplicateScopeRules() {
        assertEquals("12345678901234567890", cleanCompatThreadReferenceWord("　123456789012345678901　"))
        assertTrue(isCompatThreadRefuseForbidden("　無題　"))
        assertFalse(isCompatThreadRefuseForbidden("無題ではない"))
        val rules = listOf(
            rule("global", CompatNgKind.THREAD_REFUSE, "*", "test", "ＴＥＳＴ"),
            rule("local", CompatNgKind.THREAD_IGNORE, "thread-a", "body", "ＢＯＤＹ")
        )

        assertTrue(
            hasCompatThreadReferenceDuplicate(
                rules,
                CompatNgKind.THREAD_REFUSE,
                "Test",
                globalScope = false
            ),
            "ThreadRefuse manual add compares the word across both scopes"
        )
        assertFalse(
            hasCompatThreadReferenceDuplicate(
                rules,
                CompatNgKind.THREAD_IGNORE,
                "body",
                globalScope = true
            ),
            "ThreadIgnore permits the same word in the other scope"
        )
        assertTrue(
            hasCompatThreadReferenceDuplicate(
                rules,
                CompatNgKind.THREAD_IGNORE,
                "body",
                globalScope = false
            )
        )
    }

    @Test
    fun longPressRegistrationUsesRawReferenceRowsAndReferenceTables() {
        val post = CompatPostSnapshot(
            position = 0,
            postNo = "123",
            author = "作者",
            subject = "題名",
            timestamp = "08/25 12:00 IP:192.0.2.1",
            posterId = "ID:AbCd",
            messageHtml = "本文のとても長い一行1234567890<br>&gt;&gt;100<br>削除されました"
        )

        assertEquals(
            listOf(
                CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, "題名"),
                CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, "作者"),
                CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, "ID:AbCd"),
                CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, "IP:192.0.2.1"),
                CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_REFUSE, "No.123"),
                CompatThreadNgRegistrationCandidate(CompatNgKind.THREAD_IGNORE, "本文のとても長い一行1234567890".take(20))
            ),
            compatReferenceThreadNgCandidates(post)
        )
    }

    private fun rule(
        id: String,
        kind: CompatNgKind,
        scope: String,
        value: String,
        memo: String = ""
    ) = CompatNgRule(id, kind, scope, value, 1L, memo = memo)
}
