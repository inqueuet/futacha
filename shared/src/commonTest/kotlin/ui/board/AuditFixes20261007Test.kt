package com.valoser.futacha.shared.ui.board

import com.valoser.futacha.shared.compat.COMPAT_URL_BODY_CHAR_CLASS
import com.valoser.futacha.shared.model.Post
import com.valoser.futacha.shared.model.QuoteReference
import com.valoser.futacha.shared.model.ThreadPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the 2026-10-07 audit fixes in the Futacha (modern) thread screen. */
class AuditFixes20261007Test {
    private fun post(id: String, body: String, author: String? = "名無し", subject: String? = null) = Post(
        id = id,
        author = author,
        subject = subject,
        timestamp = "25/01/01(水)00:00:00",
        messageHtml = body,
        imageUrl = null,
        thumbnailUrl = null
    )

    // M1: a failure message that waits for its snackbar to be dismissed must not hold the action.
    @Test
    fun failureHandlerThatSuspendsDoesNotKeepTheActionInProgress() = runBlocking<Unit> {
        val gate = CompletableDeferred<Unit>()
        val progress = mutableListOf<Boolean>()
        val shown = mutableListOf<String>()
        val result = launchManagedThreadAction(
            actionInProgress = false,
            lastBusyActionNoticeAtMillis = 0L,
            nowMillis = 10_000L,
            busyNoticeIntervalMillis = 1_000L,
            successMessage = "成功",
            failurePrefix = "返信の送信に失敗しました",
            callbacks = ThreadActionRuntimeCallbacks<String>(
                onActionInProgressChanged = { progress += it },
                onShowMessage = { shown += it },
                onFailure = { error ->
                    // Stands in for showSnackbar(), which suspends until the user dismisses it.
                    gate.await()
                    shown += "shown:${error.message}"
                },
                onDebugLog = {},
                onInfoLog = {},
                onErrorLog = { _, _ -> }
            )
        ) {
            ThreadActionRunResult.Failure(IllegalStateException("boom"))
        }
        val job = assertNotNull(result.launchedJob)
        withTimeout(5_000L) { job.join() }
        // The job (what sendJob.join() waits for) has ended and the flag is released while the
        // message is still waiting to be dismissed.
        assertEquals(listOf(true, false), progress)
        assertTrue(shown.isEmpty())
        gate.complete(Unit)
    }

    // L1: "DEL依頼＋NG" goes through the same gates as the plain DEL request.
    @Test
    fun delAndNgGateRefusesArchivesAndBusyScreens() {
        assertEquals(DelAndNgGate.PROCEED, resolveDelAndNgGate("https://may.2chan.net/b/", actionInProgress = false))
        assertEquals(DelAndNgGate.BUSY, resolveDelAndNgGate("https://may.2chan.net/b/", actionInProgress = true))
        assertEquals(
            DelAndNgGate.ARCHIVE_READ_ONLY,
            resolveDelAndNgGate("https://inqueuet.com/b/res/1.htm", actionInProgress = false)
        )
    }

    @Test
    fun reportAndNgResultKeepsTheReviewWordingAndTheFailureReason() = runBlocking<Unit> {
        val review = sendReportAndOptionalNg(
            alsoNg = true,
            send = { error("offline") },
            registerNg = {},
            reviewCompliance = true
        )
        assertTrue(review.startsWith("通報を送信できませんでした"), review)
        assertFalse(review.contains("削除依頼"), review)
        assertTrue(review.contains("NGに登録しました"), review)

        val gone = sendReportAndOptionalNg(
            alsoNg = true,
            send = { error("HTTP 404") },
            registerNg = { error("disk full") }
        )
        assertTrue(gone.contains("削除依頼を送信できませんでした"), gone)
        assertTrue(gone.contains("404"), "the specific reason is kept: $gone")
        assertTrue(gone.contains("NGの登録に失敗"), gone)

        val ok = sendReportAndOptionalNg(true, send = {}, registerNg = {}, reviewCompliance = true)
        assertTrue(ok.startsWith("通報を送信しました"), ok)
    }

    // 三巡目: quote taps are tied to the line's text, not to "the N-th quote line".
    @Test
    fun quoteReferencesFollowTheLineTextWhenTheParserMergedOrSkippedLines() {
        val lines = listOf("本文", ">1行目", ">2行目", "間の本文", ">>5")
        val references = listOf(
            // Consecutive lines quoting the same post's text are one reference.
            QuoteReference(">1行目\n>2行目", listOf("3")),
            QuoteReference(">>5", listOf("5"))
        )
        assertEquals(mapOf(1 to 0, 2 to 0, 4 to 1), assignThreadMessageQuoteReferences(lines, references))
    }

    @Test
    fun aQuoteLineTheParserCouldNotResolveOpensNothingInsteadOfTheNextReference() {
        val lines = listOf(">解決できない行", ">>7")
        val references = listOf(QuoteReference(">>7", listOf("7")))
        assertEquals(mapOf(1 to 0), assignThreadMessageQuoteReferences(lines, references))
    }

    @Test
    fun positionalPairingRemainsWhenEveryQuoteLineHasAReferenceButTheTextDiffers() {
        val lines = listOf(">a", ">b")
        val references = listOf(QuoteReference(">x", listOf("1")), QuoteReference(">y", listOf("2")))
        assertEquals(mapOf(0 to 0, 1 to 1), assignThreadMessageQuoteReferences(lines, references))
        assertTrue(assignThreadMessageQuoteReferences(lines, emptyList()).isEmpty())
    }

