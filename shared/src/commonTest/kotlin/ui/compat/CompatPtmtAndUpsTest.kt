package com.valoser.futacha.shared.ui.compat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompatPtmtAndUpsTest {
    @Test
    fun ptmtRequiresTheLegacyConfirmationPhrase() {
        assertEquals("決意が不足しています", validateCompatPtmtCheck(""))
        assertEquals("決意に誤字があります", validateCompatPtmtCheck("後悔しませんね"))
        assertNull(validateCompatPtmtCheck("後悔しません"))
    }

    @Test
    fun ptmtRejectsNonAsciiValues() {
        assertEquals(
            "半角英数字記号以外の文字が使われています",
            validateCompatPtmtValue("abcあ", "後悔しません")
        )
        assertNull(validateCompatPtmtValue("abc-123", "後悔しません"))
    }

    @Test
    fun ptmtMutationNoticesMatchTheReferenceForNoChangeDeleteAndOverwrite() {
        assertEquals("変更はありません", compatPtmtMutationNotice("same", "same"))
        assertEquals("削除しました", compatPtmtMutationNotice("old", ""))
        assertEquals("削除しました", compatPtmtMutationNotice(null, ""))
        assertEquals("変更しました", compatPtmtMutationNotice("old", "new"))
        assertEquals("変更しました", compatPtmtMutationNotice(null, "new"))
    }

    @Test
    fun upsUploadUsesLegacyThreeMegabyteLimit() {
        assertTrue(isCompatUpsUploadSizeAllowed(1))
        assertTrue(isCompatUpsUploadSizeAllowed(3_000_000))
        assertFalse(isCompatUpsUploadSizeAllowed(0))
        assertFalse(isCompatUpsUploadSizeAllowed(3_000_001))
    }
}
