package com.valoser.futacha.shared.ui.futaber

import com.valoser.futacha.shared.model.Post
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FutaberPostSupportTest {
    private fun post(id: String, html: String) = Post(
        id = id, author = null, subject = null, timestamp = "", messageHtml = html, imageUrl = null, thumbnailUrl = null
    )

    @Test
    fun aPostTargetSurvivesTheSavedStateRoundTripAndBadInputIsRejected() {
        val reply = FutaberPostTarget.Reply("may-b", "123", "題名")
        val create = FutaberPostTarget.CreateThread("may-b")
        assertEquals(reply, futaberPostTargetFromStrings(futaberPostTargetToStrings(reply)))
        assertEquals(create, futaberPostTargetFromStrings(futaberPostTargetToStrings(create)))
        assertNull(futaberPostTargetFromStrings(futaberPostTargetToStrings(null)))
        assertNull(futaberPostTargetFromStrings(listOf("r", "only")))
        assertNull(futaberPostTargetFromStrings(listOf("x", "a")))
        assertTrue(reply.draftKey != create.draftKey)
        assertTrue(reply.draftKey != FutaberPostTarget.Reply("may-b", "124", "題名").draftKey)
    }

    @Test
    fun draftsRoundTripAndAnyJunkReadsAsNoDrafts() {
        val drafts = listOf(FutaberDraft("r:b:1", "題", "本文", 5L))
        assertEquals(drafts, decodeFutaberDrafts(encodeFutaberDrafts(drafts)))
        assertTrue(decodeFutaberDrafts(null).isEmpty())
        assertTrue(decodeFutaberDrafts("not json").isEmpty())
        assertTrue(decodeFutaberDrafts("""[{"key":""}]""").isEmpty())
    }

    @Test
    fun savingMovesTheDraftToTheFrontAndAnEmptyOneRemovesIt() {
        val a = FutaberDraft("a", comment = "A", updatedAt = 1)
        val b = FutaberDraft("b", comment = "B", updatedAt = 2)
        val both = futaberUpsertDraft(futaberUpsertDraft(emptyList(), a), b)
        assertEquals(listOf("b", "a"), both.map { it.key })
        assertEquals(listOf("a", "b"), futaberUpsertDraft(both, a.copy(comment = "A2")).map { it.key })
        assertEquals("A2", futaberUpsertDraft(both, a.copy(comment = "A2")).first().comment)
        assertEquals(listOf("b"), futaberUpsertDraft(both, FutaberDraft("a", "  ", "\n")).map { it.key })
        assertEquals("B", futaberDraftFor(both, "b")?.comment)
        assertNull(futaberDraftFor(both, "zzz"))
    }

    @Test
    fun theStoreKeepsOnlyTheNewestDraftsAndStaysUnderTheSettingSizeLimit() {
        var drafts = emptyList<FutaberDraft>()
        for (i in 1..10) {
            drafts = futaberUpsertDraft(drafts, FutaberDraft("k$i", comment = "あ".repeat(FUTABER_DRAFT_MAX_COMMENT_CHARS)))
        }
        assertTrue(drafts.size <= FUTABER_DRAFT_MAX_COUNT)
        assertEquals("k10", drafts.first().key)
        assertTrue(encodeFutaberDrafts(drafts).length <= 20_000)
        val long = futaberUpsertDraft(emptyList(), FutaberDraft("k", "x".repeat(500), "y".repeat(10_000))).single()
        assertEquals(FUTABER_DRAFT_MAX_SUBJECT_CHARS, long.subject.length)
        assertEquals(FUTABER_DRAFT_MAX_COMMENT_CHARS, long.comment.length)
    }

    @Test
    fun quotesTakeTheOwnLinesOfThePostAndNeverQuoteAQuote() {
        val p = post("55", "元の一行目<br>>前の引用<br>二行目<br><br>三行目")
        assertEquals(">元の一行目\n>二行目\n>三行目", futaberQuoteBody(p))
        assertEquals(">No.55", futaberQuoteByNumber(p))
        assertEquals(">三行目", futaberQuoteLastLine(p))
        assertEquals("", futaberQuoteBody(post("1", ">だけ")))
        assertEquals("", futaberQuoteLastLine(post("1", ">だけ")))
    }

    @Test
    fun appendingAQuoteStartsANewLineAndLeavesRoomToType() {
        assertEquals(">A\n", futaberAppendQuote("", ">A"))
        assertEquals("前の文\n>A\n", futaberAppendQuote("前の文", ">A"))
        assertEquals("前の文\n>A\n", futaberAppendQuote("前の文\n", ">A"))
        assertEquals("そのまま", futaberAppendQuote("そのまま", " "))
    }

    @Test
    fun postSettingsReadFromPreferencesWithConfirmationOnByDefault() {
        assertEquals(FutaberPostSettings(), FutaberPostSettings.from(emptyMap()))
        val s = FutaberPostSettings.from(
            mapOf(
                FutaberPreferenceKeys.POST_NAME to "名",
                FutaberPreferenceKeys.POST_EMAIL to "sage",
                FutaberPreferenceKeys.POST_CONFIRM to "OFF"
            )
        )
        assertEquals(FutaberPostSettings("名", "sage", false), s)
    }

    @Test
    fun takingCharactersNeverCutsASurrogatePairInHalf() {
        val emoji = "\uD83D\uDE00" // one character outside the BMP, two UTF-16 units
        assertEquals("abc", "abcdef".futaberTakeChars(3))
        assertEquals("ab", "ab".futaberTakeChars(5))
        assertEquals("", "abc".futaberTakeChars(0))
        // The cut would fall between the two halves: it stops one unit earlier.
        assertEquals("a", ("a" + emoji + "b").futaberTakeChars(2))
        assertEquals("a$emoji", ("a" + emoji + "b").futaberTakeChars(3))
        assertEquals("", (emoji + emoji).futaberTakeChars(1))
        // A lone high surrogate followed by a normal character is not a pair and is left as it is.
        assertEquals("\uD83Dx", "\uD83Dxyz".futaberTakeChars(2))
    }

    @Test
    fun aDraftIsBoundedWithoutBreakingACharacterAtTheLimit() {
        val emoji = "\uD83D\uDE00"
        val comment = "a".repeat(FUTABER_DRAFT_MAX_COMMENT_CHARS - 1) + emoji
        val saved = futaberUpsertDraft(emptyList(), FutaberDraft("r:b:1", comment = comment)).single()
        assertEquals(FUTABER_DRAFT_MAX_COMMENT_CHARS - 1, saved.comment.length)
        assertTrue(saved.comment.none { it.isSurrogate() })
    }
}