    // 三巡目: a URL ends at a full-width space / Japanese text, parentheses stay balanced.
    @Test
    fun urlsStopAtFullWidthCharactersAndKeepBalancedParentheses() {
        val regex = Regex("https?://$COMPAT_URL_BODY_CHAR_CLASS+", RegexOption.IGNORE_CASE)
        assertEquals("https://x.y/src/123.jpg", regex.find("見て https://x.y/src/123.jpg　これ")?.value)
        assertEquals("https://x.y/a", regex.find("（https://x.y/a）です")?.value)
        assertEquals("https://x.y/wiki/A_(B)", regex.find("https://x.y/wiki/A_(B)です")?.value)

        val wiki = "https://ja.wikipedia.org/wiki/A_(B)"
        assertEquals(4 until 4 + wiki.length, threadMessageUrlRange(wiki, 4))
        // A closing parenthesis without an opener and the sentence full stop are not part of the URL.
        assertEquals(0 until "https://x.y/y".length, threadMessageUrlRange("https://x.y/y).", 0))
        assertNull(threadMessageUrlRange("https://", 0))
    }

    @Test
    fun readAloudStillSpeaksTheTextAfterAUrlFollowedByAFullWidthSpace() {
        val spoken = stripUrlsForReadAloud("リンク https://x.y/a.jpg　これ")
        assertTrue(spoken.contains("これ"), spoken)
        assertFalse(spoken.contains("https"), spoken)
    }

    // 低: full-width/half-width ASCII and the kana types no longer separate search and NG matches.
    @Test
    fun searchAndNgIgnoreTheFullWidthAsciiDifference() {
        assertEquals("abc 123", canonicalThreadSearchText("ＡＢＣ　１２３").lowercase())
        val body = post("1", "ＮＧです")
        val other = post("2", "関係ない")
        val author = post("3", "本文", author = "ＡＬＩＣＥ")
        val lower = buildLowerBodyByPost(listOf(body, other, author))
        assertTrue(matchesNgFilters(body, emptyList(), listOf("ng"), lower))
        assertFalse(matchesNgFilters(other, emptyList(), listOf("ng"), lower))
        assertTrue(matchesNgFilters(author, listOf("alice"), emptyList(), lower))
        // Highlight ranges computed on the folded text still index the original text.
        assertEquals(listOf(0..2), computeHighlightRanges("ＡＢＣ", "abc"))
        val page = ThreadPage(
            threadId = "1",
            boardTitle = null,
            expiresAtLabel = null,
            deletedNotice = null,
            posts = listOf(body, other)
        )
        // A full-width rule matches the half-width text and vice versa.
        assertEquals(listOf("2"), applyNgFilters(page, emptyList(), listOf("ＮＧ"), enabled = true).posts.map { it.id })
    }

    // The kana types (hiragana, katakana, half-width katakana) are not told apart either, but sounds are.
    @Test
    fun searchAndNgIgnoreTheKanaTypeButNotTheSound() {
        assertEquals("ねこ", canonicalThreadSearchText("ネコ"))
        assertEquals("ねこ", canonicalThreadSearchText("ﾈｺ"))
        val cat = post("1", "うちのネコ")
        val dog = post("2", "うちのイヌ")
        val lower = buildLowerBodyByPost(listOf(cat, dog))
        assertTrue(matchesNgFilters(cat, emptyList(), listOf("ねこ"), lower))
        assertTrue(matchesNgFilters(cat, emptyList(), listOf("ﾈｺ"), lower))
        assertFalse(matchesNgFilters(dog, emptyList(), listOf("ねこ"), lower))
        assertEquals(listOf(3..4), computeHighlightRanges("うちのネコ", "ねこ"))
        // 「か」 is not 「が」.
        assertFalse(matchesNgFilters(post("3", "ガ"), emptyList(), listOf("か"), buildLowerBodyByPost(listOf(post("3", "ガ")))))
    }

    // 低: the newer of the saved copy and the shared snapshot is shown.
    @Test
    fun theNewerOfTheSavedCopyAndTheSharedSnapshotWins() {
        fun candidate(label: String, at: Long) = OfflineThreadPageCandidate(
            page = ThreadPage(
                threadId = label,
                boardTitle = null,
                expiresAtLabel = null,
                deletedNotice = null,
                posts = emptyList()
            ),
            storedAtEpochMillis = at
        )
        assertEquals("shared", chooseNewestOfflineThreadPage(candidate("saved", 100), candidate("shared", 200))?.threadId)
        assertEquals("saved", chooseNewestOfflineThreadPage(candidate("saved", 300), candidate("shared", 200))?.threadId)
        assertEquals("saved", chooseNewestOfflineThreadPage(candidate("saved", 200), candidate("shared", 200))?.threadId)
        assertEquals("shared", chooseNewestOfflineThreadPage(null, candidate("shared", 1))?.threadId)
        assertEquals("saved", chooseNewestOfflineThreadPage(candidate("saved", 1), null)?.threadId)
        assertNull(chooseNewestOfflineThreadPage(null, null))
    }
}
