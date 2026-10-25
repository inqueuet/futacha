package com.valoser.futacha.shared.ui.ftutter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FtutterBackupDraftMergeTest {
    @Test fun exactlyTheEncodedBudgetFitsButOneMoreCharacterIsRejected() {
        val current = (1..4).associate { "res:$it" to FtutterDraft(comment = "あ".repeat(4_000)) }
        val key = "res:new"
        // Default fields are omitted: FtutterDraft() encodes as {}, whereas
        // the real candidate also needs the non-empty comment field's syntax.
        val unitCandidate = encodeFtutterDraftsForStore(current + (key to FtutterDraft(comment = "x")))
        val remaining = FTUTTER_DRAFT_STORE_MAX_CHARS - unitCandidate.length + 1
        assertTrue(remaining in 1 until FTUTTER_DRAFT_MAX_COMMENT_CHARS)
        val fitting = mapOf(key to FtutterDraft(comment = "x".repeat(remaining)))
        assertEquals(FTUTTER_DRAFT_STORE_MAX_CHARS, encodeFtutterDraftsForStore(current + fitting).length,
            "fixture must occupy the exact encoded budget")
        val fits = ftutterMergeImportedDrafts(encodeFtutterDraftsForStore(current), fitting)
        assertEquals(FTUTTER_DRAFT_STORE_MAX_CHARS, fits.encoded.length)
        assertEquals(1, fits.added)
        assertEquals(0, fits.rejected)
        val oversized = mapOf(key to FtutterDraft(comment = "x".repeat(remaining + 1)))
        assertEquals(FTUTTER_DRAFT_STORE_MAX_CHARS + 1, encodeFtutterDraftsForStore(current + oversized).length,
            "fixture must exceed the encoded budget by exactly one character")
        val exceeds = ftutterMergeImportedDrafts(encodeFtutterDraftsForStore(current), oversized)
        assertEquals(current, parseFtutterDrafts(exceeds.encoded))
        assertEquals(1, exceeds.rejected)
        assertNotNull(exceeds.notice)
    }

    @Test fun fieldAndAttachmentLimitsRejectTheWholeCandidateWithoutCuttingText() {
        val candidates = linkedMapOf(
            "title" to FtutterDraft(title = "t".repeat(201)),
            "name" to FtutterDraft(name = "n".repeat(201)),
            "mail" to FtutterDraft(mail = "m".repeat(201)),
            "attachment" to FtutterDraft(comment = "keep this intact", sourceAttachmentPath = "p".repeat(4097)),
            "accepted" to FtutterDraft(comment = "😀".repeat(2_000), sourceAttachmentPath = "content://source/image")
        )
        val fit = ftutterMergeImportedDrafts(null, candidates)
        assertEquals(4, fit.rejected)
        assertEquals(mapOf("accepted" to candidates.getValue("accepted")), parseFtutterDrafts(fit.encoded))
        assertEquals(1, fit.added)
    }

    @Test fun savingAfterConfirmationKeepsNewEditsAndDoesNotResurrectDeletedDrafts() = runBlocking<Unit> {
        var stored: String? = encodeFtutterDraftsForStore(mapOf("res:deleted" to FtutterDraft(comment = "before confirmation")))
        val imported = linkedMapOf("res:shared" to FtutterDraft(comment = "old backup"), "res:new" to FtutterDraft(comment = "import"))
        val preview = ftutterMergeImportedDrafts(stored, imported)
        assertEquals(2, preview.added)
        val writer = FtutterPreferenceWriter(CoroutineScope(coroutineContext), { delay(1); stored }, { _, value -> delay(1); stored = value }) { _, error -> throw error }
        val latest = linkedMapOf("res:shared" to FtutterDraft(comment = "edited while confirming"), "res:other" to FtutterDraft(comment = "new current draft"))
        writer.save(FtutterKeys.DRAFTS, encodeFtutterDraftsForStore(latest))
        var actual: FtutterDraftImport? = null
        writer.editAllNow(mapOf(FtutterKeys.DRAFTS to { raw: String? ->
            ftutterMergeImportedDrafts(raw, imported).also { actual = it }.encoded
        })) { values -> stored = values.getValue(FtutterKeys.DRAFTS) }
        assertEquals(latest + ("res:new" to FtutterDraft(comment = "import")), parseFtutterDrafts(stored))
        assertEquals(1, actual?.rejected)
        assertEquals(1, actual?.added)
        assertNull(parseFtutterDrafts(stored)["res:deleted"])
        assertNotNull(actual?.notice)
    }

    @Test fun anIdenticalExistingDraftDoesNotCountAsLostOrConsumeCapacity() {
        val draft = FtutterDraft(comment = "same", sourceAttachmentPath = "/original/image.png")
        val fit = ftutterMergeImportedDrafts(encodeFtutterDraftsForStore(mapOf("res:1" to draft)), mapOf("res:1" to draft))
        assertEquals(mapOf("res:1" to draft), parseFtutterDrafts(fit.encoded))
        assertEquals(0, fit.added)
        assertEquals(0, fit.rejected)
        assertNull(fit.notice)
    }
}
