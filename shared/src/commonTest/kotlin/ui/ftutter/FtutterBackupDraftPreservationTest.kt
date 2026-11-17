package com.valoser.futacha.shared.ui.ftutter

import com.valoser.futacha.shared.model.BoardSummary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The actual import plan API; safe to run against the unchanged importer as the before regression. */
class FtutterBackupDraftPreservationTest {
    private val board = BoardSummary("m70", "M70", "", "https://may.2chan.net/27/futaba.php", "")
    private val createKey = ftutterDraftKey(FtutterPostTarget.Create(board.id), board.url)
    private fun replyKey(id: String) = ftutterDraftKey(FtutterPostTarget.Reply(board.id, id), board.url)
    private fun draftJson(comment: String) = JsonObject(mapOf("comment" to JsonPrimitive(comment)))
    private fun source(create: String? = null, replies: Map<String, String> = emptyMap()): Map<String, FtutterPrefValue> = buildMap {
        create?.let {
            put("itaList", FtutterPrefValue.Texts(listOf(JsonObject(mapOf(
                "url" to JsonPrimitive(board.url), "kakikomiData" to draftJson(it)
            )).toString())))
        }
        put("rirekiUrlList", FtutterPrefValue.Texts(replies.keys.map { "https://may.2chan.net/27/res/$it.htm" }))
        replies.forEach { (id, comment) ->
            put("https://may.2chan.net/27/res/$id.htm", FtutterPrefValue.Text(JsonObject(mapOf(
                "title" to JsonPrimitive("M70 $id"), "kakikomiData" to draftJson(comment)
            )).toString()))
        }
    }
    private fun encode(drafts: Map<String, FtutterDraft>): String {
        var raw = "{}"
        drafts.entries.toList().asReversed().forEach { (key, value) -> raw = ftutterDraftsWith(parseFtutterDrafts(raw), key, value) }
        assertEquals(drafts, parseFtutterDrafts(raw), "fixture must fit without trimming")
        return raw
    }
    private fun plan(source: Map<String, FtutterPrefValue>, existing: Map<String, FtutterDraft>) =
        ftutterBackupPlan(source, listOf(board), emptyList(), mapOf(FtutterKeys.DRAFTS to encode(existing)), 0)
    private fun drafts(plan: FtutterBackupPlan) = parseFtutterDrafts(plan.preferences[FtutterKeys.DRAFTS])

    @Test fun fullDraftListKeepsAllCurrentDraftsAndReportsTheRejectedImport() {
        val current = (1..20).associate { replyKey("$it") to FtutterDraft(comment = "current $it") }
        val plan = plan(source(create = "old backup"), current)
        assertEquals(current, drafts(plan))
        assertEquals(current.keys.toList(), drafts(plan).keys.toList())
        assertTrue(plan.summary.contains("下書き"), plan.summary)
        assertTrue(plan.summary.contains("1件は取り込みません"), plan.summary)
    }

    @Test fun conflictingCreateAndReplyDraftsKeepCurrentTextAndAttachmentHint() {
        val current = linkedMapOf(createKey to FtutterDraft(comment = "new create", sourceAttachmentPath = "content://current/image"),
            replyKey("100") to FtutterDraft(comment = "new reply"))
        val plan = plan(source(create = "old create", replies = mapOf("100" to "old reply")), current)
        assertEquals(current, drafts(plan))
        assertTrue(plan.summary.contains("2件は取り込みません"), plan.summary)
    }

    @Test fun encodedBudgetCannotEvictOrShortenExistingDrafts() {
        val current = (1..4).associate { replyKey("$it") to FtutterDraft(comment = "あ".repeat(3_950)) }
        val plan = plan(source(replies = mapOf("100" to "い".repeat(3_900), "101" to "small")), current)
        val expected = current + (replyKey("101") to FtutterDraft(comment = "small"))
        assertEquals(expected, drafts(plan))
        assertTrue(plan.summary.contains("1件は取り込みません"), plan.summary)
    }

    @Test fun anOversizedImportedDraftIsRejectedRatherThanSilentlyTruncated() {
        val current = mapOf(createKey to FtutterDraft(comment = "keep"))
        val plan = plan(source(replies = mapOf("100" to "😀".repeat(2_001))), current)
        assertEquals(current, drafts(plan))
        assertTrue(plan.summary.contains("1件は取り込みません"), plan.summary)
    }

    @Test fun escapedTextMustFitItsEncodedSizeAndLaterSmallDraftCanStillBeImported() {
        val current = (1..4).associate { replyKey("$it") to FtutterDraft(comment = "あ".repeat(3_900)) }
        val plan = plan(source(replies = linkedMapOf("100" to "\n".repeat(3_000), "101" to "fits")), current)
        assertEquals(current + (replyKey("101") to FtutterDraft(comment = "fits")), drafts(plan))
    }

    @Test fun importingIntoAnEmptyStorePreservesSourceOrderAndIdempotence() {
        val source = source(create = "create", replies = linkedMapOf("100" to "first", "101" to "second"))
        val plan = plan(source, emptyMap())
        val expected = linkedMapOf(createKey to FtutterDraft(comment = "create"), replyKey("100") to FtutterDraft(comment = "first"),
            replyKey("101") to FtutterDraft(comment = "second"))
        assertEquals(expected, drafts(plan))
        assertEquals(expected.keys.toList(), drafts(plan).keys.toList())
        val repeated = plan(source, expected)
        assertEquals(expected, drafts(repeated))
        assertFalse(repeated.summary.contains("件は取り込みません"), repeated.summary)
    }
}
