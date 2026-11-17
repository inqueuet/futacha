package com.valoser.futacha.shared.ui.compat

import com.valoser.futacha.shared.network.PersistentCookieStorage
import com.valoser.futacha.shared.repository.CookieRepository
import com.valoser.futacha.shared.repository.InMemoryFileSystem
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

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
    fun ptmtManualCookieLastsTheReference400Days() = runBlocking {
        // (L59) The reference meant 400 days; 400 days in ms overflowed an Int to
        // 200,261,632 (about 2.3 days). Use the real clock: an expiry in the past is
        // dropped by the storage instead of being stored.
        val repository = CookieRepository(PersistentCookieStorage(InMemoryFileSystem()))
        val now = Clock.System.now().toEpochMilliseconds()
        saveCompatPtmtCookie(repository, "abc-123", now)
        val stored = repository.listCookies().single { it.name == "ptmt" }
        assertEquals("abc-123", stored.value)
        assertEquals("2chan.net", stored.domain)
        assertEquals(now + 34_560_000_000L, stored.expiresAtMillis)
    }

    @Test
    fun upsUploadUsesLegacyThreeMegabyteLimit() {
        assertTrue(isCompatUpsUploadSizeAllowed(1))
        assertTrue(isCompatUpsUploadSizeAllowed(3_000_000))
        assertFalse(isCompatUpsUploadSizeAllowed(0))
        assertFalse(isCompatUpsUploadSizeAllowed(3_000_001))
    }
}
